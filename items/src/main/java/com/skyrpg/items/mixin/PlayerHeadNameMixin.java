package com.skyrpg.items.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.component.ResolvableProfile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A SkyBlock head (Bonzo's Mask, the Recombobulator...) carries a skin with a blank name; vanilla would call it "'s Head".
 * Its own item name wins instead.
 */
@Mixin(PlayerHeadItem.class)
abstract class PlayerHeadNameMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void skyrpg$itemName(ItemStack stack, CallbackInfoReturnable<Component> cir) {
        ResolvableProfile profile = stack.get(DataComponents.PROFILE);
        Component name = stack.get(DataComponents.ITEM_NAME);
        if (name != null && profile != null && profile.name().map(String::isBlank).orElse(true)) {
            cir.setReturnValue(name);
        }
    }
}
