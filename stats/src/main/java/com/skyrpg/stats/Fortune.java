package com.skyrpg.stats;

import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mining, Farming and Foraging Fortune: breaking a block in {@code #skyrpg_stats:<type>_fortune_blocks}
 * drops its normal drops again, once per full 100 fortune plus a chance for one more from the rest
 * (250 fortune = 2 extra copies, 50% for a third).
 */
final class Fortune {
    private static final TagKey<Block> MINING = TagKey.create(Registries.BLOCK, SkyRpgStats.id("mining_fortune_blocks"));
    private static final TagKey<Block> FARMING = TagKey.create(Registries.BLOCK, SkyRpgStats.id("farming_fortune_blocks"));
    private static final TagKey<Block> FORAGING = TagKey.create(Registries.BLOCK, SkyRpgStats.id("foraging_fortune_blocks"));

    private Fortune() {
    }

    static void onBlockBroken(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer) || serverPlayer.isCreative()) {
            return;
        }
        ItemStack tool = serverPlayer.getMainHandItem();
        StatType stat;
        if (state.is(MINING)) {
            if (hasSilkTouch(serverLevel, tool)) {
                return; // re-placeable ores would otherwise give endless extra drops
            }
            stat = StatTypes.MINING_FORTUNE;
        } else if (state.is(FARMING)) {
            if (!isGrown(state)) {
                return;
            }
            stat = StatTypes.FARMING_FORTUNE;
        } else if (state.is(FORAGING)) {
            stat = StatTypes.FORAGING_FORTUNE;
        } else {
            return;
        }

        double fortune = Stats.get(serverPlayer, stat);
        StatType crop = stat == StatTypes.FARMING_FORTUNE ? cropFortune(state.getBlock()) : null;
        if (crop != null) {
            fortune += Stats.get(serverPlayer, crop);
        }
        fortune += com.skyrpg.core.stats.FortuneBonus.of(serverPlayer, state);
        if (fortune <= 0) {
            return;
        }
        int extra = (int) (fortune / 100) + (serverPlayer.getRandom().nextDouble() < (fortune % 100) / 100 ? 1 : 0);
        if (extra <= 0) {
            return;
        }
        for (ItemStack drop : Block.getDrops(state, serverLevel, pos, blockEntity, serverPlayer, tool)) {
            int count = drop.getCount() * extra;
            // Split into normal stacks rather than one oversized item entity.
            while (count > 0) {
                int stack = Math.min(count, drop.getMaxStackSize());
                Block.popResource(serverLevel, pos, drop.copyWithCount(stack));
                count -= stack;
            }
        }
    }

    private static boolean hasSilkTouch(ServerLevel level, ItemStack tool) {
        Holder<Enchantment> silkTouch = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH);
        return EnchantmentHelper.getItemEnchantmentLevel(silkTouch, tool) > 0;
    }

    /** Crops, nether wart and cocoa only count when fully grown; melons, pumpkins, cane and cactus always do. */
    /** A crop's own Fortune (Wheat Fortune ...) that adds to Farming Fortune on it, or null. */
    private static StatType cropFortune(Block block) {
        if (block == Blocks.WHEAT) {
            return StatTypes.WHEAT_FORTUNE;
        } else if (block == Blocks.CARROTS) {
            return StatTypes.CARROT_FORTUNE;
        } else if (block == Blocks.POTATOES) {
            return StatTypes.POTATO_FORTUNE;
        } else if (block == Blocks.PUMPKIN) {
            return StatTypes.PUMPKIN_FORTUNE;
        } else if (block == Blocks.MELON) {
            return StatTypes.MELON_FORTUNE;
        } else if (block == Blocks.SUGAR_CANE) {
            return StatTypes.SUGAR_CANE_FORTUNE;
        } else if (block == Blocks.CACTUS) {
            return StatTypes.CACTUS_FORTUNE;
        } else if (block == Blocks.COCOA) {
            return StatTypes.COCOA_BEANS_FORTUNE;
        } else if (block == Blocks.NETHER_WART) {
            return StatTypes.NETHER_STALK_FORTUNE;
        } else if (block == Blocks.RED_MUSHROOM || block == Blocks.BROWN_MUSHROOM || block == Blocks.RED_MUSHROOM_BLOCK
                || block == Blocks.BROWN_MUSHROOM_BLOCK) {
            return StatTypes.MUSHROOM_FORTUNE;
        }
        return null;
    }

    private static boolean isGrown(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }
        if (block instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
        }
        return true;
    }
}
