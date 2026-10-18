package com.skyrpg.skyblock.client.screen;

import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.quiver.Quiver;
import com.skyrpg.skyblock.quiver.QuiverMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** The Quiver: its arrows, the type your bows shoot (on a card under them), how full it is, and the inventory. */
public final class QuiverScreen extends AbstractContainerScreen<QuiverMenu> {
    private static final ScreenTheme THEME = ScreenTheme.SKYBLOCK;
    private static final int WIDTH = 182;
    private static final int HEIGHT = QuiverMenu.INV_Y + 3 * 18 + 4 + 18 + 10;

    public QuiverScreen(QuiverMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        int used = 0;
        int arrows = 0;
        for (int i = 0; i < Quiver.SIZE; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (!stack.isEmpty()) {
                used++;
                arrows += stack.getCount();
            }
        }
        SkyScreens.window(g, font, THEME, x, y, imageWidth, imageHeight, Component.literal("Quiver"),
                Component.literal(arrows + " arrow" + (arrows == 1 ? "" : "s")).withColor(Theme.AQUA));
        for (int i = 0; i < Quiver.SIZE; i++) {
            SkyScreens.well(g, THEME, x + QuiverMenu.GRID_X + (i % 9) * 18, y + QuiverMenu.GRID_Y + (i / 9) * 18);
        }

        // The card under the grid: the arrow your bows shoot, and how many slots are in use.
        int cx = x + THEME.inset;
        int cy = y + QuiverMenu.GRID_Y + 5 * 18 + 4;
        int cw = imageWidth - THEME.inset * 2;
        SkyScreens.card(g, THEME, cx, cy, cw, 16, false, false);
        ItemStack ammo = minecraft.player == null ? ItemStack.EMPTY : Quiver.ammo(minecraft.player);
        Component shooting;
        if (ammo.isEmpty()) {
            shooting = Component.literal("No arrows: put some in to shoot").withColor(THEME.dim);
        } else {
            Draw.item(g, ammo, cx + 3, cy, 1f);
            shooting = Component.literal("Shooting ").withColor(THEME.muted).append(ammo.getHoverName());
        }
        Draw.fit(g, font, shooting, cx + (ammo.isEmpty() ? 6 : 22), cy + 4, cw - 92, 0.75f, 0.45f, Draw.Align.LEFT, THEME.text, false);
        SkyScreens.bar(g, THEME, cx + cw - 62, cy + 5, 56, 5, used / (float) Quiver.SIZE, used >= Quiver.SIZE ? Theme.RED : Theme.AQUA);
        Draw.fit(g, font, Component.literal(used + "/" + Quiver.SIZE), cx + cw - 65, cy + 4, 26, 0.65f, 0.4f, Draw.Align.RIGHT, THEME.muted, false);
        SkyScreens.inventoryWells(g, THEME, menu, Quiver.SIZE, x, y);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }
}
