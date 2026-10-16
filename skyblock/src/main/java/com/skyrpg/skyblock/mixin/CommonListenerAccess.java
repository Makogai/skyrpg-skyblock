package com.skyrpg.skyblock.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A player's network connection (to read the address they connected with). */
@Mixin(ServerCommonPacketListenerImpl.class)
public interface CommonListenerAccess {
    @Accessor("connection")
    Connection skyrpg$connection();
}
