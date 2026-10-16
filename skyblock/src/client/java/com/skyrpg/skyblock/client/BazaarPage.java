package com.skyrpg.skyblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.menu.SearchField;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Purse;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.bazaar.Bazaar;
import com.skyrpg.skyblock.bazaar.BazaarCatalog;
import com.skyrpg.skyblock.client.screen.SkyScreens;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Bazaar: Hypixel's five tabs and their groups, every product with what it buys and sells for, and a product's
 * own view with instant buying and selling, buy orders and sell offers (amount and price typed in, or one tenth of a
 * coin better than the best order) and the order book. Manage Orders lists your orders with what's ready to claim.
 */
public final class BazaarPage implements MenuPage {
    private static final int[] TAB_COLORS = {0xFFE5C34A, Theme.AQUA, Theme.RED, Theme.GREEN, Theme.PURPLE};
    private static final int GROUP_W = 100;
    private static final int ROW = 18;
    private static final int BUTTON_H = 13;

    private static Bazaar.Book book;
    private static List<Bazaar.OrderView> orders = List.of();

    public static void setBook(Bazaar.Book received) {
        book = received;
    }

    public static void setOrders(Bazaar.Orders received) {
        orders = received.orders();
    }

    private enum View {
        BROWSE, PRODUCT, ORDERS
    }

    /** A small box for a typed number. */
    private static final class NumberField {
        String text = "";

        double value() {
            try {
                return text.isEmpty() ? 0 : Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        void set(double value, boolean whole) {
            text = whole ? Long.toString((long) value) : Bazaar.round(value) == Math.floor(Bazaar.round(value))
                    ? Long.toString((long) Bazaar.round(value)) : Double.toString(Bazaar.round(value));
        }
    }

    private View view = View.BROWSE;
    private int tab;
    private int group;
    private String product;
    private double groupScroll;
    private double productScroll;
    private double orderScroll;
    private long lastRequest;
    private final SearchField search = new SearchField("Search the Bazaar");
    private final NumberField buyAmount = new NumberField();
    private final NumberField buyPrice = new NumberField();
    private final NumberField sellAmount = new NumberField();
    private final NumberField sellPrice = new NumberField();
    private NumberField focused;
    private boolean searchFocused;
    private final Map<String, ItemStack> stacks = new HashMap<>();
    /** Clickable things from the last frame: x, y, w, h, then what. */
    private final List<int[]> hits = new ArrayList<>();
    private final List<Runnable> actions = new ArrayList<>();

    @Override
    public Identifier id() {
        return SkyRpgSkyblock.id(Bazaar.PAGE);
    }

    @Override
    public Component title() {
        return Component.literal("Bazaar");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Buy and sell instantly, or place orders. Selling costs " + (Bazaar.TAX * 100) + "% tax");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.GOLD_INGOT);
    }

    @Override
    public int order() {
        return 982;
    }

    @Override
    public void opened() {
        request();
    }

    private ItemStack stack(String id) {
        return stacks.computeIfAbsent(id, key -> {
            ItemDefinition def = ItemRegistry.skyblock(key);
            return def == null ? new ItemStack(Items.BARRIER) : SkyItems.create(def, 1);
        });
    }

    private static String name(String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? id : def.name();
    }

    private static int color(String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? Theme.TEXT : def.rarity().color();
    }

    private static String coins(double value) {
        return value <= 0 ? "-" : value >= 100_000 ? Msg.compact(value) : Msg.number(Bazaar.round(value));
    }

    private void request() {
        lastRequest = Util.getMillis();
        ClientPlayNetworking.send(new Bazaar.Request(product == null ? "" : product));
    }

    private void act(int kind, String id, int amount, double price, long order) {
        ClientPlayNetworking.send(new Bazaar.Act(kind, id == null ? "" : id, amount, price, order));
        SkyScreens.click();
    }

    /** A button; when clicked runs {@code action}. */
    private void button(PageContext ctx, int x, int y, int w, String label, int color, boolean enabled, Runnable action) {
        GuiGraphicsExtractor g = ctx.graphics();
        boolean over = enabled && ctx.hovered(x, y, w, BUTTON_H);
        int shown = enabled ? color : Theme.DIM;
        Draw.panel(g, x, y, w, BUTTON_H, over ? Draw.mix(Theme.TRACK, shown, 0.25f) : Theme.TRACK, over ? shown : Draw.mix(Theme.TRACK, shown, 0.55f));
        Draw.fit(g, ctx.font(), Component.literal(label), x + w / 2, y + 3, w - 4, 0.85f, 0.5f, Draw.Align.CENTER, shown, false);
        if (enabled) {
            hits.add(new int[] {x, y, w, BUTTON_H, actions.size()});
            actions.add(action);
        }
    }

    private void field(PageContext ctx, int x, int y, int w, NumberField field, String placeholder) {
        GuiGraphicsExtractor g = ctx.graphics();
        boolean on = focused == field;
        Draw.panel(g, x, y, w, BUTTON_H, Theme.INPUT, on ? Theme.ACCENT : ctx.hovered(x, y, w, BUTTON_H) ? Theme.MUTED : Theme.CARD_BORDER);
        boolean caret = on && (Util.getMillis() / 530) % 2 == 0;
        Component text = field.text.isEmpty() ? Component.literal(placeholder).withColor(Theme.DIM)
                : Component.literal(field.text + (caret ? "|" : "")).withColor(Theme.TEXT);
        Draw.fit(g, ctx.font(), text, x + 4, y + 3, w - 6, 0.85f, 0.5f, Draw.Align.LEFT, Theme.TEXT, false);
        hits.add(new int[] {x, y, w, BUTTON_H, actions.size()});
        actions.add(() -> {
            focused = field;
            searchFocused = false;
        });
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(PageContext ctx) {
        hits.clear();
        actions.clear();
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        if (SkyBlockXp.level(ctx.player()) < Bazaar.level() && !ctx.player().isCreative()) {
            Draw.fit(g, font, Component.literal("🔒 The Bazaar opens at SkyBlock Level " + Bazaar.level()), ctx.x() + ctx.width() / 2,
                    ctx.y() + ctx.height() / 2 - 10, ctx.width() - 20, 1.2f, 0.7f, Draw.Align.CENTER, Theme.RED, false);
            Draw.fit(g, font, Component.literal("You're level " + SkyBlockXp.level(ctx.player()) + ". See SkyBlock Leveling for how to earn XP."),
                    ctx.x() + ctx.width() / 2, ctx.y() + ctx.height() / 2 + 6, ctx.width() - 20, 0.85f, 0.5f, Draw.Align.CENTER, Theme.MUTED, false);
            return;
        }
        if (BazaarCatalog.categories().isEmpty()) {
            Draw.text(g, font, "Waiting for the Bazaar from the server...", ctx.x(), ctx.y() + 4, Theme.MUTED);
            return;
        }
        // Live prices while you look at a product or your orders.
        if (view != View.BROWSE && Util.getMillis() - lastRequest > 2000) {
            request();
        }
        switch (view) {
            case BROWSE -> browse(ctx);
            case PRODUCT -> product(ctx);
            case ORDERS -> orders(ctx);
        }
    }

    private void tabs(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int x = ctx.x();
        int ordersW = 74;
        List<BazaarCatalog.Category> categories = BazaarCatalog.categories();
        // The tabs share what's left of the row, shrinking together when the window is narrow.
        int natural = 0;
        for (BazaarCatalog.Category category : categories) {
            natural += font.width(category.name()) + 12 + 3;
        }
        float squeeze = Math.min(1f, (ctx.width() - ordersW - 6) / (float) Math.max(1, natural));
        for (int i = 0; i < categories.size(); i++) {
            String name = categories.get(i).name();
            int w = (int) ((font.width(name) + 12) * squeeze);
            int color = TAB_COLORS[i % TAB_COLORS.length];
            boolean on = i == tab && search.isEmpty();
            boolean over = ctx.hovered(x, ctx.y(), w, 14);
            Draw.panel(g, x, ctx.y(), w, 14, on ? Draw.mix(Theme.CARD, color, 0.25f) : over ? Theme.CARD_HOVER : Theme.CARD, on || over ? color : Theme.CARD_BORDER);
            Draw.fit(g, font, Component.literal(name), x + w / 2, ctx.y() + 3, w - 4, 1f, 0.5f, Draw.Align.CENTER, on ? color : Theme.MUTED, false);
            int index = i;
            hits.add(new int[] {x, ctx.y(), w, 14, actions.size()});
            actions.add(() -> {
                tab = index;
                group = 0;
                groupScroll = 0;
                productScroll = 0;
                search.clear();
                SkyScreens.click();
            });
            x += w + 3;
        }
        long ready = orders.stream().filter(o -> o.claimable() > 0).count();
        button(ctx, ctx.x() + ctx.width() - ordersW, ctx.y(), ordersW, "Manage Orders" + (ready > 0 ? " (" + ready + ")" : ""),
                ready > 0 ? Theme.GREEN : Theme.ACCENT, true, () -> {
                    view = View.ORDERS;
                    request();
                });
    }

    private void browse(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        tabs(ctx);
        int top = ctx.y() + 18;
        int height = ctx.height() - 18;
        BazaarCatalog.Category category = BazaarCatalog.categories().get(Math.min(tab, BazaarCatalog.categories().size() - 1));
        int accent = TAB_COLORS[tab % TAB_COLORS.length];

        // Groups down the left, the search on top of them.
        int gx = ctx.x();
        search.draw(g, font, gx, top, GROUP_W, searchFocused, ctx.hovered(gx, top, GROUP_W, SearchField.HEIGHT), ctx.mouseX());
        hits.add(new int[] {gx, top, GROUP_W, SearchField.HEIGHT, actions.size()});
        actions.add(() -> {
            searchFocused = true;
            focused = null;
            if (!search.isEmpty() && SearchField.overClear(gx, GROUP_W, ctx.mouseX())) {
                search.clear();
            }
        });
        int listTop = top + SearchField.HEIGHT + 3;
        int listH = top + height - listTop;
        List<BazaarCatalog.Group> groups = category.groups();
        int content = groups.size() * 13;
        groupScroll = Mth.clamp(groupScroll, 0, Math.max(0, content - listH));
        g.enableScissor(gx, listTop, gx + GROUP_W, listTop + listH);
        for (int i = 0; i < groups.size(); i++) {
            int y = listTop + i * 13 - (int) groupScroll;
            if (y + 13 < listTop || y > listTop + listH) {
                continue;
            }
            BazaarCatalog.Group each = groups.get(i);
            boolean on = i == group && search.isEmpty();
            boolean over = ctx.hovered(gx, y, GROUP_W, 12) && ctx.mouseY() >= listTop && ctx.mouseY() < listTop + listH;
            g.fill(gx, y, gx + GROUP_W, y + 12, on ? Draw.mix(Theme.CARD, accent, 0.2f) : over ? Theme.CARD_HOVER : Theme.CARD);
            if (on) {
                g.fill(gx, y, gx + 2, y + 12, accent);
            }
            Draw.item(g, stack(each.icon()), gx + 3, y + 1, 0.6f);
            Draw.fit(g, font, Component.literal(each.name()), gx + 15, y + 2, GROUP_W - 18, 0.8f, 0.5f, Draw.Align.LEFT, on ? accent : Theme.TEXT, false);
            int index = i;
            if (y >= listTop && y + 12 <= listTop + listH) {
                hits.add(new int[] {gx, y, GROUP_W, 12, actions.size()});
                actions.add(() -> {
                    group = index;
                    productScroll = 0;
                    search.clear();
                    SkyScreens.click();
                });
            }
        }
        g.disableScissor();

        // The products of the group (or what the search finds, from every tab).
        List<BazaarCatalog.Product> products = shown(category);
        int px = gx + GROUP_W + 6;
        int pw = ctx.width() - GROUP_W - 6;
        int pContent = products.size() * ROW;
        productScroll = Mth.clamp(productScroll, 0, Math.max(0, pContent - height));
        g.enableScissor(px, top, px + pw, top + height);
        for (int i = 0; i < products.size(); i++) {
            int y = top + i * ROW - (int) productScroll;
            if (y + ROW < top || y > top + height) {
                continue;
            }
            BazaarCatalog.Product p = products.get(i);
            boolean over = ctx.hovered(px, y, pw - 4, ROW - 2) && ctx.mouseY() >= top && ctx.mouseY() < top + height;
            Draw.panel(g, px, y, pw - 4, ROW - 2, over ? Theme.CARD_HOVER : Theme.CARD, over ? accent : Theme.CARD_BORDER);
            Draw.item(g, stack(p.id()), px + 2, y, 1f);
            int textW = pw - 4 - 22 - 96;
            Draw.fit(g, font, Component.literal(name(p.id())), px + 21, y + 4, textW, 0.9f, 0.5f, Draw.Align.LEFT, color(p.id()), false);
            Draw.fit(g, font, Component.literal("Buy ").withColor(Theme.DIM).append(Component.literal(coins(p.buy())).withColor(Theme.GREEN)),
                    px + pw - 54, y + 4, 48, 0.8f, 0.5f, Draw.Align.RIGHT, Theme.GREEN, false);
            Draw.fit(g, font, Component.literal("Sell ").withColor(Theme.DIM).append(Component.literal(coins(p.sell())).withColor(Theme.ACCENT)),
                    px + pw - 8, y + 4, 46, 0.8f, 0.5f, Draw.Align.RIGHT, Theme.ACCENT, false);
            if (y >= top && y + ROW <= top + height) {
                hits.add(new int[] {px, y, pw - 4, ROW - 2, actions.size()});
                actions.add(() -> openProduct(p.id()));
            }
        }
        g.disableScissor();
        if (products.isEmpty()) {
            Draw.fit(g, font, Component.literal("Nothing matches"), px + pw / 2, top + 20, pw - 10, 1f, 0.6f, Draw.Align.CENTER, Theme.DIM, false);
        }
        if (pContent > height) {
            Draw.scrollbar(g, px + pw - 3, top, height, pContent, productScroll, accent);
        }
    }

    private List<BazaarCatalog.Product> shown(BazaarCatalog.Category category) {
        if (search.isEmpty()) {
            List<BazaarCatalog.Group> groups = category.groups();
            return groups.isEmpty() ? List.of() : groups.get(Math.min(group, groups.size() - 1)).products();
        }
        String needle = search.text().toLowerCase(Locale.ROOT).trim();
        List<BazaarCatalog.Product> out = new ArrayList<>();
        for (BazaarCatalog.Category c : BazaarCatalog.categories()) {
            for (BazaarCatalog.Group each : c.groups()) {
                for (BazaarCatalog.Product p : each.products()) {
                    if (name(p.id()).toLowerCase(Locale.ROOT).contains(needle) || p.id().toLowerCase(Locale.ROOT).contains(needle.replace(' ', '_'))) {
                        out.add(p);
                    }
                }
            }
        }
        return out;
    }

    private void openProduct(String id) {
        product = id;
        view = View.PRODUCT;
        book = null;
        buyAmount.text = "";
        buyPrice.text = "";
        sellAmount.text = "";
        sellPrice.text = "";
        focused = null;
        request();
        SkyScreens.click();
    }

    private void product(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        String id = product;
        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        int have = SkyStacks.count(ctx.player().getInventory(), id);
        long purse = Purse.get(ctx.player());
        Bazaar.Book shown = book != null && book.product().equals(id) ? book : null;
        double instantBuy = shown == null ? 0 : shown.instantBuy();
        double instantSell = shown == null ? 0 : shown.instantSell();

        // Header.
        button(ctx, x, y, 34, "◀ Back", Theme.MUTED, true, () -> {
            view = View.BROWSE;
            focused = null;
        });
        Draw.item(g, stack(id), x + 40, y - 2, 1f);
        Draw.fit(g, font, Component.literal(name(id)), x + 60, y + 1, w - 200, 1f, 0.6f, Draw.Align.LEFT, color(id), false);
        if (ctx.hovered(x + 40, y - 2, 16, 16)) {
            ctx.tooltip(stack(id).getTooltipLines(Item.TooltipContext.of(ctx.player().level()), ctx.player(), TooltipFlag.NORMAL), color(id));
        }
        Draw.fit(g, font, Component.literal("You have ").withColor(Theme.DIM).append(Component.literal(Msg.number(have)).withColor(Theme.TEXT))
                .append(Component.literal("  ·  Purse ").withColor(Theme.DIM)).append(Component.literal(Msg.number(purse)).withColor(Theme.ACCENT)),
                x + w, y + 2, 150, 0.85f, 0.5f, Draw.Align.RIGHT, Theme.TEXT, false);

        int colW = (w - 6) / 2;
        int top = y + 18;
        int buyX = x;
        int sellX = x + colW + 6;
        int bookTop = top + 92;

        // Buying.
        Draw.panel(g, buyX, top, colW, 88, Theme.CARD, Draw.mix(Theme.CARD_BORDER, Theme.GREEN, 0.4f));
        Draw.fit(g, font, Component.literal("Buy").withColor(Theme.GREEN), buyX + 6, top + 4, 40, 1f, 0.6f, Draw.Align.LEFT, Theme.GREEN, false);
        Draw.fit(g, font, Component.literal("Instantly: ").withColor(Theme.DIM).append(Component.literal(coins(instantBuy) + " each").withColor(Theme.TEXT)),
                buyX + colW - 6, top + 4, colW - 40, 0.8f, 0.5f, Draw.Align.RIGHT, Theme.TEXT, false);
        int bw = (colW - 12 - 8) / 3;
        int stackSize = stack(id).getMaxStackSize();
        button(ctx, buyX + 6, top + 16, bw, "Buy 1", Theme.GREEN, instantBuy > 0 && purse >= instantBuy, () -> act(Bazaar.INSTANT_BUY, id, 1, 0, 0));
        button(ctx, buyX + 10 + bw, top + 16, bw, "Buy " + stackSize, Theme.GREEN, instantBuy > 0 && purse >= instantBuy * stackSize,
                () -> act(Bazaar.INSTANT_BUY, id, stackSize, 0, 0));
        int fill = instantBuy > 0 ? (int) Math.min(Bazaar.MAX_AMOUNT, purse / instantBuy) : 0;
        button(ctx, buyX + 14 + 2 * bw, top + 16, bw, "Max", Theme.GREEN, fill > 0, () -> act(Bazaar.INSTANT_BUY, id, Math.max(1, fill), 0, 0));
        Draw.fit(g, font, Component.literal("Buy Order").withColor(Theme.MUTED), buyX + 6, top + 36, colW - 12, 0.8f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);
        int half = (colW - 16) / 2;
        field(ctx, buyX + 6, top + 46, half, buyAmount, "Amount");
        field(ctx, buyX + 10 + half, top + 46, half, buyPrice, "Price each");
        double best = shown == null || shown.buyOrders().isEmpty() ? 0 : shown.buyOrders().getFirst().price();
        button(ctx, buyX + 6, top + 61, half, "Top +0.1", Theme.MUTED, best > 0, () -> buyPrice.set(best + 0.1, false));
        button(ctx, buyX + 10 + half, top + 61, half, "Same", Theme.MUTED, best > 0, () -> buyPrice.set(best, false));
        int amount = (int) Math.min(Bazaar.MAX_AMOUNT, buyAmount.value());
        double price = Bazaar.round(buyPrice.value());
        long total = (long) Math.ceil(amount * price);
        button(ctx, buyX + 6, top + 75 - 1, colW - 12, amount > 0 && price > 0 ? "Place: " + Msg.number(total) + " coins" : "Place Buy Order",
                Theme.GREEN, amount > 0 && price > 0 && purse >= total, () -> {
                    act(Bazaar.BUY_ORDER, id, amount, price, 0);
                    buyAmount.text = "";
                    buyPrice.text = "";
                });

        // Selling.
        Draw.panel(g, sellX, top, colW, 88, Theme.CARD, Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, 0.4f));
        Draw.fit(g, font, Component.literal("Sell").withColor(Theme.ACCENT), sellX + 6, top + 4, 40, 1f, 0.6f, Draw.Align.LEFT, Theme.ACCENT, false);
        Draw.fit(g, font, Component.literal("Instantly: ").withColor(Theme.DIM).append(Component.literal(coins(instantSell) + " each").withColor(Theme.TEXT)),
                sellX + colW - 6, top + 4, colW - 40, 0.8f, 0.5f, Draw.Align.RIGHT, Theme.TEXT, false);
        long allPays = (long) Math.floor(have * instantSell * (1 - Bazaar.TAX));
        button(ctx, sellX + 6, top + 16, colW - 12, have > 0 && instantSell > 0 ? "Sell all " + Msg.number(have) + ": " + Msg.number(allPays) + " coins"
                : "Sell Instantly", Theme.ACCENT, have > 0 && instantSell > 0, () -> act(Bazaar.INSTANT_SELL, id, have, 0, 0));
        Draw.fit(g, font, Component.literal("Sell Offer").withColor(Theme.MUTED), sellX + 6, top + 36, colW - 12, 0.8f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);
        field(ctx, sellX + 6, top + 46, half, sellAmount, have > 0 ? "All (" + Msg.number(have) + ")" : "Amount");
        field(ctx, sellX + 10 + half, top + 46, half, sellPrice, "Price each");
        double cheapest = shown == null || shown.sellOffers().isEmpty() ? 0 : shown.sellOffers().getFirst().price();
        button(ctx, sellX + 6, top + 61, half, "Best -0.1", Theme.MUTED, cheapest > 0.1, () -> sellPrice.set(cheapest - 0.1, false));
        button(ctx, sellX + 10 + half, top + 61, half, "Same", Theme.MUTED, cheapest > 0, () -> sellPrice.set(cheapest, false));
        int offer = sellAmount.text.isEmpty() ? have : (int) Math.min(have, sellAmount.value());
        double ask = Bazaar.round(sellPrice.value());
        long pays = (long) Math.floor(offer * ask * (1 - Bazaar.TAX));
        button(ctx, sellX + 6, top + 74, colW - 12, offer > 0 && ask > 0 ? "Place: " + Msg.number(pays) + " coins after tax" : "Place Sell Offer",
                Theme.ACCENT, offer > 0 && ask > 0, () -> {
                    act(Bazaar.SELL_OFFER, id, offer, ask, 0);
                    sellAmount.text = "";
                    sellPrice.text = "";
                });

        // The order book, best first; the market maker's line is the floor (or ceiling) no one trades past.
        int bookH = y + ctx.height() - bookTop;
        if (bookH > 20 && shown != null) {
            bookSide(ctx, buyX, bookTop, colW, bookH, "Sell offers", shown.sellOffers(), Theme.GREEN);
            bookSide(ctx, sellX, bookTop, colW, bookH, "Buy orders", shown.buyOrders(), Theme.ACCENT);
        }
    }

    private void bookSide(PageContext ctx, int x, int y, int w, int h, String title, List<Bazaar.Level> levels, int color) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Draw.panel(g, x, y, w, h, Theme.CARD, Theme.CARD_BORDER);
        Draw.fit(g, font, Component.literal(title), x + 6, y + 3, w - 12, 0.8f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);
        int ly = y + 13;
        for (Bazaar.Level level : levels) {
            if (ly > y + h - 8) {
                break;
            }
            String amount = level.maker() ? "Market" : Msg.number(level.amount()) + "x · " + level.orders() + (level.orders() == 1 ? " order" : " orders");
            Draw.fit(g, font, Component.literal(coins(level.price())), x + 6, ly, w / 2 - 6, 0.8f, 0.5f, Draw.Align.LEFT, color, false);
            Draw.fit(g, font, Component.literal(amount), x + w - 6, ly, w / 2 - 6, 0.75f, 0.5f, Draw.Align.RIGHT, level.maker() ? Theme.DIM : Theme.TEXT, false);
            ly += 9;
        }
    }

    private void orders(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int x = ctx.x();
        int y = ctx.y();
        int w = ctx.width();
        button(ctx, x, y, 34, "◀ Back", Theme.MUTED, true, () -> view = product != null ? View.PRODUCT : View.BROWSE);
        Draw.fit(g, font, Component.literal("Your orders"), x + 40, y + 2, 120, 1f, 0.6f, Draw.Align.LEFT, Theme.TEXT, false);
        boolean anyReady = orders.stream().anyMatch(o -> o.claimable() > 0);
        button(ctx, x + w - 70, y, 70, "Claim all", Theme.GREEN, anyReady, () -> act(Bazaar.CLAIM, "", 0, 0, -1));
        int top = y + 18;
        int height = ctx.height() - 18;
        if (orders.isEmpty()) {
            Draw.fit(g, font, Component.literal("No orders. Place one on a product's page."), x + w / 2, top + 30, w - 20, 1f, 0.6f, Draw.Align.CENTER,
                    Theme.DIM, false);
            return;
        }
        int rowH = 24;
        int content = orders.size() * rowH;
        orderScroll = Mth.clamp(orderScroll, 0, Math.max(0, content - height));
        g.enableScissor(x, top, x + w, top + height);
        for (int i = 0; i < orders.size(); i++) {
            Bazaar.OrderView o = orders.get(i);
            int ry = top + i * rowH - (int) orderScroll;
            if (ry + rowH < top || ry > top + height) {
                continue;
            }
            int accent = o.buy() ? Theme.GREEN : Theme.ACCENT;
            Draw.panel(g, x, ry, w - 4, rowH - 2, Theme.CARD, o.claimable() > 0 ? accent : Theme.CARD_BORDER);
            Draw.item(g, stack(o.product()), x + 3, ry + 3, 1f);
            Component line = Component.literal(o.buy() ? "BUY " : "SELL ").withColor(accent)
                    .append(Component.literal(Msg.number(o.amount()) + "x " + name(o.product())).withColor(color(o.product())))
                    .append(Component.literal(" at " + coins(o.price())).withColor(Theme.MUTED));
            Draw.fit(g, font, line, x + 22, ry + 3, w - 22 - 100, 0.85f, 0.5f, Draw.Align.LEFT, Theme.TEXT, false);
            Draw.bar(g, x + 22, ry + 15, w - 22 - 132, 3, o.amount() == 0 ? 1 : o.filled() / (float) o.amount(), accent);
            Draw.fit(g, font, Component.literal(Msg.number(o.filled()) + "/" + Msg.number(o.amount()) + " filled"), x + w - 64, ry + 13, 44, 0.65f, 0.4f,
                    Draw.Align.RIGHT, Theme.DIM, false);
            if (ry >= top && ry + rowH <= top + height) {
                button(ctx, x + w - 60, ry + 4, 26, "Claim", Theme.GREEN, o.claimable() > 0, () -> act(Bazaar.CLAIM, o.product(), 0, 0, o.id()));
                button(ctx, x + w - 32, ry + 4, 26, "Cancel", Theme.RED, o.filled() < o.amount(), () -> act(Bazaar.CANCEL, o.product(), 0, 0, o.id()));
            }
        }
        g.disableScissor();
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
        focused = null;
        searchFocused = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double mx, double my, double amount) {
        switch (view) {
            case BROWSE -> {
                if (mx < ctx.x() + GROUP_W) {
                    groupScroll -= amount * 26;
                } else {
                    productScroll -= amount * ROW * 2;
                }
            }
            case ORDERS -> orderScroll -= amount * 24;
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(PageContext ctx, KeyEvent event) {
        if (focused != null) {
            int key = event.key();
            if (key == InputConstants.KEY_BACKSPACE && !focused.text.isEmpty()) {
                focused.text = focused.text.substring(0, focused.text.length() - 1);
            } else if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_TAB) {
                focused = null;
                return key != InputConstants.KEY_ESCAPE;
            }
            return true; // digits would switch menu pages
        }
        if (searchFocused || !search.isEmpty()) {
            if (search.keyPressed(event)) {
                return true;
            }
            int key = event.key();
            return key >= InputConstants.KEY_0 && key <= InputConstants.KEY_9 || key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT
                    || key == InputConstants.KEY_BACKSPACE;
        }
        return false;
    }

    @Override
    public boolean charTyped(PageContext ctx, CharacterEvent event) {
        if (focused != null) {
            String c = event.codepointAsString();
            if (c.matches("[0-9]") || c.equals(".") && !focused.text.contains(".")) {
                if (focused.text.length() < 12) {
                    focused.text += c;
                }
            } else if (c.equalsIgnoreCase("k") && !focused.text.isEmpty()) {
                focused.set(focused.value() * 1000, focused == buyAmount || focused == sellAmount);
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
        if (focused != null) {
            focused = null;
            return true;
        }
        if (view != View.BROWSE) {
            view = view == View.ORDERS && product != null ? View.PRODUCT : View.BROWSE;
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
        return view != View.BROWSE || !search.isEmpty() || focused != null;
    }

    /** "Level 7 to open" or nothing, for the SkyBlock Menu's tile. */
    static String summary() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return "";
        }
        if (SkyBlockXp.level(player) < Bazaar.level()) {
            return "Opens at SkyBlock Level " + Bazaar.level();
        }
        long ready = orders.stream().filter(o -> o.claimable() > 0).count();
        return ready > 0 ? ready + " orders to claim" : "Buy and sell anything";
    }
}
