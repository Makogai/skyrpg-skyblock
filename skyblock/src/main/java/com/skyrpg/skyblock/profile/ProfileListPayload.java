package com.skyrpg.skyblock.profile;

import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

/** A player's profiles, for the Profile Management page. Sent when they join and when a co-op changes. */
public record ProfileListPayload(List<Entry> profiles) implements CustomPacketPayload {
    public static final Type<ProfileListPayload> TYPE = new Type<>(SkyRpgSkyblock.id("profiles"));

    /** One profile: its fruit, its members' names (you first), when it began, and whether you're playing it. */
    public record Entry(String name, List<String> members, long created, boolean playing) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::name,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Entry::members,
                ByteBufCodecs.VAR_LONG, Entry::created,
                ByteBufCodecs.BOOL, Entry::playing,
                Entry::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileListPayload> CODEC = StreamCodec.composite(
            Entry.CODEC.apply(ByteBufCodecs.list()), ProfileListPayload::profiles, ProfileListPayload::new);

    public static void send(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, TYPE)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        Profile current = Profiles.current(player);
        List<Entry> entries = new ArrayList<>();
        for (Profile profile : Profiles.of(player.getUUID())) {
            List<String> members = new ArrayList<>();
            members.add(player.getPlainTextName());
            for (UUID member : profile.members()) {
                if (!member.equals(player.getUUID())) {
                    members.add(server.services().nameToIdCache().get(member).map(NameAndId::name).orElse("?"));
                }
            }
            entries.add(new Entry(profile.name(), members, profile.created(), current != null && current.id().equals(profile.id())));
        }
        ServerPlayNetworking.send(player, new ProfileListPayload(entries));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
