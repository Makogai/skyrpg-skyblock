package com.skyrpg.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * What one registered item does, loaded from {@code data/<namespace>/skyrpg/items/<path>.json}. The file's
 * id is the item's id ({@code skyrpg_items:harvester_helmet}), and the item itself ({@link ModItems})
 * decides what it is: armour slot, tool, look.
 * <pre>
 * {
 *   "name": "Harvester Hat",
 *   "rarity": "uncommon",
 *   "stats": {"health": 15, "farming_fortune": 10},
 *   "set": "skyrpg_items:harvester",             // optional
 *   "description": "...",                        // optional
 *   "glint": true,                               // optional
 *   "ability": {"type": "mend", "name": "Mend", "mana": 60, "cooldown": 5,
 *               "description": "...", "params": {"amount": 40}},  // optional
 *   "effects": {"skyrpg_farming:reseed": 1},     // optional: gear effects while held or worn (see GearEffects)
 *   "perks": ["Replants what you harvest"]       // optional: lines the tooltip shows for those effects
 * }
 * </pre>
 * Stats are keyed by {@link StatType} id. "damage" is weapon damage and sets the item's attack damage.
 * <p>
 * A SkyBlock item isn't a registered item: it's a vanilla {@code "base"} item carrying its SkyBlock id in
 * {@code custom_data} ({@code {id: "ASPECT_OF_THE_END"}}), like on Hypixel, which is also what texture packs
 * such as FurfSky key their looks on. Its file is {@code data/skyrpg_skyblock/skyrpg/items/aspect_of_the_end.json}:
 * <pre>
 * {"skyblock_id": "ASPECT_OF_THE_END", "base": "minecraft:diamond_sword", "name": "Aspect of the End", ...}
 * </pre>
 * Heads add {@code "skin": {"value": ..., "signature": ...}}, leather {@code "color": "#RRGGBB"}.
 *
 * @param skyblockId the SkyBlock id (null for a registered SkyRPG item); {@code item} is then its base item
 * @param json the definition as loaded, so it can be sent to clients unchanged
 */
public record ItemDefinition(Identifier id, String skyblockId, Item item, String name, Rarity rarity, Map<StatType, Double> stats,
                             Identifier set, String description, boolean glint, Ability ability, Map<Identifier, Double> effects,
                             List<String> perks, JsonObject json) {

    /** An item's right-click ability. {@code params} holds type-specific settings like "distance". */
    /** An item's ability; {@code trigger} is how it's cast, as Hypixel words it: RIGHT CLICK, LEFT CLICK, SNEAK, HOLD RIGHT CLICK. */
    public record Ability(String type, String name, double mana, double cooldownSeconds, String description, JsonObject params, String trigger) {
        public double param(String key, double fallback) {
            return params.has(key) ? params.get(key).getAsDouble() : fallback;
        }

        /** Cast by right-clicking (also when held down). */
        public boolean rightClick() {
            return trigger.contains("RIGHT");
        }
    }

    public double stat(StatType stat) {
        return stats.getOrDefault(stat, 0.0);
    }

    /** Whether this is a SkyBlock item (a vanilla base item with a SkyBlock id) rather than a registered one. */
    public boolean skyblock() {
        return skyblockId != null;
    }

    public static ItemDefinition parse(Identifier id, JsonObject json) {
        Identifier itemId = id;
        String skyblockId = null;
        if (json.has("base")) {
            itemId = Identifier.parse(json.get("base").getAsString());
            skyblockId = json.has("skyblock_id") ? json.get("skyblock_id").getAsString() : id.getPath().toUpperCase(java.util.Locale.ROOT);
        }
        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            throw new IllegalArgumentException("no registered item " + itemId);
        }
        Map<StatType, Double> stats = new LinkedHashMap<>();
        if (json.has("stats")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("stats").entrySet()) {
                StatType stat = StatTypes.get(entry.getKey());
                if (stat == null) {
                    throw new IllegalArgumentException("unknown stat " + entry.getKey());
                }
                stats.put(stat, entry.getValue().getAsDouble());
            }
        }
        Ability ability = null;
        if (json.has("ability")) {
            JsonObject a = json.getAsJsonObject("ability");
            ability = new Ability(a.get("type").getAsString(), a.get("name").getAsString(),
                    a.has("mana") ? a.get("mana").getAsDouble() : 0,
                    a.has("cooldown") ? a.get("cooldown").getAsDouble() : 0,
                    a.has("description") ? a.get("description").getAsString() : "",
                    a.has("params") ? a.getAsJsonObject("params") : new JsonObject(),
                    a.has("trigger") ? a.get("trigger").getAsString() : "RIGHT CLICK");
        }
        List<String> perks = new ArrayList<>();
        if (json.has("perks")) {
            json.getAsJsonArray("perks").forEach(line -> perks.add(line.getAsString()));
        }
        return new ItemDefinition(id, skyblockId, BuiltInRegistries.ITEM.getValue(itemId), json.get("name").getAsString(),
                json.has("rarity") ? Rarity.parse(json.get("rarity").getAsString()) : Rarity.COMMON,
                stats,
                json.has("set") ? Identifier.parse(json.get("set").getAsString()) : null,
                json.has("description") ? json.get("description").getAsString() : "",
                json.has("glint") && json.get("glint").getAsBoolean(),
                ability, effects(json.getAsJsonObject("effects")), List.copyOf(perks), json);
    }

    /** {@code {"skyrpg_farming:reseed": 1}} as a map (empty if absent). */
    static Map<Identifier, Double> effects(JsonObject json) {
        Map<Identifier, Double> effects = new LinkedHashMap<>();
        if (json != null) {
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                effects.put(Identifier.parse(entry.getKey()), entry.getValue().getAsDouble());
            }
        }
        return Map.copyOf(effects);
    }
}
