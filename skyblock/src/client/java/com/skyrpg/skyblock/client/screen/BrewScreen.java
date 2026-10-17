package com.skyrpg.skyblock.client.screen;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.alchemy.BrewMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A brewing stand: the ingredient on top, three bottles under it, the brew's progress between them. */
public final class BrewScreen extends AbstractContainerScreen<BrewMenu> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = 196;
    private static final int POTION = 0xFFB98CFF;

    public BrewScreen(BrewMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        SkyScreens.window(g, font, x, y, imageWidth, imageHeight, Component.literal("Brewing Stand"), Component.literal("Alchemy").withColor(POTION));
        SkyScreens.well(g, x + BrewMenu.INGREDIENT_X - 1, y + BrewMenu.INGREDIENT_Y - 1);
        for (int i = 0; i < 3; i++) {
            SkyScreens.well(g, x + BrewMenu.BOTTLE_X[i] - 1, y + BrewMenu.BOTTLE_Y[i] - 1);
        }
        float progress = menu.progress();
        int barX = x + 40;
        int barY = y + BrewMenu.INGREDIENT_Y + 30;
        String state = progress > 0 ? "Brewing... " + Math.round(progress * 100) + "%" : "Ingredient on top, bottles below";
        Draw.fit(g, font, Component.literal(state), x + imageWidth / 2, y + BrewMenu.INGREDIENT_Y + 20, imageWidth - 20, 0.6f, 0.45f,
                Draw.Align.CENTER, progress > 0 ? POTION : Theme.DIM, false);
        Draw.bar(g, barX, barY, imageWidth - 80, 3, progress, POTION);
        SkyScreens.label(g, font, "Inventory", x + BrewMenu.INV_X, y + BrewMenu.INV_Y - 10);
        SkyScreens.inventoryWells(g, menu, 4, x, y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }
}
