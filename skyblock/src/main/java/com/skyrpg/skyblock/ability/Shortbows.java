package com.skyrpg.skyblock.ability;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.skyblock.quiver.Quiver;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's shortbows (Juju, Terminator, Artisanal, Spirit, Dragon, Machine Gun, Mosquito, Scorpion): no drawing, a
 * left or right click shoots a full-power arrow from your quiver at once, again every 0.5s divided by (1 + Attack Speed
 * / 100), 0.15s at the fastest (holding right click keeps shooting). The Terminator shoots three, fanned out; after three
 * hits its left click casts Salvation (a beam through up to five enemies that always crits).
 */
final class Shortbows {
    static final Set<String> SHORTBOWS = Set.of("JUJU_SHORTBOW", "TERMINATOR", "ARTISANAL_SHORTBOW", "ITEM_SPIRIT_BOW", "STARRED_ITEM_SPIRIT_BOW",
            "DRAGON_SHORTBOW", "MACHINE_GUN_BOW", "MOSQUITO_BOW", "SCORPION_BOW");
    private static final String TERMINATOR = "TERMINATOR";
    private static final String NASTY_BITE = "skyrpg_nasty_bite";
    private static final Map<UUID, Long> NEXT_SHOT = new HashMap<>();
    /** Terminator hits toward Salvation (3 charge it). */
    private static final Map<UUID, Integer> HITS = new HashMap<>();

    private Shortbows() {
    }

    static void init() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (hand != InteractionHand.MAIN_HAND || !isShortbow(player.getItemInHand(hand))) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer server) {
                shoot(server);
            }
            return InteractionResult.SUCCESS;
        });
        Abilities.onLeftClick(player -> {
            ItemStack held = player.getMainHandItem();
            if (!isShortbow(held)) {
                return false;
            }
            // a charged Terminator's left click is Salvation, the Mosquito Bow's is Nasty Bite (the abilities); otherwise it shoots
            if (TERMINATOR.equals(SkyItems.skyblockId(held)) && HITS.getOrDefault(player.getUUID(), 0) >= 3
                    || "MOSQUITO_BOW".equals(SkyItems.skyblockId(held))) {
                return false;
            }
            shoot(player);
            return true;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            // Nasty Bite: the enhanced shot heals the shooter
            if (source.getDirectEntity() instanceof AbstractArrow bite && bite.getOwner() instanceof ServerPlayer healed && entity instanceof Mob
                    && bite.entityTags().contains(NASTY_BITE)) {
                healed.heal((float) (189 / StatTypes.HEALTH_SCALE));
                bite.removeTag(NASTY_BITE);
            }
            if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getOwner() instanceof ServerPlayer shooter && entity instanceof Mob
                    && arrow.getWeaponItem() != null && TERMINATOR.equals(SkyItems.skyblockId(arrow.getWeaponItem()))) {
                int hits = Math.min(3, HITS.getOrDefault(shooter.getUUID(), 0) + 1);
                HITS.put(shooter.getUUID(), hits);
                if (hits == 3) {
                    shooter.sendOverlayMessage(Msg.text("Salvation is ready! (LEFT CLICK)", ChatFormatting.GOLD));
                }
            }
        });
        Abilities.register("salvation", Shortbows::salvation);
        Abilities.register("nasty_bite", (player, ability) -> shoot(player, true));
    }

    static boolean isShortbow(ItemStack stack) {
        String id = SkyItems.skyblockId(stack);
        return id != null && SHORTBOWS.contains(id);
    }

    private static void shoot(ServerPlayer player) {
        shoot(player, false);
    }

    /** An enhanced shot (Nasty Bite) heals on hit; false when it couldn't shoot. */
    private static boolean shoot(ServerPlayer player, boolean enhanced) {
        long now = player.level().getServer().getTickCount();
        if (now < NEXT_SHOT.getOrDefault(player.getUUID(), 0L)) {
            return false;
        }
        ItemStack bow = player.getMainHandItem();
        ItemStack ammo = Quiver.ammo(player);
        if (ammo.isEmpty()) {
            player.sendOverlayMessage(Msg.text("You don't have any arrows in your quiver!", ChatFormatting.RED));
            NEXT_SHOT.put(player.getUUID(), now + 10);
            return false;
        }
        double attackSpeed = Math.max(0, Stats.get(player, StatTypes.ATTACK_SPEED));
        NEXT_SHOT.put(player.getUUID(), now + Math.max(3, Math.round(10 / (1 + attackSpeed / 100))));
        ServerLevel level = (ServerLevel) player.level();
        boolean terminator = TERMINATOR.equals(SkyItems.skyblockId(bow));
        for (int i = terminator ? -1 : 0; i <= (terminator ? 1 : 0); i++) {
            Arrow arrow = new Arrow(level, player, ammo.copyWithCount(1), bow);
            arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + i * 5, 0, 3.0f, 0.5f);
            arrow.setCritArrow(true);
            if (enhanced) {
                arrow.addTag(NASTY_BITE);
            }
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            level.addFreshEntity(arrow);
        }
        Quiver.used(player, bow, ammo);
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.2f);
        return true;
    }

    /** Salvation: after three Terminator hits, a beam through up to five enemies, always a crit. */
    private static boolean salvation(ServerPlayer player, ItemDefinition.Ability ability) {
        if (HITS.getOrDefault(player.getUUID(), 0) < 3) {
            return false;
        }
        HITS.remove(player.getUUID());
        ItemStack bow = player.getMainHandItem();
        ItemDefinition def = SkyItems.definition(bow);
        double bowDamage = def == null ? 0 : ItemMods.stats(bow, def).getOrDefault(StatTypes.DAMAGE, 0.0);
        double damage = (5 + bowDamage) * (1 + Math.max(0, Stats.get(player, StatTypes.STRENGTH)) / 100)
                * (1 + Math.max(0, Stats.get(player, StatTypes.CRIT_DAMAGE)) / 100);
        ServerLevel level = (ServerLevel) player.level();
        var eye = player.getEyePosition();
        var end = eye.add(player.getLookAngle().scale(40));
        var wall = level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        var stop = wall.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? wall.getLocation() : end;
        level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(eye, stop).inflate(1),
                        e -> e instanceof Mob && e.isAlive() && e.getBoundingBox().inflate(0.4).clip(eye, stop).isPresent()).stream()
                .sorted(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(eye))).limit(5)
                .forEach(mob -> HypixelAbilities.hurt(player, mob, damage));
        for (double d = 1; d < stop.distanceTo(eye); d += 0.4) {
            var at = eye.add(player.getLookAngle().scale(d));
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 1.0f, 2.0f);
        return true;
    }

}
