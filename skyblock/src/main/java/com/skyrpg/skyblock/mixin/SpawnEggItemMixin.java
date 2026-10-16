package com.skyrpg.skyblock.mixin;

import com.skyrpg.skyblock.SkyblockItemUse;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SkyBlock items on spawn eggs (decoys, training dummies) don't hatch their base mob on a block. */
@Mixin(SpawnEggItem.class)
public abstract class SpawnEggItemMixin {
    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void skyrpg$keepSkyblockItems(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (SkyblockItemUse.blocksVanillaUse(context.getItemInHand())) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
