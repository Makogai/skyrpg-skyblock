package com.skyrpg.skyblock.mixin;

import com.skyrpg.core.world.MobTags;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mobs tagged {@link MobTags#NO_SUNBURN} (the Hub's zone mobs) don't burn in daylight, as on Hypixel. */
@Mixin(Mob.class)
abstract class MobSunburnMixin {
    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void skyrpg$noSunburn(CallbackInfoReturnable<Boolean> cir) {
        if (((Mob) (Object) this).entityTags().contains(MobTags.NO_SUNBURN)) {
            cir.setReturnValue(false);
        }
    }
}
