package com.skyrpg.skyblock.mining;

import com.mojang.serialization.Codec;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.time.SkyBlockTime;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Dwarven Mines' daily characters. <b>Fetchur</b> (the village) wants one item a day and pays 20,000 coins and 2,500
 * Mithril Powder; everybody gets the same request, it changes at midnight UTC, and the 12 requests cycle by the day of the
 * month (Hypixel's list, starting with Yellow Stained Glass on the 1st). The <b>Puzzler</b> (the palace) shows a row of
 * arrows, one block each (up is straight ahead, right, down is back, left), and you pick where it ends: 2,500 Mithril
 * Powder, once every 24 hours after you solve it. The <b>King</b> in the Royal Palace is one of seven who take turns, a
 * new one each SkyBlock day (every 20 minutes); he opens the Commissions board, and meeting all seven gives the King
 * Talisman.
 *
 * <p>Decided: Fetchur takes the items from your inventory in a card screen (Hypixel: right-click him with them), and
 * accepts at least the amount. The Puzzler has no glowstone floor to walk: the arrows are the puzzle and the answer is a
 * card (same puzzle until you solve it). The 7 Kings are one NPC whose name is told in chat, in Hypixel's order; Kings
 * have no buff (Hypixel's don't either). The Powder Buff perk and 2x Powder count for both powder rewards; Bits don't exist.
 */
final class DailyNpcs {
    /** The day (UTC) of Fetchur's last request done. */
    private static final AttachmentType<String> FETCHUR_DAY = AttachmentRegistry.create(SkyRpgSkyblock.id("fetchur_day"),
            builder -> builder.persistent(Codec.STRING).copyOnDeath());
    private static final AttachmentType<Long> PUZZLER_SOLVED = AttachmentRegistry.create(SkyRpgSkyblock.id("puzzler_solved"),
            builder -> builder.persistent(Codec.LONG).copyOnDeath());
    /** The puzzle you're on (a seed), until you solve it. */
    private static final AttachmentType<Long> PUZZLER_SEED = AttachmentRegistry.create(SkyRpgSkyblock.id("puzzler_seed"),
            builder -> builder.persistent(Codec.LONG).copyOnDeath());
    private static final AttachmentType<List<String>> KINGS_MET = AttachmentRegistry.create(SkyRpgSkyblock.id("kings_met"),
            builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

    private static final List<String> KINGS = List.of("Brammor", "Emkam", "Redros", "Erren", "Thormyr", "Emmor", "Grandan");

    /** What Fetchur asks for: his hint, the amount, and the SkyBlock items that will do. */
    private record Request(String hint, int amount, String... items) {
    }

    private static final List<Request> REQUESTS = List.of(
            new Request("yellow and see through", 20, "STAINED_GLASS:4"),
            new Request("circular and sometimes moves", 1, "COMPASS"),
            new Request("expensive minerals", 20, "MITHRIL_ORE"),
            new Request("useful during celebrations", 1, "FIREWORK_ROCKET"),
            new Request("hot and gives energy", 1, "CHEAP_COFFEE", "DECENT_COFFEE", "BLACK_COFFEE"),
            new Request("tall and can be opened", 1, "IRON_DOOR", "WOOD_DOOR"),
            new Request("brown and fluffy", 3, "RABBIT_FOOT"),
            new Request("explosive but more than usual", 1, "SUPERBOOM_TNT"),
            new Request("wearable and grows", 1, "PUMPKIN"),
            new Request("shiny and makes sparks", 1, "FLINT_AND_STEEL"),
            new Request("green and some dudes trade stuff for it", 50, "EMERALD"),
            new Request("red and soft", 50, "WOOL:14"));

    private static final String[] ARROWS = {"▲", "▶", "▼", "◀"};
    private static final int[][] STEPS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    private static final long DAY_MILLIS = 24 * 60 * 60 * 1000L;

    private DailyNpcs() {
    }

    static void init() {
        Actions.register("fetchur", (player, params) -> openFetchur(player));
        Actions.register("puzzler", (player, params) -> openPuzzler(player));
        Actions.register("king", (player, params) -> king(player));
    }

    // ---------------------------------------------------------------- Fetchur

    private static String today() {
        return LocalDate.now(ZoneOffset.UTC).toString();
    }

    /** Today's request: the 12 cycle through the month, starting over on the 1st. */
    private static Request request() {
        return REQUESTS.get((LocalDate.now(ZoneOffset.UTC).getDayOfMonth() - 1) % REQUESTS.size());
    }

    private static int have(ServerPlayer player, Request request) {
        int total = 0;
        for (String item : request.items()) {
            total += SkyStacks.count(player.getInventory(), item);
        }
        return total;
    }

    private static ItemStack icon(Request request) {
        ItemDefinition def = ItemRegistry.skyblock(request.items()[0]);
        return def == null ? new ItemStack(Items.BARRIER) : SkyItems.create(def, request.amount());
    }

    private static void openFetchur(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Fetchur");
        CardMenu.open(player, p -> fetchurView(p, npc), DailyNpcs::fetchurClick);
    }

    private static CardView fetchurView(ServerPlayer player, int npc) {
        Request request = request();
        boolean done = today().equals(player.getAttached(FETCHUR_DAY));
        int have = have(player, request);
        List<CardView.Card> cards = new ArrayList<>();
        if (done) {
            cards.add(CardView.Card.info("Done for today", "come back another time, maybe tmrw", 0x55FF55, new ItemStack(Items.CLOCK)));
        } else {
            cards.add(new CardView.Card("Fetchur's request", "its/theyre " + request.hint() + ". i need " + request.amount() + " pls", 0xFFFFFF,
                    have >= request.amount(), "Give", icon(request),
                    "", List.of(Chip.item(icon(request), request.amount(), have), Chip.note(new ItemStack(Items.GOLD_INGOT), "20,000 coins", true),
                    Chip.note(new ItemStack(Items.PRISMARINE_CRYSTALS), "2,500 Mithril Powder", true)), -1, ""));
        }
        return new CardView("MINING", "Fetchur", "Daily request", npc, List.of(Chip.balance("coins", Wallet.balance(player))), cards,
                "hi i need your help maybe. im looking for some stuff, dont remember the name tbh", 1, 56);
    }

    private static String fetchurClick(ServerPlayer player, int card) {
        Request request = request();
        if (today().equals(player.getAttached(FETCHUR_DAY))) {
            return "";
        }
        if (have(player, request) < request.amount()) {
            player.sendSystemMessage(npc("Fetchur", ChatFormatting.YELLOW, "you didnt find my things yet?"));
            return "You don't have what Fetchur asked for.";
        }
        int left = request.amount();
        for (String item : request.items()) {
            int take = Math.min(left, SkyStacks.count(player.getInventory(), item));
            if (take > 0) {
                SkyStacks.take(player.getInventory(), item, take);
                left -= take;
            }
        }
        player.setAttached(FETCHUR_DAY, today());
        Wallet.give(player, 20_000);
        int powder = buffed(player, 2_500);
        Hotm.givePowder(player, Hotm.MITHRIL, powder);
        player.sendSystemMessage(npc("Fetchur", ChatFormatting.YELLOW, "thanks thats probably what i needed"));
        player.sendSystemMessage(npc("Fetchur", ChatFormatting.YELLOW, "take some gifts!"));
        Notice.Panel panel = Notice.of(Notice.Kind.QUEST_COMPLETE).tag("FETCHUR").panel();
        panel.line(Component.literal("+" + Msg.number(powder) + " " + Hotm.powderName(Hotm.MITHRIL)).withColor(Hotm.powderColor(Hotm.MITHRIL)));
        panel.line(Component.literal("+20,000 coins").withStyle(ChatFormatting.GOLD));
        panel.send(player, Component.literal("Fetchur's request done!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
        return "";
    }

    /** A powder reward after the Powder Buff perk (Hypixel's rewards count it; 2x Powder is applied when it's given). */
    private static int buffed(ServerPlayer player, int base) {
        Hotm.State hotm = Hotm.state(player);
        HotmTree.Perk buff = HotmTree.get("powder_buff");
        if (buff != null && hotm.active("powder_buff") > 0) {
            return (int) Math.round(base * (1 + buff.number("stat", hotm.active("powder_buff"), false) / 100));
        }
        return base;
    }

    // ---------------------------------------------------------------- the Puzzler

    /** The moves of a puzzle: 6 to 9 arrows. */
    private static int[] moves(long seed) {
        Random random = new Random(seed);
        int[] moves = new int[6 + random.nextInt(4)];
        for (int i = 0; i < moves.length; i++) {
            moves[i] = random.nextInt(4);
        }
        return moves;
    }

    private static int[] end(int[] moves) {
        int x = 0;
        int z = 0;
        for (int move : moves) {
            x += STEPS[move][0];
            z += STEPS[move][1];
        }
        return new int[] {x, z};
    }

    private static String where(int[] spot) {
        String across = spot[0] == 0 ? "" : Math.abs(spot[0]) + (spot[0] > 0 ? " right" : " left");
        String along = spot[1] == 0 ? "" : Math.abs(spot[1]) + (spot[1] > 0 ? " ahead" : " back");
        String both = across.isEmpty() ? along : along.isEmpty() ? across : across + ", " + along;
        return both.isEmpty() ? "Right where you started" : both;
    }

    /** The five places to pick from: the right one among four others, in a fixed order for this puzzle. */
    private static List<int[]> options(long seed) {
        int[] right = end(moves(seed));
        Random random = new Random(seed * 31 + 5);
        List<int[]> spots = new ArrayList<>();
        spots.add(right);
        while (spots.size() < 5) {
            int[] spot = {right[0] + random.nextInt(5) - 2, right[1] + random.nextInt(5) - 2};
            boolean fresh = true;
            for (int[] known : spots) {
                fresh &= known[0] != spot[0] || known[1] != spot[1];
            }
            if (fresh) {
                spots.add(spot);
            }
        }
        Collections.shuffle(spots, random);
        return spots;
    }

    private static boolean solvedToday(ServerPlayer player) {
        return System.currentTimeMillis() - player.getAttachedOrElse(PUZZLER_SOLVED, 0L) < DAY_MILLIS;
    }

    private static void openPuzzler(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Puzzler");
        if (!solvedToday(player) && player.getAttachedOrElse(PUZZLER_SEED, 0L) == 0L) {
            player.setAttached(PUZZLER_SEED, 1 + (long) (player.getRandom().nextDouble() * 1_000_000_000L));
        }
        CardMenu.open(player, p -> puzzlerView(p, npc), DailyNpcs::puzzlerClick);
    }

    private static CardView puzzlerView(ServerPlayer player, int npc) {
        List<CardView.Card> cards = new ArrayList<>();
        long seed = player.getAttachedOrElse(PUZZLER_SEED, 0L);
        if (solvedToday(player) || seed == 0L) {
            cards.add(CardView.Card.info("Solved", "▶▲Come▲ ◀back◀ ▼▶tomorrow▶", 0x55FF55, new ItemStack(Items.CLOCK)));
        } else {
            StringBuilder arrows = new StringBuilder();
            for (int move : moves(seed)) {
                arrows.append(ARROWS[move]).append(' ');
            }
            // the arrows as the card's title (a subtitle's text is too small to read them)
            cards.add(CardView.Card.info(arrows.toString().trim(), "Start facing the door and take one block for each arrow.", 0xFF55FF,
                    new ItemStack(Items.COMPASS)));
            for (int[] spot : options(seed)) {
                cards.add(new CardView.Card(where(spot), "", 0xF2F2F2, true, "", new ItemStack(Items.GLOWSTONE), "", List.of(), -1, ""));
            }
        }
        return new CardView("MINING", "Puzzler", "Daily puzzle", npc, List.of(Chip.balance("coins", Wallet.balance(player))), cards,
                "Solve it for 2,500 Mithril Powder, once every 24 hours.", 1, 36);
    }

    private static String puzzlerClick(ServerPlayer player, int card) {
        long seed = player.getAttachedOrElse(PUZZLER_SEED, 0L);
        if (card < 1 || seed == 0L || solvedToday(player)) {
            return "";
        }
        int[] picked = options(seed).get(card - 1);
        int[] right = end(moves(seed));
        if (picked[0] != right[0] || picked[1] != right[1]) {
            player.sendSystemMessage(npc("Puzzler", ChatFormatting.LIGHT_PURPLE, "╳ (╯°□°）╯︵ ┻━┻"));
            player.sendSystemMessage(npc("Puzzler", ChatFormatting.LIGHT_PURPLE, "▶▲Wrong▲ ◀block◀ ▼▶try again▶▶"));
            return "Wrong block, try again.";
        }
        player.setAttached(PUZZLER_SOLVED, System.currentTimeMillis());
        player.removeAttached(PUZZLER_SEED);
        int powder = buffed(player, 2_500);
        Hotm.givePowder(player, Hotm.MITHRIL, powder);
        player.sendSystemMessage(npc("Puzzler", ChatFormatting.LIGHT_PURPLE, "✌✓"));
        player.sendSystemMessage(npc("Puzzler", ChatFormatting.LIGHT_PURPLE, "▶▶Nice! ▲Here, ◀have ▼some◀ ▶Mithril Powder!▲"));
        player.sendSystemMessage(Component.literal("Puzzler ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("gave you ").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(Msg.number(powder) + " Mithril Powder").withStyle(ChatFormatting.DARK_GREEN))
                .append(Component.literal(" for solving the puzzle!").withStyle(ChatFormatting.GOLD)));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
        return "";
    }

    // ---------------------------------------------------------------- the King

    /** Which of the seven reigns now: a new one every SkyBlock day. */
    static String kingNow() {
        long days = Math.floorDiv(System.currentTimeMillis() - SkyBlockTime.EPOCH, SkyBlockTime.DAY);
        return KINGS.get((int) Math.floorMod(days, (long) KINGS.size()));
    }

    private static void king(ServerPlayer player) {
        String king = kingNow();
        List<String> met = new ArrayList<>(player.getAttachedOrElse(KINGS_MET, List.of()));
        boolean first = met.isEmpty();
        MutableLine line = new MutableLine(player, "King " + king, ChatFormatting.GOLD);
        if (first) {
            line.say("Hello Adventurer!");
            line.say("I see you've found yourself in my cavernous kingdom.");
            line.say("All work here is rewarded, complete commissions and I will bestow you with riches!");
        } else {
            line.say("Today the King is " + king + ". Serve the Mines well.");
        }
        if (!met.contains(king)) {
            met.add(king);
            player.setAttached(KINGS_MET, List.copyOf(met));
            player.sendSystemMessage(Component.literal("You have spoken to " + met.size() + " of the 7 Kings!").withStyle(ChatFormatting.GOLD));
            if (met.size() == KINGS.size()) {
                ItemDefinition talisman = ItemRegistry.skyblock("KING_TALISMAN");
                if (talisman != null) {
                    ItemGains.give(player, SkyItems.create(talisman, 1), ItemGains.Source.OTHER);
                }
                Notice.of(Notice.Kind.SUCCESS).tag("A ROYAL MEETING").send(player, Component.literal("You met all seven Kings and got the King Talisman!")
                        .withStyle(ChatFormatting.GOLD));
            }
        }
        CommissionsMenu.open(player);
    }

    /** A line of an NPC's talk: "[NPC] King Brammor: text". */
    private static Component npc(String name, ChatFormatting color, String text) {
        return Component.literal("[NPC] ").withStyle(ChatFormatting.YELLOW).append(Component.literal(name).withStyle(color))
                .append(Component.literal(": " + text).withStyle(ChatFormatting.WHITE));
    }

    private record MutableLine(ServerPlayer player, String name, ChatFormatting color) {
        void say(String text) {
            player.sendSystemMessage(npc(name, color, text));
        }
    }
}
