package com.skyrpg.skyblock.client.screen;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.skyblock.alchemy.BrewMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A brewing stand: the ingredient on top, three bottles under it, the brew's progress between them. */
public final class BrewScreen extends AbstractContainerScreen<BrewMenu> {
    private static final int WIDTH = 196;
    private static final int HEIGHT = 210;
    private static final int POTION = 0xFFB98CFF;
    private static final ScreenTheme THEME = ScreenTheme.MAGIC;

    public BrewScreen(BrewMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        SkyScreens.window(g, font, THEME, x, y, imageWidth, imageHeight, Component.literal("Brewing Stand"),
                Component.literal("Alchemy").withColor(POTION));
        // The stand: a card holding the ingredient well, the progress and the three bottle wells.
        int cardTop = y + SkyScreens.contentTop(THEME);
        int cardH = BrewMenu.BOTTLE_Y[1] + 18 + 6 - SkyScreens.contentTop(THEME);
        SkyScreens.card(g, THEME, x + THEME.inset, cardTop, imageWidth - 2 * THEME.inset, cardH, false, true);
        SkyScreens.well(g, THEME, x + BrewMenu.INGREDIENT_X - 1, y + BrewMenu.INGREDIENT_Y - 1);
        Draw.item(g, new ItemStack(Items.NETHER_WART), x + THEME.inset + 8, y + BrewMenu.INGREDIENT_Y, 1f);
        Draw.fit(g, font, Component.literal("Ingredient"), x + BrewMenu.INGREDIENT_X + 24, y + BrewMenu.INGREDIENT_Y + 5, 60, 0.7f, 0.45f,
                Draw.Align.LEFT, THEME.muted, false);
        for (int i = 0; i < 3; i++) {
            SkyScreens.well(g, THEME, x + BrewMenu.BOTTLE_X[i] - 1, y + BrewMenu.BOTTLE_Y[i] - 1);
        }
        Draw.fit(g, font, Component.literal("Bottles"), x + THEME.inset + 24, y + BrewMenu.BOTTLE_Y[1] + 6, 40, 0.7f, 0.45f, Draw.Align.CENTER,
                THEME.muted, false);
        float progress = menu.progress();
        int barW = imageWidth - 80;
        int barX = x + (imageWidth - barW) / 2;
        int barY = y + BrewMenu.INGREDIENT_Y + 30;
        String state = progress > 0 ? "Brewing... " + Math.round(progress * 100) + "%" : "Ingredient on top, bottles below";
        Draw.fit(g, font, Component.literal(state), x + imageWidth / 2, barY - 9, imageWidth - 30, 0.7f, 0.45f, Draw.Align.CENTER,
                progress > 0 ? POTION : THEME.dim, false);
        SkyScreens.bar(g, THEME, barX, barY, barW, 6, progress, POTION);
        SkyScreens.label(g, font, THEME, "Inventory", x + BrewMenu.INV_X, y + BrewMenu.INV_Y - 10);
        SkyScreens.inventoryWells(g, THEME, menu, 4, x, y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }
}
