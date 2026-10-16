package com.skyrpg.skyblock.client;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Purse;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.time.SkyBlockTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * Hypixel's SkyBlock scoreboard, in SkyRPG's style: the real date, the SkyBlock date and time, where you are and
 * your purse.
 */
public final class SidebarWidget implements HudWidget {
    private static final int PAD = 6;
    private static final int LINE = 10;
    private static final int MIN_WIDTH = 102;
    private static final DateTimeFormatter REAL_DATE = DateTimeFormatter.ofPattern("MM/dd/yy");

    /** Where you are, as the server last said ("Your Island"). */
    private static String area = "";

    static void setArea(String name) {
        area = name;
    }

    @Override
    public Identifier id() {
        return SkyRpgSkyblock.id("sidebar");
    }

    @Override
    public Component name() {
        return Component.literal("SkyBlock sidebar (date, time, location, purse)");
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.MIDDLE_RIGHT;
    }

    @Override
    public int defaultX() {
        return -4;
    }

    @Override
    public int defaultY() {
        return 0;
    }

    private static List<Component> lines(boolean editing) {
        List<Component> lines = new ArrayList<>();
        SkyBlockTime.Date date = SkyBlockTime.now();
        lines.add(Component.literal(LocalDate.now().format(REAL_DATE)).withStyle(ChatFormatting.GRAY));
        lines.add(Component.empty());
        lines.add(Component.literal(date.dayText()).withStyle(ChatFormatting.WHITE));
        lines.add(Component.literal(date.isDay() ? "☀ " : "☽ ").withStyle(date.isDay() ? ChatFormatting.YELLOW : ChatFormatting.AQUA)
                .append(Component.literal(date.clock()).withStyle(ChatFormatting.GRAY)));
        String where = area.isEmpty() && editing ? "Your Island" : area;
        if (!where.isEmpty()) {
            lines.add(Component.literal("⏣ ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(where).withStyle(where.equals("Your Island") ? ChatFormatting.GREEN : ChatFormatting.AQUA)));
        }
        lines.add(Component.empty());
        var player = Minecraft.getInstance().player;
        long purse = player == null ? 0 : Purse.get(player);
        lines.add(Component.literal("Purse: ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal(Msg.number(purse)).withStyle(ChatFormatting.GOLD)));
        return lines;
    }

    private static Component title() {
        return Component.literal("SKYBLOCK").withStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW).withBold(true));
    }

    @Override
    public int width(Font font) {
        int widest = font.width(title());
        for (Component line : lines(true)) {
            widest = Math.max(widest, font.width(line));
        }
        return Math.max(MIN_WIDTH, PAD * 2 + widest);
    }

    @Override
    public int height(Font font) {
        return PAD * 2 + 12 + lines(true).size() * LINE - 2;
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        int width = width(font);
        Draw.panel(g, 0, 0, width, height(font), 0xE0121620, Theme.BORDER);
        Component title = title();
        Draw.text(g, font, title, (width - font.width(title)) / 2, PAD, Theme.TEXT);
        int y = PAD + 12;
        for (Component line : lines(editing)) {
            Draw.text(g, font, line, PAD, y, Theme.TEXT);
            y += LINE;
        }
    }
}
