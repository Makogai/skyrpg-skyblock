package com.skyrpg.skyblock.hollows;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.skyblock.pet.SkyBlockPets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Hollows' two bosses. Boss Corleone (Team Treasurite's leader) waits in the Mines of Divan's central chamber and comes
 * back 90 seconds after he falls; he throws a ball that becomes a Smog, a small Wither that blows up after a few seconds.
 * Bal fills Khazad-dum's arena with his lava: he spawns a minute after the last one while somebody is in the hall, hops
 * after the nearest player, spits a fireball each hop, and every hit you land might shake loose a Flawed gem, a Yoggie or
 * his pet and might call his Fire Bats and Bald Blazes. Whoever did at least half a percent of his health gets the Topaz
 * Crystal when he falls.
 *
 * <p>Decided: Hypixel's health numbers for them aren't on the wiki mirror, so Corleone has 1,000,000 health and 4,000
 * damage (the wiki's) and Bal 10,000,000 health and 4,000 damage at level 263. Bal's drops are rolled per hit by the
 * wiki's chances (they fall on the ground on Hypixel and collect themselves) and go straight into your pack. Corleone's
 * "Rough Ruby 15/12" and "Rough Jade 18/14/9" rows are separate rolls at 100/75 and 100/75/60 percent.
 */
public final class HollowsBosses {
    private static final String TAG = "skyrpg_hollows_boss";
    private static final String MINION = "skyrpg_hollows_minion";
    private static final double SHARE = 0.005;

    private record Boss(String name, EntityType<? extends Mob> type, int level, double health, double damage, double xp, long coins, Vec3 spot,
            int respawn, double wake, double leash) {
    }

    private static final Boss CORLEONE = new Boss("Boss Corleone", EntityTypes.VINDICATOR, 195, 1_000_000, 4_000, 292, 65,
            new Vec3(150.5, 101, -140.5), 90 * 20, 40, 28);
    private static final Boss BAL = new Boss("Bal", EntityTypes.MAGMA_CUBE, 263, 10_000_000, 4_000, 1_972, 657, new Vec3(0.5, 25, 160.5),
            60 * 20, 60, 20);
    private static final List<Boss> BOSSES = List.of(CORLEONE, BAL);

    private static final class Live {
        Mob mob;
        long nextSpawn;
        long nextMove;
        final Map<UUID, Double> damage = new HashMap<>();
        final Map<UUID, Long> lastRoll = new HashMap<>();
        final Set<UUID> greeted = new HashSet<>();
        ServerBossEvent bar;
    }

    /** A Smog, and when it blows. */
    private record Smog(Mob mob, long blows) {
    }

    private static final Map<String, Live> LIVE = new HashMap<>();
    private static final List<Smog> SMOGS = new ArrayList<>();

    private HollowsBosses() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(HollowsBosses::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity.entityTags().contains(TAG) && source.getEntity() instanceof ServerPlayer player && entity instanceof Mob mob) {
                Live live = live(mob);
                if (live != null) {
                    live.damage.merge(player.getUUID(), (double) taken, Double::sum);
                    if (mob.getCustomName() != null && mob.getCustomName().getString().equals(BAL.name())) {
                        balHit(live, mob, player);
                    }
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.entityTags().contains(TAG) && entity instanceof Mob mob) {
                died(mob);
            }
        });
        // A boss left over from before a restart is gone; its timer starts over (checked a tick later: a fresh one is known by then).
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG)) {
                level.getServer().execute(() -> {
                    if (live(entity) == null) {
                        entity.discard();
                    }
                });
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
            SMOGS.forEach(s -> s.mob().discard());
            LIVE.clear();
            SMOGS.clear();
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

    /** Admin: spawns the boss now (Corleone or Bal). Returns false if the name is unknown. */
    static boolean spawnNow(MinecraftServer server, String name) {
        ServerLevel level = server.getLevel(CrystalHollows.LEVEL);
        Boss boss = BOSSES.stream().filter(b -> b.name().toLowerCase().contains(name.toLowerCase())).findFirst().orElse(null);
        if (boss == null || level == null) {
            return false;
        }
        Live live = LIVE.computeIfAbsent(boss.name(), k -> new Live());
        if (live.mob == null) {
            spawn(level, boss, live);
        }
        return live.mob != null;
    }

    private static boolean anyoneNear(ServerLevel level, Vec3 pos, double radius) {
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && player.position().distanceTo(pos) < radius) {
                return true;
            }
        }
        return false;
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(CrystalHollows.LEVEL);
        if (level == null) {
            return;
        }
        long now = server.getTickCount();
        for (Boss boss : BOSSES) {
            Live live = LIVE.computeIfAbsent(boss.name(), k -> new Live());
            if (live.mob != null && (!live.mob.isAlive() || live.mob.isRemoved() || level.getEntity(live.mob.getUUID()) != live.mob)) {
                if (live.mob.getRemovalReason() != Entity.RemovalReason.KILLED) {
                    live.nextSpawn = now + 200;
                }
                clear(live);
            }
            if (live.mob == null) {
                if (now % 20 == 0 && now >= live.nextSpawn && anyoneNear(level, boss.spot(), boss.wake())
                        && level.hasChunkAt(net.minecraft.core.BlockPos.containing(boss.spot()))) {
                    spawn(level, boss, live);
                }
                continue;
            }
            Mob mob = live.mob;
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
            if (now % 20 == 0 && mob.position().distanceTo(boss.spot()) > boss.leash()) {
                mob.teleportTo(boss.spot().x, boss.spot().y, boss.spot().z);
                mob.setTarget(null);
            }
            if (now >= live.nextMove) {
                live.nextMove = now + (boss == BAL ? 50 : 100) + mob.getRandom().nextInt(40);
                move(level, boss, mob);
            }
        }
        if (now % 100 == 0) {
            // Minions nobody owns any more (their boss is gone, or they came back from disk) leave.
            boolean bal = LIVE.containsKey(BAL.name()) && LIVE.get(BAL.name()).mob != null;
            for (Mob minion : level.getEntities(EntityTypes.BLAZE, b -> b.entityTags().contains(MINION))) {
                if (!bal) {
                    minion.discard();
                }
            }
            for (Mob smog : level.getEntities(EntityTypes.WITHER_SKELETON, w -> w.entityTags().contains(MINION))) {
                if (SMOGS.stream().noneMatch(s -> s.mob() == smog)) {
                    smog.discard();
                }
            }
        }
        for (Smog smog : List.copyOf(SMOGS)) {
            if (smog.mob().isRemoved() || !smog.mob().isAlive()) {
                SMOGS.remove(smog);
            } else if (now >= smog.blows()) {
                SMOGS.remove(smog);
                Mob mob = smog.mob();
                level.sendParticles(ParticleTypes.EXPLOSION, mob.getX(), mob.getY() + 1, mob.getZ(), 4, 1, 0.5, 1, 0);
                level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 1.0f);
                hitAround(level, mob, 4, 1_500);
                mob.discard();
            }
        }
    }

    private static void spawn(ServerLevel level, Boss boss, Live live) {
        Mob mob = HollowsMobs.spawn(level, boss.type(), boss.spot(), boss.name(), boss.level(), boss.health(), boss.damage(), boss.xp(), TAG);
        if (mob == null) {
            return;
        }
        if (mob instanceof MagmaCube cube) {
            cube.setSize(8, true);
        }
        live.mob = mob;
        live.damage.clear();
        live.greeted.clear();
        live.nextMove = level.getServer().getTickCount() + 100;
        live.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(boss.name()).withStyle(ChatFormatting.RED),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(boss.spot()) < 80) {
                player.sendSystemMessage(boss == BAL ? Component.literal("The ground starts to shake and the lava bubbles...")
                        .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC)
                        : Component.literal("Boss Corleone has appeared!").withStyle(ChatFormatting.RED));
            }
        }
        level.playSound(null, mob.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 1.2f);
    }

    private static void clear(Live live) {
        live.mob = null;
        if (live.bar != null) {
            live.bar.removeAllPlayers();
            live.bar = null;
        }
    }

    private static ServerPlayer nearest(ServerLevel level, Mob mob, double radius) {
        ServerPlayer best = null;
        for (ServerPlayer player : level.players()) {
            if (!player.isCreative() && !player.isSpectator() && player.isAlive() && player.distanceTo(mob) < radius
                    && (best == null || player.distanceTo(mob) < best.distanceTo(mob))) {
                best = player;
            }
        }
        return best;
    }

    private static void move(ServerLevel level, Boss boss, Mob mob) {
        ServerPlayer target = nearest(level, mob, 24);
        if (target == null) {
            return;
        }
        if (boss == CORLEONE) {
            SMOGS.removeIf(s -> s.mob().isRemoved());
            if (SMOGS.size() >= 2) {
                return;
            }
            level.sendParticles(ParticleTypes.SMOKE, mob.getX(), mob.getY() + 1.5, mob.getZ(), 20, 0.3, 0.3, 0.3, 0.02);
            Mob smog = HollowsMobs.spawn(level, EntityTypes.WITHER_SKELETON, mob.position().add(0, 0.5, 0), "Smog", 100, 10_000, 1_500, 0, MINION);
            if (smog != null) {
                smog.setTarget(target);
                smog.getAttribute(Attributes.SCALE).setBaseValue(0.5);
                SMOGS.add(new Smog(smog, level.getServer().getTickCount() + 120));
            }
            return;
        }
        // Bal: a hop towards the nearest player and a fireball along the way.
        Vec3 away = target.position().subtract(mob.position());
        Vec3 dir = new Vec3(away.x, 0, away.z).normalize();
        mob.setDeltaMovement(dir.x * 0.6, 0.7, dir.z * 0.6);
        Vec3 from = mob.position().add(0, 2, 0);
        Vec3 to = target.position().add(0, 1, 0);
        for (int i = 1; i <= 10; i++) {
            Vec3 p = from.lerp(to, i / 10.0);
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.01);
        }
        level.playSound(null, mob.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0f, 0.6f);
        target.hurtServer(level, level.damageSources().mobAttack(mob), (float) (mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.3));
        target.igniteForSeconds(2);
    }

    private static void hitAround(ServerLevel level, Mob mob, double radius, double damage) {
        for (ServerPlayer player : level.players()) {
            if (!player.isCreative() && !player.isSpectator() && player.distanceTo(mob) <= radius) {
                player.hurtServer(level, level.damageSources().mobAttack(mob), (float) damage);
            }
        }
    }

    /** A hit on Bal: his first-hit taunt, drops by the wiki's chances, and now and then minions. */
    private static void balHit(Live live, Mob bal, ServerPlayer player) {
        ServerLevel level = (ServerLevel) bal.level();
        long now = level.getServer().getTickCount();
        if (live.greeted.add(player.getUUID())) {
            player.sendSystemMessage(Component.literal("[BOSS] Bal: ").withStyle(ChatFormatting.RED)
                    .append(Component.literal("You cannot best me!").withStyle(ChatFormatting.WHITE)));
        }
        if (now < live.lastRoll.getOrDefault(player.getUUID(), 0L)) {
            return;
        }
        live.lastRoll.put(player.getUUID(), now + 6);
        double roll = player.getRandom().nextDouble() * 100;
        String got = null;
        if (roll < 0.0054) {
            got = "pet:LEGENDARY";
        } else if (roll < 0.018) {
            got = "pet:EPIC";
        } else if (roll < 5.018) {
            got = "FLAWED_RUBY_GEM";
        } else if (roll < 10.018) {
            got = "FLAWED_TOPAZ_GEM";
        } else if (roll < 14.518) {
            got = "YOGGIE";
        }
        if (got != null) {
            ItemStack stack = got.startsWith("pet:") ? (SkyBlockPets.known("BAL") ? SkyBlockPets.create("BAL", got.substring(4), 0) : ItemStack.EMPTY)
                    : HollowsState.stack(got, 1);
            if (!stack.isEmpty()) {
                if (got.startsWith("pet:")) {
                    player.sendSystemMessage(Component.literal("RARE DROP! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(stack.getHoverName()));
                }
                HollowsState.give(player, stack, ItemGains.Source.COMBAT);
            }
        }
        if (player.getRandom().nextInt(25) == 0 && level.getEntities(EntityTypes.BLAZE, b -> b.entityTags().contains(MINION)).size() < 5) {
            player.sendSystemMessage(Component.literal("[BOSS] Bal: ").withStyle(ChatFormatting.RED)
                    .append(Component.literal("Bah! Come forth my minions! Fly above, take them out!").withStyle(ChatFormatting.WHITE)));
            for (int i = 0; i < 2; i++) {
                Vec3 at = bal.position().add(player.getRandom().nextDouble() * 6 - 3, 3, player.getRandom().nextDouble() * 6 - 3);
                Mob minion = HollowsMobs.spawn(level, EntityTypes.BLAZE, at, "Bald Blaze", 70, 20_000, 1_200, 90, MINION);
                if (minion != null) {
                    minion.setTarget(player);
                }
            }
        }
    }

    private static void died(Mob mob) {
        Live live = live(mob);
        if (live == null) {
            return;
        }
        Boss boss = mob.getCustomName() != null && mob.getCustomName().getString().equals(BAL.name()) ? BAL : CORLEONE;
        ServerLevel level = (ServerLevel) mob.level();
        live.nextSpawn = level.getServer().getTickCount() + boss.respawn();
        clear(live);
        for (Mob minion : level.getEntities(EntityTypes.BLAZE, b -> b.entityTags().contains(MINION))) {
            minion.discard();
        }
        double needed = mob.getMaxHealth() * SHARE;
        for (Map.Entry<UUID, Double> e : live.damage.entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (player == null || e.getValue() < needed) {
                continue;
            }
            Wallet.give(player, boss.coins());
            SkillLevels.giveXp(player, SkillLevels.COMBAT, boss.xp());
            Notice.Panel panel = Notice.of(Notice.Kind.COMBAT).tag("BOSS").panel()
                    .line(Component.literal(" +" + Msg.number(boss.coins()) + " coins").withStyle(ChatFormatting.GOLD));
            if (boss == CORLEONE) {
                corleoneDrops(player, panel);
            } else if (Crystals.give(player, Crystals.Gem.TOPAZ)) {
                panel.line(Component.literal(" Topaz Crystal").withColor(Crystals.Gem.TOPAZ.color));
            }
            panel.send(player, Component.literal(boss.name() + " is down!").withStyle(ChatFormatting.RED));
        }
    }

    private static void corleoneDrops(ServerPlayer player, Notice.Panel panel) {
        var random = player.getRandom();
        int ruby = 15 + (random.nextDouble() < 0.75 ? 12 : 0);
        int jade = 18 + (random.nextDouble() < 0.75 ? 14 : 0) + (random.nextDouble() < 0.6 ? 9 : 0);
        HollowsState.give(player, "ROUGH_RUBY_GEM", ruby, ItemGains.Source.COMBAT);
        HollowsState.give(player, "ROUGH_JADE_GEM", jade, ItemGains.Source.COMBAT);
        panel.line(Component.literal(" " + ruby + "x Rough Ruby Gemstone, " + jade + "x Rough Jade Gemstone").withStyle(ChatFormatting.GRAY));
        if (random.nextDouble() < 0.25) {
            HollowsState.give(player, "CORLEONITE", 1, ItemGains.Source.COMBAT);
            panel.line(Component.literal(" Corleonite").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
