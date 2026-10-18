package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Bestiary: islands down the left (families found / total), the chosen island's families on the right, each with its
 * head, tier and a bar to the next tier. The header has your Bestiary Milestone and family tiers in all.
 */
public final class BestiaryScreen extends AbstractContainerScreen<BestiaryMenu> {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 230;
    private static final ScreenTheme THEME = ScreenTheme.SKYBLOCK;
    private static final int HEADER = SkyScreens.contentTop(THEME);
    private static final int TOP = HEADER + 17;
    private static final int LIST_W = 100;
    private static final int ROW_H = 21;
    private static final int CARD_W = 36;
    private static final int CARD_H = 44;
    private static final int COLUMNS = 5;

    private double islandScroll;
    private double familyScroll;

    public BestiaryScreen(BestiaryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int listHeight() {
        return imageHeight - TOP - 10;
    }

    private int gridX() {
        return leftPos + THEME.inset + LIST_W + 6;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        BestiaryMenu.View view = menu.view();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Bestiary"),
                Component.literal(view.totalTiers() + " tiers").withColor(THEME.accent));
        // The Milestone strip: your Milestone and the bar to the next one (every 10 tiers: +2 Health; each tier: +1 SkyBlock XP).
        int stripX = leftPos + THEME.inset;
        int stripW = imageWidth - 2 * THEME.inset;
        SkyScreens.card(g, THEME, stripX, topPos + HEADER, stripW, 13, false, false);
        Draw.item(g, new ItemStack(Items.ZOMBIE_HEAD), stripX + 3, topPos + HEADER - 1, 0.9f);
        Draw.fit(g, font, Component.literal("Milestone " + view.milestone()), stripX + 20, topPos + HEADER + 3, 60, 0.75f, 0.5f, Draw.Align.LEFT,
                THEME.accent, false);
        SkyScreens.bar(g, THEME, stripX + 82, topPos + HEADER + 4, 70, 5, (view.totalTiers() % 10) / 10f, THEME.accent);
        Draw.fit(g, font, Component.literal(view.totalTiers() % 10 + "/10"), stripX + 156, topPos + HEADER + 3, 24, 0.65f, 0.4f, Draw.Align.LEFT,
                THEME.muted, false);
        Draw.fit(g, font, Component.literal("+2 Health per Milestone  ·  +1 SkyBlock XP per tier"), stripX + stripW - 5, topPos + HEADER + 3,
                stripW - 190, 0.6f, 0.4f, Draw.Align.RIGHT, THEME.dim, false);
        int top = topPos + TOP;
        int height = listHeight();
        List<Component> tooltip = null;
        // Islands.
        List<BestiaryMenu.IslandView> islands = view.islands();
        islandScroll = Mth.clamp(islandScroll, 0, Math.max(0, islands.size() * ROW_H - height));
        int listX = leftPos + THEME.inset;
        g.enableScissor(listX, top, listX + LIST_W, top + height);
        for (int i = 0; i < islands.size(); i++) {
            BestiaryMenu.IslandView island = islands.get(i);
            int y = top + i * ROW_H - (int) Math.round(islandScroll);
            if (y + ROW_H < top || y > top + height) {
                continue;
            }
            boolean selected = i == view.selected();
            boolean over = SkyScreens.inside(mouseX, mouseY, listX, y, LIST_W, ROW_H - 2) && mouseY >= top && mouseY < top + height;
            boolean complete = island.found() >= island.total() && island.total() > 0;
            SkyScreens.card(g, THEME, listX, y, LIST_W, ROW_H - 2, selected || over, false);
            if (selected) {
                Draw.outline(g, listX, y, LIST_W, ROW_H - 2, THEME.accent);
            }
            Draw.item(g, island.icon(), listX + 3, y + 1, 1f);
            Draw.fit(g, font, Component.literal(island.name()), listX + 22, y + 3, LIST_W - 26, 0.7f, 0.4f, Draw.Align.LEFT,
                    selected ? THEME.accent : THEME.text, false);
            Draw.fit(g, font, Component.literal(island.found() + "/" + island.total()), listX + LIST_W - 4, y + 11, 24, 0.55f, 0.4f,
                    Draw.Align.RIGHT, complete ? THEME.good : THEME.muted, false);
            SkyScreens.bar(g, THEME, listX + 22, y + 12, LIST_W - 50, 4, island.total() <= 0 ? 0f : (float) island.found() / island.total(),
                    complete ? THEME.good : THEME.accent);
        }
        g.disableScissor();
        if (islands.size() * ROW_H > height) {
            Draw.scrollbar(g, listX + LIST_W + 1, top, height, islands.size() * ROW_H, islandScroll, THEME.accent);
        }
        // Families.
        List<BestiaryMenu.FamilyView> families = view.families();
        int rows = (families.size() + COLUMNS - 1) / COLUMNS;
        familyScroll = Mth.clamp(familyScroll, 0, Math.max(0, rows * (CARD_H + 2) - height));
        g.enableScissor(gridX(), top, leftPos + imageWidth - THEME.inset, top + height);
        for (int i = 0; i < families.size(); i++) {
            BestiaryMenu.FamilyView family = families.get(i);
            int x = gridX() + (i % COLUMNS) * (CARD_W + 2);
            int y = top + (i / COLUMNS) * (CARD_H + 2) - (int) Math.round(familyScroll);
            boolean found = family.kills() > 0;
            boolean maxed = family.tier() >= family.max() && family.max() > 0;
            if (y + CARD_H < top || y > top + height) {
                continue;
            }
            boolean over = SkyScreens.inside(mouseX, mouseY, x, y, CARD_W, CARD_H) && mouseY >= top && mouseY < top + height;
            SkyScreens.card(g, THEME, x, y, CARD_W, CARD_H, over, !found);
            if (maxed) {
                Draw.outline(g, x, y, CARD_W, CARD_H, THEME.good);
            }
            g.fill(x + (CARD_W - 20) / 2, y + 2, x + (CARD_W + 20) / 2, y + 22, 0x55000000);
            Draw.item(g, family.head(), x + (CARD_W - 16) / 2, y + 4, 1f);
            Draw.fit(g, font, Component.literal(found ? family.name() : "???"), x + CARD_W / 2, y + 24, CARD_W - 4, 0.5f, 0.35f,
                    Draw.Align.CENTER, found ? THEME.text : THEME.dim, false);
            Draw.fit(g, font, Component.literal(family.tier() + "/" + family.max()), x + CARD_W / 2, y + 31, CARD_W - 4, 0.55f, 0.4f,
                    Draw.Align.CENTER, maxed ? THEME.good : THEME.muted, false);
            float progress = maxed ? 1f : family.next() <= 0 ? 0f : (float) family.kills() / family.next();
            SkyScreens.bar(g, THEME, x + 4, y + CARD_H - 8, CARD_W - 8, 4, Math.min(1f, progress), maxed ? THEME.good : THEME.accent);
            if (over) {
                tooltip = new ArrayList<>();
                tooltip.add(Component.literal(family.name()).withColor(THEME.accent));
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
        if (rows * (CARD_H + 2) > height) {
            Draw.scrollbar(g, leftPos + imageWidth - THEME.inset - 3, top, height, rows * (CARD_H + 2), familyScroll, THEME.accent);
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
        int top = topPos + TOP;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && SkyScreens.inside(event.x(), event.y(), leftPos + THEME.inset, top, LIST_W, listHeight())) {
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
