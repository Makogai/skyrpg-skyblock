package com.skyrpg.skyblock.galatea;

import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.essence.Essence;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Galatea's trees (Hypixel's): Fig (toughness 10) and Mangrove (50). Chop a log with an axe and it goes with the logs
 * your Sweep reaches past the tree's toughness (Hypixel's table; up to 20 extra a chop, half as many out of order). Cut
 * them in order: a Fig's trunk before its branches, a Mangrove's branches, then its trunk, then its roots. Each log is a
 * Fig or Mangrove Log (Foraging Fortune and Fig/Mangrove Fortune for more) and 15 / 20 Foraging XP. The last log felled,
 * everyone who cut a tenth or more of it gets the **Tree Gift** (a fifth: x1.5, a third: x2): Forest Essence, Foraging
 * XP, maybe Tender Wood or Vinesap, rarely Stretching Sticks. Trees grow back a minute later.
 */
final class GalateaTrees {
    /** A kind of tree: its blocks in cutting order, toughness, drop, XP per log, gift. */
    enum Kind {
        FIG("Fig", 10, "FIG_LOG", 15, 2, 100, "TENDER_WOOD", StatTypes.FIG_FORTUNE,
                new int[][] {{1, 7}, {2, 9}, {3, 11}, {4, 16}, {5, 21}, {10, 62}, {20, 234}}),
        MANGROVE("Mangrove", 50, "MANGROVE_LOG", 20, 3, 500, "VINESAP", StatTypes.MANGROVE_FORTUNE,
                new int[][] {{1, 50}, {2, 55}, {3, 60}, {4, 75}, {5, 90}, {10, 230}, {20, 1055}});

        final String name;
        final int toughness;
        final String log;
        final int xp;
        final int essence;
        final int giftXp;
        final String giftItem;
        final com.skyrpg.core.stats.StatType fortune;
        /** Hypixel's Sweep for extra logs a chop: {extra logs, Sweep needed}. */
        final int[][] sweep;

        Kind(String name, int toughness, String log, int xp, int essence, int giftXp, String giftItem, com.skyrpg.core.stats.StatType fortune,
             int[][] sweep) {
            this.name = name;
            this.toughness = toughness;
            this.log = log;
            this.xp = xp;
            this.essence = essence;
            this.giftXp = giftXp;
            this.giftItem = giftItem;
            this.fortune = fortune;
            this.sweep = sweep;
        }

        /** Extra logs a chop for this much Sweep (linear between Hypixel's points), up to 20. */
        int extra(double sweep) {
            int best = 0;
            for (int i = 0; i < this.sweep.length; i++) {
                int[] at = this.sweep[i];
                if (sweep >= at[1]) {
                    best = at[0];
                } else if (i > 0) {
                    int[] before = this.sweep[i - 1];
                    best = before[0] + (int) ((sweep - before[1]) * (at[0] - before[0]) / (at[1] - before[1]));
                    break;
                } else {
                    break;
                }
            }
            return Math.min(20, best);
        }
    }

    /** A tree as it stood, the blocks still to cut and who cut how many. */
    private static final class Tree {
        Kind kind;
        final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        final Set<BlockPos> left = new HashSet<>();
        final Map<UUID, Integer> cut = new HashMap<>();
        int total;
        long regrowAt;
        /** The last chop: a tree left half-cut grows back whole after a while. */
        long touched;
    }

    private static final Map<BlockPos, Tree> BY_BLOCK = new HashMap<>();
    private static final List<Tree> FELLED = new ArrayList<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();
    /** When each player last threw their axe (a second apart). */
    private static final Map<UUID, Long> THROWN = new HashMap<>();
    private static final int REGROW_TICKS = 60 * 20;
    private static final int ABANDONED_TICKS = 120 * 20;
    /** Block changes without neighbour updates: vines, lanterns and leaves around a tree don't pop or decay. */
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private GalateaTrees() {
    }

    static void init() {
        Identifier first = SkyRpgSkyblock.id("galatea_first");
        AttackBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(first, (player, level, hand, pos, direction) -> {
            BlockPos log = Galatea.isGalatea(level) ? treeBlockAt(level, pos) : null;
            if (log == null) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer server) {
                chop(server, (ServerLevel) level, log, 1);
            }
            return InteractionResult.SUCCESS;
        });
        // Right-click with an axe: throw it at a tree (sneaking: the Heart of the Forest's axe ability instead). Before the
        // abilities, so an axe's own right-click ability doesn't fire on Galatea.
        Identifier axe = SkyRpgSkyblock.id("galatea_axe");
        Identifier abilities = Identifier.fromNamespaceAndPath("skyrpg_items", "abilities");
        UseItemCallback.EVENT.addPhaseOrdering(axe, abilities);
        UseItemCallback.EVENT.addPhaseOrdering(axe, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(axe, (player, level, hand) -> rightClick(player, level, hand));
        UseBlockCallback.EVENT.addPhaseOrdering(axe, abilities);
        UseBlockCallback.EVENT.addPhaseOrdering(axe, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(axe, (player, level, hand, hit) -> rightClick(player, level, hand));
        ServerTickEvents.END_SERVER_TICK.register(GalateaTrees::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerLevel level = server.getLevel(Galatea.LEVEL);
            if (level != null) {
                for (Tree tree : List.copyOf(new HashSet<>(BY_BLOCK.values()))) {
                    regrow(level, tree);
                }
                for (Tree tree : List.copyOf(FELLED)) {
                    regrow(level, tree);
                }
            }
        });
    }

    /** The tree block hit: the block itself, or the log a vine (lichen, cocoa...) on it hangs from; else null. */
    private static BlockPos treeBlockAt(net.minecraft.world.level.Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (kindOf(state) != null) {
            return pos.immutable();
        }
        if (hangs(state)) {
            for (Direction side : Direction.values()) {
                BlockPos near = pos.relative(side);
                if (kindOf(level.getBlockState(near)) != null) {
                    return near.immutable();
                }
            }
        }
        return null;
    }

    static Kind kindOf(BlockState state) {
        if (state.is(Blocks.JUNGLE_LOG)) {
            return Kind.FIG;
        }
        if (state.is(Blocks.MANGROVE_LOG) || state.is(Blocks.MANGROVE_ROOTS) || state.is(Blocks.MUDDY_MANGROVE_ROOTS)) {
            return Kind.MANGROVE;
        }
        return null;
    }

    private static boolean leaves(BlockState state) {
        return state.is(Blocks.AZALEA_LEAVES) || state.is(Blocks.FLOWERING_AZALEA_LEAVES) || state.is(Blocks.MANGROVE_LEAVES);
    }

    private static boolean hangs(BlockState state) {
        return state.is(Blocks.VINE) || state.is(Blocks.GLOW_LICHEN) || state.is(Blocks.COCOA) || state.is(Blocks.LADDER)
                || state.is(Blocks.MANGROVE_PROPAGULE) || state.is(Blocks.PALE_HANGING_MOSS) || state.is(Blocks.HANGING_ROOTS)
                || state.is(Blocks.SPORE_BLOSSOM) || state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT) || state.is(Blocks.MOSS_CARPET)
                || (state.is(Blocks.LANTERN) || state.is(Blocks.SOUL_LANTERN)) && state.getValue(net.minecraft.world.level.block.LanternBlock.HANGING);
    }

    /** Its place in the cutting order: Fig trunk 0, branches 1; Mangrove branches 0, trunk 1, roots 2. */
    private static int stage(Kind kind, BlockState state) {
        boolean upright = state.hasProperty(RotatedPillarBlock.AXIS) && state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y;
        if (kind == Kind.FIG) {
            return upright ? 0 : 1;
        }
        if (!state.is(Blocks.MANGROVE_LOG)) {
            return 2;
        }
        return upright ? 1 : 0;
    }

    /** The tree this log belongs to: found once by its connected logs, remembered until it's felled. */
    private static Tree tree(ServerLevel level, BlockPos pos, Kind kind) {
        Tree known = BY_BLOCK.get(pos);
        if (known != null) {
            return known;
        }
        Tree tree = new Tree();
        tree.kind = kind;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(pos));
        Set<BlockPos> seen = new HashSet<>(List.of(pos));
        while (!queue.isEmpty() && tree.left.size() < 300) {
            BlockPos at = queue.poll();
            BlockState state = level.getBlockState(at);
            if (kindOf(state) != kind) {
                continue;
            }
            tree.left.add(at);
            tree.original.put(at, state);
            for (BlockPos next : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
                if (seen.add(next.immutable())) {
                    queue.add(next.immutable());
                }
            }
        }
        // its canopy goes with it, and comes back with it
        Set<BlockPos> canopy = new HashSet<>();
        for (BlockPos log : tree.left) {
            for (BlockPos near : BlockPos.betweenClosed(log.offset(-3, -1, -3), log.offset(3, 4, 3))) {
                if (!tree.original.containsKey(near) && leaves(level.getBlockState(near))) {
                    canopy.add(near.immutable());
                }
            }
        }
        for (BlockPos leaf : canopy) {
            tree.original.put(leaf, level.getBlockState(leaf));
        }
        // and what hangs on it (vines, lichen, a lantern under the leaves) falls with it and comes back with it
        ArrayDeque<BlockPos> hang = new ArrayDeque<>(tree.original.keySet());
        while (!hang.isEmpty() && tree.original.size() < 2000) {
            BlockPos at = hang.poll();
            for (Direction side : Direction.values()) {
                BlockPos near = at.relative(side);
                BlockState state = level.getBlockState(near);
                if (!tree.original.containsKey(near) && hangs(state)) {
                    tree.original.put(near.immutable(), state);
                    hang.add(near.immutable());
                }
            }
        }
        tree.total = tree.left.size();
        for (BlockPos log : tree.left) {
            BY_BLOCK.put(log, tree);
        }
        return tree;
    }

    private static InteractionResult rightClick(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level,
            net.minecraft.world.InteractionHand hand) {
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND || !Galatea.isGalatea(level) || !isAxe(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            if (server.isShiftKeyDown()) {
                Hotf.useAbility(server);
            } else {
                throwAxe(server, (ServerLevel) level);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * A thrown axe: it flies through leaves and vines to the first Galatea log within 20 blocks and chops it with half
     * the Sweep (Axe Toss: all of it). Homing Axe finds a log near where you aimed; Ricochet sometimes bounces on to
     * another tree.
     */
    static void throwAxe(ServerPlayer player, ServerLevel level) {
        long now = level.getServer().getTickCount();
        // Rosemary's Axed: half the wait
        if (now - THROWN.getOrDefault(player.getUUID(), -100L) < (Rosemary.level(player, "axed") > 0 ? 10 : 20)) {
            return;
        }
        THROWN.put(player.getUUID(), now);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockPos target = null;
        Vec3 end = eye.add(look.scale(20));
        for (double d = 0.5; d <= 20; d += 0.25) {
            Vec3 at = eye.add(look.scale(d));
            BlockPos pos = BlockPos.containing(at);
            BlockState state = level.getBlockState(pos);
            if (kindOf(state) != null) {
                target = pos.immutable();
                end = at;
                break;
            }
            if (!state.isAir() && !leaves(state) && !hangs(state) && state.isSolidRender()) {
                end = at;
                break;
            }
        }
        if (target == null && Hotf.has(player, "homing_axe")) {
            double best = 0.95;
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(eye).offset(-20, -10, -20), BlockPos.containing(eye).offset(20, 10, 20))) {
                Vec3 to = Vec3.atCenterOf(pos).subtract(eye);
                if (to.length() > 20 || kindOf(level.getBlockState(pos)) == null) {
                    continue;
                }
                double dot = look.dot(to.normalize());
                if (dot > best) {
                    best = dot;
                    target = pos.immutable();
                    end = Vec3.atCenterOf(pos);
                }
            }
        }
        // the axe's flight
        Vec3 path = end.subtract(eye);
        for (double d = 0; d < path.length(); d += 0.6) {
            Vec3 at = eye.add(path.normalize().scale(d));
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.3f);
        if (target == null) {
            return;
        }
        LAST.remove(player.getUUID());
        chop(player, level, target, Hotf.thrownSweep(player));
        if (player.getRandom().nextDouble() * 100 < Hotf.number(player, "ricochet", "stat")) {
            Tree first = BY_BLOCK.get(target);
            for (BlockPos pos : BlockPos.betweenClosed(target.offset(-8, -4, -8), target.offset(8, 6, 8))) {
                if (kindOf(level.getBlockState(pos)) != null && (first == null || BY_BLOCK.get(pos) != first) && pos.distSqr(target) > 9) {
                    LAST.remove(player.getUUID());
                    player.sendOverlayMessage(Component.literal("Ricochet!").withStyle(ChatFormatting.GOLD));
                    chop(player, level, pos.immutable(), Hotf.thrownSweep(player));
                    break;
                }
            }
        }
    }

    /** An axe: a vanilla one or a SkyBlock item of the AXE category. */
    static boolean isAxe(ItemStack tool) {
        ItemDefinition def = SkyItems.definition(tool);
        return tool.is(ItemTags.AXES) || def != null && "AXE".equals(def.json().has("category") ? def.json().get("category").getAsString() : "");
    }

    /** A chop at this log; {@code sweepFactor} is 0.5 for a thrown axe (1 with Axe Toss). */
    static void chop(ServerPlayer player, ServerLevel level, BlockPos pos, double sweepFactor) {
        long now = level.getServer().getTickCount();
        if (now - LAST.getOrDefault(player.getUUID(), -10L) < 3) {
            return;
        }
        if (!isAxe(player.getMainHandItem())) {
            player.sendOverlayMessage(Component.literal("You need an axe to chop Galatea's trees.").withStyle(ChatFormatting.RED));
            return;
        }
        LAST.put(player.getUUID(), now);
        BlockState state = level.getBlockState(pos);
        Kind kind = kindOf(state);
        Tree tree = tree(level, pos, kind);
        if (!tree.left.contains(pos)) {
            return;
        }
        tree.touched = now;
        int stage = stage(kind, state);
        boolean outOfOrder = tree.left.stream().anyMatch(p -> stage(kind, level.getBlockState(p)) < stage);
        double sweep = Stats.get(player, StatTypes.SWEEP) * (outOfOrder ? 0.5 : 1) * sweepFactor;
        // Timber: a chance for one more; Two for One: the whole stage at once
        int count = 1 + kind.extra(sweep) + (player.getRandom().nextDouble() * 100 < Stats.get(player, StatTypes.TIMBER) ? 1 : 0);
        boolean whole = Hotf.twoForOne(player);
        if (whole) {
            count = tree.left.size();
            player.sendSystemMessage(Component.literal("TWO FOR ONE! ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    .append(Component.literal("Your chop felled the whole " + kind.name + " Tree!").withStyle(ChatFormatting.GREEN)));
        }
        // this log, then the nearest of the same stage
        List<BlockPos> take = new ArrayList<>(tree.left.stream().filter(p -> whole || stage(kind, level.getBlockState(p)) == stage)
                .sorted(Comparator.comparingDouble(p -> p.distSqr(pos))).limit(count).toList());
        if (!take.contains(pos)) {
            take.set(take.size() - 1, pos);
        }
        double fortune = Stats.get(player, StatTypes.FORAGING_FORTUNE) + Stats.get(player, kind.fortune);
        long logs = 0;
        for (BlockPos log : take) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(log)), log.getX() + 0.5, log.getY() + 0.5,
                    log.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
            level.setBlock(log, Blocks.AIR.defaultBlockState(), QUIET);
            tree.left.remove(log);
            BY_BLOCK.remove(log);
            logs += 1 + (long) (fortune / 100) + (player.getRandom().nextDouble() < (fortune % 100) / 100 ? 1 : 0);
        }
        tree.cut.merge(player.getUUID(), take.size(), Integer::sum);
        give(player, kind.log, (int) Math.min(Integer.MAX_VALUE, logs), ItemGains.Source.FORAGING);
        SkillLevels.giveXp(player, SkillLevels.FORAGING, kind.xp * take.size());
        Hotf.onLogs(player, kind, take.size());
        Starlyn.logs(player, kind, logs);
        level.playSound(null, pos, SoundEvents.WOOD_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.8f);
        player.sendOverlayMessage(Component.literal(kind.name + " Tree  ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(tree.left.size() + "/" + tree.total + " logs left").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(outOfOrder ? "  ·  out of order: half Sweep!" : "").withStyle(ChatFormatting.RED)));
        if (tree.left.isEmpty()) {
            felled(level, tree);
        }
    }

    private static void felled(ServerLevel level, Tree tree) {
        for (BlockPos pos : tree.original.keySet()) {
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET);
            }
        }
        tree.regrowAt = level.getServer().getTickCount() + REGROW_TICKS;
        FELLED.add(tree);
        double size = Math.max(1, Math.min(20, tree.total / 10.0));
        for (Map.Entry<UUID, Integer> entry : tree.cut.entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            double share = entry.getValue() / (double) tree.total;
            if (player == null || share < 0.10) {
                continue;
            }
            double mult = (share >= 0.33 ? 2 : share >= 0.20 ? 1.5 : 1) * Hotf.giftLuck(player);
            int essence = Hotf.essence(player, (int) Math.round(tree.kind.essence * mult * size * Rosemary.tasty(player)));
            long xp = Math.round(tree.kind.giftXp * mult * size);
            Essence.give(player, "FOREST", essence);
            SkillLevels.giveXp(player, SkillLevels.FORAGING, xp);
            long whispers = Hotf.onGift(player, tree.kind);
            Starlyn.gift(player);
            var panel = Notice.of(Notice.Kind.SUCCESS).tag("TREE GIFT").panel()
                    .line(Component.literal(" +" + essence + " Forest Essence  ·  +" + Msg.number(xp) + " Foraging XP  ·  " + Math.round(share * 100) + "% cut")
                            .withStyle(ChatFormatting.GRAY))
                    .line(Component.literal(" +" + Msg.number(whispers) + " Forest Whispers  ·  +"
                            + (tree.kind == Kind.MANGROVE ? Hotf.MANGROVE_XP : Hotf.FIG_XP) + " Heart of the Forest XP").withStyle(ChatFormatting.DARK_AQUA));
            if (player.getRandom().nextBoolean()) {
                give(player, tree.kind.giftItem, 1, ItemGains.Source.FORAGING);
                ItemDefinition def = ItemRegistry.skyblock(tree.kind.giftItem);
                panel.line(Component.literal(" +1 " + (def == null ? tree.kind.giftItem : def.name())).withStyle(ChatFormatting.GREEN));
            }
            if (player.getRandom().nextDouble() < 0.08 * mult) {
                give(player, "STRETCHING_STICKS", 1, ItemGains.Source.FORAGING);
                panel.line(Component.literal(" RARE! Stretching Sticks").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            panel.send(player, Component.literal("You felled a " + tree.kind.name + " Tree!").withStyle(ChatFormatting.GREEN));
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
        }
    }

    private static void regrow(ServerLevel level, Tree tree) {
        for (Map.Entry<BlockPos, BlockState> e : tree.original.entrySet()) {
            level.setBlock(e.getKey(), e.getValue(), QUIET);
            BY_BLOCK.remove(e.getKey());
        }
        FELLED.remove(tree);
    }

    /** Someone stands in one of the tree's blocks (under its canopy is fine): it waits to grow back. */
    private static boolean occupied(ServerLevel level, Tree tree) {
        AABB box = null;
        for (BlockPos pos : tree.original.keySet()) {
            box = box == null ? new AABB(pos) : box.minmax(new AABB(pos));
        }
        return box != null && level.getEntitiesOfClass(ServerPlayer.class, box.inflate(0.5)).stream()
                .anyMatch(p -> tree.original.keySet().stream().anyMatch(pos -> p.getBoundingBox().intersects(new AABB(pos))));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0 || FELLED.isEmpty() && BY_BLOCK.isEmpty()) {
            return;
        }
        ServerLevel level = server.getLevel(Galatea.LEVEL);
        if (level == null) {
            return;
        }
        long now = server.getTickCount();
        // half-cut trees nobody came back to
        for (Tree tree : List.copyOf(new HashSet<>(BY_BLOCK.values()))) {
            if (tree.left.size() < tree.total && now - tree.touched > ABANDONED_TICKS && !occupied(level, tree)) {
                regrow(level, tree);
            }
        }
        for (Tree tree : List.copyOf(FELLED)) {
            if (now < tree.regrowAt) {
                continue;
            }
            if (occupied(level, tree)) {
                tree.regrowAt = now + 100;
                continue;
            }
            regrow(level, tree);
            // sparkles over where it stands
            AABB box = null;
            for (BlockPos pos : tree.original.keySet()) {
                box = box == null ? new AABB(pos) : box.minmax(new AABB(pos));
            }
            if (box != null) {
                var c = box.getCenter();
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y, c.z, 30, box.getXsize() / 3, box.getYsize() / 3, box.getZsize() / 3, 0);
            }
        }
    }

    /** Items into the inventory (sacks first, as any gain), the rest dropped. */
    static void give(ServerPlayer player, String id, int count, ItemGains.Source source) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null || count <= 0) {
            return;
        }
        int left = count;
        while (left > 0) {
            int n = Math.min(64, left);
            ItemStack stack = SkyItems.create(def, n);
            ItemGains.give(player, stack, source);
            if (!stack.isEmpty()) {
                player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            left -= n;
        }
    }
}
