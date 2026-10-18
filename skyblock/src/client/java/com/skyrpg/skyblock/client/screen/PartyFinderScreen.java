package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.skyblock.dungeon.PartyFinder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Party Finder: the listed parties (click one to join), and listing your own for a floor. */
public final class PartyFinderScreen extends AbstractContainerScreen<PartyFinder> {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 232;
    private static final int RED = 0xFFE04848;
    private static final int ROWS = 9;
    private static final int ROW_H = 17;
    private static final ScreenTheme THEME = ScreenTheme.DUNGEON;

    public PartyFinderScreen(PartyFinder menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int rowY(int i) {
        return topPos + SkyScreens.contentTop(THEME) + i * (ROW_H + 2);
    }

    private int rowX() {
        return leftPos + THEME.inset;
    }

    private int rowW() {
        return imageWidth - 2 * THEME.inset;
    }

    private int bottomY() {
        return topPos + imageHeight - 30;
    }

    /** A row's text split into name, floor, party size and classes. */
    private static String[] parts(PartyFinder.Row row) {
        String[] p = row.text().split("  ·  ", 4);
        String[] out = {"", "", "", ""};
        System.arraycopy(p, 0, out, 0, Math.min(4, p.length));
        return out;
    }

    private static int partySize(String part) {
        try {
            return Integer.parseInt(part.split("/")[0].trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        PartyFinder.View view = menu.view();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Party Finder"),
                Component.literal(view.rows().size() + " listed").withColor(RED));
        if (view.rows().isEmpty()) {
            Draw.item(g, new ItemStack(Items.SKELETON_SKULL), leftPos + imageWidth / 2 - 8, topPos + 70, 1f);
            Draw.fit(g, font, Component.literal("Nobody's listed right now. List your party below!"), leftPos + imageWidth / 2, topPos + 94, imageWidth - 20,
                    0.8f, 0.5f, Draw.Align.CENTER, THEME.muted, false);
        }
        List<Component> tooltip = null;
        for (int i = 0; i < Math.min(ROWS, view.rows().size()); i++) {
            PartyFinder.Row row = view.rows().get(i);
            String[] p = parts(row);
            int x = rowX();
            int y = rowY(i);
            int w = rowW();
            boolean over = row.joinable() && SkyScreens.inside(mouseX, mouseY, x, y, w, ROW_H);
            SkyScreens.card(g, THEME, x, y, w, ROW_H, over, !row.joinable());
            boolean master = p[1].startsWith("M");
            Draw.item(g, new ItemStack(master ? Items.WITHER_SKELETON_SKULL : Items.SKELETON_SKULL), x + 3, y + 2, 0.8f);
            Draw.fit(g, font, Component.literal(p[0]), x + 20, y + 5, 90, 0.75f, 0.45f, Draw.Align.LEFT, row.joinable() ? THEME.text : THEME.muted, true);
            Draw.fit(g, font, Component.literal(p[1]), x + 114, y + 5, 30, 0.75f, 0.45f, Draw.Align.LEFT, master ? RED : THEME.accent, true);
            int size = partySize(p[2]);
            SkyScreens.bar(g, THEME, x + 146, y + 6, 36, 5, size / 5f, size >= 5 ? RED : THEME.good);
            Draw.scaledText(g, font, Component.literal(p[2]), x + 185, y + 5, 0.7f, THEME.muted);
            Draw.fit(g, font, Component.literal(p[3]), x + 208, y + 5, w - 208 - 44, 0.65f, 0.4f, Draw.Align.LEFT, THEME.muted, false);
            SkyScreens.button(g, font, THEME, x + w - 38, y + 2, 34, ROW_H - 4, row.joinable() ? "Join" : "Yours", row.joinable(), over);
            if (SkyScreens.inside(mouseX, mouseY, x, y, w, ROW_H)) {
                tooltip = List.of(Component.literal(p[0] + "'s party").withColor(THEME.text),
                        Component.literal("Floor " + p[1] + "  ·  " + p[2] + " players").withColor(master ? RED : THEME.accent),
                        Component.literal(p[3].isEmpty() ? "No classes picked" : p[3]).withColor(THEME.muted), Component.empty(),
                        Component.literal(row.joinable() ? "Click to join" : "Your party").withColor(row.joinable() ? THEME.good : THEME.dim));
            }
        }
        int y = bottomY();
        int bx = rowX();
        button(g, mouseX, mouseY, bx, y, 16, "◀", true);
        Draw.fit(g, font, Component.literal(view.floor()), bx + 36, y + 4, 30, 0.8f, 0.5f, Draw.Align.CENTER, RED, true);
        button(g, mouseX, mouseY, bx + 54, y, 16, "▶", true);
        button(g, mouseX, mouseY, bx + 80, y, 110, view.listed() ? "Update listing" : "List my party", true);
        if (view.listed()) {
            button(g, mouseX, mouseY, bx + 196, y, 84, "Unlist", true);
        }
        Draw.fit(g, font, Component.literal("Click a party to join it. Floor arrows pick the floor you list."), leftPos + imageWidth / 2,
                topPos + imageHeight - 15, imageWidth - 24, 0.62f, 0.45f, Draw.Align.CENTER, THEME.dim, false);
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, THEME.accent);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private void button(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, String text, boolean enabled) {
        SkyScreens.button(g, font, THEME, x, y, w, 16, text, enabled, SkyScreens.inside(mouseX, mouseY, x, y, w, 16));
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
                if (view.rows().get(i).joinable() && SkyScreens.inside(mx, my, rowX(), rowY(i), rowW(), ROW_H)) {
                    return press(PartyFinder.JOIN + i);
                }
            }
            int y = bottomY();
            if (SkyScreens.inside(mx, my, rowX(), y, 16, 16)) {
                return press(PartyFinder.FLOOR_PREV);
            }
            if (SkyScreens.inside(mx, my, rowX() + 54, y, 16, 16)) {
                return press(PartyFinder.FLOOR_NEXT);
            }
            if (SkyScreens.inside(mx, my, rowX() + 80, y, 110, 16)) {
                return press(PartyFinder.LIST);
            }
            if (view.listed() && SkyScreens.inside(mx, my, rowX() + 196, y, 84, 16)) {
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
