package com.skyrpg.core.stats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hypixel's True Damage: a hit that Defense doesn't reduce (True Defense does, the same way Defense reduces normal
 * damage). The stats mod checks {@link #active} while it scales the hit. Boss abilities also hit through invulnerability
 * frames: on Hypixel they land whenever they're cast, not only between melee hits.
 */
public final class TrueDamage {
    private static final ThreadLocal<Boolean> ACTIVE = ThreadLocal.withInitial(() -> false);

    private TrueDamage() {
    }

    /** Whether the hit being handled right now is true damage. */
    public static boolean active() {
        return ACTIVE.get();
    }

    /** True damage: {@code amount} in SkyBlock units (5 to a vanilla heart's half). */
    public static boolean hurt(LivingEntity target, DamageSource source, double amount) {
        if (!(target.level() instanceof ServerLevel level) || amount <= 0) {
            return false;
        }
        ACTIVE.set(true);
        try {
            target.setInvulnerableTime(0);
            return target.hurtServer(level, source, (float) (amount / StatTypes.HEALTH_SCALE));
        } finally {
            ACTIVE.set(false);
        }
    }

    /** An ability hit that Defense does reduce, in SkyBlock units, through invulnerability frames. */
    public static boolean ability(LivingEntity target, DamageSource source, double amount) {
        if (!(target.level() instanceof ServerLevel level) || amount <= 0) {
            return false;
        }
        target.setInvulnerableTime(0);
        return target.hurtServer(level, source, (float) (amount / StatTypes.HEALTH_SCALE));
    }
}
