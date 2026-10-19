package com.skyrpg.hub;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * End and nether portal blocks on the public islands (Hypixel's maps have them: the Hub's fountain to the Dungeon Hub)
 * don't take anyone to vanilla's End or Nether: walking in fires {@link #ENTERED}, and the SkyBlock mod sends the
 * player where that portal goes. Once every two seconds at most, per player.
 */
public final class HubPortals {
    @FunctionalInterface
    public interface Entered {
        void entered(ServerPlayer player, BlockState portal, BlockPos pos);
    }

    public static final Event<Entered> ENTERED = EventFactory.createArrayBacked(Entered.class, listeners -> (player, portal, pos) -> {
        for (Entered listener : listeners) {
            listener.entered(player, portal, pos);
        }
    });

    private static final Map<UUID, Long> LAST = new HashMap<>();

    private HubPortals() {
    }

    /** Called by the portal blocks' mixin, server side. */
    public static void entered(ServerPlayer player, BlockState portal, BlockPos pos) {
        long now = player.level().getServer().getTickCount();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < 40) {
            return;
        }
        LAST.put(player.getUUID(), now);
        ENTERED.invoker().entered(player, portal, pos);
    }
}
