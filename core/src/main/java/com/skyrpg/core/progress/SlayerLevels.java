package com.skyrpg.core.progress;

import java.util.function.ToIntBiFunction;
import net.minecraft.world.entity.player.Player;

/**
 * Slayer levels for any mod, without depending on the combat mod: SkyBlock recipes lock behind them ({@code
 * "requires": {"slayer": "zombie", "level": 3}}). Keys are Hypixel's ("zombie", "spider", "wolf", "enderman"). The
 * combat mod installs the lookups (on the server from the loaded lines, on the client from the synced page data);
 * without it every level is 0.
 */
public final class SlayerLevels {
    private static ToIntBiFunction<Player, String> server = (player, key) -> 0;
    private static ToIntBiFunction<Player, String> client = (player, key) -> 0;

    private SlayerLevels() {
    }

    public static void install(ToIntBiFunction<Player, String> installed) {
        server = installed;
    }

    public static void installClient(ToIntBiFunction<Player, String> installed) {
        client = installed;
    }

    /** The player's level (0 for none) in the slayer with that key. */
    public static int level(Player player, String key) {
        return (player.level().isClientSide() ? client : server).applyAsInt(player, key);
    }
}
