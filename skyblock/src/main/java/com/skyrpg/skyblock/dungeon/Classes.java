package com.skyrpg.skyblock.dungeon;

import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The five dungeon classes, in a run. Passives (while in the Catacombs): Healer's healing aura (allies within 8 blocks
 * heal 1% of their health a second), Mage +Intelligence, Berserk +Strength, Archer +Crit Damage, Tank +Health and
 * Defense; bigger with the class level. The drop key ({@code key.drop}, Q) uses the class ability; sneak + drop the
 * ultimate:
 *
 * <pre>
 * Healer   Healing Circle (heal allies in 8 blocks 25%, 10 s)      Wish (heal everyone fully + 20% absorption, 120 s)
 * Mage     Guided Sheep (a blast 10 blocks ahead, 30 s)            Thunderstorm (lightning on mobs in 10 blocks, 10 s; 100 s)
 * Berserk  Throwing Axe (the first mob in 15 blocks, 10 s)         Ragnarok (+100 Strength, +100 Speed for 15 s; 60 s)
 * Archer   Explosive Shot (three blasts where you aim, 40 s)       Rapid Fire (5 shots a second at your target for 4 s; 100 s)
 * Tank     Seismic Wave (mobs in 7 blocks, scales with Defense, 15 s)  Castle of Stone (-70% damage for 20 s; 150 s)
 * </pre>
 *
 * Our numbers (Hypixel's class pages weren't retrievable; see {@code docs/design/catacombs.md}).
 */
public final class Classes {
    private static final Map<UUID, Long> ABILITY_READY = new HashMap<>();
    private static final Map<UUID, Long> ULTIMATE_READY = new HashMap<>();
    /** Running ultimates: who, which, until when. */
    private record Buff(String kind, long until) {
    }

    private static final Map<UUID, Buff> BUFFS = new HashMap<>();

    private Classes() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Classes::tick);
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Dungeon Class";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                Run run = Catacombs.runOf(player);
                if (run == null || !player.level().dimension().equals(Catacombs.LEVEL)) {
                    return;
                }
                String picked = run.classes.getOrDefault(player.getUUID(), "berserk");
                int level = DungeonXp.level(player, picked);
                switch (picked) {
                    case "mage" -> out.add(StatTypes.INTELLIGENCE, 100 + 3 * level);
                    case "berserk" -> out.add(StatTypes.STRENGTH, 20 + level);
                    case "archer" -> out.add(StatTypes.CRIT_DAMAGE, 10 + level);
                    case "tank" -> {
                        out.add(StatTypes.HEALTH, 100 + 2 * level);
                        out.add(StatTypes.DEFENSE, 50 + level);
                    }
                    default -> {
                    }
                }
                Buff buff = BUFFS.get(player.getUUID());
                if (buff != null && buff.kind().equals("ragnarok")) {
                    out.add(StatTypes.STRENGTH, 100);
                    out.add(StatTypes.SPEED, 100);
                }
            }
        });
        // Castle of Stone: 70% less damage taken.
        IncomingDamage.registerHit((target, source) -> {
            Buff buff = target instanceof ServerPlayer player ? BUFFS.get(player.getUUID()) : null;
            return buff != null && buff.kind().equals("castle") ? 0.3 : 1;
        });
    }

    /** The drop key in a run: the class ability (or with sneak, the ultimate). True when it was used for that. */
    public static boolean onDrop(ServerPlayer player) {
        Run run = Catacombs.runOf(player);
        if (run == null || run.ghosts.contains(player.getUUID()) || run.phase == Run.Phase.BUILDING || run.phase == Run.Phase.DONE) {
            return false;
        }
        String picked = run.classes.getOrDefault(player.getUUID(), "berserk");
        long now = player.level().getGameTime();
        boolean ultimate = player.isShiftKeyDown();
        Map<UUID, Long> ready = ultimate ? ULTIMATE_READY : ABILITY_READY;
        long at = ready.getOrDefault(player.getUUID(), 0L);
        String name = ultimate ? ultimateName(picked) : abilityName(picked);
        if (now < at) {
            player.sendOverlayMessage(Component.literal(name + " is on cooldown for " + ((at - now) / 20 + 1) + "s.").withStyle(ChatFormatting.RED));
            return true;
        }
        int seconds = ultimate ? ultimate(player, run, picked) : ability(player, run, picked);
        if (picked.equals("mage")) {
            seconds = (int) Math.round(seconds * 0.75);
        }
        ready.put(player.getUUID(), now + seconds * 20L);
        player.sendOverlayMessage(Component.literal("Used " + name + "!").withStyle(ultimate ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        return true;
    }

    static String abilityName(String c) {
        return switch (c) {
            case "healer" -> "Healing Circle";
            case "mage" -> "Guided Sheep";
            case "archer" -> "Explosive Shot";
            case "tank" -> "Seismic Wave";
            default -> "Throwing Axe";
        };
    }

    static String ultimateName(String c) {
        return switch (c) {
            case "healer" -> "Wish";
            case "mage" -> "Thunderstorm";
            case "archer" -> "Rapid Fire";
            case "tank" -> "Castle of Stone";
            default -> "Ragnarok";
        };
    }

    // ---------------------------------------------------------------- abilities (return the cooldown in seconds)

    private static int ability(ServerPlayer player, Run run, String picked) {
        ServerLevel level = player.level();
        switch (picked) {
            case "healer" -> {
                for (ServerPlayer ally : allies(run, player, 8)) {
                    heal(ally, 0.25);
                }
                ring(level, player.position(), 8, ParticleTypes.HEART);
                Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.4f);
                return 10;
            }
            case "mage" -> {
                Vec3 at = aimed(player, 10);
                blast(player, at, 5, 20_000 * (1 + Math.max(0, Stats.get(player, StatTypes.INTELLIGENCE)) / 100));
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                Msg.playSound(player, SoundEvents.SHEEP_AMBIENT, 1.0f, 1.0f);
                return 30;
            }
            case "archer" -> {
                Vec3 at = aimed(player, 30);
                double damage = 15_000 * (1 + Math.max(0, Stats.get(player, StatTypes.STRENGTH)) / 100);
                for (int i = 0; i < 3; i++) {
                    Vec3 spot = at.add(player.getRandom().nextGaussian() * 1.5, 0, player.getRandom().nextGaussian() * 1.5);
                    blast(player, spot, 4, damage);
                    level.sendParticles(ParticleTypes.EXPLOSION, spot.x, spot.y, spot.z, 2, 0.3, 0.3, 0.3, 0);
                }
                Msg.playSound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.2f);
                return 40;
            }
            case "tank" -> {
                double damage = 20_000 * (1 + Math.max(0, Stats.get(player, StatTypes.DEFENSE)) / 500);
                blast(player, player.position(), 7, damage);
                ring(level, player.position(), 7, ParticleTypes.CLOUD);
                Msg.playSound(player, SoundEvents.ANVIL_LAND, 0.8f, 0.6f);
                return 15;
            }
            default -> {
                LivingEntity target = firstInLine(player, 15);
                if (target != null) {
                    hurt(player, target, 30_000 * (1 + Math.max(0, Stats.get(player, StatTypes.STRENGTH)) / 100));
                    level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1, target.getZ(), 20, 0.3, 0.5, 0.3, 0.2);
                }
                Msg.playSound(player, SoundEvents.TRIDENT_THROW.value(), 1.0f, 1.0f);
                return 10;
            }
        }
    }

    private static int ultimate(ServerPlayer player, Run run, String picked) {
        long now = player.level().getGameTime();
        switch (picked) {
            case "healer" -> {
                for (ServerPlayer ally : run.online()) {
                    if (!run.ghosts.contains(ally.getUUID())) {
                        ally.setHealth(ally.getMaxHealth());
                        ally.setAbsorptionAmount(ally.getMaxHealth() * 0.2f);
                        ally.sendSystemMessage(Component.literal(player.getScoreboardName() + "'s Wish healed you!").withStyle(ChatFormatting.GREEN));
                    }
                }
                Msg.playSound(player, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.4f);
                return 120;
            }
            case "mage" -> {
                BUFFS.put(player.getUUID(), new Buff("thunder", now + 200));
                return 100;
            }
            case "archer" -> {
                BUFFS.put(player.getUUID(), new Buff("rapid", now + 80));
                return 100;
            }
            case "tank" -> {
                BUFFS.put(player.getUUID(), new Buff("castle", now + 400));
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 400, 0, false, false, true));
                Msg.playSound(player, SoundEvents.IRON_GOLEM_REPAIR, 1.0f, 0.8f);
                return 150;
            }
            default -> {
                BUFFS.put(player.getUUID(), new Buff("ragnarok", now + 300));
                Stats.refresh(player);
                Msg.playSound(player, SoundEvents.RAVAGER_ROAR, 0.8f, 1.2f);
                return 60;
            }
        }
    }

    /** Every tick: Healer auras (each second), running ultimates. */
    private static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        BUFFS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                return true;
            }
            Buff buff = entry.getValue();
            long now = player.level().getGameTime();
            if (now > buff.until()) {
                Stats.refresh(player);
                return true;
            }
            switch (buff.kind()) {
                case "thunder" -> {
                    if (tick % 20 == 0) {
                        int struck = 0;
                        for (LivingEntity mob : mobsNear(player, player.position(), 10)) {
                            if (struck++ >= 3) {
                                break;
                            }
                            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
                            if (bolt != null) {
                                bolt.snapTo(mob.getX(), mob.getY(), mob.getZ());
                                bolt.setVisualOnly(true);
                                player.level().addFreshEntity(bolt);
                            }
                            hurt(player, mob, 10_000 * (1 + Math.max(0, Stats.get(player, StatTypes.INTELLIGENCE)) / 100));
                        }
                    }
                }
                case "rapid" -> {
                    if (tick % 4 == 0) {
                        LivingEntity target = firstInLine(player, 30);
                        if (target != null) {
                            hurt(player, target, 0.75 * 15_000 * (1 + Math.max(0, Stats.get(player, StatTypes.STRENGTH)) / 100));
                            Msg.playSound(player, SoundEvents.ARROW_SHOOT, 0.6f, 1.4f);
                        }
                    }
                }
                default -> {
                }
            }
            return false;
        });
        if (tick % 20 == 0) {
            ServerLevel level = server.getLevel(Catacombs.LEVEL);
            if (level == null) {
                return;
            }
            for (ServerPlayer healer : level.players()) {
                Run run = Catacombs.runOf(healer);
                if (run != null && "healer".equals(run.classes.get(healer.getUUID())) && !run.ghosts.contains(healer.getUUID())) {
                    for (ServerPlayer ally : allies(run, healer, 8)) {
                        heal(ally, 0.01);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static List<ServerPlayer> allies(Run run, ServerPlayer from, double range) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : run.online()) {
            if (!run.ghosts.contains(p.getUUID()) && p.distanceTo(from) <= range) {
                out.add(p);
            }
        }
        return out;
    }

    private static void heal(ServerPlayer player, double share) {
        player.heal((float) (player.getMaxHealth() * share));
    }

    private static List<LivingEntity> mobsNear(ServerPlayer player, Vec3 at, double range) {
        return player.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(range),
                e -> e.isAlive() && !(e instanceof Player) && e.entityTags().contains(DungeonMobs.TAG) && e.position().distanceTo(at) <= range);
    }

    private static void blast(ServerPlayer player, Vec3 at, double radius, double damage) {
        for (LivingEntity mob : mobsNear(player, at, radius)) {
            hurt(player, mob, damage);
        }
    }

    private static void hurt(ServerPlayer player, LivingEntity target, double amount) {
        ServerLevel level = player.level();
        target.setInvulnerableTime(0);
        target.hurtServer(level, level.damageSources().indirectMagic(player, player), (float) (amount / StatTypes.HEALTH_SCALE));
    }

    /** Where the player is looking, up to {@code range} blocks (stops at a wall). */
    private static Vec3 aimed(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        var hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        return hit.getLocation();
    }

    private static LivingEntity firstInLine(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity best = null;
        double bestDistance = range;
        for (LivingEntity mob : mobsNear(player, eye, range)) {
            Vec3 to = mob.getBoundingBox().getCenter().subtract(eye);
            double along = to.dot(look);
            if (along <= 0 || along > bestDistance) {
                continue;
            }
            if (to.subtract(look.scale(along)).length() < 1.5) {
                best = mob;
                bestDistance = along;
            }
        }
        return best;
    }

    private static void ring(ServerLevel level, Vec3 at, double radius, ParticleOptions particle) {
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            level.sendParticles(particle, at.x + Math.cos(a) * radius, at.y + 0.2, at.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }
}
