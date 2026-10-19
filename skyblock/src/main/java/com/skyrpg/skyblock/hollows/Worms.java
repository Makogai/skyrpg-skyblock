package com.skyrpg.skyblock.hollows;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.pet.SkyBlockPets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Stoneworms and Scathas (Hypixel's): mining Hard Stone has a 1 in 600 chance of "something approaching" through your
 * tunnel, a Stoneworm four times in five and a Scatha the fifth, with 30 seconds between worms. A worm is a chain of
 * segments (5, or 10 for a Scatha) that slithers along its Y level and turns back at walls; every hit on it, with anything,
 * does exactly one hit, and it loses a segment. The head and tail hit you (400 and 2,500 damage) when you're close.
 *
 * <p>Decided: a segment is a scaled Silverfish following the head's trail; everybody who hit the worm shares its drops (the
 * pet drops to one of them); the Combat XP and coins go to every hitter too. Hypixel's "Tunnel Vision" perk, Blazing
 * reforge and Tracking stat aren't here (no such sources in the build yet).
 */
public final class Worms {
    private static final String TAG = "skyrpg_worm";
    private static final double CHANCE = 1.0 / 600;
    private static final int COOLDOWN = 30 * 20;
    private static final int SPACING = 5;

    private static final class Worm {
        final boolean scatha;
        int hits;
        final List<Mob> segments = new ArrayList<>();
        final LinkedList<Vec3> trail = new LinkedList<>();
        Vec3 direction;
        final Vec3 origin;
        final long born;
        long nextHit;
        long nextBite;
        int squelches = 3;
        final Set<UUID> hitters = new HashSet<>();

        Worm(boolean scatha, Vec3 origin, Vec3 direction, long born) {
            this.scatha = scatha;
            this.hits = scatha ? 10 : 5;
            this.origin = origin;
            this.direction = direction;
            this.born = born;
        }

        Mob head() {
            return segments.isEmpty() ? null : segments.get(0);
        }
    }

    private static final List<Worm> WORMS = new ArrayList<>();
    private static final Map<UUID, Worm> BY_SEGMENT = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private Worms() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Worms::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!entity.entityTags().contains(TAG)) {
                return true;
            }
            Worm worm = BY_SEGMENT.get(entity.getUUID());
            if (worm != null && source.getEntity() instanceof ServerPlayer player && entity.level() instanceof ServerLevel level) {
                hit(level, worm, player);
            }
            return false;
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG)) {
                // Checked a tick later: a fresh segment is known by then, one back from disk isn't.
                level.getServer().execute(() -> {
                    if (!BY_SEGMENT.containsKey(entity.getUUID())) {
                        entity.discard();
                    }
                });
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> WORMS.forEach(Worms::remove));
    }

    /** Hard Stone was just mined at {@code pos}: maybe a worm. */
    public static void dug(ServerLevel level, ServerPlayer player, BlockPos pos) {
        long now = level.getServer().getTickCount();
        if (level.getRandom().nextDouble() >= CHANCE || now < LAST.getOrDefault(player.getUUID(), 0L)) {
            return;
        }
        LAST.put(player.getUUID(), now + COOLDOWN);
        spawn(level, player, level.getRandom().nextInt(5) == 0);
    }

    /** Admin and mining: a worm in the tunnel in front of the player. */
    static void spawn(ServerLevel level, ServerPlayer player, boolean scatha) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : flat.normalize();
        Vec3 at = null;
        for (int k = 10; k >= 3 && at == null; k--) {
            Vec3 spot = player.position().add(flat.scale(k));
            BlockPos feet = BlockPos.containing(spot);
            if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()) {
                at = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            }
        }
        if (at == null) {
            at = player.position().add(flat.scale(-2));
            flat = flat.scale(-1);
        }
        Worm worm = new Worm(scatha, at, flat.scale(-1), level.getServer().getTickCount());
        int segments = scatha ? 10 : 5;
        for (int i = 0; i < segments; i++) {
            Mob mob = HollowsMobs.spawn(level, EntityTypes.SILVERFISH, at, scatha ? "Scatha" : "Stoneworm", scatha ? 181 : 119, 1_000_000,
                    scatha ? 2_500 : 400, scatha ? 271 : 178, TAG);
            if (mob == null) {
                continue;
            }
            mob.setNoAi(true);
            mob.setNoGravity(true);
            mob.setSilent(true);
            var scale = mob.getAttribute(Attributes.SCALE);
            if (scale != null) {
                scale.setBaseValue(i == 0 ? (scatha ? 3.0 : 2.4) : (scatha ? 2.4 : 1.9));
            }
            mob.setCustomNameVisible(i == 0);
            worm.segments.add(mob);
            BY_SEGMENT.put(mob.getUUID(), worm);
        }
        if (worm.head() == null) {
            return;
        }
        WORMS.add(worm);
        player.sendSystemMessage(Component.literal("You hear the sound of something approaching...").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    private static void hit(ServerLevel level, Worm worm, ServerPlayer player) {
        long now = level.getServer().getTickCount();
        if (now < worm.nextHit) {
            return;
        }
        worm.nextHit = now + 6;
        worm.hitters.add(player.getUUID());
        worm.hits--;
        Msg.playSound(player, SoundEvents.SLIME_SQUISH, 1.0f, 0.7f);
        if (worm.hits > 0) {
            if (worm.segments.size() > worm.hits) {
                Mob tail = worm.segments.remove(worm.segments.size() - 1);
                BY_SEGMENT.remove(tail.getUUID());
                tail.discard();
            }
            return;
        }
        killed(level, worm);
        remove(worm);
    }

    private static void killed(ServerLevel level, Worm worm) {
        var random = level.getRandom();
        boolean scatha = worm.scatha;
        boolean petGiven = false;
        for (UUID id : worm.hitters) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            String tier = scatha ? "" : "ROUGH_";
            if (scatha) {
                HollowsState.give(player, "WORM_MEMBRANE", 4, ItemGains.Source.COMBAT);
            } else {
                HollowsState.give(player, "WORM_MEMBRANE", 1, ItemGains.Source.COMBAT);
            }
            String prefix = scatha ? "FINE_" : "ROUGH_";
            HollowsState.give(player, prefix + "TOPAZ_GEM", 1, ItemGains.Source.COMBAT);
            if (random.nextDouble() < 0.4) {
                HollowsState.give(player, prefix + "AMETHYST_GEM", 1, ItemGains.Source.COMBAT);
            }
            if (random.nextDouble() < 0.2) {
                HollowsState.give(player, prefix + "JADE_GEM", 1, ItemGains.Source.COMBAT);
            }
            if (random.nextDouble() < 0.1) {
                HollowsState.give(player, prefix + "AMBER_GEM", 1, ItemGains.Source.COMBAT);
            }
            if (random.nextDouble() < 0.1) {
                HollowsState.give(player, prefix + "SAPPHIRE_GEM", 1, ItemGains.Source.COMBAT);
            }
            if (scatha && random.nextDouble() < 0.1) {
                HollowsState.give(player, "DWARVEN_OS_BLOCK_BRAN", 1, ItemGains.Source.COMBAT);
            }
            Wallet.give(player, scatha ? 60 : 39);
            SkillLevels.giveXp(player, SkillLevels.COMBAT, scatha ? 271 : 178);
            if (scatha && !petGiven) {
                double roll = random.nextDouble() * 100;
                String pet = roll < 0.04 ? "LEGENDARY" : roll < 0.16 ? "EPIC" : roll < 0.4 ? "RARE" : null;
                if (pet != null && SkyBlockPets.known("SCATHA")) {
                    petGiven = true;
                    ItemStack stack = SkyBlockPets.create("SCATHA", pet, 0);
                    player.sendSystemMessage(Component.literal("RARE DROP! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(Component.literal(pet.charAt(0) + pet.substring(1).toLowerCase() + " Scatha Pet").withStyle(ChatFormatting.LIGHT_PURPLE)));
                    HollowsState.give(player, stack, ItemGains.Source.COMBAT);
                }
            }
            player.sendOverlayMessage(Component.literal((scatha ? "Scatha" : "Stoneworm") + " slain!").withStyle(ChatFormatting.GREEN));
        }
    }

    private static void remove(Worm worm) {
        for (Mob segment : worm.segments) {
            BY_SEGMENT.remove(segment.getUUID());
            segment.discard();
        }
        worm.segments.clear();
        WORMS.remove(worm);
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (Worm worm : new ArrayList<>(WORMS)) {
            Mob head = worm.head();
            if (head == null || head.isRemoved() || !(head.level() instanceof ServerLevel level)) {
                remove(worm);
                continue;
            }
            if (now - worm.born > 6000 || level.getNearestPlayer(head, 64) == null) {
                remove(worm);
                continue;
            }
            if (worm.squelches > 0 && (now - worm.born) % 10 == 0) {
                worm.squelches--;
                level.playSound(null, head.blockPosition(), SoundEvents.SLIME_SQUISH, net.minecraft.sounds.SoundSource.HOSTILE, 1.5f, 0.5f);
            }
            // Slither: forward, turning back at a wall or too far from where it came from.
            Vec3 next = head.position().add(worm.direction.scale(0.14));
            BlockPos ahead = BlockPos.containing(next.add(worm.direction.scale(0.6)));
            boolean blocked = !level.getBlockState(ahead).isAir() || !level.getBlockState(ahead.above()).isAir();
            if (blocked || next.distanceTo(worm.origin) > 24) {
                worm.direction = worm.direction.scale(-1);
                next = head.position();
            }
            float yaw = (float) (Math.toDegrees(Math.atan2(-worm.direction.x, worm.direction.z)));
            worm.trail.addFirst(next);
            while (worm.trail.size() > worm.segments.size() * SPACING + 2) {
                worm.trail.removeLast();
            }
            for (int i = 0; i < worm.segments.size(); i++) {
                Vec3 at = worm.trail.get(Math.min(i * SPACING, worm.trail.size() - 1));
                worm.segments.get(i).snapTo(at.x, at.y, at.z, yaw, 0);
            }
            if (now >= worm.nextBite) {
                for (ServerPlayer player : level.players()) {
                    if (player.isCreative() || player.isSpectator()) {
                        continue;
                    }
                    for (Mob segment : worm.segments) {
                        if (player.distanceToSqr(segment) < 3.5) {
                            player.hurtServer(level, level.damageSources().mobAttack(head), (float) head.getAttributeValue(Attributes.ATTACK_DAMAGE));
                            worm.nextBite = now + 20;
                            break;
                        }
                    }
                }
            }
        }
        Iterator<Map.Entry<UUID, Long>> it = LAST.entrySet().iterator();
        if (now % 1200 == 0) {
            while (it.hasNext()) {
                if (it.next().getValue() < now) {
                    it.remove();
                }
            }
        }
    }
}
