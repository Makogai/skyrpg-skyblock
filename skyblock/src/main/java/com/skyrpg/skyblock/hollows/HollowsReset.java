package com.skyrpg.skyblock.hollows;

import com.skyrpg.hub.HubAreas;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Hypixel's Crystal Hollows lobbies close after a while and the next one is generated anew. Ours is one shared world
 * that's made anew on the first start a week after it was generated (or after an admin's {@code /hollows reset}): its
 * folder is deleted before the worlds load, so it generates again with fresh gemstones, and its structures are placed
 * again. Mined tunnels never grow back in between, as on Hypixel.
 */
public final class HollowsReset {
    private static final long WEEK = 7L * 24 * 60 * 60 * 1000;
    private static final String CREATED = "skyrpg_hollows_created.txt";
    private static final String REQUESTED = "skyrpg_hollows_reset.txt";
    private static boolean reset;

    private HollowsReset() {
    }

    static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(HollowsReset::beforeWorlds);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            // Which Hollows this is: what players did in an older one is forgotten (HollowsState).
            try {
                HollowsState.epoch = Long.parseLong(Files.readString(server.getWorldPath(LevelResource.ROOT).resolve(CREATED)).trim());
            } catch (IOException | NumberFormatException e) {
                HollowsState.epoch = 1;
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (reset) {
                reset = false;
                HubAreas.replaceAll(server, CrystalHollows.LEVEL);
                SkyRpgSkyblock.LOGGER.info("The Crystal Hollows were made anew");
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("hollowsreset")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS)).executes(ctx -> {
                    Path root = ctx.getSource().getServer().getWorldPath(LevelResource.ROOT);
                    try {
                        Files.writeString(root.resolve(REQUESTED), String.valueOf(System.currentTimeMillis()));
                    } catch (IOException e) {
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("The Crystal Hollows will be made anew on the next restart.")
                            .withStyle(ChatFormatting.GOLD), true);
                    return 1;
                })));
    }

    private static void beforeWorlds(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT);
        Path created = root.resolve(CREATED);
        Path requested = root.resolve(REQUESTED);
        Path folder = root.resolve("dimensions").resolve(SkyRpgSkyblock.MOD_ID).resolve("crystal_hollows");
        long now = System.currentTimeMillis();
        try {
            long since = Files.exists(created) ? Long.parseLong(Files.readString(created).trim()) : -1;
            if (since < 0) {
                Files.writeString(created, String.valueOf(now));
                return;
            }
            if (now - since < WEEK && !Files.exists(requested)) {
                return;
            }
            if (Files.exists(folder)) {
                try (Stream<Path> files = Files.walk(folder)) {
                    for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(file);
                    }
                }
            }
            Files.deleteIfExists(requested);
            Files.writeString(created, String.valueOf(now));
            reset = true;
        } catch (IOException | NumberFormatException e) {
            SkyRpgSkyblock.LOGGER.warn("Couldn't check the Crystal Hollows' age", e);
        }
    }
}
