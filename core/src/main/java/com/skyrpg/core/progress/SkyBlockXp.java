package com.skyrpg.core.progress;

import java.util.function.ToIntFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

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
    private static ToIntFunction<Player> xpOf = player -> 0;

    /** XP per SkyBlock Level, as on Hypixel. */
    public static final int PER_LEVEL = 100;

    private SkyBlockXp() {
    }

    public static void install(Giver installed) {
        giver = installed;
    }

    /** Where a player's total SkyBlock XP is read from (both sides: the progress mod syncs it). */
    public static void installXp(ToIntFunction<Player> installed) {
        xpOf = installed;
    }

    /** Total SkyBlock XP. */
    public static int xp(Player player) {
        return Math.max(0, xpOf.applyAsInt(player));
    }

    /** The player's SkyBlock Level. */
    public static int level(Player player) {
        return xp(player) / PER_LEVEL;
    }

    /** Gives {@code xp} SkyBlock XP, {@code reason} saying what for ("Mining level"). */
    public static void give(ServerPlayer player, int xp, String reason) {
        if (xp > 0) {
            giver.give(player, xp, reason);
        }
    }
}
