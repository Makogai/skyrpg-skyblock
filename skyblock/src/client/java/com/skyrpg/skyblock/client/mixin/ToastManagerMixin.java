package com.skyrpg.skyblock.client.mixin;

import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.RecipeToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.TutorialToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SkyBlock has its own recipes and progress: vanilla's "New Recipe(s) Unlocked!", "Advancement Made!" and tutorial
 * toasts ("Open your inventory") would only point at things that don't apply here, so they never show.
 */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void skyrpg$noVanillaToasts(Toast toast, CallbackInfo ci) {
        if (toast instanceof RecipeToast || toast instanceof AdvancementToast || toast instanceof TutorialToast) {
            ci.cancel();
        }
    }
}
