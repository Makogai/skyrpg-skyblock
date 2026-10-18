package com.skyrpg.skyblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.storage.Storage;
import com.skyrpg.skyblock.storage.StoragePayloads;
import com.skyrpg.skyblock.storage.Wardrobe;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Storage, as in Hypixel's SkyBlock Menu: the Wardrobe, the Ender Chest pages and the 18 backpack slots, each with a
 * peek at what's inside. Click one to open it; right-click an empty backpack to take it out. Ender chests in the world
 * open this page too.
 */
public final class StoragePage implements MenuPage {
    private static final int WARDROBE_H = 40;
    private static final int GAP = 5;
    private static final int COLUMNS = 3;

    private static final int BACKPACK_COLUMNS = 6;
    private static final int BACKPACK_H = 34;

    private static StoragePayloads.Summary summary = new StoragePayloads.Summary(Storage.STARTING_PAGES, List.of(), List.of(), -1, List.of());

    /** Clickable cards this frame: x, y, w, h, page (-1 = wardrobe). */
    private final List<int[]> cards = new ArrayList<>();

    static void set(StoragePayloads.Summary payload) {
        summary = payload;
    }

    static StoragePayloads.Summary summary() {
        return summary;
    }

    @Override
    public Identifier id() {
        return SkyRpgSkyblock.id("storage");
    }

    @Override
    public Component title() {
        return Component.literal("Storage");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Your Wardrobe, Ender Chest pages and backpacks. Ender chests open this too");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.ENDER_CHEST);
    }

    @Override
    public int order() {
        return 32;
    }

    /** Ender Chest cards at least 40 tall: the name, a row of items and the slot count. */
    @Override
    public int scrollHeight(int width) {
        int rows = (Storage.MAX_PAGES + COLUMNS - 1) / COLUMNS;
        int backpackRows = (Storage.BACKPACK_SLOTS + BACKPACK_COLUMNS - 1) / BACKPACK_COLUMNS;
        return WARDROBE_H + GAP + rows * (52 + GAP) + 14 + backpackRows * (BACKPACK_H + GAP) - GAP;
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        cards.clear();

        // The Wardrobe: one icon per set, the worn one in green.
        int y = ctx.y();
        boolean overWardrobe = ctx.hovered(ctx.x(), y, ctx.width(), WARDROBE_H);
        Draw.panel(g, ctx.x(), y, ctx.width(), WARDROBE_H, overWardrobe ? Theme.CARD_HOVER : Theme.CARD, overWardrobe ? Theme.ACCENT : Theme.CARD_BORDER);
        Draw.text(g, font, "Wardrobe", ctx.x() + 8, y + 6, Theme.ACCENT);
        String worn = summary.equipped() >= 0 ? "Wearing set " + (summary.equipped() + 1) : "Save up to " + Wardrobe.SETS + " armour sets";
        Draw.text(g, font, worn, ctx.x() + 8, y + 18, Theme.MUTED);
        int iconX = ctx.x() + ctx.width() - 8 - Wardrobe.SETS * 19;
        for (int set = 0; set < Wardrobe.SETS; set++) {
            int x = iconX + set * 19;
            g.fill(x, y + 11, x + 18, y + 29, set == summary.equipped() ? 0x6634C759 : Theme.TRACK);
            if (set < summary.wardrobe().size() && !summary.wardrobe().get(set).isEmpty()) {
                Draw.item(g, summary.wardrobe().get(set), x + 1, y + 12, 1f);
            }
        }
        cards.add(new int[] {ctx.x(), y, ctx.width(), WARDROBE_H, -1});

        // Ender Chest pages.
        int top = y + WARDROBE_H + GAP;
        int rows = (Storage.MAX_PAGES + COLUMNS - 1) / COLUMNS;
        int cellW = (ctx.width() - GAP * (COLUMNS - 1)) / COLUMNS;
        int cellH = 40;
        for (int page = 0; page < Storage.MAX_PAGES; page++) {
            int x = ctx.x() + page % COLUMNS * (cellW + GAP);
            int cy = top + page / COLUMNS * (cellH + GAP);
            boolean open = page < summary.unlocked();
            boolean over = open && ctx.hovered(x, cy, cellW, cellH);
            Draw.panel(g, x, cy, cellW, cellH, over ? Theme.CARD_HOVER : Theme.CARD, over ? Theme.PURPLE : Theme.CARD_BORDER);
            Draw.fitText(g, font, "Ender Chest " + (page + 1), x + 6, cy + 5, cellW - 12, open ? Theme.PURPLE : Theme.DIM);
            if (!open) {
                Draw.text(g, font, "Locked", x + 6, cy + 18, Theme.DIM);
                if (ctx.hovered(x, cy, cellW, cellH)) {
                    ctx.tooltip(List.of(Component.literal("Ender Chest " + (page + 1)).withColor(Theme.PURPLE),
                            Component.literal("Unlocks with SkyBlock Leveling").withColor(Theme.MUTED)), Theme.PURPLE);
                }
                continue;
            }
            StoragePayloads.PagePreview preview = page < summary.pages().size() ? summary.pages().get(page) : null;
            int used = preview == null ? 0 : preview.used();
            String fill = used + "/" + Storage.PAGE_SIZE + " slots";
            Draw.text(g, font, fill, x + cellW - 6 - font.width(fill), cy + cellH - 12, Theme.DIM);
            if (preview != null) {
                int ix = x + 5;
                for (ItemStack stack : preview.items()) {
                    if (ix + 16 > x + cellW - 4) {
                        break;
                    }
                    Draw.item(g, stack, ix, cy + 18, 1f);
                    ix += 17;
                }
            }
            if (used == 0) {
                Draw.text(g, font, "Empty", x + 6, cy + 18, Theme.DIM);
            }
            cards.add(new int[] {x, cy, cellW, cellH, page});
        }

        // The backpack slots: the backpack, how full it is, a peek inside.
        int by = top + rows * (cellH + GAP) + 2;
        Draw.text(g, font, "Backpacks", ctx.x(), by, Theme.ACCENT);
        by += 12;
        int bw = (ctx.width() - GAP * (BACKPACK_COLUMNS - 1)) / BACKPACK_COLUMNS;
        for (int slot = 0; slot < Storage.BACKPACK_SLOTS; slot++) {
            int x = ctx.x() + slot % BACKPACK_COLUMNS * (bw + GAP);
            int cy = by + slot / BACKPACK_COLUMNS * (BACKPACK_H + GAP);
            StoragePayloads.BackpackPreview preview = slot < summary.backpacks().size() ? summary.backpacks().get(slot) : null;
            boolean filled = preview != null && !preview.backpack().isEmpty();
            boolean over = ctx.hovered(x, cy, bw, BACKPACK_H);
            Draw.panel(g, x, cy, bw, BACKPACK_H, over && filled ? Theme.CARD_HOVER : Theme.CARD, over && filled ? Theme.ACCENT : Theme.CARD_BORDER);
            if (!filled) {
                Draw.fitText(g, font, "Slot " + (slot + 1), x + 5, cy + 5, bw - 10, Theme.DIM);
                Draw.fitText(g, font, "Empty", x + 5, cy + 18, bw - 10, Theme.DIM);
                if (over) {
                    ctx.tooltip(List.of(Component.literal("Backpack slot " + (slot + 1)).withColor(Theme.ACCENT),
                            Component.literal("Right-click a backpack in your hand to put it here").withColor(Theme.MUTED)), Theme.ACCENT);
                }
                continue;
            }
            Draw.item(g, preview.backpack(), x + 4, cy + 4, 1f);
            Draw.fitText(g, font, preview.used() + "/" + preview.size(), x + 22, cy + 8, bw - 26, preview.used() >= preview.size() ? Theme.RED : Theme.MUTED);
            int ix = x + 4;
            for (ItemStack stack : preview.items()) {
                if (ix + 10 > x + bw - 3) {
                    break;
                }
                Draw.item(g, stack, ix, cy + 22, 0.62f);
                ix += 11;
            }
            if (over) {
                ctx.tooltip(List.of(preview.backpack().getHoverName().copy(),
                        Component.literal(preview.used() + " of " + preview.size() + " slots used").withColor(Theme.MUTED),
                        Component.literal("Click to open").withColor(Theme.GREEN),
                        Component.literal(preview.used() == 0 ? "Right-click to take it out" : "Empty it to take it out").withColor(Theme.MUTED)),
                        Theme.ACCENT);
            }
            cards.add(new int[] {x, cy, bw, BACKPACK_H, Storage.BACKPACK_PAGE + slot});
        }
    }

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        boolean right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        if (button != InputConstants.MOUSE_BUTTON_LEFT && !right) {
            return false;
        }
        for (int[] card : cards) {
            if (x >= card[0] && x < card[0] + card[2] && y >= card[1] && y < card[1] + card[3]) {
                if (right) {
                    // right-click a backpack: take it out (the server checks it's empty)
                    if (card[4] >= Storage.BACKPACK_PAGE) {
                        ClientPlayNetworking.send(new StoragePayloads.Open(card[4] + Storage.BACKPACK_PAGE));
                        return true;
                    }
                    return false;
                }
                ClientPlayNetworking.send(new StoragePayloads.Open(card[4]));
                return true;
            }
        }
        return false;
    }
}
