package com.skyrpg.minions.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.minions.MinionEntity;
import com.skyrpg.minions.MinionMenu;
import com.skyrpg.minions.MinionType;
import com.skyrpg.skyblock.client.screen.SkyScreens;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

/**
 * A minion, SkyRPG style: the minion itself turning on a glowing stand with a ring that fills up to its next action,
 * its storage (locked slots dark until a higher tier), and three buttons: collect everything, upgrade to the next
 * tier (with what it takes, ticked off when you have it), and pick it up.
 */
public final class MinionScreen extends AbstractContainerScreen<MinionMenu> {
    private static final int BUTTON_H = 16;

    public MinionScreen(MinionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, MinionMenu.WIDTH, MinionMenu.HEIGHT);
    }

    private int accent() {
        return switch (menu.view().category()) {
            case "mining" -> 0xFF9AA4B5;
            case "farming" -> 0xFFE5C34A;
            case "combat", "slayer" -> 0xFFFF6464;
            case "foraging" -> 0xFFC08A5A;
            case "fishing" -> Theme.BLUE;
            default -> Theme.PURPLE;
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        MinionMenu.View view = menu.view();
        int x = leftPos;
        int y = topPos;
        int accent = accent();
        SkyScreens.window(g, font, x, y, imageWidth, imageHeight, Component.literal(view.name()),
                Component.literal("Tier " + MinionType.roman(view.tier())).withColor(accent));

        // The stand: the minion turning over a glow, and the action timer underneath.
        int px = x + MinionMenu.PAD;
        int py = y + MinionMenu.TOP;
        Draw.panel(g, px, py, MinionMenu.LEFT_W, MinionMenu.LEFT_H, Theme.CARD, Theme.CARD_BORDER);
        g.fillGradient(px + 1, py + 1, px + MinionMenu.LEFT_W - 1, py + 66, Draw.withAlpha(accent, 0.22f), 0x00000000);
        g.fill(px + MinionMenu.LEFT_W / 2 - 16, py + 62, px + MinionMenu.LEFT_W / 2 + 16, py + 64, Draw.withAlpha(accent, 0.5f));
        Entity entity = minecraft.level.getEntity(view.entityId());
        float time = Util.getMillis() / 1000f;
        if (entity instanceof LivingEntity living) {
            Draw.entityTurning(g, px + 10, py + 4, px + MinionMenu.LEFT_W - 10, py + 64, 46, time * 30f % 360f, time * 20f, living);
        }
        float progress = menu.progress();
        int barX = px + 8;
        int barW = MinionMenu.LEFT_W - 16;
        Draw.bar(g, barX, py + 70, barW, 4, menu.full() ? 1f : progress, menu.full() ? Theme.RED : accent);
        String timer = menu.full() ? "Storage full!" : "Next action in " + String.format("%.1f", Math.max(0, view.actionSeconds() * (1 - progress))) + "s";
        Draw.fit(g, font, Component.literal(timer), px + MinionMenu.LEFT_W / 2, py + 77, barW, 1f, 0.7f, Draw.Align.CENTER,
                menu.full() ? Theme.RED : Theme.MUTED, false);
        Draw.fit(g, font, Component.literal("⏱ " + trim(view.actionSeconds()) + "s per action").withColor(Theme.TEXT),
                px + MinionMenu.LEFT_W / 2, py + 87, barW, 1f, 0.7f, Draw.Align.CENTER, Theme.TEXT, false);
        Draw.fit(g, font, Component.literal("Generated ").withColor(Theme.DIM).append(Component.literal(Msg.number(menu.generated())).withColor(accent)),
                px + MinionMenu.LEFT_W / 2, py + 96, barW, 1f, 0.7f, Draw.Align.CENTER, Theme.TEXT, false);

        // Storage.
        int gx = x + MinionMenu.GRID_X;
        int gy = y + MinionMenu.GRID_Y;
        int stored = 0;
        for (int i = 0; i < MinionEntity.MAX_SLOTS; i++) {
            stored += menu.getSlot(i).getItem().getCount();
        }
        Draw.text(g, font, "Storage", gx, gy - 11, Theme.DIM);
        String fill = Msg.number(stored) + "/" + Msg.number(view.storage());
        Draw.text(g, font, fill, gx + MinionMenu.GRID_COLUMNS * 18 - font.width(fill), gy - 11, menu.full() ? Theme.RED : Theme.MUTED);
        for (int i = 0; i < MinionEntity.MAX_SLOTS; i++) {
            int sx = gx + (i % MinionMenu.GRID_COLUMNS) * 18;
            int sy = gy + (i / MinionMenu.GRID_COLUMNS) * 18;
            if (i < view.slots()) {
                SkyScreens.well(g, sx, sy);
            } else {
                g.fill(sx, sy, sx + 18, sy + 18, 0xFF0E1118);
                g.fill(sx + 6, sy + 8, sx + 12, sy + 10, 0xFF1E2430);
            }
        }
        if (view.slots() < MinionEntity.MAX_SLOTS) {
            Draw.fit(g, font, Component.literal("More slots at higher tiers"), gx + MinionMenu.GRID_COLUMNS * 9, gy + 3 * 18 + 4,
                    MinionMenu.GRID_COLUMNS * 18, 1f, 0.6f, Draw.Align.CENTER, Theme.DIM, false);
        }

        // Buttons, with the upgrade's cost.
        int bx = x + MinionMenu.BUTTONS_X;
        int by = y + MinionMenu.TOP;
        button(g, font, bx, by, "Collect All", Theme.GREEN, stored > 0, mouseX, mouseY);
        boolean maxed = view.tier() >= view.maxTier();
        button(g, font, bx, by + BUTTON_H + 4, maxed ? "Max tier" : "Upgrade to " + MinionType.roman(view.tier() + 1), Theme.ACCENT,
                view.canUpgrade(), mouseX, mouseY);
        int cy = by + 2 * (BUTTON_H + 4);
        for (MinionMenu.Cost cost : view.upgrade()) {
            Draw.item(g, cost.item(), bx, cy, 0.75f);
            boolean enough = cost.have() >= cost.need();
            Draw.fit(g, font, Component.literal(Msg.compact(cost.have()) + "/" + Msg.compact(cost.need())), bx + 15, cy + 2, MinionMenu.BUTTON_W - 15,
                    1f, 0.6f, Draw.Align.LEFT, enough ? Theme.GREEN : Theme.RED, false);
            cy += 13;
        }
        button(g, font, bx, y + MinionMenu.TOP + MinionMenu.LEFT_H - BUTTON_H, "Pick Up", Theme.RED, true, mouseX, mouseY);

        // Fuel and upgrades: Hypixel's three slots, not working yet.
        int ux = x + MinionMenu.GRID_X;
        int uy = y + MinionMenu.GRID_Y + 3 * 18 + 22;
        Draw.text(g, font, "Fuel", ux, uy - 10, Theme.DIM);
        Draw.text(g, font, "Upgrades", ux + 30, uy - 10, Theme.DIM);
        for (int i = 0; i < 3; i++) {
            int sx = ux + (i == 0 ? 0 : 30 + (i - 1) * 20);
            g.fill(sx, uy, sx + 18, uy + 18, 0xFF0E1118);
            g.fill(sx + 1, uy + 1, sx + 17, uy + 17, 0xFF11151D);
        }

        SkyScreens.label(g, font, "Inventory", x + MinionMenu.INV_X, y + MinionMenu.INV_Y - 11);
        SkyScreens.inventoryWells(g, menu, MinionEntity.MAX_SLOTS, x, y);
    }

    private static String trim(float seconds) {
        return seconds == Math.floor(seconds) ? Integer.toString((int) seconds) : String.format("%.1f", seconds);
    }

    private void button(GuiGraphicsExtractor g, Font font, int bx, int by, String label, int color, boolean enabled, int mouseX, int mouseY) {
        boolean over = enabled && SkyScreens.inside(mouseX, mouseY, bx, by, MinionMenu.BUTTON_W, BUTTON_H);
        int shown = enabled ? color : Theme.DIM;
        Draw.panel(g, bx, by, MinionMenu.BUTTON_W, BUTTON_H, over ? Draw.mix(Theme.TRACK, shown, 0.25f) : Theme.TRACK,
                over ? shown : Draw.mix(Theme.TRACK, shown, 0.55f));
        Draw.fit(g, font, Component.literal(label), bx + MinionMenu.BUTTON_W / 2, by + 4, MinionMenu.BUTTON_W - 6, 1f, 0.65f,
                Draw.Align.CENTER, shown, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int bx = leftPos + MinionMenu.BUTTONS_X;
            int by = topPos + MinionMenu.TOP;
            int[][] buttons = {
                    {by, MinionMenu.COLLECT},
                    {by + BUTTON_H + 4, MinionMenu.UPGRADE},
                    {topPos + MinionMenu.TOP + MinionMenu.LEFT_H - BUTTON_H, MinionMenu.PICK_UP}};
            for (int[] button : buttons) {
                if (SkyScreens.inside(event.x(), event.y(), bx, button[0], MinionMenu.BUTTON_W, BUTTON_H)) {
                    if (button[1] == MinionMenu.UPGRADE && !menu.view().canUpgrade()) {
                        return true;
                    }
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button[1]);
                    SkyScreens.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (hoveredSlot == null && menu.getCarried().isEmpty()) {
            int bx = leftPos + MinionMenu.BUTTONS_X;
            int cy = topPos + MinionMenu.TOP + 2 * (BUTTON_H + 4);
            int uy = topPos + MinionMenu.GRID_Y + 3 * 18 + 22;
            if (SkyScreens.inside(mouseX, mouseY, leftPos + MinionMenu.GRID_X, uy, 70, 18)) {
                Draw.queueTooltip(List.of(Component.literal("Fuel and upgrades").withColor(Theme.ACCENT),
                        Component.literal("Coal, Enchanted Lava Buckets, Compactors, Auto Smelters... coming soon").withColor(Theme.MUTED)), Theme.ACCENT);
            }
            for (MinionMenu.Cost cost : menu.view().upgrade()) {
                if (SkyScreens.inside(mouseX, mouseY, bx, cy, MinionMenu.BUTTON_W, 12)) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(cost.item().getHoverName());
                    lines.add(Component.literal("You have " + Msg.number(cost.have()) + " of " + Msg.number(cost.need())).withColor(Theme.MUTED));
                    Draw.queueTooltip(lines, cost.have() >= cost.need() ? Theme.GREEN : Theme.RED);
                }
                cy += 13;
            }
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }
}
