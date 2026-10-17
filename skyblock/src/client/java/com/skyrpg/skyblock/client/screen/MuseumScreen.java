package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.museum.MuseumMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Museum: category tabs with how many you've donated, then the category's donations as item cells: green when
 * donated, gold when you have it with you (click to donate), dim when you haven't found it. Hover for its SkyBlock XP.
 */
public final class MuseumScreen extends AbstractContainerScreen<MuseumMenu> {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 230;
    private static final int TABS_Y = 24;
    private static final int TAB_H = 22;
    private static final int GRID_Y = TABS_Y + 2 * TAB_H + 6;
    private static final int CELL = 22;
    private static final int COLUMNS = 12;
    private static final int GOLD = 0xFFFFAA00;

    private double scroll;

    public MuseumScreen(MuseumMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int tabX(int i) {
        return leftPos + 8 + (i % 4) * ((imageWidth - 16) / 4);
    }

    private int tabY(int i) {
        return topPos + TABS_Y + (i / 4) * TAB_H;
    }

    private int gridX() {
        return leftPos + (imageWidth - COLUMNS * CELL) / 2;
    }

    private int gridHeight() {
        return imageHeight - GRID_Y - 6;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        MuseumMenu.View view = menu.view();
        int done = view.done().stream().mapToInt(Integer::intValue).sum();
        int total = view.totals().stream().mapToInt(Integer::intValue).sum();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("Museum"),
                Component.literal(done + "/" + total + " donated").withColor(GOLD));
        int tabW = (imageWidth - 16) / 4 - 2;
        for (int i = 0; i < view.totals().size() && i < MuseumMenu.NAMES.size(); i++) {
            boolean selected = i == view.selected();
            boolean over = SkyScreens.inside(mouseX, mouseY, tabX(i), tabY(i), tabW, TAB_H - 2);
            Draw.panel(g, tabX(i), tabY(i), tabW, TAB_H - 2, selected ? Draw.mix(Theme.CARD, GOLD, 0.15f) : over ? Theme.CARD_HOVER : Theme.CARD,
                    selected ? GOLD : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal(MuseumMenu.NAMES.get(i)), tabX(i) + tabW / 2, tabY(i) + 2, tabW - 4, 0.7f, 0.45f, Draw.Align.CENTER,
                    selected ? GOLD : Theme.TEXT, false);
            Draw.fit(g, font, Component.literal(view.done().get(i) + "/" + view.totals().get(i)), tabX(i) + tabW / 2, tabY(i) + 11, tabW - 4, 0.6f,
                    0.4f, Draw.Align.CENTER, Theme.MUTED, false);
        }
        List<MuseumMenu.EntryView> entries = view.entries();
        int top = topPos + GRID_Y;
        int height = gridHeight();
        int rows = (entries.size() + COLUMNS - 1) / COLUMNS;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows * CELL - height));
        List<Component> tooltip = null;
        g.enableScissor(leftPos + 4, top, leftPos + imageWidth - 4, top + height);
        for (int i = 0; i < entries.size(); i++) {
            MuseumMenu.EntryView entry = entries.get(i);
            int x = gridX() + (i % COLUMNS) * CELL;
            int y = top + (i / COLUMNS) * CELL - (int) Math.round(scroll);
            if (y + CELL < top || y > top + height) {
                continue;
            }
            boolean over = SkyScreens.inside(mouseX, mouseY, x, y, CELL - 2, CELL - 2) && mouseY >= top && mouseY < top + height;
            int border = entry.state() == 2 ? Theme.GREEN : entry.state() == 1 ? GOLD : Draw.mix(Theme.CARD, Theme.CARD_BORDER, 0.4f);
            Draw.panel(g, x, y, CELL - 2, CELL - 2, over ? Theme.CARD_HOVER : entry.state() == 2 ? Draw.mix(Theme.CARD, Theme.GREEN, 0.12f) : Theme.CARD,
                    border);
            if (entry.state() > 0) {
                Draw.item(g, entry.icon(), x + 2, y + 2, 1f);
            } else {
                Draw.fit(g, font, Component.literal("?"), x + (CELL - 2) / 2, y + 6, CELL - 4, 1f, 0.6f, Draw.Align.CENTER, Theme.DIM, false);
            }
            if (over) {
                tooltip = new ArrayList<>();
                tooltip.add(entry.state() > 0 ? Component.literal(entry.name()).withColor(GOLD) : Component.literal("§8???"));
                if (entry.pieces() > 1) {
                    tooltip.add(Component.literal("§7Armor set: " + entry.pieces() + " pieces"));
                }
                if (entry.xp() > 0) {
                    tooltip.add(Component.literal("§7Donation: §b+" + entry.xp() + " SkyBlock XP"));
                }
                if (!entry.stage().isEmpty()) {
                    tooltip.add(Component.literal("§8" + entry.stage().charAt(0) + entry.stage().substring(1).toLowerCase(java.util.Locale.ROOT)));
                }
                tooltip.add(Component.empty());
                tooltip.add(Component.literal(entry.state() == 2 ? "§aDonated!" : entry.state() == 1 ? "§eClick to donate it!"
                        : "§7Bring " + (entry.pieces() > 1 ? "the whole set" : "it") + " to donate it."));
            }
        }
        g.disableScissor();
        if (rows * CELL > height) {
            Draw.scrollbar(g, leftPos + imageWidth - 5, top, height, rows * CELL, scroll, GOLD);
        }
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, GOLD);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int tabW = (imageWidth - 16) / 4 - 2;
            for (int i = 0; i < menu.view().totals().size(); i++) {
                if (SkyScreens.inside(event.x(), event.y(), tabX(i), tabY(i), tabW, TAB_H - 2)) {
                    if (i != menu.view().selected()) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                        SkyScreens.click();
                    }
                    return true;
                }
            }
            int top = topPos + GRID_Y;
            if (event.y() >= top && event.y() < top + gridHeight()) {
                int column = (int) ((event.x() - gridX()) / CELL);
                int row = (int) ((event.y() - top + scroll) / CELL);
                int index = row * COLUMNS + column;
                if (column >= 0 && column < COLUMNS && index >= 0 && index < menu.view().entries().size()
                        && menu.view().entries().get(index).state() == 1) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MuseumMenu.ENTRY + index);
                    SkyScreens.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * 22;
        return true;
    }
}
