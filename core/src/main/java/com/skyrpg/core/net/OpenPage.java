package com.skyrpg.core.net;

import com.skyrpg.core.SkyRpgCore;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server to client: "open this page of the SkyRPG menu", for commands and chat clicks that lead to a page
 * ({@code /sblevel} opens SkyBlock Leveling). {@code page} is the page's id.
 */
public record OpenPage(String page) implements CustomPacketPayload {
    public static final Type<OpenPage> TYPE = new Type<>(SkyRpgCore.id("open_page"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPage> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenPage::page, OpenPage::new);

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
    }

    public static void send(ServerPlayer player, Identifier page) {
        if (ServerPlayNetworking.canSend(player, TYPE)) {
            ServerPlayNetworking.send(player, new OpenPage(page.toString()));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
