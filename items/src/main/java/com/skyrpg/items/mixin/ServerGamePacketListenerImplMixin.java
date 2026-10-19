package com.skyrpg.items.mixin;

import com.skyrpg.items.ability.Abilities;
import com.skyrpg.items.backpack.BackpackMenuSlot;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In the creative inventory, the client sends every slot change as a "set creative slot" packet, and the
 * server only takes slots 1..45 (vanilla's inventory menu). Without this, accessories put on in creative
 * would be gone on the next sync; the backpack slots after them (50..52) too.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    /** A left click at the air (a punch) or at a mob (an attack): left-click abilities. Blocks: AttackBlockCallback. */
    @Inject(method = "handlePunch", at = @At("TAIL"))
    private void skyrpg$leftClickAir(ServerboundPunchPacket packet, CallbackInfo ci) {
        Abilities.leftClick(player);
    }

    @Inject(method = "handleAttack", at = @At("TAIL"))
    private void skyrpg$leftClickMob(ServerboundAttackPacket packet, CallbackInfo ci) {
        Abilities.leftClick(player);
    }

    @ModifyConstant(method = "handleSetCreativeModeSlot", constant = @Constant(intValue = 45))
    private int skyrpg$acceptAccessorySlots(int lastSlot) {
        return BackpackMenuSlot.END_INDEX - 1;
    }
}
