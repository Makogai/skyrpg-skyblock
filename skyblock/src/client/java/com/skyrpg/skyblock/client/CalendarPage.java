package com.skyrpg.skyblock.client;

import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.time.CalendarEvents;
import com.skyrpg.skyblock.time.SkyBlockTime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Calendar and Events, as in Hypixel's SkyBlock Menu: today's SkyBlock date and time, the year's twelve months with
 * their festivals, and what's coming up next with a countdown.
 */
public final class CalendarPage implements MenuPage {
    private static final int HEADER_H = 34;
    private static final int GAP = 5;
    private static final int COLUMNS = 3;
    /** How far ahead "Coming up" looks: about four SkyBlock months. */
    private static final long LOOKAHEAD = 4 * SkyBlockTime.MONTH;
    private static final int UPCOMING = 9;

    @Override
    public Identifier id() {
        return SkyRpgSkyblock.id("calendar");
    }

    @Override
    public Component title() {
        return Component.literal("Calendar and Events");
    }

    @Override
    public Component subtitle() {
        return Component.literal("The SkyBlock year: 12 months of 31 days, a day every 20 minutes");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.CLOCK);
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        long now = System.currentTimeMillis();
        SkyBlockTime.Date date = SkyBlockTime.at(now);

        // Today.
        Draw.panel(g, ctx.x(), ctx.y(), ctx.width(), HEADER_H, Theme.CARD, Theme.CARD_BORDER);
        Draw.fit(g, font, Component.literal(date.dayText()), ctx.x() + 10, ctx.y() + 5, ctx.width() / 2, 1.5f, 1f,
                Draw.Align.LEFT, Theme.ACCENT, true);
        Draw.text(g, font, "Year " + date.year() + "   " + (date.isDay() ? "☀ " : "☽ ") + date.clock(),
                ctx.x() + 10, ctx.y() + 22, Theme.MUTED);
        long nextDay = date.dayStart() + SkyBlockTime.DAY;
        String tomorrow = "Next day in " + SkyBlockTime.countdown(nextDay - now);
        Draw.text(g, font, tomorrow, ctx.x() + ctx.width() - 10 - font.width(tomorrow), ctx.y() + 22, Theme.DIM);

        int top = ctx.y() + HEADER_H + GAP;
        int height = ctx.height() - HEADER_H - GAP;
        boolean wide = ctx.width() >= 280;
        int gridW = wide ? ctx.width() * 11 / 20 : ctx.width();
        int gridH = wide ? height : height / 2;
        drawMonths(ctx, date, ctx.x(), top, gridW, gridH);
        int listX = wide ? ctx.x() + gridW + GAP : ctx.x();
        int listY = wide ? top : top + gridH + GAP;
        drawUpcoming(ctx, now, listX, listY, wide ? ctx.width() - gridW - GAP : ctx.width(), wide ? height : height - gridH - GAP);
    }

    private void drawMonths(PageContext ctx, SkyBlockTime.Date date, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int rows = (SkyBlockTime.MONTHS.size() + COLUMNS - 1) / COLUMNS;
        int cellW = (w - GAP * (COLUMNS - 1)) / COLUMNS;
        int cellH = (h - GAP * (rows - 1)) / rows;
        for (int month = 0; month < SkyBlockTime.MONTHS.size(); month++) {
            int cx = x + month % COLUMNS * (cellW + GAP);
            int cy = y + month / COLUMNS * (cellH + GAP);
            boolean current = month == date.month();
            boolean hovered = ctx.hovered(cx, cy, cellW, cellH);
            Draw.panel(g, cx, cy, cellW, cellH, hovered ? Theme.CARD_HOVER : Theme.CARD, current ? Theme.ACCENT : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal(SkyBlockTime.MONTHS.get(month)), cx + 5, cy + 5, cellW - 10, 1f, 0.7f,
                    Draw.Align.LEFT, current ? Theme.ACCENT : Theme.TEXT, false);
            List<CalendarEvents.Event> events = CalendarEvents.inMonth(month);
            // One dot per event in the month; the names are in the tooltip and under "Coming up".
            int dotX = cx + 5;
            int dotY = cy + Math.min(cellH - 10, 17);
            for (CalendarEvents.Event event : events) {
                g.fill(dotX, dotY, dotX + 4, dotY + 4, event.color());
                dotX += 6;
            }
            if (current) {
                Draw.bar(g, cx + 5, cy + cellH - 6, cellW - 10, 3, date.day() / (float) SkyBlockTime.DAYS_PER_MONTH, Theme.ACCENT);
            }
            if (hovered && !events.isEmpty()) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(SkyBlockTime.MONTHS.get(month)).withColor(Theme.ACCENT));
                for (CalendarEvents.Event event : events) {
                    tip.add(Component.literal(event.name()).withColor(event.color()).append(Component.literal("  " + span(event)).withColor(Theme.DIM)));
                    tip.add(Component.literal(event.description()).withColor(Theme.MUTED));
                }
                ctx.tooltip(tip, Theme.ACCENT);
            }
        }
    }

    private void drawUpcoming(PageContext ctx, long now, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        Draw.text(g, font, "Coming up", x + 8, y + 6, Theme.TEXT);
        int ly = y + 20;
        int shown = 0;
        java.util.Set<String> repeating = new java.util.HashSet<>();
        for (CalendarEvents.Occurrence occurrence : CalendarEvents.upcoming(now, LOOKAHEAD)) {
            CalendarEvents.Event event = occurrence.event();
            // Events that come round every few days show only their next time.
            if (event.everyDays() > 0 && !repeating.add(event.name())) {
                continue;
            }
            if (shown++ >= UPCOMING || ly + 20 > y + h) {
                break;
            }
            g.fill(x + 8, ly + 3, x + 11, ly + 6, event.color());
            Draw.fitText(g, font, event.name(), x + 15, ly, w - 23, Theme.TEXT);
            boolean active = occurrence.active(now);
            String when = active ? "Now! Ends in " + SkyBlockTime.countdown(occurrence.end() - now)
                    : SkyBlockTime.at(occurrence.start()).dayText() + " · in " + SkyBlockTime.countdown(occurrence.start() - now);
            Draw.fitText(g, font, when, x + 15, ly + 10, w - 23, active ? Theme.GREEN : Theme.DIM);
            ly += 23;
        }
    }

    private static String span(CalendarEvents.Event event) {
        String start = SkyBlockTime.ordinal(event.day());
        if (event.days() <= 1) {
            return start;
        }
        int endDay = (event.day() - 1 + event.days() - 1) % SkyBlockTime.DAYS_PER_MONTH + 1;
        int endMonth = event.month() + (event.day() - 1 + event.days() - 1) / SkyBlockTime.DAYS_PER_MONTH;
        return start + " to " + (endMonth != event.month() ? SkyBlockTime.MONTHS.get(endMonth % 12) + " " : "") + SkyBlockTime.ordinal(endDay);
    }
}
