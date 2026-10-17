package com.skyrpg.skyblock.gem;

import com.skyrpg.core.action.Actions;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.items.gem.Gemstones;
import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
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
 * Hypixel's Gemstone Grinder: put an item in and its gemstone slots show. Button {@code i} works slot {@code i}: unlock it
 * (its coins and gemstones), or put your best fitting gemstone from your inventory in it; {@link #REMOVE} {@code + i}
 * takes the gem out again for Hypixel's removal cost (1 coin for Rough up to 500,000 for Perfect).
 */
public final class GemGrinderMenu extends AbstractContainerMenu {
    public static final int REMOVE = 100;
    public static final int WIDTH = 236;
    public static final int PAD = 8;
    public static final int ITEM_X = 14;
    public static final int ITEM_Y = 40;
    public static final int INV_X = (WIDTH - 162) / 2 + 1;
    public static final int INV_Y = 108;
    public static final int HEIGHT = INV_Y + 3 * 18 + 4 + 18 + PAD;

    private final SimpleContainer input = new SimpleContainer(1);

    public GemGrinderMenu(int id, Inventory inventory) {
        super(SkyMenus.GEM_GRINDER, id);
        addSlot(new Slot(input, 0, ITEM_X + 1, ITEM_Y + 1) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addStandardInventorySlots(inventory, INV_X, INV_Y);
    }

    public static void init() {
        Actions.register("gemstone_grinder", (player, params) -> open(player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(Commands.literal("gemstones").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                    open(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new GemGrinderMenu(id, inventory), Component.literal("Gemstone Grinder")));
    }

    public ItemStack item() {
        return input.getItem(0);
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (!(clicker instanceof ServerPlayer player)) {
            return false;
        }
        ItemStack item = item();
        ItemDefinition def = SkyItems.definition(item);
        List<Gemstones.Slot> slots = Gemstones.slots(def);
        boolean remove = id >= REMOVE;
        int index = remove ? id - REMOVE : id;
        if (index < 0 || index >= slots.size()) {
            return false;
        }
        Gemstones.Slot slot = slots.get(index);
        Gemstones.Gem current = Gemstones.gems(item).stream().filter(gem -> gem.slot().equals(slot.id())).findFirst().orElse(null);
        String problem;
        if (remove) {
            problem = current == null ? "" : removeGem(player, item, slot, current);
        } else if (!Gemstones.unlocked(item, slot)) {
            problem = unlock(player, item, slot);
        } else if (current == null) {
            problem = apply(player, item, slot);
        } else {
            problem = "Right-click to take this gemstone out.";
        }
        if (!problem.isEmpty()) {
            player.sendOverlayMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
        }
        input.setChanged();
        broadcastChanges();
        return true;
    }

    private static String unlock(ServerPlayer player, ItemStack item, Gemstones.Slot slot) {
        for (String cost : slot.items()) {
            String[] parts = cost.split(":");
            if (SkyStacks.count(player.getInventory(), parts[0]) < Integer.parseInt(parts[1])) {
                return "You don't have what unlocking this slot costs!";
            }
        }
        if (slot.coins() > 0 && !Wallet.take(player, slot.coins())) {
            return "You need " + Msg.number(slot.coins()) + " coins to unlock this slot!";
        }
        for (String cost : slot.items()) {
            String[] parts = cost.split(":");
            SkyStacks.take(player.getInventory(), parts[0], Integer.parseInt(parts[1]));
        }
        Gemstones.unlock(item, slot);
        player.sendSystemMessage(Component.literal("You unlocked a gemstone slot!").withStyle(ChatFormatting.GREEN));
        player.level().playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
        return "";
    }

    /** The best gemstone in the inventory this slot takes (highest tier). */
    private static String apply(ServerPlayer player, ItemStack item, Gemstones.Slot slot) {
        String bestId = null;
        String[] best = null;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            String id = SkyStacks.id(player.getInventory().getItem(i));
            String[] gem = Gemstones.parseGem(id);
            if (gem == null || !slot.accepts().contains(gem[0])) {
                continue;
            }
            if (best == null || Gemstones.TIERS.indexOf(gem[1]) > Gemstones.TIERS.indexOf(best[1])) {
                best = gem;
                bestId = id;
            }
        }
        if (best == null) {
            return "You have no gemstone that fits this slot!";
        }
        SkyStacks.take(player.getInventory(), bestId, 1);
        Gemstones.set(item, slot, best[0], best[1]);
        player.sendSystemMessage(Component.literal("You put a ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(Gemstones.name(best[0], best[1])).withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" in your item!").withStyle(ChatFormatting.GREEN)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.2f);
        return "";
    }

    private static String removeGem(ServerPlayer player, ItemStack item, Gemstones.Slot slot, Gemstones.Gem gem) {
        long cost = Gemstones.removalCost(gem.tier());
        if (cost > 0 && !Wallet.take(player, cost)) {
            return "Taking it out costs " + Msg.number(cost) + " coins!";
        }
        Gemstones.set(item, slot, null, null);
        ItemDefinition def = ItemRegistry.skyblock(gem.tier() + "_" + gem.type() + "_GEM");
        if (def != null) {
            ItemStack back = SkyItems.create(def, 1);
            if (!player.getInventory().add(back)) {
                player.drop(back, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8f, 1.0f);
        return "";
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
                : input.getItem(0).isEmpty() && stack.getMaxStackSize() == 1 && moveItemStackTo(stack, 0, 1, false);
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
        clearContainer(clicker, input);
    }

    @Override
    public boolean stillValid(Player clicker) {
        return clicker.isAlive();
    }

    /** For the screen: the item's name in its rarity colour. */
    public static Component itemName(ItemStack item) {
        ItemDefinition def = SkyItems.definition(item);
        return def == null ? item.getHoverName() : Component.literal(ItemMods.name(item, def)).withColor(ItemMods.rarity(item, def).color());
    }
}
