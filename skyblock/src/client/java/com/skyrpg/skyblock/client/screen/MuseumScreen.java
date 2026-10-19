package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Museum: category tabs with how many you've donated, the milestone bar (click for the Curator's rewards), then the
 * category's donations as item cells: green when donated (click to borrow it back), gold when you have it with you (click
 * to donate it, or to return a borrowed one), blue while it's on loan, dim when you haven't found it. Hover for its
 * SkyBlock XP.
 */
public final class MuseumScreen extends AbstractContainerScreen<MuseumMenu> {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 246;
    private static final ScreenTheme THEME = ScreenTheme.SKYBLOCK;
    private static final int TABS_Y = SkyScreens.contentTop(THEME);
    private static final int TAB_H = 22;
    private static final int STRIP_Y = TABS_Y + 2 * TAB_H + 1;
    private static final int STRIP_H = 13;
    private static final int GRID_Y = STRIP_Y + STRIP_H + 4;
    private static final int CELL = 22;
    private static final int COLUMNS = 12;
    private static final int GOLD = THEME.accent;
    private static final int LOAN = 0xFF6AA6FF;

    private double scroll;

    public MuseumScreen(MuseumMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int tabX(int i) {
        return leftPos + THEME.inset + (i % 4) * ((imageWidth - 2 * THEME.inset) / 4);
    }

    private int tabY(int i) {
        return topPos + TABS_Y + (i / 4) * TAB_H;
    }

    private int tabW() {
        return (imageWidth - 2 * THEME.inset) / 4 - 2;
    }

    private boolean overStrip(double mx, double my) {
        return SkyScreens.inside(mx, my, leftPos + THEME.inset, topPos + STRIP_Y, imageWidth - 2 * THEME.inset, STRIP_H);
    }

    private int gridX() {
        return leftPos + (imageWidth - COLUMNS * CELL) / 2;
    }

    private int gridHeight() {
        return imageHeight - GRID_Y - 10;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        MuseumMenu.View view = menu.view();
        int done = view.done().stream().mapToInt(Integer::intValue).sum();
        int total = view.totals().stream().mapToInt(Integer::intValue).sum();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Museum"),
                Component.literal(done + "/" + total + " donated").withColor(GOLD));
        int tabW = tabW();
        for (int i = 0; i < view.totals().size() && i < MuseumMenu.NAMES.size(); i++) {
            boolean selected = i == view.selected();
            boolean over = SkyScreens.inside(mouseX, mouseY, tabX(i), tabY(i), tabW, TAB_H - 2);
            SkyScreens.tab(g, font, THEME, tabX(i), tabY(i), tabW, TAB_H - 2, "", selected, over);
            Draw.fit(g, font, Component.literal(MuseumMenu.NAMES.get(i)), tabX(i) + tabW / 2, tabY(i) + 3, tabW - 6, 0.7f, 0.45f, Draw.Align.CENTER,
                    selected ? GOLD : over ? THEME.text : THEME.muted, selected);
            int have = view.done().get(i);
            int of = Math.max(1, view.totals().get(i));
            SkyScreens.bar(g, THEME, tabX(i) + 6, tabY(i) + 12, tabW - 12, 5, (float) have / of, have >= of ? GOLD : THEME.good);
        }
        // The milestone bar: how far to the next one; hover for what it gives, click for the Curator.
        int stripX = leftPos + THEME.inset;
        int stripW = imageWidth - 2 * THEME.inset;
        boolean overStrip = overStrip(mouseX, mouseY);
        SkyScreens.card(g, THEME, stripX, topPos + STRIP_Y, stripW, STRIP_H, overStrip, false);
        int milestone = view.milestone();
        float toNext = milestone >= 40 ? 1 : (view.xp() % 100) / 100f;
        SkyScreens.bar(g, THEME, stripX + 4, topPos + STRIP_Y + STRIP_H - 4, stripW - 8, 2, toNext, GOLD);
        Draw.fit(g, font, Component.literal("Museum Milestone " + milestone + "/40"), stripX + 6, topPos + STRIP_Y + 2, 120, 0.7f, 0.45f, Draw.Align.LEFT,
                GOLD, false);
        Draw.fit(g, font, Component.literal(view.claimable() > 0 ? "Rewards to claim!" : view.xp() + " Museum XP"), stripX + stripW - 6, topPos + STRIP_Y + 2, 110,
                0.7f, 0.45f, Draw.Align.RIGHT, view.claimable() > 0 ? THEME.good : THEME.muted, false);
        List<MuseumMenu.EntryView> entries = view.entries();
        int top = topPos + GRID_Y;
        int height = gridHeight();
        int rows = (entries.size() + COLUMNS - 1) / COLUMNS;
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows * CELL - height));
        List<Component> tooltip = null;
        g.enableScissor(leftPos + THEME.inset, top, leftPos + imageWidth - THEME.inset, top + height);
        for (int i = 0; i < entries.size(); i++) {
            MuseumMenu.EntryView entry = entries.get(i);
            int x = gridX() + (i % COLUMNS) * CELL;
            int y = top + (i / COLUMNS) * CELL - (int) Math.round(scroll);
            if (y + CELL < top || y > top + height) {
                continue;
            }
            boolean over = SkyScreens.inside(mouseX, mouseY, x, y, CELL - 2, CELL - 2) && mouseY >= top && mouseY < top + height;
            int state = entry.state();
            boolean donated = state == MuseumMenu.DONATED || state == MuseumMenu.COUNTED;
            boolean loan = state == MuseumMenu.RETURNABLE || state == MuseumMenu.BORROWED;
            SkyScreens.card(g, THEME, x, y, CELL - 2, CELL - 2, over && state > 0, state == 0);
            if (donated) {
                Draw.outline(g, x, y, CELL - 2, CELL - 2, Draw.withAlpha(THEME.good, 0.8f));
            } else if (state == MuseumMenu.CAN_DONATE || state == MuseumMenu.RETURNABLE) {
                Draw.outline(g, x, y, CELL - 2, CELL - 2, GOLD);
            } else if (state == MuseumMenu.BORROWED) {
                Draw.outline(g, x, y, CELL - 2, CELL - 2, LOAN);
            }
            if (state > 0) {
                Draw.item(g, entry.icon(), x + 2, y + 2, 1f);
            } else {
                Draw.fit(g, font, Component.literal("?"), x + (CELL - 2) / 2, y + 6, CELL - 4, 1f, 0.6f, Draw.Align.CENTER, THEME.dim, false);
            }
            if (donated || loan) {
                // A green tick in the corner of what's in the Museum, a blue arrow on what's out on loan.
                g.fill(x + CELL - 9, y + CELL - 9, x + CELL - 3, y + CELL - 3, 0xFF0B0E14);
                Draw.fit(g, font, Component.literal(donated ? "✔" : "➜"), x + CELL - 6, y + CELL - 9, 6, 0.55f, 0.4f, Draw.Align.CENTER,
                        donated ? THEME.good : LOAN, false);
            }
            if (over) {
                tooltip = new ArrayList<>();
                if (state > 0) {
                    tooltip.addAll(entry.icon().getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
                } else {
                    tooltip.add(Component.literal("§8???"));
                }
                tooltip.add(Component.empty());
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
                tooltip.add(Component.literal(switch (state) {
                    case MuseumMenu.DONATED -> "§aDonated! §eClick to borrow it back.";
                    case MuseumMenu.COUNTED -> "§aDonated! §7A higher donation covers this one.";
                    case MuseumMenu.CAN_DONATE -> "§eClick to donate it!";
                    case MuseumMenu.RETURNABLE -> "§9On loan. §eClick to return it to the Museum!";
                    case MuseumMenu.BORROWED -> "§9On loan. §7Bring " + (entry.pieces() > 1 ? "the whole set" : "it") + " back to return it.";
                    default -> "§7Bring " + (entry.pieces() > 1 ? "the whole set" : "it") + " to donate it.";
                }));
                if (state == MuseumMenu.DONATED || loan) {
                    tooltip.add(Component.literal("§8It still counts for your milestones."));
                }
            }
        }
        g.disableScissor();
        if (rows * CELL > height) {
            Draw.scrollbar(g, leftPos + imageWidth - THEME.inset - 3, top, height, rows * CELL, scroll, GOLD);
        }
        if (overStrip && !view.next().isEmpty()) {
            tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§6Museum Milestones"));
            tooltip.add(Component.literal("§7Every §b100 SkyBlock XP §7the Museum pays is one."));
            tooltip.add(Component.empty());
            for (String line : view.next()) {
                tooltip.add(Component.literal(line));
            }
            tooltip.add(Component.empty());
            tooltip.add(Component.literal("§eClick to visit the Curator!"));
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
            if (overStrip(event.x(), event.y())) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MuseumMenu.CURATOR);
                SkyScreens.click();
                return true;
            }
            int tabW = tabW();
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
                        && menu.view().entries().get(index).state() >= MuseumMenu.CAN_DONATE
                        && menu.view().entries().get(index).state() <= MuseumMenu.BORROWED) {
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
