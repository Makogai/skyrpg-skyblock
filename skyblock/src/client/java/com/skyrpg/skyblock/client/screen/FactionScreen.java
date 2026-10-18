package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A faction leader's screen, in the Crimson theme: the leader's portrait and your wallet on the left; on the right your
 * reputation card (rank, progress to the next rank) and today's quests as cards with progress bars and reputation chips, or a
 * card to join (or switch to) the faction.
 */
public final class FactionScreen extends AbstractContainerScreen<FactionMenu> {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 226;
    private static final ScreenTheme THEME = ScreenTheme.CRIMSON;
    private static final List<String> RANKS = List.of("Neutral", "Friendly", "Trusted", "Honored", "Hero");
    private static final int[] RANK_REP = {0, 1_000, 3_000, 7_000, 12_000};
    private static final int REP_H = 36;
    private static final int QUEST_H = 21;
    private static final int GAP = 2;
    private int hoveredQuest = -1;
    private boolean hoverRep;

    public FactionScreen(FactionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int color() {
        return menu.view().faction().equals("mage") ? 0xFFAA55FF : 0xFFFF7733;
    }

    private String plural() {
        return menu.view().faction().equals("mage") ? "Mages" : "Barbarians";
    }

    private ItemStack factionIcon() {
        return new ItemStack(menu.view().faction().equals("mage") ? Items.AMETHYST_SHARD : Items.IRON_AXE);
    }

    private int contentX() {
        return leftPos + THEME.inset + NpcCards.PANEL + 6;
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

    private int rank() {
        int rank = 0;
        while (rank + 1 < RANK_REP.length && menu.view().rep() >= RANK_REP[rank + 1]) {
            rank++;
        }
        return rank;
    }

    private int[] buttonBox() {
        int w = 150;
        return new int[] {contentX() + (contentW() - w) / 2, contentBottom() - 30, w, 18};
    }

    private int questY(int i) {
        return contentY() + REP_H + 17 + i * (QUEST_H + GAP);
    }

    private static String number(long n) {
        return String.format("%,d", n);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        FactionMenu.View view = menu.view();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal(view.leader()),
                Component.literal(plural()).withColor(color()));
        NpcCards.npcPanel(g, font, THEME, minecraft, leftPos + THEME.inset, contentY(), contentBottom() - contentY(), view.npc(), "Your wallet",
                view.wallet(), mouseX, mouseY);
        int x = contentX();
        int w = contentW();
        int accent = color();

        // Reputation and rank.
        int y = contentY();
        int rank = rank();
        hoverRep = SkyScreens.inside(mouseX, mouseY, x, y, w, REP_H);
        SkyScreens.card(g, THEME, x, y, w, REP_H, hoverRep, false);
        NpcCards.icon(g, factionIcon(), x + 4, y + 8, 20, 1.125f);
        int textX = x + 30;
        Draw.fit(g, font, Component.literal("Reputation  " + number(view.rep())), textX, y + 5, w / 2, 0.85f, 0.5f, Draw.Align.LEFT, THEME.text, true);
        Draw.fit(g, font, Component.literal(RANKS.get(rank)), x + w - 6, y + 5, w / 3, 0.85f, 0.5f, Draw.Align.RIGHT, accent, true);
        float progress = rank + 1 < RANK_REP.length ? (float) (view.rep() - RANK_REP[rank]) / (RANK_REP[rank + 1] - RANK_REP[rank]) : 1f;
        SkyScreens.bar(g, THEME, textX, y + 17, x + w - 6 - textX, 6, progress, accent);
        // Rank pips under the bar: reached ranks lit.
        String next = rank + 1 < RANK_REP.length ? number(RANK_REP[rank + 1] - view.rep()) + " Rep to " + RANKS.get(rank + 1) : "Highest rank reached";
        Draw.fit(g, font, Component.literal(next), textX, y + 26, x + w - 6 - textX - 40, 0.6f, 0.4f, Draw.Align.LEFT, THEME.muted, false);
        for (int r = 0; r < RANKS.size(); r++) {
            int px = x + w - 6 - (RANKS.size() - r) * 7 + 2;
            g.fill(px, y + 26, px + 5, y + 31, 0xFF000000);
            g.fill(px + 1, y + 27, px + 4, y + 30, r <= rank ? accent : THEME.track);
        }

        hoveredQuest = -1;
        switch (view.state()) {
            case MEMBER -> {
                long h = view.reset() / 3600;
                long m = view.reset() % 3600 / 60;
                int hy = y + REP_H + 5;
                Draw.fit(g, font, Component.literal("Daily quests"), x + 2, hy, w / 2, 0.8f, 0.5f, Draw.Align.LEFT, THEME.accent, false);
                Draw.fit(g, font, Component.literal("New quests in " + h + "h " + m + "m"), x + w - 2, hy + 1, w / 2, 0.62f, 0.4f, Draw.Align.RIGHT,
                        THEME.dim, false);
                for (int i = 0; i < view.quests().size() && questY(i) + QUEST_H <= contentBottom() + 2; i++) {
                    FactionMenu.QuestRow row = view.quests().get(i);
                    int qy = questY(i);
                    boolean over = SkyScreens.inside(mouseX, mouseY, x, qy, w, QUEST_H);
                    if (over) {
                        hoveredQuest = i;
                    }
                    quest(g, row, x, qy, w, over);
                }
            }
            case JOIN, SWITCH -> {
                boolean join = view.state() == FactionMenu.State.JOIN;
                int top = y + REP_H + 6;
                int h = contentBottom() - top;
                SkyScreens.card(g, THEME, x, top, w, h, false, false);
                NpcCards.icon(g, factionIcon(), x + w / 2 - 20, top + 10, 40, 2f);
                Draw.fit(g, font, Component.literal(join ? "Fight for the " + plural() + "?" : "Switch to the " + plural() + "?"), x + w / 2, top + 55, w - 16,
                        1f, 0.6f, Draw.Align.CENTER, accent, true);
                String line = join ? "You can switch sides later." : "Your reputation stays with each side.";
                Draw.fit(g, font, Component.literal(line), x + w / 2, top + 68, w - 16, 0.75f, 0.5f, Draw.Align.CENTER, THEME.muted, false);
                int[] b = buttonBox();
                SkyScreens.button(g, font, THEME, b[0], b[1], b[2], b[3], (join ? "Join the " : "Switch to the ") + plural(), true,
                        SkyScreens.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]));
            }
            case LOCKED -> {
                int top = y + REP_H + 6;
                int h = contentBottom() - top;
                SkyScreens.card(g, THEME, x, top, w, h, false, true);
                NpcCards.icon(g, new ItemStack(Items.IRON_BARS), x + w / 2 - 20, top + 14, 40, 2f);
                Draw.fit(g, font, Component.literal("Not yet"), x + w / 2, top + 60, w - 16, 1f, 0.6f, Draw.Align.CENTER, THEME.accent, true);
                Draw.fit(g, font, Component.literal(view.note()), x + w / 2, top + 75, w - 16, 0.75f, 0.4f, Draw.Align.CENTER, THEME.muted, false);
            }
        }
    }

    /** One quest card: icon, text, progress bar with its count, a reputation chip, and a check when it's done. */
    private void quest(GuiGraphicsExtractor g, FactionMenu.QuestRow row, int x, int y, int w, boolean over) {
        SkyScreens.card(g, THEME, x, y, w, QUEST_H, over && !row.done(), row.done());
        NpcCards.icon(g, row.icon(), x + 3, y + 2, 17, 0.9f);
        int textX = x + 24;
        Chip rep = new Chip("", new ItemStack(Items.NETHER_STAR), "+" + number(row.rep()) + " Rep", true);
        int chipW = SkyScreens.chipWidth(font, rep, 0.7f);
        int right = x + w - 6 - chipW - 6;
        int good = THEME.good;
        Draw.fit(g, font, Component.literal(row.text()), textX, y + 3, right - textX, 0.75f, 0.45f, Draw.Align.LEFT, row.done() ? good : THEME.text,
                false);
        int barW = Math.min(90, right - textX - 30);
        SkyScreens.bar(g, THEME, textX, y + 12, barW, 5, (float) Math.min(row.progress(), row.amount()) / Math.max(1, row.amount()),
                row.done() ? good : color());
        Draw.fit(g, font, Component.literal(row.done() ? "Done" : row.progress() + "/" + row.amount()), textX + barW + 4, y + 11, right - textX - barW - 4,
                0.62f, 0.4f, Draw.Align.LEFT, row.done() ? good : THEME.muted, false);
        SkyScreens.chip(g, font, THEME, x + w - 6 - chipW, y + (QUEST_H - 9) / 2, rep, 0.7f);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        FactionMenu.View view = menu.view();
        List<Component> lines = new ArrayList<>();
        if (hoveredQuest >= 0 && hoveredQuest < view.quests().size()) {
            FactionMenu.QuestRow row = view.quests().get(hoveredQuest);
            lines.add(Component.literal(row.text()).withColor(row.done() ? THEME.good : THEME.text));
            lines.add(Component.literal(row.where()).withColor(THEME.muted));
            lines.add(Component.empty());
            lines.add(Component.literal("Progress: " + row.progress() + "/" + row.amount()).withColor(THEME.text));
            lines.add(Component.literal("Reward: +" + number(row.rep()) + " Reputation").withColor(color()));
        } else if (hoverRep) {
            lines.add(Component.literal(plural() + " reputation").withColor(color()));
            for (int r = 0; r < RANKS.size(); r++) {
                lines.add(Component.literal((r <= rank() ? "✔ " : "• ") + RANKS.get(r) + "  " + number(RANK_REP[r])).withColor(
                        r <= rank() ? THEME.good : THEME.muted));
            }
        }
        if (!lines.isEmpty()) {
            Draw.queueTooltip(lines, THEME.accent);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        FactionMenu.State state = menu.view().state();
        int[] b = buttonBox();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && (state == FactionMenu.State.JOIN || state == FactionMenu.State.SWITCH)
                && SkyScreens.inside(event.x(), event.y(), b[0], b[1], b[2], b[3])) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FactionMenu.JOIN);
            SkyScreens.click();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
