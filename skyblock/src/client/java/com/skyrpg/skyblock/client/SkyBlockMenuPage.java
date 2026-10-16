package com.skyrpg.skyblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Purse;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.pets.Pets;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.profile.ProfileListPayload;
import com.skyrpg.skyblock.profile.Profiles;
import com.skyrpg.skyblock.storage.StoragePayloads;
import com.skyrpg.skyblock.time.SkyBlockTime;
import com.skyrpg.skyblock.travel.FastTravel;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * The SkyBlock Menu: the first page of the menu. On the left, you: your model (it looks at the mouse), your profile,
 * where you are, the date and your purse. On the right, everything you can open, grouped by colour (you, your gear,
 * the world, your account), each tile saying something live about it: the set you wear, how full your storage is,
 * your pet, how many warps you have. Grey tiles are still being built.
 */
public final class SkyBlockMenuPage implements MenuPage {
    private static final int HERO_W = 112;
    private static final int GAP = 5;
    private static final int COLUMNS = 2;

    /** A group of tiles and its colour. */
    private enum Group {
        YOU(Theme.ACCENT), GEAR(Theme.PURPLE), WORLD(Theme.AQUA), ACCOUNT(Theme.GREEN);

        final int color;

        Group(int color) {
            this.color = color;
        }
    }

    /** A tile: what it opens (a menu page, or an action; neither = not built yet), and a live line about it. */
    private record Tile(Group group, String name, Item icon, Identifier page, Runnable action, Supplier<String> live) {
        boolean ready() {
            return page != null || action != null;
        }
    }

    private static final List<Tile> TILES = List.of(
            new Tile(Group.YOU, "Your Profile", Items.PLAYER_HEAD, Identifier.parse("skyrpg_core:profile"), null,
                    () -> "Stats, level and more"),
            new Tile(Group.YOU, "Skills", Items.DIAMOND_SWORD, Identifier.parse("skyrpg_skills:skills"), null,
                    () -> "Level up as you play"),
            new Tile(Group.YOU, "Collections", Items.PAINTING, Identifier.parse("skyrpg_progress:collections"), null,
                    () -> "Gather to unlock recipes"),
            new Tile(Group.YOU, "SkyBlock Leveling", Items.EXPERIENCE_BOTTLE, SkyRpgSkyblock.id("leveling"), null,
                    () -> {
                        var player = net.minecraft.client.Minecraft.getInstance().player;
                        return player == null ? "" : "Level " + com.skyrpg.core.progress.SkyBlockXp.level(player) + " · "
                                + com.skyrpg.core.progress.SkyBlockXp.xp(player) % 100 + "/100";
                    }),
            new Tile(Group.GEAR, "Storage", Items.ENDER_CHEST, SkyRpgSkyblock.id("storage"), null, SkyBlockMenuPage::storageLine),
            new Tile(Group.GEAR, "Wardrobe", Items.LEATHER_CHESTPLATE, null,
                    () -> ClientPlayNetworking.send(new StoragePayloads.Open(-1)), SkyBlockMenuPage::wardrobeLine),
            new Tile(Group.GEAR, "Pets", Items.BONE, Identifier.parse("skyrpg_pets:pets"), null, SkyBlockMenuPage::petLine),
            new Tile(Group.GEAR, "SkyBlock Items", Items.CHEST, SkyRpgSkyblock.id("items"), null,
                    () -> Msg.number(ItemRegistry.items().stream().filter(def -> def.skyblock()).count()) + " items"),
            new Tile(Group.WORLD, "Fast Travel", Items.ENDER_EYE, SkyRpgSkyblock.id("fast_travel"), null, SkyBlockMenuPage::warpLine),
            new Tile(Group.WORLD, "Calendar", Items.CLOCK, SkyRpgSkyblock.id("calendar"), null, () -> SkyBlockTime.now().dayText()),
            new Tile(Group.WORLD, "Recipe Book", Items.BOOK, SkyRpgSkyblock.id("recipe_book"), null, RecipeBookPage::summary),
            new Tile(Group.ACCOUNT, "Profiles", Items.NAME_TAG, SkyRpgSkyblock.id("profiles"), null, SkyBlockMenuPage::profileLine),
            new Tile(Group.ACCOUNT, "Personal Bank", Items.GOLD_BLOCK, null, null, () -> ""),
            new Tile(Group.ACCOUNT, "Settings", Items.REDSTONE_TORCH, Identifier.parse("skyrpg_core:settings"), null,
                    () -> "HUD, sounds, keys"));

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
        return Component.literal("You, your gear, the world and your account. Grey tiles are on the way");
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
        hits.clear();
        int heroW = Math.min(HERO_W, ctx.width() / 3);
        drawHero(ctx, ctx.x(), ctx.y(), heroW, ctx.height());
        drawTiles(ctx, ctx.x() + heroW + GAP, ctx.y(), ctx.width() - heroW - GAP, ctx.height());
    }

    /** You: your model, name, profile, place, date and purse. */
    private void drawHero(PageContext ctx, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        int modelH = Math.max(60, h - 62);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + modelH, 0x33F2B33D, 0x00000000);
        // A pedestal glow under the feet.
        g.fill(x + w / 2 - 22, y + modelH - 4, x + w / 2 + 22, y + modelH - 2, 0x40F2B33D);
        Draw.entityFollowsMouse(g, x + 4, y + 6, x + w - 4, y + modelH - 2, Math.max(20, (modelH - 12) * 3 / 8),
                ctx.mouseX(), ctx.mouseY(), ctx.player());

        int ty = y + modelH + 4;
        Draw.fit(g, font, Component.literal(ctx.player().getPlainTextName()), x + w / 2, ty, w - 8, 1f, 0.7f, Draw.Align.CENTER, Theme.TEXT, true);
        ty += 11;
        String profile = ProfilesPage.profiles().stream().filter(ProfileListPayload.Entry::playing).map(ProfileListPayload.Entry::name)
                .findFirst().orElse("");
        if (!profile.isEmpty()) {
            int pw = font.width(profile) + 10;
            Draw.panel(g, x + (w - pw) / 2, ty - 1, pw, 11, Draw.withAlpha(Theme.ACCENT, 0.15f), Draw.withAlpha(Theme.ACCENT, 0.6f));
            Draw.text(g, font, profile, x + (w - pw) / 2 + 5, ty + 1, Theme.ACCENT);
        }
        ty += 14;
        String area = SidebarWidget.area();
        if (!area.isEmpty()) {
            Draw.fit(g, font, Component.literal("⏣ " + area), x + w / 2, ty, w - 8, 1f, 0.7f, Draw.Align.CENTER, Theme.GREEN, false);
        }
        ty += 10;
        SkyBlockTime.Date date = SkyBlockTime.now();
        Draw.fit(g, font, Component.literal(date.dayText() + "  " + (date.isDay() ? "☀ " : "☽ ") + date.clock()), x + w / 2, ty, w - 8,
                1f, 0.7f, Draw.Align.CENTER, Theme.MUTED, false);
        ty += 10;
        Draw.fit(g, font, Component.literal("Purse ").withColor(Theme.MUTED).append(Component.literal(Msg.number(Purse.get(ctx.player())))
                .withColor(Theme.ACCENT)), x + w / 2, ty, w - 8, 1f, 0.7f, Draw.Align.CENTER, Theme.ACCENT, false);
    }

    private void drawTiles(PageContext ctx, int x, int y, int w, int h) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int rows = (TILES.size() + COLUMNS - 1) / COLUMNS;
        int tileW = (w - GAP * (COLUMNS - 1)) / COLUMNS;
        int tileH = Math.max(18, Math.min(28, (h - (rows - 1) * 3) / rows));
        float time = Util.getMillis() / 1000f;
        for (int i = 0; i < TILES.size(); i++) {
            Tile tile = TILES.get(i);
            int tx = x + i % COLUMNS * (tileW + GAP);
            int ty = y + i / COLUMNS * (tileH + 3);
            boolean ready = tile.ready();
            boolean over = ready && ctx.hovered(tx, ty, tileW, tileH);
            int color = ready ? tile.group().color : Theme.DIM;
            Draw.panel(g, tx, ty, tileW, tileH, over ? Theme.CARD_HOVER : Theme.CARD, over ? color : Theme.CARD_BORDER);
            // The group's colour down the left edge; a soft wash of it on hover.
            g.fill(tx + 1, ty + 2, tx + 3, ty + tileH - 2, Draw.withAlpha(color, ready ? 0.9f : 0.3f));
            if (over) {
                g.fillGradient(tx + 3, ty + 1, tx + tileW - 1, ty + tileH - 1, Draw.withAlpha(color, 0.12f), 0x00000000);
            }
            float scale = over ? 1.1f + 0.05f * Mth.sin(time * 6f) : 1f;
            int iconSize = Math.round(16 * scale);
            Draw.item(g, icon(ctx, tile), tx + 7 - (iconSize - 16) / 2, ty + (tileH - iconSize) / 2, scale);
            int textX = tx + 28;
            int textW = tileW - 32;
            String live = ready ? tile.live().get() : "Coming soon";
            if (live.isEmpty()) {
                Draw.fit(g, font, Component.literal(tile.name()), textX, ty + (tileH - 8) / 2, textW, 1f, 0.7f, Draw.Align.LEFT,
                        ready ? Theme.TEXT : Theme.DIM, false);
            } else {
                Draw.fit(g, font, Component.literal(tile.name()), textX, ty + tileH / 2 - 9, textW, 1f, 0.7f, Draw.Align.LEFT,
                        ready ? (over ? color : Theme.TEXT) : Theme.DIM, false);
                Draw.fit(g, font, Component.literal(live), textX, ty + tileH / 2 + 1, textW, 1f, 0.7f, Draw.Align.LEFT,
                        ready ? Theme.MUTED : Theme.DIM, false);
            }
            hits.add(new int[] {tx, ty, tileW, tileH, i});
        }
    }

    private static ItemStack icon(PageContext ctx, Tile tile) {
        if (tile.icon() == Items.BONE) {
            ItemStack pet = ctx.player().getAttachedOrElse(Pets.ACTIVE, ItemStack.EMPTY);
            if (!pet.isEmpty()) {
                return pet;
            }
        }
        ItemStack stack = new ItemStack(tile.icon());
        if (tile.icon() == Items.PLAYER_HEAD) {
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(ctx.player().getGameProfile()));
        }
        return stack;
    }

    private static String storageLine() {
        StoragePayloads.Summary summary = StoragePage.summary();
        int items = summary.pages().stream().mapToInt(StoragePayloads.PagePreview::used).sum();
        return Msg.number(items) + " items in " + summary.unlocked() + " pages";
    }

    private static String wardrobeLine() {
        StoragePayloads.Summary summary = StoragePage.summary();
        if (summary.equipped() >= 0) {
            return "Wearing set " + (summary.equipped() + 1);
        }
        long saved = summary.wardrobe().stream().filter(stack -> !stack.isEmpty()).count();
        return saved == 0 ? "Save your armour sets" : saved + " sets saved";
    }

    private static String petLine() {
        var player = Minecraft.getInstance().player;
        ItemStack pet = player == null ? ItemStack.EMPTY : player.getAttachedOrElse(Pets.ACTIVE, ItemStack.EMPTY);
        return pet.isEmpty() ? "No pet out" : pet.getHoverName().getString();
    }

    private static String warpLine() {
        long open = FastTravelPage.warps().stream().filter(FastTravel.Entry::unlocked).count();
        return open + (open == 1 ? " warp" : " warps") + " unlocked";
    }

    private static String profileLine() {
        return ProfilesPage.profiles().size() + " of " + Profiles.MAX + " profiles";
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
