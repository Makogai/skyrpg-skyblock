package com.skyrpg.core.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How fast a player breaks a block, when a mod has its own rule (SkyBlock's block strength and Breaking Power): the rule
 * sees vanilla's progress per tick and returns its own. Runs on both sides, like vanilla's (the client draws the cracks).
 */
public final class BlockStrength {
    @FunctionalInterface
    public interface Rule {
        /** Progress per tick (1 = breaks at once), or {@code vanilla} to leave it alone. */
        float progress(Player player, BlockGetter level, BlockPos pos, BlockState state, float vanilla);
    }

    private static Rule rule;

    private BlockStrength() {
    }

    public static void install(Rule installed) {
        rule = installed;
    }

    public static float apply(Player player, BlockGetter level, BlockPos pos, BlockState state, float vanilla) {
        return rule == null ? vanilla : rule.progress(player, level, pos, state, vanilla);
    }
}
