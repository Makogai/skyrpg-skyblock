package com.skyrpg.skyblock.rift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.time.SkyBlockTime;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Ubik Von Neumann's games on the Mountaintop (Hypixel's), each worth an Enigma Soul: Ubik's Boxes (three boxes, he
 * opens an empty one; the right box is an IQ Point, switching for the right reason a soul), Ubik's Averages (pick 1-100
 * each SkyBlock year; the closest tenth to half the average of everyone's picks win Free Will and the soul, a pick of four
 * times the average two Free Wills, five losses in a row the soul anyway) and Tit for Tat (5-9 rounds of split or steal:
 * both split 3,000 Motes each, both steal 1,000, a steal against a split 5,000 for the stealer; split first and then copy
 * Ubik's last move every round for his Cube and the soul).
 */
final class Ubik {
    static final String BOXES = "npc:Ubik's Boxes";
    static final String AVERAGES = "npc:Ubik's Averages";
    static final String TATS = "npc:Ubik's Tit for Tat";
    private static final long SPLIT_PAY = 3_000;
    private static final long BOTH_STEAL_PAY = 1_000;
    private static final long STEAL_PAY = 5_000;
    /** Decided: Hypixel's wait between games wasn't found. */
    private static final long TAT_COOLDOWN = 60 * 60 * 1000L;

    private static final class Boxes {
        int prize;
        int pick = -1;
        int opened = -1;
        int stage;
        boolean switched;
        boolean won;
    }

    private static final class Tats {
        int rounds;
        int round;
        boolean lastMine;
        boolean lastUbik;
        boolean copied = true;
        long paid;
        final List<String> log = new ArrayList<>();
    }

    private static final Map<UUID, Boxes> BOX_GAMES = new HashMap<>();
    private static final Map<UUID, Tats> TAT_GAMES = new HashMap<>();
    private static final Map<UUID, Integer> PICKING = new HashMap<>();
    private static final Map<UUID, Long> LAST_TATS = new HashMap<>();

    private Ubik() {
    }

    // ---------------------------------------------------------------- the monthly Averages pool, saved with the world

    static final class Pool extends SavedData {
        static final Codec<Pool> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("month", -1).forGetter(p -> p.month),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("picks", Map.of()).forGetter(p -> p.picks),
                Codec.STRING.listOf().optionalFieldOf("winners", List.of()).forGetter(p -> p.winners),
                Codec.STRING.listOf().optionalFieldOf("claimed", List.of()).forGetter(p -> p.claimed),
                Codec.DOUBLE.optionalFieldOf("target", 0.0).forGetter(p -> p.target),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("streaks", Map.of()).forGetter(p -> p.streaks),
                Codec.STRING.listOf().optionalFieldOf("double", List.of()).forGetter(p -> p.doubled)).apply(i, Pool::new));
        static final SavedDataType<Pool> TYPE = new SavedDataType<>(SkyRpgSkyblock.id("ubik_averages"), Pool::new, CODEC, null);

        int month;
        final Map<String, Integer> picks;
        List<String> winners;
        final List<String> claimed;
        double target;
        /** Losses in a row (five earn the soul anyway), and who picked four times the average (two Free Wills). */
        final Map<String, Integer> streaks;
        List<String> doubled;

        private Pool() {
            this(-1, Map.of(), List.of(), List.of(), 0, Map.of(), List.of());
        }

        private Pool(int month, Map<String, Integer> picks, List<String> winners, List<String> claimed, double target, Map<String, Integer> streaks,
                     List<String> doubled) {
            this.month = month;
            this.picks = new HashMap<>(picks);
            this.winners = new ArrayList<>(winners);
            this.claimed = new ArrayList<>(claimed);
            this.target = target;
            this.streaks = new HashMap<>(streaks);
            this.doubled = new ArrayList<>(doubled);
        }

        static Pool get(MinecraftServer server) {
            Pool pool = server.overworld().getDataStorage().computeIfAbsent(TYPE);
            pool.roll();
            return pool;
        }

        /** A new SkyBlock year: last year's picks are judged (the closest tenth of the players win, at least one). */
        private void roll() {
            int current = SkyBlockTime.now().year();
            if (current == month) {
                return;
            }
            if (!picks.isEmpty()) {
                double average = picks.values().stream().mapToInt(Integer::intValue).average().orElse(0);
                target = average / 2;
                List<Map.Entry<String, Integer>> ranked = picks.entrySet().stream()
                        .sorted((a, b) -> Double.compare(Math.abs(a.getValue() - target), Math.abs(b.getValue() - target))).toList();
                int count = Math.max(1, (int) Math.ceil(ranked.size() / 10.0));
                double cut = Math.abs(ranked.get(count - 1).getValue() - target);
                winners = new ArrayList<>(ranked.stream().filter(e -> Math.abs(e.getValue() - target) <= cut).map(Map.Entry::getKey).toList());
                doubled = new ArrayList<>(picks.entrySet().stream().filter(e -> e.getValue() >= 4 * average).map(Map.Entry::getKey).toList());
                for (String player : picks.keySet()) {
                    streaks.put(player, winners.contains(player) ? 0 : streaks.getOrDefault(player, 0) + 1);
                }
                claimed.clear();
            }
            picks.clear();
            month = current;
            setDirty();
        }
    }

    // ---------------------------------------------------------------- Ubik himself

    static void open(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            Pool pool = Pool.get(p.level().getServer());
            String me = p.getUUID().toString();
            Integer pick = pool.picks.get(me);
            boolean won = !pool.claimed.contains(me) && (pool.winners.contains(me) || pool.streaks.getOrDefault(me, 0) >= 5);
            long wait = LAST_TATS.getOrDefault(p.getUUID(), 0L) + TAT_COOLDOWN - System.currentTimeMillis();
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("Ubik's Boxes", "Three boxes, one prize. I'll open an empty one for you.", 0xFFFF55, true, "Play",
                    new ItemStack(Items.CHEST), "", List.of(), -1, EnigmaSouls.has(p, BOXES) ? "Soul found" : ""));
            cards.add(new CardView.Card("Ubik's Averages", pick == null ? "Pick 1 to 100. Closest to half the average of everyone's picks wins."
                    : "You picked " + pick + " this year. Change it any time before the year ends.", 0x55FFFF, true, pick == null ? "Pick" : "Change",
                    new ItemStack(Items.COMPARATOR), "", List.of(), -1, pool.winners.isEmpty() ? "" : "Last target " + String.format("%.1f", pool.target)));
            if (won) {
                cards.add(new CardView.Card(pool.winners.contains(me) ? "You won last year!" : "Five years of trying",
                        pool.winners.contains(me) ? "Closest to half the average: Free Will is yours." : "Ubik respects persistence.", 0x55FF55, true, "Claim",
                        RiftNpcs.icon("FREE_WILL"), "", List.of(), -1, ""));
            }
            cards.add(new CardView.Card("Tit for Tat", wait > 0 ? "Again in " + SkyBlockTime.countdown(wait) : "Split or steal, a few rounds against me.",
                    0xFF55FF, wait <= 0, "Play", RiftNpcs.icon("UBIKS_CUBE"), "", List.of(), -1, EnigmaSouls.has(p, TATS) ? "Soul found" : ""));
            return new CardView("RIFT", "Ubik Von Neumann", "Games", npc, List.of(Chip.balance("motes", Motes.get(p))), cards,
                    "Each game hides an Enigma Soul. Think it through.", 1, 36);
        }, (p, card) -> {
            Pool pool = Pool.get(p.level().getServer());
            String me = p.getUUID().toString();
            boolean won = !pool.claimed.contains(me) && (pool.winners.contains(me) || pool.streaks.getOrDefault(me, 0) >= 5);
            int index = card;
            if (index == 0) {
                BOX_GAMES.remove(p.getUUID());
                boxes(p, npc);
                return null;
            }
            if (index == 1) {
                PICKING.put(p.getUUID(), pool.picks.getOrDefault(p.getUUID().toString(), 50));
                averages(p, npc);
                return null;
            }
            if (won && index == 2) {
                pool.claimed.add(me);
                boolean winner = pool.winners.contains(me);
                if (winner) {
                    give(p, "FREE_WILL");
                    if (pool.doubled.contains(me)) {
                        give(p, "FREE_WILL");
                    }
                } else {
                    pool.streaks.put(me, 0);
                }
                pool.setDirty();
                if (!EnigmaSouls.has(p, AVERAGES)) {
                    EnigmaSouls.find(p, AVERAGES);
                }
                say(p, winner ? "Half the average. You understood the game: everyone else was trying to understand you."
                        : "Five years and never close. Have this anyway.");
                return "";
            }
            if (LAST_TATS.getOrDefault(p.getUUID(), 0L) + TAT_COOLDOWN > System.currentTimeMillis()) {
                return "Not again so soon.";
            }
            Tats tats = new Tats();
            tats.rounds = 5 + p.getRandom().nextInt(5);
            tats.lastMine = p.getRandom().nextBoolean();
            TAT_GAMES.put(p.getUUID(), tats);
            LAST_TATS.put(p.getUUID(), System.currentTimeMillis());
            tats(p, npc);
            return null;
        });
    }

    // ---------------------------------------------------------------- Ubik's Boxes

    private static void boxes(ServerPlayer player, int npc) {
        Boxes game = BOX_GAMES.computeIfAbsent(player.getUUID(), id -> {
            Boxes b = new Boxes();
            b.prize = player.getRandom().nextInt(3);
            return b;
        });
        CardMenu.open(player, p -> {
            List<CardView.Card> cards = new ArrayList<>();
            String subtitle;
            switch (game.stage) {
                case 0 -> {
                    subtitle = "Pick a box";
                    for (int i = 0; i < 3; i++) {
                        cards.add(new CardView.Card("Box " + (i + 1), "One of these holds the prize.", 0xFFFF55, true, "", new ItemStack(Items.CHEST), "",
                                List.of(), -1, ""));
                    }
                }
                case 1 -> {
                    subtitle = "Stay or switch?";
                    int other = 3 - game.pick - game.opened;
                    cards.add(new CardView.Card("Box " + (game.opened + 1) + " is empty", "Ubik opened it for you.", 0x777777, false, "",
                            new ItemStack(Items.BARRIER), "", List.of(), -1, ""));
                    cards.add(new CardView.Card("Stay with box " + (game.pick + 1), "Your first pick.", 0xFFFF55, true, "", new ItemStack(Items.CHEST), "",
                            List.of(), -1, ""));
                    cards.add(new CardView.Card("Switch to box " + (other + 1), "The one he didn't open.", 0x55FFFF, true, "", new ItemStack(Items.ENDER_CHEST),
                            "", List.of(), -1, ""));
                }
                case 2 -> {
                    subtitle = game.won ? "The prize was yours!" : "Empty...";
                    cards.add(new CardView.Card("Why did you switch?", "Ubik wants to know.", 0xFF55FF, false, "", new ItemStack(Items.BOOK), "",
                            List.of(), -1, ""));
                    cards.add(new CardView.Card("It felt lucky", "", 0xEEEEEE, true, "", new ItemStack(Items.RABBIT_FOOT), "", List.of(), -1, ""));
                    cards.add(new CardView.Card("It increases my odds", "", 0xEEEEEE, true, "", new ItemStack(Items.COMPARATOR), "", List.of(), -1, ""));
                    cards.add(new CardView.Card("You were bluffing", "", 0xEEEEEE, true, "", new ItemStack(Items.SPYGLASS), "", List.of(), -1, ""));
                }
                default -> {
                    subtitle = game.won ? "The prize was yours!" : "Empty...";
                    cards.add(new CardView.Card("Play again", "Three new boxes.", 0xFFFF55, true, "", new ItemStack(Items.CHEST), "", List.of(), -1, ""));
                }
            }
            return new CardView("RIFT", "Ubik's Boxes", subtitle, npc, List.of(), cards, "Ubik always opens an empty box you didn't pick.", 1, 30);
        }, (p, card) -> {
            switch (game.stage) {
                case 0 -> {
                    game.pick = card;
                    List<Integer> empty = new ArrayList<>();
                    for (int i = 0; i < 3; i++) {
                        if (i != game.pick && i != game.prize) {
                            empty.add(i);
                        }
                    }
                    game.opened = empty.get(p.getRandom().nextInt(empty.size()));
                    game.stage = 1;
                    say(p, "Box " + (game.opened + 1) + " is empty. Would you like to switch?");
                }
                case 1 -> {
                    if (card == 0) {
                        return "";
                    }
                    game.switched = card == 2;
                    int last = game.switched ? 3 - game.pick - game.opened : game.pick;
                    game.won = last == game.prize;
                    if (game.won && RiftFlags.set(p, "ubik:iq")) {
                        give(p, "IQ_POINT");
                    }
                    say(p, (game.won ? "The prize! " : "Empty, I'm afraid. ") + (game.switched ? "Tell me: why did you switch?" : "You stayed. Most people do."));
                    Msg.playSound(p, game.won ? SoundEvents.PLAYER_LEVELUP : SoundEvents.VILLAGER_NO, 0.8f, 1.2f);
                    game.stage = game.switched ? 2 : 3;
                }
                case 2 -> {
                    if (card == 0) {
                        return "";
                    }
                    if (card == 2) {
                        say(p, "Exactly. Two in three, if you switch. Most people never see it.");
                        if (!EnigmaSouls.has(p, BOXES)) {
                            EnigmaSouls.find(p, BOXES);
                        }
                    } else {
                        say(p, "Hm. The right move for the wrong reason.");
                    }
                    game.stage = 3;
                }
                default -> {
                    BOX_GAMES.remove(p.getUUID());
                    boxes(p, npc);
                    return null;
                }
            }
            return "";
        });
    }

    // ---------------------------------------------------------------- Ubik's Averages

    private static void averages(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            int pick = PICKING.getOrDefault(p.getUUID(), 50);
            List<CardView.Card> cards = new ArrayList<>();
            for (int step : new int[] {-10, -1, 1, 10}) {
                cards.add(new CardView.Card((step > 0 ? "+" : "") + step, "", 0xEEEEEE, true, "", new ItemStack(step > 0 ? Items.DYE.lime() : Items.DYE.red()),
                        "", List.of(), -1, ""));
            }
            cards.add(new CardView.Card("Pick " + pick, "Closest to half the average wins.", 0x55FF55, true, "Pick", new ItemStack(Items.COMPARATOR), "",
                    List.of(), pick / 100f, pick + "/100"));
            return new CardView("RIFT", "Ubik's Averages", "Year " + SkyBlockTime.now().year(), npc, List.of(), cards,
                    "Everyone's picks are averaged when the SkyBlock year ends; the closest tenth win.", 4, 30);
        }, (p, card) -> {
            int pick = PICKING.getOrDefault(p.getUUID(), 50);
            if (card < 4) {
                PICKING.put(p.getUUID(), Math.max(1, Math.min(100, pick + new int[] {-10, -1, 1, 10}[card])));
                return "";
            }
            Pool pool = Pool.get(p.level().getServer());
            pool.picks.put(p.getUUID().toString(), pick);
            pool.setDirty();
            say(p, pick + ". Interesting. We'll see what everyone else thinks you think.");
            return null;
        });
    }

    // ---------------------------------------------------------------- Tit for Tat

    private static void tats(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            Tats game = TAT_GAMES.get(p.getUUID());
            List<CardView.Card> cards = new ArrayList<>();
            if (game == null) {
                return new CardView("RIFT", "Tit for Tat", "Over", npc, List.of(), cards, "", 1, 30);
            }
            boolean over = game.round >= game.rounds;
            if (!over) {
                cards.add(new CardView.Card("Split", "Both split: 3,000 Motes each. Split against a steal: nothing.", 0x55FF55, true, "",
                        new ItemStack(Items.EMERALD), "", List.of(), -1, ""));
                cards.add(new CardView.Card("Steal", "Steal against a split: 5,000. Both steal: 1,000 each.", 0xFF5555, true, "",
                        new ItemStack(Items.IRON_SWORD), "", List.of(), -1, ""));
            }
            for (int i = Math.max(0, game.log.size() - 4); i < game.log.size(); i++) {
                cards.add(new CardView.Card("Round " + (i + 1), game.log.get(i), 0xAAAAAA, false, "", new ItemStack(Items.PAPER), "", List.of(), -1, ""));
            }
            return new CardView("RIFT", "Tit for Tat", over ? "Game over" : "Round " + (game.round + 1), npc,
                    List.of(new Chip("motes", ItemStack.EMPTY, "+" + Msg.number(game.paid), true)), cards, "Ubik won't say how many rounds there are.", 1, 30);
        }, (p, card) -> {
            Tats game = TAT_GAMES.get(p.getUUID());
            if (game == null || game.round >= game.rounds) {
                return null;
            }
            boolean split = card == 0;
            // Ubik copies your last move (a coin flip in the first round); copying his, after splitting first, is the way
            boolean ubikSplits = game.lastMine;
            game.copied &= game.round == 0 ? split : split == game.lastUbik;
            game.lastUbik = ubikSplits;
            long pay = split && ubikSplits ? SPLIT_PAY : !split && ubikSplits ? STEAL_PAY : !split ? BOTH_STEAL_PAY : 0;
            game.paid += pay;
            Motes.give(p, pay);
            game.log.add("You " + (split ? "split" : "stole") + ", Ubik " + (ubikSplits ? "split" : "stole") + (pay > 0 ? ": +" + pay + " Motes" : "."));
            game.lastMine = split;
            game.round++;
            if (game.round >= game.rounds) {
                TAT_GAMES.remove(p.getUUID());
                if (game.copied && !EnigmaSouls.has(p, TATS)) {
                    give(p, "UBIKS_CUBE");
                    say(p, "You played it my way, every round. Take my Cube; you've earned it.");
                    EnigmaSouls.find(p, TATS);
                } else {
                    say(p, game.copied ? "Every round my way. Again." : "That's the game. " + Msg.number(game.paid) + " Motes for you.");
                }
                return null;
            }
            return "";
        });
    }

    private static void say(ServerPlayer player, String line) {
        player.sendSystemMessage(Component.literal("[NPC] Ubik Von Neumann: ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    private static void give(ServerPlayer player, String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        ItemStack stack = SkyItems.create(def, 1);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }
}
