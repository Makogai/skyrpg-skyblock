package com.skyrpg.skyblock.rift;

import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Rift's small mechanics: the Wyld Woods' slime launchers (stand on the pad, up you go), Lil' Pads (right-click the
 * water: a lily pad to stand on for 10 seconds), the Odonata (dragonflies over the ponds; hold an Empty Odonata Bottle
 * and look at one for 3 seconds to bottle it), and the areas' gates (Rift Time left and secured Timecharms).
 */
final class RiftWorld {
    static final String ODONATA = "skyrpg_rift_odonata";
    private static final int ODONATA_PER_POND = 4;
    private static final int GAZE_TICKS = 60;
    private static final int PAD_TICKS = 10 * 20;

    /** An area's gate: Rift Time it needs left, and the Timecharm ("" for none). */
    record Gate(int seconds, String charm) {
    }

    /** Hypixel's gates (the Plaza's Mirrorverse and Chicken N Egg charms are waited for with the Supreme, decided: those come later). */
    static final Map<String, Gate> GATES = Map.of(
            "Black Lagoon", new Gate(4 * 60, ""),
            "West Village", new Gate(3 * 60, Timecharms.SUPREME),
            "Dreadfarm", new Gate(0, Timecharms.SUPREME),
            "Village Plaza", new Gate(8 * 60, Timecharms.SUPREME),
            "Barrier Street", new Gate(10 * 60, Timecharms.SUPREME),
            "Deja Vu Alley", new Gate(5 * 60, Timecharms.SUPREME));

    private record Pad(BlockPos pos, long until) {
    }

    private static final List<Pad> PADS = new ArrayList<>();
    private static final Map<UUID, Long> LAUNCHED = new HashMap<>();
    private static final Map<UUID, Integer> GAZE = new HashMap<>();
    private static final Map<UUID, String> LAST_ZONE = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_SAFE = new HashMap<>();
    private static final Map<String, List<Bee>> PONDS = new HashMap<>();

    private RiftWorld() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(RiftWorld::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !entity.entityTags().contains(ODONATA));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(ODONATA) && PONDS.values().stream().noneMatch(list -> list.contains(entity))) {
                entity.discard();
            }
        });
        // Lil' Pads: a lily pad on the water you look at.
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(player instanceof ServerPlayer server) || !Rift.isRift(level)) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getItemInHand(hand);
            ItemDefinition def = SkyItems.definition(held);
            if (def == null || !"LIL_PAD".equals(def.skyblockId())) {
                return InteractionResult.PASS;
            }
            BlockHitResult hit = level.clip(new ClipContext(player.getEyePosition(), player.getEyePosition().add(player.getLookAngle().scale(6)),
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
            BlockPos water = hit.getBlockPos();
            if (hit.getType() != HitResult.Type.BLOCK || !level.getFluidState(water).is(Fluids.WATER) || !level.getBlockState(water.above()).isAir()) {
                server.sendOverlayMessage(Component.literal("Look at open water to place a Lil' Pad.").withStyle(ChatFormatting.GRAY));
                return InteractionResult.FAIL;
            }
            level.setBlockAndUpdate(water.above(), Blocks.LILY_PAD.defaultBlockState());
            PADS.add(new Pad(water.above(), server.level().getServer().getTickCount() + PAD_TICKS));
            held.shrink(1);
            Msg.playSound(server, SoundEvents.LILY_PAD_PLACE, 1f, 1f);
            return InteractionResult.SUCCESS;
        });
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = Rift.level(server);
        if (level == null) {
            return;
        }
        long now = server.getTickCount();
        // Lil' Pads sink after 10 seconds.
        PADS.removeIf(pad -> {
            if (now < pad.until()) {
                return false;
            }
            if (level.getBlockState(pad.pos()).is(Blocks.LILY_PAD)) {
                level.setBlockAndUpdate(pad.pos(), Blocks.AIR.defaultBlockState());
                level.sendParticles(ParticleTypes.SPLASH, pad.pos().getX() + 0.5, pad.pos().getY(), pad.pos().getZ() + 0.5, 8, 0.3, 0.1, 0.3, 0);
            }
            return true;
        });
        Map<String, Rift.Spot> spots = Rift.spots();
        for (ServerPlayer player : level.players()) {
            launchers(player, spots, now);
            if (now % 5 == 0) {
                gaze(player, level);
            }
            if (now % 10 == 0) {
                gates(player, level);
            }
        }
        if (now % 40 == 0) {
            ponds(level, spots);
        }
    }

    // ---------------------------------------------------------------- launchers

    private static void launchers(ServerPlayer player, Map<String, Rift.Spot> spots, long now) {
        if (!player.onGround() || now - LAUNCHED.getOrDefault(player.getUUID(), -100L) < 40) {
            return;
        }
        for (Map.Entry<String, Rift.Spot> e : spots.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith("launch_") || key.endsWith("_top")) {
                continue;
            }
            Rift.Spot pad = e.getValue();
            Rift.Spot top = spots.get(key + "_top");
            Vec3 at = pad.pos();
            if (top == null || Math.abs(player.getX() - at.x) > 1.2 || Math.abs(player.getZ() - at.z) > 1.2 || Math.abs(player.getY() - at.y) > 0.8) {
                continue;
            }
            // Enough to clear the top by about four blocks (gravity 0.08 a tick and 2% drag; tuned in game).
            double height = top.pos().y - at.y + 5;
            double speed = Math.sqrt(2 * 0.08 * height) * 1.18;
            player.setDeltaMovement(0, speed, 0);
            player.syncVelocity = true;
            player.fallDistance = 0;
            LAUNCHED.put(player.getUUID(), now);
            Msg.playSound(player, SoundEvents.SLIME_JUMP, 1f, 0.8f);
            player.level().getServer().getLevel(Rift.LEVEL).sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 20, 0.5, 0.2, 0.5, 0.1);
            return;
        }
    }

    // ---------------------------------------------------------------- the Odonata

    private static void ponds(ServerLevel level, Map<String, Rift.Spot> spots) {
        for (Map.Entry<String, Rift.Spot> e : spots.entrySet()) {
            if (!e.getKey().startsWith("odonata_")) {
                continue;
            }
            Vec3 pond = e.getValue().pos();
            List<Bee> bees = PONDS.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            bees.removeIf(bee -> bee.isRemoved() || bee.position().distanceToSqr(pond) > 30 * 30);
            boolean near = !level.getPlayers(p -> p.position().distanceToSqr(pond) < 40 * 40).isEmpty();
            if (!near) {
                bees.forEach(Bee::discard);
                bees.clear();
                continue;
            }
            if (bees.size() < ODONATA_PER_POND && level.isLoaded(BlockPos.containing(pond))) {
                Bee bee = EntityTypes.BEE.create(level, EntitySpawnReason.EVENT);
                if (bee != null) {
                    bee.snapTo(pond.x + level.getRandom().nextDouble() * 6 - 3, pond.y + 1.5, pond.z + level.getRandom().nextDouble() * 6 - 3, 0, 0);
                    bee.setCustomName(Component.literal("Odonata").withStyle(ChatFormatting.AQUA));
                    bee.setPersistenceRequired();
                    bee.addTag(ODONATA);
                    bees.add(bee);
                    level.addFreshEntity(bee);
                }
            }
        }
    }

    /** Holding an Empty Odonata Bottle and looking at an Odonata close by for 3 seconds bottles it. */
    private static void gaze(ServerPlayer player, ServerLevel level) {
        ItemDefinition def = SkyItems.definition(player.getMainHandItem());
        if (def == null || !"EMPTY_ODONATA_BOTTLE".equals(def.skyblockId())) {
            GAZE.remove(player.getUUID());
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Mob target = null;
        for (Mob bee : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(5), m -> m.entityTags().contains(ODONATA))) {
            Vec3 to = bee.position().add(0, 0.3, 0).subtract(eye);
            if (to.length() < 5 && look.dot(to.normalize()) > 0.93) {
                target = bee;
                break;
            }
        }
        if (target == null) {
            GAZE.remove(player.getUUID());
            return;
        }
        int ticks = GAZE.merge(player.getUUID(), 5, Integer::sum);
        player.sendOverlayMessage(Component.literal("Bottling the Odonata... " + Math.min(100, ticks * 100 / GAZE_TICKS) + "%")
                .withStyle(ChatFormatting.AQUA));
        if (ticks >= GAZE_TICKS) {
            GAZE.remove(player.getUUID());
            player.getMainHandItem().shrink(1);
            ItemDefinition bottled = com.skyrpg.items.ItemRegistry.skyblock("BOTTLED_ODONATA");
            if (bottled != null) {
                ItemStack stack = SkyItems.create(bottled, 1);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
            level.sendParticles(ParticleTypes.GLOW, target.getX(), target.getY(), target.getZ(), 12, 0.2, 0.2, 0.2, 0.05);
            target.discard();
            player.sendSystemMessage(Component.literal("You bottled an Odonata!").withStyle(ChatFormatting.AQUA));
            Msg.playSound(player, SoundEvents.BOTTLE_FILL, 1f, 1.2f);
        }
    }

    // ---------------------------------------------------------------- gates

    private static void gates(ServerPlayer player, ServerLevel level) {
        Zones.Zone zone = Zones.at(level, player.blockPosition());
        String name = zone == null ? "" : zone.name();
        String last = LAST_ZONE.getOrDefault(player.getUUID(), "");
        Gate gate = GATES.get(name);
        if (!name.equals(last) && gate != null && !player.isCreative()) {
            String problem = null;
            if (!gate.charm().isEmpty() && !Timecharms.secured(player, gate.charm())) {
                problem = name + " needs the " + Timecharms.name(gate.charm()) + " secured with Elise.";
            } else if (gate.seconds() > 0 && Rift.timeLeft(player) < gate.seconds()) {
                problem = "You need at least " + Rift.clock(gate.seconds()) + " of Rift Time left to enter " + name + ".";
            }
            if (problem != null) {
                Vec3 back = LAST_SAFE.get(player.getUUID());
                if (back != null) {
                    player.teleportTo(back.x, back.y, back.z);
                }
                player.sendOverlayMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
                return;
            }
        }
        LAST_ZONE.put(player.getUUID(), name);
        if (player.onGround()) {
            LAST_SAFE.put(player.getUUID(), player.position());
        }
    }
}
