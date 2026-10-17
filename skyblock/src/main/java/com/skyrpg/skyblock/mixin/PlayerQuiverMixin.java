package com.skyrpg.skyblock.mixin;

import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.skyblock.quiver.Quiver;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** In SkyBlock a bow with no arrows in the inventory shoots from the quiver (on both sides, so the client draws the bow). */
@Mixin(Player.class)
abstract class PlayerQuiverMixin {
    @Inject(method = "getProjectile", at = @At("RETURN"), cancellable = true)
    private void skyrpg$quiver(ItemStack weapon, CallbackInfoReturnable<ItemStack> cir) {
        Player player = (Player) (Object) this;
        if (cir.getReturnValue().isEmpty() && weapon.getItem() instanceof ProjectileWeaponItem && SkyBlockWorlds.isSkyBlock(player.level())) {
            ItemStack ammo = Quiver.ammo(player);
            if (!ammo.isEmpty()) {
                cir.setReturnValue(ammo.copy());
            }
        }
    }
}
