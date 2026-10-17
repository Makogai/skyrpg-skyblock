package com.skyrpg.skyblock;

import com.google.gson.JsonObject;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A SkyBlock item is a vanilla item underneath, but it isn't that item: an Enchanted Diamond Block doesn't place, a
 * Wither Catalyst (a head) doesn't go on the ground, a dungeon decoy (a spawn egg) doesn't hatch a pig, and nothing
 * gets eaten or thrown just because its base could. Hypixel's {@code can_place} items still place. Bows, rods,
 * crossbows, ender pearls and wearables keep their vanilla use; abilities run before this (the items mod's
 * {@code Abilities}), so this only catches what's left.
 */
public final class SkyblockItemUse {
    private SkyblockItemUse() {
    }

    /** Utility blocks Hypixel lets you place on your island although their data doesn't say so. */
    private static final java.util.Set<String> PLACEABLE = java.util.Set.of("BREWING_STAND_ITEM");

    static void init() {
        UseItemCallback.EVENT.register((player, level, hand) ->
                blocksVanillaUse(player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    /** Whether this stack is a SkyBlock item whose base item's right-click use doesn't belong to it. */
    public static boolean blocksVanillaUse(ItemStack stack) {
        ItemDefinition def = SkyItems.definition(stack);
        if (def == null || !def.skyblock() || def.ability() != null) {
            return false;
        }
        Item item = stack.getItem();
        return !(item instanceof BowItem || item instanceof CrossbowItem || item instanceof FishingRodItem
                || item instanceof EnderpearlItem || stack.has(DataComponents.EQUIPPABLE));
    }

    /**
     * Whether this stack is a SkyBlock item that mustn't be placed as a block (Hypixel's {@code can_place} ones may, and its
     * furniture, like the Experimentation Table: placed as its head, it keeps the item's data and drops it back).
     */
    public static boolean blocksPlacing(ItemStack stack) {
        ItemDefinition def = SkyItems.definition(stack);
        if (def == null || !def.skyblock()) {
            return false;
        }
        if (PLACEABLE.contains(def.skyblockId())) {
            return false;
        }
        JsonObject hypixel = def.json().getAsJsonObject("hypixel");
        return hypixel == null || !hypixel.has("furniture") && (!hypixel.has("can_place") || !hypixel.get("can_place").getAsBoolean());
    }
}
