package com.skyrpg.skyblock.client.screen;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.ui.Chip;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** The pieces SkyBlock's item screens are drawn with: the window, its title, slot wells. Never vanilla's textures. */
public final class SkyScreens {
    public static final int WELL = 0xFF0B0E14;
    public static final int WELL_EDGE = 0xFF262D3A;
    private static final Identifier[] ARMOR_GHOSTS = {
            Identifier.withDefaultNamespace("container/slot/helmet"), Identifier.withDefaultNamespace("container/slot/chestplate"),
            Identifier.withDefaultNamespace("container/slot/leggings"), Identifier.withDefaultNamespace("container/slot/boots")};

    private SkyScreens() {
    }

    /** The window: SkyRPG's dark panel with a gold rule along the top, and a title with a subtitle on its right. */
    public static void window(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component title, Component subtitle) {
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x55000000);
        Draw.panel(g, x, y, w, h, Theme.WINDOW, Theme.BORDER);
        g.fill(x, y, x + w, y + 1, Theme.ACCENT);
        Draw.fit(g, font, title, x + 8, y + 7, w / 2, 1.25f, 1f, Draw.Align.LEFT, Theme.TEXT, true);
        Draw.fit(g, font, subtitle, x + w - 8, y + 10, w / 2 - 12, 1f, 0.8f, Draw.Align.RIGHT, Theme.MUTED, false);
    }

    /**
     * A themed window: the theme's background and frame, the title (with a subtitle on its right) and the header divider
     * under them. Content starts at {@code y + }{@link #contentTop}. The default theme is the plain window.
     */
    public static void window(GuiGraphicsExtractor g, Font font, ScreenTheme theme, int x, int y, int w, int h, Component title,
                              Component subtitle) {
        if (!theme.sprited()) {
            window(g, font, x, y, w, h, title, subtitle);
            return;
        }
        g.fill(x + 4, y + 5, x + w + 4, y + h + 5, 0x66000000);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, theme.sprite("background"), x + 4, y + 4, w - 8, h - 8);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, theme.sprite("frame"), x, y, w, h);
        Draw.fit(g, font, title, x + 14, y + 12, w / 2, 1.25f, 1f, Draw.Align.LEFT, theme.text, true);
        Draw.fit(g, font, subtitle, x + w - 14, y + 14, w / 2 - 20, 0.85f, 0.6f, Draw.Align.RIGHT, theme.muted, false);
        divider(g, theme, x + 10, y + 26, w - 20);
    }

    /** Where content starts below a themed window's top. */
    public static int contentTop(ScreenTheme theme) {
        return theme.sprited() ? 36 : 26;
    }

    /** The theme's header divider across {@code w} (a rope or rule with its end pieces). */
    public static void divider(GuiGraphicsExtractor g, ScreenTheme theme, int x, int y, int w) {
        if (!theme.sprited()) {
            g.fill(x, y + 2, x + w, y + 3, Theme.BORDER);
            return;
        }
        g.blitSprite(RenderPipelines.GUI_TEXTURED, theme.sprite("divider"), x + 9, y, w - 18, 6);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, theme.sprite("divider_left"), x, y - 2, 11, 9);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, theme.sprite("divider_right"), x + w - 11, y - 2, 11, 9);
    }

    /** A card: normal, hovered (the theme's highlight edge) or locked (darker). */
    public static void card(GuiGraphicsExtractor g, ScreenTheme theme, int x, int y, int w, int h, boolean hover, boolean locked) {
        if (!theme.sprited()) {
            Draw.panel(g, x, y, w, h, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? theme.accent : Theme.CARD_BORDER);
            return;
        }
        g.blitSprite(RenderPipelines.GUI_TEXTURED, theme.sprite(locked ? "card_locked" : hover ? "card_hover" : "card"), x, y, w, h);
    }

    /** A button with its label; returns nothing, check clicks with {@link #inside}. */
    public static void button(GuiGraphicsExtractor g, Font font, ScreenTheme theme, int x, int y, int w, int h, String label, boolean enabled,
                              boolean hover) {
        if (!theme.sprited()) {
            Draw.panel(g, x, y, w, h, hover && enabled ? Theme.CARD_HOVER : Theme.CARD, enabled ? Theme.GREEN : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal(label), x + w / 2, y + (h - 6) / 2, w - 4, 0.7f, 0.45f, Draw.Align.CENTER,
                    enabled ? Theme.GREEN : Theme.DIM, false);
            return;
        }
        g.blitSprite(RenderPipelines.GUI_TEXTURED, theme.sprite(!enabled ? "button_disabled" : hover ? "button_hover" : "button"), x, y, w, h);
        Draw.fit(g, font, Component.literal(label), x + w / 2, y + (h - 6) / 2 + 1, w - 6, 0.75f, 0.45f, Draw.Align.CENTER,
                enabled ? 0xFFFFFFFF : 0xFFC9C9C9, true);
    }

    /** A tab with its label. */
    public static void tab(GuiGraphicsExtractor g, Font font, ScreenTheme theme, int x, int y, int w, int h, String label, boolean active,
                           boolean hover) {
        if (!theme.sprited()) {
            Draw.panel(g, x, y, w, h, active || hover ? Theme.CARD_HOVER : Theme.CARD, active ? theme.accent : Theme.CARD_BORDER);
        } else {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, theme.sprite(active ? "tab_active" : "tab"), x, y, w, h);
        }
        Draw.fit(g, font, Component.literal(label), x + w / 2, y + (h - 6) / 2, w - 6, 0.75f, 0.45f, Draw.Align.CENTER,
                active ? theme.accent : hover ? theme.text : theme.muted, active);
    }

    /** A progress bar in the theme's colours. */
    public static void bar(GuiGraphicsExtractor g, ScreenTheme theme, int x, int y, int w, int h, float progress, int color) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, theme.track);
        int fill = Math.round((w - 2) * Math.max(0, Math.min(1, progress)));
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + h - 1, color);
            g.fill(x + 1, y + 1, x + 1 + fill, y + 2, Draw.lighten(color, 0.35f));
        }
    }

    /** A chip: its icon then its text (red when you haven't enough); returns its width. */
    public static int chip(GuiGraphicsExtractor g, Font font, ScreenTheme theme, int x, int y, Chip chip, float scale) {
        int icon = 9;
        if (!chip.currency().isEmpty()) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, SkyRpgSkyblock.id("currency/" + chip.currency()), x, y, icon, icon);
        } else if (!chip.item().isEmpty()) {
            Draw.item(g, chip.item(), x - 1, y - 1, 0.65f);
            icon = 10;
        }
        Component text = Component.literal(chip.text());
        int width = Math.round(font.width(text) * scale);
        Draw.scaledText(g, font, text, x + icon + 2, y + (int) ((9 - 8 * scale) / 2) + 1, scale, chip.enough() ? theme.text : Theme.RED);
        return icon + 2 + width;
    }

    /** The width {@link #chip} will take. */
    public static int chipWidth(Font font, Chip chip, float scale) {
        return (chip.currency().isEmpty() && !chip.item().isEmpty() ? 10 : 9) + 2 + Math.round(font.width(chip.text()) * scale);
    }

    /** Chips in a row with gaps; returns the x after the last. */
    public static int chips(GuiGraphicsExtractor g, Font font, ScreenTheme theme, int x, int y, java.util.List<Chip> chips, float scale, int maxX) {
        for (Chip chip : chips) {
            if (x + chipWidth(font, chip, scale) > maxX) {
                Draw.scaledText(g, font, Component.literal("…"), x, y + 1, scale, theme.muted);
                break;
            }
            x += chip(g, font, theme, x, y, chip, scale) + 6;
        }
        return x;
    }

    /**
     * An NPC's live model in a box, turning to the mouse, without its floating name or role line (Mannequins' role
     * line is hidden just while it's drawn).
     */
    public static void npcPortrait(GuiGraphicsExtractor g, net.minecraft.world.entity.LivingEntity entity, int x0, int y0, int x1, int y1, int size,
                                   int mouseX, int mouseY) {
        // Name tags only draw while the HUD shows, and the model's picture is captured right here.
        var hud = Minecraft.getInstance().gui.hud;
        boolean shown = !hud.isHidden();
        if (shown) {
            hud.toggle();
        }
        try {
            portrait(g, entity, x0, y0, x1, y1, size, mouseX, mouseY);
        } finally {
            if (shown) {
                hud.toggle();
            }
        }
    }

    private static void portrait(GuiGraphicsExtractor g, net.minecraft.world.entity.LivingEntity entity, int x0, int y0, int x1, int y1, int size,
                                 int mouseX, int mouseY) {
        if (entity instanceof net.minecraft.world.entity.decoration.Mannequin mannequin) {
            var data = com.skyrpg.skyblock.mixin.MannequinRoleAccess.skyrpg$roleData();
            java.util.Optional<Component> role = mannequin.getEntityData().get(data);
            mannequin.getEntityData().set(data, java.util.Optional.empty());
            Draw.entityFollowsMouse(g, x0, y0, x1, y1, size, mouseX, mouseY, entity);
            mannequin.getEntityData().set(data, role);
        } else {
            Draw.entityFollowsMouse(g, x0, y0, x1, y1, size, mouseX, mouseY, entity);
        }
    }

    /** A currency's name for labels and tooltips. */
    public static String currencyName(String currency) {
        return switch (currency) {
            case "coins" -> "Coins";
            case "copper" -> "Copper";
            case "pests" -> "Pests";
            case "tickets" -> "Tickets";
            case "medal_gold" -> "Gold";
            case "medal_silver" -> "Silver";
            case "medal_bronze" -> "Bronze";
            default -> currency;
        };
    }

    /** A big item icon (scaled), e.g. on an offer card. */
    public static void icon(GuiGraphicsExtractor g, ItemStack stack, int x, int y, float scale) {
        Draw.item(g, stack, x, y, scale);
    }

    /** An 18 by 18 well for a slot whose item sits at (x + 1, y + 1). */
    public static void well(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, WELL_EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, WELL);
    }

    /** A well with the faint outline of the armour piece that goes there (0 helmet to 3 boots), when it's empty. */
    public static void armorWell(GuiGraphicsExtractor g, int x, int y, int piece, boolean empty) {
        well(g, x, y);
        if (empty) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_GHOSTS[piece], x + 1, y + 1, 16, 16, 0x55FFFFFF);
        }
    }

    /** Wells under every slot of the player's inventory part of a menu (slots from {@code first} on). */
    public static void inventoryWells(GuiGraphicsExtractor g, AbstractContainerMenu menu, int first, int left, int top) {
        for (int i = first; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            well(g, left + slot.x - 1, top + slot.y - 1);
        }
    }

    /** A small label over the inventory. */
    public static void label(GuiGraphicsExtractor g, Font font, String text, int x, int y) {
        Draw.text(g, font, text, x, y, Theme.DIM);
    }

    public static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
