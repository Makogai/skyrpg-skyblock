package com.skyrpg.skyblock.mixin;

import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.skyblock.quiver.Quiver;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A shot's arrow that came from the quiver is taken out of it (multishot's extra arrows cost nothing, as in vanilla). */
@Mixin(ProjectileWeaponItem.class)
abstract class QuiverAmmoMixin {
    @Inject(method = "useAmmo", at = @At("HEAD"))
    private static void skyrpg$fromQuiver(ItemStack weapon, ItemStack projectile, LivingEntity shooter, boolean extra,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!extra && shooter instanceof ServerPlayer player && !player.hasInfiniteMaterials() && SkyBlockWorlds.isSkyBlock(player.level())) {
            Quiver.used(player, weapon, projectile);
        }
    }
}
