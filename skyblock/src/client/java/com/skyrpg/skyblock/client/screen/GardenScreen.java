package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.garden.GardenMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Garden's screens: the Desk's tabs (the plot grid, Crop Milestones, the Composter, SkyMart) and a visitor's offer.
 * Long lists (SkyMart) scroll with the mouse wheel.
 */
public final class GardenScreen extends AbstractContainerScreen<GardenMenu> {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 222;
    private static final int GREEN = 0xFF7BD35A;
    private static final String[] TABS = {"Plots", "Milestones", "Composter", "SkyMart"};
    private static final int ROW_STEP = 21;
    private int scroll;

    public GardenScreen(GardenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private boolean tabbed() {
        return menu.view().tab().ordinal() < 4;
    }

    private int listTop() {
        return topPos + (tabbed() ? 48 : 32) + (menu.view().tab() == GardenMenu.Tab.PLOTS ? 116 : 0);
    }

    private int tabX(int i) {
        return leftPos + 10 + i * 76;
    }

    private int buttonX() {
        return leftPos + imageWidth - 74;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        GardenMenu.View view = menu.view();
        String title = switch (view.tab()) {
            case SKYMART -> "SkyMart";
            case VISITOR -> "Visitor";
            case PHILLIP -> "Pesthunter Phillip";
            case JACOB -> "Jacob";
            case ANITA -> "Anita";
            default -> "The Garden";
        };
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal(title), Component.literal(view.header()).withColor(GREEN));
        if (tabbed()) {
            for (int i = 0; i < TABS.length; i++) {
                boolean active = view.tab().ordinal() == i;
                boolean over = SkyScreens.inside(mouseX, mouseY, tabX(i), topPos + 26, 72, 14);
                Draw.panel(g, tabX(i), topPos + 26, 72, 14, active ? Theme.CARD_HOVER : over ? Theme.CARD_HOVER : Theme.CARD,
                        active ? GREEN : Theme.CARD_BORDER);
                Draw.fit(g, font, Component.literal(TABS[i]), tabX(i) + 36, topPos + 29, 68, 0.7f, 0.45f, Draw.Align.CENTER,
                        active ? GREEN : Theme.MUTED, false);
            }
        }
        if (view.tab() == GardenMenu.Tab.PLOTS) {
            grid(g, view);
        }
        List<GardenMenu.Row> rows = view.rows();
        int top = listTop();
        int visible = Math.max(1, (topPos + imageHeight - 20 - top) / ROW_STEP);
        scroll = Math.max(0, Math.min(scroll, rows.size() - visible));
        for (int i = scroll; i < Math.min(rows.size(), scroll + visible); i++) {
            GardenMenu.Row row = rows.get(i);
            int y = top + (i - scroll) * ROW_STEP;
            int color = 0xFF000000 | row.color();
            Draw.panel(g, leftPos + 10, y, imageWidth - 20, ROW_STEP - 3, Theme.CARD, row.enabled() ? color : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal(row.title()), leftPos + 15, y + 2, imageWidth - 100, 0.72f, 0.45f, Draw.Align.LEFT, color, false);
            Draw.fit(g, font, Component.literal(row.detail()), leftPos + 15, y + 11, imageWidth - 100, 0.58f, 0.35f, Draw.Align.LEFT, Theme.DIM, false);
            if (!row.button().isEmpty()) {
                boolean over = row.enabled() && SkyScreens.inside(mouseX, mouseY, buttonX(), y + 2, 60, 14);
                Draw.panel(g, buttonX(), y + 2, 60, 14, over ? Theme.CARD_HOVER : Theme.CARD, row.enabled() ? Theme.GREEN : Theme.CARD_BORDER);
                Draw.fit(g, font, Component.literal(row.button()), buttonX() + 30, y + 5, 56, 0.7f, 0.45f, Draw.Align.CENTER,
                        row.enabled() ? Theme.GREEN : Theme.DIM, false);
            }
        }
        if (rows.size() > visible) {
            Draw.fit(g, font, Component.literal("scroll for more (" + (scroll + 1) + "-" + Math.min(rows.size(), scroll + visible) + " of "
                    + rows.size() + ")"), leftPos + imageWidth / 2, topPos + imageHeight - 24, imageWidth - 20, 0.55f, 0.35f, Draw.Align.CENTER,
                    Theme.DIM, false);
        }
        Draw.fit(g, font, Component.literal(view.footer()), leftPos + imageWidth / 2, topPos + imageHeight - 13, imageWidth - 20, 0.62f, 0.4f,
                Draw.Align.CENTER, Theme.DIM, false);
    }

    /** The 5 x 5 plots: the Barn, unlocked, the next one, locked. */
    private void grid(GuiGraphicsExtractor g, GardenMenu.View view) {
        int size = 21;
        int x0 = leftPos + (imageWidth - size * 5) / 2;
        int y0 = topPos + 48;
        for (int i = 0; i < view.grid().size(); i++) {
            int state = view.grid().get(i);
            int x = x0 + (i % 5) * size;
            int y = y0 + (i / 5) * size;
            int fill = switch (state) {
                case 0 -> 0xFF8A4B2A;
                case 1 -> 0xFF3F7D2C;
                case 2 -> 0xFF2E3A28;
                default -> 0xFF1E2128;
            };
            int border = state == 2 ? GREEN : state == 1 ? 0xFF5DA13F : Theme.CARD_BORDER;
            Draw.panel(g, x, y, size - 2, size - 2, fill, border);
            String label = state == 0 ? "Barn" : state == 3 ? "" : state == 2 ? "Next" : "";
            if (!label.isEmpty()) {
                Draw.fit(g, font, Component.literal(label), x + (size - 2) / 2, y + 7, size - 4, 0.5f, 0.3f, Draw.Align.CENTER, Theme.TEXT, false);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, scroll - (int) Math.signum(vertical));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            GardenMenu.View view = menu.view();
            if (tabbed()) {
                for (int i = 0; i < TABS.length; i++) {
                    if (SkyScreens.inside(event.x(), event.y(), tabX(i), topPos + 26, 72, 14)) {
                        scroll = 0;
                        return press(GardenMenu.TAB + i);
                    }
                }
            }
            int top = listTop();
            int visible = Math.max(1, (topPos + imageHeight - 20 - top) / ROW_STEP);
            for (int i = scroll; i < Math.min(view.rows().size(), scroll + visible); i++) {
                GardenMenu.Row row = view.rows().get(i);
                int y = top + (i - scroll) * ROW_STEP;
                if (row.enabled() && !row.button().isEmpty() && SkyScreens.inside(event.x(), event.y(), buttonX(), y + 2, 60, 14)) {
                    return press(GardenMenu.ROW + i);
                }
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
