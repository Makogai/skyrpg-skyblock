package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.mining.ForgeMenu;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Forge, in the mining theme: the slot cards on top (forging with a progress bar and time left, ready to take, empty,
 * or locked), then every recipe as a card with its output, materials (have/need chips, red when short), coins and time.
 * Click a recipe to forge it in a free slot; click a finished slot to take the item. Hover for the item's real tooltip.
 */
public final class ForgeScreen extends AbstractContainerScreen<ForgeMenu> {
    private static final ScreenTheme THEME = ScreenTheme.MINING;
    private static final int WIDTH = 300;
    private static final int HEIGHT = 232;
    private static final int SLOT_W = 36;
    private static final int SLOT_GAP = 4;
    private static final int SLOT_H = 38;
    private static final int SLOTS_Y = SkyScreens.contentTop(THEME);
    private static final int LIST_Y = SLOTS_Y + SLOT_H + 14;
    private static final int ROW_H = 28;
    private static final int GAP = 3;
    private static final int ORANGE = 0xFFFF8A2A;

    private double scroll;

    public ForgeScreen(ForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int slotX(int i) {
        return leftPos + (imageWidth - (ForgeMenu.MAX_SLOTS * SLOT_W + (ForgeMenu.MAX_SLOTS - 1) * SLOT_GAP)) / 2 + i * (SLOT_W + SLOT_GAP);
    }

    private int listTop() {
        return topPos + LIST_Y;
    }

    private int listHeight() {
        return imageHeight - LIST_Y - 16;
    }

    private int listX() {
        return leftPos + THEME.inset;
    }

    private int listW() {
        return imageWidth - 2 * THEME.inset;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        ForgeMenu.View view = menu.view();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("The Forge"),
                Component.literal("HotM " + view.hotm()).withColor(THEME.muted));
        List<Component> tooltip = null;
        for (int i = 0; i < view.slots().size(); i++) {
            ForgeMenu.SlotView slot = view.slots().get(i);
            int x = slotX(i);
            int y = topPos + SLOTS_Y;
            boolean ready = !slot.output().isEmpty() && slot.left() <= 0;
            boolean busy = !slot.output().isEmpty() && !ready;
            boolean over = SkyScreens.inside(mouseX, mouseY, x, y, SLOT_W, SLOT_H);
            SkyScreens.card(g, THEME, x, y, SLOT_W, SLOT_H, over && ready, !slot.open());
            if (ready) {
                Draw.outline(g, x, y, SLOT_W, SLOT_H, THEME.good);
            } else if (busy) {
                Draw.outline(g, x, y, SLOT_W, SLOT_H, ORANGE);
            }
            String label;
            int color;
            if (!slot.open()) {
                Draw.item(g, new ItemStack(Items.IRON_BARS), x + (SLOT_W - 14) / 2, y + 6, 0.875f);
                label = "Locked";
                color = THEME.dim;
            } else if (slot.output().isEmpty()) {
                Draw.fit(g, font, Component.literal("+"), x + SLOT_W / 2, y + 8, SLOT_W, 1.6f, 1f, Draw.Align.CENTER, THEME.dim, false);
                label = "Empty";
                color = THEME.muted;
            } else {
                g.fill(x + (SLOT_W - 18) / 2, y + 3, x + (SLOT_W + 18) / 2, y + 21, 0x55000000);
                Draw.item(g, slot.output(), x + (SLOT_W - 16) / 2, y + 4, 1f);
                label = ready ? "Claim!" : time(slot.left() / 1000);
                color = ready ? THEME.good : ORANGE;
                if (over) {
                    tooltip = new ArrayList<>(slot.output().getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
                    tooltip.add(Component.empty());
                    tooltip.add(Component.literal(ready ? "§aReady! Click to take it." : "§7Done in §6" + time(slot.left() / 1000)));
                }
            }
            if (busy && slot.total() > 0) {
                SkyScreens.bar(g, THEME, x + 4, y + 23, SLOT_W - 8, 4, 1f - (float) slot.left() / slot.total(), ORANGE);
            }
            Draw.fit(g, font, Component.literal(label), x + SLOT_W / 2, y + SLOT_H - 9, SLOT_W - 4, 0.65f, 0.45f, Draw.Align.CENTER, color, false);
        }
        Draw.fit(g, font, Component.literal("Recipes"), leftPos + THEME.inset + 2, topPos + LIST_Y - 10, 100, 0.75f, 0.5f, Draw.Align.LEFT,
                THEME.accent, false);
        Draw.fit(g, font, Component.literal("Click a recipe to forge it in a free slot"), leftPos + imageWidth - THEME.inset - 2, topPos + LIST_Y - 9,
                imageWidth / 2, 0.62f, 0.45f, Draw.Align.RIGHT, THEME.dim, false);

        List<ForgeMenu.RecipeView> recipes = view.recipes();
        int top = listTop();
        int height = listHeight();
        int content = recipes.size() * (ROW_H + GAP);
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - height));
        int x = listX();
        int w = listW() - (content > height ? 6 : 0);
        g.enableScissor(leftPos + 2, top, leftPos + imageWidth - 2, top + height);
        for (int i = 0; i < recipes.size(); i++) {
            ForgeMenu.RecipeView recipe = recipes.get(i);
            int y = top + i * (ROW_H + GAP) - (int) Math.round(scroll);
            if (y + ROW_H < top || y > top + height) {
                continue;
            }
            boolean locked = recipe.hotm() > view.hotm();
            boolean over = SkyScreens.inside(mouseX, mouseY, x, Math.max(y, top), w, ROW_H) && mouseY < top + height;
            SkyScreens.card(g, THEME, x, y, w, ROW_H, over, locked);
            if (recipe.ready() && !locked) {
                Draw.outline(g, x, y, w, ROW_H, THEME.good);
            }
            g.fill(x + 4, y + 4, x + 24, y + ROW_H - 4, 0x55000000);
            Draw.item(g, recipe.output(), x + 6, y + 6, 1f);
            int tx = x + 30;
            String right = locked ? "HotM " + recipe.hotm() : time(recipe.seconds());
            int rightW = Math.round(font.width(right) * 0.7f) + 14;
            Draw.fit(g, font, recipe.output().getHoverName(), tx, y + 4, w - 30 - rightW - 8, 0.85f, 0.5f, Draw.Align.LEFT,
                    locked ? THEME.dim : THEME.text, false);
            if (locked) {
                Draw.fit(g, font, Component.literal(right), x + w - 6, y + 5, rightW, 0.7f, 0.45f, Draw.Align.RIGHT, Theme.RED, false);
            } else {
                Chip clock = Chip.note(new ItemStack(Items.CLOCK), right, true);
                SkyScreens.chip(g, font, THEME, x + w - 6 - SkyScreens.chipWidth(font, clock, 0.7f), y + 4, clock, 0.7f);
            }
            // The materials with have/need (red when short), then the coins.
            List<Chip> chips = new ArrayList<>();
            for (int k = 0; k < recipe.inputs().size(); k++) {
                ItemStack input = recipe.inputs().get(k);
                int have = recipe.have().get(k);
                chips.add(Chip.note(input, compact(have) + "/" + compact(input.getCount()), have >= input.getCount()));
            }
            if (recipe.coins() > 0) {
                chips.add(new Chip("coins", ItemStack.EMPTY, String.format(Locale.ROOT, "%,d", recipe.coins()), true));
            }
            SkyScreens.chips(g, font, THEME, tx, y + 16, chips, 0.7f, x + w - 6);
            if (locked) {
                // Dim the icon (items draw above fills in the same stratum).
                g.nextStratum();
                g.fill(x + 6, y + 6, x + 22, y + 22, 0x99140F0A);
            }
            if (over) {
                tooltip = tooltip(recipe, locked);
            }
        }
        g.disableScissor();
        if (content > height) {
            Draw.scrollbar(g, listX() + listW() - 4, top, height, content, scroll, THEME.accent);
        }
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, ORANGE);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private List<Component> tooltip(ForgeMenu.RecipeView recipe, boolean locked) {
        List<Component> lines = new ArrayList<>(recipe.output().getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player,
                TooltipFlag.NORMAL));
        lines.add(Component.empty());
        lines.add(Component.literal("§7Materials:"));
        for (int k = 0; k < recipe.inputs().size(); k++) {
            ItemStack input = recipe.inputs().get(k);
            int have = recipe.have().get(k);
            lines.add(Component.literal(" " + (have >= input.getCount() ? "§a" : "§c") + input.getCount() + "x ").append(input.getHoverName())
                    .append(Component.literal(" §8(" + have + ")")));
        }
        if (recipe.coins() > 0) {
            lines.add(Component.literal(String.format(Locale.ROOT, " §6%,d Coins", recipe.coins())));
        }
        lines.add(Component.empty());
        lines.add(Component.literal("§7Duration: §6" + time(recipe.seconds())));
        lines.add(Component.empty());
        lines.add(Component.literal(locked ? "§cRequires Heart of the Mountain " + recipe.hotm() + "!" : recipe.ready() ? "§eClick to forge!"
                : "§cYou don't have the materials!"));
        return lines;
    }

    private static String compact(int n) {
        return n >= 1000 ? (n / 1000) + "k" : String.valueOf(n);
    }

    /** "6h", "1d 4h", "45m 10s". */
    private static String time(long seconds) {
        long d = seconds / 86400;
        long h = seconds % 86400 / 3600;
        long m = seconds % 3600 / 60;
        long s = seconds % 60;
        if (d > 0) {
            return d + "d" + (h > 0 ? " " + h + "h" : "");
        }
        if (h > 0) {
            return h + "h" + (m > 0 ? " " + m + "m" : "");
        }
        if (m > 0) {
            return m + "m" + (s > 0 ? " " + s + "s" : "");
        }
        return s + "s";
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            ForgeMenu.View view = menu.view();
            for (int i = 0; i < view.slots().size(); i++) {
                ForgeMenu.SlotView slot = view.slots().get(i);
                if (SkyScreens.inside(event.x(), event.y(), slotX(i), topPos + SLOTS_Y, SLOT_W, SLOT_H)) {
                    if (!slot.output().isEmpty() && slot.left() <= 0) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ForgeMenu.CLAIM + i);
                        SkyScreens.click();
                    }
                    return true;
                }
            }
            int top = listTop();
            if (SkyScreens.inside(event.x(), event.y(), listX(), top, listW(), listHeight())) {
                double at = event.y() - top + scroll;
                int index = (int) (at / (ROW_H + GAP));
                if (index >= 0 && index < view.recipes().size() && at % (ROW_H + GAP) < ROW_H) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
                    SkyScreens.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * (ROW_H + GAP);
        return true;
    }
}
