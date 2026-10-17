package com.skyrpg.skyblock.end;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;

/** The End's own features: the Dragon's Nest altar, the dragon fight and Special Zealots. */
public final class EndFeatures {
    private EndFeatures() {
    }

    public static void init() {
        DragonAltar.init();
        DragonFight.init();
        SpecialZealots.init();
        // Eyes left in the altar over a restart belong to nobody: empty it.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerLevel end = server.getLevel(DragonFight.THE_END);
            if (end != null) {
                DragonAltar.reset(end);
            }
        });
    }
}
