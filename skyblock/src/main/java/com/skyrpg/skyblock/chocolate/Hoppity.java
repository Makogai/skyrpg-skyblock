package com.skyrpg.skyblock.chocolate;

import com.skyrpg.core.action.Actions;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.time.SkyBlockTime;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hoppity, in the Hub: each year's Hunt he has seven rabbits for you (yours alone), each sold once, for coins. The
 * first two are always Common, the later ones likelier to be rarer (Hypixel's slot odds). A rabbit you already have
 * comes as its duplicate's chocolate.
 */
final class Hoppity {
    /** Each slot's odds, Common to Legendary (Hypixel's). */
    private static final double[][] SLOTS = {{100}, {100}, {90, 10}, {75, 25}, {50, 40, 10}, {30, 50, 16, 4}, {10, 45, 35, 7, 3}};
    /** Price by rarity, Common to Legendary. */
    private static final long[] PRICES = {250_000, 500_000, 1_000_000, 2_500_000, 5_000_000};

    private Hoppity() {
    }

    static void init() {
        Actions.register("hoppity", (player, params) -> open(player));
    }

    /** This year's offers for the player, rolled the first time they look. */
    private static void stock(ServerPlayer player, Factory f) {
        int year = SkyBlockTime.now().year();
        if (f.hoppityYear == year && f.hoppityOffers.size() == SLOTS.length) {
            return;
        }
        f.hoppityYear = year;
        f.hoppityOffers = new ArrayList<>();
        f.hoppityBought = new ArrayList<>();
        for (double[] odds : SLOTS) {
            double total = 0;
            for (double o : odds) {
                total += o;
            }
            double pick = player.getRandom().nextDouble() * total;
            int rarity = 0;
            for (int i = 0; i < odds.length; i++) {
                pick -= odds[i];
                if (pick < 0) {
                    rarity = i;
                    break;
                }
            }
            Rabbits.Rabbit rabbit = Rabbits.any(rarity, player.getRandom());
            f.hoppityOffers.add(rabbit == null ? "" : rabbit.name());
            f.hoppityBought.add(false);
        }
    }

    static void open(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Hoppity");
        CardMenu.open(player, p -> view(p, npc), Hoppity::buy);
    }

    private static CardView view(ServerPlayer player, int npc) {
        Factory f = ChocolateFactory.get(player);
        stock(player, f);
        ChocolateFactory.save(player, f);
        boolean hunt = FactoryMenu.hunt();
        List<CardView.Card> cards = new ArrayList<>();
        long coins = Wallet.balance(player);
        for (int i = 0; i < f.hoppityOffers.size(); i++) {
            Rabbits.Rabbit rabbit = Rabbits.get(f.hoppityOffers.get(i));
            if (rabbit == null) {
                cards.add(CardView.Card.info("Sold out", "", 0x888888, new ItemStack(Items.RABBIT_HIDE)));
                continue;
            }
            boolean bought = f.hoppityBought.get(i);
            long price = PRICES[Math.min(4, rabbit.rarity())];
            boolean owned = f.has(rabbit.name());
            String detail = bought ? "Bought this year." : !hunt ? "Come back during Hoppity's Hunt (all spring)."
                    : rabbit.text() + (owned ? " (You have it: a duplicate's chocolate.)" : "");
            cards.add(new CardView.Card(rabbit.name() + " (" + Rabbits.rarityName(rabbit.rarity()) + ")", detail, colorOf(rabbit.rarity()),
                    hunt && !bought && coins >= price, bought ? "Bought" : "Buy", new ItemStack(Items.RABBIT_SPAWN_EGG), "",
                    bought ? List.of() : List.of(Chip.price("coins", price, coins)), -1, owned ? "owned" : ""));
        }
        return new CardView("CHOCOLATE", "Hoppity", "Hoppity's Hunt", npc, List.of(Chip.balance("coins", coins)), cards,
                "Find my eggs all over SkyBlock! Your rabbits work in your Chocolate Factory (/cf).", 1, 40);
    }

    private static String buy(ServerPlayer player, int card) {
        if (!FactoryMenu.hunt()) {
            return "Hoppity only sells rabbits during Hoppity's Hunt.";
        }
        Factory f = ChocolateFactory.get(player);
        stock(player, f);
        if (card < 0 || card >= f.hoppityOffers.size() || f.hoppityBought.get(card)) {
            return "";
        }
        Rabbits.Rabbit rabbit = Rabbits.get(f.hoppityOffers.get(card));
        if (rabbit == null) {
            return "";
        }
        long price = PRICES[Math.min(4, rabbit.rarity())];
        if (!Wallet.take(player, price)) {
            return "You don't have enough coins!";
        }
        f.hoppityBought.set(card, true);
        player.sendSystemMessage(Component.literal("You bought ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(rabbit.name()).withStyle(rabbit.color()))
                .append(Component.literal(" from Hoppity for " + Msg.number(price) + " coins!").withStyle(ChatFormatting.GREEN)));
        ChocolateFactory.found(player, f, rabbit);
        ChocolateFactory.milestones(player, f);
        ChocolateFactory.save(player, f);
        return "";
    }

    private static int colorOf(int rarity) {
        return switch (rarity) {
            case 0 -> 0xFFFFFF;
            case 1 -> 0x55FF55;
            case 2 -> 0x5555FF;
            case 3 -> 0xAA00AA;
            default -> 0xFFAA00;
        };
    }
}
