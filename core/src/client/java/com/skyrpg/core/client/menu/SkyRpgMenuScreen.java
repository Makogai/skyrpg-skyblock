package com.skyrpg.core.client.menu;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.SkyRpgCoreClient;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The SkyRPG menu window: a sidebar listing every registered page, the selected page's header, the page, and a
 * footer with key hints.
 *
 * <p>The window is laid out in its own units and drawn scaled to the screen (see {@link MenuLayout}), so it covers
 * the same share of a big monitor as of a laptop, with crisp text. Everything inside (pages included) works in those
 * units; this class converts the mouse.
 *
 * <p>Opens with a short slide-in; 1-9 or the arrow keys switch pages; the menu key closes it; Esc steps back
 * inside the page if it can, else closes (keys go to the page first, so one with a text field can take them). On
 * narrow windows the sidebar folds down to icons (hover one for its name) to leave the page room. When the pages
 * don't fit down the sidebar, the list scrolls (wheel over it, a thin scrollbar, fades at the cut edges), and a page
 * taller than the window ({@link MenuPage#scrollHeight}) scrolls inside the content area.
 */
public class SkyRpgMenuScreen extends Screen {
    private static final int SIDEBAR_MIN = 108;
    private static final int SIDEBAR_MAX = 148;
    /** The icons-only sidebar used when the window is narrower than {@link #FULL_SIDEBAR_FROM}. */
    private static final int COMPACT_SIDEBAR = 36;
    private static final int FULL_SIDEBAR_FROM = 440;
    private static final int NAV_TOP = 46;
    private static final int COMPACT_NAV_TOP = 12;
    /** With many pages the "Menu" label under the logo goes and the list starts here instead. */
    private static final int CROWDED_NAV_TOP = 34;
    private static final int NAV_HEIGHT = 22;
    private static final int MIN_NAV_HEIGHT = 18;
    /** The smallest a nav row gets once the player row is gone: an item icon's height. */
    private static final int TIGHT_NAV_HEIGHT = 16;
    private static final int NAV_GAP = 2;
    private static final int PAD = 14;
    /** Title, subtitle and the line under them. */
    private static final int HEADER = 44;
    private static final int FOOTER = 16;
    /** Space between the header line and the page, and between the page and the footer. */
    private static final int CONTENT_GAP = 7;
    /** The player row at the bottom of the sidebar: its top is this far above the window's bottom. */
    private static final int PLAYER_ROW = 31;
    /** How far one notch of the mouse wheel moves a scrolled list. */
    private static final int WHEEL_STEP = 28;

    private final long openedAt = Util.getMillis();
    private final Map<Identifier, Float> navHover = new HashMap<>();
    private MenuPage page;
    private long pageOpenedAt = openedAt;
    private long lastFrame = openedAt;
    private MenuLayout layout;
    /** A press on the page started a drag the page asked for, so moves and the release go to it. */
    private boolean pageDrag;
    private final Scroll navScroll = new Scroll();
    private final Scroll pageScroll = new Scroll();
    /** The page's {@link MenuPage#scrollHeight} this frame, when it's taller than the content area (else 0). */
    private int pageNeed;

    /** A scroll position that eases towards where the wheel sent it. */
    private static final class Scroll {
        double target;
        double pos;

        double update(int max, float frameSeconds) {
            target = Mth.clamp(target, 0, Math.max(0, max));
            pos += (target - pos) * (1 - Math.exp(-frameSeconds * 16));
            if (Math.abs(target - pos) < 0.3 || !Double.isFinite(pos)) {
                pos = target;
            }
            return pos;
        }

        void reset() {
            target = 0;
            pos = 0;
        }
    }

    public SkyRpgMenuScreen(MenuPage page) {
        super(Component.literal("SkyRPG"));
        this.page = page;
        SkyRpgMenu.remember(page);
        page.opened();
    }

    @Override
    protected void init() {
        relayout();
        // Opening on a page far down a scrolling list shows it.
        revealActive();
        navScroll.pos = navScroll.target;
    }

    private void relayout() {
        Window window = minecraft.getWindow();
        layout = MenuLayout.compute(window.getWidth(), window.getHeight(), window.getGuiScale(), SkyRpgMenu.size());
    }

    @Override
    public void added() {
        super.added();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2f, 0.6f));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private float intro() {
        return Draw.easeOut((Util.getMillis() - openedAt) / 220f);
    }

    /** The window slides up into place as the menu opens. */
    private int slide() {
        return Math.round((1 - intro()) * 10);
    }

    // ------------------------------------------------------------------ layout (menu units)

    private boolean compact() {
        return layout.width() < FULL_SIDEBAR_FROM;
    }

    private int sidebar() {
        return compact() ? COMPACT_SIDEBAR : Mth.clamp(layout.width() / 4, SIDEBAR_MIN, SIDEBAR_MAX);
    }

    private int navTop() {
        return layout.top() + (compact() ? COMPACT_NAV_TOP : crowded() ? CROWDED_NAV_TOP : NAV_TOP);
    }

    /** Too many pages for normal rows under the full logo block. */
    private boolean crowded() {
        int pages = Math.max(1, SkyRpgMenu.pages().size());
        return layout.top() + NAV_TOP + pages * (MIN_NAV_HEIGHT + NAV_GAP) > layout.bottom() - PLAYER_ROW - 10;
    }

    /**
     * Nav rows shrink a little if there are many pages, so they never run into the player row. When even the smallest
     * rows don't fit above it, the player row goes and the rows may shrink to the icon's height. When even those don't
     * fit, the rows stay at a comfortable height and the list scrolls ({@link #navOverflows}).
     */
    private int navHeight() {
        int pages = Math.max(1, SkyRpgMenu.pages().size());
        int withPlayerRow = (layout.bottom() - PLAYER_ROW - 10 - navTop()) / pages - NAV_GAP;
        if (withPlayerRow >= MIN_NAV_HEIGHT) {
            return Math.min(withPlayerRow, NAV_HEIGHT);
        }
        int withoutPlayerRow = (layout.bottom() - 6 - navTop()) / pages - NAV_GAP;
        if (withoutPlayerRow >= TIGHT_NAV_HEIGHT) {
            return Math.min(withoutPlayerRow, NAV_HEIGHT);
        }
        return MIN_NAV_HEIGHT;
    }

    /** The player row is dropped when the pages need its space. */
    private boolean showPlayerRow() {
        int pages = SkyRpgMenu.pages().size();
        return navTop() + pages * (navHeight() + NAV_GAP) <= layout.bottom() - PLAYER_ROW - 8;
    }

    /** Where the page list ends: above the player row, or near the window's bottom without it. */
    private int navBottom() {
        return showPlayerRow() ? layout.bottom() - PLAYER_ROW - 8 : layout.bottom() - 4;
    }

    /** The whole list's height, rows and gaps. */
    private int navContentHeight() {
        return SkyRpgMenu.pages().size() * (navHeight() + NAV_GAP) - NAV_GAP;
    }

    /** More pages than fit down the sidebar: the list scrolls. */
    private boolean navOverflows() {
        return navTop() + navContentHeight() > navBottom();
    }

    private int navMaxScroll() {
        return Math.max(0, navContentHeight() - (navBottom() - navTop()));
    }

    /** The y of page {@code index}'s row, with the list's scroll. */
    private int navRowY(int index) {
        return navTop() + index * (navHeight() + NAV_GAP) - (int) Math.round(navScroll.pos);
    }

    private boolean overSidebar(double mouseX, double mouseY) {
        return mouseX >= layout.left() && mouseX < layout.left() + sidebar() && mouseY >= navTop() && mouseY < navBottom();
    }

    /** Scrolls the page list so the open page's row is in view (after picking one with a key). */
    private void revealActive() {
        if (!navOverflows()) {
            return;
        }
        int index = SkyRpgMenu.pages().indexOf(page);
        int rowTop = index * (navHeight() + NAV_GAP);
        int area = navBottom() - navTop();
        if (rowTop < navScroll.target) {
            navScroll.target = rowTop;
        } else if (rowTop + navHeight() > navScroll.target + area) {
            navScroll.target = rowTop + navHeight() - area;
        }
    }

    private int contentX() {
        return layout.left() + sidebar() + 1 + PAD;
    }

    private int contentWidth() {
        return layout.width() - sidebar() - 1 - PAD * 2;
    }

    private int contentY() {
        return layout.top() + HEADER + CONTENT_GAP;
    }

    private int contentHeight() {
        return layout.bottom() - FOOTER - CONTENT_GAP - contentY();
    }

    /** The mouse in menu units (not counting the intro slide). */
    private double menuMouseX() {
        return minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()) / layout.scale();
    }

    private double menuMouseY() {
        return minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()) / layout.scale();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, Draw.withAlpha(Theme.OVERLAY, intro()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (layout == null || layout.size() != SkyRpgMenu.size()) {
            // The Menu size setting was just changed (on the Settings page, inside this menu).
            relayout();
        }
        long now = Util.getMillis();
        float frameSeconds = Math.min((now - lastFrame) / 1000f, 0.1f);
        lastFrame = now;
        int slide = slide();
        double menuX = menuMouseX();
        double menuY = menuMouseY();
        int mx = (int) Math.floor(menuX);
        int my = (int) Math.floor(menuY) - slide;

        Draw.clearQueuedTooltip();
        g.pose().pushMatrix();
        g.pose().scale(layout.scale(), layout.scale());
        g.pose().translate(0, slide);
        drawWindow(g);
        drawSidebar(g, mx, my, frameSeconds);
        drawFooter(g);
        drawPage(g, mx, my, frameSeconds);
        g.pose().popMatrix();

        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // Tooltips from the page or sidebar, on top of everything, kept on screen in menu units.
        g.pose().pushMatrix();
        g.pose().scale(layout.scale(), layout.scale());
        Draw.drawQueuedTooltip(g, font, (int) menuX, (int) menuY, layout.screenWidth(), layout.screenHeight());
        g.pose().popMatrix();
    }

    private void drawWindow(GuiGraphicsExtractor g) {
        int left = layout.left();
        int top = layout.top();
        int w = layout.width();
        int h = layout.height();
        int sidebar = sidebar();
        Draw.shadow(g, left, top, w, h, 8, 0.55f * intro());
        Draw.panel(g, left, top, w, h, Theme.WINDOW, Theme.BORDER);
        g.fill(left + 1, top + 1, left + sidebar, top + h - 1, Theme.SIDEBAR);
        g.fill(left + sidebar, top + 1, left + sidebar + 1, top + h - 1, Theme.BORDER);
        // Brand stripe along the top edge, brightest over the page.
        g.fill(left + 2, top, left + sidebar, top + 1, Draw.withAlpha(Theme.ACCENT, 0.55f));
        g.fill(left + sidebar, top, left + w - 2, top + 1, Draw.withAlpha(Theme.ACCENT, 0.85f));
        // Footer strip.
        g.fill(left + sidebar + 1, top + h - FOOTER, left + w - 1, top + h - 2, Theme.FOOTER);
        g.fill(left + sidebar + 1, top + h - FOOTER, left + w - 1, top + h - FOOTER + 1, Theme.BORDER);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mouseX, int mouseY, float frameSeconds) {
        boolean compact = compact();
        int sidebar = sidebar();
        int left = layout.left();
        int top = layout.top();
        if (!compact) {
            Draw.scaledText(g, font, Component.literal("SKY").withColor(Theme.TEXT).append(Component.literal("RPG").withColor(Theme.ACCENT)),
                    left + 12, top + 13, 1.5f, Theme.TEXT);
            if (!crowded()) {
                Draw.text(g, font, "Menu", left + 12, top + 28, Theme.DIM);
            }
        }

        int navHeight = navHeight();
        boolean overflow = navOverflows();
        navScroll.update(overflow ? navMaxScroll() : 0, frameSeconds);
        int navTop = navTop();
        int navBottom = navBottom();
        if (overflow) {
            g.enableScissor(left + 1, navTop, left + sidebar, navBottom);
        }
        boolean mouseInList = !overflow || (mouseY >= navTop && mouseY < navBottom);
        List<MenuPage> pages = SkyRpgMenu.pages();
        for (int i = 0; i < pages.size(); i++) {
            MenuPage candidate = pages.get(i);
            int y = navRowY(i);
            if (y + navHeight < navTop || y > navBottom) {
                continue;
            }
            int x = left + 6;
            int w = sidebar - 12;
            boolean active = candidate == page;
            boolean hovered = mouseInList && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + navHeight;
            float hover = navHover.getOrDefault(candidate.id(), 0f);
            hover = Mth.lerp(Math.min(1, frameSeconds * 14), hover, hovered ? 1 : 0);
            navHover.put(candidate.id(), hover);

            if (hovered && !active) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            if (active) {
                Draw.rounded(g, x, y, w, navHeight, Theme.ACCENT_SOFT);
                g.fill(x, y + 5, x + 2, y + navHeight - 5, Theme.ACCENT);
            } else if (hover > 0.02f) {
                Draw.rounded(g, x, y, w, navHeight, Draw.withAlpha(Theme.HOVER, hover));
            }
            int iconY = y + (navHeight - 16) / 2;
            int nudge = Math.round(hover * 1.5f);
            if (compact) {
                g.item(candidate.icon(), x + (w - 16) / 2 + 1, iconY);
                if (hovered) {
                    Draw.queueTooltip(List.of(candidate.title().copy().withColor(Theme.TEXT)), Theme.ACCENT);
                }
            } else {
                g.item(candidate.icon(), x + 6 + nudge, iconY);
                int color = active ? Theme.TEXT : Draw.mix(Theme.MUTED, Theme.TEXT, hover);
                int keyRoom = i < 9 ? 10 : 0;
                Draw.fitText(g, font, candidate.title(), x + 27 + nudge, y + (navHeight - 8) / 2, w - 27 - 4 - keyRoom, color);
                if (i < 9) {
                    // The number key that opens this page, faint until hovered.
                    Draw.rightText(g, font, Integer.toString(i + 1), x + w - 5, y + (navHeight - 8) / 2,
                            Draw.withAlpha(Theme.DIM, active ? 0.9f : 0.35f + 0.55f * hover));
                }
            }
        }
        if (overflow) {
            g.disableScissor();
            // Fades where rows are cut off, and a thin scrollbar along the sidebar's edge.
            g.nextStratum();
            double max = navMaxScroll();
            if (navScroll.pos > 0.5) {
                g.fillGradient(left + 1, navTop, left + sidebar, navTop + 10, Theme.SIDEBAR, Draw.withAlpha(Theme.SIDEBAR, 0f));
            }
            if (navScroll.pos < max - 0.5) {
                g.fillGradient(left + 1, navBottom - 10, left + sidebar, navBottom, Draw.withAlpha(Theme.SIDEBAR, 0f), Theme.SIDEBAR);
            }
            Draw.scrollbar(g, left + sidebar - 3, navTop, navBottom - navTop, navContentHeight(), navScroll.pos, Theme.DIM);
        }

        if (minecraft.player != null && showPlayerRow()) {
            int footerY = layout.bottom() - 24;
            g.fill(left + 10, footerY - 7, left + sidebar - 10, footerY - 6, Theme.BORDER);
            if (compact) {
                PlayerFaceExtractor.extractRenderState(g, minecraft.player.getSkin(), left + (sidebar - 12) / 2, footerY, 12);
            } else {
                PlayerFaceExtractor.extractRenderState(g, minecraft.player.getSkin(), left + 12, footerY, 12);
                Draw.fitText(g, font, minecraft.player.getName(), left + 30, footerY + 2, sidebar - 30 - 8, Theme.TEXT);
            }
        }
    }

    /** Key hints along the bottom: the page's on the left, Esc on the right. */
    private void drawFooter(GuiGraphicsExtractor g) {
        int y = layout.bottom() - FOOTER + 4;
        int right = layout.right() - PAD + 4;
        int escWidth = KeyHints.width(font, page.canGoBack() ? "[Esc] back" : "[Esc] close");
        KeyHints.draw(g, font, page.canGoBack() ? "[Esc] back" : "[Esc] close", right - escWidth, y, escWidth);
        String hint = page.hint().getString();
        if (hint.isEmpty() && SkyRpgMenu.pages().size() > 1) {
            hint = "[1-" + Math.min(9, SkyRpgMenu.pages().size()) + "] pages";
        }
        if (!hint.isEmpty()) {
            KeyHints.draw(g, font, hint, contentX(), y, right - escWidth - 12 - contentX());
        }
    }

    private void drawPage(GuiGraphicsExtractor g, int mouseX, int mouseY, float frameSeconds) {
        float appear = Draw.easeOut((Util.getMillis() - pageOpenedAt) / 180f);
        int shift = Math.round((1 - appear) * 6);
        int x = contentX();
        int room = contentWidth();
        int top = layout.top();

        // Header: title, subtitle and a line with a short accent under the title.
        g.pose().pushMatrix();
        g.pose().translate(shift, 0);
        Draw.fit(g, font, page.title(), x, top + 13, room, 1.5f, 1f, Draw.Align.LEFT, Theme.TEXT, false);
        if (Draw.fitText(g, font, page.subtitle(), x, top + 29, room, Theme.MUTED)) {
            Draw.hoverTooltip(mouseX - shift, mouseY, x, top + 28, room, 10, List.of(page.subtitle()), Theme.ACCENT);
        }
        g.pose().popMatrix();
        int lineY = top + HEADER - 1;
        g.fill(x, lineY, x + room, lineY + 1, Theme.BORDER);
        int underline = Math.min(room, Math.round(Draw.fittedWidth(font, page.title(), room, 1.5f, 1f) * appear));
        g.fill(x, lineY, x + underline, lineY + 1, Theme.ACCENT);

        // A page taller than the content area gets its full height, scrolled and cut to the area.
        int need = page.scrollHeight(room);
        pageNeed = need > contentHeight() ? need : 0;
        double offset = pageScroll.update(pageNeed > 0 ? pageNeed - contentHeight() : 0, frameSeconds);
        if (pageNeed > 0) {
            g.enableScissor(x - PAD + 2, contentY(), x + room + PAD - 2, contentY() + contentHeight());
        }
        g.pose().pushMatrix();
        g.pose().translate(shift, -(float) Math.round(offset));
        page.render(context(g, mouseX - shift, mouseY));
        g.pose().popMatrix();
        if (pageNeed > 0) {
            g.disableScissor();
            g.nextStratum();
            Draw.scrollbar(g, x + room + 5, contentY(), contentHeight(), pageNeed, offset, Theme.DIM);
        }
    }

    /**
     * The page's context for this mouse position (menu units). While the page scrolls, it's the page's full height
     * and the mouse is moved by the scroll; a mouse outside the visible area hovers nothing on the page.
     */
    private PageContext context(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (pageNeed <= 0) {
            return new PageContext(g, font, minecraft.player, contentX(), contentY(), contentWidth(), contentHeight(),
                    mouseX, mouseY, pageOpenedAt);
        }
        int offset = (int) Math.round(pageScroll.pos);
        boolean inside = mouseY >= contentY() && mouseY < contentY() + contentHeight();
        return new PageContext(g, font, minecraft.player, contentX(), contentY(), contentWidth(), pageNeed,
                mouseX, inside ? mouseY + offset : Integer.MIN_VALUE / 2, pageOpenedAt);
    }

    /** The mouse's y as the page sees it, for input handlers that take it separately from the context. */
    private double pageY(double mouseY) {
        return pageNeed > 0 ? mouseY + Math.round(pageScroll.pos) : mouseY;
    }

    // ------------------------------------------------------------------ input

    private void select(MenuPage target) {
        if (target == page) {
            return;
        }
        page = target;
        pageOpenedAt = Util.getMillis();
        pageDrag = false;
        pageScroll.reset();
        pageNeed = 0;
        SkyRpgMenu.remember(target);
        target.opened();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.4f));
    }

    /** Menu units from GUI coordinates, as the page sees them (after the intro slide). */
    private double unitX(double guiX) {
        return guiX / layout.scale();
    }

    private double unitY(double guiY) {
        return guiY / layout.scale() - slide();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = unitX(event.x());
        double mouseY = unitY(event.y());
        int navHeight = navHeight();
        List<MenuPage> pages = SkyRpgMenu.pages();
        boolean inList = !navOverflows() || (mouseY >= navTop() && mouseY < navBottom());
        for (int i = 0; inList && i < pages.size(); i++) {
            int y = navRowY(i);
            if (mouseX >= layout.left() + 6 && mouseX < layout.left() + sidebar() - 6 && mouseY >= y && mouseY < y + navHeight) {
                select(pages.get(i));
                return true;
            }
        }
        PageContext ctx = context(null, (int) Math.floor(mouseX), (int) Math.floor(mouseY));
        if (pageNeed > 0 && ctx.mouseY() == Integer.MIN_VALUE / 2) {
            // A click above or below the scrolled page's visible part isn't on the page.
            return super.mouseClicked(event, doubleClick);
        }
        if (page.mouseClicked(ctx, mouseX, pageY(mouseY), event.button())) {
            pageDrag = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (pageDrag) {
            double mouseX = unitX(event.x());
            double mouseY = unitY(event.y());
            if (page.mouseDragged(dragContext(mouseX, mouseY), mouseX, pageY(mouseY), event.button())) {
                return true;
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pageDrag) {
            pageDrag = false;
            double mouseX = unitX(event.x());
            double mouseY = unitY(event.y());
            if (page.mouseReleased(dragContext(mouseX, mouseY), mouseX, pageY(mouseY), event.button())) {
                return true;
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double x = unitX(mouseX);
        double y = unitY(mouseY);
        if (navOverflows() && overSidebar(x, y)) {
            navScroll.target -= scrollY * WHEEL_STEP;
            return true;
        }
        if (page.mouseScrolled(context(null, (int) Math.floor(x), (int) Math.floor(y)), x, pageY(y), scrollY)) {
            return true;
        }
        if (pageNeed > 0) {
            pageScroll.target -= scrollY * WHEEL_STEP;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** A drag keeps going outside the visible part (a slider pulled past the edge), so the mouse is only shifted. */
    private PageContext dragContext(double mouseX, double mouseY) {
        PageContext ctx = context(null, (int) Math.floor(mouseX), (int) Math.floor(mouseY));
        return pageNeed > 0 ? new PageContext(null, font, minecraft.player, ctx.x(), ctx.y(), ctx.width(), ctx.height(),
                (int) Math.floor(mouseX), (int) Math.floor(pageY(mouseY)), pageOpenedAt) : ctx;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            // Back inside the page first (clear a search, leave a pane); close when there's nowhere to go back to.
            if (page.back(inputContext())) {
                return true;
            }
            onClose();
            return true;
        }
        // The page first, so a text field on it can take digits and letters.
        if (page.keyPressed(inputContext(), event)) {
            return true;
        }
        if (SkyRpgCoreClient.MENU_KEY.matches(event)) {
            onClose();
            return true;
        }
        List<MenuPage> pages = SkyRpgMenu.pages();
        int index = pages.indexOf(page);
        int digit = event.key() - InputConstants.KEY_1;
        if (digit >= 0 && digit < Math.min(9, pages.size())) {
            select(pages.get(digit));
            revealActive();
            return true;
        }
        if (event.key() == InputConstants.KEY_DOWN || event.key() == InputConstants.KEY_UP) {
            int step = event.key() == InputConstants.KEY_DOWN ? 1 : -1;
            select(pages.get(Math.floorMod(index + step, pages.size())));
            revealActive();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return page.charTyped(inputContext(), event) || super.charTyped(event);
    }

    /** A page context for keyboard input, with the mouse where it is now. */
    private PageContext inputContext() {
        return context(null, (int) Math.floor(menuMouseX()), (int) Math.floor(menuMouseY()) - slide());
    }

    /**
     * Footer hints: text where "[Key]" parts are drawn as little key caps, e.g. "[Tab] switch pane  [/] search".
     * Shared by the menu and pages that want the same look.
     */
    static final class KeyHints {
        private KeyHints() {
        }

        static int width(Font font, String hint) {
            int width = 0;
            for (String part : parts(hint)) {
                width += part.startsWith("[") ? font.width(part.substring(1, part.length() - 1)) + 6 : font.width(part);
            }
            return width;
        }

        /** Draws as much of the hint as fits in {@code room}, whole parts only. */
        static void draw(GuiGraphicsExtractor g, Font font, String hint, int x, int y, int room) {
            int cursor = x;
            for (String part : parts(hint)) {
                boolean key = part.startsWith("[");
                String text = key ? part.substring(1, part.length() - 1) : part;
                int w = key ? font.width(text) + 6 : font.width(text);
                if (cursor + w - x > room) {
                    return;
                }
                if (key) {
                    Draw.panel(g, cursor, y - 2, w, 11, Theme.KEY, Theme.CARD_BORDER);
                    Draw.text(g, font, text, cursor + 3, y, Theme.MUTED);
                } else {
                    Draw.text(g, font, text, cursor, y, Theme.DIM);
                }
                cursor += w;
            }
        }

        private static List<String> parts(String hint) {
            List<String> parts = new java.util.ArrayList<>();
            int i = 0;
            while (i < hint.length()) {
                int open = hint.indexOf('[', i);
                int close = open < 0 ? -1 : hint.indexOf(']', open);
                if (open < 0 || close < 0) {
                    parts.add(hint.substring(i));
                    break;
                }
                if (open > i) {
                    parts.add(hint.substring(i, open));
                }
                parts.add(hint.substring(open, close + 1));
                i = close + 1;
            }
            return parts;
        }
    }
}
