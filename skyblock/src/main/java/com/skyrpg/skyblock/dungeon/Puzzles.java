package com.skyrpg.skyblock.dungeon;

import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Catacombs' puzzles. A puzzle room's {@code puzzle} marker names one; it's built when someone first walks in.
 * Solving one clears its room; failing one ({@link #fail}) leaves it uncleared and costs 14 Skill score (an unsolved
 * puzzle at the end counts as failed, as on Hypixel).
 *
 * <ul>
 * <li><b>Three Weirdos</b>: three NPCs before three chests; talk to them. Exactly one tells the truth (the statements are
 * generated so only one chest fits). Open the chest with the reward.</li>
 * <li><b>Blaze</b> (Higher or Lower): ten blazes with their health over their heads; kill them from lowest to highest
 * (or highest to lowest: the sign says which). Out of order fails it.</li>
 * <li><b>Tic Tac Toe</b>: the board on the floor; right-click a tile to play. The AI doesn't lose; don't let it win (a draw
 * solves it).</li>
 * </ul>
 */
final class Puzzles {
    private Puzzles() {
    }

    /** A puzzle for a room, or null if the kind isn't known. */
    static Puzzle create(String kind, Run run, Run.RoomState state, Vec3 center) {
        return switch (kind) {
            case "weirdos" -> new Weirdos(run, state, center);
            case "blaze" -> new BlazePuzzle(run, state, center);
            case "tictactoe" -> new TicTacToe(run, state, center);
            default -> null;
        };
    }

    abstract static class Puzzle {
        final Run run;
        final Run.RoomState state;
        final Vec3 center;
        boolean built;
        boolean solved;
        boolean failed;

        Puzzle(Run run, Run.RoomState state, Vec3 center) {
            this.run = run;
            this.state = state;
            this.center = center;
        }

        abstract String name();

        abstract void build();

        boolean useBlock(ServerPlayer player, BlockPos pos) {
            return false;
        }

        boolean useEntity(ServerPlayer player, Entity entity) {
            return false;
        }

        void died(Entity entity, ServerPlayer killer) {
        }

        BlockPos at(int dx, int dy, int dz) {
            return BlockPos.containing(center).offset(dx, dy, dz);
        }

        void solve(ServerPlayer by) {
            if (solved || failed) {
                return;
            }
            solved = true;
            state.cleared = true;
            run.broadcast(Component.literal("PUZZLE SOLVED! ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    .append(Component.literal((by == null ? "Someone" : by.getScoreboardName()) + " solved the " + name() + " puzzle!")
                            .withStyle(ChatFormatting.YELLOW)));
            for (ServerPlayer p : run.online()) {
                Msg.playSound(p, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.6f);
            }
            if (by != null) {
                Secrets.reward(by, run);
            }
        }

        void fail(ServerPlayer by, String why) {
            if (solved || failed) {
                return;
            }
            failed = true;
            run.broadcast(Component.literal("PUZZLE FAIL! ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal((by == null ? "Someone" : by.getScoreboardName()) + " " + why).withStyle(ChatFormatting.YELLOW)));
            for (ServerPlayer p : run.online()) {
                Msg.playSound(p, SoundEvents.VILLAGER_NO, 1.0f, 0.8f);
            }
        }

        void set(BlockPos pos, BlockState block) {
            run.level.setBlock(pos, block, Block.UPDATE_CLIENTS);
        }
    }

    // ---------------------------------------------------------------- Three Weirdos

    static final class Weirdos extends Puzzle {
        private static final String[] NAMES = {"Baxter", "Benson", "Carver", "Elmo", "Eveleth", "Hope", "Luverne", "Montgomery", "Ralph", "Rose",
                "Victoria", "Winona", "Ardis", "Hubert"};
        private final String[] names = new String[3];
        private final String[] lines = new String[3];
        private final BlockPos[] chests = new BlockPos[3];
        private final Map<UUID, Integer> npcs = new HashMap<>();
        private int answer;

        Weirdos(Run run, Run.RoomState state, Vec3 center) {
            super(run, state, center);
        }

        @Override
        String name() {
            return "Three Weirdos";
        }

        @Override
        void build() {
            RandomSource random = run.random;
            List<String> pool = new ArrayList<>(List.of(NAMES));
            for (int i = 0; i < 3; i++) {
                names[i] = pool.remove(random.nextInt(pool.size()));
            }
            generate(random);
            for (int i = 0; i < 3; i++) {
                int dx = (i - 1) * 3;
                chests[i] = at(dx, 0, 1);
                set(chests[i], Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH));
                Mob npc = (Mob) EntityTypes.VILLAGER.create(run.level, EntitySpawnReason.EVENT);
                if (npc == null) {
                    continue;
                }
                npc.snapTo(center.x + dx, center.y, center.z - 1, 0, 0);
                npc.setNoAi(true);
                npc.setSilent(true);
                npc.setPermanentlyInvulnerable(true);
                npc.setPersistenceRequired();
                npc.setCustomName(Component.literal(names[i]).withStyle(ChatFormatting.YELLOW));
                npc.setCustomNameVisible(true);
                npc.addTag(run.tag);
                npc.addTag(Catacombs.TAG);
                run.level.addFreshEntity(npc);
                npcs.put(npc.getUUID(), i);
            }
            run.label(center.add(0, 3, 0), Component.literal("Three Weirdos").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                    .append(Component.literal("\nOnly one of them tells the truth. Open the right chest.").withStyle(ChatFormatting.GRAY)));
        }

        /** Statements for each NPC with exactly one true for the answer, and not exactly one for any other chest. */
        private void generate(RandomSource random) {
            answer = random.nextInt(3);
            for (int attempt = 0; attempt < 2000; attempt++) {
                int[][] picks = new int[3][2];
                for (int i = 0; i < 3; i++) {
                    picks[i][0] = random.nextInt(4);
                    picks[i][1] = (i + 1 + random.nextInt(2)) % 3;
                }
                if (trueCount(picks, answer) != 1) {
                    continue;
                }
                boolean unique = true;
                for (int other = 0; other < 3; other++) {
                    if (other != answer && trueCount(picks, other) == 1) {
                        unique = false;
                    }
                }
                if (unique) {
                    for (int i = 0; i < 3; i++) {
                        String target = names[picks[i][1]];
                        lines[i] = switch (picks[i][0]) {
                            case 0 -> "The reward is in my chest!";
                            case 1 -> "My chest doesn't have the reward. I'm not lying.";
                            case 2 -> "The reward is in " + target + "'s chest.";
                            default -> "The reward isn't in " + target + "'s chest.";
                        };
                    }
                    return;
                }
            }
            // Can't happen in practice; a plain fallback that still has one answer.
            lines[answer] = "The reward is in my chest!";
            lines[(answer + 1) % 3] = "The reward is in " + names[(answer + 2) % 3] + "'s chest.";
            lines[(answer + 2) % 3] = "The reward isn't in " + names[answer] + "'s chest.";
        }

        private static int trueCount(int[][] picks, int reward) {
            int count = 0;
            for (int i = 0; i < 3; i++) {
                boolean truth = switch (picks[i][0]) {
                    case 0 -> reward == i;
                    case 1 -> reward != i;
                    case 2 -> reward == picks[i][1];
                    default -> reward != picks[i][1];
                };
                if (truth) {
                    count++;
                }
            }
            return count;
        }

        @Override
        boolean useEntity(ServerPlayer player, Entity entity) {
            Integer i = npcs.get(entity.getUUID());
            if (i == null) {
                return false;
            }
            player.sendSystemMessage(Component.literal("[NPC] " + names[i]).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(": " + lines[i]).withStyle(ChatFormatting.WHITE)));
            Msg.playSound(player, SoundEvents.VILLAGER_AMBIENT, 0.8f, 1.0f);
            return true;
        }

        @Override
        boolean useBlock(ServerPlayer player, BlockPos pos) {
            for (int i = 0; i < 3; i++) {
                if (pos.equals(chests[i])) {
                    if (!solved && !failed) {
                        run.level.blockEvent(pos, Blocks.CHEST, 1, 1);
                        if (i == answer) {
                            solve(player);
                        } else {
                            fail(player, "picked the wrong chest!");
                        }
                    }
                    return true;
                }
            }
            return false;
        }
    }

    // ---------------------------------------------------------------- the Blaze puzzle

    static final class BlazePuzzle extends Puzzle {
        private final Map<UUID, Integer> health = new HashMap<>();
        private boolean ascending;

        BlazePuzzle(Run run, Run.RoomState state, Vec3 center) {
            super(run, state, center);
        }

        @Override
        String name() {
            return "Higher Or Lower";
        }

        @Override
        void build() {
            RandomSource random = run.random;
            ascending = random.nextBoolean();
            List<Integer> values = new ArrayList<>();
            while (values.size() < 10) {
                int value = (5 + random.nextInt(60)) * 1000 * (1 + run.floor.number());
                if (!values.contains(value)) {
                    values.add(value);
                }
            }
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI * 2 * i / 10;
                Vec3 at = center.add(Math.cos(angle) * 5, 1 + random.nextInt(8), Math.sin(angle) * 5);
                Mob blaze = (Mob) EntityTypes.BLAZE.create(run.level, EntitySpawnReason.EVENT);
                if (blaze == null) {
                    continue;
                }
                blaze.snapTo(at.x, at.y, at.z, random.nextFloat() * 360, 0);
                Zones.dressAs(blaze, "Blaze", 10 + run.floor.number() * 20, values.get(i), 0, 0);
                blaze.setNoAi(true);
                blaze.setNoGravity(true);
                blaze.setPersistenceRequired();
                blaze.addTag(run.tag);
                blaze.addTag(Catacombs.TAG);
                blaze.addTag(DungeonMobs.TAG);
                blaze.addTag(Zones.FIGHTABLE_TAG);
                run.level.addFreshEntity(blaze);
                health.put(blaze.getUUID(), values.get(i));
            }
            run.label(center.add(0, 1.5, 0), Component.literal("Higher Or Lower").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("\nKill the blazes from " + (ascending ? "lowest to highest" : "highest to lowest") + " health!")
                            .withStyle(ChatFormatting.GRAY)));
        }

        @Override
        void died(Entity entity, ServerPlayer killer) {
            Integer value = health.remove(entity.getUUID());
            if (value == null || solved || failed) {
                return;
            }
            boolean right = health.values().stream().allMatch(other -> ascending ? other > value : other < value);
            if (!right) {
                fail(killer, "killed a blaze in the wrong order!");
                for (UUID id : health.keySet()) {
                    Entity blaze = run.level.getEntity(id);
                    if (blaze != null) {
                        blaze.discard();
                    }
                }
                health.clear();
                return;
            }
            if (health.isEmpty()) {
                solve(killer);
            }
        }
    }

    // ---------------------------------------------------------------- Tic Tac Toe

    static final class TicTacToe extends Puzzle {
        private static final int EMPTY = 0;
        private static final int PLAYER = 1;
        private static final int AI = 2;
        private final int[] board = new int[9];

        TicTacToe(Run run, Run.RoomState state, Vec3 center) {
            super(run, state, center);
        }

        @Override
        String name() {
            return "Tic Tac Toe";
        }

        private BlockPos tile(int i) {
            return at(i % 3 - 1, -1, i / 3 - 1);
        }

        @Override
        void build() {
            for (int i = 0; i < 9; i++) {
                set(tile(i), Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState());
            }
            // The AI opens in a corner or the middle.
            int[] openings = {0, 2, 4, 6, 8};
            place(openings[run.random.nextInt(openings.length)], AI);
            run.label(center.add(0, 2.5, 0), Component.literal("Tic Tac Toe").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                    .append(Component.literal("\nRight-click a white tile. Don't let it win!").withStyle(ChatFormatting.GRAY)));
        }

        private void place(int i, int who) {
            board[i] = who;
            set(tile(i), (who == AI ? Blocks.CONCRETE.pick(DyeColor.RED) : Blocks.CONCRETE.pick(DyeColor.BLUE)).defaultBlockState());
        }

        @Override
        boolean useBlock(ServerPlayer player, BlockPos pos) {
            for (int i = 0; i < 9; i++) {
                if (!pos.equals(tile(i))) {
                    continue;
                }
                if (solved || failed || board[i] != EMPTY) {
                    return true;
                }
                place(i, PLAYER);
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_HAT.value(), 1.0f, 1.2f);
                if (winner(board) == PLAYER) {
                    solve(player);
                    return true;
                }
                if (full(board)) {
                    solve(player);
                    return true;
                }
                place(bestMove(), AI);
                if (winner(board) == AI) {
                    fail(player, "lost to the Tic Tac Toe AI!");
                } else if (full(board)) {
                    solve(player);
                }
                return true;
            }
            return false;
        }

        private int bestMove() {
            int best = -1;
            int bestScore = Integer.MIN_VALUE;
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                if (board[i] == EMPTY) {
                    order.add(i);
                }
            }
            order.sort(Comparator.comparingInt(i -> run.random.nextInt(100)));
            for (int i : order) {
                board[i] = AI;
                int score = minimax(board, false, 0);
                board[i] = EMPTY;
                if (score > bestScore) {
                    bestScore = score;
                    best = i;
                }
            }
            return best;
        }

        private static int minimax(int[] b, boolean aiTurn, int depth) {
            int w = winner(b);
            if (w == AI) {
                return 10 - depth;
            }
            if (w == PLAYER) {
                return depth - 10;
            }
            if (full(b)) {
                return 0;
            }
            int best = aiTurn ? Integer.MIN_VALUE : Integer.MAX_VALUE;
            for (int i = 0; i < 9; i++) {
                if (b[i] == EMPTY) {
                    b[i] = aiTurn ? AI : PLAYER;
                    int score = minimax(b, !aiTurn, depth + 1);
                    b[i] = EMPTY;
                    best = aiTurn ? Math.max(best, score) : Math.min(best, score);
                }
            }
            return best;
        }

        private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

        private static int winner(int[] b) {
            for (int[] line : LINES) {
                if (b[line[0]] != EMPTY && b[line[0]] == b[line[1]] && b[line[1]] == b[line[2]]) {
                    return b[line[0]];
                }
            }
            return EMPTY;
        }

        private static boolean full(int[] b) {
            for (int v : b) {
                if (v == EMPTY) {
                    return false;
                }
            }
            return true;
        }
    }
}
