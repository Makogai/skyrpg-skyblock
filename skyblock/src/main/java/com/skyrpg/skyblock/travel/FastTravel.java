package com.skyrpg.skyblock.travel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.skyrpg.core.combat.Warmups;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.hub.Hub;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.area.Areas;
import com.skyrpg.skyblock.island.Islands;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's Fast Travel: {@code /warp <place>} and the Fast Travel page. Your island and the Hub are always there;
 * other warps come from {@code data/<ns>/skyrpg/warps/<id>.json} as their islands are built, and unlock the first
 * time you set foot in their area (as on Hypixel, where you have to visit an island before warping to it).
 * <pre>
 * {"name": "The Barn", "description": "...", "icon": "minecraft:wheat", "dimension": "skyrpg_skyblock:barn",
 *  "pos": [0.5, 70, 0.5], "yaw": 180, "area": "The Barn", "order": 30}
 * </pre>
 */
public final class FastTravel {
    public static final String DIRECTORY = "skyrpg/warps";

    /** A destination. {@code area} is the area name (see {@link Areas}) whose first visit unlocks it; empty = always. */
    public record Warp(String id, String name, String description, Identifier icon, int order, String area, Consumer<ServerPlayer> go) {
    }

    /** One warp as the Fast Travel page shows it. */
    public record Entry(String id, String name, String description, Identifier icon, boolean unlocked) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::id,
                ByteBufCodecs.STRING_UTF8, Entry::name,
                ByteBufCodecs.STRING_UTF8, Entry::description,
                Identifier.STREAM_CODEC, Entry::icon,
                ByteBufCodecs.BOOL, Entry::unlocked,
                Entry::new);
    }

    /** Server to client: the warps for the Fast Travel page. */
    public record WarpList(List<Entry> warps) implements CustomPacketPayload {
        public static final Type<WarpList> TYPE = new Type<>(SkyRpgSkyblock.id("warps"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WarpList> CODEC =
                StreamCodec.composite(Entry.CODEC.apply(ByteBufCodecs.list()), WarpList::warps, WarpList::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static final AttachmentType<List<String>> VISITED = AttachmentRegistry.create(SkyRpgSkyblock.id("warps_unlocked"),
            builder -> builder.initializer(List::of).persistent(Codec.STRING.listOf()).copyOnDeath());

    private static final Map<String, Warp> BUILT_IN = new LinkedHashMap<>();
    private static Map<String, Warp> loaded = Map.of();

    static {
        BUILT_IN.put("home", new Warp("home", "Private Island", "Your own island", Identifier.parse("minecraft:grass_block"), 0, "",
                Islands::sendHome));
        BUILT_IN.put("hub", new Warp("hub", "Hub", "The village at the heart of SkyBlock", Identifier.parse("minecraft:bell"), 10, "",
                Hub::sendToHub));
        BUILT_IN.put("garden", new Warp("garden", "The Garden", "Your plots, crop milestones and visitors (SkyBlock Level 5)",
                Identifier.parse("minecraft:wheat"), 11, "", com.skyrpg.skyblock.garden.Garden::send));
    }

    private FastTravel() {
    }

    public static void init() {
        // Portals on Hypixel's maps: the Hub's fountain (and the old portal in the Mountain) lead to the Dungeon Hub.
        com.skyrpg.hub.HubPortals.ENTERED.register((player, portal, pos) -> {
            if (com.skyrpg.hub.Hub.isHub(player.level())) {
                go(player, "dungeon_hub");
            } else {
                player.sendOverlayMessage(Component.literal("This portal doesn't lead anywhere yet.").withStyle(ChatFormatting.GRAY));
            }
        });
        VISITED.identifier();
        PayloadTypeRegistry.clientboundPlay().register(WarpList.TYPE, WarpList.CODEC);
        JsonDataLoader.register(SkyRpgSkyblock.id("warps"), DIRECTORY, FastTravel::load);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
        // Visiting a warp's area unlocks it.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 40 != 0 || loaded.isEmpty()) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                String area = Areas.of(player);
                for (Warp warp : loaded.values()) {
                    if (!warp.area().isEmpty() && warp.area().equals(area) && unlock(player, warp)) {
                        player.sendSystemMessage(Component.literal("Fast Travel unlocked: ").withStyle(ChatFormatting.GREEN)
                                .append(Component.literal(warp.name()).withStyle(ChatFormatting.AQUA))
                                .append(Component.literal(" (/warp " + warp.id() + ")").withStyle(ChatFormatting.DARK_GRAY)));
                        send(player);
                    }
                }
            }
        });
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, Warp> warps = new LinkedHashMap<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                JsonArray pos = json.getAsJsonArray("pos");
                Vec3 at = new Vec3(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble());
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(json.get("dimension").getAsString()));
                float yaw = json.has("yaw") ? json.get("yaw").getAsFloat() : 0;
                float pitch = json.has("pitch") ? json.get("pitch").getAsFloat() : 0;
                String name = json.get("name").getAsString();
                warps.put(id.getPath(), new Warp(id.getPath(), name, json.has("description") ? json.get("description").getAsString() : "",
                        Identifier.parse(json.has("icon") ? json.get("icon").getAsString() : "minecraft:ender_eye"),
                        json.has("order") ? json.get("order").getAsInt() : 100,
                        json.has("area") ? json.get("area").getAsString() : name,
                        player -> {
                            ServerLevel level = player.level().getServer().getLevel(dimension);
                            if (level == null) {
                                player.sendSystemMessage(Component.literal(name + " isn't open yet.").withStyle(ChatFormatting.RED));
                                return;
                            }
                            player.teleport(new TeleportTransition(level, at, Vec3.ZERO, yaw, pitch, Set.of(), TeleportTransition.DO_NOTHING));
                            player.resetFallDistance();
                        }));
            } catch (RuntimeException e) {
                SkyRpgSkyblock.LOGGER.error("Skipping invalid warp {}: {}", id, e.getMessage());
            }
        });
        loaded = warps;
        SkyRpgSkyblock.LOGGER.info("Loaded {} warps", warps.size() + BUILT_IN.size());
    }

    /** Every warp, in Fast Travel order. */
    public static List<Warp> all() {
        List<Warp> out = new ArrayList<>(BUILT_IN.values());
        out.addAll(loaded.values());
        out.sort((a, b) -> Integer.compare(a.order(), b.order()));
        return out;
    }

    public static boolean unlocked(ServerPlayer player, Warp warp) {
        return warp.area().isEmpty() || player.getAttachedOrElse(VISITED, List.of()).contains(warp.id());
    }

    /** A launch pad sent you there: the warp opens as if you'd walked into its area. */
    public static void unlockOnArrival(ServerPlayer player, Warp warp) {
        if (!warp.area().isEmpty() && unlock(player, warp)) {
            player.sendSystemMessage(Component.literal("Fast Travel unlocked: ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(warp.name()).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" (/warp " + warp.id() + ")").withStyle(ChatFormatting.DARK_GRAY)));
            send(player);
        }
    }

    private static boolean unlock(ServerPlayer player, Warp warp) {
        List<String> visited = player.getAttachedOrElse(VISITED, List.of());
        if (visited.contains(warp.id())) {
            return false;
        }
        Set<String> more = new HashSet<>(visited);
        more.add(warp.id());
        player.setAttached(VISITED, List.copyOf(more));
        // SkyBlock Leveling's "Fast Travels Unlocked" (+15 a Travel Scroll on Hypixel; decided: a warp unlocked here counts as one)
        com.skyrpg.skyblock.leveling.LevelingTasks.once(player, "fast_travel", warp.id(), 15);
        return true;
    }

    public static void send(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, WarpList.TYPE)) {
            return;
        }
        List<Entry> entries = new ArrayList<>();
        for (Warp warp : all()) {
            entries.add(new Entry(warp.id(), warp.name(), warp.description(), warp.icon(), unlocked(player, warp)));
        }
        ServerPlayNetworking.send(player, new WarpList(entries));
    }

    /** Sends a player to a warp at once (a portal: no unlock check, no warmup). False if there's no such warp. */
    public static boolean go(ServerPlayer player, String id) {
        Warp warp = all().stream().filter(w -> w.id().equalsIgnoreCase(id)).findFirst().orElse(null);
        if (warp == null) {
            return false;
        }
        warp.go().accept(player);
        return true;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("warp").then(Commands.argument("place", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(all().stream().map(Warp::id), builder))
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String id = StringArgumentType.getString(ctx, "place");
                    Warp warp = all().stream().filter(w -> w.id().equalsIgnoreCase(id)).findFirst().orElse(null);
                    if (warp == null) {
                        ctx.getSource().sendFailure(Component.literal("Unknown destination " + id + ". See Fast Travel in the SkyBlock Menu."));
                        return 0;
                    }
                    if (!unlocked(player, warp)) {
                        ctx.getSource().sendFailure(Component.literal("You haven't been to " + warp.name() + " yet. Visit it once to unlock the warp."));
                        return 0;
                    }
                    return Warmups.request(player, warp.name(), true, () -> {
                        player.sendSystemMessage(Component.literal("Warping you to " + warp.name() + "...").withStyle(ChatFormatting.GRAY));
                        warp.go().accept(player);
                    }) ? 1 : 0;
                })));
    }
}
