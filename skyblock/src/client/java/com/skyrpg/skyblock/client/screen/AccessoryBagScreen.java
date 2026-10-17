package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.accessory.AccessoryBag;
import com.skyrpg.skyblock.accessory.AccessoryBagMenu;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Accessory Bag: a tab per page down the left, the page's slots (the ones past the bag's size locked), your
 * Magical Power at the top, and a tab to Maxwell's powers.
 */
public final class AccessoryBagScreen extends AbstractContainerScreen<AccessoryBagMenu> {
    private static final int TAB_STEP = AccessoryBagMenu.TAB_H + 2;
    private static final int GOLD = 0xFFFFAA00;
    private static final ItemStack MAXWELL_ICON = new ItemStack(Items.AMETHYST_SHARD);

    public AccessoryBagScreen(AccessoryBagMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AccessoryBagMenu.WIDTH, AccessoryBagMenu.HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        int x = leftPos;
        int y = topPos;
        int page = menu.page();
        SkyScreens.window(g, font, x, y, imageWidth, imageHeight, Component.literal("Accessory Bag"),
                Component.literal(menu.magicalPower() + " Magical Power").withColor(GOLD));
        for (int i = 0; i < menu.pages(); i++) {
            int tx = x + AccessoryBagMenu.PAD;
            int ty = y + AccessoryBagMenu.TOP + i * TAB_STEP;
            boolean current = i == page;
            boolean over = SkyScreens.inside(mouseX, mouseY, tx, ty, AccessoryBagMenu.TAB_W, AccessoryBagMenu.TAB_H);
            Draw.panel(g, tx, ty, AccessoryBagMenu.TAB_W, AccessoryBagMenu.TAB_H, current || over ? Theme.CARD_HOVER : Theme.CARD,
                    current ? GOLD : over ? Draw.mix(Theme.CARD_BORDER, GOLD, 0.6f) : Theme.CARD_BORDER);
            if (current) {
                g.fill(tx, ty + 2, tx + 2, ty + AccessoryBagMenu.TAB_H - 2, GOLD);
            }
            String number = Integer.toString(i + 1);
            Draw.text(g, font, number, tx + (AccessoryBagMenu.TAB_W - font.width(number)) / 2, ty + 5, current ? GOLD : Theme.MUTED);
        }
        int my = maxwellTabY();
        boolean overMaxwell = SkyScreens.inside(mouseX, mouseY, x + AccessoryBagMenu.PAD, my, AccessoryBagMenu.TAB_W, AccessoryBagMenu.TAB_H);
        Draw.panel(g, x + AccessoryBagMenu.PAD, my, AccessoryBagMenu.TAB_W, AccessoryBagMenu.TAB_H, overMaxwell ? Theme.CARD_HOVER : Theme.CARD,
                overMaxwell ? Theme.PURPLE : Theme.CARD_BORDER);
        Draw.item(g, MAXWELL_ICON, x + AccessoryBagMenu.PAD + (AccessoryBagMenu.TAB_W - 16) / 2, my, 1f);

        Draw.panel(g, x + AccessoryBagMenu.GRID_X - 3, y + AccessoryBagMenu.GRID_Y - 3, 9 * 18 + 6, AccessoryBagMenu.ROWS * 18 + 6,
                0xFF0E1119, Draw.withAlpha(GOLD, 0.35f));
        for (int i = 0; i < AccessoryBag.PAGE_SIZE; i++) {
            int sx = x + AccessoryBagMenu.GRID_X + (i % 9) * 18;
            int sy = y + AccessoryBagMenu.GRID_Y + (i / 9) * 18;
            SkyScreens.well(g, sx, sy);
            if (!menu.open(i)) {
                // Locked: dimmed, with a small bar.
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xC00A0C12);
                g.fill(sx + 6, sy + 8, sx + 12, sy + 10, 0xFF3A3F4E);
            }
        }
        SkyScreens.label(g, font, "Inventory", x + AccessoryBagMenu.INV_X, y + AccessoryBagMenu.INV_Y - 11);
        SkyScreens.inventoryWells(g, menu, AccessoryBag.PAGE_SIZE, x, y);
    }

    private int maxwellTabY() {
        return topPos + AccessoryBagMenu.TOP + menu.pages() * TAB_STEP + 2;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (menu.getCarried().isEmpty()) {
            int tx = leftPos + AccessoryBagMenu.PAD;
            if (SkyScreens.inside(mouseX, mouseY, tx, maxwellTabY(), AccessoryBagMenu.TAB_W, AccessoryBagMenu.TAB_H)) {
                Draw.queueTooltip(List.of(Component.literal("Accessory Powers").withColor(Theme.PURPLE),
                        Component.literal("Maxwell's powers, scaled by your Magical Power").withColor(Theme.MUTED)), Theme.PURPLE);
            }
            int gx = leftPos + AccessoryBagMenu.GRID_X;
            int gy = topPos + AccessoryBagMenu.GRID_Y;
            if (mouseX >= gx && mouseX < gx + 9 * 18 && mouseY >= gy && mouseY < gy + AccessoryBagMenu.ROWS * 18) {
                int slot = (mouseY - gy) / 18 * 9 + (mouseX - gx) / 18;
                if (!menu.open(slot)) {
                    Draw.queueTooltip(List.of(Component.literal("Locked slot").withColor(Theme.RED),
                            Component.literal("More slots come with the Redstone collection").withColor(Theme.MUTED)), Theme.RED);
                }
            }
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int tx = leftPos + AccessoryBagMenu.PAD;
            for (int i = 0; i < menu.pages(); i++) {
                if (SkyScreens.inside(event.x(), event.y(), tx, topPos + AccessoryBagMenu.TOP + i * TAB_STEP, AccessoryBagMenu.TAB_W, AccessoryBagMenu.TAB_H)) {
                    if (i != menu.page()) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                        SkyScreens.click();
                    }
                    return true;
                }
            }
            if (SkyScreens.inside(event.x(), event.y(), tx, maxwellTabY(), AccessoryBagMenu.TAB_W, AccessoryBagMenu.TAB_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, AccessoryBagMenu.MAXWELL);
                SkyScreens.click();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
