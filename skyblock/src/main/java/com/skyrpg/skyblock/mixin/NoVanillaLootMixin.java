package com.skyrpg.skyblock.mixin;

import com.skyrpg.skyblock.fishing.SkySeaCreatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sea creatures drop only Hypixel's drops (given to the killer), none of the vanilla mob's loot or XP. */
@Mixin(LivingEntity.class)
abstract class NoVanillaLootMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void skyrpg$noVanillaLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        if (((LivingEntity) (Object) this).entityTags().contains(SkySeaCreatures.TAG)) {
            ci.cancel();
        }
    }
}
