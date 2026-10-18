package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.skyblock.crimson.KuudraMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Kuudra's screens, in the Crimson theme: the tiers at the Gatekeeper (key cards), the keys at an Emissary (price chips), Master
 * Tao's Dojo tests (score bars) and the chests after a win (reward icons). The NPC's portrait and your balances sit on the left
 * when there is one; long lists scroll with the mouse wheel.
 */
public final class KuudraScreen extends AbstractContainerScreen<KuudraMenu> {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 226;
    private static final ScreenTheme THEME = ScreenTheme.CRIMSON;
    private static final int GAP = 3;
    private int scroll;
    private int hovered = -1;
    private ItemStack hoveredItem = ItemStack.EMPTY;

    public KuudraScreen(KuudraMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    // ---------------------------------------------------------------- layout

    private boolean npcView() {
        return menu.view().npc() >= 0;
    }

    private int contentX() {
        return leftPos + THEME.inset + (npcView() ? NpcCards.PANEL + 6 : 0);
    }

    private int contentY() {
        return topPos + SkyScreens.contentTop(THEME);
    }

    private int contentW() {
        return leftPos + imageWidth - THEME.inset - contentX();
    }

    private int contentBottom() {
        return topPos + imageHeight - 20;
    }

    private int cardHeight() {
        return switch (menu.view().mode()) {
            case TIERS -> 28;
            case KEYS -> 30;
            case DOJO -> 22;
            case REWARDS -> 64;
        };
    }

    private int gap() {
        return menu.view().mode() == KuudraMenu.Mode.DOJO ? 2 : GAP;
    }

    private int visibleRows() {
        return Math.max(1, (contentBottom() - contentY() + gap()) / (cardHeight() + gap()));
    }

    private int maxScroll() {
        return Math.max(0, menu.view().rows().size() - visibleRows());
    }

    /** A card's box {x, y, w, h}, or null when it's scrolled out of view. */
    private int[] cardBox(int index) {
        int row = index - scroll;
        if (row < 0 || row >= visibleRows()) {
            return null;
        }
        int scrollbar = maxScroll() > 0 ? 6 : 0;
        return new int[] {contentX(), contentY() + row * (cardHeight() + gap()), contentW() - scrollbar, cardHeight()};
    }

    private int[] buttonBox(int[] box) {
        int bw = 44;
        int bh = 13;
        return new int[] {box[0] + box[2] - bw - 4, box[1] + (box[3] - bh) / 2, bw, bh};
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        KuudraMenu.View view = menu.view();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal(view.title()), Component.literal(view.subtitle()));
        if (npcView()) {
            NpcCards.npcPanel(g, font, THEME, minecraft, leftPos + THEME.inset, contentY(), contentBottom() - contentY(), view.npc(), "Your wallet",
                    view.wallet(), mouseX, mouseY);
        }
        hovered = -1;
        hoveredItem = ItemStack.EMPTY;
        for (int i = 0; i < view.rows().size(); i++) {
            int[] box = cardBox(i);
            if (box == null) {
                continue;
            }
            boolean over = SkyScreens.inside(mouseX, mouseY, box[0], box[1], box[2], box[3]);
            if (over) {
                hovered = i;
            }
            card(g, view.rows().get(i), box, over, mouseX, mouseY);
        }
        if (maxScroll() > 0) {
            int h = visibleRows() * (cardHeight() + gap()) - gap();
            Draw.scrollbar(g, contentX() + contentW() - 4, contentY(), h, view.rows().size() * (cardHeight() + gap()), scroll * (cardHeight() + gap()),
                    THEME.accent);
        }
        Draw.fit(g, font, Component.literal(view.footer()), (contentX() + leftPos + imageWidth - THEME.inset) / 2, topPos + imageHeight - 18,
                leftPos + imageWidth - THEME.inset - contentX() - 4, 0.62f, 0.4f, Draw.Align.CENTER, THEME.dim, false);
    }

    /** One card: icon, title, note, progress bar, detail, chips, reward icons and its button. */
    private void card(GuiGraphicsExtractor g, KuudraMenu.Row row, int[] box, boolean over, int mouseX, int mouseY) {
        int x = box[0];
        int y = box[1];
        int w = box[2];
        int h = box[3];
        boolean big = h >= 50;
        boolean open = row.button().equals("Open") || row.button().equals("Opened");
        SkyScreens.card(g, THEME, x, y, w, h, over && row.enabled(), !row.enabled() && !open);
        int color = row.enabled() || open ? 0xFF000000 | row.color() : THEME.muted;
        int iconBox = big ? 36 : h >= 28 ? 22 : 18;
        float scale = big ? 2f : h >= 28 ? 1.25f : 1f;
        NpcCards.icon(g, row.icon(), x + 4, y + (h - iconBox) / 2, iconBox, scale);
        int textX = x + 8 + iconBox;
        int right = buttonBox(box)[0] - 4;
        Draw.fit(g, font, Component.literal(row.title()), textX, y + (big ? 6 : 4), Math.max(10, right - textX), big ? 1f : 0.85f, 0.5f,
                Draw.Align.LEFT, color, true);
        int line = y + (big ? 18 : h >= 28 ? 14 : 13);
        if (!row.note().isEmpty()) {
            Draw.fit(g, font, Component.literal(row.note()), right, y + (big ? 7 : 5), Math.max(10, (right - textX) / 2), 0.6f, 0.45f,
                    Draw.Align.RIGHT, row.enabled() ? THEME.muted : THEME.dim, false);
        }
        int detailX = textX;
        if (row.progress() >= 0) {
            int barW = Math.max(30, (right - textX) * 11 / 20);
            SkyScreens.bar(g, THEME, textX, line + 1, barW, 5, row.progress(), row.progress() >= 1 ? THEME.accent : THEME.good);
            detailX = textX + barW + 4;
        }
        int chipsY = y + (big ? 29 : h - 12);
        if (!row.detail().isEmpty()) {
            int detailW = Math.min(Math.max(10, right - detailX), Math.round(font.width(row.detail()) * 0.62f) + 2);
            Draw.fit(g, font, Component.literal(row.detail()), detailX, line, Math.max(10, right - detailX), 0.62f, 0.45f, Draw.Align.LEFT,
                    row.enabled() ? THEME.muted : THEME.dim, false);
            // No room for a chip row under the detail: the chips follow it on the same line.
            if (!big && chipsY < line + 8) {
                chipsY = line - 1;
                textX = detailX + detailW + 6;
            }
        }
        if (!row.chips().isEmpty()) {
            SkyScreens.chips(g, font, THEME, textX, chipsY, row.chips(), 0.7f, right);
        }
        if (!row.items().isEmpty()) {
            int ix = textX;
            int itemY = y + 43;
            for (ItemStack stack : row.items()) {
                if (ix + 18 > right) {
                    break;
                }
                NpcCards.icon(g, stack, ix, itemY, 18, 1f);
                if (SkyScreens.inside(mouseX, mouseY, ix, itemY, 18, 18)) {
                    hoveredItem = stack;
                }
                NpcCards.count(g, font, stack, ix + 1, itemY + 1, 16, 0xFFFFFFFF);
                ix += 20;
            }
        }
        int[] b = buttonBox(box);
        SkyScreens.button(g, font, THEME, b[0], b[1], b[2], b[3], row.button(), row.enabled(),
                row.enabled() && SkyScreens.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    /** The hovered thing's tooltip: a reward item's own tooltip, or the card's item tooltip with its price and requirement. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (!hoveredItem.isEmpty()) {
            Draw.queueTooltip(NpcCards.itemTip(minecraft, hoveredItem), THEME.accent);
        } else if (hovered >= 0 && hovered < menu.view().rows().size()) {
            KuudraMenu.Row row = menu.view().rows().get(hovered);
            List<Component> lines = new ArrayList<>();
            if (NpcCards.skyItem(row.icon())) {
                lines.addAll(NpcCards.itemTip(minecraft, row.icon()));
            } else {
                lines.add(Component.literal(row.title()).withColor(0xFF000000 | row.color()));
                if (!row.detail().isEmpty()) {
                    lines.add(Component.literal(row.detail()).withColor(THEME.muted));
                }
            }
            String header = menu.view().mode() == KuudraMenu.Mode.KEYS ? "Cost" : menu.view().mode() == KuudraMenu.Mode.REWARDS ? "Inside" : "";
            NpcCards.chipLines(lines, THEME, header, row.chips());
            Draw.queueTooltip(lines, THEME.accent);
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
            List<KuudraMenu.Row> rows = menu.view().rows();
            for (int i = 0; i < rows.size(); i++) {
                int[] box = cardBox(i);
                if (box == null || !rows.get(i).enabled()) {
                    continue;
                }
                int[] b = buttonBox(box);
                if (SkyScreens.inside(event.x(), event.y(), b[0], b[1], b[2], b[3])) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, KuudraMenu.ROW + i);
                    SkyScreens.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
