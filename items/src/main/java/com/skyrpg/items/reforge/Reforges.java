package com.skyrpg.items.reforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.Rarity;
import com.skyrpg.items.SkyRpgItems;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Hypixel's reforges ({@code skyrpg/reforges/*.json}, from tools/skyblock/import_reforges.py): a name prefix with stats
 * by rarity, for some item categories ("SWORD", "HELMET"...) or only some items. The Blacksmith rolls the ones without
 * a stone; the rest come from their reforge stone. Items keep the reforge's {@code modifier} in their custom data, as
 * on Hypixel. The client gets them with the item definitions.
 */
public final class Reforges {
    public static final String DIRECTORY = "skyrpg/reforges";

    /** A reforge. {@code stone} is the SkyBlock id of its reforge stone (null for the Blacksmith's own). */
    public record Reforge(String id, String name, String modifier, Set<String> types, Set<String> items, Set<Rarity> rarities,
                          Map<Rarity, Map<StatType, Double>> stats, String stone, Map<Rarity, Long> costs, String ability,
                          JsonObject json) {
        /** Whether this reforge can go on that item at that rarity. */
        public boolean fits(ItemDefinition def, Rarity rarity) {
            if (def == null || !def.skyblock() || !rarities.contains(rarity)) {
                return false;
            }
            if (!items.isEmpty()) {
                return items.contains(def.skyblockId());
            }
            String category = category(def);
            return category != null && types.contains(category);
        }

        /** Its stats on an item of that rarity (the nearest lower rarity it lists, for rarities past its table). */
        public Map<StatType, Double> statsAt(Rarity rarity) {
            for (int i = rarity.ordinal(); i >= 0; i--) {
                Map<StatType, Double> at = stats.get(Rarity.values()[i]);
                if (at != null) {
                    return at;
                }
            }
            return Map.of();
        }

        /** What applying it with its stone costs at that rarity (0 for a Blacksmith reforge). */
        public long costAt(Rarity rarity) {
            for (int i = rarity.ordinal(); i >= 0; i--) {
                Long at = costs.get(Rarity.values()[i]);
                if (at != null) {
                    return at;
                }
            }
            return 0;
        }
    }

    private static Map<String, Reforge> byModifier = Map.of();
    private static Map<String, Reforge> byStone = Map.of();

    private Reforges() {
    }

    /** The reforge an item's {@code modifier} names, or null. */
    public static Reforge byModifier(String modifier) {
        return modifier == null ? null : byModifier.get(modifier.toLowerCase(Locale.ROOT));
    }

    /** The reforge a reforge stone applies, or null when this isn't a reforge stone. */
    public static Reforge byStone(String skyblockId) {
        return skyblockId == null ? null : byStone.get(skyblockId);
    }

    public static Collection<Reforge> all() {
        return byModifier.values();
    }

    /** The Blacksmith's reforges that fit this item (what a random reforge picks from). */
    public static List<Reforge> blacksmith(ItemDefinition def, Rarity rarity) {
        List<Reforge> out = new ArrayList<>();
        for (Reforge reforge : byModifier.values()) {
            if (reforge.stone() == null && reforge.fits(def, rarity)) {
                out.add(reforge);
            }
        }
        return out;
    }

    /** Hypixel's item category ("SWORD", "HELMET"...) of a SkyBlock item, or null for one that can't be reforged. */
    public static String category(ItemDefinition def) {
        return def.json().has("category") ? def.json().get("category").getAsString() : null;
    }

    /** Loads the data files (server) or what the server sent (client). */
    public static void load(Map<Identifier, JsonElement> files) {
        Map<String, Reforge> loaded = new LinkedHashMap<>();
        Map<String, Reforge> stones = new LinkedHashMap<>();
        files.forEach((id, element) -> {
            try {
                Reforge reforge = parse(id.getPath(), element.getAsJsonObject());
                loaded.put(reforge.modifier(), reforge);
                if (reforge.stone() != null) {
                    stones.put(reforge.stone(), reforge);
                }
            } catch (RuntimeException e) {
                SkyRpgItems.LOGGER.warn("Skipping reforge {}: {}", id, e.getMessage());
            }
        });
        byModifier = loaded;
        byStone = stones;
        SkyRpgItems.LOGGER.info("Loaded {} reforges ({} from stones)", loaded.size(), stones.size());
    }

    /** Every reforge as its JSON, for the client. */
    public static JsonObject toJson() {
        JsonObject out = new JsonObject();
        byModifier.values().forEach(reforge -> out.add(SkyRpgItems.id(reforge.id()).toString(), reforge.json()));
        return out;
    }

    private static Reforge parse(String id, JsonObject json) {
        String name = json.get("name").getAsString();
        String modifier = json.has("modifier") ? json.get("modifier").getAsString() : name.toLowerCase(Locale.ROOT);
        Set<String> types = strings(json.getAsJsonArray("types"));
        Set<String> items = json.has("items") ? strings(json.getAsJsonArray("items")) : Set.of();
        Set<Rarity> rarities = new HashSet<>();
        for (String rarity : strings(json.getAsJsonArray("rarities"))) {
            Rarity parsed = rarity(rarity);
            if (parsed != null) {
                rarities.add(parsed);
            }
        }
        Map<Rarity, Map<StatType, Double>> stats = new EnumMap<>(Rarity.class);
        if (json.has("stats")) {
            for (Map.Entry<String, JsonElement> byRarity : json.getAsJsonObject("stats").entrySet()) {
                Rarity rarity = rarity(byRarity.getKey());
                if (rarity == null) {
                    continue;
                }
                Map<StatType, Double> at = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> stat : byRarity.getValue().getAsJsonObject().entrySet()) {
                    StatType type = StatTypes.get(stat.getKey());
                    if (type != null) {
                        at.put(type, stat.getValue().getAsDouble());
                    }
                }
                stats.put(rarity, Map.copyOf(at));
            }
        }
        Map<Rarity, Long> costs = new EnumMap<>(Rarity.class);
        if (json.has("costs")) {
            json.getAsJsonObject("costs").entrySet().forEach(cost -> {
                Rarity rarity = rarity(cost.getKey());
                if (rarity != null) {
                    costs.put(rarity, cost.getValue().getAsLong());
                }
            });
        }
        return new Reforge(id, name, modifier.toLowerCase(Locale.ROOT), types, items, rarities, stats,
                json.has("stone") ? json.get("stone").getAsString() : null, costs,
                json.has("ability") ? json.get("ability").getAsString() : "", json);
    }

    private static Set<String> strings(JsonArray array) {
        Set<String> out = new HashSet<>();
        if (array != null) {
            array.forEach(e -> out.add(e.getAsString()));
        }
        return Set.copyOf(out);
    }

    private static Rarity rarity(String name) {
        try {
            return Rarity.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
