package com.skyrpg.skyblock.accessory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.resources.Identifier;

/**
 * Hypixel's Accessory Powers (Maxwell: {@code skyrpg/accessory_powers/*.json}, from tools/skyblock/import_powers.py) and
 * the accessory families ({@code skyrpg/accessory_families/families.json}: a lower accessory stops counting once you
 * have a higher one of its line, like the Speed Talisman under the Speed Ring).
 */
public final class AccessoryPowers {
    /** A power: base stats (times the Magical Power multiplier), a unique bonus, and what it takes to have it. */
    public record Power(String id, String name, boolean starter, String stone, int combat, Map<StatType, Double> stats,
                        Map<StatType, Double> unique) {
    }

    private static Map<String, Power> powers = Map.of();
    /** Accessory id -> the higher ones of its line. */
    private static Map<String, Set<String>> higher = Map.of();
    private static Set<String> ignored = Set.of();

    private AccessoryPowers() {
    }

    public static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("accessory_powers"), "skyrpg/accessory_powers", AccessoryPowers::loadPowers);
        JsonDataLoader.register(SkyRpgSkyblock.id("accessory_families"), "skyrpg/accessory_families", AccessoryPowers::loadFamilies);
    }

    public static Power get(String id) {
        return id == null ? null : powers.get(id);
    }

    /** Every power, by name. */
    public static List<Power> all() {
        return List.copyOf(powers.values());
    }

    /** The power a Power Stone teaches, or null. */
    public static Power byStone(String skyblockId) {
        for (Power power : powers.values()) {
            if (power.stone() != null && power.stone().equals(skyblockId)) {
                return power;
            }
        }
        return null;
    }

    /** Whether owning {@code other} makes {@code accessory} stop counting (it's a higher one of the same line). */
    public static boolean outranks(String other, String accessory) {
        return higher.getOrDefault(accessory, Set.of()).contains(other);
    }

    /** Accessories Hypixel never counts (event and joke ones). */
    public static boolean ignored(String accessory) {
        return ignored.contains(accessory);
    }

    /** Hypixel's multiplier on a power's stats: 29.97 × ln(0.0019 × MP + 1)^1.2. */
    public static double multiplier(int magicalPower) {
        return magicalPower <= 0 ? 0 : 29.97 * Math.pow(Math.log(0.0019 * magicalPower + 1), 1.2);
    }

    private static void loadPowers(Map<Identifier, JsonElement> files) {
        Map<String, Power> loaded = new TreeMap<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                loaded.put(id.getPath(), new Power(id.getPath(), json.get("name").getAsString(),
                        json.has("starter") && json.get("starter").getAsBoolean(), json.has("stone") ? json.get("stone").getAsString() : null,
                        json.has("combat") ? json.get("combat").getAsInt() : 0, stats(json.getAsJsonObject("stats")),
                        stats(json.getAsJsonObject("unique"))));
            } catch (RuntimeException e) {
                SkyRpgSkyblock.LOGGER.warn("Skipping accessory power {}: {}", id, e.getMessage());
            }
        });
        powers = loaded;
        SkyRpgSkyblock.LOGGER.info("Loaded {} accessory powers", loaded.size());
    }

    private static void loadFamilies(Map<Identifier, JsonElement> files) {
        Map<String, Set<String>> up = new HashMap<>();
        Set<String> skip = new HashSet<>();
        files.values().forEach(element -> {
            JsonObject json = element.getAsJsonObject();
            if (json.has("upgrades")) {
                json.getAsJsonObject("upgrades").entrySet().forEach(entry -> {
                    Set<String> above = up.computeIfAbsent(entry.getKey(), k -> new HashSet<>());
                    entry.getValue().getAsJsonArray().forEach(e -> above.add(e.getAsString()));
                });
            }
            if (json.has("ignored")) {
                json.getAsJsonArray("ignored").forEach(e -> skip.add(e.getAsString()));
            }
        });
        higher = up;
        ignored = skip;
    }

    private static Map<StatType, Double> stats(JsonObject json) {
        if (json == null) {
            return Map.of();
        }
        Map<StatType, Double> out = new LinkedHashMap<>();
        json.entrySet().forEach(entry -> {
            StatType stat = StatTypes.get(entry.getKey());
            if (stat != null) {
                out.put(stat, entry.getValue().getAsDouble());
            }
        });
        return Map.copyOf(out);
    }

    /** The powers whose stone the player could hand Maxwell, as their ids. */
    static List<String> stoneIds() {
        List<String> out = new ArrayList<>();
        powers.values().forEach(p -> {
            if (p.stone() != null) {
                out.add(p.stone());
            }
        });
        return out;
    }
}
