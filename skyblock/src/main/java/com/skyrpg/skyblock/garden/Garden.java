package com.skyrpg.skyblock.garden;

import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.island.Islands;
import com.skyrpg.skyblock.profile.Profile;
import com.skyrpg.skyblock.profile.Profiles;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's Garden (SkyBlock Level 5): every profile's own 480 x 480 farm in the {@code skyrpg_skyblock:garden} world, in
 * the same cell as its private island. A 5 x 5 grid of 96 x 96 plots around the Barn (the middle plot, a template from
 * {@code tools/builds/garden}); the other 24 unlock in Hypixel's order for Compost, then Compost Bundles, at the Desk.
 * Members build and farm only on unlocked plots; locked ones are fenced. Each plot gives +3 Farming Fortune in the Garden
 * and 5 SkyBlock XP.
 *
 * <p>Harvesting the ten Garden crops here raises their crop milestones (NEU's thresholds; each level 10 Garden XP times
 * the level and 1 SkyBlock XP, decided); Garden XP raises the Garden level (NEU's table, I-XV, 10 SkyBlock XP each).
 */
public final class Garden {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, SkyRpgSkyblock.id("garden"));
    static final int PLOT = 96;
    static final int HALF = 240;
    static final int GROUND = 64;
    static final int REQUIRED_LEVEL = 5;

    /** Garden XP to reach each level (index 0 = level 1: 0). */
    static final long[] LEVEL_XP = {0, 70, 70, 140, 240, 600, 1500, 2000, 2500, 3000, 10000, 10000, 10000, 10000, 10000};

    /** The plots in unlock order: grid column, row, and cost (Compost, or Compost Bundles from plot 12). */
    static final int[][] PLOTS = {{2, 1, 1}, {1, 2, 2}, {3, 2, 4}, {2, 3, 8}, {1, 1, 16}, {3, 1, 24}, {1, 3, 32}, {3, 3, 48},
            {2, 0, 64}, {0, 2, 96}, {4, 2, 128}, {2, 4, 1}, {1, 0, 1}, {3, 0, 2}, {0, 1, 2}, {4, 1, 3}, {0, 3, 3}, {4, 3, 4},
            {1, 4, 5}, {3, 4, 7}, {0, 0, 8}, {4, 0, 10}, {0, 4, 12}, {4, 4, 15}};

    /** The ten Garden crops: key, name, SkyBlock item, how many a block gives at base, and its milestone curve. */
    record Crop(String key, String name, String item, double perBlock, long[] curve) {
    }

    private static final long[] CURVE_A = {30, 50, 80, 200, 350, 700, 1500, 2500, 3500, 5000, 6500, 8000, 10000, 20000, 35000, 50000, 75000,
            100000, 175000, 250000, 375000, 400000, 450000, 650000, 800000};
    private static final long[] CURVE_B = {100, 150, 250, 500, 1000, 2000, 4500, 9000, 12000, 15000, 20000, 25000, 35000, 70000, 120000,
            180000, 250000, 350000, 600000, 850000, 1100000, 1400000, 1800000, 2200000, 2600000};
    private static final long[] CURVE_MELON = {150, 250, 400, 1000, 1750, 3500, 7500, 12500, 17500, 25000, 32500, 40000, 50000, 100000, 175000,
            250000, 375000, 500000, 875000, 1250000, 1875000, 2000000, 2250000, 3250000, 4000000};
    private static final long[] CURVE_C = {60, 100, 160, 400, 700, 1400, 3000, 5000, 7000, 10000, 13000, 16000, 20000, 40000, 70000, 100000,
            150000, 200000, 350000, 500000, 750000, 800000, 900000, 1300000, 1600000};
    private static final long[] CURVE_D = {90, 150, 240, 600, 1050, 2100, 4500, 7500, 10500, 15000, 19500, 24000, 30000, 60000, 105000, 150000,
            225000, 300000, 525000, 750000, 1125000, 1200000, 1350000, 1950000, 2400000};

    static final List<Crop> CROPS = List.of(
            new Crop("wheat", "Wheat", "WHEAT", 1, CURVE_A),
            new Crop("carrot", "Carrot", "CARROT_ITEM", 3, CURVE_B),
            new Crop("potato", "Potato", "POTATO_ITEM", 3, CURVE_B),
            new Crop("pumpkin", "Pumpkin", "PUMPKIN", 1, CURVE_A),
            new Crop("melon", "Melon", "MELON", 5, CURVE_MELON),
            new Crop("sugar_cane", "Sugar Cane", "SUGAR_CANE", 1, CURVE_C),
            new Crop("cactus", "Cactus", "CACTUS", 1, CURVE_C),
            new Crop("cocoa", "Cocoa Beans", "INK_SACK:3", 3, CURVE_D),
            new Crop("mushroom", "Mushroom", "RED_MUSHROOM", 1, CURVE_A),
            new Crop("nether_wart", "Nether Wart", "NETHER_STALK", 2.5, CURVE_D));
    static final int MAX_MILESTONE = 46;

    private Garden() {
    }

    public static void init() {
        GardenRules.init();
        GardenMenu.init();
        Composter.init();
        Visitors.init();
        ServerTickEvents.END_SERVER_TICK.register(Garden::tick);
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer && isGarden(level) && !player.isCreative()) {
                harvested(server, serverPlayer, pos, state);
            }
        });
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Garden Plots";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                if (!isGarden(player.level())) {
                    return;
                }
                GardenData.State state = state(player);
                if (state != null && state.plots > 0) {
                    out.add(StatTypes.FARMING_FORTUNE, 3.0 * state.plots);
                }
            }
        });
    }

    public static boolean isGarden(Level level) {
        return level.dimension().equals(LEVEL);
    }

    static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    /** The middle of a profile's Garden (its island cell's centre, at grass level). */
    static BlockPos center(Profile profile) {
        BlockPos c = Islands.center(profile.island());
        return new BlockPos(c.getX(), GROUND, c.getZ());
    }

    /** The profile whose Garden this is (by cell), or null. */
    static Profile ownerAt(BlockPos pos) {
        return Islands.ownerAt(new BlockPos(pos.getX(), Islands.SURFACE, pos.getZ()));
    }

    /** This player's current profile's Garden, or null. */
    static GardenData.State state(ServerPlayer player) {
        Profile profile = Profiles.current(player);
        return profile == null ? null : GardenData.get(player.level().getServer()).of(profile.id());
    }

    static GardenData.State state(MinecraftServer server, Profile profile) {
        return GardenData.get(server).of(profile.id());
    }

    /** The plot grid cell (column, row) of a position in its Garden, or null outside the Garden. */
    static int[] gridAt(BlockPos pos) {
        Profile owner = ownerAt(pos);
        if (owner == null) {
            return null;
        }
        BlockPos c = center(owner);
        int dx = pos.getX() - (c.getX() - HALF);
        int dz = pos.getZ() - (c.getZ() - HALF);
        if (dx < 0 || dz < 0 || dx >= PLOT * 5 || dz >= PLOT * 5) {
            return null;
        }
        return new int[] {dx / PLOT, dz / PLOT};
    }

    /** The sidebar's area: "Plot - 3" on a plot, "The Garden" in the Barn's plot. */
    public static String areaName(BlockPos pos) {
        int[] grid = gridAt(pos);
        int plot = grid == null ? 0 : plotNumber(grid[0], grid[1]);
        return plot > 0 ? "Plot - " + plot : "The Garden";
    }

    /** Which plot (1-24) is at this grid cell, 0 for the Barn. */
    static int plotNumber(int column, int row) {
        if (column == 2 && row == 2) {
            return 0;
        }
        for (int i = 0; i < PLOTS.length; i++) {
            if (PLOTS[i][0] == column && PLOTS[i][1] == row) {
                return i + 1;
            }
        }
        return -1;
    }

    /** Garden level for this much XP (1-15). */
    static int levelFor(long xp) {
        int level = 1;
        long left = xp;
        while (level < LEVEL_XP.length && left >= LEVEL_XP[level]) {
            left -= LEVEL_XP[level];
            level++;
        }
        return level;
    }

    static int level(GardenData.State state) {
        return levelFor(state.xp);
    }

    /** The Garden level each plot needs (Hypixel's groups: plots 1-4 at I, 5-8 at III, 9-12 at V, 13-24 at VII). */
    static int plotLevel(int plot) {
        return plot <= 4 ? 1 : plot <= 8 ? 3 : plot <= 12 ? 5 : 7;
    }

    /** A crop milestone's level for a harvested count. */
    static int milestone(Crop crop, long count) {
        int level = 0;
        long need = 0;
        while (level < MAX_MILESTONE) {
            long step = level < crop.curve().length ? crop.curve()[level] : crop.curve()[crop.curve().length - 1];
            if (count < need + step) {
                break;
            }
            need += step;
            level++;
        }
        return level;
    }

    /** Crops harvested needed for the next milestone level from here (0 at the top). */
    static long toNext(Crop crop, long count) {
        int level = milestone(crop, count);
        if (level >= MAX_MILESTONE) {
            return 0;
        }
        long need = 0;
        for (int i = 0; i <= level; i++) {
            need += i < crop.curve().length ? crop.curve()[i] : crop.curve()[crop.curve().length - 1];
        }
        return need - count;
    }

    // ---------------------------------------------------------------- going there

    /** To your Garden (SkyBlock Level 5). */
    public static void send(ServerPlayer player) {
        Profile profile = Profiles.current(player);
        if (profile == null) {
            return;
        }
        if (SkyBlockXp.level(player) < REQUIRED_LEVEL && !player.isCreative()) {
            player.sendSystemMessage(Component.literal("The Garden opens at SkyBlock Level " + REQUIRED_LEVEL + ".").withStyle(ChatFormatting.RED));
            return;
        }
        ServerLevel level = level(player.level().getServer());
        if (level == null) {
            return;
        }
        GardenData.State state = state(player.level().getServer(), profile);
        BlockPos c = center(profile);
        if (!state.built) {
            build(level, profile, state);
        }
        Vec3 arrival = Vec3.atBottomCenterOf(c.offset(0, 1, 40));
        player.teleport(new TeleportTransition(level, arrival, Vec3.ZERO, 180, 0, Set.of(), TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
    }

    /** First visit: the Barn, and fences round the 24 locked plots. */
    private static void build(ServerLevel level, Profile profile, GardenData.State state) {
        BlockPos c = center(profile);
        BlockPos origin = new BlockPos(c.getX() - 48, GROUND - 4, c.getZ() - 48);
        for (int cx = (c.getX() - HALF) >> 4; cx <= (c.getX() + HALF) >> 4; cx++) {
            for (int cz = (c.getZ() - HALF) >> 4; cz <= (c.getZ() + HALF) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        StructureTemplate template = level.getServer().getStructureTemplateManager()
                .get(Identifier.fromNamespaceAndPath("skyrpg_skyblock", "garden/barn")).orElse(null);
        if (template != null) {
            template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_CLIENTS);
        }
        for (int i = 0; i < PLOTS.length; i++) {
            fence(level, c, PLOTS[i][0], PLOTS[i][1], true);
        }
        state.built = true;
        SkyRpgSkyblock.LOGGER.info("Built the {} Garden in cell {}", profile.name(), profile.island());
    }

    /** Fences a plot's edge (or takes the fence down where no other locked plot needs it). */
    static void fence(ServerLevel level, BlockPos c, int column, int row, boolean up) {
        int x0 = c.getX() - HALF + column * PLOT;
        int z0 = c.getZ() - HALF + row * PLOT;
        for (int i = 0; i < PLOT; i++) {
            for (int[] at : new int[][] {{x0 + i, z0}, {x0 + i, z0 + PLOT - 1}, {x0, z0 + i}, {x0 + PLOT - 1, z0 + i}}) {
                BlockPos pos = new BlockPos(at[0], GROUND + 1, at[1]);
                if (up) {
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
                    }
                } else if (level.getBlockState(pos).is(Blocks.OAK_FENCE)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Unlocks the next plot (paid by the caller): its fence comes down and it fills with wild growth to clear. */
    static void unlock(ServerPlayer player, Profile profile, GardenData.State state) {
        ServerLevel level = level(player.level().getServer());
        int[] plot = PLOTS[state.plots];
        state.plots++;
        GardenData.get(player.level().getServer()).setDirty();
        if (level != null) {
            BlockPos c = center(profile);
            fence(level, c, plot[0], plot[1], false);
            // Fences between this plot and still-locked neighbours go back up.
            for (int i = state.plots; i < PLOTS.length; i++) {
                fence(level, c, PLOTS[i][0], PLOTS[i][1], true);
            }
            overgrow(level, c, plot[0], plot[1]);
        }
        SkyBlockXp.give(player, 5, "Garden plot");
        Notice.of(Notice.Kind.FARMING).tag("GARDEN").panel()
                .line(Component.literal(" +3 Farming Fortune in the Garden, +5 SkyBlock XP").withStyle(ChatFormatting.GRAY))
                .line(Component.literal(" Clear its wild growth to farm it.").withStyle(ChatFormatting.GRAY))
                .send(player, Component.literal("Plot " + state.plots + " unlocked!").withStyle(ChatFormatting.GREEN));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    /** A fresh plot's wild growth: tall grass, flowers, bushes and the odd tree. */
    private static void overgrow(ServerLevel level, BlockPos c, int column, int row) {
        var random = level.getRandom();
        int x0 = c.getX() - HALF + column * PLOT;
        int z0 = c.getZ() - HALF + row * PLOT;
        BlockState[] plants = {Blocks.SHORT_GRASS.defaultBlockState(), Blocks.FERN.defaultBlockState(), Blocks.DANDELION.defaultBlockState(),
                Blocks.POPPY.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState()};
        for (int x = x0 + 2; x < x0 + PLOT - 2; x++) {
            for (int z = z0 + 2; z < z0 + PLOT - 2; z++) {
                BlockPos pos = new BlockPos(x, GROUND + 1, z);
                if (!level.getBlockState(pos).isAir()) {
                    continue;
                }
                double roll = random.nextDouble();
                if (roll < 0.35) {
                    level.setBlock(pos, plants[random.nextInt(plants.length)], Block.UPDATE_CLIENTS);
                } else if (roll < 0.36) {
                    level.setBlock(pos, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                } else if (roll < 0.362 && x > x0 + 4 && x < x0 + PLOT - 5 && z > z0 + 4 && z < z0 + PLOT - 5) {
                    tree(level, pos, random.nextBoolean() ? Blocks.OAK_LOG : Blocks.BIRCH_LOG,
                            random.nextBoolean() ? Blocks.OAK_LEAVES : Blocks.BIRCH_LEAVES);
                }
            }
        }
    }

    private static void tree(ServerLevel level, BlockPos base, Block log, Block leaves) {
        int height = 4 + level.getRandom().nextInt(2);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = height - 2; dy <= height + 1; dy++) {
                    int r = dy > height ? 1 : 2;
                    if (Math.abs(dx) <= r && Math.abs(dz) <= r && !(Math.abs(dx) == 2 && Math.abs(dz) == 2)) {
                        BlockPos pos = base.offset(dx, dy, dz);
                        if (level.getBlockState(pos).isAir() || level.getBlockState(pos).canBeReplaced()) {
                            level.setBlock(pos, leaves.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true),
                                    Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
        for (int dy = 0; dy < height; dy++) {
            level.setBlock(base.above(dy), log.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ---------------------------------------------------------------- harvesting

    /** The Garden crop a broken block is (grown), or null. */
    static Crop cropOf(BlockState state) {
        Block block = state.getBlock();
        String key = null;
        if (block == Blocks.WHEAT && ((CropBlock) block).isMaxAge(state)) {
            key = "wheat";
        } else if (block == Blocks.CARROTS && ((CropBlock) block).isMaxAge(state)) {
            key = "carrot";
        } else if (block == Blocks.POTATOES && ((CropBlock) block).isMaxAge(state)) {
            key = "potato";
        } else if (block == Blocks.PUMPKIN) {
            key = "pumpkin";
        } else if (block == Blocks.MELON) {
            key = "melon";
        } else if (block == Blocks.SUGAR_CANE) {
            key = "sugar_cane";
        } else if (block == Blocks.CACTUS) {
            key = "cactus";
        } else if (block == Blocks.COCOA && state.getValue(CocoaBlock.AGE) >= 2) {
            key = "cocoa";
        } else if (block == Blocks.RED_MUSHROOM || block == Blocks.BROWN_MUSHROOM) {
            key = "mushroom";
        } else if (block == Blocks.NETHER_WART && state.getValue(NetherWartBlock.AGE) >= 3) {
            key = "nether_wart";
        }
        if (key == null) {
            return null;
        }
        for (Crop crop : CROPS) {
            if (crop.key().equals(key)) {
                return crop;
            }
        }
        return null;
    }

    private static void harvested(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        Crop crop = cropOf(state);
        Profile owner = ownerAt(pos);
        if (crop == null || owner == null) {
            return;
        }
        GardenData.State garden = state(level.getServer(), owner);
        double fortune = Stats.get(player, StatTypes.FARMING_FORTUNE);
        long count = Math.max(1, Math.round(crop.perBlock() * (1 + fortune / 100)));
        long before = garden.crops.getOrDefault(crop.key(), 0L);
        long after = before + count;
        garden.crops.put(crop.key(), after);
        int from = milestone(crop, before);
        int to = milestone(crop, after);
        for (int lvl = from + 1; lvl <= to; lvl++) {
            long xp = 10L * lvl;
            SkyBlockXp.give(player, 1, crop.name() + " milestone");
            Notice.of(Notice.Kind.FARMING).tag("CROP MILESTONE").panel()
                    .line(Component.literal(" +" + xp + " Garden XP  ·  +1 SkyBlock XP").withStyle(ChatFormatting.GRAY))
                    .send(player, Component.literal(crop.name() + " " + (lvl - 1) + " ➜ " + lvl).withStyle(ChatFormatting.GREEN));
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
            giveXp(player, garden, xp);
        }
    }

    /** Garden XP; level-ups are announced with their 10 SkyBlock XP. */
    static void giveXp(ServerPlayer player, GardenData.State garden, long xp) {
        int before = level(garden);
        garden.xp += xp;
        GardenData.get(player.level().getServer()).setDirty();
        int after = level(garden);
        for (int lvl = before + 1; lvl <= after; lvl++) {
            SkyBlockXp.give(player, 10, "Garden level");
            Notice.of(Notice.Kind.FARMING).tag("GARDEN LEVEL UP").panel()
                    .line(Component.literal(" +10 SkyBlock XP").withStyle(ChatFormatting.AQUA))
                    .send(player, Component.literal("Garden " + Msg.roman(lvl - 1) + " ➜ " + Msg.roman(lvl)).withStyle(ChatFormatting.GREEN));
            Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        }
    }

    // ---------------------------------------------------------------- every second

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel level = level(server);
        if (level == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            // Stay inside your Garden's 480 x 480.
            Profile owner = ownerAt(player.blockPosition());
            int[] grid = gridAt(player.blockPosition());
            Profile mine = Profiles.current(player);
            if (owner == null || grid == null) {
                Profile back = mine != null ? mine : owner;
                if (back != null) {
                    BlockPos c = center(back);
                    player.teleportTo(level, c.getX() + 0.5, GROUND + 1, c.getZ() + 40.5, Set.of(), 180, 0, true);
                    player.sendOverlayMessage(Component.literal("That's the edge of the Garden.").withStyle(ChatFormatting.RED));
                }
            }
        }
    }
}
