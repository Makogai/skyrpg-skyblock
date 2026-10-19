package com.skyrpg.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.items.accessory.Accessories;
import com.skyrpg.items.accessory.AccessorySlot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's armour set bonuses, read from a SkyBlock item's {@code "set_bonuses"} (NEU's lore, see import_items.py):
 * {@code {"kind": "full", "name": "Young Blood", "pieces": 4, "description": "..."}}, kind {@code full} (Full Set
 * Bonus), {@code tiered} (Tiered Bonus) or {@code piece} (Piece Bonus). Pieces that share a bonus name are one set, so
 * a bonus counts every worn piece that carries it: armour in its slot, and the belt, necklace, cloak and gloves.
 * What a bonus does is the game's: {@code SetAbilities} in the SkyBlock mod asks {@link #count}.
 */
public final class SetBonuses {
    /** One bonus on an item; {@code key} is the name in snake case, what the game code asks for. */
    public record Bonus(String kind, String key, String name, int pieces, String description) {
        public String label() {
            return switch (kind) {
                case "tiered" -> "Tiered Bonus";
                case "piece" -> "Piece Bonus";
                default -> "Full Set Bonus";
            };
        }
    }

    private record Cached(JsonObject json, List<Bonus> bonuses) {
    }

    private static final Map<Identifier, Cached> CACHE = new ConcurrentHashMap<>();
    private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private SetBonuses() {
    }

    public static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    /** The bonuses an item carries (empty for most). */
    public static List<Bonus> of(ItemDefinition def) {
        if (def == null || !def.json().has("set_bonuses")) {
            return List.of();
        }
        Cached cached = CACHE.get(def.id());
        if (cached != null && cached.json() == def.json()) {
            return cached.bonuses();
        }
        List<Bonus> bonuses = new ArrayList<>();
        JsonArray array = def.json().getAsJsonArray("set_bonuses");
        for (JsonElement element : array) {
            JsonObject b = element.getAsJsonObject();
            String name = b.get("name").getAsString();
            bonuses.add(new Bonus(b.get("kind").getAsString(), key(name), name, b.has("pieces") ? b.get("pieces").getAsInt() : 0,
                    b.has("description") ? b.get("description").getAsString() : ""));
        }
        List<Bonus> frozen = List.copyOf(bonuses);
        CACHE.put(def.id(), new Cached(def.json(), frozen));
        return frozen;
    }

    /** How many worn pieces carry each bonus, by key (armour in its slot, accessories in theirs). */
    public static Map<String, Integer> worn(Player player) {
        Map<String, Integer> counts = new HashMap<>();
        for (EquipmentSlot slot : WORN) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemDefinition def = SkyItems.definition(stack);
            if (def != null && SkyItems.armorSlot(def.item()) == slot) {
                count(def, counts);
            }
        }
        for (AccessorySlot slot : AccessorySlot.values()) {
            ItemStack stack = Accessories.worn(player, slot);
            if (!stack.isEmpty()) {
                count(SkyItems.definition(stack), counts);
            }
        }
        return counts;
    }

    private static void count(ItemDefinition def, Map<String, Integer> counts) {
        for (Bonus bonus : of(def)) {
            counts.merge(bonus.key(), 1, Integer::sum);
        }
    }

    /** How many worn pieces carry this bonus. */
    public static int count(Player player, String key) {
        return worn(player).getOrDefault(key, 0);
    }

    /** Whether the full set (all the bonus's pieces) is worn. */
    public static boolean full(Player player, String key, int pieces) {
        return count(player, key) >= pieces;
    }
}
