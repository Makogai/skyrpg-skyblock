package com.skyrpg.skyblock.recipe;

import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * SkyBlock's crafting table: a 3 by 3 grid of real slots under our own screen. Whatever SkyBlock recipe the grid makes
 * ({@link Recipes#match}) shows as the result (both sides work it out from the same recipes); the Craft buttons make
 * it once or as many times as the grid holds, into your inventory. What's left in the grid goes back to you when you
 * close it.
 */
public final class CraftMenu extends AbstractContainerMenu {
    public static final int CRAFT = 0;
    public static final int CRAFT_ALL = 1;
    // Layout, shared with the screen.
    public static final int PAD = 8;
    public static final int TOP = 26;
    public static final int GRID_X = PAD + 10;
    public static final int GRID_Y = TOP + 8;
    public static final int RESULT_X = GRID_X + 3 * 18 + 26;
    public static final int WIDTH = 236;
    public static final int RESULT_W = WIDTH - PAD - RESULT_X;
    public static final int INV_X = (WIDTH - 162) / 2 + 1;
    public static final int INV_Y = GRID_Y + 3 * 18 + 24;
    public static final int HEIGHT = INV_Y + 3 * 18 + 4 + 18 + PAD;
    public static final int GRID_SIZE = 9;

    private final SimpleContainer grid;
    private final Player player;

    public CraftMenu(int id, Inventory inventory) {
        super(SkyMenus.CRAFT, id);
        this.player = inventory.player;
        this.grid = new SimpleContainer(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            addSlot(new Slot(grid, i, GRID_X + 1 + (i % 3) * 18, GRID_Y + 1 + (i / 3) * 18));
        }
        addStandardInventorySlots(inventory, INV_X, INV_Y);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new CraftMenu(id, inventory), Component.literal("Craft Item")));
    }

    /** The grid's stacks, rows top to bottom. */
    public List<ItemStack> gridStacks() {
        List<ItemStack> out = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            out.add(grid.getItem(i));
        }
        return out;
    }

    /** What the grid makes right now, or null. */
    public Recipes.Match match() {
        return Recipes.match(gridStacks(), player);
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (!(clicker instanceof ServerPlayer server) || id != CRAFT && id != CRAFT_ALL) {
            return false;
        }
        Recipes.Match match = match();
        if (match == null) {
            return false;
        }
        Recipes.Recipe recipe = match.recipe();
        if (!Recipes.unlocked(server, recipe)) {
            server.sendOverlayMessage(Component.literal("Locked: requires " + recipe.requires().describe()).withStyle(ChatFormatting.RED));
            return true;
        }
        ItemDefinition output = ItemRegistry.skyblock(recipe.output());
        int times = Math.min(match.times(gridStacks()), id == CRAFT_ALL ? Crafting.MAX_CRAFTS : 1);
        if (output == null || times <= 0) {
            return false;
        }
        for (int i = 0; i < GRID_SIZE; i++) {
            if (match.need()[i] > 0) {
                grid.getItem(i).shrink(match.need()[i] * times);
            }
        }
        grid.setChanged();
        Crafting.give(server, output, recipe.count() * times);
        server.level().playSound(null, server.blockPosition(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 0.6f, 1.2f);
        broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player clicker, int index) {
        Slot from = slots.get(index);
        if (!from.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = from.getItem();
        ItemStack before = stack.copy();
        boolean moved = index < GRID_SIZE
                ? moveItemStackTo(stack, GRID_SIZE, slots.size(), true)
                : moveItemStackTo(stack, 0, GRID_SIZE, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            from.setByPlayer(ItemStack.EMPTY);
        } else {
            from.setChanged();
        }
        return before;
    }

    @Override
    public void removed(Player clicker) {
        super.removed(clicker);
        clearContainer(clicker, grid);
    }

    @Override
    public boolean stillValid(Player clicker) {
        return clicker.isAlive();
    }
}
