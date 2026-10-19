package com.skyrpg.skyblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.menu.SearchField;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.progress.CollectionLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.client.screen.SkyScreens;
import com.skyrpg.skyblock.recipe.Crafting;
import com.skyrpg.skyblock.recipe.Recipes;
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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Recipe Book: every SkyBlock item that has a recipe, searchable, with what you can craft right now lit up and
 * what you haven't unlocked yet dimmed. Pick one to see its grid (ingredient counts, green when you have enough), what
 * unlocks it, and craft it straight from your inventory (Hypixel's Supercraft): once, or as many as you can.
 */
public final class RecipeBookPage implements MenuPage {
    private static final int CELL = 20;
    private static final int DETAIL_W = 148;
    private static final int GRID_CELL = 22;
    private static final int BUTTON_H = 16;

    /** Which recipes the list shows. */
    private enum Filter {
        ALL("All"), UNLOCKED("Unlocked"), CRAFTABLE("Craftable");

        private final String label;

        Filter(String label) {
            this.label = label;
        }

        Filter next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private final SearchField search = new SearchField("Search recipes");
    private Filter filter = Filter.ALL;
    private double scroll;
    private List<String> shown = List.of();
    private String shownFor;
    private Filter shownFilter;
    private int shownVersion = -1;
    private long shownAt;
    private final Map<String, ItemStack> stacks = new HashMap<>();
    private String selected;
    private int recipeIndex;
    private String hovered;
    private int fieldW;
    private int filterX;
    private int filterW;
    // The detail panel's buttons, from the last frame.
    private int craftX;
    private int craftY;
    private int craftW;
    private int arrowsY;
    private int detailX;
    // The list's layout, from the last frame, so a click finds its cell by where it is.
    private int listTop;
    private int listHeight;
    private int listW;
    private int columns = 1;

    private static String summary = "";
    private static long summaryAt;

    /** "412/2,468 unlocked", for the SkyBlock Menu's tile (worked out once a second). */
    static String summary() {
        long now = System.currentTimeMillis();
        var player = Minecraft.getInstance().player;
        if (player != null && now - summaryAt > 1000) {
            summaryAt = now;
            long open = Recipes.all().stream().filter(recipe -> Recipes.unlocked(player, recipe)).count();
            summary = Msg.number(open) + "/" + Msg.number(Recipes.all().size()) + " unlocked";
        }
        return summary;
    }

    @Override
    public Identifier id() {
        return SkyRpgSkyblock.id("recipe_book");
    }

    @Override
    public Component title() {
        return Component.literal("Recipe Book");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Every recipe, what unlocks it, and crafting straight from your inventory");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.BOOK);
    }

    @Override
    public int order() {
        return 984;
    }

    private ItemStack stack(String skyblockId) {
        return stacks.computeIfAbsent(skyblockId, id -> {
            ItemDefinition def = ItemRegistry.skyblock(id);
            return def == null ? new ItemStack(Items.BARRIER) : SkyItems.create(def, 1);
        });
    }

    private static String name(String skyblockId) {
        ItemDefinition def = ItemRegistry.skyblock(skyblockId);
        return def == null ? skyblockId : def.name();
    }

    private static int color(String skyblockId) {
        ItemDefinition def = ItemRegistry.skyblock(skyblockId);
        return def == null ? Theme.TEXT : def.rarity().color();
    }

    /** Whether any recipe for this item is unlocked. */
    private static boolean unlocked(PageContext ctx, String output) {
        for (Recipes.Recipe recipe : Recipes.making(output)) {
            if (Recipes.unlocked(ctx.player(), recipe)) {
                return true;
            }
        }
        return false;
    }

    private static boolean craftable(PageContext ctx, String output) {
        for (Recipes.Recipe recipe : Recipes.making(output)) {
            if (Recipes.unlocked(ctx.player(), recipe) && Crafting.craftable(ctx.player().getInventory(), recipe) > 0) {
                return true;
            }
        }
        return false;
    }

    private void refresh(PageContext ctx) {
        // Craftable depends on the inventory, so that list refreshes now and then.
        long now = System.currentTimeMillis();
        boolean stale = filter == Filter.CRAFTABLE && now - shownAt > 1000;
        if (search.text().equals(shownFor) && filter == shownFilter && Recipes.version() == shownVersion && !stale) {
            return;
        }
        boolean sameView = search.text().equals(shownFor) && filter == shownFilter;
        String needle = search.text().toLowerCase(Locale.ROOT).trim();
        String asId = needle.replace(' ', '_');
        shown = Recipes.outputs().stream()
                .filter(id -> ItemRegistry.skyblock(id) != null)
                .filter(id -> needle.isEmpty() || name(id).toLowerCase(Locale.ROOT).contains(needle) || id.toLowerCase(Locale.ROOT).contains(asId))
                .filter(id -> switch (filter) {
                    case ALL -> true;
                    case UNLOCKED -> unlocked(ctx, id);
                    case CRAFTABLE -> craftable(ctx, id);
                })
                .sorted((a, b) -> {
                    ItemDefinition da = ItemRegistry.skyblock(a);
                    ItemDefinition db = ItemRegistry.skyblock(b);
                    return da.rarity() != db.rarity() ? da.rarity().compareTo(db.rarity()) : da.name().compareToIgnoreCase(db.name());
                })
                .toList();
        if (!sameView) {
            scroll = 0;
        }
        shownFor = search.text();
        shownFilter = filter;
        shownVersion = Recipes.version();
        shownAt = now;
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        refresh(ctx);
        hovered = null;
        listW = ctx.width() - DETAIL_W - 8;
        detailX = ctx.x() + listW + 8;

        // Search and the filter switch.
        String filterText = "Show: " + filter.label;
        filterW = font.width(filterText) + 12;
        fieldW = listW - filterW - 4;
        filterX = ctx.x() + fieldW + 4;
        search.draw(g, font, ctx.x(), ctx.y(), fieldW, true, ctx.hovered(ctx.x(), ctx.y(), fieldW, SearchField.HEIGHT), ctx.mouseX());
        boolean overFilter = ctx.hovered(filterX, ctx.y(), filterW, SearchField.HEIGHT);
        int filterColor = filter == Filter.CRAFTABLE ? Theme.GREEN : filter == Filter.UNLOCKED ? Theme.AQUA : Theme.MUTED;
        Draw.panel(g, filterX, ctx.y(), filterW, SearchField.HEIGHT, overFilter ? Theme.CARD_HOVER : Theme.CARD,
                overFilter ? filterColor : Theme.CARD_BORDER);
        Draw.text(g, font, filterText, filterX + 6, ctx.y() + (SearchField.HEIGHT - 8) / 2 + 1, filterColor);

        // The items.
        int top = ctx.y() + SearchField.HEIGHT + 5;
        int height = ctx.height() - SearchField.HEIGHT - 5;
        columns = Math.max(1, (listW - 6) / CELL);
        listTop = top;
        listHeight = height;
        int rows = (shown.size() + columns - 1) / columns;
        int content = rows * CELL;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - height));
        int firstRow = (int) (scroll / CELL);
        int offset = (int) (scroll % CELL);
        Inventory inventory = ctx.player().getInventory();
        List<int[]> locked = new ArrayList<>();
        int[] pickedAt = null;
        g.enableScissor(ctx.x(), top, ctx.x() + listW, top + height);
        for (int row = firstRow; row < rows && (row - firstRow) * CELL - offset < height; row++) {
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index >= shown.size()) {
                    break;
                }
                String id = shown.get(index);
                int x = ctx.x() + col * CELL;
                int y = top + (row - firstRow) * CELL - offset;
                boolean over = ctx.hovered(x, y, CELL - 2, CELL - 2) && ctx.mouseY() >= top && ctx.mouseY() < top + height;
                boolean open = unlocked(ctx, id);
                boolean ready = open && craftable(ctx, id);
                boolean picked = id.equals(selected);
                g.fill(x, y, x + CELL - 2, y + CELL - 2, picked ? Draw.mix(Theme.CARD, Theme.ACCENT, 0.25f) : over ? Theme.CARD_HOVER : Theme.CARD);
                if (ready) {
                    g.fillGradient(x, y, x + CELL - 2, y + CELL - 2, 0x00000000, Draw.withAlpha(Theme.GREEN, 0.35f));
                }
                g.fill(x, y + CELL - 3, x + CELL - 2, y + CELL - 2, open ? color(id) : Theme.DIM);
                Draw.item(g, stack(id), x + 1, y + 1, 1f);
                if (!open) {
                    locked.add(new int[] {x, y});
                }
                if (picked) {
                    pickedAt = new int[] {x, y};
                }
                if (over) {
                    hovered = id;
                }
            }
        }
        // Over the items (they draw on top of fills in the same stratum): locked ones dimmed, the picked one outlined.
        g.nextStratum();
        for (int[] at : locked) {
            g.fill(at[0], at[1], at[0] + CELL - 2, at[1] + CELL - 3, 0xB0101319);
        }
        if (pickedAt != null) {
            Draw.outline(g, pickedAt[0], pickedAt[1], CELL - 2, CELL - 2, Theme.ACCENT);
        }
        g.disableScissor();
        if (shown.isEmpty()) {
            Draw.fit(g, font, Component.literal(Recipes.all().isEmpty() ? "No recipes yet (waiting for the server)" : "Nothing matches"),
                    ctx.x() + listW / 2, top + 20, listW - 10, 1f, 0.7f, Draw.Align.CENTER, Theme.DIM, false);
        }
        if (content > height) {
            Draw.scrollbar(g, ctx.x() + listW - 3, top, height, content, scroll, Theme.DIM);
        }

        detail(ctx, g, font, inventory);

        if (hovered != null) {
            ItemStack stack = stack(hovered);
            List<Component> lines = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(ctx.player().level()), ctx.player(), TooltipFlag.NORMAL));
            if (!unlocked(ctx, hovered)) {
                lines.add(Component.literal("🔒 Requires " + Recipes.making(hovered).getFirst().requires().describe()).withColor(Theme.RED));
            }
            lines.add(Component.literal("Click to see the recipe").withColor(Theme.DIM));
            ctx.tooltip(lines, color(hovered));
        }
    }

    /** The picked item's recipe: grid, what unlocks it, and the craft buttons. */
    private void detail(PageContext ctx, GuiGraphicsExtractor g, Font font, Inventory inventory) {
        int x = detailX;
        int y = ctx.y();
        int h = ctx.height();
        Draw.panel(g, x, y, DETAIL_W, h, Theme.CARD, Theme.CARD_BORDER);
        craftW = 0;
        arrowsY = -1;
        List<Recipes.Recipe> recipes = selected == null ? List.of() : Recipes.making(selected);
        if (recipes.isEmpty()) {
            Draw.fit(g, font, Component.literal("Pick an item to see its recipe"), x + DETAIL_W / 2, y + h / 2 - 4, DETAIL_W - 12, 1f, 0.7f,
                    Draw.Align.CENTER, Theme.DIM, false);
            return;
        }
        recipeIndex = Mth.clamp(recipeIndex, 0, recipes.size() - 1);
        Recipes.Recipe recipe = recipes.get(recipeIndex);
        int accent = color(selected);
        boolean open = Recipes.unlocked(ctx.player(), recipe);

        // Header: the item, its name and rarity.
        g.fillGradient(x + 1, y + 1, x + DETAIL_W - 1, y + 30, Draw.withAlpha(accent, 0.22f), 0x00000000);
        Draw.item(g, stack(selected), x + 6, y + 5, 1.25f);
        Draw.fit(g, font, Component.literal(name(selected)).withColor(accent), x + 30, y + 6, DETAIL_W - 36, 1f, 0.6f, Draw.Align.LEFT, accent, false);
        ItemDefinition def = ItemRegistry.skyblock(selected);
        String sub = (def == null ? "" : def.rarity().label()) + (recipe.count() > 1 ? "  ·  makes " + recipe.count() : "");
        Draw.fit(g, font, Component.literal(sub), x + 30, y + 17, DETAIL_W - 36, 0.7f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);

        // The grid, its cells lit by whether you have enough of that ingredient overall.
        Map<String, Integer> need = recipe.ingredients();
        int gx = x + 8;
        int gy = y + 36;
        for (int i = 0; i < 9; i++) {
            int cx = gx + (i % 3) * GRID_CELL;
            int cy = gy + (i / 3) * GRID_CELL;
            Recipes.Cell cell = recipe.grid().get(i);
            SkyScreens.well(g, cx, cy);
            if (cell.isEmpty()) {
                continue;
            }
            boolean enough = Crafting.count(inventory, cell.item()) >= need.get(cell.item());
            g.fill(cx + 1, cy + 17, cx + 17, cy + 18, enough ? Theme.GREEN : Theme.RED);
            ItemStack icon = stack(cell.item());
            g.item(icon, cx + 1, cy + 1);
            g.itemDecorations(font, icon, cx + 1, cy + 1, cell.count() > 1 ? Msg.compact(cell.count()) : null);
            if (ctx.hovered(cx, cy, 18, 18)) {
                List<Component> lines = new ArrayList<>(icon.getTooltipLines(Item.TooltipContext.of(ctx.player().level()), ctx.player(), TooltipFlag.NORMAL));
                lines.add(Component.literal("Needs " + Msg.number(need.get(cell.item())) + " in all, you have "
                        + Msg.number(Crafting.count(inventory, cell.item()))).withColor(enough ? Theme.GREEN : Theme.RED));
                ctx.tooltip(lines, enough ? Theme.GREEN : Theme.RED);
            }
        }
        // Arrow and what it makes.
        int ax = gx + 3 * GRID_CELL + 4;
        int ay = gy + GRID_CELL + 5;
        Draw.text(g, font, "➜", ax, ay, open ? accent : Theme.DIM);
        int ox = ax + 14;
        int oy = gy + GRID_CELL - 3;
        g.fillGradient(ox - 2, oy - 2, ox + 24, oy + 24, Draw.withAlpha(accent, 0.45f), Draw.withAlpha(accent, 0.1f));
        SkyScreens.well(g, ox + 2, oy + 2);
        ItemStack out = stack(selected);
        g.item(out, ox + 3, oy + 3);
        g.itemDecorations(font, out, ox + 3, oy + 3, recipe.count() > 1 ? Integer.toString(recipe.count()) : null);

        // More than one recipe: switch between them.
        int by = gy + 3 * GRID_CELL + 4;
        if (recipes.size() > 1) {
            arrowsY = by;
            String pages = "◀  Recipe " + (recipeIndex + 1) + " of " + recipes.size() + "  ▶";
            Draw.fit(g, font, Component.literal(pages), x + DETAIL_W / 2, by, DETAIL_W - 12, 0.8f, 0.6f, Draw.Align.CENTER, Theme.MUTED, false);
            by += 11;
        }

        // What unlocks it.
        Recipes.Requirement requires = recipe.requires();
        Component line;
        if (requires.none()) {
            line = Component.literal("✔ Always known").withColor(Theme.GREEN);
        } else if (open) {
            line = Component.literal("✔ Unlocked: " + requires.describe()).withColor(Theme.GREEN);
        } else {
            line = Component.literal("🔒 Requires " + requires.describe()).withColor(Theme.RED);
        }
        Draw.fit(g, font, line, x + 8, by, DETAIL_W - 16, 0.8f, 0.5f, Draw.Align.LEFT, Theme.TEXT, false);
        by += 10;
        if (!open && !requires.collection().isEmpty()) {
            int have = CollectionLevels.tier(ctx.player(), Recipes.collectionId(requires.collection()));
            Draw.fit(g, font, Component.literal("Your tier: " + (have == 0 ? "none" : CollectionLevels.roman(have))).withColor(Theme.MUTED),
                    x + 8, by, DETAIL_W - 16, 0.7f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);
        }

        // Craft buttons.
        int times = open ? Crafting.craftable(inventory, recipe) : 0;
        craftX = x + 8;
        craftY = y + h - BUTTON_H - 8;
        craftW = DETAIL_W - 16;
        boolean can = times > 0;
        boolean over = can && ctx.hovered(craftX, craftY, craftW, BUTTON_H);
        int color = can ? Theme.GREEN : Theme.DIM;
        Draw.panel(g, craftX, craftY, craftW, BUTTON_H, over ? Draw.mix(Theme.TRACK, color, 0.25f) : Theme.TRACK,
                over ? color : Draw.mix(Theme.TRACK, color, 0.55f));
        String label = !open ? "Locked" : !can ? "Missing materials" : "Craft" + (times > 1 ? "  (shift: x" + Math.min(times, Crafting.MAX_CRAFTS) + ")" : "");
        Draw.fit(g, font, Component.literal(label), craftX + craftW / 2, craftY + 4, craftW - 6, 1f, 0.6f, Draw.Align.CENTER, color, false);
        if (can) {
            Draw.fit(g, font, Component.literal("You can craft this " + Msg.number(times) + (times == 1 ? " time" : " times")).withColor(Theme.MUTED),
                    x + DETAIL_W / 2, craftY - 10, DETAIL_W - 12, 0.7f, 0.5f, Draw.Align.CENTER, Theme.MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (y >= ctx.y() && y < ctx.y() + SearchField.HEIGHT && x < ctx.x() + fieldW) {
            if (!search.isEmpty() && SearchField.overClear(ctx.x(), fieldW, x)) {
                search.clear();
            }
            return true;
        }
        if (SkyScreens.inside(x, y, filterX, ctx.y(), filterW, SearchField.HEIGHT)) {
            filter = filter.next();
            SkyScreens.click();
            return true;
        }
        String clicked = cellAt(ctx, x, y);
        if (clicked != null) {
            if (!clicked.equals(selected)) {
                selected = clicked;
                recipeIndex = 0;
            }
            SkyScreens.click();
            return true;
        }
        if (arrowsY >= 0 && y >= arrowsY - 2 && y < arrowsY + 9 && x >= detailX && x < detailX + DETAIL_W) {
            int count = Recipes.making(selected).size();
            recipeIndex = (recipeIndex + (x < detailX + DETAIL_W / 2.0 ? count - 1 : 1)) % count;
            SkyScreens.click();
            return true;
        }
        if (craftW > 0 && SkyScreens.inside(x, y, craftX, craftY, craftW, BUTTON_H) && selected != null) {
            List<Recipes.Recipe> recipes = Recipes.making(selected);
            if (!recipes.isEmpty()) {
                Recipes.Recipe recipe = recipes.get(Mth.clamp(recipeIndex, 0, recipes.size() - 1));
                if (Recipes.unlocked(ctx.player(), recipe) && Crafting.craftable(ctx.player().getInventory(), recipe) > 0) {
                    ClientPlayNetworking.send(new Crafting.Craft(recipe.id(), Minecraft.getInstance().hasShiftDown()));
                    SkyScreens.click();
                }
            }
            return true;
        }
        return false;
    }

    /** The item in the list under this point, or null. */
    private String cellAt(PageContext ctx, double x, double y) {
        if (x < ctx.x() || x >= ctx.x() + listW || y < listTop || y >= listTop + listHeight) {
            return null;
        }
        int col = (int) ((x - ctx.x()) / CELL);
        double inList = y - listTop + scroll;
        int row = (int) (inList / CELL);
        if (col >= columns || (x - ctx.x()) % CELL >= CELL - 2 || inList % CELL >= CELL - 2) {
            return null;
        }
        int index = row * columns + col;
        return index >= 0 && index < shown.size() ? shown.get(index) : null;
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double x, double y, double amount) {
        scroll -= amount * CELL * 2;
        return true;
    }

    @Override
    public boolean keyPressed(PageContext ctx, KeyEvent event) {
        if (search.keyPressed(event)) {
            return true;
        }
        // Digits and arrows would switch menu pages; while searching, they edit the search.
        int key = event.key();
        return !search.isEmpty() && (key >= InputConstants.KEY_0 && key <= InputConstants.KEY_9
                || key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_BACKSPACE);
    }

    @Override
    public boolean charTyped(PageContext ctx, CharacterEvent event) {
        return search.charTyped(event);
    }

    @Override
    public boolean back(PageContext ctx) {
        if (!search.isEmpty()) {
            search.clear();
            return true;
        }
        if (selected != null) {
            selected = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean canGoBack() {
        return !search.isEmpty() || selected != null;
    }
}
