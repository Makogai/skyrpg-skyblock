package com.skyrpg.skyblock.reforge;

import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.Rarity;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.enchant.Books;
import com.skyrpg.items.enchant.Enchants;
import com.skyrpg.items.reforge.Reforges;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * What the Blacksmith and the anvil do to an item, worked out the same way on both sides (the screen previews it, the
 * server does it):
 * <ul>
 *   <li>Reforge (the Blacksmith): a random reforge of his for coins by rarity, never the one it has; or the reforge of a
 *       reforge stone in the second slot, for that stone's price.</li>
 *   <li>Anvil: an enchanted book (for XP levels; two books combine for free), a Recombobulator 3000 (one rarity up,
 *       once), an Accessory Enrichment (+stat on a Legendary accessory), a Hot Potato Book (up to 10) or a Fuming Potato Book (up to 15 in all) on the item in the first slot; a Wither scroll
 *       (Implosion, Shadow Warp, Wither Shield, or the Ultimate Wither Scroll for all three) on a Necron's Blade, or a Pocket
 *       Sack-in-a-Sack on a sack (three times the size; up to three).</li>
 * </ul>
 */
public final class ItemWork {
    public enum Mode { REFORGE, ANVIL }

    public static final String RECOMBOBULATOR = "RECOMBOBULATOR_3000";
    public static final String HOT_POTATO_BOOK = "HOT_POTATO_BOOK";
    public static final String FUMING_POTATO_BOOK = "FUMING_POTATO_BOOK";

    /** Hypixel's Blacksmith prices for a random reforge, by rarity. */
    private static final long[] REFORGE_COST = {250, 500, 1_000, 2_500, 5_000, 10_000, 15_000, 25_000, 50_000, 50_000, 50_000};
    /** What a Recombobulator 3000 can go on. */
    private static final Set<String> RECOMBOBULATES = Set.of("SWORD", "LONGSWORD", "BOW", "FISHING_WEAPON", "FISHING_ROD", "HELMET",
            "CHESTPLATE", "LEGGINGS", "BOOTS", "NECKLACE", "CLOAK", "BELT", "GLOVES", "BRACELET", "ACCESSORY", "PICKAXE", "DRILL",
            "GAUNTLET", "AXE", "FARMING_TOOL", "WAND", "SPADE", "SHEARS", "VACUUM", "DEPLOYABLE");

    /**
     * What pressing the button would do. {@code result} is the item afterwards (null for a random reforge, which isn't
     * known yet, or when it can't be done); {@code problem} says why not ("" when it can).
     */
    public record Preview(ItemStack result, long coins, int levels, String action, String problem) {
        public boolean ok() {
            return problem.isEmpty();
        }
    }

    private ItemWork() {
    }

    public static Preview preview(Mode mode, ItemStack item, ItemStack extra) {
        if (item.isEmpty()) {
            return new Preview(null, 0, 0, "", mode == Mode.REFORGE ? "Place an item to reforge" : "Place an item to upgrade");
        }
        ItemDefinition def = SkyItems.definition(item);
        if (def == null || !def.skyblock()) {
            return new Preview(null, 0, 0, "", "That isn't a SkyBlock item");
        }
        Rarity rarity = ItemMods.rarity(item, def);
        return mode == Mode.REFORGE ? reforge(item, def, rarity, extra) : anvil(item, def, extra);
    }

    private static Preview reforge(ItemStack item, ItemDefinition def, Rarity rarity, ItemStack stone) {
        if (!stone.isEmpty()) {
            Reforges.Reforge reforge = Reforges.byStone(SkyItems.skyblockId(stone));
            if (reforge == null) {
                return new Preview(null, 0, 0, "", "That isn't a reforge stone");
            }
            if (!reforge.fits(def, rarity)) {
                return new Preview(null, 0, 0, reforge.name(), "The " + reforge.name() + " reforge doesn't fit this item");
            }
            ItemStack result = item.copy();
            ItemMods.setReforge(result, reforge);
            return new Preview(result, reforge.costAt(rarity), 0, reforge.name(), "");
        }
        List<Reforges.Reforge> options = Reforges.blacksmith(def, rarity);
        if (options.isEmpty()) {
            return new Preview(null, 0, 0, "", "This item can't be reforged");
        }
        return new Preview(null, REFORGE_COST[Math.min(rarity.ordinal(), REFORGE_COST.length - 1)], 0, "Random reforge", "");
    }

    private static Preview anvil(ItemStack item, ItemDefinition def, ItemStack extra) {
        if (extra.isEmpty()) {
            return new Preview(null, 0, 0, "", "Add an enchanted book, a Recombobulator 3000, a Potato Book, an Enrichment, a Pocket Sack-in-a-Sack or a Wither scroll");
        }
        if (Books.isBook(extra)) {
            return book(item, def, extra);
        }
        String id = SkyItems.skyblockId(extra);
        String category = Reforges.category(def);
        ItemStack result = item.copy();
        if (RECOMBOBULATOR.equals(id)) {
            if (ItemMods.recombobulated(item)) {
                return new Preview(null, 0, 0, "Recombobulate", "This item is already recombobulated");
            }
            if (category == null || !RECOMBOBULATES.contains(category) || ItemMods.upgraded(def.rarity()) == def.rarity()) {
                return new Preview(null, 0, 0, "Recombobulate", "This item can't be recombobulated");
            }
            ItemMods.recombobulate(result);
            return new Preview(result, 0, 0, "Recombobulate", "");
        }
        boolean fuming = FUMING_POTATO_BOOK.equals(id);
        if (HOT_POTATO_BOOK.equals(id) || fuming) {
            String action = fuming ? "Fuming Potato Book" : "Hot Potato Book";
            if (!ItemMods.takesPotatoBooks(def)) {
                return new Preview(null, 0, 0, action, "Potato Books only go on weapons, rods and armour");
            }
            int books = ItemMods.hotPotatoBooks(item);
            if (books >= (fuming ? ItemMods.FUMING_MAX : ItemMods.HOT_POTATO_MAX)) {
                return new Preview(null, 0, 0, action, fuming ? "This item has all 15 Potato Books" : "This item has 10 Hot Potato Books: use Fuming ones now");
            }
            ItemMods.addPotatoBook(result);
            return new Preview(result, 0, 0, action, "");
        }
        // a Pocket Sack-in-a-Sack: a sack holds three times as much (up to three of them)
        if (com.skyrpg.skyblock.sack.Sacks.POCKET.equals(id)) {
            if (!com.skyrpg.skyblock.sack.Sacks.isSack(def.skyblockId())) {
                return new Preview(null, 0, 0, "Pocket Sack-in-a-Sack", "That only goes on a sack");
            }
            if (com.skyrpg.skyblock.sack.Sacks.pockets(item) >= com.skyrpg.skyblock.sack.Sacks.MAX_POCKETS) {
                return new Preview(null, 0, 0, "Pocket Sack-in-a-Sack", "This sack has all three Pocket Sack-in-a-Sacks");
            }
            int pockets = com.skyrpg.skyblock.sack.Sacks.pockets(item) + 1;
            ItemMods.edit(result, tag -> tag.putInt(com.skyrpg.skyblock.sack.Sacks.POCKETS, pockets));
            return new Preview(result, 0, 0, "Pocket Sack-in-a-Sack", "");
        }
        // an Accessory Enrichment: +stat on a Legendary or better accessory (a new one replaces the old)
        ItemMods.Enrichment enrichment = ItemMods.enrichmentOfItem(id);
        if (enrichment != null) {
            String action = enrichment.name() + " Enrichment";
            if (!ItemMods.enrichable(item, def)) {
                return new Preview(null, 0, 0, action, "Enrichments go on Legendary or better accessories");
            }
            if (ItemMods.enrichment(item) == enrichment) {
                return new Preview(null, 0, 0, action, "This accessory already has the " + enrichment.name() + " Enrichment");
            }
            ItemMods.setEnrichment(result, enrichment);
            return new Preview(result, 0, 0, action, "");
        }
        // the Wither scrolls: on Necron's Blade and its upgrades (the Ultimate Wither Scroll is all three at once)
        boolean ultimate = com.skyrpg.items.ability.ItemAbilities.ULTIMATE_WITHER_SCROLL.equals(id);
        if (ultimate || com.skyrpg.items.ability.ItemAbilities.WITHER_SCROLLS.contains(id)) {
            String action = "Apply " + SkyItems.definition(extra).name();
            if (!com.skyrpg.items.ability.ItemAbilities.WITHER_BLADES.contains(def.skyblockId())) {
                return new Preview(null, 0, 0, action, "Wither scrolls only go on Necron's Blade, Hyperion, Astraea, Scylla and Valkyrie");
            }
            java.util.List<String> on = ItemMods.scrolls(item);
            java.util.List<String> adding = ultimate ? com.skyrpg.items.ability.ItemAbilities.WITHER_SCROLLS : java.util.List.of(id);
            if (on.containsAll(adding)) {
                return new Preview(null, 0, 0, action, ultimate ? "This blade has all three scrolls" : "This blade already has that scroll");
            }
            for (String scroll : adding) {
                if (!on.contains(scroll)) {
                    ItemMods.addScroll(result, scroll);
                }
            }
            return new Preview(result, 0, 0, action, "");
        }
        return new Preview(null, 0, 0, "", "That can't be combined with this item");
    }

    /**
     * An enchanted book on an item (its XP levels for each enchant it adds), or on another book (free). Two equal levels
     * make the next one, up to the table's top level for table enchants (Sharpness V and V don't make VI); otherwise the
     * higher level stays.
     */
    private static Preview book(ItemStack item, ItemDefinition def, ItemStack book) {
        Map<String, Integer> enchants = ItemMods.enchantments(book);
        if (enchants.isEmpty()) {
            return new Preview(null, 0, 0, "Apply Book", "That book has no enchantment");
        }
        boolean onBook = Books.isBook(item);
        ItemStack result = item.copy();
        int levels = 0;
        boolean changed = false;
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            Enchants.Enchant enchant = Enchants.get(entry.getKey());
            if (enchant == null) {
                continue;
            }
            if (!onBook && !enchant.fits(def)) {
                return new Preview(null, 0, 0, "Apply Book", enchant.name() + " doesn't go on this item");
            }
            int have = ItemMods.enchantment(result, enchant.id());
            int level = entry.getValue();
            int target = have == level && enchant.combinable() && level < enchant.combineMax() ? level + 1 : Math.max(have, level);
            if (target == have) {
                continue;
            }
            ItemMods.setEnchantment(result, enchant.id(), target);
            changed = true;
            if (!onBook) {
                levels += enchant.xpCost(target);
            }
        }
        if (!changed) {
            return new Preview(null, 0, 0, onBook ? "Combine Books" : "Apply Book", "Nothing to add: it has these already");
        }
        return new Preview(result, 0, levels, onBook ? "Combine Books" : "Apply Book", "");
    }

    /** A random Blacksmith reforge for this item, never the one it already has (unless it's the only one). */
    public static Reforges.Reforge roll(ItemStack item, RandomSource random) {
        ItemDefinition def = SkyItems.definition(item);
        if (def == null) {
            return null;
        }
        List<Reforges.Reforge> options = new java.util.ArrayList<>(Reforges.blacksmith(def, ItemMods.rarity(item, def)));
        Reforges.Reforge current = ItemMods.reforge(item);
        if (options.size() > 1) {
            options.remove(current);
        }
        return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
    }
}
