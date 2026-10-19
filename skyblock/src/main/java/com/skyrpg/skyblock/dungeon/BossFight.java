package com.skyrpg.skyblock.dungeon;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * A floor's boss fight, in its arena beside the map ({@code structure/catacombs/boss/<boss>.nbt}, or a plain arena until
 * one is built). Each floor's fight is in {@link Bosses}; this is what they share: the arena, spawning the boss and its
 * minions with Hypixel's health, a boss bar, speaking, hurting players, and the win.
 */
abstract class BossFight {
    static final int ARENA = 61;
    static final int HEIGHT = 30;

    final Run run;
    /** The boss mob the bar follows (null between phases). */
    Mob boss;
    final Set<UUID> minions = new HashSet<>();
    private ServerBossEvent bar;
    private long beginAt;
    private boolean begun;
    boolean won;

    BossFight(Run run) {
        this.run = run;
    }

    static BossFight start(Run run) {
        BossFight fight = switch (run.floor.number()) {
            case 2 -> new Bosses.Scarf(run);
            case 3 -> new Bosses.Professor(run);
            case 4 -> new Bosses.Thorn(run);
            case 5 -> new Bosses.Livid(run);
            case 6 -> new Bosses.Sadan(run);
            case 7 -> new Bosses.Necron(run);
            default -> new Bosses.Bonzo(run);
        };
        fight.beginAt = run.level.getGameTime() + 100;
        fight.intro();
        return fight;
    }

    // ---------------------------------------------------------------- the arena

    /**
     * The arena: Hypixel's own if it's installed ({@code catacombs/boss/hypixel/<boss>}, in the world's generated folder
     * from {@code tools/worlds/hypixel_dungeons.py arenas}; its middle and way in are markers in it), else ours, else a
     * plain hall of blackstone.
     */
    static void buildArena(Run run) {
        BlockPos o = run.bossOrigin;
        var manager = run.level.getServer().getStructureTemplateManager();
        StructureTemplate hypixel = manager.get(Identifier.fromNamespaceAndPath("skyrpg_skyblock", "catacombs/boss/hypixel/" + run.floor.boss()))
                .orElse(null);
        int sizeX = hypixel == null ? ARENA : hypixel.getSize().getX();
        int sizeZ = hypixel == null ? ARENA : hypixel.getSize().getZ();
        for (int cx = o.getX() >> 4; cx <= (o.getX() + sizeX) >> 4; cx++) {
            for (int cz = o.getZ() >> 4; cz <= (o.getZ() + sizeZ) >> 4; cz++) {
                run.level.getChunk(cx, cz);
            }
        }
        if (hypixel != null) {
            hypixel.placeInWorld(run.level, o, o, new StructurePlaceSettings(), run.random, Block.UPDATE_CLIENTS);
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(o.getX(), o.getY(), o.getZ(), o.getX() + sizeX,
                    o.getY() + hypixel.getSize().getY(), o.getZ() + sizeZ);
            for (net.minecraft.world.entity.Marker marker : run.level.getEntitiesOfClass(net.minecraft.world.entity.Marker.class, box,
                    m -> m.entityTags().contains("skyrpg_catacombs"))) {
                String kind = ((com.skyrpg.skyblock.mixin.EntityDataAccess) marker).skyrpg$customData().copyTag().getStringOr("catacombs", "");
                if (kind.equals("arena_center")) {
                    run.arenaCenter = marker.position();
                } else if (kind.equals("arena_arrival")) {
                    run.arenaArrival = marker.position();
                }
                marker.discard();
            }
            return;
        }
        Identifier id = Identifier.fromNamespaceAndPath("skyrpg_skyblock", "catacombs/boss/" + run.floor.boss());
        StructureTemplate template = manager.get(id).orElse(null);
        if (template != null) {
            template.placeInWorld(run.level, o, o, new StructurePlaceSettings(), run.random, Block.UPDATE_CLIENTS);
            return;
        }
        for (int x = 0; x < ARENA; x++) {
            for (int z = 0; z < ARENA; z++) {
                for (int y = 0; y < HEIGHT; y++) {
                    boolean edge = x == 0 || z == 0 || x == ARENA - 1 || z == ARENA - 1;
                    BlockState state = y == 0 ? Blocks.BEDROCK.defaultBlockState()
                            : y <= Run.FLOOR ? ((x + z) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS).defaultBlockState()
                            : edge || y == HEIGHT - 1 ? Blocks.STONE_BRICKS.defaultBlockState()
                            : y == HEIGHT - 2 && x % 6 == 3 && z % 6 == 3 ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    run.level.setBlock(o.offset(x, y, z), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    Vec3 arrival() {
        return run.arenaArrival != null ? run.arenaArrival : Vec3.atBottomCenterOf(run.bossOrigin.offset(ARENA / 2, Run.FLOOR + 1, ARENA - 6));
    }

    Vec3 center() {
        return run.arenaCenter != null ? run.arenaCenter : Vec3.atBottomCenterOf(run.bossOrigin.offset(ARENA / 2, Run.FLOOR + 1, ARENA / 2));
    }

    /** A spot in the arena: dx/dz blocks from the middle (on Hypixel's arenas, moved onto the nearest ground). */
    Vec3 spot(double dx, double dz) {
        Vec3 at = center().add(dx, 0, dz);
        if (run.arenaCenter == null) {
            return at;
        }
        BlockPos base = BlockPos.containing(at);
        for (int d = 0; d <= 6; d++) {
            for (int sign : new int[] {1, -1}) {
                BlockPos pos = base.above(d * sign);
                if (run.level.getBlockState(pos).getCollisionShape(run.level, pos).isEmpty()
                        && run.level.getBlockState(pos.above()).getCollisionShape(run.level, pos.above()).isEmpty()
                        && !run.level.getBlockState(pos.below()).getCollisionShape(run.level, pos.below()).isEmpty()) {
                    return Vec3.atBottomCenterOf(pos).add(at.x - Math.floor(at.x) - 0.5, 0, at.z - Math.floor(at.z) - 0.5);
                }
                if (d == 0) {
                    break;
                }
            }
        }
        return center();
    }

    void teleportIn(ServerPlayer player) {
        Vec3 at = arrival();
        // facing the arena's middle
        Vec3 to = center().subtract(at);
        float yaw = to.horizontalDistanceSqr() < 1 ? 180 : (float) (Math.atan2(to.z, to.x) * 180 / Math.PI) - 90;
        player.teleport(new net.minecraft.world.level.portal.TeleportTransition(run.level, at, Vec3.ZERO, yaw, 0, java.util.Set.of(),
                net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
    }

    // ---------------------------------------------------------------- the fight

    abstract String name();

    /** What the boss says when the party arrives. */
    abstract void intro();

    /** The fight starts (5 seconds after arriving). */
    abstract void begin();

    /** Every tick of the fight. */
    abstract void fight(long now);

    /** A mob of this run died (minions, phases). */
    void died(LivingEntity entity) {
    }

    /** A right-click on one of the fight's parts (true when it was one). */
    boolean useEntity(ServerPlayer player, Entity entity) {
        return false;
    }

    /** Whether a hit on this entity counts (false: it's shielded). */
    boolean allowDamage(LivingEntity entity, DamageSource source) {
        return true;
    }

    /** Whether this entity may die (false keeps it alive, for a phase change). */
    boolean allowDeath(LivingEntity entity) {
        return true;
    }

    void tick(long now) {
        if (won) {
            return;
        }
        if (!begun) {
            if (now >= beginAt) {
                begun = true;
                begin();
            }
            return;
        }
        minions.removeIf(id -> {
            Entity e = run.level.getEntity(id);
            return e == null || !e.isAlive();
        });
        updateBar();
        fight(now);
    }

    // ---------------------------------------------------------------- helpers

    void say(String who, String text) {
        run.broadcast(Component.literal("[BOSS] " + who).withStyle(ChatFormatting.RED).append(Component.literal(": " + text).withStyle(ChatFormatting.WHITE)));
    }

    List<ServerPlayer> targets() {
        return run.online().stream().filter(p -> !run.ghosts.contains(p.getUUID())).toList();
    }

    ServerPlayer randomTarget() {
        List<ServerPlayer> targets = targets();
        return targets.isEmpty() ? null : targets.get(run.random.nextInt(targets.size()));
    }

    /** A boss or minion: the entity, Hypixel's name, health and damage, tagged for the run. */
    <T extends Mob> T spawn(EntityType<T> type, Vec3 at, String name, double health, double damage, int level) {
        T mob = type.create(run.level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return null;
        }
        mob.snapTo(at.x, at.y, at.z, run.random.nextFloat() * 360, 0);
        Zones.dressAs(mob, name, level, health, damage, 0);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, 0);
        }
        mob.setPersistenceRequired();
        mob.addTag(DungeonMobs.TAG);
        mob.addTag(run.tag);
        mob.addTag(Zones.FIGHTABLE_TAG);
        run.level.addFreshEntity(mob);
        run.level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 30, 0.5, 1, 0.5, 0.05);
        return mob;
    }

    <T extends Mob> T spawnBoss(EntityType<T> type, Vec3 at, String name, double health, double damage) {
        T mob = spawn(type, at, name, health, damage, 100 + run.floor.number() * 80);
        if (mob != null) {
            mob.addTag(Catacombs.BOSS);
            boss = mob;
            for (ServerPlayer p : run.online()) {
                Msg.playSound(p, SoundEvents.WITHER_SPAWN, 0.5f, 1.4f);
            }
        }
        return mob;
    }

    Mob minion(EntityType<? extends Mob> type, Vec3 at, String name, double health, double damage) {
        Mob mob = spawn(type, at, name, health * minionScale(), damage, 50 + run.floor.number() * 40);
        if (mob != null) {
            minions.add(mob.getUUID());
            if (randomTarget() instanceof ServerPlayer target) {
                mob.setTarget(target);
            }
        }
        return mob;
    }

    /** A hit of the boss's damage times {@code times} on every player within {@code radius} of a spot. */
    void hitAround(Vec3 at, double radius, double times) {
        if (boss == null) {
            return;
        }
        double damage = boss.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * times;
        for (ServerPlayer p : targets()) {
            if (p.position().distanceTo(at) <= radius) {
                p.hurtServer(run.level, run.level.damageSources().mobAttack(boss), (float) damage);
            }
        }
    }

    /** The floor's boss damage (Hypixel's bosses hit about 400 on Floor I, many thousands by Floor VII). */
    double bossDamage() {
        return run.floor.master() ? 12_000 * Math.pow(1.5, run.floor.number()) : 400 * Math.pow(30, run.floor.strength());
    }

    /** A boss's health: Hypixel's normal or Master Mode number. */
    double hp(double normal, double master) {
        return run.floor.master() ? master : normal;
    }

    /** Minions are this much stronger in Master Mode. */
    double minionScale() {
        return run.floor.master() ? 40 : 1;
    }

    void stack(Mob mob, EquipmentSlot slot, ItemStack stack) {
        mob.setItemSlot(slot, stack);
    }

    private void updateBar() {
        if (boss == null || !boss.isAlive() || boss instanceof WitherBoss) {
            if (bar != null) {
                bar.setVisible(false);
            }
            return;
        }
        if (bar == null) {
            bar = new ServerBossEvent(UUID.randomUUID(), boss.getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
        }
        bar.setVisible(true);
        bar.setName(Component.literal(boss.getCustomName() == null ? name() : boss.getCustomName().getString()).withStyle(ChatFormatting.RED));
        bar.setProgress(Math.max(0, Math.min(1, boss.getHealth() / boss.getMaxHealth())));
        List<ServerPlayer> online = run.online();
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
            if (!online.contains(p)) {
                bar.removePlayer(p);
            }
        }
        for (ServerPlayer p : online) {
            if (!bar.getPlayers().contains(p)) {
                bar.addPlayer(p);
            }
        }
    }

    /** The fight is won: the bar goes, the minions go, the run ends. */
    void win(String line) {
        if (won) {
            return;
        }
        won = true;
        if (line != null) {
            say(name(), line);
        }
        if (bar != null) {
            bar.removeAllPlayers();
        }
        for (UUID id : minions) {
            Entity e = run.level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        }
        run.level.getServer().execute(run::finish);
    }

    void close() {
        if (bar != null) {
            bar.removeAllPlayers();
        }
    }

    static double scale() {
        return StatTypes.HEALTH_SCALE;
    }
}
