package com.skyrpg.core.progress;

import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The emblem a player shows after their name in chat (Hypixel's SkyBlock Leveling emblems): the skyblock mod installs
 * which one a player picked; the chat mod asks for it. Null when there's none.
 */
public final class SkyBlockEmblems {
    private static Function<ServerPlayer, Component> provider = player -> null;

    private SkyBlockEmblems() {
    }

    public static void install(Function<ServerPlayer, Component> installed) {
        provider = installed;
    }

    /** The player's emblem as it shows in chat, or null. */
    public static Component of(ServerPlayer player) {
        return provider.apply(player);
    }
}
