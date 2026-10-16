package com.skyrpg.skyblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.storage.StoragePayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * The SkyBlock Menu, as on Hypixel: the first page of the K menu, a board of everything you can open, in Hypixel's
 * order. Each tile opens its page; the ones whose systems aren't built yet say so.
 */
public final class SkyBlockMenuPage implements MenuPage {
    private static final int COLUMNS = 3;
    private static final int TILE_H = 34;
    private static final int GAP = 5;

    /** A tile: what it opens (a K menu page id, or an action), its icon and its one-line description. */
    private record Tile(String name, String description, Item icon, Identifier page, Runnable action) {
        boolean ready() {
            return page != null || action != null;
        }
    }

    private static final List<Tile> TILES = List.of(
            new Tile("Your SkyBlock Profile", "Stats, level, purse", Items.PLAYER_HEAD, Identifier.parse("skyrpg_core:profile"), null),
            new Tile("Skills", "Farming, mining, combat...", Items.DIAMOND_SWORD, Identifier.parse("skyrpg_skills:skills"), null),
            new Tile("Collections", "Gather to unlock recipes", Items.PAINTING, Identifier.parse("skyrpg_progress:collections"), null),
            new Tile("Recipe Book", "Recipes you know", Items.BOOK, null, null),
            new Tile("SkyBlock Leveling", "Your level and rewards", Items.EXPERIENCE_BOTTLE, null, null),
            new Tile("Quest Log", "Quests and what's next", Items.WRITABLE_BOOK, Identifier.parse("skyrpg_progress:journal"), null),
            new Tile("Calendar and Events", "Today and coming events", Items.CLOCK, SkyRpgSkyblock.id("calendar"), null),
            new Tile("Storage", "Ender Chest pages", Items.CHEST, SkyRpgSkyblock.id("storage"), null),
            new Tile("Pets", "Choose your pet", Items.BONE, Identifier.parse("skyrpg_pets:pets"), null),
            new Tile("Wardrobe", "Saved armour sets", Items.LEATHER_CHESTPLATE, null,
                    () -> ClientPlayNetworking.send(new StoragePayloads.Open(-1))),
            new Tile("Fast Travel", "Warp around SkyBlock", Items.ENDER_EYE, SkyRpgSkyblock.id("fast_travel"), null),
            new Tile("Profile Management", "Switch or start one", Items.NAME_TAG, SkyRpgSkyblock.id("profiles"), null),
            new Tile("Personal Bank", "Coins safe from death", Items.GOLD_BLOCK, null, null),
            new Tile("Settings", "HUD, sounds, keys", Items.REDSTONE_TORCH, Identifier.parse("skyrpg_core:settings"), null),
            new Tile("SkyBlock Items", "Every item in SkyBlock", Items.ENDER_CHEST, SkyRpgSkyblock.id("items"), null));

    /** Tiles drawn this frame: x, y, w, h, index. */
    private final List<int[]> hits = new ArrayList<>();

    @Override
    public Identifier id() {
        return SkyRpgSkyblock.id("menu");
    }

    @Override
    public Component title() {
        return Component.literal("SkyBlock Menu");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Everything in one place. Pages in grey are still being built");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.NETHER_STAR);
    }

    @Override
    public int order() {
        return -100;
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        hits.clear();
        int tileW = (ctx.width() - GAP * (COLUMNS - 1)) / COLUMNS;
        int rows = (TILES.size() + COLUMNS - 1) / COLUMNS;
        int tileH = Math.min(TILE_H, (ctx.height() - GAP * (rows - 1)) / rows);
        for (int i = 0; i < TILES.size(); i++) {
            Tile tile = TILES.get(i);
            int x = ctx.x() + i % COLUMNS * (tileW + GAP);
            int y = ctx.y() + i / COLUMNS * (tileH + GAP);
            boolean over = ctx.hovered(x, y, tileW, tileH);
            boolean ready = tile.ready();
            Draw.panel(g, x, y, tileW, tileH, over && ready ? Theme.CARD_HOVER : Theme.CARD, over && ready ? Theme.ACCENT : Theme.CARD_BORDER);
            Draw.item(g, icon(ctx, tile), x + 5, y + (tileH - 16) / 2, 1f);
            Draw.fit(g, font, Component.literal(tile.name()), x + 26, y + tileH / 2 - 9, tileW - 30, 1f, 0.7f,
                    Draw.Align.LEFT, ready ? Theme.ACCENT : Theme.DIM, false);
            Draw.fit(g, font, Component.literal(ready ? tile.description() : "Coming soon"), x + 26, y + tileH / 2 + 2, tileW - 30, 1f, 0.7f,
                    Draw.Align.LEFT, ready ? Theme.MUTED : Theme.DIM, false);
            hits.add(new int[] {x, y, tileW, tileH, i});
        }
    }

    private static ItemStack icon(PageContext ctx, Tile tile) {
        ItemStack stack = new ItemStack(tile.icon());
        if (tile.icon() == Items.PLAYER_HEAD) {
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(ctx.player().getGameProfile()));
        }
        return stack;
    }

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (int[] hit : hits) {
            if (x >= hit[0] && x < hit[0] + hit[2] && y >= hit[1] && y < hit[1] + hit[3]) {
                Tile tile = TILES.get(hit[4]);
                if (tile.page() != null) {
                    SkyRpgMenu.open(tile.page());
                } else if (tile.action() != null) {
                    tile.action().run();
                }
                return true;
            }
        }
        return false;
    }
}
