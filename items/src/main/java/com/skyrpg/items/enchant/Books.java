package com.skyrpg.items.enchant;

import com.google.gson.JsonObject;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.Rarity;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyRpgItems;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Enchanted books. A book is Hypixel's {@code ENCHANTED_BOOK} with its {@code enchantments}; one with a single enchant
 * also answers to the Bazaar's product id for it, {@code ENCHANTMENT_SHARPNESS_6}: {@link ItemRegistry#skyblock} gives
 * such an id a definition of its own ("Sharpness VI"), {@link SkyItems#create} makes it into the real book, and
 * {@code SkyStacks} counts a book as it, so the Bazaar trades books like any other product.
 */
public final class Books {
    public static final String BOOK = "ENCHANTED_BOOK";
    public static final String PRODUCT = "ENCHANTMENT_";

    private static final Map<String, ItemDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private Books() {
    }

    public static boolean isBook(ItemStack stack) {
        return BOOK.equals(SkyItems.skyblockId(stack));
    }

    /** "ENCHANTMENT_SHARPNESS_6" for a book holding only Sharpness VI, else null. */
    public static String productId(ItemStack stack) {
        if (!isBook(stack)) {
            return null;
        }
        Map<String, Integer> enchants = ItemMods.enchantments(stack);
        if (enchants.size() != 1) {
            return null;
        }
        Map.Entry<String, Integer> only = enchants.entrySet().iterator().next();
        return productId(only.getKey(), only.getValue());
    }

    public static String productId(String enchant, int level) {
        return PRODUCT + enchant.toUpperCase(Locale.ROOT) + "_" + level;
    }

    /** The definition for a book product id, or null when it names no known enchant level. */
    public static ItemDefinition definition(String productId) {
        if (productId == null || !productId.startsWith(PRODUCT)) {
            return null;
        }
        return DEFINITIONS.computeIfAbsent(productId, id -> {
            int cut = id.lastIndexOf('_');
            Enchants.Enchant enchant = cut <= PRODUCT.length() ? null : Enchants.get(id.substring(PRODUCT.length(), cut).toLowerCase(Locale.ROOT));
            ItemDefinition book = ItemRegistry.skyblock(BOOK);
            int level;
            try {
                level = Integer.parseInt(id.substring(cut + 1));
            } catch (NumberFormatException e) {
                return null;
            }
            if (enchant == null || book == null || level < 1 || level > enchant.max()) {
                return null;
            }
            Enchants.Level at = enchant.level(level);
            Rarity rarity;
            try {
                rarity = Rarity.valueOf(at == null ? "COMMON" : at.rarity());
            } catch (IllegalArgumentException e) {
                rarity = Rarity.COMMON;
            }
            JsonObject json = book.json().deepCopy();
            json.addProperty("skyblock_id", id);
            json.addProperty("name", enchant.label(level));
            JsonObject of = new JsonObject();
            of.addProperty("enchant", enchant.id());
            of.addProperty("level", level);
            json.add("book", of);
            return new ItemDefinition(SkyRpgItems.id("book/" + id.toLowerCase(Locale.ROOT)), id, Items.ENCHANTED_BOOK, enchant.label(level), rarity,
                    Map.of(), null, "", true, null, Map.of(), List.of(), json);
        });
    }

    /** A real book for a book product's definition (null if the definition isn't one). */
    public static ItemStack create(ItemDefinition def, int count) {
        if (!def.json().has("book")) {
            return null;
        }
        JsonObject of = def.json().getAsJsonObject("book");
        return create(of.get("enchant").getAsString(), of.get("level").getAsInt(), count);
    }

    public static ItemStack create(String enchant, int level, int count) {
        ItemDefinition book = ItemRegistry.skyblock(BOOK);
        if (book == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = SkyItems.create(book, count);
        ItemMods.setEnchantment(stack, enchant, level);
        return stack;
    }

    /** Forget the definitions made so far (the enchants or items were reloaded). */
    public static void clear() {
        DEFINITIONS.clear();
    }
}
