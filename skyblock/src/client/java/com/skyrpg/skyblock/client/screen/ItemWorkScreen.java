package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
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
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Blacksmith's "Reforge Item" and the anvil's "Combine Items": the item and the second slot on the left, and on the
 * right a card with what the button will do (the item as it'll be, or "Random reforge"), its price and the button.
 */
public final class ItemWorkScreen extends AbstractContainerScreen<ItemWorkMenu> {
    private static final int CARD_X = 60;
    private static final int BUTTON_H = 14;
    private static final int COIN = 0xFFF2B33D;

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
        return leftPos + CARD_X + 6;
    }

    private int buttonY() {
        return cardY() + cardH() - BUTTON_H - 5;
    }

    private int buttonW() {
        return cardW() - 12;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        int x = leftPos;
        int y = topPos;
        SkyScreens.window(g, font, x, y, imageWidth, imageHeight, Component.literal(reforge() ? "Reforge Item" : "Combine Items"),
                Component.literal(reforge() ? "Blacksmith" : "Anvil").withColor(Theme.MUTED));

        // The two slots, each with a small label.
        SkyScreens.label(g, font, "Item", x + ItemWorkMenu.ITEM_X - 2, y + ItemWorkMenu.ITEM_Y - 10);
        SkyScreens.well(g, x + ItemWorkMenu.ITEM_X, y + ItemWorkMenu.ITEM_Y);
        SkyScreens.label(g, font, reforge() ? "Stone" : "Upgrade", x + ItemWorkMenu.EXTRA_X - 2, y + ItemWorkMenu.EXTRA_Y - 10);
        SkyScreens.well(g, x + ItemWorkMenu.EXTRA_X, y + ItemWorkMenu.EXTRA_Y);

        ItemWork.Preview preview = menu.preview();
        ItemStack item = menu.item();
        ItemDefinition def = SkyItems.definition(item);
        ItemStack shown = preview.result() != null ? preview.result() : item;
        int accent = def == null ? Theme.ACCENT : ItemMods.rarity(shown, def).color();

        int rx = x + CARD_X;
        int ry = cardY();
        int rw = cardW();
        Draw.panel(g, rx, ry, rw, cardH(), Theme.CARD, preview.ok() ? Draw.mix(Theme.CARD_BORDER, accent, 0.6f) : Theme.CARD_BORDER);
        if (item.isEmpty() || def == null) {
            Draw.fit(g, font, Component.literal(preview.problem()), rx + rw / 2, ry + 14, rw - 10, 1f, 0.6f, Draw.Align.CENTER, Theme.DIM, false);
            Draw.fit(g, font, Component.literal(reforge() ? "Reforges change an item's bonus stats" : "Recombobulators and Potato Books go here")
                    .withColor(Theme.DIM), rx + rw / 2, ry + 26, rw - 10, 0.8f, 0.5f, Draw.Align.CENTER, Theme.DIM, false);
        } else {
            g.fillGradient(rx + 1, ry + 1, rx + rw - 1, ry + 24, Draw.withAlpha(accent, preview.ok() ? 0.28f : 0.12f), 0x00000000);
            SkyScreens.well(g, rx + 5, ry + 5);
            g.item(shown, rx + 6, ry + 6);
            Draw.fit(g, font, Component.literal(ItemMods.name(shown, def)), rx + 27, ry + 6, rw - 32, 1f, 0.55f, Draw.Align.LEFT, accent, false);
            Component status;
            if (!preview.ok()) {
                status = Component.literal(preview.problem()).withColor(Theme.RED);
            } else if (preview.result() == null) {
                Reforges.Reforge current = ItemMods.reforge(item);
                status = Component.literal("Random reforge" + (current == null ? "" : " · now " + current.name())).withColor(Theme.TEXT);
            } else {
                status = Component.literal("✔ " + preview.action()).withColor(Theme.GREEN);
            }
            Draw.fit(g, font, status, rx + 27, ry + 16, rw - 32, 0.75f, 0.5f, Draw.Align.LEFT, Theme.TEXT, false);
            if (preview.coins() > 0) {
                Draw.fit(g, font, Component.literal("Cost: ").withColor(Theme.MUTED).append(Component.literal(Msg.number(preview.coins()) + " coins").withColor(COIN)),
                        rx + 27, ry + 26, rw - 32, 0.75f, 0.5f, Draw.Align.LEFT, Theme.TEXT, false);
            }
        }
        boolean enabled = preview.ok();
        boolean over = enabled && SkyScreens.inside(mouseX, mouseY, buttonX(), buttonY(), buttonW(), BUTTON_H);
        int color = enabled ? Theme.GREEN : Theme.DIM;
        float pulse = enabled ? 0.55f + 0.2f * (float) Math.sin(Util.getMillis() / 260.0) : 0.55f;
        Draw.panel(g, buttonX(), buttonY(), buttonW(), BUTTON_H, over ? Draw.mix(Theme.TRACK, color, 0.25f) : Theme.TRACK,
                over ? color : Draw.mix(Theme.TRACK, color, pulse));
        Draw.fit(g, font, Component.literal(reforge() ? "Reforge" : "Combine"), buttonX() + buttonW() / 2, buttonY() + 3, buttonW() - 4, 1f, 0.6f,
                Draw.Align.CENTER, color, false);

        SkyScreens.label(g, font, "Inventory", x + ItemWorkMenu.INV_X, y + ItemWorkMenu.INV_Y - 11);
        SkyScreens.inventoryWells(g, menu, 2, x, y);
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
            Draw.queueTooltip(lines, def == null ? Theme.ACCENT : ItemMods.rarity(shown, def).color());
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }
}
