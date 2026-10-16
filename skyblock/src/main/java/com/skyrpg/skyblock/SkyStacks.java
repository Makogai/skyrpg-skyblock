package com.skyrpg.skyblock;

import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import net.minecraft.world.entity.player.Inventory;
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
        ItemDefinition def = ItemRegistry.skyblock(skyblockId);
        return def != null && plain(def) && stack.is(def.item()) && SkyItems.definition(stack) == null;
    }

    /** A SkyBlock item that is nothing but its material (no skin, colour or stats), like Cobblestone or Wheat. */
    public static boolean plain(ItemDefinition def) {
        return !def.json().has("skin") && !def.json().has("color") && def.stats().isEmpty() && def.ability() == null;
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
