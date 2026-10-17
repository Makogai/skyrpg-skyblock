package com.skyrpg.items;

import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.items.mixin.CustomDataAccess;
import com.skyrpg.items.enchant.Enchants;
import com.skyrpg.items.reforge.Reforges;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * What's been done to one SkyBlock item, kept in its custom data under Hypixel's own keys: its reforge
 * ({@code modifier}), a Recombobulator 3000 ({@code rarity_upgrades}: one rarity up) and Hot Potato and Fuming Potato
 * Books ({@code hot_potato_count}, up to 10 + 5) and its enchantments ({@code enchantments}: id to level). Stats, name,
 * rarity and tooltip all go through here.
 */
public final class ItemMods {
    public static final String MODIFIER = "modifier";
    public static final String RARITY_UPGRADES = "rarity_upgrades";
    public static final String HOT_POTATO = "hot_potato_count";
    public static final String ENCHANTMENTS = "enchantments";
    /** Hot Potato Books stop at 10; Fuming Potato Books go on to 15. */
    public static final int HOT_POTATO_MAX = 10;
    public static final int FUMING_MAX = 15;

    private static final Set<String> WEAPONS = Set.of("SWORD", "LONGSWORD", "BOW", "FISHING_WEAPON", "FISHING_ROD");
    private static final Set<String> ARMOR = Set.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    private ItemMods() {
    }

    private static CompoundTag tag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? null : ((CustomDataAccess) (Object) data).skyrpg$tag();
    }

    /** The reforge on this item, or null. */
    public static Reforges.Reforge reforge(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag == null ? null : Reforges.byModifier(tag.getString(MODIFIER).orElse(null));
    }

    public static boolean recombobulated(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag != null && tag.getIntOr(RARITY_UPGRADES, 0) > 0;
    }

    public static int hotPotatoBooks(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag == null ? 0 : tag.getIntOr(HOT_POTATO, 0);
    }

    /** The item's rarity after a Recombobulator: one step up (Common to Divine, Special to Very Special). */
    public static Rarity rarity(ItemStack stack, ItemDefinition def) {
        return recombobulated(stack) ? upgraded(def.rarity()) : def.rarity();
    }

    public static Rarity upgraded(Rarity rarity) {
        return switch (rarity) {
            case COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC -> Rarity.values()[rarity.ordinal() + 1];
            case SPECIAL -> Rarity.VERY_SPECIAL;
            default -> rarity;
        };
    }

    /** "Fierce Aspect of the End": the name with its reforge in front. */
    public static String name(ItemStack stack, ItemDefinition def) {
        Reforges.Reforge reforge = reforge(stack);
        return reforge == null ? def.name() : reforge.name() + " " + def.name();
    }

    /** Whether Hot Potato Books work on it: weapons and fishing rods (damage and strength) and armour (health, defense). */
    public static boolean takesPotatoBooks(ItemDefinition def) {
        String category = Reforges.category(def);
        return category != null && (WEAPONS.contains(category) || ARMOR.contains(category));
    }

    /** What its Hot Potato and Fuming Potato Books add (per book: +2 Damage and +2 Strength, or +4 Health and +2 Defense). */
    public static Map<StatType, Double> potatoStats(ItemStack stack, ItemDefinition def) {
        int books = hotPotatoBooks(stack);
        if (books <= 0 || !takesPotatoBooks(def)) {
            return Map.of();
        }
        return ARMOR.contains(Reforges.category(def))
                ? Map.of(StatTypes.HEALTH, 4.0 * books, StatTypes.DEFENSE, 2.0 * books)
                : Map.of(StatTypes.DAMAGE, 2.0 * books, StatTypes.STRENGTH, 2.0 * books);
    }

    /** What its reforge adds at the item's (upgraded) rarity. */
    public static Map<StatType, Double> reforgeStats(ItemStack stack, ItemDefinition def) {
        Reforges.Reforge reforge = reforge(stack);
        return reforge == null ? Map.of() : reforge.statsAt(rarity(stack, def));
    }

    /** All of this item's stats: its own, plus its reforge's and its potato books'. */
    public static Map<StatType, Double> stats(ItemStack stack, ItemDefinition def) {
        Map<StatType, Double> reforge = reforgeStats(stack, def);
        Map<StatType, Double> potato = potatoStats(stack, def);
        if (reforge.isEmpty() && potato.isEmpty()) {
            return def.stats();
        }
        Map<StatType, Double> out = new LinkedHashMap<>(def.stats());
        reforge.forEach((stat, value) -> out.merge(stat, value, Double::sum));
        potato.forEach((stat, value) -> out.merge(stat, value, Double::sum));
        return out;
    }

    /** Changes the item's custom data (a copy: components are immutable). */
    public static void edit(ItemStack stack, Consumer<CompoundTag> change) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, change);
    }

    public static void setReforge(ItemStack stack, Reforges.Reforge reforge) {
        edit(stack, tag -> {
            if (reforge == null) {
                tag.remove(MODIFIER);
            } else {
                tag.putString(MODIFIER, reforge.modifier());
            }
        });
        SkyItems.normalize(stack);
    }

    public static void recombobulate(ItemStack stack) {
        edit(stack, tag -> tag.putInt(RARITY_UPGRADES, 1));
        SkyItems.normalize(stack);
    }

    public static void addPotatoBook(ItemStack stack) {
        edit(stack, tag -> tag.putInt(HOT_POTATO, tag.getIntOr(HOT_POTATO, 0) + 1));
        SkyItems.normalize(stack);
    }

    /** Its enchantments, id to level, in the order they were put on (Hypixel's {@code enchantments} compound). */
    public static Map<String, Integer> enchantments(ItemStack stack) {
        CompoundTag tag = tag(stack);
        if (tag == null || !(tag.get(ENCHANTMENTS) instanceof CompoundTag enchants) || enchants.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String id : enchants.keySet()) {
            int level = enchants.getIntOr(id, 0);
            if (level > 0) {
                out.put(id, level);
            }
        }
        return out;
    }

    public static int enchantment(ItemStack stack, String id) {
        return enchantments(stack).getOrDefault(id, 0);
    }

    /**
     * Puts an enchantment on at that level (0 takes it off). Whatever conflicts with it comes off, and so does any other
     * ultimate enchantment: an item has only one.
     */
    public static void setEnchantment(ItemStack stack, String id, int level) {
        Enchants.Enchant enchant = Enchants.get(id);
        edit(stack, tag -> {
            CompoundTag enchants = tag.get(ENCHANTMENTS) instanceof CompoundTag existing ? existing : new CompoundTag();
            if (enchant != null && level > 0) {
                for (String other : List.copyOf(enchants.keySet())) {
                    Enchants.Enchant on = Enchants.get(other);
                    if (enchant.conflicts().contains(other) || on != null && on.conflicts().contains(id)
                            || enchant.ultimate() && on != null && on.ultimate() && !other.equals(id)) {
                        enchants.remove(other);
                    }
                }
                enchants.putInt(id, level);
            } else {
                enchants.remove(id);
            }
            if (enchants.isEmpty()) {
                tag.remove(ENCHANTMENTS);
            } else {
                tag.put(ENCHANTMENTS, enchants);
            }
        });
        SkyItems.normalize(stack);
    }
}
