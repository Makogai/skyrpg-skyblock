package com.skyrpg.skyblock.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.travel.Lifts;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** A Lift Operator's lift: every stop, top to bottom; the ones you've been to take you there with a click. */
final class LiftScreen extends Screen {
    private static final int WIDTH = 230;
    private static final int ROW = 26;
    private static final int HEADER = 40;
    private static final int ACCENT = 0xFFFFD25A;
    private static final ItemStack ICON = new ItemStack(Items.IRON_CHAIN);
    private static final ItemStack STOP = new ItemStack(Items.MINECART);

    private final Lifts.Menu menu;
    private int left;
    private int top;
    private int listHeight;
    private double scroll;

    LiftScreen(Lifts.Menu menu) {
        super(Component.literal(menu.name()));
        this.menu = menu;
    }

    @Override
    protected void init() {
        listHeight = Math.min(Math.max(1, menu.stops().size()) * ROW, height - HEADER - 40);
        left = (width - WIDTH) / 2;
        top = (height - (HEADER + listHeight + 20)) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, 0x9006121A);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int windowHeight = HEADER + listHeight + 20;
        Draw.panel(g, left, top, WIDTH, windowHeight, Theme.WINDOW, 0xFF5A4A1F);
        Draw.item(g, ICON, left + 10, top + 9, 1.5f);
        Draw.fit(g, font, Component.literal(menu.name().toUpperCase(java.util.Locale.ROOT)), left + 40, top + 10, WIDTH - 50, Draw.Align.LEFT, ACCENT);
        Draw.fit(g, font, Component.literal("Ride to any layer you've been to"), left + 40, top + 22, WIDTH - 50, Draw.Align.LEFT, Theme.MUTED);

        int listTop = top + HEADER;
        List<Lifts.StopView> stops = menu.stops();
        g.enableScissor(left + 4, listTop, left + WIDTH - 4, listTop + listHeight);
        for (int i = 0; i < stops.size(); i++) {
            Lifts.StopView stop = stops.get(i);
            int y = listTop + i * ROW - (int) Math.round(scroll);
            if (y + ROW < listTop || y > listTop + listHeight) {
                continue;
            }
            int x = left + 8;
            int w = WIDTH - 16;
            boolean usable = stop.unlocked() && !stop.here();
            boolean hover = usable && mouseX >= x && mouseX < x + w && mouseY >= Math.max(y, listTop)
                    && mouseY < Math.min(y + ROW - 3, listTop + listHeight);
            Draw.panel(g, x, y, w, ROW - 3, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? ACCENT : Theme.CARD_BORDER);
            Draw.item(g, STOP, x + 4, y + 3, 1f);
            Draw.fit(g, font, Component.literal(stop.name()), x + 24, y + 7, w - 100, Draw.Align.LEFT,
                    stop.unlocked() ? Theme.TEXT : Theme.DIM);
            String state = stop.here() ? "You're here" : stop.unlocked() ? "Click to ride" : "Not visited yet";
            Draw.fit(g, font, Component.literal(state), x + w - 6, y + 7, 90, Draw.Align.RIGHT,
                    stop.here() ? Theme.GREEN : stop.unlocked() ? ACCENT : Theme.DIM);
        }
        g.disableScissor();
        int contentHeight = stops.size() * ROW;
        if (contentHeight > listHeight) {
            Draw.scrollbar(g, left + WIDTH - 5, listTop, listHeight, contentHeight, scroll, ACCENT);
        }
        Draw.fit(g, font, Component.literal("Click a layer to ride · Esc to close"), left + WIDTH / 2, top + windowHeight - 13,
                WIDTH - 20, Draw.Align.CENTER, Theme.DIM);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }
        int listTop = top + HEADER;
        double mx = event.x();
        double my = event.y();
        if (mx >= left + 8 && mx < left + WIDTH - 8 && my >= listTop && my < listTop + listHeight) {
            int index = (int) ((my - listTop + scroll) / ROW);
            if (index >= 0 && index < menu.stops().size() && (my - listTop + scroll) % ROW < ROW - 3) {
                Lifts.StopView stop = menu.stops().get(index);
                if (stop.unlocked() && !stop.here()) {
                    ClientPlayNetworking.send(new Lifts.Ride(menu.lift(), index));
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int contentHeight = menu.stops().size() * ROW;
        scroll = Mth.clamp(scroll - scrollY * 18, 0, Math.max(0, contentHeight - listHeight));
        return true;
    }
}
