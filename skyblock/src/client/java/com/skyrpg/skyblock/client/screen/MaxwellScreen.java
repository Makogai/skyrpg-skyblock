package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.accessory.MaxwellMenu;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Maxwell's Accessory Powers: your Magical Power and its multiplier, then a card per power with what it gives you now,
 * its unique bonus and what you can do with it (pick it, learn it with 9 Power Stones, or the Combat level it needs).
 */
public final class MaxwellScreen extends AbstractContainerScreen<MaxwellMenu> {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 236;
    private static final int TOP = 52;
    private static final int CARD_H = 34;
    private static final int GOLD = 0xFFFFAA00;
    private static final ScreenTheme THEME = ScreenTheme.MAGIC;

    private double scroll;

    public MaxwellScreen(MaxwellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int listTop() {
        return topPos + TOP;
    }

    private int listHeight() {
        return imageHeight - TOP - 14;
    }

    private int cardX() {
        return leftPos + THEME.inset;
    }

    private int cardW() {
        return imageWidth - 2 * THEME.inset - (menu.view().powers().size() * (CARD_H + 3) > listHeight() ? 6 : 0);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        MaxwellMenu.View view = menu.view();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Accessory Powers"),
                Component.literal("Maxwell").withColor(THEME.muted));
        int hy = topPos + SkyScreens.contentTop(THEME) + 1;
        Draw.item(g, new ItemStack(Items.NETHER_STAR), leftPos + THEME.inset, hy - 3, 0.75f);
        Draw.fit(g, font, Component.literal(view.magicalPower() + " Magical Power").withColor(GOLD)
                        .append(Component.literal("  ·  stats ×" + String.format(java.util.Locale.ROOT, "%.2f", view.multiplier())).withColor(THEME.muted)),
                leftPos + THEME.inset + 16, hy, imageWidth - 2 * THEME.inset - 16, 0.9f, 0.6f, Draw.Align.LEFT, THEME.text, true);

        List<MaxwellMenu.PowerView> powers = view.powers();
        int top = listTop();
        int height = listHeight();
        int content = powers.size() * (CARD_H + 3);
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - height));
        List<Component> tooltip = null;
        g.enableScissor(leftPos + 4, top, leftPos + imageWidth - 4, top + height);
        for (int i = 0; i < powers.size(); i++) {
            MaxwellMenu.PowerView power = powers.get(i);
            int cy = top + i * (CARD_H + 3) - (int) Math.round(scroll);
            if (cy + CARD_H < top || cy > top + height) {
                continue;
            }
            int cx = cardX();
            int cw = cardW();
            boolean clickable = power.state() == 0 || power.state() == 2 && power.stonesHave() >= MaxwellMenu.STONES;
            boolean hover = SkyScreens.inside(mouseX, mouseY, cx, Math.max(cy, top), cw, Math.min(CARD_H, top + height - Math.max(cy, top)));
            boolean over = clickable && hover;
            SkyScreens.card(g, THEME, cx, cy, cw, CARD_H, over || power.state() == 1, power.state() == 3);
            if (power.state() == 1) {
                Draw.outline(g, cx, cy, cw, CARD_H, GOLD);
            }
            int nameColor = power.state() == 3 ? THEME.dim : power.state() == 1 ? GOLD : THEME.text;
            SkyScreens.well(g, THEME, cx + 6, cy + (CARD_H - 18) / 2);
            ItemStack icon = power.stone().isEmpty() ? new ItemStack(Items.AMETHYST_SHARD) : power.stone();
            Draw.item(g, icon, cx + 7, cy + (CARD_H - 16) / 2, 1f);
            int tx = cx + 30;
            int right = cx + cw - 6;
            int rightW = power.state() == 2 ? 100 : 62;
            Draw.fit(g, font, Component.literal(power.name()), tx, cy + 4, right - rightW - tx, 1f, 0.6f, Draw.Align.LEFT, nameColor, true);
            Draw.fit(g, font, Component.literal(power.stats().isEmpty() ? "No stats" : power.stats()), tx, cy + 15, cw - 36, 0.7f, 0.4f,
                    Draw.Align.LEFT, power.state() == 3 ? THEME.dim : THEME.good, false);
            if (!power.unique().isEmpty()) {
                Draw.fit(g, font, Component.literal("Unique: " + power.unique()), tx, cy + 24, cw - 36, 0.65f, 0.4f, Draw.Align.LEFT,
                        power.state() == 3 ? THEME.dim : THEME.accent, false);
            }
            switch (power.state()) {
                case 1 -> Draw.fit(g, font, Component.literal("Your power"), right, cy + 5, rightW, 0.8f, 0.45f, Draw.Align.RIGHT, GOLD, true);
                case 0 -> SkyScreens.button(g, font, THEME, right - 50, cy + 4, 50, 13, "Select", true, over);
                case 2 -> {
                    boolean enough = power.stonesHave() >= MaxwellMenu.STONES;
                    SkyScreens.button(g, font, THEME, right - 44, cy + 4, 44, 13, "Learn", enough, over);
                    if (!power.stone().isEmpty()) {
                        Chip chip = Chip.item(power.stone(), MaxwellMenu.STONES, power.stonesHave());
                        String have = " (" + power.stonesHave() + ")";
                        SkyScreens.chip(g, font, THEME, right - 44 - 6 - SkyScreens.chipWidth(font, chip, 0.7f)
                                - Math.round(font.width(have) * 0.6f), cy + 6, chip, 0.7f);
                        Draw.scaledText(g, font, Component.literal(have), right - 44 - 4 - Math.round(font.width(have) * 0.6f), cy + 7, 0.6f, THEME.muted);
                    }
                }
                default -> Draw.fit(g, font, Component.literal("Needs Combat " + power.combat()), right, cy + 5, rightW, 0.8f, 0.45f,
                        Draw.Align.RIGHT, Theme.RED, false);
            }
            if (hover) {
                tooltip = new ArrayList<>();
                tooltip.add(Component.literal(power.name()).withColor(nameColor));
                tooltip.add(Component.literal(power.stats().isEmpty() ? "No stats" : power.stats()).withColor(THEME.good));
                if (!power.unique().isEmpty()) {
                    tooltip.add(Component.literal("Unique: " + power.unique()).withColor(THEME.accent));
                }
                tooltip.add(Component.empty());
                tooltip.add(switch (power.state()) {
                    case 1 -> Component.literal("Your power right now").withColor(GOLD);
                    case 0 -> Component.literal("Click to select").withColor(THEME.good);
                    case 2 -> Component.literal(power.stonesHave() >= MaxwellMenu.STONES ? "Click to learn it with " + MaxwellMenu.STONES + " "
                            + power.stone().getHoverName().getString() : "Needs " + MaxwellMenu.STONES + " " + power.stone().getHoverName().getString()
                            + " (" + power.stonesHave() + ")").withColor(power.stonesHave() >= MaxwellMenu.STONES ? THEME.good : Theme.RED);
                    default -> Component.literal("Needs Combat " + power.combat()).withColor(Theme.RED);
                });
            }
        }
        g.disableScissor();
        if (content > height) {
            Draw.scrollbar(g, leftPos + imageWidth - THEME.inset - 4, top, height, content, scroll, THEME.accent);
        }
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
            List<MaxwellMenu.PowerView> powers = menu.view().powers();
            int top = listTop();
            if (SkyScreens.inside(event.x(), event.y(), cardX(), top, cardW(), listHeight())) {
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
