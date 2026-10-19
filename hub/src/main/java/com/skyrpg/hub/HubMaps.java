package com.skyrpg.hub;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Islands that run on Hypixel's own map: a world download installed into the dimension's folder with
 * {@code tools/worlds/hypixel_map.py install}, which leaves a {@code hypixel_map.json} there. Hypixel's builds are
 * theirs, so the maps never ship with the mod or go in the repo; they're installed into each world by hand. On such
 * an island our own builds (the hub build, its areas, the decor) aren't placed, and the hub takes its "official"
 * settings (spawn, podiums) from hub.json.
 */
public final class HubMaps {
    public static final String MARKER = "hypixel_map.json";
    private static final Set<ResourceKey<Level>> OFFICIAL = new HashSet<>();

    private HubMaps() {
    }

    static void init() {
        // Before SERVER_STARTED, where the builds are placed.
        ServerLifecycleEvents.SERVER_STARTING.register(HubMaps::scan);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> OFFICIAL.clear());
    }

    /** The levels don't exist yet when the server starts: look for markers in the world's dimensions folder instead. */
    private static void scan(MinecraftServer server) {
        OFFICIAL.clear();
        Path dimensions = server.getWorldPath(LevelResource.ROOT).resolve("dimensions");
        if (!Files.isDirectory(dimensions)) {
            return;
        }
        try (var namespaces = Files.list(dimensions)) {
            for (Path namespace : namespaces.toList()) {
                if (!Files.isDirectory(namespace)) {
                    continue;
                }
                try (var paths = Files.list(namespace)) {
                    for (Path path : paths.toList()) {
                        if (Files.exists(path.resolve(MARKER))) {
                            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                                    Identifier.fromNamespaceAndPath(namespace.getFileName().toString(), path.getFileName().toString()));
                            OFFICIAL.add(key);
                            SkyRpgHub.LOGGER.info("{} runs on Hypixel's map", key.identifier());
                        }
                    }
                }
            }
        } catch (IOException e) {
            SkyRpgHub.LOGGER.error("Couldn't look for Hypixel's maps: {}", e.getMessage());
        }
    }

    /** Whether this island has Hypixel's map installed. */
    public static boolean official(ResourceKey<Level> dimension) {
        return OFFICIAL.contains(dimension);
    }
}
