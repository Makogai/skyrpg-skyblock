package com.skyrpg.skyblock.rift;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Bacte (Hypixel's Colosseum boss): a giant slime of 1,000 health, a letter of its name for each of its five phases.
 * Each phase raises its tentacles (1, 2, 3, 4, then 6 of 20 health); while any is up Bacte can't be hurt. From phase 2
 * spikes burst from marked spots, from phase 3 a slice sweeps the arena, in phase 5 the floor turns bouncy. Every attack
 * takes Rift Time. It drops 8 Bacte Fragments and 6,000 Motes (Hypixel's) to everyone who fought it and comes back a
 * minute later. Decided: punching Globowls is replaced by hitting the tentacles; the attacks' timings and costs.
 */
final class Bacte {
    static final String TAG = "skyrpg_bacte";
    static final String TENTACLE = "skyrpg_bacte_tentacle";
    private static final double HEALTH = 1000;
    private static final int RESPAWN_TICKS = 60 * 20;
    private static final int[] TENTACLES = {1, 2, 3, 4, 6};
    private static final DustParticleOptions GREEN = new DustParticleOptions(0x55FF55, 1.6f);

    private static Slime boss;
    private static int phase;
    private static long deadAt = -RESPAWN_TICKS;
    private static final List<Slime> ARMS = new ArrayList<>();
    private static final Set<UUID> FIGHTERS = new HashSet<>();
    private static final List<Vec3> SPIKES = new ArrayList<>();
    private static long spikesAt;
    private static final ServerBossEvent BAR = new ServerBossEvent(UUID.randomUUID(), Component.literal("B").withStyle(ChatFormatting.GREEN),
            BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.NOTCHED_10);

    private Bacte() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Bacte::tick);
        // Bacte can't be hurt while a tentacle is up.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> entity != boss || ARMS.stream().allMatch(Slime::isRemoved));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if ((entity == boss || entity.entityTags().contains(TENTACLE)) && source.getEntity() instanceof ServerPlayer player) {
                FIGHTERS.add(player.getUUID());
            }
        });
        // Slimes split when they die: Bacte and its tentacles shrink to the smallest size first.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof Slime slime && (entity == boss || entity.entityTags().contains(TENTACLE))) {
                slime.setSize(1, false);
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity == boss) {
                defeated(entity.level().getServer());
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if ((entity.entityTags().contains(TAG) && entity != boss) || (entity.entityTags().contains(TENTACLE) && !ARMS.contains(entity))) {
                entity.discard();
            }
        });
    }

    private static List<ServerPlayer> inArena(ServerLevel level, Vec3 centre) {
        return level.getPlayers(p -> p.position().distanceToSqr(centre) < 24 * 24 && Math.abs(p.getY() - centre.y) < 12);
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = Rift.level(server);
        Rift.Spot spot = Rift.spots().get("bacte");
        if (level == null || spot == null) {
            return;
        }
        long now = server.getTickCount();
        List<ServerPlayer> players = inArena(level, spot.pos());
        for (ServerPlayer p : new ArrayList<>(BAR.getPlayers())) {
            if (!players.contains(p) || boss == null) {
                BAR.removePlayer(p);
            }
        }
        if (boss == null || boss.isRemoved()) {
            boss = null;
            if (!players.isEmpty() && now - deadAt >= RESPAWN_TICKS && level.isLoaded(BlockPos.containing(spot.pos()))) {
                spawn(level, spot.pos(), players);
            }
            return;
        }
        players.forEach(BAR::addPlayer);
        BAR.setProgress(Math.max(0, boss.getHealth() / boss.getMaxHealth()));
        ARMS.removeIf(Slime::isRemoved);
        // A phase for every 200 health lost; each raises its tentacles.
        int now_phase = Math.min(5, 1 + (int) ((1 - boss.getHealth() / boss.getMaxHealth()) * 5));
        if (now_phase != phase) {
            phase = now_phase;
            BAR.setName(Component.literal("Bacte".substring(0, phase)).withStyle(ChatFormatting.GREEN));
            raise(level, players);
        }
        if (players.isEmpty()) {
            return;
        }
        // Spikes (phase 2+), every 8 seconds: four marked spots burst 2 seconds later.
        if (phase >= 2 && now % 160 == 0) {
            SPIKES.clear();
            for (int i = 0; i < 4; i++) {
                ServerPlayer p = players.get(level.getRandom().nextInt(players.size()));
                SPIKES.add(p.position().add(level.getRandom().nextDouble() * 3 - 1.5, 0, level.getRandom().nextDouble() * 3 - 1.5));
            }
            spikesAt = now + 40;
        }
        if (!SPIKES.isEmpty()) {
            for (Vec3 spike : SPIKES) {
                level.sendParticles(GREEN, spike.x, spike.y + 0.1, spike.z, 4, 1, 0, 1, 0);
            }
            if (now >= spikesAt) {
                for (Vec3 spike : SPIKES) {
                    level.sendParticles(ParticleTypes.ITEM_SLIME, spike.x, spike.y + 1, spike.z, 30, 0.6, 1.2, 0.6, 0.1);
                    for (ServerPlayer p : players) {
                        if (p.position().distanceToSqr(spike) < 2 * 2) {
                            hit(p, 8, "Spikes");
                        }
                    }
                }
                level.playSound(null, BlockPos.containing(SPIKES.get(0)), SoundEvents.SLIME_ATTACK, SoundSource.HOSTILE, 1.5f, 0.6f);
                SPIKES.clear();
            }
        }
        // Slice (phase 3+), every 10 seconds: a sweep through the arena; jump over it.
        if (phase >= 3 && now % 200 == 100) {
            level.playSound(null, boss.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 2f, 0.5f);
            for (ServerPlayer p : players) {
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX(), p.getY() + 0.3, p.getZ(), 3, 0.8, 0, 0.8, 0);
                if (p.onGround()) {
                    hit(p, 6, "Slice");
                }
            }
        }
        // Phase 5: the arena turns to slime: everyone bounces.
        if (phase >= 5 && now % 20 == 0) {
            for (ServerPlayer p : players) {
                p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 40, 2, false, false));
            }
        }
    }

    private static void hit(ServerPlayer player, int seconds, String what) {
        Rift.takeTime(player, seconds);
        player.sendOverlayMessage(Component.literal(what + "! -" + seconds + "s ф Rift Time").withStyle(ChatFormatting.RED));
    }

    private static void spawn(ServerLevel level, Vec3 at, List<ServerPlayer> players) {
        Slime slime = EntityTypes.SLIME.create(level, EntitySpawnReason.EVENT);
        if (slime == null) {
            return;
        }
        slime.setSize(6, true);
        slime.snapTo(at.x, at.y, at.z, 0, 0);
        slime.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH / StatTypes.HEALTH_SCALE);
        slime.setHealth(slime.getMaxHealth());
        slime.setCustomName(Component.literal("Bacte").withStyle(ChatFormatting.GREEN));
        slime.setCustomNameVisible(true);
        slime.setPersistenceRequired();
        slime.addTag(TAG);
        boss = slime;
        phase = 0;
        FIGHTERS.clear();
        SPIKES.clear();
        level.addFreshEntity(slime);
        for (ServerPlayer p : players) {
            Notice.of(Notice.Kind.WARN).tag("BACTE").send(p, Component.literal("Bacte oozes into the arena! Cut its tentacles to reach it.")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** The phase's tentacles around the arena (Hypixel's 1, 2, 3, 4 then 6; 20 health from phase 4, decided 60 before). */
    private static void raise(ServerLevel level, List<ServerPlayer> players) {
        int count = TENTACLES[phase - 1];
        double health = phase >= 4 ? 20 : 60;
        for (int i = 0; i < count; i++) {
            Rift.Spot spot = Rift.spots().get("bacte_tentacle_" + (i % 6 + 1));
            if (spot == null) {
                continue;
            }
            Slime arm = EntityTypes.SLIME.create(level, EntitySpawnReason.EVENT);
            if (arm == null) {
                continue;
            }
            arm.setSize(2, true);
            arm.snapTo(spot.pos().x, spot.pos().y, spot.pos().z, 0, 0);
            arm.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health / StatTypes.HEALTH_SCALE);
            arm.setHealth(arm.getMaxHealth());
            arm.setCustomName(Component.literal("Bacte's Tentacle").withStyle(ChatFormatting.DARK_GREEN));
            arm.setPersistenceRequired();
            arm.addTag(TENTACLE);
            ARMS.add(arm);
            level.addFreshEntity(arm);
        }
        for (ServerPlayer p : players) {
            p.sendOverlayMessage(Component.literal("Bacte raises " + count + " tentacle" + (count == 1 ? "" : "s") + "!").withStyle(ChatFormatting.GREEN));
        }
    }

    private static void defeated(MinecraftServer server) {
        deadAt = server.getTickCount();
        boss = null;
        SPIKES.clear();
        ARMS.forEach(Slime::discard);
        ARMS.clear();
        ItemDefinition fragment = ItemRegistry.skyblock("BACTE_FRAGMENT");
        ServerLevel level = Rift.level(server);
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
            Motes.give(player, 6_000);
            Notice.of(Notice.Kind.SUCCESS).tag("BACTE").panel()
                    .line(Component.literal(" 8 Bacte Fragments  ·  +6,000 Motes").withStyle(ChatFormatting.GRAY))
                    .send(player, Component.literal("You defeated Bacte!").withStyle(ChatFormatting.GREEN));
            Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        FIGHTERS.clear();
    }
}
