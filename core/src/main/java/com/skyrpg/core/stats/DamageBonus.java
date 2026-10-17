package com.skyrpg.core.stats;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hypixel's additive damage bonuses on a player's hit on a mob, in percent: they're added up with the Combat skill's
 * Warrior bonus into one multiplier (enchantments like Sharpness and Smite register here).
 */
public final class DamageBonus {
    /** A bonus for this hit, in percent (0 when it doesn't apply). */
    public interface Source {
        double percent(ServerPlayer attacker, LivingEntity target, DamageSource source);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    private DamageBonus() {
    }

    public static void register(Source source) {
        SOURCES.add(source);
    }

    /** Every bonus for this hit, added up. */
    public static double percent(ServerPlayer attacker, LivingEntity target, DamageSource source) {
        double total = 0;
        for (Source bonus : SOURCES) {
            total += bonus.percent(attacker, target, source);
        }
        return total;
    }
}
