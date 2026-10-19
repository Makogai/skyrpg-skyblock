package com.skyrpg.skyblock.hollows;

import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Up to three Fairy Grottos in a Crystal Hollows (Hypixel's rare rooms of Jasper): a round pink cave of moss and petals
 * whose walls are veined with Jasper (magenta glass), with Butterflies fluttering in it that may drop a Jasper Crystal.
 * They're carved into the rock once for each new Hollows (their places are kept in the world folder), somewhere under
 * 200 blocks from the middle that isn't one of the structures.
 *
 * <p>Decided: no water to fish a Fairy Soul out of (the souls are placed by the structures' builds), the Butterflies are
 * bees named Butterfly (level 5, 400 health, 20 damage) that drop a Jasper Crystal one time in fifty, and a grotto isn't a
 * zone of its own (the scoreboard keeps saying where you are in the Hollows).
 */
public final class FairyGrottos {
    private static final String FILE = "skyrpg_hollows_grottos.txt";
    private static final String TAG = "skyrpg_butterfly";
    private static final int RADIUS = 7;
    /** Structures' centres the grottos keep away from. */
    private static final int[][] AVOID = {{0, 0}, {-140, -140}, {145, -145}, {-145, 145}, {145, 145}, {0, 160}};
    private static final List<BlockPos> GROTTOS = new ArrayList<>();

    private FairyGrottos() {
    }

    static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(FairyGrottos::load);
        ServerTickEvents.END_SERVER_TICK.register(FairyGrottos::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.entityTags().contains(TAG) && source.getEntity() instanceof ServerPlayer killer
                    && killer.getRandom().nextInt(50) == 0) {
                HollowsState.give(killer, "JASPER_CRYSTAL", 1, com.skyrpg.core.event.ItemGains.Source.COMBAT);
                killer.sendSystemMessage(net.minecraft.network.chat.Component.literal("RARE DROP! Jasper Crystal")
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
            }
        });
    }

    public static List<BlockPos> all() {
        return List.copyOf(GROTTOS);
    }

    /** Reads the grottos of this Hollows (or makes them if it's a new one). */
    private static void load(MinecraftServer server) {
        ServerLevel level = server.getLevel(CrystalHollows.LEVEL);
        if (level == null) {
            return;
        }
        Path file = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
        GROTTOS.clear();
        try {
            if (Files.exists(file)) {
                List<String> lines = Files.readAllLines(file);
                if (!lines.isEmpty() && lines.get(0).trim().equals(String.valueOf(HollowsState.epoch))) {
                    for (String line : lines.subList(1, lines.size())) {
                        String[] p = line.trim().split(",");
                        if (p.length == 3) {
                            GROTTOS.add(new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])));
                        }
                    }
                    return;
                }
            }
            RandomSource random = RandomSource.create(HollowsState.epoch);
            for (int i = 0; i < 3; i++) {
                BlockPos pos = spot(random);
                carve(level, pos, random);
                GROTTOS.add(pos);
            }
            save(file);
        } catch (IOException | NumberFormatException e) {
            SkyRpgSkyblock.LOGGER.warn("Couldn't make the Fairy Grottos", e);
        }
    }

    private static void save(Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(String.valueOf(HollowsState.epoch));
        GROTTOS.forEach(p -> lines.add(p.getX() + "," + p.getY() + "," + p.getZ()));
        Files.write(file, lines);
    }

    private static BlockPos spot(RandomSource random) {
        for (int tries = 0; tries < 100; tries++) {
            int x = random.nextInt(360) - 180;
            int z = random.nextInt(360) - 180;
            boolean clear = true;
            for (int[] avoid : AVOID) {
                clear &= Math.hypot(x - avoid[0], z - avoid[1]) > 70;
            }
            for (BlockPos other : GROTTOS) {
                clear &= Math.hypot(x - other.getX(), z - other.getZ()) > 80;
            }
            if (clear) {
                return new BlockPos(x, 75 + random.nextInt(55), z);
            }
        }
        return new BlockPos(100, 100, 0);
    }

    /** Admin: a grotto at a spot, remembered for this Hollows. */
    static void place(ServerLevel level, BlockPos pos) {
        carve(level, pos, level.getRandom());
        GROTTOS.add(pos);
        try {
            save(level.getServer().getWorldPath(LevelResource.ROOT).resolve(FILE));
        } catch (IOException e) {
            SkyRpgSkyblock.LOGGER.warn("Couldn't save the Fairy Grottos", e);
        }
    }

    /** A round cave: air inside, a shell of Hard Stone and Jasper, a mossy floor with petals, glowstone for light. */
    private static void carve(ServerLevel level, BlockPos center, RandomSource random) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState jasper = Blocks.STAINED_GLASS.pick(net.minecraft.world.item.DyeColor.MAGENTA).defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -RADIUS - 2; dx <= RADIUS + 2; dx++) {
            for (int dy = -RADIUS - 2; dy <= RADIUS + 2; dy++) {
                for (int dz = -RADIUS - 2; dz <= RADIUS + 2; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy * 1.4 + dz * dz);
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (d <= RADIUS - 0.5) {
                        level.setBlock(pos, dy == -4 && d < RADIUS - 1 ? Blocks.MOSS_BLOCK.defaultBlockState() : dy < -4 ? stone : air, Block.UPDATE_CLIENTS);
                        if (dy == -3 && d < RADIUS - 1.5 && random.nextInt(3) == 0) {
                            level.setBlock(pos, Blocks.PINK_PETALS.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    } else if (d <= RADIUS + 1.5) {
                        int roll = random.nextInt(100);
                        level.setBlock(pos, roll < 14 ? jasper : roll < 17 ? Blocks.GLOWSTONE.defaultBlockState() : stone, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(CrystalHollows.LEVEL);
        if (level == null || GROTTOS.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (BlockPos grotto : GROTTOS) {
            Vec3 center = Vec3.atCenterOf(grotto);
            if (now % 10 == 0 && level.getNearestPlayer(center.x, center.y, center.z, 24, false) != null) {
                level.sendParticles(ParticleTypes.CHERRY_LEAVES, center.x, center.y + 2, center.z, 3, 4, 2, 4, 0.01);
            }
            if (now % 200 != 0 || level.getNearestPlayer(center.x, center.y, center.z, 24, false) == null) {
                continue;
            }
            int butterflies = level.getEntities(EntityTypes.BEE, b -> b.entityTags().contains(TAG) && b.position().distanceToSqr(center) < 400).size();
            if (butterflies < 5) {
                Mob bee = HollowsMobs.spawn(level, EntityTypes.BEE, center.add(level.getRandom().nextDouble() * 6 - 3, -1, level.getRandom().nextDouble() * 6 - 3),
                        "Butterfly", 5, 400, 20, 4, TAG);
                if (bee != null) {
                    bee.setSilent(true);
                }
            }
        }
    }
}
