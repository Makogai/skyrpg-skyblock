package com.skyrpg.skyblock.reforge;

import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Coins;
import com.skyrpg.economy.Purse;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.reforge.Reforges;
import com.skyrpg.skyblock.storage.SkyMenus;
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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Blacksmith's "Reforge Item" and the anvil's "Combine Items" ({@link ItemWork}): an item slot, a second slot (a
 * reforge stone, or a Recombobulator 3000 or Potato Book) and one button. Both slots go back to you when you close it.
 */
public final class ItemWorkMenu extends AbstractContainerMenu {
    public static final int GO = 0;
    public static final int ITEM = 0;
    public static final int EXTRA = 1;
    // Layout, shared with the screen.
    public static final int WIDTH = 236;
    public static final int PAD = 10;
    public static final int TOP = 36;
    public static final int ITEM_X = 30;
    public static final int EXTRA_X = 30;
    public static final int ITEM_Y = TOP + 10;
    public static final int EXTRA_Y = ITEM_Y + 34;
    public static final int INV_X = (WIDTH - 162) / 2 + 1;
    public static final int INV_Y = EXTRA_Y + 18 + 24;
    public static final int HEIGHT = INV_Y + 3 * 18 + 4 + 18 + PAD;

    private final ItemWork.Mode mode;
    private final SimpleContainer inputs = new SimpleContainer(2);

    public ItemWorkMenu(MenuType<ItemWorkMenu> type, int id, Inventory inventory, ItemWork.Mode mode) {
        super(type, id);
        this.mode = mode;
        addSlot(new Slot(inputs, ITEM, ITEM_X + 1, ITEM_Y + 1) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new Slot(inputs, EXTRA, EXTRA_X + 1, EXTRA_Y + 1));
        addStandardInventorySlots(inventory, INV_X, INV_Y);
    }

    public static void open(ServerPlayer player, ItemWork.Mode mode) {
        MenuType<ItemWorkMenu> type = mode == ItemWork.Mode.REFORGE ? SkyMenus.REFORGE : SkyMenus.ANVIL;
        String title = mode == ItemWork.Mode.REFORGE ? "Reforge Item" : "Combine Items";
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ItemWorkMenu(type, id, inventory, mode), Component.literal(title)));
    }

    public ItemWork.Mode mode() {
        return mode;
    }

    public ItemStack item() {
        return inputs.getItem(ITEM);
    }

    public ItemStack extra() {
        return inputs.getItem(EXTRA);
    }

    public ItemWork.Preview preview() {
        return ItemWork.preview(mode, item(), extra());
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (!(clicker instanceof ServerPlayer player) || id != GO) {
            return false;
        }
        ItemWork.Preview preview = preview();
        if (!preview.ok()) {
            player.sendOverlayMessage(Component.literal(preview.problem()).withStyle(ChatFormatting.RED));
            return true;
        }
        if (preview.levels() > 0 && !player.isCreative() && player.experienceLevel < preview.levels()) {
            player.sendOverlayMessage(Component.literal("You need " + preview.levels() + " XP levels.").withStyle(ChatFormatting.RED));
            return true;
        }
        if (preview.coins() > 0 && !Purse.trySpend(player, preview.coins())) {
            player.sendOverlayMessage(Component.literal("You need ").withStyle(ChatFormatting.RED).append(Coins.text(preview.coins()))
                    .append(Component.literal(" coins in your purse.").withStyle(ChatFormatting.RED)));
            return true;
        }
        ItemStack item = item();
        String before = ItemMods.name(item, SkyItems.definition(item));
        if (mode == ItemWork.Mode.REFORGE && preview.result() == null) {
            Reforges.Reforge rolled = ItemWork.roll(item, player.getRandom());
            ItemMods.setReforge(item, rolled);
        } else {
            inputs.setItem(ITEM, preview.result());
            extra().shrink(1);
        }
        inputs.setChanged();
        if (preview.levels() > 0) {
            if (!player.isCreative()) {
                player.giveExperienceLevels(-preview.levels());
            }
            com.skyrpg.core.skills.SkillLevels.giveXp(player, com.skyrpg.skyblock.enchant.EnchantMenu.ENCHANTING,
                    com.skyrpg.skyblock.enchant.EnchantMenu.enchantingXp(preview.levels()));
        }
        ItemStack after = item();
        int color = ItemMods.rarity(after, SkyItems.definition(after)).color();
        if (mode == ItemWork.Mode.REFORGE) {
            player.sendSystemMessage(Component.literal("You reforged your ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(before).withColor(color))
                    .append(Component.literal(" into a ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(ItemMods.name(after, SkyItems.definition(after))).withColor(color))
                    .append(Component.literal("!").withStyle(ChatFormatting.GREEN)));
            player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7f, 1.1f);
        } else {
            player.sendOverlayMessage(Msg.text(preview.action() + ": ", ChatFormatting.GREEN)
                    .append(Component.literal(ItemMods.name(after, SkyItems.definition(after))).withColor(color)));
            player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7f, 1.0f);
        }
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
        boolean moved;
        if (index < 2) {
            moved = moveItemStackTo(stack, 2, slots.size(), true);
        } else if (inputs.getItem(ITEM).isEmpty() && SkyItems.definition(stack) != null && stack.getMaxStackSize() == 1) {
            moved = moveItemStackTo(stack, ITEM, ITEM + 1, false);
        } else {
            moved = moveItemStackTo(stack, EXTRA, EXTRA + 1, false);
        }
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
        clearContainer(clicker, inputs);
    }

    @Override
    public boolean stillValid(Player clicker) {
        return clicker.isAlive();
    }
}
