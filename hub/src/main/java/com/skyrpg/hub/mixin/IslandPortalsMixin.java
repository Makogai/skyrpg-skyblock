package com.skyrpg.hub.mixin;

import com.skyrpg.hub.Hub;
import com.skyrpg.hub.HubPortals;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Portals on the public islands go where the SkyBlock mod says ({@link HubPortals}), never to vanilla's End or Nether. */
@Mixin({EndPortalBlock.class, NetherPortalBlock.class})
abstract class IslandPortalsMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void skyrpg$islandPortal(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean movedByPiston,
                                     CallbackInfo ci) {
        if (!Hub.isPublic(level)) {
            return;
        }
        ci.cancel();
        if (entity instanceof ServerPlayer player) {
            HubPortals.entered(player, state, pos);
        }
    }
}
