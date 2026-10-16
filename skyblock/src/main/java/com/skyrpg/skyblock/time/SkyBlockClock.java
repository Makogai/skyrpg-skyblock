package com.skyrpg.skyblock.time;

import com.skyrpg.skyblock.SkyRpgSkyblock;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Keeps the sky in step with the SkyBlock clock: morning on the calendar is morning in the world. Both run a day in
 * 20 minutes, so this only sets the time of day at start-up and nudges it back once a minute if it drifted (the day
 * count is left alone).
 */
public final class SkyBlockClock {
    private static final int CHECK_TICKS = 20 * 60;
    /** Drift smaller than this (in ticks) is left alone, so the sun never visibly jumps. */
    private static final long TOLERANCE = 40;

    private SkyBlockClock() {
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(SkyBlockClock::sync);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % CHECK_TICKS == 0) {
                sync(server);
            }
        });
    }

    private static void sync(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        overworld.dimensionType().defaultClock().ifPresent(clock -> {
            long total = overworld.clockManager().getInstance(clock).totalTicks();
            long current = Math.floorMod(total, 24000L);
            long target = SkyBlockTime.minecraftTimeOfDay(System.currentTimeMillis());
            long diff = target - current;
            if (diff > 12000) {
                diff -= 24000;
            } else if (diff < -12000) {
                diff += 24000;
            }
            if (Math.abs(diff) > TOLERANCE) {
                overworld.clockManager().setTotalTicks(clock, total + diff);
                if (Math.abs(diff) > 1200) {
                    SkyRpgSkyblock.LOGGER.info("Set the time of day to SkyBlock's {}", SkyBlockTime.now().clock());
                }
            }
        });
    }
}
