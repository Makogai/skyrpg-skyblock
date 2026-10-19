package com.skyrpg.skyblock.hollows;

import com.skyrpg.skyblock.zone.Zones;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** The Goblin Queen's guards: they come for anyone who reaches the Amber Crystal without King Yolkar's scent. */
final class GoblinQueen {
    static final String TAG = "skyrpg_hollows_guard";

    private GoblinQueen() {
    }

    /** Three Goblin Guards around the player (not more than six alive at once). */
    static void guards(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || level.getEntities(EntityTypes.ZOMBIE, z -> z.entityTags().contains(TAG)).size() >= 6) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            double angle = i * Math.PI * 2 / 3;
            Vec3 pos = player.position().add(Math.cos(angle) * 4, 0.5, Math.sin(angle) * 4);
            Mob guard = HollowsMobs.spawn(level, EntityTypes.ZOMBIE, pos, "Goblin Guard", 50, 30_000, 900, 120, TAG);
            if (guard != null) {
                guard.setTarget(player);
            }
        }
    }

    static void init() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerLevel level = server.getLevel(CrystalHollows.LEVEL);
            if (level != null && server.getTickCount() % 600 == 0) {
                // Guards nobody is near any more go home.
                for (var guard : level.getEntities(EntityTypes.ZOMBIE, z -> z.entityTags().contains(TAG))) {
                    if (level.getNearestPlayer(guard, 40) == null) {
                        guard.discard();
                    }
                }
            }
        });
    }
}
