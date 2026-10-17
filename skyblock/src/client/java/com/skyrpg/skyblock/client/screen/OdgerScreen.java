package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.fishing.OdgerMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Odger: every trophy fish with its four tiers, your Trophy Fisher rank, filleting and the rank rewards. */
public final class OdgerScreen extends AbstractContainerScreen<OdgerMenu> {
    private static final int WIDTH = 330;
    private static final int HEIGHT = 232;
    private static final int[] TIER_COLORS = {0xFFB07A4A, 0xFFC8C8C8, 0xFFFFAA00, 0xFF55FFFF};
    private static final String[] TIER_LETTERS = {"B", "S", "G", "D"};
    private static final int ORANGE = 0xFFFF8A2A;

    public OdgerScreen(OdgerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int rowX(int i) {
        return leftPos + 10 + (i / 9) * 156;
    }

    private int rowY(int i) {
        return topPos + 30 + (i % 9) * 18;
    }

    private int buttonY() {
        return topPos + imageHeight - 24;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        OdgerMenu.View view = menu.view();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("Odger"),
                Component.literal("Trophy Fisher: " + OdgerMenu.RANKS.get(view.claimed())).withColor(ORANGE));
        String hover = null;
        for (int i = 0; i < view.rows().size(); i++) {
            OdgerMenu.Row row = view.rows().get(i);
            int x = rowX(i);
            int y = rowY(i);
            boolean any = row.counts().stream().anyMatch(c -> c > 0);
            Draw.panel(g, x, y, 150, 16, Theme.CARD, any ? (0xFF000000 | row.color()) : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal(row.name()), x + 4, y + 5, 70, 0.62f, 0.4f, Draw.Align.LEFT,
                    any ? 0xFF000000 | row.color() : Theme.DIM, false);
            for (int t = 0; t < 4; t++) {
                int count = row.counts().get(t);
                Draw.fit(g, font, Component.literal(TIER_LETTERS[t] + " " + (count > 999 ? "999+" : count)), x + 78 + t * 18, y + 5, 18, 0.55f, 0.4f,
                        Draw.Align.LEFT, count > 0 ? TIER_COLORS[t] : 0xFF505050, false);
            }
            if (SkyScreens.inside(mouseX, mouseY, x, y, 150, 16)) {
                hover = row.how();
            }
        }
        int y = buttonY();
        boolean canFillet = view.fillet() > 0;
        boolean filletOver = SkyScreens.inside(mouseX, mouseY, leftPos + 10, y, 150, 16);
        Draw.panel(g, leftPos + 10, y, 150, 16, filletOver && canFillet ? Theme.CARD_HOVER : Theme.CARD, canFillet ? ORANGE : Theme.CARD_BORDER);
        Draw.fit(g, font, Component.literal(canFillet ? "Fillet for " + String.format("%,d", view.fillet()) + " Magmafish" : "No trophy fish to fillet"),
                leftPos + 85, y + 4, 144, 0.7f, 0.45f, Draw.Align.CENTER, canFillet ? ORANGE : Theme.DIM, false);
        boolean canClaim = view.earned() > view.claimed();
        boolean claimOver = SkyScreens.inside(mouseX, mouseY, leftPos + 170, y, 150, 16);
        Draw.panel(g, leftPos + 170, y, 150, 16, claimOver && canClaim ? Theme.CARD_HOVER : Theme.CARD, canClaim ? Theme.GREEN : Theme.CARD_BORDER);
        String next = view.claimed() >= 4 ? "Master Trophy Fisher" : canClaim ? "Claim: " + OdgerMenu.RANKS.get(view.earned())
                : "Next: " + OdgerMenu.RANKS.get(view.claimed() + 1) + " (" + requirement(view.claimed() + 1) + ")";
        Draw.fit(g, font, Component.literal(next), leftPos + 245, y + 4, 144, 0.65f, 0.4f, Draw.Align.CENTER, canClaim ? Theme.GREEN : Theme.MUTED, false);
        if (hover != null) {
            Draw.queueTooltip(List.of(Component.literal(hover)), ORANGE);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private static String requirement(int rank) {
        return switch (rank) {
            case 1 -> "Bronze of 15 fish";
            case 2 -> "Silver of all 18";
            case 3 -> "Gold of all 18";
            default -> "Diamond of all 18";
        };
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int y = buttonY();
            if (SkyScreens.inside(event.x(), event.y(), leftPos + 10, y, 150, 16) && menu.view().fillet() > 0) {
                return press(OdgerMenu.FILLET);
            }
            if (SkyScreens.inside(event.x(), event.y(), leftPos + 170, y, 150, 16) && menu.view().earned() > menu.view().claimed()) {
                return press(OdgerMenu.CLAIM);
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
