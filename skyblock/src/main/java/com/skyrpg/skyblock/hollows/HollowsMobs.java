package com.skyrpg.skyblock.hollows;

import com.skyrpg.skyblock.zone.Zones;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Spawning the Hollows' own mobs (bosses, guards, minions) with Hypixel's name, level, health and damage. */
final class HollowsMobs {
    private HollowsMobs() {
    }

    /** A named, levelled mob at a spot (it can be fought anywhere), or null if it couldn't be made. */
    static Mob spawn(ServerLevel level, EntityType<? extends Mob> type, Vec3 pos, String name, int levelNumber, double health, double damage,
            double xp, String tag) {
        Mob mob = type.create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return null;
        }
        mob.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360, 0);
        Zones.dressAs(mob, name, levelNumber, health, damage, xp);
        mob.addTag(tag);
        mob.addTag(Zones.FIGHTABLE_TAG);
        mob.setPersistenceRequired();
        mob.setCustomNameVisible(true);
        level.addFreshEntity(mob);
        return mob;
    }
}
