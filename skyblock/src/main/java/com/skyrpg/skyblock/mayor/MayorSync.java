package com.skyrpg.skyblock.mayor;

import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.List;
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

/**
 * The perks in effect, sent to every player on join and whenever they change (a new term, Jerry's next turn), so the
 * client's calendar shows the Mayor's events.
 */
public final class MayorSync {
    /** Server to client: the Mayor's key and every perk in effect now. */
    public record State(String mayor, List<String> perks) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(SkyRpgSkyblock.id("mayor_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, State::mayor,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), State::perks,
                State::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static State last;

    private MayorSync() {
    }

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player, state()));
        ServerTickEvents.END_SERVER_TICK.register(MayorSync::tick);
    }

    /** Every perk any mayor has that is in effect now. */
    static State state() {
        List<String> perks = new ArrayList<>();
        for (String key : List.of("slayer", "mining", "pets", "economist", "farming", "events", "fishing", "dungeons", "jerry", "scorpius", "derpy")) {
            Mayors.Mayor mayor = Mayors.mayor(key);
            if (mayor == null) {
                continue;
            }
            for (Mayors.Perk perk : mayor.perks()) {
                if (Mayors.active(perk.id()) && !perks.contains(perk.id())) {
                    perks.add(perk.id());
                }
            }
        }
        return new State(Mayors.current().mayor(), List.copyOf(perks));
    }

    private static void send(ServerPlayer player, State state) {
        if (ServerPlayNetworking.canSend(player, State.TYPE)) {
            ServerPlayNetworking.send(player, state);
        }
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 200 != 0) {
            return;
        }
        State now = state();
        if (!now.equals(last)) {
            last = now;
            server.getPlayerList().getPlayers().forEach(p -> send(p, now));
        }
    }
}
