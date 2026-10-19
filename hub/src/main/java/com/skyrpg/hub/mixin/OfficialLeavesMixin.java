package com.skyrpg.hub.mixin;

import com.skyrpg.hub.HubMaps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hypixel's maps ({@link HubMaps}) are full of leaves with no log near them (Hypixel never random-ticks): they'd all
 * decay here, and whatever stands on them (big dripleaf) with them. On an official map, leaves never decay.
 */
@Mixin(LeavesBlock.class)
abstract class OfficialLeavesMixin {
    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void skyrpg$noDecay(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (HubMaps.official(level.dimension())) {
            ci.cancel();
        }
    }
}
