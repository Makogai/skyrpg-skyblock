package com.skyrpg.skyblock.crimson;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.essence.Essence;
import com.skyrpg.skyblock.zone.Zones;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Crimson Isle's minibosses (Hypixel's numbers): Bladesoul in the Stronghold, the Mage Outlaw in the Courtyard,
 * Barbarian Duke X in the Dukedom, Ashfang in his ruins and the Magma Boss in the Magma Chamber. Each comes back two
 * minutes after it dies (while someone is near its spot). Everyone who did at least half a percent of its health in
 * damage gets its Crimson Essence (25, or 50 for Ashfang and the Magma Boss), Combat XP, coins and 20 reputation, and
 * the miniboss counts for their faction quests. The Magma Boss also drops Magma Cream, Compact Ooze and a Rekindled
 * Ember Fragment. Each one has a move every few seconds (a sweep, a slam, a fire burst).
 */
public final class Minibosses {
    static final String TAG = "skyrpg_crimson_miniboss";
    private static final int RESPAWN_TICKS = 2 * 60 * 20;
    private static final double SHARE = 0.005;

    /** A miniboss: its spot, entity, level, health, damage, essence, Combat XP and coins. */
    record Boss(String name, String place, EntityType<? extends Mob> type, int level, double health, double damage, int essence, double xp,
                long coins) {
    }

    static final List<Boss> BOSSES = List.of(
            new Boss("Bladesoul", "the Stronghold", EntityTypes.WITHER_SKELETON, 477, 50_000_000, 12_000, 25, 2_500, 1_500),
            new Boss("Mage Outlaw", "the Courtyard", EntityTypes.VINDICATOR, 515, 70_000_000, 14_000, 25, 3_000, 1_500),
            new Boss("Barbarian Duke X", "the Dukedom", EntityTypes.PIGLIN_BRUTE, 486, 70_000_000, 14_000, 25, 3_000, 1_500),
            new Boss("Ashfang", "the Ruins of Ashfang", EntityTypes.BLAZE, 508, 50_000_000, 12_000, 50, 3_000, 1_300),
            new Boss("Magma Boss", "the Magma Chamber", EntityTypes.MAGMA_CUBE, 523, 200_000_000, 2_000, 50, 3_922, 1_307));

    /** One boss's state: the living entity, when it may return, who hurt it how much, its bar. */
    private static final class Live {
        Mob mob;
        long nextSpawn;
        long nextMove;
        final Map<UUID, Double> damage = new HashMap<>();
        ServerBossEvent bar;
    }

    private static final Map<String, Live> LIVE = new HashMap<>();

    private Minibosses() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Minibosses::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity.entityTags().contains(TAG) && source.getEntity() instanceof ServerPlayer player) {
                Live live = live(entity);
                if (live != null) {
                    live.damage.merge(player.getUUID(), (double) taken, Double::sum);
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.entityTags().contains(TAG)) {
                died(entity);
            }
        });
        // A miniboss left over from before a restart is gone; its timer starts over.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && live(entity) == null) {
                entity.discard();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LIVE.values().forEach(live -> {
                if (live.mob != null) {
                    live.mob.discard();
                }
                if (live.bar != null) {
                    live.bar.removeAllPlayers();
                }
            });
            LIVE.clear();
        });
    }

    private static Live live(Entity entity) {
        for (Live live : LIVE.values()) {
            if (live.mob == entity) {
                return live;
            }
        }
        return null;
    }

    private static Boss boss(String name) {
        return BOSSES.stream().filter(b -> b.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    /** Admin: spawns it now (if its spot is known and it isn't up). */
    static boolean spawnNow(MinecraftServer server, String name) {
        Boss boss = boss(name);
        ServerLevel level = CrimsonIsle.level(server);
        if (boss == null || level == null || CrimsonIsle.spot(boss.name()) == null) {
            return false;
        }
        Live live = LIVE.computeIfAbsent(boss.name(), k -> new Live());
        if (live.mob != null && live.mob.isAlive() && level.getEntity(live.mob.getUUID()) == live.mob) {
            return true;
        }
        spawn(level, boss, live);
        return live.mob != null;
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = CrimsonIsle.level(server);
        if (level == null) {
            return;
        }
        long now = server.getTickCount();
        for (Boss boss : BOSSES) {
            Live live = LIVE.computeIfAbsent(boss.name(), k -> new Live());
            CrimsonIsle.Spot spot = CrimsonIsle.spot(boss.name());
            if (spot == null) {
                continue;
            }
            if (live.mob != null && (!live.mob.isAlive() || live.mob.isRemoved() || level.getEntity(live.mob.getUUID()) != live.mob)) {
                if (live.mob.getRemovalReason() != Entity.RemovalReason.KILLED) {
                    // Unloaded or discarded: it comes back soon.
                    live.nextSpawn = now + 200;
                }
                clear(live);
            }
            if (live.mob == null) {
                if (now % 20 == 0 && now >= live.nextSpawn && anyoneNear(level, spot.pos(), 96)
                        && level.hasChunkAt(net.minecraft.core.BlockPos.containing(spot.pos()))) {
                    spawn(level, boss, live);
                }
                continue;
            }
            Mob mob = live.mob;
            // Bar for players nearby.
            if (now % 10 == 0) {
                live.bar.setProgress(Math.max(0, mob.getHealth() / mob.getMaxHealth()));
                for (ServerPlayer player : level.players()) {
                    if (player.distanceTo(mob) < 48) {
                        live.bar.addPlayer(player);
                    } else {
                        live.bar.removePlayer(player);
                    }
                }
            }
            // Leashed to its spot.
            if (now % 20 == 0 && mob.position().distanceTo(spot.pos()) > 28) {
                mob.teleportTo(spot.pos().x, spot.pos().y, spot.pos().z);
                mob.setTarget(null);
            }
            if (now >= live.nextMove) {
                live.nextMove = now + 100 + mob.getRandom().nextInt(60);
                move(level, boss, mob);
            }
        }
    }

    private static boolean anyoneNear(ServerLevel level, Vec3 pos, double radius) {
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && player.position().distanceTo(pos) < radius) {
                return true;
            }
        }
        return false;
    }

    private static void spawn(ServerLevel level, Boss boss, Live live) {
        CrimsonIsle.Spot spot = CrimsonIsle.spot(boss.name());
        Mob mob = boss.type().create(level, EntitySpawnReason.EVENT);
        if (mob == null || spot == null) {
            return;
        }
        mob.snapTo(spot.pos().x, spot.pos().y, spot.pos().z, spot.yaw(), 0);
        if (mob instanceof MagmaCube cube) {
            cube.setSize(8, true);
        }
        Zones.dressAs(mob, boss.name(), boss.level(), boss.health(), boss.damage(), -1);
        mob.addTag(TAG);
        mob.addTag(Zones.FIGHTABLE_TAG);
        mob.setPersistenceRequired();
        mob.setCustomNameVisible(true);
        if (mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin piglin) {
            piglin.setImmuneToZombification(true);
        }
        // Known before it enters the world (the load check discards minibosses nobody owns).
        live.mob = mob;
        level.addFreshEntity(mob);
        live.damage.clear();
        live.nextMove = level.getServer().getTickCount() + 100;
        live.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(boss.name()).withStyle(ChatFormatting.RED),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.literal(boss.name() + " has spawned in " + boss.place() + "!").withStyle(ChatFormatting.RED));
        }
        level.playSound(null, mob.blockPosition(), SoundEvents.WITHER_SPAWN, net.minecraft.sounds.SoundSource.HOSTILE, 0.6f, 1.2f);
    }

    private static void clear(Live live) {
        live.mob = null;
        if (live.bar != null) {
            live.bar.removeAllPlayers();
            live.bar = null;
        }
    }

    /** Its move: Bladesoul and the Duke sweep around them, the Outlaw and Ashfang burst fire, the Magma Boss slams. */
    private static void move(ServerLevel level, Boss boss, Mob mob) {
        double damage = mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
        switch (boss.name()) {
            case "Bladesoul", "Barbarian Duke X" -> {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, mob.getX(), mob.getY() + 1, mob.getZ(), 12, 2.5, 0.3, 2.5, 0);
                level.playSound(null, mob.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 0.7f);
                hitAround(level, mob, 5, damage * 1.5);
            }
            case "Mage Outlaw", "Ashfang" -> {
                level.sendParticles(ParticleTypes.FLAME, mob.getX(), mob.getY() + 1, mob.getZ(), 60, 3, 1, 3, 0.05);
                level.playSound(null, mob.blockPosition(), SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 0.8f);
                hitAround(level, mob, 7, damage);
                for (ServerPlayer player : level.players()) {
                    if (player.distanceTo(mob) < 7) {
                        player.igniteForSeconds(3);
                    }
                }
            }
            case "Magma Boss" -> {
                mob.setDeltaMovement(mob.getDeltaMovement().add(0, 1.1, 0));
                level.sendParticles(ParticleTypes.LAVA, mob.getX(), mob.getY(), mob.getZ(), 40, 4, 0.5, 4, 0);
                level.playSound(null, mob.blockPosition(), SoundEvents.MAGMA_CUBE_JUMP, net.minecraft.sounds.SoundSource.HOSTILE, 1.5f, 0.5f);
                hitAround(level, mob, 9, damage * 3);
            }
            default -> {
            }
        }
    }

    private static void hitAround(ServerLevel level, Mob mob, double radius, double damage) {
        for (ServerPlayer player : level.players()) {
            if (!player.isCreative() && !player.isSpectator() && player.distanceTo(mob) <= radius) {
                player.hurtServer(level, level.damageSources().mobAttack(mob), (float) damage);
            }
        }
    }

    private static void died(LivingEntity entity) {
        Live live = live(entity);
        if (live == null) {
            return;
        }
        Boss boss = null;
        for (Map.Entry<String, Live> e : LIVE.entrySet()) {
            if (e.getValue() == live) {
                boss = boss(e.getKey());
            }
        }
        ServerLevel level = (ServerLevel) entity.level();
        live.nextSpawn = level.getServer().getTickCount() + RESPAWN_TICKS;
        clear(live);
        if (boss == null) {
            return;
        }
        double needed = entity.getMaxHealth() * SHARE;
        for (Map.Entry<UUID, Double> e : live.damage.entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || e.getValue() < needed) {
                continue;
            }
            Essence.give(player, "CRIMSON", boss.essence());
            com.skyrpg.core.skills.SkillLevels.giveXp(player, com.skyrpg.core.skills.SkillLevels.COMBAT, boss.xp());
            Wallet.give(player, boss.coins());
            Notice.Panel panel = Notice.of(Notice.Kind.COMBAT).tag("MINIBOSS").panel()
                    .line(Component.literal(" +" + boss.essence() + " Crimson Essence").withStyle(ChatFormatting.RED))
                    .line(Component.literal(" +" + Msg.number(boss.coins()) + " coins").withStyle(ChatFormatting.GOLD));
            if (boss.name().equals("Magma Boss")) {
                give(player, "MAGMA_CREAM", 20 + player.getRandom().nextInt(16));
                if (player.getRandom().nextBoolean()) {
                    give(player, "ENCHANTED_MAGMA_CREAM", 1);
                }
                give(player, "COMPACT_OOZE", 1);
                give(player, "REKINDLED_EMBER_FRAGMENT", 1);
                panel.line(Component.literal(" Magma Cream, Compact Ooze, a Rekindled Ember Fragment").withStyle(ChatFormatting.GRAY));
            }
            panel.send(player, Component.literal(boss.name() + " is down!").withStyle(ChatFormatting.RED));
            Factions.give(player, 20, boss.name());
            FactionQuests.minibossKilled(player, boss.name());
        }
    }

    private static void give(ServerPlayer player, String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        ItemStack stack = SkyItems.create(def, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }
}
