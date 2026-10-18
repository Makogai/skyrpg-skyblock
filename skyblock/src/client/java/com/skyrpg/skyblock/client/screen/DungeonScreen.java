package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.skyblock.dungeon.DungeonMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Mort's screen: your class (five cards) and the floors (eight cards); click a floor to take your party in. */
public final class DungeonScreen extends AbstractContainerScreen<DungeonMenu> {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 218;
    private static final String[] CLASSES = {"Healer", "Mage", "Berserk", "Archer", "Tank"};
    private static final ItemStack[] CLASS_ICONS = {new ItemStack(Items.SPLASH_POTION), new ItemStack(Items.BLAZE_ROD), new ItemStack(Items.IRON_SWORD),
            new ItemStack(Items.BOW), new ItemStack(Items.LEATHER_CHESTPLATE)};
    private static final ItemStack[] FLOOR_ICONS = {new ItemStack(Items.SKELETON_SKULL), new ItemStack(Items.ZOMBIE_HEAD), new ItemStack(Items.REDSTONE),
            new ItemStack(Items.SPLASH_POTION), new ItemStack(Items.GHAST_TEAR), new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.CLAY_BALL),
            new ItemStack(Items.WITHER_SKELETON_SKULL)};
    /** Each class: its passive, its ability (drop key) and its ultimate (sneak + drop key). */
    private static final String[][] CLASS_INFO = {
            {"Heals allies within 8 blocks (1% health a second)", "Healing Circle: heal allies nearby 25% (10s)", "Wish: everyone to full health, +20% absorption (120s)"},
            {"+100 Intelligence (+3 a level), -25% ability cooldowns", "Guided Sheep: a blast 10 blocks ahead (30s)", "Thunderstorm: lightning on mobs around you for 10s (100s)"},
            {"+20 Strength (+1 a level)", "Throwing Axe: hits the first mob in 15 blocks (10s)", "Ragnarok: +100 Strength and Speed for 15s (60s)"},
            {"+10 Crit Damage (+1 a level)", "Explosive Shot: three blasts where you aim (40s)", "Rapid Fire: five shots a second at your target for 4s (100s)"},
            {"+100 Health, +50 Defense (more each level)", "Seismic Wave: mobs within 7 blocks, more with Defense (15s)", "Castle of Stone: take 70% less damage for 20s (150s)"}};
    private static final int RED = 0xFFE04848;
    private static final ScreenTheme THEME = ScreenTheme.DUNGEON;
    private static final int CLASS_Y = 48;
    private static final int FLOOR_Y = 102;
    private static final int HEADER_Y = 87;
    private static final int MAX_CLASS_LEVEL = 50;

    public DungeonScreen(DungeonMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    private int classW() {
        return (imageWidth - 20 - 4 * 5) / 5;
    }

    private int classX(int i) {
        return leftPos + 10 + i * (classW() + 5);
    }

    private int floorW() {
        return (imageWidth - 20 - 3 * 5) / 4;
    }

    private int floorX(int i) {
        return leftPos + 10 + (i % 4) * (floorW() + 5);
    }

    private int floorY(int i) {
        return topPos + FLOOR_Y + (i / 4) * 48;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        DungeonMenu.View view = menu.view();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("The Catacombs"),
                Component.literal("Catacombs " + view.catacombs() + "  ·  Party of " + view.party()).withColor(RED));
        Draw.fit(g, font, Component.literal("Your class"), leftPos + 10, topPos + CLASS_Y - 9, 100, 0.8f, 0.5f, Draw.Align.LEFT, THEME.accent, false);
        List<Component> tooltip = null;
        for (int i = 0; i < 5; i++) {
            int x = classX(i);
            int y = topPos + CLASS_Y;
            boolean picked = CLASSES[i].equalsIgnoreCase(view.picked());
            boolean hover = SkyScreens.inside(mouseX, mouseY, x, y, classW(), 34);
            SkyScreens.card(g, THEME, x, y, classW(), 34, hover, false);
            if (picked) {
                Draw.outline(g, x, y, classW(), 34, THEME.good);
            }
            g.fill(x + 3, y + 4, x + 22, y + 23, 0x55000000);
            Draw.item(g, CLASS_ICONS[i], x + 4, y + 5, 1f);
            Draw.fit(g, font, Component.literal(CLASSES[i]), x + 25, y + 6, classW() - 28, 0.75f, 0.45f, Draw.Align.LEFT,
                    picked ? THEME.good : THEME.text, true);
            Draw.fit(g, font, Component.literal(picked ? "Selected" : "Level " + view.classLevel(i)), x + 25, y + 16, classW() - 28, 0.62f, 0.4f,
                    Draw.Align.LEFT, picked ? THEME.good : THEME.muted, false);
            SkyScreens.bar(g, THEME, x + 4, y + 27, classW() - 8, 4, view.classLevel(i) / (float) MAX_CLASS_LEVEL, picked ? THEME.good : THEME.accent);
            if (hover) {
                tooltip = List.of(Component.literal(CLASSES[i] + "  ·  Level " + view.classLevel(i)).withColor(THEME.good),
                        Component.literal(CLASS_INFO[i][0]).withColor(THEME.muted), Component.empty(),
                        Component.literal("Drop key: " + CLASS_INFO[i][1]).withColor(THEME.text),
                        Component.literal("Sneak + drop key: " + CLASS_INFO[i][2]).withColor(0xFFFFAA00), Component.empty(),
                        Component.literal(picked ? "Your class" : "Click to pick").withColor(picked ? THEME.good : THEME.muted));
            }
        }
        Draw.fit(g, font, Component.literal(view.leader() ? "Pick a floor" : "Your party's leader picks the floor"), leftPos + 10, topPos + HEADER_Y + 3, 130,
                0.8f, 0.5f, Draw.Align.LEFT, THEME.accent, false);
        boolean finderOver = SkyScreens.inside(mouseX, mouseY, finderX(), topPos + HEADER_Y, 80, 13);
        SkyScreens.button(g, font, THEME, finderX(), topPos + HEADER_Y, 80, 13, "Party Finder", true, finderOver);
        if (view.masterOpen()) {
            boolean over = SkyScreens.inside(mouseX, mouseY, modeX(), topPos + HEADER_Y, 90, 13);
            SkyScreens.button(g, font, THEME, modeX(), topPos + HEADER_Y, 90, 13, view.master() ? "Master Mode" : "Normal floors", true, over);
            if (view.master()) {
                Draw.outline(g, modeX(), topPos + HEADER_Y, 90, 13, RED);
            }
        }
        List<DungeonMenu.FloorView> floors = view.floors();
        List<Component> floorTip = null;
        for (int i = 0; i < floors.size(); i++) {
            DungeonMenu.FloorView floor = floors.get(i);
            int x = floorX(i);
            int y = floorY(i);
            boolean hover = SkyScreens.inside(mouseX, mouseY, x, y, floorW(), 44);
            SkyScreens.card(g, THEME, x, y, floorW(), 44, hover && floor.open(), !floor.open());
            g.fill(x + 3, y + 4, x + 22, y + 23, 0x55000000);
            Draw.item(g, FLOOR_ICONS[Math.min(i, FLOOR_ICONS.length - 1)], x + 4, y + 5, 1f);
            Draw.fit(g, font, Component.literal(floor.title()), x + 25, y + 6, floorW() - 28, 0.8f, 0.5f, Draw.Align.LEFT,
                    floor.open() ? THEME.text : THEME.dim, true);
            Draw.fit(g, font, Component.literal(floor.boss()), x + 25, y + 16, floorW() - 28, 0.62f, 0.4f, Draw.Align.LEFT, THEME.muted, false);
            Draw.fit(g, font, Component.literal(floor.open() ? "Click to enter" : "Needs Catacombs " + floor.requires()), x + 5, y + 31, floorW() - 10,
                    0.65f, 0.4f, Draw.Align.LEFT, floor.open() ? THEME.good : RED, false);
            if (!floor.open()) {
                g.nextStratum();
                g.fill(x + 3, y + 4, x + 22, y + 23, 0x99100808);
            }
            if (hover) {
                floorTip = List.of(Component.literal("The Catacombs - " + floor.title()).withColor(RED),
                        Component.literal("Boss: " + floor.boss()).withColor(THEME.muted),
                        Component.literal("Requires Catacombs " + floor.requires()).withColor(floor.open() ? THEME.good : RED));
            }
        }
        if (floorTip != null) {
            tooltip = floorTip;
        }
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, THEME.accent);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private int finderX() {
        return leftPos + imageWidth - 186;
    }

    private int modeX() {
        return leftPos + imageWidth - 100;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int i = 0; i < 5; i++) {
                if (SkyScreens.inside(event.x(), event.y(), classX(i), topPos + CLASS_Y, classW(), 34)) {
                    return press(DungeonMenu.CLASS + i);
                }
            }
            if (SkyScreens.inside(event.x(), event.y(), finderX(), topPos + HEADER_Y, 80, 13)) {
                return press(DungeonMenu.FINDER);
            }
            if (menu.view().masterOpen() && SkyScreens.inside(event.x(), event.y(), modeX(), topPos + HEADER_Y, 90, 13)) {
                return press(DungeonMenu.MODE);
            }
            List<DungeonMenu.FloorView> floors = menu.view().floors();
            for (int i = 0; i < floors.size(); i++) {
                if (floors.get(i).open() && SkyScreens.inside(event.x(), event.y(), floorX(i), floorY(i), floorW(), 44)) {
                    return press(DungeonMenu.FLOOR + floors.get(i).number());
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean press(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        SkyScreens.click();
        return true;
    }
}
