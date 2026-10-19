package com.skyrpg.skyblock.enchant;

import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SkyStacks;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The farming enchantments that act on a crop block: Delicate (a tool that won't break stems or crops that haven't grown)
 * and Replenish (a broken crop is planted again from your inventory), and Dedication's Fortune, which the Garden asks for
 * when it counts a harvest. Harvesting, Cultivating, the Turbo enchants, Pesterminator and Green Thumb are stats
 * ({@link EnchantEffects}).
 */
public final class EnchantFarming {
    private EnchantFarming() {
    }

    public static void init() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            // Delicate leaves stems and young crops alone (the block stays, nothing drops).
            return !(player instanceof ServerPlayer && ItemMods.enchantment(player.getMainHandItem(), "delicate") > 0 && young(state));
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer && !player.isCreative()
                    && ItemMods.enchantment(player.getMainHandItem(), "replenish") > 0) {
                replant(server, serverPlayer, pos, state);
            }
        });
    }

    /** A stem, or a crop that isn't fully grown. */
    private static boolean young(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof StemBlock) {
            return true;
        }
        if (block instanceof CropBlock crop) {
            return !crop.isMaxAge(state);
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) < NetherWartBlock.MAX_AGE;
        }
        if (block instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) < CocoaBlock.MAX_AGE;
        }
        return false;
    }

    /** Replenish: a grown crop, nether wart or cocoa pod you broke grows back from a seed (or the crop itself) in your inventory. */
    private static void replant(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        BlockState fresh;
        String seed;
        if (block instanceof CropBlock crop && crop.isMaxAge(state)) {
            fresh = crop.getStateForAge(0);
            seed = block == net.minecraft.world.level.block.Blocks.WHEAT ? "SEEDS"
                    : block == net.minecraft.world.level.block.Blocks.CARROTS ? "CARROT_ITEM"
                    : block == net.minecraft.world.level.block.Blocks.POTATOES ? "POTATO_ITEM" : null;
        } else if (block instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE) {
            fresh = state.setValue(NetherWartBlock.AGE, 0);
            seed = "NETHER_STALK";
        } else if (block instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE) {
            fresh = state.setValue(CocoaBlock.AGE, 0);
            seed = "INK_SACK:3";
        } else {
            return;
        }
        if (seed == null || !level.getBlockState(pos).isAir() || !SkyStacks.take(player.getInventory(), seed, 1)) {
            return;
        }
        level.setBlock(pos, fresh, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.6f, 1.0f);
    }

    /** Dedication: its Farming Fortune for the crop, times the crop's Garden milestone (held tool). */
    public static double dedication(ServerPlayer player, int milestone) {
        int level = ItemMods.enchantment(player.getMainHandItem(), "dedication");
        com.skyrpg.items.enchant.Enchants.Enchant enchant = com.skyrpg.items.enchant.Enchants.get("dedication");
        return level <= 0 || enchant == null ? 0 : enchant.value(level) * milestone;
    }
}
