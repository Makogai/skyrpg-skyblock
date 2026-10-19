package com.skyrpg.skyblock.fishing;

import com.skyrpg.hub.mixin.DisplayAccess;
import com.skyrpg.hub.mixin.TextDisplayAccess;
import com.skyrpg.items.RodParts;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's Fishing Hotspots: rings of particles on the water (the Crimson Isle's lava) of the islands that have them. A
 * hotspot lasts about four minutes and then moves. A bobber inside one gets its one buff (Fishing Speed +15, Sea Creature
 * Chance +5, Double Hook Chance +2, Trophy Fish Chance +5 or Treasure Chance +1; doubled by a Hotspot Sinker), and 15% of the
 * sea creature bites in it are hotspot sea creatures ({@link SkySeaCreatures#rollSpecial}).
 * <p>
 * Hypixel's islands hold a set number at once (Hub 3, Crimson Isle 3, Spider's Den 1, The Park 1). Ours appear at a spot of
 * open water 8 to 28 blocks from someone who holds a rod, up to that number, so they're where someone can use them.
 */
public final class SkyHotspots {
    private static final String TAG = "skyrpg_sb_hotspot";
    private static final long LIFETIME_TICKS = 4 * 60 * 20;
    private static final double RADIUS = 3.5;
    /** Islands with hotspots and how many each holds at once (Hypixel's). */
    private static final Map<String, Integer> ISLANDS = Map.of("skyrpg_hub:hub", 3, "skyrpg_skyblock:spiders_den", 1,
            "skyrpg_skyblock:the_park", 1, "skyrpg_skyblock:crimson_isle", 3);

    /** A buff a hotspot can have: its stat, name and Hypixel's value. */
    public record Buff(String stat, String name, double value) {
    }

    static final List<Buff> BUFFS = List.of(new Buff("fishing_speed", "Fishing Speed", 15), new Buff("sea_creature_chance", "Sea Creature Chance", 5),
            new Buff("double_hook_chance", "Double Hook Chance", 2), new Buff("trophy_fish_chance", "Trophy Fish Chance", 5),
            new Buff("treasure_chance", "Treasure Chance", 1));

    static final class Spot {
        final ServerLevel level;
        final Vec3 center;
        final boolean lava;
        final Buff buff;
        final long expires;
        Display.TextDisplay label;

        Spot(ServerLevel level, Vec3 center, boolean lava, Buff buff, long expires) {
            this.level = level;
            this.center = center;
            this.lava = lava;
            this.buff = buff;
            this.expires = expires;
        }
    }

    private static final List<Spot> SPOTS = new ArrayList<>();

    private SkyHotspots() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(SkyHotspots::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            SPOTS.forEach(SkyHotspots::removeLabel);
            SPOTS.clear();
        });
    }

    /** The hotspot this bobber floats in, or null. */
    static Spot at(FishingHook hook) {
        if (SPOTS.isEmpty() || !(hook.level() instanceof ServerLevel level)) {
            return null;
        }
        for (Spot spot : SPOTS) {
            double dx = hook.getX() - spot.center.x;
            double dz = hook.getZ() - spot.center.z;
            if (spot.level == level && dx * dx + dz * dz <= RADIUS * RADIUS && Math.abs(hook.getY() - spot.center.y) <= 3) {
                return spot;
            }
        }
        return null;
    }

    /** What the hotspot under this bobber gives for a stat (twice that with a Hotspot Sinker on the rod), or 0. */
    static double bonus(FishingHook hook, ServerPlayer player, String stat) {
        Spot spot = at(hook);
        if (spot == null || !spot.buff.stat().equals(stat)) {
            return 0;
        }
        return spot.buff.value() * (RodParts.has(player.getMainHandItem(), "HOTSPOT_SINKER") ? 2 : 1);
    }

    /** Tells the fisher what their bobber is in, as the bite wait starts. */
    static void announce(FishingHook hook, ServerPlayer player) {
        Spot spot = at(hook);
        if (spot != null) {
            double value = bonus(hook, player, spot.buff.stat());
            player.sendOverlayMessage(Component.literal("Hotspot: ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal("+" + (long) value + " " + spot.buff.name()).withStyle(ChatFormatting.YELLOW)));
        }
    }

    // ---------------------------------------------------------------- life cycle

    private static boolean fishing(ServerPlayer player) {
        return player.fishing != null || player.getMainHandItem().getItem() instanceof FishingRodItem
                || player.getOffhandItem().getItem() instanceof FishingRodItem;
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % 20 == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                Integer max = ISLANDS.get(level.dimension().identifier().toString());
                if (max != null) {
                    fill(level, max, now);
                }
            }
        }
        for (Iterator<Spot> it = SPOTS.iterator(); it.hasNext(); ) {
            Spot spot = it.next();
            if (now >= spot.expires || spot.level.getServer() != server) {
                removeLabel(spot);
                spot.level.sendParticles(spot.lava ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SPLASH, spot.center.x, spot.center.y + 0.2, spot.center.z,
                        40, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0.05);
                it.remove();
            } else {
                particles(spot, now);
            }
        }
        if (now % 200 == 0) {
            // Labels loaded back from a chunk saved with the world (or left by a crash).
            for (ServerLevel level : server.getAllLevels()) {
                for (Display.TextDisplay stray : level.getEntities(EntityTypes.TEXT_DISPLAY,
                        display -> display.entityTags().contains(TAG) && SPOTS.stream().noneMatch(spot -> spot.label == display))) {
                    stray.discard();
                }
            }
        }
    }

    /** Spawns one more hotspot on this island if it has fewer than it should and someone is fishing. */
    private static void fill(ServerLevel level, int max, long now) {
        if (SPOTS.stream().filter(spot -> spot.level == level).count() >= max) {
            return;
        }
        List<ServerPlayer> fishers = level.players().stream().filter(p -> !p.isSpectator() && fishing(p)).toList();
        if (fishers.isEmpty()) {
            return;
        }
        ServerPlayer near = fishers.get(level.getRandom().nextInt(fishers.size()));
        boolean lava = level.dimension().equals(com.skyrpg.skyblock.crimson.CrimsonIsle.LEVEL);
        Vec3 site = findSite(level, near, lava);
        if (site != null) {
            spawn(level, site, lava, BUFFS.get(level.getRandom().nextInt(BUFFS.size())), now + LIFETIME_TICKS - level.getRandom().nextInt(600));
            near.sendOverlayMessage(Component.literal("A fishing hotspot appeared nearby!").withStyle(ChatFormatting.LIGHT_PURPLE));
            level.playSound(null, site.x, site.y, site.z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.AMBIENT, 1.0f, 0.7f);
        }
    }

    private static void spawn(ServerLevel level, Vec3 at, boolean lava, Buff buff, long expires) {
        Spot spot = new Spot(level, at, lava, buff, expires);
        Display.TextDisplay label = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
        label.snapTo(at.x, at.y + 2.4, at.z, 0, 0);
        ((TextDisplayAccess) label).skyrpg$setText(Component.literal("Fishing Hotspot").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("\n+" + (long) buff.value() + " " + buff.name()).withStyle(ChatFormatting.YELLOW)));
        ((TextDisplayAccess) label).skyrpg$setBackgroundColor(0x55000000);
        ((DisplayAccess) label).skyrpg$setBillboard(Display.BillboardConstraints.CENTER);
        label.addTag(TAG);
        level.addFreshEntity(label);
        spot.label = label;
        SPOTS.add(spot);
    }

    private static void removeLabel(Spot spot) {
        if (spot.label != null) {
            spot.label.discard();
            spot.label = null;
        }
    }

    /** A spot of open fluid 8 to 28 blocks from the player where a circle of the hotspot's size is fluid, or null. */
    private static Vec3 findSite(ServerLevel level, ServerPlayer player, boolean lava) {
        TagKey<Fluid> tag = lava ? FluidTags.LAVA : FluidTags.WATER;
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            double distance = 8 + player.getRandom().nextDouble() * 20;
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            if (!level.hasChunkAt(new BlockPos(x, player.getBlockY(), z))) {
                continue;
            }
            int y = surface(level, x, z, player.getBlockY() + 12, player.getBlockY() - 16, tag);
            if (y == Integer.MIN_VALUE) {
                continue;
            }
            boolean overlaps = false;
            for (Spot other : SPOTS) {
                overlaps |= other.level == level && Math.hypot(other.center.x - x, other.center.z - z) < RADIUS * 2 + 1;
            }
            if (overlaps) {
                continue;
            }
            int samples = 0;
            int good = 0;
            for (double dx = -RADIUS; dx <= RADIUS; dx += 1.5) {
                for (double dz = -RADIUS; dz <= RADIUS; dz += 1.5) {
                    if (dx * dx + dz * dz > RADIUS * RADIUS) {
                        continue;
                    }
                    samples++;
                    BlockPos pos = new BlockPos((int) Math.floor(x + 0.5 + dx), y, (int) Math.floor(z + 0.5 + dz));
                    if (level.getFluidState(pos).is(tag) && level.getFluidState(pos.above()).isEmpty()
                            && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
                        good++;
                    }
                }
            }
            if (samples > 0 && good >= samples * 0.8) {
                BlockPos pos = new BlockPos(x, y, z);
                return new Vec3(x + 0.5, y + level.getFluidState(pos).getHeight(level, pos), z + 0.5);
            }
        }
        return null;
    }

    /** The y of the topmost fluid block at x, z searching downward (air above it), or MIN_VALUE. */
    private static int surface(ServerLevel level, int x, int z, int from, int to, TagKey<Fluid> tag) {
        for (int y = from; y >= Math.max(to, level.getMinY()); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getFluidState(pos).is(tag)) {
                return level.getFluidState(pos.above()).isEmpty() ? y : Integer.MIN_VALUE;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static void particles(Spot spot, long now) {
        boolean fading = spot.expires - now < 200;
        ParticleOptions ring = spot.lava ? ParticleTypes.FLAME : ParticleTypes.END_ROD;
        ParticleOptions inside = spot.lava ? ParticleTypes.LAVA : ParticleTypes.BUBBLE_POP;
        for (ServerPlayer player : spot.level.players()) {
            if (player.distanceToSqr(spot.center) > 96 * 96) {
                continue;
            }
            if (now % 2 == 0 && !(fading && now % 4 == 0)) {
                // A turning ring: a few points a tick, drifting round.
                for (int i = 0; i < 4; i++) {
                    double angle = now * 0.12 + i * (Math.PI / 2);
                    spot.level.sendParticles(player, ring, true, true, spot.center.x + Math.cos(angle) * RADIUS, spot.center.y + 0.15,
                            spot.center.z + Math.sin(angle) * RADIUS, 1, 0, 0.02, 0, 0.0);
                }
            }
            if (now % 10 == 0) {
                // A light pillar in the middle so it shows from afar, and pops inside.
                spot.level.sendParticles(player, ring, true, true, spot.center.x, spot.center.y + 0.4, spot.center.z, 3, 0.15, 0.5, 0.15, 0.01);
                spot.level.sendParticles(player, inside, false, false, spot.center.x, spot.center.y + 0.1, spot.center.z, 4, RADIUS * 0.5, 0.05,
                        RADIUS * 0.5, 0.02);
            }
        }
    }

    /** Testing: a hotspot with a chosen buff (or a random one) on the water or lava where this player stands. */
    static int debugSpawn(ServerPlayer player, String stat) {
        Buff buff = BUFFS.stream().filter(b -> b.stat().equals(stat)).findFirst().orElse(BUFFS.get(0));
        ServerLevel level = player.level();
        spawn(level, player.position(), level.dimension().equals(com.skyrpg.skyblock.crimson.CrimsonIsle.LEVEL), buff,
                level.getServer().getTickCount() + LIFETIME_TICKS);
        return 1;
    }
}
