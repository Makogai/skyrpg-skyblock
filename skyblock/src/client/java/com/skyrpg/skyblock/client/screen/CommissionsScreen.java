package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.mining.CommissionsMenu;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Commissions board: a card per commission with its progress; a finished one glows and is claimed with a click. */
public final class CommissionsScreen extends AbstractContainerScreen<CommissionsMenu> {
    private static final int WIDTH = 260;
    private static final int CARD_H = 38;
    private static final int TOP = 40;
    private static final int AQUA = 0xFF55FFFF;

    public CommissionsScreen(CommissionsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, TOP + menu.view().slots().size() * (CARD_H + 4) + 26);
    }

    private int cardY(int i) {
        return topPos + TOP + i * (CARD_H + 4);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        CommissionsMenu.View view = menu.view();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("Commissions"),
                Component.literal(view.completed() + " completed").withColor(Theme.MUTED));
        String daily = view.dailyLeft() > 0 ? view.dailyLeft() + " daily bonus" + (view.dailyLeft() == 1 ? "" : "es") + " left today (+900 HotM XP)"
                : "Daily bonuses used up";
        Draw.fit(g, font, Component.literal(daily), leftPos + 10, topPos + 24, imageWidth - 20, 0.7f, 0.5f, Draw.Align.LEFT,
                view.dailyLeft() > 0 ? 0xFFAA55FF : Theme.DIM, false);
        List<CommissionsMenu.SlotView> slots = view.slots();
        for (int i = 0; i < slots.size(); i++) {
            CommissionsMenu.SlotView slot = slots.get(i);
            int x = leftPos + 8;
            int y = cardY(i);
            int w = imageWidth - 16;
            boolean over = slot.done() && SkyScreens.inside(mouseX, mouseY, x, y, w, CARD_H);
            Draw.panel(g, x, y, w, CARD_H, over ? Theme.CARD_HOVER : slot.done() ? Draw.mix(Theme.CARD, Theme.GREEN, 0.12f) : Theme.CARD,
                    slot.done() ? Theme.GREEN : Theme.CARD_BORDER);
            Draw.item(g, new ItemStack(slot.done() ? Items.WRITABLE_BOOK : Items.PAPER), x + 5, y + 5, 1f);
            Draw.fit(g, font, Component.literal(slot.name()), x + 26, y + 4, w - 32, 0.9f, 0.55f, Draw.Align.LEFT, slot.done() ? Theme.GREEN : AQUA, false);
            Draw.fit(g, font, Component.literal(slot.objective()), x + 26, y + 15, w - 32, 0.7f, 0.45f, Draw.Align.LEFT, Theme.MUTED, false);
            float progress = slot.amount() <= 0 ? 1f : (float) slot.progress() / slot.amount();
            Draw.bar(g, x + 26, y + 27, w - 90, 4, progress, slot.done() ? Theme.GREEN : AQUA);
            String label = slot.done() ? "Click to claim!" : slot.progress() + "/" + slot.amount();
            Draw.fit(g, font, Component.literal(label), x + w - 6, y + 25, 70, 0.7f, 0.45f, Draw.Align.RIGHT, slot.done() ? Theme.GREEN : Theme.TEXT, false);
        }
        Draw.fit(g, font, Component.literal("Rewards: HotM XP, 500 Mithril Powder, Mining XP"), leftPos + imageWidth / 2, topPos + imageHeight - 14,
                imageWidth - 20, 0.65f, 0.45f, Draw.Align.CENTER, Theme.DIM, false);
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
                if (slots.get(i).done() && SkyScreens.inside(event.x(), event.y(), leftPos + 8, cardY(i), imageWidth - 16, CARD_H)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    SkyScreens.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
