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
    private static final ScreenTheme THEME = ScreenTheme.SKYBLOCK;
    private static final int WIDTH = 300;
    private static final int HEIGHT = 232;
    private static final int TOP = 56;
    private static final int CELL = 26;
    private static final int CELL_H = 30;
    private static final int COLUMNS = 10;
    private static final int HEADER = 14;

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
        return imageHeight - TOP - 10;
    }

    /** Where each section starts (content y, before scrolling). */
    private List<Integer> sectionTops() {
        List<Integer> tops = new ArrayList<>();
        int y = 0;
        for (SacksMenu.TypeView type : menu.view().types()) {
            tops.add(y);
            y += HEADER + (int) Math.ceil(type.items().size() / (double) COLUMNS) * CELL_H + 8;
        }
        tops.add(y);
        return tops;
    }

    private boolean overSacks(double mx, double my) {
        return SkyScreens.inside(mx, my, leftPos + THEME.inset, topPos + 36, 90, 14);
    }

    private boolean overInsert(double mx, double my) {
        return SkyScreens.inside(mx, my, leftPos + imageWidth - THEME.inset - 90, topPos + 36, 90, 14);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        SacksMenu.View view = menu.view();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Sacks"),
                Component.literal(view.types().size() + " sack" + (view.types().size() == 1 ? "" : "s")).withColor(THEME.muted));
        boolean overButton = overInsert(mouseX, mouseY);
        SkyScreens.button(g, font, THEME, leftPos + imageWidth - THEME.inset - 90, topPos + 36, 90, 14, "Insert inventory", true, overButton);
        SkyScreens.button(g, font, THEME, leftPos + THEME.inset, topPos + 36, 90, 14, "Sack of Sacks", true, overSacks(mouseX, mouseY));
        if (view.missing() > 0) {
            Draw.fit(g, font, Component.literal(view.missing() + " more kinds to find"), leftPos + THEME.inset + 96, topPos + 40, 90, 0.7f, 0.45f,
                    Draw.Align.LEFT, THEME.dim, false);
        }
        if (view.types().isEmpty()) {
            int cw = 220;
            SkyScreens.card(g, THEME, leftPos + (imageWidth - cw) / 2, topPos + 80, cw, 50, false, false);
            Draw.fit(g, font, Component.literal("You have no sacks yet."), leftPos + imageWidth / 2, topPos + 92, cw - 12, 1f, 0.6f,
                    Draw.Align.CENTER, THEME.text, false);
            Draw.fit(g, font, Component.literal("Right-click a sack (Small Mining Sack...) to put it in your Sack of Sacks."), leftPos + imageWidth / 2, topPos + 108,
                    cw - 12, 0.75f, 0.45f, Draw.Align.CENTER, THEME.muted, false);
            return;
        }
        List<Integer> tops = sectionTops();
        int top = listTop();
        int height = listHeight();
        int content = tops.getLast();
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - height));
        List<Component> tooltip = null;
        g.enableScissor(leftPos + 10, top, leftPos + imageWidth - 10, top + height);
        for (int t = 0; t < view.types().size(); t++) {
            SacksMenu.TypeView type = view.types().get(t);
            int y = top + tops.get(t) - (int) Math.round(scroll);
            if (y > top + height || y + tops.get(t + 1) - tops.get(t) < top) {
                continue;
            }
            int rows = (int) Math.ceil(type.items().size() / (double) COLUMNS);
            SkyScreens.card(g, THEME, gridX() - 5, y - 3, COLUMNS * CELL + 8, HEADER + rows * CELL_H + 6, false, false);
            Draw.item(g, type.icon(), gridX(), y - 1, 0.7f);
            Draw.fit(g, font, Component.literal(type.name() + " Sack"), gridX() + 14, y + 2, 150, 0.85f, 0.5f, Draw.Align.LEFT, THEME.accent, false);
            long stored = 0;
            for (SacksMenu.ItemView it : type.items()) {
                stored += it.count();
            }
            Draw.fit(g, font, Component.literal(String.format(Locale.ROOT, "%,d per item", type.size())), gridX() + COLUMNS * CELL - 2, y + 2, 100,
                    0.65f, 0.45f, Draw.Align.RIGHT, THEME.muted, false);
            SkyScreens.bar(g, THEME, gridX() + COLUMNS * CELL - 2 - 100, y + 10, 100, 3,
                    type.items().isEmpty() ? 0 : stored / (float) ((long) type.size() * type.items().size()), THEME.good);
            for (int i = 0; i < type.items().size(); i++) {
                SacksMenu.ItemView item = type.items().get(i);
                int cx = gridX() + i % COLUMNS * CELL;
                int cy = y + HEADER + 2 + i / COLUMNS * CELL_H;
                boolean over = SkyScreens.inside(mouseX, mouseY, cx, cy, CELL - 2, CELL_H - 2) && mouseY >= top && mouseY < top + height;
                SkyScreens.card(g, THEME, cx, cy, CELL - 2, CELL_H - 2, over, item.count() <= 0);
                Draw.item(g, item.item(), cx + 4, cy + 2, 0.9f);
                Draw.fit(g, font, Component.literal(compact(item.count())), cx + CELL - 4, cy + 17, CELL - 4, 0.55f, 0.4f, Draw.Align.RIGHT,
                        item.count() > 0 ? THEME.text : THEME.dim, false);
                SkyScreens.bar(g, THEME, cx + 3, cy + CELL_H - 8, CELL - 8, 3, type.size() <= 0 ? 0 : item.count() / (float) type.size(),
                        item.count() >= type.size() ? THEME.accent : THEME.good);
                if (over) {
                    tooltip = new ArrayList<>(item.item().getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(minecraft.level), minecraft.player,
                            net.minecraft.world.item.TooltipFlag.NORMAL));
                    tooltip.add(Component.empty());
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
            Draw.scrollbar(g, leftPos + imageWidth - 9, top, height, content, scroll, THEME.accent);
        }
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, THEME.accent);
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
            if (overSacks(event.x(), event.y())) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SacksMenu.SACK_OF_SACKS);
                SkyScreens.click();
                return true;
            }
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
                        int cy = y + HEADER + 2 + i / COLUMNS * CELL_H;
                        if (SkyScreens.inside(event.x(), event.y(), cx, cy, CELL - 2, CELL_H - 2) && type.items().get(i).count() > 0) {
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
