package com.skyrpg.skyblock.mixin;

import com.skyrpg.skyblock.fishing.SkySeaCreatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sea creatures and Glacite Maniacs drop only Hypixel's drops (given to the killer), none of the vanilla mob's loot or XP. */
@Mixin(LivingEntity.class)
abstract class NoVanillaLootMixin {
    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"), cancellable = true)
    private void skyrpg$noVanillaLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        java.util.Set<String> tags = ((LivingEntity) (Object) this).entityTags();
        if (tags.contains(SkySeaCreatures.TAG) || tags.contains(com.skyrpg.skyblock.glacite.Mineshafts.MANIAC)
                || tags.contains(com.skyrpg.skyblock.dungeon.DungeonMobs.TAG)) {
            ci.cancel();
        }
    }
}
