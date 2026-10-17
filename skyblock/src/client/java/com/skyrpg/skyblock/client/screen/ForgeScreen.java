package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.mining.ForgeMenu;
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
import net.minecraft.world.item.ItemStack;

/**
 * The Forge: the slot row on top (forging with time left, ready to take, empty, or locked), then every recipe, its
 * materials with what you have (green when you have enough) and its time. Click a recipe to forge it in a free slot;
 * click a finished slot to take the item.
 */
public final class ForgeScreen extends AbstractContainerScreen<ForgeMenu> {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 230;
    private static final int SLOT_W = 38;
    private static final int SLOT_H = 34;
    private static final int SLOTS_Y = 24;
    private static final int LIST_Y = 72;
    private static final int ROW_H = 24;
    private static final int ORANGE = 0xFFFF8A2A;

    private double scroll;

    public ForgeScreen(ForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int slotX(int i) {
        return leftPos + (imageWidth - ForgeMenu.MAX_SLOTS * (SLOT_W + 2)) / 2 + i * (SLOT_W + 2);
    }

    private int listTop() {
        return topPos + LIST_Y;
    }

    private int listHeight() {
        return imageHeight - LIST_Y - 6;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        ForgeMenu.View view = menu.view();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("The Forge"),
                Component.literal("HotM " + view.hotm()).withColor(Theme.MUTED));
        List<Component> tooltip = null;
        for (int i = 0; i < view.slots().size(); i++) {
            ForgeMenu.SlotView slot = view.slots().get(i);
            int x = slotX(i);
            int y = topPos + SLOTS_Y;
            boolean ready = !slot.output().isEmpty() && slot.left() <= 0;
            boolean over = SkyScreens.inside(mouseX, mouseY, x, y, SLOT_W, SLOT_H);
            int border = !slot.open() ? Theme.CARD_BORDER : ready ? Theme.GREEN : !slot.output().isEmpty() ? ORANGE : Theme.CARD_BORDER;
            Draw.panel(g, x, y, SLOT_W, SLOT_H, over && ready ? Theme.CARD_HOVER : Theme.CARD, border);
            String label;
            int color;
            if (!slot.open()) {
                label = "Locked";
                color = Theme.DIM;
            } else if (slot.output().isEmpty()) {
                label = "Empty";
                color = Theme.MUTED;
            } else {
                Draw.item(g, slot.output(), x + (SLOT_W - 16) / 2, y + 3, 1f);
                label = ready ? "Claim!" : time(slot.left() / 1000);
                color = ready ? Theme.GREEN : ORANGE;
                if (over) {
                    tooltip = new ArrayList<>();
                    tooltip.add(slot.output().getHoverName());
                    tooltip.add(Component.literal(ready ? "§aReady! Click to take it." : "§7Done in §6" + time(slot.left() / 1000)));
                }
            }
            Draw.fit(g, font, Component.literal(label), x + SLOT_W / 2, y + SLOT_H - 10, SLOT_W - 4, 0.65f, 0.45f, Draw.Align.CENTER, color, false);
            if (!slot.output().isEmpty() && !ready && slot.total() > 0) {
                Draw.bar(g, x + 3, y + 21, SLOT_W - 6, 2, 1f - (float) slot.left() / slot.total(), ORANGE);
            }
        }
        Draw.fit(g, font, Component.literal("Recipes"), leftPos + 10, topPos + LIST_Y - 10, 100, 0.75f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);

        List<ForgeMenu.RecipeView> recipes = view.recipes();
        int top = listTop();
        int height = listHeight();
        int content = recipes.size() * (ROW_H + 2);
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - height));
        g.enableScissor(leftPos + 4, top, leftPos + imageWidth - 4, top + height);
        for (int i = 0; i < recipes.size(); i++) {
            ForgeMenu.RecipeView recipe = recipes.get(i);
            int y = top + i * (ROW_H + 2) - (int) Math.round(scroll);
            if (y + ROW_H < top || y > top + height) {
                continue;
            }
            int x = leftPos + 8;
            int w = imageWidth - 16;
            boolean locked = recipe.hotm() > view.hotm();
            boolean over = SkyScreens.inside(mouseX, mouseY, x, Math.max(y, top), w, ROW_H) && mouseY < top + height;
            Draw.panel(g, x, y, w, ROW_H, over ? Theme.CARD_HOVER : Theme.CARD, recipe.ready() && !locked ? Theme.GREEN : Theme.CARD_BORDER);
            Draw.item(g, recipe.output(), x + 3, y + 4, 1f);
            Draw.fit(g, font, recipe.output().getHoverName(), x + 23, y + 3, w / 2, 0.8f, 0.5f, Draw.Align.LEFT, locked ? Theme.DIM : Theme.TEXT, false);
            String right = locked ? "HotM " + recipe.hotm() : time(recipe.seconds());
            Draw.fit(g, font, Component.literal(right), x + w - 4, y + 3, 60, 0.7f, 0.45f, Draw.Align.RIGHT, locked ? Theme.RED : ORANGE, false);
            // The materials, small, with have/need.
            int mx = x + 23;
            for (int k = 0; k < recipe.inputs().size(); k++) {
                ItemStack input = recipe.inputs().get(k);
                int have = recipe.have().get(k);
                Draw.item(g, input, mx, y + 12, 0.6f);
                String count = compact(have) + "/" + compact(input.getCount());
                Draw.fit(g, font, Component.literal(count), mx + 11, y + 14, 40, 0.55f, 0.4f, Draw.Align.LEFT,
                        have >= input.getCount() ? Theme.GREEN : Theme.RED, false);
                mx += 11 + Math.min(40, font.width(count) * 55 / 100) + 5;
            }
            if (recipe.coins() > 0) {
                Draw.fit(g, font, Component.literal(String.format(Locale.ROOT, "%,d coins", recipe.coins())), x + w - 4, y + 14, 70, 0.55f, 0.4f,
                        Draw.Align.RIGHT, 0xFFFFAA00, false);
            }
            if (over) {
                tooltip = tooltip(recipe, locked);
            }
        }
        g.disableScissor();
        if (content > height) {
            Draw.scrollbar(g, leftPos + imageWidth - 5, top, height, content, scroll, ORANGE);
        }
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, ORANGE);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private static List<Component> tooltip(ForgeMenu.RecipeView recipe, boolean locked) {
        List<Component> lines = new ArrayList<>();
        lines.add(recipe.output().getHoverName());
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
            if (SkyScreens.inside(event.x(), event.y(), leftPos + 8, top, imageWidth - 16, listHeight())) {
                double at = event.y() - top + scroll;
                int index = (int) (at / (ROW_H + 2));
                if (index >= 0 && index < view.recipes().size() && at % (ROW_H + 2) < ROW_H) {
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
        scroll -= scrollY * 24;
        return true;
    }
}
