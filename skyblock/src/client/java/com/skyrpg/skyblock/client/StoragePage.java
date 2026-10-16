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
 * Storage, as in Hypixel's SkyBlock Menu: the Wardrobe and the Ender Chest pages, each with a peek at what's inside.
 * Click one to open it. Ender chests in the world open this page too.
 */
public final class StoragePage implements MenuPage {
    private static final int WARDROBE_H = 40;
    private static final int GAP = 5;
    private static final int COLUMNS = 3;

    private static StoragePayloads.Summary summary = new StoragePayloads.Summary(Storage.STARTING_PAGES, List.of(), List.of(), -1);

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
        return Component.literal("Your Wardrobe and Ender Chest pages. Ender chests open this too");
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
        return WARDROBE_H + GAP + rows * (40 + GAP) - GAP;
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
        int cellH = Math.min(52, (ctx.height() - WARDROBE_H - GAP - GAP * (rows - 1)) / rows);
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
    }

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (int[] card : cards) {
            if (x >= card[0] && x < card[0] + card[2] && y >= card[1] && y < card[1] + card[3]) {
                ClientPlayNetworking.send(new StoragePayloads.Open(card[4]));
                return true;
            }
        }
        return false;
    }
}
