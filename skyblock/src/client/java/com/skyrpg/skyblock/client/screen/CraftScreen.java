package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.progress.CollectionLevels;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.recipe.CraftMenu;
import com.skyrpg.skyblock.recipe.Crafting;
import com.skyrpg.skyblock.recipe.Recipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * SkyBlock's crafting table, SkyRPG style: the 3 by 3 grid, an arrow that lights up when the grid makes something,
 * and the result on a card glowing in its rarity's colour, with what it takes to unlock it and buttons to craft one
 * or all. The Recipe Book is a click away at the top.
 */
public final class CraftScreen extends AbstractContainerScreen<CraftMenu> {
    private static final int BUTTON_H = 14;
    private static final int PANEL_H = 3 * 18 + 2;

    public CraftScreen(CraftMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CraftMenu.WIDTH, CraftMenu.HEIGHT);
    }

    private int buttonW() {
        return (CraftMenu.RESULT_W - 16) / 2;
    }

    private int buttonsY() {
        return topPos + CraftMenu.GRID_Y - 1 + PANEL_H - BUTTON_H - 5;
    }

    private ItemStack output(Recipes.Match match) {
        ItemDefinition def = ItemRegistry.skyblock(match.recipe().output());
        return def == null ? ItemStack.EMPTY : SkyItems.create(def, match.recipe().count());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        int x = leftPos;
        int y = topPos;
        boolean overBook = overBook(mouseX, mouseY);
        SkyScreens.window(g, font, x, y, imageWidth, imageHeight, Component.literal("Craft Item"),
                Component.literal("📖 Recipe Book ▸").withColor(overBook ? Theme.ACCENT : Theme.MUTED));

        Recipes.Match match = menu.match();
        ItemDefinition def = match == null ? null : ItemRegistry.skyblock(match.recipe().output());
        boolean open = match != null && Recipes.unlocked(minecraft.player, match.recipe());
        int accent = def == null ? Theme.ACCENT : def.rarity().color();

        // The grid, on its own card.
        int gx = x + CraftMenu.GRID_X;
        int gy = y + CraftMenu.GRID_Y;
        Draw.panel(g, gx - 5, gy - 5, 3 * 18 + 10, 3 * 18 + 10, Theme.CARD, Theme.CARD_BORDER);
        for (int i = 0; i < CraftMenu.GRID_SIZE; i++) {
            int sx = gx + (i % 3) * 18;
            int sy = gy + (i / 3) * 18;
            SkyScreens.well(g, sx, sy);
            if (match != null && match.need()[i] > 0) {
                // Each used cell's need, ticked green when the stack covers it.
                g.fill(sx + 1, sy + 16, sx + 17, sy + 17, Draw.withAlpha(open ? Theme.GREEN : Theme.RED, 0.8f));
            }
        }

        // The arrow: dim, or pulsing in the result's colour when the grid makes something.
        int ax = gx + 3 * 18 + 8;
        int ay = gy + 3 * 18 / 2 - 4;
        float pulse = 0.65f + 0.35f * (float) Math.sin(Util.getMillis() / 220.0);
        Draw.scaledText(g, font, Component.literal("➜"), ax, ay - 2, 1.5f, match == null ? Theme.DIM : Draw.withAlpha(open ? accent : Theme.RED, pulse));

        // The result card.
        int rx = x + CraftMenu.RESULT_X;
        int ry = gy - 1;
        int rw = CraftMenu.RESULT_W;
        Draw.panel(g, rx, ry, rw, PANEL_H, Theme.CARD, match == null ? Theme.CARD_BORDER : Draw.mix(Theme.CARD_BORDER, accent, 0.6f));
        if (match == null) {
            Draw.fit(g, font, Component.literal("Place a recipe in the grid"), rx + rw / 2, ry + 16, rw - 10, 1f, 0.6f, Draw.Align.CENTER, Theme.DIM, false);
            Draw.fit(g, font, Component.literal("or find one in the Recipe Book").withColor(Theme.DIM), rx + rw / 2, ry + 27, rw - 10, 0.8f, 0.5f,
                    Draw.Align.CENTER, Theme.DIM, false);
        } else {
            g.fillGradient(rx + 1, ry + 1, rx + rw - 1, ry + 26, Draw.withAlpha(accent, 0.28f), 0x00000000);
            ItemStack out = output(match);
            SkyScreens.well(g, rx + 5, ry + 5);
            g.item(out, rx + 6, ry + 6);
            g.itemDecorations(font, out, rx + 6, ry + 6, match.recipe().count() > 1 ? Integer.toString(match.recipe().count()) : null);
            Draw.fit(g, font, Component.literal(def == null ? match.recipe().output() : def.name()), rx + 27, ry + 6, rw - 32, 1f, 0.55f, Draw.Align.LEFT,
                    accent, false);
            Component status;
            if (!open) {
                status = Component.literal("🔒 " + match.recipe().requires().describe()).withColor(Theme.RED);
            } else {
                int times = match.times(menu.gridStacks());
                status = Component.literal("✔ Ready" + (times > 1 ? " · grid makes " + times : "")).withColor(Theme.GREEN);
            }
            Draw.fit(g, font, status, rx + 27, ry + 16, rw - 32, 0.75f, 0.5f, Draw.Align.LEFT, Theme.TEXT, false);
            int by = buttonsY();
            button(g, font, rx + 6, by, "Craft", open, mouseX, mouseY);
            button(g, font, rx + 10 + buttonW(), by, "Craft all", open && match.times(menu.gridStacks()) > 1, mouseX, mouseY);
        }

        SkyScreens.label(g, font, "Inventory", x + CraftMenu.INV_X, y + CraftMenu.INV_Y - 11);
        SkyScreens.inventoryWells(g, menu, CraftMenu.GRID_SIZE, x, y);
    }

    private void button(GuiGraphicsExtractor g, Font font, int bx, int by, String label, boolean enabled, int mouseX, int mouseY) {
        boolean over = enabled && SkyScreens.inside(mouseX, mouseY, bx, by, buttonW(), BUTTON_H);
        int color = enabled ? Theme.GREEN : Theme.DIM;
        Draw.panel(g, bx, by, buttonW(), BUTTON_H, over ? Draw.mix(Theme.TRACK, color, 0.25f) : Theme.TRACK,
                over ? color : Draw.mix(Theme.TRACK, color, 0.55f));
        Draw.fit(g, font, Component.literal(label), bx + buttonW() / 2, by + 3, buttonW() - 4, 1f, 0.6f, Draw.Align.CENTER, color, false);
    }

    private boolean overBook(double mouseX, double mouseY) {
        return SkyScreens.inside(mouseX, mouseY, leftPos + imageWidth - 90, topPos + 6, 84, 14);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (overBook(event.x(), event.y())) {
                SkyScreens.click();
                SkyRpgMenu.open(SkyRpgSkyblock.id("recipe_book"));
                return true;
            }
            Recipes.Match match = menu.match();
            if (match != null && Recipes.unlocked(minecraft.player, match.recipe())) {
                int by = buttonsY();
                int rx = leftPos + CraftMenu.RESULT_X;
                boolean all = SkyScreens.inside(event.x(), event.y(), rx + 10 + buttonW(), by, buttonW(), BUTTON_H);
                boolean one = SkyScreens.inside(event.x(), event.y(), rx + 6, by, buttonW(), BUTTON_H)
                        || SkyScreens.inside(event.x(), event.y(), rx + 5, topPos + CraftMenu.GRID_Y + 4, 18, 18);
                if (all || one) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, all || minecraft.hasShiftDown() ? CraftMenu.CRAFT_ALL : CraftMenu.CRAFT);
                    SkyScreens.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Recipes.Match match = menu.match();
        int rx = leftPos + CraftMenu.RESULT_X;
        if (match != null && hoveredSlot == null && menu.getCarried().isEmpty()
                && SkyScreens.inside(mouseX, mouseY, rx + 5, topPos + CraftMenu.GRID_Y + 4, 18, 18)) {
            ItemStack out = output(match);
            List<Component> lines = new ArrayList<>(out.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
            Recipes.Requirement requires = match.recipe().requires();
            if (!requires.none() && !Recipes.unlocked(minecraft.player, match.recipe())) {
                lines.add(Component.literal("🔒 Requires " + requires.describe()).withColor(Theme.RED));
                if (!requires.collection().isEmpty()) {
                    int have = CollectionLevels.tier(minecraft.player, Recipes.collectionId(requires.collection()));
                    lines.add(Component.literal("Your tier: " + (have == 0 ? "none" : CollectionLevels.roman(have))).withColor(Theme.MUTED));
                }
            } else {
                lines.add(Component.literal("Click to craft · shift-click for up to " + Crafting.MAX_CRAFTS).withColor(Theme.DIM));
            }
            Draw.queueTooltip(lines, ItemRegistry.skyblock(match.recipe().output()) == null ? Theme.ACCENT
                    : ItemRegistry.skyblock(match.recipe().output()).rarity().color());
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }
}
