package com.skyrpg.skyblock.enchant;

import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.enchant.Enchants;
import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
 * Hypixel's Enchantment Table ("Enchant Item"): put a weapon, armour piece or tool in, pick one of the enchants it takes
 * and a level up to the table's top, and pay its XP levels. A conflicting enchant comes off; picking the level it already
 * has takes it off (for the same XP). Every level spent gives Enchanting XP ({@code 3.5 × levels^1.5}).
 * The button id is {@code index * 16 + level}, the index into {@link Enchants#table} for the item.
 */
public final class EnchantMenu extends AbstractContainerMenu {
    public static final Identifier ENCHANTING = Identifier.fromNamespaceAndPath("skyrpg_skills", "enchanting");
    // Layout, shared with the screen.
    public static final int WIDTH = 320;
    public static final int PAD = 10;
    public static final int TOP = 36;
    public static final int ITEM_X = 16;
    public static final int ITEM_Y = TOP + 18;
    public static final int LIST_X = 44;
    public static final int LIST_W = 140;
    public static final int DETAIL_X = LIST_X + LIST_W + 6;
    public static final int LIST_Y = TOP + 4;
    public static final int LIST_H = 90;
    public static final int INV_X = (WIDTH - 162) / 2 + 1;
    public static final int INV_Y = LIST_Y + LIST_H + 16;
    public static final int HEIGHT = INV_Y + 3 * 18 + 4 + 18 + PAD;

    private final SimpleContainer slot = new SimpleContainer(1);

    public EnchantMenu(int id, Inventory inventory) {
        super(SkyMenus.ENCHANT, id);
        addSlot(new Slot(slot, 0, ITEM_X + 1, ITEM_Y + 1) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addStandardInventorySlots(inventory, INV_X, INV_Y);
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new EnchantMenu(id, inventory), Component.literal("Enchant Item")));
    }

    public ItemStack item() {
        return slot.getItem(0);
    }

    /** The enchants the item in the slot takes at the table, as listed. */
    public List<Enchants.Enchant> options() {
        ItemDefinition def = SkyItems.definition(item());
        return def == null || !def.skyblock() ? List.of() : Enchants.table(def);
    }

    /** Enchanting XP for spending this many levels (Hypixel's 3.5 × levels^1.5). */
    public static double enchantingXp(int levels) {
        return 3.5 * Math.pow(levels, 1.5);
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (!(clicker instanceof ServerPlayer player)) {
            return false;
        }
        List<Enchants.Enchant> options = options();
        int index = id / 16;
        int level = id % 16;
        if (index < 0 || index >= options.size()) {
            return false;
        }
        Enchants.Enchant enchant = options.get(index);
        if (level < 1 || level > enchant.tableMax()) {
            return false;
        }
        ItemStack item = item();
        int has = ItemMods.enchantment(item, enchant.id());
        if (has > enchant.tableMax()) {
            player.sendOverlayMessage(Component.literal(enchant.label(has) + " is above what the table can do.").withStyle(ChatFormatting.RED));
            return true;
        }
        int cost = enchant.xpCost(level);
        if (!player.isCreative() && player.experienceLevel < cost) {
            player.sendOverlayMessage(Component.literal("You need " + cost + " XP levels.").withStyle(ChatFormatting.RED));
            return true;
        }
        boolean removing = has == level;
        ItemMods.setEnchantment(item, enchant.id(), removing ? 0 : level);
        slot.setChanged();
        if (!player.isCreative()) {
            player.giveExperienceLevels(-cost);
        }
        SkillLevels.giveXp(player, ENCHANTING, enchantingXp(cost));
        player.level().playSound(null, player.blockPosition(), removing ? SoundEvents.GRINDSTONE_USE : SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.8f, 1.0f);
        player.sendOverlayMessage(Component.literal(removing ? "Removed " : "Enchanted: ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(enchant.label(level)).withStyle(enchant.ultimate() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.BLUE)));
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
        boolean moved = index == 0 ? moveItemStackTo(stack, 1, slots.size(), true)
                : slot.getItem(0).isEmpty() && stack.getMaxStackSize() == 1 && moveItemStackTo(stack, 0, 1, false);
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
        clearContainer(clicker, slot);
    }

    @Override
    public boolean stillValid(Player clicker) {
        return clicker.isAlive();
    }
}
