package com.skyrpg.skyblock.ability;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.TeleportTransition;

/**
 * Hypixel's item abilities (items get theirs from NEU's lore in {@code import_items.py}: name, mana, cooldown,
 * description, and a type named after the ability). The common ones work here, their numbers read from the
 * description as Hypixel words them ("Teleport 8 blocks", "deals 12,000 damage"): Instant Transmission, Dragon Rage, Ice
 * Spray, Molten Wave, Giant's Slam, Showtime, Guided Bat, Ice Bolt, Rapid Fire, Fire Blast, Speed Boost and the wands'
 * heals. Ability damage grows with Intelligence (+1% per point) and Ability Damage. Others show but don't work yet.
 */
public final class HypixelAbilities {
    private static final Pattern TELEPORT = Pattern.compile("Teleport (\\d+) blocks");
    private static final Pattern DAMAGE = Pattern.compile("([\\d,]+) damage");
    private static final Pattern SPEED = Pattern.compile("\\+(\\d+) Speed for (\\d+)\\s*s");
    private static final Pattern HEAL = Pattern.compile("Heal ([\\d,]+)\\D{0,3} per second for (\\d+)s");

    /** Speed bonuses running out (Speed Boost, Instant Transmission): player -> (amount, until tick). */
    private record Buff(double amount, long until) {
    }

    private static final Map<UUID, Buff> SPEED_BUFFS = new HashMap<>();
    /** Wand heals: player -> (per tick in vanilla health, until tick); they don't stack. */
    private static final Map<UUID, Buff> HEALS = new HashMap<>();

    private HypixelAbilities() {
    }

    public static void init() {
        Abilities.register("instant_transmission", HypixelAbilities::instantTransmission);
        Abilities.register("dragon_rage", (p, a) -> cone(p, a, 8, 70, ParticleTypes.FLAME, SoundEvents.ENDER_DRAGON_GROWL, 2.5, 0));
        Abilities.register("ice_spray", (p, a) -> cone(p, a, 8, 60, ParticleTypes.SNOWFLAKE, SoundEvents.PLAYER_HURT_FREEZE, 0.2, 5));
        Abilities.register("molten_wave", (p, a) -> cone(p, a, 10, 30, ParticleTypes.FALLING_LAVA, SoundEvents.BLAZE_SHOOT, 0.8, 0));
        Abilities.register("giant_s_slam", HypixelAbilities::slam);
        Abilities.register("showtime", (p, a) -> bolt(p, a, 30, 3.5, ParticleTypes.EXPLOSION, SoundEvents.GENERIC_EXPLODE.value(), 0));
        Abilities.register("guided_bat", (p, a) -> bolt(p, a, 30, 3.0, ParticleTypes.EXPLOSION, SoundEvents.BAT_TAKEOFF, 0));
        Abilities.register("ice_bolt", (p, a) -> bolt(p, a, 25, 0, ParticleTypes.SNOWFLAKE, SoundEvents.GLASS_BREAK, 5));
        Abilities.register("rapid_fire", (p, a) -> {
            boolean hit = bolt(p, a, 25, 0, ParticleTypes.CRIT, SoundEvents.SNOWBALL_THROW, 0);
            p.push(p.getLookAngle().scale(-0.6));
            p.syncVelocity = true;
            return hit || true;
        });
        Abilities.register("fire_blast", (p, a) -> bolt(p, a, 25, 1.5, ParticleTypes.FLAME, SoundEvents.BLAZE_SHOOT, 0));
        Abilities.register("speed_boost", HypixelAbilities::speedBoost);
        MoreAbilities.init();
        Deployables.init();
        // The Blaze Slayer daggers: swap attunement (the Hellion Shield only lets the matching one through).
        Abilities.register("attunement", (p, a) -> {
            net.minecraft.world.item.ItemStack held = p.getMainHandItem();
            String mode = com.skyrpg.items.ItemMods.swapAttunement(held, com.skyrpg.items.SkyItems.definition(held));
            if (mode.isEmpty()) {
                return false;
            }
            p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Attuned: " + mode).withStyle(net.minecraft.ChatFormatting.GOLD));
            Msg.playSound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, mode.equals("Ashen") || mode.equals("Spirit") ? 0.8f : 1.3f);
            return true;
        });
        for (String heal : new String[] {"small_heal", "medium_heal", "big_heal", "huge_heal"}) {
            Abilities.register(heal, HypixelAbilities::heal);
        }
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Abilities";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                Buff buff = SPEED_BUFFS.get(player.getUUID());
                if (buff != null) {
                    out.add(StatTypes.SPEED, buff.amount());
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(HypixelAbilities::tick);
    }

    static double number(Pattern pattern, String text, int group, double fallback) {
        Matcher m = pattern.matcher(text);
        return m.find() ? Double.parseDouble(m.group(group).replace(",", "")) : fallback;
    }

    /** Hypixel's ability damage: the base times (1 + Intelligence / 100) times (1 + Ability Damage %). */
    static double abilityDamage(ServerPlayer player, double base) {
        double intelligence = Math.max(0, Stats.get(player, StatTypes.INTELLIGENCE));
        double bonus = Math.max(0, Stats.get(player, StatTypes.ABILITY_DAMAGE));
        return base * (1 + intelligence / 100) * (1 + bonus / 100);
    }

    static void hurt(ServerPlayer player, LivingEntity target, double amount) {
        if (!(target.level() instanceof ServerLevel level) || target == player || target instanceof Player) {
            return;
        }
        target.setInvulnerableTime(0);
        target.hurtServer(level, level.damageSources().indirectMagic(player, player), (float) (amount / StatTypes.HEALTH_SCALE));
    }

    // ---------------------------------------------------------------- teleport

    static boolean instantTransmission(ServerPlayer player, ItemDefinition.Ability ability) {
        int distance = (int) number(TELEPORT, ability.description(), 1, 8);
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        // The furthest spot along the look where the player fits, stopping before walls.
        Vec3 target = null;
        for (double d = distance; d >= 1; d -= 0.5) {
            Vec3 at = eye.add(look.scale(d)).subtract(0, player.getEyeHeight(), 0);
            BlockHitResult wall = level.clip(new ClipContext(eye, at.add(0, player.getEyeHeight(), 0), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            if (wall.getType() == HitResult.Type.BLOCK) {
                continue;
            }
            AABB box = player.getBoundingBox().move(at.subtract(player.position()));
            if (level.noCollision(player, box)) {
                target = at;
                break;
            }
        }
        if (target == null) {
            player.sendOverlayMessage(Msg.text("There are blocks in the way!", net.minecraft.ChatFormatting.RED));
            return false;
        }
        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.5, 0.3, 0.2);
        player.teleport(new TeleportTransition(level, target, player.getDeltaMovement(), player.getYRot(), player.getXRot(), java.util.Set.of(),
                TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        level.playSound(null, BlockPos.containing(target), SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.2f);
        Matcher speed = SPEED.matcher(ability.description());
        if (speed.find()) {
            buffSpeed(player, Double.parseDouble(speed.group(1)), Integer.parseInt(speed.group(2)));
        }
        return true;
    }

    // ---------------------------------------------------------------- damage shapes

    /** Every monster in a cone in front of you takes the ability's damage (with knockback, or slowness for {@code freeze} s). */
    private static boolean cone(ServerPlayer player, ItemDefinition.Ability ability, double range, double degrees, ParticleOptions particle,
            SoundEvent sound, double knockback, int freeze) {
        ServerLevel level = (ServerLevel) player.level();
        double damage = abilityDamage(player, number(DAMAGE, ability.description(), 1, 1000));
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double cos = Math.cos(Math.toRadians(degrees / 2));
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                e -> e instanceof Mob && e.isAlive())) {
            Vec3 to = target.getBoundingBox().getCenter().subtract(eye);
            if (to.length() > range || to.normalize().dot(look) < cos) {
                continue;
            }
            hurt(player, target, damage);
            if (knockback > 0) {
                target.knockback(knockback, -look.x, -look.z, level.damageSources().indirectMagic(player, player), (float) knockback);
            }
            if (freeze > 0) {
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, freeze * 20, 9));
            }
        }
        for (int i = 1; i <= range; i++) {
            Vec3 at = eye.add(look.scale(i));
            level.sendParticles(particle, at.x, at.y, at.z, 6 + i * 2, i * 0.15, i * 0.15, i * 0.15, 0.02);
        }
        level.playSound(null, player.blockPosition(), sound, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    /** Giant's Slam: everything near you takes the damage. */
    private static boolean slam(ServerPlayer player, ItemDefinition.Ability ability) {
        ServerLevel level = (ServerLevel) player.level();
        double damage = abilityDamage(player, number(DAMAGE, ability.description(), 1, 10000));
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8), e -> e instanceof Mob && e.isAlive())) {
            hurt(player, target, damage);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.6f);
        return true;
    }

    /**
     * A shot along your look: the first monster it reaches (or the wall) is hit, and with a {@code blast} radius everything
     * around that spot too. {@code slow}: seconds of slowness on what it hits.
     */
    private static boolean bolt(ServerPlayer player, ItemDefinition.Ability ability, double range, double blast, ParticleOptions particle,
            SoundEvent sound, int slow) {
        ServerLevel level = (ServerLevel) player.level();
        double damage = abilityDamage(player, number(DAMAGE, ability.description(), 1, 500));
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() == HitResult.Type.BLOCK) {
            end = wall.getLocation();
        }
        Vec3 impact = end;
        LivingEntity first = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1), e -> e instanceof Mob && e.isAlive())) {
            if (target.getBoundingBox().inflate(0.4).clip(eye, end).isPresent()) {
                double d = target.distanceToSqr(eye);
                if (d < best) {
                    best = d;
                    first = target;
                }
            }
        }
        if (first != null) {
            impact = first.getBoundingBox().getCenter();
        }
        double length = impact.distanceTo(eye);
        for (double d = 1; d < length; d += 0.7) {
            Vec3 at = eye.add(look.scale(d));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        if (blast > 0) {
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(impact, impact).inflate(blast), e -> e instanceof Mob && e.isAlive())) {
                hurt(player, target, damage);
            }
            level.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
        } else if (first != null) {
            hurt(player, first, damage);
            if (slow > 0) {
                first.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, slow * 20, 2));
            }
        }
        level.playSound(null, BlockPos.containing(impact), sound, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    // ---------------------------------------------------------------- buffs and heals

    private static boolean speedBoost(ServerPlayer player, ItemDefinition.Ability ability) {
        Matcher m = SPEED.matcher(ability.description().replace("30s", "30 s"));
        double amount = m.find() ? Double.parseDouble(m.group(1)) : 100;
        int seconds = m.find(0) ? Integer.parseInt(m.group(2)) : 30;
        buffSpeed(player, amount, seconds);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 2.0f);
        return true;
    }

    static void buffSpeed(ServerPlayer player, double amount, int seconds) {
        long now = player.level().getServer().getTickCount();
        SPEED_BUFFS.put(player.getUUID(), new Buff(amount, now + seconds * 20L));
        Stats.refresh(player);
    }

    private static boolean heal(ServerPlayer player, ItemDefinition.Ability ability) {
        Matcher m = HEAL.matcher(ability.description());
        double perSecond = m.find() ? Double.parseDouble(m.group(1).replace(",", "")) : 20;
        int seconds = m.find(0) ? Integer.parseInt(m.group(2)) : 5;
        long now = player.level().getServer().getTickCount();
        HEALS.put(player.getUUID(), new Buff(perSecond / StatTypes.HEALTH_SCALE / 20, now + seconds * 20L));
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.5, player.getZ(), 5, 0.4, 0.3, 0.4, 0);
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (SPEED_BUFFS.isEmpty() && HEALS.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        SPEED_BUFFS.entrySet().removeIf(entry -> {
            if (now < entry.getValue().until()) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            entry.setValue(new Buff(0, 0));
            if (player != null) {
                // Refreshed after it's gone from the map (the next tick), below.
                server.execute(() -> Stats.refresh(player));
            }
            return true;
        });
        HEALS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || now >= entry.getValue().until()) {
                return true;
            }
            player.heal((float) entry.getValue().amount());
            return false;
        });
    }

    static {
        SkyRpgSkyblock.LOGGER.debug("Hypixel abilities ready");
    }
}
