package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.garden.GardenMenu;
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
 * The Garden's screens, in the farming theme. The Desk: tabs over the plot map (grass plots, the Barn, the next plot),
 * the crop cards (milestone bars, upgrades), the Composter's gauges and upgrades, and SkyMart's shelves. The NPCs'
 * views (SkyMart, Phillip, Jacob, Anita, a visitor) put the NPC and your wallet on the left and their cards on the right.
 * Every item card shows the real item's tooltip with its price; long lists scroll with the mouse wheel.
 */
public final class GardenScreen extends AbstractContainerScreen<GardenMenu> {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 226;
    private static final ScreenTheme THEME = ScreenTheme.FARMING;
    private static final String[] TABS = {"Plots", "Crops", "Composter", "SkyMart"};
    private static final int PANEL = 96;
    private static final int GAP = 4;
    private int scroll;
    private int hovered = -1;

    public GardenScreen(GardenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    // ---------------------------------------------------------------- layout

    private GardenMenu.View view() {
        return menu.view();
    }

    private int contentX() {
        return leftPos + THEME.inset + (view().npcView() ? PANEL + 6 : 0);
    }

    private int contentY() {
        return topPos + SkyScreens.contentTop(THEME) + (view().npcView() ? 2 : 20);
    }

    private int contentW() {
        return leftPos + imageWidth - THEME.inset - contentX();
    }

    private int contentBottom() {
        return topPos + imageHeight - 24;
    }

    /** Columns, card height, and the rows laid out as cards (the plot map and a visitor's buttons are drawn apart). */
    private int columns() {
        return switch (view().tab()) {
            case PLOTS, JACOB, VISITOR -> 1;
            case SKYMART -> view().npcView() ? 2 : 3;
            default -> 2;
        };
    }

    private int cardHeight() {
        return switch (view().tab()) {
            case PLOTS -> 44;
            case MILESTONES -> 27;
            case COMPOSTER -> 34;
            case SKYMART, PHILLIP, ANITA -> 28;
            case JACOB -> 40;
            case VISITOR -> 30;
        };
    }

    /** The rows drawn as cards: a visitor's Accept and Decline become the buttons at the bottom. */
    private int cardCount() {
        int n = view().rows().size();
        return view().tab() == GardenMenu.Tab.VISITOR ? Math.max(0, n - 2) : n;
    }

    private int cardsX() {
        return view().tab() == GardenMenu.Tab.PLOTS ? contentX() + 146 : contentX();
    }

    private int cardsW() {
        return view().tab() == GardenMenu.Tab.PLOTS ? contentW() - 146 : contentW();
    }

    private int visibleRows() {
        int bottom = contentBottom() - (view().tab() == GardenMenu.Tab.VISITOR ? 22 : 0);
        return Math.max(1, (bottom - contentY() + GAP) / (cardHeight() + GAP));
    }

    private int totalRows() {
        return (cardCount() + columns() - 1) / columns();
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - visibleRows());
    }

    /** A card's box {x, y, w, h}, or null when it's scrolled out of view. */
    private int[] cardBox(int index) {
        int cols = columns();
        int row = index / cols - scroll;
        if (row < 0 || row >= visibleRows()) {
            return null;
        }
        int scrollbar = maxScroll() > 0 ? 6 : 0;
        int w = (cardsW() - scrollbar - GAP * (cols - 1)) / cols;
        int h = cardHeight();
        return new int[] {cardsX() + index % cols * (w + GAP), contentY() + row * (h + GAP), w, h};
    }

    private int[] buttonBox(int[] box) {
        int bw = 44;
        int bh = 13;
        return new int[] {box[0] + box[2] - bw - 4, box[1] + (box[3] - bh) / 2, bw, bh};
    }

    /** A visitor's Accept (0) and Decline (1) buttons. */
    private int[] visitorButton(int which) {
        int w = (contentW() - GAP) / 2;
        return new int[] {contentX() + which * (w + GAP), contentBottom() - 18, w, 18};
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        GardenMenu.View view = view();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        String title = view.npcView() ? view.npcName() : "The Garden";
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal(title),
                Component.literal(view.header()));
        if (view.npcView()) {
            npcPanel(g, view, mouseX, mouseY);
        } else {
            for (int i = 0; i < TABS.length; i++) {
                int[] tab = tabBox(i);
                SkyScreens.tab(g, font, THEME, tab[0], tab[1], tab[2], tab[3], TABS[i], view.tab().ordinal() == i,
                        SkyScreens.inside(mouseX, mouseY, tab[0], tab[1], tab[2], tab[3]));
            }
        }
        if (view.tab() == GardenMenu.Tab.PLOTS) {
            plots(g, view);
        }
        hovered = -1;
        for (int i = 0; i < cardCount(); i++) {
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
            int top = contentY();
            int h = visibleRows() * (cardHeight() + GAP) - GAP;
            Draw.scrollbar(g, cardsX() + cardsW() - 4, top, h, totalRows() * (cardHeight() + GAP), scroll * (cardHeight() + GAP), THEME.accent);
        }
        if (view.tab() == GardenMenu.Tab.VISITOR && view.rows().size() >= 2) {
            for (int b = 0; b < 2; b++) {
                int[] box = visitorButton(b);
                GardenMenu.Row row = view.rows().get(view.rows().size() - 2 + b);
                boolean enabled = b == 1 || visitorReady();
                SkyScreens.button(g, font, THEME, box[0], box[1], box[2], box[3], row.title(), enabled,
                        enabled && SkyScreens.inside(mouseX, mouseY, box[0], box[1], box[2], box[3]));
            }
        }
        // The bottom strip: your balances on the Desk, and the footer line.
        int bottom = topPos + imageHeight - 19;
        int x = leftPos + THEME.inset + 4;
        if (!view.npcView()) {
            for (Chip chip : view.wallet()) {
                x += SkyScreens.chip(g, font, THEME, x, bottom, chip, 0.75f) + 2;
                String name = chip.currency().isEmpty() ? chip.item().getHoverName().getString() : SkyScreens.currencyName(chip.currency());
                Draw.scaledText(g, font, Component.literal(name), x, bottom + 2, 0.6f, THEME.dim);
                x += Math.round(font.width(name) * 0.6f) + 8;
            }
        }
        int footerLeft = view.npcView() ? contentX() : x;
        Draw.fit(g, font, Component.literal(view.footer()), (footerLeft + leftPos + imageWidth - THEME.inset) / 2, bottom + 1,
                leftPos + imageWidth - THEME.inset - footerLeft - 4, 0.62f, 0.45f, Draw.Align.CENTER, THEME.dim, false);
    }

    /** Whether you have everything the visitor wants (their cards' bars are full). */
    private boolean visitorReady() {
        for (int i = 0; i < cardCount(); i++) {
            GardenMenu.Row row = view().rows().get(i);
            if (row.progress() >= 0 && row.progress() < 1) {
                return false;
            }
        }
        return true;
    }

    private int[] tabBox(int i) {
        int w = (imageWidth - 2 * THEME.inset - 3 * GAP) / 4;
        return new int[] {leftPos + THEME.inset + i * (w + GAP), topPos + SkyScreens.contentTop(THEME), w, 15};
    }

    /** The NPC's portrait (its live model, turning to the mouse), its name, and your balances. */
    private void npcPanel(GuiGraphicsExtractor g, GardenMenu.View view, int mouseX, int mouseY) {
        int x = leftPos + THEME.inset;
        int y = topPos + SkyScreens.contentTop(THEME) + 2;
        int h = contentBottom() - y;
        SkyScreens.card(g, THEME, x, y, PANEL, h, false, false);
        Entity entity = view.npc() >= 0 && minecraft.level != null ? minecraft.level.getEntity(view.npc()) : null;
        int portrait = 92;
        g.fill(x + 4, y + 4, x + PANEL - 4, y + portrait, 0x40000000);
        if (entity instanceof LivingEntity living) {
            SkyScreens.npcPortrait(g, living, x + 4, y + 4, x + PANEL - 4, y + portrait, 36, mouseX, mouseY);
        }
        int wy = y + portrait + 6;
        Draw.fit(g, font, Component.literal("Your wallet"), x + PANEL / 2, wy, PANEL - 8, 0.7f, 0.5f, Draw.Align.CENTER, THEME.accent, false);
        wy += 11;
        for (Chip chip : view.wallet()) {
            if (wy + 10 > y + h - 2) {
                break;
            }
            int cw = SkyScreens.chip(g, font, THEME, x + 8, wy, chip, 0.8f);
            String name = chip.currency().isEmpty() ? chip.item().getHoverName().getString() : SkyScreens.currencyName(chip.currency());
            Draw.fit(g, font, Component.literal(name), x + 12 + cw, wy + 2, PANEL - 18 - cw, 0.62f, 0.45f, Draw.Align.LEFT, THEME.muted, false);
            wy += 12;
        }
    }

    /** The 5 x 5 plot map: grass plots you own, the Barn, the next plot (gold edged), and locked stone ones. */
    private void plots(GuiGraphicsExtractor g, GardenMenu.View view) {
        int cell = 26;
        int x0 = contentX() + 6;
        int y0 = contentY() + 2;
        for (int i = 0; i < view.grid().size(); i++) {
            int state = view.grid().get(i);
            int x = x0 + (i % 5) * cell;
            int y = y0 + (i / 5) * cell;
            String piece = switch (state) {
                case 0 -> "card_hover";
                case 1 -> "button";
                case 2 -> "button_hover";
                default -> "button_disabled";
            };
            g.blitSprite(RenderPipelines.GUI_TEXTURED, THEME.sprite(piece), x, y, cell - 2, cell - 2);
            if (state == 0) {
                Draw.item(g, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.HAY_BLOCK), x + 4, y + 4, 1f);
            } else if (state == 2) {
                Draw.outline(g, x - 1, y - 1, cell, cell, THEME.accent);
                Draw.fit(g, font, Component.literal("Next"), x + (cell - 2) / 2, y + 9, cell - 4, 0.6f, 0.4f, Draw.Align.CENTER, 0xFFFFFFFF, true);
            } else if (state == 3) {
                g.fill(x + 1, y + 1, x + cell - 3, y + cell - 3, 0x50000000);
            }
        }
        // A legend under the cards on the right.
        int lx = cardsX();
        int ly = contentY() + cardHeight() + 10;
        String[][] legend = {{"card_hover", "The Barn"}, {"button", "Your plots"}, {"button_hover", "Next to unlock"}, {"button_disabled", "Locked"}};
        for (String[] entry : legend) {
            if (entry[0].equals("card_hover")) {
                Draw.item(g, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.HAY_BLOCK), lx - 1, ly - 1, 0.875f);
            } else {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, THEME.sprite(entry[0]), lx, ly, 12, 12);
            }
            if (entry[0].equals("button_hover")) {
                Draw.outline(g, lx - 1, ly - 1, 14, 14, THEME.accent);
            }
            Draw.fit(g, font, Component.literal(entry[1]), lx + 16, ly + 3, cardsW() - 20, 0.7f, 0.5f, Draw.Align.LEFT, THEME.muted, false);
            ly += 16;
        }
    }

    /** One card: icon (or currency sprite), title, note, progress bar, detail, chips and button. */
    private void card(GuiGraphicsExtractor g, GardenMenu.Row row, int[] box, boolean over, int mouseX, int mouseY) {
        int x = box[0];
        int y = box[1];
        int w = box[2];
        int h = box[3];
        boolean wholeCard = row.button().isEmpty() && row.enabled();
        boolean affordable = row.chips().stream().allMatch(Chip::enough);
        SkyScreens.card(g, THEME, x, y, w, h, over && row.enabled(), false);
        int textX = x + 6;
        float scale = h >= 30 ? 1.25f : 1f;
        int size = Math.round(16 * scale);
        if (!row.icon().isEmpty() || !row.sprite().isEmpty()) {
            int iy = y + (h - size) / 2;
            g.fill(x + 4, iy - 1, x + 6 + size, iy + size + 1, 0x55000000);
            if (!row.icon().isEmpty()) {
                Draw.item(g, row.icon().copyWithCount(1), x + 5, iy, scale);
            } else {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SkyRpgSkyblock.id("currency/" + row.sprite()), x + 5 + (size - 18) / 2, iy + (size - 18) / 2, 18, 18);
            }
            textX = x + 10 + size;
        }
        int right = row.button().isEmpty() ? x + w - 5 : buttonBox(box)[0] - 4;
        Component title = !row.icon().isEmpty() && row.color() == 0xFFFFFF ? row.icon().getHoverName() : Component.literal(row.title());
        if (row.icon().getCount() > 1) {
            title = title.copy().append(Component.literal(" x" + row.icon().getCount()).withColor(THEME.muted));
        }
        int noteW = row.note().isEmpty() ? 0 : Math.round(font.width(row.note()) * 0.6f) + 4;
        Draw.fit(g, font, title, textX, y + 5, Math.max(10, right - textX - noteW), 0.8f, 0.5f, Draw.Align.LEFT, 0xFF000000 | row.color(), true);
        if (noteW > 0) {
            Draw.fit(g, font, Component.literal(row.note()), right, y + 6, noteW, 0.6f, 0.45f, Draw.Align.RIGHT, THEME.muted, false);
        }
        int line = y + 15;
        if (row.progress() >= 0) {
            SkyScreens.bar(g, THEME, textX, line, right - textX, 5, row.progress(), row.progress() >= 1 ? THEME.accent : THEME.good);
            line += 8;
        }
        boolean chipsBeside = !row.chips().isEmpty() && !row.detail().isEmpty() && line + 8 + 10 > y + h;
        int chipsW = 0;
        if (chipsBeside) {
            for (Chip chip : row.chips()) {
                chipsW += SkyScreens.chipWidth(font, chip, 0.7f) + 6;
            }
            SkyScreens.chips(g, font, THEME, right - chipsW + 6, line - 1, row.chips(), 0.7f, right + 1);
        }
        if (!row.detail().isEmpty()) {
            Draw.fit(g, font, Component.literal(row.detail()), textX, line, right - textX - chipsW, 0.62f, 0.45f, Draw.Align.LEFT, THEME.muted, false);
            line += 8;
        }
        if (!row.chips().isEmpty() && !chipsBeside) {
            SkyScreens.chips(g, font, THEME, textX, Math.max(line, y + h - 13), row.chips(), 0.7f, right);
        }
        if (!row.button().isEmpty()) {
            int[] b = buttonBox(box);
            SkyScreens.button(g, font, THEME, b[0], b[1], b[2], b[3], row.button(), row.enabled(),
                    SkyScreens.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]));
        }
        if (wholeCard && !affordable && !row.icon().isEmpty()) {
            // What you can't afford: its icon dimmed (items draw above fills in the same stratum).
            int iy = y + (h - size) / 2;
            g.nextStratum();
            g.fill(x + 5, iy, x + 5 + size, iy + size, 0x99140F0A);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    /** The hovered card's tooltip: an item's own tooltip with its price, or the card's full text. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        GardenMenu.Row hoveredRow = hovered >= 0 && hovered < cardCount() ? view().rows().get(hovered) : null;
        boolean shopItem = hoveredRow != null && !hoveredRow.icon().isEmpty() && hoveredRow.color() == 0xFFFFFF;
        if (hoveredRow != null && (shopItem || hoveredRow.enabled() && !hoveredRow.chips().isEmpty())) {
            GardenMenu.Row row = hoveredRow;
            List<Component> lines = new ArrayList<>();
            if (!row.icon().isEmpty() && row.color() == 0xFFFFFF) {
                lines.addAll(row.icon().getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
            } else {
                lines.add(Component.literal(row.title()).withColor(0xFF000000 | row.color()));
                if (!row.detail().isEmpty()) {
                    lines.add(Component.literal(row.detail()).withColor(THEME.muted));
                }
            }
            if (!row.chips().isEmpty()) {
                lines.add(Component.empty());
                String label = row.button().isEmpty() && row.enabled() ? "Cost" : row.button().equals("Upgrade") ? "Upgrade cost" : "";
                if (!label.isEmpty()) {
                    lines.add(Component.literal(label).withColor(THEME.muted));
                }
                for (Chip chip : row.chips()) {
                    String what = chip.currency().isEmpty() ? chip.item().getHoverName().getString() : currencyName(chip.currency());
                    what = chip.currency().startsWith("medal_") ? what + " medals" : chip.currency().equals("tickets") ? "Jacob's Tickets" : what;
                    lines.add(Component.literal(chip.text() + " " + what).withColor(chip.enough() ? THEME.text : 0xFFFF6464));
                }
            }
            if (row.button().isEmpty() && row.enabled()) {
                boolean can = row.chips().stream().allMatch(Chip::enough);
                lines.add(Component.empty());
                lines.add(Component.literal(can ? "Click to " + (row.note().equals("Trade") ? "trade" : "buy") : "You can't afford this")
                        .withColor(can ? THEME.good : 0xFFFF6464));
            }
            Draw.queueTooltip(lines, THEME.accent);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private static String currencyName(String currency) {
        return SkyScreens.currencyName(currency);
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
            GardenMenu.View view = view();
            if (!view.npcView()) {
                for (int i = 0; i < TABS.length; i++) {
                    int[] tab = tabBox(i);
                    if (SkyScreens.inside(event.x(), event.y(), tab[0], tab[1], tab[2], tab[3])) {
                        scroll = 0;
                        return press(GardenMenu.TAB + i);
                    }
                }
            }
            if (view.tab() == GardenMenu.Tab.VISITOR && view.rows().size() >= 2) {
                for (int b = 0; b < 2; b++) {
                    int[] box = visitorButton(b);
                    if ((b == 1 || visitorReady()) && SkyScreens.inside(event.x(), event.y(), box[0], box[1], box[2], box[3])) {
                        return press(GardenMenu.ROW + view.rows().size() - 2 + b);
                    }
                }
            }
            for (int i = 0; i < cardCount(); i++) {
                int[] box = cardBox(i);
                GardenMenu.Row row = view.rows().get(i);
                if (box == null || !row.enabled()) {
                    continue;
                }
                int[] hit = row.button().isEmpty() ? box : buttonBox(box);
                if (SkyScreens.inside(event.x(), event.y(), hit[0], hit[1], hit[2], hit[3])) {
                    return press(GardenMenu.ROW + i);
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
