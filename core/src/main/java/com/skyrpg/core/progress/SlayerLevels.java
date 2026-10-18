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
    /** Slayers kept by other mods (the Rift's Vampire Slayer): key -> their level, on both sides. */
    private static final java.util.Map<String, java.util.function.ToIntFunction<Player>> EXTRA = new java.util.concurrent.ConcurrentHashMap<>();

    private SlayerLevels() {
    }

    public static void install(ToIntBiFunction<Player, String> installed) {
        server = installed;
    }

    public static void installClient(ToIntBiFunction<Player, String> installed) {
        client = installed;
    }

    /** A slayer another mod keeps (both sides read it from the player). */
    public static void installKey(String key, java.util.function.ToIntFunction<Player> lookup) {
        EXTRA.put(key, lookup);
    }

    /** The player's level (0 for none) in the slayer with that key. */
    public static int level(Player player, String key) {
        java.util.function.ToIntFunction<Player> extra = EXTRA.get(key);
        if (extra != null) {
            return extra.applyAsInt(player);
        }
        return (player.level().isClientSide() ? client : server).applyAsInt(player, key);
    }
}
