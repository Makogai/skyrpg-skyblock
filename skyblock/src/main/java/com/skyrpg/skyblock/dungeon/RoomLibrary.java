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
 *
 * <p>Hypixel's own rooms ({@code catacombs/rooms/hypixel/<category>/}, in the world's generated folder from
 * {@code tools/worlds/hypixel_dungeons.py}, never committed) are taken first for any category that has some; they're
 * built 30 blocks deeper (their floor at y 38, for the basements under it), see {@link #HYPIXEL_DROP}.
 */
final class RoomLibrary {
    private static final String PREFIX = "catacombs/rooms/";
    private static Map<String, List<Identifier>> rooms = Map.of();
    private static Map<String, List<Identifier>> hypixel = Map.of();
    /** How much deeper a Hypixel room's template starts than ours (its floor is at y 38, ours at y 8). */
    static final int HYPIXEL_DROP = 30;

    private RoomLibrary() {
    }

    static void load(MinecraftServer server) {
        Map<String, List<Identifier>> found = new HashMap<>();
        Map<String, List<Identifier>> fromHypixel = new HashMap<>();
        server.getStructureTemplateManager().listTemplates()
                .filter(id -> id.getNamespace().equals("skyrpg_skyblock") && id.getPath().startsWith(PREFIX))
                .forEach(id -> {
                    String rest = id.getPath().substring(PREFIX.length());
                    boolean isHypixel = rest.startsWith("hypixel/");
                    if (isHypixel) {
                        rest = rest.substring("hypixel/".length());
                    }
                    String[] parts = rest.split("/");
                    // special/<kind> (ours: special/entrance; Hypixel's: special/entrance/<room>), else the first folder
                    String category = parts[0].equals("special") && parts.length > 1 ? "special/" + parts[1] : parts.length > 1 ? parts[0] : "";
                    if (!category.isEmpty() && !category.equals("test")) {
                        (isHypixel ? fromHypixel : found).computeIfAbsent(category, k -> new ArrayList<>()).add(id);
                    }
                });
        rooms = Map.copyOf(found);
        hypixel = Map.copyOf(fromHypixel);
        com.skyrpg.skyblock.SkyRpgSkyblock.LOGGER.info("Catacombs rooms: {}; Hypixel's: {}", rooms.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue().size()).sorted().toList(), hypixel.entrySet().stream()
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
        List<Identifier> list = hypixel.containsKey(category) ? hypixel.get(category) : rooms.get(category);
        return list == null || list.isEmpty() ? null : list.get(random.nextInt(list.size()));
    }

    static int count(String category) {
        List<Identifier> list = rooms.get(category);
        return (list == null ? 0 : list.size()) + hypixel.getOrDefault(category, List.of()).size();
    }

    /** Whether a template is one of Hypixel's (built {@link #HYPIXEL_DROP} deeper). */
    static boolean isHypixel(Identifier template) {
        return template.getPath().startsWith(PREFIX + "hypixel/");
    }
}
