package com.skyrpg.skyblock.rift;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Leech Supreme (Hypixel's Black Lagoon miniboss) in the Leeches Lair: 800 health, four phases as it loses a fifth
 * at a time. Slime Pound (a shockwave around it), then Wicked Bombs (marked circles that blow up), the Leech Swarm (small
 * leeches) and the Mortiferous Lazer (a sweep you dodge by sneaking). Everything it does takes Rift Time. It drops 8 Leech
 * Supreme Fragments and 3,000 Motes to everyone who fought it and comes back 40 seconds later (Hypixel's). Decided: the
 * attacks' timings and costs (Hypixel's numbers weren't found).
 */
final class LeechSupreme {
    static final String TAG = "skyrpg_leech_supreme";
    static final String SWARM = "skyrpg_leech_swarm";
    private static final double HEALTH = 800;
    private static final int RESPAWN_TICKS = 40 * 20;
    private static final DustParticleOptions RED = new DustParticleOptions(0xD02020, 1.6f);

    private static Silverfish boss;
    private static long deadAt = -RESPAWN_TICKS;
    private static final Set<UUID> FIGHTERS = new HashSet<>();
    private static final List<Vec3> BOMBS = new ArrayList<>();
    private static long bombsAt;
    private static long lazerUntil;
    private static final ServerBossEvent BAR = new ServerBossEvent(UUID.randomUUID(), Component.literal("Leech Supreme").withStyle(ChatFormatting.DARK_GREEN),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);

    private LeechSupreme() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(LeechSupreme::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity == boss && source.getEntity() instanceof ServerPlayer player) {
                FIGHTERS.add(player.getUUID());
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity == boss) {
                defeated(entity.level().getServer());
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if ((entity.entityTags().contains(TAG) && entity != boss) || entity.entityTags().contains(SWARM) && boss == null) {
                entity.discard();
            }
        });
    }

    private static List<ServerPlayer> inLair(ServerLevel level) {
        return level.getPlayers(p -> {
            Zones.Zone zone = Zones.at(level, p.blockPosition());
            return zone != null && zone.name().equals("Leeches Lair");
        });
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = Rift.level(server);
        if (level == null) {
            return;
        }
        long now = server.getTickCount();
        List<ServerPlayer> players = inLair(level);
        // The bar for whoever's in the lair.
        for (ServerPlayer p : new ArrayList<>(BAR.getPlayers())) {
            if (!players.contains(p) || boss == null) {
                BAR.removePlayer(p);
            }
        }
        if (boss == null || boss.isRemoved()) {
            boss = null;
            Rift.Spot spot = Rift.spots().get("leech_supreme");
            if (!players.isEmpty() && spot != null && now - deadAt >= RESPAWN_TICKS && level.isLoaded(net.minecraft.core.BlockPos.containing(spot.pos()))) {
                spawn(level, spot.pos());
            }
            return;
        }
        players.forEach(BAR::addPlayer);
        BAR.setProgress(Math.max(0, boss.getHealth() / boss.getMaxHealth()));
        if (players.isEmpty()) {
            return;
        }
        int phase = phase();
        // Slime Pound, every 7 seconds: a shockwave around it.
        if (now % 140 == 0) {
            level.sendParticles(ParticleTypes.ITEM_SLIME, boss.getX(), boss.getY() + 0.2, boss.getZ(), 80, 4, 0.1, 4, 0.2);
            level.playSound(null, boss.blockPosition(), SoundEvents.SLIME_SQUISH, boss.getSoundSource(), 2f, 0.5f);
            for (ServerPlayer p : players) {
                if (p.distanceToSqr(boss) < 8 * 8 && p.onGround()) {
                    hit(p, 8, "Slime Pound");
                    Vec3 away = p.position().subtract(boss.position()).normalize().scale(1.2);
                    p.setDeltaMovement(away.x, 0.6, away.z);
                    p.syncVelocity = true;
                }
            }
        }
        // Wicked Bombs (phase 2+), every 9 seconds: three marked circles, they go off 2 seconds later.
        if (phase >= 2 && now % 180 == 0) {
            BOMBS.clear();
            for (int i = 0; i < 3 && !players.isEmpty(); i++) {
                ServerPlayer p = players.get(level.getRandom().nextInt(players.size()));
                BOMBS.add(p.position().add(level.getRandom().nextDouble() * 4 - 2, 0, level.getRandom().nextDouble() * 4 - 2));
            }
            bombsAt = now + 40;
        }
        if (!BOMBS.isEmpty()) {
            for (Vec3 bomb : BOMBS) {
                for (int a = 0; a < 16; a++) {
                    double t = a * Math.PI / 8;
                    level.sendParticles(RED, bomb.x + Math.cos(t) * 2.5, bomb.y + 0.1, bomb.z + Math.sin(t) * 2.5, 1, 0, 0, 0, 0);
                }
            }
            if (now >= bombsAt) {
                for (Vec3 bomb : BOMBS) {
                    level.sendParticles(ParticleTypes.EXPLOSION, bomb.x, bomb.y + 0.5, bomb.z, 2, 0.5, 0.2, 0.5, 0);
                    level.playSound(null, net.minecraft.core.BlockPos.containing(bomb), SoundEvents.GENERIC_EXPLODE.value(),
                            boss.getSoundSource(), 1f, 1.2f);
                    for (ServerPlayer p : players) {
                        if (p.position().distanceToSqr(bomb) < 2.5 * 2.5) {
                            hit(p, 10, "Wicked Bomb");
                        }
                    }
                }
                BOMBS.clear();
            }
        }
        // Leech Swarm (phase 3+), every 15 seconds: two small leeches.
        if (phase >= 3 && now % 300 == 0) {
            for (int i = 0; i < 2; i++) {
                Silverfish leech = EntityTypes.SILVERFISH.create(level, EntitySpawnReason.EVENT);
                if (leech != null) {
                    leech.snapTo(boss.getX() + i * 2 - 1, boss.getY(), boss.getZ() + 1, 0, 0);
                    leech.setCustomName(Component.literal("Leech").withStyle(ChatFormatting.DARK_GREEN));
                    leech.addTag(SWARM);
                    level.addFreshEntity(leech);
                }
            }
        }
        // Mortiferous Lazer (phase 4), every 12 seconds for 3 seconds: sneak to duck under it.
        if (phase >= 4 && now % 240 == 0) {
            lazerUntil = now + 60;
            Notice.of(Notice.Kind.WARN).tag("LEECH SUPREME").send(players.get(0), Component.literal("Mortiferous Lazer! Sneak!").withStyle(ChatFormatting.RED));
            for (int i = 1; i < players.size(); i++) {
                players.get(i).sendOverlayMessage(Component.literal("Mortiferous Lazer! Sneak!").withStyle(ChatFormatting.RED));
            }
        }
        if (now < lazerUntil && now % 10 == 0) {
            for (ServerPlayer p : players) {
                level.sendParticles(ParticleTypes.END_ROD, p.getX(), boss.getEyeY(), p.getZ(), 6, 0.6, 0.05, 0.6, 0);
                if (!p.isShiftKeyDown() && p.hasLineOfSight(boss)) {
                    hit(p, 2, "Mortiferous Lazer");
                }
            }
        }
    }

    /** 1 to 4: a phase for every fifth of its health it has lost (to 160 health). */
    private static int phase() {
        double left = boss.getHealth() / boss.getMaxHealth();
        return left > 0.8 ? 1 : left > 0.6 ? 2 : left > 0.4 ? 3 : 4;
    }

    private static void hit(ServerPlayer player, int seconds, String what) {
        Rift.takeTime(player, seconds);
        player.sendOverlayMessage(Component.literal(what + "! -" + seconds + "s ф Rift Time").withStyle(ChatFormatting.RED));
    }

    private static void spawn(ServerLevel level, Vec3 at) {
        Silverfish leech = EntityTypes.SILVERFISH.create(level, EntitySpawnReason.EVENT);
        if (leech == null) {
            return;
        }
        leech.snapTo(at.x, at.y, at.z, 0, 0);
        leech.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH / StatTypes.HEALTH_SCALE);
        leech.setHealth(leech.getMaxHealth());
        leech.getAttribute(Attributes.SCALE).setBaseValue(5);
        leech.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6 / StatTypes.HEALTH_SCALE);
        leech.setCustomName(Component.literal("Leech Supreme").withStyle(ChatFormatting.DARK_GREEN));
        leech.setCustomNameVisible(true);
        leech.setPersistenceRequired();
        leech.addTag(TAG);
        FIGHTERS.clear();
        BOMBS.clear();
        boss = leech;
        level.addFreshEntity(leech);
        for (ServerPlayer p : inLair(level)) {
            p.sendSystemMessage(Component.literal("The Leech Supreme stirs in the depths!").withStyle(ChatFormatting.DARK_GREEN));
        }
        level.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.ELDER_GUARDIAN_CURSE, net.minecraft.sounds.SoundSource.HOSTILE,
                1f, 0.6f);
    }

    private static void defeated(MinecraftServer server) {
        ServerLevel level = Rift.level(server);
        deadAt = server.getTickCount();
        boss = null;
        BOMBS.clear();
        if (level == null) {
            return;
        }
        for (net.minecraft.world.entity.Entity e : new ArrayList<>(level.getEntities((net.minecraft.world.entity.Entity) null,
                new net.minecraft.world.phys.AABB(-200, 0, -40, -50, 80, 70), e -> e.entityTags().contains(SWARM)))) {
            e.discard();
        }
        ItemDefinition fragment = ItemRegistry.skyblock("LEECH_SUPREME_FRAGMENT");
        for (UUID id : FIGHTERS) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.level() != level) {
                continue;
            }
            if (fragment != null) {
                ItemStack stack = SkyItems.create(fragment, 8);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
            Motes.give(player, 3_000);
            Notice.of(Notice.Kind.SUCCESS).tag("LEECH SUPREME").panel()
                    .line(Component.literal(" 8 Leech Supreme Fragments  ·  +3,000 Motes").withStyle(ChatFormatting.GRAY))
                    .send(player, Component.literal("You defeated the Leech Supreme!").withStyle(ChatFormatting.DARK_GREEN));
            Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        FIGHTERS.clear();
    }
}
