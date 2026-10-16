package com.skyrpg.skyblock.mixin;

import com.skyrpg.skyblock.profile.Profiles;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Remembers the address a client connected with, so a profile switch can send it back there. */
@Mixin(Connection.class)
public abstract class ConnectionMixin implements Profiles.ConnectionHost {
    @Unique
    private String skyrpg$host;
    @Unique
    private int skyrpg$port;

    @Override
    public String skyrpg$host() {
        return skyrpg$host;
    }

    @Override
    public int skyrpg$port() {
        return skyrpg$port;
    }

    @Override
    public void skyrpg$setAddress(String host, int port) {
        skyrpg$host = host;
        skyrpg$port = port;
    }
}
