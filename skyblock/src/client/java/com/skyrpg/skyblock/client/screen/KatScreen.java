package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.pet.KatMenu;
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
import net.minecraft.world.item.TooltipFlag;

/**
 * Kat, the pet sitter: on top the pet she's looking after (time left, or "Claim!"), below your pets with what their next
 * rarity costs. Click a pet to give it to her.
 */
public final class KatScreen extends AbstractContainerScreen<KatMenu> {
    private static final int WIDTH = 280;
    private static final int HEIGHT = 226;
    private static final int TOP = 24;
    private static final int CARD_H = 30;
    private static final int LIST_Y = TOP + CARD_H + 16;
    private static final int ROW_H = 26;
    private static final int PINK = 0xFFFF8AC8;

    private double scroll;

    public KatScreen(KatMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int listHeight() {
        return imageHeight - LIST_Y - 6;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        KatMenu.View view = menu.view();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("Kat"),
                Component.literal("Pet Sitter").withColor(PINK));
        List<Component> tooltip = null;
        int x = leftPos + 8;
        int w = imageWidth - 16;
        int y = topPos + TOP;
        boolean ready = !view.with().isEmpty() && view.left() <= 0;
        boolean overCard = ready && SkyScreens.inside(mouseX, mouseY, x, y, w, CARD_H);
        Draw.panel(g, x, y, w, CARD_H, overCard ? Theme.CARD_HOVER : Theme.CARD, view.with().isEmpty() ? Theme.CARD_BORDER : ready ? Theme.GREEN : PINK);
        if (view.with().isEmpty()) {
            Draw.fit(g, font, Component.literal("Kat isn't looking after a pet. Pick one below to upgrade its rarity."), x + 8, y + 11, w - 16, 0.7f,
                    0.45f, Draw.Align.LEFT, Theme.MUTED, false);
        } else {
            Draw.item(g, view.with(), x + 6, y + 7, 1f);
            Draw.fit(g, font, view.with().getHoverName(), x + 28, y + 5, w / 2, 0.8f, 0.5f, Draw.Align.LEFT, Theme.TEXT, false);
            Draw.fit(g, font, Component.literal("Becoming " + cap(view.tier())), x + 28, y + 17, w / 2, 0.65f, 0.45f, Draw.Align.LEFT, PINK, false);
            Draw.fit(g, font, Component.literal(ready ? "Claim!" : time(view.left())), x + w - 8, y + 11, 90, 0.85f, 0.5f, Draw.Align.RIGHT,
                    ready ? Theme.GREEN : PINK, false);
        }
        Draw.fit(g, font, Component.literal("Your pets"), leftPos + 10, topPos + LIST_Y - 10, 100, 0.75f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);
        List<KatMenu.PetView> pets = view.pets();
        int top = topPos + LIST_Y;
        int height = listHeight();
        scroll = Mth.clamp(scroll, 0, Math.max(0, pets.size() * (ROW_H + 2) - height));
        g.enableScissor(leftPos + 4, top, leftPos + imageWidth - 4, top + height);
        for (int i = 0; i < pets.size(); i++) {
            KatMenu.PetView pet = pets.get(i);
            int ry = top + i * (ROW_H + 2) - (int) Math.round(scroll);
            if (ry + ROW_H < top || ry > top + height) {
                continue;
            }
            boolean over = SkyScreens.inside(mouseX, mouseY, x, ry, w, ROW_H) && mouseY >= top && mouseY < top + height;
            boolean can = pet.ready() && view.with().isEmpty();
            Draw.panel(g, x, ry, w, ROW_H, over ? Theme.CARD_HOVER : Theme.CARD, can ? PINK : Theme.CARD_BORDER);
            Draw.item(g, pet.pet(), x + 5, ry + 5, 1f);
            Draw.fit(g, font, pet.pet().getHoverName(), x + 26, ry + 4, w / 2, 0.75f, 0.45f, Draw.Align.LEFT, Theme.TEXT, false);
            if (pet.next().isEmpty()) {
                Draw.fit(g, font, Component.literal("Already at its best"), x + 26, ry + 15, w / 2, 0.6f, 0.4f, Draw.Align.LEFT, Theme.DIM, false);
            } else {
                String cost = "→ " + cap(pet.next()) + "  ·  " + time(pet.seconds() * 1000)
                        + (pet.coins() > 0 ? String.format(Locale.ROOT, "  ·  %,d coins", pet.coins()) : "");
                Draw.fit(g, font, Component.literal(cost), x + 26, ry + 15, w - 90, 0.6f, 0.4f, Draw.Align.LEFT, PINK, false);
                int ix = x + w - 6;
                for (int k = pet.items().size() - 1; k >= 0; k--) {
                    ix -= 20;
                    Draw.item(g, pet.items().get(k), ix, ry + 5, 0.9f);
                }
            }
            if (over) {
                tooltip = new ArrayList<>(pet.pet().getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
                if (!pet.next().isEmpty()) {
                    tooltip.add(Component.empty());
                    tooltip.add(Component.literal("§dUpgrade to " + cap(pet.next())));
                    if (pet.coins() > 0) {
                        tooltip.add(Component.literal(String.format(Locale.ROOT, " §6%,d Coins", pet.coins())));
                    }
                    for (int k = 0; k < pet.items().size(); k++) {
                        ItemStack item = pet.items().get(k);
                        tooltip.add(Component.literal(" " + (pet.have().get(k) >= item.getCount() ? "§a" : "§c") + item.getCount() + "x ")
                                .append(item.getHoverName()));
                    }
                    tooltip.add(Component.literal(" §7Takes " + time(pet.seconds() * 1000)));
                    tooltip.add(Component.empty());
                    tooltip.add(Component.literal(!view.with().isEmpty() ? "§cKat is busy with another pet." : pet.ready() ? "§eClick to give it to Kat!"
                            : "§cYou don't have the items."));
                }
            }
        }
        g.disableScissor();
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, PINK);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String time(long millis) {
        long minutes = Math.max(0, millis / 60000);
        long days = minutes / 1440;
        long hours = minutes / 60 % 24;
        if (days > 0) {
            return days + "d" + (hours > 0 ? " " + hours + "h" : "");
        }
        return hours > 0 ? hours + "h " + minutes % 60 + "m" : Math.max(1, minutes) + "m";
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            KatMenu.View view = menu.view();
            if (!view.with().isEmpty() && view.left() <= 0 && SkyScreens.inside(event.x(), event.y(), leftPos + 8, topPos + TOP, imageWidth - 16, CARD_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, KatMenu.CLAIM);
                SkyScreens.click();
                return true;
            }
            int top = topPos + LIST_Y;
            if (SkyScreens.inside(event.x(), event.y(), leftPos + 8, top, imageWidth - 16, listHeight())) {
                int index = (int) ((event.y() - top + scroll) / (ROW_H + 2));
                if (index >= 0 && index < view.pets().size() && view.pets().get(index).ready() && view.with().isEmpty()) {
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
        scroll -= scrollY * 20;
        return true;
    }
}
