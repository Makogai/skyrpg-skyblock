package com.skyrpg.skyblock.client.screen;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Small pieces the Crimson Isle, Malik and Odger screens share: an NPC panel with a wallet, item icons, tooltips. */
final class NpcCards {
    /** The width of the NPC panel on the left of a screen. */
    static final int PANEL = 96;

    private NpcCards() {
    }

    /** An item icon on a dark backing, {@code scale} times 16px, centred in a {@code box} square at (x, y). */
    static void icon(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int box, float scale) {
        g.fill(x, y, x + box, y + box, 0x55000000);
        if (!stack.isEmpty()) {
            int size = Math.round(16 * scale);
            Draw.item(g, stack.copyWithCount(1), x + (box - size) / 2, y + (box - size) / 2, scale);
        }
    }

    /** An item's own tooltip lines, as the game would show them. */
    static List<Component> itemTip(Minecraft minecraft, ItemStack stack) {
        return new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
    }

    /** Whether the stack is one of SkyBlock's own items (so its own tooltip is worth showing). */
    static boolean skyItem(ItemStack stack) {
        return !stack.isEmpty() && com.skyrpg.items.SkyItems.definition(stack) != null;
    }

    /** A chip's name for lists and tooltips: its currency, or its item. */
    static String chipName(Chip chip) {
        if (chip.currency().isEmpty()) {
            return chip.item().isEmpty() ? "" : chip.item().getHoverName().getString();
        }
        return chip.currency().startsWith("medal_") ? SkyScreens.currencyName(chip.currency()) + " medals" : SkyScreens.currencyName(chip.currency());
    }

    /** Lines for a tooltip: a header, then each chip as "amount name" (red when you haven't enough). */
    static void chipLines(List<Component> lines, ScreenTheme theme, String header, List<Chip> chips) {
        if (chips.isEmpty()) {
            return;
        }
        lines.add(Component.empty());
        if (!header.isEmpty()) {
            lines.add(Component.literal(header).withColor(theme.muted));
        }
        for (Chip chip : chips) {
            lines.add(Component.literal(chip.text() + (labelled(chip) ? "" : " " + chipName(chip))).withColor(chip.enough() ? theme.text : 0xFFFF6464));
        }
    }

    /** Whether a chip's own text already says what it is ("5 Crimson Essence"), so its currency or item name isn't added. */
    static boolean labelled(Chip chip) {
        return chip.text().matches(".*[A-Za-z].*") || chipName(chip).isEmpty();
    }

    /**
     * The NPC panel: a card with the NPC's live portrait (turning to the mouse) and your wallet under it, one chip a line with
     * its name. {@code entityId} is the NPC's entity id (nothing is drawn in the portrait box when it isn't loaded).
     */
    static void npcPanel(GuiGraphicsExtractor g, Font font, ScreenTheme theme, Minecraft minecraft, int x, int y, int h, int entityId,
                         String walletTitle, List<Chip> wallet, int mouseX, int mouseY) {
        SkyScreens.card(g, theme, x, y, PANEL, h, false, false);
        Entity entity = entityId >= 0 && minecraft.level != null ? minecraft.level.getEntity(entityId) : null;
        int portrait = Math.min(92, h - 50);
        g.fill(x + 4, y + 4, x + PANEL - 4, y + portrait, 0x40000000);
        if (entity instanceof LivingEntity living) {
            SkyScreens.npcPortrait(g, living, x + 4, y + 4, x + PANEL - 4, y + portrait, 36, mouseX, mouseY);
        }
        int wy = y + portrait + 6;
        if (wallet.isEmpty()) {
            return;
        }
        Draw.fit(g, font, Component.literal(walletTitle), x + PANEL / 2, wy, PANEL - 8, 0.7f, 0.5f, Draw.Align.CENTER, theme.accent, false);
        wy += 11;
        for (Chip chip : wallet) {
            if (wy + 10 > y + h - 2) {
                break;
            }
            int cw = SkyScreens.chip(g, font, theme, x + 8, wy, chip, 0.8f);
            String name = labelled(chip) ? "" : chipName(chip);
            Draw.fit(g, font, Component.literal(name), x + 12 + cw, wy + 2, PANEL - 18 - cw, 0.62f, 0.45f, Draw.Align.LEFT, theme.muted, false);
            wy += 12;
        }
    }

    /** "x3" over the bottom right of an icon drawn at (x, y), {@code size} px wide, when there's more than one. */
    static void count(GuiGraphicsExtractor g, Font font, ItemStack stack, int x, int y, int size, int color) {
        if (stack.getCount() > 1) {
            String text = "x" + stack.getCount();
            int w = Math.round(font.width(text) * 0.55f);
            g.nextStratum();
            Draw.scaledText(g, font, Component.literal(text), x + size - w, y + size - 5, 0.55f, color);
        }
    }
}
