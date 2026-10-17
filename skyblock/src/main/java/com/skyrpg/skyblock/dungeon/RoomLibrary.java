package com.skyrpg.skyblock.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;

/**
 * The room templates ({@code structure/catacombs/rooms/<category>/<name>.nbt}, built by {@code tools/builds/catacombs}),
 * by category: a normal room's shape (one, two, three, four, square, l), or special/entrance, special/blood,
 * special/fairy, puzzle, trap. Read from the structure manager when the server starts.
 */
final class RoomLibrary {
    private static final String PREFIX = "catacombs/rooms/";
    private static Map<String, List<Identifier>> rooms = Map.of();

    private RoomLibrary() {
    }

    static void load(MinecraftServer server) {
        Map<String, List<Identifier>> found = new HashMap<>();
        server.getStructureTemplateManager().listTemplates()
                .filter(id -> id.getNamespace().equals("skyrpg_skyblock") && id.getPath().startsWith(PREFIX))
                .forEach(id -> {
                    String rest = id.getPath().substring(PREFIX.length());
                    String category = rest.startsWith("special/") ? rest : rest.substring(0, Math.max(0, rest.indexOf('/')));
                    if (!category.isEmpty() && !category.equals("test")) {
                        found.computeIfAbsent(category, k -> new ArrayList<>()).add(id);
                    }
                });
        rooms = Map.copyOf(found);
        com.skyrpg.skyblock.SkyRpgSkyblock.LOGGER.info("Catacombs rooms: {}", rooms.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue().size()).sorted().toList());
    }

    /** Which category a layout room takes its template from. */
    static String category(Layout.Room room) {
        return switch (room.type()) {
            case ENTRANCE -> "special/entrance";
            case BLOOD -> "special/blood";
            case FAIRY -> "special/fairy";
            case PUZZLE -> rooms.containsKey("puzzle") ? "puzzle" : "one";
            case TRAP -> rooms.containsKey("trap") ? "trap" : "one";
            default -> switch (room.shape()) {
                case TWO -> "two";
                case THREE -> "three";
                case FOUR -> "four";
                case SQUARE -> "square";
                case L -> "l";
                default -> "one";
            };
        };
    }

    /** A random template of a category, or null if there's none. */
    static Identifier pick(String category, RandomSource random) {
        List<Identifier> list = rooms.get(category);
        return list == null || list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    static int count(String category) {
        List<Identifier> list = rooms.get(category);
        return list == null ? 0 : list.size();
    }
}
