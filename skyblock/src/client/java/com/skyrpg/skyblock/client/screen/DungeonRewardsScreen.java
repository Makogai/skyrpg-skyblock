package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.dungeon.RewardsMenu;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/** The Catacombs' reward chests: one card per chest with what's in it and its price; click one to open it. */
public final class DungeonRewardsScreen extends AbstractContainerScreen<RewardsMenu> {
    private static final int WIDTH = 380;
    private static final int HEIGHT = 214;
    private static final int CARD_H = 140;
    private static final ScreenTheme THEME = ScreenTheme.DUNGEON;
    private static final String[] NAMES = {"Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock"};
    private static final int[] COLORS = {0xFFB07A3E, 0xFFFFAA00, 0xFF55FFFF, 0xFF55FF55, 0xFFA77BEA, 0xFF8C8C8C};
    private static final Item[] ICONS = {Items.CHEST, Items.CHEST, Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK, Items.OBSIDIAN, Items.BEDROCK};

    public DungeonRewardsScreen(RewardsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int cardWidth() {
        int n = Math.max(1, menu.view().chests().size());
        return Math.min(100, (imageWidth - 2 * THEME.inset - (n - 1) * 6) / n);
    }

    private int cardX(int i) {
        int n = menu.view().chests().size();
        int total = n * cardWidth() + (n - 1) * 6;
        return leftPos + (imageWidth - total) / 2 + i * (cardWidth() + 6);
    }

    private int cardY() {
        return topPos + 52;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        RewardsMenu.View view = menu.view();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Reward Chests"),
                Component.literal(view.floor()).withColor(Theme.RED));
        // Score, rank and purse as chips across the top.
        Chip purse = Chip.balance("coins", view.purse());
        String score = "Score " + view.score();
        String rank = "Rank " + view.rank();
        int total = Math.round(font.width(score) * 0.8f) + 12 + Math.round(font.width(rank) * 0.8f) + 12 + SkyScreens.chipWidth(font, purse, 0.8f);
        int cx = leftPos + (imageWidth - total) / 2;
        int cy = topPos + SkyScreens.contentTop(THEME) + 1;
        Draw.scaledText(g, font, Component.literal(score), cx, cy + 1, 0.8f, THEME.text);
        cx += Math.round(font.width(score) * 0.8f) + 12;
        Draw.scaledText(g, font, Component.literal(rank), cx, cy + 1, 0.8f, THEME.accent);
        cx += Math.round(font.width(rank) * 0.8f) + 12;
        SkyScreens.chip(g, font, THEME, cx, cy, purse, 0.8f);
        boolean anyOpened = view.chests().stream().anyMatch(RewardsMenu.ChestView::opened);
        List<Component> tooltip = null;
        int w = cardWidth();
        int h = CARD_H;
        for (int i = 0; i < view.chests().size(); i++) {
            RewardsMenu.ChestView chest = view.chests().get(i);
            int x = cardX(i);
            int y = cardY();
            int color = COLORS[Math.min(chest.tier(), COLORS.length - 1)];
            boolean hover = SkyScreens.inside(mouseX, mouseY, x, y, w, h);
            boolean dim = anyOpened && !chest.opened();
            boolean broke = !chest.opened() && chest.cost() > view.purse();
            SkyScreens.card(g, THEME, x, y, w, h, hover && !anyOpened && !broke, dim);
            if (chest.opened()) {
                Draw.outline(g, x, y, w, h, THEME.good);
            }
            g.fill(x + 3, y + 3, x + w - 3, y + 5, dim ? THEME.dim : color);
            int ix0 = x + (w - 20) / 2;
            g.fill(ix0 - 1, y + 8, ix0 + 21, y + 30, 0x55000000);
            Draw.item(g, new ItemStack(ICONS[Math.min(chest.tier(), ICONS.length - 1)]), ix0 + 2, y + 11, 1f);
            Draw.fit(g, font, Component.literal(NAMES[Math.min(chest.tier(), NAMES.length - 1)] + " Chest"), x + w / 2, y + 33, w - 6, 0.8f, 0.45f,
                    Draw.Align.CENTER, dim ? THEME.dim : color, true);
            int py = y + 44;
            if (chest.opened()) {
                Draw.fit(g, font, Component.literal("Opened!"), x + w / 2, py + 1, w - 6, 0.7f, 0.45f, Draw.Align.CENTER, THEME.good, true);
            } else if (chest.cost() == 0) {
                Draw.fit(g, font, Component.literal("Free"), x + w / 2, py + 1, w - 6, 0.7f, 0.45f, Draw.Align.CENTER, THEME.good, true);
            } else {
                Chip price = Chip.price("coins", chest.cost(), view.purse());
                SkyScreens.chip(g, font, THEME, x + (w - SkyScreens.chipWidth(font, price, 0.75f)) / 2, py, price, 0.75f);
            }
            int ly = y + 57;
            for (String line : chest.essence()) {
                Draw.fit(g, font, Component.literal(line), x + 5, ly, w - 10, 0.6f, 0.4f, Draw.Align.LEFT, 0xFFFF55FF, false);
                ly += 9;
            }
            int slots = Math.max(1, (w - 8) / 18);
            int ix = x + (w - slots * 18 + 2) / 2;
            int iy = ly + 2;
            int col = 0;
            for (ItemStack stack : chest.items()) {
                if (col == slots) {
                    col = 0;
                    ix = x + (w - slots * 18 + 2) / 2;
                    iy += 18;
                }
                SkyScreens.well(g, THEME, ix - 1, iy - 1);
                Draw.item(g, stack, ix, iy, 1f);
                if (SkyScreens.inside(mouseX, mouseY, ix, iy, 16, 16)) {
                    tooltip = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
                }
                ix += 18;
                col++;
            }
            if (chest.items().isEmpty()) {
                Draw.fit(g, font, Component.literal("No items"), x + w / 2, iy + 4, w - 6, 0.6f, 0.4f, Draw.Align.CENTER, THEME.dim, false);
            }
            if (broke && hover) {
                tooltip = List.of(Component.literal("You can't afford this chest").withColor(Theme.RED));
            }
        }
        Draw.fit(g, font, Component.literal(anyOpened ? "You've opened your chest for this run." : "Pick one chest to open. Click a chest."),
                leftPos + imageWidth / 2, topPos + imageHeight - 16, imageWidth - 20, 0.7f, 0.45f, Draw.Align.CENTER, THEME.dim, false);
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, THEME.accent);
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
            for (int i = 0; i < menu.view().chests().size(); i++) {
                if (SkyScreens.inside(event.x(), event.y(), cardX(i), cardY(), cardWidth(), CARD_H)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    SkyScreens.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
