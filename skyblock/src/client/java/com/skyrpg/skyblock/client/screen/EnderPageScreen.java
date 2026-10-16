package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.storage.EnderPageMenu;
import com.skyrpg.skyblock.storage.Storage;
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
 * An Ender Chest page, SkyRPG style: a tab per page down the left (the locked ones dimmed), the page's 45 slots, and
 * a Wardrobe tab. Switching tabs swaps the page in place, without closing the screen.
 */
public final class EnderPageScreen extends AbstractContainerScreen<EnderPageMenu> {
    private static final int TAB_STEP = EnderPageMenu.TAB_H + 2;
    private static final ItemStack WARDROBE_ICON = new ItemStack(Items.LEATHER_CHESTPLATE);

    public EnderPageScreen(EnderPageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, EnderPageMenu.WIDTH, EnderPageMenu.HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        int x = leftPos;
        int y = topPos;
        int page = menu.page();
        SkyScreens.window(g, font, x, y, imageWidth, imageHeight, Component.literal("Ender Chest"),
                Component.literal("Page " + (page + 1) + " of " + Math.max(1, menu.unlocked())));
        for (int i = 0; i < Storage.MAX_PAGES; i++) {
            int tx = x + EnderPageMenu.PAD;
            int ty = y + EnderPageMenu.TOP + i * TAB_STEP;
            boolean locked = i >= menu.unlocked();
            boolean current = i == page;
            boolean over = !locked && SkyScreens.inside(mouseX, mouseY, tx, ty, EnderPageMenu.TAB_W, EnderPageMenu.TAB_H);
            Draw.panel(g, tx, ty, EnderPageMenu.TAB_W, EnderPageMenu.TAB_H, locked ? Theme.TRACK : current || over ? Theme.CARD_HOVER : Theme.CARD,
                    current ? Theme.PURPLE : over ? Draw.mix(Theme.CARD_BORDER, Theme.PURPLE, 0.6f) : locked ? Theme.CARD : Theme.CARD_BORDER);
            if (current) {
                g.fill(tx, ty + 2, tx + 2, ty + EnderPageMenu.TAB_H - 2, Theme.PURPLE);
            }
            String number = Integer.toString(i + 1);
            Draw.text(g, font, number, tx + (EnderPageMenu.TAB_W - font.width(number)) / 2, ty + 5,
                    locked ? Theme.DIM : current ? Theme.PURPLE : Theme.MUTED);
        }
        int wy = wardrobeTabY();
        boolean overWardrobe = SkyScreens.inside(mouseX, mouseY, x + EnderPageMenu.PAD, wy, EnderPageMenu.TAB_W, EnderPageMenu.TAB_H);
        Draw.panel(g, x + EnderPageMenu.PAD, wy, EnderPageMenu.TAB_W, EnderPageMenu.TAB_H, overWardrobe ? Theme.CARD_HOVER : Theme.CARD,
                overWardrobe ? Theme.ACCENT : Theme.CARD_BORDER);
        Draw.item(g, WARDROBE_ICON, x + EnderPageMenu.PAD + (EnderPageMenu.TAB_W - 16) / 2, wy, 1f);

        // The page: a sunken panel holding the wells.
        Draw.panel(g, x + EnderPageMenu.GRID_X - 3, y + EnderPageMenu.GRID_Y - 3, 9 * 18 + 6, EnderPageMenu.ROWS * 18 + 6,
                0xFF0E1119, Draw.withAlpha(Theme.PURPLE, 0.35f));
        for (int i = 0; i < Storage.PAGE_SIZE; i++) {
            SkyScreens.well(g, x + EnderPageMenu.GRID_X + (i % 9) * 18, y + EnderPageMenu.GRID_Y + (i / 9) * 18);
        }
        SkyScreens.label(g, font, "Inventory", x + EnderPageMenu.INV_X, y + EnderPageMenu.INV_Y - 11);
        SkyScreens.inventoryWells(g, menu, Storage.PAGE_SIZE, x, y);
    }

    private int wardrobeTabY() {
        return topPos + EnderPageMenu.TOP + Storage.MAX_PAGES * TAB_STEP + 2;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        int tx = leftPos + EnderPageMenu.PAD;
        for (int i = 0; i < Storage.MAX_PAGES; i++) {
            if (SkyScreens.inside(mouseX, mouseY, tx, topPos + EnderPageMenu.TOP + i * TAB_STEP, EnderPageMenu.TAB_W, EnderPageMenu.TAB_H)) {
                boolean locked = i >= menu.unlocked();
                Draw.queueTooltip(List.of(Component.literal("Ender Chest " + (i + 1)).withColor(Theme.PURPLE),
                        Component.literal(locked ? "Unlocks with SkyBlock Leveling" : i == menu.page() ? "Open now" : "Click to open").withColor(Theme.MUTED)),
                        Theme.PURPLE);
            }
        }
        if (SkyScreens.inside(mouseX, mouseY, tx, wardrobeTabY(), EnderPageMenu.TAB_W, EnderPageMenu.TAB_H)) {
            Draw.queueTooltip(List.of(Component.literal("Wardrobe").withColor(Theme.ACCENT),
                    Component.literal("Your saved armour sets").withColor(Theme.MUTED)), Theme.ACCENT);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int tx = leftPos + EnderPageMenu.PAD;
            for (int i = 0; i < Storage.MAX_PAGES; i++) {
                if (SkyScreens.inside(event.x(), event.y(), tx, topPos + EnderPageMenu.TOP + i * TAB_STEP, EnderPageMenu.TAB_W, EnderPageMenu.TAB_H)) {
                    if (i < menu.unlocked() && i != menu.page()) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                        SkyScreens.click();
                    }
                    return true;
                }
            }
            if (SkyScreens.inside(event.x(), event.y(), tx, wardrobeTabY(), EnderPageMenu.TAB_W, EnderPageMenu.TAB_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, EnderPageMenu.WARDROBE_BUTTON);
                SkyScreens.click();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
