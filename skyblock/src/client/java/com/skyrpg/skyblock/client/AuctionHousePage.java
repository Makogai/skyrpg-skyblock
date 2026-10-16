package com.skyrpg.skyblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.menu.SearchField;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Purse;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.auction.AuctionHouse;
import com.skyrpg.skyblock.client.screen.SkyScreens;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Auction House: browse what's for sale by category (search, sort, Buy It Now only), look at an auction to bid or
 * buy, put up an item of your own (pick it from your inventory, Buy It Now or bidding, price, duration, with the fee
 * shown), and Manage your auctions and bids to collect what you sold or won.
 */
public final class AuctionHousePage implements MenuPage {
    private static final int BUTTON_H = 13;
    private static final int CARD_H = 26;
    private static final String[] SORTS = {"Ending soon", "Lowest price", "Highest price"};

    private static AuctionHouse.Listing listing = new AuctionHouse.Listing(List.of(), 0, 1);
    private static AuctionHouse.Mine mine = new AuctionHouse.Mine(List.of(), List.of(), 0);
    private static long listingAt;
    private static long mineAt;

    public static void setListing(AuctionHouse.Listing received) {
        listing = received;
        listingAt = Util.getMillis();
    }

    public static void setMine(AuctionHouse.Mine received) {
        mine = received;
        mineAt = Util.getMillis();
    }

    private enum View {
        BROWSE, DETAIL, CREATE, MANAGE
    }

    private View view = View.BROWSE;
    private int category = -1;
    private int sort;
    private boolean binOnly;
    private int page;
    private double scroll;
    private long lastRequest;
    private final SearchField search = new SearchField("Search the Auction House");
    private boolean searchFocused;
    private String lastQuery = "";
    private AuctionHouse.View detail;
    private String bidText = "";
    private boolean bidFocused;
    // Creating.
    private int pickedSlot = -1;
    private boolean createBin = true;
    private String priceText = "";
    private boolean priceFocused;
    private int duration = 3;
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();

    @Override
    public Identifier id() {
        return SkyRpgSkyblock.id(AuctionHouse.PAGE);
    }

    @Override
    public Component title() {
        return Component.literal("Auction House");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Buy and sell items with other players: Buy It Now or bidding");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.GOLDEN_HORSE_ARMOR);
    }

    @Override
    public int order() {
        return 981;
    }

    @Override
    public void opened() {
        browse();
        ClientPlayNetworking.send(new AuctionHouse.Act(AuctionHouse.Act.MINE, 0, 0, 0, false, 0));
    }

    private void browse() {
        lastRequest = Util.getMillis();
        lastQuery = search.text();
        ClientPlayNetworking.send(new AuctionHouse.Browse(category, search.text(), sort, binOnly, page));
    }

    private void act(int kind, long id, long amount, int slot, boolean bin, int duration) {
        ClientPlayNetworking.send(new AuctionHouse.Act(kind, id, amount, slot, bin, duration));
        SkyScreens.click();
        lastRequest = 0;
    }

    private static String time(long millis) {
        long s = millis / 1000;
        if (s <= 0) {
            return "Ended";
        }
        if (s >= 86_400) {
            return s / 86_400 + "d " + s % 86_400 / 3600 + "h";
        }
        if (s >= 3600) {
            return s / 3600 + "h " + s % 3600 / 60 + "m";
        }
        if (s >= 60) {
            return s / 60 + "m " + s % 60 + "s";
        }
        return s + "s";
    }

    private static long left(AuctionHouse.View v, long receivedAt) {
        return v.leftMillis() - (Util.getMillis() - receivedAt);
    }

    private static int rarityColor(ItemStack stack) {
        ItemDefinition def = SkyItems.definition(stack);
        return def == null ? Theme.TEXT : def.rarity().color();
    }

    private static long typed(String text) {
        try {
            String t = text.trim().toLowerCase();
            double mult = 1;
            if (t.endsWith("k")) {
                mult = 1_000;
                t = t.substring(0, t.length() - 1);
            } else if (t.endsWith("m")) {
                mult = 1_000_000;
                t = t.substring(0, t.length() - 1);
            } else if (t.endsWith("b")) {
                mult = 1_000_000_000;
                t = t.substring(0, t.length() - 1);
            }
            return t.isEmpty() ? 0 : (long) (Double.parseDouble(t) * mult);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void button(PageContext ctx, int x, int y, int w, String label, int color, boolean enabled, Runnable action) {
        GuiGraphicsExtractor g = ctx.graphics();
        boolean over = enabled && ctx.hovered(x, y, w, BUTTON_H);
        int shown = enabled ? color : Theme.DIM;
        Draw.panel(g, x, y, w, BUTTON_H, over ? Draw.mix(Theme.TRACK, shown, 0.25f) : Theme.TRACK, over ? shown : Draw.mix(Theme.TRACK, shown, 0.55f));
        Draw.fit(g, ctx.font(), Component.literal(label), x + w / 2, y + 3, w - 4, 0.85f, 0.45f, Draw.Align.CENTER, shown, false);
        if (enabled) {
            hit(x, y, w, BUTTON_H, action);
        }
    }

    private void hit(int x, int y, int w, int h, Runnable action) {
        hits.add(new int[] {x, y, w, h, actions.size()});
        actions.add(action);
    }

    /** A box for typed coins ("2.5m" works). */
    private void coinField(PageContext ctx, int x, int y, int w, String text, boolean on, String placeholder, Runnable focus) {
        GuiGraphicsExtractor g = ctx.graphics();
        Draw.panel(g, x, y, w, BUTTON_H, Theme.INPUT, on ? Theme.ACCENT : ctx.hovered(x, y, w, BUTTON_H) ? Theme.MUTED : Theme.CARD_BORDER);
        boolean caret = on && (Util.getMillis() / 530) % 2 == 0;
        Component shown = text.isEmpty() ? Component.literal(placeholder).withColor(Theme.DIM) : Component.literal(text + (caret ? "|" : "")).withColor(Theme.TEXT);
        Draw.fit(g, ctx.font(), shown, x + 4, y + 3, w - 6, 0.85f, 0.45f, Draw.Align.LEFT, Theme.TEXT, false);
        hit(x, y, w, BUTTON_H, focus);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(PageContext ctx) {
        hits.clear();
        actions.clear();
        if (view == View.BROWSE && (Util.getMillis() - lastRequest > 5000 || !search.text().equals(lastQuery))) {
            browse();
        }
        if ((view == View.MANAGE || view == View.DETAIL) && Util.getMillis() - lastRequest > 2000) {
            lastRequest = Util.getMillis();
            ClientPlayNetworking.send(new AuctionHouse.Act(AuctionHouse.Act.MINE, 0, 0, 0, false, 0));
            ClientPlayNetworking.send(new AuctionHouse.Browse(category, lastQuery, sort, binOnly, page));
        }
        switch (view) {
            case BROWSE -> browseView(ctx);
            case DETAIL -> detailView(ctx);
            case CREATE -> createView(ctx);
            case MANAGE -> manageView(ctx);
        }
    }

    private void browseView(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();

        // Categories.
        List<String> tabs = new ArrayList<>();
        tabs.add("All");
        tabs.addAll(AuctionHouse.CATEGORIES);
        int natural = 0;
        for (String tab : tabs) {
            natural += font.width(tab) + 10 + 2;
        }
        float squeeze = Math.min(1f, w / (float) natural);
        int tx = x;
        for (int i = 0; i < tabs.size(); i++) {
            int tw = (int) ((font.width(tabs.get(i)) + 10) * squeeze);
            int index = i - 1;
            boolean on = category == index;
            boolean over = ctx.hovered(tx, y, tw, 13);
            Draw.panel(g, tx, y, tw, 13, on ? Draw.mix(Theme.CARD, Theme.ACCENT, 0.25f) : over ? Theme.CARD_HOVER : Theme.CARD,
                    on || over ? Theme.ACCENT : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal(tabs.get(i)), tx + tw / 2, y + 3, tw - 4, 0.85f, 0.45f, Draw.Align.CENTER, on ? Theme.ACCENT : Theme.MUTED, false);
            hit(tx, y, tw, 13, () -> {
                category = index;
                page = 0;
                scroll = 0;
                browse();
                SkyScreens.click();
            });
            tx += tw + 2;
        }

        // Search, sort, BIN only, create, manage, pages.
        int ry = y + 16;
        int searchW = Math.min(130, w / 3);
        search.draw(g, font, x, ry, searchW, searchFocused, ctx.hovered(x, ry, searchW, SearchField.HEIGHT), ctx.mouseX());
        hit(x, ry, searchW, SearchField.HEIGHT, () -> {
            searchFocused = true;
            bidFocused = false;
            priceFocused = false;
            if (!search.isEmpty() && SearchField.overClear(x, searchW, ctx.mouseX())) {
                search.clear();
            }
        });
        int bx = x + searchW + 4;
        int rest = w - searchW - 4;
        int bw = (rest - 4 * 3) / 5;
        button(ctx, bx, ry + 1, bw, SORTS[sort], Theme.MUTED, true, () -> {
            sort = (sort + 1) % SORTS.length;
            browse();
        });
        button(ctx, bx + bw + 3, ry + 1, bw, binOnly ? "BIN only ✔" : "BIN only", binOnly ? Theme.GREEN : Theme.MUTED, true, () -> {
            binOnly = !binOnly;
            page = 0;
            browse();
        });
        button(ctx, bx + 2 * (bw + 3), ry + 1, bw, "Create", Theme.ACCENT, true, () -> {
            view = View.CREATE;
            pickedSlot = -1;
            priceText = "";
        });
        long ready = mine.auctions().stream().filter(a -> a.status() != AuctionHouse.View.ACTIVE).count()
                + mine.bids().stream().filter(a -> a.status() == AuctionHouse.View.WON).count() + (mine.refunds() > 0 ? 1 : 0);
        button(ctx, bx + 3 * (bw + 3), ry + 1, bw, ready > 0 ? "Manage (" + ready + ")" : "Manage", ready > 0 ? Theme.GREEN : Theme.MUTED, true, () -> {
            view = View.MANAGE;
            lastRequest = 0;
        });
        int px = bx + 4 * (bw + 3);
        int pw = x + w - px;
        button(ctx, px, ry + 1, 12, "◀", Theme.MUTED, listing.page() > 0, () -> {
            page = listing.page() - 1;
            scroll = 0;
            browse();
        });
        Draw.fit(g, font, Component.literal((listing.page() + 1) + "/" + listing.pages()), px + pw / 2, ry + 4, pw - 26, 0.8f, 0.45f, Draw.Align.CENTER,
                Theme.MUTED, false);
        button(ctx, px + pw - 12, ry + 1, 12, "▶", Theme.MUTED, listing.page() < listing.pages() - 1, () -> {
            page = listing.page() + 1;
            scroll = 0;
            browse();
        });

        // The auctions.
        int top = ry + SearchField.HEIGHT + 4;
        int height = y + ctx.height() - top;
        List<AuctionHouse.View> auctions = listing.auctions();
        if (auctions.isEmpty()) {
            Draw.fit(g, font, Component.literal("Nothing for sale here yet. Be the first: Create an auction."), x + w / 2, top + 30, w - 20, 1f, 0.5f,
                    Draw.Align.CENTER, Theme.DIM, false);
            return;
        }
        int columns = w >= 300 ? 3 : 2;
        int cw = (w - (columns - 1) * 4) / columns;
        int rows = (auctions.size() + columns - 1) / columns;
        int content = rows * (CARD_H + 3);
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - height));
        g.enableScissor(x, top, x + w, top + height);
        for (int i = 0; i < auctions.size(); i++) {
            AuctionHouse.View a = auctions.get(i);
            int cx = x + i % columns * (cw + 4);
            int cy = top + i / columns * (CARD_H + 3) - (int) scroll;
            if (cy + CARD_H < top || cy > top + height) {
                continue;
            }
            card(ctx, cx, cy, cw, a, listingAt, top, height);
        }
        g.disableScissor();
        if (content > height) {
            Draw.scrollbar(g, x + w - 2, top, height, content, scroll, Theme.ACCENT);
        }
    }

    private void card(PageContext ctx, int cx, int cy, int cw, AuctionHouse.View a, long receivedAt, int top, int height) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int color = rarityColor(a.item());
        boolean visible = cy >= top && cy + CARD_H <= top + height;
        boolean over = visible && ctx.hovered(cx, cy, cw, CARD_H);
        Draw.panel(g, cx, cy, cw, CARD_H, over ? Theme.CARD_HOVER : Theme.CARD, over ? color : Theme.CARD_BORDER);
        g.fill(cx + 1, cy + 2, cx + 3, cy + CARD_H - 2, color);
        Draw.item(g, a.item(), cx + 5, cy + 5, 1f);
        Draw.fit(g, font, a.item().getHoverName(), cx + 24, cy + 4, cw - 28, 0.85f, 0.45f, Draw.Align.LEFT, color, false);
        Component price = a.bin() ? Component.literal("BIN ").withColor(Theme.GREEN).append(Component.literal(Msg.compact(a.price())).withColor(Theme.ACCENT))
                : Component.literal(a.bids() > 0 ? "Bid " : "Starts ").withColor(Theme.AQUA).append(Component.literal(Msg.compact(a.price())).withColor(Theme.ACCENT))
                        .append(Component.literal(a.bids() > 0 ? " · " + a.bids() : "").withColor(Theme.DIM));
        Draw.fit(g, font, price, cx + 24, cy + 15, cw - 70, 0.8f, 0.45f, Draw.Align.LEFT, Theme.TEXT, false);
        Draw.fit(g, font, Component.literal(time(left(a, receivedAt))), cx + cw - 4, cy + 15, 44, 0.75f, 0.45f, Draw.Align.RIGHT, Theme.MUTED, false);
        if (over) {
            List<Component> lines = new ArrayList<>(a.item().getTooltipLines(Item.TooltipContext.of(ctx.player().level()), ctx.player(), TooltipFlag.NORMAL));
            lines.add(Component.empty());
            lines.add(Component.literal("Seller: " + a.seller()).withColor(Theme.MUTED));
            lines.add(Component.literal("Click to " + (a.bin() ? "buy" : "bid")).withColor(Theme.DIM));
            ctx.tooltip(lines, color);
            hit(cx, cy, cw, CARD_H, () -> {
                detail = a;
                bidText = "";
                view = View.DETAIL;
                lastRequest = 0;
                SkyScreens.click();
            });
        }
    }

    private void detailView(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        // Keep up with the newest copy of it (the price moves when someone bids).
        for (AuctionHouse.View fresh : listing.auctions()) {
            if (fresh.id() == detail.id()) {
                detail = fresh;
            }
        }
        AuctionHouse.View a = detail;
        button(ctx, x, y, 34, "◀ Back", Theme.MUTED, true, () -> view = View.BROWSE);
        int color = rarityColor(a.item());
        int leftW = w / 2 - 4;
        Draw.panel(g, x, y + 16, leftW, ctx.height() - 16, Theme.CARD, Draw.mix(Theme.CARD_BORDER, color, 0.5f));
        List<Component> lines = a.item().getTooltipLines(Item.TooltipContext.of(ctx.player().level()), ctx.player(), TooltipFlag.NORMAL);
        int ly = y + 22;
        Draw.item(g, a.item(), x + 6, ly, 1.25f);
        for (int i = 0; i < lines.size() && ly < y + ctx.height() - 9; i++) {
            Draw.fit(g, font, lines.get(i), x + (i == 0 ? 30 : 8), i == 0 ? ly + 5 : ly, leftW - (i == 0 ? 36 : 14), 0.8f, 0.4f, Draw.Align.LEFT, Theme.TEXT, false);
            ly += i == 0 ? 20 : 9;
        }

        int rx = x + leftW + 8;
        int rw = w - leftW - 8;
        int ry = y + 16;
        Draw.panel(g, rx, ry, rw, ctx.height() - 16, Theme.CARD, Theme.CARD_BORDER);
        long left = left(a, listingAt);
        Draw.fit(g, font, Component.literal(a.bin() ? "Buy It Now" : "Auction").withColor(a.bin() ? Theme.GREEN : Theme.AQUA), rx + 6, ry + 5, rw - 12,
                1f, 0.5f, Draw.Align.LEFT, Theme.TEXT, false);
        Draw.fit(g, font, Component.literal("Seller: " + a.seller()), rx + 6, ry + 18, rw - 12, 0.85f, 0.45f, Draw.Align.LEFT, Theme.MUTED, false);
        Draw.fit(g, font, Component.literal("Ends in: ").withColor(Theme.MUTED).append(Component.literal(time(left)).withColor(Theme.TEXT)), rx + 6, ry + 28,
                rw - 12, 0.85f, 0.45f, Draw.Align.LEFT, Theme.TEXT, false);
        long purse = Purse.get(ctx.player());
        if (a.bin()) {
            Draw.fit(g, font, Component.literal("Price: ").withColor(Theme.MUTED).append(Component.literal(Msg.number(a.price()) + " coins").withColor(Theme.ACCENT)),
                    rx + 6, ry + 42, rw - 12, 0.9f, 0.45f, Draw.Align.LEFT, Theme.TEXT, false);
            boolean can = !a.mine() && left > 0 && purse >= a.price();
            button(ctx, rx + 6, ry + 58, rw - 12, a.mine() ? "Your own item" : purse < a.price() ? "Not enough coins" : "Buy for " + Msg.compact(a.price()),
                    Theme.GREEN, can, () -> {
                        act(AuctionHouse.Act.BUY, a.id(), 0, 0, false, 0);
                        view = View.BROWSE;
                    });
        } else {
            Draw.fit(g, font, Component.literal(a.bids() > 0 ? "Top bid: " : "Starting bid: ").withColor(Theme.MUTED)
                    .append(Component.literal(Msg.number(a.price()) + " coins").withColor(Theme.ACCENT))
                    .append(Component.literal(a.bids() > 0 ? "  (" + a.bids() + (a.bids() == 1 ? " bid)" : " bids)") : "").withColor(Theme.DIM)),
                    rx + 6, ry + 42, rw - 12, 0.85f, 0.45f, Draw.Align.LEFT, Theme.TEXT, false);
            if (a.topBidder()) {
                Draw.fit(g, font, Component.literal("✔ You have the top bid").withColor(Theme.GREEN), rx + 6, ry + 52, rw - 12, 0.85f, 0.45f,
                        Draw.Align.LEFT, Theme.GREEN, false);
            }
            coinField(ctx, rx + 6, ry + 62, rw - 12, bidText, bidFocused, "Bid at least " + Msg.compact(a.nextBid()), () -> {
                bidFocused = true;
                searchFocused = false;
            });
            long bid = Math.max(typed(bidText), a.nextBid());
            boolean can = !a.mine() && !a.topBidder() && left > 0 && purse >= bid;
            button(ctx, rx + 6, ry + 78, rw - 12, a.mine() ? "Your own auction" : "Bid " + Msg.compact(bid), Theme.AQUA, can,
                    () -> act(AuctionHouse.Act.BID, a.id(), bid, 0, false, 0));
            Draw.fit(g, font, Component.literal("Each bid is at least 15% over the last. If you're outbid, your coins come back.").withColor(Theme.DIM),
                    rx + 6, ry + 95, rw - 12, 0.7f, 0.4f, Draw.Align.LEFT, Theme.DIM, false);
        }
        if (a.mine() && a.bids() == 0 && left > 0) {
            button(ctx, rx + 6, y + ctx.height() - BUTTON_H - 6, rw - 12, "Cancel auction", Theme.RED, true, () -> {
                act(AuctionHouse.Act.CANCEL, a.id(), 0, 0, false, 0);
                view = View.MANAGE;
            });
        }
    }

    private void createView(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        button(ctx, x, y, 34, "◀ Back", Theme.MUTED, true, () -> view = View.BROWSE);
        Draw.fit(g, font, Component.literal("Pick an item to sell").withColor(Theme.MUTED), x + 40, y + 3, 140, 0.9f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);

        // Your inventory, to pick from.
        Inventory inventory = ctx.player().getInventory();
        int gx = x;
        int gy = y + 18;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            // the hotbar last, as in the inventory screen
            int row = i < 9 ? 3 : (i - 9) / 9;
            int col = i % 9;
            int sx = gx + col * 18;
            int sy = gy + row * 18 + (i < 9 ? 3 : 0);
            ItemStack stack = inventory.getItem(i);
            boolean picked = i == pickedSlot;
            SkyScreens.well(g, sx, sy);
            if (picked) {
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, Draw.withAlpha(Theme.ACCENT, 0.35f));
            }
            if (!stack.isEmpty()) {
                g.item(stack, sx + 1, sy + 1);
                g.itemDecorations(font, stack, sx + 1, sy + 1);
                int slot = i;
                if (ctx.hovered(sx, sy, 18, 18)) {
                    ctx.tooltip(stack.getTooltipLines(Item.TooltipContext.of(ctx.player().level()), ctx.player(), TooltipFlag.NORMAL), rarityColor(stack));
                }
                hit(sx, sy, 18, 18, () -> {
                    pickedSlot = slot;
                    SkyScreens.click();
                });
            }
        }

        // The listing.
        int rx = gx + 9 * 18 + 8;
        int rw = x + w - rx;
        int ry = gy;
        Draw.panel(g, rx, ry, rw, ctx.height() - 18, Theme.CARD, Theme.CARD_BORDER);
        ItemStack chosen = pickedSlot >= 0 ? inventory.getItem(pickedSlot) : ItemStack.EMPTY;
        if (chosen.isEmpty()) {
            Draw.fit(g, font, Component.literal("← Click an item"), rx + rw / 2, ry + 10, rw - 10, 0.9f, 0.5f, Draw.Align.CENTER, Theme.DIM, false);
            pickedSlot = -1;
        } else {
            Draw.item(g, chosen, rx + 6, ry + 5, 1f);
            Draw.fit(g, font, chosen.getHoverName(), rx + 26, ry + 9, rw - 30, 0.85f, 0.45f, Draw.Align.LEFT, rarityColor(chosen), false);
        }
        int half = (rw - 16) / 2;
        button(ctx, rx + 6, ry + 26, half, createBin ? "✔ Buy It Now" : "Buy It Now", Theme.GREEN, true, () -> createBin = true);
        button(ctx, rx + 10 + half, ry + 26, half, !createBin ? "✔ Auction" : "Auction", Theme.AQUA, true, () -> createBin = false);
        coinField(ctx, rx + 6, ry + 42, rw - 12, priceText, priceFocused, createBin ? "Price (e.g. 25k)" : "Starting bid (e.g. 25k)", () -> {
            priceFocused = true;
            searchFocused = false;
        });
        int dw = (rw - 12 - 4 * 2) / 5;
        for (int i = 0; i < AuctionHouse.HOURS.length; i++) {
            int index = i;
            button(ctx, rx + 6 + i * (dw + 2), ry + 58, dw, AuctionHouse.HOURS[i] + "h", duration == i ? Theme.ACCENT : Theme.MUTED, true, () -> duration = index);
        }
        long price = typed(priceText);
        long fee = price > 0 ? AuctionHouse.fee(price, duration) : 0;
        Draw.fit(g, font, Component.literal("Fee: ").withColor(Theme.MUTED).append(Component.literal(price > 0 ? Msg.number(fee) + " coins" : "-").withColor(Theme.ACCENT)),
                rx + 6, ry + 75, rw - 12, 0.8f, 0.45f, Draw.Align.LEFT, Theme.TEXT, false);
        boolean can = !chosen.isEmpty() && price > 0 && Purse.get(ctx.player()) >= fee;
        button(ctx, rx + 6, ry + 86, rw - 12, "Create " + (createBin ? "BIN auction" : "auction"), Theme.ACCENT, can, () -> {
            act(AuctionHouse.Act.CREATE, 0, price, pickedSlot, createBin, duration);
            pickedSlot = -1;
            priceText = "";
            view = View.MANAGE;
        });
    }

    private void manageView(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        button(ctx, x, y, 34, "◀ Back", Theme.MUTED, true, () -> {
            view = View.BROWSE;
            lastRequest = 0;
        });
        boolean anything = mine.refunds() > 0 || mine.auctions().stream().anyMatch(a -> a.status() != AuctionHouse.View.ACTIVE)
                || mine.bids().stream().anyMatch(a -> a.status() == AuctionHouse.View.WON);
        button(ctx, x + w - 70, y, 70, "Collect all", Theme.GREEN, anything, () -> act(AuctionHouse.Act.COLLECT_ALL, 0, 0, 0, false, 0));
        if (mine.refunds() > 0) {
            Draw.fit(g, font, Component.literal(Msg.number(mine.refunds()) + " coins back from bids you lost").withColor(Theme.ACCENT), x + 40, y + 3,
                    w - 120, 0.8f, 0.45f, Draw.Align.LEFT, Theme.ACCENT, false);
        }
        int colW = (w - 6) / 2;
        int top = y + 17;
        int height = y + ctx.height() - top;
        list(ctx, x, top, colW, height, "Your auctions", mine.auctions());
        list(ctx, x + colW + 6, top, colW, height, "Your bids", mine.bids());
    }

    private void list(PageContext ctx, int x, int y, int w, int h, String title, List<AuctionHouse.View> auctions) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        Draw.fit(g, font, Component.literal(title + " (" + auctions.size() + ")"), x + 6, y + 4, w - 12, 0.85f, 0.45f, Draw.Align.LEFT, Theme.MUTED, false);
        if (auctions.isEmpty()) {
            Draw.fit(g, font, Component.literal("None"), x + w / 2, y + 24, w - 10, 0.85f, 0.45f, Draw.Align.CENTER, Theme.DIM, false);
            return;
        }
        int ry = y + 14;
        for (AuctionHouse.View a : auctions) {
            if (ry + 22 > y + h) {
                break;
            }
            int color = rarityColor(a.item());
            Draw.item(g, a.item(), x + 4, ry + 2, 1f);
            Draw.fit(g, font, a.item().getHoverName(), x + 22, ry + 1, w - 26 - 44, 0.8f, 0.4f, Draw.Align.LEFT, color, false);
            String state = switch (a.status()) {
                case AuctionHouse.View.SOLD -> "Sold: collect " + Msg.compact(a.claimable());
                case AuctionHouse.View.EXPIRED -> "Ended unsold";
                case AuctionHouse.View.WON -> "Won for " + Msg.compact(a.price());
                case AuctionHouse.View.LOST -> "Outbid";
                default -> (a.bin() ? "BIN " : "Bid ") + Msg.compact(a.price()) + " · " + time(left(a, mineAt));
            };
            int stateColor = a.status() == AuctionHouse.View.SOLD || a.status() == AuctionHouse.View.WON ? Theme.GREEN
                    : a.status() == AuctionHouse.View.ACTIVE ? Theme.MUTED : Theme.DIM;
            Draw.fit(g, font, Component.literal(state), x + 22, ry + 11, w - 26 - 44, 0.75f, 0.4f, Draw.Align.LEFT, stateColor, false);
            boolean collect = a.status() == AuctionHouse.View.SOLD || a.status() == AuctionHouse.View.EXPIRED || a.status() == AuctionHouse.View.WON;
            if (collect) {
                button(ctx, x + w - 44, ry + 4, 40, "Collect", Theme.GREEN, true, () -> act(AuctionHouse.Act.COLLECT, a.id(), 0, 0, false, 0));
            } else if (a.status() == AuctionHouse.View.ACTIVE) {
                button(ctx, x + w - 44, ry + 4, 40, "View", Theme.MUTED, true, () -> {
                    detail = a;
                    bidText = "";
                    view = View.DETAIL;
                });
            }
            ry += 22;
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(PageContext ctx, double mx, double my, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            int[] hit = hits.get(i);
            if (SkyScreens.inside(mx, my, hit[0], hit[1], hit[2], hit[3])) {
                actions.get(hit[4]).run();
                return true;
            }
        }
        searchFocused = false;
        bidFocused = false;
        priceFocused = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double mx, double my, double amount) {
        if (view == View.BROWSE) {
            scroll -= amount * (CARD_H + 3);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(PageContext ctx, KeyEvent event) {
        int key = event.key();
        if (bidFocused || priceFocused) {
            if (key == InputConstants.KEY_BACKSPACE) {
                if (bidFocused && !bidText.isEmpty()) {
                    bidText = bidText.substring(0, bidText.length() - 1);
                } else if (priceFocused && !priceText.isEmpty()) {
                    priceText = priceText.substring(0, priceText.length() - 1);
                }
            } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_ESCAPE) {
                bidFocused = false;
                priceFocused = false;
                return key != InputConstants.KEY_ESCAPE;
            }
            return true;
        }
        if (view == View.BROWSE && (searchFocused || !search.isEmpty())) {
            if (search.keyPressed(event)) {
                return true;
            }
            return key >= InputConstants.KEY_0 && key <= InputConstants.KEY_9 || key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT
                    || key == InputConstants.KEY_BACKSPACE;
        }
        return false;
    }

    @Override
    public boolean charTyped(PageContext ctx, CharacterEvent event) {
        String c = event.codepointAsString();
        if (bidFocused || priceFocused) {
            if (c.matches("[0-9.kKmMbB]")) {
                if (bidFocused && bidText.length() < 14) {
                    bidText += c;
                } else if (priceFocused && priceText.length() < 14) {
                    priceText += c;
                }
            }
            return true;
        }
        if (view == View.BROWSE) {
            searchFocused = true;
            return search.charTyped(event);
        }
        return false;
    }

    @Override
    public boolean back(PageContext ctx) {
        if (bidFocused || priceFocused) {
            bidFocused = false;
            priceFocused = false;
            return true;
        }
        if (view != View.BROWSE) {
            view = View.BROWSE;
            return true;
        }
        if (!search.isEmpty()) {
            search.clear();
            return true;
        }
        return false;
    }

    @Override
    public boolean canGoBack() {
        return view != View.BROWSE || !search.isEmpty() || bidFocused || priceFocused;
    }

    /** For the SkyBlock Menu tile. */
    static String summary() {
        long ready = mine.auctions().stream().filter(a -> a.status() != AuctionHouse.View.ACTIVE).count()
                + mine.bids().stream().filter(a -> a.status() == AuctionHouse.View.WON).count();
        return ready > 0 ? ready + " to collect" : mine.auctions().isEmpty() ? "Trade items with players" : mine.auctions().size() + " auctions up";
    }
}
