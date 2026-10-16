package com.skyrpg.skyblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.profile.ProfileListPayload;
import com.skyrpg.skyblock.profile.Profiles;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Profile Management, as in Hypixel's SkyBlock Menu: your profiles (up to {@link Profiles#MAX}), which one you're
 * playing, and buttons to switch to another or start a new one. Both ask for a second click, since they reconnect you.
 */
public final class ProfilesPage implements MenuPage {
    private static final int ROW_H = 40;
    private static final int GAP = 5;
    private static final int BUTTON_W = 74;
    private static final int BUTTON_H = 18;
    /** How long a "Confirm?" button waits for the second click. */
    private static final long CONFIRM_MILLIS = 4000;

    private static List<ProfileListPayload.Entry> profiles = List.of();

    /** Buttons drawn this frame: x, y, w, h, and the profile index (or -1 for "new profile"). */
    private final List<int[]> buttons = new ArrayList<>();
    private int armed = Integer.MIN_VALUE;
    private long armedAt;

    static void set(ProfileListPayload payload) {
        profiles = List.copyOf(payload.profiles());
    }

    static List<ProfileListPayload.Entry> profiles() {
        return profiles;
    }

    @Override
    public Identifier id() {
        return SkyRpgSkyblock.id("profiles");
    }

    @Override
    public Component title() {
        return Component.literal("Profile Management");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Each profile is a fresh start: its own island, inventory, skills and coins");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.NAME_TAG);
    }

    @Override
    public int order() {
        return 990;
    }

    @Override
    public void opened() {
        armed = Integer.MIN_VALUE;
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        buttons.clear();
        if (armed != Integer.MIN_VALUE && Util.getMillis() - armedAt > CONFIRM_MILLIS) {
            armed = Integer.MIN_VALUE;
        }
        SimpleDateFormat date = new SimpleDateFormat("MMM d, yyyy");
        int y = ctx.y();
        for (int i = 0; i < profiles.size(); i++) {
            ProfileListPayload.Entry entry = profiles.get(i);
            boolean hovered = ctx.hovered(ctx.x(), y, ctx.width(), ROW_H);
            Draw.panel(g, ctx.x(), y, ctx.width(), ROW_H, hovered ? Theme.CARD_HOVER : Theme.CARD, entry.playing() ? Theme.GREEN : Theme.CARD_BORDER);
            g.fill(ctx.x() + 1, y + 5, ctx.x() + 3, y + ROW_H - 5, entry.playing() ? Theme.GREEN : Theme.ACCENT);
            int textX = ctx.x() + 12;
            Draw.text(g, font, entry.name(), textX, y + 7, entry.playing() ? Theme.GREEN : Theme.ACCENT);
            String kind = entry.members().size() > 1
                    ? "Co-op with " + String.join(", ", entry.members().subList(1, entry.members().size()))
                    : "Solo";
            Draw.fitText(g, font, kind, textX + font.width(entry.name()) + 8, y + 7, ctx.width() - BUTTON_W - 40 - font.width(entry.name()), Theme.MUTED);
            Draw.fitText(g, font, "Started " + date.format(new Date(entry.created())), textX, y + 22, ctx.width() - BUTTON_W - 30, Theme.DIM);
            int bx = ctx.x() + ctx.width() - BUTTON_W - 10;
            int by = y + (ROW_H - BUTTON_H) / 2;
            if (entry.playing()) {
                Draw.text(g, font, "Playing", bx + (BUTTON_W - font.width("Playing")) / 2, by + 5, Theme.GREEN);
            } else {
                button(ctx, bx, by, armed == i ? "Confirm?" : "Switch", armed == i ? Theme.AQUA : Theme.ACCENT, i);
            }
            y += ROW_H + GAP;
        }
        if (profiles.size() < Profiles.MAX) {
            boolean hovered = ctx.hovered(ctx.x(), y, ctx.width(), ROW_H);
            Draw.panel(g, ctx.x(), y, ctx.width(), ROW_H, hovered ? Theme.CARD_HOVER : Theme.CARD, Theme.CARD_BORDER);
            Draw.text(g, font, "+ New profile", ctx.x() + 12, y + 7, Theme.TEXT);
            Draw.fitText(g, font, "A new island and a clean start. Your other profiles stay as they are.", ctx.x() + 12, y + 22,
                    ctx.width() - BUTTON_W - 30, Theme.DIM);
            button(ctx, ctx.x() + ctx.width() - BUTTON_W - 10, y + (ROW_H - BUTTON_H) / 2, armed == -1 ? "Confirm?" : "Create",
                    armed == -1 ? Theme.AQUA : Theme.GREEN, -1);
            y += ROW_H + GAP;
        }
        Draw.fitText(g, font, profiles.size() + " of " + Profiles.MAX + " profiles. Switching reconnects you for a moment.",
                ctx.x(), y + 4, ctx.width(), Theme.DIM);
    }

    private void button(PageContext ctx, int x, int y, String label, int color, int index) {
        boolean hovered = ctx.hovered(x, y, BUTTON_W, BUTTON_H);
        Draw.panel(ctx.graphics(), x, y, BUTTON_W, BUTTON_H, hovered ? Theme.CARD_HOVER : Theme.CARD, hovered ? color : Theme.CARD_BORDER);
        Draw.text(ctx.graphics(), ctx.font(), label, x + (BUTTON_W - ctx.font().width(label)) / 2, y + 5, color);
        buttons.add(new int[] {x, y, BUTTON_W, BUTTON_H, index});
    }

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (int[] b : buttons) {
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) {
                int index = b[4];
                if (armed != index) {
                    armed = index;
                    armedAt = Util.getMillis();
                    return true;
                }
                armed = Integer.MIN_VALUE;
                var connection = Minecraft.getInstance().getConnection();
                if (connection != null) {
                    connection.sendCommand(index < 0 ? "profiles create" : "profiles switch " + profiles.get(index).name());
                }
                Minecraft.getInstance().gui.setScreen(null);
                return true;
            }
        }
        return false;
    }
}
