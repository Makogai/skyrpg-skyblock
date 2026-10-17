package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.dungeon.PartyFinder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The Party Finder: the listed parties (click one to join), and listing your own for a floor. */
public final class PartyFinderScreen extends AbstractContainerScreen<PartyFinder> {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 200;
    private static final int RED = 0xFFE04848;
    private static final int ROWS = 9;

    public PartyFinderScreen(PartyFinder menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int rowY(int i) {
        return topPos + 32 + i * 14;
    }

    private int bottomY() {
        return topPos + imageHeight - 26;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        PartyFinder.View view = menu.view();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("Party Finder"),
                Component.literal(view.rows().size() + " listed").withColor(RED));
        if (view.rows().isEmpty()) {
            Draw.fit(g, font, Component.literal("Nobody's listed right now. List your party below!"), leftPos + imageWidth / 2, topPos + 70, imageWidth - 20,
                    0.8f, 0.5f, Draw.Align.CENTER, Theme.MUTED, false);
        }
        for (int i = 0; i < Math.min(ROWS, view.rows().size()); i++) {
            PartyFinder.Row row = view.rows().get(i);
            int y = rowY(i);
            boolean over = row.joinable() && SkyScreens.inside(mouseX, mouseY, leftPos + 10, y, imageWidth - 20, 12);
            Draw.panel(g, leftPos + 10, y, imageWidth - 20, 12, over ? Theme.CARD_HOVER : Theme.CARD, row.joinable() ? RED : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal(row.text()), leftPos + 14, y + 3, imageWidth - 70, 0.65f, 0.4f, Draw.Align.LEFT, Theme.TEXT, false);
            Draw.fit(g, font, Component.literal(row.joinable() ? "Join" : "Yours"), leftPos + imageWidth - 30, y + 3, 40, 0.65f, 0.4f, Draw.Align.CENTER,
                    row.joinable() ? Theme.GREEN : Theme.DIM, false);
        }
        int y = bottomY();
        button(g, mouseX, mouseY, leftPos + 10, y, 16, "◀", Theme.MUTED);
        Draw.fit(g, font, Component.literal(view.floor()), leftPos + 46, y + 4, 30, 0.8f, 0.5f, Draw.Align.CENTER, RED, false);
        button(g, mouseX, mouseY, leftPos + 64, y, 16, "▶", Theme.MUTED);
        button(g, mouseX, mouseY, leftPos + 90, y, 110, view.listed() ? "Update listing" : "List my party", Theme.GREEN);
        if (view.listed()) {
            button(g, mouseX, mouseY, leftPos + 206, y, 84, "Unlist", RED);
        }
    }

    private void button(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, String text, int color) {
        boolean over = SkyScreens.inside(mouseX, mouseY, x, y, w, 16);
        Draw.panel(g, x, y, w, 16, over ? Theme.CARD_HOVER : Theme.CARD, color);
        Draw.fit(g, font, Component.literal(text), x + w / 2, y + 4, w - 4, 0.75f, 0.5f, Draw.Align.CENTER, color, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            double mx = event.x();
            double my = event.y();
            PartyFinder.View view = menu.view();
            for (int i = 0; i < Math.min(ROWS, view.rows().size()); i++) {
                if (view.rows().get(i).joinable() && SkyScreens.inside(mx, my, leftPos + 10, rowY(i), imageWidth - 20, 12)) {
                    return press(PartyFinder.JOIN + i);
                }
            }
            int y = bottomY();
            if (SkyScreens.inside(mx, my, leftPos + 10, y, 16, 16)) {
                return press(PartyFinder.FLOOR_PREV);
            }
            if (SkyScreens.inside(mx, my, leftPos + 64, y, 16, 16)) {
                return press(PartyFinder.FLOOR_NEXT);
            }
            if (SkyScreens.inside(mx, my, leftPos + 90, y, 110, 16)) {
                return press(PartyFinder.LIST);
            }
            if (view.listed() && SkyScreens.inside(mx, my, leftPos + 206, y, 84, 16)) {
                return press(PartyFinder.UNLIST);
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean press(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        SkyScreens.click();
        return true;
    }
}
