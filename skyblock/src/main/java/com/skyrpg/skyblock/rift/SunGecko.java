package com.skyrpg.skyblock.rift;

import com.mojang.serialization.Codec;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.essence.Essence;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The Sun Gecko in the Time Chamber (Hypixel's): Oruo the Almighty lets you in for 5,000 Motes (Rift Time runs double
 * inside). It has 250 health and fights with Instant Transmission (it appears beside you), Perfect Volley (you're thrown
 * skyward), Extreme Speed (three untouchable clones to fool your aim) and the Spirit Beam (a beam from above takes a second of Rift
 * Time). In here you have Hearts. Your combo is everything: hit it again before the timer runs out to keep it, every
 * few hits raise it (and your damage); a big enough combo stops the Volley throwing you, and losing a combo costs 4
 * Hearts. A win is 5 Sun Gecko Essence; after two, Oruo opens the seven modifiers (each +5 essence) and the essence shop.
 */
final class SunGecko {
    static final String TAG = "skyrpg_sun_gecko";
    static final String SOUL = "npc:Sun Gecko";
    static final String ESSENCE = "SUN_GECKO";
    private static final double HEALTH = 250;
    private static final long PRICE = 5_000;
    private static final double BASE_HEARTS = 10;
    static final List<String[]> MODS = List.of(
            new String[] {"Revival", "It comes back to life once. Shurikens are left round the chamber."},
            new String[] {"Combo Maniac", "Raising your combo stuns it for a moment."},
            new String[] {"Time Sliced", "Three minutes, then it hurts more and more. The first 30 s need a hit less."},
            new String[] {"Buffantics", "Hazards to dodge and buffs that keep your combo going."},
            new String[] {"Collective", "-1 max Heart per modifier, +0.2 s combo timer per modifier."},
            new String[] {"Brand New Dance", "Its attacks are faster and harder, and a Wyld Nuke joins in."},
            new String[] {"Culmination", "Nothing heals you; combos need a hit less."});

    /** wins, blessing (0-3), mania (Combo Mania bought), mods (the modifiers on, a bit each). */
    static final AttachmentType<Map<String, Integer>> DATA = AttachmentRegistry.create(SkyRpgSkyblock.id("rift_sun_gecko"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    private static final class Fight {
        UUID player;
        Mob gecko;
        ServerBossEvent bar;
        double hp = HEALTH;
        int mods;
        boolean revived;
        int hits;
        int combo;
        long lastHit;
        long started;
        double hearts;
        double maxHearts;
        long nextAttack;
        long nextHop;
        String pending = "";
        long pendingAt;
        Vec3 beamAt;
        final List<Mob> clones = new ArrayList<>();
        long clonesUntil;
        long clonesHit;
        Interaction nuke;
        int nukeHits;
        long nukeUntil;
        Vec3 hazard;
        long hazardAt;
        Vec3 buff;
        int sliced;

        boolean on(int mod) {
            return (mods >> mod & 1) == 1;
        }
    }

    private static Fight fight;

    private SunGecko() {
    }

    static void init() {
        Identifier first = SkyRpgSkyblock.id("rift_first");
        AttackEntityCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(first, (player, level, hand, entity, hit) -> {
            if (!Rift.isRift(level)) {
                return InteractionResult.PASS;
            }
            boolean ours = entity.getCustomName() != null && entity.getCustomName().getString().startsWith("Sun Gecko")
                    || entity instanceof Interaction && level.isClientSide() == false && fight != null && entity == fight.nuke;
            if (level.isClientSide()) {
                // The Time Chamber has no zone mobs, so the Hub's protection would turn the hit away on the client.
                return entity.getCustomName() != null && entity.getCustomName().getString().startsWith("Sun Gecko") || entity instanceof Interaction
                        ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            if (!ours || !(player instanceof ServerPlayer server) || fight == null || !server.getUUID().equals(fight.player)) {
                return ours ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            if (entity == fight.gecko) {
                hit(server, (ServerLevel) level);
            } else if (entity == fight.nuke) {
                fight.nukeHits++;
                Msg.playSound(server, SoundEvents.ANVIL_LAND, 0.5f, 1.5f + fight.nukeHits * 0.2f);
                if (fight.nukeHits >= 3) {
                    server.sendOverlayMessage(Component.literal("Wyld Nuke disarmed!").withStyle(ChatFormatting.GREEN));
                    fight.nuke.discard();
                    fight.nuke = null;
                }
            }
            return InteractionResult.SUCCESS;
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && (fight == null || entity != fight.gecko && !fight.clones.contains(entity) && entity != fight.nuke)) {
                entity.discard();
            }
        });
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Blessing of Time";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                int tier = player.getAttachedOrElse(DATA, Map.of()).getOrDefault("blessing", 0);
                if (tier > 0 && Rift.isRift(player.level())) {
                    out.add(StatTypes.SPEED, tier);
                    out.add(StatTypes.RIFT_TIME, 30.0 * tier);
                    out.add(StatTypes.RIFT_MANA_REGEN, tier);
                    out.add(StatTypes.STRENGTH, 2.0 * tier);
                    out.add(StatTypes.HEALTH, 5.0 * tier);
                    out.add(StatTypes.INTELLIGENCE, 2.0 * tier);
                    out.add(StatTypes.DEFENSE, 2.0 * tier);
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(SunGecko::tick);
    }

    private static int data(ServerPlayer player, String key) {
        return player.getAttachedOrElse(DATA, Map.of()).getOrDefault(key, 0);
    }

    private static void set(ServerPlayer player, String key, int value) {
        Map<String, Integer> map = new HashMap<>(player.getAttachedOrElse(DATA, Map.of()));
        map.put(key, value);
        player.setAttached(DATA, Map.copyOf(map));
    }

    private static int modCount(int mods) {
        return Integer.bitCount(mods);
    }

    // ---------------------------------------------------------------- the fight

    private static void start(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Rift.Spot door = Rift.spots().get("time_chamber");
        Rift.Spot boss = Rift.spots().get("sun_gecko");
        if (door == null || boss == null) {
            return;
        }
        Mob gecko = EntityTypes.AXOLOTL.create(level, EntitySpawnReason.EVENT);
        if (gecko == null) {
            return;
        }
        Fight f = new Fight();
        f.player = player.getUUID();
        f.mods = data(player, "wins") >= 2 ? data(player, "mods") : 0;
        f.maxHearts = BASE_HEARTS - (f.on(4) ? modCount(f.mods) : 0);
        f.hearts = f.maxHearts;
        long now = level.getServer().getTickCount();
        f.started = now;
        f.nextAttack = now + 80;
        f.nextHop = now + 60;
        gecko.setComponent(net.minecraft.core.component.DataComponents.AXOLOTL_VARIANT,
                net.minecraft.world.entity.animal.axolotl.Axolotl.Variant.GOLD);
        gecko.getAttribute(Attributes.SCALE).setBaseValue(2.4);
        gecko.setNoAi(true);
        gecko.setNoGravity(true);
        gecko.setPermanentlyInvulnerable(true);
        gecko.setPersistenceRequired();
        gecko.setCustomName(Component.literal("Sun Gecko").withStyle(ChatFormatting.GOLD));
        gecko.setCustomNameVisible(true);
        gecko.addTag(TAG);
        gecko.addTag(Zones.FIGHTABLE_TAG);
        gecko.snapTo(boss.pos().x, boss.pos().y + 0.2, boss.pos().z, 180, 0);
        f.gecko = gecko;
        f.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("Sun Gecko").withStyle(ChatFormatting.GOLD), BossEvent.BossBarColor.YELLOW,
                BossEvent.BossBarOverlay.NOTCHED_10);
        f.bar.addPlayer(player);
        fight = f;
        level.addFreshEntity(gecko);
        player.teleportTo(level, door.pos().x, door.pos().y, door.pos().z, Set.of(), door.yaw(), 0, true);
        if (f.on(0)) {
            give(player, "FAKE_SHURIKEN", 8);
        }
        Notice.of(Notice.Kind.WARN).tag("TIME CHAMBER").panel()
                .line(Component.literal(" Keep your combo going: every hit resets its timer. Lose it and it costs 4 ❤.").withStyle(ChatFormatting.GRAY))
                .line(Component.literal(" Rift Time runs twice as fast in here.").withStyle(ChatFormatting.GRAY))
                .send(player, Component.literal("The Sun Gecko wakes up!").withStyle(ChatFormatting.GOLD));
        Msg.playSound(player, SoundEvents.BLAZE_SHOOT, 1f, 0.6f);
    }

    private static double comboTimer(ServerPlayer player, Fight f) {
        return 2.5 + 0.5 * data(player, "mania") + (f.on(4) ? 0.2 * modCount(f.mods) : 0);
    }

    private static int hitsPerLevel(Fight f, long now) {
        int need = 3;
        if (f.on(6) || f.on(2) && now - f.started < 30 * 20) {
            need--;
        }
        return Math.max(1, need);
    }

    private static void hit(ServerPlayer player, ServerLevel level) {
        Fight f = fight;
        long now = level.getServer().getTickCount();
        boolean kept = now - f.lastHit <= comboTimer(player, f) * 20;
        f.hits = kept ? f.hits + 1 : 1;
        f.lastHit = now;
        int before = f.combo;
        f.combo = f.hits / hitsPerLevel(f, now);
        if (f.combo > before) {
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 0.8f + Math.min(1.2f, f.combo * 0.1f));
            if (f.on(1)) {
                f.nextAttack = Math.max(f.nextAttack, now + 14);
                level.sendParticles(ParticleTypes.CRIT, f.gecko.getX(), f.gecko.getY() + 1.5, f.gecko.getZ(), 12, 0.6, 0.4, 0.6, 0.1);
            }
        }
        double damage = Rift.damage(player) * (1 + 0.25 * f.combo);
        f.hp -= damage;
        level.sendParticles(ParticleTypes.FLAME, f.gecko.getX(), f.gecko.getY() + 1, f.gecko.getZ(), 6, 0.5, 0.4, 0.5, 0.02);
        Msg.playSound(player, SoundEvents.AXOLOTL_HURT, 1f, 0.7f);
        if (f.hp > 0) {
            return;
        }
        if (f.on(0) && !f.revived) {
            f.revived = true;
            f.hp = HEALTH;
            player.sendSystemMessage(Component.literal("The Sun Gecko revives! Your combo holds.").withStyle(ChatFormatting.GOLD));
            Msg.playSound(player, SoundEvents.TOTEM_USE, 0.8f, 1.2f);
            return;
        }
        win(player);
    }

    private static void tick(MinecraftServer server) {
        Fight f = fight;
        if (f == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(f.player);
        ServerLevel level = Rift.level(server);
        Zones.Zone zone = player == null ? null : Zones.at(player.level(), player.blockPosition());
        if (player == null || level == null || player.level() != level || zone == null || !zone.name().equals("Time Chamber") || !f.gecko.isAlive()) {
            end(player, false, "You left the Time Chamber.");
            return;
        }
        long now = server.getTickCount();
        f.bar.setProgress((float) Math.max(0, f.hp / HEALTH));
        f.bar.setName(Component.literal("Sun Gecko  ").withStyle(ChatFormatting.GOLD).append(Component.literal(Math.max(0, Math.round(f.hp)) + "/"
                + (int) HEALTH + "❤").withStyle(ChatFormatting.RED)));
        double timer = comboTimer(player, f);
        if (f.combo > 0 && now - f.lastHit > timer * 20) {
            f.combo = 0;
            f.hits = 0;
            hurt(player, 4, "COMBO LOST");
        }
        if (now % 2 == 0) {
            double left = f.combo > 0 || f.hits > 0 ? Math.max(0, timer - (now - f.lastHit) / 20.0) : 0;
            player.sendOverlayMessage(Component.literal("❤ " + fmt(f.hearts) + "/" + fmt(f.maxHearts) + "  ").withStyle(ChatFormatting.RED)
                    .append(Component.literal("Combo x" + f.combo).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(left > 0 ? "  (" + String.format("%.1f", left) + "s)" : "").withStyle(ChatFormatting.GRAY)));
        }
        if (f.hearts <= 0) {
            end(player, false, "You ran out of Hearts!");
            return;
        }
        Vec3 home = Rift.spots().get("sun_gecko").pos();
        boolean dance = f.on(5);
        // its attacks
        if (!f.pending.isEmpty() && now >= f.pendingAt) {
            resolve(player, level, f, dance);
        }
        if (f.pending.isEmpty() && now >= f.nextAttack) {
            List<String> attacks = new ArrayList<>(List.of("transmission", "volley", "speed", "beam"));
            if (dance && f.nuke == null) {
                attacks.add("nuke");
            }
            String attack = attacks.get(level.getRandom().nextInt(attacks.size()));
            f.nextAttack = now + (dance ? 50 : 80);
            begin(player, level, f, attack, now, home);
        }
        if (f.pending.equals("beam") && f.beamAt != null && now % 3 == 0) {
            for (int y = 0; y < 12; y += 2) {
                level.sendParticles(player, new DustParticleOptions(0xFFDD55, 1.4f), true, false, f.beamAt.x, f.beamAt.y + y, f.beamAt.z, 2, 0.6, 0.2, 0.6, 0);
            }
        }
        // the clones
        if (!f.clones.isEmpty()) {
            for (Mob clone : f.clones) {
                Vec3 to = player.position().subtract(clone.position());
                clone.snapTo(clone.position().add(to.normalize().scale(dance ? 0.35 : 0.25)), clone.getYRot(), 0);
                // Hypixel's clones only hurt with Brand New Dance; otherwise they're there to fool your aim
                if (dance && to.length() < 1.3 && now - f.clonesHit > 15) {
                    f.clonesHit = now;
                    hurt(player, 1, "Extreme Speed");
                }
            }
            if (now > f.clonesUntil) {
                f.clones.forEach(Entity::discard);
                f.clones.clear();
            }
        }
        // the nuke
        if (f.nuke != null) {
            level.sendParticles(ParticleTypes.SMOKE, f.nuke.getX(), f.nuke.getY() + 0.8, f.nuke.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
            if (now > f.nukeUntil) {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, f.nuke.getX(), f.nuke.getY(), f.nuke.getZ(), 1, 0, 0, 0, 0);
                f.nuke.discard();
                f.nuke = null;
                hurt(player, 5, "Wyld Nuke");
            }
        }
        // it hops round the chamber
        if (now >= f.nextHop && f.pending.isEmpty()) {
            f.nextHop = now + 60 + level.getRandom().nextInt(40);
            double a = level.getRandom().nextDouble() * Math.PI * 2;
            double r = 2 + level.getRandom().nextDouble() * 6;
            move(level, f, home.add(Math.cos(a) * r, 0, Math.sin(a) * r));
        }
        f.gecko.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
        // Buffantics: a hazard to dodge, a buff to grab
        if (f.on(3)) {
            if (f.hazard == null && now % 160 == 0) {
                f.hazard = player.position();
                f.hazardAt = now + 30;
            }
            if (f.hazard != null) {
                if (now % 4 == 0) {
                    level.sendParticles(player, new DustParticleOptions(0xFF3030, 1.2f), true, false, f.hazard.x, f.hazard.y + 0.1, f.hazard.z, 12, 1.2, 0, 1.2, 0);
                }
                if (now >= f.hazardAt) {
                    if (player.position().distanceTo(f.hazard) < 2.2) {
                        hurt(player, 1, "Hazard");
                    }
                    f.hazard = null;
                }
            }
            if (f.buff == null && now % 200 == 100) {
                double a = level.getRandom().nextDouble() * Math.PI * 2;
                f.buff = home.add(Math.cos(a) * 6, 0.5, Math.sin(a) * 6);
            }
            if (f.buff != null) {
                if (now % 4 == 0) {
                    level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, f.buff.x, f.buff.y, f.buff.z, 4, 0.2, 0.3, 0.2, 0);
                }
                if (player.position().distanceTo(f.buff) < 1.5) {
                    f.buff = null;
                    f.lastHit = now;
                    player.sendOverlayMessage(Component.literal("Combo buff! The timer is reset.").withStyle(ChatFormatting.GREEN));
                    Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
                }
            }
        }
        // Time Sliced: three minutes, then it hurts more and more
        if (f.on(2) && now - f.started > 3 * 60 * 20 && (now - f.started) % 100 == 0) {
            f.sliced++;
            hurt(player, f.sliced, "Time Sliced");
        }
    }

    private static void begin(ServerPlayer player, ServerLevel level, Fight f, String attack, long now, Vec3 home) {
        switch (attack) {
            case "transmission" -> {
                Vec3 look = player.getLookAngle();
                move(level, f, player.position().add(-look.x * 2, 0, -look.z * 2));
                f.pending = "transmission";
                f.pendingAt = now + 10;
                say(player, "Instant Transmission!");
            }
            case "volley" -> {
                f.pending = "volley";
                f.pendingAt = now + 15;
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 15, 0.6, 0.1, 0.6, 0.02);
                say(player, "Perfect Volley!");
            }
            case "speed" -> {
                for (int k = 0; k < 3; k++) {
                    Mob clone = EntityTypes.AXOLOTL.create(level, EntitySpawnReason.EVENT);
                    if (clone == null) {
                        continue;
                    }
                    clone.setComponent(net.minecraft.core.component.DataComponents.AXOLOTL_VARIANT,
                            net.minecraft.world.entity.animal.axolotl.Axolotl.Variant.GOLD);
                    clone.getAttribute(Attributes.SCALE).setBaseValue(1.4);
                    clone.setNoAi(true);
                    clone.setPermanentlyInvulnerable(true);
                    clone.setGlowingTag(true);
                    clone.setCustomName(Component.literal("Sun Gecko (clone)").withStyle(ChatFormatting.YELLOW));
                    clone.addTag(TAG);
                    double a = k * Math.PI * 2 / 3;
                    clone.snapTo(home.x + Math.cos(a) * 5, home.y, home.z + Math.sin(a) * 5, 0, 0);
                    f.clones.add(clone);
                    level.addFreshEntity(clone);
                }
                f.clonesUntil = now + 100;
                say(player, "Extreme Speed!");
            }
            case "beam" -> {
                move(level, f, home.add(0, 6, 0));
                f.beamAt = player.position();
                f.pending = "beam";
                f.pendingAt = now + 30;
                say(player, "Spirit Beam!");
            }
            default -> {
                Interaction nuke = EntityTypes.INTERACTION.create(level, EntitySpawnReason.EVENT);
                if (nuke == null) {
                    return;
                }
                nuke.addTag(TAG);
                Vec3 at = player.position().add(player.getLookAngle().multiply(2, 0, 2));
                nuke.snapTo(at.x, at.y, at.z, 0, 0);
                f.nuke = nuke;
                f.nukeHits = 0;
                f.nukeUntil = now + 100;
                level.addFreshEntity(nuke);
                say(player, "Wyld Nuke! Hit it three times!");
            }
        }
        Msg.playSound(player, SoundEvents.BLAZE_AMBIENT, 1f, 1.4f);
    }

    private static void resolve(ServerPlayer player, ServerLevel level, Fight f, boolean dance) {
        switch (f.pending) {
            case "transmission" -> {
                if (player.position().distanceTo(f.gecko.position()) < 3.5) {
                    hurt(player, dance ? 2 : 1, "Instant Transmission");
                }
            }
            case "volley" -> {
                if (f.combo >= 3) {
                    player.sendOverlayMessage(Component.literal("Your combo keeps you on your feet!").withStyle(ChatFormatting.GREEN));
                } else {
                    double a = level.getRandom().nextDouble() * Math.PI * 2;
                    player.setDeltaMovement(Math.cos(a) * 0.6, 1.3, Math.sin(a) * 0.6);
                    player.syncVelocity = true;
                    if (dance) {
                        hurt(player, 1, "Perfect Volley");
                    }
                }
            }
            case "beam" -> {
                level.sendParticles(ParticleTypes.END_ROD, f.beamAt.x, f.beamAt.y + 0.5, f.beamAt.z, 40, 0.5, 3, 0.5, 0.05);
                double dx = player.getX() - f.beamAt.x;
                double dz = player.getZ() - f.beamAt.z;
                if (dx * dx + dz * dz < 1.8 * 1.8) {
                    Rift.takeTime(player, 1);
                    player.sendOverlayMessage(Component.literal("Spirit Beam! -1s ф Rift Time").withStyle(ChatFormatting.RED));
                    if (dance) {
                        hurt(player, 1, "Spirit Beam");
                    }
                }
                f.beamAt = null;
            }
            default -> {
            }
        }
        f.pending = "";
    }

    private static void move(ServerLevel level, Fight f, Vec3 to) {
        level.sendParticles(ParticleTypes.FLAME, f.gecko.getX(), f.gecko.getY() + 1, f.gecko.getZ(), 15, 0.6, 0.6, 0.6, 0.05);
        f.gecko.snapTo(to.x, to.y + 0.2, to.z, f.gecko.getYRot(), 0);
        level.sendParticles(ParticleTypes.FLAME, to.x, to.y + 1, to.z, 15, 0.6, 0.6, 0.6, 0.05);
    }

    private static void hurt(ServerPlayer player, double hearts, String what) {
        Fight f = fight;
        f.hearts -= hearts;
        player.sendSystemMessage(Component.literal(what + "! -" + fmt(hearts) + " ❤").withStyle(ChatFormatting.RED));
        Msg.playSound(player, SoundEvents.PLAYER_HURT, 1f, 1f);
    }

    private static String fmt(double v) {
        return v % 1 == 0 ? String.valueOf((int) v) : String.format("%.1f", v);
    }

    private static void win(ServerPlayer player) {
        Fight f = fight;
        int mods = modCount(f.mods);
        int essence = 5 + 5 * mods;
        Essence.give(player, ESSENCE, essence);
        int wins = data(player, "wins") + 1;
        set(player, "wins", wins);
        set(player, "xp", data(player, "xp") + 50 + 50 * mods);
        var panel = Notice.of(Notice.Kind.SUCCESS).tag("SUN GECKO").panel()
                .line(Component.literal(" +" + essence + " Sun Gecko Essence  ·  +" + (50 + 50 * mods) + " Sun Gecko Experience").withStyle(ChatFormatting.GOLD));
        if (wins == 2) {
            panel.line(Component.literal(" Oruo will show you the modifiers and the essence shop now.").withStyle(ChatFormatting.GRAY));
        }
        if (mods == MODS.size()) {
            panel.line(Component.literal(" MAXIMUM POWER: every modifier at once!").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        panel.send(player, Component.literal("You beat the Sun Gecko!").withStyle(ChatFormatting.GOLD));
        Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        end(player, true, null);
    }

    private static void end(ServerPlayer player, boolean won, String why) {
        Fight f = fight;
        fight = null;
        if (f == null) {
            return;
        }
        f.gecko.discard();
        f.clones.forEach(Entity::discard);
        if (f.nuke != null) {
            f.nuke.discard();
        }
        f.bar.removeAllPlayers();
        if (player != null && !won && why != null) {
            Notice.of(Notice.Kind.WARN).tag("SUN GECKO").send(player, Component.literal(why + " The Sun Gecko wins this time.").withStyle(ChatFormatting.RED));
            Rift.Spot door = Rift.spots().get("time_chamber");
            if (door != null && Rift.isRift(player.level())) {
                player.teleportTo((ServerLevel) player.level(), door.pos().x, door.pos().y, door.pos().z, Set.of(), door.yaw(), 0, true);
            }
        }
    }

    private static void say(ServerPlayer player, String attack) {
        player.sendOverlayMessage(Component.literal(attack).withStyle(ChatFormatting.GOLD));
    }

    // ---------------------------------------------------------------- Oruo the Almighty

    private record Sale(String what, String item, int count, int essence, String note) {
    }

    private static final List<Sale> SHOP = List.of(new Sale("Combo Mania", "COMBO_MANIA_TALISMAN", 1, 50, "+0.5 s combo timer, each"),
            new Sale("Extremely Real Shuriken", "FAKE_SHURIKEN", 16, 32, ""), new Sale("Grandma's Knitting Needle", "GRANDMAS_KNITTING_NEEDLE", 1, 50, ""),
            new Sale("Super Leech Modifier", "SUPER_LEECH_MODIFIER", 2, 25, ""), new Sale("Enigma Soul", "", 1, 25, "once"),
            new Sale("Blessing of Time", "", 1, 0, "I: 5, II: 50, III: 100"));

    static void oruo(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            int wins = data(p, "wins");
            long motes = Motes.get(p);
            long essence = Essence.get(p, ESSENCE);
            boolean busy = fight != null && !fight.player.equals(p.getUUID());
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("Fight the Sun Gecko", busy ? "Someone's in there already. Wait your turn." : "250 ❤. Keep your combo going.",
                    0xFFAA00, !busy && fight == null, "Enter", new ItemStack(Items.BLAZE_POWDER), "", List.of(Chip.price("motes", PRICE, motes)), -1,
                    wins + " win" + (wins == 1 ? "" : "s")));
            if (wins >= 2) {
                int mods = data(p, "mods");
                for (int i = 0; i < MODS.size(); i++) {
                    boolean on = (mods >> i & 1) == 1;
                    cards.add(new CardView.Card(MODS.get(i)[0], MODS.get(i)[1], on ? 0x55FF55 : 0xAAAAAA, true, on ? "On" : "Off",
                            new ItemStack(on ? Items.DYE.lime() : Items.DYE.gray()), "", List.of(), -1, "+5 essence"));
                }
                for (Sale sale : SHOP) {
                    int blessing = data(p, "blessing");
                    int cost = sale.what().equals("Blessing of Time") ? new int[] {5, 50, 100, 0}[Math.min(3, blessing)] : sale.essence();
                    boolean soulDone = sale.what().equals("Enigma Soul") && EnigmaSouls.has(p, SOUL);
                    boolean maxed = sale.what().equals("Blessing of Time") && blessing >= 3;
                    String title = sale.what() + (sale.what().equals("Blessing of Time") && !maxed ? " " + Msg.roman(blessing + 1) : "");
                    ItemStack icon = sale.item().isEmpty() ? new ItemStack(sale.what().equals("Enigma Soul") ? Items.AMETHYST_SHARD : Items.CLOCK)
                            : RiftNpcs.icon(sale.item()).copyWithCount(sale.count());
                    cards.add(new CardView.Card(title, soulDone ? "Bought" : maxed ? "Maxed" : sale.note().isEmpty() ? "x" + sale.count() : sale.note(), 0xFFDD55,
                            !soulDone && !maxed && essence >= cost, "Buy", icon, "", List.of(new Chip("", ItemStack.EMPTY, cost + " essence", essence >= cost)),
                            -1, ""));
                }
            }
            return new CardView("RIFT", "Oruo the Almighty", "The Time Chamber", npc, List.of(Chip.balance("motes", motes),
                    new Chip("", ItemStack.EMPTY, essence + " Sun Gecko Essence", true)), cards, wins >= 2 ? "Each modifier: +5 essence a win."
                    : "Win twice and I'll show you more.", wins >= 2 ? 2 : 1, 38);
        }, (p, card) -> {
            if (card == 0) {
                if (fight != null) {
                    return "Someone's in there already.";
                }
                if (Rift.timeLeft(p) < 120) {
                    return "You need at least 2:00 of Rift Time left.";
                }
                if (!Motes.take(p, PRICE)) {
                    return "You need " + Msg.number(PRICE) + " Motes.";
                }
                start(p);
                return null;
            }
            int index = card - 1;
            if (index < MODS.size()) {
                set(p, "mods", data(p, "mods") ^ (1 << index));
                return "";
            }
            Sale sale = SHOP.get(index - MODS.size());
            int blessing = data(p, "blessing");
            int cost = sale.what().equals("Blessing of Time") ? new int[] {5, 50, 100, 0}[Math.min(3, blessing)] : sale.essence();
            if (sale.what().equals("Blessing of Time") && blessing >= 3 || sale.what().equals("Enigma Soul") && EnigmaSouls.has(p, SOUL)) {
                return "";
            }
            if (!Essence.take(p, ESSENCE, cost)) {
                return "You need " + cost + " Sun Gecko Essence.";
            }
            switch (sale.what()) {
                case "Blessing of Time" -> {
                    set(p, "blessing", blessing + 1);
                    Stats.refresh(p);
                }
                case "Enigma Soul" -> EnigmaSouls.find(p, SOUL);
                case "Combo Mania" -> {
                    set(p, "mania", data(p, "mania") + 1);
                    if (data(p, "mania") == 1) {
                        give(p, sale.item(), 1);
                    }
                }
                default -> give(p, sale.item(), sale.count());
            }
            p.sendSystemMessage(Component.literal("Oruo hands you " + sale.what() + ".").withStyle(ChatFormatting.GOLD));
            return "";
        });
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
