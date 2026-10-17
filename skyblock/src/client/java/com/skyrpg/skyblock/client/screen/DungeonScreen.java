package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
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
    private static final int HEIGHT = 214;
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
    private static final int CLASS_Y = 46;
    private static final int FLOOR_Y = 100;

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
        return topPos + FLOOR_Y + (i / 4) * 50;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        DungeonMenu.View view = menu.view();
        SkyScreens.window(g, font, leftPos, topPos, imageWidth, imageHeight, Component.literal("The Catacombs"),
                Component.literal("Catacombs " + view.catacombs() + "  ·  Party of " + view.party()).withColor(RED));
        Draw.fit(g, font, Component.literal("Your class"), leftPos + 10, topPos + CLASS_Y - 11, 100, 0.8f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);
        for (int i = 0; i < 5; i++) {
            int x = classX(i);
            int y = topPos + CLASS_Y;
            boolean picked = CLASSES[i].equalsIgnoreCase(view.picked());
            boolean hover = SkyScreens.inside(mouseX, mouseY, x, y, classW(), 34);
            Draw.panel(g, x, y, classW(), 34, hover ? Theme.CARD_HOVER : Theme.CARD, picked ? Theme.GREEN : Theme.CARD_BORDER);
            Draw.item(g, CLASS_ICONS[i], x + 4, y + 9, 1f);
            Draw.fit(g, font, Component.literal(CLASSES[i]), x + 23, y + 7, classW() - 26, 0.75f, 0.45f, Draw.Align.LEFT,
                    picked ? Theme.GREEN : Theme.TEXT, false);
            Draw.fit(g, font, Component.literal("Level " + view.classLevel(i)), x + 23, y + 19, classW() - 26, 0.65f, 0.4f, Draw.Align.LEFT, Theme.MUTED, false);
            if (hover) {
                Draw.queueTooltip(List.of(Component.literal(CLASSES[i]).withColor(Theme.GREEN),
                        Component.literal(CLASS_INFO[i][0]).withColor(Theme.MUTED), Component.empty(),
                        Component.literal("Drop key: " + CLASS_INFO[i][1]).withColor(Theme.TEXT),
                        Component.literal("Sneak + drop key: " + CLASS_INFO[i][2]).withColor(0xFFFFAA00)), Theme.GREEN);
            }
        }
        Draw.fit(g, font, Component.literal(view.leader() ? "Pick a floor" : "Your party's leader picks the floor"), leftPos + 10, topPos + FLOOR_Y - 11, 200,
                0.8f, 0.5f, Draw.Align.LEFT, Theme.MUTED, false);
        List<DungeonMenu.FloorView> floors = view.floors();
        List<Component> tooltip = null;
        for (int i = 0; i < floors.size(); i++) {
            DungeonMenu.FloorView floor = floors.get(i);
            int x = floorX(i);
            int y = floorY(i);
            boolean hover = SkyScreens.inside(mouseX, mouseY, x, y, floorW(), 44);
            int edge = floor.open() ? RED : Theme.CARD_BORDER;
            Draw.panel(g, x, y, floorW(), 44, hover && floor.open() ? Theme.CARD_HOVER : Theme.CARD, edge);
            g.fill(x, y, x + 2, y + 44, floor.open() ? RED : Theme.DIM);
            Draw.item(g, FLOOR_ICONS[Math.min(i, FLOOR_ICONS.length - 1)], x + 6, y + 6, 1f);
            Draw.fit(g, font, Component.literal(floor.title()), x + 26, y + 6, floorW() - 30, 0.8f, 0.5f, Draw.Align.LEFT,
                    floor.open() ? Theme.TEXT : Theme.DIM, false);
            Draw.fit(g, font, Component.literal(floor.boss()), x + 26, y + 17, floorW() - 30, 0.65f, 0.4f, Draw.Align.LEFT, Theme.MUTED, false);
            Draw.fit(g, font, Component.literal(floor.open() ? "Click to enter" : "Catacombs " + floor.requires()), x + 6, y + 31, floorW() - 10,
                    0.65f, 0.4f, Draw.Align.LEFT, floor.open() ? Theme.GREEN : RED, false);
            if (hover) {
                tooltip = List.of(Component.literal("The Catacombs - " + floor.title()).withColor(RED),
                        Component.literal("Boss: " + floor.boss()).withColor(Theme.MUTED),
                        Component.literal("Requires Catacombs " + floor.requires()).withColor(floor.open() ? Theme.GREEN : RED));
            }
        }
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, RED);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
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
