package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Purse;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.shop.ShopMenu;
import com.skyrpg.skyblock.shop.SkyShops;
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

    private int accent() {
        String island = menu.view().island();
        if (island.startsWith("farming") || island.equals("garden")) {
            return 0xFFE5C34A;
        }
        if (island.startsWith("mining") || island.equals("crystal_hollows")) {
            return Theme.AQUA;
        }
        if (island.startsWith("foraging")) {
            return Theme.GREEN;
        }
        if (island.startsWith("combat") || island.equals("crimson_isle")) {
            return Theme.RED;
        }
        if (island.startsWith("fishing")) {
            return Theme.BLUE;
        }
        return Theme.ACCENT;
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
        int accent = accent();
        SkyScreens.window(g, font, x, y, imageWidth, imageHeight, Component.literal(menu.view().name()),
                Component.literal(islandName(menu.view().island())).withColor(Theme.MUTED));

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
                g.fill(cx + 1, cy + 1, cx + ShopMenu.CELL - 1, cy + ShopMenu.CELL - 1, 0x40000000);
                continue;
            }
            ShopMenu.OfferView offer = offers.get(index);
            boolean over = SkyScreens.inside(mouseX, mouseY, cx + 1, cy + 1, ShopMenu.CELL - 2, ShopMenu.CELL - 2);
            boolean can = affordable(offer);
            Draw.panel(g, cx + 1, cy + 1, ShopMenu.CELL - 2, ShopMenu.CELL - 2, over ? Theme.CARD_HOVER : Theme.CARD,
                    over ? accent : can ? Draw.mix(Theme.CARD_BORDER, accent, 0.25f) : Theme.CARD_BORDER);
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
            Draw.scrollbar(g, gx + ShopMenu.COLUMNS * ShopMenu.CELL + 1, gy + 1, ShopMenu.ROWS * ShopMenu.CELL - 2,
                    rows() * ShopMenu.CELL, scroll * ShopMenu.CELL, accent);
        }

        // Buyback and the inventory.
        SkyScreens.label(g, font, "Buyback", x + ShopMenu.INV_X, y + ShopMenu.BUYBACK_Y - 10);
        Draw.fit(g, font, Component.literal("Click an item below to sell it"), x + ShopMenu.INV_X + 162, y + ShopMenu.BUYBACK_Y - 10, 110,
                0.75f, 0.5f, Draw.Align.RIGHT, Theme.DIM, false);
        for (int i = 0; i < SkyShops.BUYBACK; i++) {
            SkyScreens.well(g, x + ShopMenu.INV_X - 1 + i * 18, y + ShopMenu.BUYBACK_Y);
        }
        SkyScreens.label(g, font, "Inventory", x + ShopMenu.INV_X, y + ShopMenu.INV_Y - 11);
        Draw.fit(g, font, Component.literal("Purse ").withColor(Theme.MUTED).append(Component.literal(Msg.number(Purse.get(minecraft.player)))
                .withColor(Theme.ACCENT)), x + ShopMenu.INV_X + 162, y + ShopMenu.INV_Y - 11, 100, 0.85f, 0.5f, Draw.Align.RIGHT, Theme.ACCENT, false);
        SkyScreens.inventoryWells(g, menu, ShopMenu.INVENTORY_START, x, y);
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
            lines.add(Component.literal("Click to buy back for ").withColor(Theme.MUTED)
                    .append(Component.literal(Msg.number(price) + " Coins").withColor(Theme.ACCENT)));
        } else if (price > 0) {
            lines.add(Component.literal("Click to sell for ").withColor(Theme.MUTED)
                    .append(Component.literal(Msg.number(price) + " Coins").withColor(Theme.ACCENT)));
        } else {
            lines.add(Component.literal("Can't be sold here").withColor(Theme.DIM));
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
            lines.add(Component.literal("Cost").withColor(Theme.MUTED));
            if (offer.coins() > 0) {
                boolean enough = Purse.get(minecraft.player) >= offer.coins();
                lines.add(Component.literal(Msg.number(offer.coins()) + " Coins").withColor(enough ? Theme.ACCENT : Theme.RED));
            }
            for (ShopMenu.Cost cost : offer.costs()) {
                String id = SkyStacks.id(cost.icon());
                int have = id == null ? 0 : SkyStacks.count(minecraft.player.getInventory(), id);
                lines.add(cost.icon().getHoverName().copy().append(Component.literal(" x" + Msg.number(cost.count())).withColor(Theme.MUTED))
                        .append(Component.literal("  (" + Msg.number(have) + ")").withColor(have >= cost.count() ? Theme.GREEN : Theme.RED)));
            }
            lines.add(Component.empty());
            int stackUnits = Math.max(1, offer.item().getMaxStackSize() / Math.max(1, offer.count()));
            lines.add(Component.literal(affordable(offer) ? "Click to buy" : "You can't afford this").withColor(affordable(offer) ? Theme.GREEN : Theme.RED));
            if (stackUnits > 1) {
                lines.add(Component.literal("Shift-click to buy " + Msg.number((long) stackUnits * offer.count())).withColor(Theme.DIM));
            }
            Draw.queueTooltip(lines, accent());
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }
}
