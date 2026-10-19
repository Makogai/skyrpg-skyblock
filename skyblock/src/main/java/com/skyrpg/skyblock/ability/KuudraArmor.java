package com.skyrpg.skyblock.ability;

import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Kuudra armour sets' stacks (Hypixel's wiki, October 2026), built while you wear 2+ pieces of a set (the lowest
 * piece's prestige - plain, Hot, Burning, Fiery, Infernal - sets the tier), one stack at most every 1.5s / 1s / 0.5s
 * with 2 / 3 / 4 pieces, up to 10, losing one after 4s / 7s / 10s without a new one:
 * <ul>
 *   <li>Crimson, Dominus (melee hits): each stack +0.05 (+0.1 from Hot) Swing Range, +1 / +2 Ferocity from Burning /
 *       Fiery, and Infernal +10% melee damage; at 10, every melee hit also swipes a line in a random direction.</li>
 *   <li>Aurora, Arcane Energy (ability damage; 1s / 0.7s / 0.5s, lost after 4s): at 10, sneak to fire 3-5 homing
 *       missiles of 5,000 damage.</li>
 *   <li>Terror, Hydra Strike (arrow hits, every 0.2s): each stack +2% to +5% arrow damage per piece past one; at 10,
 *       two more arrows of 20-60% (counted into the hit).</li>
 *   <li>Fervor (attacking): at 10, sneak for a Ground Pound around you that hits as hard as your Effective Health
 *       (25 million at most); with all four pieces each hit may pull three mobs onto you (Intimidate).</li>
 * </ul>
 * Hollow's Spirit stacks charge the Hollow Wand, which isn't in yet.
 */
final class KuudraArmor {
    enum Set { CRIMSON, AURORA, TERROR, FERVOR }

    private static final String[] TIERS = {"", "hot_", "burning_", "fiery_", "infernal_"};
    private static final String[] ICONS = {"ᝐ Dominus", "Ѫ Arcane Energy", "⁑ Hydra Strike", "҉ Fervor"};
    private static final int MAX = 10;

    /** A player's stacks of one set: how many, and the tick they last gained one. */
    private static final class Stacks {
        int count;
        long lastGain = -1000;
    }

    private static final Map<UUID, Map<Set, Stacks>> STACKS = new HashMap<>();

    /** What a player wears of a set: pieces, and the lowest tier among them. */
    private record Worn(int pieces, int tier) {
    }

    private KuudraArmor() {
    }

    static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (!(source.getEntity() instanceof ServerPlayer player) || entity == player || !(entity instanceof Mob)) {
                return;
            }
            if (source.is(DamageTypes.PLAYER_ATTACK)) {
                gain(player, Set.CRIMSON);
                gain(player, Set.FERVOR);
                swipe(player, entity, taken);
                intimidate(player);
            } else if (source.is(DamageTypeTags.IS_PROJECTILE)) {
                gain(player, Set.TERROR);
            } else if (source.is(DamageTypes.INDIRECT_MAGIC) && source.getDirectEntity() == player) {
                gain(player, Set.AURORA);
            }
        });
        IncomingDamage.registerHit(KuudraArmor::damageFactor);
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Dominus";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                int stacks = stacks(player, Set.CRIMSON);
                Worn worn = worn(player, Set.CRIMSON);
                if (stacks > 0 && worn.pieces() >= 2) {
                    out.add(StatTypes.SWING_RANGE, stacks * (worn.tier() == 0 ? 0.05 : 0.1));
                    out.add(StatTypes.FEROCITY, stacks * (worn.tier() >= 3 ? 2 : worn.tier() == 2 ? 1 : 0));
                }
            }
        });
        Abilities.register("homing_missiles", KuudraArmor::homingMissiles);
        Abilities.register("ground_pound", KuudraArmor::groundPound);
        ServerTickEvents.END_SERVER_TICK.register(KuudraArmor::tick);
    }

    // ---------------------------------------------------------------- wearing

    private static Worn worn(ServerPlayer player, Set set) {
        int pieces = 0;
        int tier = TIERS.length;
        String family = set.name().toLowerCase();
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemDefinition def = SkyItems.definition(player.getItemBySlot(slot));
            if (def == null) {
                continue;
            }
            String path = def.id().getPath();
            for (int t = TIERS.length - 1; t >= 0; t--) {
                if (path.startsWith(TIERS[t] + family + "_")) {
                    pieces++;
                    tier = Math.min(tier, t);
                    break;
                }
            }
        }
        return new Worn(pieces, pieces == 0 ? 0 : tier);
    }

    private static int stacks(ServerPlayer player, Set set) {
        Map<Set, Stacks> mine = STACKS.get(player.getUUID());
        Stacks stacks = mine == null ? null : mine.get(set);
        return stacks == null ? 0 : stacks.count;
    }

    /** Ticks between stacks for this many pieces. */
    private static int gainEvery(Set set, int pieces) {
        if (set == Set.TERROR) {
            return 4;
        }
        if (set == Set.AURORA) {
            return pieces >= 4 ? 10 : pieces == 3 ? 14 : 20;
        }
        return pieces >= 4 ? 10 : pieces == 3 ? 20 : 30;
    }

    /** Ticks without a new stack before one is lost. */
    private static int loseAfter(Set set, int pieces) {
        return set == Set.AURORA ? 80 : pieces >= 4 ? 200 : pieces == 3 ? 140 : 80;
    }

    private static void gain(ServerPlayer player, Set set) {
        Worn worn = worn(player, set);
        if (worn.pieces() < (set == Set.AURORA ? 1 : 2)) {
            return;
        }
        long now = player.level().getServer().getTickCount();
        Stacks stacks = STACKS.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(Set.class)).computeIfAbsent(set, s -> new Stacks());
        if (stacks.count >= MAX) {
            // full: they don't fade while you keep at it
            stacks.lastGain = now;
            return;
        }
        if (now - stacks.lastGain < gainEvery(set, worn.pieces())) {
            return;
        }
        stacks.lastGain = now;
        stacks.count++;
        show(player, set, stacks.count);
        if (set == Set.CRIMSON) {
            Stats.refresh(player);
        }
    }

    private static void show(ServerPlayer player, Set set, int count) {
        player.sendOverlayMessage(Msg.text(count + " " + ICONS[set.ordinal()] + (count >= MAX && (set == Set.AURORA || set == Set.FERVOR) ? "  (sneak!)" : ""),
                count >= MAX ? ChatFormatting.GOLD : ChatFormatting.YELLOW));
    }

    private static void reset(ServerPlayer player, Set set) {
        Map<Set, Stacks> mine = STACKS.get(player.getUUID());
        if (mine != null) {
            mine.remove(set);
        }
    }

    // ---------------------------------------------------------------- payoffs

    /** Infernal Crimson's +10% melee damage a stack; Terror's arrow damage a stack, and its two extra arrows at 10. */
    private static double damageFactor(LivingEntity target, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || target == player) {
            return 1;
        }
        if (source.is(DamageTypes.PLAYER_ATTACK)) {
            int stacks = stacks(player, Set.CRIMSON);
            Worn worn = stacks > 0 ? worn(player, Set.CRIMSON) : null;
            return worn != null && worn.pieces() >= 2 && worn.tier() >= 4 ? 1 + 0.1 * stacks : 1;
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            int stacks = stacks(player, Set.TERROR);
            Worn worn = stacks > 0 ? worn(player, Set.TERROR) : null;
            if (worn == null || worn.pieces() < 2) {
                return 1;
            }
            double perStack = (2 + 0.75 * worn.tier()) * (worn.pieces() - 1) / 100;
            double extra = stacks >= MAX ? 2 * (0.2 + 0.1 * worn.tier()) : 0;
            return (1 + perStack * stacks) * (1 + extra);
        }
        return 1;
    }

    /** Dominus at 10: a melee hit also swipes a line in a random direction, hitting every mob in it as hard. */
    private static void swipe(ServerPlayer player, LivingEntity hit, float taken) {
        Worn worn = worn(player, Set.CRIMSON);
        if (worn.pieces() < 2 || stacks(player, Set.CRIMSON) < MAX) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        Vec3 from = player.position().add(0, 1, 0);
        Vec3 dir = Vec3.directionFromRotation(0, player.getRandom().nextFloat() * 360);
        Vec3 to = from.add(dir.scale(6));
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(1.2),
                e -> e instanceof Mob && e.isAlive() && e != hit && e.getBoundingBox().inflate(0.8).clip(from, to).isPresent())) {
            HypixelAbilities.hurt(player, mob, taken * StatTypes.HEALTH_SCALE);
        }
        for (double d = 0.5; d < 6; d += 0.5) {
            Vec3 at = from.add(dir.scale(d));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }

    /** Fervor's full set (Intimidate): a melee hit has a half chance to turn three mobs near you onto you. */
    private static void intimidate(ServerPlayer player) {
        if (worn(player, Set.FERVOR).pieces() < 4 || player.getRandom().nextBoolean()) {
            return;
        }
        player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(10), Mob::isAlive).stream()
                .filter(mob -> mob.getTarget() != player).limit(3).forEach(mob -> mob.setTarget(player));
    }

    /** Aurora at 10 stacks: 3, 4, 4, 5 or 5 missiles (by tier) at the nearest mobs, 5,000 damage each. */
    private static boolean homingMissiles(ServerPlayer player, ItemDefinition.Ability ability) {
        Worn worn = worn(player, Set.AURORA);
        if (stacks(player, Set.AURORA) < MAX) {
            return false;
        }
        reset(player, Set.AURORA);
        int count = new int[] {3, 4, 4, 5, 5}[worn.tier()];
        ServerLevel level = (ServerLevel) player.level();
        List<Mob> targets = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20), Mob::isAlive).stream()
                .sorted(Comparator.comparingDouble(m -> m.distanceToSqr(player))).limit(count).toList();
        if (targets.isEmpty()) {
            return true;
        }
        double damage = HypixelAbilities.abilityDamage(player, 5000);
        for (int i = 0; i < count; i++) {
            Mob target = targets.get(i % targets.size());
            Vec3 from = player.getEyePosition();
            MoreAbilities.later(level.getServer(), 6 + i * 3L, () -> {
                if (!target.isAlive()) {
                    return;
                }
                Vec3 to = target.getBoundingBox().getCenter();
                for (double d = 0; d < from.distanceTo(to); d += 0.6) {
                    Vec3 at = from.add(to.subtract(from).normalize().scale(d)).add(0, Math.sin(d / 2) * 0.6, 0);
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                }
                HypixelAbilities.hurt(player, target, damage);
                level.sendParticles(ParticleTypes.EXPLOSION, to.x, to.y, to.z, 1, 0, 0, 0, 0);
            });
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    /** Fervor at 10 stacks: a Ground Pound hitting mobs within 6 blocks as hard as your Effective Health (25 million at most). */
    private static boolean groundPound(ServerPlayer player, ItemDefinition.Ability ability) {
        if (stacks(player, Set.FERVOR) < MAX) {
            return false;
        }
        reset(player, Set.FERVOR);
        ServerLevel level = (ServerLevel) player.level();
        double health = player.getMaxHealth() * StatTypes.HEALTH_SCALE;
        double effective = health * (1 + Math.max(0, Stats.get(player, StatTypes.DEFENSE)) / 100);
        double damage = Math.min(25_000_000, effective);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(6), Mob::isAlive)) {
            HypixelAbilities.hurt(player, mob, damage);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.2, player.getZ(), 40, 3, 0.2, 3, 0.1);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, 0.5f);
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (STACKS.isEmpty() || server.getTickCount() % 5 != 0) {
            return;
        }
        long now = server.getTickCount();
        STACKS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                return true;
            }
            entry.getValue().entrySet().removeIf(e -> {
                Set set = e.getKey();
                Stacks stacks = e.getValue();
                Worn worn = worn(player, set);
                if (worn.pieces() < (set == Set.AURORA ? 1 : 2)) {
                    if (set == Set.CRIMSON) {
                        server.execute(() -> Stats.refresh(player));
                    }
                    return true;
                }
                if (now - stacks.lastGain >= loseAfter(set, worn.pieces())) {
                    stacks.count--;
                    stacks.lastGain = now - loseAfter(set, worn.pieces()) + 20;
                    if (set == Set.CRIMSON) {
                        server.execute(() -> Stats.refresh(player));
                    }
                    if (stacks.count > 0) {
                        show(player, set, stacks.count);
                    }
                }
                return stacks.count <= 0;
            });
            return entry.getValue().isEmpty();
        });
    }
}
