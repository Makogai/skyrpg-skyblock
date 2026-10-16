package com.skyrpg.skyblock.mixin;

import com.skyrpg.skyblock.SkyblockItemUse;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SkyBlock items on block bases (enchanted blocks, heads, wool) don't place unless Hypixel lets them. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void skyrpg$keepSkyblockItems(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (SkyblockItemUse.blocksPlacing(context.getItemInHand())) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
