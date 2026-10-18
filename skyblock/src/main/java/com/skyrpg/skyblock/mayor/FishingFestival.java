package com.skyrpg.skyblock.mayor;

import com.skyrpg.core.text.Notice;
import com.skyrpg.skyblock.time.CalendarEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Marina's Fishing Festival, the first 3 SkyBlock days of every month: Hypixel's start and end announcements (the
 * sharks themselves are sea creatures, {@code SkySeaCreatures}).
 */
final class FishingFestival {
    private static Boolean running;

    private FishingFestival() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FishingFestival::tick);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) {
            return;
        }
        boolean now = CalendarEvents.running("fishing_festival", System.currentTimeMillis()) != null;
        if (running != null && running != now) {
            Component line = now ? Component.literal("The festival is now underway! Break out your fishing rods and watch out for sharks!")
                    : Component.literal("The festival has concluded! Time to dry off and repair your rods!");
            for (var player : server.getPlayerList().getPlayers()) {
                player.sendSystemMessage(Notice.build(Notice.Kind.SUCCESS, Component.literal("FISHING FESTIVAL ")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD).append(line.copy().withStyle(ChatFormatting.WHITE))));
            }
        }
        running = now;
    }
}
