package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.experiment.ExperimentMenu;
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
import net.minecraft.world.item.TooltipFlag;

/**
 * The Experimentation Table: every stake of Superpairs, Chronomatron and Ultrasequencer with your charges and banked bonus
 * clicks; and the three games' boards, drawn from the menu's data slots every frame.
 */
public final class ExperimentScreen extends AbstractContainerScreen<ExperimentMenu> {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 214;
    private static final int TOP = 24;
    private static final int COLUMN_Y = 76;
    private static final int CARD_H = 20;
    private static final int TILE = 26;
    private static final int BOARD_Y = 52;
    private static final int[] STAKE_COLORS = {0xFF6BE06B, 0xFFF2D94A, 0xFFF29A3D, 0xFFFF6464, 0xFFB98CFF, 0xFFFF6AD5};
    private static final String[] GAMES = {"", "Superpairs", "Chronomatron", "Ultrasequencer"};
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
                ? Component.literal("Enchanting " + view.enchanting() + "  ·  " + view.levels() + " levels").withColor(Theme.PURPLE)
                : Component.literal(GAMES[view.mode()]).withColor(Theme.PURPLE);
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal(view.name()), subtitle);
        List<Component> tooltip = switch (view.mode()) {
            case ExperimentMenu.TABLE -> table(g, mouseX, mouseY);
            case ExperimentMenu.SUPERPAIRS -> superpairs(g, mouseX, mouseY);
            case ExperimentMenu.CHRONOMATRON -> chronomatron(g, mouseX, mouseY);
            default -> ultrasequencer(g, mouseX, mouseY);
        };
        if (view.mode() != ExperimentMenu.TABLE) {
            boolean over = menu.status(0) == ExperimentMenu.PHASE_OVER;
            button(g, mouseX, mouseY, backX(), backY(), 80, over ? "Back" : "Leave", over ? Theme.GREEN : Theme.MUTED);
        }
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, Theme.PURPLE);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    // ---- the table ---------------------------------------------------------------------------------------------

    private List<Component> table(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ExperimentMenu.View view = view();
        List<Component> tooltip = null;
        int x = leftPos + 10;
        int y = topPos + TOP + 2;
        Draw.fit(g, font, Component.literal("Charges"), x, y, 60, 0.75f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);
        for (int i = 0; i < view.maxCharges(); i++) {
            int cx = x + 44 + i * 12;
            g.fill(cx, y, cx + 8, y + 8, Theme.CARD_BORDER);
            g.fill(cx + 1, y + 1, cx + 7, y + 7, i < view.charges() ? Theme.PURPLE : Theme.TRACK);
        }
        if (view.charges() < view.maxCharges()) {
            Draw.fit(g, font, Component.literal("next in " + time(view.nextCharge())), x + 44 + view.maxCharges() * 12 + 4, y, 90, 0.7f, 0.5f,
                    Draw.Align.LEFT, Theme.DIM, false);
            if (view.buyCost() >= 0) {
                button(g, mouseX, mouseY, buyX(), buyY(), 104, "Buy a charge · " + view.buyCost() + " lvl", Theme.PURPLE);
                if (SkyScreens.inside(mouseX, mouseY, buyX(), buyY(), 104, 16)) {
                    tooltip = List.of(Component.literal("Buy a charge").withColor(Theme.PURPLE),
                            Component.literal("Costs " + view.buyCost() + " XP levels. Up to 3 a day.").withColor(Theme.MUTED));
                }
            }
        }
        int bonus = view.chronoBonus() + view.ultraBonus();
        String banked = "Bonus clicks banked: " + (bonus > 0 ? "+" + bonus : "none") + "   ·   Metaphysical Serum: " + view.serums() + "/3";
        Draw.fit(g, font, Component.literal(banked), x, y + 13, imageWidth - 20, 0.7f, 0.5f, Draw.Align.LEFT, bonus > 0 ? Theme.AQUA : Theme.DIM, false);
        Draw.fit(g, font, Component.literal("Do the add-ons first: their bonus clicks go into your next Superpairs."), x, y + 23, imageWidth - 20,
                0.65f, 0.45f, Draw.Align.LEFT, Theme.DIM, false);
        int colW = columnWidth();
        for (int game = 1; game <= 3; game++) {
            int cx = columnX(game);
            Draw.fit(g, font, Component.literal(GAMES[game]), cx, topPos + COLUMN_Y - 11, colW, 0.85f, 0.5f, Draw.Align.LEFT, Theme.TEXT, false);
        }
        for (ExperimentMenu.TierView tier : view.tiers()) {
            int cx = columnX(tier.game());
            int cy = cardY(tier.index());
            boolean unlocked = view.enchanting() >= tier.enchanting();
            boolean over = SkyScreens.inside(mouseX, mouseY, cx, cy, colW, CARD_H);
            int color = stakeColor(tier.name());
            Draw.panel(g, cx, cy, colW, CARD_H, over && unlocked ? Theme.CARD_HOVER : Theme.CARD, unlocked ? color : Theme.CARD_BORDER);
            g.fill(cx, cy, cx + 2, cy + CARD_H, unlocked ? color : Theme.DIM);
            Draw.fit(g, font, Component.literal(tier.name()), cx + 6, cy + 3, colW - 10, 0.75f, 0.5f, Draw.Align.LEFT,
                    unlocked ? color : Theme.DIM, false);
            String line = unlocked ? tier.cost() + " levels" : "Enchanting " + tier.enchanting();
            Draw.fit(g, font, Component.literal(line), cx + 6, cy + 12, colW - 10, 0.6f, 0.4f, Draw.Align.LEFT,
                    unlocked ? Theme.MUTED : Theme.RED, false);
            if (over) {
                tooltip = new ArrayList<>();
                tooltip.add(Component.literal(tier.name() + " " + GAMES[tier.game()]).withColor(color));
                tooltip.add(Component.literal(BLURBS[tier.game()]).withColor(Theme.MUTED));
                tooltip.add(Component.empty());
                tooltip.add(Component.literal("Requires Enchanting " + tier.enchanting()).withColor(unlocked ? Theme.GREEN : Theme.RED));
                tooltip.add(Component.literal("Costs " + tier.cost() + " XP levels").withColor(view.levels() >= tier.cost() ? Theme.MUTED : Theme.RED));
                if (tier.game() == ExperimentMenu.SUPERPAIRS) {
                    tooltip.add(Component.literal("Uses a charge").withColor(Theme.MUTED));
                }
                tooltip.add(Component.empty());
                tooltip.add(Component.literal(unlocked ? "Click to start!" : "Locked").withColor(unlocked ? Theme.ACCENT : Theme.RED));
            }
        }
        return tooltip;
    }

    private int columnWidth() {
        return (imageWidth - 20 - 2 * 6) / 3;
    }

    private int columnX(int game) {
        return leftPos + 10 + (game - 1) * (columnWidth() + 6);
    }

    private int cardY(int index) {
        return topPos + COLUMN_Y + index * (CARD_H + 2);
    }

    private int buyX() {
        return leftPos + imageWidth - 114;
    }

    private int buyY() {
        return topPos + TOP - 2;
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
                over ? Theme.GREEN : Theme.TEXT, false);
        List<Component> tooltip = null;
        int bx = boardX(view.width(), TILE);
        for (int i = 0; i < view.width() * view.height(); i++) {
            int value = menu.cell(i);
            int tx = bx + i % view.width() * TILE;
            int ty = topPos + BOARD_Y + i / view.width() * TILE;
            boolean hover = SkyScreens.inside(mouseX, mouseY, tx, ty, TILE - 2, TILE - 2);
            if (value <= 0) {
                Draw.panel(g, tx, ty, TILE - 2, TILE - 2, hover && !over ? Theme.CARD_HOVER : Theme.CARD, Theme.PURPLE);
                Draw.fit(g, font, Component.literal("?"), tx + (TILE - 2) / 2, ty + 8, TILE, 1f, 1f, Draw.Align.CENTER, Theme.PURPLE, false);
                continue;
            }
            int state = value / 1000;
            int picture = value % 1000 - 1;
            int border = state == 2 ? Theme.GREEN : state == 1 ? Theme.ACCENT : Theme.CARD_BORDER;
            Draw.panel(g, tx, ty, TILE - 2, TILE - 2, state == 3 ? Theme.TRACK : Theme.CARD, border);
            if (picture >= 0 && picture < view.palette().size()) {
                ItemStack stack = view.palette().get(picture);
                Draw.item(g, stack, tx + 4, ty + 4, 1f);
                if (state == 3) {
                    g.fill(tx + 1, ty + 1, tx + TILE - 3, ty + TILE - 3, 0x88070910);
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
                phase == ExperimentMenu.PHASE_PLAY ? Theme.ACCENT : phase == ExperimentMenu.PHASE_OVER ? Theme.GREEN : Theme.TEXT, false);
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
            Draw.panel(g, px, py, size, size, lit ? color : Draw.mix(Theme.CARD, color, hover ? 0.45f : 0.25f), lit ? 0xFFFFFFFF : color);
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
                phase == ExperimentMenu.PHASE_PLAY ? Theme.ACCENT : phase == ExperimentMenu.PHASE_OVER ? Theme.GREEN : Theme.TEXT, false);
        xpLine(g, Math.min(done, 20), 20, view.xpPerStep());
        int size = view.width() > 7 ? 24 : 28;
        int bx = boardX(view.width(), size);
        for (int i = 0; i < view.width() * view.height(); i++) {
            int value = menu.cell(i);
            int sx = bx + i % view.width() * size;
            int sy = topPos + BOARD_Y + 6 + i / view.width() * size;
            boolean hover = SkyScreens.inside(mouseX, mouseY, sx, sy, size - 2, size - 2) && phase == ExperimentMenu.PHASE_PLAY;
            int fill = value >= 2000 ? 0xFF3A1E24 : value >= 1000 ? 0xFF1E3A2A : value > 0 ? 0xFF2A2350 : hover ? Theme.CARD_HOVER : Theme.CARD;
            int border = value >= 2000 ? Theme.RED : value >= 1000 ? Theme.GREEN : value > 0 ? Theme.PURPLE : Theme.CARD_BORDER;
            Draw.panel(g, sx, sy, size - 2, size - 2, fill, border);
            if (value > 0) {
                int number = value % 1000;
                Draw.fit(g, font, Component.literal(String.valueOf(number)), sx + (size - 2) / 2, sy + (size - 2) / 2 - 4, size, 1f, 0.8f,
                        Draw.Align.CENTER, value >= 2000 ? Theme.RED : value >= 1000 ? Theme.GREEN : Theme.TEXT, false);
            }
        }
        return null;
    }

    private void xpLine(GuiGraphicsExtractor g, int steps, int cap, int perStep) {
        String text = String.format(Locale.ROOT, "%,d Enchanting Exp so far  (%,d a step, up to %d)", steps * perStep, perStep, cap);
        Draw.fit(g, font, Component.literal(text), leftPos + imageWidth / 2, topPos + TOP + 18, imageWidth - 20, 0.65f, 0.45f, Draw.Align.CENTER,
                Theme.AQUA, false);
    }

    private int backX() {
        return leftPos + (imageWidth - 80) / 2;
    }

    private int backY() {
        return topPos + imageHeight - 24;
    }

    private void button(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, String text, int color) {
        boolean over = SkyScreens.inside(mouseX, mouseY, x, y, w, 16);
        Draw.panel(g, x, y, w, 16, over ? Theme.CARD_HOVER : Theme.CARD, color);
        Draw.fit(g, font, Component.literal(text), x + w / 2, y + 4, w - 6, 0.75f, 0.5f, Draw.Align.CENTER, color, false);
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
                int bx = boardX(view.width(), TILE);
                return SkyScreens.inside(mx, my, bx + i % view.width() * TILE, topPos + BOARD_Y + i / view.width() * TILE, TILE - 2, TILE - 2);
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
                int size = view.width() > 7 ? 24 : 28;
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
