package com.skyrpg.skyblock.galatea;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.zone.Zones;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Galatea's critters and the ways to catch their shards (Hypixel's Hunting): forest critters wander the woods (the
 * Lasso: throw it on one, its stamina drains, and when it's nearly spent REEL in, right-click again; too early or let it
 * get away and it slips free; better lassos drain faster), water critters swim the lakes (the Fishing Net: scoop the one
 * you look at; Medium nets and up reach the shy ones), and Galatea's mobs give up their shard when they die with Charm
 * Chance on your side. Which shard a critter carries is its name; rarer ones are scarcer (decided weights).
 */
final class Critters {
    static final String TAG = "skyrpg_critter";
    private static final int PER_PLAYER = 7;
    private static final int[] WEIGHT = {60, 25, 10, 4, 1};
    private static final Set<String> FOREST_ZONES = Set.of("Evergreen Plateau", "North Reaches", "West Reaches", "South Reaches", "South Wetlands",
            "Tangleburg", "Forest Temple");
    private static final Set<String> WATER_ZONES = Set.of("Murkwater Loch", "Murkwater Shallows", "South Wetlands", "Squid Cave", "Murkwater Outpost");

    private record Kind(String shard, EntityType<? extends Mob> type, boolean water, boolean shy) {
    }

    private static final List<Kind> KINDS = List.of(
            new Kind("Bunbun", EntityTypes.RABBIT, false, false), new Kind("Firefox", EntityTypes.FOX, false, false),
            new Kind("Honeybuzz", EntityTypes.BEE, false, false), new Kind("Crow", EntityTypes.PARROT, false, false),
            new Kind("Kiwi", EntityTypes.CHICKEN, false, false), new Kind("Treefrog", EntityTypes.FROG, false, false),
            new Kind("Groundhog", EntityTypes.ARMADILLO, false, false), new Kind("Blue Jay", EntityTypes.PARROT, false, false),
            new Kind("Boreal Owl", EntityTypes.PARROT, false, false), new Kind("Heron", EntityTypes.PARROT, false, false),
            new Kind("Bambuleaf", EntityTypes.PANDA, false, false), new Kind("Mochibear", EntityTypes.PANDA, false, false),
            new Kind("Field Mouse", EntityTypes.RABBIT, false, false), new Kind("Sparrow", EntityTypes.PARROT, false, false),
            new Kind("Murkbat", EntityTypes.BAT, false, false), new Kind("Gecko", EntityTypes.FROG, false, false),
            new Kind("Cod", EntityTypes.COD, true, false), new Kind("Salmon", EntityTypes.SALMON, true, false),
            new Kind("Tadgang", EntityTypes.TADPOLE, true, false), new Kind("Azure", EntityTypes.TROPICAL_FISH, true, false),
            new Kind("Coralot", EntityTypes.AXOLOTL, true, false), new Kind("Newt", EntityTypes.AXOLOTL, true, false),
            new Kind("Birries", EntityTypes.TROPICAL_FISH, true, false), new Kind("Salamander", EntityTypes.AXOLOTL, true, true),
            new Kind("Mossybit", EntityTypes.FROG, true, false), new Kind("Lumisquid", EntityTypes.GLOW_SQUID, true, true),
            new Kind("Bullfrog", EntityTypes.FROG, true, true), new Kind("Joydive", EntityTypes.DOLPHIN, true, true),
            new Kind("Sepialot", EntityTypes.SQUID, true, true), new Kind("Water Snake", EntityTypes.TADPOLE, true, true));

    private static final class Lassoed {
        Mob critter;
        Hunting.Shard shard;
        int tier;
        double stamina = 100;
        int layers;
        long reelFrom;
        int lookingAway;
    }

    private static final Map<Mob, Hunting.Shard> LIVE = new HashMap<>();
    private static final Map<UUID, Lassoed> LASSOED = new HashMap<>();
    private static final Map<UUID, Long> NETTED = new HashMap<>();
    /** Action-bar lines sent next tick, after the ability's own banner. */
    private static final Map<UUID, Component> SAY = new HashMap<>();

    private Critters() {
    }

    static void init() {
        Abilities.register("throw", (player, ability) -> lasso(player, null));
        Abilities.register("yoink", (player, ability) -> net(player, null));
        // Clicking a mob up close is an entity click, not an item use: the lasso (a lead) and the net catch the critter you
        // clicked, and never leash or touch any other mob.
        Identifier first = SkyRpgSkyblock.id("critters_first");
        UseEntityCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(first, (player, level, hand, entity, hit) -> {
            String id = SkyItems.skyblockId(player.getItemInHand(hand));
            boolean lasso = id != null && id.endsWith("_LASSO");
            boolean net = id != null && id.endsWith("_FISHING_NET");
            if (!(lasso || net) || !(entity instanceof Mob mob)) {
                return InteractionResult.PASS;
            }
            if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer server) {
                if (lasso) {
                    lasso(server, mob);
                } else {
                    net(server, mob);
                }
            }
            return InteractionResult.SUCCESS;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !entity.entityTags().contains(TAG));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer killer) || !Galatea.isGalatea(entity.level()) || entity instanceof Player
                    || entity.getCustomName() == null) {
                return;
            }
            String name = entity.getCustomName().getString().replaceAll("^\\[.*?\\]\\s*", "").replaceAll("\\s*[\\d,./]+.*$", "").trim();
            Hunting.Shard shard = Hunting.byName(name.equals("Chillblade") || name.equals("Chillshot") ? "Chill" : name);
            double charm = Stats.get(killer, StatTypes.CHARM_CHANCE);
            if (shard != null && charm > 0 && killer.getRandom().nextDouble() * 100 < charm) {
                Hunting.catchShard(killer, shard, 1, "charmed");
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && !LIVE.containsKey(entity)) {
                entity.discard();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Critters::tick);
    }

    private static int tier(ServerPlayer player, String... ids) {
        ItemDefinition def = SkyItems.definition(player.getMainHandItem());
        if (def == null) {
            return -1;
        }
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].equals(def.skyblockId())) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- the lasso

    private static boolean lasso(ServerPlayer player, Mob clicked) {
        int tier = tier(player, "ABYSMAL_LASSO", "VINERIP_LASSO", "ENTANGLER_LASSO", "EVERSTRETCH_LASSO");
        if (tier < 0) {
            // another item's "Throw" (the Livid Dagger, the Axe of the Shredded)
            return false;
        }
        Lassoed held = LASSOED.get(player.getUUID());
        long now = player.level().getServer().getTickCount();
        if (held != null) {
            // the reel
            if (held.reelFrom > 0 && now >= held.reelFrom) {
                LASSOED.remove(player.getUUID());
                caught(player, held.critter, held.shard, "lasso");
            } else {
                say(player, Component.literal("Too early! It slipped away.").withStyle(ChatFormatting.RED));
                release(player, held);
            }
            return true;
        }
        if (!Galatea.isGalatea(player.level())) {
            say(player, Component.literal("There's nothing to lasso here.").withStyle(ChatFormatting.GRAY));
            return false;
        }
        Mob target = clicked != null ? (isCritter(clicked, false) ? clicked : null) : aimed(player, 14, false);
        if (target == null) {
            say(player, Component.literal("Aim the lasso at a forest critter.").withStyle(ChatFormatting.GRAY));
            return true;
        }
        Lassoed lassoed = new Lassoed();
        lassoed.critter = target;
        lassoed.shard = LIVE.get(target);
        lassoed.tier = tier;
        lassoed.layers = 1 + Hunting.rarity(lassoed.shard);
        LASSOED.put(player.getUUID(), lassoed);
        target.setLeashedTo(player, true);
        Msg.playSound(player, SoundEvents.LEAD_TIED, 1f, 1f);
        return true;
    }

    private static void release(ServerPlayer player, Lassoed held) {
        LASSOED.remove(player.getUUID());
        if (held.critter.isAlive()) {
            held.critter.removeLeash();
            held.critter.setDeltaMovement(held.critter.position().subtract(player.position()).normalize().scale(0.8).add(0, 0.4, 0));
            held.critter.needsSync = true;
        }
    }

    // ---------------------------------------------------------------- the net

    private static boolean net(ServerPlayer player, Mob clicked) {
        int tier = tier(player, "BASIC_FISHING_NET", "MEDIUM_FISHING_NET", "TURBO_FISHING_NET", "GIGANTIC_FISHING_NET");
        if (tier < 0) {
            return false;
        }
        long now = player.level().getServer().getTickCount();
        if (now - NETTED.getOrDefault(player.getUUID(), -100L) < 20) {
            return true;
        }
        NETTED.put(player.getUUID(), now);
        Mob target = clicked != null ? (isCritter(clicked, true) ? clicked : null) : aimed(player, 8 + 4 * tier, true);
        if (target == null) {
            say(player, Component.literal("The net comes up empty.").withStyle(ChatFormatting.GRAY));
            return true;
        }
        Kind kind = kindOf(target);
        if (kind != null && kind.shy() && tier < 1) {
            say(player, Component.literal("It slips through this net: you need a Medium Fishing Net or better.").withStyle(ChatFormatting.RED));
            return true;
        }
        Hunting.Shard shard = LIVE.get(target);
        double chance = 0.55 + 0.12 * tier - 0.08 * Hunting.rarity(shard);
        if (player.getRandom().nextDouble() < chance) {
            caught(player, target, shard, "net");
        } else {
            say(player, Component.literal("It darted out of the net!").withStyle(ChatFormatting.YELLOW));
            target.setDeltaMovement(target.position().subtract(player.position()).normalize().scale(0.9));
            target.needsSync = true;
        }
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + 0.5, target.getZ(), 20, 0.5, 0.3, 0.5, 0.1);
        return true;
    }

    /** The critter in your sights (forest or water), or null. */
    private static Mob aimed(ServerPlayer player, double range, boolean water) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Mob best = null;
        double bestDot = 0.9;
        for (Mob critter : LIVE.keySet()) {
            if (critter.level() != player.level() || !critter.isAlive()) {
                continue;
            }
            Kind kind = kindOf(critter);
            if (kind == null || kind.water() != water || LASSOED.values().stream().anyMatch(l -> l.critter == critter)) {
                continue;
            }
            Vec3 to = critter.position().add(0, critter.getBbHeight() / 2, 0).subtract(eye);
            double dot = look.dot(to.normalize());
            if (to.length() <= range && dot > bestDot) {
                bestDot = dot;
                best = critter;
            }
        }
        return best;
    }

    private static boolean isCritter(Mob mob, boolean water) {
        Kind kind = kindOf(mob);
        return kind != null && kind.water() == water && mob.isAlive() && LASSOED.values().stream().noneMatch(l -> l.critter == mob);
    }

    private static Kind kindOf(Mob critter) {
        Hunting.Shard shard = LIVE.get(critter);
        return shard == null ? null : KINDS.stream().filter(k -> k.shard().equals(shard.name())).findFirst().orElse(null);
    }

    private static void caught(ServerPlayer player, Mob critter, Hunting.Shard shard, String how) {
        ServerLevel level = (ServerLevel) critter.level();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, critter.getX(), critter.getY() + 0.5, critter.getZ(), 15, 0.4, 0.4, 0.4, 0);
        critter.removeLeash();
        LIVE.remove(critter);
        critter.discard();
        if (shard != null) {
            Hunting.catchShard(player, shard, 1, how);
        }
    }

    // ---------------------------------------------------------------- the tick: lassos, and critters coming and going

    private static void say(ServerPlayer player, Component line) {
        SAY.put(player.getUUID(), line);
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        SAY.forEach((id, line) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.sendOverlayMessage(line);
            }
        });
        SAY.clear();
        for (UUID id : List.copyOf(LASSOED.keySet())) {
            Lassoed held = LASSOED.get(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !held.critter.isAlive() || player.level() != held.critter.level() || player.distanceTo(held.critter) > 16) {
                if (player != null) {
                    say(player, Component.literal("The lasso slipped off.").withStyle(ChatFormatting.RED));
                    release(player, held);
                } else {
                    LASSOED.remove(id);
                }
                continue;
            }
            Vec3 to = held.critter.position().subtract(player.getEyePosition()).normalize();
            held.lookingAway = player.getLookAngle().dot(to) < 0.6 ? held.lookingAway + 1 : 0;
            if (held.lookingAway > 30) {
                say(player, Component.literal("You looked away and it slipped free!").withStyle(ChatFormatting.RED));
                release(player, held);
                continue;
            }
            if (held.reelFrom > 0) {
                if (now > held.reelFrom + 40) {
                    say(player, Component.literal("Too slow! It slipped free.").withStyle(ChatFormatting.RED));
                    release(player, held);
                }
                continue;
            }
            // it pulls away; your lasso wears it down
            double drain = (0.55 + 0.3 * held.tier) * (1 + Stats.get(player, StatTypes.PULL) / 100);
            held.stamina -= drain / held.layers;
            if (now % 4 == 0) {
                Vec3 away = held.critter.position().subtract(player.position()).normalize();
                held.critter.setDeltaMovement(away.x * 0.25, 0.05, away.z * 0.25);
                held.critter.needsSync = true;
                int bars = (int) Math.ceil(held.stamina / 10);
                player.sendOverlayMessage(Component.literal(held.shard.name() + "  ").withColor(Hunting.RARITY_COLORS[Hunting.rarity(held.shard)])
                        .append(Component.literal("■".repeat(Math.max(0, bars))).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("■".repeat(Math.max(0, 10 - bars))).withStyle(ChatFormatting.DARK_GRAY)));
            }
            if (held.stamina <= 20) {
                held.reelFrom = now;
                player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 5));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("REEL!").withColor(0x55FF55)));
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.8f);
            }
        }
        if (now % 40 != 0) {
            return;
        }
        ServerLevel level = server.getLevel(Galatea.LEVEL);
        LIVE.keySet().removeIf(critter -> {
            boolean near = critter.isAlive() && level != null && critter.level() == level
                    && !level.getPlayers(p -> p.distanceTo(critter) < 48).isEmpty();
            if (!near) {
                critter.discard();
            }
            return !near;
        });
        if (level == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            long around = LIVE.keySet().stream().filter(c -> c.distanceTo(player) < 32).count();
            if (around < PER_PLAYER) {
                spawnNear(level, player);
            }
        }
    }

    private static void spawnNear(ServerLevel level, ServerPlayer player) {
        var random = level.getRandom();
        double a = random.nextDouble() * Math.PI * 2;
        double r = 8 + random.nextDouble() * 16;
        int x = (int) Math.floor(player.getX() + Math.cos(a) * r);
        int z = (int) Math.floor(player.getZ() + Math.sin(a) * r);
        BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        if (top.getY() <= level.getMinY() + 1) {
            return;
        }
        boolean water = level.getFluidState(top.below()).isSource();
        Zones.Zone zone = Zones.at(level, top);
        String name = zone == null ? "" : zone.name();
        if (water ? !WATER_ZONES.contains(name) : !FOREST_ZONES.contains(name)) {
            return;
        }
        List<Kind> pool = KINDS.stream().filter(k -> k.water() == water && Hunting.byName(k.shard()) != null).toList();
        if (pool.isEmpty()) {
            return;
        }
        int total = pool.stream().mapToInt(k -> WEIGHT[Hunting.rarity(Hunting.byName(k.shard()))]).sum();
        int roll = random.nextInt(total);
        Kind kind = pool.get(0);
        for (Kind k : pool) {
            roll -= WEIGHT[Hunting.rarity(Hunting.byName(k.shard()))];
            if (roll < 0) {
                kind = k;
                break;
            }
        }
        Mob critter = kind.type().create(level, EntitySpawnReason.EVENT);
        if (critter == null) {
            return;
        }
        Hunting.Shard shard = Hunting.byName(kind.shard());
        BlockPos at = water ? top.below(2) : top;
        critter.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360, 0);
        critter.setCustomName(Component.literal(shard.name()).withColor(Hunting.RARITY_COLORS[Hunting.rarity(shard)]));
        critter.setCustomNameVisible(true);
        critter.addTag(TAG);
        if (critter instanceof net.minecraft.world.entity.AgeableMob ageable && (shard.name().equals("Field Mouse") || shard.name().equals("Mochibear"))) {
            ageable.setBaby(true);
        }
        LIVE.put(critter, shard);
        level.addFreshEntity(critter);
    }
}
