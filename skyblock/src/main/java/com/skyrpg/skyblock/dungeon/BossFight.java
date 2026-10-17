package com.skyrpg.skyblock.dungeon;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.zone.Zones;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * A floor's boss, in its arena beside the map ({@code structure/catacombs/boss/<boss>.nbt}, or a plain arena until one is
 * built). Floor I is Bonzo: 250,000 health, throws balloons at you and calls his undead; when he falls he gets back up
 * once ("Oh, I'm not done yet!") with his health back and faster. Floors without their own fight yet use the same one
 * with Hypixel's boss health for that floor.
 */
final class BossFight {
    static final int ARENA = 41;
    private static final double[] HEALTH = {0, 250_000, 1_000_000, 3_000_000, 4_000_000, 7_000_000, 40_000_000, 1_000_000_000};
    private static final String[] NAMES = {"The Watcher", "Bonzo", "Scarf", "The Professor", "Thorn", "Livid", "Sadan", "Necron"};

    private final Run run;
    private Mob boss;
    private int phase = 1;
    private long spawnAt;
    private long nextBalloon;
    private long nextMinions;
    private final Set<UUID> minions = new HashSet<>();

    private BossFight(Run run) {
        this.run = run;
    }

    /** The arena: its template if there is one, else a plain stone-brick hall. */
    static void buildArena(Run run) {
        BlockPos o = run.bossOrigin;
        for (int cx = o.getX() >> 4; cx <= (o.getX() + ARENA) >> 4; cx++) {
            for (int cz = o.getZ() >> 4; cz <= (o.getZ() + ARENA) >> 4; cz++) {
                run.level.getChunk(cx, cz);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("skyrpg_skyblock", "catacombs/boss/" + run.floor.boss());
        StructureTemplate template = run.level.getServer().getStructureTemplateManager().get(id).orElse(null);
        if (template != null) {
            template.placeInWorld(run.level, o, o, new StructurePlaceSettings(), run.random, Block.UPDATE_CLIENTS);
            return;
        }
        for (int x = 0; x < ARENA; x++) {
            for (int z = 0; z < ARENA; z++) {
                for (int y = 0; y < 24; y++) {
                    boolean edge = x == 0 || z == 0 || x == ARENA - 1 || z == ARENA - 1;
                    BlockState state = y == 0 ? Blocks.BEDROCK.defaultBlockState()
                            : y <= Run.FLOOR ? ((x + z) % 2 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS : Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS).defaultBlockState()
                            : edge || y == 23 ? Blocks.STONE_BRICKS.defaultBlockState()
                            : y == 22 && x % 6 == 3 && z % 6 == 3 ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    run.level.setBlock(o.offset(x, y, z), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    Vec3 arrival() {
        return Vec3.atBottomCenterOf(run.bossOrigin.offset(ARENA / 2, Run.FLOOR + 1, ARENA - 6));
    }

    Vec3 center() {
        return Vec3.atBottomCenterOf(run.bossOrigin.offset(ARENA / 2, Run.FLOOR + 1, ARENA / 2));
    }

    static BossFight start(Run run) {
        BossFight fight = new BossFight(run);
        fight.spawnAt = run.level.getGameTime() + 100;
        String name = NAMES[Math.min(run.floor.number(), NAMES.length - 1)];
        fight.say(name, run.floor.number() == 1 ? "Gratz for making it this far, but I'm basically unbeatable." : "So you made it this far.");
        return fight;
    }

    void teleportIn(ServerPlayer player) {
        Vec3 at = arrival();
        player.teleport(new net.minecraft.world.level.portal.TeleportTransition(run.level, at, Vec3.ZERO, 180, 0, java.util.Set.of(),
                net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
    }

    private void say(String who, String text) {
        run.broadcast(Component.literal("[BOSS] " + who).withStyle(ChatFormatting.RED).append(Component.literal(": " + text).withStyle(ChatFormatting.WHITE)));
    }

    private String name() {
        return NAMES[Math.min(run.floor.number(), NAMES.length - 1)];
    }

    void tick(long now) {
        if (boss == null) {
            if (now >= spawnAt) {
                spawnBoss();
            }
            return;
        }
        if (!boss.isAlive()) {
            return;
        }
        minions.removeIf(id -> {
            Entity e = run.level.getEntity(id);
            return e == null || !e.isAlive();
        });
        List<ServerPlayer> targets = run.online().stream().filter(p -> !run.ghosts.contains(p.getUUID())).toList();
        if (targets.isEmpty()) {
            return;
        }
        if (boss.getTarget() == null || !boss.getTarget().isAlive()) {
            boss.setTarget(targets.get(run.random.nextInt(targets.size())));
        }
        // Balloons: one at a random player, popping for a hit of the boss's damage nearby.
        if (now >= nextBalloon) {
            nextBalloon = now + (phase == 1 ? 120 : 70);
            ServerPlayer target = targets.get(run.random.nextInt(targets.size()));
            Vec3 at = target.position();
            run.level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1, at.z, 3, 0.8, 0.4, 0.8, 0);
            for (ServerPlayer p : targets) {
                if (p.distanceToSqr(at) < 9) {
                    double damage = boss.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 1.5;
                    p.hurtServer(run.level, run.level.damageSources().mobAttack(boss), (float) damage);
                }
            }
            Msg.playSound(target, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
        }
        if (now >= nextMinions && minions.size() < 4) {
            nextMinions = now + 200;
            for (int i = 0; i < 2; i++) {
                Vec3 at = center().add(run.random.nextInt(11) - 5, 0, run.random.nextInt(11) - 5);
                Mob minion = DungeonMobs.spawn(run.level, at, "zombie", false, run.floor, run.tag);
                if (minion != null) {
                    minions.add(minion.getUUID());
                }
            }
        }
    }

    private void spawnBoss() {
        Zombie zombie = EntityTypes.ZOMBIE.create(run.level, EntitySpawnReason.EVENT);
        if (zombie == null) {
            return;
        }
        Vec3 at = center().add(0, 0, -10);
        zombie.snapTo(at.x, at.y, at.z, 0, 0);
        double health = HEALTH[Math.min(run.floor.number(), HEALTH.length - 1)];
        double damage = 400 * Math.pow(30, run.floor.strength());
        Zones.dressAs(zombie, name(), 50 + run.floor.number() * 50, health, damage, 0);
        zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BLAZE_ROD));
        ItemStack coat = new ItemStack(Items.LEATHER_CHESTPLATE);
        coat.set(DataComponents.DYED_COLOR, new DyedItemColor(0xB02E26));
        zombie.setItemSlot(EquipmentSlot.CHEST, coat);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            zombie.setDropChance(slot, 0);
        }
        zombie.setPersistenceRequired();
        zombie.addTag(DungeonMobs.TAG);
        zombie.addTag(run.tag);
        zombie.addTag(Catacombs.BOSS);
        zombie.addTag(Zones.FIGHTABLE_TAG);
        run.level.addFreshEntity(zombie);
        boss = zombie;
        nextBalloon = run.level.getGameTime() + 80;
        nextMinions = run.level.getGameTime() + 60;
        run.level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 40, 0.5, 1, 0.5, 0.05);
        for (ServerPlayer p : run.online()) {
            Msg.playSound(p, SoundEvents.WITHER_SPAWN, 0.6f, 1.4f);
        }
    }

    /** The boss would die: the first time (Floor I and the stand-ins) he gets back up. False keeps him alive. */
    boolean allowDeath(LivingEntity entity) {
        if (entity != boss) {
            return true;
        }
        if (phase == 1) {
            phase = 2;
            entity.setHealth(entity.getMaxHealth());
            entity.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 600, 1, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0, false, false));
            say(name(), run.floor.number() == 1 ? "Oh, I'm not done yet!" : "That was only the beginning.");
            for (ServerPlayer p : run.online()) {
                Msg.playSound(p, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.8f, 1.2f);
            }
            return false;
        }
        say(name(), run.floor.number() == 1 ? "Alright, maybe I'm just weak after all.." : "Impossible...");
        minions.forEach(id -> {
            Entity e = run.level.getEntity(id);
            if (e != null) {
                e.discard();
            }
        });
        run.level.getServer().execute(run::finish);
        return true;
    }
}
