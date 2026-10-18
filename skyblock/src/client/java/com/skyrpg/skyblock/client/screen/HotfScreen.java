package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.galatea.HotfMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Heart of the Forest tree, in the Galatea theme: tier 1 at the bottom, tier 8 at the top, each perk an icon card on
 * its grid spot (Hypixel's woods: a pale oak button locked, a stripped log unlocked, a log maxed; saplings for axe
 * abilities, mangrove for the Center of the Forest; a dead bush when switched off). Hover a perk for what it does and
 * costs; click to unlock or upgrade it (shift: ten levels), right-click to switch it off or on. The side has your HotF
 * tier and XP bar, and your tokens and Whispers as icon cards.
 */
public final class HotfScreen extends AbstractContainerScreen<HotfMenu> {
    private static final ScreenTheme THEME = ScreenTheme.GALATEA;
    private static final int WIDTH = 300;
    private static final int HEIGHT = 200;
    private static final int ROWS = 8;
    private static final int CELL = 18;
    private static final int GRID_X = 30;
    private static final int GRID_Y = 38;
    private static final int GREEN = 0xFF55FF55;

    public HotfScreen(HotfMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int cellX(int column) {
        return leftPos + GRID_X + column * CELL;
    }

    private int cellY(int row) {
        return topPos + GRID_Y + row * CELL;
    }

    private HotfMenu.PerkView perkAt(double mouseX, double mouseY) {
        for (HotfMenu.PerkView perk : menu.view().perks()) {
            if (SkyScreens.inside(mouseX, mouseY, cellX(perk.x()), cellY(perk.y()), CELL - 1, CELL - 1)) {
                return perk;
            }
        }
        return null;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        HotfMenu.View view = menu.view();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Heart of the Forest"),
                Component.literal("Tier " + view.level()).withColor(GREEN));

        // The tree sits on a card; tier labels down its side, the open tiers bright.
        SkyScreens.card(g, THEME, leftPos + THEME.inset, topPos + GRID_Y - 3, GRID_X - THEME.inset + 7 * CELL + 3, ROWS * CELL + 5, false, false);
        for (int row = 0; row < ROWS; row++) {
            int tier = ROWS - row;
            int color = tier <= view.level() ? GREEN : THEME.dim;
            Draw.fit(g, font, Component.literal(String.valueOf(tier)), leftPos + GRID_X - 5, cellY(row) + 5, 16, 0.75f, 0.5f, Draw.Align.RIGHT, color, false);
        }
        HotfMenu.PerkView hovered = perkAt(mouseX, mouseY);
        for (HotfMenu.PerkView perk : view.perks()) {
            int x = cellX(perk.x());
            int y = cellY(perk.y());
            int border = perk.selected() ? 0xFFFFAA00 : perk.state() == 3 ? 0xFF55FFFF : perk.state() == 2 ? THEME.good
                    : perk.state() == 1 ? 0xFFFFFF55 : 0;
            SkyScreens.card(g, THEME, x, y, CELL - 1, CELL - 1, perk == hovered, perk.state() == 0);
            if (border != 0) {
                Draw.outline(g, x, y, CELL - 1, CELL - 1, border);
            }
            Draw.item(g, icon(perk), x + 1, y + 1, 0.9f);
        }

        // The side: the tier and its XP bar, then tokens and powders.
        int sx = cellX(7) + 10;
        int sw = leftPos + imageWidth - THEME.inset - sx;
        int sy = topPos + GRID_Y - 3;
        boolean maxed = view.xpNext() <= 0;
        SkyScreens.card(g, THEME, sx, sy, sw, 40, false, false);
        Draw.fit(g, font, Component.literal("HotF Tier " + view.level()), sx + 6, sy + 5, sw - 12, 0.9f, 0.5f, Draw.Align.LEFT, GREEN, true);
        SkyScreens.bar(g, THEME, sx + 6, sy + 17, sw - 12, 6, maxed ? 1f : (float) view.xp() / view.xpNext(), GREEN);
        String xp = maxed ? "Max tier" : String.format(Locale.ROOT, "%,d / %,d XP to Tier %d", view.xp(), view.xpNext(), view.level() + 1);
        Draw.fit(g, font, Component.literal(xp), sx + 6, sy + 27, sw - 12, 0.65f, 0.45f, Draw.Align.LEFT, THEME.muted, false);
        sy += 44;
        sy = stat(g, font, sx, sy, sw, Items.OAK_SAPLING, "Tokens of the Forest", String.valueOf(view.tokens()), GREEN);
        sy = stat(g, font, sx, sy, sw, Items.FERN, "Forest Whispers", String.format(Locale.ROOT, "%,d", view.forest()), 0xFF00AAAA);
        sy = stat(g, font, sx, sy, sw, Items.DEAD_BUSH, "Desert Whispers", String.format(Locale.ROOT, "%,d", view.desert()), 0xFFFFAA00);

        Draw.fit(g, font, Component.literal("Click: unlock/upgrade  ·  Shift: 10 levels  ·  Right-click: switch off/on"), leftPos + imageWidth / 2,
                topPos + imageHeight - 15, imageWidth - 2 * THEME.inset, 0.62f, 0.45f, Draw.Align.CENTER, THEME.dim, false);

        if (hovered != null) {
            Draw.queueTooltip(tooltip(hovered), border(hovered));
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    /** A balance card: an icon, the label and the value in its colour. */
    private int stat(GuiGraphicsExtractor g, Font font, int x, int y, int w, net.minecraft.world.item.Item icon, String label, String value, int color) {
        SkyScreens.card(g, THEME, x, y, w, 25, false, false);
        g.fill(x + 4, y + 4, x + 21, y + 21, 0x55000000);
        Draw.item(g, new ItemStack(icon), x + 5, y + 5, 1f);
        Draw.fit(g, font, Component.literal(label), x + 26, y + 5, w - 30, 0.65f, 0.45f, Draw.Align.LEFT, THEME.muted, false);
        Draw.fit(g, font, Component.literal(value), x + 26, y + 13, w - 30, 0.95f, 0.5f, Draw.Align.LEFT, color, false);
        return y + 28;
    }

    private static int border(HotfMenu.PerkView perk) {
        return perk.state() == 3 ? 0xFF55FFFF : perk.state() == 2 ? Theme.GREEN : perk.state() == 1 ? 0xFFFFFF55 : Theme.RED;
    }

    private static ItemStack icon(HotfMenu.PerkView perk) {
        if (perk.id().equals("center_of_the_forest")) {
            return new ItemStack(perk.state() == 3 ? Items.MANGROVE_WOOD : Items.MANGROVE_ROOTS);
        }
        if (perk.level() > 0 && !perk.enabled()) {
            return new ItemStack(Items.DEAD_BUSH);
        }
        if (perk.ability()) {
            return new ItemStack(perk.level() > 0 ? Items.OAK_SAPLING : Items.PALE_OAK_SAPLING);
        }
        return new ItemStack(perk.state() == 3 ? Items.OAK_LOG : perk.level() > 0 ? Items.STRIPPED_OAK_LOG : Items.PALE_OAK_BUTTON);
    }

    private static List<Component> tooltip(HotfMenu.PerkView perk) {
        List<Component> lines = new ArrayList<>();
        int color = perk.state() == 3 ? 0x55FFFF : perk.level() > 0 ? 0x55FF55 : perk.state() == 1 ? 0xFFFF55 : 0xFF5555;
        lines.add(Component.literal(perk.name()).withColor(color));
        if (perk.max() > 1) {
            lines.add(Component.literal("§7Level " + Math.max(perk.level(), 0) + "§8/" + perk.max()));
        }
        lines.add(Component.empty());
        perk.lore().forEach(line -> lines.add(Component.literal(line)));
        if (!perk.cost().isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.literal(perk.cost()));
        }
        lines.add(Component.empty());
        if (perk.state() == 1) {
            lines.add(Component.literal("§eClick to unlock!"));
        } else if (perk.state() == 0) {
            lines.add(Component.literal("§cUnlock a perk next to it first!"));
        } else if (perk.ability()) {
            lines.add(Component.literal(perk.selected() ? "§aSELECTED" : "§eClick to select!"));
        } else if (perk.state() == 2) {
            lines.add(Component.literal("§eClick to upgrade!"));
        } else {
            lines.add(Component.literal("§bMAXED"));
        }
        if (perk.level() > 0) {
            lines.add(Component.literal(perk.enabled() ? "§7Right-click to §cdisable§7." : "§7Right-click to §aenable§7."));
        }
        return lines;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        HotfMenu.PerkView perk = perkAt(event.x(), event.y());
        if (perk != null) {
            int index = menu.view().perks().indexOf(perk);
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, HotfMenu.TOGGLE + index);
            } else if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, (event.hasShiftDown() ? HotfMenu.TEN : 0) + index);
            }
            SkyScreens.click();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
