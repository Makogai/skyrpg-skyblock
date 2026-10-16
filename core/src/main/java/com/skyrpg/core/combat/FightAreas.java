package com.skyrpg.core.combat;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;

/**
 * Where a slayer boss may rise: anywhere but the Hub, unless a game mode says otherwise (SkyBlock lets bosses rise in
 * the Hub's combat zones, like Hypixel's Graveyard).
 */
public final class FightAreas {
    private static Predicate<ServerPlayer> allowed = player -> !player.level().dimension().identifier().getNamespace().equals("skyrpg_hub");

    private FightAreas() {
    }

    public static void install(Predicate<ServerPlayer> installed) {
        allowed = installed;
    }

    public static boolean allowed(ServerPlayer player) {
        return allowed.test(player);
    }
}
