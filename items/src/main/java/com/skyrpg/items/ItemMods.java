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
    /** Dungeon stars (Hypixel's {@code upgrade_level}): 0 to 5, bought with essence. */
    public static final String STARS = "upgrade_level";
    private static final String STAR = "✪";
    /** Hot Potato Books stop at 10; Fuming Potato Books go on to 15. */
    public static final int HOT_POTATO_MAX = 10;
    public static final int FUMING_MAX = 15;

    private static final Set<String> WEAPONS = Set.of("SWORD", "LONGSWORD", "BOW", "FISHING_WEAPON", "FISHING_ROD");
    private static final Set<String> ARMOR = Set.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    private ItemMods() {
    }

    public static CompoundTag tag(ItemStack stack) {
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

    /** Hypixel's Blaze Slayer daggers: the attunement they're in ("Ashen"), stored as {@code td_attune_mode}. */
    public static final String ATTUNEMENT = "td_attune_mode";

    /** The two attunements a dagger swaps between ("Ashen", "Auric"), from its ability ("Swap between Ashen and Auric mode"). */
    public static String[] attunements(ItemDefinition def) {
        if (def == null || def.ability() == null) {
            return new String[0];
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("between (\\w+) and (\\w+) mode").matcher(def.ability().description());
        return m.find() ? new String[] {m.group(1), m.group(2)} : new String[0];
    }

    /** The dagger's attunement now (its first one by default), or "" for anything else. */
    public static String attunement(ItemStack stack, ItemDefinition def) {
        String[] modes = attunements(def);
        if (modes.length == 0) {
            return "";
        }
        String mode = tag(stack).getStringOr(ATTUNEMENT, "");
        return mode.equals(modes[1]) ? modes[1] : modes[0];
    }

    /** Swaps the dagger to its other attunement; returns the new one. */
    public static String swapAttunement(ItemStack stack, ItemDefinition def) {
        String[] modes = attunements(def);
        if (modes.length == 0) {
            return "";
        }
        String next = attunement(stack, def).equals(modes[0]) ? modes[1] : modes[0];
        edit(stack, tag -> tag.putString(ATTUNEMENT, next));
        return next;
    }

    public static int stars(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag == null ? 0 : tag.getIntOr(STARS, 0);
    }

    public static void setStars(ItemStack stack, int stars) {
        edit(stack, tag -> tag.putInt(STARS, stars));
        SkyItems.normalize(stack);
    }

    /** A dungeon item (Hypixel's {@code dungeon_item}): its stats are boosted in the Catacombs. */
    public static boolean dungeonItem(ItemDefinition def) {
        return def != null && def.json().has("hypixel") && def.json().getAsJsonObject("hypixel").has("dungeon_item")
                && def.json().getAsJsonObject("hypixel").get("dungeon_item").getAsBoolean();
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
        String name = reforge == null ? def.name() : reforge.name() + " " + def.name();
        int stars = stars(stack);
        // Master Stars (6-10), and Kuudra armour's Crimson stars past five (up to 15), show as a circled number after the five stars.
        String master = stars > 5 ? String.valueOf((char) (0x278A + Math.min(10, stars - 5) - 1)) : "";
        return stars > 0 ? name + " " + STAR.repeat(Math.min(5, stars)) + master : name;
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

    /** All of this item's stats: its own, plus its reforge's, its potato books' and its gemstones'. */
    public static Map<StatType, Double> stats(ItemStack stack, ItemDefinition def) {
        Map<StatType, Double> reforge = reforgeStats(stack, def);
        Map<StatType, Double> potato = potatoStats(stack, def);
        Map<StatType, Double> gems = gemStats(stack, def);
        if (reforge.isEmpty() && potato.isEmpty() && gems.isEmpty()) {
            return def.stats();
        }
        Map<StatType, Double> out = new LinkedHashMap<>(def.stats());
        reforge.forEach((stat, value) -> out.merge(stat, value, Double::sum));
        potato.forEach((stat, value) -> out.merge(stat, value, Double::sum));
        gems.forEach((stat, value) -> out.merge(stat, value, Double::sum));
        return out;
    }

    /** What its gemstones add, at the item's (upgraded) rarity. */
    public static Map<StatType, Double> gemStats(ItemStack stack, ItemDefinition def) {
        return com.skyrpg.items.gem.Gemstones.stats(stack, rarity(stack, def));
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
    /** Hypixel's {@code ability_scroll}: the Wither scrolls put on a Necron's Blade, in the order they went on. */
    public static final String SCROLLS = "ability_scroll";

    public static List<String> scrolls(ItemStack stack) {
        CompoundTag tag = tag(stack);
        if (tag == null || !(tag.get(SCROLLS) instanceof net.minecraft.nbt.ListTag list)) {
            return List.of();
        }
        List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            list.getString(i).ifPresent(out::add);
        }
        return out;
    }

    public static void addScroll(ItemStack stack, String scroll) {
        edit(stack, tag -> {
            net.minecraft.nbt.ListTag list = tag.get(SCROLLS) instanceof net.minecraft.nbt.ListTag had ? had : new net.minecraft.nbt.ListTag();
            list.add(net.minecraft.nbt.StringTag.valueOf(scroll));
            tag.put(SCROLLS, list);
        });
        SkyItems.normalize(stack);
    }

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
