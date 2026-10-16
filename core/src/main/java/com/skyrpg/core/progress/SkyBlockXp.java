package com.skyrpg.core.progress;

import net.minecraft.server.level.ServerPlayer;

/**
 * Gives SkyBlock XP (the level next to your name) from any mod: skill levels, collection tiers, unique minions. The
 * progress mod installs where it goes; core knows nothing about the level itself.
 */
public final class SkyBlockXp {
    @FunctionalInterface
    public interface Giver {
        void give(ServerPlayer player, int xp, String reason);
    }

    private static Giver giver = (player, xp, reason) -> {
    };

    private SkyBlockXp() {
    }

    public static void install(Giver installed) {
        giver = installed;
    }

    /** Gives {@code xp} SkyBlock XP, {@code reason} saying what for ("Mining level"). */
    public static void give(ServerPlayer player, int xp, String reason) {
        if (xp > 0) {
            giver.give(player, xp, reason);
        }
    }
}
