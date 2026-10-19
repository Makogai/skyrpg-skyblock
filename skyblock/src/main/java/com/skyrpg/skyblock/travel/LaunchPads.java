package com.skyrpg.skyblock.travel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's launch pads: step on one and you're sent to another island (the Farm's pad to The Barn, the Graveyard's
 * to the Spider's Den...), if you have the skill level the island asks for; otherwise you're pushed back with what it
 * needs. {@code data/<ns>/skyrpg/launch_pads/<id>.json}:
 * <pre>
 * {"name": "The Barn", "dimension": "skyrpg_hub:hub", "from": [184, 64, -200], "to": [188, 67, -196], "warp": "barn",
 *  "requires": {"skill": "skyrpg_skills:farming", "level": 1}}
 * </pre>
 * The warp is a Fast Travel warp; arriving there unlocks it, as visiting its area does. A pad back to the Hub names a
 * spot instead: {@code "destination": {"dimension": "skyrpg_hub:hub", "pos": [186.5, 65, -194.5], "yaw": 180}}.
 * A jump pad on the same island (Hypixel's Park climbs to its upper woods on them) names where it throws you:
 * {@code "jump": [-354, 103, -20]}, and you fly there in an arc.
 */
public final class LaunchPads {
    public static final String DIRECTORY = "skyrpg/launch_pads";

    public record Pad(String id, String name, ResourceKey<Level> dimension, AABB box, String warp, Identifier skill, int level,
                      ResourceKey<Level> toDimension, Vec3 toPos, float toYaw, Vec3 jump) {
    }

    private static List<Pad> pads = List.of();
    /** No second launch (or second warning) for a moment after one. */
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();

    private LaunchPads() {
    }

    public static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("launch_pads"), DIRECTORY, LaunchPads::load);
        ServerTickEvents.END_SERVER_TICK.register(LaunchPads::tick);
    }

    private static void load(Map<Identifier, JsonElement> files) {
        List<Pad> loaded = new ArrayList<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                JsonArray from = json.getAsJsonArray("from");
                JsonArray to = json.getAsJsonArray("to");
                AABB box = new AABB(from.get(0).getAsInt(), from.get(1).getAsInt(), from.get(2).getAsInt(),
                        to.get(0).getAsInt() + 1, to.get(1).getAsInt() + 1, to.get(2).getAsInt() + 1);
                Identifier skill = null;
                int level = 0;
                if (json.has("requires")) {
                    JsonObject requires = json.getAsJsonObject("requires");
                    skill = Identifier.parse(requires.get("skill").getAsString());
                    level = requires.get("level").getAsInt();
                }
                ResourceKey<Level> toDimension = null;
                Vec3 toPos = null;
                float toYaw = 0;
                if (json.has("destination")) {
                    JsonObject to2 = json.getAsJsonObject("destination");
                    JsonArray p = to2.getAsJsonArray("pos");
                    toDimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(to2.get("dimension").getAsString()));
                    toPos = new Vec3(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble());
                    toYaw = to2.has("yaw") ? to2.get("yaw").getAsFloat() : 0;
                }
                Vec3 jump = null;
                if (json.has("jump")) {
                    JsonArray j = json.getAsJsonArray("jump");
                    jump = new Vec3(j.get(0).getAsDouble() + 0.5, j.get(1).getAsDouble(), j.get(2).getAsDouble() + 0.5);
                }
                loaded.add(new Pad(id.getPath(), json.get("name").getAsString(),
                        ResourceKey.create(Registries.DIMENSION, Identifier.parse(json.get("dimension").getAsString())), box,
                        json.has("warp") ? json.get("warp").getAsString() : "", skill, level, toDimension, toPos, toYaw, jump));
            } catch (RuntimeException e) {
                SkyRpgSkyblock.LOGGER.warn("Skipping launch pad {}: {}", id, e.getMessage());
            }
        });
        pads = List.copyOf(loaded);
        SkyRpgSkyblock.LOGGER.info("Loaded {} launch pads", pads.size());
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % 4 != 0 || pads.isEmpty()) {
            return;
        }
        COOLDOWN.values().removeIf(until -> until < now);
        for (Pad pad : pads) {
            ServerLevel level = server.getLevel(pad.dimension());
            if (level == null) {
                continue;
            }
            for (ServerPlayer player : List.copyOf(level.players())) {
                if (player.isSpectator() || COOLDOWN.containsKey(player.getUUID()) || !pad.box().intersects(player.getBoundingBox())) {
                    continue;
                }
                COOLDOWN.put(player.getUUID(), now + 40);
                launch(player, pad, level);
            }
        }
    }

    private static void launch(ServerPlayer player, Pad pad, ServerLevel level) {
        if (pad.skill() != null && SkillLevels.level(player, pad.skill()) < pad.level()) {
            // Not yet: a push back off the pad and what it takes.
            Vec3 away = player.position().subtract(pad.box().getCenter()).multiply(1, 0, 1);
            Vec3 push = (away.lengthSqr() < 0.01 ? player.getLookAngle().scale(-1) : away.normalize()).scale(1.2).add(0, 0.4, 0);
            player.push(push.x, push.y, push.z);
            player.syncVelocity = true;
            String name = Character.toUpperCase(pad.name().charAt(0)) + pad.name().substring(1);
            Notice.of(Notice.Kind.WARN).send(player, Msg.text(name + " requires ", ChatFormatting.GRAY)
                    .append(Msg.text(SkillLevels.displayName(pad.skill()) + " Skill " + Msg.roman(pad.level()), ChatFormatting.RED))
                    .append(Msg.text(".", ChatFormatting.GRAY)));
            Msg.playSound(player, SoundEvents.VILLAGER_NO, 0.7f, 1.0f);
            return;
        }
        if (pad.jump() != null) {
            Vec3 velocity = jumpVelocity(player.position(), pad.jump());
            player.setDeltaMovement(velocity);
            player.syncVelocity = true;
            player.resetFallDistance();
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 12, 0.4, 0.1, 0.4, 0.05);
            Msg.playSound(player, SoundEvents.SLIME_JUMP, 1.0f, 0.8f);
            return;
        }
        if (pad.toPos() != null) {
            ServerLevel to = level.getServer().getLevel(pad.toDimension());
            if (to != null) {
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 20, 0.4, 0.1, 0.4, 0.05);
                Msg.playSound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
                player.teleport(new net.minecraft.world.level.portal.TeleportTransition(to, pad.toPos(), Vec3.ZERO, pad.toYaw(), 0, java.util.Set.of(),
                        net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
                player.resetFallDistance();
            }
            return;
        }
        FastTravel.Warp warp = FastTravel.all().stream().filter(w -> w.id().equals(pad.warp())).findFirst().orElse(null);
        if (warp == null) {
            player.sendOverlayMessage(Msg.text(pad.name() + " isn't open yet.", ChatFormatting.RED));
            return;
        }
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 20, 0.4, 0.1, 0.4, 0.05);
        Msg.playSound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f);
        player.sendOverlayMessage(Component.literal("Travelling to ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(pad.name()).withStyle(ChatFormatting.AQUA)).append(Component.literal("...").withStyle(ChatFormatting.GRAY)));
        warp.go().accept(player);
        FastTravel.unlockOnArrival(player, warp);
    }

    /**
     * The push that lands a player at {@code target}: a player in the air keeps 91% of their sideways speed and 98% of
     * their upward speed each tick, less 0.08 for gravity. Of the flight times that come down onto the target, the
     * shortest whose arc peaks at least 2.5 blocks above it (so it clears the ledge it lands on).
     */
    static Vec3 jumpVelocity(Vec3 from, Vec3 target) {
        double dx = target.x - from.x;
        double dz = target.z - from.z;
        double dy = target.y - from.y;
        double flat = Math.sqrt(dx * dx + dz * dz);
        for (int ticks = 8; ticks <= 60; ticks++) {
            // y(t) = a * vy + b, with vy the starting upward speed
            double a = 0;
            double b = 0;
            double va = 1;
            double vb = 0;
            for (int k = 0; k < ticks; k++) {
                a += va;
                b += vb;
                va *= 0.98;
                vb = (vb - 0.08) * 0.98;
            }
            double vy = (dy - b) / a;
            boolean falling = va * vy + vb < 0;
            double vh = flat * (1 - 0.91) / (1 - Math.pow(0.91, ticks));
            // the highest point of this arc
            double peak = 0;
            double y = 0;
            double v = vy;
            for (int k = 0; k < ticks && v > 0; k++) {
                y += v;
                v = (v - 0.08) * 0.98;
                peak = Math.max(peak, y);
            }
            if (falling && vy > 0 && vy < 3.5 && vh < 3 && peak >= dy + 2.5) {
                return new Vec3(flat == 0 ? 0 : dx / flat * vh, vy, flat == 0 ? 0 : dz / flat * vh);
            }
        }
        return new Vec3(dx * 0.1, 1.5, dz * 0.1);
    }
}
