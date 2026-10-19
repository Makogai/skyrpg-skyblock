package com.skyrpg.core.world;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Extra minion speed from things outside the minions mod (a pet's "Foraging minions work 20% faster while on your island"):
 * mods register a source, and a minion adds them up with its fuel and upgrades.
 */
public final class MinionSpeed {
    /** The extra speed, in percent, for a minion of this profile, type and category at this place (0 when none). */
    public interface Source {
        double percent(UUID profile, String type, String category, Level level, BlockPos pos);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    private MinionSpeed() {
    }

    public static void register(Source source) {
        SOURCES.add(source);
    }

    public static double of(UUID profile, String type, String category, Level level, BlockPos pos) {
        double total = 0;
        for (Source source : SOURCES) {
            total += source.percent(profile, type, category, level, pos);
        }
        return total;
    }
}
