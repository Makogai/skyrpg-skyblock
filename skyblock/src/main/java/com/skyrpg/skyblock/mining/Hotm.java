package com.skyrpg.skyblock.mining;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
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
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Heart of the Mountain: a mining progression of its own. HotM XP (from commissions and mining in the Dwarven
 * Mines) raises the HotM level (1 to 10), each level opens the next tier of the tree and gives Tokens of the Mountain;
 * a token unlocks a perk next to one you have, and perks level up with powder (Mithril, Gemstone, Glacite). Perks give
 * stats (Mining Speed, Mining Fortune) and effects; one unlocked Pickaxe Ability is selected and fires on right-click
 * with a pickaxe or drill. All of it is per profile (a player attachment).
 */
public final class Hotm {
    public static final String MITHRIL = "MITHRIL";
    public static final String GEMSTONE = "GEMSTONE";
    public static final String GLACITE = "GLACITE";
    public static final List<String> POWDERS = List.of(MITHRIL, GEMSTONE, GLACITE);
    /** Tokens of the Mountain each HotM level gives (index 0: level 1). */
    private static final int[] TOKENS = {1, 2, 2, 2, 2, 2, 3, 2, 2, 2};
    /** Core of the Mountain's extra tokens: +1 at levels 5 and 7, +2 at 10. */
    private static final int[] CORE_TOKENS = {0, 0, 0, 0, 0, 1, 1, 2, 2, 2, 4};
    private static final int ABILITY_COOLDOWN = 120 * 20;

    /** One profile's Heart of the Mountain. */
    public record State(long xp, Map<String, Integer> perks, List<String> disabled, Map<String, Long> powder, Map<String, Long> earned,
            String ability) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("xp", 0L).forGetter(State::xp),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("perks", Map.of()).forGetter(State::perks),
                Codec.STRING.listOf().optionalFieldOf("disabled", List.of()).forGetter(State::disabled),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("powder", Map.of()).forGetter(State::powder),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("earned", Map.of()).forGetter(State::earned),
                Codec.STRING.optionalFieldOf("ability", "").forGetter(State::ability)).apply(i, State::new));
        static final State EMPTY = new State(0, Map.of(), List.of(), Map.of(), Map.of(), "");

        public State {
            perks = Map.copyOf(perks);
            disabled = List.copyOf(disabled);
            powder = Map.copyOf(powder);
            earned = Map.copyOf(earned);
        }

        public int perk(String id) {
            return perks.getOrDefault(id, 0);
        }

        /** A perk's level if it's unlocked and switched on, else 0. */
        public int active(String id) {
            return disabled.contains(id) ? 0 : perk(id);
        }

        public long powder(String type) {
            return powder.getOrDefault(type, 0L);
        }

        State withPerk(String id, int level) {
            Map<String, Integer> more = new LinkedHashMap<>(perks);
            more.put(id, level);
            return new State(xp, more, disabled, powder, earned, ability);
        }

        State withPowder(String type, long delta, boolean earn) {
            Map<String, Long> have = new HashMap<>(powder);
            have.merge(type, delta, Long::sum);
            Map<String, Long> total = new HashMap<>(earned);
            if (earn && delta > 0) {
                total.merge(type, delta, Long::sum);
            }
            return new State(xp, perks, disabled, have, total, ability);
        }
    }

    public static final AttachmentType<State> STATE = AttachmentRegistry.create(SkyRpgSkyblock.id("hotm"),
            builder -> builder.initializer(() -> State.EMPTY).persistent(State.CODEC).copyOnDeath());

    /** Strong Arm's Mining Speed on Dwarven Metals (synced: the client works out breaking times too). */
    public static final AttachmentType<Integer> STRONG_ARM = AttachmentRegistry.create(SkyRpgSkyblock.id("strong_arm"),
            builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                    net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.targetOnly()));

    /** Running Mining Speed Boosts: the Mining Speed they add, until when. */
    private record Boost(double speed, long until) {
    }

    private static final Map<UUID, Boost> BOOSTS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

    private Hotm() {
    }

    public static void init() {
        HotmTree.init();
        HotmMenu.init();
        Forge.init();
        Commissions.init();
        MinesMobs.init();
        MinesGoblins.init();
        MiningEvents.init();
        DailyNpcs.init();
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Heart of the Mountain";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                if (!SkyBlockWorlds.isSkyBlock(player.level())) {
                    return;
                }
                State state = state(player);
                boolean peak = state.perk("core_of_the_mountain") >= 2;
                boolean island = SkyMining.miningIsland(player.level().dimension());
                boolean shaft = player.level().dimension().equals(SkyMining.GLACITE_MINESHAFTS);
                int strongArm = 0;
                for (HotmTree.Perk perk : HotmTree.all()) {
                    int level = state.active(perk.id());
                    if (level <= 0) {
                        continue;
                    }
                    double value = perk.values().containsKey("stat") ? perk.number("stat", level, peak) : 0;
                    switch (perk.id()) {
                        case "mining_speed" -> out.add(StatTypes.MINING_SPEED, value);
                        case "mining_fortune" -> out.add(StatTypes.MINING_FORTUNE, value);
                        // The Glacite Powder perks (tiers 8 to 10).
                        case "warm_heart" -> out.add(StatTypes.COLD_RESISTANCE, value);
                        case "metal_head" -> out.add(StatTypes.DWARVEN_METAL_FORTUNE, value);
                        case "mining_master" -> out.add(StatTypes.PRISTINE, value);
                        case "miners_blessing" -> {
                            if (island) {
                                out.add(StatTypes.MAGIC_FIND, 30);
                            }
                        }
                        case "rags_to_riches" -> {
                            if (shaft) {
                                out.add(StatTypes.MINING_FORTUNE, value);
                            }
                        }
                        case "eager_adventurer" -> {
                            if (shaft) {
                                out.add(StatTypes.MINING_SPEED, value);
                            }
                        }
                        case "strong_arm" -> strongArm = (int) value;
                        default -> {
                        }
                    }
                }
                if (player.getAttachedOrElse(STRONG_ARM, 0) != strongArm) {
                    player.setAttached(STRONG_ARM, strongArm);
                }
                Boost boost = BOOSTS.get(player.getUUID());
                if (boost != null) {
                    out.add(StatTypes.MINING_SPEED, boost.speed());
                }
            }
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(player instanceof ServerPlayer server) || !SkyBlockWorlds.isSkyBlock(level) || !isMiningTool(player.getItemInHand(hand))) {
                return InteractionResult.PASS;
            }
            return useAbility(server) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(Hotm::tick);
        Actions.register("hotm", (player, params) -> HotmMenu.open(player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal("hotm").executes(ctx -> {
                HotmMenu.open(ctx.getSource().getPlayerOrException());
                return 1;
            }));
            dispatcher.register(Commands.literal("sbhotm").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("xp").then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(ctx -> {
                                giveXp(EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "amount"));
                                return 1;
                            }))))
                    .then(Commands.literal("powder").then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("type", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(POWDERS.stream().map(p -> p.toLowerCase(Locale.ROOT)), builder))
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(ctx -> {
                                        givePowder(EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "type").toUpperCase(Locale.ROOT),
                                                IntegerArgumentType.getInteger(ctx, "amount"));
                                        return 1;
                                    })))))
                    .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        target.setAttached(STATE, State.EMPTY);
                        Stats.refresh(target);
                        return 1;
                    })))
                    // A perk at a level, free (for testing): /sbhotm perk <player> <perk> <level>.
                    .then(Commands.literal("perk").then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("perk", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(HotmTree.all().stream().map(HotmTree.Perk::id), builder))
                                    .then(Commands.argument("level", IntegerArgumentType.integer(0)).executes(ctx -> {
                                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                        HotmTree.Perk perk = HotmTree.get(StringArgumentType.getString(ctx, "perk"));
                                        if (perk == null) {
                                            return 0;
                                        }
                                        set(target, state(target).withPerk(perk.id(), Math.min(perk.max(), IntegerArgumentType.getInteger(ctx, "level"))));
                                        Stats.refresh(target);
                                        return 1;
                                    })))))
                    // A player's mining stats where they stand: /sbhotm stats <player>.
                    .then(Commands.literal("stats").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        StringBuilder out = new StringBuilder(target.getScoreboardName() + ":");
                        for (com.skyrpg.core.stats.StatType stat : List.of(StatTypes.MINING_SPEED, StatTypes.MINING_FORTUNE, StatTypes.DWARVEN_METAL_FORTUNE,
                                StatTypes.GEMSTONE_FORTUNE, StatTypes.PRISTINE, StatTypes.COLD_RESISTANCE, StatTypes.MAGIC_FIND)) {
                            out.append(" ").append(stat.name()).append(" ").append(Msg.number(Stats.get(target, stat))).append(",");
                        }
                        out.append(" Strong Arm ").append(target.getAttachedOrElse(STRONG_ARM, 0));
                        ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
                        return 1;
                    }))));
        });
    }

    // ---------------------------------------------------------------- state

    public static State state(ServerPlayer player) {
        return player.getAttachedOrElse(STATE, State.EMPTY);
    }

    /** A perk's number at the level the player has it switched on at (0 when it's off or not bought). */
    public static double perkNumber(ServerPlayer player, String id, String key) {
        State state = state(player);
        int level = state.active(id);
        HotmTree.Perk perk = HotmTree.get(id);
        return level <= 0 || perk == null ? 0 : perk.number(key, level, state.perk("core_of_the_mountain") >= 2);
    }

    private static void set(ServerPlayer player, State state) {
        player.setAttached(STATE, state);
        syncLeveling(player, state);
    }

    /** SkyBlock XP for each HotM tier reached (Hypixel's), from Tier 1. */
    private static final int[] TIER_SBXP = {35, 45, 60, 75, 90, 110, 130, 180, 210, 240};
    /** SkyBlock XP for each Peak of the Mountain (Core of the Mountain) level. */
    private static final int[] PEAK_SBXP = {25, 35, 50, 65, 75, 100, 125, 150, 175, 200};

    /**
     * The SkyBlock Leveling tasks the mountain pays: its tiers, the Peak, and powder earned (+1 per 2,400 Mithril or
     * 2,500 Gemstone or Glacite Powder up to 600,000 of each, then less and less up to Hypixel's 1,942 in all).
     * Decided: past 600,000 the rest of each powder's share comes in as 1 - e^(-extra / scale), the scale such that it's
     * all but full at Hypixel's 12.5M Mithril / 20M Gemstone and Glacite.
     */
    public static void syncLeveling(ServerPlayer player) {
        syncLeveling(player, state(player));
    }

    private static void syncLeveling(ServerPlayer player, State state) {
        int tier = level(state.xp());
        int tiers = 0;
        for (int i = 0; i < Math.min(tier, TIER_SBXP.length); i++) {
            tiers += TIER_SBXP[i];
        }
        com.skyrpg.skyblock.leveling.LevelingTasks.sync(player, "hotm", tiers);
        int peak = 0;
        for (int i = 0; i < Math.min(state.perk("core_of_the_mountain"), PEAK_SBXP.length); i++) {
            peak += PEAK_SBXP[i];
        }
        com.skyrpg.skyblock.leveling.LevelingTasks.sync(player, "peak_of_the_mountain", peak);
        int powder = powderXp(state.earned().getOrDefault(MITHRIL, 0L), 2400, 397, 12_500_000)
                + powderXp(state.earned().getOrDefault(GEMSTONE, 0L), 2500, 408, 20_000_000)
                + powderXp(state.earned().getOrDefault(GLACITE, 0L), 2500, 408, 20_000_000);
        com.skyrpg.skyblock.leveling.LevelingTasks.sync(player, "powder", powder);
    }

    private static int powderXp(long earned, int per, int beyond, long full) {
        long cap = 600_000;
        int xp = (int) (Math.min(earned, cap) / per);
        if (earned > cap) {
            double scale = (full - cap) / 4.6;
            xp += (int) Math.floor(beyond * (1 - Math.exp(-(earned - cap) / scale)));
        }
        return xp;
    }

    /** The HotM level (1 to 10) for this much XP. */
    public static int level(long xp) {
        int level = 1;
        while (level < HotmTree.maxLevel() && xp >= HotmTree.totalFor(level + 1)) {
            level++;
        }
        return level;
    }

    public static int level(ServerPlayer player) {
        return level(state(player).xp());
    }

    /** All the Tokens of the Mountain this profile has earned (HotM levels and Core of the Mountain). */
    public static int tokens(State state) {
        int level = level(state.xp());
        int total = 0;
        for (int i = 0; i < level && i < TOKENS.length; i++) {
            total += TOKENS[i];
        }
        return total + CORE_TOKENS[Math.min(CORE_TOKENS.length - 1, state.perk("core_of_the_mountain"))];
    }

    /** Tokens spent: one per unlocked perk. */
    public static int tokensSpent(State state) {
        return (int) state.perks().values().stream().filter(level -> level > 0).count();
    }

    /** Whether a perk can be unlocked now: its tier is open and it's next to one already unlocked (or it's the first). */
    public static boolean unlockable(State state, HotmTree.Perk perk) {
        if (state.perk(perk.id()) > 0 || perk.tier() > level(state.xp())) {
            return false;
        }
        if (perk.tier() == 1) {
            return true;
        }
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            HotmTree.Perk next = HotmTree.at(perk.x() + d[0], perk.y() + d[1]);
            if (next != null && state.perk(next.id()) > 0) {
                return true;
            }
        }
        return false;
    }

    /** What a click on a perk does: unlock it with a token, upgrade it with powder, or select it (abilities). */
    static String click(ServerPlayer player, String id, int times) {
        HotmTree.Perk perk = HotmTree.get(id);
        if (perk == null) {
            return "";
        }
        State state = state(player);
        int level = state.perk(id);
        if (level == 0) {
            if (!unlockable(state, perk)) {
                return perk.tier() > level(state.xp()) ? "Reach HotM " + perk.tier() + " to unlock this!" : "Unlock a perk next to it first!";
            }
            if (tokens(state) - tokensSpent(state) < 1) {
                return "You don't have a Token of the Mountain!";
            }
            state = state.withPerk(id, 1);
            if (perk.ability() && state.ability().isEmpty()) {
                state = new State(state.xp(), state.perks(), state.disabled(), state.powder(), state.earned(), id);
            }
            set(player, state);
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.4f);
            player.sendSystemMessage(Notice.build(Notice.Kind.MINING, Component.literal("Unlocked ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(perk.name()).withStyle(ChatFormatting.GREEN)).append(Component.literal("!").withStyle(ChatFormatting.GRAY))));
            Stats.refresh(player);
            return "";
        }
        if (perk.ability()) {
            set(player, new State(state.xp(), state.perks(), state.disabled(), state.powder(), state.earned(), id));
            player.sendOverlayMessage(Component.literal("Selected ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(perk.name()).withStyle(ChatFormatting.GOLD)));
            Msg.playSound(player, SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.2f);
            return "";
        }
        int done = 0;
        for (int i = 0; i < times && level < perk.max(); i++) {
            String type = perk.powderAt(level);
            int cost = perk.costAt(level);
            if (state.powder(type) < cost) {
                break;
            }
            state = state.withPowder(type, -cost, false).withPerk(id, level + 1);
            level++;
            done++;
        }
        if (done == 0) {
            return level >= perk.max() ? "This perk is maxed!" : "You don't have enough " + powderName(perk.powderAt(level)) + "!";
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
        set(player, new State(state.xp(), state.perks(), disabled, state.powder(), state.earned(), state.ability()));
        Stats.refresh(player);
    }

    // ---------------------------------------------------------------- XP and powder

    public static void giveXp(ServerPlayer player, long amount) {
        if (amount <= 0) {
            return;
        }
        State state = state(player);
        int before = level(state.xp());
        state = new State(state.xp() + amount, state.perks(), state.disabled(), state.powder(), state.earned(), state.ability());
        set(player, state);
        int after = level(state.xp());
        for (int level = before + 1; level <= after; level++) {
            levelUp(player, level);
        }
    }

    private static void levelUp(ServerPlayer player, int level) {
        Notice.Panel panel = Notice.of(Notice.Kind.MINING).tag("HEART OF THE MOUNTAIN").panel();
        panel.heading("Rewards", 0x55FF55);
        int tokens = level - 1 < TOKENS.length ? TOKENS[level - 1] : 0;
        panel.line(Component.literal("+" + tokens + " Token" + (tokens == 1 ? "" : "s") + " of the Mountain").withStyle(ChatFormatting.DARK_PURPLE));
        panel.line(Component.literal("Unlocked Tier " + Msg.roman(level) + " perks").withStyle(ChatFormatting.GRAY));
        panel.send(player, Component.literal("Tier " + Msg.roman(level)).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    public static void givePowder(ServerPlayer player, String type, long amount) {
        if (amount <= 0 || !POWDERS.contains(type)) {
            return;
        }
        // 2x Powder (a Dwarven Mines event) doubles every gain on that island.
        if (MiningEvents.doublePowder() && player.level().dimension().equals(SkyMining.DWARVEN_MINES)) {
            amount *= 2;
        }
        set(player, state(player).withPowder(type, amount, true));
    }

    public static String powderName(String type) {
        return switch (type) {
            case GEMSTONE -> "Gemstone Powder";
            case GLACITE -> "Glacite Powder";
            default -> "Mithril Powder";
        };
    }

    public static int powderColor(String type) {
        return switch (type) {
            case GEMSTONE -> 0xFF55FF;
            case GLACITE -> 0x55FFFF;
            default -> 0x00AA00;
        };
    }

    // ---------------------------------------------------------------- pickaxe abilities

    /** A pickaxe, drill or gauntlet (Hypixel's tools that use the Pickaxe Ability). */
    public static boolean isMiningTool(ItemStack stack) {
        ItemDefinition def = SkyItems.definition(stack);
        if (def == null || def.json() == null || !def.json().has("category")) {
            return stack.is(net.minecraft.tags.ItemTags.PICKAXES);
        }
        String category = def.json().get("category").getAsString();
        return category.equals("PICKAXE") || category.equals("DRILL") || category.equals("GAUNTLET");
    }

    private static boolean useAbility(ServerPlayer player) {
        State state = state(player);
        String ability = state.ability();
        if (ability.isEmpty() || state.active(ability) <= 0) {
            return false;
        }
        HotmTree.Perk perk = HotmTree.get(ability);
        long now = player.level().getServer().getTickCount();
        Long ready = COOLDOWNS.get(player.getUUID());
        if (ready != null && now < ready) {
            player.sendOverlayMessage(Component.literal("Your pickaxe ability is on cooldown for " + ((ready - now) / 20 + 1) + "s.")
                    .withStyle(ChatFormatting.RED));
            return true;
        }
        boolean peak = state.perk("core_of_the_mountain") >= 2;
        if (!ability.equals("mining_speed_boost")) {
            player.sendOverlayMessage(Component.literal(perk.name() + " isn't ready yet.").withStyle(ChatFormatting.RED));
            return true;
        }
        double percent = perk.number("boost", 1, peak);
        int seconds = (int) perk.number("duration", 1, peak);
        double base = Stats.get(player, StatTypes.MINING_SPEED);
        BOOSTS.put(player.getUUID(), new Boost(base * percent / 100, now + seconds * 20L));
        // Mineshaft Mayhem can take a quarter off the cooldown.
        COOLDOWNS.put(player.getUUID(), now + Math.round(ABILITY_COOLDOWN * com.skyrpg.skyblock.glacite.Mineshafts.cooldownFactor(player)));
        Stats.refresh(player);
        player.sendSystemMessage(Component.literal("You used your ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(perk.name()).withStyle(ChatFormatting.GOLD))
                .append(Component.literal(" Pickaxe Ability!").withStyle(ChatFormatting.GREEN)));
        Msg.playSound(player, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (BOOSTS.isEmpty() && COOLDOWNS.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        BOOSTS.entrySet().removeIf(entry -> {
            if (now < entry.getValue().until()) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                Stats.refresh(player);
                player.sendSystemMessage(Component.literal("Your Mining Speed Boost has expired!").withStyle(ChatFormatting.RED));
            }
            return true;
        });
        // Refreshed after removal so the stats drop the boost.
        COOLDOWNS.entrySet().removeIf(entry -> {
            if (now < entry.getValue()) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                HotmTree.Perk perk = HotmTree.get(state(player).ability());
                if (perk != null) {
                    player.sendSystemMessage(Component.literal(perk.name()).withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(" is now available!").withStyle(ChatFormatting.GREEN)));
                }
            }
            return true;
        });
    }
}
