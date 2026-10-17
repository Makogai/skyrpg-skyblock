package com.skyrpg.skyblock.alchemy;

import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.skyblock.mixin.BrewingStandAccess;
import com.skyrpg.skyblock.storage.SkyMenus;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;

/**
 * A brewing stand in SkyBlock: three bottle slots and the ingredient slot of the stand itself (the brew keeps going with
 * the menu closed), and its brew time. Taking a brewed potion out pays the Alchemy XP it carries.
 */
public final class BrewMenu extends AbstractContainerMenu {
    public static final Identifier ALCHEMY = Identifier.fromNamespaceAndPath("skyrpg_skills", "alchemy");
    public static final int[] BOTTLE_X = {56, 79, 102};
    public static final int[] BOTTLE_Y = {64, 71, 64};
    public static final int INGREDIENT_X = 79;
    public static final int INGREDIENT_Y = 26;
    public static final int INV_X = 8;
    public static final int INV_Y = 112;
    private static final int STAND_SLOTS = 4;

    private final Container stand;
    private final ContainerData data;

    public BrewMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(5), new SimpleContainerData(4));
    }

    private BrewMenu(int id, Inventory inventory, Container stand, ContainerData data) {
        super(SkyMenus.BREW, id);
        this.stand = stand;
        this.data = data;
        for (int i = 0; i < 3; i++) {
            addSlot(new Slot(stand, i, BOTTLE_X[i], BOTTLE_Y[i]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return Potions.read(stack) != null;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public void onTake(Player player, ItemStack stack) {
                    payXp(player, stack);
                    super.onTake(player, stack);
                }
            });
        }
        addSlot(new Slot(stand, SkyBrewing.INGREDIENT, INGREDIENT_X, INGREDIENT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                String id = SkyBrewing.ingredientId(stack);
                return id != null && Potions.ingredient(id);
            }
        });
        addStandardInventorySlots(inventory, INV_X, INV_Y);
        addDataSlots(data);
    }

    public static void open(ServerPlayer player, BrewingStandBlockEntity stand) {
        ContainerData data = ((BrewingStandAccess) stand).skyrpg$data();
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new BrewMenu(id, inventory, stand, data), Component.literal("Brewing Stand")));
    }

    /** How far the brew is, 0 to 1 (0 when nothing brews). */
    public float progress() {
        int left = data.get(BrewingStandBlockEntity.DATA_BREW_TIME);
        return left <= 0 ? 0 : 1 - left / (float) SkyBrewing.BREW_TICKS;
    }

    private static void payXp(Player player, ItemStack stack) {
        Potions.Brew brew = Potions.read(stack);
        if (brew == null || brew.alchemyXp() <= 0) {
            return;
        }
        if (player instanceof ServerPlayer server) {
            SkillLevels.giveXp(server, ALCHEMY, brew.alchemyXp() * stack.getCount());
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove("alchemy_xp"));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot from = slots.get(index);
        if (!from.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = from.getItem();
        if (index < SkyBrewing.INGREDIENT) {
            // Paid on the stack that moves, so the copy below (and the slot's onTake) carry no XP to pay twice.
            payXp(player, stack);
        }
        ItemStack before = stack.copy();
        if (index < STAND_SLOTS) {
            if (!moveItemStackTo(stack, STAND_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            from.onQuickCraft(stack, before);
        } else {
            String id = SkyBrewing.ingredientId(stack);
            boolean moved = id != null && Potions.ingredient(id) && moveItemStackTo(stack, SkyBrewing.INGREDIENT, STAND_SLOTS, false)
                    || Potions.read(stack) != null && moveItemStackTo(stack, 0, SkyBrewing.INGREDIENT, false);
            if (!moved) {
                int hotbar = slots.size() - 9;
                if (index < hotbar ? !moveItemStackTo(stack, hotbar, slots.size(), false) : !moveItemStackTo(stack, STAND_SLOTS, hotbar, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) {
            from.setByPlayer(ItemStack.EMPTY);
        } else {
            from.setChanged();
        }
        if (stack.getCount() == before.getCount()) {
            return ItemStack.EMPTY;
        }
        from.onTake(player, before);
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return stand.stillValid(player);
    }
}
