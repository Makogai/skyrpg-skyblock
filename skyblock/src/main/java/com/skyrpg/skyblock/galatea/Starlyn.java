package com.skyrpg.skyblock.galatea;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.essence.Essence;
import com.skyrpg.skyblock.time.SkyBlockTime;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Agatha's Starlyn Contests on the Moonglade Marsh (Hypixel's): one each SkyBlock day (20 real minutes). Everything
 * foraging and hunting on Galatea scores (Hypixel's points: a Tree Gift 10, 100 Fig Logs 3, 100 Mangrove Logs 6, a critter
 * 3 to 45 by rarity), Starlyn Supreme adds a share. When the day turns, your points land in a bracket (Hypixel's eight,
 * 25 to 12,000 points) and Agatha pays it out: Agatha's Coupons (her prize shop's currency), Forest Whispers, Forest
 * Essence, Foraging and Heart of the Forest XP; Epic and up a 10% chance of Agatha's Prize; the day's first three
 * contests +1,200 HotF XP. Decided: the bracket thresholds between Hypixel's two ends, and the rewards in between.
 */
final class Starlyn {
    static final String[] BRACKETS = {"Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic", "Divine", "Special"};
    static final int[] COLORS = {0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00, 0xFF55FF, 0x55FFFF, 0xFF5555};
    static final long[] POINTS = {25, 100, 400, 1_000, 2_500, 5_000, 8_000, 12_000};
    private static final int[] COUPONS = {10, 15, 20, 25, 30, 35, 40, 45};
    private static final long[] WHISPERS = {2_500, 4_000, 6_000, 8_000, 10_000, 13_000, 16_000, 20_000};
    private static final int[] ESSENCE = {10, 20, 30, 40, 50, 60, 70, 80};
    private static final long[] FORAGING_XP = {2_000, 4_000, 8_000, 15_000, 25_000, 40_000, 60_000, 80_000};
    private static final long[] HOTF_XP = {100, 150, 250, 350, 450, 550, 650, 750};
    /** Points for a critter by its shard's rarity. */
    private static final int[] CRITTER = {3, 6, 12, 25, 45};

    /** A finished contest waiting at Agatha: which day, how many points. */
    record Result(long contest, double points) {
        static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("contest").forGetter(Result::contest),
                Codec.DOUBLE.fieldOf("points").forGetter(Result::points)).apply(i, Result::new));
    }

    /** The contest you're in, your points, results to claim, and the real day's count of claimed contests. */
    record Entry(long contest, double points, List<Result> unclaimed, long day, int claimedToday) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("contest", 0L).forGetter(Entry::contest),
                Codec.DOUBLE.optionalFieldOf("points", 0.0).forGetter(Entry::points),
                Result.CODEC.listOf().optionalFieldOf("unclaimed", List.of()).forGetter(Entry::unclaimed),
                Codec.LONG.optionalFieldOf("day", 0L).forGetter(Entry::day),
                Codec.INT.optionalFieldOf("claimed_today", 0).forGetter(Entry::claimedToday)).apply(i, Entry::new));
        static final Entry EMPTY = new Entry(0, 0, List.of(), 0, 0);

        Entry {
            unclaimed = List.copyOf(unclaimed);
        }
    }

    static final AttachmentType<Entry> ENTRY = AttachmentRegistry.create(SkyRpgSkyblock.id("starlyn"),
            builder -> builder.initializer(() -> Entry.EMPTY).persistent(Entry.CODEC).copyOnDeath());

    private Starlyn() {
    }

    static void init() {
        Actions.register("starlyn", (player, params) -> open(player));
        // For testing: /sbstarlyn points <player> <n>, /sbstarlyn end <player> (finishes their contest now).
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(net.minecraft.commands.Commands.literal("sbstarlyn")
                        .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .then(net.minecraft.commands.Commands.literal("points")
                                .then(net.minecraft.commands.Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .then(net.minecraft.commands.Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                                    Entry entry = entry(target);
                                                    target.setAttached(ENTRY, new Entry(entry.contest(), entry.points()
                                                            + com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "amount"), entry.unclaimed(),
                                                            entry.day(), entry.claimedToday()));
                                                    return 1;
                                                }))))
                        .then(net.minecraft.commands.Commands.literal("end")
                                .then(net.minecraft.commands.Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                            Entry entry = entry(target);
                                            target.setAttached(ENTRY, new Entry(entry.contest() - 1, entry.points(), entry.unclaimed(), entry.day(),
                                                    entry.claimedToday()));
                                            entry(target);
                                            return 1;
                                        })))));
    }

    /** The contest running now: the SkyBlock day's number. */
    static long contest() {
        return Math.floorDiv(System.currentTimeMillis() - SkyBlockTime.EPOCH, SkyBlockTime.DAY);
    }

    private static long endsAt() {
        return SkyBlockTime.EPOCH + (contest() + 1) * SkyBlockTime.DAY;
    }

    /** The player's entry, a finished contest moved to the results waiting at Agatha. */
    private static Entry entry(ServerPlayer player) {
        Entry entry = player.getAttachedOrElse(ENTRY, Entry.EMPTY);
        long now = contest();
        if (entry.contest() == now) {
            return entry;
        }
        List<Result> unclaimed = new ArrayList<>(entry.unclaimed());
        if (entry.points() >= POINTS[0]) {
            unclaimed.add(new Result(entry.contest(), entry.points()));
            player.sendSystemMessage(Notice.build(Notice.Kind.SUCCESS, Component.literal("Your Starlyn Contest ended with ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(Msg.number(Math.floor(entry.points())) + " points").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(": see Agatha on the Murkwater Loch pier for your rewards!").withStyle(ChatFormatting.GRAY))));
        }
        // at most the last few results wait
        while (unclaimed.size() > 10) {
            unclaimed.removeFirst();
        }
        Entry fresh = new Entry(now, 0, unclaimed, entry.day(), entry.claimedToday());
        player.setAttached(ENTRY, fresh);
        return fresh;
    }

    /** -1 below the first bracket. */
    static int bracket(double points) {
        int bracket = -1;
        for (int i = 0; i < POINTS.length; i++) {
            if (points >= POINTS[i]) {
                bracket = i;
            }
        }
        return bracket;
    }

    /** Points on the Moonglade Marsh (Galatea only), Starlyn Supreme's share added. */
    static void score(ServerPlayer player, double points, String why) {
        if (!Galatea.isGalatea(player.level()) || points <= 0) {
            return;
        }
        Entry entry = entry(player);
        int before = bracket(entry.points());
        double total = entry.points() + points * Hotf.contestPoints(player);
        player.setAttached(ENTRY, new Entry(entry.contest(), total, entry.unclaimed(), entry.day(), entry.claimedToday()));
        int after = bracket(total);
        if (after > before) {
            player.sendSystemMessage(Component.literal("STARLYN CONTEST ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                    .append(Component.literal("You reached the ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(BRACKETS[after]).withColor(COLORS[after]))
                    .append(Component.literal(" bracket!").withStyle(ChatFormatting.GRAY)));
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_CHIME.value(), 1f, 1.4f);
        }
    }

    static void gift(ServerPlayer player) {
        score(player, 10, "gift");
    }

    static void logs(ServerPlayer player, GalateaTrees.Kind kind, long logs) {
        score(player, logs * (kind == GalateaTrees.Kind.MANGROVE ? 0.06 : 0.03), "logs");
    }

    static void critter(ServerPlayer player, int rarity) {
        score(player, CRITTER[Math.max(0, Math.min(4, rarity))], "critter");
    }

    /** Agatha: this contest, rewards to claim, and her prize shop. */
    static void open(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Agatha");
        CardMenu.open(player, p -> {
            Entry entry = entry(p);
            int bracket = bracket(entry.points());
            List<CardView.Card> cards = new ArrayList<>();
            String next = bracket + 1 < POINTS.length ? Msg.number(POINTS[bracket + 1]) + " for " + BRACKETS[bracket + 1] : "the top bracket!";
            cards.add(new CardView.Card("Starlyn Contest", Msg.number(Math.floor(entry.points())) + " points  ·  next: " + next
                    + "  ·  ends in " + SkyBlockTime.countdown(endsAt() - System.currentTimeMillis()),
                    bracket >= 0 ? COLORS[bracket] : 0xAAAAAA, false, "", new ItemStack(Items.SUNFLOWER), "", List.of(),
                    bracket + 1 < POINTS.length ? (float) (entry.points() / POINTS[bracket + 1]) : 1f, bracket >= 0 ? BRACKETS[bracket] : "No bracket"));
            int waiting = entry.unclaimed().size();
            cards.add(new CardView.Card("Claim rewards", waiting == 0 ? "No finished contests to claim." : waiting + " finished contest"
                    + (waiting == 1 ? "" : "s") + " waiting.", 0x55FF55, waiting > 0, "Claim", new ItemStack(Items.CHEST), "", List.of(), -1, ""));
            cards.add(new CardView.Card("Prize shop", "Spend Agatha's Coupons on frog pets, talismans and Fig and Mangrove upgrades.", 0xFF55FF,
                    true, "Browse", new ItemStack(Items.NAME_TAG), "", List.of(), -1, ""));
            cards.add(new CardView.Card("How it scores", "Tree Gift 10  ·  100 Fig Logs 3  ·  100 Mangrove Logs 6  ·  critters 3 to 45",
                    0xEEEEEE, false, "", new ItemStack(Items.WRITABLE_BOOK), "", List.of(), -1, ""));
            return new CardView("GALATEA", "Agatha", "Starlyn Contests", npc,
                    List.of(Chip.note(new ItemStack(Items.NAME_TAG), coupons(p) + " Coupons", true)), cards,
                    "A new contest every SkyBlock day, on the Moonglade Marsh.", 1, 40);
        }, (p, card) -> {
            if (card == 1) {
                return claim(p);
            }
            if (card == 2) {
                com.google.gson.JsonObject params = new com.google.gson.JsonObject();
                params.addProperty("shop", "agatha");
                Actions.run("skyblock_shop", p, params);
            }
            return "";
        });
    }

    private static long coupons(ServerPlayer player) {
        long count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if ("AGATHA_COUPON".equals(SkyItems.skyblockId(stack))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static String claim(ServerPlayer player) {
        Entry entry = entry(player);
        if (entry.unclaimed().isEmpty()) {
            return "No finished contests to claim.";
        }
        long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        int claimedToday = entry.day() == today ? entry.claimedToday() : 0;
        for (Result result : entry.unclaimed()) {
            int bracket = Math.max(0, bracket(result.points()));
            Notice.Panel panel = Notice.of(Notice.Kind.SUCCESS).tag("STARLYN CONTEST").panel();
            give(player, "AGATHA_COUPON", COUPONS[bracket]);
            panel.line(Component.literal(" +" + COUPONS[bracket] + " Agatha's Coupons").withStyle(ChatFormatting.LIGHT_PURPLE));
            Hotf.giveWhispers(player, Hotf.FOREST, WHISPERS[bracket]);
            panel.line(Component.literal(" +" + Msg.number(WHISPERS[bracket]) + " Forest Whispers").withStyle(ChatFormatting.DARK_AQUA));
            Essence.give(player, "FOREST", ESSENCE[bracket]);
            panel.line(Component.literal(" +" + ESSENCE[bracket] + " Forest Essence").withStyle(ChatFormatting.GREEN));
            SkillLevels.giveXp(player, SkillLevels.FORAGING, FORAGING_XP[bracket]);
            panel.line(Component.literal(" +" + Msg.number(FORAGING_XP[bracket]) + " Foraging XP").withStyle(ChatFormatting.DARK_AQUA));
            long hotf = HOTF_XP[bracket] + (claimedToday < 3 ? 1_200 : 0);
            claimedToday++;
            Hotf.giveXp(player, hotf);
            panel.line(Component.literal(" +" + Msg.number(hotf) + " Heart of the Forest XP").withStyle(ChatFormatting.DARK_GREEN));
            if (bracket >= 3 && player.getRandom().nextDouble() < 0.10) {
                give(player, "STARLYN_PRIZE", 1);
                panel.line(Component.literal(" RARE! Agatha's Prize").withStyle(ChatFormatting.GOLD));
            }
            panel.send(player, Component.literal(BRACKETS[bracket] + " bracket").withColor(COLORS[bracket])
                    .append(Component.literal("  ·  " + Msg.number(Math.floor(result.points())) + " points").withStyle(ChatFormatting.GRAY)));
        }
        player.setAttached(ENTRY, new Entry(entry.contest(), entry.points(), List.of(), today, claimedToday));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.3f);
        return "";
    }

    private static void give(ServerPlayer player, String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        ItemStack stack = SkyItems.create(def, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }
}
