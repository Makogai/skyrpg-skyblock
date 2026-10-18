package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
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
    private static final ScreenTheme THEME = ScreenTheme.SKYBLOCK;
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
        SkyScreens.window(g, font, THEME, x, y, imageWidth, imageHeight, Component.literal("Ender Chest"),
                Component.literal("Page " + (page + 1) + " of " + Math.max(1, menu.unlocked())));
        for (int i = 0; i < Storage.MAX_PAGES; i++) {
            int tx = x + EnderPageMenu.PAD;
            int ty = y + EnderPageMenu.TOP + i * TAB_STEP;
            boolean locked = i >= menu.unlocked();
            boolean current = i == page;
            boolean over = !locked && SkyScreens.inside(mouseX, mouseY, tx, ty, EnderPageMenu.TAB_W, EnderPageMenu.TAB_H);
            SkyScreens.tab(g, font, THEME, tx, ty, EnderPageMenu.TAB_W, EnderPageMenu.TAB_H, Integer.toString(i + 1), current, over);
            if (locked) {
                g.fill(tx + 1, ty + 1, tx + EnderPageMenu.TAB_W - 1, ty + EnderPageMenu.TAB_H - 1, 0xAA0A0D12);
            }
        }
        int wy = wardrobeTabY();
        boolean overWardrobe = SkyScreens.inside(mouseX, mouseY, x + EnderPageMenu.PAD, wy, EnderPageMenu.TAB_W, EnderPageMenu.TAB_H);
        SkyScreens.tab(g, font, THEME, x + EnderPageMenu.PAD, wy, EnderPageMenu.TAB_W, EnderPageMenu.TAB_H, "", false, overWardrobe);
        Draw.item(g, WARDROBE_ICON, x + EnderPageMenu.PAD + (EnderPageMenu.TAB_W - 12) / 2, wy + 2, 0.75f);

        // The page: a well under every slot.
        for (int i = 0; i < Storage.PAGE_SIZE; i++) {
            SkyScreens.well(g, THEME, x + EnderPageMenu.GRID_X + (i % 9) * 18, y + EnderPageMenu.GRID_Y + (i / 9) * 18);
        }
        int labelY = y + EnderPageMenu.INV_Y - 11;
        SkyScreens.label(g, font, THEME, "Inventory", x + EnderPageMenu.INV_X, labelY);
        // How many pages you've unlocked, as a small bar.
        int unlocked = Math.max(1, menu.unlocked());
        Draw.fit(g, font, Component.literal("Pages " + unlocked + "/" + Storage.MAX_PAGES), x + EnderPageMenu.INV_X + 162 - 62, labelY, 56, 0.7f,
                0.5f, Draw.Align.RIGHT, THEME.muted, false);
        SkyScreens.bar(g, THEME, x + EnderPageMenu.INV_X + 162 - 58, labelY + 1, 58, 6, (float) unlocked / Storage.MAX_PAGES, THEME.accent);
        SkyScreens.inventoryWells(g, THEME, menu, Storage.PAGE_SIZE, x, y);
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
                Draw.queueTooltip(List.of(Component.literal("Ender Chest " + (i + 1)).withColor(THEME.accent),
                        Component.literal(locked ? "Unlocks with SkyBlock Leveling" : i == menu.page() ? "Open now" : "Click to open").withColor(THEME.muted)),
                        THEME.accent);
            }
        }
        if (SkyScreens.inside(mouseX, mouseY, tx, wardrobeTabY(), EnderPageMenu.TAB_W, EnderPageMenu.TAB_H)) {
            Draw.queueTooltip(List.of(Component.literal("Wardrobe").withColor(THEME.accent),
                    Component.literal("Your saved armour sets").withColor(THEME.muted)), THEME.accent);
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
