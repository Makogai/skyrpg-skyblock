package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.skyblock.pet.PetsMenu;
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
import net.minecraft.world.item.TooltipFlag;

/**
 * Hypixel's Pets menu: the pet you have out on a card at the top (with Put away), every other pet in a grid below.
 * Click a pet to summon it, right-click to take it out as an item; hovering shows its full tooltip.
 */
public final class PetsScreen extends AbstractContainerScreen<PetsMenu> {
    private static final ScreenTheme THEME = ScreenTheme.SKYBLOCK;
    private static final int WIDTH = 236;
    private static final int HEIGHT = 224;
    private static final int CARD_Y = 36;
    private static final int CARD_H = 38;
    private static final int GRID_Y = CARD_Y + CARD_H + 16;
    private static final int CELL = 22;
    private static final int COLUMNS = 9;

    public PetsScreen(PetsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int gridX() {
        return leftPos + (imageWidth - COLUMNS * CELL) / 2;
    }

    private int buttonX() {
        return leftPos + imageWidth - THEME.inset - 8 - 62;
    }

    private int buttonY() {
        return topPos + CARD_Y + 12;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        PetsMenu.View view = menu.view();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Pets"),
                Component.literal((view.stored().size() + (view.active().isEmpty() ? 0 : 1)) + " pets").withColor(THEME.muted));
        int cx = leftPos + THEME.inset;
        int cy = topPos + CARD_Y;
        int cw = imageWidth - THEME.inset * 2;
        ItemStack active = view.active();
        SkyScreens.card(g, THEME, cx, cy, cw, CARD_H, !active.isEmpty(), active.isEmpty());
        if (active.isEmpty()) {
            Draw.fit(g, font, Component.literal("No pet out. Click one below to summon it."), cx + cw / 2, cy + 15, cw - 10, 0.85f, 0.5f,
                    Draw.Align.CENTER, THEME.dim, false);
        } else {
            int rarity = rarityColor(active);
            g.fill(cx + 3, cy + 3, cx + 5, cy + CARD_H - 3, Draw.withAlpha(rarity, 0.9f));
            SkyScreens.well(g, THEME, cx + 9, cy + 10);
            g.item(active, cx + 10, cy + 11);
            Draw.fit(g, font, active.getHoverName(), cx + 32, cy + 9, cw - 114, 1f, 0.55f, Draw.Align.LEFT, THEME.text, false);
            Draw.fit(g, font, Component.literal("Summoned").withColor(THEME.good), cx + 32, cy + 21, cw - 114, 0.75f, 0.45f, Draw.Align.LEFT,
                    THEME.good, false);
            boolean over = SkyScreens.inside(mouseX, mouseY, buttonX(), buttonY(), 62, 14);
            SkyScreens.button(g, font, THEME, buttonX(), buttonY(), 62, 14, "Put away", true, over);
        }

        SkyScreens.label(g, font, THEME, "Your pets", leftPos + THEME.inset, topPos + GRID_Y - 11);
        List<ItemStack> stored = view.stored();
        int rows = Math.max(3, (stored.size() + COLUMNS - 1) / COLUMNS);
        for (int i = 0; i < rows * COLUMNS; i++) {
            int x = gridX() + (i % COLUMNS) * CELL;
            int y = topPos + GRID_Y + (i / COLUMNS) * CELL;
            if (y + CELL > topPos + imageHeight - 24) {
                break;
            }
            boolean over = i < stored.size() && SkyScreens.inside(mouseX, mouseY, x, y, CELL - 2, CELL - 2);
            SkyScreens.card(g, THEME, x, y, CELL - 2, CELL - 2, over, i >= stored.size());
            if (i < stored.size()) {
                g.item(stored.get(i), x + 2, y + 2);
                g.fill(x + 3, y + CELL - 5, x + CELL - 5, y + CELL - 4, Draw.withAlpha(rarityColor(stored.get(i)), 0.95f));
            }
        }
        Draw.fit(g, font, Component.literal("Click to summon · right-click to take out · right-click a pet item to add it"),
                leftPos + imageWidth / 2, topPos + imageHeight - 19, imageWidth - 24, 0.7f, 0.45f, Draw.Align.CENTER, THEME.dim, false);
    }

    private static int rarityColor(ItemStack pet) {
        com.skyrpg.items.ItemDefinition def = com.skyrpg.items.SkyItems.definition(pet);
        return def == null ? THEME.accent : def.rarity().color();
    }

    private int storedAt(double mouseX, double mouseY) {
        List<ItemStack> stored = menu.view().stored();
        for (int i = 0; i < stored.size(); i++) {
            int x = gridX() + (i % COLUMNS) * CELL;
            int y = topPos + GRID_Y + (i / COLUMNS) * CELL;
            if (SkyScreens.inside(mouseX, mouseY, x, y, CELL - 2, CELL - 2)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        ItemStack hovered = ItemStack.EMPTY;
        int index = storedAt(mouseX, mouseY);
        if (index >= 0) {
            hovered = menu.view().stored().get(index);
        } else if (!menu.view().active().isEmpty() && SkyScreens.inside(mouseX, mouseY, leftPos + THEME.inset + 9, topPos + CARD_Y + 10, 18, 18)) {
            hovered = menu.view().active();
        }
        if (!hovered.isEmpty()) {
            List<Component> lines = new ArrayList<>(hovered.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
            Draw.queueTooltip(lines, rarityColor(hovered));
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = storedAt(event.x(), event.y());
        if (index >= 0) {
            boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, right ? PetsMenu.TAKE + index : index);
            SkyScreens.click();
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && !menu.view().active().isEmpty()
                && SkyScreens.inside(event.x(), event.y(), buttonX(), buttonY(), 62, 14)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PetsMenu.DESPAWN);
            SkyScreens.click();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
