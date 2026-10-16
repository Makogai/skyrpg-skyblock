package com.skyrpg.stats;

import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.Crits;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.SkyArrows;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.stats.TrueDamage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * How Strength, Crit Chance, Crit Damage and Defense change damage. Called (through a mixin) at the start
 * of every {@code LivingEntity.hurtServer}, before vanilla's armor and invulnerability handling.
 */
public final class CombatStats {
    private CombatStats() {
    }

    /** Warrior's bonus in percent at this Combat level. */
    static int warrior(int combatLevel) {
        return 4 * Math.min(50, combatLevel) + Math.max(0, combatLevel - 50);
    }

    public static float modifyDamage(LivingEntity target, ServerLevel level, DamageSource source, float amount) {
        if (amount <= 0) {
            return amount;
        }
        double damage = amount;
        Crits.set(false);

        // A player's own melee hit: Strength, then a crit roll.
        if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() == attacker
                && source.is(DamageTypes.PLAYER_ATTACK) && target != attacker) {
            damage *= 1 + Math.max(0, Stats.get(attacker, StatTypes.STRENGTH)) / 100;
            if (attacker.getRandom().nextDouble() * 100 < Stats.get(attacker, StatTypes.CRIT_CHANCE)) {
                damage *= 1 + Math.max(0, Stats.get(attacker, StatTypes.CRIT_DAMAGE)) / 100;
                Crits.set(true);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 14, 0.3, 0.3, 0.3, 0.25);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
                        attacker.getSoundSource(), 1.0f, 1.0f);
            }
        }

        // A SkyRPG bow's arrow: the bow's damage (not vanilla's speed-based arrow damage), then the same Strength and crit as melee.
        if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getOwner() instanceof ServerPlayer shooter && target != shooter
                && SkyArrows.of(arrow) != null) {
            damage = SkyArrows.of(arrow).damage() / StatTypes.HEALTH_SCALE;
            damage *= 1 + Math.max(0, Stats.get(shooter, StatTypes.STRENGTH)) / 100;
            if (shooter.getRandom().nextDouble() * 100 < Stats.get(shooter, StatTypes.CRIT_CHANCE)) {
                damage *= 1 + Math.max(0, Stats.get(shooter, StatTypes.CRIT_DAMAGE)) / 100;
                Crits.set(true);
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 14, 0.3, 0.3, 0.3, 0.25);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT,
                        shooter.getSoundSource(), 1.0f, 1.0f);
            }
        }

        // The Combat skill's Warrior perk: +4% damage to mobs a level up to 50, +1% a level after (Hypixel).
        if (source.getEntity() instanceof ServerPlayer hitter && !(target instanceof net.minecraft.world.entity.player.Player)) {
            damage *= 1 + warrior(SkillLevels.level(hitter, SkillLevels.COMBAT)) / 100.0;
        }

        // Other mods' multipliers (slayer bosses tougher than vanilla's health cap, slayer weapons).
        damage *= IncomingDamage.multiplier(target, source);

        // True Damage (boss abilities): True Defense reduces it the way Defense reduces the rest; Defense does nothing.
        if (target instanceof ServerPlayer defender && TrueDamage.active()) {
            double trueDefense = Math.max(0, Stats.get(defender, StatTypes.TRUE_DEFENSE));
            return (float) (damage * 100 / (100 + trueDefense));
        }
        // Defense: 100 defense halves damage, 300 quarters it, and so on.
        if (target instanceof ServerPlayer defender && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            double defense = Math.max(0, Stats.get(defender, StatTypes.DEFENSE));
            damage *= 100 / (100 + defense);
        }
        return (float) damage;
    }
}
