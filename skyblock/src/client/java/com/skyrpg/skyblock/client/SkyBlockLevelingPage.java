package com.skyrpg.skyblock.client;

import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.progress.SkyBlockLevels;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Notice;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * SkyBlock Leveling: your level in its prefix colour with the bar to the next one, what your levels give you, what the
 * next level brings, the features levels open up, the prefix colour ladder, and where SkyBlock XP comes from.
 */
public final class SkyBlockLevelingPage implements MenuPage {
    private static final int GAP = 6;
    private static final int HERO_W = 128;

    /** Where SkyBlock XP comes from today, and how much. */
    private static final List<String[]> SOURCES = List.of(
            new String[] {"Skill levels", "5 to 30 each"},
            new String[] {"Collection tiers", "4 each"},
            new String[] {"Unique minions", "1 to 24 each"},
            new String[] {"Fairy Souls, slayers, bestiary...", "coming"});

    @Override
    public Identifier id() {
        return SkyRpgSkyblock.id("leveling");
    }

    @Override
    public Component title() {
        return Component.literal("SkyBlock Leveling");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Your SkyBlock Level, what it gives you and how to raise it");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.EXPERIENCE_BOTTLE);
    }

    @Override
    public int order() {
        return 983;
    }

    private static int rgb(ChatFormatting format) {
        return 0xFF000000 | Notice.rgb(format);
    }

    /** The hero's lines, or the right column with every SkyBlock XP source. */
    @Override
    public int scrollHeight(int width) {
        int features = 13 + SkyBlockLevels.UNLOCKS.size() * 8 + 2;
        int sources = 13 + (SOURCES.size() + 1) / 2 * 9 + 4;
        return Math.max(170, features + 4 + 26 + 4 + sources);
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int xp = SkyBlockXp.xp(ctx.player());
        int level = xp / SkyBlockXp.PER_LEVEL;
        int into = xp % SkyBlockXp.PER_LEVEL;
        int tier = rgb(SkyBlockLevels.prefixColor(level));
        // Below 40 the prefix is grey: the big number reads better in the text colour, with the grey glow.
        int number = level < 40 ? Theme.TEXT : tier;
        int heroW = Math.min(HERO_W, ctx.width() / 3);
        hero(ctx, g, font, ctx.x(), ctx.y(), heroW, ctx.height(), level, into, xp, tier, number);
        right(ctx, g, font, ctx.x() + heroW + GAP, ctx.y(), ctx.width() - heroW - GAP, ctx.height(), level);
    }

    /** The level, big, the bar to the next, what your levels give and what the next one adds. */
    private void hero(PageContext ctx, GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int level, int into, int xp, int tier,
                      int number) {
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 70, Draw.withAlpha(tier, 0.28f), 0x00000000);
        float pulse = 0.85f + 0.15f * Mth.sin(Util.getMillis() / 400f);
        int cx = x + w / 2;
        int inner = w - 12;
        Draw.fit(g, font, Component.literal("SkyBlock Level"), cx, y + 6, inner, 0.85f, 0.6f, Draw.Align.CENTER, Theme.MUTED, false);
        Draw.fit(g, font, Component.literal(String.valueOf(level)), cx, y + 18, inner, 2.5f, 1.25f, Draw.Align.CENTER,
                Draw.withAlpha(number, pulse), true);
        Component badge = Component.literal("[").withColor(0xFF555555).append(Component.literal(String.valueOf(level)).withColor(tier))
                .append(Component.literal("] ").withColor(0xFF555555)).append(Component.literal(ctx.player().getPlainTextName()).withColor(Theme.TEXT));
        Draw.fit(g, font, badge, cx, y + 42, inner, 0.85f, 0.5f, Draw.Align.CENTER, Theme.TEXT, false);
        int barX = x + 6;
        Draw.bar(g, barX, y + 53, inner, 5, into / (float) SkyBlockXp.PER_LEVEL, level < 40 ? Theme.AQUA : tier);
        Draw.fit(g, font, Component.literal(into + "/" + SkyBlockXp.PER_LEVEL + " XP to level " + (level + 1)), cx, y + 61, inner, 0.75f, 0.5f,
                Draw.Align.CENTER, Theme.MUTED, false);
        Draw.fit(g, font, Component.literal(xp + " SkyBlock XP in all"), cx, y + 69, inner, 0.7f, 0.5f, Draw.Align.CENTER, Theme.DIM, false);

        StatType health = StatTypes.get("health");
        StatType strength = StatTypes.get("strength");
        int ly = y + 82;
        Draw.fit(g, font, Component.literal("Your levels give"), barX, ly, inner, 0.75f, 0.5f, Draw.Align.LEFT, Theme.DIM, false);
        ly += 9;
        if (health != null) {
            line(g, font, "+" + SkyBlockLevels.health(level) + " " + health.symbol() + " Health", barX, ly, inner, health.color());
            ly += 9;
        }
        if (strength != null) {
            line(g, font, "+" + SkyBlockLevels.strength(level) + " " + strength.symbol() + " Strength", barX, ly, inner, strength.color());
            ly += 9;
        }
        ly += 4;
        Draw.fit(g, font, Component.literal("Level " + (level + 1) + " adds"), barX, ly, inner, 0.75f, 0.5f, Draw.Align.LEFT, Theme.DIM, false);
        ly += 9;
        if (health != null) {
            line(g, font, "+" + SkyBlockLevels.HEALTH_PER_LEVEL + " " + health.symbol() + " Health", barX, ly, inner, health.color());
            ly += 9;
        }
        if ((level + 1) % SkyBlockLevels.LEVELS_PER_STRENGTH == 0 && strength != null) {
            line(g, font, "+1 " + strength.symbol() + " Strength", barX, ly, inner, strength.color());
            ly += 9;
        }
        for (SkyBlockLevels.Unlock unlock : SkyBlockLevels.UNLOCKS) {
            if (unlock.level() == level + 1 && ly < y + h - 9) {
                line(g, font, unlock.name(), barX, ly, inner, rgb(unlock.color()));
                ly += 9;
            }
        }
    }

    private static void line(GuiGraphicsExtractor g, Font font, String text, int x, int y, int w, int color) {
        Draw.fit(g, font, Component.literal(text), x, y, w, 0.85f, 0.5f, Draw.Align.LEFT, color, false);
    }

    /** The features levels open, the prefix colour ladder and where SkyBlock XP comes from. */
    private void right(PageContext ctx, GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, int level) {
        int row = 9;
        int featureRow = 8;
        int featuresH = 13 + SkyBlockLevels.UNLOCKS.size() * featureRow + 2;
        Draw.panel(g, x, y, w, featuresH, Theme.CARD, Theme.CARD_BORDER);
        Draw.fit(g, font, Component.literal("Features"), x + 6, y + 4, w - 12, 0.85f, 0.6f, Draw.Align.LEFT, Theme.ACCENT, false);
        int uy = y + 14;
        for (SkyBlockLevels.Unlock unlock : SkyBlockLevels.UNLOCKS) {
            boolean done = level >= unlock.level();
            String at = "Level " + unlock.level();
            int atW = Math.round(font.width(at) * 0.8f);
            Draw.fit(g, font, Component.literal(done ? "✔" : "🔒"), x + 6, uy, 10, 0.8f, 0.5f, Draw.Align.LEFT, done ? Theme.GREEN : Theme.DIM, false);
            Draw.fit(g, font, Component.literal(unlock.name()), x + 16, uy, w - 26 - atW, 0.8f, 0.5f, Draw.Align.LEFT,
                    done ? rgb(unlock.color()) : Theme.MUTED, false);
            Draw.fit(g, font, Component.literal(at), x + w - 6, uy, atW + 2, 0.8f, 0.5f, Draw.Align.RIGHT, done ? Theme.GREEN : Theme.DIM, false);
            uy += featureRow;
        }

        // Prefix colours: a chip per 40 levels, yours outlined.
        int py = y + featuresH + 4;
        int prefixH = 26;
        Draw.panel(g, x, py, w, prefixH, Theme.CARD, Theme.CARD_BORDER);
        Draw.fit(g, font, Component.literal("Prefix colours"), x + 6, py + 4, w - 12, 0.85f, 0.6f, Draw.Align.LEFT, Theme.ACCENT, false);
        int chips = 13;
        int chipW = (w - 12) / chips;
        for (int i = 0; i < chips; i++) {
            int at = i * 40;
            int cx = x + 6 + i * chipW;
            int color = rgb(SkyBlockLevels.prefixColor(at));
            g.fill(cx, py + 14, cx + chipW - 1, py + 24, Draw.withAlpha(color, level >= at ? 0.4f : 0.12f));
            if (level / 40 == i) {
                Draw.outline(g, cx, py + 14, chipW - 1, 10, color == 0xFFAAAAAA ? Theme.TEXT : color);
            }
            Draw.fit(g, font, Component.literal(String.valueOf(at)), cx + chipW / 2, py + 16, chipW - 2, 0.65f, 0.4f, Draw.Align.CENTER,
                    level >= at ? color : Draw.withAlpha(color, 0.5f), false);
            if (ctx.hovered(cx, py + 14, chipW - 1, 10)) {
                ctx.tooltip(List.of(Component.literal(SkyBlockLevels.prefixName(at) + " prefix").withColor(color),
                        Component.literal("From SkyBlock Level " + at).withColor(Theme.MUTED)), color);
            }
        }

        // Where SkyBlock XP comes from.
        int sy = py + prefixH + 4;
        int sh = y + h - sy;
        if (sh < 22) {
            return;
        }
        Draw.panel(g, x, sy, w, sh, Theme.CARD, Theme.CARD_BORDER);
        Draw.fit(g, font, Component.literal("Earning SkyBlock XP"), x + 6, sy + 4, w - 12, 0.85f, 0.6f, Draw.Align.LEFT, Theme.ACCENT, false);
        // Two columns, so they fit under the rest.
        int colW = (w - 12) / 2;
        for (int i = 0; i < SOURCES.size(); i++) {
            String[] source = SOURCES.get(i);
            int lx = x + 6 + (i % 2) * colW;
            int ly = sy + 13 + (i / 2) * row;
            if (ly > sy + sh - row) {
                break;
            }
            boolean coming = source[1].equals("coming");
            int amountW = Math.round(font.width(source[1]) * 0.7f);
            Draw.fit(g, font, Component.literal("• " + source[0]), lx, ly, colW - amountW - 8, 0.75f, 0.45f, Draw.Align.LEFT,
                    coming ? Theme.DIM : Theme.TEXT, false);
            Draw.fit(g, font, Component.literal(source[1]), lx + colW - 4, ly, amountW + 2, 0.7f, 0.45f, Draw.Align.RIGHT,
                    coming ? Theme.DIM : Theme.AQUA, false);
        }
    }
}
