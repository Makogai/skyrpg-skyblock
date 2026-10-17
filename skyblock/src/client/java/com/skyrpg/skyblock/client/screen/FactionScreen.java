package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.crimson.FactionMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A faction leader's screen: your reputation and rank with the faction, then today's quests (or joining it). */
public final class FactionScreen extends AbstractContainerScreen<FactionMenu> {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 214;
    private static final List<String> RANKS = List.of("Neutral", "Friendly", "Trusted", "Honored", "Hero");
    private static final int[] RANK_REP = {0, 1_000, 3_000, 7_000, 12_000};

    public FactionScreen(FactionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int color() {
        return menu.view().faction().equals("mage") ? 0xFFAA55FF : 0xFFFF7733;
    }

    private String plural() {
        return menu.view().faction().equals("mage") ? "Mages" : "Barbarians";
    }

    private int buttonX() {
        return leftPos + (imageWidth - 150) / 2;
    }

    private int buttonY() {
        return topPos + 130;
    }

    private static String number(long n) {
        return String.format("%,d", n);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        FactionMenu.View view = menu.view();
        int accent = color();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal(view.leader()),
                Component.literal(plural()).withColor(accent));

        // Reputation and rank.
        int x = leftPos + 12;
        int w = imageWidth - 24;
        int y = topPos + 26;
        int rank = 0;
        while (rank + 1 < RANK_REP.length && view.rep() >= RANK_REP[rank + 1]) {
            rank++;
        }
        Draw.panel(g, x, y, w, 30, Theme.CARD, accent);
        Draw.fit(g, font, Component.literal("Reputation: " + number(view.rep()) + "   ·   " + RANKS.get(rank)), x + 8, y + 5, w - 16, 0.85f, 0.5f,
                Draw.Align.LEFT, accent, false);
        float progress = rank + 1 < RANK_REP.length ? (float) (view.rep() - RANK_REP[rank]) / (RANK_REP[rank + 1] - RANK_REP[rank]) : 1f;
        Draw.bar(g, x + 8, y + 17, w - 16, 5, progress, accent);
        String next = rank + 1 < RANK_REP.length ? number(RANK_REP[rank + 1] - view.rep()) + " to " + RANKS.get(rank + 1) : "Highest rank";
        Draw.fit(g, font, Component.literal(next), x + w - 8, y + 5, 120, 0.6f, 0.4f, Draw.Align.RIGHT, Theme.MUTED, false);

        y += 38;
        switch (view.state()) {
            case MEMBER -> {
                long h = view.reset() / 3600;
                long m = view.reset() % 3600 / 60;
                Draw.fit(g, font, Component.literal("Daily quests"), x, y, w / 2, 0.8f, 0.5f, Draw.Align.LEFT, Theme.TEXT, false);
                Draw.fit(g, font, Component.literal("New quests in " + h + "h " + m + "m"), x + w, y, w / 2, 0.65f, 0.4f, Draw.Align.RIGHT, Theme.DIM, false);
                y += 12;
                for (FactionMenu.QuestRow row : view.quests()) {
                    Draw.panel(g, x, y, w, 22, Theme.CARD, row.done() ? Theme.GREEN : Theme.CARD_BORDER);
                    Draw.fit(g, font, Component.literal((row.done() ? "✔ " : "") + row.text()), x + 6, y + 3, w - 90, 0.72f, 0.45f, Draw.Align.LEFT,
                            row.done() ? Theme.GREEN : Theme.TEXT, false);
                    Draw.fit(g, font, Component.literal(row.where()), x + 6, y + 13, w - 90, 0.6f, 0.4f, Draw.Align.LEFT, Theme.DIM, false);
                    Draw.fit(g, font, Component.literal(row.progress() + "/" + row.amount()), x + w - 6, y + 3, 80, 0.72f, 0.45f, Draw.Align.RIGHT,
                            row.done() ? Theme.GREEN : Theme.TEXT, false);
                    Draw.fit(g, font, Component.literal("+" + row.rep() + " Rep"), x + w - 6, y + 13, 80, 0.6f, 0.4f, Draw.Align.RIGHT, accent, false);
                    y += 25;
                }
            }
            case JOIN, SWITCH -> {
                String line = view.state() == FactionMenu.State.JOIN ? "Fight for the " + plural() + "? You can switch sides later."
                        : "Leave your faction for the " + plural() + "? Your reputation stays with each side.";
                Draw.fit(g, font, Component.literal(line), leftPos + imageWidth / 2, y + 20, w, 0.8f, 0.5f, Draw.Align.CENTER, Theme.TEXT, false);
                boolean over = SkyScreens.inside(mouseX, mouseY, buttonX(), buttonY(), 150, 18);
                Draw.panel(g, buttonX(), buttonY(), 150, 18, over ? Theme.CARD_HOVER : Theme.CARD, accent);
                Draw.fit(g, font, Component.literal((view.state() == FactionMenu.State.JOIN ? "Join the " : "Switch to the ") + plural()),
                        buttonX() + 75, buttonY() + 5, 144, 0.8f, 0.5f, Draw.Align.CENTER, accent, false);
            }
            case LOCKED -> Draw.fit(g, font, Component.literal(view.note()), leftPos + imageWidth / 2, y + 30, w, 0.8f, 0.5f, Draw.Align.CENTER,
                    Theme.MUTED, false);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        FactionMenu.State state = menu.view().state();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && (state == FactionMenu.State.JOIN || state == FactionMenu.State.SWITCH)
                && SkyScreens.inside(event.x(), event.y(), buttonX(), buttonY(), 150, 18)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FactionMenu.JOIN);
            SkyScreens.click();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
