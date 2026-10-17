package com.skyrpg.skyblock.area;

import com.skyrpg.hub.Hub;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.island.Islands;
import com.skyrpg.skyblock.profile.Profile;
import com.skyrpg.skyblock.profile.Profiles;
import com.skyrpg.skyblock.zone.Zones;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.Level;

/**
 * Where each player is, by name, as Hypixel's scoreboard shows it ("⏣ Your Island", "⏣ Hub"). Worked out on the
 * server once a second and sent to the player when it changes. Public islands will name their zones here too.
 */
public final class Areas {
    private static final Map<UUID, String> CURRENT = new ConcurrentHashMap<>();

    /** The area name the player's client shows. */
    public record AreaPayload(String area) implements CustomPacketPayload {
        public static final Type<AreaPayload> TYPE = new Type<>(SkyRpgSkyblock.id("area"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AreaPayload> CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8, AreaPayload::area, AreaPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private Areas() {
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(AreaPayload.TYPE, AreaPayload.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CURRENT.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                server.getPlayerList().getPlayers().forEach(Areas::update);
            }
        });
    }

    /** The name of the area this player is in. */
    public static String of(ServerPlayer player) {
        Level level = player.level();
        if (Islands.isIslands(level)) {
            Profile owner = Islands.ownerAt(player.blockPosition());
            if (owner == null) {
                return "The Void";
            }
            Profile mine = Profiles.current(player);
            if (mine != null && mine.id().equals(owner.id())) {
                return "Your Island";
            }
            MinecraftServer server = level.getServer();
            String name = server.services().nameToIdCache().get(owner.owner()).map(NameAndId::name).orElse("Someone");
            return name + "'s Island";
        }
        Zones.Zone zone = Zones.at(level, player.blockPosition());
        if (zone != null) {
            return zone.name();
        }
        if (Hub.isHub(level)) {
            return "Hub";
        }
        if (Hub.isPublic(level)) {
            String island = com.skyrpg.hub.HubAreas.islandName(level.dimension());
            return island != null ? island : "Hub";
        }
        if (level.dimension() == Level.NETHER) {
            return "The Nether";
        }
        if (level.dimension() == Level.END) {
            return "The End";
        }
        return "Survival World";
    }

    private static void update(ServerPlayer player) {
        String area = of(player);
        if (!area.equals(CURRENT.put(player.getUUID(), area)) && ServerPlayNetworking.canSend(player, AreaPayload.TYPE)) {
            ServerPlayNetworking.send(player, new AreaPayload(area));
        }
    }
}
