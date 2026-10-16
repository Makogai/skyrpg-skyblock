package com.skyrpg.skyblock.client.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.storage.Wardrobe;
import com.skyrpg.skyblock.storage.WardrobeMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The Wardrobe, SkyRPG style: nine cards, each a model of you wearing that set (turning slowly, and towards the
 * mouse when you hover it), its four pieces underneath and a button to wear it. The set you have on glows green.
 * Hover a model to see what the whole set gives.
 */
public final class WardrobeScreen extends AbstractContainerScreen<WardrobeMenu> {
    private static final int GREEN_GLOW = 0x5534C759;
    private final Mannequin[] mannequins = new Mannequin[Wardrobe.SETS];

    public WardrobeScreen(WardrobeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WardrobeMenu.WIDTH, WardrobeMenu.HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        int worn = menu.equipped();
        int x = leftPos;
        int y = topPos;
        SkyScreens.window(g, font, x, y, imageWidth, imageHeight, Component.literal("Wardrobe"),
                Component.literal(worn >= 0 ? "Wearing set " + (worn + 1) : "Save up to " + Wardrobe.SETS + " armour sets"));
        float time = Util.getMillis() / 1000f;
        for (int set = 0; set < Wardrobe.SETS; set++) {
            int cx = x + WardrobeMenu.cardX(set);
            int cy = y + WardrobeMenu.TOP;
            int ch = WardrobeMenu.BUTTON_Y + WardrobeMenu.BUTTON_H + 3 - WardrobeMenu.TOP;
            boolean isWorn = set == worn;
            boolean empty = empty(set);
            boolean over = SkyScreens.inside(mouseX, mouseY, cx, cy, WardrobeMenu.CARD_W, ch);
            int border = isWorn ? Theme.GREEN : over ? Theme.ACCENT : Theme.CARD_BORDER;
            Draw.panel(g, cx, cy, WardrobeMenu.CARD_W, ch, over ? Theme.CARD_HOVER : Theme.CARD, border);
            // A glow behind the model: green on the set you wear, faint on the rest.
            g.fillGradient(cx + 1, cy + 1, cx + WardrobeMenu.CARD_W - 1, cy + WardrobeMenu.MODEL_H,
                    isWorn ? GREEN_GLOW : over ? 0x33F2B33D : 0x18FFFFFF, 0x00000000);
            Draw.text(g, font, Integer.toString(set + 1), cx + 3, cy + 3, isWorn ? Theme.GREEN : Theme.DIM);
            if (empty) {
                String text = "Empty";
                Draw.text(g, font, text, cx + (WardrobeMenu.CARD_W - font.width(text)) / 2, cy + WardrobeMenu.MODEL_H / 2 - 4, Theme.DIM);
            } else {
                float yaw = over ? Mth.clamp((mouseX - (cx + WardrobeMenu.CARD_W / 2f)) * 4f, -70f, 70f) : 25f * Mth.sin(time * 0.6f + set);
                Draw.entityTurning(g, cx + 2, cy + 4, cx + WardrobeMenu.CARD_W - 2, cy + WardrobeMenu.MODEL_H, 22, yaw, time * 20f, mannequin(set));
            }
            for (int piece = 0; piece < 4; piece++) {
                SkyScreens.armorWell(g, x + WardrobeMenu.slotX(set, piece), y + WardrobeMenu.slotY(piece) - 1, piece,
                        menu.piece(set, piece).isEmpty());
            }
            button(g, font, set, isWorn, empty, mouseX, mouseY);
        }
        SkyScreens.label(g, font, "Inventory", x + WardrobeMenu.INV_X, y + WardrobeMenu.INV_Y - 11);
        SkyScreens.inventoryWells(g, menu, Wardrobe.SETS * 4, x, y);
    }

    private void button(GuiGraphicsExtractor g, Font font, int set, boolean isWorn, boolean empty, int mouseX, int mouseY) {
        int bx = leftPos + WardrobeMenu.cardX(set) + 2;
        int by = topPos + WardrobeMenu.BUTTON_Y;
        int bw = WardrobeMenu.CARD_W - 4;
        boolean over = !empty && SkyScreens.inside(mouseX, mouseY, bx, by, bw, WardrobeMenu.BUTTON_H);
        int color = isWorn ? Theme.GREEN : empty ? Theme.DIM : Theme.ACCENT;
        Draw.panel(g, bx, by, bw, WardrobeMenu.BUTTON_H, over ? Draw.mix(Theme.TRACK, color, 0.22f) : Theme.TRACK,
                over ? color : Draw.mix(Theme.TRACK, color, 0.5f));
        String label = isWorn ? (over ? "Take off" : "Worn") : empty ? "Empty" : "Wear";
        Draw.fit(g, font, Component.literal(label), bx + bw / 2, by + 3, bw - 4, 1f, 0.7f, Draw.Align.CENTER, color, false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (hoveredSlot != null || !menu.getCarried().isEmpty()) {
            return;
        }
        for (int set = 0; set < Wardrobe.SETS; set++) {
            int cx = leftPos + WardrobeMenu.cardX(set);
            if (!empty(set) && SkyScreens.inside(mouseX, mouseY, cx, topPos + WardrobeMenu.TOP, WardrobeMenu.CARD_W, WardrobeMenu.MODEL_H)) {
                Draw.queueTooltip(summary(set), set == menu.equipped() ? Theme.GREEN : Theme.ACCENT);
            }
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    /** "Set 3", then everything the four pieces give together. */
    private List<Component> summary(int set) {
        Map<StatType, Double> total = new LinkedHashMap<>();
        for (int piece = 0; piece < 4; piece++) {
            ItemDefinition def = SkyItems.definition(menu.piece(set, piece));
            if (def != null) {
                def.stats().forEach((stat, value) -> total.merge(stat, value, Double::sum));
            }
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("Set " + (set + 1)).withColor(set == menu.equipped() ? Theme.GREEN : Theme.ACCENT));
        total.forEach((stat, value) -> lines.add(Component.literal(stat.symbol() + " ").withColor(stat.color())
                .append(Component.literal(stat.name() + " ").withColor(Theme.MUTED))
                .append(Component.literal(stat.formatBonus(value)).withColor(stat.color()))));
        if (total.isEmpty()) {
            lines.add(Component.literal("No stats").withColor(Theme.DIM));
        }
        lines.add(Component.literal(set == menu.equipped() ? "You're wearing it" : "Press Wear to put it on").withColor(Theme.DIM));
        return lines;
    }

    private boolean empty(int set) {
        if (set == menu.equipped()) {
            return false;
        }
        for (int piece = 0; piece < 4; piece++) {
            if (!menu.piece(set, piece).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int set = 0; set < Wardrobe.SETS; set++) {
                int bx = leftPos + WardrobeMenu.cardX(set) + 2;
                int by = topPos + WardrobeMenu.BUTTON_Y;
                if (!empty(set) && SkyScreens.inside(event.x(), event.y(), bx, by, WardrobeMenu.CARD_W - 4, WardrobeMenu.BUTTON_H)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, set);
                    SkyScreens.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** A model of you wearing this set (made once per screen, never added to the world). */
    private Mannequin mannequin(int set) {
        Mannequin mannequin = mannequins[set];
        if (mannequin == null) {
            mannequin = new Mannequin(minecraft.level, minecraft.player.getGameProfile(), -41_000 - set, minecraft.player);
            mannequins[set] = mannequin;
        }
        for (int piece = 0; piece < 4; piece++) {
            ItemStack want = menu.piece(set, piece);
            if (!ItemStack.matches(mannequin.getItemBySlot(SLOTS[piece]), want)) {
                mannequin.setItemSlot(SLOTS[piece], want.copy());
            }
        }
        return mannequin;
    }

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final class Mannequin extends RemotePlayer {
        Mannequin(ClientLevel level, GameProfile profile, int id, Player real) {
            super(level, profile);
            setId(id);
            entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, real.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));
        }
    }
}
