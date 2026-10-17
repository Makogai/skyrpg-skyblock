package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.accessory.MaxwellMenu;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * Maxwell's Accessory Powers: your Magical Power and its multiplier, then a card per power with what it gives you now,
 * its unique bonus and what you can do with it (pick it, learn it with 9 Power Stones, or the Combat level it needs).
 */
public final class MaxwellScreen extends AbstractContainerScreen<MaxwellMenu> {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 220;
    private static final int TOP = 40;
    private static final int CARD_H = 32;
    private static final int GOLD = 0xFFFFAA00;

    private double scroll;

    public MaxwellScreen(MaxwellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int listTop() {
        return topPos + TOP;
    }

    private int listHeight() {
        return imageHeight - TOP - 8;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        MaxwellMenu.View view = menu.view();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("Accessory Powers"),
                Component.literal("Maxwell").withColor(Theme.MUTED));
        Draw.fit(g, font, Component.literal(view.magicalPower() + " Magical Power").withColor(GOLD)
                        .append(Component.literal("  ·  stats ×" + String.format(java.util.Locale.ROOT, "%.2f", view.multiplier())).withColor(Theme.MUTED)),
                leftPos + 10, topPos + 24, imageWidth - 20, 0.9f, 0.6f, Draw.Align.LEFT, Theme.TEXT, false);

        List<MaxwellMenu.PowerView> powers = view.powers();
        int top = listTop();
        int height = listHeight();
        int content = powers.size() * (CARD_H + 3);
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - height));
        g.enableScissor(leftPos + 4, top, leftPos + imageWidth - 4, top + height);
        for (int i = 0; i < powers.size(); i++) {
            MaxwellMenu.PowerView power = powers.get(i);
            int cy = top + i * (CARD_H + 3) - (int) Math.round(scroll);
            if (cy + CARD_H < top || cy > top + height) {
                continue;
            }
            int cx = leftPos + 8;
            int cw = imageWidth - 16;
            boolean clickable = power.state() == 0 || power.state() == 2 && power.stonesHave() >= MaxwellMenu.STONES;
            boolean over = clickable && SkyScreens.inside(mouseX, mouseY, cx, Math.max(cy, top), cw, CARD_H);
            int border = power.state() == 1 ? GOLD : over ? Theme.ACCENT : Theme.CARD_BORDER;
            Draw.panel(g, cx, cy, cw, CARD_H, power.state() == 1 ? Draw.mix(Theme.CARD, GOLD, 0.12f) : over ? Theme.CARD_HOVER : Theme.CARD, border);
            int nameColor = power.state() == 3 ? Theme.DIM : power.state() == 1 ? GOLD : Theme.TEXT;
            Draw.fit(g, font, Component.literal(power.name()), cx + 6, cy + 4, cw / 2, 1f, 0.6f, Draw.Align.LEFT, nameColor, false);
            Draw.fit(g, font, Component.literal(power.stats().isEmpty() ? "No stats" : power.stats()), cx + 6, cy + 15, cw - 12, 0.7f, 0.4f,
                    Draw.Align.LEFT, power.state() == 3 ? Theme.DIM : Theme.GREEN, false);
            if (!power.unique().isEmpty()) {
                Draw.fit(g, font, Component.literal("Unique: " + power.unique()), cx + 6, cy + 23, cw - 12, 0.65f, 0.4f, Draw.Align.LEFT,
                        Theme.AQUA, false);
            }
            String state = switch (power.state()) {
                case 1 -> "Your power";
                case 0 -> "Click to select";
                case 2 -> "Learn: " + MaxwellMenu.STONES + "× " + power.stone().getHoverName().getString() + " (" + power.stonesHave() + ")";
                default -> "Needs Combat " + power.combat();
            };
            int stateColor = power.state() == 1 ? GOLD : power.state() == 0 ? Theme.ACCENT : power.state() == 2
                    ? (power.stonesHave() >= MaxwellMenu.STONES ? Theme.GREEN : Theme.MUTED) : Theme.RED;
            Draw.fit(g, font, Component.literal(state), cx + cw - 6, cy + 4, cw / 2, 0.8f, 0.45f, Draw.Align.RIGHT, stateColor, false);
            if (power.state() == 2 && !power.stone().isEmpty()) {
                Draw.item(g, power.stone(), cx + cw - 22, cy + 14, 0.9f);
            }
        }
        g.disableScissor();
        if (content > height) {
            Draw.scrollbar(g, leftPos + imageWidth - 5, top, height, content, scroll, GOLD);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            List<MaxwellMenu.PowerView> powers = menu.view().powers();
            int top = listTop();
            if (SkyScreens.inside(event.x(), event.y(), leftPos + 8, top, imageWidth - 16, listHeight())) {
                int index = (int) ((event.y() - top + scroll) / (CARD_H + 3));
                if (index >= 0 && index < powers.size() && (event.y() - top + scroll) % (CARD_H + 3) < CARD_H) {
                    MaxwellMenu.PowerView power = powers.get(index);
                    if (power.state() == 0) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                        SkyScreens.click();
                    } else if (power.state() == 2 && power.stonesHave() >= MaxwellMenu.STONES) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MaxwellMenu.LEARN + index);
                        SkyScreens.click();
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * 20;
        return true;
    }
}
