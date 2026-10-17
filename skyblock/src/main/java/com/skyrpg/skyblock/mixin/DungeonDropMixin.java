package com.skyrpg.skyblock.mixin;

import com.skyrpg.skyblock.dungeon.Classes;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** In a dungeon the drop key is the class ability (Hypixel's): nothing is dropped, and the client's slot is put back. */
@Mixin(ServerPlayer.class)
abstract class DungeonDropMixin {
    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void skyrpg$classAbility(boolean all, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (Classes.onDrop(player)) {
            ci.cancel();
            player.containerMenu.sendAllDataToRemote();
        }
    }
}
