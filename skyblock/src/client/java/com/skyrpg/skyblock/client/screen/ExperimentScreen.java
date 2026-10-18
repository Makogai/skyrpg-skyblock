package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.experiment.ExperimentMenu;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Experimentation Table: every stake of Superpairs, Chronomatron and Ultrasequencer with your charges and banked bonus
 * clicks; and the three games' boards, drawn from the menu's data slots every frame.
 */
public final class ExperimentScreen extends AbstractContainerScreen<ExperimentMenu> {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 236;
    private static final ScreenTheme THEME = ScreenTheme.MAGIC;
    private static final int TOP = 36;
    private static final int COLUMN_Y = 90;
    private static final int CARD_H = 20;
    private static final int TILE = 26;
    private static final int BOARD_Y = 62;
    private static final int[] STAKE_COLORS = {0xFF6BE06B, 0xFFF2D94A, 0xFFF29A3D, 0xFFFF6464, 0xFFB98CFF, 0xFFFF6AD5};
    private static final String[] GAMES = {"", "Superpairs", "Chronomatron", "Ultrasequencer"};
    private static final ItemStack[] GAME_ICONS = {ItemStack.EMPTY, new ItemStack(Items.ENDER_EYE), new ItemStack(Items.NOTE_BLOCK),
            new ItemStack(Items.COMPARATOR)};
    private static final String[] BLURBS = {"",
            "Find pairs of prizes with a few clicks.",
            "Repeat the notes. Bonus Superpairs clicks.",
            "Click the numbers in order. Bonus clicks."};

    public ExperimentScreen(ExperimentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private ExperimentMenu.View view() {
        return menu.view();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        ExperimentMenu.View view = view();
        Component subtitle = view.mode() == ExperimentMenu.TABLE
                ? Component.literal("Enchanting " + view.enchanting() + "  ·  " + view.levels() + " levels").withColor(THEME.good)
                : Component.literal(GAMES[view.mode()]).withColor(THEME.good);
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal(view.name()), subtitle);
        List<Component> tooltip = switch (view.mode()) {
            case ExperimentMenu.TABLE -> table(g, mouseX, mouseY);
            case ExperimentMenu.SUPERPAIRS -> superpairs(g, mouseX, mouseY);
            case ExperimentMenu.CHRONOMATRON -> chronomatron(g, mouseX, mouseY);
            default -> ultrasequencer(g, mouseX, mouseY);
        };
        if (view.mode() != ExperimentMenu.TABLE) {
            boolean over = menu.status(0) == ExperimentMenu.PHASE_OVER;
            button(g, mouseX, mouseY, backX(), backY(), 80, over ? "Back" : "Leave", true);
        }
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, THEME.accent);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    // ---- the table ---------------------------------------------------------------------------------------------

    private List<Component> table(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ExperimentMenu.View view = view();
        List<Component> tooltip = null;
        int x = leftPos + THEME.inset;
        int y = topPos + TOP + 2;
        Draw.fit(g, font, Component.literal("Charges"), x, y + 2, 60, 0.8f, 0.5f, Draw.Align.LEFT, THEME.accent, false);
        for (int i = 0; i < view.maxCharges(); i++) {
            int cx = x + 44 + i * 14;
            SkyScreens.card(g, THEME, cx - 1, y - 1, 12, 12, false, i >= view.charges());
            if (i < view.charges()) {
                Draw.item(g, new ItemStack(Items.AMETHYST_SHARD), cx, y, 0.625f);
            }
        }
        if (view.charges() < view.maxCharges()) {
            Draw.fit(g, font, Component.literal("next in " + time(view.nextCharge())), x + 44 + view.maxCharges() * 14 + 4, y + 2, 90, 0.7f, 0.5f,
                    Draw.Align.LEFT, THEME.dim, false);
            if (view.buyCost() >= 0) {
                Chip cost = new Chip("", new ItemStack(Items.EXPERIENCE_BOTTLE), view.buyCost() + " lv", view.levels() >= view.buyCost());
                button(g, mouseX, mouseY, buyX(), buyY(), 104, "Buy a charge", true);
                int cw = SkyScreens.chipWidth(font, cost, 0.65f);
                SkyScreens.chip(g, font, THEME, buyX() + 104 - cw - 4, buyY() + 3, cost, 0.65f);
                if (SkyScreens.inside(mouseX, mouseY, buyX(), buyY(), 104, 16)) {
                    tooltip = List.of(Component.literal("Buy a charge").withColor(THEME.good),
                            Component.literal("Costs " + view.buyCost() + " XP levels. Up to 3 a day.").withColor(THEME.muted));
                }
            }
        }
        int bonus = view.chronoBonus() + view.ultraBonus();
        int ly = y + 15;
        Draw.fit(g, font, Component.literal("Bonus clicks"), x, ly + 2, 60, 0.7f, 0.5f, Draw.Align.LEFT, THEME.muted, false);
        int cx = x + 44;
        cx += SkyScreens.chip(g, font, THEME, cx, ly, Chip.note(new ItemStack(Items.EXPERIENCE_BOTTLE), bonus > 0 ? "+" + bonus + " banked" : "none banked", bonus > 0), 0.75f) + 10;
        SkyScreens.chip(g, font, THEME, cx, ly, Chip.note(new ItemStack(Items.HONEY_BOTTLE), "Metaphysical Serum " + view.serums() + "/3", true), 0.75f);
        Draw.fit(g, font, Component.literal("Do the add-ons first: their bonus clicks go into your next Superpairs."), x, ly + 14, imageWidth - 2 * THEME.inset,
                0.65f, 0.45f, Draw.Align.LEFT, THEME.dim, false);
        int colW = columnWidth();
        for (int game = 1; game <= 3; game++) {
            int gx = columnX(game);
            Draw.item(g, GAME_ICONS[game], gx, topPos + COLUMN_Y - 15, 0.75f);
            Draw.fit(g, font, Component.literal(GAMES[game]), gx + 15, topPos + COLUMN_Y - 11, colW - 15, 0.85f, 0.5f, Draw.Align.LEFT, THEME.text, true);
        }
        for (ExperimentMenu.TierView tier : view.tiers()) {
            int cx2 = columnX(tier.game());
            int cy = cardY(tier.index());
            boolean unlocked = view.enchanting() >= tier.enchanting();
            boolean over = SkyScreens.inside(mouseX, mouseY, cx2, cy, colW, CARD_H);
            int color = stakeColor(tier.name());
            SkyScreens.card(g, THEME, cx2, cy, colW, CARD_H, over && unlocked, !unlocked);
            g.fill(cx2 + 3, cy + 3, cx2 + 5, cy + CARD_H - 3, unlocked ? color : THEME.dim);
            Draw.fit(g, font, Component.literal(tier.name()), cx2 + 9, cy + 3, colW - 13, 0.75f, 0.5f, Draw.Align.LEFT,
                    unlocked ? color : THEME.dim, true);
            if (unlocked) {
                SkyScreens.chip(g, font, THEME, cx2 + 9, cy + 12, new Chip("", new ItemStack(Items.EXPERIENCE_BOTTLE), tier.cost() + " lv",
                        view.levels() >= tier.cost()), 0.62f);
            } else {
                Draw.fit(g, font, Component.literal("Enchanting " + tier.enchanting()), cx2 + 9, cy + 12, colW - 13, 0.6f, 0.4f, Draw.Align.LEFT, Theme.RED, false);
            }
            if (over) {
                tooltip = new ArrayList<>();
                tooltip.add(Component.literal(tier.name() + " " + GAMES[tier.game()]).withColor(color));
                tooltip.add(Component.literal(BLURBS[tier.game()]).withColor(THEME.muted));
                tooltip.add(Component.empty());
                tooltip.add(Component.literal("Requires Enchanting " + tier.enchanting()).withColor(unlocked ? THEME.good : Theme.RED));
                tooltip.add(Component.literal("Costs " + tier.cost() + " XP levels").withColor(view.levels() >= tier.cost() ? THEME.muted : Theme.RED));
                if (tier.game() == ExperimentMenu.SUPERPAIRS) {
                    tooltip.add(Component.literal("Uses a charge").withColor(THEME.muted));
                }
                tooltip.add(Component.empty());
                tooltip.add(Component.literal(unlocked ? "Click to start!" : "Locked").withColor(unlocked ? THEME.accent : Theme.RED));
            }
        }
        return tooltip;
    }

    private int columnWidth() {
        return (imageWidth - 2 * THEME.inset - 2 * 6) / 3;
    }

    private int columnX(int game) {
        return leftPos + THEME.inset + (game - 1) * (columnWidth() + 6);
    }

    private int cardY(int index) {
        return topPos + COLUMN_Y + index * (CARD_H + 2);
    }

    private int buyX() {
        return leftPos + imageWidth - 114;
    }

    private int buyY() {
        return topPos + TOP;
    }

    private static int stakeColor(String name) {
        return switch (name) {
            case "Beginner" -> STAKE_COLORS[0];
            case "High" -> STAKE_COLORS[1];
            case "Grand" -> STAKE_COLORS[2];
            case "Supreme" -> STAKE_COLORS[3];
            case "Transcendent" -> STAKE_COLORS[4];
            default -> STAKE_COLORS[5];
        };
    }

    // ---- the games ---------------------------------------------------------------------------------------------

    /** Superpairs' tile size: 26, smaller when the board has many rows. */
    private static int tile(ExperimentMenu.View view) {
        return Math.min(TILE, 140 / Math.max(1, view.height()));
    }

    /** Ultrasequencer's square size. */
    private static int square(ExperimentMenu.View view) {
        int base = view.width() > 7 ? 24 : 28;
        return Math.min(base, 138 / Math.max(1, view.height()));
    }

    private int boardX(int columns, int size) {
        return leftPos + (imageWidth - columns * size + 2) / 2;
    }

    private List<Component> superpairs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ExperimentMenu.View view = view();
        boolean over = menu.status(0) == ExperimentMenu.PHASE_OVER;
        int clicks = menu.status(1);
        String head = over ? (clicks > 0 ? "Board cleared!  " : "Out of clicks!  ") + menu.status(2) + " pairs won" : "Clicks left: " + clicks + "   ·   Pairs: " + menu.status(2)
                + (menu.status(3) == 1 ? "   ·   Instant Find!" : "");
        Draw.fit(g, font, Component.literal(head), leftPos + imageWidth / 2, topPos + TOP + 6, imageWidth - 20, 0.85f, 0.5f, Draw.Align.CENTER,
                over ? THEME.good : THEME.text, false);
        List<Component> tooltip = null;
        int tile = tile(view);
        int bx = boardX(view.width(), tile);
        for (int i = 0; i < view.width() * view.height(); i++) {
            int value = menu.cell(i);
            int tx = bx + i % view.width() * tile;
            int ty = topPos + BOARD_Y + i / view.width() * tile;
            boolean hover = SkyScreens.inside(mouseX, mouseY, tx, ty, tile - 2, tile - 2);
            if (value <= 0) {
                SkyScreens.card(g, THEME, tx, ty, tile - 2, tile - 2, hover && !over, false);
                Draw.fit(g, font, Component.literal("?"), tx + (tile - 2) / 2, ty + (tile - 2) / 2 - 4, tile, 1f, 1f, Draw.Align.CENTER, THEME.good, true);
                continue;
            }
            int state = value / 1000;
            int picture = value % 1000 - 1;
            SkyScreens.card(g, THEME, tx, ty, tile - 2, tile - 2, state == 1, state == 3);
            if (state == 2 || state == 1) {
                Draw.outline(g, tx, ty, tile - 2, tile - 2, state == 2 ? THEME.good : THEME.accent);
            }
            if (picture >= 0 && picture < view.palette().size()) {
                ItemStack stack = view.palette().get(picture);
                Draw.item(g, stack, tx + (tile - 18) / 2 + 1, ty + (tile - 18) / 2 + 1, 1f);
                if (state == 3) {
                    g.fill(tx + 1, ty + 1, tx + tile - 3, ty + tile - 3, 0x88070910);
                }
                if (hover) {
                    tooltip = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
                }
            }
        }
        return tooltip;
    }

    private List<Component> chronomatron(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ExperimentMenu.View view = view();
        int phase = menu.status(0);
        int done = menu.status(1);
        String head = switch (phase) {
            case ExperimentMenu.PHASE_WATCH -> "Listen...  chain of " + menu.status(3);
            case ExperimentMenu.PHASE_PLAY -> "Your turn:  " + menu.status(2) + " / " + menu.status(3);
            default -> "Game over!  Longest chain: " + done;
        };
        Draw.fit(g, font, Component.literal(head), leftPos + imageWidth / 2, topPos + TOP + 6, imageWidth - 20, 0.9f, 0.5f, Draw.Align.CENTER,
                phase == ExperimentMenu.PHASE_PLAY ? THEME.accent : phase == ExperimentMenu.PHASE_OVER ? THEME.good : THEME.text, false);
        xpLine(g, Math.min(done, 15), 15, view.xpPerStep());
        int pads = view.width();
        int perRow = Math.min(5, pads);
        int size = 40;
        for (int i = 0; i < pads; i++) {
            int row = i / perRow;
            int inRow = Math.min(perRow, pads - row * perRow);
            int px = leftPos + (imageWidth - inRow * (size + 4) + 4) / 2 + i % perRow * (size + 4);
            int py = topPos + BOARD_Y + 6 + row * (size + 6);
            boolean lit = menu.cell(i) == 1;
            int color = 0xFF000000 | (ExperimentMenu.NOTES[i].getTextureDiffuseColor() & 0xFFFFFF);
            boolean hover = SkyScreens.inside(mouseX, mouseY, px, py, size, size) && phase == ExperimentMenu.PHASE_PLAY;
            Draw.panel(g, px, py, size, size, lit ? color : Draw.mix(Theme.CARD, color, hover ? 0.5f : 0.3f), lit ? 0xFFFFFFFF : color);
            Draw.item(g, view.palette().get(i), px + (size - 16) / 2, py + (size - 16) / 2, 1f);
        }
        return null;
    }

    private List<Component> ultrasequencer(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ExperimentMenu.View view = view();
        int phase = menu.status(0);
        int done = menu.status(1);
        String head = switch (phase) {
            case ExperimentMenu.PHASE_WATCH -> "Remember the numbers!";
            case ExperimentMenu.PHASE_PLAY -> "Click them in order:  next is " + menu.status(2);
            default -> "Game over!  Longest sequence: " + done;
        };
        Draw.fit(g, font, Component.literal(head), leftPos + imageWidth / 2, topPos + TOP + 6, imageWidth - 20, 0.9f, 0.5f, Draw.Align.CENTER,
                phase == ExperimentMenu.PHASE_PLAY ? THEME.accent : phase == ExperimentMenu.PHASE_OVER ? THEME.good : THEME.text, false);
        xpLine(g, Math.min(done, 20), 20, view.xpPerStep());
        int size = square(view);
        int bx = boardX(view.width(), size);
        for (int i = 0; i < view.width() * view.height(); i++) {
            int value = menu.cell(i);
            int sx = bx + i % view.width() * size;
            int sy = topPos + BOARD_Y + 6 + i / view.width() * size;
            boolean hover = SkyScreens.inside(mouseX, mouseY, sx, sy, size - 2, size - 2) && phase == ExperimentMenu.PHASE_PLAY;
            if (value > 0) {
                int fill = value >= 2000 ? 0xFF3A1E24 : value >= 1000 ? 0xFF1E3A2A : 0xFF2A2350;
                int border = value >= 2000 ? Theme.RED : value >= 1000 ? Theme.GREEN : THEME.good;
                Draw.panel(g, sx, sy, size - 2, size - 2, fill, border);
            } else {
                SkyScreens.card(g, THEME, sx, sy, size - 2, size - 2, hover, false);
            }
            if (value > 0) {
                int number = value % 1000;
                Draw.fit(g, font, Component.literal(String.valueOf(number)), sx + (size - 2) / 2, sy + (size - 2) / 2 - 4, size, 1f, 0.8f,
                        Draw.Align.CENTER, value >= 2000 ? Theme.RED : value >= 1000 ? Theme.GREEN : THEME.text, false);
            }
        }
        return null;
    }

    private void xpLine(GuiGraphicsExtractor g, int steps, int cap, int perStep) {
        String text = String.format(Locale.ROOT, "%,d Enchanting Exp so far  (%,d a step, up to %d)", steps * perStep, perStep, cap);
        Draw.fit(g, font, Component.literal(text), leftPos + imageWidth / 2, topPos + TOP + 18, imageWidth - 20, 0.65f, 0.45f, Draw.Align.CENTER,
                THEME.accent, false);
    }

    private int backX() {
        return leftPos + (imageWidth - 80) / 2;
    }

    private int backY() {
        return topPos + imageHeight - 24;
    }

    private void button(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, String text, boolean enabled) {
        SkyScreens.button(g, font, THEME, x, y, w, 16, text, enabled, SkyScreens.inside(mouseX, mouseY, x, y, w, 16));
    }

    private static String time(long seconds) {
        long minutes = Math.max(1, seconds / 60);
        return minutes >= 60 ? minutes / 60 + "h " + minutes % 60 + "m" : minutes + "m";
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        ExperimentMenu.View view = view();
        double mx = event.x();
        double my = event.y();
        if (view.mode() == ExperimentMenu.TABLE) {
            if (view.charges() < view.maxCharges() && view.buyCost() >= 0 && SkyScreens.inside(mx, my, buyX(), buyY(), 104, 16)) {
                return press(ExperimentMenu.BUY);
            }
            for (ExperimentMenu.TierView tier : view.tiers()) {
                if (SkyScreens.inside(mx, my, columnX(tier.game()), cardY(tier.index()), columnWidth(), CARD_H)) {
                    return press(ExperimentMenu.START + tier.game() * 100 + tier.index());
                }
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (SkyScreens.inside(mx, my, backX(), backY(), 80, 16)) {
            return press(ExperimentMenu.BACK);
        }
        for (int i = 0; i < ExperimentMenu.CELLS; i++) {
            if (cellAt(view, i, mx, my)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean cellAt(ExperimentMenu.View view, int i, double mx, double my) {
        switch (view.mode()) {
            case ExperimentMenu.SUPERPAIRS -> {
                if (i >= view.width() * view.height()) {
                    return false;
                }
                int tile = tile(view);
                int bx = boardX(view.width(), tile);
                return SkyScreens.inside(mx, my, bx + i % view.width() * tile, topPos + BOARD_Y + i / view.width() * tile, tile - 2, tile - 2);
            }
            case ExperimentMenu.CHRONOMATRON -> {
                int pads = view.width();
                if (i >= pads) {
                    return false;
                }
                int perRow = Math.min(5, pads);
                int size = 40;
                int row = i / perRow;
                int inRow = Math.min(perRow, pads - row * perRow);
                int px = leftPos + (imageWidth - inRow * (size + 4) + 4) / 2 + i % perRow * (size + 4);
                int py = topPos + BOARD_Y + 6 + row * (size + 6);
                return SkyScreens.inside(mx, my, px, py, size, size);
            }
            default -> {
                if (i >= view.width() * view.height()) {
                    return false;
                }
                int size = square(view);
                int bx = boardX(view.width(), size);
                return SkyScreens.inside(mx, my, bx + i % view.width() * size, topPos + BOARD_Y + 6 + i / view.width() * size, size - 2, size - 2);
            }
        }
    }

    private boolean press(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        SkyScreens.click();
        return true;
    }
}
