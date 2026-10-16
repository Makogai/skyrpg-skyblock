package com.skyrpg.skyblock.island;

import com.skyrpg.hub.Hub;
import com.skyrpg.skyblock.profile.Profile;
import com.skyrpg.skyblock.profile.Profiles;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * How islands behave: only the profile's members (playing that profile) build on an island, and only inside its
 * build area; guests look but don't touch. The portal on every island leads to the Hub. A new profile starts on its
 * island, and dying on an island puts you back on your own.
 */
public final class IslandRules {
    private static final int TICKS = 5;
    private static final Map<UUID, Long> PORTAL_COOLDOWN = new ConcurrentHashMap<>();

    private IslandRules() {
    }

    public static void init() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> mayBuild(player, level, pos));
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                mayBuild(player, level, pos) ? InteractionResult.PASS : InteractionResult.FAIL);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            BlockPos against = hit.getBlockPos();
            BlockPos placed = against.relative(hit.getDirection());
            return mayBuild(player, level, against) && mayBuild(player, level, placed) ? InteractionResult.PASS : refuse(player, level);
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            Item item = player.getItemInHand(hand).getItem();
            boolean changesWorld = item instanceof BlockItem || item instanceof BucketItem || item instanceof SpawnEggItem;
            return changesWorld && !mayBuild(player, level, player.blockPosition()) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                Islands.isIslands(level) && !isMember(player, entity.blockPosition()) && !player.isCreative()
                        ? InteractionResult.FAIL : InteractionResult.PASS);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            if (Profiles.takeFresh(player)) {
                Islands.sendHome(player);
                Profile profile = Profiles.current(player);
                player.sendSystemMessage(Component.literal("Welcome to your island! You're playing on the ")
                        .append(Component.literal(profile.name()).withStyle(ChatFormatting.GOLD))
                        .append(" profile. Walk through the portal to visit the Hub.").withStyle(ChatFormatting.GREEN));
            }
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
            if (!alive && Islands.isIslands(oldPlayer.level())) {
                Islands.sendHome(player);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(IslandRules::tick);
    }

    /** Whether this player is a member of the island at this position, playing that profile right now. */
    public static boolean isMember(Player player, BlockPos pos) {
        Profile owner = Islands.ownerAt(pos);
        if (owner == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        Profile current = Profiles.current(serverPlayer);
        return current != null && current.id().equals(owner.id());
    }

    /** Off the islands anything goes (other mods guard their places); on them, members inside the build area, and creative. */
    static boolean mayBuild(Player player, Level level, BlockPos pos) {
        if (!Islands.isIslands(level) || player.isCreative()) {
            return true;
        }
        if (level.isClientSide()) {
            return true; // the server decides
        }
        return Islands.inBuildArea(pos) && isMember(player, pos);
    }

    private static InteractionResult refuse(Player player, Level level) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendOverlayMessage(Component.literal(isMember(player, player.blockPosition())
                    ? "You can't build outside your island." : "You can't do that on someone else's island.").withStyle(ChatFormatting.RED));
        }
        return InteractionResult.FAIL;
    }

    /** The portal's opening on the island in this cell. */
    static AABB portal(int cell) {
        BlockPos corner = Islands.center(cell).offset(StarterIsland.PORTAL_X + 1, 2, StarterIsland.PORTAL_Z);
        return new AABB(corner.getX(), corner.getY(), corner.getZ(), corner.getX() + 2, corner.getY() + 3, corner.getZ() + 1);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % TICKS != 0) {
            return;
        }
        ServerLevel level = Islands.level(server);
        if (level == null) {
            return;
        }
        long now = server.getTickCount();
        // Copy: warping a player to the Hub removes them from level.players() mid-loop.
        for (ServerPlayer player : List.copyOf(level.players())) {
            int cell = Islands.cellAt(player.blockPosition());
            if (cell < 0) {
                continue;
            }
            AABB opening = portal(cell);
            if (player.distanceToSqr(opening.getCenter()) < 48 * 48) {
                level.sendParticles(player, ParticleTypes.PORTAL, true, false, opening.getCenter().x, opening.getCenter().y, opening.getCenter().z,
                        12, 0.5, 0.7, 0.1, 0.4);
            }
            if (player.getBoundingBox().intersects(opening) && now >= PORTAL_COOLDOWN.getOrDefault(player.getUUID(), 0L)) {
                PORTAL_COOLDOWN.put(player.getUUID(), now + 60);
                player.sendSystemMessage(Component.literal("Warping you to the Hub...").withStyle(ChatFormatting.GRAY));
                Hub.sendToHub(player);
            }
        }
    }
}
