package com.skyrpg.skyblock.mixin;

import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.skyblock.alchemy.SkyBrewing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In SkyBlock worlds a brewing stand brews Hypixel's way (see {@link SkyBrewing}), not vanilla's. */
@Mixin(BrewingStandBlockEntity.class)
abstract class BrewingStandTickMixin {
    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void skyrpg$skyBlockBrewing(ServerLevel level, BlockPos pos, BlockState state, BrewingStandBlockEntity stand, CallbackInfo ci) {
        if (SkyBlockWorlds.isSkyBlock(level)) {
            SkyBrewing.tick(level, pos, stand);
            ci.cancel();
        }
    }
}
