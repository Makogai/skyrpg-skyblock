package com.skyrpg.core.world;

import java.util.List;
import net.minecraft.world.entity.Entity;

/** Entity tags any mod may check. */
public final class MobTags {
    /**
     * A mob from a spawner (see the combat mod): it drops its normal loot and counts for kill totals, but gives little
     * skill XP and no SkyRPG rewards, so AFK spawner farms don't replace playing.
     */
    public static final String FARMED = "skyrpg_farmed";
    /** A mob that doesn't burn in daylight (Hypixel's Graveyard Zombies fight all day). */
    public static final String NO_SUNBURN = "skyrpg_no_sunburn";
    /** "skyrpg_combat_xp=36": the Combat XP this mob gives, in place of its kind's (a Crypt Ghoul is a zombie). */
    private static final String COMBAT_XP = "skyrpg_combat_xp=";

    private MobTags() {
    }

    /** The Combat XP this mob gives, or -1 if it gives its kind's. */
    public static double combatXp(Entity entity) {
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(COMBAT_XP)) {
                try {
                    return Double.parseDouble(tag.substring(COMBAT_XP.length()));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }
        return -1;
    }

    public static void setCombatXp(Entity entity, double xp) {
        List<String> old = entity.entityTags().stream().filter(tag -> tag.startsWith(COMBAT_XP)).toList();
        old.forEach(entity::removeTag);
        entity.addTag(COMBAT_XP + (xp == Math.rint(xp) ? String.valueOf((long) xp) : String.valueOf(xp)));
    }
}
