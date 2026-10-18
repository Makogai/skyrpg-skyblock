package com.skyrpg.core.stats;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Extra fortune for particular blocks, added when one is broken (Vulcan's Fungus and Harena Fortuna: Mining Fortune on
 * Mycelium and Red Sand). Mods register a bonus; the stats mod's fortune adds them up.
 */
public final class FortuneBonus {
    public interface Bonus {
        double fortune(ServerPlayer player, BlockState state);
    }

    private static final List<Bonus> BONUSES = new ArrayList<>();

    private FortuneBonus() {
    }

    public static void register(Bonus bonus) {
        BONUSES.add(bonus);
    }

    public static double of(ServerPlayer player, BlockState state) {
        double total = 0;
        for (Bonus bonus : BONUSES) {
            total += bonus.fortune(player, state);
        }
        return total;
    }
}
