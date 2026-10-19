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
            case "icefill" -> new IceFill(run, state, center);
            case "teleportmaze" -> new TeleportMaze(run, state, center);
            case "quiz" -> new Quiz(run, state, center);
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

        /** Every tick while it's unsolved (puzzles you walk through). */
        void tick() {
        }

        /** Every block of one kind in the room (Hypixel's rooms keep their puzzle's pieces), from its basement up. */
        List<BlockPos> find(Block block) {
            List<BlockPos> out = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(state.min.below(RoomLibrary.HYPIXEL_DROP), state.max.above(40))) {
                if (run.level.getBlockState(pos).is(block)) {
                    out.add(pos.immutable());
                }
            }
            return out;
        }

        /** The players in this puzzle's room. */
        List<ServerPlayer> here() {
            return run.online().stream().filter(p -> run.roomAt(p.position()) == state).toList();
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
            Blessings.bless(run, by);
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

    // ---------------------------------------------------------------- Ice Fill

    /**
     * <b>Ice Fill</b> (Hypixel's room): three grids of ice on the room's floor, 3×3, 5×5 and 7×7, with polished andesite
     * in the way. Every ice block turns to packed ice as you step on it; cover a whole grid without stepping on one twice
     * (stepping back onto its packed ice starts that grid over). Decided: each grid is laid around a random path through
     * it (the andesite is what the path leaves out), so every grid can be done; the room's own floor is all packed ice in
     * the captures (they were saved solved), so the grids are laid on it when the puzzle starts.
     */
    static final class IceFill extends Puzzle {
        private static final int[] SIZES = {3, 5, 7};
        private final List<List<BlockPos>> grids = new ArrayList<>();
        private final List<java.util.Set<BlockPos>> packed = new ArrayList<>();
        private final Map<UUID, BlockPos> standing = new HashMap<>();

        IceFill(Run run, Run.RoomState state, Vec3 center) {
            super(run, state, center);
        }

        @Override
        String name() {
            return "Ice Fill";
        }

        @Override
        void build() {
            // the room's fountains would flow over the floor here (Hypixel's water doesn't flow): they go
            for (BlockPos pos : find(Blocks.WATER)) {
                set(pos, Blocks.AIR.defaultBlockState());
            }
            // the floor: packed ice (or ice) with room to stand on it, at its commonest height
            List<BlockPos> floor = new ArrayList<>();
            for (Block block : new Block[] {Blocks.PACKED_ICE, Blocks.ICE}) {
                for (BlockPos pos : find(block)) {
                    BlockState above = run.level.getBlockState(pos.above());
                    if ((above.isAir() || above.is(Blocks.POLISHED_ANDESITE)) && run.level.getBlockState(pos.above(2)).isAir()) {
                        floor.add(pos);
                    }
                }
            }
            Map<Integer, Integer> heights = new HashMap<>();
            floor.forEach(pos -> heights.merge(pos.getY(), 1, Integer::sum));
            int y = heights.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(0);
            floor.removeIf(pos -> pos.getY() != y);
            if (floor.size() < 30) {
                solve(null);
                return;
            }
            // its main square: the rows and columns that are mostly floor
            int[] xs = span(floor.stream().mapToInt(BlockPos::getX).toArray());
            int[] zs = span(floor.stream().mapToInt(BlockPos::getZ).toArray());
            boolean alongX = xs[1] - xs[0] >= zs[1] - zs[0];
            int first = alongX ? xs[0] : zs[0];
            int length = (alongX ? xs[1] - xs[0] : zs[1] - zs[0]) + 1;
            int across = (alongX ? zs[1] - zs[0] : xs[1] - xs[0]) + 1;
            int mid = alongX ? (zs[0] + zs[1]) / 2 : (xs[0] + xs[1]) / 2;
            java.util.Set<BlockPos> onFloor = new java.util.HashSet<>(floor);
            // the old andesite goes; the grids get their own
            for (BlockPos pos : floor) {
                if (run.level.getBlockState(pos.above()).is(Blocks.POLISHED_ANDESITE)) {
                    set(pos.above(), Blocks.AIR.defaultBlockState());
                }
            }
            int at = first;
            int gap = Math.max(1, (length - 15) / 2);
            for (int size : SIZES) {
                int n = Math.min(size, Math.min(across, length));
                if (n < 2 || at - first + n > length) {
                    break;
                }
                List<BlockPos> tiles = new ArrayList<>();
                java.util.Set<Integer> onPath = new java.util.HashSet<>();
                path(n).forEach(c -> onPath.add(c[0] * 64 + c[1]));
                for (int a = 0; a < n; a++) {
                    for (int b = 0; b < n; b++) {
                        int lateral = mid - n / 2 + b;
                        BlockPos pos = alongX ? new BlockPos(at + a, y, lateral) : new BlockPos(lateral, y, at + a);
                        if (!onFloor.contains(pos)) {
                            continue;
                        }
                        if (onPath.contains(a * 64 + b)) {
                            set(pos, Blocks.ICE.defaultBlockState());
                            tiles.add(pos);
                        } else {
                            set(pos.above(), Blocks.POLISHED_ANDESITE.defaultBlockState());
                        }
                    }
                }
                if (!tiles.isEmpty()) {
                    grids.add(tiles);
                    packed.add(new java.util.HashSet<>());
                }
                at += n + gap;
            }
            if (grids.isEmpty()) {
                solve(null);
            }
        }

        /** The span of the values that come up in at least half as many places as the commonest one. */
        private static int[] span(int[] values) {
            Map<Integer, Integer> counts = new HashMap<>();
            for (int v : values) {
                counts.merge(v, 1, Integer::sum);
            }
            int most = counts.values().stream().max(Integer::compare).orElse(0);
            int lo = counts.entrySet().stream().filter(e -> e.getValue() * 2 >= most).mapToInt(Map.Entry::getKey).min().orElse(0);
            int hi = counts.entrySet().stream().filter(e -> e.getValue() * 2 >= most).mapToInt(Map.Entry::getKey).max().orElse(0);
            return new int[] {lo, hi};
        }

        /**
         * A long path through an n×n grid from the middle of its first row, no cell twice (Warnsdorff's rule with random
         * ties: the best of a few tries), cut a little short on the bigger grids so there's andesite to steer around.
         */
        private List<int[]> path(int n) {
            List<int[]> best = List.of();
            for (int attempt = 0; attempt < 40 && best.size() < n * n; attempt++) {
                boolean[][] seen = new boolean[n][n];
                List<int[]> walk = new ArrayList<>();
                int a = 0;
                int b = n / 2;
                while (true) {
                    seen[a][b] = true;
                    walk.add(new int[] {a, b});
                    List<int[]> next = new ArrayList<>();
                    for (int[] d : STEPS) {
                        int na = a + d[0];
                        int nb = b + d[1];
                        if (na >= 0 && nb >= 0 && na < n && nb < n && !seen[na][nb]) {
                            next.add(new int[] {na, nb});
                        }
                    }
                    if (next.isEmpty()) {
                        break;
                    }
                    java.util.Collections.shuffle(next, new java.util.Random(run.random.nextLong()));
                    next.sort(Comparator.comparingInt(c -> onward(seen, c[0], c[1], n)));
                    a = next.getFirst()[0];
                    b = next.getFirst()[1];
                }
                if (walk.size() > best.size()) {
                    best = walk;
                }
            }
            int keep = n <= 3 ? best.size() : Math.max(n * n * 3 / 4, best.size() - run.random.nextInt(n / 2 + 1));
            return best.subList(0, Math.min(keep, best.size()));
        }

        private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        private static int onward(boolean[][] seen, int a, int b, int n) {
            int count = 0;
            for (int[] d : STEPS) {
                int na = a + d[0];
                int nb = b + d[1];
                if (na >= 0 && nb >= 0 && na < n && nb < n && !seen[na][nb]) {
                    count++;
                }
            }
            return count;
        }

        @Override
        void tick() {
            for (ServerPlayer player : here()) {
                BlockPos under = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
                BlockPos was = standing.put(player.getUUID(), under);
                if (under.equals(was)) {
                    continue;
                }
                for (int i = 0; i < grids.size(); i++) {
                    if (!grids.get(i).contains(under)) {
                        continue;
                    }
                    java.util.Set<BlockPos> done = packed.get(i);
                    if (done.size() >= grids.get(i).size()) {
                        break;
                    }
                    if (done.contains(under)) {
                        for (BlockPos pos : done) {
                            set(pos, Blocks.ICE.defaultBlockState());
                        }
                        done.clear();
                        player.sendSystemMessage(Component.literal("You stepped on the packed ice again: this grid starts over.").withStyle(ChatFormatting.RED));
                        Msg.playSound(player, SoundEvents.GLASS_BREAK, 1.0f, 0.8f);
                        break;
                    }
                    done.add(under);
                    set(under, Blocks.PACKED_ICE.defaultBlockState());
                    Msg.playSound(player, SoundEvents.GLASS_STEP, 1.0f, 1.4f);
                    if (done.size() >= grids.get(i).size()) {
                        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.6f);
                        if (finished()) {
                            solve(player);
                        } else {
                            player.sendSystemMessage(Component.literal("Grid " + (i + 1) + " of " + grids.size() + " done!").withStyle(ChatFormatting.AQUA));
                        }
                    }
                    break;
                }
            }
        }

        private boolean finished() {
            for (int i = 0; i < grids.size(); i++) {
                if (packed.get(i).size() < grids.get(i).size()) {
                    return false;
                }
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- Teleport Maze

    /**
     * <b>Teleport Maze</b> (Hypixel's room): its end portal frames are pads, each sending you to another one; one of them
     * leads out, to the room's middle, and solves it.
     */
    static final class TeleportMaze extends Puzzle {
        private final Map<BlockPos, BlockPos> to = new HashMap<>();
        private BlockPos exit;
        private final Map<UUID, Long> lastJump = new HashMap<>();
        private final Map<UUID, BlockPos> onPad = new HashMap<>();

        TeleportMaze(Run run, Run.RoomState state, Vec3 center) {
            super(run, state, center);
        }

        @Override
        String name() {
            return "Teleport Maze";
        }

        @Override
        void build() {
            List<BlockPos> pads = new ArrayList<>(find(Blocks.END_PORTAL_FRAME));
            if (pads.size() < 2) {
                solve(null);
                return;
            }
            java.util.Collections.shuffle(pads, new java.util.Random(run.random.nextLong()));
            exit = pads.getFirst();
            for (int i = 0; i < pads.size(); i++) {
                // each pad to a pad of the next one along, so every pad goes somewhere else
                to.put(pads.get(i), pads.get((i + 1 + run.random.nextInt(pads.size() - 1)) % pads.size()));
            }
        }

        @Override
        void tick() {
            long now = run.level.getGameTime();
            for (ServerPlayer player : here()) {
                BlockPos feet = player.blockPosition();
                BlockPos pad = run.level.getBlockState(feet).is(Blocks.END_PORTAL_FRAME) ? feet
                        : run.level.getBlockState(feet.below()).is(Blocks.END_PORTAL_FRAME) ? feet.below() : null;
                // a pad sends you on as you step onto it (you land on the next one: step off it to go on)
                BlockPos was = pad == null ? onPad.remove(player.getUUID()) : onPad.put(player.getUUID(), pad);
                if (pad == null || pad.equals(was) || now - lastJump.getOrDefault(player.getUUID(), 0L) < 10) {
                    continue;
                }
                lastJump.put(player.getUUID(), now);
                BlockPos next = to.getOrDefault(pad, pad);
                if (pad.equals(exit)) {
                    onPad.remove(player.getUUID());
                } else {
                    onPad.put(player.getUUID(), next);
                }
                Vec3 at = pad.equals(exit) ? center : Vec3.atBottomCenterOf(next.above());
                player.teleportTo(run.level, at.x, at.y, at.z, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
                Msg.playSound(player, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.2f);
                if (pad.equals(exit)) {
                    solve(player);
                }
            }
        }
    }

    // ---------------------------------------------------------------- Quiz

    /**
     * <b>Quiz</b> (Hypixel's room): three questions, each with three answers on stands around the room's middle;
     * right-click the right one. A wrong answer fails it. Decided: our questions are about the game itself (Hypixel
     * asks about SkyBlock's lore).
     */
    static final class Quiz extends Puzzle {
        private static final String[][] QUESTIONS = {
                {"How many Fairy Souls does Tia take for each exchange?", "5", "10", "3"},
                {"Who is the boss of Floor I?", "Bonzo", "Scarf", "The Professor"},
                {"How many Hot Potato Books can an item take?", "10", "5", "15"},
                {"What does a Recombobulator 3000 do?", "Raises an item's rarity", "Adds a reforge", "Repairs an item"},
                {"Who stands at the Catacombs' gate in the Dungeon Hub?", "Mort", "Ophelia", "Malik"},
                {"Which mayor brings the Mythological Ritual?", "Diana", "Paul", "Aatrox"},
                {"What does SkyMart sell its gear for?", "Copper", "Bits", "Gems"},
                {"What opens a Wither door?", "A Wither Key", "A Blood Key", "A lever"},
                {"How many Garden plots can you unlock?", "24", "25", "20"},
                {"Who runs Hoppity's Hunt?", "Hoppity", "Jerry", "Oringo"},
                {"Which slayer is the Revenant Horror?", "Zombie", "Spider", "Wolf"},
                {"Who sells the Accessory Powers in the Hub?", "Maxwell", "Elizabeth", "Rosetta"},
                {"What wakes up when the Blood room is opened?", "The Watcher", "Bonzo", "The fairy"},
                {"How much SkyBlock XP is one SkyBlock Level?", "100", "1,000", "10"},
                {"What do you need for the Garden?", "SkyBlock Level 5", "Farming 10", "A Garden Key"},
                {"Who reforges items with his own reforges?", "The Blacksmith", "Rosetta", "Maxwell"},
                {"What do star mobs have before their name?", "A star (✯)", "A skull", "A heart"},
                {"How many puzzles can fail before it costs score?", "None: every one costs 14", "Two", "One"},
        };
        private final List<String[]> asked = new ArrayList<>();
        private final List<Entity> stands = new ArrayList<>();
        private int question;
        private int right;

        Quiz(Run run, Run.RoomState state, Vec3 center) {
            super(run, state, center);
        }

        @Override
        String name() {
            return "Quiz";
        }

        @Override
        void build() {
            List<String[]> all = new ArrayList<>(List.of(QUESTIONS));
            java.util.Collections.shuffle(all, new java.util.Random(run.random.nextLong()));
            asked.addAll(all.subList(0, 3));
            ask();
        }

        private void ask() {
            stands.forEach(Entity::discard);
            stands.clear();
            String[] q = asked.get(question);
            List<String> answers = new ArrayList<>(List.of(q[1], q[2], q[3]));
            java.util.Collections.shuffle(answers, new java.util.Random(run.random.nextLong()));
            right = answers.indexOf(q[1]);
            run.broadcast(Component.literal("[STATUE] Oruo the Omniscient: ").withStyle(ChatFormatting.RED)
                    .append(Component.literal("Question " + (question + 1) + " of 3: " + q[0]).withStyle(ChatFormatting.WHITE)));
            String[] letters = {"ⓐ", "ⓑ", "ⓒ"};
            for (int i = 0; i < 3; i++) {
                Vec3 at = center.add(-4 + i * 4, 0, 4);
                net.minecraft.world.entity.decoration.ArmorStand stand = EntityTypes.ARMOR_STAND.create(run.level, EntitySpawnReason.EVENT);
                if (stand == null) {
                    continue;
                }
                stand.snapTo(at.x, at.y, at.z, 180, 0);
                stand.setCustomName(Component.literal(letters[i] + " " + answers.get(i)).withStyle(ChatFormatting.GOLD));
                stand.setCustomNameVisible(true);
                stand.setNoGravity(true);
                stand.addTag(run.tag);
                stand.addTag(Catacombs.TAG);
                run.level.addFreshEntity(stand);
                stands.add(stand);
            }
        }

        @Override
        boolean useEntity(ServerPlayer player, Entity entity) {
            int index = stands.indexOf(entity);
            if (index < 0 || solved || failed) {
                return index >= 0;
            }
            if (index != right) {
                stands.forEach(Entity::discard);
                fail(player, "answered the Quiz wrong!");
                return true;
            }
            question++;
            if (question >= asked.size()) {
                stands.forEach(Entity::discard);
                solve(player);
            } else {
                Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                ask();
            }
            return true;
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
