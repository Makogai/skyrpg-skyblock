package com.skyrpg.skyblock.mining;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hypixel's Commissions: a board of tasks, claimed for HotM XP, powder and Mining XP from the King or an Emissary. The
 * Dwarven Mines and the Crystal Hollows have boards of their own (opened where you are): in the Mines, Mithril and
 * Titanium (anywhere or in one area), Goblins, Glacite Walkers and Treasure Hoarder hits; in the Hollows, Hard Stone,
 * each gemstone, treasure chests and the Hollows' mobs. The first four claims each day also give 900 HotM XP. Two slots,
 * a third after 100 commissions, a fourth from Core of the Mountain 3.
 */
public final class Commissions {
    /** What a commission counts. */
    enum Goal {
        MITHRIL, TITANIUM, GOBLINS, GLACITE_WALKERS, HOARDER_HITS, HARD_STONE, GEMS, CHESTS, AUTOMATONS, SLUDGES, YOGS, THYSTS, TREASURITE
    }

    /** A commission: its name, what it wants and how many, and a zone ("" = anywhere) or, for gems, the gemstone. */
    public record Task(String id, String name, Goal goal, int amount, String zone) {
        String objective() {
            String where = zone.isEmpty() ? "" : " in " + zone;
            return switch (goal) {
                case MITHRIL -> "Mine " + amount + " Mithril" + where;
                case TITANIUM -> "Mine " + amount + " Titanium" + where;
                case GOBLINS -> "Slay " + amount + " Goblins";
                case GLACITE_WALKERS -> "Slay " + amount + " Glacite Walkers";
                case HOARDER_HITS -> "Damage Treasure Hoarders " + amount + " times";
                case HARD_STONE -> "Mine " + amount + " Hard Stone";
                case GEMS -> "Mine " + amount + " " + zone + " Gemstones";
                case CHESTS -> "Open " + amount + " Treasure Chests";
                case AUTOMATONS -> "Slay " + amount + " Automatons";
                case SLUDGES -> "Slay " + amount + " Sludges";
                case YOGS -> "Slay " + amount + " Yogs";
                case THYSTS -> "Slay " + amount + " Thysts";
                case TREASURITE -> "Slay " + amount + " Team Treasurite members";
            };
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> HOLLOWS_TASKS = new ArrayList<>();

    static {
        TASKS.add(new Task("mithril_miner", "Mithril Miner", Goal.MITHRIL, 350, ""));
        TASKS.add(new Task("titanium_miner", "Titanium Miner", Goal.TITANIUM, 15, ""));
        for (String zone : List.of("Lava Springs", "Cliffside Veins", "Rampart's Quarry", "Upper Mines", "Royal Mines")) {
            String key = zone.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]+", "_");
            TASKS.add(new Task(key + "_mithril", zone + " Mithril", Goal.MITHRIL, 250, zone));
            TASKS.add(new Task(key + "_titanium", zone + " Titanium", Goal.TITANIUM, 10, zone));
        }
        TASKS.add(new Task("goblin_slayer", "Goblin Slayer", Goal.GOBLINS, 100, ""));
        TASKS.add(new Task("glacite_walker_slayer", "Glacite Walker Slayer", Goal.GLACITE_WALKERS, 50, ""));
        TASKS.add(new Task("treasure_hoarder_puncher", "Treasure Hoarder Puncher", Goal.HOARDER_HITS, 10, ""));
        HOLLOWS_TASKS.add(new Task("hard_stone_miner", "Hard Stone Miner", Goal.HARD_STONE, 1000, ""));
        for (String gem : List.of("Ruby", "Amethyst", "Jade", "Amber", "Sapphire", "Topaz")) {
            HOLLOWS_TASKS.add(new Task(gem.toLowerCase(java.util.Locale.ROOT) + "_collector", gem + " Gemstone Collector", Goal.GEMS, 100, gem));
        }
        HOLLOWS_TASKS.add(new Task("chest_looter", "Chest Looter", Goal.CHESTS, 3, ""));
        HOLLOWS_TASKS.add(new Task("hollows_goblin_slayer", "Goblin Slayer", Goal.GOBLINS, 13, ""));
        HOLLOWS_TASKS.add(new Task("automaton_slayer", "Automaton Slayer", Goal.AUTOMATONS, 13, ""));
        HOLLOWS_TASKS.add(new Task("sludge_slayer", "Sludge Slayer", Goal.SLUDGES, 25, ""));
        HOLLOWS_TASKS.add(new Task("yog_slayer", "Yog Slayer", Goal.YOGS, 13, ""));
        HOLLOWS_TASKS.add(new Task("thyst_slayer", "Thyst Slayer", Goal.THYSTS, 5, ""));
        HOLLOWS_TASKS.add(new Task("treasurite_slayer", "Team Treasurite Member Slayer", Goal.TREASURITE, 13, ""));
    }

    /** One slot: which commission and how far along. */
    public record Slot(String task, int progress) {
        static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("task").forGetter(Slot::task),
                Codec.INT.optionalFieldOf("progress", 0).forGetter(Slot::progress)).apply(i, Slot::new));
    }

    /** A board, commissions done on it in all, and today's (for the daily bonus). */
    public record Board(List<Slot> slots, int completed, String day, int today) {
        static final Codec<Board> CODEC = RecordCodecBuilder.create(i -> i.group(
                Slot.CODEC.listOf().optionalFieldOf("slots", List.of()).forGetter(Board::slots),
                Codec.INT.optionalFieldOf("completed", 0).forGetter(Board::completed),
                Codec.STRING.optionalFieldOf("day", "").forGetter(Board::day),
                Codec.INT.optionalFieldOf("today", 0).forGetter(Board::today)).apply(i, Board::new));
        static final Board EMPTY = new Board(List.of(), 0, "", 0);

        public Board {
            slots = List.copyOf(slots);
        }

        Board withSlots(List<Slot> now) {
            return new Board(now, completed, day, today);
        }
    }

    public static final AttachmentType<Board> BOARD = AttachmentRegistry.create(com.skyrpg.skyblock.SkyRpgSkyblock.id("commissions"),
            builder -> builder.initializer(() -> Board.EMPTY).persistent(Board.CODEC).copyOnDeath());
    public static final AttachmentType<Board> HOLLOWS_BOARD = AttachmentRegistry.create(
            com.skyrpg.skyblock.SkyRpgSkyblock.id("hollows_commissions"),
            builder -> builder.initializer(() -> Board.EMPTY).persistent(Board.CODEC).copyOnDeath());
    /** HotM XP for a claimed Dwarven Mines commission at HotM 1, 2 and 3+ (the Hollows' are always 400). */
    private static final int[] HOTM_XP = {100, 200, 400};
    private static final int HOLLOWS_HOTM_XP = 400;
    private static final int POWDER = 500;
    private static final int HOLLOWS_POWDER = 1500;
    private static final int MINING_XP = 5000;
    private static final int HOLLOWS_MINING_XP = 10000;
    private static final int DAILY = 4;
    private static final int DAILY_XP = 900;

    private Commissions() {
    }

    static void init() {
        Actions.register("commissions", (player, params) -> CommissionsMenu.open(player));
        SkyMining.MINED.register((player, state, zone) -> {
            if (inMines(player)) {
                Goal goal = isTitanium(state) ? Goal.TITANIUM : isMithril(state) ? Goal.MITHRIL : null;
                if (goal != null) {
                    progress(player, false, task -> task.goal() == goal && (task.zone().isEmpty() || task.zone().equals(zone)));
                }
            } else if (inHollows(player)) {
                String gem = gem(state);
                if (state.is(Blocks.STONE)) {
                    progress(player, true, task -> task.goal() == Goal.HARD_STONE);
                } else if (gem != null) {
                    progress(player, true, task -> task.goal() == Goal.GEMS && task.zone().equals(gem));
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            String name = name(entity);
            if (inMines(player)) {
                Goal goal = switch (name) {
                    case "Goblin", "Knifethrower", "Fireslinger", "Weakling", "Golden Goblin" -> Goal.GOBLINS;
                    case "Glacite Walker" -> Goal.GLACITE_WALKERS;
                    default -> null;
                };
                if (goal != null) {
                    progress(player, false, task -> task.goal() == goal);
                }
            } else if (inHollows(player)) {
                Goal goal = switch (name) {
                    case "Goblin", "Knifethrower", "Weakling" -> Goal.GOBLINS;
                    case "Automaton" -> Goal.AUTOMATONS;
                    case "Sludge" -> Goal.SLUDGES;
                    case "Yog" -> Goal.YOGS;
                    case "Thyst" -> Goal.THYSTS;
                    case "Grunt" -> Goal.TREASURITE;
                    default -> null;
                };
                if (goal != null) {
                    progress(player, true, task -> task.goal() == goal);
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (source.getEntity() instanceof ServerPlayer player && "Treasure Hoarder".equals(name(entity))) {
                progress(player, false, task -> task.goal() == Goal.HOARDER_HITS);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(Commands.literal("sbcommission").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("open").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            CommissionsMenu.open(EntityArgument.getPlayer(ctx, "player"));
                            return 1;
                        })))
                        .then(Commands.literal("complete").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            boolean hollows = inHollows(target);
                            Board board = board(target, hollows);
                            List<Slot> slots = new ArrayList<>();
                            board.slots().forEach(slot -> slots.add(new Slot(slot.task(), task(slot.task()).amount())));
                            target.setAttached(attachment(hollows), board.withSlots(slots));
                            return 1;
                        })))
                        .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("task", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                java.util.stream.Stream.concat(TASKS.stream(), HOLLOWS_TASKS.stream()).map(Task::id), builder))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            Task task = task(StringArgumentType.getString(ctx, "task"));
                                            if (task == null) {
                                                return 0;
                                            }
                                            boolean hollows = HOLLOWS_TASKS.contains(task);
                                            Board board = board(target, hollows);
                                            List<Slot> slots = new ArrayList<>(board.slots());
                                            slots.set(0, new Slot(task.id(), 0));
                                            target.setAttached(attachment(hollows), board.withSlots(slots));
                                            return 1;
                                        }))))));
    }

    static boolean inMines(ServerPlayer player) {
        return player.level().dimension().equals(SkyMining.DWARVEN_MINES);
    }

    static boolean inHollows(ServerPlayer player) {
        return player.level().dimension().equals(SkyMining.CRYSTAL_HOLLOWS);
    }

    private static AttachmentType<Board> attachment(boolean hollows) {
        return hollows ? HOLLOWS_BOARD : BOARD;
    }

    private static String name(LivingEntity entity) {
        return entity.getCustomName() == null ? "" : entity.getCustomName().getString();
    }

    static boolean isMithril(BlockState state) {
        return state.is(Blocks.WOOL.pick(DyeColor.GRAY)) || state.is(Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN))
                || state.is(Blocks.PRISMARINE) || state.is(Blocks.PRISMARINE_BRICKS) || state.is(Blocks.DARK_PRISMARINE)
                || state.is(Blocks.WOOL.pick(DyeColor.LIGHT_BLUE));
    }

    static boolean isTitanium(BlockState state) {
        return state.is(Blocks.POLISHED_DIORITE);
    }

    /** The gemstone a block is ("Ruby"), or null. */
    static String gem(BlockState state) {
        String[][] gems = {{"RED", "Ruby"}, {"PURPLE", "Amethyst"}, {"LIME", "Jade"}, {"ORANGE", "Amber"}, {"LIGHT_BLUE", "Sapphire"},
                {"YELLOW", "Topaz"}, {"MAGENTA", "Jasper"}};
        for (String[] gem : gems) {
            DyeColor color = DyeColor.valueOf(gem[0]);
            if (state.is(Blocks.STAINED_GLASS.pick(color)) || state.is(Blocks.STAINED_GLASS_PANE.pick(color))) {
                return gem[1];
            }
        }
        return null;
    }

    public static Task task(String id) {
        for (Task task : TASKS) {
            if (task.id().equals(id)) {
                return task;
            }
        }
        for (Task task : HOLLOWS_TASKS) {
            if (task.id().equals(id)) {
                return task;
            }
        }
        return null;
    }

    /** How many slots a board has. */
    public static int slotCount(ServerPlayer player, boolean hollows) {
        Board board = player.getAttachedOrElse(attachment(hollows), Board.EMPTY);
        return 2 + (board.completed() >= 100 ? 1 : 0) + (Hotm.state(player).perk("core_of_the_mountain") >= 3 ? 1 : 0);
    }

    /** The Dwarven Mines board, filled up. */
    public static Board board(ServerPlayer player) {
        return board(player, false);
    }

    /** A board, filled up to its slots with new commissions. */
    public static Board board(ServerPlayer player, boolean hollows) {
        Board board = player.getAttachedOrElse(attachment(hollows), Board.EMPTY);
        int want = slotCount(player, hollows);
        if (board.slots().size() >= want) {
            return board;
        }
        List<Slot> slots = new ArrayList<>(board.slots());
        while (slots.size() < want) {
            slots.add(new Slot(pick(slots, hollows ? HOLLOWS_TASKS : TASKS, player.getRandom()).id(), 0));
        }
        Board filled = board.withSlots(slots);
        player.setAttached(attachment(hollows), filled);
        return filled;
    }

    /** A random commission not already on the board. */
    private static Task pick(List<Slot> taken, List<Task> from, RandomSource random) {
        List<Task> open = new ArrayList<>();
        for (Task task : from) {
            if (taken.stream().noneMatch(slot -> slot.task().equals(task.id()))) {
                open.add(task);
            }
        }
        return open.isEmpty() ? from.getFirst() : open.get(random.nextInt(open.size()));
    }

    /** A treasure chest was opened in the Hollows. */
    public static void chestOpened(ServerPlayer player) {
        progress(player, true, task -> task.goal() == Goal.CHESTS);
    }

    private static void progress(ServerPlayer player, boolean hollows, Predicate<Task> counts) {
        Board board = board(player, hollows);
        List<Slot> slots = new ArrayList<>(board.slots());
        boolean changed = false;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            Task task = task(slot.task());
            if (task == null || slot.progress() >= task.amount() || !counts.test(task)) {
                continue;
            }
            int now = slot.progress() + 1;
            slots.set(i, new Slot(slot.task(), now));
            changed = true;
            if (now >= task.amount()) {
                String where = hollows ? "Emissary Sisko" : "the King or an Emissary";
                Notice.of(Notice.Kind.MINING).tag("COMMISSION COMPLETE").send(player, Component.literal(task.name()).withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(" - visit " + where + " to claim your rewards!").withStyle(ChatFormatting.GRAY)));
                Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.6f);
            } else if (now % Math.max(1, task.amount() / 4) == 0) {
                player.sendOverlayMessage(Component.literal(task.name() + " ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(now + "/" + task.amount()).withStyle(ChatFormatting.YELLOW)));
            }
        }
        if (changed) {
            player.setAttached(attachment(hollows), board.withSlots(slots));
        }
    }

    /** Claims a finished commission in a slot: rewards, and a new commission. */
    static String claim(ServerPlayer player, boolean hollows, int index) {
        Board board = board(player, hollows);
        if (index < 0 || index >= board.slots().size()) {
            return "";
        }
        Slot slot = board.slots().get(index);
        Task task = task(slot.task());
        if (task == null || slot.progress() < task.amount()) {
            return "That commission isn't done yet!";
        }
        // The daily bonus counts both boards together.
        Board mines = player.getAttachedOrElse(BOARD, Board.EMPTY);
        String day = LocalDate.now(ZoneOffset.UTC).toString();
        int today = day.equals(mines.day()) ? mines.today() : 0;
        int level = Hotm.level(player);
        int xp = hollows ? HOLLOWS_HOTM_XP : HOTM_XP[Math.min(HOTM_XP.length - 1, Math.max(0, level - 1))];
        boolean bonus = today < DAILY;
        int powder = hollows ? HOLLOWS_POWDER : POWDER;
        String powderType = hollows ? Hotm.GEMSTONE : Hotm.MITHRIL;
        int miningXp = hollows ? HOLLOWS_MINING_XP : MINING_XP;
        Hotm.giveXp(player, xp + (bonus ? DAILY_XP : 0));
        Hotm.givePowder(player, powderType, powder);
        SkillLevels.giveXp(player, SkillLevels.MINING, miningXp);
        List<Slot> slots = new ArrayList<>(board.slots());
        slots.set(index, new Slot(pick(slots, hollows ? HOLLOWS_TASKS : TASKS, player.getRandom()).id(), 0));
        player.setAttached(attachment(hollows), new Board(slots, board.completed() + 1, board.day(), board.today()));
        Board daily = player.getAttachedOrElse(BOARD, Board.EMPTY);
        player.setAttached(BOARD, new Board(daily.slots(), daily.completed(), day, today + 1));
        Notice.Panel panel = Notice.of(Notice.Kind.MINING).tag("COMMISSION").panel();
        panel.heading("Rewards", 0x55FF55);
        panel.line(Component.literal("+" + Msg.number(xp) + " HotM Experience").withStyle(ChatFormatting.DARK_PURPLE));
        if (bonus) {
            panel.line(Component.literal("+" + Msg.number(DAILY_XP) + " HotM Experience (daily bonus " + (today + 1) + "/" + DAILY + ")")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        panel.line(Component.literal("+" + Msg.number(powder) + " " + Hotm.powderName(powderType)).withColor(Hotm.powderColor(powderType)));
        panel.line(Component.literal("+" + Msg.number(miningXp) + " Mining Experience").withStyle(ChatFormatting.DARK_AQUA));
        panel.send(player, Component.literal(task.name()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
        if (board.completed() + 1 == 100) {
            player.sendSystemMessage(Notice.build(Notice.Kind.MINING, Component.literal("100 commissions! This board has a third slot.")
                    .withStyle(ChatFormatting.GOLD)));
        }
        return "";
    }
}
