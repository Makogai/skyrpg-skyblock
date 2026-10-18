package com.skyrpg.skyblock.mayor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.time.SkyBlockTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Hypixel's Mayors and Elections ({@code skyrpg/mayors/mayors.json}). On the SkyBlock calendar, election {@code Y} opens
 * on the 27th of Late Summer of year Y and closes on the 27th of Late Spring of Y + 1; the winner is Mayor (all their
 * perks) for one SkyBlock year, the runner-up Minister (one perk). Five candidates: regular mayors (not last term's
 * Mayor or Minister, unless Diaz's Long Term Investment brings the Minister back with every perk), each with 1 to 4
 * of their perks, one marked for the Minister; every 8 years a special one (Jerry, Scorpius, Derpy in turn, Hypixel's
 * years) with all of theirs. Decided: candidates and perk counts come from the election year (the same everywhere,
 * restarts or not); players' votes decide, and ties (no votes, on a quiet server) go to a popularity rolled per
 * election. Perks are checked with {@link #active}.
 */
public final class Mayors {
    public record Perk(String id, String name, String description) {
    }

    public record Mayor(String key, String name, int color, String icon, boolean special, List<Perk> perks) {
        public Perk perk(String id) {
            return perks.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
        }
    }

    /** A candidate in an election: which perks they run with, and the one they'd bring as Minister. */
    public record Candidate(String key, List<String> perks, String minister) {
    }

    /** An election's outcome: the Mayor and their perks, the Minister and theirs (empty when there's none). */
    public record Result(int year, String mayor, List<String> perks, String minister, String ministerPerk) {
        static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("year").forGetter(Result::year),
                Codec.STRING.fieldOf("mayor").forGetter(Result::mayor),
                Codec.STRING.listOf().fieldOf("perks").forGetter(Result::perks),
                Codec.STRING.optionalFieldOf("minister", "").forGetter(Result::minister),
                Codec.STRING.optionalFieldOf("minister_perk", "").forGetter(Result::ministerPerk)).apply(i, Result::new));
    }

    /** Votes per election (player -> candidate key), results once an election closes, and the first election kept. */
    static final class Book extends SavedData {
        static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Codec.unboundedMap(Codec.STRING, Codec.STRING)).optionalFieldOf("votes", Map.of()).forGetter(b -> b.votes),
                Codec.unboundedMap(Codec.STRING, Result.CODEC).optionalFieldOf("results", Map.of()).forGetter(b -> b.results),
                Codec.INT.optionalFieldOf("first", -1).forGetter(b -> b.first),
                Codec.STRING.listOf().optionalFieldOf("bribed", List.of()).forGetter(b -> b.bribed)).apply(i, Book::new));
        static final SavedDataType<Book> TYPE = new SavedDataType<>(SkyRpgSkyblock.id("elections"), Book::new, CODEC, null);

        final Map<String, Map<String, String>> votes;
        final Map<String, Result> results;
        int first;
        /** "year:player" for every Scorpius bribe paid. */
        final List<String> bribed;

        private Book() {
            this(Map.of(), Map.of(), -1, List.of());
        }

        private Book(Map<String, Map<String, String>> votes, Map<String, Result> results, int first, List<String> bribed) {
            this.votes = new HashMap<>();
            votes.forEach((year, map) -> this.votes.put(year, new HashMap<>(map)));
            this.results = new HashMap<>(results);
            this.first = first;
            this.bribed = new ArrayList<>(bribed);
        }
    }

    /** The regular mayors (the special ones only come every 8 years). */
    static final List<String> REGULAR = List.of("slayer", "mining", "pets", "economist", "farming", "events", "fishing", "dungeons");
    /** Jerry's Perkpocalypse: whose perks, in turn, every 18 SkyBlock days. */
    private static final List<String> PERKPOCALYPSE = List.of("slayer", "mining", "pets", "farming", "fishing", "dungeons");
    private static final int LATE_SPRING = 2;
    private static final int LATE_SUMMER = 5;

    private static Map<String, Mayor> mayors = Map.of();
    private static MinecraftServer server;
    /** What Statspocalypse added last time (it reads the stats it raises). */
    private static final Map<UUID, Map<StatType, Double>> STATSPOCALYPSE = new HashMap<>();

    private Mayors() {
    }

    public static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("mayors"), "skyrpg/mayors", Mayors::load);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Mayor";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                perkStats(player, out);
            }
        });
        Actions.register("election", (player, params) -> ElectionMenu.open(player));
        com.skyrpg.core.world.Mayor.install(Mayors::active);
        com.skyrpg.skyblock.time.CalendarEvents.perks(Mayors::active);
        MayorSync.init();
        MiningFiesta.init();
        FishingFestival.init();
        Diana.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal("election").executes(ctx -> {
                ElectionMenu.open(ctx.getSource().getPlayerOrException());
                return 1;
            }));
            dispatcher.register(Commands.literal("mayor").executes(ctx -> {
                ElectionMenu.mayor(ctx.getSource().getPlayerOrException());
                return 1;
            }));
            // For testing: /sbmayor set <key> (this term's Mayor, with every perk), /sbmayor clear.
            dispatcher.register(Commands.literal("sbmayor").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("set").then(Commands.argument("mayor", StringArgumentType.word())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(mayors.keySet(), builder))
                            .executes(ctx -> {
                                Mayor mayor = mayors.get(StringArgumentType.getString(ctx, "mayor"));
                                if (mayor == null) {
                                    return 0;
                                }
                                Book book = book();
                                int year = termElection();
                                book.results.put(Integer.toString(year), new Result(year, mayor.key(), mayor.perks().stream().map(Perk::id).toList(), "", ""));
                                book.setDirty();
                                ctx.getSource().getServer().getPlayerList().getPlayers().forEach(Stats::refresh);
                                ctx.getSource().sendSuccess(() -> Component.literal("Mayor " + mayor.name() + " with every perk."), false);
                                return 1;
                            })))
                    .then(Commands.literal("clear").executes(ctx -> {
                        Book book = book();
                        book.results.remove(Integer.toString(termElection()));
                        book.setDirty();
                        return 1;
                    }))
                    .then(Commands.literal("year").then(Commands.argument("year", IntegerArgumentType.integer(1)).executes(ctx -> {
                        int year = IntegerArgumentType.getInteger(ctx, "year");
                        ctx.getSource().sendSuccess(() -> Component.literal("Election " + year + ": " + candidates(year)), false);
                        return 1;
                    }))));
        });
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, Mayor> loaded = new LinkedHashMap<>();
        for (JsonElement file : files.values()) {
            JsonObject json = file.getAsJsonObject();
            if (!json.has("mayors")) {
                continue;
            }
            for (JsonElement element : json.getAsJsonArray("mayors")) {
                JsonObject m = element.getAsJsonObject();
                List<Perk> perks = new ArrayList<>();
                for (JsonElement p : m.getAsJsonArray("perks")) {
                    JsonObject perk = p.getAsJsonObject();
                    perks.add(new Perk(perk.get("id").getAsString(), perk.get("name").getAsString(), perk.get("description").getAsString()));
                }
                String key = m.get("key").getAsString();
                loaded.put(key, new Mayor(key, m.get("name").getAsString(), Integer.parseInt(m.get("color").getAsString().substring(1), 16),
                        m.get("icon").getAsString(), m.has("special") && m.get("special").getAsBoolean(), List.copyOf(perks)));
            }
        }
        mayors = loaded;
        SkyRpgSkyblock.LOGGER.info("Loaded {} mayors", loaded.size());
    }

    public static Mayor mayor(String key) {
        return mayors.get(key);
    }

    private static Book book() {
        return server.overworld().getDataStorage().computeIfAbsent(Book.TYPE);
    }

    // ---------------------------------------------------------------- the calendar

    /** When election {@code year} opens (real millis). */
    public static long opens(int year) {
        return SkyBlockTime.start(year, LATE_SUMMER, 27);
    }

    /** When it closes and its winner takes office. */
    public static long closes(int year) {
        return SkyBlockTime.start(year + 1, LATE_SPRING, 27);
    }

    /** The election whose winner is in office now. */
    public static int termElection() {
        int year = SkyBlockTime.now().year();
        return System.currentTimeMillis() >= SkyBlockTime.start(year, LATE_SPRING, 27) ? year - 1 : year - 2;
    }

    /** The election open for votes now, or -1 between elections. */
    public static int openElection() {
        int year = SkyBlockTime.now().year();
        long now = System.currentTimeMillis();
        if (now >= opens(year)) {
            return year;
        }
        return now < closes(year - 1) ? year - 1 : -1;
    }

    // ---------------------------------------------------------------- candidates and results

    /** The special candidate in this election, or null: every 8 years, Jerry, Scorpius and Derpy in turn (Hypixel's years). */
    private static String special(int year) {
        return switch (Math.floorMod(year, 24)) {
            case 16 -> "jerry";
            case 0 -> "scorpius";
            case 8 -> "derpy";
            default -> null;
        };
    }

    /** Election {@code year}'s five candidates (special last). */
    public static List<Candidate> candidates(int year) {
        Random random = new Random(year * 7_919L + 13);
        Result last = year - 1 >= first() ? result(year - 1) : null;
        boolean investment = last != null && last.perks().contains("long_term_investment");
        List<String> pool = new ArrayList<>(REGULAR);
        if (last != null) {
            pool.remove(last.mayor());
            if (!investment) {
                pool.remove(last.minister());
            }
        }
        java.util.Collections.shuffle(pool, random);
        String special = special(year);
        List<String> keys = new ArrayList<>();
        // Diaz's investment: last term's Minister runs again
        if (investment && !last.minister().isEmpty()) {
            keys.add(last.minister());
            pool.remove(last.minister());
        }
        for (String key : pool) {
            if (keys.size() >= (special == null ? 5 : 4)) {
                break;
            }
            keys.add(key);
        }
        if (special != null) {
            keys.add(special);
        }
        List<Candidate> out = new ArrayList<>();
        for (String key : keys) {
            Mayor mayor = mayors.get(key);
            if (mayor == null) {
                continue;
            }
            List<String> all = new ArrayList<>(mayor.perks().stream().map(Perk::id).toList());
            if (mayor.special()) {
                out.add(new Candidate(key, List.copyOf(all), ""));
                continue;
            }
            java.util.Collections.shuffle(all, random);
            int roll = random.nextInt(100);
            int count = investment && last.minister().equals(key) ? all.size() : roll < 25 ? 1 : roll < 65 ? 2 : roll < 92 ? 3 : 4;
            List<String> perks = List.copyOf(all.subList(0, Math.min(all.size(), count)));
            out.add(new Candidate(key, perks, perks.get(random.nextInt(perks.size()))));
        }
        return out;
    }

    /** The first election this world keeps (elections before it are only rolled, with no one excluded). */
    private static int first() {
        if (server == null) {
            return Integer.MAX_VALUE;
        }
        Book book = book();
        if (book.first < 0) {
            book.first = termElection();
            book.setDirty();
        }
        return book.first;
    }

    /** Votes in an election, per candidate. */
    public static Map<String, Integer> tally(int year) {
        Map<String, Integer> tally = new HashMap<>();
        if (server != null) {
            book().votes.getOrDefault(Integer.toString(year), Map.of()).values().forEach(key -> tally.merge(key, 1, Integer::sum));
        }
        return tally;
    }

    /** An election's outcome (kept once it has closed). */
    public static Result result(int year) {
        Book book = server == null ? null : book();
        if (book != null && book.results.containsKey(Integer.toString(year))) {
            return book.results.get(Integer.toString(year));
        }
        List<Candidate> candidates = candidates(year);
        Map<String, Integer> tally = tally(year);
        Random popularity = new Random(year * 104_729L + 7);
        Map<String, Integer> score = new HashMap<>();
        for (Candidate c : candidates) {
            score.put(c.key(), tally.getOrDefault(c.key(), 0) * 1_000 + popularity.nextInt(1_000));
        }
        List<Candidate> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparingInt((Candidate c) -> -score.get(c.key())));
        Candidate winner = ranked.getFirst();
        Candidate second = ranked.size() > 1 ? ranked.get(1) : null;
        boolean minister = second != null && !mayors.get(second.key()).special();
        Result result = new Result(year, winner.key(), winner.perks(), minister ? second.key() : "", minister ? second.minister() : "");
        if (book != null && System.currentTimeMillis() >= closes(year)) {
            book.results.put(Integer.toString(year), result);
            book.setDirty();
        }
        return result;
    }

    /** The Mayor and Minister in office. */
    public static Result current() {
        return result(termElection());
    }

    // ---------------------------------------------------------------- votes

    public static String vote(ServerPlayer player) {
        int year = openElection();
        return year < 0 ? "" : book().votes.getOrDefault(Integer.toString(year), Map.of()).getOrDefault(player.getUUID().toString(), "");
    }

    /** Votes for a candidate in the open election (changing a vote is fine). */
    public static boolean vote(ServerPlayer player, String key) {
        int year = openElection();
        if (year < 0 || candidates(year).stream().noneMatch(c -> c.key().equals(key))) {
            return false;
        }
        Book book = book();
        book.votes.computeIfAbsent(Integer.toString(year), y -> new HashMap<>()).put(player.getUUID().toString(), key);
        book.setDirty();
        return true;
    }

    /** Scorpius won the election that put him in office and this player voted for him there, and hasn't claimed yet. */
    public static boolean bribeWaiting(ServerPlayer player) {
        int year = termElection();
        Result result = current();
        if (!result.mayor().equals("scorpius")) {
            return false;
        }
        Book book = book();
        return "scorpius".equals(book.votes.getOrDefault(Integer.toString(year), Map.of()).get(player.getUUID().toString()))
                && !book.bribed.contains(year + ":" + player.getUUID());
    }

    static void bribed(ServerPlayer player) {
        Book book = book();
        book.bribed.add(termElection() + ":" + player.getUUID());
        book.setDirty();
    }

    // ---------------------------------------------------------------- perks

    /**
     * Whether a perk is in effect: the Mayor's, the Minister's one, or, under Jerry's Perkpocalypse, the perks of the
     * mayor whose turn it is (18 SkyBlock days each).
     */
    public static boolean active(String perk) {
        if (server == null) {
            return false;
        }
        Result result = current();
        if (result.perks().contains(perk) || result.ministerPerk().equals(perk)) {
            return true;
        }
        if (result.mayor().equals("jerry") && result.perks().contains("perkpocalypse")) {
            long since = System.currentTimeMillis() - closes(result.year());
            int turn = (int) Math.floorMod(since / (18 * SkyBlockTime.DAY), PERKPOCALYPSE.size());
            Mayor mayor = mayors.get(PERKPOCALYPSE.get(turn));
            return mayor != null && !perk.equals("grand_feast") && mayor.perk(perk) != null;
        }
        return false;
    }

    /** The perks that are stats. */
    private static void perkStats(ServerPlayer player, Stats.Contributor out) {
        if (!SkyBlockWorlds.isSkyBlock(player.level())) {
            return;
        }
        boolean open = !com.skyrpg.skyblock.island.Islands.isIslands(player.level());
        if (active("pathfinder")) {
            out.add(StatTypes.MAGIC_FIND, 20);
        }
        if (active("huntress_intuition")) {
            out.add(StatTypes.TRACKING, 10);
        }
        if (active("luck_of_the_sea")) {
            out.add(StatTypes.SEA_CREATURE_CHANCE, 15);
        }
        if (active("double_trouble")) {
            out.add(StatTypes.DOUBLE_HOOK_CHANCE, 0.1 * Stats.get(player, StatTypes.SEA_CREATURE_CHANCE));
        }
        if (open && active("mining_xp_buff")) {
            out.add(StatTypes.MINING_WISDOM, 60);
        }
        if (open && active("mining_fiesta")) {
            out.add(StatTypes.MINING_WISDOM, 25);
            out.add(StatTypes.MINING_FORTUNE, 25);
        }
        if (open && active("fishing_xp_buff")) {
            out.add(StatTypes.FISHING_WISDOM, 50);
        }
        if (active("moar_skillz")) {
            for (StatType stat : StatTypes.all()) {
                if (stat.id().endsWith("_wisdom")) {
                    out.add(stat, 50);
                }
            }
        }
        // Statspocalypse: +10% of the combat stats, from the totals without itself
        Map<StatType, Double> last = STATSPOCALYPSE.getOrDefault(player.getUUID(), Map.of());
        Map<StatType, Double> now = new HashMap<>();
        if (active("statspocalypse")) {
            for (StatType stat : List.of(StatTypes.HEALTH, StatTypes.DEFENSE, StatTypes.STRENGTH, StatTypes.CRIT_DAMAGE, StatTypes.CRIT_CHANCE,
                    StatTypes.INTELLIGENCE, StatTypes.SPEED, StatTypes.FEROCITY)) {
                double bonus = Math.max(0, Stats.get(player, stat) - last.getOrDefault(stat, 0.0)) * 0.10;
                out.add(stat, bonus);
                now.put(stat, bonus);
            }
        }
        if (now.isEmpty()) {
            STATSPOCALYPSE.remove(player.getUUID());
        } else {
            STATSPOCALYPSE.put(player.getUUID(), now);
        }
    }
}
