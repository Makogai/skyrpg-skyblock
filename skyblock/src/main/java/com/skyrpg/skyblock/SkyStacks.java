package com.skyrpg.skyblock;

import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Counting and taking SkyBlock items by their SkyBlock id, for costs (minion upgrades, recipes, NPC trades). A plain
 * vanilla stack counts as the SkyBlock item it's the base of when that item is just the material ("COBBLESTONE" is
 * any cobblestone), so blocks mined before they turned into SkyBlock items still count.
 */
public final class SkyStacks {
    private SkyStacks() {
    }

    /** Whether this stack is the SkyBlock item {@code skyblockId}. */
    public static boolean is(ItemStack stack, String skyblockId) {
        if (stack.isEmpty()) {
            return false;
        }
        String id = SkyItems.skyblockId(stack);
        if (id != null) {
            return id.equals(skyblockId);
        }
        return skyblockId.equals(id(stack));
    }

    /**
     * The SkyBlock id of a stack: its own, or for a plain vanilla stack the SkyBlock item it's the material of (coal
     * mined from an ore is "COAL"). Null when it's neither.
     */
    public static String id(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String id = SkyItems.skyblockId(stack);
        if (id != null || SkyItems.definition(stack) != null) {
            return id;
        }
        return plainIds().get(stack.getItem());
    }

    private static Collection<ItemDefinition> plainFrom;
    private static Map<Item, String> plainIds = Map.of();

    /**
     * Which SkyBlock item each vanilla item is when it's just the material: oak planks are "WOOD", cobblestone is
     * "COBBLESTONE". With several on one base, the one named like the vanilla item wins, then the shortest id.
     */
    private static synchronized Map<Item, String> plainIds() {
        Collection<ItemDefinition> all = ItemRegistry.items();
        if (all != plainFrom) {
            Map<Item, String> ids = new HashMap<>();
            for (ItemDefinition def : all) {
                if (!def.skyblock() || !plain(def)) {
                    continue;
                }
                String guess = BuiltInRegistries.ITEM.getKey(def.item()).getPath().toUpperCase(Locale.ROOT);
                ids.merge(def.item(), def.skyblockId(), (a, b) -> a.equals(guess) ? a : b.equals(guess) ? b : a.length() <= b.length() ? a : b);
            }
            plainIds = ids;
            plainFrom = all;
        }
        return plainIds;
    }

    /** A SkyBlock item that is nothing but its material (no skin, colour or stats), like Cobblestone or Wheat. */
    public static boolean plain(ItemDefinition def) {
        return !def.json().has("skin") && !def.json().has("color") && def.stats().isEmpty() && def.ability() == null && !def.glint();
    }

    public static int count(Inventory inventory, String skyblockId) {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (is(stack, skyblockId)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Removes {@code count} of the item; false (and nothing removed) if there isn't that much. */
    public static boolean take(Inventory inventory, String skyblockId, int count) {
        if (count(inventory, skyblockId) < count) {
            return false;
        }
        int left = count;
        for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (is(stack, skyblockId)) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
        inventory.setChanged();
        return true;
    }
}
