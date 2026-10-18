package com.skyrpg.skyblock.galatea;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.stats.DamageBonus;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;

/**
 * Hypixel's Heart of the Forest: Galatea's own progression, like the Heart of the Mountain. HotF XP (from Tree Gifts and
 * Starlyn Contests) raises the tier (1 to 8), each tier opens the next row of the tree and gives Tokens of the Forest; a
 * token unlocks a perk next to one you have, and perks level up with Forest Whispers (Fig and Mangrove gifts, contests)
 * or Desert Whispers (Torrhus, later). Perks give foraging stats and effects; one unlocked Axe Ability is selected and
 * fires on sneak + right-click with an axe on Galatea or The Park. Per profile (a player attachment).
 */
public final class Hotf {
    public static final String FOREST = "FOREST_WHISPERS";
    public static final String DESERT = "DESERT_WHISPERS";
    public static final List<String> WHISPERS = List.of(FOREST, DESERT);
    /** Tokens of the Forest each tier gives (index 0: tier 1). */
    private static final int[] TOKENS = {1, 2, 2, 2, 2, 2, 2, 2};
    /** Center of the Forest's tokens by its level (cumulative). */
    private static final int[] CENTER_TOKENS = {0, 1, 2, 3, 4, 6};
    /** The tiers that open an axe ability slot: tier 2 (two abilities), tier 6 (the third). */
    static final int ABILITY_TIER = 2;
    /** HotF XP per Tree Gift (Hypixel's). */
    static final int FIG_XP = 10;
    static final int MANGROVE_XP = 20;
    /** The Foraging islands (Galatea, The Park): Strength/Speed Boost and Forest Fisher count there. */
    private static final ResourceKey<Level> PARK = Galatea.PARK;

    /** One profile's Heart of the Forest, with the day's counters (gifts, first log type). */
    public record State(long xp, Map<String, Integer> perks, List<String> disabled, Map<String, Long> whispers, String ability, long day,
            int gifts, String firstLog) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("xp", 0L).forGetter(State::xp),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("perks", Map.of()).forGetter(State::perks),
                Codec.STRING.listOf().optionalFieldOf("disabled", List.of()).forGetter(State::disabled),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("whispers", Map.of()).forGetter(State::whispers),
                Codec.STRING.optionalFieldOf("ability", "").forGetter(State::ability),
                Codec.LONG.optionalFieldOf("day", 0L).forGetter(State::day),
                Codec.INT.optionalFieldOf("gifts", 0).forGetter(State::gifts),
                Codec.STRING.optionalFieldOf("first_log", "").forGetter(State::firstLog)).apply(i, State::new));
        static final State EMPTY = new State(0, Map.of(), List.of(), Map.of(), "", 0, 0, "");

        public State {
            perks = Map.copyOf(perks);
            disabled = List.copyOf(disabled);
            whispers = Map.copyOf(whispers);
        }

        public int perk(String id) {
            return perks.getOrDefault(id, 0);
        }

        /** A perk's level if it's unlocked and switched on, else 0. */
        public int active(String id) {
            return disabled.contains(id) ? 0 : perk(id);
        }

        public long whispers(String type) {
            return whispers.getOrDefault(type, 0L);
        }

        State withPerk(String id, int level) {
            Map<String, Integer> more = new LinkedHashMap<>(perks);
            more.put(id, level);
            return new State(xp, more, disabled, whispers, ability, day, gifts, firstLog);
        }

        State withWhispers(String type, long delta) {
            Map<String, Long> have = new HashMap<>(whispers);
            have.merge(type, delta, Long::sum);
            return new State(xp, perks, disabled, have, ability, day, gifts, firstLog);
        }

        State withAbility(String id) {
            return new State(xp, perks, disabled, whispers, id, day, gifts, firstLog);
        }

        State withXp(long more) {
            return new State(xp + more, perks, disabled, whispers, ability, day, gifts, firstLog);
        }

        /** The day's counters, reset when the (UTC) day changed. */
        State today(long today) {
            return today == day ? this : new State(xp, perks, disabled, whispers, ability, today, 0, "");
        }
    }

    public static final AttachmentType<State> STATE = AttachmentRegistry.create(SkyRpgSkyblock.id("hotf"),
            builder -> builder.initializer(() -> State.EMPTY).persistent(State.CODEC).copyOnDeath());

    /** A running axe ability: which, until when. */
    private record Running(String ability, long until, double sweep) {
    }

    private static final Map<UUID, Running> RUNNING = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    /** What Galatea's Might added last time (it reads the stats it raises). */
    private static final Map<UUID, Map<StatType, Double>> MIGHT = new HashMap<>();

    private Hotf() {
    }

    static void init() {
        HotfTree.init();
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Heart of the Forest";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                if (SkyBlockWorlds.isSkyBlock(player.level())) {
                    contributeStats(player, out);
                }
            }
        });
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Galatea's Might";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                contributeMight(player, out);
            }
        });
        DamageBonus.register((attacker, target, source) -> {
            Running running = RUNNING.get(attacker.getUUID());
            return running != null && running.ability().equals("damage_boost") && Galatea.isGalatea(target.level())
                    && GalateaTrees.isAxe(attacker.getMainHandItem()) ? 100 : 0;
        });
        ServerTickEvents.END_SERVER_TICK.register(Hotf::tick);
        Actions.register("hotf", (player, params) -> HotfMenu.open(player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal("hotf").executes(ctx -> {
                HotfMenu.open(ctx.getSource().getPlayerOrException());
                return 1;
            }));
            dispatcher.register(Commands.literal("sbhotf").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("xp").then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(ctx -> {
                                giveXp(EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "amount"));
                                return 1;
                            }))))
                    .then(Commands.literal("whispers").then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("type", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("forest", "desert"), builder))
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(ctx -> {
                                        String type = StringArgumentType.getString(ctx, "type").toUpperCase(Locale.ROOT) + "_WHISPERS";
                                        giveWhispers(EntityArgument.getPlayer(ctx, "player"), type, IntegerArgumentType.getInteger(ctx, "amount"));
                                        return 1;
                                    })))))
                    .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        target.setAttached(STATE, State.EMPTY);
                        Stats.refresh(target);
                        return 1;
                    })))
                    .then(Commands.literal("perk").then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("perk", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(HotfTree.all().stream().map(HotfTree.Perk::id), builder))
                                    .then(Commands.argument("level", IntegerArgumentType.integer(0)).executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        HotfTree.Perk perk = HotfTree.get(StringArgumentType.getString(ctx, "perk"));
                                        if (perk == null) {
                                            return 0;
                                        }
                                        set(target, state(target).withPerk(perk.id(), Math.min(perk.max(), IntegerArgumentType.getInteger(ctx, "level"))));
                                        Stats.refresh(target);
                                        return 1;
                                    }))))));
        });
    }

    // ---------------------------------------------------------------- state

    public static State state(ServerPlayer player) {
        return player.getAttachedOrElse(STATE, State.EMPTY);
    }

    private static void set(ServerPlayer player, State state) {
        player.setAttached(STATE, state);
    }

    /** A perk's number at the level it's switched on at (0 when off or not bought). */
    static double number(ServerPlayer player, String id, String key) {
        int level = state(player).active(id);
        HotfTree.Perk perk = HotfTree.get(id);
        return level <= 0 || perk == null ? 0 : perk.number(key, level);
    }

    static boolean has(ServerPlayer player, String id) {
        return state(player).active(id) > 0;
    }

    /** The HotF tier (1 to 8) for this much XP. */
    public static int tier(long xp) {
        int tier = 1;
        while (tier < HotfTree.maxLevel() && xp >= HotfTree.totalFor(tier + 1)) {
            tier++;
        }
        return tier;
    }

    public static int tier(ServerPlayer player) {
        return tier(state(player).xp());
    }

    /** Every Token of the Forest earned (tiers and Center of the Forest). */
    public static int tokens(State state) {
        int tier = tier(state.xp());
        int total = 0;
        for (int i = 0; i < tier && i < TOKENS.length; i++) {
            total += TOKENS[i];
        }
        return total + CENTER_TOKENS[Math.min(CENTER_TOKENS.length - 1, state.perk("center_of_the_forest"))];
    }

    /** Tokens spent: one per unlocked perk. */
    public static int tokensSpent(State state) {
        return (int) state.perks().values().stream().filter(level -> level > 0).count();
    }

    /** Whether a perk can be unlocked now: its tier is open and it's next to one already unlocked (or on tier 1). */
    public static boolean unlockable(State state, HotfTree.Perk perk) {
        if (state.perk(perk.id()) > 0 || perk.tier() > tier(state.xp())) {
            return false;
        }
        if (perk.tier() == 1) {
            return true;
        }
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            HotfTree.Perk next = HotfTree.at(perk.x() + d[0], perk.y() + d[1]);
            if (next != null && state.perk(next.id()) > 0) {
                return true;
            }
        }
        return false;
    }

    /** A click on a perk: unlock it with a token, upgrade it with Whispers, or select it (axe abilities). */
    static String click(ServerPlayer player, String id, int times) {
        HotfTree.Perk perk = HotfTree.get(id);
        if (perk == null) {
            return "";
        }
        State state = state(player);
        int level = state.perk(id);
        if (level == 0) {
            if (!unlockable(state, perk)) {
                return perk.tier() > tier(state.xp()) ? "Reach Heart of the Forest tier " + perk.tier() + " to unlock this!"
                        : "Unlock a perk next to it first!";
            }
            if (tokens(state) - tokensSpent(state) < 1) {
                return "You don't have a Token of the Forest!";
            }
            state = state.withPerk(id, 1);
            if (perk.ability() && state.ability().isEmpty()) {
                state = state.withAbility(id);
            }
            set(player, state);
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.4f);
            player.sendSystemMessage(Notice.build(Notice.Kind.SUCCESS, Component.literal("Unlocked ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(perk.name()).withStyle(ChatFormatting.GREEN)).append(Component.literal("!").withStyle(ChatFormatting.GRAY))));
            Stats.refresh(player);
            return "";
        }
        if (perk.ability()) {
            set(player, state.withAbility(id));
            player.sendOverlayMessage(Component.literal("Selected ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(perk.name()).withStyle(ChatFormatting.GOLD)));
            Msg.playSound(player, SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.2f);
            return "";
        }
        int done = 0;
        for (int i = 0; i < times && level < perk.max(); i++) {
            String type = perk.powderAt(level);
            int cost = perk.costAt(level);
            if (state.whispers(type) < cost) {
                break;
            }
            state = state.withWhispers(type, -cost).withPerk(id, level + 1);
            level++;
            done++;
        }
        if (done == 0) {
            return level >= perk.max() ? "This perk is maxed!" : "You don't have enough " + whisperName(perk.powderAt(level)) + "!";
        }
        set(player, state);
        Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        Stats.refresh(player);
        return "";
    }

    /** Switches a perk on or off (it keeps its level). */
    static void toggle(ServerPlayer player, String id) {
        State state = state(player);
        if (state.perk(id) <= 0) {
            return;
        }
        List<String> disabled = new ArrayList<>(state.disabled());
        if (!disabled.remove(id)) {
            disabled.add(id);
        }
        set(player, new State(state.xp(), state.perks(), disabled, state.whispers(), state.ability(), state.day(), state.gifts(), state.firstLog()));
        Stats.refresh(player);
    }

    // ---------------------------------------------------------------- XP and Whispers

    public static void giveXp(ServerPlayer player, long amount) {
        if (amount <= 0) {
            return;
        }
        State state = state(player);
        int before = tier(state.xp());
        state = state.withXp(amount);
        set(player, state);
        int after = tier(state.xp());
        for (int tier = before + 1; tier <= after; tier++) {
            tierUp(player, tier);
        }
    }

    private static void tierUp(ServerPlayer player, int tier) {
        Notice.Panel panel = Notice.of(Notice.Kind.SUCCESS).tag("HEART OF THE FOREST").panel();
        panel.heading("Rewards", 0x55FF55);
        int tokens = tier - 1 < TOKENS.length ? TOKENS[tier - 1] : 0;
        panel.line(Component.literal("+" + tokens + " Token" + (tokens == 1 ? "" : "s") + " of the Forest").withStyle(ChatFormatting.DARK_GREEN));
        panel.line(Component.literal("Unlocked Tier " + Msg.roman(tier) + " perks").withStyle(ChatFormatting.GRAY));
        if (tier == ABILITY_TIER) {
            panel.line(Component.literal("Axe Abilities").withStyle(ChatFormatting.GOLD));
        }
        panel.send(player, Component.literal("Tier " + Msg.roman(tier)).withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.BOLD));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    public static void giveWhispers(ServerPlayer player, String type, long amount) {
        if (amount <= 0 || !WHISPERS.contains(type)) {
            return;
        }
        set(player, state(player).withWhispers(type, amount));
    }

    public static String whisperName(String type) {
        return DESERT.equals(type) ? "Desert Whispers" : "Forest Whispers";
    }

    public static int whisperColor(String type) {
        return DESERT.equals(type) ? 0xFFAA00 : 0x00AAAA;
    }

    // ---------------------------------------------------------------- hooks from the trees, hunting and contests

    private static long today() {
        return LocalDate.now(ZoneOffset.UTC).toEpochDay();
    }

    /** How much more a Tree Gift gives (Luck of the Forest, 250 Gifts while the day's first 250 last), as a factor. */
    static double giftLuck(ServerPlayer player) {
        State state = state(player).today(today());
        double percent = number(player, "luck_of_the_forest", "stat");
        if (state.gifts() < 250) {
            percent += number(player, "250_gifts", "stat");
        }
        return 1 + percent / 100;
    }

    /** A Tree Gift for this player: HotF XP and Forest Whispers (and the day's gift count). Returns the Whispers given. */
    static long onGift(ServerPlayer player, GalateaTrees.Kind kind) {
        State state = state(player).today(today());
        long whispers = 20 + player.getRandom().nextInt(21);
        if (state.gifts() < 250 && state.active("250_gifts") > 0) {
            whispers += 20;
        }
        if (state.active("tree_whisperer") > 0) {
            whispers += 200;
        }
        if (state.active("center_of_the_forest") >= 3) {
            whispers += 20;
        }
        state = new State(state.xp(), state.perks(), state.disabled(), state.whispers(), state.ability(), state.day(), state.gifts() + 1, state.firstLog());
        set(player, state.withWhispers(FOREST, whispers));
        giveXp(player, kind == GalateaTrees.Kind.MANGROVE ? MANGROVE_XP : FIG_XP);
        return whispers;
    }

    /** Logs cut: Center of the Forest's Whispers per log, Daily Wishes on the day's first kind of log. */
    static void onLogs(ServerPlayer player, GalateaTrees.Kind kind, long logs) {
        State state = state(player).today(today());
        long whispers = state.active("center_of_the_forest") >= 3 ? 2 * logs : 0;
        if (state.firstLog().isEmpty()) {
            long wishes = Math.round(number(player, "daily_wishes", "stat"));
            if (wishes > 0) {
                whispers += wishes;
                player.sendSystemMessage(Component.literal("Daily Wishes! ").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)
                        .append(Component.literal("+" + Msg.number(wishes) + " Forest Whispers").withStyle(ChatFormatting.AQUA)));
            }
            state = new State(state.xp(), state.perks(), state.disabled(), state.whispers(), state.ability(), state.day(), state.gifts(), kind.name());
        }
        set(player, whispers > 0 ? state.withWhispers(FOREST, whispers) : state);
    }

    /** Forest Essence from a gift, with Essence Fortune's chance to double it. */
    static int essence(ServerPlayer player, int amount) {
        return player.getRandom().nextDouble() * 100 < number(player, "essence_fortune", "stat") ? amount * 2 : amount;
    }

    /** Two for One: this chop fells the whole tree. */
    static boolean twoForOne(ServerPlayer player) {
        return player.getRandom().nextDouble() * 100 < number(player, "two_for_one", "stat");
    }

    /** Monster Hunter: a critter caught on Galatea is worth 25 Forest Whispers. */
    static void onCritter(ServerPlayer player) {
        if (has(player, "monster_hunter") && Galatea.isGalatea(player.level())) {
            giveWhispers(player, FOREST, 25);
        }
    }

    /** Starlyn Supreme: more contest points, as a factor. */
    static double contestPoints(ServerPlayer player) {
        return 1 + number(player, "starlyn_supreme", "stat") / 100;
    }

    /** The Sweep factor on a thrown axe: half, or whole while Axe Toss runs. */
    static double thrownSweep(ServerPlayer player) {
        Running running = RUNNING.get(player.getUUID());
        return running != null && running.ability().equals("axe_toss") ? 1 : 0.5;
    }

    // ---------------------------------------------------------------- stats

    private static boolean foragingIsland(ServerPlayer player) {
        return Galatea.isGalatea(player.level()) || player.level().dimension().equals(PARK);
    }

    private static void contributeStats(ServerPlayer player, Stats.Contributor out) {
        State state = state(player).today(today());
        boolean island = foragingIsland(player);
        double sweep = 0;
        double fortune = 0;
        for (HotfTree.Perk perk : HotfTree.all()) {
            int level = state.active(perk.id());
            if (level <= 0) {
                continue;
            }
            double value = perk.values().containsKey("stat") ? perk.number("stat", level) : 0;
            switch (perk.id()) {
                case "sweep" -> sweep += value;
                case "foraging_fortune" -> fortune += value;
                case "hunters_luck" -> out.add(StatTypes.HUNTING_FORTUNE, value);
                case "deep_waters" -> out.add(StatTypes.PRESSURE_RESISTANCE, value);
                case "iron_lungs" -> out.add(StatTypes.RESPIRATION, value);
                case "efficient_forager" -> out.add(StatTypes.FORAGING_WISDOM, value);
                case "timber" -> out.add(StatTypes.TIMBER, value);
                case "foraging_madness" -> {
                    sweep += 10;
                    fortune += 50;
                }
                case "early_bird" -> {
                    if (state.gifts() < 250) {
                        sweep += 20;
                        fortune += 100;
                    }
                }
                case "lottery" -> {
                    // the day's buff: Fig Fortune, Mangrove Fortune or Sweep
                    switch ((int) Math.floorMod(player.getUUID().getLeastSignificantBits() ^ state.day() * 31, 3)) {
                        case 0 -> out.add(StatTypes.FIG_FORTUNE, 50);
                        case 1 -> out.add(StatTypes.MANGROVE_FORTUNE, 50);
                        default -> sweep += 10;
                    }
                }
                case "strength_boost" -> {
                    if (island) {
                        out.add(StatTypes.STRENGTH, value);
                    }
                }
                case "speed_boost" -> {
                    if (island) {
                        out.add(StatTypes.SPEED, value);
                    }
                }
                case "forest_fisher" -> {
                    if (island) {
                        out.add(StatTypes.FISHING_SPEED, perk.number("fishingSpeed", level));
                        out.add(StatTypes.SEA_CREATURE_CHANCE, perk.number("seaCreatureChance", level));
                    }
                }
                case "half_full", "half_empty" -> {
                    String other = perk.id().equals("half_full") ? "half_empty" : "half_full";
                    long partners = player.level().players().stream()
                            .filter(p -> p != player && p instanceof ServerPlayer sp && state(sp).active(other) > 0).limit(5).count();
                    fortune += perk.number("fortune", level) * partners;
                    sweep += perk.number("sweep", level) * partners;
                }
                case "forest_speed" -> {
                    double share = Math.min(500, Stats.get(player, StatTypes.SPEED)) * value / 100;
                    fortune += share;
                    sweep += share;
                }
                case "forest_strength" -> {
                    double share = Math.min(1000, Stats.get(player, StatTypes.STRENGTH)) * value / 100;
                    fortune += share;
                    sweep += share;
                }
                default -> {
                }
            }
        }
        Running running = RUNNING.get(player.getUUID());
        if (running != null && running.sweep() > 0) {
            sweep += running.sweep();
        }
        if (state.active("center_of_the_forest") >= 3) {
            sweep *= 1.05;
        }
        out.add(StatTypes.SWEEP, sweep);
        out.add(StatTypes.FORAGING_FORTUNE, fortune);
    }

    /** Galatea's Might: a share of Health, Defense, Strength and Crit Damage on Galatea, from the totals without itself. */
    private static void contributeMight(ServerPlayer player, Stats.Contributor out) {
        double percent = Galatea.isGalatea(player.level()) ? number(player, "galateas_might", "stat") : 0;
        Map<StatType, Double> last = MIGHT.getOrDefault(player.getUUID(), Map.of());
        Map<StatType, Double> now = new HashMap<>();
        if (percent > 0) {
            for (StatType stat : List.of(StatTypes.HEALTH, StatTypes.DEFENSE, StatTypes.STRENGTH, StatTypes.CRIT_DAMAGE)) {
                double base = Stats.get(player, stat) - last.getOrDefault(stat, 0.0);
                double bonus = Math.max(0, base) * percent / 100;
                out.add(stat, bonus);
                now.put(stat, bonus);
            }
        }
        if (now.isEmpty()) {
            MIGHT.remove(player.getUUID());
        } else {
            MIGHT.put(player.getUUID(), now);
        }
    }

    // ---------------------------------------------------------------- axe abilities

    /** The ability level: 1, or 2 with Center of the Forest II. */
    private static int abilityLevel(State state) {
        return state.active("center_of_the_forest") >= 2 ? 2 : 1;
    }

    /** Sneak + right-click with an axe on a Foraging island: the selected Axe Ability. True when it was handled. */
    static boolean useAbility(ServerPlayer player) {
        State state = state(player);
        String ability = state.ability();
        if (ability.isEmpty() || state.active(ability) <= 0 || !foragingIsland(player)) {
            return false;
        }
        HotfTree.Perk perk = HotfTree.get(ability);
        long now = player.level().getServer().getTickCount();
        Long ready = COOLDOWNS.get(player.getUUID());
        if (ready != null && now < ready) {
            player.sendOverlayMessage(Component.literal("Your axe ability is on cooldown for " + ((ready - now) / 20 + 1) + "s.")
                    .withStyle(ChatFormatting.RED));
            return true;
        }
        boolean better = abilityLevel(state) >= 2;
        int seconds;
        int cooldown;
        double sweep = 0;
        switch (ability) {
            case "damage_boost" -> {
                seconds = better ? 15 : 10;
                cooldown = better ? 170 : 210;
            }
            case "axe_toss" -> {
                seconds = better ? 15 : 10;
                cooldown = better ? 100 : 120;
            }
            case "maniac_slicer" -> {
                double mana = Stats.mana(player);
                Stats.spendMana(player, mana);
                sweep = Math.floor(mana / 100);
                seconds = better ? 25 : 20;
                cooldown = better ? 50 : 60;
            }
            default -> {
                return false;
            }
        }
        RUNNING.put(player.getUUID(), new Running(ability, now + seconds * 20L, sweep));
        COOLDOWNS.put(player.getUUID(), now + cooldown * 20L);
        Stats.refresh(player);
        player.sendSystemMessage(Component.literal("You used your ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(perk.name()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" Axe Ability!" + (sweep > 0 ? " +" + Msg.number(sweep) + " Sweep" : "")).withStyle(ChatFormatting.GREEN)));
        Msg.playSound(player, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.2f);
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (RUNNING.isEmpty() && COOLDOWNS.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (UUID id : List.copyOf(RUNNING.keySet())) {
            Running running = RUNNING.get(id);
            if (now < running.until()) {
                continue;
            }
            // removed first, so the refreshed stats drop Maniac Slicer's Sweep
            RUNNING.remove(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                HotfTree.Perk perk = HotfTree.get(running.ability());
                player.sendSystemMessage(Component.literal("Your " + (perk == null ? "axe ability" : perk.name()) + " has expired!")
                        .withStyle(ChatFormatting.RED));
                Stats.refresh(player);
            }
        }
        COOLDOWNS.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                HotfTree.Perk perk = HotfTree.get(state(player).ability());
                if (perk != null) {
                    player.sendSystemMessage(Component.literal(perk.name()).withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(" is now available!").withStyle(ChatFormatting.GREEN)));
                }
            }
            return true;
        });
    }
}
