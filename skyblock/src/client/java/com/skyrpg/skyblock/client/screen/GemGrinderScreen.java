package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.gem.Gemstones;
import com.skyrpg.skyblock.gem.GemGrinderMenu;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * The Gemstone Grinder: the item on the left, its gemstone slots as cards on the right (locked with its unlock cost,
 * open and empty, or holding a gem). Click a card to unlock it or put your best fitting gemstone in; right-click a full
 * one to take the gem out.
 */
public final class GemGrinderScreen extends AbstractContainerScreen<GemGrinderMenu> {
    private static final ScreenTheme THEME = ScreenTheme.MINING;
    private static final int CARDS_X = 46;
    private static final int CARD_W = 34;
    private static final int CARD_H = 34;
    private static final int CARDS_Y = SkyScreens.contentTop(ScreenTheme.MINING);
    private static final int PINK = 0xFFFF55FF;

    public GemGrinderScreen(GemGrinderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, GemGrinderMenu.WIDTH, GemGrinderMenu.HEIGHT);
    }

    private int cardX(int i) {
        return leftPos + CARDS_X + (i % 5) * (CARD_W + 2);
    }

    private int cardY(int i) {
        return topPos + CARDS_Y + (i / 5) * (CARD_H + 2);
    }

    /** A gemstone's item, or empty when its definition is missing. */
    private static ItemStack gemStack(Gemstones.Gem gem) {
        ItemDefinition gemDef = ItemRegistry.skyblock(gem.tier() + "_" + gem.type() + "_GEM");
        return gemDef == null ? ItemStack.EMPTY : SkyItems.create(gemDef, 1);
    }

    private static String compact(long n) {
        return n >= 1_000_000 ? (n / 1_000_000) + "M" : n >= 1000 ? (n / 1000) + "k" : String.valueOf(n);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Gemstone Grinder"),
                Component.literal("Gemstones").withColor(PINK));
        SkyScreens.label(g, font, THEME, "Item", leftPos + GemGrinderMenu.ITEM_X + 1, topPos + GemGrinderMenu.ITEM_Y - 10);
        SkyScreens.well(g, THEME, leftPos + GemGrinderMenu.ITEM_X, topPos + GemGrinderMenu.ITEM_Y);
        SkyScreens.inventoryWells(g, THEME, menu, 1, leftPos, topPos);
        ItemStack item = menu.item();
        ItemDefinition def = SkyItems.definition(item);
        List<Gemstones.Slot> slots = Gemstones.slots(def);
        List<Component> tooltip = null;
        if (item.isEmpty()) {
            Draw.fit(g, font, Component.literal("Put an item with gemstone slots in."), leftPos + CARDS_X, topPos + CARDS_Y + 14,
                    imageWidth - CARDS_X - 14, 0.8f, 0.5f, Draw.Align.LEFT, THEME.muted, false);
        } else if (slots.isEmpty()) {
            Draw.fit(g, font, Component.literal("This item has no gemstone slots."), leftPos + CARDS_X, topPos + CARDS_Y + 14,
                    imageWidth - CARDS_X - 14, 0.8f, 0.5f, Draw.Align.LEFT, Theme.RED, false);
        }
        for (int i = 0; i < slots.size(); i++) {
            Gemstones.Slot slot = slots.get(i);
            Gemstones.Gem gem = Gemstones.gems(item).stream().filter(gg -> gg.slot().equals(slot.id())).findFirst().orElse(null);
            boolean open = Gemstones.unlocked(item, slot);
            int x = cardX(i);
            int y = cardY(i);
            boolean over = SkyScreens.inside(mouseX, mouseY, x, y, CARD_W, CARD_H);
            SkyScreens.card(g, THEME, x, y, CARD_W, CARD_H, over, !open);
            Draw.fit(g, font, Component.literal(cap(slot.type())), x + CARD_W / 2, y + 4, CARD_W - 4, 0.6f, 0.4f, Draw.Align.CENTER,
                    open ? THEME.text : THEME.dim, false);
            int iconX = x + (CARD_W - 16) / 2;
            ItemStack gemItem = gem == null ? ItemStack.EMPTY : gemStack(gem);
            if (!gemItem.isEmpty()) {
                g.fill(iconX - 1, y + 11, iconX + 17, y + 29, 0x55000000);
                Draw.item(g, gemItem, iconX, y + 12, 1f);
            } else if (!open) {
                Draw.item(g, new ItemStack(Items.IRON_BARS), iconX, y + 11, 0.8f);
            } else {
                g.fill(iconX - 1, y + 11, iconX + 17, y + 29, 0x55000000);
                Draw.fit(g, font, Component.literal("+"), x + CARD_W / 2, y + 16, CARD_W, 1.4f, 1f, Draw.Align.CENTER, THEME.good, false);
            }
            if (!open && slot.coins() > 0) {
                Chip price = new Chip("coins", ItemStack.EMPTY, compact(slot.coins()), true);
                int cw = SkyScreens.chipWidth(font, price, 0.65f);
                SkyScreens.chip(g, font, THEME, x + (CARD_W - cw) / 2, y + CARD_H - 11, price, 0.65f);
            } else {
                String label = !open ? "Locked" : gem == null ? "Empty" : cap(gem.tier());
                Draw.fit(g, font, Component.literal(label), x + CARD_W / 2, y + CARD_H - 9, CARD_W - 4, 0.55f, 0.4f, Draw.Align.CENTER,
                        !open ? THEME.dim : gem == null ? THEME.good : PINK, false);
            }
            if (over) {
                tooltip = tooltip(slot, gem, gemItem, open);
            }
        }
        if (!item.isEmpty() && !slots.isEmpty()) {
            Draw.fit(g, font, Component.literal("Click: unlock or fill  ·  Right-click: take the gem out"), leftPos + imageWidth / 2,
                    topPos + GemGrinderMenu.INV_Y - 13, imageWidth - 24, 0.6f, 0.45f, Draw.Align.CENTER, THEME.dim, false);
        }
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, PINK);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private List<Component> tooltip(Gemstones.Slot slot, Gemstones.Gem gem, ItemStack gemItem, boolean open) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(cap(slot.type()) + " Gemstone Slot").withColor(PINK));
        if (slot.accepts().size() > 1) {
            lines.add(Component.literal("§7Takes: " + String.join(", ", slot.accepts().stream().map(GemGrinderScreen::cap).toList())));
        }
        lines.add(Component.empty());
        if (!open) {
            lines.add(Component.literal("§7Cost to unlock:"));
            if (slot.coins() > 0) {
                lines.add(Component.literal(String.format(Locale.ROOT, " §6%,d Coins", slot.coins())));
            }
            for (String cost : slot.items()) {
                String[] parts = cost.split(":");
                ItemDefinition def = ItemRegistry.skyblock(parts[0]);
                lines.add(Component.literal(" §8" + parts[1] + "x ").append(Component.literal(def == null ? parts[0] : def.name())
                        .withColor(def == null ? 0xFFFFFF : def.rarity().color())));
            }
            lines.add(Component.empty());
            lines.add(Component.literal("§eClick to unlock!"));
        } else if (gem == null) {
            lines.add(Component.literal("§eClick to put your best fitting gemstone in!"));
        } else {
            if (gemItem.isEmpty()) {
                lines.add(Component.literal("§d" + Gemstones.name(gem.type(), gem.tier())));
            } else {
                lines.addAll(gemItem.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
            }
            lines.add(Component.empty());
            lines.add(Component.literal(String.format(Locale.ROOT, "§eRight-click to take it out (§6%,d coins§e).", Gemstones.removalCost(gem.tier()))));
        }
        return lines;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        List<Gemstones.Slot> slots = Gemstones.slots(SkyItems.definition(menu.item()));
        for (int i = 0; i < slots.size(); i++) {
            if (SkyScreens.inside(event.x(), event.y(), cardX(i), cardY(i), CARD_W, CARD_H)) {
                boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, (right ? GemGrinderMenu.REMOVE : 0) + i);
                SkyScreens.click();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
