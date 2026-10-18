package com.skyrpg.skyblock.festival;

import com.skyrpg.core.action.Actions;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.hub.Hub;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.pet.SkyBlockPets;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.time.CalendarEvents;
import com.skyrpg.skyblock.time.SkyBlockTime;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Traveling Zoo (Early Summer and Early Winter 1st to 3rd, a real hour each): Oringo brings three of six exotic
 * pets to the Hub plaza, at level 1, for coins and the animal's own goods. One of the three is Legendary, in turn; the
 * other two are random pets at Common to Epic. Decided where Hypixel's cycle table wasn't readable: the Legendary goes
 * round the six in order, one visit after another, and the other two are seeded by the visit (everyone sees the same).
 */
final class TravelingZoo {
    static final String NAME = "Traveling Zoo";
    private static final List<String> PETS = List.of("BLUE_WHALE", "LION", "TIGER", "GIRAFFE", "ELEPHANT", "MONKEY");
    private static final String[] TIER_NAMES = {"Common", "Uncommon", "Rare", "Epic", "Legendary"};

    /** A pet's price at each rarity (Common to Legendary): coins, and an item with how many. */
    private record Price(long coins, String item, int count) {
    }

    private static final List<List<Price>> PRICES = List.of(
            List.of(new Price(10_000, "RAW_FISH", 64), new Price(25_000, "ENCHANTED_RAW_FISH", 1), new Price(100_000, "ENCHANTED_RAW_FISH", 16),
                    new Price(1_000_000, "ENCHANTED_COOKED_FISH", 1), new Price(10_000_000, "ENCHANTED_COOKED_FISH", 8)),
            List.of(new Price(10_000, "RAW_BEEF", 64), new Price(25_000, "ENCHANTED_RAW_BEEF", 2), new Price(100_000, "ENCHANTED_RAW_BEEF", 32),
                    new Price(1_000_000, "ENCHANTED_RAW_BEEF", 256), new Price(15_000_000, "ENCHANTED_RAW_BEEF", 1024)),
            List.of(new Price(10_000, "RAW_CHICKEN", 128), new Price(25_000, "ENCHANTED_RAW_CHICKEN", 2), new Price(100_000, "ENCHANTED_RAW_CHICKEN", 32),
                    new Price(1_000_000, "ENCHANTED_RAW_CHICKEN", 256), new Price(15_000_000, "ENCHANTED_RAW_CHICKEN", 1024)),
            List.of(new Price(10_000, "LOG_2", 64), new Price(25_000, "ENCHANTED_ACACIA_LOG", 1), new Price(100_000, "ENCHANTED_ACACIA_LOG", 16),
                    new Price(1_000_000, "ENCHANTED_ACACIA_LOG", 128), new Price(10_000_000, "ENCHANTED_ACACIA_LOG", 512)),
            List.of(new Price(10_000, "LOG_2:1", 64), new Price(25_000, "ENCHANTED_DARK_OAK_LOG", 1), new Price(100_000, "ENCHANTED_DARK_OAK_LOG", 16),
                    new Price(1_000_000, "ENCHANTED_DARK_OAK_LOG", 128), new Price(15_000_000, "ENCHANTED_DARK_OAK_LOG", 512)),
            List.of(new Price(10_000, "LOG:3", 64), new Price(25_000, "ENCHANTED_JUNGLE_LOG", 1), new Price(100_000, "ENCHANTED_JUNGLE_LOG", 16),
                    new Price(1_000_000, "ENCHANTED_JUNGLE_LOG", 128), new Price(18_000_000, "ENCHANTED_JUNGLE_LOG", 512)));

    /** One pet on offer: which (index into PETS) at which rarity (0 Common ... 4 Legendary). */
    record Offer(int pet, int tier) {
    }

    private static long announced;

    private TravelingZoo() {
    }

    static void init() {
        Actions.register("oringo", (player, params) -> open(player));
        ServerTickEvents.END_SERVER_TICK.register(TravelingZoo::tick);
    }

    /** This visit's three pets: the Legendary in turn (two visits a year), two others seeded by the visit. */
    static List<Offer> offers(long visitStart) {
        SkyBlockTime.Date date = SkyBlockTime.at(visitStart);
        int visit = date.year() * 2 + (date.month() >= 6 ? 1 : 0);
        int legendary = Math.floorMod(visit, PETS.size());
        Random random = new Random(visit * 31L + 7);
        List<Integer> others = new ArrayList<>();
        for (int i = 0; i < PETS.size(); i++) {
            if (i != legendary) {
                others.add(i);
            }
        }
        java.util.Collections.shuffle(others, random);
        List<Offer> offers = new ArrayList<>();
        offers.add(new Offer(others.get(0), random.nextInt(4)));
        offers.add(new Offer(legendary, 4));
        offers.add(new Offer(others.get(1), random.nextInt(4)));
        return offers;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        CalendarEvents.Occurrence visit = CalendarEvents.occurrence(System.currentTimeMillis(), NAME);
        if (visit == null || visit.start() == announced) {
            return;
        }
        announced = visit.start();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!SkyBlockWorlds.isSkyBlock(player.level())) {
                continue;
            }
            if (Hub.isHub(player.level())) {
                player.sendSystemMessage(Component.literal("Oringo ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal("has brought his ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("Traveling Zoo ").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("shop in town!").withStyle(ChatFormatting.YELLOW)));
                player.sendSystemMessage(Component.literal("Acquire ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("exclusive pets").withStyle(ChatFormatting.GOLD))
                        .append(Component.literal("! Visit ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("Oringo ").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("at the hub plaza.").withStyle(ChatFormatting.YELLOW)));
            } else {
                player.sendSystemMessage(Component.literal("The ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("Traveling Zoo ").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("is visiting the ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("SkyBlock Hub").withStyle(ChatFormatting.AQUA))
                        .append(Component.literal("!").withStyle(ChatFormatting.YELLOW)));
                player.sendSystemMessage(Component.literal("CLICK HERE ").withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(true)
                                .withClickEvent(new ClickEvent.RunCommand("/warp hub")))
                        .append(Component.literal("to shop for ").withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(false)))
                        .append(Component.literal("exotic pets").withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(false)))
                        .append(Component.literal("!").withStyle(style -> style.withColor(ChatFormatting.YELLOW).withBold(false))));
            }
            Msg.playSound(player, SoundEvents.PANDA_AMBIENT, 1f, 1f);
        }
    }

    static void open(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Oringo");
        Entity near = npc < 0 ? null : player.level().getEntity(npc);
        int shown = near != null && near.getName().getString().contains("Oringo") ? npc : -1;
        CardMenu.open(player, p -> view(p, shown), TravelingZoo::buy);
    }

    private static CardView view(ServerPlayer player, int npc) {
        CalendarEvents.Occurrence visit = CalendarEvents.occurrence(System.currentTimeMillis(), NAME);
        List<CardView.Card> cards = new ArrayList<>();
        if (visit == null) {
            cards.add(CardView.Card.info("The zoo is packing up", "Check back for the next Traveling Zoo: Early Summer and Early Winter.",
                    0xAAAAAA, SkyBlockPets.create("MONKEY", "COMMON", 0)));
        } else {
            long coins = Wallet.balance(player);
            for (Offer offer : offers(visit.start())) {
                String type = PETS.get(offer.pet());
                Price price = PRICES.get(offer.pet()).get(offer.tier());
                ItemStack pet = SkyBlockPets.create(type, SkyBlockPets.TIERS[offer.tier()], 0);
                List<Chip> chips = List.of(Chip.price("coins", price.coins(), coins),
                        Chip.item(icon(price.item()), price.count(), Sacks.countAnywhere(player, price.item())));
                boolean afford = coins >= price.coins() && Sacks.countAnywhere(player, price.item()) >= price.count();
                cards.add(new CardView.Card(pet.isEmpty() ? type : pet.getHoverName().getString(), TIER_NAMES[offer.tier()] + " · level 1",
                        0xFFFFFF, afford, "Buy", pet, "", chips, -1, offer.tier() == 4 ? "This visit's Legendary" : ""));
            }
        }
        String left = visit == null ? "" : "Leaving in " + SkyBlockTime.countdown(visit.end() - System.currentTimeMillis());
        return new CardView("SKYBLOCK", "Oringo", "Traveling Zoo", npc, List.of(Chip.balance("coins", Wallet.balance(player))), cards,
                "I've brought exotic pets from all over! " + left, 1, 44);
    }

    private static String buy(ServerPlayer player, int card) {
        CalendarEvents.Occurrence visit = CalendarEvents.occurrence(System.currentTimeMillis(), NAME);
        if (visit == null) {
            return "The Traveling Zoo has left!";
        }
        List<Offer> offers = offers(visit.start());
        if (card < 0 || card >= offers.size()) {
            return "";
        }
        Offer offer = offers.get(card);
        Price price = PRICES.get(offer.pet()).get(offer.tier());
        if (Wallet.balance(player) < price.coins()) {
            return "You don't have enough coins!";
        }
        if (Sacks.countAnywhere(player, price.item()) < price.count()) {
            return "You don't have the items that takes!";
        }
        if (!Wallet.take(player, price.coins()) || !Sacks.takeAnywhere(player, price.item(), price.count())) {
            return "You can't afford that!";
        }
        ItemStack pet = SkyBlockPets.create(PETS.get(offer.pet()), SkyBlockPets.TIERS[offer.tier()], 0);
        Component name = pet.getHoverName().copy();
        if (!player.getInventory().add(pet)) {
            player.drop(pet, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        player.sendSystemMessage(Component.literal("You bought a ").withStyle(ChatFormatting.GREEN).append(name)
                .append(Component.literal(" from Oringo!").withStyle(ChatFormatting.GREEN)));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1f, 1.2f);
        return "";
    }

    private static ItemStack icon(String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? ItemStack.EMPTY : SkyItems.create(def, 1);
    }
}
