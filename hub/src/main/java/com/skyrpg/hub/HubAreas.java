package com.skyrpg.hub;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The Hub's other areas (Hypixel's Graveyard, Coal Mine, Farm, Forest...), each its own build next to the village's:
 * a structure, where it goes and a version. {@code data/<ns>/skyrpg/hub_areas/<id>.json}:
 * <pre>
 * {"structure": "skyrpg_hub:areas/graveyard", "origin": [-260, 16, -110], "version": 1}
 * </pre>
 * On start, every area whose version is newer than the one this world has is cleared and placed (areas are generated
 * by tools/builds/hub_areas/; {@code /hubadmin area <id> place} re-places one after a /reload, {@code save} saves it
 * from the world after hand edits).
 */
public final class HubAreas {
    public static final String DIRECTORY = "skyrpg/hub_areas";
    /** Which version of each area this world has. Stored on the hub level. */
    static final AttachmentType<Map<String, Integer>> PLACED = AttachmentRegistry.create(SkyRpgHub.id("placed_areas"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));

    public record Area(String id, Identifier structure, BlockPos origin, int version) {
    }

    private static Map<String, Area> areas = Map.of();

    private HubAreas() {
    }

    static void load(Map<Identifier, JsonElement> files) {
        Map<String, Area> loaded = new LinkedHashMap<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                JsonArray origin = json.getAsJsonArray("origin");
                loaded.put(id.getPath(), new Area(id.getPath(), Identifier.parse(json.get("structure").getAsString()),
                        new BlockPos(origin.get(0).getAsInt(), origin.get(1).getAsInt(), origin.get(2).getAsInt()),
                        json.has("version") ? json.get("version").getAsInt() : 1));
            } catch (RuntimeException e) {
                SkyRpgHub.LOGGER.warn("Skipping hub area {}: {}", id, e.getMessage());
            }
        });
        areas = Map.copyOf(loaded);
    }

    public static Map<String, Area> all() {
        return areas;
    }

    /** On start: place every area this world doesn't have yet, or has an older version of. */
    static void onServerStarted(MinecraftServer server) {
        ServerLevel hub = Hub.level(server);
        if (hub == null) {
            return;
        }
        Map<String, Integer> placed = new HashMap<>(hub.getAttachedOrElse(PLACED, Map.of()));
        boolean changed = false;
        for (Area area : areas.values()) {
            int has = placed.getOrDefault(area.id(), 0);
            if (has < area.version() && place(server, hub, area, has > 0)) {
                placed.put(area.id(), area.version());
                changed = true;
                SkyRpgHub.LOGGER.info("Placed hub area {} version {}", area.id(), area.version());
            }
        }
        if (changed) {
            hub.setAttached(PLACED, Map.copyOf(placed));
        }
    }

    /** Places an area's build (clearing its box first when it replaces an older one). False if it has no build yet. */
    static boolean place(MinecraftServer server, ServerLevel hub, Area area, boolean clearFirst) {
        Optional<StructureTemplate> template = server.getStructureTemplateManager().get(area.structure());
        if (template.isEmpty()) {
            SkyRpgHub.LOGGER.warn("No build {} for hub area {}", area.structure(), area.id());
            return false;
        }
        if (clearFirst) {
            clear(hub, area.origin(), template.get().getSize());
        }
        template.get().placeInWorld(hub, area.origin(), area.origin(), new StructurePlaceSettings(), hub.getRandom(), Block.UPDATE_CLIENTS);
        return true;
    }

    /** {@code /hubadmin area <id> place}: clears and places it now (after a /reload picked up a new build). */
    static boolean replace(MinecraftServer server, ServerLevel hub, Area area) {
        // Forget the cached template so the file on disk is read again.
        server.getStructureTemplateManager().remove(area.structure());
        if (!place(server, hub, area, true)) {
            return false;
        }
        Map<String, Integer> placed = new HashMap<>(hub.getAttachedOrElse(PLACED, Map.of()));
        placed.put(area.id(), area.version());
        hub.setAttached(PLACED, Map.copyOf(placed));
        SkyRpgHub.LOGGER.info("Placed hub area {} version {}", area.id(), area.version());
        return true;
    }

    /** {@code /hubadmin area <id> save}: the area's box from the world into the world's generated/ folder. */
    static Path save(MinecraftServer server, ServerLevel hub, Area area) {
        StructureTemplate template = server.getStructureTemplateManager().getOrCreate(area.structure());
        Vec3i size = template.getSize();
        template.fillFromWorld(hub, area.origin(), size, false, List.of(Blocks.AIR));
        server.getStructureTemplateManager().save(area.structure());
        return server.getWorldPath(LevelResource.GENERATED_DIR).resolve(area.structure().getNamespace()).resolve("structure")
                .resolve(area.structure().getPath() + ".nbt");
    }

    /** Empties an area's box, except where it reaches into the village's (bridges start on its islets). */
    private static void clear(ServerLevel hub, BlockPos min, Vec3i size) {
        BlockPos max = min.offset(size).offset(-1, -1, -1);
        HubConfig config = HubConfig.get();
        BlockPos villageMin = config.buildOrigin();
        BlockPos villageMax = villageMin.offset(config.buildSize()).offset(-1, -1, -1);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            boolean village = pos.getX() >= villageMin.getX() && pos.getX() <= villageMax.getX() && pos.getY() >= villageMin.getY()
                    && pos.getY() <= villageMax.getY() && pos.getZ() >= villageMin.getZ() && pos.getZ() <= villageMax.getZ();
            if (!village && !hub.getBlockState(pos).isAir()) {
                hub.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
    }
}
