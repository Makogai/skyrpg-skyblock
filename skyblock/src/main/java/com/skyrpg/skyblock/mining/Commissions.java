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
import java.util.function.BiPredicate;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hypixel's Dwarven Mines Commissions: a board of tasks (mine Mithril or Titanium, anywhere or in one area; slay Goblins
 * or Glacite Walkers; punch Treasure Hoarders). Finish one and claim it from the King or an Emissary for HotM XP,
 * Mithril Powder and Mining XP (the first four each day give 900 HotM XP more); a new one takes its place. Two slots, a
 * third after 100 commissions, a fourth from Core of the Mountain 3.
 */
public final class Commissions {
    /** What a commission counts. */
    enum Goal {
        MITHRIL, TITANIUM, GOBLINS, GLACITE_WALKERS, HOARDER_HITS
    }

    /** A commission: its name, what it wants and how many, in which zone ("" = anywhere in the Mines). */
    public record Task(String id, String name, Goal goal, int amount, String zone) {
        String objective() {
            String where = zone.isEmpty() ? "" : " in " + zone;
            return switch (goal) {
                case MITHRIL -> "Mine " + amount + " Mithril" + where;
                case TITANIUM -> "Mine " + amount + " Titanium" + where;
                case GOBLINS -> "Slay " + amount + " Goblins";
                case GLACITE_WALKERS -> "Slay " + amount + " Glacite Walkers";
                case HOARDER_HITS -> "Damage Treasure Hoarders " + amount + " times";
            };
        }
    }

    private static final List<Task> TASKS = new ArrayList<>();

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
    }

    /** One slot: which commission and how far along. */
    public record Slot(String task, int progress) {
        static final Codec<Slot> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("task").forGetter(Slot::task),
                Codec.INT.optionalFieldOf("progress", 0).forGetter(Slot::progress)).apply(i, Slot::new));
    }

    /** The board, commissions done in all, and today's (for the daily bonus). */
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
    }

    public static final AttachmentType<Board> BOARD = AttachmentRegistry.create(com.skyrpg.skyblock.SkyRpgSkyblock.id("commissions"),
            builder -> builder.initializer(() -> Board.EMPTY).persistent(Board.CODEC).copyOnDeath());
    /** HotM XP for a claimed commission at HotM 1, 2 and 3+. */
    private static final int[] HOTM_XP = {100, 200, 400};
    private static final int POWDER = 500;
    private static final int MINING_XP = 5000;
    private static final int DAILY = 4;
    private static final int DAILY_XP = 900;

    private Commissions() {
    }

    static void init() {
        Actions.register("commissions", (player, params) -> CommissionsMenu.open(player));
        SkyMining.MINED.register((player, state, zone) -> {
            if (!player.level().dimension().equals(SkyMining.DWARVEN_MINES)) {
                return;
            }
            Goal goal = isTitanium(state) ? Goal.TITANIUM : isMithril(state) ? Goal.MITHRIL : null;
            if (goal != null) {
                progress(player, (task, z) -> task.goal() == goal && (task.zone().isEmpty() || task.zone().equals(zone)), zone);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer player && player.level().dimension().equals(SkyMining.DWARVEN_MINES)) {
                String name = name(entity);
                Goal goal = switch (name) {
                    case "Goblin", "Knifethrower", "Fireslinger", "Weakling", "Golden Goblin" -> Goal.GOBLINS;
                    case "Glacite Walker" -> Goal.GLACITE_WALKERS;
                    default -> null;
                };
                if (goal != null) {
                    progress(player, (task, z) -> task.goal() == goal, "");
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (source.getEntity() instanceof ServerPlayer player && "Treasure Hoarder".equals(name(entity))) {
                progress(player, (task, z) -> task.goal() == Goal.HOARDER_HITS, "");
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
                            Board board = board(target);
                            List<Slot> slots = new ArrayList<>();
                            board.slots().forEach(slot -> slots.add(new Slot(slot.task(), task(slot.task()).amount())));
                            target.setAttached(BOARD, new Board(slots, board.completed(), board.day(), board.today()));
                            return 1;
                        })))
                        .then(Commands.literal("set").then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("task", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TASKS.stream().map(Task::id), builder))
                                        .executes(ctx -> {
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            Task task = task(StringArgumentType.getString(ctx, "task"));
                                            if (task == null) {
                                                return 0;
                                            }
                                            Board board = board(target);
                                            List<Slot> slots = new ArrayList<>(board.slots());
                                            if (slots.isEmpty()) {
                                                slots.add(new Slot(task.id(), 0));
                                            } else {
                                                slots.set(0, new Slot(task.id(), 0));
                                            }
                                            target.setAttached(BOARD, new Board(slots, board.completed(), board.day(), board.today()));
                                            return 1;
                                        }))))));
    }

    private static String name(LivingEntity entity) {
        return entity.getCustomName() == null ? "" : entity.getCustomName().getString();
    }

    static boolean isMithril(BlockState state) {
        return state.is(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.GRAY)) || state.is(Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.CYAN))
                || state.is(Blocks.PRISMARINE) || state.is(Blocks.PRISMARINE_BRICKS) || state.is(Blocks.DARK_PRISMARINE)
                || state.is(Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.LIGHT_BLUE));
    }

    static boolean isTitanium(BlockState state) {
        return state.is(Blocks.POLISHED_DIORITE);
    }

    public static Task task(String id) {
        for (Task task : TASKS) {
            if (task.id().equals(id)) {
                return task;
            }
        }
        return null;
    }

    /** How many slots this profile's board has. */
    public static int slotCount(ServerPlayer player) {
        Board board = player.getAttachedOrElse(BOARD, Board.EMPTY);
        return 2 + (board.completed() >= 100 ? 1 : 0) + (Hotm.state(player).perk("core_of_the_mountain") >= 3 ? 1 : 0);
    }

    /** The board, filled up to its slots with new commissions. */
    public static Board board(ServerPlayer player) {
        Board board = player.getAttachedOrElse(BOARD, Board.EMPTY);
        int want = slotCount(player);
        if (board.slots().size() >= want) {
            return board;
        }
        List<Slot> slots = new ArrayList<>(board.slots());
        while (slots.size() < want) {
            slots.add(new Slot(pick(slots, player.getRandom()).id(), 0));
        }
        Board filled = new Board(slots, board.completed(), board.day(), board.today());
        player.setAttached(BOARD, filled);
        return filled;
    }

    /** A random commission not already on the board. */
    private static Task pick(List<Slot> taken, RandomSource random) {
        List<Task> open = new ArrayList<>();
        for (Task task : TASKS) {
            if (taken.stream().noneMatch(slot -> slot.task().equals(task.id()))) {
                open.add(task);
            }
        }
        return open.isEmpty() ? TASKS.getFirst() : open.get(random.nextInt(open.size()));
    }

    private static void progress(ServerPlayer player, BiPredicate<Task, String> counts, String zone) {
        Board board = board(player);
        List<Slot> slots = new ArrayList<>(board.slots());
        boolean changed = false;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            Task task = task(slot.task());
            if (task == null || slot.progress() >= task.amount() || !counts.test(task, zone)) {
                continue;
            }
            int now = slot.progress() + 1;
            slots.set(i, new Slot(slot.task(), now));
            changed = true;
            if (now >= task.amount()) {
                Notice.of(Notice.Kind.MINING).tag("COMMISSION COMPLETE").send(player, Component.literal(task.name()).withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(" - visit the King or an Emissary to claim your rewards!").withStyle(ChatFormatting.GRAY)));
                Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.6f);
            } else if (now % Math.max(1, task.amount() / 4) == 0) {
                player.sendOverlayMessage(Component.literal(task.name() + " ").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(now + "/" + task.amount()).withStyle(ChatFormatting.YELLOW)));
            }
        }
        if (changed) {
            player.setAttached(BOARD, new Board(slots, board.completed(), board.day(), board.today()));
        }
    }

    /** Claims a finished commission in a slot (at the King or an Emissary): rewards, and a new commission. */
    static String claim(ServerPlayer player, int index) {
        Board board = board(player);
        if (index < 0 || index >= board.slots().size()) {
            return "";
        }
        Slot slot = board.slots().get(index);
        Task task = task(slot.task());
        if (task == null || slot.progress() < task.amount()) {
            return "That commission isn't done yet!";
        }
        String day = LocalDate.now(ZoneOffset.UTC).toString();
        int today = day.equals(board.day()) ? board.today() : 0;
        int level = Hotm.level(player);
        int xp = HOTM_XP[Math.min(HOTM_XP.length - 1, Math.max(0, level - 1))];
        boolean bonus = today < DAILY;
        Hotm.giveXp(player, xp + (bonus ? DAILY_XP : 0));
        Hotm.givePowder(player, Hotm.MITHRIL, POWDER);
        SkillLevels.giveXp(player, SkillLevels.MINING, MINING_XP);
        List<Slot> slots = new ArrayList<>(board.slots());
        slots.set(index, new Slot(pick(slots, player.getRandom()).id(), 0));
        player.setAttached(BOARD, new Board(slots, board.completed() + 1, day, today + 1));
        Notice.Panel panel = Notice.of(Notice.Kind.MINING).tag("COMMISSION").panel();
        panel.heading("Rewards", 0x55FF55);
        panel.line(Component.literal("+" + Msg.number(xp) + " HotM Experience").withStyle(ChatFormatting.DARK_PURPLE));
        if (bonus) {
            panel.line(Component.literal("+" + Msg.number(DAILY_XP) + " HotM Experience (daily bonus " + (today + 1) + "/" + DAILY + ")")
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        panel.line(Component.literal("+" + Msg.number(POWDER) + " Mithril Powder").withStyle(ChatFormatting.DARK_GREEN));
        panel.line(Component.literal("+" + Msg.number(MINING_XP) + " Mining Experience").withStyle(ChatFormatting.DARK_AQUA));
        panel.send(player, Component.literal(task.name()).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
        if (board.completed() + 1 == 100) {
            player.sendSystemMessage(Notice.build(Notice.Kind.MINING, Component.literal("100 commissions! Your board has a third slot.")
                    .withStyle(ChatFormatting.GOLD)));
        }
        return "";
    }
}
