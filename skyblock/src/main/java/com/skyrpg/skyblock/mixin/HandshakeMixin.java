package com.skyrpg.skyblock.mixin;

import com.skyrpg.skyblock.profile.Profiles;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import net.minecraft.server.network.ServerHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The handshake says which host name and port the client used to reach us. */
@Mixin(ServerHandshakePacketListenerImpl.class)
public abstract class HandshakeMixin {
    @Shadow
    @Final
    private Connection connection;

    @Inject(method = "handleIntention", at = @At("HEAD"))
    private void skyrpg$rememberAddress(ClientIntentionPacket packet, CallbackInfo ci) {
        ((Profiles.ConnectionHost) connection).skyrpg$setAddress(packet.hostName(), packet.port());
    }
}
