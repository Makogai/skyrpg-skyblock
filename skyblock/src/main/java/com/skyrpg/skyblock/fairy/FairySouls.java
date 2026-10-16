package com.skyrpg.skyblock.fairy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.hub.mixin.DisplayAccess;
import com.skyrpg.hub.mixin.ItemDisplayAccess;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Hypixel's Fairy Souls: hidden all over the islands, each found once per profile by clicking it ("SOUL! You found a
 * Fairy Soul!"). Tia the Fairy, in the Wilderness, exchanges them five at a time for 10 SkyBlock XP each exchange
 * (Hypixel's backpack slots, unlocked at some exchanges, wait for backpacks). Where they are comes from
 * {@code data/<ns>/skyrpg/fairy_souls/<island>.json}:
 * <pre>
 * {"island": "Hub", "dimension": "skyrpg_hub:hub", "souls": [[-170, 82, -56], ...]}
 * </pre>
 * A soul is a slowly turning pink crystal (an item display) with a click box over it, there only while a player is
 * near; it sparkles for the players who haven't found it yet.
 */
public final class FairySouls {
    public static final String DIRECTORY = "skyrpg/fairy_souls";
    private static final String TAG = "skyrpg_fairy_soul";
    private static final int PER_EXCHANGE = 5;
    private static final int XP_PER_EXCHANGE = 10;
    private static final double NEAR = 48;
    private static final int TURN_TICKS = 40;

    /** The souls this profile has found ("dimension x y z"), and how many exchanges Tia has made with it. */
    static final AttachmentType<List<String>> FOUND = AttachmentRegistry.create(SkyRpgSkyblock.id("fairy_souls"),
            builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
    static final AttachmentType<Integer> EXCHANGED = AttachmentRegistry.create(SkyRpgSkyblock.id("fairy_exchanges"),
            builder -> builder.persistent(Codec.INT).copyOnDeath());

    public record Soul(String key, String island, ResourceKey<Level> dimension, BlockPos pos) {
    }

    private static Map<String, Soul> souls = Map.of();
    /** Spawned souls: key -> [display, click box]. */
    private static final Map<String, Entity[]> LIVE = new HashMap<>();
    private static final Map<UUID, String> BY_ENTITY = new HashMap<>();

    private FairySouls() {
    }

    public static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("fairy_souls"), DIRECTORY, FairySouls::load);
        ServerTickEvents.END_SERVER_TICK.register(FairySouls::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> despawnAll());
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> despawnAll());
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> click(player, entity));
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> click(player, entity));
        // Tia the Fairy: "fairy_exchange" trades every five souls found for SkyBlock XP.
        Actions.register("fairy_exchange", (player, params) -> exchange(player));
        // Admins: /fairysouls give <players> <count> (the first souls not yet found), /fairysouls reset <players>.
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(net.minecraft.commands.Commands.literal("fairysouls")
                        .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .then(net.minecraft.commands.Commands.literal("give").then(net.minecraft.commands.Commands
                                .argument("players", net.minecraft.commands.arguments.EntityArgument.players())
                                .then(net.minecraft.commands.Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                        .executes(ctx -> {
                                            int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count");
                                            for (ServerPlayer player : net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "players")) {
                                                List<String> found = new ArrayList<>(player.getAttachedOrElse(FOUND, List.of()));
                                                for (Soul soul : souls.values()) {
                                                    if (count <= 0) {
                                                        break;
                                                    }
                                                    if (!found.contains(soul.key())) {
                                                        found.add(soul.key());
                                                        count--;
                                                    }
                                                }
                                                player.setAttached(FOUND, List.copyOf(found));
                                            }
                                            ctx.getSource().sendSuccess(() -> Component.literal("Gave Fairy Souls"), true);
                                            return 1;
                                        }))))
                        .then(net.minecraft.commands.Commands.literal("reset").then(net.minecraft.commands.Commands
                                .argument("players", net.minecraft.commands.arguments.EntityArgument.players())
                                .executes(ctx -> {
                                    for (ServerPlayer player : net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx, "players")) {
                                        player.removeAttached(FOUND);
                                        player.removeAttached(EXCHANGED);
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("Reset Fairy Souls"), true);
                                    return 1;
                                })))));
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, Soul> loaded = new LinkedHashMap<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(json.get("dimension").getAsString()));
                String island = json.has("island") ? json.get("island").getAsString() : id.getPath();
                for (JsonElement soul : json.getAsJsonArray("souls")) {
                    JsonArray p = soul.getAsJsonArray();
                    BlockPos pos = new BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
                    String key = dimension.identifier() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
                    loaded.put(key, new Soul(key, island, dimension, pos));
                }
            } catch (RuntimeException e) {
                SkyRpgSkyblock.LOGGER.warn("Skipping fairy souls {}: {}", id, e.getMessage());
            }
        });
        souls = Map.copyOf(loaded);
        SkyRpgSkyblock.LOGGER.info("Loaded {} Fairy Souls", souls.size());
    }

    public static int total() {
        return souls.size();
    }

    public static int found(Player player) {
        return player.getAttachedOrElse(FOUND, List.of()).size();
    }

    // ---------------------------------------------------------------- the souls in the world

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % 20 != 0) {
            return;
        }
        for (Soul soul : souls.values()) {
            ServerLevel level = server.getLevel(soul.dimension());
            if (level == null) {
                continue;
            }
            List<ServerPlayer> near = level.getPlayers(p -> soul.pos().distToCenterSqr(p.position()) < NEAR * NEAR);
            Entity[] live = LIVE.get(soul.key());
            if (near.isEmpty() || !level.isLoaded(soul.pos())) {
                if (live != null) {
                    despawn(soul.key(), live);
                }
                continue;
            }
            if (live == null || live[0].isRemoved()) {
                if (live != null) {
                    despawn(soul.key(), live);
                }
                live = spawn(level, soul);
            }
            // A slow turn, interpolated by the client between steps.
            if (now % TURN_TICKS == 0 && live[0] instanceof Display.ItemDisplay display) {
                DisplayAccess access = (DisplayAccess) display;
                access.skyrpg$setTransformation(pose(now + TURN_TICKS));
                access.skyrpg$setTransformationInterpolationDuration(TURN_TICKS);
                access.skyrpg$setTransformationInterpolationDelay(0);
            }
            // Sparkles only for those who haven't found it.
            for (ServerPlayer player : near) {
                if (!has(player, soul)) {
                    level.sendParticles(player, ParticleTypes.END_ROD, false, false, soul.pos().getX() + 0.5, soul.pos().getY() + 0.7,
                            soul.pos().getZ() + 0.5, 3, 0.25, 0.3, 0.25, 0.01);
                }
            }
        }
    }

    private static Transformation pose(long tick) {
        float angle = (float) Math.toRadians((tick * 4.5) % 360);
        return new Transformation(new Vector3f(0, 0.55f, 0), new Quaternionf().rotateY(angle), new Vector3f(0.45f, 0.45f, 0.45f),
                new Quaternionf());
    }

    private static Entity[] spawn(ServerLevel level, Soul soul) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        display.snapTo(soul.pos().getX() + 0.5, soul.pos().getY(), soul.pos().getZ() + 0.5, 0, 0);
        ((ItemDisplayAccess) display).skyrpg$setItemStack(new ItemStack(Items.STAINED_GLASS.pink()));
        DisplayAccess access = (DisplayAccess) display;
        access.skyrpg$setTransformation(pose(level.getServer().getTickCount()));
        access.skyrpg$setBrightnessOverride(new net.minecraft.util.Brightness(15, 15));
        access.skyrpg$setViewRange(0.6f);
        display.setGlowingTag(true);
        display.addTag(TAG);
        Interaction box = EntityTypes.INTERACTION.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (box != null) {
            box.snapTo(soul.pos().getX() + 0.5, soul.pos().getY() + 0.2, soul.pos().getZ() + 0.5, 0, 0);
            box.addTag(TAG);
        }
        level.addFreshEntity(display);
        if (box != null) {
            level.addFreshEntity(box);
            BY_ENTITY.put(box.getUUID(), soul.key());
        }
        BY_ENTITY.put(display.getUUID(), soul.key());
        Entity[] live = box == null ? new Entity[] {display} : new Entity[] {display, box};
        LIVE.put(soul.key(), live);
        return live;
    }

    private static void despawn(String key, Entity[] live) {
        for (Entity entity : live) {
            BY_ENTITY.remove(entity.getUUID());
            entity.discard();
        }
        LIVE.remove(key);
    }

    private static void despawnAll() {
        new HashMap<>(LIVE).forEach(FairySouls::despawn);
    }

    // ---------------------------------------------------------------- finding and exchanging

    private static boolean has(Player player, Soul soul) {
        return player.getAttachedOrElse(FOUND, List.of()).contains(soul.key());
    }

    private static InteractionResult click(Player player, Entity entity) {
        if (player.level().isClientSide() || !entity.entityTags().contains(TAG)) {
            return InteractionResult.PASS;
        }
        String key = BY_ENTITY.get(entity.getUUID());
        Soul soul = key == null ? null : souls.get(key);
        if (soul == null || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (has(serverPlayer, soul)) {
            serverPlayer.sendOverlayMessage(Msg.text("You have already found that Fairy Soul!", ChatFormatting.LIGHT_PURPLE));
            return InteractionResult.SUCCESS;
        }
        List<String> found = new ArrayList<>(serverPlayer.getAttachedOrElse(FOUND, List.of()));
        found.add(soul.key());
        serverPlayer.setAttached(FOUND, List.copyOf(found));
        long onIsland = souls.values().stream().filter(s -> s.island().equals(soul.island()) && found.contains(s.key())).count();
        long ofIsland = souls.values().stream().filter(s -> s.island().equals(soul.island())).count();
        Notice.Panel panel = Notice.of(Notice.Kind.INFO).icon(Notice.SPARKLE).tag("SOUL!").color(0xFF55FF).panel();
        panel.line(Msg.text(soul.island() + ": " + onIsland + "/" + ofIsland + "  ·  Total: " + found.size() + "/" + souls.size(), ChatFormatting.GRAY));
        int ready = found.size() / PER_EXCHANGE - serverPlayer.getAttachedOrElse(EXCHANGED, 0);
        if (ready > 0) {
            panel.line(Msg.text("Bring them to Tia the Fairy in the Wilderness to exchange them.", ChatFormatting.LIGHT_PURPLE));
        }
        panel.send(serverPlayer, Component.literal("You found a Fairy Soul!").withColor(0xFF55FF));
        Msg.playSound(serverPlayer, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
        Msg.playSound(serverPlayer, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.8f);
        if (serverPlayer.level() instanceof ServerLevel level) {
            level.sendParticles(serverPlayer, ParticleTypes.CHERRY_LEAVES, false, false, soul.pos().getX() + 0.5, soul.pos().getY() + 0.7,
                    soul.pos().getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.05);
        }
        return InteractionResult.SUCCESS;
    }

    /** Tia the Fairy: every five souls found and not yet exchanged become 10 SkyBlock XP. */
    private static void exchange(ServerPlayer player) {
        int found = found(player);
        int done = player.getAttachedOrElse(EXCHANGED, 0);
        int ready = found / PER_EXCHANGE - done;
        if (ready <= 0) {
            int need = PER_EXCHANGE - found % PER_EXCHANGE;
            Notice.of(Notice.Kind.INFO).icon(Notice.SPARKLE).tag("TIA").color(0xFF55FF).send(player,
                    Msg.text("Find " + need + " more Fairy Soul" + (need == 1 ? "" : "s") + " and I'll exchange them. (" + found + "/" + souls.size()
                            + " found)", ChatFormatting.GRAY));
            return;
        }
        Notice.Panel panel = Notice.of(Notice.Kind.INFO).icon(Notice.SPARKLE).tag("FAIRY SOUL EXCHANGE").color(0xFF55FF).panel();
        for (int i = 1; i <= ready; i++) {
            panel.line(Msg.text("Exchange #" + (done + i) + ": +" + XP_PER_EXCHANGE + " SkyBlock XP", ChatFormatting.AQUA));
        }
        player.setAttached(EXCHANGED, done + ready);
        SkyBlockXp.give(player, XP_PER_EXCHANGE * ready, "Fairy Soul exchange");
        panel.send(player, Msg.text("Tia exchanged " + ready * PER_EXCHANGE + " Fairy Souls. (" + found + "/" + souls.size() + " found)",
                ChatFormatting.LIGHT_PURPLE));
        Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.2f);
    }

    /** Every soul's island, for pages that count them. */
    public static Set<String> islands() {
        Set<String> out = new java.util.LinkedHashSet<>();
        souls.values().forEach(soul -> out.add(soul.island()));
        return out;
    }
}
