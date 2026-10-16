package com.skyrpg.skyblock.island;

import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.profile.Profile;
import com.skyrpg.skyblock.profile.Profiles;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Private islands. Every profile's island sits in its own cell of the void {@code skyrpg_skyblock:islands} dimension,
 * {@link #CELL} blocks apart in rows of {@link #ROW}; cell {@code n} is centred on
 * {@code ((n % ROW) * CELL, (n / ROW) * CELL)}. Members may build within {@link #BUILD_RADIUS} of the centre.
 */
public final class Islands {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, SkyRpgSkyblock.id("islands"));
    public static final int CELL = 1024;
    public static final int ROW = 1000;
    /** How far from the centre members can build (Hypixel's island is 160 by 160). */
    public static final int BUILD_RADIUS = 80;
    /** The island's grass level. */
    public static final int SURFACE = 70;
    public static final int MIN_BUILD_Y = 0;
    public static final int MAX_BUILD_Y = 250;

    private Islands() {
    }

    public static boolean isIslands(Level level) {
        return level.dimension().equals(LEVEL);
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    public static BlockPos center(int island) {
        return new BlockPos((island % ROW) * CELL, SURFACE, (island / ROW) * CELL);
    }

    /** The cell this position is in, or -1 outside the grid. */
    public static int cellAt(BlockPos pos) {
        int column = Math.round(pos.getX() / (float) CELL);
        int row = Math.round(pos.getZ() / (float) CELL);
        return column < 0 || column >= ROW || row < 0 ? -1 : row * ROW + column;
    }

    /** The profile whose island this position belongs to, or null (the void between islands). */
    public static Profile ownerAt(BlockPos pos) {
        int cell = cellAt(pos);
        return cell < 0 ? null : Profiles.byIsland(cell);
    }

    /** Whether this position is inside its cell's build area. */
    public static boolean inBuildArea(BlockPos pos) {
        int cell = cellAt(pos);
        if (cell < 0) {
            return false;
        }
        BlockPos center = center(cell);
        return Math.abs(pos.getX() - center.getX()) <= BUILD_RADIUS && Math.abs(pos.getZ() - center.getZ()) <= BUILD_RADIUS
                && pos.getY() >= MIN_BUILD_Y && pos.getY() <= MAX_BUILD_Y;
    }

    /** Where you arrive on an island: on the grass by the centre, facing the portal side. */
    public static Vec3 spawn(int island) {
        return Vec3.atBottomCenterOf(center(island).above()).add(0, 0, 2);
    }

    /** Places the profile's starter island if it hasn't been yet. */
    public static void ensureBuilt(MinecraftServer server, Profile profile) {
        if (profile.built()) {
            return;
        }
        ServerLevel level = level(server);
        if (level == null) {
            return;
        }
        StarterIsland.place(level, center(profile.island()));
        Profiles.built(profile);
        SkyRpgSkyblock.LOGGER.info("Built the {} island in cell {}", profile.name(), profile.island());
    }

    /** To the island of the profile you're playing. */
    public static void sendHome(ServerPlayer player) {
        Profile profile = Profiles.current(player);
        if (profile == null) {
            return;
        }
        ensureBuilt(player.level().getServer(), profile);
        teleport(player, profile, 180);
    }

    /** To someone else's island, as a guest (or to your own if it's yours). */
    public static void visit(ServerPlayer player, Profile profile, String ownerName) {
        ensureBuilt(player.level().getServer(), profile);
        teleport(player, profile, 180);
        Profile mine = Profiles.current(player);
        if (mine == null || !mine.id().equals(profile.id())) {
            player.sendSystemMessage(Component.literal("You're visiting " + ownerName + "'s island (" + profile.name() + ").")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    private static void teleport(ServerPlayer player, Profile profile, float yaw) {
        ServerLevel level = level(player.level().getServer());
        level.getChunk(center(profile.island()));
        player.teleport(new TeleportTransition(level, spawn(profile.island()), Vec3.ZERO, yaw, 0, Set.of(), TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
    }
}
