package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.dungeon.MalikMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

/** Malik: upgrade the held dungeon item with essence, one star at a time. */
public final class MalikScreen extends AbstractContainerScreen<MalikMenu> {
    private static final int WIDTH = 260;
    private static final int HEIGHT = 214;
    private static final int PINK = 0xFFFF55FF;
    private static final int GOLD = 0xFFFFAA00;

    public MalikScreen(MalikMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int tabX() {
        return leftPos + imageWidth - 112;
    }

    private int perkY(int i) {
        return topPos + 42 + i * 14;
    }

    private void shop(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        MalikMenu.View view = menu.view();
        for (int i = 0; i < view.perks().size(); i++) {
            int y = perkY(i);
            boolean over = SkyScreens.inside(mouseX, mouseY, leftPos + 10, y, imageWidth - 20, 12);
            boolean wither = i >= 5;
            Draw.panel(g, leftPos + 10, y, imageWidth - 20, 12, over ? Theme.CARD_HOVER : Theme.CARD, wither ? 0xFF555555 : 0xFFAA00AA);
            Draw.fit(g, font, Component.literal(view.perks().get(i)), leftPos + 14, y + 3, imageWidth - 28, 0.62f, 0.4f, Draw.Align.LEFT,
                    view.perks().get(i).endsWith("maxed") ? Theme.GREEN : Theme.TEXT, false);
        }
        Draw.fit(g, font, Component.literal(view.essence() + "   ·   click a perk to buy its next level, works in the Catacombs"),
                leftPos + imageWidth / 2, topPos + imageHeight - 12, imageWidth - 16, 0.6f, 0.4f, Draw.Align.CENTER, Theme.DIM, false);
    }

    private int buttonX() {
        return leftPos + (imageWidth - 120) / 2;
    }

    private int buttonY() {
        return topPos + imageHeight - 30;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        MalikMenu.View view = menu.view();
        boolean vulcan = view.who().equals("Vulcan");
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal(view.who()),
                Component.literal(vulcan ? "Kuudra armour stars" : view.shop() ? "Essence shop" : "Dungeon item stars").withColor(vulcan ? 0xFFFF5555 : PINK));
        if (!vulcan) {
            boolean tabOver = SkyScreens.inside(mouseX, mouseY, tabX(), topPos + 24, 100, 12);
            Draw.panel(g, tabX(), topPos + 24, 100, 12, tabOver ? Theme.CARD_HOVER : Theme.CARD, PINK);
            Draw.fit(g, font, Component.literal(view.shop() ? "\u2190 Item stars" : "Essence shop \u2192"), tabX() + 50, topPos + 27, 94, 0.7f, 0.45f,
                    Draw.Align.CENTER, PINK, false);
        }
        if (view.shop()) {
            shop(g, mouseX, mouseY);
            Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
            return;
        }
        int x = leftPos + 14;
        int y = topPos + 42;
        int w = imageWidth - 28;
        List<Component> tooltip = null;
        Draw.panel(g, x, y, w, 44, Theme.CARD, view.item().isEmpty() ? Theme.CARD_BORDER : GOLD);
        if (view.item().isEmpty()) {
            Draw.fit(g, font, Component.literal(vulcan ? "Hold a piece of Kuudra armour, then talk to me." : "Hold a dungeon item, then talk to me."),
                    x + w / 2, y + 17, w - 10, 0.8f, 0.5f, Draw.Align.CENTER,
                    Theme.MUTED, false);
        } else {
            Draw.item(g, view.item(), x + 8, y + 10, 1.5f);
            Draw.fit(g, font, view.item().getHoverName(), x + 40, y + 8, w - 48, 0.85f, 0.5f, Draw.Align.LEFT, Theme.TEXT, false);
            String stars = "✪".repeat(view.stars()) + "☆".repeat(Math.max(0, view.max() - view.stars()));
            Draw.fit(g, font, Component.literal(stars + "   " + view.stars() + "/" + view.max() + " stars"), x + 40, y + 24, w - 48,
                    0.8f, 0.5f, Draw.Align.LEFT, GOLD, false);
            if (SkyScreens.inside(mouseX, mouseY, x + 8, y + 10, 24, 24)) {
                tooltip = new ArrayList<>(view.item().getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
            }
        }
        int cy = y + 52;
        if (!view.item().isEmpty()) {
            if (view.cost().isEmpty()) {
                Draw.fit(g, font, Component.literal("Every star is on it!"), x, cy, w, 0.8f, 0.5f, Draw.Align.LEFT, Theme.GREEN, false);
            } else {
                Draw.fit(g, font, Component.literal("Next star costs:"), x, cy, w, 0.75f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);
                for (String line : view.cost()) {
                    cy += 10;
                    Draw.fit(g, font, Component.literal(" " + line), x, cy, w, 0.75f, 0.5f, Draw.Align.LEFT, PINK, false);
                }
            }
        }
        Draw.fit(g, font, Component.literal(view.essence()), leftPos + imageWidth / 2, buttonY() - 12, w, 0.65f, 0.4f, Draw.Align.CENTER, Theme.DIM, false);
        boolean can = !view.item().isEmpty() && !view.cost().isEmpty();
        boolean over = SkyScreens.inside(mouseX, mouseY, buttonX(), buttonY(), 120, 16);
        Draw.panel(g, buttonX(), buttonY(), 120, 16, over && can ? Theme.CARD_HOVER : Theme.CARD, can ? Theme.GREEN : Theme.MUTED);
        Draw.fit(g, font, Component.literal("Add a star"), buttonX() + 60, buttonY() + 4, 114, 0.75f, 0.5f, Draw.Align.CENTER,
                can ? Theme.GREEN : Theme.MUTED, false);
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, GOLD);
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
            if (!menu.view().who().equals("Vulcan") && SkyScreens.inside(event.x(), event.y(), tabX(), topPos + 24, 100, 12)) {
                return press(MalikMenu.TAB);
            }
            if (menu.view().shop()) {
                for (int i = 0; i < menu.view().perks().size(); i++) {
                    if (SkyScreens.inside(event.x(), event.y(), leftPos + 10, perkY(i), imageWidth - 20, 12)) {
                        return press(MalikMenu.PERK + i);
                    }
                }
            } else if (SkyScreens.inside(event.x(), event.y(), buttonX(), buttonY(), 120, 16)) {
                return press(MalikMenu.UPGRADE);
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
