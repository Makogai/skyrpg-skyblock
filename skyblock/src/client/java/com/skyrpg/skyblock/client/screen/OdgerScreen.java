package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.skyblock.fishing.OdgerMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Odger, in the fishing theme: his portrait, your Trophy Fisher rank with its progress bar, the next rank's reward icons and the
 * claim and fillet buttons on the left; on the right a card for every trophy fish with its four tiers (the tier's own item and
 * your count), scrolling with the mouse wheel. Hover a fish for how it's caught, a tier for the item's own tooltip.
 */
public final class OdgerScreen extends AbstractContainerScreen<OdgerMenu> {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 226;
    private static final ScreenTheme THEME = ScreenTheme.FISHING;
    private static final int[] TIER_COLORS = {0xFFB07A4A, 0xFFC8C8C8, 0xFFFFAA00, 0xFF55FFFF};
    private static final String[] TIER_NAMES = {"Bronze", "Silver", "Gold", "Diamond"};
    private static final int PANEL = 100;
    private static final int CARD_H = 38;
    private static final int GAP = 3;
    private int scroll;
    private int hoveredFish = -1;
    private int hoveredTier = -1;
    private ItemStack hoveredReward = ItemStack.EMPTY;

    public OdgerScreen(OdgerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    // ---------------------------------------------------------------- layout

    private int panelX() {
        return leftPos + THEME.inset;
    }

    private int top() {
        return topPos + SkyScreens.contentTop(THEME);
    }

    private int bottom() {
        return topPos + imageHeight - 20;
    }

    private int gridX() {
        return panelX() + PANEL + 6;
    }

    private int gridW() {
        return leftPos + imageWidth - THEME.inset - gridX();
    }

    private int visibleRows() {
        return Math.max(1, (bottom() - top() + GAP) / (CARD_H + GAP));
    }

    private int totalRows() {
        return (menu.view().rows().size() + 1) / 2;
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - visibleRows());
    }

    private int[] cardBox(int index) {
        int row = index / 2 - scroll;
        if (row < 0 || row >= visibleRows()) {
            return null;
        }
        int scrollbar = maxScroll() > 0 ? 6 : 0;
        int w = (gridW() - scrollbar - GAP) / 2;
        return new int[] {gridX() + index % 2 * (w + GAP), top() + row * (CARD_H + GAP), w, CARD_H};
    }

    private int[] claimBox() {
        return new int[] {panelX() + 4, top() + 121, PANEL - 8, 14};
    }

    private int[] filletBox() {
        return new int[] {panelX() + 4, top() + 138, PANEL - 8, 14};
    }

    /** The tier a fish is best caught in (-1: not caught). */
    private static int best(OdgerMenu.Row row) {
        for (int t = 3; t >= 0; t--) {
            if (row.counts().get(t) > 0) {
                return t;
            }
        }
        return -1;
    }

    private static String requirement(int rank) {
        return switch (rank) {
            case 1 -> "Bronze of 15 fish";
            case 2 -> "Silver of all 18";
            case 3 -> "Gold of all 18";
            default -> "Diamond of all 18";
        };
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        OdgerMenu.View view = menu.view();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Odger"),
                Component.literal("Trophy Fisher: " + OdgerMenu.RANKS.get(view.claimed())));
        panel(g, view, mouseX, mouseY);
        hoveredFish = -1;
        hoveredTier = -1;
        for (int i = 0; i < view.rows().size(); i++) {
            int[] box = cardBox(i);
            if (box != null) {
                fish(g, view.rows().get(i), i, box, mouseX, mouseY);
            }
        }
        if (maxScroll() > 0) {
            int h = visibleRows() * (CARD_H + GAP) - GAP;
            Draw.scrollbar(g, gridX() + gridW() - 4, top(), h, totalRows() * (CARD_H + GAP), scroll * (CARD_H + GAP), THEME.accent);
        }
        int caught = (int) view.rows().stream().filter(r -> best(r) >= 0).count();
        Draw.fit(g, font, Component.literal(caught + "/" + view.rows().size() + " trophy fish caught   ·   hover a fish to see how to catch it"),
                (gridX() + leftPos + imageWidth - THEME.inset) / 2, topPos + imageHeight - 18, gridW() - 4, 0.62f, 0.4f, Draw.Align.CENTER, THEME.dim, false);
    }

    /** Odger's portrait, your rank and its progress, the next rewards, the claim and fillet buttons and your balances. */
    private void panel(GuiGraphicsExtractor g, OdgerMenu.View view, int mouseX, int mouseY) {
        int x = panelX();
        int y = top();
        SkyScreens.card(g, THEME, x, y, PANEL, bottom() - y, false, false);
        Entity entity = view.npc() >= 0 && minecraft.level != null ? minecraft.level.getEntity(view.npc()) : null;
        g.fill(x + 4, y + 4, x + PANEL - 4, y + 52, 0x40000000);
        if (entity instanceof LivingEntity living) {
            SkyScreens.npcPortrait(g, living, x + 4, y + 4, x + PANEL - 4, y + 52, 24, mouseX, mouseY);
        }
        int cx = x + PANEL / 2;
        Draw.fit(g, font, Component.literal(OdgerMenu.RANKS.get(view.claimed()) + " Trophy Fisher"), cx, y + 56, PANEL - 8, 0.8f, 0.5f,
                Draw.Align.CENTER, THEME.accent, true);
        // Progress to the next rank: fish at the next rank's tier or better.
        int next = view.claimed() + 1;
        if (next <= 4) {
            int tier = next - 1;
            int need = next == 1 ? 15 : view.rows().size();
            int have = 0;
            for (OdgerMenu.Row row : view.rows()) {
                have += best(row) >= tier ? 1 : 0;
            }
            have = Math.min(have, need);
            SkyScreens.bar(g, THEME, x + 6, y + 68, PANEL - 12, 6, (float) have / need, TIER_COLORS[tier]);
            Draw.fit(g, font, Component.literal(OdgerMenu.RANKS.get(next) + ": " + requirement(next)), cx, y + 77, PANEL - 8, 0.58f, 0.4f,
                    Draw.Align.CENTER, THEME.muted, false);
            Draw.fit(g, font, Component.literal("Rewards for " + OdgerMenu.RANKS.get(next)), cx, y + 90, PANEL - 8, 0.62f, 0.4f, Draw.Align.CENTER,
                    THEME.accent, false);
        } else {
            SkyScreens.bar(g, THEME, x + 6, y + 68, PANEL - 12, 6, 1f, TIER_COLORS[3]);
            Draw.fit(g, font, Component.literal("Every rank claimed!"), cx, y + 77, PANEL - 8, 0.62f, 0.4f, Draw.Align.CENTER, THEME.good, false);
        }
        hoveredReward = ItemStack.EMPTY;
        int rx = x + 6;
        for (ItemStack stack : view.rewards()) {
            if (rx + 14 > x + PANEL - 4) {
                break;
            }
            NpcCards.icon(g, stack, rx, y + 101, 14, 0.75f);
            if (SkyScreens.inside(mouseX, mouseY, rx, y + 101, 14, 14)) {
                hoveredReward = stack;
            }
            rx += 13;
        }
        boolean canClaim = view.earned() > view.claimed();
        int[] c = claimBox();
        SkyScreens.button(g, font, THEME, c[0], c[1], c[2], c[3], canClaim ? "Claim " + OdgerMenu.RANKS.get(view.earned()) : view.claimed() >= 4 ? "Master!" : "Claim rewards",
                canClaim, canClaim && SkyScreens.inside(mouseX, mouseY, c[0], c[1], c[2], c[3]));
        boolean canFillet = view.fillet() > 0;
        int[] f = filletBox();
        SkyScreens.button(g, font, THEME, f[0], f[1], f[2], f[3], canFillet ? "Fillet: " + String.format("%,d", view.fillet()) : "Nothing to fillet",
                canFillet, canFillet && SkyScreens.inside(mouseX, mouseY, f[0], f[1], f[2], f[3]));
        SkyScreens.chips(g, font, THEME, x + 6, y + 157, view.wallet(), 0.7f, x + PANEL - 4);
    }

    /** A fish's card: its best tier's item (dimmed until caught), its name, the four tiers with your counts and a tier strip. */
    private void fish(GuiGraphicsExtractor g, OdgerMenu.Row row, int index, int[] box, int mouseX, int mouseY) {
        int x = box[0];
        int y = box[1];
        int w = box[2];
        int best = best(row);
        boolean over = SkyScreens.inside(mouseX, mouseY, x, y, w, CARD_H);
        if (over) {
            hoveredFish = index;
        }
        SkyScreens.card(g, THEME, x, y, w, CARD_H, over, best < 0);
        NpcCards.icon(g, row.icons().get(Math.max(0, best)), x + 4, y + 4, 20, 1.125f);
        Draw.fit(g, font, Component.literal(row.name()), x + 28, y + 6, w - 32, 0.8f, 0.5f, Draw.Align.LEFT,
                best >= 0 ? 0xFF000000 | row.color() : THEME.dim, true);
        Draw.fit(g, font, Component.literal(best >= 0 ? TIER_NAMES[best] + " best" : "Not caught yet"), x + 28, y + 16, w - 32, 0.6f, 0.4f,
                Draw.Align.LEFT, best >= 0 ? TIER_COLORS[best] : THEME.dim, false);
        int cell = (w - 8) / 4;
        for (int t = 0; t < 4; t++) {
            int cx = x + 4 + t * cell;
            int count = row.counts().get(t);
            boolean overCell = SkyScreens.inside(mouseX, mouseY, cx, y + 26, cell, 10);
            if (overCell) {
                hoveredTier = t;
            }
            Draw.item(g, row.icons().get(t), cx, y + 25, 0.6f);
            Draw.fit(g, font, Component.literal(count > 999 ? "999+" : String.valueOf(count)), cx + 11, y + 28, cell - 11, 0.6f, 0.4f, Draw.Align.LEFT,
                    count > 0 ? TIER_COLORS[t] : 0xFF505050, false);
            if (count <= 0) {
                g.nextStratum();
                g.fill(cx, y + 25, cx + 10, y + 35, 0x88101820);
            }
        }
        for (int t = 0; t < 4; t++) {
            int sx = x + 4 + t * cell;
            g.fill(sx, y + CARD_H - 4, sx + cell - 2, y + CARD_H - 2, row.counts().get(t) > 0 ? TIER_COLORS[t] : THEME.track);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        OdgerMenu.View view = menu.view();
        if (!hoveredReward.isEmpty()) {
            Draw.queueTooltip(NpcCards.itemTip(minecraft, hoveredReward), THEME.accent);
        } else if (hoveredFish >= 0 && hoveredFish < view.rows().size()) {
            OdgerMenu.Row row = view.rows().get(hoveredFish);
            if (hoveredTier >= 0 && !row.icons().get(hoveredTier).isEmpty()) {
                List<Component> lines = NpcCards.itemTip(minecraft, row.icons().get(hoveredTier));
                lines.add(Component.empty());
                lines.add(Component.literal("You've caught " + row.counts().get(hoveredTier)).withColor(TIER_COLORS[hoveredTier]));
                Draw.queueTooltip(lines, THEME.accent);
            } else {
                List<Component> lines = new ArrayList<>();
                lines.add(Component.literal(row.name()).withColor(0xFF000000 | row.color()));
                lines.add(Component.literal(row.how()).withColor(THEME.muted));
                lines.add(Component.empty());
                for (int t = 0; t < 4; t++) {
                    lines.add(Component.literal(TIER_NAMES[t] + ": " + row.counts().get(t)).withColor(row.counts().get(t) > 0 ? TIER_COLORS[t] : THEME.dim));
                }
                Draw.queueTooltip(lines, THEME.accent);
            }
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
            int[] f = filletBox();
            if (SkyScreens.inside(event.x(), event.y(), f[0], f[1], f[2], f[3]) && menu.view().fillet() > 0) {
                return press(OdgerMenu.FILLET);
            }
            int[] c = claimBox();
            if (SkyScreens.inside(event.x(), event.y(), c[0], c[1], c[2], c[3]) && menu.view().earned() > menu.view().claimed()) {
                return press(OdgerMenu.CLAIM);
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
