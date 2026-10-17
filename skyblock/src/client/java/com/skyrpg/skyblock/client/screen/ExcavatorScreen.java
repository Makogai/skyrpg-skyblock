package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.glacite.ExcavatorMenu;
import java.util.ArrayList;
import java.util.List;
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
 * The Fossil Excavator: the setup card (your chisel and Suspicious Scrap, Start) and the dig, a 9 x 6 bed of dirt drawn
 * from the menu's data slots every frame.
 */
public final class ExcavatorScreen extends AbstractContainerScreen<ExcavatorMenu> {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 214;
    private static final int TOP = 24;
    private static final int TILE = 22;
    private static final int BOARD_Y = 50;
    private static final int DIRT = 0xFF5A3E26;
    private static final int DIRT_HOVER = 0xFF6E4D30;
    private static final int DIRT_EDGE = 0xFF3A2716;
    private static final int GOLD = 0xFFFFAA00;
    private static final int BONE = 0xFFE6DCC0;
    private static final ItemStack BONE_ICON = new ItemStack(Items.BONE);

    public ExcavatorScreen(ExcavatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private ExcavatorMenu.View view() {
        return menu.view();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        ExcavatorMenu.View view = view();
        Component subtitle = Component.literal(view.mode() == ExcavatorMenu.SETUP ? "Fossil Research Center" : "Uncover what's in the dirt")
                .withColor(GOLD);
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("Fossil Excavator"), subtitle);
        List<Component> tooltip = view.mode() == ExcavatorMenu.SETUP ? setup(g, mouseX, mouseY) : dig(g, mouseX, mouseY);
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, GOLD);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    // ---- setup --------------------------------------------------------------------------------------------------

    private List<Component> setup(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ExcavatorMenu.View view = view();
        int x = leftPos + 20;
        int y = topPos + TOP + 16;
        int w = imageWidth - 40;
        boolean chisel = !view.chisel().isEmpty();
        Draw.panel(g, x, y, w, 40, Theme.CARD, chisel ? GOLD : Theme.CARD_BORDER);
        if (chisel) {
            Draw.item(g, view.chisel(), x + 8, y + 8, 1.5f);
            Draw.fit(g, font, view.chisel().getHoverName(), x + 40, y + 7, w - 48, 0.9f, 0.5f, Draw.Align.LEFT, Theme.TEXT, false);
            Draw.fit(g, font, Component.literal(view.charges() + " charges  ·  " + view.highlight() + "% highlight chance"), x + 40, y + 21, w - 48,
                    0.75f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);
        } else {
            Draw.fit(g, font, Component.literal("No chisel! Get one to dig."), x + 10, y + 15, w - 20, 0.9f, 0.5f, Draw.Align.LEFT, Theme.RED, false);
        }
        int sy = y + 48;
        Draw.panel(g, x, sy, w, 26, Theme.CARD, view.scrap() > 0 ? Theme.AQUA : Theme.CARD_BORDER);
        Draw.fit(g, font, Component.literal("Suspicious Scrap: " + view.scrap()), x + 10, sy + 9, w - 20, 0.85f, 0.5f, Draw.Align.LEFT,
                view.scrap() > 0 ? Theme.AQUA : Theme.RED, false);
        Draw.fit(g, font, Component.literal("Each dig uses one Suspicious Scrap. The chisel is kept."), leftPos + imageWidth / 2, sy + 34, w,
                0.7f, 0.45f, Draw.Align.CENTER, Theme.DIM, false);
        Draw.fit(g, font, Component.literal("Click tiles to uncover them; each click uses a charge. Uncover a whole fossil to keep it."),
                leftPos + imageWidth / 2, sy + 44, w, 0.7f, 0.45f, Draw.Align.CENTER, Theme.DIM, false);
        boolean ready = chisel && view.scrap() > 0;
        button(g, mouseX, mouseY, startX(), startY(), 120, "Start Excavation", ready ? Theme.GREEN : Theme.MUTED);
        return null;
    }

    private int startX() {
        return leftPos + (imageWidth - 120) / 2;
    }

    private int startY() {
        return topPos + imageHeight - 32;
    }

    // ---- the dig ------------------------------------------------------------------------------------------------

    private List<Component> dig(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ExcavatorMenu.View view = view();
        boolean over = menu.status(3) == 1;
        int charges = menu.status(0);
        int fossil = menu.status(2);
        String head = (over ? "Done!  " : "Chisel charges: " + charges + " / " + menu.status(1))
                + (fossil >= 0 ? "   ·   " + view.fossil() + ": " + fossil + "%" : "");
        Draw.fit(g, font, Component.literal(head), leftPos + imageWidth / 2, topPos + TOP + 6, imageWidth - 20, 0.85f, 0.5f, Draw.Align.CENTER,
                over ? Theme.GREEN : fossil >= 0 ? GOLD : Theme.TEXT, false);
        List<Component> tooltip = null;
        int bx = boardX();
        for (int i = 0; i < ExcavatorMenu.CELLS; i++) {
            int value = menu.cell(i);
            int tx = bx + i % ExcavatorMenu.WIDTH * TILE;
            int ty = topPos + BOARD_Y + i / ExcavatorMenu.WIDTH * TILE;
            boolean hover = SkyScreens.inside(mouseX, mouseY, tx, ty, TILE - 2, TILE - 2);
            if (value <= 1 || value == 4) {
                Draw.panel(g, tx, ty, TILE - 2, TILE - 2, hover && !over ? DIRT_HOVER : DIRT, value == 1 ? GOLD : value == 4 ? BONE : DIRT_EDGE);
                speckles(g, tx, ty, i);
                if (value == 1) {
                    Draw.fit(g, font, Component.literal("✦"), tx + (TILE - 2) / 2, ty + 6, TILE, 1f, 1f, Draw.Align.CENTER, GOLD, false);
                }
                continue;
            }
            if (value == 2) {
                Draw.panel(g, tx, ty, TILE - 2, TILE - 2, Theme.TRACK, Theme.TRACK);
            } else if (value == 3) {
                Draw.panel(g, tx, ty, TILE - 2, TILE - 2, Draw.mix(Theme.CARD, BONE, 0.35f), BONE);
                Draw.item(g, BONE_ICON, tx + 2, ty + 2, 1f);
            } else {
                int picture = value - 1000;
                Draw.panel(g, tx, ty, TILE - 2, TILE - 2, Theme.CARD, Theme.GREEN);
                if (picture >= 0 && picture < view.palette().size()) {
                    ItemStack stack = view.palette().get(picture);
                    Draw.item(g, stack, tx + 2, ty + 2, 1f);
                    if (hover) {
                        tooltip = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
                    }
                }
            }
        }
        button(g, mouseX, mouseY, finishX(), finishY(), 100, over ? "Close" : "Finish", over ? Theme.GREEN : Theme.MUTED);
        if (!over && SkyScreens.inside(mouseX, mouseY, finishX(), finishY(), 100, 16)) {
            tooltip = List.of(Component.literal("Finish now").withColor(GOLD),
                    Component.literal("Keep what you've uncovered; the rest stays buried.").withColor(Theme.MUTED));
        }
        return tooltip;
    }

    /** A few darker and lighter grains so a tile reads as dirt (the same for a tile every frame). */
    private static void speckles(GuiGraphicsExtractor g, int x, int y, int seed) {
        int h = seed * 0x9E3779B1;
        for (int k = 0; k < 6; k++) {
            h = h * 1103515245 + 12345;
            int px = x + 3 + Math.floorMod(h >> 8, TILE - 8);
            int py = y + 3 + Math.floorMod(h >> 16, TILE - 8);
            g.fill(px, py, px + 2, py + 2, k % 2 == 0 ? 0xFF46301C : 0xFF6E5034);
        }
    }

    private int boardX() {
        return leftPos + (imageWidth - ExcavatorMenu.WIDTH * TILE + 2) / 2;
    }

    private int finishX() {
        return leftPos + (imageWidth - 100) / 2;
    }

    private int finishY() {
        return topPos + imageHeight - 26;
    }

    private void button(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, String text, int color) {
        boolean over = SkyScreens.inside(mouseX, mouseY, x, y, w, 16);
        Draw.panel(g, x, y, w, 16, over ? Theme.CARD_HOVER : Theme.CARD, color);
        Draw.fit(g, font, Component.literal(text), x + w / 2, y + 4, w - 6, 0.75f, 0.5f, Draw.Align.CENTER, color, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        double mx = event.x();
        double my = event.y();
        if (view().mode() == ExcavatorMenu.SETUP) {
            if (SkyScreens.inside(mx, my, startX(), startY(), 120, 16)) {
                return press(ExcavatorMenu.START);
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (SkyScreens.inside(mx, my, finishX(), finishY(), 100, 16)) {
            if (menu.status(3) == 1) {
                onClose();
                return true;
            }
            return press(ExcavatorMenu.FINISH);
        }
        int bx = boardX();
        for (int i = 0; i < ExcavatorMenu.CELLS; i++) {
            int tx = bx + i % ExcavatorMenu.WIDTH * TILE;
            int ty = topPos + BOARD_Y + i / ExcavatorMenu.WIDTH * TILE;
            if (SkyScreens.inside(mx, my, tx, ty, TILE - 2, TILE - 2)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean press(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        SkyScreens.click();
        return true;
    }
}
