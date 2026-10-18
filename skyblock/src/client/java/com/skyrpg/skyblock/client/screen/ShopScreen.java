package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Purse;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.shop.ShopMenu;
import com.skyrpg.skyblock.shop.SkyShops;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * An NPC shop, SkyRPG style: the merchant's offers in a grid (what you can't afford dimmed, the price in the tooltip),
 * your last sales to buy back, and your inventory, where every item shows what the merchant pays for it and a click
 * sells it.
 */
public final class ShopScreen extends AbstractContainerScreen<ShopMenu> {
    private double scroll;
    private int hoveredOffer = -1;

    public ShopScreen(ShopMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ShopMenu.WIDTH, ShopMenu.HEIGHT);
    }

    /** The theme for the shop's island. */
    private ScreenTheme theme() {
        String island = menu.view().island();
        if (island.startsWith("farming") || island.equals("garden")) {
            return ScreenTheme.FARMING;
        }
        if (island.startsWith("mining") || island.startsWith("dwarven") || island.equals("crystal_hollows")) {
            return ScreenTheme.MINING;
        }
        if (island.equals("crimson_isle")) {
            return ScreenTheme.CRIMSON;
        }
        if (island.startsWith("fishing") || island.startsWith("backwater")) {
            return ScreenTheme.FISHING;
        }
        if (island.startsWith("dungeon")) {
            return ScreenTheme.DUNGEON;
        }
        return ScreenTheme.SKYBLOCK;
    }

    private static String islandName(String island) {
        if (island.isEmpty()) {
            return "";
        }
        String spaced = island.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1).toLowerCase(Locale.ROOT);
    }

    private int rows() {
        return (menu.view().offers().size() + ShopMenu.COLUMNS - 1) / ShopMenu.COLUMNS;
    }

    private int maxScroll() {
        return Math.max(0, rows() - ShopMenu.ROWS);
    }

    /** Whether you have the coins and items for one of this offer. */
    private boolean affordable(ShopMenu.OfferView offer) {
        if (offer.coins() > Purse.get(minecraft.player)) {
            return false;
        }
        for (ShopMenu.Cost cost : offer.costs()) {
            String id = SkyStacks.id(cost.icon());
            if (id == null || SkyStacks.count(minecraft.player.getInventory(), id) < cost.count()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        int x = leftPos;
        int y = topPos;
        ScreenTheme theme = theme();
        SkyScreens.window(g, font, theme, x, y, imageWidth, imageHeight, Component.literal(menu.view().name()),
                Component.literal(islandName(menu.view().island())));

        // The offers, a row of cards at a time.
        int gx = x + ShopMenu.OFFERS_X;
        int gy = y + ShopMenu.OFFERS_Y;
        List<ShopMenu.OfferView> offers = menu.view().offers();
        int first = (int) scroll * ShopMenu.COLUMNS;
        hoveredOffer = -1;
        List<int[]> dim = new ArrayList<>();
        for (int slot = 0; slot < ShopMenu.COLUMNS * ShopMenu.ROWS; slot++) {
            int index = first + slot;
            int cx = gx + slot % ShopMenu.COLUMNS * ShopMenu.CELL;
            int cy = gy + slot / ShopMenu.COLUMNS * ShopMenu.CELL;
            if (index >= offers.size()) {
                SkyScreens.well(g, theme, cx + 1, cy + 1);
                continue;
            }
            ShopMenu.OfferView offer = offers.get(index);
            boolean over = SkyScreens.inside(mouseX, mouseY, cx + 1, cy + 1, ShopMenu.CELL - 2, ShopMenu.CELL - 2);
            boolean can = affordable(offer);
            SkyScreens.card(g, theme, cx + 1, cy + 1, ShopMenu.CELL - 2, ShopMenu.CELL - 2, over, !can && !over);
            if (over) {
                Draw.outline(g, cx + 1, cy + 1, ShopMenu.CELL - 2, ShopMenu.CELL - 2, theme.accent);
            }
            g.item(offer.item(), cx + 2, cy + 2);
            g.itemDecorations(font, offer.item(), cx + 2, cy + 2, offer.count() > 1 ? Integer.toString(offer.count()) : null);
            if (!can) {
                dim.add(new int[] {cx + 2, cy + 2});
            }
            if (over) {
                hoveredOffer = index;
            }
        }
        // What you can't afford, dimmed over the items (they draw above fills in the same stratum).
        g.nextStratum();
        for (int[] at : dim) {
            g.fill(at[0], at[1], at[0] + 16, at[1] + 16, 0x90101319);
        }
        if (maxScroll() > 0) {
            Draw.scrollbar(g, gx + ShopMenu.COLUMNS * ShopMenu.CELL + 2, gy + 1, ShopMenu.ROWS * ShopMenu.CELL - 2,
                    rows() * ShopMenu.CELL, scroll * ShopMenu.CELL, theme.accent);
        }

        // Buyback (its right side shows the hovered offer's price, or how selling works) and the inventory with your purse.
        int right = x + ShopMenu.INV_X + 162;
        int buybackLabel = y + ShopMenu.BUYBACK_Y - 10;
        SkyScreens.label(g, font, theme, "Buyback", x + ShopMenu.INV_X, buybackLabel);
        if (hoveredOffer >= 0 && hoveredOffer < offers.size()) {
            List<Chip> price = priceChips(offers.get(hoveredOffer));
            int width = 0;
            for (Chip chip : price) {
                width += SkyScreens.chipWidth(font, chip, 0.75f) + 6;
            }
            SkyScreens.chips(g, font, theme, right - width + 6, buybackLabel - 1, price, 0.75f, right + 1);
        } else {
            Draw.fit(g, font, Component.literal("Click an item below to sell it"), right, buybackLabel, 110, 0.7f, 0.5f, Draw.Align.RIGHT,
                    theme.dim, false);
        }
        for (int i = 0; i < SkyShops.BUYBACK; i++) {
            SkyScreens.well(g, theme, x + ShopMenu.INV_X - 1 + i * 18, y + ShopMenu.BUYBACK_Y);
        }
        int invLabel = y + ShopMenu.INV_Y - 11;
        SkyScreens.label(g, font, theme, "Inventory", x + ShopMenu.INV_X, invLabel);
        Chip purse = Chip.balance("coins", Purse.get(minecraft.player));
        int purseW = SkyScreens.chipWidth(font, purse, 0.8f);
        SkyScreens.chip(g, font, theme, right - purseW, invLabel - 1, purse, 0.8f);
        Draw.fit(g, font, Component.literal("Purse"), right - purseW - 4, invLabel, 40, 0.7f, 0.5f, Draw.Align.RIGHT, theme.muted, false);
        SkyScreens.inventoryWells(g, theme, menu, ShopMenu.INVENTORY_START, x, y);
    }

    /** An offer's price as chips: the coins, then each item cost against what you carry. */
    private List<Chip> priceChips(ShopMenu.OfferView offer) {
        List<Chip> chips = new ArrayList<>();
        if (offer.coins() > 0) {
            chips.add(Chip.price("coins", offer.coins(), Purse.get(minecraft.player)));
        }
        for (ShopMenu.Cost cost : offer.costs()) {
            String id = SkyStacks.id(cost.icon());
            chips.add(Chip.item(cost.icon(), cost.count(), id == null ? 0 : SkyStacks.count(minecraft.player.getInventory(), id)));
        }
        return chips;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    /** Your items show what the merchant pays; the buyback row what it costs to get one back. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        List<Component> lines = new ArrayList<>(super.getTooltipFromContainerItem(stack));
        if (hoveredSlot == null) {
            return lines;
        }
        long price = SkyShops.sellPrice(stack);
        lines.add(Component.empty());
        if (hoveredSlot.index < ShopMenu.INVENTORY_START) {
            lines.add(Component.literal("Click to buy back for ").withColor(theme().muted)
                    .append(Component.literal(Msg.number(price) + " Coins").withColor(theme().accent)));
        } else if (price > 0) {
            lines.add(Component.literal("Click to sell for ").withColor(theme().muted)
                    .append(Component.literal(Msg.number(price) + " Coins").withColor(theme().accent)));
        } else {
            lines.add(Component.literal("Can't be sold here").withColor(theme().dim));
        }
        return lines;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int index = offerAt(event.x(), event.y());
            if (index >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index + (minecraft.hasShiftDown() ? ShopMenu.STACK : 0));
                SkyScreens.click();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int offerAt(double mouseX, double mouseY) {
        int gx = leftPos + ShopMenu.OFFERS_X;
        int gy = topPos + ShopMenu.OFFERS_Y;
        if (mouseX < gx || mouseY < gy || mouseX >= gx + ShopMenu.COLUMNS * ShopMenu.CELL || mouseY >= gy + ShopMenu.ROWS * ShopMenu.CELL) {
            return -1;
        }
        int col = (int) ((mouseX - gx) / ShopMenu.CELL);
        int row = (int) ((mouseY - gy) / ShopMenu.CELL);
        int index = ((int) scroll + row) * ShopMenu.COLUMNS + col;
        return index < menu.view().offers().size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (maxScroll() > 0) {
            scroll = Mth.clamp(scroll - Math.signum(vertical), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (hoveredOffer >= 0 && hoveredOffer < menu.view().offers().size() && menu.getCarried().isEmpty()) {
            ShopMenu.OfferView offer = menu.view().offers().get(hoveredOffer);
            List<Component> lines = new ArrayList<>(offer.item().getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player,
                    TooltipFlag.NORMAL));
            lines.add(Component.empty());
            lines.add(Component.literal("Cost").withColor(theme().muted));
            if (offer.coins() > 0) {
                boolean enough = Purse.get(minecraft.player) >= offer.coins();
                lines.add(Component.literal(Msg.number(offer.coins()) + " Coins").withColor(enough ? theme().accent : Theme.RED));
            }
            for (ShopMenu.Cost cost : offer.costs()) {
                String id = SkyStacks.id(cost.icon());
                int have = id == null ? 0 : SkyStacks.count(minecraft.player.getInventory(), id);
                lines.add(cost.icon().getHoverName().copy().append(Component.literal(" x" + Msg.number(cost.count())).withColor(theme().muted))
                        .append(Component.literal("  (" + Msg.number(have) + ")").withColor(have >= cost.count() ? Theme.GREEN : Theme.RED)));
            }
            lines.add(Component.empty());
            int stackUnits = Math.max(1, offer.item().getMaxStackSize() / Math.max(1, offer.count()));
            lines.add(Component.literal(affordable(offer) ? "Click to buy" : "You can't afford this").withColor(affordable(offer) ? Theme.GREEN : Theme.RED));
            if (stackUnits > 1) {
                lines.add(Component.literal("Shift-click to buy " + Msg.number((long) stackUnits * offer.count())).withColor(theme().dim));
            }
            Draw.queueTooltip(lines, theme().accent);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }
}
