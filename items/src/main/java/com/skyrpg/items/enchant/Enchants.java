package com.skyrpg.items.enchant;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyRpgItems;
import com.skyrpg.items.reforge.Reforges;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;

/**
 * Hypixel's enchantments ({@code skyrpg/skyblock_enchants/*.json}, from tools/skyblock/import_enchants.py): which item categories
 * take each, how high the Enchantment Table goes, the XP cost of every level, what conflicts with it, and each level's
 * lore and number (the "value" the effects use). Items keep them as Hypixel does, an {@code enchantments} compound of id
 * to level in their custom data. The client gets them with the item definitions.
 */
public final class Enchants {
    public static final String DIRECTORY = "skyrpg/skyblock_enchants";

    public record Level(List<String> lore, double value, String rarity) {
    }

    /** {@code tableMax} is 0 for enchants that only come as books. */
    public record Enchant(String id, String name, Set<String> categories, int tableMax, int max, int[] xp, Set<String> conflicts,
                          boolean ultimate, boolean combinable, Map<Integer, Level> levels, JsonObject json) {
        public boolean fits(ItemDefinition def) {
            String category = def == null ? null : Reforges.category(def);
            return category != null && categories.contains(category);
        }

        /** XP levels it costs to apply this level (at the table or from a book); 0 when unknown. */
        public int xpCost(int level) {
            return level >= 1 && level <= xp.length ? xp[level - 1] : 0;
        }

        public Level level(int level) {
            return levels.get(level);
        }

        /** The number in this level's lore ("Increases melee damage dealt by 30%" is 30), 0 if none. */
        public double value(int level) {
            Level at = levels.get(Math.min(level, max));
            return at == null ? 0 : at.value();
        }

        /** How high two equal books combine: the table's top level for table enchants, else the highest book. */
        public int combineMax() {
            return tableMax > 0 ? tableMax : max;
        }

        /** "Sharpness V". */
        public String label(int level) {
            return name + " " + roman(level);
        }
    }

    /** NEU's conflict pools spell two Turbo enchants differently from their ids. */
    private static final Map<String, String> ALIASES = Map.of("turbo_coco", "turbo_cocoa", "turbo_cactus", "turbo_cacti");

    private static Map<String, Enchant> byId = Map.of();

    private Enchants() {
    }

    public static Enchant get(String id) {
        return id == null ? null : byId.get(id);
    }

    public static Collection<Enchant> all() {
        return byId.values();
    }

    /** The enchants this item can take at the Enchantment Table, by name. */
    public static List<Enchant> table(ItemDefinition def) {
        List<Enchant> out = new ArrayList<>();
        for (Enchant enchant : byId.values()) {
            if (enchant.tableMax() > 0 && enchant.fits(def)) {
                out.add(enchant);
            }
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    public static void load(Map<Identifier, JsonElement> files) {
        Map<String, Enchant> loaded = new TreeMap<>();
        files.forEach((id, element) -> {
            try {
                loaded.put(id.getPath(), parse(id.getPath(), element.getAsJsonObject()));
            } catch (RuntimeException e) {
                SkyRpgItems.LOGGER.warn("Skipping enchant {}: {}", id, e.getMessage());
            }
        });
        byId = loaded;
        Books.clear();
        SkyRpgItems.LOGGER.info("Loaded {} enchantments", loaded.size());
    }

    public static JsonObject toJson() {
        JsonObject out = new JsonObject();
        byId.values().forEach(enchant -> out.add(SkyRpgItems.id(enchant.id()).toString(), enchant.json()));
        return out;
    }

    private static Enchant parse(String id, JsonObject json) {
        JsonArray xpArray = json.getAsJsonArray("xp");
        int[] xp = new int[xpArray == null ? 0 : xpArray.size()];
        for (int i = 0; i < xp.length; i++) {
            xp[i] = xpArray.get(i).getAsInt();
        }
        Map<Integer, Level> levels = new LinkedHashMap<>();
        json.getAsJsonObject("levels").entrySet().forEach(entry -> {
            JsonObject level = entry.getValue().getAsJsonObject();
            List<String> lore = new ArrayList<>();
            level.getAsJsonArray("lore").forEach(line -> lore.add(line.getAsString()));
            levels.put(Integer.parseInt(entry.getKey()), new Level(List.copyOf(lore),
                    level.has("value") ? level.get("value").getAsDouble() : 0, level.has("rarity") ? level.get("rarity").getAsString() : "COMMON"));
        });
        return new Enchant(id, json.get("name").getAsString(), strings(json.getAsJsonArray("categories")),
                json.has("table_max") ? json.get("table_max").getAsInt() : 0, json.get("max").getAsInt(), xp,
                strings(json.getAsJsonArray("conflicts")), json.has("ultimate") && json.get("ultimate").getAsBoolean(),
                !json.has("combinable") || json.get("combinable").getAsBoolean(), levels, json);
    }

    private static Set<String> strings(JsonArray array) {
        Set<String> out = new HashSet<>();
        if (array != null) {
            array.forEach(e -> out.add(ALIASES.getOrDefault(e.getAsString(), e.getAsString())));
        }
        return Set.copyOf(out);
    }

    public static String roman(int number) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return number >= 0 && number < numerals.length ? numerals[number] : Integer.toString(number);
    }
}
