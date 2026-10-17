package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.crimson.KuudraMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Kuudra's screens: the tiers at the Gatekeeper, the keys at an Emissary, the chests after a win (rows with a button). */
public final class KuudraScreen extends AbstractContainerScreen<KuudraMenu> {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 210;
    private static final int RED = 0xFFE04848;

    public KuudraScreen(KuudraMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    /** Rows are 28 apart, closer when there are more than five (the Dojo's seven tests). */
    private int step() {
        return menu.view().rows().size() > 5 ? 22 : 28;
    }

    private int rowY(int i) {
        return topPos + 28 + i * step();
    }

    private int buttonX() {
        return leftPos + imageWidth - 70;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        KuudraMenu.View view = menu.view();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal(view.title()),
                Component.literal(view.subtitle()).withColor(RED));
        String hover = null;
        for (int i = 0; i < view.rows().size(); i++) {
            KuudraMenu.Row row = view.rows().get(i);
            int y = rowY(i);
            int color = 0xFF000000 | row.color();
            int tight = step() < 28 ? 1 : 0;
            Draw.panel(g, leftPos + 10, y, imageWidth - 20, step() - 4, Theme.CARD, row.enabled() ? color : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal(row.title()), leftPos + 16, y + 4 - tight * 2, imageWidth - 100, 0.8f, 0.5f, Draw.Align.LEFT,
                    row.enabled() ? color : Theme.MUTED, false);
            Draw.fit(g, font, Component.literal(row.detail()), leftPos + 16, y + 14 - tight * 4, imageWidth - 100, 0.6f, 0.35f, Draw.Align.LEFT,
                    Theme.DIM, false);
            if (SkyScreens.inside(mouseX, mouseY, leftPos + 16, y + 12, imageWidth - 100, 10) && font.width(row.detail()) * 0.6f > imageWidth - 100) {
                hover = row.detail();
            }
            int by = y + (step() - 4 - 16) / 2;
            boolean over = row.enabled() && SkyScreens.inside(mouseX, mouseY, buttonX(), by, 56, 16);
            Draw.panel(g, buttonX(), by, 56, 16, over ? Theme.CARD_HOVER : Theme.CARD, row.enabled() ? Theme.GREEN : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal(row.button()), buttonX() + 28, by + 4, 52, 0.75f, 0.45f, Draw.Align.CENTER,
                    row.enabled() ? Theme.GREEN : Theme.DIM, false);
        }
        Draw.fit(g, font, Component.literal(view.footer()), leftPos + imageWidth / 2, topPos + imageHeight - 14, imageWidth - 20, 0.62f, 0.4f,
                Draw.Align.CENTER, Theme.DIM, false);
        if (hover != null) {
            Draw.queueTooltip(List.of(Component.literal(hover)), RED);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            List<KuudraMenu.Row> rows = menu.view().rows();
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).enabled() && SkyScreens.inside(event.x(), event.y(), buttonX(), rowY(i) + (step() - 20) / 2, 56, 16)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, KuudraMenu.ROW + i);
                    SkyScreens.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
