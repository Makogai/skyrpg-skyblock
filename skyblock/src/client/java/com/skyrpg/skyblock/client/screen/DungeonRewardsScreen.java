package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.dungeon.RewardsMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** The Catacombs' reward chests: one card per chest with what's in it and its price; click one to open it. */
public final class DungeonRewardsScreen extends AbstractContainerScreen<RewardsMenu> {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 200;
    private static final String[] NAMES = {"Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock"};
    private static final int[] COLORS = {0xFFB07A3E, 0xFFFFAA00, 0xFF55FFFF, 0xFF55FF55, 0xFFA77BEA, 0xFF8C8C8C};

    public DungeonRewardsScreen(RewardsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int cardWidth() {
        int n = Math.max(1, menu.view().chests().size());
        return Math.min(96, (imageWidth - 20 - (n - 1) * 6) / n);
    }

    private int cardX(int i) {
        int n = menu.view().chests().size();
        int total = n * cardWidth() + (n - 1) * 6;
        return leftPos + (imageWidth - total) / 2 + i * (cardWidth() + 6);
    }

    private int cardY() {
        return topPos + 50;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        RewardsMenu.View view = menu.view();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("Reward Chests"),
                Component.literal(view.floor()).withColor(Theme.RED));
        Draw.fit(g, font, Component.literal("Score " + view.score() + "  ·  Rank " + view.rank() + "  ·  Purse " + String.format(Locale.ROOT, "%,d", view.purse())),
                leftPos + imageWidth / 2, topPos + 30, imageWidth - 20, 0.8f, 0.5f, Draw.Align.CENTER, Theme.TEXT, false);
        boolean anyOpened = view.chests().stream().anyMatch(RewardsMenu.ChestView::opened);
        List<Component> tooltip = null;
        int w = cardWidth();
        int h = 128;
        for (int i = 0; i < view.chests().size(); i++) {
            RewardsMenu.ChestView chest = view.chests().get(i);
            int x = cardX(i);
            int y = cardY();
            int color = COLORS[Math.min(chest.tier(), COLORS.length - 1)];
            boolean hover = SkyScreens.inside(mouseX, mouseY, x, y, w, h);
            boolean dim = anyOpened && !chest.opened();
            Draw.panel(g, x, y, w, h, hover && !anyOpened ? Theme.CARD_HOVER : Theme.CARD, dim ? Theme.CARD_BORDER : color);
            g.fill(x, y, x + w, y + 2, dim ? Theme.DIM : color);
            Draw.fit(g, font, Component.literal(NAMES[Math.min(chest.tier(), NAMES.length - 1)] + " Chest"), x + w / 2, y + 6, w - 6, 0.8f, 0.5f,
                    Draw.Align.CENTER, dim ? Theme.DIM : color, false);
            String price = chest.opened() ? "Opened!" : chest.cost() == 0 ? "Free" : String.format(Locale.ROOT, "%,d coins", chest.cost());
            Draw.fit(g, font, Component.literal(price), x + w / 2, y + 17, w - 6, 0.7f, 0.45f, Draw.Align.CENTER,
                    chest.opened() ? Theme.GREEN : chest.cost() > view.purse() ? Theme.RED : Theme.MUTED, false);
            int ly = y + 30;
            for (String line : chest.essence()) {
                Draw.fit(g, font, Component.literal(line), x + 5, ly, w - 10, 0.6f, 0.4f, Draw.Align.LEFT, 0xFFFF55FF, false);
                ly += 9;
            }
            int ix = x + 5;
            int iy = ly + 2;
            for (ItemStack stack : chest.items()) {
                if (ix + 16 > x + w - 4) {
                    ix = x + 5;
                    iy += 18;
                }
                Draw.item(g, stack, ix, iy, 1f);
                if (SkyScreens.inside(mouseX, mouseY, ix, iy, 16, 16)) {
                    tooltip = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
                }
                ix += 18;
            }
            if (chest.items().isEmpty()) {
                Draw.fit(g, font, Component.literal("No items"), x + w / 2, iy + 4, w - 6, 0.6f, 0.4f, Draw.Align.CENTER, Theme.DIM, false);
            }
        }
        Draw.fit(g, font, Component.literal(anyOpened ? "You've opened your chest for this run." : "Pick one chest to open. Click a chest."),
                leftPos + imageWidth / 2, topPos + imageHeight - 16, imageWidth - 20, 0.7f, 0.45f, Draw.Align.CENTER, Theme.DIM, false);
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, Theme.ACCENT);
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
                if (SkyScreens.inside(event.x(), event.y(), cardX(i), cardY(), cardWidth(), 128)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    SkyScreens.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
