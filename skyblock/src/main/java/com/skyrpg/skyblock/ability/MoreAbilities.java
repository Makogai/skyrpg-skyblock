package com.skyrpg.skyblock.ability;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * More of Hypixel's item abilities, their numbers read from the descriptions as Hypixel words them ("dealing 504 damage",
 * "Heal for 500❤", "+40 Strength ... for 30 seconds"). Shots (Dreadlord, Witherlord, Ink Wand, Runic Staff, Bingo
 * Blaster, Yeti Sword, Giant's Eye Sword, the Hope staffs, the Alchemist's Staff, Ender Bow, the boomerang and spear),
 * blasts around you (Staff of the Volcano, Golem Sword, Implosion, the Miniature Nuke), lingering areas (Fire Veil,
 * Starlight Wand, Fire Fury Staff, Fire Freeze Staff, Gyrokinetic Wand, Shadow Warp, Pigman Sword), sweeping arcs (the
 * reapers, whips), bouncing roses, lightning; teleports (Aspect of the Leech, Sinseeker, Etherwarp, Shadowstep, Shadow
 * Fury, Tactical Insertion, the teammate leaps, Leaping Sword, scaffolding, the Salmon set in water); timed buffs
 * (Soulcry, Weird Tuba, Sword of Bad Health, Hellstorm, Holy Ice, Soul Esoward, Ragnarock Axe, Enrager, Wand of Strength,
 * Wither Shield, Edible Mace, Jingle Bells, Archfiend Dice); heals (the Zombie Swords, Healing Melons); and the
 * Batphone and Royal Pigeon (Maddox, the King's commissions); Extreme Focus (your mana into your next hit), the
 * Tormentor's Chain of Agony (a toggle: melee hits chain to five mobs near), the Wither Cloak's Creeper Veil, the
 * Voodoo Doll, the Moody Grappleshot's pull; Slug Boots (a hurting fall bounces you up instead) and Spring Boots (sneak
 * to charge a jump).
 *
 * <p>Decided where Hypixel's rule needs systems we don't have: Soulcry's Ferocity counts against every mob (not only
 * Endermen); the Archfiend Dice's roll gives -120, -72, -24, +24, +72 or +120 Health; the Wither Shield's absorption is a
 * flat 200 Health; the teammate leaps go to the nearest other player on the island.
 */
final class MoreAbilities {
    private static final Pattern DAMAGE = Pattern.compile("([\\d,]+(?:\\.\\d+)?)(?:\\s+base)?(?:\\s+magic)?\\s+damage", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEARTS = Pattern.compile("([\\d,]+(?:\\.\\d+)?)❤");
    private static final Pattern STAT_BONUS = Pattern.compile("\\+([\\d.]+) (Strength|Attack Speed|Crit Damage|Crit Chance|Speed|Defense|Ferocity|Intelligence)");
    private static final Pattern FOR_SECONDS = Pattern.compile("for (\\d+(?:\\.\\d+)?)\\s*(?:s\\b|seconds)");
    private static final Map<String, StatType> STAT_NAMES = Map.of("Strength", StatTypes.STRENGTH, "Attack Speed", StatTypes.ATTACK_SPEED,
            "Crit Damage", StatTypes.CRIT_DAMAGE, "Crit Chance", StatTypes.CRIT_CHANCE, "Speed", StatTypes.SPEED, "Defense", StatTypes.DEFENSE,
            "Ferocity", StatTypes.FEROCITY, "Intelligence", StatTypes.INTELLIGENCE);

    /** The Archfiend Dice's roll ("amount|until epoch ms"), kept on the player for its 24 hours. */
    private static final AttachmentType<String> DICE = AttachmentRegistry.create(SkyRpgSkyblock.id("archfiend_dice"),
            builder -> builder.persistent(Codec.STRING).copyOnDeath());

    /** A stat bonus running out. */
    private record Timed(Map<StatType, Double> stats, long until) {
    }

    /** A multiplier on damage someone takes ({@code taken}) or deals, until a tick; {@code once} is used up by one hit. */
    private record Factor(double taken, double dealt, long until, boolean once) {
    }

    /** An area that hurts (or pulls) mobs every half second: around its owner, or at a spot. */
    private record Aura(UUID owner, ServerLevel level, Vec3 at, double radius, double perSecond, double pull, long until, ParticleOptions particle) {
    }

    private static final Map<UUID, Map<String, Timed>> BUFFS = new HashMap<>();
    private static final Map<UUID, Map<String, Factor>> FACTORS = new HashMap<>();
    private static final List<Aura> AURAS = new ArrayList<>();
    private static final List<Map.Entry<Long, Runnable>> LATER = new ArrayList<>();
    /** Shadow Warp's distortion waiting to be set off (its spot, until when). */
    private static final Map<UUID, Map.Entry<Vec3, Long>> WARPS = new HashMap<>();
    /** Ragnarock's channel: when it began and when you were last hurt. */
    private static final Map<UUID, Long> CHANNELS = new HashMap<>();
    private static final Map<UUID, Long> LAST_HURT = new HashMap<>();
    /** Blocks climbed with scaffolding since you last stood on the ground. */
    private static final Map<UUID, Integer> CLIMBED = new HashMap<>();
    /** Leaping Sword: waiting to land (until when). */
    private static final Map<UUID, Long> LEAPING = new HashMap<>();
    /** Extreme Focus: the mana your next hit adds, until when. */
    private static final Map<UUID, Map.Entry<Double, Long>> FOCUS = new HashMap<>();
    /** Tormentor's Chain of Agony switched on. */
    private static final Set<UUID> AGONY = new java.util.HashSet<>();
    /** Spring Boots: ticks of sneaking charged. */
    private static final Map<UUID, Integer> SPRING = new HashMap<>();

    private MoreAbilities() {
    }

    static void init() {
        // ---- shots
        Abilities.register("dreadlord", (p, a) -> shot(p, damage(p, a, 500), p.getLookAngle(), 30, 0, ParticleTypes.SMOKE, SoundEvents.WITHER_SHOOT) != null);
        Abilities.register("witherlord", (p, a) -> {
            double each = damage(p, a, 300);
            for (int i = -1; i <= 1; i++) {
                shot(p, each, p.getLookAngle().yRot((float) Math.toRadians(8 * i)), 30, 0, ParticleTypes.SMOKE, SoundEvents.WITHER_SHOOT);
            }
            return true;
        });
        Abilities.register("ink_bomb", (p, a) -> {
            Vec3 at = shot(p, damage(p, a, 10000), p.getLookAngle(), 20, 3, ParticleTypes.SQUID_INK, SoundEvents.SQUID_SQUIRT);
            for (LivingEntity mob : mobs(p, at, 3)) {
                mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
                mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            }
            return true;
        });
        Abilities.register("runic_zap", (p, a) -> pierce(p, damage(p, a, 10000), 20, 1, 1, ParticleTypes.ELECTRIC_SPARK));
        Abilities.register("bingo_blast", (p, a) -> pierce(p, damage(p, a, 800), 15, 99, 0.5, ParticleTypes.SPLASH));
        Abilities.register("eye_beam", (p, a) -> pierce(p, damage(p, a, 4000), 25, 99, 1, ParticleTypes.END_ROD));
        Abilities.register("sinrecall_transmission", (p, a) -> {
            pierce(p, damage(p, a, 1000), 4, 99, 1, ParticleTypes.SOUL_FIRE_FLAME);
            return blink(p, 4);
        });
        Abilities.register("terrain_toss", (p, a) -> shot(p, damage(p, a, 17000), p.getLookAngle(), 25, 2.5, ParticleTypes.CAMPFIRE_COSY_SMOKE,
                SoundEvents.GENERIC_EXPLODE.value()) != null);
        Abilities.register("ray_of_hope", (p, a) -> {
            Stats.spendMana(p, maxMana(p) * 0.02);
            return shot(p, damage(p, a, 2500), p.getLookAngle(), 30, 3, ParticleTypes.END_ROD, SoundEvents.BEACON_ACTIVATE) != null;
        });
        Abilities.register("coin_conversion", (p, a) -> {
            if (!Wallet.take(p, 100_000)) {
                p.sendOverlayMessage(Msg.text("You need 100,000 coins for that!", ChatFormatting.RED));
                return false;
            }
            Vec3 at = shot(p, damage(p, a, 10000), p.getLookAngle(), 30, 1.5, ParticleTypes.WAX_ON, SoundEvents.EXPERIENCE_ORB_PICKUP);
            for (LivingEntity mob : mobs(p, at, 1.5)) {
                mob.knockback(1.5, -p.getLookAngle().x, -p.getLookAngle().z, p.level().damageSources().indirectMagic(p, p), 1.5f);
            }
            return true;
        });
        Abilities.register("swing", (p, a) -> shot(p, melee(p), p.getLookAngle(), 15, 0, ParticleTypes.CRIT, SoundEvents.SNOWBALL_THROW) != null);
        Abilities.register("thwack", (p, a) -> shot(p, melee(p), p.getLookAngle(), 25, 0, ParticleTypes.CRIT, SoundEvents.SNOWBALL_THROW) != null);
        Abilities.register("lightning_strike", (p, a) -> {
            LivingEntity target = nearest(p, 10);
            if (target == null) {
                p.sendOverlayMessage(Msg.text("No monsters nearby!", ChatFormatting.RED));
                return false;
            }
            lightning((ServerLevel) p.level(), target.position());
            HypixelAbilities.hurt(p, target, damage(p, a, 40));
            return true;
        });
        Abilities.register("ender_warp", MoreAbilities::enderWarp);
        Abilities.register("heat_seeking_rose", (p, a) -> ricochet(p, melee(p), 3, 1.25));
        Abilities.register("petal_barrage", (p, a) -> {
            double missing = 1 - p.getHealth() / Math.max(1, p.getMaxHealth());
            double each = melee(p) * (1 + 0.02 * Math.floor(missing * 10));
            for (int i = 0; i < 3; i++) {
                ricochet(p, each, 5, 1);
            }
            return true;
        });
        // ---- blasts around you
        Abilities.register("explode", (p, a) -> {
            for (LivingEntity mob : around(p, 4, damage(p, a, 24000), ParticleTypes.FLAME, SoundEvents.GENERIC_EXPLODE.value())) {
                mob.igniteForSeconds(4);
            }
            return true;
        });
        Abilities.register("iron_punch", (p, a) -> around(p, 4, damage(p, a, 250), ParticleTypes.CRIT, SoundEvents.ANVIL_LAND) != null);
        // the Wither scrolls' 10,000 (NEU's lore shows one player's numbers, not the base)
        Abilities.register("implosion", (p, a) -> around(p, 10, HypixelAbilities.abilityDamage(p, 10000), ParticleTypes.EXPLOSION,
                SoundEvents.GENERIC_EXPLODE.value()) != null);
        // all three scrolls on a Necron's Blade: teleport, implode, shield
        Abilities.register("wither_impact", (p, a) -> {
            blink(p, 10);
            around(p, 10, HypixelAbilities.abilityDamage(p, 10000), ParticleTypes.EXPLOSION, SoundEvents.GENERIC_EXPLODE.value());
            witherShield(p);
            return true;
        });
        Abilities.register("detonate", (p, a) -> {
            ServerLevel level = (ServerLevel) p.level();
            for (LivingEntity mob : mobs(p, p.position(), 20)) {
                HypixelAbilities.hurt(p, mob, mob.getMaxHealth() * 0.8 * StatTypes.HEALTH_SCALE);
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 1, p.getZ(), 3, 2, 1, 2, 0);
            level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0f, 0.6f);
            consume(p);
            return true;
        });
        // ---- lingering areas
        Abilities.register("fire_veil", (p, a) -> aura(p, null, 3, damage(p, a, 15000), 0, 5, ParticleTypes.FLAME));
        Abilities.register("starfall", (p, a) -> aura(p, aim(p, 20), 3, damage(p, a, 300), 0, 5, ParticleTypes.END_ROD));
        Abilities.register("firestorm", (p, a) -> {
            Vec3 at = shot(p, 0, p.getLookAngle(), 25, 0, ParticleTypes.FLAME, SoundEvents.FIRECHARGE_USE);
            return aura(p, at, 7, damage(p, a, 42000) / 10, 0, 10, ParticleTypes.LAVA);
        });
        Abilities.register("fire_freeze", (p, a) -> {
            Vec3 at = p.position();
            ServerLevel level = (ServerLevel) p.level();
            aura(p, at, 5, 0, 0, 5, ParticleTypes.SNOWFLAKE);
            later(level.getServer(), 100, () -> {
                for (LivingEntity mob : mobs(p, at, 5)) {
                    mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 9));
                    mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 9));
                }
                level.playSound(null, BlockPos.containing(at), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0f, 0.6f);
            });
            return true;
        });
        Abilities.register("gravity_storm", (p, a) -> aura(p, aim(p, 30), 8, 0, 0.35, 3, ParticleTypes.REVERSE_PORTAL));
        Abilities.register("shadow_warp", (p, a) -> {
            ServerLevel level = (ServerLevel) p.level();
            long now = level.getServer().getTickCount();
            Map.Entry<Vec3, Long> waiting = WARPS.remove(p.getUUID());
            if (waiting != null && now < waiting.getValue()) {
                Vec3 at = waiting.getKey();
                for (LivingEntity mob : mobs(p, at, 6)) {
                    HypixelAbilities.hurt(p, mob, HypixelAbilities.abilityDamage(p, 10000));
                }
                AURAS.removeIf(aura -> aura.owner().equals(p.getUUID()) && aura.at() != null && aura.at().equals(at));
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 0.8f);
                return true;
            }
            Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(10));
            WARPS.put(p.getUUID(), Map.entry(at, now + 100));
            return aura(p, at, 8, 0, 0.4, 5, ParticleTypes.PORTAL);
        });
        Abilities.register("burning_souls", (p, a) -> {
            buff(p, "burning_souls", Map.of(StatTypes.DEFENSE, 75.0), 5);
            return aura(p, null, 4, damage(p, a, 30000) / 5, 0, 5, ParticleTypes.SOUL_FIRE_FLAME);
        });
        // ---- sweeping arcs
        Abilities.register("reaving_strike", (p, a) -> arc(p, 5, 120, melee(p) * 1.25));
        Abilities.register("flay", (p, a) -> arc(p, 6, 90, melee(p)));
        // ---- teleports
        Abilities.register("weird_transmission", HypixelAbilities::instantTransmission);
        Abilities.register("ether_transmission", MoreAbilities::etherwarp);
        Abilities.register("shadowstep", MoreAbilities::shadowstep);
        Abilities.register("shadow_fury", MoreAbilities::shadowFury);
        Abilities.register("gorilla_tactics", (p, a) -> {
            ServerLevel level = (ServerLevel) p.level();
            Vec3 mark = p.position();
            level.sendParticles(ParticleTypes.PORTAL, mark.x, mark.y + 1, mark.z, 30, 0.3, 0.6, 0.3, 0.1);
            later(level.getServer(), 60, () -> {
                if (p.isAlive() && p.level() == level) {
                    teleport(p, mark);
                }
            });
            return true;
        });
        for (String leap : new String[] {"spirit_leap", "haunt", "aspiring_leap"}) {
            Abilities.register(leap, MoreAbilities::toTeammate);
        }
        Abilities.register("leap", (p, a) -> {
            p.push(p.getLookAngle().x * 0.8, 1.1, p.getLookAngle().z * 0.8);
            p.syncVelocity = true;
            LEAPING.put(p.getUUID(), (long) p.level().getServer().getTickCount() + 100);
            return true;
        });
        Abilities.register("climb", (p, a) -> {
            int climbed = CLIMBED.getOrDefault(p.getUUID(), 0);
            if (climbed >= 16) {
                p.sendOverlayMessage(Msg.text("You can't climb any higher!", ChatFormatting.RED));
                return false;
            }
            CLIMBED.put(p.getUUID(), climbed + 4);
            p.setDeltaMovement(p.getDeltaMovement().x, 0.9, p.getDeltaMovement().z);
            p.syncVelocity = true;
            p.resetFallDistance();
            return true;
        });
        Abilities.register("water_burst", (p, a) -> {
            if (!p.isInWater() || !fullSet(p, "salmon_")) {
                return false;
            }
            p.push(p.getLookAngle().scale(1.6));
            p.syncVelocity = true;
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, p.getX(), p.getY() + 0.5, p.getZ(), 30, 0.4, 0.4, 0.4, 0.1);
            return true;
        });
        // ---- buffs
        Abilities.register("soulcry", (p, a) -> {
            buff(p, "soulcry", Map.of(StatTypes.FEROCITY, 400.0), 4);
            sound(p, SoundEvents.ENDERMAN_SCREAM, 0.6f);
            return true;
        });
        Abilities.register("howl", (p, a) -> {
            Map<StatType, Double> stats = statBonuses(a.description());
            double seconds = seconds(a.description(), 30);
            List<ServerPlayer> group = new ArrayList<>(List.of(p));
            group.addAll(others(p, 10, 4));
            for (ServerPlayer member : group) {
                buff(member, "howl", stats, seconds);
            }
            sound(p, SoundEvents.GOAT_HORN_SOUND_VARIANTS.getFirst().value(), 1.0f);
            return true;
        });
        Abilities.register("bad_health", (p, a) -> {
            double percent = p.getHealth() / Math.max(1, p.getMaxHealth()) * 100;
            buff(p, "bad_health", Map.of(StatTypes.STRENGTH, Math.min(100, 5 * Math.floor(percent / 5))), 5);
            return true;
        });
        Abilities.register("hellstorm", (p, a) -> {
            buff(p, "hellstorm", Map.of(StatTypes.ABILITY_DAMAGE, 5.0), 30);
            factor(p, "hellstorm", 2, 1, 30, false);
            return true;
        });
        Abilities.register("splash_yo_face", (p, a) -> {
            factor(p, "holy_ice", 0.2, 1, 1.5, false);
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1.5, p.getZ(), 40, 0.5, 0.3, 0.5, 0.1);
            return true;
        });
        Abilities.register("soulward", (p, a) -> {
            factor(p, "soulward", 0, 0, 5, false);
            later(p.level().getServer(), 100, () -> factor(p, "soulward_after", 1, 0.5, 2, false));
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.SOUL, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
            return true;
        });
        Abilities.register("ragnarock", (p, a) -> {
            CHANNELS.put(p.getUUID(), (long) p.level().getServer().getTickCount());
            p.sendSystemMessage(Msg.text("Channelling... don't get hit for 3 seconds!", ChatFormatting.GOLD));
            return true;
        });
        Abilities.register("enrage", (p, a) -> {
            for (LivingEntity mob : mobs(p, p.position(), 10)) {
                if (mob instanceof Mob m) {
                    m.setTarget(p);
                }
            }
            factor(p, "enrage", 0.9, 1, 10, false);
            sound(p, SoundEvents.RAVAGER_ROAR, 1.0f);
            return true;
        });
        Abilities.register("jingle_bells", (p, a) -> {
            Stats.spendMana(p, maxMana(p) * 0.5);
            for (LivingEntity mob : mobs(p, p.position(), 10)) {
                if (mob instanceof Mob m) {
                    m.setTarget(p);
                }
            }
            sound(p, SoundEvents.NOTE_BLOCK_BELL.value(), 1.4f);
            return true;
        });
        Abilities.register("life_blood", (p, a) -> {
            p.setHealth(Math.max(1, p.getHealth() - p.getMaxHealth() * 0.1f));
            for (ServerPlayer member : others(p, 10, 99)) {
                buff(member, "life_blood", Map.of(StatTypes.STRENGTH, 30.0), 10);
            }
            buff(p, "life_blood", Map.of(StatTypes.STRENGTH, 30.0), 10);
            return true;
        });
        Abilities.register("wither_shield", (p, a) -> {
            witherShield(p);
            return true;
        });
        Abilities.register("me_smash_head", (p, a) -> {
            factor(p, "edible_mace", 1, 2, 30, true);
            return true;
        });
        Abilities.register("try_your_luck", MoreAbilities::archfiendDice);
        // ---- heals
        Abilities.register("instant_heal", (p, a) -> {
            List<Double> hearts = hearts(a.description());
            p.heal((float) (hearts.isEmpty() ? 100 : hearts.getFirst()) / (float) StatTypes.HEALTH_SCALE);
            if (hearts.size() > 1) {
                for (ServerPlayer other : others(p, 8, 99)) {
                    other.heal((float) (hearts.get(1) / StatTypes.HEALTH_SCALE));
                }
            }
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.5, p.getZ(), 6, 0.5, 0.3, 0.5, 0);
            return true;
        });
        Abilities.register("succulence", (p, a) -> {
            List<Double> hearts = hearts(a.description());
            p.heal((float) (hearts.isEmpty() ? 7 : hearts.getFirst()) / (float) StatTypes.HEALTH_SCALE);
            sound(p, SoundEvents.GENERIC_EAT.value(), 1.0f);
            consume(p);
            return true;
        });
        Abilities.register("extreme_focus", (p, a) -> {
            double mana = Stats.mana(p);
            if (mana < 1) {
                return false;
            }
            Stats.spendMana(p, mana);
            FOCUS.put(p.getUUID(), Map.entry(mana, (long) p.level().getServer().getTickCount() + 600));
            p.sendOverlayMessage(Msg.text("Your next hit deals +" + Msg.number(Math.round(mana)) + " damage!", ChatFormatting.AQUA));
            return true;
        });
        Abilities.register("chain_of_agony", (p, a) -> {
            boolean on = AGONY.add(p.getUUID()) || !AGONY.remove(p.getUUID());
            p.sendOverlayMessage(Msg.text("Chain of Agony " + (on ? "on" : "off"), on ? ChatFormatting.DARK_PURPLE : ChatFormatting.GRAY));
            return true;
        });
        Abilities.register("creeper_veil", (p, a) -> {
            factor(p, "creeper_veil", 0, 1, 10, false);
            return aura(p, null, 1.2, 0, 0, 10, ParticleTypes.COMPOSTER);
        });
        Abilities.register("acupuncture", (p, a) -> {
            LivingEntity target = looked(p, 20);
            if (target == null) {
                p.sendOverlayMessage(Msg.text("No enemy in sight!", ChatFormatting.RED));
                return false;
            }
            ServerLevel level = (ServerLevel) p.level();
            Vec3 at = target.getBoundingBox().getCenter();
            for (int i = 0; i < 8; i++) {
                Vec3 from = at.add(Vec3.directionFromRotation(0, i * 45).scale(3)).add(0, 1, 0);
                for (double d = 0; d < 3; d += 0.4) {
                    Vec3 step = from.add(at.subtract(from).normalize().scale(d));
                    level.sendParticles(ParticleTypes.CRIT, step.x, step.y, step.z, 1, 0, 0, 0, 0);
                }
            }
            HypixelAbilities.hurt(p, target, melee(p) * 2);
            return true;
        });
        Abilities.register("big_pull", (p, a) -> {
            LivingEntity target = looked(p, 25);
            if (target == null) {
                return false;
            }
            Vec3 to = p.position().subtract(target.position());
            target.setDeltaMovement(to.normalize().scale(Math.min(2.5, to.length() * 0.25)).add(0, 0.4, 0));
            target.syncVelocity = true;
            return true;
        });
        // ---- calls
        Abilities.register("whassup", (p, a) -> Actions.run("open_slayer", p, new JsonObject()));
        Abilities.register("wasssaaaaa", (p, a) -> Actions.run("open_slayer", p, new JsonObject()));
        Abilities.register("seek_the_king", (p, a) -> Actions.run("commissions", p, new JsonObject()));

        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Abilities (timed)";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                Map<String, Timed> buffs = BUFFS.get(player.getUUID());
                if (buffs != null) {
                    for (Timed timed : buffs.values()) {
                        timed.stats().forEach(out::add);
                    }
                }
                String dice = player.getAttached(DICE);
                if (dice != null) {
                    String[] parts = dice.split("\\|");
                    if (System.currentTimeMillis() < Long.parseLong(parts[1])) {
                        out.add(StatTypes.HEALTH, Double.parseDouble(parts[0]));
                    }
                }
            }
        });
        IncomingDamage.registerHit((target, source) -> {
            double factor = 1;
            Map<String, Factor> taken = FACTORS.get(target.getUUID());
            if (taken != null) {
                for (Factor f : taken.values()) {
                    factor *= f.taken();
                }
            }
            if (source.getEntity() instanceof ServerPlayer attacker && attacker != target) {
                Map<String, Factor> dealt = FACTORS.get(attacker.getUUID());
                if (dealt != null) {
                    Iterator<Map.Entry<String, Factor>> it = dealt.entrySet().iterator();
                    while (it.hasNext()) {
                        Factor f = it.next().getValue();
                        factor *= f.dealt();
                        if (f.once() && f.dealt() != 1) {
                            it.remove();
                        }
                    }
                }
            }
            return factor;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity instanceof ServerPlayer player) {
                LAST_HURT.put(player.getUUID(), (long) player.level().getServer().getTickCount());
            }
            if (!(source.getEntity() instanceof ServerPlayer player) || entity == player || !(entity instanceof Mob)) {
                return;
            }
            boolean melee = source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK);
            boolean arrow = source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE);
            Map.Entry<Double, Long> focus = melee || arrow ? FOCUS.remove(player.getUUID()) : null;
            if (focus != null && player.level().getServer().getTickCount() < focus.getValue()) {
                HypixelAbilities.hurt(player, entity, focus.getKey());
            }
            if (melee && AGONY.contains(player.getUUID())) {
                ItemDefinition held = SkyItems.definition(player.getMainHandItem());
                if (held != null && "TORMENTOR".equals(held.skyblockId())) {
                    mobs(player, entity.position(), 5).stream().filter(m -> m != entity).limit(5).forEach(m -> {
                        HypixelAbilities.hurt(player, m, taken * StatTypes.HEALTH_SCALE);
                        ((ServerLevel) player.level()).sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 1, m.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
                    });
                }
            }
        });
        // Slug Boots: a fall that would hurt bounces you back up instead
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player && source.is(net.minecraft.world.damagesource.DamageTypes.FALL) && wearing(player, "SLUG_BOOTS")) {
                player.setDeltaMovement(player.getDeltaMovement().x, Math.min(1.6, 0.4 + player.fallDistance * 0.05), player.getDeltaMovement().z);
                player.syncVelocity = true;
                player.resetFallDistance();
                return false;
            }
            return true;
        });
        ServerTickEvents.END_SERVER_TICK.register(MoreAbilities::tick);
    }

    // ---------------------------------------------------------------- numbers

    /** The ability's damage from its description ("dealing 504 damage"), grown by Intelligence and Ability Damage. */
    private static double damage(ServerPlayer player, ItemDefinition.Ability ability, double fallback) {
        Matcher m = DAMAGE.matcher(ability.description());
        double base = m.find() ? Double.parseDouble(m.group(1).replace(",", "")) : fallback;
        return HypixelAbilities.abilityDamage(player, base);
    }

    /** A melee hit's damage, Hypixel's way: (5 + Damage) x (1 + Strength / 100). */
    private static double melee(ServerPlayer player) {
        return (5 + Math.max(0, Stats.get(player, StatTypes.DAMAGE))) * (1 + Math.max(0, Stats.get(player, StatTypes.STRENGTH)) / 100);
    }

    private static double maxMana(ServerPlayer player) {
        return 100 + Math.max(0, Stats.get(player, StatTypes.INTELLIGENCE));
    }

    private static List<Double> hearts(String text) {
        List<Double> out = new ArrayList<>();
        Matcher m = HEARTS.matcher(text);
        while (m.find()) {
            out.add(Double.parseDouble(m.group(1).replace(",", "")));
        }
        return out;
    }

    private static Map<StatType, Double> statBonuses(String text) {
        Map<StatType, Double> out = new LinkedHashMap<>();
        Matcher m = STAT_BONUS.matcher(text);
        while (m.find()) {
            out.merge(STAT_NAMES.get(m.group(2)), Double.parseDouble(m.group(1)), Double::sum);
        }
        return out;
    }

    private static double seconds(String text, double fallback) {
        Matcher m = FOR_SECONDS.matcher(text);
        return m.find() ? Double.parseDouble(m.group(1)) : fallback;
    }

    // ---------------------------------------------------------------- shapes

    private static List<LivingEntity> mobs(ServerPlayer player, Vec3 at, double radius) {
        if (at == null) {
            return List.of();
        }
        return player.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> e instanceof Mob && e.isAlive() && e.position().distanceTo(at) <= radius + e.getBbWidth());
    }

    private static LivingEntity nearest(ServerPlayer player, double radius) {
        return mobs(player, player.position(), radius).stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
    }

    /** Where you're aiming: the block (or the air) up to this far ahead. */
    private static Vec3 aim(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : end;
    }

    /**
     * A shot along a direction: the first mob it meets (or the wall) is hit, and with a {@code blast} radius everything
     * around that spot. Returns where it landed.
     */
    private static Vec3 shot(ServerPlayer player, double damage, Vec3 direction, double range, double blast, ParticleOptions particle, SoundEvent sound) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(direction.scale(range));
        BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() == HitResult.Type.BLOCK) {
            end = wall.getLocation();
        }
        LivingEntity first = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1), e -> e instanceof Mob && e.isAlive())) {
            if (target.getBoundingBox().inflate(0.4).clip(eye, end).isPresent() && target.distanceToSqr(eye) < best) {
                best = target.distanceToSqr(eye);
                first = target;
            }
        }
        Vec3 impact = first != null ? first.getBoundingBox().getCenter() : end;
        for (double d = 1; d < impact.distanceTo(eye); d += 0.7) {
            Vec3 at = eye.add(direction.scale(d));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        if (damage > 0) {
            if (blast > 0) {
                for (LivingEntity target : mobs(player, impact, blast)) {
                    HypixelAbilities.hurt(player, target, damage);
                }
                level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
            } else if (first != null) {
                HypixelAbilities.hurt(player, first, damage);
            }
        }
        level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
        return impact;
    }

    /** A beam through up to {@code count} mobs, each hit taking {@code falloff} times the one before. */
    private static boolean pierce(ServerPlayer player, double damage, double range, int count, double falloff, ParticleOptions particle) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = aim(player, range);
        List<LivingEntity> hit = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1),
                e -> e instanceof Mob && e.isAlive() && e.getBoundingBox().inflate(0.4).clip(eye, end).isPresent()));
        hit.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
        double each = damage;
        for (int i = 0; i < Math.min(count, hit.size()); i++) {
            HypixelAbilities.hurt(player, hit.get(i), each);
            each *= falloff;
        }
        for (double d = 1; d < end.distanceTo(eye); d += 0.5) {
            Vec3 at = eye.add(player.getLookAngle().scale(d));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GUARDIAN_ATTACK, SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    /** Everything within {@code radius} of you takes the damage. */
    private static List<LivingEntity> around(ServerPlayer player, double radius, double damage, ParticleOptions particle, SoundEvent sound) {
        ServerLevel level = (ServerLevel) player.level();
        List<LivingEntity> hit = mobs(player, player.position(), radius);
        for (LivingEntity mob : hit) {
            HypixelAbilities.hurt(player, mob, damage);
        }
        level.sendParticles(particle, player.getX(), player.getY() + 1, player.getZ(), (int) (radius * 12), radius / 2, 0.6, radius / 2, 0.05);
        level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, 1.0f);
        return hit;
    }

    /** A sweep in front of you: every mob within the arc takes the damage. */
    private static boolean arc(ServerPlayer player, double range, double degrees, double damage) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double cos = Math.cos(Math.toRadians(degrees / 2));
        for (LivingEntity mob : mobs(player, player.position(), range)) {
            Vec3 to = mob.getBoundingBox().getCenter().subtract(eye);
            if (to.normalize().dot(look) >= cos) {
                HypixelAbilities.hurt(player, mob, damage);
            }
        }
        for (int i = -3; i <= 3; i++) {
            Vec3 at = eye.add(look.yRot((float) Math.toRadians(degrees / 6 * i)).scale(range * 0.6));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y - 0.3, at.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);
        return true;
    }

    /** A shot that bounces to the next mob nearby, up to {@code bounces}, each hit {@code growth} times the last. */
    private static boolean ricochet(ServerPlayer player, double damage, int bounces, double growth) {
        ServerLevel level = (ServerLevel) player.level();
        LivingEntity target = null;
        Vec3 eye = player.getEyePosition();
        Vec3 end = aim(player, 20);
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.5), e -> e instanceof Mob && e.isAlive())) {
            if (mob.getBoundingBox().inflate(0.8).clip(eye, end).isPresent() && (target == null || mob.distanceToSqr(eye) < target.distanceToSqr(eye))) {
                target = mob;
            }
        }
        Set<LivingEntity> done = new java.util.HashSet<>();
        Vec3 from = eye;
        double each = damage;
        while (target != null && done.size() < bounces) {
            done.add(target);
            HypixelAbilities.hurt(player, target, each);
            Vec3 to = target.getBoundingBox().getCenter();
            for (double d = 0; d < from.distanceTo(to); d += 0.6) {
                Vec3 at = from.add(to.subtract(from).normalize().scale(d));
                level.sendParticles(ParticleTypes.CHERRY_LEAVES, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            }
            from = to;
            each *= growth;
            LivingEntity last = target;
            target = mobs(player, to, 8).stream().filter(m -> !done.contains(m))
                    .min(Comparator.comparingDouble(m -> m.distanceToSqr(last))).orElse(null);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 1.0f, 1.4f);
        return true;
    }

    private static void lightning(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.snapTo(at.x, at.y, at.z);
            level.addFreshEntity(bolt);
        }
    }

    /** A lingering area for {@code seconds}: around you ({@code at} null) or at a spot; hurts and/or pulls mobs in it. */
    private static boolean aura(ServerPlayer player, Vec3 at, double radius, double perSecond, double pull, double seconds, ParticleOptions particle) {
        long now = player.level().getServer().getTickCount();
        AURAS.add(new Aura(player.getUUID(), (ServerLevel) player.level(), at, radius, perSecond, pull, now + Math.round(seconds * 20), particle));
        return true;
    }

    // ---------------------------------------------------------------- teleports

    private static void teleport(ServerPlayer player, Vec3 to) {
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.5, 0.3, 0.2);
        player.teleport(new TeleportTransition(level, to, Vec3.ZERO, player.getYRot(), player.getXRot(), Set.of(), TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        level.playSound(null, BlockPos.containing(to), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    /** Teleports up to {@code blocks} ahead, stopping before walls. */
    private static boolean blink(ServerPlayer player, int blocks) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        for (double d = blocks; d >= 1; d -= 0.5) {
            Vec3 at = eye.add(player.getLookAngle().scale(d)).subtract(0, player.getEyeHeight(), 0);
            if (level.clip(new ClipContext(eye, at.add(0, player.getEyeHeight(), 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType()
                    == HitResult.Type.BLOCK) {
                continue;
            }
            if (level.noCollision(player, player.getBoundingBox().move(at.subtract(player.position())))) {
                teleport(player, at);
                return true;
            }
        }
        return true;
    }

    /** Etherwarp: onto the top of the block you're looking at (up to 57 blocks), if you fit there. */
    private static boolean etherwarp(ServerPlayer player, ItemDefinition.Ability ability) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(57)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.sendOverlayMessage(Msg.text("No block in range!", ChatFormatting.RED));
            return false;
        }
        Vec3 to = Vec3.atBottomCenterOf(hit.getBlockPos().above());
        if (!level.noCollision(player, player.getBoundingBox().move(to.subtract(player.position())))) {
            player.sendOverlayMessage(Msg.text("There's no room there!", ChatFormatting.RED));
            return false;
        }
        teleport(player, to);
        return true;
    }

    /** Behind the mob you're looking at (up to 20 blocks), with +25 Strength for 10 seconds. */
    private static boolean shadowstep(ServerPlayer player, ItemDefinition.Ability ability) {
        LivingEntity target = looked(player, 20);
        if (target == null) {
            player.sendOverlayMessage(Msg.text("No enemy in sight!", ChatFormatting.RED));
            return false;
        }
        Vec3 behind = target.position().add(Vec3.directionFromRotation(0, target.getYRot()).scale(-1.2));
        teleport(player, behind);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
        buff(player, "shadowstep", Map.of(StatTypes.STRENGTH, 25.0), 10);
        return true;
    }

    /** To up to 5 mobs within 12 blocks, one after another, rooting each. */
    private static boolean shadowFury(ServerPlayer player, ItemDefinition.Ability ability) {
        List<LivingEntity> targets = mobs(player, player.position(), 12).stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(player))).limit(5).toList();
        if (targets.isEmpty()) {
            player.sendOverlayMessage(Msg.text("No enemies nearby!", ChatFormatting.RED));
            return false;
        }
        for (int i = 0; i < targets.size(); i++) {
            LivingEntity target = targets.get(i);
            later(player.level().getServer(), i * 8L, () -> {
                if (target.isAlive() && player.isAlive() && target.level() == player.level()) {
                    teleport(player, target.position().add(Vec3.directionFromRotation(0, target.getYRot()).scale(-1.2)));
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 9));
                }
            });
        }
        return true;
    }

    private static boolean toTeammate(ServerPlayer player, ItemDefinition.Ability ability) {
        ServerPlayer mate = others(player, 500, 1).stream().findFirst().orElse(null);
        if (mate == null) {
            player.sendOverlayMessage(Msg.text("There's nobody to leap to!", ChatFormatting.RED));
            return false;
        }
        teleport(player, mate.position());
        factor(player, "leap_immunity", 0, 1, 1, false);
        return true;
    }

    /** Ender Bow: an ender pearl's flight in an instant; where it lands, mobs within 8 blocks lose 10% of their health (500 at most). */
    private static boolean enderWarp(ServerPlayer player, ItemDefinition.Ability ability) {
        Vec3 at = aim(player, 30);
        Vec3 landing = at.subtract(player.getLookAngle().scale(0.6));
        teleport(player, landing);
        for (LivingEntity mob : mobs(player, landing, 8)) {
            HypixelAbilities.hurt(player, mob, Math.min(500, mob.getHealth() * StatTypes.HEALTH_SCALE * 0.1));
        }
        return true;
    }

    private static LivingEntity looked(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = aim(player, range);
        LivingEntity best = null;
        for (LivingEntity mob : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1), e -> e instanceof Mob && e.isAlive())) {
            if (mob.getBoundingBox().inflate(0.5).clip(eye, end).isPresent() && (best == null || mob.distanceToSqr(eye) < best.distanceToSqr(eye))) {
                best = mob;
            }
        }
        return best;
    }

    /** Other players near you, nearest first. */
    private static List<ServerPlayer> others(ServerPlayer player, double radius, int count) {
        return ((ServerLevel) player.level()).players().stream()
                .filter(o -> o != player && o.isAlive() && !o.isSpectator() && o.distanceTo(player) <= radius)
                .sorted(Comparator.comparingDouble(o -> o.distanceToSqr(player))).limit(count).map(o -> (ServerPlayer) o).toList();
    }

    private static boolean fullSet(ServerPlayer player, String prefix) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemDefinition def = SkyItems.definition(player.getItemBySlot(slot));
            if (def == null || !def.id().getPath().startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- buffs

    private static void buff(ServerPlayer player, String key, Map<StatType, Double> stats, double seconds) {
        long until = player.level().getServer().getTickCount() + Math.round(seconds * 20);
        BUFFS.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(key, new Timed(stats, until));
        Stats.refresh(player);
    }

    private static void factor(ServerPlayer player, String key, double taken, double dealt, double seconds, boolean once) {
        long until = player.level().getServer().getTickCount() + Math.round(seconds * 20);
        FACTORS.computeIfAbsent(player.getUUID(), id -> new HashMap<>()).put(key, new Factor(taken, dealt, until, once));
    }

    /** Wither Shield: 10% less damage taken for 5 seconds and a 200 Health absorption shield. */
    private static void witherShield(ServerPlayer player) {
        factor(player, "wither_shield", 0.9, 1, 5, false);
        // 200 Health = 40 vanilla: Absorption X (absorption needs the effect to raise its cap)
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 9, false, false, true));
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.8, 0.5, 0.1);
    }

    /** Archfiend Dice: 666.7k coins for a roll of 1 to 6, -120 to +120 Health for a day (a new roll replaces it). */
    private static boolean archfiendDice(ServerPlayer player, ItemDefinition.Ability ability) {
        if (!Wallet.take(player, 666_700)) {
            player.sendOverlayMessage(Msg.text("You need 666,700 coins to roll!", ChatFormatting.RED));
            return false;
        }
        int roll = 1 + player.getRandom().nextInt(6);
        int health = -120 + (roll - 1) * 48;
        player.setAttached(DICE, health + "|" + (System.currentTimeMillis() + 24L * 3_600_000));
        Stats.refresh(player);
        player.sendSystemMessage(Msg.text("You rolled a " + roll + "! " + (health > 0 ? "+" : "") + health + "❤ Health for 24 hours.",
                health > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        sound(player, SoundEvents.ITEM_PICKUP, 0.6f);
        return true;
    }

    // ---------------------------------------------------------------- odds and ends

    private static void consume(ServerPlayer player) {
        if (!player.isCreative()) {
            player.getMainHandItem().shrink(1);
        }
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0f, pitch);
    }

    static void later(MinecraftServer server, long ticks, Runnable task) {
        LATER.add(Map.entry(server.getTickCount() + ticks, task));
    }

    private static boolean wearing(ServerPlayer player, String boots) {
        ItemDefinition def = SkyItems.definition(player.getItemBySlot(EquipmentSlot.FEET));
        return def != null && boots.equals(def.skyblockId());
    }

    /** Spring Boots: sneaking on the ground charges a jump (up to 2 seconds); letting go launches you. */
    private static void springBoots(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!wearing(player, "SPRING_BOOTS")) {
                SPRING.remove(player.getUUID());
                continue;
            }
            int charge = SPRING.getOrDefault(player.getUUID(), 0);
            if (player.isShiftKeyDown() && player.onGround()) {
                SPRING.put(player.getUUID(), Math.min(40, charge + 1));
                if (charge % 8 == 0 && charge > 0) {
                    ((ServerLevel) player.level()).sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 3, 0.2, 0, 0.2, 0);
                }
            } else if (charge > 0) {
                SPRING.remove(player.getUUID());
                if (charge >= 10 && !player.isShiftKeyDown()) {
                    player.setDeltaMovement(player.getDeltaMovement().x, 0.6 + charge * 0.025, player.getDeltaMovement().z);
                    player.syncVelocity = true;
                    player.level().playSound(null, player.blockPosition(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0f, 1.0f);
                }
            }
        }
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        springBoots(server);
        if (!LATER.isEmpty()) {
            List<Runnable> due = new ArrayList<>();
            LATER.removeIf(e -> {
                if (now >= e.getKey()) {
                    due.add(e.getValue());
                    return true;
                }
                return false;
            });
            due.forEach(Runnable::run);
        }
        // buffs and factors running out
        BUFFS.entrySet().removeIf(entry -> {
            boolean changed = entry.getValue().values().removeIf(t -> now >= t.until());
            if (changed) {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    server.execute(() -> Stats.refresh(player));
                }
            }
            return entry.getValue().isEmpty();
        });
        FACTORS.entrySet().removeIf(entry -> {
            entry.getValue().values().removeIf(f -> now >= f.until());
            return entry.getValue().isEmpty();
        });
        if (now % 10 == 0) {
            pulseAuras(server, now);
        }
        // Ragnarock: 3 seconds unhurt since the channel began
        CHANNELS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                return true;
            }
            if (LAST_HURT.getOrDefault(entry.getKey(), -1L) > entry.getValue()) {
                player.sendOverlayMessage(Msg.text("Ragnarock's channel was broken!", ChatFormatting.RED));
                return true;
            }
            if (now - entry.getValue() >= 60) {
                ItemDefinition def = SkyItems.definition(player.getMainHandItem());
                double strength = def == null ? 0 : def.stat(StatTypes.STRENGTH);
                buff(player, "ragnarock", Map.of(StatTypes.STRENGTH, strength * 0.5), 10);
                player.sendOverlayMessage(Msg.text("Ragnarock! +" + Msg.number(strength * 0.5) + " Strength", ChatFormatting.GOLD));
                return true;
            }
            return false;
        });
        // scaffolding climbs reset on the ground; the Leaping Sword lands
        CLIMBED.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            return player == null || player.onGround();
        });
        LEAPING.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || now > entry.getValue()) {
                return true;
            }
            if (player.onGround() && entry.getValue() - now < 90) {
                for (LivingEntity mob : around(player, 4, HypixelAbilities.abilityDamage(player, 350), ParticleTypes.CLOUD, SoundEvents.GENERIC_SMALL_FALL)) {
                    mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 9));
                }
                return true;
            }
            return false;
        });
    }

    private static void pulseAuras(MinecraftServer server, long now) {
        Iterator<Aura> it = AURAS.iterator();
        while (it.hasNext()) {
            Aura aura = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(aura.owner());
            if (owner == null || now >= aura.until() || owner.level() != aura.level()) {
                it.remove();
                continue;
            }
            Vec3 center = aura.at() != null ? aura.at() : owner.position();
            for (LivingEntity mob : mobs(owner, center, aura.radius())) {
                if (aura.perSecond() > 0) {
                    HypixelAbilities.hurt(owner, mob, aura.perSecond() / 2);
                }
                if (aura.pull() > 0) {
                    Vec3 to = center.subtract(mob.position());
                    mob.setDeltaMovement(mob.getDeltaMovement().add(to.normalize().scale(Math.min(aura.pull(), to.length() * 0.3))));
                    mob.syncVelocity = true;
                }
            }
            aura.level().sendParticles(aura.particle(), center.x, center.y + 0.5, center.z, (int) (aura.radius() * 8), aura.radius() / 2, 0.4,
                    aura.radius() / 2, 0.02);
        }
    }
}
