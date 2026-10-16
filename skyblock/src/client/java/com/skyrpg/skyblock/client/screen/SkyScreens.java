package com.skyrpg.skyblock.client.screen;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** The pieces SkyBlock's item screens are drawn with: the window, its title, slot wells. Never vanilla's textures. */
final class SkyScreens {
    static final int WELL = 0xFF0B0E14;
    static final int WELL_EDGE = 0xFF262D3A;
    private static final Identifier[] ARMOR_GHOSTS = {
            Identifier.withDefaultNamespace("container/slot/helmet"), Identifier.withDefaultNamespace("container/slot/chestplate"),
            Identifier.withDefaultNamespace("container/slot/leggings"), Identifier.withDefaultNamespace("container/slot/boots")};

    private SkyScreens() {
    }

    /** The window: SkyRPG's dark panel with a gold rule along the top, and a title with a subtitle on its right. */
    static void window(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component title, Component subtitle) {
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x55000000);
        Draw.panel(g, x, y, w, h, Theme.WINDOW, Theme.BORDER);
        g.fill(x, y, x + w, y + 1, Theme.ACCENT);
        Draw.fit(g, font, title, x + 8, y + 7, w / 2, 1.25f, 1f, Draw.Align.LEFT, Theme.TEXT, true);
        Draw.fit(g, font, subtitle, x + w - 8, y + 10, w / 2 - 12, 1f, 0.8f, Draw.Align.RIGHT, Theme.MUTED, false);
    }

    /** An 18 by 18 well for a slot whose item sits at (x + 1, y + 1). */
    static void well(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, WELL_EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, WELL);
    }

    /** A well with the faint outline of the armour piece that goes there (0 helmet to 3 boots), when it's empty. */
    static void armorWell(GuiGraphicsExtractor g, int x, int y, int piece, boolean empty) {
        well(g, x, y);
        if (empty) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_GHOSTS[piece], x + 1, y + 1, 16, 16, 0x55FFFFFF);
        }
    }

    /** Wells under every slot of the player's inventory part of a menu (slots from {@code first} on). */
    static void inventoryWells(GuiGraphicsExtractor g, AbstractContainerMenu menu, int first, int left, int top) {
        for (int i = first; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            well(g, left + slot.x - 1, top + slot.y - 1);
        }
    }

    /** A small label over the inventory. */
    static void label(GuiGraphicsExtractor g, Font font, String text, int x, int y) {
        Draw.text(g, font, text, x, y, Theme.DIM);
    }

    static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
