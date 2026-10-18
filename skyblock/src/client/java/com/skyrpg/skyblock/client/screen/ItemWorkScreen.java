package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.ui.Chip;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.reforge.Reforges;
import com.skyrpg.skyblock.reforge.ItemWork;
import com.skyrpg.skyblock.reforge.ItemWorkMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Blacksmith's "Reforge Item" and the anvil's "Combine Items": the item and the second slot on the left, and on the
 * right a card with what the button will do (the item as it'll be, or "Random reforge"), its price and the button.
 */
public final class ItemWorkScreen extends AbstractContainerScreen<ItemWorkMenu> {
    private static final int CARD_X = 60;
    private static final int BUTTON_H = 14;
    private static final ScreenTheme THEME = ScreenTheme.MAGIC;

    public ItemWorkScreen(ItemWorkMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ItemWorkMenu.WIDTH, ItemWorkMenu.HEIGHT);
    }

    private boolean reforge() {
        return menu.mode() == ItemWork.Mode.REFORGE;
    }

    private int cardW() {
        return imageWidth - ItemWorkMenu.PAD - CARD_X;
    }

    private int cardY() {
        return topPos + ItemWorkMenu.ITEM_Y - 4;
    }

    private int cardH() {
        return ItemWorkMenu.EXTRA_Y + 18 - ItemWorkMenu.ITEM_Y + 8;
    }

    private int buttonX() {
        return leftPos + CARD_X + 8;
    }

    private int buttonY() {
        return cardY() + cardH() - BUTTON_H - 6;
    }

    private int buttonW() {
        return cardW() - 16;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        int x = leftPos;
        int y = topPos;
        SkyScreens.window(g, font, THEME, x, y, imageWidth, imageHeight, Component.literal(reforge() ? "Reforge Item" : "Combine Items"),
                Component.literal(reforge() ? "Blacksmith" : "Anvil").withColor(THEME.muted));

        // The two slots, each with a small label.
        SkyScreens.label(g, font, THEME, "Item", x + ItemWorkMenu.ITEM_X, y + ItemWorkMenu.ITEM_Y - 9);
        SkyScreens.well(g, THEME, x + ItemWorkMenu.ITEM_X, y + ItemWorkMenu.ITEM_Y);
        SkyScreens.label(g, font, THEME, reforge() ? "Stone" : "Upgrade", x + ItemWorkMenu.EXTRA_X, y + ItemWorkMenu.EXTRA_Y - 9);
        SkyScreens.well(g, THEME, x + ItemWorkMenu.EXTRA_X, y + ItemWorkMenu.EXTRA_Y);

        ItemWork.Preview preview = menu.preview();
        ItemStack item = menu.item();
        ItemDefinition def = SkyItems.definition(item);
        ItemStack shown = preview.result() != null ? preview.result() : item;
        int accent = def == null ? THEME.accent : ItemMods.rarity(shown, def).color();

        int rx = x + CARD_X;
        int ry = cardY();
        int rw = cardW();
        SkyScreens.card(g, THEME, rx, ry, rw, cardH(), false, item.isEmpty() || def == null);
        if (item.isEmpty() || def == null) {
            Draw.item(g, new ItemStack(reforge() ? Items.ANVIL : Items.ENCHANTED_BOOK), rx + rw / 2 - 8, ry + 8, 1f);
            Draw.fit(g, font, Component.literal(preview.problem()), rx + rw / 2, ry + 30, rw - 10, 0.9f, 0.6f, Draw.Align.CENTER, THEME.muted, false);
            Draw.fit(g, font, Component.literal(reforge() ? "Reforges change an item's bonus stats" : "Books, Recombobulators and Potato Books go here"),
                    rx + rw / 2, ry + 42, rw - 10, 0.7f, 0.45f, Draw.Align.CENTER, THEME.dim, false);
        } else {
            g.fillGradient(rx + 4, ry + 4, rx + rw - 4, ry + 26, Draw.withAlpha(accent, preview.ok() ? 0.28f : 0.12f), 0x00000000);
            SkyScreens.well(g, THEME, rx + 5, ry + 5);
            g.item(shown, rx + 6, ry + 6);
            Draw.fit(g, font, Component.literal(ItemMods.name(shown, def)), rx + 27, ry + 7, rw - 32, 1f, 0.55f, Draw.Align.LEFT, accent, true);
            Component status;
            if (!preview.ok()) {
                status = Component.literal(preview.problem()).withColor(Theme.RED);
            } else if (preview.result() == null) {
                Reforges.Reforge current = ItemMods.reforge(item);
                status = Component.literal("Random reforge" + (current == null ? "" : " · now " + current.name())).withColor(THEME.text);
            } else {
                status = Component.literal("✔ " + preview.action()).withColor(THEME.good);
            }
            Draw.fit(g, font, status, rx + 27, ry + 17, rw - 32, 0.75f, 0.5f, Draw.Align.LEFT, THEME.text, false);
            if (preview.levels() > 0) {
                boolean afford = minecraft.player != null && (minecraft.player.isCreative() || minecraft.player.experienceLevel >= preview.levels());
                SkyScreens.chip(g, font, THEME, rx + 8, ry + 29, Chip.note(new ItemStack(Items.EXPERIENCE_BOTTLE), preview.levels() + " XP levels", afford), 0.8f);
            }
            if (preview.coins() > 0) {
                SkyScreens.chip(g, font, THEME, rx + 8, ry + 29, Chip.balance("coins", preview.coins()), 0.8f);
            }
        }
        boolean enabled = preview.ok();
        boolean over = enabled && SkyScreens.inside(mouseX, mouseY, buttonX(), buttonY(), buttonW(), BUTTON_H);
        SkyScreens.button(g, font, THEME, buttonX(), buttonY(), buttonW(), BUTTON_H, reforge() ? "Reforge" : "Combine", enabled, over);

        SkyScreens.label(g, font, THEME, "Inventory", x + ItemWorkMenu.INV_X, y + ItemWorkMenu.INV_Y - 10);
        SkyScreens.inventoryWells(g, THEME, menu, 2, x, y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && menu.preview().ok()
                && SkyScreens.inside(event.x(), event.y(), buttonX(), buttonY(), buttonW(), BUTTON_H)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ItemWorkMenu.GO);
            SkyScreens.click();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        ItemWork.Preview preview = menu.preview();
        ItemStack shown = preview.result() != null ? preview.result() : menu.item();
        if (!shown.isEmpty() && hoveredSlot == null && menu.getCarried().isEmpty()
                && SkyScreens.inside(mouseX, mouseY, leftPos + CARD_X + 5, cardY() + 5, 18, 18)) {
            List<Component> lines = new ArrayList<>(shown.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
            ItemDefinition def = SkyItems.definition(shown);
            Draw.queueTooltip(lines, def == null ? THEME.accent : ItemMods.rarity(shown, def).color());
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }
}
