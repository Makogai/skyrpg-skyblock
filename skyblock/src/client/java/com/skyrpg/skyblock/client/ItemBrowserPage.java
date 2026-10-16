package com.skyrpg.skyblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.menu.SearchField;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * Every SkyBlock item in a searchable grid, with its full tooltip on hover. Type to search by name or SkyBlock id;
 * admins click an item to get one ({@code /sbitem}).
 */
public final class ItemBrowserPage implements MenuPage {
    private static final int CELL = 20;

    private final SearchField search = new SearchField("Search items by name or id");
    private double scroll;
    private List<ItemDefinition> shown = List.of();
    private String shownFor;
    private int shownSize = -1;
    private final Map<Identifier, ItemStack> stacks = new HashMap<>();
    private ItemDefinition hovered;
    private int fieldW;

    @Override
    public Identifier id() {
        return SkyRpgSkyblock.id("items");
    }

    @Override
    public Component title() {
        return Component.literal("SkyBlock Items");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Every SkyBlock item. Type to search; admins click one to get it");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.CHEST);
    }

    @Override
    public int order() {
        return 985;
    }

    private void refresh() {
        int size = ItemRegistry.items().size();
        if (search.text().equals(shownFor) && size == shownSize) {
            return;
        }
        String needle = search.text().toLowerCase(Locale.ROOT).trim();
        String asId = needle.replace(' ', '_');
        shown = ItemRegistry.items().stream()
                .filter(ItemDefinition::skyblock)
                .filter(def -> needle.isEmpty() || def.name().toLowerCase(Locale.ROOT).contains(needle)
                        || def.skyblockId().toLowerCase(Locale.ROOT).contains(asId))
                .sorted((a, b) -> a.rarity() != b.rarity() ? a.rarity().compareTo(b.rarity()) : a.name().compareToIgnoreCase(b.name()))
                .toList();
        shownFor = search.text();
        shownSize = size;
        scroll = 0;
    }

    private ItemStack stack(ItemDefinition def) {
        return stacks.computeIfAbsent(def.id(), id -> SkyItems.create(def, 1));
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        refresh();
        hovered = null;
        String count = shown.size() + " items";
        fieldW = ctx.width() - font.width(count) - 10;
        search.draw(g, font, ctx.x(), ctx.y(), fieldW, true, ctx.hovered(ctx.x(), ctx.y(), fieldW, SearchField.HEIGHT), ctx.mouseX());
        Draw.text(g, font, count, ctx.x() + ctx.width() - font.width(count), ctx.y() + 4, Theme.MUTED);

        int top = ctx.y() + SearchField.HEIGHT + 5;
        int height = ctx.height() - SearchField.HEIGHT - 5;
        int columns = Math.max(1, (ctx.width() - 6) / CELL);
        int rows = (shown.size() + columns - 1) / columns;
        int content = rows * CELL;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - height));
        int firstRow = (int) (scroll / CELL);
        int offset = (int) (scroll % CELL);
        g.enableScissor(ctx.x(), top, ctx.x() + ctx.width(), top + height);
        for (int row = firstRow; row < rows && (row - firstRow) * CELL - offset < height; row++) {
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index >= shown.size()) {
                    break;
                }
                ItemDefinition def = shown.get(index);
                int x = ctx.x() + col * CELL;
                int y = top + (row - firstRow) * CELL - offset;
                boolean over = ctx.hovered(x, y, CELL - 2, CELL - 2) && ctx.mouseY() >= top && ctx.mouseY() < top + height;
                g.fill(x, y, x + CELL - 2, y + CELL - 2, over ? Theme.CARD_HOVER : Theme.CARD);
                g.fill(x, y + CELL - 3, x + CELL - 2, y + CELL - 2, def.rarity().color());
                Draw.item(g, stack(def), x + 1, y + 1, 1f);
                if (over) {
                    hovered = def;
                }
            }
        }
        g.disableScissor();
        if (content > height) {
            Draw.scrollbar(g, ctx.x() + ctx.width() - 3, top, height, content, scroll, Theme.DIM);
        }
        if (hovered != null) {
            ItemStack stack = stack(hovered);
            List<Component> lines = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(ctx.player().level()), ctx.player(), TooltipFlag.NORMAL));
            lines.add(Component.literal(hovered.skyblockId()).withColor(Theme.DIM));
            ctx.tooltip(lines, hovered.rarity().color());
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
        if (hovered == null) {
            return false;
        }
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.sendCommand("sbitem " + hovered.skyblockId().toLowerCase(Locale.ROOT).replace(':', '-'));
        }
        return true;
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
        if (search.isEmpty()) {
            return false;
        }
        search.clear();
        return true;
    }

    @Override
    public boolean canGoBack() {
        return !search.isEmpty();
    }
}
