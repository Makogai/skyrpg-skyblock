package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

/**
 * Any {@link CardMenu}: a themed window with the NPC (its live model) and your balances on the left and the cards on the
 * right, in the view's grid. A card shows an item icon or currency sprite, its title (an item's own name and rarity
 * colour), a note, a progress bar, a detail line, chips and a button, or is itself the button. Item cards show the real
 * item's tooltip with the price; what you can't afford has its icon dimmed and red chips. Long lists scroll.
 */
public final class CardScreen extends AbstractContainerScreen<CardMenu> {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 226;
    private static final int PANEL = 96;
    private static final int GAP = 4;
    /**
     * The server reopens a card menu after every click (and when it changes, as a stray rabbit turns up): a menu reopened
     * right after the same one closed keeps where you'd scrolled to.
     */
    private static String lastTitle = "";
    private static int lastScroll;
    private static long lastClosed;
    private final ScreenTheme theme;
    private int scroll;
    private int hovered = -1;

    public CardScreen(CardMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        ScreenTheme chosen = ScreenTheme.SKYBLOCK;
        try {
            chosen = ScreenTheme.valueOf(menu.view().theme());
        } catch (IllegalArgumentException ignored) {
            // An unknown theme falls back to SkyRPG's own.
        }
        this.theme = chosen;
        if (key().equals(lastTitle) && System.currentTimeMillis() - lastClosed < 1500) {
            scroll = lastScroll;
        }
    }

    private String key() {
        return menu.view().title() + "|" + menu.view().subtitle();
    }

    @Override
    public void removed() {
        super.removed();
        lastTitle = key();
        lastScroll = scroll;
        lastClosed = System.currentTimeMillis();
    }

    private CardView view() {
        return menu.view();
    }

    private boolean panel() {
        return view().npc() >= 0 || !view().wallet().isEmpty();
    }

    private int contentX() {
        return leftPos + theme.inset + (panel() ? PANEL + 6 : 0);
    }

    private int contentY() {
        return topPos + SkyScreens.contentTop(theme) + 2;
    }

    private int contentW() {
        return leftPos + imageWidth - theme.inset - contentX();
    }

    private int contentBottom() {
        return topPos + imageHeight - 24;
    }

    private int columns() {
        return Math.max(1, view().columns());
    }

    private int cardHeight() {
        return Math.max(20, view().cardHeight());
    }

    private int visibleRows() {
        return Math.max(1, (contentBottom() - contentY() + GAP) / (cardHeight() + GAP));
    }

    private int totalRows() {
        return (view().cards().size() + columns() - 1) / columns();
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - visibleRows());
    }

    private int[] cardBox(int index) {
        int row = index / columns() - scroll;
        if (row < 0 || row >= visibleRows()) {
            return null;
        }
        int scrollbar = maxScroll() > 0 ? 6 : 0;
        int w = (contentW() - scrollbar - GAP * (columns() - 1)) / columns();
        return new int[] {contentX() + index % columns() * (w + GAP), contentY() + row * (cardHeight() + GAP), w, cardHeight()};
    }

    private int[] buttonBox(int[] box) {
        return new int[] {box[0] + box[2] - 48, box[1] + (box[3] - 13) / 2, 44, 13};
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        CardView view = view();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        SkyScreens.window(g, font, theme, leftPos, topPos, imageWidth, imageHeight, Component.literal(view.title()), Component.literal(view.subtitle()));
        if (panel()) {
            npcPanel(g, view, mouseX, mouseY);
        }
        hovered = -1;
        for (int i = 0; i < view.cards().size(); i++) {
            int[] box = cardBox(i);
            if (box == null) {
                continue;
            }
            boolean over = SkyScreens.inside(mouseX, mouseY, box[0], box[1], box[2], box[3]);
            if (over) {
                hovered = i;
            }
            card(g, view.cards().get(i), box, over, mouseX, mouseY);
        }
        if (maxScroll() > 0) {
            int h = visibleRows() * (cardHeight() + GAP) - GAP;
            Draw.scrollbar(g, contentX() + contentW() - 4, contentY(), h, totalRows() * (cardHeight() + GAP), scroll * (cardHeight() + GAP), theme.accent);
        }
        int left = panel() ? contentX() : leftPos + theme.inset;
        Draw.fit(g, font, Component.literal(view.footer()), (left + leftPos + imageWidth - theme.inset) / 2, topPos + imageHeight - 18,
                leftPos + imageWidth - theme.inset - left - 4, 0.62f, 0.45f, Draw.Align.CENTER, theme.dim, false);
    }

    /** The NPC's live model (when there is one) and your balances. */
    private void npcPanel(GuiGraphicsExtractor g, CardView view, int mouseX, int mouseY) {
        int x = leftPos + theme.inset;
        int y = topPos + SkyScreens.contentTop(theme) + 2;
        int h = contentBottom() - y;
        SkyScreens.card(g, theme, x, y, PANEL, h, false, false);
        int wy = y + 6;
        Entity entity = view.npc() >= 0 && minecraft.level != null ? minecraft.level.getEntity(view.npc()) : null;
        if (entity instanceof LivingEntity living) {
            int portrait = 92;
            g.fill(x + 4, y + 4, x + PANEL - 4, y + portrait, 0x40000000);
            SkyScreens.npcPortrait(g, living, x + 4, y + 4, x + PANEL - 4, y + portrait, 36, mouseX, mouseY);
            wy = y + portrait + 6;
        }
        if (view.wallet().isEmpty()) {
            return;
        }
        Draw.fit(g, font, Component.literal("Your wallet"), x + PANEL / 2, wy, PANEL - 8, 0.7f, 0.5f, Draw.Align.CENTER, theme.accent, false);
        wy += 11;
        for (Chip chip : view.wallet()) {
            if (wy + 10 > y + h - 2) {
                break;
            }
            int cw = SkyScreens.chip(g, font, theme, x + 8, wy, chip, 0.8f);
            // a chip with neither currency nor item, or with words of its own ("Hunting 5", not "12.3k"), says what it is
            boolean words = chip.text().chars().filter(Character::isLetter).count() >= 3;
            String name = chip.currency().isEmpty() ? chip.item().isEmpty() || words ? "" : chip.item().getHoverName().getString()
                    : SkyScreens.currencyName(chip.currency());
            if (!name.isEmpty()) {
                Draw.fit(g, font, Component.literal(name), x + 12 + cw, wy + 2, PANEL - 18 - cw, 0.62f, 0.45f, Draw.Align.LEFT, theme.muted, false);
            }
            wy += 12;
        }
    }

    private void card(GuiGraphicsExtractor g, CardView.Card card, int[] box, boolean over, int mouseX, int mouseY) {
        int x = box[0];
        int y = box[1];
        int w = box[2];
        int h = box[3];
        boolean wholeCard = card.button().isEmpty() && card.enabled();
        boolean affordable = card.chips().stream().allMatch(Chip::enough);
        SkyScreens.card(g, theme, x, y, w, h, over && card.enabled(), false);
        int textX = x + 6;
        float scale = h >= 30 ? 1.25f : 1f;
        int size = Math.round(16 * scale);
        int iy = y + (h - size) / 2;
        if (!card.icon().isEmpty() || !card.sprite().isEmpty()) {
            g.fill(x + 4, iy - 1, x + 6 + size, iy + size + 1, 0x55000000);
            if (!card.icon().isEmpty()) {
                Draw.item(g, card.icon().copyWithCount(1), x + 5, iy, scale);
            } else {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SkyRpgSkyblock.id("currency/" + card.sprite()), x + 5 + (size - 18) / 2, iy + (size - 18) / 2,
                        18, 18);
            }
            textX = x + 10 + size;
        }
        int right = card.button().isEmpty() ? x + w - 5 : buttonBox(box)[0] - 4;
        Component title = !card.icon().isEmpty() && card.color() == 0xFFFFFF ? card.icon().getHoverName() : Component.literal(card.title());
        if (card.icon().getCount() > 1) {
            title = title.copy().append(Component.literal(" x" + card.icon().getCount()).withColor(theme.muted));
        }
        int noteW = card.note().isEmpty() ? 0 : Math.round(font.width(card.note()) * 0.6f) + 4;
        Draw.fit(g, font, title, textX, y + 5, Math.max(10, right - textX - noteW), 0.8f, 0.5f, Draw.Align.LEFT, 0xFF000000 | card.color(), true);
        if (noteW > 0) {
            Draw.fit(g, font, Component.literal(card.note()), right, y + 6, noteW, 0.6f, 0.45f, Draw.Align.RIGHT, theme.muted, false);
        }
        int line = y + 15;
        if (card.progress() >= 0) {
            SkyScreens.bar(g, theme, textX, line, right - textX, 5, card.progress(), card.progress() >= 1 ? theme.accent : theme.good);
            line += 8;
        }
        boolean chipsBeside = !card.chips().isEmpty() && !card.detail().isEmpty() && line + 8 + 10 > y + h;
        int chipsW = 0;
        if (chipsBeside) {
            for (Chip chip : card.chips()) {
                chipsW += SkyScreens.chipWidth(font, chip, 0.7f) + 6;
            }
            SkyScreens.chips(g, font, theme, right - chipsW + 6, line - 1, card.chips(), 0.7f, right + 1);
        }
        if (!card.detail().isEmpty()) {
            Draw.fit(g, font, Component.literal(card.detail()), textX, line, right - textX - chipsW, 0.62f, 0.45f, Draw.Align.LEFT, theme.muted, false);
            line += 8;
        }
        if (!card.chips().isEmpty() && !chipsBeside) {
            SkyScreens.chips(g, font, theme, textX, Math.max(line, y + h - 13), card.chips(), 0.7f, right);
        }
        if (!card.button().isEmpty()) {
            int[] b = buttonBox(box);
            SkyScreens.button(g, font, theme, b[0], b[1], b[2], b[3], card.button(), card.enabled(), SkyScreens.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]));
        }
        if (wholeCard && !affordable && !card.icon().isEmpty()) {
            g.nextStratum();
            g.fill(x + 5, iy, x + 5 + size, iy + size, 0x99100A14);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        CardView.Card card = hovered >= 0 && hovered < view().cards().size() ? view().cards().get(hovered) : null;
        boolean item = card != null && !card.icon().isEmpty() && card.color() == 0xFFFFFF;
        if (card != null && (item || !card.chips().isEmpty() || card.detail().length() > 40)) {
            List<Component> lines = new ArrayList<>();
            if (item) {
                lines.addAll(card.icon().getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
            } else {
                lines.add(Component.literal(card.title()).withColor(0xFF000000 | card.color()));
                if (!card.detail().isEmpty()) {
                    lines.add(Component.literal(card.detail()).withColor(theme.muted));
                }
            }
            if (!card.chips().isEmpty()) {
                lines.add(Component.empty());
                if (card.enabled() && card.button().isEmpty()) {
                    lines.add(Component.literal("Cost").withColor(theme.muted));
                }
                for (Chip chip : card.chips()) {
                    String what = chip.currency().isEmpty() ? chip.item().isEmpty() ? "" : chip.item().getHoverName().getString()
                            : SkyScreens.currencyName(chip.currency());
                    lines.add(Component.literal(what.isEmpty() ? chip.text() : chip.text() + " " + what).withColor(chip.enough() ? theme.text : 0xFFFF6464));
                }
            }
            if (card.enabled() && card.button().isEmpty()) {
                boolean can = card.chips().stream().allMatch(Chip::enough);
                lines.add(Component.empty());
                lines.add(Component.literal(can ? "Click to " + (card.note().isEmpty() ? "buy" : card.note().toLowerCase(java.util.Locale.ROOT))
                        : "You can't afford this").withColor(can ? theme.good : 0xFFFF6464));
            }
            Draw.queueTooltip(lines, theme.accent);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(vertical)));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int i = 0; i < view().cards().size(); i++) {
                int[] box = cardBox(i);
                CardView.Card card = view().cards().get(i);
                if (box == null || !card.enabled()) {
                    continue;
                }
                int[] hit = card.button().isEmpty() ? box : buttonBox(box);
                if (SkyScreens.inside(event.x(), event.y(), hit[0], hit[1], hit[2], hit[3])) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                    SkyScreens.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
