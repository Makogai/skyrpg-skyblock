package com.skyrpg.skyblock.island;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The island a new profile starts on, like Hypixel's: a round patch of grass over a tapering lump of dirt and stone,
 * an oak tree, and the portal to the Hub on the north side. Placed block by block (it's small).
 */
final class StarterIsland {
    static final int RADIUS = 10;
    /** The Hub portal: its frame's west column and its depth, relative to the centre. */
    static final int PORTAL_X = -2;
    static final int PORTAL_Z = -7;

    private StarterIsland() {
    }

    static void place(ServerLevel level, BlockPos center) {
        for (int cx = (center.getX() - RADIUS - 2) >> 4; cx <= (center.getX() + RADIUS + 2) >> 4; cx++) {
            for (int cz = (center.getZ() - RADIUS - 2) >> 4; cz <= (center.getZ() + RADIUS + 2) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        for (int dx = -RADIUS - 2; dx <= RADIUS + 2; dx++) {
            for (int dz = -RADIUS - 2; dz <= RADIUS + 2; dz++) {
                double edge = edge(dx, dz);
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > edge) {
                    continue;
                }
                set(level, center.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState());
                // Each layer down is narrower; dirt near the top, then stone.
                for (int depth = 1; depth <= 9; depth++) {
                    double layer = edge * (1 - Math.pow(depth / 10.0, 1.4)) - noise(dx * 3 + depth, dz * 3 - depth) * 1.2;
                    if (distance > layer) {
                        break;
                    }
                    set(level, center.offset(dx, -depth, dz), underside(dx, dz, depth));
                }
            }
        }
        tree(level, center.offset(5, 1, 4));
        portal(level, center.offset(PORTAL_X, 1, PORTAL_Z));
    }

    /** The island's ragged outline: a radius that wobbles with the angle. */
    private static double edge(int dx, int dz) {
        double angle = Math.atan2(dz, dx);
        return RADIUS + Math.sin(angle * 3) * 0.9 + Math.cos(angle * 5 + 1) * 0.6;
    }

    /** A fixed pseudo-random value in [0, 1) for a position, so the island comes out the same every time. */
    private static double noise(int x, int z) {
        long h = Mth.getSeed(x, 0, z);
        return ((h >>> 16) & 0xFFFF) / 65536.0;
    }

    private static BlockState underside(int dx, int dz, int depth) {
        double n = noise(dx, dz * 7 + depth * 13);
        if (depth <= 2 || (depth == 3 && n < 0.5)) {
            return Blocks.DIRT.defaultBlockState();
        }
        if (n < 0.08) {
            return Blocks.COAL_ORE.defaultBlockState();
        }
        if (n < 0.25) {
            return Blocks.ANDESITE.defaultBlockState();
        }
        return n < 0.4 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    private static void tree(ServerLevel level, BlockPos base) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int y = 3; y <= 6; y++) {
            int r = y <= 4 ? 2 : 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                    if ((corner && (y == 6 || noise(dx + y, dz) < 0.5)) || (y == 6 && corner)) {
                        continue;
                    }
                    set(level, base.offset(dx, y, dz), leaves);
                }
            }
        }
        for (int y = 0; y < 5; y++) {
            set(level, base.above(y), Blocks.OAK_LOG.defaultBlockState());
        }
    }

    /** A 4 by 5 frame, open in the middle: the opening is drawn with particles and works like a portal (see IslandPortal). */
    private static void portal(ServerLevel level, BlockPos corner) {
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 5; y++) {
                boolean frame = x == 0 || x == 3 || y == 0 || y == 4;
                set(level, corner.offset(x, y, 0), frame ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
        }
        set(level, corner.offset(1, 4, 0), Blocks.CRYING_OBSIDIAN.defaultBlockState());
        set(level, corner.offset(2, 4, 0), Blocks.CRYING_OBSIDIAN.defaultBlockState());
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
