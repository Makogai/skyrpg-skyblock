package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.skyblock.mining.CommissionsMenu;
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
 * The Commissions board, in the mining theme: a card per commission with its icon, objective and progress bar; a finished
 * one glows green with a Claim button (the whole card claims it too). The daily bonus sits under the header.
 */
public final class CommissionsScreen extends AbstractContainerScreen<CommissionsMenu> {
    private static final ScreenTheme THEME = ScreenTheme.MINING;
    private static final int WIDTH = 260;
    private static final int CARD_H = 34;
    private static final int GAP = 3;
    private static final int TOP = SkyScreens.contentTop(THEME) + 12;
    private static final int PURPLE = 0xFFAA55FF;

    public CommissionsScreen(CommissionsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, Math.min(236, TOP + menu.view().slots().size() * (CARD_H + GAP) + 16));
    }

    private int cardX() {
        return leftPos + THEME.inset;
    }

    private int cardW() {
        return imageWidth - 2 * THEME.inset;
    }

    private int cardY(int i) {
        return topPos + TOP + i * (CARD_H + GAP);
    }

    private int[] claimBox(int i) {
        return new int[] {cardX() + cardW() - 56, cardY(i) + (CARD_H - 14) / 2, 50, 14};
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        CommissionsMenu.View view = menu.view();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Commissions"),
                Component.literal(view.completed() + " completed").withColor(THEME.muted));
        String daily = view.dailyLeft() > 0 ? view.dailyLeft() + " daily bonus" + (view.dailyLeft() == 1 ? "" : "es") + " left today (+900 HotM XP)"
                : "Daily bonuses used up";
        Draw.item(g, new ItemStack(view.dailyLeft() > 0 ? Items.NETHER_STAR : Items.COAL), leftPos + THEME.inset, topPos + SkyScreens.contentTop(THEME) - 1, 0.6f);
        Draw.fit(g, font, Component.literal(daily), leftPos + THEME.inset + 12, topPos + SkyScreens.contentTop(THEME) + 1, imageWidth - 2 * THEME.inset - 12,
                0.7f, 0.5f, Draw.Align.LEFT, view.dailyLeft() > 0 ? PURPLE : THEME.dim, false);
        List<CommissionsMenu.SlotView> slots = view.slots();
        List<Component> tooltip = null;
        for (int i = 0; i < slots.size(); i++) {
            CommissionsMenu.SlotView slot = slots.get(i);
            int x = cardX();
            int y = cardY(i);
            int w = cardW();
            boolean done = slot.done();
            boolean over = SkyScreens.inside(mouseX, mouseY, x, y, w, CARD_H);
            SkyScreens.card(g, THEME, x, y, w, CARD_H, over && done, false);
            if (done) {
                Draw.outline(g, x, y, w, CARD_H, THEME.good);
            }
            int iy = y + (CARD_H - 18) / 2;
            g.fill(x + 4, iy - 1, x + 24, iy + 19, 0x55000000);
            Draw.item(g, new ItemStack(done ? Items.WRITABLE_BOOK : Items.PAPER), x + 5, iy, 1.1f);
            int tx = x + 30;
            int right = done ? claimBox(i)[0] - 4 : x + w - 6;
            Draw.fit(g, font, Component.literal(slot.name()), tx, y + 5, right - tx, 0.9f, 0.55f, Draw.Align.LEFT, done ? THEME.good : THEME.accent, true);
            Draw.fit(g, font, Component.literal(slot.objective()), tx, y + 16, right - tx, 0.7f, 0.45f, Draw.Align.LEFT, THEME.muted, false);
            float progress = slot.amount() <= 0 ? 1f : (float) slot.progress() / slot.amount();
            String label = done ? "Done" : String.format(Locale.ROOT, "%,d/%,d", slot.progress(), slot.amount());
            int labelW = Math.round(font.width(label) * 0.65f) + 4;
            SkyScreens.bar(g, THEME, tx, y + 25, right - tx - labelW - 2, 5, progress, done ? THEME.good : THEME.accent);
            Draw.fit(g, font, Component.literal(label), right, y + 25, labelW, 0.65f, 0.4f, Draw.Align.RIGHT, done ? THEME.good : THEME.text, false);
            if (done) {
                int[] b = claimBox(i);
                SkyScreens.button(g, font, THEME, b[0], b[1], b[2], b[3], "Claim", true, SkyScreens.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]));
            }
            if (over) {
                tooltip = List.of(Component.literal(slot.name()).withColor(done ? THEME.good : THEME.accent),
                        Component.literal(slot.objective()).withColor(THEME.muted),
                        Component.empty(),
                        Component.literal(String.format(Locale.ROOT, "Progress: %,d/%,d", slot.progress(), slot.amount())).withColor(THEME.text),
                        Component.literal("Rewards: HotM XP, powder and Mining XP").withColor(THEME.dim),
                        Component.empty(),
                        Component.literal(done ? "Click to claim!" : "Keep mining!").withColor(done ? THEME.good : THEME.muted));
            }
        }
        Draw.fit(g, font, Component.literal("Rewards: HotM XP, powder and Mining XP"), leftPos + imageWidth / 2, topPos + imageHeight - 17,
                imageWidth - 2 * THEME.inset, 0.62f, 0.45f, Draw.Align.CENTER, THEME.dim, false);
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
            List<CommissionsMenu.SlotView> slots = menu.view().slots();
            for (int i = 0; i < slots.size(); i++) {
                if (slots.get(i).done() && SkyScreens.inside(event.x(), event.y(), cardX(), cardY(i), cardW(), CARD_H)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    SkyScreens.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
