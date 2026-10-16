package com.skyrpg.combat.slayer;

import com.skyrpg.combat.MobLevels;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.TrueDamage;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.world.MobTags;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's slayer bosses fight the way they do on Hypixel (the fandom wiki's ability tables), each tier keeping the
 * tiers below:
 * <ul>
 *   <li>All of them: hits of their DPS about once a second; they regenerate fast when they haven't hurt anyone for 5
 *   seconds; the Revenant, Tarantula and Sven slam-dunk a target who stands in one block too long (pulled in, True
 *   Damage).</li>
 *   <li>Revenant Horror: arrows heal it; Life Drain; Pestilence (II, Defense shred); Enrage (III); the Atoned Horror (V)
 *   regenerates, throws TNT at a circle under you (heals when it lands on you) and every 30 seconds goes off
 *   (Thermonuclear).</li>
 *   <li>Tarantula Broodfather: a cave spider rides it; Backstab; Noxious Paralysis (II); Web of Lies (III, egg sacs);
 *   Bat Barrage (IV); Till Death Do Us Part (V, the Conjoined Brood).</li>
 *   <li>Sven Packmaster: Agile; True Damage (II); Call the pups! (III).</li>
 *   <li>Voidgloom Seraph: Dissonance; Malevolent Hitshield; Yang Glyphs (II); Nukekubi Fixations (III); Broken Heart
 *   Radiation (IV).</li>
 * </ul>
 * Their kinds ("revenant", "tarantula", "sven", "voidgloom") keep SkyRPG's survival tricks out of these fights.
 */
final class HypixelBosses {
    private static final String PART_TAG = "skyrpg_slayer_part";
    private static final DustParticleOptions RED = new DustParticleOptions(0xE02020, 1.2f);
    private static final DustParticleOptions WHITE = new DustParticleOptions(0xFFFFFF, 1.0f);
    private static final DustParticleOptions BLUE = new DustParticleOptions(0x3FA9F5, 0.9f);
    private static final DustParticleOptions PINK = new DustParticleOptions(0xFF5FA2, 1.4f);
    private static final double[] BACKSTAB = {0.5, 0.75, 1.0, 1.25, 1.5};
    private static final double[] NOXIOUS_VITALITY = {25, 50, 75, 99};
    private static final double[] NOXIOUS_SPEED = {0.5, 1, 1, 2};
    private static final double[] SVEN_TRUE = {10, 50, 200};
    private static final int[] HITSHIELD = {15, 30, 60, 100};
    private static final String[] DIFFICULTY = {"Beginner", "Strong", "Challenging", "Deadly", "Excruciating"};

    /** One Hypixel fight's own bookkeeping, kept in its {@link SlayerBosses.Run}. */
    static final class State {
        double baseDamage;
        double baseSpeed;
        double damageFactor = 1;
        BlockPos ownerBlock;
        long ownerStillSince;
        // Revenant Horror
        long madAt;
        long enragedUntil;
        long nextTnt;
        long boomAt;
        long nextBoom;
        // Tarantula Broodfather
        Mob rider;
        long nextBackstab;
        int hitsDealt;
        int websDone;
        boolean bats;
        long nextBats;
        boolean fused;
        // Sven Packmaster
        boolean pupsCalled;
        int pupsSpawned;
        final List<Mob> pups = new ArrayList<>();
        // Voidgloom Seraph
        int shield;
        int shieldsUsed;
        int shieldBonus;
        long shieldSince;
        long nextDissonance;
        long nextGlyph;
        long glyphThrowAt;
        int heartsDone;
        long radiationUntil;
        double beamAngle;
        final Map<UUID, Long> beamCooldown = new HashMap<>();
        long nextHead;
        final Map<BossMoves.Totem, Integer> looked = new HashMap<>();
    }

    private HypixelBosses() {
    }

    static boolean isHypixelKind(String kind) {
        return switch (kind) {
            case "revenant", "tarantula", "sven", "voidgloom" -> true;
            default -> false;
        };
    }

    static void init() {
        // Arrows heal the Revenant; the Tarantula strikes harder from behind and, in Bat Barrage, shrugs off magic;
        // nothing hurts a boss that's protected (Sven's pups, the Voidgloom's radiation).
        IncomingDamage.registerHit(HypixelBosses::hitMultiplier);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // The cave spider riding the Tarantula never gets hurt (swings at it go to the Tarantula, below).
            if (entity.entityTags().contains(PART_TAG)) {
                return false;
            }
            SlayerBosses.Run run = runOf(entity);
            if (run == null || !fromPlayer(source)) {
                return true;
            }
            State state = run.hx;
            if (state.shield > 0) {
                hitShield(run);
                return false;
            }
            return true;
        });
        // A swing at the rider is a swing at the Tarantula it rides.
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!level.isClientSide() && entity.entityTags().contains(PART_TAG) && entity.getVehicle() instanceof Mob boss) {
                player.attack(boss);
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
        // Till Death Do Us Part: the Primordial Broodfather gets back up as the Conjoined Brood.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            SlayerBosses.Run run = runOf(entity);
            if (run == null || !run.line.kind().equals("tarantula") || run.tierNumber < 5 || run.hx.fused) {
                return true;
            }
            fuse(run);
            return false;
        });
    }

    private static SlayerBosses.Run runOf(Entity entity) {
        if (!(entity instanceof Mob mob) || !mob.entityTags().contains(SlayerBosses.BOSS_TAG)) {
            return null;
        }
        SlayerBosses.Run run = SlayerBosses.byBoss(mob);
        return run != null && run.line.hypixel() ? run : null;
    }

    private static boolean fromPlayer(DamageSource source) {
        return source.getEntity() instanceof ServerPlayer
                || source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer;
    }

    // ---------------------------------------------------------------- set-up

    /** The boss's name: the Revenant and Tarantula's fifth tiers are their own bosses. */
    static String bossName(SlayerLine line, int tier) {
        if (tier >= 5 && line.kind().equals("revenant")) {
            return "Atoned Horror";
        }
        if (tier >= 5 && line.kind().equals("tarantula")) {
            return "Primordial Broodfather";
        }
        return line.name();
    }

    /** Before the boss enters the world: Hypixel's damage per second, speed and looks. */
    static void setUp(Mob boss, SlayerLine line, int tier, SlayerLine.Tier stats) {
        // A hit about once a second: the attack is the tier's DPS.
        set(boss, Attributes.ATTACK_DAMAGE, dps(stats) / StatTypes.HEALTH_SCALE);
        double speed = boss.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) * (line.kind().equals("sven") ? 1.35 : 1.15);
        set(boss, Attributes.MOVEMENT_SPEED, speed);
        if (line.kind().equals("revenant") && tier >= 5) {
            // The Atoned Horror: netherite where the Revenant wears diamond, and a blade.
            boss.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
            boss.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
            boss.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
            boss.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
            boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                boss.setDropChance(slot, 0);
            }
        } else if (line.kind().equals("revenant")) {
            boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_HOE));
            boss.setDropChance(EquipmentSlot.MAINHAND, 0);
        }
    }

    /** The boss is in the world and its fight has begun. */
    static void started(SlayerBosses.Run run, ServerPlayer owner) {
        State state = run.hx;
        state.baseDamage = run.boss.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
        state.baseSpeed = run.boss.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        long now = SlayerBosses.now();
        state.ownerStillSince = now;
        switch (run.line.kind()) {
            case "revenant" -> {
                state.madAt = now + 800;
                state.nextTnt = now + 40;
                state.nextBoom = now + 600;
            }
            case "tarantula" -> {
                state.nextBackstab = now + 100;
                Mob rider = EntityTypes.CAVE_SPIDER.create(run.level, EntitySpawnReason.EVENT);
                if (rider != null) {
                    rider.snapTo(run.boss.getX(), run.boss.getY(), run.boss.getZ(), run.boss.getYRot(), 0);
                    rider.setNoAi(true);
                    rider.setSilent(true);
                    rider.setPersistenceRequired();
                    rider.addTag(PART_TAG);
                    rider.addTag(SlayerBosses.MINION_TAG);
                    run.level.addFreshEntity(rider);
                    rider.startRiding(run.boss, true, true);
                    state.rider = rider;
                }
            }
            case "voidgloom" -> {
                state.nextDissonance = now + 160;
                raiseShield(run);
            }
            default -> {
            }
        }
    }

    /** The fight is over or the boss left: its extras go, and the curses on the players lift. */
    static void takeDown(SlayerBosses.Run run) {
        State state = run.hx;
        if (state.radiationUntil != 0 && run.boss != null) {
            run.boss.setNoAi(false);
            state.radiationUntil = 0;
        }
        if (state.rider != null) {
            state.rider.discard();
            state.rider = null;
        }
        state.pups.forEach(pup -> {
            if (pup.isAlive()) {
                pup.discard();
            }
        });
        state.pups.clear();
        if (run.level != null) {
            for (ServerPlayer player : run.level.getServer().getPlayerList().getPlayers()) {
                SlayerCurses.lift(player);
            }
        }
    }

    // ---------------------------------------------------------------- the fight, every tick

    static void tick(SlayerBosses.Run run, ServerPlayer owner, long t, float health) {
        Mob boss = run.boss;
        State state = run.hx;
        long now = SlayerBosses.now();
        int tier = run.tierNumber;

        // Standing in one block: the slam-dunk's clock (it moves on as soon as the player does).
        BlockPos block = owner.blockPosition();
        if (!block.equals(state.ownerBlock)) {
            state.ownerBlock = block;
            state.ownerStillSince = now;
        }

        boolean busy = state.boomAt > now || state.radiationUntil > now || calling(state);
        // Regeneration: a boss that hasn't hurt anyone for 5 seconds heals fast.
        if (now % 20 == 0 && now - run.lastHit > 100 && !busy && !(state.pupsCalled && !state.pups.isEmpty())) {
            boss.heal(boss.getMaxHealth() * 0.02f);
            SlayerBosses.particles(run.level, ParticleTypes.HAPPY_VILLAGER, boss.position().add(0, 1.2, 0), 4, 0.4, 0);
        }
        // Slam-dunk: a player who stays in one block too long gets pulled in.
        if (!run.line.kind().equals("voidgloom") && now - state.ownerStillSince > 120 && now - run.lastHit > 100 && !busy) {
            slamDunk(run, owner);
            state.ownerStillSince = now;
        }

        switch (run.line.kind()) {
            case "revenant" -> revenant(run, owner, t, health, now, tier);
            case "tarantula" -> tarantula(run, owner, health, now, tier);
            case "sven" -> sven(run, owner, health, now, tier);
            case "voidgloom" -> voidgloom(run, owner, health, now, tier);
            default -> {
            }
        }
        if (now % 20 == 0) {
            applyStats(run);
        }
    }

    /** The boss's damage and speed from its state: enraged, shield bonus, heads, frozen. */
    private static void applyStats(SlayerBosses.Run run) {
        State state = run.hx;
        long now = SlayerBosses.now();
        double damage = state.baseDamage * state.damageFactor;
        if (state.enragedUntil > now) {
            damage *= 3;
        }
        damage += state.shieldBonus / StatTypes.HEALTH_SCALE;
        if (headsDps(run) > dps(run.tier)) {
            // The heads' damage outdoes the boss's own: only the heads hurt.
            damage = 0;
        }
        double speed = state.baseSpeed;
        if (state.enragedUntil > now) {
            speed *= 1.3;
        }
        if (state.boomAt > now || state.radiationUntil > now) {
            speed = 0;
            damage = 0;
        } else if (calling(state)) {
            speed *= 0.25;
        }
        set(run.boss, Attributes.ATTACK_DAMAGE, damage);
        set(run.boss, Attributes.MOVEMENT_SPEED, speed);
    }

    /** A hit on a player (melee or ability). */
    static void bossHit(SlayerBosses.Run run, ServerPlayer player, boolean melee) {
        int tier = run.tierNumber;
        switch (run.line.kind()) {
            case "revenant" -> {
                if (melee) {
                    // A speed boost on hit.
                    run.boss.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, 1));
                }
            }
            case "tarantula" -> {
                if (melee && tier >= 2 && ++run.hx.hitsDealt % 5 == 0) {
                    SlayerCurses.slow(player, NOXIOUS_SPEED[Math.min(tier, 5) - 2]);
                    player.sendOverlayMessage(Msg.text("Noxious Paralysis slows you down!", ChatFormatting.DARK_GREEN));
                }
            }
            case "sven" -> {
                if (melee && tier >= 2) {
                    TrueDamage.hurt(player, source(run), SVEN_TRUE[Math.min(tier, 4) - 2]);
                }
            }
            default -> {
            }
        }
    }

    /** Hits on the boss: arrows heal the Revenant; nothing gets through a protected boss. */
    private static double hitMultiplier(LivingEntity entity, DamageSource source) {
        SlayerBosses.Run run = runOf(entity);
        if (run != null) {
            State state = run.hx;
            long now = SlayerBosses.now();
            if (state.radiationUntil > now || state.pupsCalled && !state.pups.isEmpty()) {
                return 0;
            }
            if (run.line.kind().equals("revenant") && source.getDirectEntity() instanceof AbstractArrow) {
                run.boss.heal(run.boss.getMaxHealth() * 0.005f);
                SlayerBosses.particles(run.level, ParticleTypes.HEART, run.boss.position().add(0, 2, 0), 2, 0.3, 0);
                return 0;
            }
            if (run.line.kind().equals("tarantula") && state.bats && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC))) {
                return 0;
            }
            return 1;
        }
        // Backstab: the Tarantula's hits from behind.
        if (entity instanceof ServerPlayer player && source.getEntity() instanceof Mob attacker) {
            SlayerBosses.Run attackerRun = runOf(attacker);
            if (attackerRun != null && attackerRun.line.kind().equals("tarantula") && source.getDirectEntity() == attacker) {
                Vec3 toBoss = attacker.position().subtract(player.position()).multiply(1, 0, 1).normalize();
                Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
                if (look.dot(toBoss) < -0.2) {
                    return 1 + BACKSTAB[Math.min(attackerRun.tierNumber, 5) - 1];
                }
            }
        }
        return 1;
    }

    // ---------------------------------------------------------------- shared moves

    /** Ability damage from the boss: through Defense, or true damage; hurting someone stops its regeneration. */
    private static void hurt(SlayerBosses.Run run, ServerPlayer player, double amount, boolean trueDamage) {
        if (player.isCreative() || player.isSpectator() || amount <= 0) {
            return;
        }
        if (trueDamage) {
            TrueDamage.hurt(player, source(run), amount);
        } else {
            TrueDamage.ability(player, source(run), amount);
        }
    }

    /** The boss's ability damage: caused by the boss but not its melee (so it doesn't count as a melee hit). */
    private static DamageSource source(SlayerBosses.Run run) {
        return new DamageSource(run.level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.INDIRECT_MAGIC),
                null, run.boss);
    }

    /** The tier's damage per second (the line's data keeps it as damage = DPS / 15). */
    private static double dps(SlayerLine.Tier tier) {
        return tier.damage() * 15;
    }

    private static double maxHealth(ServerPlayer player) {
        return player.getMaxHealth() * StatTypes.HEALTH_SCALE;
    }

    private static void slamDunk(SlayerBosses.Run run, ServerPlayer owner) {
        Mob boss = run.boss;
        Vec3 pull = boss.position().subtract(owner.position()).normalize().scale(1.4).add(0, 0.5, 0);
        owner.push(pull.x, pull.y, pull.z);
        owner.syncVelocity = true;
        SlayerBosses.sound(run, SoundEvents.ANVIL_LAND, 1.0f, 0.6f);
        SlayerBosses.particles(run.level, ParticleTypes.EXPLOSION, owner.position().add(0, 1, 0), 1, 0, 0);
        owner.sendOverlayMessage(Msg.text("Slam-dunked! Don't stand still.", ChatFormatting.RED, ChatFormatting.BOLD));
        hurt(run, owner, dps(run.tier) * 2, true);
    }

    private static void line(ServerLevel level, net.minecraft.core.particles.ParticleOptions particle, Vec3 from, Vec3 to, int points) {
        for (int i = 0; i <= points; i++) {
            Vec3 p = from.lerp(to, i / (double) points);
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    // ---------------------------------------------------------------- Revenant Horror

    private static void revenant(SlayerBosses.Run run, ServerPlayer owner, long t, float health, long now, int tier) {
        Mob boss = run.boss;
        State state = run.hx;
        if (tier >= 5) {
            atoned(run, owner, health, now);
            return;
        }
        // Life Drain: every 5 seconds it drains you from up to 12 blocks and heals.
        if (t % 100 == 50 && boss.distanceTo(owner) < 12) {
            line(run.level, ParticleTypes.DAMAGE_INDICATOR, owner.position().add(0, 1, 0), boss.position().add(0, 1.4, 0), 10);
            SlayerBosses.sound(run, SoundEvents.WITCH_DRINK, 1.0f, 0.6f);
            hurt(run, owner, dps(run.tier) * 1.5, false);
            boss.heal(boss.getMaxHealth() * 0.01f);
        }
        // Pestilence: everyone within 5 blocks, every second, and their Defense shredded by a quarter.
        if (tier >= 2 && t % 20 == 0) {
            SlayerBosses.ring(run.level, ParticleTypes.SNEEZE, boss.position().add(0, 0.3, 0), 5, 26);
            for (ServerPlayer player : SlayerBosses.players(run, 5)) {
                hurt(run, player, dps(run.tier) * 0.25, false);
                SlayerCurses.shred(player, 0.25, 60);
            }
        }
        // Enrage: mad after 40 seconds, enraged 5 seconds later for 12; then it starts over.
        if (tier >= 3) {
            if (state.madAt > 0 && now == state.madAt) {
                SlayerBosses.tell(run, Msg.text("The Revenant Horror is getting MAD!", ChatFormatting.RED));
                SlayerBosses.updateBar(run);
            }
            if (state.madAt > 0 && now >= state.madAt + 100) {
                state.enragedUntil = now + 240;
                state.madAt = 0;
                SlayerBosses.sound(run, SoundEvents.RAVAGER_ROAR, 1.5f, 0.7f);
                SlayerBosses.tell(run, Msg.text("The Revenant Horror is ENRAGED!", ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                applyStats(run);
                SlayerBosses.updateBar(run);
            }
            if (state.enragedUntil > now && t % 5 == 0) {
                SlayerBosses.particles(run.level, ParticleTypes.ANGRY_VILLAGER, boss.position().add(0, 2.2, 0), 1, 0.4, 0);
                SlayerBosses.particles(run.level, ParticleTypes.FLAME, boss.position().add(0, 1, 0), 4, 0.4, 0.02);
            }
            if (state.enragedUntil != 0 && now >= state.enragedUntil) {
                state.enragedUntil = 0;
                state.madAt = now + 800;
                applyStats(run);
                SlayerBosses.updateBar(run);
            }
        }
    }

    /** The Atoned Horror: Redemption, Explosive Assault and Thermonuclear. */
    private static void atoned(SlayerBosses.Run run, ServerPlayer owner, float health, long now) {
        Mob boss = run.boss;
        State state = run.hx;
        // Redemption: a thousandth of its health every second.
        if (now % 20 == 0) {
            boss.heal(boss.getMaxHealth() * 0.001f);
        }
        // Thermonuclear: every 30 seconds it stops for 5 and then blasts everything within 7 blocks.
        if (state.boomAt == 0 && now >= state.nextBoom) {
            state.boomAt = now + 100;
            SlayerBosses.tell(run, Msg.text("The Atoned Horror is charging up... GET AWAY!", ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            SlayerBosses.sound(run, SoundEvents.TNT_PRIMED, 2f, 0.5f);
            applyStats(run);
        }
        if (state.boomAt > now) {
            long left = state.boomAt - now;
            double radius = 7 * (1 - left / 100.0);
            if (now % 4 == 0) {
                SlayerBosses.ring(run.level, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.BEDROCK.defaultBlockState()),
                        boss.position().add(0, 0.2, 0), Math.max(0.5, radius), 30);
            }
            if (left % 20 == 0) {
                SlayerBosses.updateBar(run);
                for (ServerPlayer player : SlayerBosses.players(run, 20)) {
                    player.sendOverlayMessage(Msg.text("Boom! " + left / 20, ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                }
            }
            return;
        }
        if (state.boomAt != 0 && now >= state.boomAt) {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(run.level, EntitySpawnReason.EVENT);
                if (bolt != null) {
                    bolt.setVisualOnly(true);
                    bolt.snapTo(boss.getX() + Math.cos(angle) * 4, boss.getY(), boss.getZ() + Math.sin(angle) * 4, 0, 0);
                    run.level.addFreshEntity(bolt);
                }
            }
            for (ServerPlayer player : SlayerBosses.players(run, 7)) {
                hurt(run, player, maxHealth(player) * 3, true);
            }
            state.boomAt = 0;
            state.nextBoom = now + 600;
            applyStats(run);
            SlayerBosses.updateBar(run);
            return;
        }
        // Explosive Assault: TNT on a circle under you every second, twice as often below a third of its health.
        if (now >= state.nextTnt) {
            state.nextTnt = now + (health < 0.33f ? 10 : 20);
            Vec3 center = SlayerBosses.ground(run.level, owner.position());
            for (int i = 0; i <= 20; i += 5) {
                BossMoves.later(run, i, () -> {
                    SlayerBosses.ring(run.level, WHITE, center.add(0, 0.15, 0), 3.5, 28);
                    SlayerBosses.ring(run.level, RED, center.add(0, 0.15, 0), 1.2, 12);
                });
            }
            BossMoves.later(run, 20, () -> {
                run.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 0.5, center.z, 1, 0, 0, 0, 0);
                run.level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE,
                        1.2f, 1.0f);
                for (ServerPlayer player : SlayerBosses.players(run, 30)) {
                    double distance = player.position().distanceTo(center);
                    if (distance > 3.5) {
                        continue;
                    }
                    hurt(run, player, maxHealth(player) * (0.15 + 0.45 * (1 - distance / 3.5)), true);
                    if (player.getUUID().equals(run.owner)) {
                        run.boss.heal(run.boss.getMaxHealth() * 0.05f);
                        SlayerBosses.particles(run.level, ParticleTypes.HEART, run.boss.position().add(0, 2, 0), 5, 0.4, 0);
                    }
                }
            });
        }
    }

    // ---------------------------------------------------------------- Tarantula Broodfather

    private static void tarantula(SlayerBosses.Run run, ServerPlayer owner, float health, long now, int tier) {
        Mob boss = run.boss;
        State state = run.hx;
        if (state.rider != null && !state.rider.isPassenger() && boss.isAlive()) {
            state.rider.startRiding(boss, true, true);
        }
        if (tier >= 5 && now % 20 == 0) {
            boss.heal(boss.getMaxHealth() * 0.001f);
        }
        // Backstab: it leaps behind you every so often.
        if (now >= state.nextBackstab && boss.distanceTo(owner) < 16) {
            state.nextBackstab = now + 140;
            Vec3 behind = owner.position().subtract(owner.getLookAngle().multiply(1, 0, 1).normalize().scale(2));
            Vec3 at = SlayerBosses.ground(run.level, behind);
            SlayerBosses.particles(run.level, ParticleTypes.POOF, boss.position().add(0, 0.5, 0), 10, 0.4, 0.02);
            boss.teleportTo(at.x, at.y, at.z);
            boss.setYRot((float) Math.toDegrees(Math.atan2(owner.getZ() - at.z, owner.getX() - at.x)) - 90);
            SlayerBosses.sound(run, SoundEvents.SPIDER_AMBIENT, 1.5f, 0.6f);
            owner.sendOverlayMessage(Msg.text("The Broodfather leaps behind you!", ChatFormatting.RED));
        }
        // Noxious Paralysis: less healing for everyone fighting it, while it lives.
        if (tier >= 2 && now % 20 == 0) {
            for (ServerPlayer player : SlayerBosses.players(run, 30)) {
                SlayerCurses.vitality(player, NOXIOUS_VITALITY[Math.min(tier, 5) - 2]);
            }
        }
        if (state.fused) {
            // The Conjoined Brood keeps only the passive abilities.
            return;
        }
        // Web of Lies: at two thirds and a third of its health.
        int webs = health < 0.33f ? 2 : health < 0.66f ? 1 : 0;
        if (tier >= 3 && webs > state.websDone) {
            state.websDone = webs;
            webOfLies(run, owner);
        }
        // Bat Barrage: from two thirds of its health, a horde of bats each taking a share of your health a second.
        if (tier >= 4 && health < 0.66f && !state.bats) {
            state.bats = true;
            state.nextBats = now;
            SlayerBosses.tell(run, Msg.text("Bat Barrage! The Broodfather can't be hurt by magic now.", ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            SlayerBosses.updateBar(run);
        }
        if (state.bats && now >= state.nextBats) {
            state.nextBats = now + 400;
            SlayerBosses.raiseAt(run, owner, EntityTypes.BAT, 5, boss.position().add(0, 1.5, 0));
            SlayerBosses.sound(run, SoundEvents.BAT_TAKEOFF, 1.5f, 0.8f);
        }
        if (state.bats && now % 20 == 0) {
            long bats = run.minions.stream().filter(minion -> minion.isAlive() && minion.getType() == EntityTypes.BAT).count();
            if (bats > 0) {
                hurt(run, owner, maxHealth(owner) * (tier >= 5 ? 0.02 : 0.01) * bats, true);
            }
        }
    }

    private static void webOfLies(SlayerBosses.Run run, ServerPlayer owner) {
        Mob boss = run.boss;
        RandomSource random = run.level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        Vec3 spot = SlayerBosses.ground(run.level, boss.position().add(Math.cos(angle) * 6, 0, Math.sin(angle) * 6));
        boss.teleportTo(spot.x, spot.y, spot.z);
        SlayerBosses.sound(run, SoundEvents.SPIDER_AMBIENT, 2f, 0.5f);
        SlayerBosses.tell(run, Msg.text("Web of Lies! Smash the egg sacs before they hatch!", ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        long until = SlayerBosses.now() + 300;
        for (int i = 0; i < 14; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 1.5 + random.nextDouble() * 3;
            BlockPos pos = BlockPos.containing(SlayerBosses.ground(run.level, spot.add(Math.cos(a) * r, 0, Math.sin(a) * r)));
            SlayerAbilities.tempBlock(run.level, pos, Blocks.COBWEB, until);
        }
        int tier = run.tierNumber;
        int sacs = tier >= 5 ? 4 : tier >= 4 ? 3 : 2;
        int hits = tier >= 5 ? 9 : tier >= 4 ? 6 : 3;
        for (int i = 0; i < sacs; i++) {
            double a = i * Math.PI * 2 / sacs;
            Vec3 at = spot.add(Math.cos(a) * 1.4, 2.2, Math.sin(a) * 1.4);
            BossMoves.totem(run, "egg", at, "minecraft:sniffer_egg", 1.0f, 0.9f, 0.9f, 240, hits);
        }
    }

    private static void fuse(SlayerBosses.Run run) {
        Mob boss = run.boss;
        State state = run.hx;
        state.fused = true;
        state.damageFactor = 2;
        state.bats = false;
        run.minions.forEach(minion -> {
            if (minion.isAlive()) {
                minion.discard();
            }
        });
        MobLevels.MobInfo info = boss.getAttached(MobLevels.INFO);
        if (info != null) {
            boss.setAttached(MobLevels.INFO, new MobLevels.MobInfo(info.level(), info.elite(), info.toughness() * 2));
        }
        boss.setHealth(boss.getMaxHealth());
        boss.setCustomName(net.minecraft.network.chat.Component.literal("Conjoined Brood").withColor(run.line.color() & 0xFFFFFF)
                .withStyle(ChatFormatting.BOLD));
        SlayerBosses.particles(run.level, ParticleTypes.SOUL, boss.position().add(0, 1, 0), 80, 1.2, 0.08);
        SlayerBosses.sound(run, SoundEvents.WITHER_SPAWN, 1.2f, 0.7f);
        SlayerBosses.tell(run, Msg.text("Till Death Do Us Part! The Primordial Broodmother fuses with it: the Conjoined Brood rises!",
                ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        applyStats(run);
        SlayerBosses.updateBar(run);
    }

    // ---------------------------------------------------------------- Sven Packmaster

    private static boolean calling(State state) {
        return state.pupsCalled && state.pupsSpawned < 5;
    }

    private static void sven(SlayerBosses.Run run, ServerPlayer owner, float health, long now, int tier) {
        Mob boss = run.boss;
        State state = run.hx;
        // Call the pups! At half health: one pup a second for five seconds, and it's protected while any live.
        if (tier >= 3 && health < 0.5f && !state.pupsCalled) {
            state.pupsCalled = true;
            SlayerBosses.tell(run, Msg.text("Sven Packmaster: Calling the pups!", ChatFormatting.WHITE, ChatFormatting.BOLD));
            SlayerBosses.sound(run, SoundEvents.WOLF_AMBIENT_BABY.value(), 2f, 0.5f);
            applyStats(run);
        }
        if (calling(state) && now % 20 == 0) {
            pup(run, owner);
            state.pupsSpawned++;
            SlayerBosses.updateBar(run);
            if (!calling(state)) {
                applyStats(run);
            }
        }
        state.pups.removeIf(pup -> !pup.isAlive());
        if (state.pupsCalled && state.pupsSpawned >= 5 && state.pups.isEmpty() && now % 20 == 0 && boss.getHealth() > 0) {
            // The pack is down: back to normal (and it can be hurt again).
            if (state.pupsSpawned == 5) {
                state.pupsSpawned++;
                SlayerBosses.tell(run, Msg.text("The pups are down: Sven Packmaster is open again!", ChatFormatting.GREEN));
                SlayerBosses.updateBar(run);
            }
        }
        if (!state.pups.isEmpty() && now % 4 == 0) {
            SlayerBosses.particles(run.level, ParticleTypes.CRIT, boss.position().add(0, 0.8, 0), 3, 0.4, 0.05);
        }
        if (now % 20 == 0) {
            for (Mob pup : state.pups) {
                if (pup instanceof Wolf wolf) {
                    wolf.setPersistentAngerTarget(EntityReference.of(owner));
                    wolf.startPersistentAngerTimer();
                    wolf.setTarget(owner);
                }
            }
        }
    }

    private static void pup(SlayerBosses.Run run, ServerPlayer owner) {
        Mob pup = EntityTypes.WOLF.create(run.level, EntitySpawnReason.MOB_SUMMONED);
        if (!(pup instanceof Wolf wolf)) {
            return;
        }
        Vec3 at = run.boss.position();
        wolf.snapTo(at.x, at.y, at.z, run.level.getRandom().nextFloat() * 360, 0);
        wolf.setBaby(true);
        // A tenth of the Packmaster's health each, and a quarter of its damage.
        double health = run.tier.health() * 0.1 / StatTypes.HEALTH_SCALE;
        set(wolf, Attributes.MAX_HEALTH, Math.min(1024, health));
        set(wolf, Attributes.ATTACK_DAMAGE, dps(run.tier) * 0.25 / StatTypes.HEALTH_SCALE);
        // The level is for show: its health and damage are set above (MobLevels.set would raise them per level).
        wolf.setAttached(MobLevels.INFO, new MobLevels.MobInfo(run.tier.level(), false, (float) Math.max(1, health / 1024)));
        wolf.setHealth(wolf.getMaxHealth());
        wolf.setCustomName(net.minecraft.network.chat.Component.literal("Sven Pup").withColor(0xFFFFFF));
        wolf.addTag(SlayerBosses.MINION_TAG);
        wolf.addTag(MobTags.FARMED);
        wolf.setPersistentAngerTarget(EntityReference.of(owner));
        wolf.startPersistentAngerTimer();
        wolf.setTarget(owner);
        run.level.addFreshEntity(wolf);
        SlayerBosses.particles(run.level, ParticleTypes.CLOUD, at.add(0, 0.5, 0), 8, 0.3, 0.02);
        SlayerBosses.sound(run, SoundEvents.WOLF_GROWL_BABY.value(), 1.2f, 1.0f);
        run.hx.pups.add(wolf);
        run.minions.add(wolf);
    }

    // ---------------------------------------------------------------- Voidgloom Seraph

    private static void raiseShield(SlayerBosses.Run run) {
        State state = run.hx;
        state.shield = HITSHIELD[Math.min(run.tierNumber, 4) - 1];
        state.shieldsUsed++;
        state.shieldSince = SlayerBosses.now();
        SlayerBosses.sound(run, SoundEvents.BEACON_ACTIVATE, 1.2f, 0.6f);
        SlayerBosses.tell(run, Msg.text("Malevolent Hitshield! (" + state.shield + " hits)", ChatFormatting.LIGHT_PURPLE));
        SlayerBosses.updateBar(run);
    }

    private static void hitShield(SlayerBosses.Run run) {
        State state = run.hx;
        state.shield--;
        Vec3 at = run.boss.position().add(0, 1.5, 0);
        SlayerBosses.particles(run.level, ParticleTypes.WITCH, at, 6, 0.4, 0.05);
        run.level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_HIT, net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.4f);
        if (state.shield <= 0) {
            state.shield = 0;
            state.shieldBonus = 0;
            SlayerBosses.sound(run, SoundEvents.GLASS_BREAK, 1.2f, 0.7f);
            SlayerBosses.particles(run.level, ParticleTypes.REVERSE_PORTAL, at, 60, 0.8, 0.12);
            SlayerBosses.tell(run, Msg.text("The Hitshield shatters!", ChatFormatting.LIGHT_PURPLE));
            applyStats(run);
        }
        if (state.shield % 5 == 0) {
            SlayerBosses.updateBar(run);
        }
    }

    private static void voidgloom(SlayerBosses.Run run, ServerPlayer owner, float health, long now, int tier) {
        Mob boss = run.boss;
        State state = run.hx;
        if (now % 5 == 0) {
            SlayerBosses.particles(run.level, ParticleTypes.CRIT, boss.position().add(0, 1.5, 0), 2, 0.4, 0.05);
        }
        // Malevolent Hitshield: on spawning, at two thirds and at a third of its health.
        int shields = health < 0.33f ? 3 : health < 0.66f ? 2 : 1;
        if (shields > state.shieldsUsed) {
            raiseShield(run);
        }
        if (state.shield > 0) {
            if (now % 4 == 0) {
                SlayerBosses.ring(run.level, ParticleTypes.WITCH, boss.position().add(0, 1.5, 0), 1.0, 10);
            }
            // From tier II it hits 3,000 harder every 3 seconds the shield is up.
            if (tier >= 2 && (now - state.shieldSince) % 60 == 59) {
                state.shieldBonus += 3000;
                applyStats(run);
            }
        }
        // Broken Heart Radiation (IV): at 5/6, 1/2 and 1/6 of its health.
        int hearts = health < 1 / 6f ? 3 : health < 0.5f ? 2 : health < 5 / 6f ? 1 : 0;
        if (tier >= 4 && hearts > state.heartsDone && state.radiationUntil <= now) {
            state.heartsDone = hearts;
            state.radiationUntil = now + 160;
            boss.setNoAi(true);
            SlayerBosses.particles(run.level, ParticleTypes.EXPLOSION_EMITTER, boss.position().add(0, 1, 0), 1, 0, 0);
            SlayerBosses.sound(run, SoundEvents.WARDEN_SONIC_CHARGE, 2f, 0.8f);
            SlayerBosses.tell(run, Msg.text("Broken Heart Radiation! Stay out of the beams!", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            applyStats(run);
            SlayerBosses.updateBar(run);
        }
        if (state.radiationUntil > now) {
            radiation(run, now);
            return;
        }
        if (state.radiationUntil != 0) {
            state.radiationUntil = 0;
            boss.setNoAi(false);
            boss.setTarget(owner);
            applyStats(run);
            SlayerBosses.updateBar(run);
        }
        // Dissonance: it teleports behind or beside you, more often while you stand still.
        if (now >= state.nextDissonance && boss instanceof Enderman seraph) {
            boolean still = now - state.ownerStillSince > 60;
            state.nextDissonance = now + (still ? 80 : 160);
            Vec3 look = owner.getLookAngle().multiply(1, 0, 1).normalize();
            Vec3 side = run.level.getRandom().nextBoolean() ? look.scale(-2) : new Vec3(-look.z, 0, look.x).scale(run.level.getRandom().nextBoolean() ? 2 : -2);
            Vec3 at = SlayerBosses.ground(run.level, owner.position().add(side));
            seraph.teleport(at.x, at.y, at.z);
        }
        // From tier II, half its damage to everyone within 6.5 blocks every second.
        if (tier >= 2 && now % 20 == 0) {
            for (ServerPlayer player : SlayerBosses.players(run, 6.5)) {
                hurt(run, player, dps(run.tier) * 0.5, false);
            }
        }
        // Yang Glyphs: from half its health at tier II, two thirds from III; never with the shield up.
        float glyphsFrom = tier >= 3 ? 0.66f : 0.5f;
        if (tier >= 2 && health < glyphsFrom && state.shield == 0) {
            if (state.glyphThrowAt == 0 && now >= state.nextGlyph && boss instanceof Enderman seraph) {
                seraph.setCarriedBlock(Blocks.BEACON.defaultBlockState());
                state.glyphThrowAt = now + 40;
            }
            if (state.glyphThrowAt != 0 && now >= state.glyphThrowAt) {
                throwGlyph(run);
                state.glyphThrowAt = 0;
                state.nextGlyph = now + (tier >= 3 ? 160 : 240);
            }
        } else if (state.glyphThrowAt != 0 && boss instanceof Enderman seraph) {
            // The shield went up while it held a glyph: it keeps it for later.
            seraph.setCarriedBlock(null);
            state.glyphThrowAt = 0;
        }
        glyphs(run);
        // Nukekubi Fixations: from a third of its health (III), heads gather around you.
        if (tier >= 3 && health < 0.33f && now >= state.nextHead && heads(run).size() < 5) {
            state.nextHead = now + 120;
            spawnHead(run, owner);
        }
        nukekubi(run, owner, now, tier);
    }

    private static void throwGlyph(SlayerBosses.Run run) {
        if (run.boss instanceof Enderman seraph) {
            seraph.setCarriedBlock(null);
        }
        RandomSource random = run.level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 3 + random.nextDouble() * 3;
        Vec3 at = SlayerBosses.ground(run.level, run.boss.position().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance));
        BossMoves.totem(run, "glyph", at, "minecraft:beacon", 0.9f, 0.8f, 0.8f, 100, 9999);
        SlayerBosses.sound(run, SoundEvents.BEACON_POWER_SELECT, 1.5f, 1.4f);
        for (ServerPlayer player : SlayerBosses.players(run, 30)) {
            player.sendOverlayMessage(Msg.text("Yang Glyph! Stand on it within 5 seconds!", ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
        }
    }

    /** A glyph breaks when someone stands on or right next to it. */
    private static void glyphs(SlayerBosses.Run run) {
        for (BossMoves.Totem totem : new ArrayList<>(BossMoves.TOTEMS)) {
            if (totem.run != run || !totem.kind.equals("glyph") || totem.hitbox == null) {
                continue;
            }
            Vec3 at = totem.hitbox.position();
            if (SlayerBosses.now() % 4 == 0) {
                for (int i = 0; i < 5; i++) {
                    run.level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1 + i * 1.5, at.z, 1, 0.05, 0.3, 0.05, 0);
                }
            }
            for (ServerPlayer player : SlayerBosses.players(run, 30)) {
                if (Math.hypot(player.getX() - at.x, player.getZ() - at.z) < 1.6 && Math.abs(player.getY() - at.y) < 2) {
                    BossMoves.breakTotem(totem, player, "broke a Yang Glyph!");
                    break;
                }
            }
        }
    }

    private static List<BossMoves.Totem> heads(SlayerBosses.Run run) {
        List<BossMoves.Totem> heads = new ArrayList<>();
        for (BossMoves.Totem totem : BossMoves.TOTEMS) {
            if (totem.run == run && totem.kind.equals("nukekubi")) {
                heads.add(totem);
            }
        }
        return heads;
    }

    private static double headsDps(SlayerBosses.Run run) {
        if (!run.line.kind().equals("voidgloom") || run.tierNumber < 3) {
            return 0;
        }
        int n = heads(run).size();
        return n == 0 ? 0 : n * (run.tierNumber >= 4 ? 2000 : 800) * Math.pow(2, n - 1);
    }

    private static void spawnHead(SlayerBosses.Run run, ServerPlayer owner) {
        RandomSource random = run.level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 4 + random.nextDouble() * 2;
        Vec3 at = owner.position().add(Math.cos(angle) * distance, 1 + random.nextDouble(), Math.sin(angle) * distance);
        BossMoves.totem(run, "nukekubi", at, "minecraft:wither_skeleton_skull", 0.8f, 0.6f, 0.6f, 0, 9999);
        line(run.level, ParticleTypes.REVERSE_PORTAL, run.boss.position().add(0, 2, 0), at, 12);
        SlayerBosses.sound(run, SoundEvents.PHANTOM_AMBIENT, 1.2f, 0.6f);
        owner.sendOverlayMessage(Msg.text("Nukekubi Fixation! Look straight at the heads to destroy them.", ChatFormatting.DARK_PURPLE));
    }

    /** The heads: half a second of looking straight at one destroys it; together they hurt more and more. */
    private static void nukekubi(SlayerBosses.Run run, ServerPlayer owner, long now, int tier) {
        List<BossMoves.Totem> heads = heads(run);
        State state = run.hx;
        state.looked.keySet().retainAll(heads);
        for (BossMoves.Totem head : heads) {
            if (head.hitbox == null) {
                continue;
            }
            Vec3 center = head.hitbox.position().add(0, 0.3, 0);
            boolean looking = false;
            for (ServerPlayer player : SlayerBosses.players(run, 30)) {
                Vec3 to = center.subtract(player.getEyePosition()).normalize();
                if (player.getLookAngle().dot(to) > 0.985) {
                    looking = true;
                    break;
                }
            }
            int ticks = looking ? state.looked.getOrDefault(head, 0) + 1 : 0;
            state.looked.put(head, ticks);
            if (looking && now % 3 == 0) {
                run.level.sendParticles(ParticleTypes.SMOKE, center.x, center.y, center.z, 2, 0.15, 0.15, 0.15, 0.01);
            }
            if (ticks >= 10) {
                state.looked.remove(head);
                run.level.playSound(null, center.x, center.y, center.z, SoundEvents.CREEPER_PRIMED, net.minecraft.sounds.SoundSource.HOSTILE, 1.2f, 0.6f);
                BossMoves.breakTotem(head, owner, null);
            }
        }
        double dps = headsDps(run);
        if (dps > 0 && now % 20 == 0) {
            for (BossMoves.Totem head : heads(run)) {
                if (head.hitbox != null) {
                    line(run.level, BLUE, head.hitbox.position().add(0, 0.3, 0), owner.position().add(0, 1.2, 0), 10);
                }
            }
            hurt(run, owner, dps, false);
        }
    }

    /** Broken Heart Radiation: four beams sweep round the seated Seraph. */
    private static void radiation(SlayerBosses.Run run, long now) {
        State state = run.hx;
        Vec3 center = run.boss.position().add(0, 1, 0);
        state.beamAngle += Math.PI * 2 / 160;
        double length = 12;
        for (int beam = 0; beam < 4; beam++) {
            double angle = state.beamAngle + beam * Math.PI / 2;
            Vec3 dir = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            if (now % 2 == 0) {
                for (int i = 1; i <= 12; i++) {
                    Vec3 p = center.add(dir.scale(i * length / 12));
                    run.level.sendParticles(PINK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
                }
            }
            for (ServerPlayer player : SlayerBosses.players(run, length + 1)) {
                Vec3 rel = player.position().add(0, 1, 0).subtract(center);
                double along = rel.x * dir.x + rel.z * dir.z;
                double across = Math.abs(rel.x * dir.z - rel.z * dir.x);
                if (along < 0 || along > length || across > 0.8 || Math.abs(rel.y) > 2) {
                    continue;
                }
                long ready = state.beamCooldown.getOrDefault(player.getUUID(), 0L);
                if (now < ready) {
                    continue;
                }
                state.beamCooldown.put(player.getUUID(), now + 20);
                hurt(run, player, maxHealth(player) * 0.25, true);
                SlayerCurses.heartbreak(player);
                player.sendOverlayMessage(Msg.text("A beam broke your heart! (-12% healing)", ChatFormatting.LIGHT_PURPLE));
            }
        }
    }

    // ---------------------------------------------------------------- what goes off, and what's shown

    /** A Hypixel totem's timer ran out. */
    static void goOff(BossMoves.Totem totem) {
        SlayerBosses.Run run = totem.run;
        if (totem.kind.equals("glyph")) {
            Vec3 at = totem.hitbox == null ? run.boss.position() : totem.hitbox.position();
            run.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
            run.level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, net.minecraft.sounds.SoundSource.HOSTILE, 1.5f, 0.6f);
            SlayerBosses.tell(run, Msg.text("A Yang Glyph went off!", ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
            for (ServerPlayer player : SlayerBosses.players(run, 30)) {
                hurt(run, player, maxHealth(player) * 1000, true);
            }
        }
    }

    /** The boss bar's extras: the tier's state. */
    static void barExtras(SlayerBosses.Run run, MutableComponent name, long now) {
        State state = run.hx;
        if (state.shield > 0) {
            name.append(Msg.text("  Hitshield: " + state.shield, ChatFormatting.LIGHT_PURPLE));
        }
        if (state.madAt > 0 && now >= state.madAt) {
            name.append(Msg.text("  MAD", ChatFormatting.RED));
        }
        if (state.enragedUntil > now) {
            name.append(Msg.text("  ENRAGED", ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
        if (state.boomAt > now) {
            name.append(Msg.text("  Boom! " + (state.boomAt - now + 19) / 20, ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
        if (state.bats) {
            name.append(Msg.text("  Bat Barrage", ChatFormatting.DARK_GRAY));
        }
        if (calling(state)) {
            name.append(Msg.text("  Calling the pups!", ChatFormatting.WHITE));
        } else if (state.pupsCalled && !state.pups.isEmpty()) {
            name.append(Msg.text("  Protected", ChatFormatting.WHITE));
        }
        if (state.radiationUntil > now) {
            name.append(Msg.text("  Broken Heart Radiation", ChatFormatting.LIGHT_PURPLE));
        }
    }

    // ---------------------------------------------------------------- the abilities, for the menus

    /** What each tier adds, for the Slayers page and Maddox's screen; the title is Hypixel's difficulty. */
    static SlayerAbilities.TierLore lore(String kind, int tier) {
        if (tier < 1 || tier > 5) {
            return null;
        }
        List<SlayerAbilities.Ability> adds = new ArrayList<>();
        switch (kind) {
            case "revenant" -> {
                switch (tier) {
                    case 1 -> {
                        adds.add(new SlayerAbilities.Ability(true, "Life Drain", "Every few seconds it drains your health and heals itself"));
                        adds.add(new SlayerAbilities.Ability(false, "Undead", "Arrows heal it instead of hurting it"));
                    }
                    case 2 -> adds.add(new SlayerAbilities.Ability(true, "Pestilence",
                            "Hurts everyone within 5 blocks every second and shreds 25% of their Defense"));
                    case 3 -> adds.add(new SlayerAbilities.Ability(true, "Enrage",
                            "After 40 seconds it gets mad, then enraged for 12: faster, and its hits are three times as hard"));
                    case 5 -> {
                        adds.add(new SlayerAbilities.Ability(false, "Redemption",
                                "The Atoned Horror heals a thousandth of its health a second, and 5% whenever its TNT hits you"));
                        adds.add(new SlayerAbilities.Ability(true, "Explosive Assault",
                                "Throws TNT at a circle under you every second; twice as often below a third of its health"));
                        adds.add(new SlayerAbilities.Ability(true, "Thermonuclear",
                                "Every 30 seconds it charges for 5, then blasts everything within 7 blocks"));
                    }
                    default -> {
                    }
                }
            }
            case "tarantula" -> {
                switch (tier) {
                    case 1 -> adds.add(new SlayerAbilities.Ability(true, "Backstab",
                            "Leaps behind you; its hits from behind deal +50% damage (+75%, +100%, +125%, +150% at higher tiers)"));
                    case 2 -> adds.add(new SlayerAbilities.Ability(true, "Noxious Paralysis",
                            "While it lives you heal 25% less (50%, 75%, 99% higher up), and every fifth hit slows you a little more"));
                    case 3 -> adds.add(new SlayerAbilities.Ability(false, "Web of Lies",
                            "At 66% and 33% health it webs the ground and lays 2 egg sacs (3, then 4) to smash before they hatch"));
                    case 4 -> adds.add(new SlayerAbilities.Ability(false, "Bat Barrage",
                            "From 66% health magic can't hurt it, and every bat in its horde takes 1% of your health a second (2% at V)"));
                    case 5 -> adds.add(new SlayerAbilities.Ability(false, "Till Death Do Us Part",
                            "When it dies it fuses into the Conjoined Brood: double health and damage, only its passive abilities"));
                    default -> {
                    }
                }
            }
            case "sven" -> {
                switch (tier) {
                    case 1 -> adds.add(new SlayerAbilities.Ability(false, "Agile", "Small and fast, so it's hard to hit"));
                    case 2 -> adds.add(new SlayerAbilities.Ability(true, "True Damage",
                            "Its hits also deal 10 True Damage that ignores Defense (50 at III, 200 at IV)"));
                    case 3 -> adds.add(new SlayerAbilities.Ability(false, "Call the pups!",
                            "At half health it slows down and calls 5 pups with a tenth of its health; it can't be hurt while they live"));
                    default -> {
                    }
                }
            }
            case "voidgloom" -> {
                switch (tier) {
                    case 1 -> {
                        adds.add(new SlayerAbilities.Ability(true, "Dissonance",
                                "Teleports behind or beside you, more often if you stand still; from II it also hurts everyone within 6.5 blocks"));
                        adds.add(new SlayerAbilities.Ability(false, "Malevolent Hitshield",
                                "On spawning and at 2/3 and 1/3 health: a shield of 15 hits (30, 60, 100 higher up), whatever each hit deals"));
                    }
                    case 2 -> adds.add(new SlayerAbilities.Ability(true, "Yang Glyphs",
                            "From half health it throws beacons: stand on one within 5 seconds or it kills everyone fighting"));
                    case 3 -> adds.add(new SlayerAbilities.Ability(true, "Nukekubi Fixations",
                            "From a third of its health, heads gather around you: look straight at one to break it; together they hurt more"));
                    case 4 -> adds.add(new SlayerAbilities.Ability(false, "Broken Heart Radiation",
                            "At 5/6, 1/2 and 1/6 health it can't be hurt for 8 seconds and sweeps beams (25% of your health, less healing)"));
                    default -> {
                    }
                }
            }
            default -> {
                return null;
            }
        }
        return new SlayerAbilities.TierLore(DIFFICULTY[tier - 1], adds);
    }

    private static void set(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
