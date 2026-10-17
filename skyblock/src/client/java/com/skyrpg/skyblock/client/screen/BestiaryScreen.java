package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.bestiary.BestiaryMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Bestiary: islands down the left (families found / total), the chosen island's families on the right, each with its
 * head, tier and a bar to the next tier. The header has your Bestiary Milestone and family tiers in all.
 */
public final class BestiaryScreen extends AbstractContainerScreen<BestiaryMenu> {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 230;
    private static final int TOP = 36;
    private static final int LIST_W = 104;
    private static final int ROW_H = 18;
    private static final int CARD_W = 36;
    private static final int CARD_H = 44;
    private static final int COLUMNS = 5;
    private static final int RED = 0xFFFF5555;

    private double islandScroll;
    private double familyScroll;

    public BestiaryScreen(BestiaryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int listHeight() {
        return imageHeight - TOP - 6;
    }

    private int gridX() {
        return leftPos + LIST_W + 14;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        BestiaryMenu.View view = menu.view();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("Bestiary"),
                Component.literal("Milestone " + view.milestone() + "  ·  " + view.totalTiers() + " tiers").withColor(RED));
        Draw.fit(g, font, Component.literal("Every 10 tiers: a Milestone, +2 Health. Each tier: +1 SkyBlock XP."), leftPos + 10, topPos + 23,
                imageWidth - 20, 0.6f, 0.45f, Draw.Align.LEFT, Theme.DIM, false);
        int top = topPos + TOP;
        int height = listHeight();
        List<Component> tooltip = null;
        // Islands.
        List<BestiaryMenu.IslandView> islands = view.islands();
        islandScroll = Mth.clamp(islandScroll, 0, Math.max(0, islands.size() * ROW_H - height));
        g.enableScissor(leftPos + 6, top, leftPos + 6 + LIST_W, top + height);
        for (int i = 0; i < islands.size(); i++) {
            BestiaryMenu.IslandView island = islands.get(i);
            int y = top + i * ROW_H - (int) Math.round(islandScroll);
            boolean selected = i == view.selected();
            boolean over = SkyScreens.inside(mouseX, mouseY, leftPos + 6, y, LIST_W, ROW_H - 1) && mouseY >= top && mouseY < top + height;
            Draw.panel(g, leftPos + 6, y, LIST_W, ROW_H - 1, selected ? Draw.mix(Theme.CARD, RED, 0.15f) : over ? Theme.CARD_HOVER : Theme.CARD,
                    selected ? RED : Theme.CARD_BORDER);
            Draw.item(g, island.icon(), leftPos + 8, y + 1, 0.85f);
            Draw.fit(g, font, Component.literal(island.name()), leftPos + 24, y + 2, LIST_W - 22, 0.65f, 0.4f, Draw.Align.LEFT, Theme.TEXT, false);
            Draw.fit(g, font, Component.literal(island.found() + "/" + island.total()), leftPos + 24, y + 10, LIST_W - 22, 0.55f, 0.4f,
                    Draw.Align.LEFT, island.found() >= island.total() ? Theme.GREEN : Theme.MUTED, false);
        }
        g.disableScissor();
        // Families.
        List<BestiaryMenu.FamilyView> families = view.families();
        int rows = (families.size() + COLUMNS - 1) / COLUMNS;
        familyScroll = Mth.clamp(familyScroll, 0, Math.max(0, rows * (CARD_H + 2) - height));
        g.enableScissor(gridX(), top, leftPos + imageWidth - 6, top + height);
        for (int i = 0; i < families.size(); i++) {
            BestiaryMenu.FamilyView family = families.get(i);
            int x = gridX() + (i % COLUMNS) * (CARD_W + 2);
            int y = top + (i / COLUMNS) * (CARD_H + 2) - (int) Math.round(familyScroll);
            boolean found = family.kills() > 0;
            boolean maxed = family.tier() >= family.max() && family.max() > 0;
            boolean over = SkyScreens.inside(mouseX, mouseY, x, y, CARD_W, CARD_H) && mouseY >= top && mouseY < top + height;
            Draw.panel(g, x, y, CARD_W, CARD_H, over ? Theme.CARD_HOVER : Theme.CARD, maxed ? Theme.GREEN : found ? Theme.CARD_BORDER
                    : Draw.mix(Theme.CARD, Theme.CARD_BORDER, 0.4f));
            Draw.item(g, family.head(), x + (CARD_W - 16) / 2, y + 3, 1f);
            Draw.fit(g, font, Component.literal(found ? family.name() : "???"), x + CARD_W / 2, y + 21, CARD_W - 4, 0.5f, 0.35f,
                    Draw.Align.CENTER, found ? Theme.TEXT : Theme.DIM, false);
            Draw.fit(g, font, Component.literal(family.tier() + "/" + family.max()), x + CARD_W / 2, y + 28, CARD_W - 4, 0.55f, 0.4f,
                    Draw.Align.CENTER, maxed ? Theme.GREEN : Theme.MUTED, false);
            float progress = maxed ? 1f : family.next() <= 0 ? 0f : (float) family.kills() / family.next();
            Draw.bar(g, x + 3, y + CARD_H - 6, CARD_W - 6, 2, Math.min(1f, progress), maxed ? Theme.GREEN : RED);
            if (over) {
                tooltip = new ArrayList<>();
                tooltip.add(Component.literal(family.name()).withColor(RED));
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Kills: §f%,d", family.kills())));
                tooltip.add(Component.literal("§7Tier: §a" + family.tier() + "§8/" + family.max()));
                if (!maxed) {
                    tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Next tier at §e%,d §7kills", family.next())));
                } else {
                    tooltip.add(Component.literal("§aMaxed!"));
                }
            }
        }
        g.disableScissor();
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, RED);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int top = topPos + TOP;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && SkyScreens.inside(event.x(), event.y(), leftPos + 6, top, LIST_W, listHeight())) {
            int index = (int) ((event.y() - top + islandScroll) / ROW_H);
            if (index >= 0 && index < menu.view().islands().size() && index != menu.view().selected()) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                SkyScreens.click();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < gridX()) {
            islandScroll -= scrollY * 18;
        } else {
            familyScroll -= scrollY * 24;
        }
        return true;
    }
}
