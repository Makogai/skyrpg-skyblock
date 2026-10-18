package com.skyrpg.skyblock.crimson;

import com.mojang.math.Transformation;
import com.skyrpg.core.text.Msg;
import com.skyrpg.hub.mixin.DisplayAccess;
import com.skyrpg.hub.mixin.TextDisplayAccess;
import com.skyrpg.skyblock.mixin.BlockDisplayAccess;
import com.skyrpg.skyblock.mixin.InteractionAccess;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One Kuudra fight in its slot of the {@code skyrpg_skyblock:kuudra} world (the arena: {@code tools/builds/kuudra}).
 * <ol>
 *   <li><b>Supplies:</b> six supply crates sit on the six islets; carry them (slowly) to the ballista.</li>
 *   <li><b>Build:</b> stand by the ballista to build it; the more of you, the faster.</li>
 *   <li><b>Kuudra:</b> it rises in the east. Fuel cells appear on the islets two at a time; each one brought to the
 *   ballista is a shot. A shot hurts Kuudra and stuns it beside the platform for 10 seconds: only then can you hurt it.</li>
 * </ol>
 * Kuudra's mobs come in waves all along (every 35 seconds in Basic down to 15 in Infernal), and Kuudra slams down on
 * someone every few seconds (a ring warns first). From Burning, two tentacles rise on the platform after each stun and
 * the ballista can't fire until they're dead; from Fiery it heals while it isn't stunned; in Infernal, below a quarter
 * of its health, it's enraged and slams twice as often.
 */
final class KuudraRun {
    enum Phase { SUPPLIES, BUILD, KUUDRA, DONE }

    static final int SPACING = 1024;
    // The arena's fixed spots (template coordinates; tools/builds/kuudra/kuudra_kit.py).
    static final int[][] ISLETS = {{104, 41}, {104, 87}, {64, 110}, {24, 87}, {24, 41}, {64, 18}};
    static final Vec3 CENTER = new Vec3(64.5, 19, 64.5);
    static final Vec3 ARRIVAL = new Vec3(52.5, 19, 64.5);
    /** Where you fire the ballista from: its back, on the walkway west of it. */
    static final Vec3 SEAT = new Vec3(58.5, 19, 64.5);
    static final Vec3 CHEST = new Vec3(64.5, 19, 54.5);
    static final Vec3 KUUDRA_DOWN = new Vec3(100.5, -2, 64.5);
    static final Vec3 KUUDRA_UP = new Vec3(100.5, 6, 64.5);
    static final Vec3 KUUDRA_STUNNED = new Vec3(85.5, 6, 64.5);
    static final int SUPPLIES = 6;

    final int id;
    final int slot;
    final Kuudra.Tier tier;
    final ServerLevel level;
    final List<UUID> players;
    final String tag;
    final RandomSource random;
    Phase phase = Phase.SUPPLIES;
    private int shots;
    long started;
    long ticks;
    /** Supply crates and fuel cells lying on islets: the click box -> its parts and islet. */
    final Map<Interaction, Pickup> pickups = new HashMap<>();
    /** Who's carrying what ("supply" or "fuel"). */
    final Map<UUID, String> carrying = new HashMap<>();
    int delivered;
    double built;
    int fuel;
    Mob kuudra;
    long stunnedUntil;
    long nextWave;
    long nextSlam;
    Vec3 slamAt;
    long slamHits;
    final List<Mob> tentacles = new ArrayList<>();
    Interaction ballista;
    final ServerBossEvent bar;
    final Map<UUID, Long> dead = new HashMap<>();
    final Set<UUID> rewarded = new HashSet<>();
    long doneAt;
    boolean failed;

    record Pickup(String kind, int islet, List<Entity> parts) {
    }

    KuudraRun(int id, int slot, Kuudra.Tier tier, ServerLevel level, List<UUID> players) {
        this.id = id;
        this.slot = slot;
        this.tier = tier;
        this.level = level;
        this.players = players;
        this.tag = "skyrpg_kuudra_" + id;
        this.random = level.getRandom();
        this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("Supplies"), BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_6);
    }

    /** A template spot in this slot's world coordinates (the template's origin is (slot x - 64, 50, -64)). */
    Vec3 at(Vec3 local) {
        return new Vec3(slot * SPACING - 64 + local.x, 50 + local.y, -64 + local.z);
    }

    Vec3 islet(int i) {
        return at(new Vec3(ISLETS[i][0] + 0.5, 17, ISLETS[i][1] + 0.5));
    }

    List<ServerPlayer> online() {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : players) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == level) {
                out.add(player);
            }
        }
        return out;
    }

    List<ServerPlayer> alive() {
        return online().stream().filter(p -> !dead.containsKey(p.getUUID())).toList();
    }

    void broadcast(Component message) {
        online().forEach(p -> p.sendSystemMessage(message));
    }

    void title(String text, ChatFormatting color) {
        for (ServerPlayer player : online()) {
            player.sendOverlayMessage(Component.literal(text).withStyle(color, ChatFormatting.BOLD));
        }
    }

    // ---------------------------------------------------------------- the phases

    void begin() {
        started = level.getGameTime();
        for (int i = 0; i < ISLETS.length; i++) {
            pickup("supply", i);
        }
        ballista = clickBox(at(SEAT), 1.6f, 2.4f, "skyrpg_kuudra_ballista");
        label(at(SEAT).add(0, 2.8, 0), Component.literal("Ballista").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("\nright-click to fire").withStyle(ChatFormatting.GRAY)));
        nextWave = started + 200;
        nextSlam = started + 300;
        broadcast(Component.literal("Kuudra stirs below. Bring the 6 supply crates to the ballista!").withStyle(ChatFormatting.RED));
        sound(online(), SoundEvents.ELDER_GUARDIAN_CURSE, 0.8f, 0.6f);
        spawnKuudra();
    }

    void tick() {
        long now = level.getGameTime();
        ticks++;
        if (phase == Phase.DONE) {
            return;
        }
        // The dead come back after 10 seconds; everyone dead at once is a lost run.
        dead.entrySet().removeIf(e -> {
            if (now < e.getValue()) {
                return false;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
            if (player != null) {
                Kuudra.respawn(this, player);
            }
            return true;
        });
        if (!online().isEmpty() && alive().isEmpty()) {
            broadcast(Component.literal("Everyone is down! Kuudra wins this time.").withStyle(ChatFormatting.RED));
            finish(true);
            return;
        }
        if (now % 10 == 0) {
            for (ServerPlayer player : online()) {
                bar.addPlayer(player);
                // The lava throws whoever falls in back onto the platform (what they carried floats back to an islet).
                if (player.getY() < 59.5 && !player.isSpectator() && !dead.containsKey(player.getUUID())) {
                    dropped(player);
                    Vec3 back = at(ARRIVAL);
                    player.clearFire();
                    player.teleportTo(level, back.x, back.y, back.z, java.util.Set.of(), -90, 0, true);
                    player.sendOverlayMessage(Component.literal("The lava spat you back onto the platform!").withStyle(ChatFormatting.RED));
                }
            }
            carriers(now);
        }
        // Mob waves.
        if (now >= nextWave) {
            nextWave = now + tier.waveSeconds() * 20L;
            wave();
        }
        slams(now);
        switch (phase) {
            case SUPPLIES -> {
                bar.setName(Component.literal("Supplies: " + delivered + "/" + SUPPLIES).withStyle(ChatFormatting.GOLD));
                bar.setProgress(delivered / (float) SUPPLIES);
            }
            case BUILD -> {
                if (now % 20 == 0) {
                    List<ServerPlayer> near = alive().stream().filter(p -> p.position().distanceTo(at(CENTER)) <= 12).toList();
                    long builders = near.size();
                    // Vulcan's Fresh Tools: the best builder's level, +3% a level
                    int tools = near.stream().mapToInt(p -> CrimsonPerks.level(p, "fresh_tools")).max().orElse(0);
                    built = Math.min(100, built + (builders == 0 ? 0 : (1.5 + builders) * (1 + 0.03 * tools)));
                    level.sendParticles(ParticleTypes.WAX_ON, at(CENTER).x, at(CENTER).y + 2, at(CENTER).z, 10, 2, 1, 2, 0);
                    if (builders > 0) {
                        sound(alive(), SoundEvents.ANVIL_USE, 0.25f, 1.5f);
                    }
                }
                bar.setName(Component.literal("Building the Ballista: " + (int) built + "%").withStyle(ChatFormatting.YELLOW));
                bar.setProgress((float) (built / 100));
                if (built >= 100) {
                    kuudraPhase();
                }
            }
            case KUUDRA -> kuudraTick(now);
            default -> {
            }
        }
    }

    private void carriers(long now) {
        for (ServerPlayer player : alive()) {
            String kind = carrying.get(player.getUUID());
            if (kind == null) {
                continue;
            }
            // Vulcan's Strongarm: lighter loads (Slowness II, I at level 1, none at 2)
            int slow = 1 - CrimsonPerks.level(player, "strongarm");
            if (slow >= 0) {
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, slow, false, false));
            }
            level.sendParticles(kind.equals("fuel") ? ParticleTypes.GLOW : ParticleTypes.WAX_OFF, player.getX(), player.getY() + 2.3, player.getZ(),
                    3, 0.2, 0.2, 0.2, 0);
            player.sendOverlayMessage(Component.literal(kind.equals("fuel") ? "Carrying a Fuel Cell: bring it to the ballista!"
                    : "Carrying a Supply Crate: bring it to the ballista!").withStyle(ChatFormatting.YELLOW));
            if (player.position().distanceTo(at(CENTER)) <= 6) {
                carrying.remove(player.getUUID());
                player.removeEffect(MobEffects.SLOWNESS);
                if (kind.equals("supply")) {
                    delivered++;
                    broadcast(Component.literal(player.getScoreboardName() + " delivered a supply crate! (" + delivered + "/" + SUPPLIES + ")")
                            .withStyle(ChatFormatting.YELLOW));
                    if (delivered >= SUPPLIES && phase == Phase.SUPPLIES) {
                        phase = Phase.BUILD;
                        broadcast(Component.literal("All supplies in! Stand by the ballista to build it.").withStyle(ChatFormatting.GOLD));
                        sound(online(), SoundEvents.PLAYER_LEVELUP, 0.8f, 1.0f);
                    }
                } else {
                    fuel++;
                    broadcast(Component.literal(player.getScoreboardName() + " loaded a fuel cell! Fire the ballista at Kuudra (" + fuel
                            + " shot" + (fuel == 1 ? "" : "s") + ")").withStyle(ChatFormatting.AQUA));
                }
                sound(online(), SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8f, 1.2f);
            }
        }
    }

    // ---------------------------------------------------------------- Kuudra

    private void spawnKuudra() {
        Mob cube = EntityTypes.MAGMA_CUBE.create(level, EntitySpawnReason.EVENT);
        if (!(cube instanceof MagmaCube magma)) {
            return;
        }
        Vec3 down = at(KUUDRA_DOWN);
        magma.snapTo(down.x, down.y, down.z, 90, 0);
        magma.setSize(24, true);
        Zones.dressAs(magma, "Kuudra", tier.mobLevel(), tier.kuudraHealth(), tier.slam(), -1);
        magma.setNoAi(true);
        magma.setNoGravity(true);
        magma.setPersistenceRequired();
        magma.setCustomNameVisible(false);
        magma.addTag(tag);
        magma.addTag(Kuudra.TAG);
        magma.addTag(Kuudra.BOSS_TAG);
        magma.addTag(Zones.FIGHTABLE_TAG);
        level.addFreshEntity(magma);
        kuudra = magma;
    }

    private void kuudraPhase() {
        phase = Phase.KUUDRA;
        Vec3 up = at(KUUDRA_UP);
        if (kuudra != null) {
            kuudra.teleportTo(up.x, up.y, up.z);
            kuudra.setCustomNameVisible(true);
        }
        bar.setColor(BossEvent.BossBarColor.RED);
        broadcast(Component.literal("The ballista is built! KUUDRA RISES! Bring fuel cells to the ballista and fire!")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        sound(online(), SoundEvents.ENDER_DRAGON_GROWL, 1.0f, 0.5f);
        level.sendParticles(ParticleTypes.LAVA, up.x, up.y, up.z, 120, 6, 3, 6, 0);
    }

    private void kuudraTick(long now) {
        if (kuudra == null || !kuudra.isAlive()) {
            return;
        }
        float health = kuudra.getHealth() / kuudra.getMaxHealth();
        bar.setName(Component.literal("Kuudra" + (stunned(now) ? "  STUNNED " + (stunnedUntil - now + 19) / 20 + "s" : "") + "  Fuel: " + fuel)
                .withStyle(ChatFormatting.RED));
        bar.setProgress(Math.max(0, health));
        if (stunned(now)) {
            if (now == stunnedUntil - 1) {
                // The stun's over: back to its pit.
                Vec3 up = at(KUUDRA_UP);
                kuudra.teleportTo(up.x, up.y, up.z);
                level.sendParticles(ParticleTypes.LAVA, up.x, up.y, up.z, 40, 4, 2, 4, 0);
                if (tier.number() >= 3) {
                    raiseTentacles();
                }
            }
        } else if (tier.number() >= 4 && now % 20 == 0) {
            // Fiery and up: it heals while it isn't stunned.
            kuudra.heal(kuudra.getMaxHealth() * 0.005f);
        }
        tentacles.removeIf(t -> !t.isAlive());
        // Fuel cells, two at a time, while there's nothing to fire and none lying about.
        boolean cellsOut = pickups.values().stream().anyMatch(p -> p.kind().equals("fuel")) || carrying.containsValue("fuel");
        if (fuel == 0 && !cellsOut && !stunned(now)) {
            List<Integer> free = new ArrayList<>();
            for (int i = 0; i < ISLETS.length; i++) {
                free.add(i);
            }
            java.util.Collections.shuffle(free, new java.util.Random(random.nextLong()));
            pickup("fuel", free.get(0));
            pickup("fuel", free.get(1));
            broadcast(Component.literal("Fuel cells have washed up on two islets!").withStyle(ChatFormatting.AQUA));
        }
    }

    boolean stunned(long now) {
        return stunnedUntil > now;
    }

    /** Someone fires the ballista: a shot of fuel, Kuudra hurt and stunned beside the platform. */
    void fire(ServerPlayer player) {
        long now = level.getGameTime();
        if (phase != Phase.KUUDRA) {
            player.sendOverlayMessage(Component.literal(phase == Phase.SUPPLIES ? "The ballista needs its supplies first."
                    : "The ballista isn't built yet: stand by it!").withStyle(ChatFormatting.RED));
            return;
        }
        if (stunned(now)) {
            player.sendOverlayMessage(Component.literal("Kuudra is already stunned: hit it!").withStyle(ChatFormatting.YELLOW));
            return;
        }
        if (!tentacles.isEmpty()) {
            player.sendOverlayMessage(Component.literal("Kill the tentacles first!").withStyle(ChatFormatting.RED));
            return;
        }
        if (fuel <= 0) {
            player.sendOverlayMessage(Component.literal("No fuel! Bring a fuel cell from the islets.").withStyle(ChatFormatting.RED));
            return;
        }
        fuel--;
        Vec3 from = at(CENTER).add(2, 2, 0);
        Vec3 to = kuudra.position().add(0, 3, 0);
        for (int i = 0; i <= 30; i++) {
            Vec3 p = from.lerp(to, i / 30.0);
            level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0);
        }
        level.playSound(null, BlockPos.containing(from), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 3f, 0.5f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        // The shot: a tenth of its health, then 10 seconds stunned beside the platform.
        // Vulcan's Kuudra Master: +0.1% a shot fired this run, up to 10%
        shots++;
        float shot = kuudra.getMaxHealth() * 0.1f * (CrimsonPerks.level(player, "kuudra_master") > 0 ? 1 + Math.min(0.1f, 0.001f * shots) : 1);
        kuudra.setHealth(Math.max(1, kuudra.getHealth() - shot));
        Vec3 stun = at(KUUDRA_STUNNED);
        kuudra.teleportTo(stun.x, stun.y, stun.z);
        stunnedUntil = now + 200;
        broadcast(Component.literal(player.getScoreboardName() + " fired the ballista! Kuudra is STUNNED: hit it!")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sound(online(), SoundEvents.ANVIL_LAND, 1.0f, 0.5f);
    }

    private void raiseTentacles() {
        for (int i = 0; i < 2; i++) {
            Mob tentacle = EntityTypes.MAGMA_CUBE.create(level, EntitySpawnReason.EVENT);
            if (!(tentacle instanceof MagmaCube cube)) {
                continue;
            }
            Vec3 at = at(new Vec3(75.5, 19, 58.5 + i * 12));
            cube.snapTo(at.x, at.y, at.z, 90, 0);
            cube.setSize(3, true);
            Zones.dressAs(cube, "Kuudra's Tentacle", tier.mobLevel(), tier.mobHealth(2.0), tier.mobDamage(1.0), 50);
            cube.setNoAi(true);
            cube.setPersistenceRequired();
            cube.addTag(tag);
            cube.addTag(Kuudra.TAG);
            cube.addTag(Zones.FIGHTABLE_TAG);
            level.addFreshEntity(cube);
            tentacles.add(cube);
        }
        broadcast(Component.literal("Kuudra's tentacles grip the platform! Kill them before you fire again.").withStyle(ChatFormatting.RED));
    }

    /** Kuudra's slam: a ring under someone, then a blow. */
    private void slams(long now) {
        if (kuudra == null || !kuudra.isAlive() || phase == Phase.DONE) {
            return;
        }
        if (slamAt != null && now >= slamHits) {
            level.sendParticles(ParticleTypes.EXPLOSION, slamAt.x, slamAt.y + 0.5, slamAt.z, 6, 1.5, 0.2, 1.5, 0);
            level.playSound(null, BlockPos.containing(slamAt), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0f, 0.6f);
            for (ServerPlayer player : alive()) {
                if (player.position().distanceTo(slamAt) <= 3.5 && !player.isCreative()) {
                    player.hurtServer(level, level.damageSources().mobAttack(kuudra), (float) (tier.slam()
                            / com.skyrpg.core.stats.StatTypes.HEALTH_SCALE));
                }
            }
            slamAt = null;
        }
        if (slamAt != null) {
            if (now % 4 == 0) {
                for (int i = 0; i < 16; i++) {
                    double a = i * Math.PI / 8;
                    level.sendParticles(ParticleTypes.FLAME, slamAt.x + Math.cos(a) * 3, slamAt.y + 0.1, slamAt.z + Math.sin(a) * 3, 1, 0, 0, 0, 0);
                }
            }
            return;
        }
        boolean enraged = tier.number() >= 5 && kuudra.getHealth() / kuudra.getMaxHealth() < 0.25f;
        if (now >= nextSlam && !stunned(now)) {
            List<ServerPlayer> targets = alive();
            if (!targets.isEmpty()) {
                ServerPlayer target = targets.get(random.nextInt(targets.size()));
                slamAt = target.position();
                slamHits = now + 30;
            }
            nextSlam = now + (enraged ? 80 : 160);
        }
    }

    /** A wave of Kuudra's followers out of the lava, near the islets and the platform. */
    private void wave() {
        int count = 2 + tier.number() + Math.max(0, online().size() - 1);
        for (int i = 0; i < count; i++) {
            Kuudra.MobKind kind = Kuudra.MOBS.get(random.nextInt(tier.number() >= 2 ? Kuudra.MOBS.size() : Kuudra.MOBS.size() - 1));
            int islet = random.nextInt(ISLETS.length);
            Vec3 base = random.nextBoolean() ? islet(islet) : at(CENTER).add(random.nextInt(17) - 8, 0, random.nextInt(17) - 8);
            Mob mob = kind.type().create(level, EntitySpawnReason.EVENT);
            if (mob == null) {
                continue;
            }
            mob.snapTo(base.x + random.nextInt(5) - 2, base.y + 0.5, base.z + random.nextInt(5) - 2, random.nextFloat() * 360, 0);
            if (mob instanceof MagmaCube cube) {
                cube.setSize(2, true);
            }
            Zones.dressAs(mob, kind.name(), tier.mobLevel(), tier.mobHealth(kind.health()), tier.mobDamage(kind.damage()), 30 * tier.number());
            if (mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin piglin) {
                piglin.setImmuneToZombification(true);
            }
            mob.setPersistenceRequired();
            mob.addTag(tag);
            mob.addTag(Kuudra.TAG);
            mob.addTag(Zones.FIGHTABLE_TAG);
            level.addFreshEntity(mob);
            List<ServerPlayer> targets = alive();
            if (!targets.isEmpty()) {
                mob.setTarget(targets.get(random.nextInt(targets.size())));
            }
            level.sendParticles(ParticleTypes.LAVA, mob.getX(), mob.getY(), mob.getZ(), 8, 0.3, 0.3, 0.3, 0);
        }
    }

    // ---------------------------------------------------------------- pickups

    private void pickup(String kind, int islet) {
        Vec3 at = islet(islet);
        List<Entity> parts = new ArrayList<>();
        Interaction box = clickBox(at, 1.2f, 1.2f, null);
        Display.BlockDisplay crate = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        crate.snapTo(at.x, at.y, at.z, 0, 0);
        BlockState look = kind.equals("fuel") ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.BARREL.defaultBlockState();
        ((BlockDisplayAccess) crate).skyrpg$setBlockState(look);
        ((DisplayAccess) crate).skyrpg$setTransformation(new Transformation(new Vector3f(-0.45f, 0, -0.45f), new Quaternionf(),
                new Vector3f(0.9f, kind.equals("fuel") ? 1.3f : 0.9f, 0.9f), new Quaternionf()));
        crate.addTag(tag);
        crate.addTag(Kuudra.TAG);
        level.addFreshEntity(crate);
        parts.add(crate);
        parts.add(label(at.add(0, 1.8, 0), Component.literal(kind.equals("fuel") ? "Fuel Cell" : "Supply Crate")
                .withStyle(kind.equals("fuel") ? ChatFormatting.AQUA : ChatFormatting.GOLD)
                .append(Component.literal("\nright-click to pick up").withStyle(ChatFormatting.GRAY))));
        parts.add(box);
        pickups.put(box, new Pickup(kind, islet, parts));
    }

    /** Picking up a crate or a cell. */
    void take(ServerPlayer player, Interaction box) {
        Pickup pickup = pickups.get(box);
        if (pickup == null || dead.containsKey(player.getUUID())) {
            return;
        }
        if (carrying.containsKey(player.getUUID())) {
            player.sendOverlayMessage(Component.literal("You're already carrying something!").withStyle(ChatFormatting.RED));
            return;
        }
        pickups.remove(box);
        pickup.parts().forEach(Entity::discard);
        carrying.put(player.getUUID(), pickup.kind());
        Msg.playSound(player, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0f, 0.8f);
    }

    /** A carrier went down: what they carried goes back to an islet. */
    void dropped(ServerPlayer player) {
        String kind = carrying.remove(player.getUUID());
        if (kind != null) {
            pickup(kind, random.nextInt(ISLETS.length));
        }
    }

    private Interaction clickBox(Vec3 at, float width, float height, String extraTag) {
        Interaction box = EntityTypes.INTERACTION.create(level, EntitySpawnReason.EVENT);
        if (box == null) {
            return null;
        }
        ((InteractionAccess) box).skyrpg$setWidth(width);
        ((InteractionAccess) box).skyrpg$setHeight(height);
        box.snapTo(at.x, at.y, at.z, 0, 0);
        box.addTag(tag);
        box.addTag(Kuudra.TAG);
        if (extraTag != null) {
            box.addTag(extraTag);
        }
        level.addFreshEntity(box);
        return box;
    }

    private Display.TextDisplay label(Vec3 at, Component text) {
        Display.TextDisplay display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
        display.snapTo(at.x, at.y, at.z, 0, 0);
        ((TextDisplayAccess) display).skyrpg$setText(text);
        ((TextDisplayAccess) display).skyrpg$setBackgroundColor(0xA00B1018);
        ((DisplayAccess) display).skyrpg$setBillboard(Display.BillboardConstraints.CENTER);
        display.addTag(tag);
        display.addTag(Kuudra.TAG);
        level.addFreshEntity(display);
        return display;
    }

    private static void sound(List<ServerPlayer> players, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        players.forEach(p -> Msg.playSound(p, sound, volume, pitch));
    }

    // ---------------------------------------------------------------- the end

    /** Kuudra fell (or everyone did). */
    void finish(boolean lost) {
        if (phase == Phase.DONE) {
            return;
        }
        phase = Phase.DONE;
        failed = lost;
        doneAt = level.getGameTime();
        bar.removeAllPlayers();
        for (Entity entity : com.google.common.collect.Lists.newArrayList(level.getAllEntities())) {
            if (entity.entityTags().contains(tag) && entity != kuudra && !(entity instanceof net.minecraft.world.entity.player.Player)) {
                entity.discard();
            }
        }
        pickups.clear();
        carrying.clear();
        for (ServerPlayer player : online()) {
            player.removeEffect(MobEffects.SLOWNESS);
            if (dead.containsKey(player.getUUID())) {
                Kuudra.respawn(this, player);
            }
        }
        dead.clear();
        if (lost) {
            if (kuudra != null) {
                kuudra.discard();
            }
            broadcast(Component.literal("You'll be sent back in 10 seconds.").withStyle(ChatFormatting.GRAY));
            doneAt -= 1000;
            return;
        }
        Kuudra.won(this);
    }

}
