package com.skyrpg.skyblock.galatea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * Hypixel's Heart of the Forest tree ({@code skyrpg/hotf/tree.json}, from {@code tools/skyblock/import_hotf.py}): 36
 * perks on a 7 × 8 grid (row 7 is tier 1, row 0 tier 8), each with its Whisper costs per upgrade and the numbers its
 * lore shows at each level; and the HotF XP for each tier.
 */
public final class HotfTree {
    public static final int COLUMNS = 7;
    public static final int ROWS = 8;

    /** A lore line, shown from perk level {@code from} on (0: always). */
    public record Line(String text, int from) {
    }

    public record Perk(String id, String name, int x, int y, int max, List<String> powder, int[] cost, Map<String, List<String>> values,
            List<Line> lore) {
        /** The HotF tier the perk is on (1 at the bottom). */
        public int tier() {
            return ROWS - y;
        }

        /** Unlocked with a token and never upgraded with powder (abilities and one-level perks). */
        public boolean single() {
            return max <= 1;
        }

        /** An axe ability (Damage Boost, Axe Toss, Maniac Slicer): one of them is selected at a time. */
        public boolean ability() {
            return lore.stream().anyMatch(line -> line.text().contains("Axe Ability:"));
        }

        /** The powder to go from {@code level} to {@code level + 1}. */
        public int costAt(int level) {
            return level >= 1 && level - 1 < cost.length ? cost[level - 1] : 0;
        }

        /** The powder type that upgrade costs. */
        public String powderAt(int level) {
            if (powder.isEmpty()) {
                return Hotf.FOREST;
            }
            return powder.size() == 1 ? powder.getFirst() : powder.get(Math.min(powder.size() - 1, Math.max(0, level - 1)));
        }

        /** A number from the lore at a level ("stat", "fortune"...). */
        public String value(String key, int level) {
            List<String> list = values.get(key);
            if (list == null || list.isEmpty()) {
                return "";
            }
            return list.get(Math.min(list.size() - 1, Math.max(0, level - 1)));
        }

        /** {@link #value} as a number (0 when it isn't one). */
        public double number(String key, int level) {
            try {
                return Double.parseDouble(value(key, level));
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        /** The lore at a level, numbers filled in. */
        public List<String> loreAt(int level) {
            List<String> out = new ArrayList<>();
            for (Line line : lore) {
                if (line.from() > level) {
                    continue;
                }
                String text = line.text();
                for (String key : values.keySet()) {
                    text = text.replace("{" + key + "}", value(key, Math.max(1, level)));
                }
                out.add(text);
            }
            return out;
        }
    }

    private static Map<String, Perk> perks = Map.of();
    private static long[] levels = {0};

    private HotfTree() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("hotf"), "skyrpg/hotf", HotfTree::load);
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, Perk> loaded = new LinkedHashMap<>();
        long[] xp = {0};
        for (JsonElement file : files.values()) {
            JsonObject json = file.getAsJsonObject();
            if (json.has("levels")) {
                JsonArray array = json.getAsJsonArray("levels");
                xp = new long[array.size()];
                for (int i = 0; i < array.size(); i++) {
                    xp[i] = array.get(i).getAsLong();
                }
            }
            if (!json.has("perks")) {
                continue;
            }
            for (JsonElement element : json.getAsJsonArray("perks")) {
                JsonObject p = element.getAsJsonObject();
                List<String> powder = new ArrayList<>();
                p.getAsJsonArray("powder").forEach(e -> powder.add(e.getAsString()));
                JsonArray costs = p.getAsJsonArray("cost");
                int[] cost = new int[costs.size()];
                for (int i = 0; i < cost.length; i++) {
                    cost[i] = costs.get(i).getAsInt();
                }
                List<Line> lore = new ArrayList<>();
                for (JsonElement line : p.getAsJsonArray("lore")) {
                    if (line.isJsonObject()) {
                        lore.add(new Line(line.getAsJsonObject().get("text").getAsString(), line.getAsJsonObject().get("from").getAsInt()));
                    } else {
                        lore.add(new Line(line.getAsString(), 0));
                    }
                }
                String id = p.get("id").getAsString();
                loaded.put(id, new Perk(id, p.get("name").getAsString(), p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("max").getAsInt(),
                        List.copyOf(powder), cost, values(p.getAsJsonObject("values")), List.copyOf(lore)));
            }
        }
        perks = loaded;
        levels = xp;
        SkyRpgSkyblock.LOGGER.info("Loaded {} Heart of the Forest perks", loaded.size());
    }

    private static Map<String, List<String>> values(JsonObject json) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        if (json == null) {
            return out;
        }
        for (String key : json.keySet()) {
            List<String> list = new ArrayList<>();
            json.getAsJsonArray(key).forEach(e -> list.add(e.getAsJsonPrimitive().getAsString()));
            out.put(key, List.copyOf(list));
        }
        return out;
    }

    public static Perk get(String id) {
        return perks.get(id);
    }

    public static List<Perk> all() {
        return List.copyOf(perks.values());
    }

    /** The perk at a grid spot, or null. */
    public static Perk at(int x, int y) {
        for (Perk perk : perks.values()) {
            if (perk.x() == x && perk.y() == y) {
                return perk;
            }
        }
        return null;
    }

    /** The highest HotF tier. */
    public static int maxLevel() {
        return levels.length;
    }

    /** The XP from one level to the next: {@code xpFor(1)} is level 1 to 2. */
    public static long xpFor(int level) {
        return level >= 1 && level < levels.length ? levels[level] : 0;
    }

    /** Total HotF XP to reach a tier (tier 1 at 0). */
    public static long totalFor(int level) {
        long total = 0;
        for (int i = 1; i < Math.min(level, levels.length); i++) {
            total += levels[i];
        }
        return total;
    }
}
