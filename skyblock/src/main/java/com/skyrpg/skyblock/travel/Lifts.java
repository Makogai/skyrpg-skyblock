package com.skyrpg.skyblock.travel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.area.Areas;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's lifts: the Deep Caverns' Lift Operators take you to any layer you've already been to. Talk to one (NPC
 * action {@code {"type": "lift", "params": {"lift": "deep_caverns"}}}) for the lift's stops; a stop opens the first time
 * you walk into its area. {@code data/<ns>/skyrpg/lifts/<id>.json}:
 * <pre>
 * {"name": "Deep Caverns Lift", "dimension": "skyrpg_skyblock:deep_caverns",
 *  "stops": [{"name": "Gunpowder Mines", "area": "Gunpowder Mines", "pos": [0.5, 169, 2.5], "yaw": 0}, ...]}
 * </pre>
 */
public final class Lifts {
    public static final String DIRECTORY = "skyrpg/lifts";
    /** How close to one of its stops you have to be to ride a lift (the operators stand at the stops). */
    private static final double REACH = 10;

    public record Stop(String name, String area, Vec3 pos, float yaw) {
    }

    public record Lift(String id, String name, ResourceKey<Level> dimension, List<Stop> stops) {
    }

    /** One stop as the lift screen shows it. */
    public record StopView(String name, boolean unlocked, boolean here) {
        static final StreamCodec<ByteBuf, StopView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StopView::name,
                ByteBufCodecs.BOOL, StopView::unlocked,
                ByteBufCodecs.BOOL, StopView::here,
                StopView::new);
    }

    /** Server to client: open the lift screen. */
    public record Menu(String lift, String name, List<StopView> stops) implements CustomPacketPayload {
        public static final Type<Menu> TYPE = new Type<>(SkyRpgSkyblock.id("lift_menu"));
        public static final StreamCodec<ByteBuf, Menu> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Menu::lift,
                ByteBufCodecs.STRING_UTF8, Menu::name,
                StopView.CODEC.apply(ByteBufCodecs.list()), Menu::stops,
                Menu::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: ride to the stop at {@code stop}. The server checks everything again. */
    public record Ride(String lift, int stop) implements CustomPacketPayload {
        public static final Type<Ride> TYPE = new Type<>(SkyRpgSkyblock.id("lift_ride"));
        public static final StreamCodec<ByteBuf, Ride> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Ride::lift,
                ByteBufCodecs.VAR_INT, Ride::stop,
                Ride::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** "lift/stop" for every stop you've been to. */
    static final AttachmentType<List<String>> VISITED = AttachmentRegistry.create(SkyRpgSkyblock.id("lift_stops"),
            builder -> builder.initializer(List::of).persistent(Codec.STRING.listOf()).copyOnDeath());

    private static Map<String, Lift> lifts = Map.of();

    private Lifts() {
    }

    public static void init() {
        VISITED.identifier();
        PayloadTypeRegistry.clientboundPlay().register(Menu.TYPE, Menu.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Ride.TYPE, Ride.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Ride.TYPE, (payload, context) -> ride(context.player(), payload.lift(), payload.stop()));
        JsonDataLoader.register(SkyRpgSkyblock.id("lifts"), DIRECTORY, Lifts::load);
        Actions.register("lift", (player, params) -> open(player, params.has("lift") ? params.get("lift").getAsString() : ""));
        ServerTickEvents.END_SERVER_TICK.register(Lifts::tick);
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, Lift> loaded = new LinkedHashMap<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                List<Stop> stops = new ArrayList<>();
                for (JsonElement e : json.getAsJsonArray("stops")) {
                    JsonObject stop = e.getAsJsonObject();
                    JsonArray p = stop.getAsJsonArray("pos");
                    String name = stop.get("name").getAsString();
                    stops.add(new Stop(name, stop.has("area") ? stop.get("area").getAsString() : name,
                            new Vec3(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()),
                            stop.has("yaw") ? stop.get("yaw").getAsFloat() : 0));
                }
                loaded.put(id.getPath(), new Lift(id.getPath(), json.get("name").getAsString(),
                        ResourceKey.create(Registries.DIMENSION, Identifier.parse(json.get("dimension").getAsString())), List.copyOf(stops)));
            } catch (RuntimeException e) {
                SkyRpgSkyblock.LOGGER.warn("Skipping lift {}: {}", id, e.getMessage());
            }
        });
        lifts = loaded;
        SkyRpgSkyblock.LOGGER.info("Loaded {} lifts", lifts.size());
    }

    /** Walking into a stop's area opens that stop. */
    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 40 != 0 || lifts.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String area = null;
            for (Lift lift : lifts.values()) {
                if (lift.dimension() != player.level().dimension()) {
                    continue;
                }
                if (area == null) {
                    area = Areas.of(player);
                }
                for (Stop stop : lift.stops()) {
                    if (stop.area().equals(area)) {
                        visit(player, key(lift, stop));
                    }
                }
            }
        }
    }

    private static String key(Lift lift, Stop stop) {
        return lift.id() + "/" + stop.name();
    }

    private static void visit(ServerPlayer player, String key) {
        List<String> visited = player.getAttachedOrElse(VISITED, List.of());
        if (!visited.contains(key)) {
            Set<String> more = new HashSet<>(visited);
            more.add(key);
            player.setAttached(VISITED, List.copyOf(more));
        }
    }

    private static boolean unlocked(ServerPlayer player, Lift lift, Stop stop) {
        return player.getAttachedOrElse(VISITED, List.of()).contains(key(lift, stop));
    }

    public static void open(ServerPlayer player, String id) {
        Lift lift = lifts.get(id);
        if (lift == null || !ServerPlayNetworking.canSend(player, Menu.TYPE)) {
            return;
        }
        String area = Areas.of(player);
        List<StopView> views = new ArrayList<>();
        for (Stop stop : lift.stops()) {
            if (stop.area().equals(area)) {
                visit(player, key(lift, stop));
            }
            views.add(new StopView(stop.name(), unlocked(player, lift, stop), stop.area().equals(area)));
        }
        ServerPlayNetworking.send(player, new Menu(lift.id(), lift.name(), views));
    }

    private static void ride(ServerPlayer player, String id, int index) {
        Lift lift = lifts.get(id);
        if (lift == null || index < 0 || index >= lift.stops().size() || player.level().dimension() != lift.dimension()
                || player.isSpectator()) {
            return;
        }
        // Only from a lift platform: one of its stops must be near.
        boolean atLift = lift.stops().stream().anyMatch(s -> s.pos().distanceToSqr(player.position()) < REACH * REACH);
        Stop stop = lift.stops().get(index);
        if (!atLift || !unlocked(player, lift, stop)) {
            player.sendOverlayMessage(Msg.text("You haven't been to " + stop.name() + " yet.", ChatFormatting.RED));
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        Msg.playSound(player, SoundEvents.CHAIN_PLACE, 1.0f, 0.7f);
        player.teleport(new TeleportTransition(level, stop.pos(), Vec3.ZERO, stop.yaw(), 0, Set.of(), TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        Msg.playSound(player, SoundEvents.PISTON_CONTRACT, 0.8f, 0.8f);
        player.sendOverlayMessage(Msg.text("Lift: ", ChatFormatting.GRAY).append(Msg.text(stop.name(), ChatFormatting.AQUA)));
    }
}
