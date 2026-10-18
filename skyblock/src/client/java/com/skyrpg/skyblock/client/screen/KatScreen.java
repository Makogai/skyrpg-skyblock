package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.economy.Purse;
import com.skyrpg.skyblock.pet.KatMenu;
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
 * Kat, the pet sitter: on top the pet she's looking after (time left, or a Claim button), below your pets as cards with
 * what their next rarity costs (time, coins and items as chips). Click a pet to give it to her.
 */
public final class KatScreen extends AbstractContainerScreen<KatMenu> {
    private static final int WIDTH = 280;
    private static final int HEIGHT = 226;
    private static final ScreenTheme THEME = ScreenTheme.SKYBLOCK;
    private static final int TOP = SkyScreens.contentTop(THEME);
    private static final int CARD_H = 34;
    private static final int LIST_Y = TOP + CARD_H + 16;
    private static final int ROW_H = 28;

    private double scroll;

    public KatScreen(KatMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int listHeight() {
        return imageHeight - LIST_Y - 10;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        KatMenu.View view = menu.view();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Kat"), Component.literal("Pet Sitter"));
        List<Component> tooltip = null;
        int x = leftPos + THEME.inset;
        int w = imageWidth - 2 * THEME.inset;
        int y = topPos + TOP;
        boolean ready = !view.with().isEmpty() && view.left() <= 0;
        boolean overCard = ready && SkyScreens.inside(mouseX, mouseY, x, y, w, CARD_H);
        SkyScreens.card(g, THEME, x, y, w, CARD_H, overCard, false);
        if (ready) {
            Draw.outline(g, x, y, w, CARD_H, THEME.good);
        }
        g.fill(x + 5, y + 7, x + 27, y + 29, 0x55000000);
        if (view.with().isEmpty()) {
            Draw.item(g, new ItemStack(Items.NAME_TAG), x + 8, y + 10, 1f);
            Draw.fit(g, font, Component.literal("Kat is free"), x + 34, y + 8, w - 42, 0.9f, 0.5f, Draw.Align.LEFT, THEME.text, true);
            Draw.fit(g, font, Component.literal("Pick a pet below to upgrade its rarity while she looks after it."), x + 34, y + 21, w - 42, 0.65f,
                    0.45f, Draw.Align.LEFT, THEME.muted, false);
        } else {
            Draw.item(g, view.with(), x + 8, y + 10, 1f);
            Draw.fit(g, font, view.with().getHoverName(), x + 34, y + 7, w / 2, 0.85f, 0.5f, Draw.Align.LEFT, THEME.text, true);
            Draw.fit(g, font, Component.literal("Becoming " + cap(view.tier())), x + 34, y + 19, w / 2, 0.7f, 0.45f, Draw.Align.LEFT, THEME.accent,
                    false);
            if (ready) {
                SkyScreens.button(g, font, THEME, x + w - 62, y + 9, 56, 16, "Claim!", true, overCard);
            } else {
                Chip left = Chip.note(new ItemStack(Items.CLOCK), time(view.left()), true);
                int cw = SkyScreens.chipWidth(font, left, 0.9f);
                SkyScreens.chip(g, font, THEME, x + w - cw - 8, y + 10, left, 0.9f);
                Draw.fit(g, font, Component.literal("left"), x + w - 8, y + 22, 30, 0.6f, 0.4f, Draw.Align.RIGHT, THEME.dim, false);
            }
        }
        Draw.fit(g, font, Component.literal("Your pets"), x, topPos + LIST_Y - 11, 100, 0.8f, 0.5f, Draw.Align.LEFT, THEME.accent, false);
        List<KatMenu.PetView> pets = view.pets();
        Draw.fit(g, font, Component.literal(pets.size() + (pets.size() == 1 ? " pet" : " pets")), x + w, topPos + LIST_Y - 11, 60, 0.7f, 0.45f,
                Draw.Align.RIGHT, THEME.dim, false);
        int top = topPos + LIST_Y;
        int height = listHeight();
        scroll = Mth.clamp(scroll, 0, Math.max(0, pets.size() * (ROW_H + 2) - height));
        if (pets.isEmpty()) {
            SkyScreens.card(g, THEME, x, top, w, 30, false, true);
            Draw.fit(g, font, Component.literal("You have no pets yet. Hatch or buy one, then bring it here."), x + w / 2, top + 11, w - 12, 0.75f,
                    0.45f, Draw.Align.CENTER, THEME.muted, false);
        }
        g.enableScissor(leftPos + THEME.inset, top, leftPos + imageWidth - THEME.inset, top + height);
        for (int i = 0; i < pets.size(); i++) {
            KatMenu.PetView pet = pets.get(i);
            int ry = top + i * (ROW_H + 2) - (int) Math.round(scroll);
            if (ry + ROW_H < top || ry > top + height) {
                continue;
            }
            boolean over = SkyScreens.inside(mouseX, mouseY, x, ry, w, ROW_H) && mouseY >= top && mouseY < top + height;
            boolean can = pet.ready() && view.with().isEmpty();
            SkyScreens.card(g, THEME, x, ry, w, ROW_H, over && can, pet.next().isEmpty());
            g.fill(x + 4, ry + 4, x + 24, ry + 24, 0x55000000);
            Draw.item(g, pet.pet(), x + 6, ry + 6, 1f);
            int right = x + w - 6;
            if (!pet.next().isEmpty()) {
                int bw = 40;
                SkyScreens.button(g, font, THEME, right - bw, ry + (ROW_H - 14) / 2, bw, 14, "Give", can, over && can);
                right -= bw + 6;
            }
            Draw.fit(g, font, pet.pet().getHoverName(), x + 29, ry + 4, right - x - 29, 0.8f, 0.45f, Draw.Align.LEFT, THEME.text, false);
            if (pet.next().isEmpty()) {
                Draw.fit(g, font, Component.literal("Already at its best"), x + 29, ry + 16, w / 2, 0.65f, 0.4f, Draw.Align.LEFT, THEME.good, false);
            } else {
                String arrow = "→ " + cap(pet.next());
                Draw.fit(g, font, Component.literal(arrow), x + 29, ry + 16, 44, 0.65f, 0.4f, Draw.Align.LEFT, THEME.accent, false);
                int cx = x + 29 + Math.min(44, Math.round(font.width(arrow) * 0.65f)) + 6;
                List<Chip> chips = new ArrayList<>();
                chips.add(Chip.note(new ItemStack(Items.CLOCK), time(pet.seconds() * 1000), true));
                if (pet.coins() > 0) {
                    chips.add(Chip.price("coins", pet.coins(), Purse.get(minecraft.player)));
                }
                for (int k = 0; k < pet.items().size(); k++) {
                    ItemStack item = pet.items().get(k);
                    chips.add(Chip.item(item, item.getCount(), pet.have().get(k)));
                }
                SkyScreens.chips(g, font, THEME, cx, ry + 15, chips, 0.7f, right);
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
                    tooltip.add(Component.literal(!view.with().isEmpty() ? "§cKat is busy with another pet." : pet.ready()
                            ? "§eClick to give it to Kat!" : "§cYou don't have the items."));
                }
            }
        }
        g.disableScissor();
        if (pets.size() * (ROW_H + 2) > height) {
            Draw.scrollbar(g, leftPos + imageWidth - THEME.inset - 3, top, height, pets.size() * (ROW_H + 2), scroll, THEME.accent);
        }
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, THEME.accent);
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
            if (!view.with().isEmpty() && view.left() <= 0
                    && SkyScreens.inside(event.x(), event.y(), leftPos + THEME.inset, topPos + TOP, imageWidth - 2 * THEME.inset, CARD_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, KatMenu.CLAIM);
                SkyScreens.click();
                return true;
            }
            int top = topPos + LIST_Y;
            if (SkyScreens.inside(event.x(), event.y(), leftPos + THEME.inset, top, imageWidth - 2 * THEME.inset, listHeight())) {
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
