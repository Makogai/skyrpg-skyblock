package com.skyrpg.skyblock.client.screen;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.quiver.Quiver;
import com.skyrpg.skyblock.quiver.QuiverMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The Quiver: its arrows, the type your bows shoot, and the inventory. */
public final class QuiverScreen extends AbstractContainerScreen<QuiverMenu> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 220;

    public QuiverScreen(QuiverMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        SkyScreens.window(g, font, x, y, imageWidth, imageHeight, Component.literal("Quiver"), Component.literal("Arrows").withColor(Theme.AQUA));
        ItemStack ammo = minecraft.player == null ? ItemStack.EMPTY : Quiver.ammo(minecraft.player);
        Component shooting = ammo.isEmpty() ? Component.literal("No arrows: put some in to shoot your bows").withColor(Theme.DIM)
                : Component.literal("Shooting: ").withColor(Theme.MUTED).append(ammo.getHoverName()).append(Component.literal("  (Arrow Swapper to change)")
                        .withColor(Theme.DIM));
        Draw.fit(g, font, shooting, x + 8, y + 20, imageWidth - 16, 0.65f, 0.45f, Draw.Align.LEFT, Theme.TEXT, false);
        for (int i = 0; i < Quiver.SIZE; i++) {
            SkyScreens.well(g, x + QuiverMenu.GRID_X + (i % 9) * 18, y + QuiverMenu.GRID_Y + (i / 9) * 18);
        }
        SkyScreens.label(g, font, "Inventory", x + QuiverMenu.INV_X, y + QuiverMenu.INV_Y - 10);
        SkyScreens.inventoryWells(g, menu, Quiver.SIZE, x, y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }
}
