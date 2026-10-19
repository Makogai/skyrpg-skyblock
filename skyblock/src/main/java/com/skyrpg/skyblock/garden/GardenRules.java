package com.skyrpg.skyblock.garden;

import com.skyrpg.skyblock.profile.Profile;
import com.skyrpg.skyblock.profile.Profiles;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;

/**
 * Who may change what in a Garden: its profile's members, on its unlocked plots (not the Barn, not locked plots).
 * Right-clicking the Desk or the Composter opens their screens instead.
 */
final class GardenRules {
    private GardenRules() {
    }

    static void init() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> mayBuild(player, level, pos));
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
                mayBuild(player, level, pos) ? InteractionResult.PASS : InteractionResult.FAIL);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!Garden.isGarden(level) || level.isClientSide()) {
                return InteractionResult.PASS;
            }
            BlockPos against = hit.getBlockPos();
            if (player instanceof ServerPlayer server && member(server, against)) {
                Profile owner = Garden.ownerAt(against);
                BlockPos c = Garden.center(owner);
                int dx = against.getX() - c.getX();
                int dy = against.getY() - c.getY();
                int dz = against.getZ() - c.getZ();
                GardenLayout layout = GardenLayout.get();
                if (layout.desk().contains(dx, dy, dz)) {
                    GardenMenu.open(server, GardenMenu.Tab.PLOTS);
                    return InteractionResult.SUCCESS;
                }
                if (layout.composter().contains(dx, dy, dz)) {
                    GardenMenu.open(server, GardenMenu.Tab.COMPOSTER);
                    return InteractionResult.SUCCESS;
                }
            }
            BlockPos placed = against.relative(hit.getDirection());
            if (mayBuild(player, level, against) && mayBuild(player, level, placed)) {
                return InteractionResult.PASS;
            }
            // Doors, gates and chests in the Barn still work for members.
            if (player instanceof ServerPlayer server && member(server, against) && !(player.getItemInHand(hand).getItem() instanceof BlockItem)) {
                return InteractionResult.PASS;
            }
            return refuse(player);
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            Item item = player.getItemInHand(hand).getItem();
            boolean changesWorld = item instanceof BlockItem || item instanceof BucketItem || item instanceof SpawnEggItem;
            return changesWorld && !mayBuild(player, level, player.blockPosition()) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }

    /** Whether this player plays the profile that owns the Garden at this position. */
    static boolean member(ServerPlayer player, BlockPos pos) {
        Profile owner = Garden.ownerAt(pos);
        Profile mine = Profiles.current(player);
        return owner != null && mine != null && owner.id().equals(mine.id());
    }

    /** Members on unlocked plots (and creative players anywhere); elsewhere in the Garden nobody. */
    static boolean mayBuild(Player player, Level level, BlockPos pos) {
        if (!Garden.isGarden(level) || player.isCreative() || level.isClientSide()) {
            return true;
        }
        if (!(player instanceof ServerPlayer server) || !member(server, pos)) {
            return false;
        }
        int[] grid = Garden.gridAt(pos);
        if (grid == null || pos.getY() < Garden.GROUND - 20 || pos.getY() > Garden.GROUND + 100) {
            return false;
        }
        int plot = Garden.plotNumber(grid[0], grid[1]);
        GardenData.State state = Garden.state(server);
        return plot > 0 && state != null && plot <= state.plots;
    }

    private static InteractionResult refuse(Player player) {
        if (player instanceof ServerPlayer server) {
            int[] grid = Garden.gridAt(server.blockPosition());
            boolean barn = grid != null && grid[0] == 2 && grid[1] == 2;
            server.sendOverlayMessage(Component.literal(!member(server, server.blockPosition()) ? "You can't do that in someone else's Garden."
                    : barn ? "The Barn's plot can't be changed." : "Unlock this plot at the Desk first.").withStyle(ChatFormatting.RED));
        }
        return InteractionResult.FAIL;
    }
}
