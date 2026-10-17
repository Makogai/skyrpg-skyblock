package com.skyrpg.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/**
 * The item and set definitions currently loaded. On the server they come from data packs; on a
 * client they're what the server sent ({@link ItemDefinitionsPayload}), parsed by the same code.
 */
public final class ItemRegistry {
    public static final String ITEMS_DIRECTORY = "skyrpg/items";
    public static final String SETS_DIRECTORY = "skyrpg/item_sets";

    private static Map<Identifier, ItemDefinition> items = Map.of();
    private static Map<String, ItemDefinition> skyblock = Map.of();
    private static Map<Identifier, ItemSet> sets = Map.of();

    private ItemRegistry() {
    }

    public static ItemDefinition item(Identifier id) {
        return items.get(id);
    }

    /** The definition of this registered item, or null if it has none. */
    public static ItemDefinition item(Item item) {
        return items.get(BuiltInRegistries.ITEM.getKey(item));
    }

    /** The SkyBlock item with this id ("ASPECT_OF_THE_END"), or null. */
    public static ItemDefinition skyblock(String skyblockId) {
        ItemDefinition def = skyblock.get(skyblockId);
        if (def != null || skyblockId == null) {
            return def;
        }
        // Enchanted books by their Bazaar id ("ENCHANTMENT_SHARPNESS_6").
        if (skyblockId.startsWith(com.skyrpg.items.enchant.Books.PRODUCT)) {
            return com.skyrpg.items.enchant.Books.definition(skyblockId);
        }
        // Other ids other mods answer for (pets by their recipe id, "BEE;4").
        for (java.util.function.Function<String, ItemDefinition> resolver : VIRTUAL) {
            ItemDefinition virtual = resolver.apply(skyblockId);
            if (virtual != null) {
                return virtual;
            }
        }
        return null;
    }

    private static final java.util.List<java.util.function.Function<String, ItemDefinition>> VIRTUAL =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Lets another mod give a definition to SkyBlock ids that aren't items of their own (its stacks come from {@link SkyItems#registerFactory}). */
    public static void registerVirtual(java.util.function.Function<String, ItemDefinition> resolver) {
        VIRTUAL.add(resolver);
    }

    /** Whether any SkyBlock items are loaded (stacks then need their custom data checked). */
    public static boolean hasSkyblockItems() {
        return !skyblock.isEmpty();
    }

    /** Matches "skyrpg_items:harvester_helmet" exactly, a SkyBlock id ("aspect_of_the_end"), or just "harvester_helmet" by path. */
    public static ItemDefinition find(Identifier id) {
        ItemDefinition exact = items.get(id);
        if (exact != null) {
            return exact;
        }
        ItemDefinition bySkyblockId = skyblock.get(id.getPath().toUpperCase(java.util.Locale.ROOT));
        if (bySkyblockId != null) {
            return bySkyblockId;
        }
        return items.values().stream().filter(def -> def.id().getPath().equals(id.getPath())).findFirst().orElse(null);
    }

    public static ItemSet set(Identifier id) {
        return id == null ? null : sets.get(id);
    }

    public static Collection<ItemDefinition> items() {
        return items.values();
    }

    public static Collection<ItemSet> sets() {
        return sets.values();
    }

    static void loadItems(Map<Identifier, JsonElement> files) {
        setItems(parseAll(files, ItemDefinition::parse, "item"));
        SkyRpgItems.LOGGER.info("Loaded {} items ({} SkyBlock items)", items.size(), skyblock.size());
    }

    private static void setItems(Map<Identifier, ItemDefinition> loaded) {
        Map<String, ItemDefinition> bySkyblockId = new LinkedHashMap<>();
        for (ItemDefinition def : loaded.values()) {
            if (def.skyblock() && bySkyblockId.putIfAbsent(def.skyblockId(), def) != null) {
                SkyRpgItems.LOGGER.error("Two items share the SkyBlock id {}; keeping {}", def.skyblockId(), bySkyblockId.get(def.skyblockId()).id());
            }
        }
        items = loaded;
        skyblock = Collections.unmodifiableMap(bySkyblockId);
    }

    static void loadSets(Map<Identifier, JsonElement> files) {
        sets = parseAll(files, ItemSet::parse, "item set");
        SkyRpgItems.LOGGER.info("Loaded {} item sets", sets.size());
    }

    /** Replaces everything with definitions sent by the server (client side). */
    public static void replace(Map<Identifier, JsonElement> itemFiles, Map<Identifier, JsonElement> setFiles) {
        setItems(parseAll(itemFiles, ItemDefinition::parse, "item"));
        sets = parseAll(setFiles, ItemSet::parse, "item set");
    }

    private static <T> Map<Identifier, T> parseAll(Map<Identifier, JsonElement> files,
                                                   BiFunction<Identifier, JsonObject, T> parser, String kind) {
        Map<Identifier, T> parsed = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                parsed.put(id, parser.apply(id, json.getAsJsonObject()));
            } catch (RuntimeException e) {
                SkyRpgItems.LOGGER.error("Skipping invalid {} {}: {}", kind, id, e.getMessage());
            }
        });
        return Collections.unmodifiableMap(parsed);
    }
}
