package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.mining.HotmMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Heart of the Mountain tree, Hypixel's way up: tier 1 at the bottom, tier 10 at the top, each perk an icon on its
 * grid spot (coal: locked, emerald: unlocked, diamond: maxed; blocks for pickaxe abilities; redstone when switched off).
 * Hover a perk for what it does and costs; click to unlock or upgrade it (shift: ten levels), right-click to switch it
 * off or on. The header has your HotM tier and XP, the side your tokens and powder.
 */
public final class HotmScreen extends AbstractContainerScreen<HotmMenu> {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 234;
    private static final int CELL = 18;
    private static final int GRID_X = 30;
    private static final int GRID_Y = 48;
    private static final int PURPLE = 0xFFAA55FF;

    public HotmScreen(HotmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int cellX(int column) {
        return leftPos + GRID_X + column * CELL;
    }

    private int cellY(int row) {
        return topPos + GRID_Y + row * CELL;
    }

    private HotmMenu.PerkView perkAt(double mouseX, double mouseY) {
        for (HotmMenu.PerkView perk : menu.view().perks()) {
            if (SkyScreens.inside(mouseX, mouseY, cellX(perk.x()), cellY(perk.y()), CELL - 1, CELL - 1)) {
                return perk;
            }
        }
        return null;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        HotmMenu.View view = menu.view();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("Heart of the Mountain"),
                Component.literal("Tier " + view.level()).withColor(PURPLE));
        // XP to the next tier.
        int barX = leftPos + 10;
        int barY = topPos + 26;
        int barW = imageWidth - 20;
        boolean maxed = view.xpNext() <= 0;
        Draw.bar(g, barX, barY, barW, 5, maxed ? 1f : (float) view.xp() / view.xpNext(), PURPLE);
        String xp = maxed ? "Max tier" : String.format(Locale.ROOT, "%,d / %,d HotM XP to Tier %d", view.xp(), view.xpNext(), view.level() + 1);
        Draw.fit(g, font, Component.literal(xp), barX, barY + 8, barW, 0.7f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);

        // Tier labels down the side, the open tiers bright.
        for (int row = 0; row < 10; row++) {
            int tier = 10 - row;
            int color = tier <= view.level() ? PURPLE : Theme.DIM;
            Draw.fit(g, font, Component.literal(String.valueOf(tier)), leftPos + 8, cellY(row) + 5, 18, 0.75f, 0.5f, Draw.Align.RIGHT, color, false);
        }
        HotmMenu.PerkView hovered = perkAt(mouseX, mouseY);
        for (HotmMenu.PerkView perk : view.perks()) {
            int x = cellX(perk.x());
            int y = cellY(perk.y());
            int border = perk.selected() ? 0xFFFFAA00 : perk.state() == 3 ? 0xFF55FFFF : perk.state() == 2 ? Theme.GREEN
                    : perk.state() == 1 ? 0xFFFFFF55 : Theme.CARD_BORDER;
            Draw.panel(g, x, y, CELL - 1, CELL - 1, perk == hovered ? Theme.CARD_HOVER : Theme.CARD, border);
            Draw.item(g, icon(perk), x + 1, y + 1, 0.9f);
        }

        // The side: tokens, powder, how to use it.
        int sx = cellX(7) + 8;
        int sw = leftPos + imageWidth - 8 - sx;
        int sy = topPos + GRID_Y;
        sy = stat(g, font, sx, sy, sw, "Tokens of the Mountain", String.valueOf(view.tokens()), PURPLE);
        sy = stat(g, font, sx, sy, sw, "Mithril Powder", String.format(Locale.ROOT, "%,d", view.mithril()), 0xFF00AA00);
        sy = stat(g, font, sx, sy, sw, "Gemstone Powder", String.format(Locale.ROOT, "%,d", view.gemstone()), 0xFFFF55FF);
        sy = stat(g, font, sx, sy, sw, "Glacite Powder", String.format(Locale.ROOT, "%,d", view.glacite()), 0xFF55FFFF);
        sy += 6;
        for (String hint : List.of("Click: unlock or upgrade", "Shift-click: 10 levels", "Right-click: switch off/on",
                "Click an ability to select it")) {
            Draw.fit(g, font, Component.literal(hint), sx, sy, sw, 0.6f, 0.45f, Draw.Align.LEFT, Theme.DIM, false);
            sy += 8;
        }

        if (hovered != null) {
            Draw.queueTooltip(tooltip(hovered), border(hovered));
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private int stat(GuiGraphicsExtractor g, Font font, int x, int y, int w, String label, String value, int color) {
        Draw.fit(g, font, Component.literal(label), x, y, w, 0.65f, 0.45f, Draw.Align.LEFT, Theme.MUTED, false);
        Draw.fit(g, font, Component.literal(value), x, y + 8, w, 0.9f, 0.5f, Draw.Align.LEFT, color, false);
        return y + 22;
    }

    private static int border(HotmMenu.PerkView perk) {
        return perk.state() == 3 ? 0xFF55FFFF : perk.state() == 2 ? Theme.GREEN : perk.state() == 1 ? 0xFFFFFF55 : Theme.RED;
    }

    private static ItemStack icon(HotmMenu.PerkView perk) {
        if (perk.id().equals("core_of_the_mountain")) {
            return new ItemStack(Items.REDSTONE_BLOCK);
        }
        if (perk.level() > 0 && !perk.enabled()) {
            return new ItemStack(Items.REDSTONE);
        }
        if (perk.ability()) {
            return new ItemStack(perk.level() > 0 ? Items.EMERALD_BLOCK : Items.COAL_BLOCK);
        }
        return new ItemStack(perk.state() == 3 ? Items.DIAMOND : perk.level() > 0 ? Items.EMERALD : Items.COAL);
    }

    private static List<Component> tooltip(HotmMenu.PerkView perk) {
        List<Component> lines = new ArrayList<>();
        int color = perk.state() == 3 ? 0x55FFFF : perk.level() > 0 ? 0x55FF55 : perk.state() == 1 ? 0xFFFF55 : 0xFF5555;
        lines.add(Component.literal(perk.name()).withColor(color));
        if (perk.max() > 1) {
            lines.add(Component.literal("§7Level " + Math.max(perk.level(), 0) + "§8/" + perk.max()));
        }
        lines.add(Component.empty());
        perk.lore().forEach(line -> lines.add(Component.literal(line)));
        if (!perk.cost().isEmpty()) {
            lines.add(Component.empty());
            lines.add(Component.literal(perk.cost()));
        }
        lines.add(Component.empty());
        if (perk.state() == 1) {
            lines.add(Component.literal("§eClick to unlock!"));
        } else if (perk.state() == 0) {
            lines.add(Component.literal("§cUnlock a perk next to it first!"));
        } else if (perk.ability()) {
            lines.add(Component.literal(perk.selected() ? "§aSELECTED" : "§eClick to select!"));
        } else if (perk.state() == 2) {
            lines.add(Component.literal("§eClick to upgrade!"));
        } else {
            lines.add(Component.literal("§bMAXED"));
        }
        if (perk.level() > 0) {
            lines.add(Component.literal(perk.enabled() ? "§7Right-click to §cdisable§7." : "§7Right-click to §aenable§7."));
        }
        return lines;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        HotmMenu.PerkView perk = perkAt(event.x(), event.y());
        if (perk != null) {
            int index = menu.view().perks().indexOf(perk);
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, HotmMenu.TOGGLE + index);
            } else if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, (event.hasShiftDown() ? HotmMenu.TEN : 0) + index);
            }
            SkyScreens.click();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
