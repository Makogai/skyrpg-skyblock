package com.skyrpg.skyblock.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.accessory.AccessoryTuning;
import com.skyrpg.skyblock.accessory.BagUpgrades;
import com.skyrpg.skyblock.accessory.MaxwellMenu;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Maxwell's screen, in three tabs. Accessory Powers: your Magical Power and its multiplier, then a card per power with
 * what it gives you now, its unique bonus and what you can do with it (pick it, learn it with 9 Power Stones, or the
 * Combat level it needs). Stats Tuning: a card for each of the eight stats with its Tuning Points (+ and -, right-click
 * adds, Shift moves ten), a reset, and the saved template. Bag Slots: where the bag's slots come from and what Jacobus,
 * the Redstone Miner and the Rift Prism ask for.
 */
public final class MaxwellScreen extends AbstractContainerScreen<MaxwellMenu> {
    private static final int WIDTH = 320;
    private static final int HEIGHT = 252;
    private static final int CARD_H = 34;
    private static final int BAG_CARD_H = 30;
    private static final int TUNE_H = 34;
    private static final int GOLD = 0xFFFFAA00;
    private static final ScreenTheme THEME = ScreenTheme.MAGIC;
    private static final String[] TABS = {"Powers", "Stats Tuning", "Bag Slots"};

    private double scroll;
    private int tab;

    public MaxwellScreen(MaxwellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.tab = menu.view().tab();
    }

    private int headerY() {
        return topPos + SkyScreens.contentTop(THEME) + 1;
    }

    private int tabY() {
        return headerY() + 12;
    }

    private int listTop() {
        return headerY() + 29;
    }

    private int listHeight() {
        return topPos + imageHeight - listTop() - 12;
    }

    private int cardX() {
        return leftPos + THEME.inset;
    }

    private int rows() {
        return tab == MaxwellMenu.POWERS ? menu.view().powers().size() : 5;
    }

    private int cardH() {
        return tab == MaxwellMenu.POWERS ? CARD_H : BAG_CARD_H;
    }

    private int cardW() {
        return imageWidth - 2 * THEME.inset - (rows() * (cardH() + 3) > listHeight() ? 6 : 0);
    }

    private int tabX(int i) {
        return leftPos + THEME.inset + i * (tabW() + 4);
    }

    private int tabW() {
        return (imageWidth - 2 * THEME.inset - 8) / 3;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        Font font = this.font;
        MaxwellMenu.View view = menu.view();
        SkyScreens.window(g, font, THEME, leftPos, topPos, imageWidth, imageHeight, Component.literal("Accessory Bag Thaumaturgy"),
                Component.literal("Maxwell").withColor(THEME.muted));
        int hy = headerY();
        Draw.item(g, new ItemStack(Items.NETHER_STAR), leftPos + THEME.inset, hy - 3, 0.75f);
        Draw.fit(g, font, Component.literal(view.magicalPower() + " Magical Power").withColor(GOLD)
                        .append(Component.literal("  ·  stats ×" + String.format(java.util.Locale.ROOT, "%.2f", view.multiplier())).withColor(THEME.muted)),
                leftPos + THEME.inset + 16, hy, imageWidth - 2 * THEME.inset - 16, 0.9f, 0.6f, Draw.Align.LEFT, THEME.text, true);
        for (int i = 0; i < TABS.length; i++) {
            SkyScreens.tab(g, font, THEME, tabX(i), tabY(), tabW(), 13, TABS[i], i == tab, SkyScreens.inside(mouseX, mouseY, tabX(i), tabY(), tabW(), 13));
        }
        List<Component> tooltip = switch (tab) {
            case MaxwellMenu.TUNING -> drawTuning(g, font, mouseX, mouseY);
            case MaxwellMenu.BAGS -> drawBags(g, font, mouseX, mouseY);
            default -> drawPowers(g, font, mouseX, mouseY);
        };
        if (tooltip != null) {
            Draw.queueTooltip(tooltip, THEME.accent);
        }
        Draw.drawQueuedTooltip(g, font, mouseX, mouseY);
    }

    private List<Component> drawPowers(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        MaxwellMenu.View view = menu.view();
        List<MaxwellMenu.PowerView> powers = view.powers();
        int top = listTop();
        int height = listHeight();
        int content = powers.size() * (CARD_H + 3);
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - height));
        List<Component> tooltip = null;
        g.enableScissor(leftPos + 4, top, leftPos + imageWidth - 4, top + height);
        for (int i = 0; i < powers.size(); i++) {
            MaxwellMenu.PowerView power = powers.get(i);
            int cy = top + i * (CARD_H + 3) - (int) Math.round(scroll);
            if (cy + CARD_H < top || cy > top + height) {
                continue;
            }
            int cx = cardX();
            int cw = cardW();
            boolean clickable = power.state() == 0 || power.state() == 2 && power.stonesHave() >= MaxwellMenu.STONES;
            boolean hover = SkyScreens.inside(mouseX, mouseY, cx, Math.max(cy, top), cw, Math.min(CARD_H, top + height - Math.max(cy, top)));
            boolean over = clickable && hover;
            SkyScreens.card(g, THEME, cx, cy, cw, CARD_H, over || power.state() == 1, power.state() == 3);
            if (power.state() == 1) {
                Draw.outline(g, cx, cy, cw, CARD_H, GOLD);
            }
            int nameColor = power.state() == 3 ? THEME.dim : power.state() == 1 ? GOLD : THEME.text;
            SkyScreens.well(g, THEME, cx + 6, cy + (CARD_H - 18) / 2);
            ItemStack icon = power.stone().isEmpty() ? new ItemStack(Items.AMETHYST_SHARD) : power.stone();
            Draw.item(g, icon, cx + 7, cy + (CARD_H - 16) / 2, 1f);
            int tx = cx + 30;
            int right = cx + cw - 6;
            int rightW = power.state() == 2 ? 100 : 62;
            Draw.fit(g, font, Component.literal(power.name()), tx, cy + 4, right - rightW - tx, 1f, 0.6f, Draw.Align.LEFT, nameColor, true);
            Draw.fit(g, font, Component.literal(power.stats().isEmpty() ? "No stats" : power.stats()), tx, cy + 15, cw - 36, 0.7f, 0.4f,
                    Draw.Align.LEFT, power.state() == 3 ? THEME.dim : THEME.good, false);
            if (!power.unique().isEmpty()) {
                Draw.fit(g, font, Component.literal("Unique: " + power.unique()), tx, cy + 24, cw - 36, 0.65f, 0.4f, Draw.Align.LEFT,
                        power.state() == 3 ? THEME.dim : THEME.accent, false);
            }
            switch (power.state()) {
                case 1 -> Draw.fit(g, font, Component.literal("Your power"), right, cy + 5, rightW, 0.8f, 0.45f, Draw.Align.RIGHT, GOLD, true);
                case 0 -> SkyScreens.button(g, font, THEME, right - 50, cy + 4, 50, 13, "Select", true, over);
                case 2 -> {
                    boolean enough = power.stonesHave() >= MaxwellMenu.STONES;
                    SkyScreens.button(g, font, THEME, right - 44, cy + 4, 44, 13, "Learn", enough, over);
                    if (!power.stone().isEmpty()) {
                        Chip chip = Chip.item(power.stone(), MaxwellMenu.STONES, power.stonesHave());
                        String have = " (" + power.stonesHave() + ")";
                        SkyScreens.chip(g, font, THEME, right - 44 - 6 - SkyScreens.chipWidth(font, chip, 0.7f)
                                - Math.round(font.width(have) * 0.6f), cy + 6, chip, 0.7f);
                        Draw.scaledText(g, font, Component.literal(have), right - 44 - 4 - Math.round(font.width(have) * 0.6f), cy + 7, 0.6f, THEME.muted);
                    }
                }
                default -> Draw.fit(g, font, Component.literal("Needs Combat " + power.combat()), right, cy + 5, rightW, 0.8f, 0.45f,
                        Draw.Align.RIGHT, Theme.RED, false);
            }
            if (hover) {
                tooltip = new ArrayList<>();
                tooltip.add(Component.literal(power.name()).withColor(nameColor));
                tooltip.add(Component.literal(power.stats().isEmpty() ? "No stats" : power.stats()).withColor(THEME.good));
                if (!power.unique().isEmpty()) {
                    tooltip.add(Component.literal("Unique: " + power.unique()).withColor(THEME.accent));
                }
                tooltip.add(Component.empty());
                tooltip.add(switch (power.state()) {
                    case 1 -> Component.literal("Your power right now").withColor(GOLD);
                    case 0 -> Component.literal("Click to select").withColor(THEME.good);
                    case 2 -> Component.literal(power.stonesHave() >= MaxwellMenu.STONES ? "Click to learn it with " + MaxwellMenu.STONES + " "
                            + power.stone().getHoverName().getString() : "Needs " + MaxwellMenu.STONES + " " + power.stone().getHoverName().getString()
                            + " (" + power.stonesHave() + ")").withColor(power.stonesHave() >= MaxwellMenu.STONES ? THEME.good : Theme.RED);
                    default -> Component.literal("Needs Combat " + power.combat()).withColor(Theme.RED);
                });
            }
        }
        g.disableScissor();
        if (content > height) {
            Draw.scrollbar(g, leftPos + imageWidth - THEME.inset - 4, top, height, content, scroll, THEME.accent);
        }
        return tooltip;
    }

    // ---------------------------------------------------------------- Stats Tuning

    private static ItemStack tuneIcon(String id) {
        return new ItemStack(switch (id) {
            case "health" -> Items.GOLDEN_APPLE;
            case "defense" -> Items.IRON_CHESTPLATE;
            case "speed" -> Items.SUGAR;
            case "strength" -> Items.BLAZE_POWDER;
            case "crit_damage" -> Items.NETHERITE_SWORD;
            case "crit_chance" -> Items.IRON_SWORD;
            case "attack_speed" -> Items.GOLDEN_AXE;
            default -> Items.ENCHANTED_BOOK;
        });
    }

    private int tuneW() {
        return (imageWidth - 2 * THEME.inset - 4) / 2;
    }

    private int tuneX(int i) {
        return cardX() + (i % 2) * (tuneW() + 4);
    }

    private int tuneY(int i) {
        return listTop() + (i / 2) * (TUNE_H + 3);
    }

    private int buttonsY() {
        return listTop() + 4 * (TUNE_H + 3) + 2;
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Msg.number((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private List<Component> drawTuning(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        MaxwellMenu.View view = menu.view();
        List<Component> tooltip = null;
        int left = view.unassigned();
        for (int i = 0; i < view.tuning().size(); i++) {
            MaxwellMenu.TuneView tune = view.tuning().get(i);
            StatType stat = StatTypes.get(tune.id());
            int cx = tuneX(i);
            int cy = tuneY(i);
            int cw = tuneW();
            boolean hover = SkyScreens.inside(mouseX, mouseY, cx, cy, cw, TUNE_H);
            SkyScreens.card(g, THEME, cx, cy, cw, TUNE_H, hover, false);
            SkyScreens.well(g, THEME, cx + 5, cy + (TUNE_H - 18) / 2);
            Draw.item(g, tuneIcon(tune.id()), cx + 6, cy + (TUNE_H - 16) / 2, 1f);
            int tx = cx + 28;
            int color = stat == null ? THEME.text : stat.color();
            String name = stat == null ? tune.id() : stat.name();
            Draw.fit(g, font, Component.literal((stat == null ? "" : stat.symbol() + " ") + name).withColor(color), tx, cy + 4, cw - 28 - 34, 0.85f, 0.5f,
                    Draw.Align.LEFT, color, true);
            Draw.fit(g, font, Component.literal(tune.points() + (tune.points() == 1 ? " point" : " points")), tx, cy + 15, cw - 28 - 4, 0.75f, 0.45f,
                    Draw.Align.LEFT, tune.points() > 0 ? THEME.good : THEME.muted, false);
            Draw.fit(g, font, Component.literal("+" + number(tune.perPoint()) + " each  ·  you have " + number(tune.have())), tx, cy + 24, cw - 28 - 4,
                    0.65f, 0.4f, Draw.Align.LEFT, THEME.muted, false);
            boolean minus = SkyScreens.inside(mouseX, mouseY, cx + cw - 30, cy + 4, 12, 12);
            boolean plus = SkyScreens.inside(mouseX, mouseY, cx + cw - 16, cy + 4, 12, 12);
            SkyScreens.button(g, font, THEME, cx + cw - 30, cy + 4, 12, 12, "-", tune.points() > 0, minus);
            SkyScreens.button(g, font, THEME, cx + cw - 16, cy + 4, 12, 12, "+", left > 0, plus);
            if (hover) {
                tooltip = new ArrayList<>();
                tooltip.add(Component.literal(name).withColor(color));
                tooltip.add(Component.literal(tune.points() + " points  ·  +" + number(tune.points() * tune.perPoint()) + " " + name).withColor(THEME.good));
                tooltip.add(Component.literal("Each point gives +" + number(tune.perPoint()) + ".").withColor(THEME.muted));
                tooltip.add(Component.empty());
                tooltip.add(Component.literal("Click + to add a point, - to take one back").withColor(THEME.text));
                tooltip.add(Component.literal("Right-click the card to add one; hold Shift for ten").withColor(THEME.muted));
            }
        }
        int by = buttonsY();
        int bw = (imageWidth - 2 * THEME.inset - 8) / 3;
        String[] labels = {"Reset points", "Save template", "Load template"};
        boolean spent = view.unassigned() < view.tuningPoints();
        boolean[] enabled = {spent, spent, view.template()};
        for (int i = 0; i < 3; i++) {
            int bx = cardX() + i * (bw + 4);
            boolean over = SkyScreens.inside(mouseX, mouseY, bx, by, bw, 14);
            SkyScreens.button(g, font, THEME, bx, by, bw, 14, labels[i], enabled[i], over && enabled[i]);
            if (over) {
                tooltip = List.of(Component.literal(labels[i]).withColor(THEME.text), Component.literal(switch (i) {
                    case 0 -> "Take every point back to spend again.";
                    case 1 -> "Keep this spread of points as your template.";
                    default -> view.template() ? "Put your template's points back on." : "No template saved yet.";
                }).withColor(THEME.muted));
            }
        }
        Component points = Component.literal(view.tuningPoints() + " Tuning Points").withColor(GOLD)
                .append(Component.literal("  ·  " + left + " to spend").withColor(left > 0 ? THEME.good : THEME.muted))
                .append(Component.literal("  ·  1 per " + AccessoryTuning.POWER_PER_POINT + " Magical Power").withColor(THEME.dim));
        Draw.fit(g, font, points, cardX(), by + 19, imageWidth - 2 * THEME.inset, 0.75f, 0.45f, Draw.Align.LEFT, THEME.text, false);
        return tooltip;
    }

    // ---------------------------------------------------------------- Bag Slots

    private record BagCard(ItemStack icon, String title, String detail, List<Chip> chips, String button, boolean enabled, int id,
                           List<Component> tip) {
    }

    private List<BagCard> bagCards() {
        MaxwellMenu.BagView bags = menu.view().bags();
        List<BagCard> out = new ArrayList<>();
        int minerSlots = bags.miner();
        int jacobusSlots = 2 * bags.jacobus();
        out.add(new BagCard(new ItemStack(Items.ENDER_CHEST), bags.slots() + " slots", "Redstone collection " + bags.redstone() + "/"
                + MaxwellMenu.REDSTONE_MAX + "  ·  Miner " + minerSlots + "/" + BagUpgrades.MINER_STEPS + "  ·  Jacobus " + jacobusSlots + "/"
                + 2 * BagUpgrades.JACOBUS_MAX, List.of(), "", false, -1, List.of(
                Component.literal("Accessory Bag size").withColor(THEME.text),
                Component.literal("Redstone collection: " + bags.redstone() + " of " + MaxwellMenu.REDSTONE_MAX + " (6 a tier from II)").withColor(THEME.muted),
                Component.literal("Redstone Miner: " + minerSlots + " of " + BagUpgrades.MINER_STEPS).withColor(THEME.muted),
                Component.literal("Jacobus: " + jacobusSlots + " of " + 2 * BagUpgrades.JACOBUS_MAX).withColor(THEME.muted))));
        boolean minerDone = bags.minerNeeds() == 0;
        out.add(new BagCard(new ItemStack(Items.REDSTONE), "Redstone Miner", minerDone ? "All four slots given" : "A slot for " + bags.minerNeeds() + " Redstone ("
                + (bags.miner() + 1) + "/" + BagUpgrades.MINER_STEPS + ")", minerDone ? List.of()
                : List.of(Chip.item(new ItemStack(Items.REDSTONE), bags.minerNeeds(), bags.minerHave())), minerDone ? "Done" : "Hand over",
                !minerDone && bags.minerHave() >= bags.minerNeeds(), MaxwellMenu.MINER, List.of(
                Component.literal("Redstone Miner").withColor(THEME.text),
                Component.literal("Needs Redstone collection II. One slot each for 128, 256, 512 and 1,024 Redstone.").withColor(THEME.muted),
                Component.literal("2 SkyBlock XP an upgrade").withColor(THEME.good))));
        boolean jacobusDone = bags.jacobus() >= BagUpgrades.JACOBUS_MAX;
        out.add(new BagCard(new ItemStack(Items.GOLD_INGOT), "Jacobus", jacobusDone ? "Every slot bought" : "+2 slots  ·  bought " + bags.jacobus() + "/" + BagUpgrades.JACOBUS_MAX,
                jacobusDone ? List.of() : List.of(Chip.price("coins", bags.jacobusCost(), bags.purse())), jacobusDone ? "Done" : "Buy",
                !jacobusDone && bags.purse() >= bags.jacobusCost(), MaxwellMenu.BUY, List.of(
                Component.literal("Jacobus").withColor(THEME.text),
                Component.literal("Two slots a purchase, up to 198. 1.5M coins, then 5M, 8M, 12M, 20M as you buy.").withColor(THEME.muted),
                Component.literal("2 SkyBlock XP a purchase; his Register after the fifth").withColor(THEME.good))));
        out.add(new BagCard(new ItemStack(Items.PRISMARINE_SHARD), "Rift Prism", bags.imbued() ? "Imbued: +" + BagUpgrades.IMBUE_POWER + " Magical Power"
                : "Imbue it for +" + BagUpgrades.IMBUE_POWER + " Magical Power", bags.imbued() ? List.of()
                : List.of(Chip.price("coins", BagUpgrades.IMBUE_COST, bags.purse()), Chip.note(new ItemStack(Items.PRISMARINE_SHARD), "Rift Prism", bags.prism())),
                bags.imbued() ? "Done" : "Imbue", !bags.imbued() && bags.prism() && bags.purse() >= BagUpgrades.IMBUE_COST, MaxwellMenu.IMBUE, List.of(
                Component.literal("Rift Prism").withColor(THEME.text),
                Component.literal("Use up a Rift Prism in your inventory for good: 200,000 coins for 11 Magical Power.").withColor(THEME.muted))));
        out.add(new BagCard(new ItemStack(Items.COMPARATOR), "Abicase", bags.abicase() ? "In your bag: +" + bags.contacts() / 2 + " Magical Power"
                : "Put an Abicase in the bag for +1 Magical Power per 2 contacts", List.of(Chip.note(new ItemStack(Items.WRITABLE_BOOK), bags.contacts() + "/80 contacts", true)),
                "", false, -1, List.of(Component.literal("Abicase").withColor(THEME.text),
                Component.literal("Every NPC you've talked to is a contact (80 at most). One Abicase counts.").withColor(THEME.muted))));
        return out;
    }

    private List<Component> drawBags(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY) {
        List<BagCard> cards = bagCards();
        int top = listTop();
        int height = listHeight();
        int content = cards.size() * (BAG_CARD_H + 3);
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - height));
        List<Component> tooltip = null;
        g.enableScissor(leftPos + 4, top, leftPos + imageWidth - 4, top + height);
        for (int i = 0; i < cards.size(); i++) {
            BagCard card = cards.get(i);
            int cy = top + i * (BAG_CARD_H + 3) - (int) Math.round(scroll);
            if (cy + BAG_CARD_H < top || cy > top + height) {
                continue;
            }
            int cx = cardX();
            int cw = cardW();
            boolean hover = SkyScreens.inside(mouseX, mouseY, cx, Math.max(cy, top), cw, Math.min(BAG_CARD_H, top + height - Math.max(cy, top)));
            boolean over = hover && card.id() >= 0 && card.enabled();
            SkyScreens.card(g, THEME, cx, cy, cw, BAG_CARD_H, over, false);
            SkyScreens.well(g, THEME, cx + 6, cy + (BAG_CARD_H - 18) / 2);
            Draw.item(g, card.icon(), cx + 7, cy + (BAG_CARD_H - 16) / 2, 1f);
            int tx = cx + 30;
            int right = cx + cw - 6;
            int buttonW = card.button().isEmpty() ? 0 : 52;
            Draw.fit(g, font, Component.literal(card.title()), tx, cy + 4, right - buttonW - tx - 4, 0.95f, 0.55f, Draw.Align.LEFT, THEME.text, true);
            Draw.fit(g, font, Component.literal(card.detail()), tx, cy + 15, right - tx, 0.65f, 0.4f, Draw.Align.LEFT, THEME.muted, false);
            if (!card.chips().isEmpty()) {
                SkyScreens.chips(g, font, THEME, tx, cy + 21, card.chips(), 0.7f, right);
            }
            if (!card.button().isEmpty()) {
                SkyScreens.button(g, font, THEME, right - 50, cy + 4, 50, 13, card.button(), card.enabled(), over);
            }
            if (hover) {
                tooltip = card.tip();
            }
        }
        g.disableScissor();
        if (content > height) {
            Draw.scrollbar(g, leftPos + imageWidth - THEME.inset - 4, top, height, content, scroll, THEME.accent);
        }
        return tooltip;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The window draws its own title and labels.
    }

    private void press(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        SkyScreens.click();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT || event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            for (int i = 0; i < TABS.length; i++) {
                if (SkyScreens.inside(mx, my, tabX(i), tabY(), tabW(), 13) && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
                    if (i != tab) {
                        tab = i;
                        scroll = 0;
                        press(MaxwellMenu.TAB + i);
                    }
                    return true;
                }
            }
            if (tab == MaxwellMenu.TUNING) {
                return clickTuning(event) || super.mouseClicked(event, doubleClick);
            }
            if (tab == MaxwellMenu.BAGS) {
                if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && SkyScreens.inside(mx, my, cardX(), listTop(), cardW(), listHeight())) {
                    int index = (int) ((my - listTop() + scroll) / (BAG_CARD_H + 3));
                    List<BagCard> cards = bagCards();
                    if (index >= 0 && index < cards.size() && (my - listTop() + scroll) % (BAG_CARD_H + 3) < BAG_CARD_H) {
                        BagCard card = cards.get(index);
                        if (card.id() >= 0 && card.enabled()) {
                            press(card.id());
                        }
                        return true;
                    }
                }
                return super.mouseClicked(event, doubleClick);
            }
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && tab == MaxwellMenu.POWERS) {
            List<MaxwellMenu.PowerView> powers = menu.view().powers();
            int top = listTop();
            if (SkyScreens.inside(mx, my, cardX(), top, cardW(), listHeight())) {
                int index = (int) ((my - top + scroll) / (CARD_H + 3));
                if (index >= 0 && index < powers.size() && (my - top + scroll) % (CARD_H + 3) < CARD_H) {
                    MaxwellMenu.PowerView power = powers.get(index);
                    if (power.state() == 0) {
                        press(index);
                    } else if (power.state() == 2 && power.stonesHave() >= MaxwellMenu.STONES) {
                        press(MaxwellMenu.LEARN + index);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean clickTuning(MouseButtonEvent event) {
        double mx = event.x();
        double my = event.y();
        boolean shift = event.hasShiftDown();
        MaxwellMenu.View view = menu.view();
        for (int i = 0; i < view.tuning().size(); i++) {
            int cx = tuneX(i);
            int cy = tuneY(i);
            int cw = tuneW();
            if (!SkyScreens.inside(mx, my, cx, cy, cw, TUNE_H)) {
                continue;
            }
            boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
            if (!right && SkyScreens.inside(mx, my, cx + cw - 30, cy + 4, 12, 12)) {
                press((shift ? MaxwellMenu.REMOVE_TEN : MaxwellMenu.REMOVE) + i);
            } else if (right || SkyScreens.inside(mx, my, cx + cw - 16, cy + 4, 12, 12)) {
                press((shift ? MaxwellMenu.ADD_TEN : MaxwellMenu.ADD) + i);
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            int by = buttonsY();
            int bw = (imageWidth - 2 * THEME.inset - 8) / 3;
            boolean spent = view.unassigned() < view.tuningPoints();
            for (int i = 0; i < 3; i++) {
                if (SkyScreens.inside(mx, my, cardX() + i * (bw + 4), by, bw, 14)) {
                    if (i == 0 && spent) {
                        press(MaxwellMenu.RESET);
                    } else if (i == 1 && spent) {
                        press(MaxwellMenu.SAVE_TEMPLATE);
                    } else if (i == 2 && view.template()) {
                        press(MaxwellMenu.LOAD_TEMPLATE);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab != MaxwellMenu.TUNING) {
            scroll -= scrollY * 20;
        }
        return true;
    }
}
