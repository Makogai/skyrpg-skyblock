package com.skyrpg.skyblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.travel.FastTravel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Fast Travel, as in Hypixel's SkyBlock Menu: every warp, the ones you've unlocked clickable. */
public final class FastTravelPage implements MenuPage {
    private static final int COLUMNS = 2;
    private static final int CARD_H = 34;
    private static final int GAP = 5;

    private static List<FastTravel.Entry> warps = List.of();

    private final List<int[]> hits = new ArrayList<>();

    static void set(FastTravel.WarpList payload) {
        warps = List.copyOf(payload.warps());
    }

    static List<FastTravel.Entry> warps() {
        return warps;
    }

    @Override
    public Identifier id() {
        return SkyRpgSkyblock.id("fast_travel");
    }

    @Override
    public Component title() {
        return Component.literal("Fast Travel");
    }

    @Override
    public Component subtitle() {
        return Component.literal("Warp to places you've been. Visit an island once to unlock its warp");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.ENDER_EYE);
    }

    @Override
    public int order() {
        return 34;
    }

    /** Every warp's card and the line under them. */
    @Override
    public int scrollHeight(int width) {
        return (warps.size() + COLUMNS - 1) / COLUMNS * (CARD_H + GAP) + 14;
    }

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        hits.clear();
        int cardW = (ctx.width() - GAP * (COLUMNS - 1)) / COLUMNS;
        for (int i = 0; i < warps.size(); i++) {
            FastTravel.Entry warp = warps.get(i);
            int x = ctx.x() + i % COLUMNS * (cardW + GAP);
            int y = ctx.y() + i / COLUMNS * (CARD_H + GAP);
            boolean over = warp.unlocked() && ctx.hovered(x, y, cardW, CARD_H);
            Draw.panel(g, x, y, cardW, CARD_H, over ? Theme.CARD_HOVER : Theme.CARD, over ? Theme.AQUA : Theme.CARD_BORDER);
            ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.getValue(warp.icon()));
            Draw.item(g, icon, x + 6, y + (CARD_H - 16) / 2, 1f);
            Draw.fitText(g, font, warp.name(), x + 28, y + 7, cardW - 34, warp.unlocked() ? Theme.AQUA : Theme.DIM);
            String line = warp.unlocked() ? "/warp " + warp.id() + "  ·  " + warp.description() : "Not visited yet";
            Draw.fitText(g, font, line, x + 28, y + 19, cardW - 34, warp.unlocked() ? Theme.MUTED : Theme.DIM);
            if (warp.unlocked()) {
                hits.add(new int[] {x, y, cardW, CARD_H, i});
            }
        }
        int more = warps.size();
        int y = ctx.y() + (more + COLUMNS - 1) / COLUMNS * (CARD_H + GAP) + 4;
        Draw.fitText(g, font, "More islands (the Barn, the Park, the Gold Mine and the rest) are on the way.", ctx.x(), y, ctx.width(), Theme.DIM);
    }

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (int[] hit : hits) {
            if (x >= hit[0] && x < hit[0] + hit[2] && y >= hit[1] && y < hit[1] + hit[3]) {
                var connection = Minecraft.getInstance().getConnection();
                if (connection != null) {
                    connection.sendCommand("warp " + warps.get(hit[4]).id());
                }
                Minecraft.getInstance().gui.setScreen(null);
                return true;
            }
        }
        return false;
    }
}
