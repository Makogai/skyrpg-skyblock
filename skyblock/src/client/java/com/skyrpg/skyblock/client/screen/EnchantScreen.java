package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.enchant.Enchants;
import com.skyrpg.skyblock.enchant.EnchantMenu;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Enchantment Table: the item on the left, every enchant it takes in a list (with the level it has, in green),
 * and for the chosen one what it does and a button per level with its XP cost. Clicking the level it already has takes
 * the enchant off.
 */
public final class EnchantScreen extends AbstractContainerScreen<EnchantMenu> {
    private static final int ROW = 14;
    private static final int LEVEL_H = 22;
    private static final int XP = 0xFF7EFC20;
    private static final int ENCHANT = 0xFF5555FF;
    private static final int ULTIMATE = 0xFFFF55FF;

    private String selected;
    private double scroll;

    public EnchantScreen(EnchantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, EnchantMenu.WIDTH, EnchantMenu.HEIGHT);
    }

    private int listX() {
        return leftPos + EnchantMenu.LIST_X;
    }

    private int listY() {
        return topPos + EnchantMenu.LIST_Y;
    }

    private int detailX() {
        return leftPos + EnchantMenu.DETAIL_X;
    }

    private int detailW() {
        return imageWidth - EnchantMenu.PAD - EnchantMenu.DETAIL_X;
    }

    /** The level buttons: one row along the bottom of the detail panel. */
    private int levelsY() {
        return listY() + EnchantMenu.LIST_H - LEVEL_H - 5;
    }

    private int levelW(Enchants.Enchant enchant) {
        int n = Math.max(1, enchant.tableMax());
        return (detailW() - 12 - 3 * (n - 1)) / n;
    }

    private int levelX(Enchants.Enchant enchant, int level) {
        return detailX() + 6 + (level - 1) * (levelW(enchant) + 3);
    }

    private Enchants.Enchant chosen(List<Enchants.Enchant> options) {
        for (Enchants.Enchant enchant : options) {
            if (enchant.id().equals(selected)) {
                return enchant;
            }
        }
        return null;
    }

    private int playerLevels() {
        return minecraft.player == null ? 0 : minecraft.player.experienceLevel;
    }

    private boolean creative() {
        return minecraft.player != null && minecraft.player.isCreative();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("Enchant Item"),
                Component.literal("Your XP: " + playerLevels() + " levels").withColor(XP));
        SkyScreens.label(g, font, "Item", leftPos + EnchantMenu.ITEM_X - 2, topPos + EnchantMenu.ITEM_Y - 10);
        SkyScreens.well(g, leftPos + EnchantMenu.ITEM_X, topPos + EnchantMenu.ITEM_Y);
        SkyScreens.label(g, font, "Inventory", leftPos + EnchantMenu.INV_X, topPos + EnchantMenu.INV_Y - 11);
        SkyScreens.inventoryWells(g, menu, 1, leftPos, topPos);

        List<Enchants.Enchant> options = menu.options();
        ItemStack item = menu.item();
        int lx = listX();
        int ly = listY();
        Draw.panel(g, lx, ly, EnchantMenu.LIST_W, EnchantMenu.LIST_H, Theme.CARD, Theme.CARD_BORDER);
        if (options.isEmpty()) {
            Draw.fit(g, font, Component.literal(item.isEmpty() ? "Place an item to enchant" : "This item can't be enchanted"),
                    lx + EnchantMenu.LIST_W / 2, ly + EnchantMenu.LIST_H / 2 - 4, EnchantMenu.LIST_W - 10, 1f, 0.6f, Draw.Align.CENTER, Theme.DIM, false);
        } else {
            int content = options.size() * ROW;
            scroll = Mth.clamp(scroll, 0, Math.max(0, content - (EnchantMenu.LIST_H - 4)));
            g.enableScissor(lx + 1, ly + 2, lx + EnchantMenu.LIST_W - 1, ly + EnchantMenu.LIST_H - 2);
            for (int i = 0; i < options.size(); i++) {
                Enchants.Enchant enchant = options.get(i);
                int ry = ly + 2 + i * ROW - (int) Math.round(scroll);
                if (ry + ROW < ly || ry > ly + EnchantMenu.LIST_H) {
                    continue;
                }
                boolean isSelected = enchant.id().equals(selected);
                boolean over = SkyScreens.inside(mouseX, mouseY, lx + 2, Math.max(ry, ly), EnchantMenu.LIST_W - 4, ROW - 1);
                if (isSelected || over) {
                    g.fill(lx + 2, ry, lx + EnchantMenu.LIST_W - 2, ry + ROW - 1, isSelected ? Draw.withAlpha(ENCHANT, 0.35f) : Theme.CARD_HOVER);
                }
                int has = ItemMods.enchantment(item, enchant.id());
                Draw.fit(g, font, Component.literal(enchant.name()), lx + 6, ry + 3, EnchantMenu.LIST_W - 40, 0.85f, 0.5f, Draw.Align.LEFT,
                        enchant.ultimate() ? ULTIMATE : ENCHANT, false);
                if (has > 0) {
                    Draw.fit(g, font, Component.literal(Enchants.roman(has)), lx + EnchantMenu.LIST_W - 6, ry + 3, 30, 0.85f, 0.5f,
                            Draw.Align.RIGHT, Theme.GREEN, false);
                }
            }
            g.disableScissor();
            if (content > EnchantMenu.LIST_H - 4) {
                Draw.scrollbar(g, lx + EnchantMenu.LIST_W - 4, ly + 2, EnchantMenu.LIST_H - 4, content, scroll, ENCHANT);
            }
        }

        // The chosen enchant: what it does, and a button per level.
        int dx = detailX();
        int dw = detailW();
        Draw.panel(g, dx, ly, dw, EnchantMenu.LIST_H, Theme.CARD, Theme.CARD_BORDER);
        Enchants.Enchant enchant = chosen(options);
        if (enchant == null) {
            Draw.fit(g, font, Component.literal(options.isEmpty() ? "" : "Pick an enchantment"), dx + dw / 2, ly + EnchantMenu.LIST_H / 2 - 4,
                    dw - 10, 1f, 0.6f, Draw.Align.CENTER, Theme.DIM, false);
            return;
        }
        int has = ItemMods.enchantment(item, enchant.id());
        Draw.fit(g, font, Component.literal(enchant.name()), dx + 6, ly + 6, dw - 12, 1f, 0.6f, Draw.Align.LEFT, enchant.ultimate() ? ULTIMATE : ENCHANT, false);
        int y = ly + 18;
        Enchants.Level shown = enchant.level(Math.max(1, Math.min(has > 0 ? has : enchant.tableMax(), enchant.max())));
        if (shown != null) {
            for (String line : shown.lore()) {
                Draw.fit(g, font, Component.literal(line), dx + 6, y, dw - 12, 0.75f, 0.45f, Draw.Align.LEFT, Theme.TEXT, false);
                y += 9;
                if (y > levelsY() - 22) {
                    break;
                }
            }
        }
        int by = levelsY();
        for (int level = 1; level <= enchant.tableMax(); level++) {
            int bx = levelX(enchant, level);
            int bw = levelW(enchant);
            int cost = enchant.xpCost(level);
            boolean afford = creative() || playerLevels() >= cost;
            boolean current = has == level;
            boolean blocked = has > enchant.tableMax();
            boolean over = !blocked && afford && SkyScreens.inside(mouseX, mouseY, bx, by, bw, LEVEL_H);
            int color = blocked ? Theme.DIM : current ? Theme.RED : afford ? Theme.GREEN : Theme.DIM;
            Draw.panel(g, bx, by, bw, LEVEL_H, over ? Draw.mix(Theme.TRACK, color, 0.25f) : Theme.TRACK,
                    over ? color : Draw.mix(Theme.TRACK, color, current ? 0.8f : 0.5f));
            Draw.fit(g, font, Component.literal(Enchants.roman(level)), bx + bw / 2, by + 3, bw - 2, 0.9f, 0.5f, Draw.Align.CENTER,
                    current ? Theme.RED : ENCHANT, false);
            Draw.fit(g, font, Component.literal(cost + " lv"), bx + bw / 2, by + 13, bw - 2, 0.7f, 0.4f, Draw.Align.CENTER,
                    afford ? XP : Theme.RED, false);
        }
        if (has > 0 && has <= enchant.tableMax()) {
            Draw.fit(g, font, Component.literal("Click " + Enchants.roman(has) + " again to remove it"), dx + dw / 2, by - 9, dw - 12, 0.65f, 0.4f,
                    Draw.Align.CENTER, Theme.DIM, false);
        }

    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            List<Enchants.Enchant> options = menu.options();
            double mx = event.x();
            double my = event.y();
            int lx = listX();
            int ly = listY();
            if (SkyScreens.inside(mx, my, lx + 2, ly + 2, EnchantMenu.LIST_W - 4, EnchantMenu.LIST_H - 4)) {
                int index = (int) ((my - ly - 2 + scroll) / ROW);
                if (index >= 0 && index < options.size()) {
                    selected = options.get(index).id();
                    SkyScreens.click();
                    return true;
                }
            }
            Enchants.Enchant enchant = chosen(options);
            if (enchant != null) {
                for (int level = 1; level <= enchant.tableMax(); level++) {
                    if (SkyScreens.inside(mx, my, levelX(enchant, level), levelsY(), levelW(enchant), LEVEL_H)) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, options.indexOf(enchant) * 16 + level);
                        SkyScreens.click();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (SkyScreens.inside(mouseX, mouseY, listX(), listY(), EnchantMenu.LIST_W, EnchantMenu.LIST_H)) {
            scroll -= scrollY * 18;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }
}
