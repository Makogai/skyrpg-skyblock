package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.sack.SacksMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Your Sacks: a section per sack type you have (its size per item), its items in a grid with how many you hold (dim when
 * none). Click an item to take a stack out, shift-click to take as much as fits; the button on top puts everything in
 * your inventory that the sacks hold away.
 */
public final class SacksScreen extends AbstractContainerScreen<SacksMenu> {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 230;
    private static final int TOP = 40;
    private static final int CELL = 26;
    private static final int COLUMNS = 10;
    private static final int HEADER = 14;
    private static final int GOLD = 0xFFFFAA00;

    private double scroll;

    public SacksScreen(SacksMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int gridX() {
        return leftPos + (imageWidth - COLUMNS * CELL) / 2;
    }

    private int listTop() {
        return topPos + TOP;
    }

    private int listHeight() {
        return imageHeight - TOP - 6;
    }

    /** Where each section starts (content y, before scrolling). */
    private List<Integer> sectionTops() {
        List<Integer> tops = new ArrayList<>();
        int y = 0;
        for (SacksMenu.TypeView type : menu.view().types()) {
            tops.add(y);
            y += HEADER + (int) Math.ceil(type.items().size() / (double) COLUMNS) * CELL + 6;
        }
        tops.add(y);
        return tops;
    }

    private boolean overInsert(double mx, double my) {
        return SkyScreens.inside(mx, my, leftPos + imageWidth - 92, topPos + 22, 84, 13);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        SacksMenu.View view = menu.view();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("Sacks"),
                Component.literal(view.types().size() + " sack" + (view.types().size() == 1 ? "" : "s")).withColor(Theme.MUTED));
        boolean overButton = overInsert(mouseX, mouseY);
        Draw.panel(g, leftPos + imageWidth - 92, topPos + 22, 84, 13, overButton ? Theme.CARD_HOVER : Theme.CARD, overButton ? GOLD : Theme.CARD_BORDER);
        Draw.fit(g, font, Component.literal("Insert inventory"), leftPos + imageWidth - 50, topPos + 25, 80, 0.7f, 0.5f, Draw.Align.CENTER, GOLD, false);
        if (view.types().isEmpty()) {
            Draw.fit(g, font, Component.literal("You have no sacks yet."), leftPos + imageWidth / 2, topPos + 80, imageWidth - 20, 1f, 0.6f,
                    Draw.Align.CENTER, Theme.TEXT, false);
            Draw.fit(g, font, Component.literal("Craft one (Small Mining Sack...) and right-click it to add it."), leftPos + imageWidth / 2, topPos + 96,
                    imageWidth - 20, 0.75f, 0.5f, Draw.Align.CENTER, Theme.MUTED, false);
            return;
        }
        List<Integer> tops = sectionTops();
        int top = listTop();
        int height = listHeight();
        int content = tops.getLast();
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - height));
        List<Component> tooltip = null;
        g.enableScissor(leftPos + 4, top, leftPos + imageWidth - 4, top + height);
        for (int t = 0; t < view.types().size(); t++) {
            SacksMenu.TypeView type = view.types().get(t);
            int y = top + tops.get(t) - (int) Math.round(scroll);
            if (y > top + height || y + tops.get(t + 1) - tops.get(t) < top) {
                continue;
            }
            Draw.item(g, type.icon(), gridX(), y - 2, 0.7f);
            Draw.fit(g, font, Component.literal(type.name() + " Sack"), gridX() + 14, y + 1, 150, 0.8f, 0.5f, Draw.Align.LEFT, GOLD, false);
            Draw.fit(g, font, Component.literal(String.format(Locale.ROOT, "%,d per item", type.size())), gridX() + COLUMNS * CELL, y + 1, 100,
                    0.65f, 0.45f, Draw.Align.RIGHT, Theme.MUTED, false);
            for (int i = 0; i < type.items().size(); i++) {
                SacksMenu.ItemView item = type.items().get(i);
                int cx = gridX() + i % COLUMNS * CELL;
                int cy = y + HEADER + i / COLUMNS * CELL;
                boolean over = SkyScreens.inside(mouseX, mouseY, cx, cy, CELL - 2, CELL - 2) && mouseY >= top && mouseY < top + height;
                Draw.panel(g, cx, cy, CELL - 2, CELL - 2, over ? Theme.CARD_HOVER : Theme.CARD, item.count() > 0 ? Theme.CARD_BORDER : Draw.mix(Theme.CARD, Theme.CARD_BORDER, 0.4f));
                Draw.item(g, item.item(), cx + 4, cy + 2, 0.9f);
                String count = compact(item.count());
                Draw.fit(g, font, Component.literal(count), cx + CELL - 4, cy + CELL - 9, CELL - 4, 0.55f, 0.4f, Draw.Align.RIGHT,
                        item.count() > 0 ? Theme.TEXT : Theme.DIM, false);
                if (over) {
                    tooltip = new ArrayList<>();
                    tooltip.add(item.item().getHoverName());
                    tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Stored: §e%,d§7/%,d", item.count(), type.size())));
                    if (item.count() > 0) {
                        tooltip.add(Component.empty());
                        tooltip.add(Component.literal("§eClick to take a stack!"));
                        tooltip.add(Component.literal("§eShift-click to fill your inventory!"));
                    }
                }
            }
        }
        g.disableScissor();
        if (content > height) {
            Draw.scrollbar(g, leftPos + imageWidth - 5, top, height, content, scroll, GOLD);
        }
        if (view.missing() > 0) {
            Draw.fit(g, font, Component.literal(view.missing() + " more kinds of sack to find"), leftPos + 10, topPos + 25, 150, 0.65f, 0.45f,
                    Draw.Align.LEFT, Theme.DIM, false);
        }
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, GOLD);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private static String compact(long n) {
        if (n >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
        }
        if (n >= 10_000) {
            return (n / 1000) + "k";
        }
        return String.valueOf(n);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (overInsert(event.x(), event.y())) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SacksMenu.INSERT);
                SkyScreens.click();
                return true;
            }
            int top = listTop();
            if (event.y() >= top && event.y() < top + listHeight()) {
                List<Integer> tops = sectionTops();
                for (int t = 0; t < menu.view().types().size(); t++) {
                    SacksMenu.TypeView type = menu.view().types().get(t);
                    int y = top + tops.get(t) - (int) Math.round(scroll);
                    for (int i = 0; i < type.items().size(); i++) {
                        int cx = gridX() + i % COLUMNS * CELL;
                        int cy = y + HEADER + i / COLUMNS * CELL;
                        if (SkyScreens.inside(event.x(), event.y(), cx, cy, CELL - 2, CELL - 2) && type.items().get(i).count() > 0) {
                            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, (event.hasShiftDown() ? SacksMenu.ALL : 0) + t * 100 + i);
                            SkyScreens.click();
                            return true;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * 24;
        return true;
    }
}
