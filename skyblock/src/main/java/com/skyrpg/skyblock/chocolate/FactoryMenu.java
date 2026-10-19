package com.skyrpg.skyblock.chocolate;

import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.time.CalendarEvents;
import com.skyrpg.skyblock.time.SkyBlockTime;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Chocolate Factory's screens (the CHOCOLATE card theme): the factory (click the chocolate, hire and upgrade, the
 * Time Tower, prestige), your rabbits, and the Chocolate Shop. While you have it open a stray rabbit sometimes runs
 * through (20% every 30 seconds, for 15 seconds): catch it for some seconds of production.
 */
final class FactoryMenu {
    private enum Page { FACTORY, RABBITS, SHOP }

    /** What each card of the screen a player has open does. */
    private static final Map<UUID, List<Consumer<ServerPlayer>>> ACTIONS = new HashMap<>();
    private static final Map<UUID, Page> OPEN = new HashMap<>();
    /** A stray rabbit on someone's screen: its rarity and the tick it runs off. */
    private record Stray(int rarity, long until) {
    }

    private static final Map<UUID, Stray> STRAYS = new HashMap<>();
    private static final Map<UUID, Long> NEXT_STRAY = new HashMap<>();
    private static final int[] STRAY_SECONDS = {5, 7, 10, 15, 20, 30, 50};

    /** The Chocolate Shop: an item, the Factory level it needs, its price, what else it costs, how many a year. */
    private record Offer(String item, int level, double price, String alsoItem, int stock) {
    }

    private static final List<Offer> SHOP = List.of(
            new Offer("SUPREME_CHOCOLATE_BAR", 1, 2e6, "", 500),
            new Offer("EGGLOCATOR", 1, 7.5e6, "", 1),
            new Offer("NIBBLE_CHOCOLATE_STICK", 2, 250e6, "", 1),
            new Offer("SMOOTH_CHOCOLATE_BAR", 2, 1e9, "NIBBLE_CHOCOLATE_STICK", 1),
            new Offer("RICH_CHOCOLATE_CHUNK", 3, 2e9, "SMOOTH_CHOCOLATE_BAR", 1),
            new Offer("GANACHE_CHOCOLATE_SLAB", 3, 3e9, "RICH_CHOCOLATE_CHUNK", 1),
            new Offer("PRESTIGE_CHOCOLATE_REALM", 4, 4.5e9, "GANACHE_CHOCOLATE_SLAB", 1),
            new Offer("DARK_CACAO_TRUFFLE", 4, 2.5e9, "", 3),
            new Offer("CHOCOLATE_FACTORY_BARN_SKIN", 5, 7e9, "", 1),
            new Offer("PET_ITEM_CHOCOLATE_SYRINGE", 5, 10e9, "", 1),
            new Offer("HOT_CHOCOLATE_MIXIN", 6, 1.5e9, "", 4));

    private FactoryMenu() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FactoryMenu::tick);
    }

    static void open(ServerPlayer player) {
        show(player, Page.FACTORY);
    }

    private static void show(ServerPlayer player, Page page) {
        OPEN.put(player.getUUID(), page);
        CardMenu.open(player, p -> view(p, OPEN.getOrDefault(p.getUUID(), Page.FACTORY)), FactoryMenu::click);
    }

    private static String click(ServerPlayer player, int card) {
        List<Consumer<ServerPlayer>> actions = ACTIONS.get(player.getUUID());
        if (actions == null || card < 0 || card >= actions.size() || actions.get(card) == null) {
            return "";
        }
        actions.get(card).accept(player);
        return OPEN.containsKey(player.getUUID()) ? "" : null;
    }

    private static CardView view(ServerPlayer player, Page page) {
        Factory f = ChocolateFactory.get(player);
        List<CardView.Card> cards = new ArrayList<>();
        List<Consumer<ServerPlayer>> actions = new ArrayList<>();
        switch (page) {
            case RABBITS -> rabbits(f, cards, actions);
            case SHOP -> shop(player, f, cards, actions);
            default -> factory(player, f, cards, actions);
        }
        ACTIONS.put(player.getUUID(), actions);
        ChocolateFactory.save(player, f);
        String subtitle = "Chocolate Factory " + Factory.LEVEL_NAMES[f.level - 1];
        String title = switch (page) {
            case RABBITS -> "Your Rabbits";
            case SHOP -> "Chocolate Shop";
            default -> "Chocolate Factory";
        };
        String footer = page == Page.FACTORY ? Msg.number(Math.round(f.perSecond(System.currentTimeMillis() ) * 10) / 10.0) + " Chocolate per second"
                : "Spent in the shop: " + Msg.compact(f.spent);
        return new CardView("CHOCOLATE", title, subtitle, -1, List.of(Chip.balance("chocolate", Math.round(f.chocolate))), cards, footer,
                2, 44);
    }

    // ---------------------------------------------------------------- the factory

    private static void factory(ServerPlayer player, Factory f, List<CardView.Card> cards, List<Consumer<ServerPlayer>> actions) {
        long now = System.currentTimeMillis();
        long tick = player.level().getServer().getTickCount();
        double mult = f.multiplier(now);
        add(cards, actions, new CardView.Card((f.chocolate >= 1e7 ? Msg.compact(f.chocolate) : Msg.number(Math.round(f.chocolate))) + " Chocolate", "+" + f.handBaked + " a click. Holds up to "
                + Msg.compact(Factory.MAX_CHOCOLATE[f.level - 1]) + ". All-time " + Msg.compact(f.allTime) + ".", 0xD7A36A, true, "",
                item("SUPREME_CHOCOLATE_BAR", Items.COCOA_BEANS), "", List.of(), (float) Math.min(1, f.chocolate / Factory.MAX_CHOCOLATE[f.level - 1]), "Bake"),
                p -> {
                    ChocolateFactory.edit(p, fac -> fac.gain(fac.handBaked));
                    Msg.playSound(p, SoundEvents.ITEM_PICKUP, 0.5f, 1.6f);
                });
        Stray stray = STRAYS.get(player.getUUID());
        // a stray takes the production card's place for its 15 seconds, so no card moves under your mouse
        if (stray != null && tick < stray.until()) {
            int seconds = STRAY_SECONDS[stray.rarity()] * (hunt() ? 2 : 1);
            add(cards, actions, new CardView.Card("A stray rabbit!", (stray.rarity() == 1 || stray.rarity() == 3 ? "An " : "A ") + Rabbits.rarityName(stray.rarity()).toLowerCase() + " rabbit is running through."
                    + " Catch it for " + seconds + "s of production!", 0xF3A6C0, true, "", new ItemStack(Items.RABBIT_SPAWN_EGG), "", List.of(), -1, "Catch"),
                    p -> {
                        STRAYS.remove(p.getUUID());
                        ChocolateFactory.edit(p, fac -> fac.gain(fac.perSecond(System.currentTimeMillis()) * seconds));
                        p.sendSystemMessage(Component.literal("You caught a stray rabbit! ").withStyle(ChatFormatting.LIGHT_PURPLE)
                                .append(Component.literal("+" + seconds + "s of production").withStyle(ChatFormatting.GOLD)));
                        Msg.playSound(p, SoundEvents.RABBIT_AMBIENT, 1f, 1.2f);
                    });
        } else {
            add(cards, actions, CardView.Card.info(Msg.number(Math.round(f.perSecond(now) * 10) / 10.0) + " per second", Msg.number(f.base()) + " base × "
                    + String.format("%.3f", mult) + (f.towerActive(now) ? " (Time Tower on!)" : ""), 0xF3A6C0, new ItemStack(Items.CLOCK)), null);
        }
        add(cards, actions, new CardView.Card("Rabbits: " + f.rabbits.size(), "Barn " + f.rabbits.size() + "/" + f.barnSize() + ". "
                + f.duplicates + " duplicates found.", 0xEEEEEE, true, "", new ItemStack(Items.RABBIT_SPAWN_EGG), "", List.of(), -1, "View"),
                p -> show(p, Page.RABBITS));
        for (int i = 0; i < 7; i++) {
            int index = i;
            boolean unlocked = f.employeeUnlocked(i);
            int lvl = f.employees[i];
            boolean max = lvl >= f.maxEmployeeLevel();
            double cost = f.employeeCost(i);
            String detail = !unlocked ? "Reach level 20 with " + Factory.EMPLOYEES[i - 1] + "."
                    : "+" + (i + 1) + " Chocolate a second each level (" + Msg.number(lvl * (i + 1)) + " now)." + (max ? " Max level." : "");
            add(cards, actions, new CardView.Card(Factory.EMPLOYEES[i] + " [" + lvl + "]", detail, 0xD7A36A, unlocked && !max && f.chocolate >= cost,
                    "", new ItemStack(Items.RABBIT_HIDE), "",
                    unlocked && !max ? List.of(Chip.price("chocolate", Math.round(cost), Math.round(f.chocolate))) : List.of(),
                    (float) lvl / f.maxEmployeeLevel(), !unlocked ? "Locked" : max ? "Max" : lvl == 0 ? "Hire" : "Promote"),
                    p -> buy(p, fac -> fac.employeeUnlocked(index) && fac.employees[index] < fac.maxEmployeeLevel(), fac -> fac.employeeCost(index),
                            fac -> fac.employees[index]++, Factory.EMPLOYEES[index]));
        }
        boolean handMax = f.handBaked >= 10;
        add(cards, actions, new CardView.Card("Hand-Baked Chocolate " + Msg.roman(f.handBaked), "+1 Chocolate a click each level.", 0xD7A36A,
                !handMax && f.chocolate >= f.handBakedCost(), "", new ItemStack(Items.COOKIE), "",
                handMax ? List.of() : List.of(Chip.price("chocolate", Math.round(f.handBakedCost()), Math.round(f.chocolate))), f.handBaked / 10f, handMax ? "Max" : "Upgrade"),
                p -> buy(p, fac -> fac.handBaked < 10, Factory::handBakedCost, fac -> fac.handBaked++, "Hand-Baked Chocolate"));
        add(cards, actions, new CardView.Card("Rabbit Barn " + Msg.roman(f.barn), "Room for " + f.barnSize() + " rabbits (+2 a level). Kept when you prestige.",
                0xD7A36A, f.chocolate >= f.barnCost(), "", new ItemStack(Items.HAY_BLOCK), "",
                List.of(Chip.price("chocolate", Math.round(f.barnCost()), Math.round(f.chocolate))), -1, "Upgrade"),
                p -> buy(p, fac -> true, Factory::barnCost, fac -> fac.barn++, "Rabbit Barn"));
        if (f.level >= 2) {
            boolean active = f.towerActive(now);
            String charge = f.towerCharges >= 3 ? "Charges full." : "Next charge in " + SkyBlockTime.countdown(f.towerNextCharge - now) + ".";
            add(cards, actions, new CardView.Card("Time Tower " + Msg.roman(Math.max(1, f.tower)), active
                    ? "On: +" + String.format("%.1f", f.towerBonus()) + "x for " + SkyBlockTime.countdown(f.towerUntil - now) + "."
                    : "+" + String.format("%.1f", f.towerBonus()) + "x production for an hour. " + f.towerCharges + "/3 charges. " + charge,
                    0xF3A6C0, !active && f.towerCharges > 0, "", new ItemStack(Items.CLOCK), "", List.of(), -1, active ? "On" : "Activate"),
                    p -> {
                        ChocolateFactory.edit(p, fac -> {
                            if (!fac.towerActive(System.currentTimeMillis()) && fac.towerCharges > 0) {
                                fac.towerCharges--;
                                fac.towerUntil = System.currentTimeMillis() + Factory.HOUR;
                                if (fac.towerNextCharge == 0) {
                                    fac.towerNextCharge = System.currentTimeMillis() + (fac.has("Einstein") ? 7 : 8) * Factory.HOUR;
                                }
                            }
                        });
                        Msg.playSound(p, SoundEvents.BELL_BLOCK, 1f, 1f);
                    });
            boolean towerMax = f.tower >= 15;
            add(cards, actions, upgrade("Upgrade the Time Tower", "+0.1x while it's on, each level.", towerMax, f.towerCost(), f, Items.CLOCK),
                    p -> buy(p, fac -> fac.tower < 15, Factory::towerCost, fac -> fac.tower++, "Time Tower"));
        }
        if (f.level >= 3) {
            add(cards, actions, upgrade("Rabbit Shrine " + Msg.roman(Math.max(1, f.shrine)), "+2% odds of Uncommon and better rabbits each level ("
                    + (2 * f.shrine) + "% now).", f.shrine >= 20, f.shrineCost(), f, Items.RABBIT_FOOT),
                    p -> buy(p, fac -> fac.shrine < 20, Factory::shrineCost, fac -> fac.shrine++, "Rabbit Shrine"));
        }
        if (f.level >= 4) {
            add(cards, actions, upgrade("Coach Jackrabbit " + Msg.roman(Math.max(1, f.coach)), "+0.01x production each level.", f.coach >= 20,
                    f.coachCost(), f, Items.GOLDEN_CARROT),
                    p -> buy(p, fac -> fac.coach < 20, Factory::coachCost, fac -> fac.coach++, "Coach Jackrabbit"));
        }
        if (f.level < 6) {
            double need = Factory.PRESTIGE_NEED[f.level];
            add(cards, actions, new CardView.Card("Prestige to Factory " + Factory.LEVEL_NAMES[f.level], "Make " + Msg.compact(need)
                    + " Chocolate this prestige (" + Msg.compact(f.thisPrestige) + "): x" + Factory.LEVEL_MULT[f.level] + " production. Everything"
                    + " but the Barn and your rabbits starts over.", 0xFFAA00, f.canPrestige(), "", new ItemStack(Items.NETHER_STAR), "", List.of(),
                    (float) Math.min(1, f.thisPrestige / need), "Prestige"),
                    p -> {
                        Factory after = ChocolateFactory.edit(p, fac -> {
                            if (fac.canPrestige()) {
                                fac.prestige();
                            }
                        });
                        p.sendSystemMessage(Component.literal("Your Chocolate Factory is now level " + Factory.LEVEL_NAMES[after.level - 1] + "!")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                        Msg.playSound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    });
        }
        add(cards, actions, new CardView.Card("Chocolate Shop", "Spend chocolate on chocolate things.", 0xEEEEEE, true, "",
                item("NIBBLE_CHOCOLATE_STICK", Items.COCOA_BEANS), "", List.of(), -1, "Open"), p -> show(p, Page.SHOP));
        int next = 0;
        while (next < Factory.MILESTONES.length && f.allTime >= Factory.MILESTONES[next]) {
            next++;
        }
        add(cards, actions, CardView.Card.info("Milestones", next < Factory.MILESTONES.length ? "Next: " + Factory.MILESTONE_RABBITS[next] + " at "
                + Msg.compact(Factory.MILESTONES[next]) + " all-time Chocolate." : "Every milestone reached!", 0xFFFF55, new ItemStack(Items.GOLD_INGOT)), null);
    }

    private static CardView.Card upgrade(String title, String detail, boolean max, double cost, Factory f, net.minecraft.world.item.Item icon) {
        return new CardView.Card(title, detail, 0xD7A36A, !max && f.chocolate >= cost, "", new ItemStack(icon), "",
                max ? List.of() : List.of(Chip.price("chocolate", Math.round(cost), Math.round(f.chocolate))), -1, max ? "Max" : "Upgrade");
    }

    /** Pays for an upgrade if it can be had and you've the chocolate. */
    private static void buy(ServerPlayer player, java.util.function.Predicate<Factory> can, java.util.function.ToDoubleFunction<Factory> cost,
                            Consumer<Factory> upgrade, String what) {
        boolean[] done = {false};
        ChocolateFactory.edit(player, f -> {
            double price = cost.applyAsDouble(f);
            if (can.test(f) && f.spend(price)) {
                upgrade.accept(f);
                done[0] = true;
            }
        });
        if (done[0]) {
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.6f);
        } else {
            player.sendOverlayMessage(Component.literal("You don't have enough Chocolate for " + what + "!").withStyle(ChatFormatting.RED));
        }
    }

    // ---------------------------------------------------------------- rabbits and shop

    private static void rabbits(Factory f, List<CardView.Card> cards, List<Consumer<ServerPlayer>> actions) {
        add(cards, actions, new CardView.Card("Back", "To the factory.", 0xEEEEEE, true, "", new ItemStack(Items.ARROW), "", List.of(), -1, "Back"),
                p -> show(p, Page.FACTORY));
        for (int rarity = 0; rarity < Rabbits.RARITIES.size(); rarity++) {
            int at = rarity;
            List<Rabbits.Rabbit> all = Rabbits.all().stream().filter(r -> r.rarity() == at).toList();
            List<String> mine = all.stream().map(Rabbits.Rabbit::name).filter(f::has).toList();
            String names = mine.isEmpty() ? "None yet." : String.join(", ", mine.subList(0, Math.min(12, mine.size())))
                    + (mine.size() > 12 ? " and " + (mine.size() - 12) + " more" : "");
            int color = switch (rarity) {
                case 0 -> 0xFFFFFF;
                case 1 -> 0x55FF55;
                case 2 -> 0x5555FF;
                case 3 -> 0xAA00AA;
                case 4 -> 0xFFAA00;
                case 5 -> 0xFF55FF;
                default -> 0x55FFFF;
            };
            add(cards, actions, new CardView.Card(Rabbits.rarityName(rarity) + ": " + mine.size() + "/" + all.size(), names, color, false, "",
                    new ItemStack(Items.RABBIT_SPAWN_EGG), "", List.of(), all.isEmpty() ? -1 : (float) mine.size() / all.size(),
                    Rabbits.FLAT[rarity] > 0 ? "+" + Rabbits.FLAT[rarity] + " +" + Rabbits.MULT[rarity] + "x" : Rabbits.MULT[rarity] > 0 ? "+" + Rabbits.MULT[rarity] + "x" : "unique"),
                    null);
        }
    }

    private static void shop(ServerPlayer player, Factory f, List<CardView.Card> cards, List<Consumer<ServerPlayer>> actions) {
        add(cards, actions, new CardView.Card("Back", "To the factory.", 0xEEEEEE, true, "", new ItemStack(Items.ARROW), "", List.of(), -1, "Back"),
                p -> show(p, Page.FACTORY));
        int year = SkyBlockTime.now().year();
        if (f.shopYear != year) {
            f.shopYear = year;
            f.shopBought.clear();
        }
        for (Offer offer : SHOP) {
            ItemStack icon = item(offer.item(), Items.COCOA_BEANS);
            int bought = f.shopBought.getOrDefault(offer.item(), 0);
            boolean open = f.level >= offer.level();
            boolean left = bought < offer.stock();
            List<Chip> price = new ArrayList<>();
            price.add(Chip.price("chocolate", Math.round(offer.price()), Math.round(f.chocolate)));
            if (!offer.alsoItem().isEmpty()) {
                price.add(Chip.item(item(offer.alsoItem(), Items.COCOA_BEANS), 1, com.skyrpg.items.SkyStacks.count(player.getInventory(), offer.alsoItem())));
            }
            add(cards, actions, new CardView.Card(icon.getHoverName().getString(), !open ? "Needs Chocolate Factory " + Factory.LEVEL_NAMES[offer.level() - 1] + "."
                    : left ? (offer.stock() - bought) + " left this year." : "Sold out this year.", 0xFFFFFF, open && left, "", icon, "", price, -1, ""),
                    p -> buyItem(p, offer));
        }
    }

    private static void buyItem(ServerPlayer player, Offer offer) {
        ItemDefinition def = ItemRegistry.skyblock(offer.item());
        if (def == null) {
            return;
        }
        boolean[] done = {false};
        ChocolateFactory.edit(player, f -> {
            int year = SkyBlockTime.now().year();
            if (f.shopYear != year) {
                f.shopYear = year;
                f.shopBought.clear();
            }
            if (f.level < offer.level() || f.shopBought.getOrDefault(offer.item(), 0) >= offer.stock() || f.chocolate < offer.price()) {
                return;
            }
            if (!offer.alsoItem().isEmpty() && !com.skyrpg.items.SkyStacks.take(player.getInventory(), offer.alsoItem(), 1)) {
                return;
            }
            f.spend(offer.price());
            f.spent += offer.price();
            f.shopBought.merge(offer.item(), 1, Integer::sum);
            done[0] = true;
        });
        if (!done[0]) {
            player.sendOverlayMessage(Component.literal("You can't buy that right now!").withStyle(ChatFormatting.RED));
            return;
        }
        ItemStack stack = SkyItems.create(def, 1);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        player.sendSystemMessage(Component.literal("You bought ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(def.name()).withColor(def.rarity().color())).append(Component.literal("!").withStyle(ChatFormatting.GREEN)));
        Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.7f, 1.6f);
    }

    private static void add(List<CardView.Card> cards, List<Consumer<ServerPlayer>> actions, CardView.Card card, Consumer<ServerPlayer> action) {
        cards.add(card);
        actions.add(action);
    }

    private static ItemStack item(String id, net.minecraft.world.item.Item fallback) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? new ItemStack(fallback) : SkyItems.create(def, 1);
    }

    static boolean hunt() {
        return CalendarEvents.running(System.currentTimeMillis(), "Hoppity's Hunt");
    }

    // ---------------------------------------------------------------- strays

    private static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (tick % 20 != 0) {
            return;
        }
        OPEN.keySet().removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player == null || !(player.containerMenu instanceof CardMenu menu) || !menu.view().theme().equals("CHOCOLATE");
        });
        List<UUID> gone = new ArrayList<>();
        STRAYS.entrySet().removeIf(e -> {
            boolean ran = tick >= e.getValue().until();
            if (ran && OPEN.get(e.getKey()) == Page.FACTORY) {
                gone.add(e.getKey());
            }
            return ran || !OPEN.containsKey(e.getKey());
        });
        for (UUID id : gone) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                show(player, Page.FACTORY);
            }
        }
        for (UUID id : List.copyOf(OPEN.keySet())) {
            if (OPEN.get(id) != Page.FACTORY) {
                continue;
            }
            long next = NEXT_STRAY.computeIfAbsent(id, k -> tick + 600);
            if (tick < next) {
                continue;
            }
            NEXT_STRAY.put(id, tick + 600);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.getRandom().nextDouble() >= 0.2) {
                continue;
            }
            stray(player);
        }
    }

    /** A stray rabbit runs through the player's factory (its rarity as an egg's, by the Factory level) for 15 seconds. */
    static void stray(ServerPlayer player) {
        long tick = player.level().getServer().getTickCount();
        Factory f = ChocolateFactory.get(player);
        double[] weights = Rabbits.WEIGHTS;
        double total = 0;
        for (int i = 0; i < weights.length; i++) {
            if (f.level >= Rabbits.LEVEL_NEEDED[i]) {
                total += weights[i];
            }
        }
        double pick = player.getRandom().nextDouble() * total;
        int rarity = 0;
        for (int i = 0; i < weights.length; i++) {
            if (f.level >= Rabbits.LEVEL_NEEDED[i]) {
                pick -= weights[i];
                if (pick < 0) {
                    rarity = i;
                    break;
                }
            }
        }
        STRAYS.put(player.getUUID(), new Stray(rarity, tick + 300));
        Msg.playSound(player, SoundEvents.RABBIT_AMBIENT, 1f, 1f);
        show(player, Page.FACTORY);
    }
}
