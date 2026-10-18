package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.skyblock.dungeon.MalikMenu;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Malik (dungeon theme) and Vulcan (Crimson theme): the held item with its stars and the next star's cost as chips, and for
 * Malik an essence shop of perk cards. The NPC's portrait and your balances (coins and essences) sit on the left.
 */
public final class MalikScreen extends AbstractContainerScreen<MalikMenu> {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 226;
    private static final int GOLD = 0xFFFFAA00;
    private static final int GAP = 3;
    private static final int PERK_H = 38;
    private int scroll;
    private int hoveredPerk = -1;
    private boolean hoverItem;

    public MalikScreen(MalikMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private boolean vulcan() {
        return menu.view().who().equals("Vulcan");
    }

    /** Vulcan is the Crimson Isle's; Malik is the dungeons'. */
    private ScreenTheme theme() {
        return vulcan() ? ScreenTheme.CRIMSON : ScreenTheme.DUNGEON;
    }

    // ---------------------------------------------------------------- layout

    private boolean npcView() {
        return menu.view().npc() >= 0;
    }

    private int contentX() {
        return leftPos + theme().inset + (npcView() ? NpcCards.PANEL + 6 : 0);
    }

    private int contentY() {
        return topPos + SkyScreens.contentTop(theme());
    }

    private int contentW() {
        return leftPos + imageWidth - theme().inset - contentX();
    }

    private int contentBottom() {
        return topPos + imageHeight - 20;
    }

    /** Where the tabs end and the page starts (Vulcan has no tabs). */
    private int pageY() {
        return contentY() + (vulcan() ? 0 : 19);
    }

    private int[] tabBox(int i) {
        return new int[] {contentX() + i * 76, contentY(), 72, 15};
    }

    private int columns() {
        return contentW() >= 280 ? 3 : 2;
    }

    private int visibleRows() {
        return Math.max(1, (contentBottom() - pageY() + GAP) / (PERK_H + GAP));
    }

    private int totalRows() {
        return (menu.view().perks().size() + columns() - 1) / columns();
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - visibleRows());
    }

    private int[] perkBox(int index) {
        int cols = columns();
        int row = index / cols - scroll;
        if (row < 0 || row >= visibleRows()) {
            return null;
        }
        int scrollbar = maxScroll() > 0 ? 6 : 0;
        int w = (contentW() - scrollbar - GAP * (cols - 1)) / cols;
        return new int[] {contentX() + index % cols * (w + GAP), pageY() + row * (PERK_H + GAP), w, PERK_H};
    }

    private int[] buttonBox() {
        int w = 130;
        return new int[] {contentX() + (contentW() - w) / 2, contentBottom() - 18, w, 18};
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        MalikMenu.View view = menu.view();
        ScreenTheme theme = theme();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        SkyScreens.window(g, font, theme, leftPos, topPos, imageWidth, imageHeight, Component.literal(view.who()),
                Component.literal(vulcan() ? "Kuudra armour stars" : view.shop() ? "Essence shop" : "Dungeon item stars"));
        if (npcView()) {
            NpcCards.npcPanel(g, font, theme, minecraft, leftPos + theme.inset, contentY(), contentBottom() - contentY(), view.npc(), "Your wallet",
                    view.wallet(), mouseX, mouseY);
        }
        hoveredPerk = -1;
        hoverItem = false;
        if (!vulcan()) {
            String[] labels = {"Item stars", "Essence shop"};
            for (int i = 0; i < 2; i++) {
                int[] t = tabBox(i);
                SkyScreens.tab(g, font, theme, t[0], t[1], t[2], t[3], labels[i], view.shop() == (i == 1),
                        SkyScreens.inside(mouseX, mouseY, t[0], t[1], t[2], t[3]));
            }
        }
        String hint;
        if (view.shop()) {
            shop(g, theme, mouseX, mouseY);
            hint = "Click a perk to buy its next level. Perks work in the Catacombs.";
        } else {
            stars(g, theme, mouseX, mouseY);
            hint = view.item().isEmpty() ? "" : "Stars raise the item's stats in the Catacombs.";
        }
        int fx = (contentX() + leftPos + imageWidth - theme.inset) / 2;
        Draw.fit(g, font, Component.literal(npcView() ? hint : hint.isEmpty() ? view.essence() : view.essence() + "   ·   " + hint), fx,
                topPos + imageHeight - 18, leftPos + imageWidth - theme.inset - contentX() - 4, 0.62f, 0.4f, Draw.Align.CENTER, theme.dim, false);
    }

    /** The held item, its stars, the next star's cost and the button. */
    private void stars(GuiGraphicsExtractor g, ScreenTheme theme, int mouseX, int mouseY) {
        MalikMenu.View view = menu.view();
        int x = contentX();
        int y = pageY();
        int w = contentW();
        ItemStack item = view.item();
        int itemH = 62;
        SkyScreens.card(g, theme, x, y, w, itemH, false, item.isEmpty());
        if (item.isEmpty()) {
            NpcCards.icon(g, new ItemStack(vulcan() ? net.minecraft.world.item.Items.NETHERITE_CHESTPLATE : net.minecraft.world.item.Items.IRON_SWORD),
                    x + 8, y + 11, 40, 2f);
            Draw.fit(g, font, Component.literal(vulcan() ? "Hold a piece of Kuudra armour," : "Hold a dungeon item,"), x + 56, y + 20, w - 64, 0.85f, 0.5f,
                    Draw.Align.LEFT, theme.text, false);
            Draw.fit(g, font, Component.literal("then talk to me again."), x + 56, y + 32, w - 64, 0.75f, 0.5f, Draw.Align.LEFT, theme.muted, false);
            return;
        }
        NpcCards.icon(g, item, x + 8, y + 11, 40, 2f);
        hoverItem = SkyScreens.inside(mouseX, mouseY, x + 8, y + 11, 40, 40);
        Draw.fit(g, font, item.getHoverName(), x + 56, y + 8, w - 64, 1f, 0.5f, Draw.Align.LEFT, theme.text, true);
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < view.max(); i++) {
            stars.append(i < view.stars() ? "✪" : "☆");
        }
        Draw.fit(g, font, Component.literal(stars.toString()), x + 56, y + 22, w - 64, 0.95f, 0.5f, Draw.Align.LEFT, GOLD, false);
        SkyScreens.bar(g, theme, x + 56, y + 36, w - 64, 6, view.max() == 0 ? 0 : (float) view.stars() / view.max(), view.stars() >= view.max() ? GOLD : theme.good);
        Draw.fit(g, font, Component.literal(view.stars() + "/" + view.max() + " stars"), x + 56, y + 46, w - 64, 0.7f, 0.45f, Draw.Align.LEFT, theme.muted,
                false);
        // The next star's cost.
        int cy = y + itemH + GAP;
        int ch = contentBottom() - 22 - cy;
        SkyScreens.card(g, theme, x, cy, w, ch, false, false);
        if (view.cost().isEmpty()) {
            Draw.fit(g, font, Component.literal("Every star is on it!"), x + w / 2, cy + ch / 2 - 4, w - 12, 0.95f, 0.5f, Draw.Align.CENTER, theme.good, true);
        } else {
            Draw.fit(g, font, Component.literal("Next star costs"), x + 8, cy + 5, w - 16, 0.75f, 0.5f, Draw.Align.LEFT, theme.accent, false);
            int cx = x + 8;
            int row = cy + 17;
            for (Chip chip : view.cost()) {
                int cw = SkyScreens.chipWidth(font, chip, 0.85f);
                if (cx + cw > x + w - 8) {
                    cx = x + 8;
                    row += 12;
                }
                SkyScreens.chip(g, font, theme, cx, row, chip, 0.85f);
                cx += cw + 10;
            }
        }
        boolean can = !view.cost().isEmpty() && view.cost().stream().allMatch(Chip::enough);
        int[] b = buttonBox();
        SkyScreens.button(g, font, theme, b[0], b[1], b[2], b[3], view.cost().isEmpty() ? "Maxed" : "Add a star", !view.cost().isEmpty(),
                can && SkyScreens.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]));
    }

    /** The essence shop: a perk card each, with its level bar, what it gives now and the next level's price. */
    private void shop(GuiGraphicsExtractor g, ScreenTheme theme, int mouseX, int mouseY) {
        List<MalikMenu.PerkRow> perks = menu.view().perks();
        for (int i = 0; i < perks.size(); i++) {
            int[] box = perkBox(i);
            if (box == null) {
                continue;
            }
            MalikMenu.PerkRow perk = perks.get(i);
            boolean over = SkyScreens.inside(mouseX, mouseY, box[0], box[1], box[2], box[3]);
            if (over) {
                hoveredPerk = i;
            }
            boolean maxed = perk.level() >= perk.max();
            boolean afford = perk.price().stream().allMatch(Chip::enough);
            SkyScreens.card(g, theme, box[0], box[1], box[2], box[3], over && !maxed, false);
            NpcCards.icon(g, perk.icon(), box[0] + 4, box[1] + 9, 20, 1.125f);
            int tx = box[0] + 28;
            int tw = box[2] - 32;
            Draw.fit(g, font, Component.literal(perk.name()), tx, box[1] + 4, tw, 0.75f, 0.45f, Draw.Align.LEFT, 0xFF000000 | perk.color(), true);
            int barW = Math.max(20, tw - 22);
            SkyScreens.bar(g, theme, tx, box[1] + 14, barW, 5, (float) perk.level() / Math.max(1, perk.max()), maxed ? GOLD : theme.good);
            Draw.fit(g, font, Component.literal(perk.level() + "/" + perk.max()), tx + barW + 3, box[1] + 13, 20, 0.6f, 0.4f, Draw.Align.LEFT,
                    maxed ? GOLD : theme.muted, false);
            Draw.fit(g, font, Component.literal(perk.effect()), tx, box[1] + 22, tw, 0.58f, 0.4f, Draw.Align.LEFT, theme.muted, false);
            if (maxed) {
                Draw.fit(g, font, Component.literal("Maxed"), tx, box[1] + 29, tw, 0.65f, 0.4f, Draw.Align.LEFT, GOLD, false);
            } else {
                SkyScreens.chips(g, font, theme, tx, box[1] + 28, perk.price(), 0.7f, box[0] + box[2] - 3);
            }
            if (!maxed && !afford) {
                g.nextStratum();
                g.fill(box[0] + 5, box[1] + 10, box[0] + 23, box[1] + 28, 0x66140A0A);
            }
        }
        if (maxScroll() > 0) {
            int h = visibleRows() * (PERK_H + GAP) - GAP;
            Draw.scrollbar(g, contentX() + contentW() - 4, pageY(), h, totalRows() * (PERK_H + GAP), scroll * (PERK_H + GAP), theme.accent);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        MalikMenu.View view = menu.view();
        ScreenTheme theme = theme();
        if (hoverItem && !view.item().isEmpty()) {
            Draw.queueTooltip(NpcCards.itemTip(minecraft, view.item()), GOLD);
        } else if (hoveredPerk >= 0 && hoveredPerk < view.perks().size()) {
            MalikMenu.PerkRow perk = view.perks().get(hoveredPerk);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(perk.name()).withColor(0xFF000000 | perk.color()));
            lines.add(Component.literal("Level " + perk.level() + "/" + perk.max()).withColor(theme.muted));
            lines.add(Component.literal("Now: " + perk.effect()).withColor(theme.text));
            if (perk.level() < perk.max()) {
                NpcCards.chipLines(lines, theme, "Next level costs", perk.price());
                lines.add(Component.empty());
                boolean can = perk.price().stream().allMatch(Chip::enough);
                lines.add(Component.literal(can ? "Click to buy" : "You can't afford this").withColor(can ? theme.good : 0xFFFF6464));
            } else {
                lines.add(Component.literal("Maxed out!").withColor(GOLD));
            }
            Draw.queueTooltip(lines, theme.accent);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(vertical)));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            MalikMenu.View view = menu.view();
            if (!vulcan()) {
                for (int i = 0; i < 2; i++) {
                    int[] t = tabBox(i);
                    if (SkyScreens.inside(event.x(), event.y(), t[0], t[1], t[2], t[3]) && view.shop() != (i == 1)) {
                        scroll = 0;
                        return press(MalikMenu.TAB);
                    }
                }
            }
            if (view.shop()) {
                for (int i = 0; i < view.perks().size(); i++) {
                    int[] box = perkBox(i);
                    if (box != null && SkyScreens.inside(event.x(), event.y(), box[0], box[1], box[2], box[3])) {
                        return press(MalikMenu.PERK + i);
                    }
                }
            } else {
                int[] b = buttonBox();
                if (SkyScreens.inside(event.x(), event.y(), b[0], b[1], b[2], b[3])) {
                    return press(MalikMenu.UPGRADE);
                }
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
