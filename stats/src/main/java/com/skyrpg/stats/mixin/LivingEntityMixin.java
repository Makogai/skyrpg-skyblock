package com.skyrpg.stats.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.stats.CombatStats;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets {@link CombatStats} scale every hit before vanilla handles it (armor, invulnerability frames, death), and a
 * player's Vitality scale the healing they get (100 = normal; slayer bosses lower it).
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @ModifyVariable(method = "heal", at = @At("HEAD"), argsOnly = true)
    private float skyrpg$vitality(float amount) {
        if ((Object) this instanceof ServerPlayer player && Stats.available()) {
            return (float) (amount * Math.max(0, Stats.get(player, StatTypes.VITALITY)) / 100);
        }
        return amount;
    }

    @WrapMethod(method = "hurtServer")
    private boolean skyrpg$applyCombatStats(ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        return original.call(level, source, CombatStats.modifyDamage((LivingEntity) (Object) this, level, source, amount));
    }
}
