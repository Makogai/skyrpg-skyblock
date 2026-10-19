package com.skyrpg.skyblock.garden;

import java.nio.file.Files;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Where things are in the Barn plot, as offsets from the Garden's middle (grass level): our Barn's
 * ({@code tools/builds/garden}), or Hypixel's when its Barn is installed ({@code tools/worlds/hypixel_garden.py} leaves
 * {@code hypixel_garden.json} beside the structure in the world's {@code generated/} folder). NPCs and the arrival
 * stand on whatever ground is nearest their spot ({@link #ground}), as the plot isn't flat.
 */
record GardenLayout(Spot arrival, Box desk, Box composter, Spot skymart, Spot phillip, Spot jacob, Spot anita, List<Spot> visitors) {
    /** A spot and the way whoever stands there faces. */
    record Spot(int x, int y, int z, float yaw) {
        BlockPos at(BlockPos center) {
            return center.offset(x, y, z);
        }
    }

    /** A box of blocks that open a screen when used. */
    record Box(int x0, int y0, int z0, int x1, int y1, int z1) {
        boolean contains(int dx, int dy, int dz) {
            return dx >= x0 && dx <= x1 && dy >= y0 && dy <= y1 && dz >= z0 && dz <= z1;
        }
    }

    static final GardenLayout OURS = new GardenLayout(new Spot(0, 1, 40, 180), new Box(0, 1, -18, 0, 2, -18), new Box(16, 1, -8, 20, 4, -4),
            new Spot(-18, 1, -2, 90), new Spot(-14, 1, 10, 90), new Spot(-5, 1, 36, -90), new Spot(5, 1, 36, 90),
            List.of(new Spot(-8, 1, 18, 180), new Spot(-4, 1, 20, 180), new Spot(0, 1, 21, 180), new Spot(4, 1, 20, 180), new Spot(8, 1, 18, 180)));
    /**
     * Hypixel's Barn: the Desk is the corner with the crafting table and chests inside the Barn, the Composter the
     * cauldron pen under the hay roof east of it; SkyMart and Pesthunter Phillip where NEU has them, the visitors on the
     * lawn either side of the Barn's door, Jacob and Anita down the path (decided: Hypixel has them in the Hub).
     */
    static final GardenLayout HYPIXEL = new GardenLayout(new Spot(0, 1, 12, 180), new Box(-8, 2, -30, -3, 6, -25), new Box(22, 1, -44, 32, 6, -23),
            new Spot(-5, 2, -27, 0), new Spot(-26, 1, -14, -90), new Spot(-4, 1, 28, 90), new Spot(6, 1, 28, -90),
            List.of(new Spot(-12, 1, -5, 0), new Spot(-7, 1, -3, 0), new Spot(-4, 1, 2, 90), new Spot(5, 1, -3, 0), new Spot(10, 1, -5, 0)));

    private static GardenLayout current = OURS;

    static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> current = detect(server));
    }

    static GardenLayout get() {
        return current;
    }

    private static GardenLayout detect(MinecraftServer server) {
        boolean hypixel = Files.exists(server.getWorldPath(LevelResource.GENERATED_DIR).resolve("skyrpg_skyblock/structure/garden/hypixel_garden.json"));
        return hypixel ? HYPIXEL : OURS;
    }

    /** The nearest place to stand at a spot (a few blocks up or down): air for feet and head, something solid below. */
    static BlockPos ground(ServerLevel level, BlockPos at) {
        for (int d = 0; d <= 4; d++) {
            for (int sign : new int[] {1, -1}) {
                BlockPos pos = at.above(d * sign);
                if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                        && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                        && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
                    return pos;
                }
                if (d == 0) {
                    break;
                }
            }
        }
        return at;
    }
}
