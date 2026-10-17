package com.skyrpg.combat.slayer;

import com.skyrpg.combat.MobLevels;
import com.skyrpg.core.stats.TrueDamage;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.world.MobTags;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SkyItems;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * The Inferno Demonlord (Hypixel's Blaze Slayer), from its wiki page and NEU's boss lore:
 * <ul>
 *   <li>Immolate: true damage every second to everyone within 8 blocks, 100 + a share of their max health that grows
 *   at 66% and 33% of the boss's health (I 5/8/12%, II-III 5/10/15%, IV 15/25/35%).</li>
 *   <li>Demonsplit: at half health (I-II), or at 66% and 33% (III-IV), it hides and two demons rise, Quazii and
 *   Typhoeus (500k to 10M health, 2,500 to 30,000 damage a second); it comes back when both are dead.</li>
 *   <li>Hellion Shield (II+): attuned Ashen, Spirit, Auric or Crystal; only hits from a dagger attuned the same way do
 *   full damage (anything else 99% less). Eight attuned hits switch the shield.</li>
 *   <li>Fire Pillars (II+, below 66%): every 20 seconds a pillar rises near you; hit it 8 times within 7 seconds or it
 *   blows up for your whole health.</li>
 *   <li>Apocalypse (III+, below 33%): fire pits open around the boss; standing in one burns 950 (III) or 2,865 (IV)
 *   true damage a second.</li>
 * </ul>
 */
final class Demonlord {
    static final String PILLAR_TAG = "skyrpg_slayer_pillar";
    private static final String FIGHTABLE = "skyrpg_fightable";
    static final List<String> ATTUNEMENTS = List.of("Ashen", "Spirit", "Auric", "Crystal");
    private static final int[] ATTUNE_COLORS = {0x555555, 0xFFFFFF, 0xFFD24A, 0x55FFFF};
    private static final double[][] IMMOLATE = {{5, 8, 12}, {5, 10, 15}, {5, 10, 15}, {15, 25, 35}};
    private static final double[] DEMON_HEALTH = {500_000, 1_750_000, 5_000_000, 10_000_000};
    private static final double[] DEMON_DPS = {2_500, 8_000, 15_000, 30_000};
    private static final double[] APOCALYPSE = {0, 0, 950, 2_865};

    /** One Demonlord fight's state. */
    static final class Fight {
        int splits;
        final List<Mob> demons = new ArrayList<>();
        int attune = -1;
        int attuned;
        long nextPillar;
        Mob pillar;
        int pillarHits;
        long pillarBoom;
        final List<Vec3> pits = new ArrayList<>();
        long pitsBurn;
        long pitsEnd;
        long nextPits;

        boolean hiding() {
            return !demons.isEmpty();
        }
    }

    private Demonlord() {
    }

    static void init() {
        // A fire pillar takes hits, not damage: eight hits break it.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!entity.entityTags().contains(PILLAR_TAG)) {
                return true;
            }
            if (source.getEntity() instanceof ServerPlayer) {
                for (SlayerBosses.Run run : SlayerBosses.runs()) {
                    Fight fight = run.hx.demon;
                    if (fight.pillar == entity) {
                        fight.pillarHits++;
                        SlayerBosses.particles(run.level, ParticleTypes.LAVA, entity.position().add(0, 1, 0), 3, 0.3, 0);
                        run.level.playSound(null, entity.blockPosition(), SoundEvents.BLAZE_HURT, net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 0.6f);
                        if (fight.pillarHits >= 8) {
                            breakPillar(run, true);
                        }
                    }
                }
            }
            return false;
        });
    }

    static void started(SlayerBosses.Run run) {
        Fight fight = run.hx.demon;
        long now = SlayerBosses.now();
        fight.nextPillar = now + 200;
        if (run.tierNumber >= 2) {
            switchShield(run);
        }
    }

    static void takeDown(SlayerBosses.Run run) {
        Fight fight = run.hx.demon;
        fight.demons.forEach(demon -> {
            if (demon.isAlive()) {
                demon.discard();
            }
        });
        fight.demons.clear();
        if (fight.pillar != null) {
            fight.pillar.discard();
            fight.pillar = null;
        }
        fight.pits.clear();
        if (run.boss != null) {
            run.boss.setInvisible(false);
            run.boss.setNoAi(false);
        }
    }

    // ---------------------------------------------------------------- every tick

    static void tick(SlayerBosses.Run run, ServerPlayer owner, float health, long now, int tier) {
        Mob boss = run.boss;
        Fight fight = run.hx.demon;
        int phase = health < 0.33f ? 2 : health < 0.66f ? 1 : 0;
        if (now % 6 == 0 && !fight.hiding()) {
            SlayerBosses.particles(run.level, ParticleTypes.FLAME, boss.position().add(0, 1.2, 0), 3, 0.5, 0.02);
            if (fight.attune >= 0) {
                SlayerBosses.ring(run.level, new DustParticleOptions(ATTUNE_COLORS[fight.attune], 1.3f), boss.position().add(0, 1.6, 0), 1.1, 10);
            }
        }

        // Immolate, every second.
        if (now % 20 == 0) {
            double share = IMMOLATE[Math.min(tier, 4) - 1][phase] / 100.0;
            Vec3 at = fight.hiding() && !fight.demons.isEmpty() ? fight.demons.get(0).position() : boss.position();
            for (ServerPlayer player : run.level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(40),
                    p -> !p.isSpectator() && !p.isCreative() && p.position().distanceTo(at) <= 8)) {
                TrueDamage.hurt(player, source(run), 100 + share * player.getMaxHealth() * StatTypes.HEALTH_SCALE);
            }
        }

        // Demonsplit: at half (I-II), or 66% and 33% (III-IV).
        int splitsDue = tier >= 3 ? (health < 0.33f ? 2 : health < 0.66f ? 1 : 0) : (health < 0.5f ? 1 : 0);
        if (splitsDue > fight.splits && !fight.hiding()) {
            fight.splits = splitsDue;
            split(run, owner, tier);
        }
        fight.demons.removeIf(demon -> !demon.isAlive());
        if (fight.splits > 0 && !fight.hiding() && boss.isInvisible()) {
            boss.setInvisible(false);
            boss.setNoAi(false);
            boss.snapTo(owner.getX(), owner.getY(), owner.getZ() + 2, boss.getYRot(), 0);
            SlayerBosses.particles(run.level, ParticleTypes.FLAME, boss.position().add(0, 1, 0), 40, 0.6, 0.05);
            SlayerBosses.tell(run, Msg.text("The demons are slain: the Inferno Demonlord returns!", ChatFormatting.GOLD));
            SlayerBosses.updateBar(run);
        }
        if (fight.hiding()) {
            if (now % 20 == 0) {
                fight.demons.forEach(demon -> demon.setTarget(owner));
            }
            return;
        }

        // Fire Pillars (II+, below 66%).
        if (tier >= 2 && phase >= 1 && fight.pillar == null && now >= fight.nextPillar) {
            pillar(run, owner);
        }
        if (fight.pillar != null) {
            if (now % 4 == 0) {
                SlayerBosses.particles(run.level, ParticleTypes.FLAME, fight.pillar.position().add(0, 1.5, 0), 8, 0.3, 0.2);
            }
            long left = fight.pillarBoom - now;
            if (left % 20 == 0 && left > 0) {
                owner.sendOverlayMessage(Msg.text("Fire Pillar: " + left / 20 + "s, " + (8 - fight.pillarHits) + " hits!", ChatFormatting.RED, ChatFormatting.BOLD));
            }
            if (now >= fight.pillarBoom) {
                Vec3 at = fight.pillar.position();
                SlayerBosses.particles(run.level, ParticleTypes.EXPLOSION_EMITTER, at.add(0, 1, 0), 1, 0, 0);
                SlayerBosses.sound(run, SoundEvents.GENERIC_EXPLODE.value(), 2f, 0.8f);
                for (ServerPlayer player : SlayerBosses.players(run, 40)) {
                    if (player.position().distanceTo(at) <= 6) {
                        TrueDamage.hurt(player, source(run), player.getMaxHealth() * StatTypes.HEALTH_SCALE * 1.2);
                    }
                }
                breakPillar(run, false);
            }
        }

        // Apocalypse (III+, below 33%): fire pits.
        if (tier >= 3 && phase >= 2) {
            if (now >= fight.nextPits) {
                fight.pits.clear();
                for (int i = 0; i < 5; i++) {
                    double angle = run.level.getRandom().nextDouble() * Math.PI * 2;
                    double r = 1.5 + run.level.getRandom().nextDouble() * 5;
                    fight.pits.add(SlayerBosses.ground(run.level, boss.position().add(Math.cos(angle) * r, 0, Math.sin(angle) * r)));
                }
                // One under you, too.
                fight.pits.add(SlayerBosses.ground(run.level, owner.position()));
                fight.pitsBurn = now + 30;
                fight.pitsEnd = now + 90;
                fight.nextPits = now + 140;
            }
            for (Vec3 pit : fight.pits) {
                if (now % 4 == 0) {
                    SlayerBosses.ring(run.level, now < fight.pitsBurn ? ParticleTypes.SMOKE : ParticleTypes.FLAME, pit.add(0, 0.1, 0), 1.5, 12);
                }
            }
            if (now >= fight.pitsBurn && now < fight.pitsEnd && now % 10 == 0) {
                for (ServerPlayer player : SlayerBosses.players(run, 40)) {
                    for (Vec3 pit : fight.pits) {
                        if (player.position().distanceTo(pit) < 1.6) {
                            TrueDamage.hurt(player, source(run), APOCALYPSE[Math.min(tier, 4) - 1] / 2);
                            break;
                        }
                    }
                }
            }
            if (now >= fight.pitsEnd) {
                fight.pits.clear();
            }
        }
    }

    private static void split(SlayerBosses.Run run, ServerPlayer owner, int tier) {
        Mob boss = run.boss;
        Fight fight = run.hx.demon;
        SlayerBosses.particles(run.level, ParticleTypes.EXPLOSION, boss.position().add(0, 1, 0), 2, 0.3, 0);
        SlayerBosses.sound(run, SoundEvents.BLAZE_DEATH, 2f, 0.5f);
        SlayerBosses.tell(run, Msg.text("Demonsplit! Slay Quazii and Typhoeus!", ChatFormatting.GOLD, ChatFormatting.BOLD));
        demon(run, owner, EntityTypes.WITHER_SKELETON, "Quazii", 0xAA00AA, boss.position().add(1.5, 0, 0));
        demon(run, owner, EntityTypes.PIGLIN_BRUTE, "Typhoeus", 0xFF5555, boss.position().add(-1.5, 0, 0));
        if (fight.pillar != null) {
            breakPillar(run, false);
        }
        boss.setInvisible(true);
        boss.setNoAi(true);
        boss.setTarget(null);
        SlayerBosses.updateBar(run);
    }

    private static void demon(SlayerBosses.Run run, ServerPlayer owner, EntityType<? extends Mob> type, String name, int color, Vec3 at) {
        Mob demon = type.create(run.level, EntitySpawnReason.MOB_SUMMONED);
        if (demon == null) {
            return;
        }
        int tier = Math.min(run.tierNumber, 4) - 1;
        demon.snapTo(at.x, at.y, at.z, run.level.getRandom().nextFloat() * 360, 0);
        double health = DEMON_HEALTH[tier] / StatTypes.HEALTH_SCALE;
        set(demon, Attributes.MAX_HEALTH, Math.min(1024, health));
        set(demon, Attributes.ATTACK_DAMAGE, DEMON_DPS[tier] / StatTypes.HEALTH_SCALE);
        demon.setAttached(MobLevels.INFO, new MobLevels.MobInfo(run.tier.level(), false, (float) Math.max(1, health / 1024)));
        demon.setHealth(demon.getMaxHealth());
        demon.setCustomName(Component.literal(name).withColor(color));
        demon.setCustomNameVisible(true);
        demon.addTag(SlayerBosses.MINION_TAG);
        demon.addTag(MobTags.FARMED);
        demon.addTag(MobTags.NO_SUNBURN);
        demon.addTag(FIGHTABLE);
        if (demon instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin piglin) {
            piglin.setImmuneToZombification(true);
        }
        demon.setTarget(owner);
        demon.setPersistenceRequired();
        run.level.addFreshEntity(demon);
        SlayerBosses.particles(run.level, ParticleTypes.FLAME, at.add(0, 1, 0), 20, 0.4, 0.05);
        run.hx.demon.demons.add(demon);
        run.minions.add(demon);
    }

    private static void pillar(SlayerBosses.Run run, ServerPlayer owner) {
        Fight fight = run.hx.demon;
        Mob pillar = EntityTypes.BLAZE.create(run.level, EntitySpawnReason.MOB_SUMMONED);
        if (pillar == null) {
            return;
        }
        double angle = run.level.getRandom().nextDouble() * Math.PI * 2;
        Vec3 at = SlayerBosses.ground(run.level, owner.position().add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4));
        pillar.snapTo(at.x, at.y, at.z, 0, 0);
        pillar.setNoAi(true);
        pillar.setNoGravity(true);
        pillar.setSilent(true);
        pillar.setPersistenceRequired();
        pillar.setCustomName(Msg.text("Fire Pillar", ChatFormatting.RED, ChatFormatting.BOLD));
        pillar.setCustomNameVisible(true);
        pillar.addTag(PILLAR_TAG);
        pillar.addTag(SlayerBosses.MINION_TAG);
        pillar.addTag(FIGHTABLE);
        run.level.addFreshEntity(pillar);
        fight.pillar = pillar;
        fight.pillarHits = 0;
        fight.pillarBoom = SlayerBosses.now() + 140;
        SlayerBosses.sound(run, SoundEvents.FIRECHARGE_USE, 1.5f, 0.6f);
        SlayerBosses.tell(run, Msg.text("A Fire Pillar rises! Hit it 8 times in 7 seconds!", ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private static void breakPillar(SlayerBosses.Run run, boolean broken) {
        Fight fight = run.hx.demon;
        if (fight.pillar == null) {
            return;
        }
        if (broken) {
            SlayerBosses.particles(run.level, ParticleTypes.CLOUD, fight.pillar.position().add(0, 1, 0), 15, 0.4, 0.05);
            SlayerBosses.sound(run, SoundEvents.FIRE_EXTINGUISH, 1.2f, 1.0f);
        }
        fight.pillar.discard();
        fight.pillar = null;
        fight.nextPillar = SlayerBosses.now() + 400;
    }

    // ---------------------------------------------------------------- the Hellion Shield

    private static void switchShield(SlayerBosses.Run run) {
        Fight fight = run.hx.demon;
        int next;
        do {
            next = run.level.getRandom().nextInt(ATTUNEMENTS.size());
        } while (next == fight.attune);
        fight.attune = next;
        fight.attuned = 0;
        SlayerBosses.sound(run, SoundEvents.BEACON_POWER_SELECT, 1.2f, 0.7f);
        SlayerBosses.tell(run, Component.literal("Hellion Shield: " + ATTUNEMENTS.get(next).toUpperCase(java.util.Locale.ROOT))
                .withColor(ATTUNE_COLORS[next]).withStyle(ChatFormatting.BOLD));
        SlayerBosses.updateBar(run);
    }

    /** Hits on the boss: nothing while the demons are out; through the Hellion Shield only with the matching dagger. */
    static double hitMultiplier(SlayerBosses.Run run, DamageSource source) {
        Fight fight = run.hx.demon;
        if (fight.hiding()) {
            return 0;
        }
        if (fight.attune < 0) {
            return 1;
        }
        ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p
                : source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer p ? p : null;
        if (player == null) {
            return 0.01;
        }
        ItemDefinition def = SkyItems.definition(player.getMainHandItem());
        String mode = def == null ? "" : ItemMods.attunement(player.getMainHandItem(), def);
        if (!mode.equalsIgnoreCase(ATTUNEMENTS.get(fight.attune)) || source.getDirectEntity() != player) {
            return 0.01;
        }
        if (++fight.attuned >= 8) {
            switchShield(run);
        }
        return 1;
    }

    static void barExtras(SlayerBosses.Run run, MutableComponent name) {
        Fight fight = run.hx.demon;
        if (fight.hiding()) {
            name.append(Msg.text("  Demonsplit", ChatFormatting.GOLD));
        } else if (fight.attune >= 0) {
            name.append(Component.literal("  " + ATTUNEMENTS.get(fight.attune).toUpperCase(java.util.Locale.ROOT)).withColor(ATTUNE_COLORS[fight.attune]));
        }
    }

    private static DamageSource source(SlayerBosses.Run run) {
        return new DamageSource(run.level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.INDIRECT_MAGIC),
                null, run.boss);
    }

    private static void set(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }
}
