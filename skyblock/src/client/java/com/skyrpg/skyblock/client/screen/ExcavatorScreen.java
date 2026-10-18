package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.glacite.ExcavatorMenu;
import com.skyrpg.skyblock.ui.Chip;
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
 * The Fossil Excavator, in the mining theme: the setup cards (your chisel with its charges and highlight chance, your
 * Suspicious Scrap, Start) and the dig, a 9 x 6 bed of dirt on a stone card drawn from the menu's data slots every frame,
 * with the chisel's charges and the fossil's progress bar above it.
 */
public final class ExcavatorScreen extends AbstractContainerScreen<ExcavatorMenu> {
    private static final ScreenTheme THEME = ScreenTheme.MINING;
    private static final int WIDTH = 300;
    private static final int HEIGHT = 228;
    private static final int TOP = SkyScreens.contentTop(THEME);
    private static final int TILE = 22;
    private static final int BOARD_Y = 55;
    private static final int BUTTON_H = 16;
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
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Fossil Excavator"), subtitle);
        List<Component> tooltip = view.mode() == ExcavatorMenu.SETUP ? setup(g, mouseX, mouseY) : dig(g, mouseX, mouseY);
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, GOLD);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private List<Component> tooltipOf(ItemStack stack) {
        return new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
    }

    /** The Suspicious Scrap's own item, or gravel when its definition is missing. */
    private static ItemStack scrapIcon() {
        ItemDefinition def = ItemRegistry.skyblock("SUSPICIOUS_SCRAP");
        return def == null ? new ItemStack(Items.GRAVEL) : SkyItems.create(def, 1);
    }

    // ---- setup --------------------------------------------------------------------------------------------------

    private List<Component> setup(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ExcavatorMenu.View view = view();
        List<Component> tooltip = null;
        int x = leftPos + THEME.inset + 6;
        int y = topPos + TOP + 4;
        int w = imageWidth - 2 * (THEME.inset + 6);
        boolean chisel = !view.chisel().isEmpty();
        boolean overChisel = SkyScreens.inside(mouseX, mouseY, x, y, w, 46);
        SkyScreens.card(g, THEME, x, y, w, 46, overChisel && chisel, !chisel);
        if (chisel) {
            g.fill(x + 6, y + 6, x + 40, y + 40, 0x55000000);
            Draw.item(g, view.chisel(), x + 7, y + 7, 2f);
            Draw.fit(g, font, view.chisel().getHoverName(), x + 48, y + 7, w - 56, 1f, 0.5f, Draw.Align.LEFT, THEME.text, true);
            List<Chip> chips = List.of(Chip.note(view.chisel(), view.charges() + " charges", true),
                    Chip.note(new ItemStack(Items.GLOWSTONE_DUST), view.highlight() + "% highlight chance", true));
            SkyScreens.chips(g, font, THEME, x + 48, y + 22, chips, 0.75f, x + w - 6);
            Draw.fit(g, font, Component.literal("Kept after every dig"), x + 48, y + 34, w - 56, 0.62f, 0.45f, Draw.Align.LEFT, THEME.dim, false);
            if (overChisel) {
                tooltip = tooltipOf(view.chisel());
            }
        } else {
            Draw.item(g, new ItemStack(Items.BARRIER), x + 12, y + 15, 1f);
            Draw.fit(g, font, Component.literal("No chisel! Get one to dig."), x + 36, y + 18, w - 44, 0.9f, 0.5f, Draw.Align.LEFT, Theme.RED, false);
        }
        int sy = y + 52;
        boolean scrap = view.scrap() > 0;
        ItemStack scrapIcon = scrapIcon();
        boolean overScrap = SkyScreens.inside(mouseX, mouseY, x, sy, w, 34);
        SkyScreens.card(g, THEME, x, sy, w, 34, overScrap && scrap, !scrap);
        g.fill(x + 6, sy + 7, x + 26, sy + 27, 0x55000000);
        Draw.item(g, scrapIcon, x + 8, sy + 9, 1f);
        Draw.fit(g, font, scrapIcon.getHoverName(), x + 34, sy + 7, w - 100, 0.9f, 0.5f, Draw.Align.LEFT, THEME.text, true);
        Draw.fit(g, font, Component.literal("Each dig uses one"), x + 34, sy + 19, w - 100, 0.65f, 0.45f, Draw.Align.LEFT, THEME.muted, false);
        Chip have = Chip.note(scrapIcon, String.valueOf(view.scrap()), scrap);
        SkyScreens.chip(g, font, THEME, x + w - 8 - SkyScreens.chipWidth(font, have, 1f), sy + 12, have, 1f);
        if (overScrap) {
            tooltip = tooltipOf(scrapIcon);
        }
        Draw.fit(g, font, Component.literal("Click tiles to uncover them; each click uses a charge."), leftPos + imageWidth / 2, sy + 42, w,
                0.7f, 0.45f, Draw.Align.CENTER, THEME.muted, false);
        Draw.fit(g, font, Component.literal("Uncover a whole fossil to keep it."), leftPos + imageWidth / 2, sy + 52, w,
                0.7f, 0.45f, Draw.Align.CENTER, THEME.muted, false);
        boolean ready = chisel && scrap;
        button(g, mouseX, mouseY, startX(), startY(), 120, "Start Excavation", ready);
        return tooltip;
    }

    private int startX() {
        return leftPos + (imageWidth - 120) / 2;
    }

    private int startY() {
        return topPos + imageHeight - 34;
    }

    // ---- the dig ------------------------------------------------------------------------------------------------

    private List<Component> dig(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        ExcavatorMenu.View view = view();
        boolean over = menu.status(3) == 1;
        int charges = menu.status(0);
        int fossil = menu.status(2);
        int bx = boardX();
        int boardW = ExcavatorMenu.WIDTH * TILE - 2;
        // The head: the chisel's charges on the left, the fossil you've found on the right with its progress bar.
        int hy = topPos + TOP + 1;
        if (over) {
            Draw.fit(g, font, Component.literal("Done!"), bx, hy, 60, 0.9f, 0.5f, Draw.Align.LEFT, THEME.good, true);
        } else {
            Chip charge = Chip.note(view.chisel().isEmpty() ? new ItemStack(Items.IRON_PICKAXE) : view.chisel(),
                    "Charges " + charges + " / " + menu.status(1), charges > 0);
            SkyScreens.chip(g, font, THEME, bx, hy, charge, 0.85f);
        }
        if (fossil >= 0) {
            Draw.fit(g, font, Component.literal(view.fossil() + ": " + fossil + "%"), bx + boardW, hy + 1, boardW / 2, 0.8f, 0.5f,
                    Draw.Align.RIGHT, fossil >= 100 ? THEME.good : GOLD, false);
        }
        SkyScreens.bar(g, THEME, bx, hy + 11, boardW, 5, fossil < 0 ? 0f : fossil / 100f, fossil >= 100 || over ? THEME.good : GOLD);
        SkyScreens.card(g, THEME, bx - 5, topPos + BOARD_Y - 4, boardW + 10, ExcavatorMenu.HEIGHT * TILE + 6, false, false);
        List<Component> tooltip = null;
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
                Draw.panel(g, tx, ty, TILE - 2, TILE - 2, THEME.track, THEME.track);
            } else if (value == 3) {
                Draw.panel(g, tx, ty, TILE - 2, TILE - 2, Draw.mix(Theme.CARD, BONE, 0.35f), BONE);
                Draw.item(g, BONE_ICON, tx + 2, ty + 2, 1f);
            } else {
                int picture = value - 1000;
                Draw.panel(g, tx, ty, TILE - 2, TILE - 2, Theme.CARD, THEME.good);
                if (picture >= 0 && picture < view.palette().size()) {
                    ItemStack stack = view.palette().get(picture);
                    Draw.item(g, stack, tx + 2, ty + 2, 1f);
                    if (hover) {
                        tooltip = tooltipOf(stack);
                    }
                }
            }
        }
        boolean finishHover = SkyScreens.inside(mouseX, mouseY, finishX(), finishY(), 100, BUTTON_H);
        button(g, mouseX, mouseY, finishX(), finishY(), 100, over ? "Close" : "Finish", true);
        if (!over && finishHover) {
            tooltip = List.of(Component.literal("Finish now").withColor(GOLD),
                    Component.literal("Keep what you've uncovered; the rest stays buried.").withColor(THEME.muted));
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
        return topPos + imageHeight - 30;
    }

    private void button(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, String text, boolean enabled) {
        SkyScreens.button(g, font, THEME, x, y, w, BUTTON_H, text, enabled, SkyScreens.inside(mouseX, mouseY, x, y, w, BUTTON_H));
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
            if (SkyScreens.inside(mx, my, startX(), startY(), 120, BUTTON_H)) {
                return press(ExcavatorMenu.START);
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (SkyScreens.inside(mx, my, finishX(), finishY(), 100, BUTTON_H)) {
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
