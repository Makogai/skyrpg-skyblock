package com.skyrpg.items.gem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.Rarity;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyRpgItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's gemstones on items ({@code skyrpg/gemstones}, from {@code tools/skyblock/import_gemstones.py}): which slots
 * each item has and what unlocking them costs, the stat each gemstone gives by its tier and the item's rarity, and which
 * gemstones a category slot (Combat, Mining, Universal...) takes. An item's gems are Hypixel's custom data:
 * {@code gems: {AMBER_0: "FINE", COMBAT_0: "PERFECT", COMBAT_0_gem: "JASPER", unlocked_slots: ["COMBAT_0"]}}. Loaded on
 * the server, sent to clients with the item definitions.
 */
public final class Gemstones {
    public static final String DIRECTORY = "skyrpg/gemstones";
    public static final String GEMS = "gems";
    public static final List<String> TIERS = List.of("ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT");

    /** One slot an item has: its id ("JADE_0", "COMBAT_0"), the gemstones it takes, and its unlock cost (free if none). */
    public record Slot(String id, List<String> accepts, long coins, List<String> items) {
        /** "JADE" or "COMBAT". */
        public String type() {
            return id.substring(0, id.lastIndexOf('_'));
        }

        public boolean free() {
            return coins <= 0 && items.isEmpty();
        }
    }

    /** A gemstone in a slot: which gem and its tier. */
    public record Gem(String slot, String type, String tier) {
    }

    private static Map<String, StatType> stats = Map.of();
    /** Gem -> tier -> rarity -> value. */
    private static Map<String, Map<String, Map<String, Double>>> values = Map.of();
    private static Map<String, List<String>> categories = Map.of();
    private static Map<String, Long> removal = Map.of();
    private static Map<String, List<Slot>> slots = Map.of();
    private static JsonObject raw = new JsonObject();

    private Gemstones() {
    }

    public static void load(Map<Identifier, JsonElement> files) {
        Map<String, StatType> loadedStats = new LinkedHashMap<>();
        Map<String, Map<String, Map<String, Double>>> loadedValues = new LinkedHashMap<>();
        Map<String, List<String>> loadedCategories = new LinkedHashMap<>();
        Map<String, Long> loadedRemoval = new LinkedHashMap<>();
        Map<String, List<Slot>> loadedSlots = new LinkedHashMap<>();
        JsonObject last = new JsonObject();
        for (JsonElement file : files.values()) {
            JsonObject json = file.getAsJsonObject();
            last = json;
            json.getAsJsonObject("types").entrySet().forEach(entry -> {
                JsonObject type = entry.getValue().getAsJsonObject();
                StatType stat = StatTypes.get(type.get("stat").getAsString());
                if (stat != null) {
                    loadedStats.put(entry.getKey(), stat);
                }
                Map<String, Map<String, Double>> byTier = new LinkedHashMap<>();
                type.getAsJsonObject("values").entrySet().forEach(tier -> {
                    Map<String, Double> byRarity = new LinkedHashMap<>();
                    tier.getValue().getAsJsonObject().entrySet().forEach(r -> byRarity.put(r.getKey(), r.getValue().getAsDouble()));
                    byTier.put(tier.getKey(), byRarity);
                });
                loadedValues.put(entry.getKey(), byTier);
            });
            json.getAsJsonObject("categories").entrySet().forEach(entry -> {
                List<String> gems = new ArrayList<>();
                entry.getValue().getAsJsonArray().forEach(e -> gems.add(e.getAsString()));
                loadedCategories.put(entry.getKey(), List.copyOf(gems));
            });
            json.getAsJsonObject("removal").entrySet().forEach(entry -> loadedRemoval.put(entry.getKey(), entry.getValue().getAsLong()));
            json.getAsJsonObject("slots").entrySet().forEach(entry -> {
                List<Slot> list = new ArrayList<>();
                entry.getValue().getAsJsonObject().entrySet().forEach(slot -> {
                    JsonObject cost = slot.getValue().getAsJsonObject();
                    List<String> items = new ArrayList<>();
                    cost.getAsJsonArray("items").forEach(e -> items.add(e.getAsString()));
                    String type = slot.getKey().substring(0, slot.getKey().lastIndexOf('_'));
                    List<String> accepts = loadedCategories.getOrDefault(type, List.of(type));
                    list.add(new Slot(slot.getKey(), accepts, cost.get("coins").getAsLong(), List.copyOf(items)));
                });
                loadedSlots.put(entry.getKey(), List.copyOf(list));
            });
        }
        stats = loadedStats;
        values = loadedValues;
        categories = loadedCategories;
        removal = loadedRemoval;
        slots = loadedSlots;
        raw = last;
        SkyRpgItems.LOGGER.info("Loaded gemstones: {} types, {} items with slots", loadedStats.size(), loadedSlots.size());
    }

    /** The data as one file, for the client. */
    public static JsonObject toJson() {
        JsonObject out = new JsonObject();
        out.add(SkyRpgItems.id("gemstones").toString(), raw);
        return out;
    }

    /** An item's gemstone slots, in Hypixel's order (none for most items). */
    public static List<Slot> slots(ItemDefinition def) {
        return def == null || def.skyblockId() == null ? List.of() : slots.getOrDefault(def.skyblockId(), List.of());
    }

    public static StatType stat(String gem) {
        return stats.get(gem);
    }

    /** What a gem gives on an item of this rarity. */
    public static double value(String gem, String tier, Rarity rarity) {
        Map<String, Double> byRarity = values.getOrDefault(gem, Map.of()).getOrDefault(tier, Map.of());
        String key = rarity.name();
        if (byRarity.containsKey(key)) {
            return byRarity.get(key);
        }
        // Rarer than the table goes (Special...): its highest column.
        return byRarity.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    public static long removalCost(String tier) {
        return removal.getOrDefault(tier, 0L);
    }

    /** "ROUGH_RUBY_GEM" -> {RUBY, ROUGH}, or null if it isn't a gemstone. */
    public static String[] parseGem(String skyblockId) {
        if (skyblockId == null || !skyblockId.endsWith("_GEM")) {
            return null;
        }
        String body = skyblockId.substring(0, skyblockId.length() - 4);
        int cut = body.indexOf('_');
        if (cut < 0) {
            return null;
        }
        String tier = body.substring(0, cut);
        String gem = body.substring(cut + 1);
        return TIERS.contains(tier) && stats.containsKey(gem) ? new String[] {gem, tier} : null;
    }

    private static CompoundTag gemsTag(ItemStack stack) {
        CompoundTag tag = ItemMods.tag(stack);
        return tag == null || !(tag.get(GEMS) instanceof CompoundTag gems) ? null : gems;
    }

    /** The gems in an item's slots. */
    public static List<Gem> gems(ItemStack stack) {
        CompoundTag gems = gemsTag(stack);
        if (gems == null) {
            return List.of();
        }
        List<Gem> out = new ArrayList<>();
        for (String key : gems.keySet()) {
            if (key.equals("unlocked_slots") || key.endsWith("_gem")) {
                continue;
            }
            String tier = gems.getStringOr(key, "");
            if (!TIERS.contains(tier)) {
                continue;
            }
            String type = key.substring(0, Math.max(0, key.lastIndexOf('_')));
            String gem = categories.containsKey(type) ? gems.getStringOr(key + "_gem", "") : type;
            if (stats.containsKey(gem)) {
                out.add(new Gem(key, gem, tier));
            }
        }
        return out;
    }

    /** Whether a slot is open on this item (free slots always are). */
    public static boolean unlocked(ItemStack stack, Slot slot) {
        if (slot.free()) {
            return true;
        }
        CompoundTag gems = gemsTag(stack);
        if (gems == null || !(gems.get("unlocked_slots") instanceof ListTag list)) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            if (slot.id().equals(list.getStringOr(i, ""))) {
                return true;
            }
        }
        return false;
    }

    public static void unlock(ItemStack stack, Slot slot) {
        ItemMods.edit(stack, tag -> {
            CompoundTag gems = tag.getCompoundOrEmpty(GEMS);
            ListTag list = gems.getListOrEmpty("unlocked_slots");
            list.add(StringTag.valueOf(slot.id()));
            gems.put("unlocked_slots", list);
            tag.put(GEMS, gems);
        });
    }

    /** Puts a gem in a slot (or takes it out when {@code gem} is null). */
    public static void set(ItemStack stack, Slot slot, String gem, String tier) {
        ItemMods.edit(stack, tag -> {
            CompoundTag gems = tag.getCompoundOrEmpty(GEMS);
            if (gem == null) {
                gems.remove(slot.id());
                gems.remove(slot.id() + "_gem");
            } else {
                gems.putString(slot.id(), tier);
                if (categories.containsKey(slot.type())) {
                    gems.putString(slot.id() + "_gem", gem);
                }
            }
            tag.put(GEMS, gems);
        });
        SkyItems.normalize(stack);
    }

    /** What the gems add, at the item's rarity. */
    public static Map<StatType, Double> stats(ItemStack stack, Rarity rarity) {
        List<Gem> gems = gems(stack);
        if (gems.isEmpty()) {
            return Map.of();
        }
        Map<StatType, Double> out = new LinkedHashMap<>();
        for (Gem gem : gems) {
            StatType stat = stats.get(gem.type());
            if (stat != null) {
                out.merge(stat, value(gem.type(), gem.tier(), rarity), Double::sum);
            }
        }
        return out;
    }

    /** The gem's display name ("Fine Ruby Gemstone"). */
    public static String name(String gem, String tier) {
        return cap(tier) + " " + cap(gem) + " Gemstone";
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
