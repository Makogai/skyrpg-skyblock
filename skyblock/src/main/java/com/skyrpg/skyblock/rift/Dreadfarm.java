package com.skyrpg.skyblock.rift;

import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Dreadfarm (Hypixel's): Shania hands out the Wand of Farming and wants two bunches of each of its peculiar crops
 * for a Proto-Chicken (her task finishes when you wear Chicken Leggs). With the wand: Agaricus Caps (look at a brown
 * mushroom until it turns red, then click it), Caducous Stems (click a rose bush's top, then its bottom) and Wilted
 * Berberis (the dead bushes with purple sparkles; the sparkles move on when you take one). The Berberis Blowgun's
 * "Swhooomp" shoots an arrow that hurts nothing but chickens and Puffs: punch a Not Chicken into the air and shoot it
 * on the way down for a Metaphoric Egg (and 50 Motes). It also pops the balloons and presses the buttons the Enigma
 * Souls hide behind.
 */
final class Dreadfarm {
    private static final String ZONE = "Dreadfarm";
    private static final String WAND = "FARMING_WAND";
    private static final String BLOWGUN_TAG = "skyrpg_blowgun";
    private static final int GAZE_TICKS = 60;
    private static final int RED_TICKS = 30 * 20;
    private static final int ROSE_TICKS = 10 * 20;
    private static final int BERBERIS_MARKS = 8;
    private static final DustParticleOptions PURPLE = new DustParticleOptions(0xB04CFF, 1.1f);
    /** The souls whose balloons are popped with the Blowgun, and the soul behind the Dreadfarm's wall of buttons. */
    static final List<String> BALLOON_SOULS = List.of("enigma:Farm Balloons", "enigma:Plaza Balloons", "enigma:Mountain Balloons",
            "enigma:Chateau Balloons");
    static final String BUTTON_SOUL = "enigma:Buttons";

    private record Shot(Arrow arrow, UUID owner, long until) {
    }

    private static final Map<UUID, BlockPos> GAZE_AT = new HashMap<>();
    private static final Map<UUID, Integer> GAZE = new HashMap<>();
    /** Mushrooms we turned red, and when they turn back. */
    private static final Map<BlockPos, Long> RED = new HashMap<>();
    /** Rose bushes: the top you clicked (the bush's lower block) and when; the bushes you picked, until they grow back. */
    private static final Map<UUID, BlockPos> ROSE_TOP = new HashMap<>();
    private static final Map<UUID, Long> ROSE_TOP_AT = new HashMap<>();
    private static final Map<UUID, Map<BlockPos, Long>> ROSE_DONE = new HashMap<>();
    private static List<BlockPos> bushes = List.of();
    private static final List<BlockPos> MARKED = new ArrayList<>();
    private static final List<Shot> SHOTS = new ArrayList<>();
    private static final List<LivingEntity[]> KILLS = new ArrayList<>();
    private static boolean killing;
    /** Balloons: the soul's wool clusters (each one by its lowest block), and the ones each player popped. */
    private static final Map<String, Map<BlockPos, List<BlockPos>>> BALLOONS = new HashMap<>();
    private static final Map<UUID, Set<BlockPos>> POPPED = new HashMap<>();
    private static List<BlockPos> buttons;
    private static final Map<UUID, Set<BlockPos>> PRESSED = new HashMap<>();

    private Dreadfarm() {
    }

    static void init() {
        // Ahead of the Hub's protection (which turns every click on a protected block away).
        Identifier first = SkyRpgSkyblock.id("rift_first");
        AttackBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(first, (player, level, hand, pos, direction) -> {
            if (!Rift.isRift(level)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                // The Hub's protection turns these clicks away on the client: send them on, the server decides.
                return crop(level.getBlockState(pos)) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return player instanceof ServerPlayer server && attack(server, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(first, (player, level, hand, hit) -> {
            if (player instanceof ServerPlayer server && Rift.isRift(level) && level.getBlockState(hit.getBlockPos()).is(BlockTags.BUTTONS)) {
                press(server, hit.getBlockPos());
            }
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(first, (player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer) || !Rift.isRift(level) || !isChicken(entity)) {
                return InteractionResult.PASS;
            }
            // A punch sends a chicken flying; it floats back down.
            LivingEntity chicken = (LivingEntity) entity;
            chicken.setDeltaMovement(Vec3.ZERO);
            chicken.push(0, 1.15, 0);
            chicken.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0));
            level.playSound(null, chicken.blockPosition(), SoundEvents.CHICKEN_HURT_BABY.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 0.9f);
            return InteractionResult.SUCCESS;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Entity direct = source.getDirectEntity();
            if (direct != null && direct.entityTags().contains(BLOWGUN_TAG)) {
                if (source.getEntity() instanceof ServerPlayer owner && (isChicken(entity) && !entity.onGround() || isPuff(entity))) {
                    KILLS.add(new LivingEntity[] {entity, owner});
                }
                direct.discard();
                return false;
            }
            // Chickens only fall to the Blowgun, mid-air (and to /kill).
            return killing || !isChicken(entity) || !Rift.isRift(entity.level()) || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        });
        // Not Chickens don't lay vanilla eggs: their egg is the Metaphoric one.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Chicken chicken && Rift.isRift(level)) {
                chicken.eggTime = Integer.MAX_VALUE;
            }
        });
        Abilities.register("swhooomp", Dreadfarm::swhooomp);
        ServerTickEvents.END_SERVER_TICK.register(Dreadfarm::tick);
    }

    static boolean isChicken(Entity entity) {
        return entity instanceof Chicken && entity.getCustomName() != null && entity.getCustomName().getString().contains("Chicken");
    }

    private static boolean isPuff(Entity entity) {
        return entity.getCustomName() != null && entity.getCustomName().getString().contains("Puff");
    }

    private static boolean holds(ServerPlayer player, String id) {
        ItemDefinition def = SkyItems.definition(player.getMainHandItem());
        return def != null && id.equals(def.skyblockId());
    }

    private static boolean inFarm(ServerPlayer player, BlockPos pos) {
        Zones.Zone zone = Zones.at(player.level(), pos);
        return zone != null && zone.name().equals(ZONE);
    }

    // ---------------------------------------------------------------- the crops

    /** A left click on a block in the Rift: true when it was one of the Dreadfarm's crops (handled here). */
    private static boolean attack(ServerPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        BlockState state = level.getBlockState(pos);
        if (!crop(state) || !inFarm(player, pos)) {
            return false;
        }
        if (!holds(player, WAND)) {
            player.sendOverlayMessage(Component.literal("You need Shania's Wand of Farming to farm the Dreadfarm's crops.").withStyle(ChatFormatting.RED));
            return true;
        }
        if (state.is(Blocks.RED_MUSHROOM)) {
            level.setBlock(pos, Blocks.BROWN_MUSHROOM.defaultBlockState(), 3);
            RED.remove(pos);
            harvest(player, pos, "AGARICUS_CAP", ParticleTypes.CRIMSON_SPORE);
        } else if (state.is(Blocks.BROWN_MUSHROOM)) {
            player.sendOverlayMessage(Component.literal("Look at the mushroom until it turns red, then click it!").withStyle(ChatFormatting.YELLOW));
        } else if (state.is(Blocks.ROSE_BUSH)) {
            boolean top = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
            BlockPos base = top ? pos.below() : pos;
            long now = level.getServer().getTickCount();
            Map<BlockPos, Long> done = ROSE_DONE.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
            if (done.getOrDefault(base, 0L) > now) {
                player.sendOverlayMessage(Component.literal("This rose bush needs a moment to grow back.").withStyle(ChatFormatting.GRAY));
            } else if (top) {
                ROSE_TOP.put(player.getUUID(), base);
                ROSE_TOP_AT.put(player.getUUID(), now);
                level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, false, false, base.getX() + 0.5, base.getY() + 1.6, base.getZ() + 0.5, 5,
                        0.25, 0.2, 0.25, 0);
                player.sendOverlayMessage(Component.literal("Now the bottom!").withStyle(ChatFormatting.GREEN));
            } else if (base.equals(ROSE_TOP.get(player.getUUID())) && now - ROSE_TOP_AT.getOrDefault(player.getUUID(), 0L) < 100) {
                ROSE_TOP.remove(player.getUUID());
                done.put(base, now + ROSE_TICKS);
                harvest(player, base.above(), "CADUCOUS_STEM", ParticleTypes.CHERRY_LEAVES);
            } else {
                player.sendOverlayMessage(Component.literal("Collect them from top to bottom!").withStyle(ChatFormatting.YELLOW));
            }
        } else if (MARKED.contains(pos)) {
            MARKED.remove(pos);
            harvest(player, pos, "WILTED_BERBERIS", ParticleTypes.WITCH);
            mark(level, pos);
        } else {
            player.sendOverlayMessage(Component.literal("Only the dead bushes with purple sparkles are Wilted Berberis.").withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    private static boolean crop(BlockState state) {
        return state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.ROSE_BUSH) || state.is(Blocks.DEAD_BUSH);
    }

    private static void harvest(ServerPlayer player, BlockPos pos, String id, net.minecraft.core.particles.ParticleOptions particle) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        ItemStack stack = SkyItems.create(def, 1);
        ItemGains.give(player, stack, ItemGains.Source.FARMING);
        if (!stack.isEmpty()) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        ((ServerLevel) player.level()).sendParticles(particle, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
        Msg.playSound(player, SoundEvents.CROP_BREAK, 1f, 1.2f);
        player.sendOverlayMessage(Component.literal("+1 " + def.name()).withStyle(ChatFormatting.GREEN));
    }

    /** Moves a Wilted Berberis mark: to another dead bush near the one picked (or anywhere). */
    private static void mark(ServerLevel level, BlockPos near) {
        List<BlockPos> close = new ArrayList<>();
        for (BlockPos bush : bushes) {
            if (!MARKED.contains(bush) && !bush.equals(near) && (near == null || bush.distSqr(near) < 14 * 14)) {
                close.add(bush);
            }
        }
        if (close.isEmpty()) {
            for (BlockPos bush : bushes) {
                if (!MARKED.contains(bush)) {
                    close.add(bush);
                }
            }
        }
        if (!close.isEmpty()) {
            MARKED.add(close.get(level.getRandom().nextInt(close.size())));
        }
    }

    /** The farm's dead bushes on its plots (coarse dirt, podzol, soul soil), found once the Dreadfarm is loaded. */
    private static void findBushes(ServerLevel level, Zones.Zone zone) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = (int) zone.box().minX; x <= zone.box().maxX; x++) {
            for (int z = (int) zone.box().minZ; z <= zone.box().maxZ; z++) {
                if (!level.isLoaded(pos.set(x, 68, z))) {
                    return;
                }
                for (int y = 62; y <= 82; y++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.DEAD_BUSH)) {
                        BlockState under = level.getBlockState(pos.below());
                        if (under.is(Blocks.COARSE_DIRT) || under.is(Blocks.PODZOL) || under.is(Blocks.SOUL_SOIL)) {
                            found.add(pos.immutable());
                        }
                    }
                }
            }
        }
        bushes = List.copyOf(found);
        for (int i = 0; i < BERBERIS_MARKS; i++) {
            mark(level, null);
        }
    }

    private static void gaze(ServerPlayer player, ServerLevel level, long now) {
        HitResult hit = player.pick(6, 0, false);
        BlockPos pos = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
        if (pos == null || !level.getBlockState(pos).is(Blocks.BROWN_MUSHROOM) || !pos.equals(GAZE_AT.get(player.getUUID()))) {
            GAZE.remove(player.getUUID());
            if (pos != null && level.getBlockState(pos).is(Blocks.BROWN_MUSHROOM)) {
                GAZE_AT.put(player.getUUID(), pos);
            } else {
                GAZE_AT.remove(player.getUUID());
            }
            return;
        }
        int ticks = GAZE.merge(player.getUUID(), 1, Integer::sum);
        if (ticks % 5 == 0) {
            int bars = Math.min(10, ticks * 10 / GAZE_TICKS);
            player.sendOverlayMessage(Component.literal("Agaricus Cap ").withStyle(ChatFormatting.RED)
                    .append(Component.literal("■".repeat(bars)).withStyle(ChatFormatting.RED))
                    .append(Component.literal("■".repeat(10 - bars)).withStyle(ChatFormatting.DARK_GRAY)));
            level.sendParticles(player, ParticleTypes.CRIMSON_SPORE, false, false, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 2, 0.15, 0.15, 0.15, 0);
        }
        if (ticks >= GAZE_TICKS) {
            GAZE.remove(player.getUUID());
            level.setBlock(pos, Blocks.RED_MUSHROOM.defaultBlockState(), 3);
            RED.put(pos, now + RED_TICKS);
            Msg.playSound(player, SoundEvents.FUNGUS_PLACE, 1f, 1.3f);
            player.sendOverlayMessage(Component.literal("It's red! Click it!").withStyle(ChatFormatting.RED));
        }
    }

    // ---------------------------------------------------------------- the Blowgun, chickens, balloons and buttons

    private static boolean swhooomp(ServerPlayer player, ItemDefinition.Ability ability) {
        if (!Rift.isRift(player.level())) {
            player.sendOverlayMessage(Component.literal("The Blowgun only works in the Rift.").withStyle(ChatFormatting.RED));
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, 2.6f, 0.4f);
        arrow.setBaseDamage(0);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.addTag(BLOWGUN_TAG);
        level.addFreshEntity(arrow);
        SHOTS.add(new Shot(arrow, player.getUUID(), level.getServer().getTickCount() + 60));
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.6f);
        return true;
    }

    private static void shots(MinecraftServer server, ServerLevel level, long now) {
        for (Iterator<Shot> it = SHOTS.iterator(); it.hasNext();) {
            Shot shot = it.next();
            Arrow arrow = shot.arrow();
            ServerPlayer owner = server.getPlayerList().getPlayer(shot.owner());
            if (arrow.isRemoved() || now > shot.until() || owner == null) {
                arrow.discard();
                it.remove();
                continue;
            }
            Vec3 ahead = arrow.getDeltaMovement().lengthSqr() > 1e-4 ? arrow.getDeltaMovement().normalize().scale(0.6) : Vec3.ZERO;
            for (Vec3 at : List.of(arrow.position(), arrow.position().add(ahead))) {
                BlockPos pos = BlockPos.containing(at);
                BlockState state = level.getBlockState(pos);
                if (state.is(BlockTags.WOOL)) {
                    pop(owner, level, pos);
                    arrow.discard();
                    break;
                }
                if (state.is(BlockTags.BUTTONS)) {
                    press(owner, pos);
                    arrow.discard();
                    break;
                }
            }
        }
        for (LivingEntity[] kill : KILLS) {
            if (kill[0].isAlive()) {
                killing = true;
                kill[0].hurtServer(level, level.damageSources().playerAttack((ServerPlayer) kill[1]), isChicken(kill[0]) ? 1000f : 25f);
                killing = false;
            }
        }
        KILLS.clear();
    }

    /** The wool balloons near a balloon soul: connected wool clusters hanging in the air. */
    private static Map<BlockPos, List<BlockPos>> balloons(ServerLevel level, String soul) {
        Map<BlockPos, List<BlockPos>> known = BALLOONS.get(soul);
        if (known != null) {
            return known;
        }
        Rift.Spot spot = Rift.spots().get(soul);
        if (spot == null || !level.isLoaded(BlockPos.containing(spot.pos()))) {
            return Map.of();
        }
        BlockPos centre = BlockPos.containing(spot.pos());
        Set<BlockPos> seen = new HashSet<>();
        Map<BlockPos, List<BlockPos>> found = new HashMap<>();
        int r = 28;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -20, -r), centre.offset(r, 30, r))) {
            if (seen.contains(pos) || !level.getBlockState(pos).is(BlockTags.WOOL)) {
                continue;
            }
            List<BlockPos> cluster = new ArrayList<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(pos.immutable()));
            seen.add(pos.immutable());
            while (!queue.isEmpty() && cluster.size() < 400) {
                BlockPos at = queue.poll();
                cluster.add(at);
                for (Direction d : Direction.values()) {
                    BlockPos next = at.relative(d);
                    if (!seen.contains(next) && level.getBlockState(next).is(BlockTags.WOOL)) {
                        seen.add(next);
                        queue.add(next);
                    }
                }
            }
            BlockPos lowest = cluster.stream().min((a, b) -> a.getY() != b.getY() ? a.getY() - b.getY() : a.compareTo(b)).orElseThrow();
            BlockState under = level.getBlockState(lowest.below());
            boolean floating = under.isAir() || under.is(Blocks.IRON_CHAIN) || under.is(BlockTags.FENCES);
            if (cluster.size() >= 6 && cluster.size() < 400 && floating) {
                found.put(lowest, cluster);
            }
        }
        BALLOONS.put(soul, found);
        return found;
    }

    private static void pop(ServerPlayer player, ServerLevel level, BlockPos hit) {
        for (String soul : BALLOON_SOULS) {
            for (Map.Entry<BlockPos, List<BlockPos>> balloon : balloons(level, soul).entrySet()) {
                if (!balloon.getValue().contains(hit)) {
                    continue;
                }
                Set<BlockPos> popped = POPPED.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
                if (!popped.add(balloon.getKey())) {
                    return;
                }
                // Popped for you: the balloon's wool goes, on your screen only.
                for (BlockPos pos : balloon.getValue()) {
                    player.connection.send(new ClientboundBlockUpdatePacket(pos, Blocks.AIR.defaultBlockState()));
                }
                Vec3 c = Vec3.atCenterOf(balloon.getKey()).add(0, 2, 0);
                level.sendParticles(player, ParticleTypes.POOF, false, false, c.x, c.y, c.z, 30, 1.2, 1.2, 1.2, 0.05);
                Msg.playSound(player, SoundEvents.FIREWORK_ROCKET_BLAST, 1f, 1.6f);
                long left = balloons(level, soul).keySet().stream().filter(b -> !popped.contains(b)).count();
                if (left == 0 && RiftFlags.set(player, "balloons:" + soul)) {
                    player.sendSystemMessage(Component.literal("You popped every balloon! An Enigma Soul is free to take.").withStyle(ChatFormatting.LIGHT_PURPLE));
                } else if (left > 0) {
                    player.sendOverlayMessage(Component.literal("Pop! " + left + " balloon" + (left == 1 ? "" : "s") + " left").withStyle(ChatFormatting.YELLOW));
                }
                return;
            }
        }
    }

    /** A button pressed (or shot) near the soul behind the Dreadfarm's wall of buttons. */
    private static void press(ServerPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        Rift.Spot spot = Rift.spots().get(BUTTON_SOUL);
        if (spot == null || pos.distToCenterSqr(spot.pos()) > 16 * 16) {
            return;
        }
        if (buttons == null) {
            List<BlockPos> found = new ArrayList<>();
            BlockPos centre = BlockPos.containing(spot.pos());
            for (BlockPos at : BlockPos.betweenClosed(centre.offset(-16, -4, -6), centre.offset(16, 6, 6))) {
                if (level.getBlockState(at).is(BlockTags.BUTTONS)) {
                    found.add(at.immutable());
                }
            }
            buttons = List.copyOf(found);
        }
        Set<BlockPos> pressed = PRESSED.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        if (!buttons.contains(pos) || !pressed.add(pos.immutable())) {
            return;
        }
        long left = buttons.stream().filter(b -> !pressed.contains(b)).count();
        Msg.playSound(player, SoundEvents.NOTE_BLOCK_HAT.value(), 1f, 0.6f + pressed.size() * 0.08f);
        if (left == 0 && RiftFlags.set(player, "buttons")) {
            player.sendSystemMessage(Component.literal("Click! Every button is pressed. An Enigma Soul is free to take.").withStyle(ChatFormatting.LIGHT_PURPLE));
        } else if (left > 0) {
            player.sendOverlayMessage(Component.literal(left + " button" + (left == 1 ? "" : "s") + " to go").withStyle(ChatFormatting.YELLOW));
        }
    }

    // ---------------------------------------------------------------- the tick

    private static void tick(MinecraftServer server) {
        ServerLevel level = Rift.level(server);
        if (level == null) {
            return;
        }
        long now = server.getTickCount();
        if (!SHOTS.isEmpty() || !KILLS.isEmpty()) {
            shots(server, level, now);
        }
        Zones.Zone farm = null;
        for (ServerPlayer player : level.players()) {
            if (!inFarm(player, player.blockPosition())) {
                continue;
            }
            farm = Zones.at(level, player.blockPosition());
            if (holds(player, WAND)) {
                gaze(player, level, now);
            }
        }
        if (now % 20 == 0) {
            RED.entrySet().removeIf(e -> {
                if (e.getValue() > now) {
                    return false;
                }
                if (level.isLoaded(e.getKey()) && level.getBlockState(e.getKey()).is(Blocks.RED_MUSHROOM)) {
                    level.setBlock(e.getKey(), Blocks.BROWN_MUSHROOM.defaultBlockState(), 3);
                }
                return true;
            });
            for (ServerPlayer player : level.players()) {
                ItemDefinition legs = SkyItems.definition(player.getItemBySlot(EquipmentSlot.LEGS));
                if (legs != null && "CHICKEN_LEGGS".equals(legs.skyblockId()) && RiftFlags.set(player, "shania")) {
                    player.sendSystemMessage(Component.literal("[NPC] Shania: ").withStyle(ChatFormatting.YELLOW)
                            .append(Component.literal("Cluck cluck! Now THAT's a pair of Chicken Leggs. Moo.").withStyle(ChatFormatting.WHITE)));
                }
            }
        }
        if (farm == null) {
            return;
        }
        if (bushes.isEmpty() && now % 100 == 0) {
            findBushes(level, farm);
        }
        if (now % 8 == 0) {
            for (BlockPos pos : MARKED) {
                level.sendParticles(PURPLE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.25, 0.3, 0.25, 0);
                level.sendParticles(ParticleTypes.WITCH, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0);
            }
        }
    }

    // ---------------------------------------------------------------- Shania

    static void shania(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            boolean wand = Sacks.countAnywhere(p, WAND) > 0;
            boolean given = RiftFlags.has(p, "shania_harvest");
            boolean done = RiftFlags.has(p, "shania");
            long caducous = Sacks.countAnywhere(p, "CADUCOUS_STEM_BUNCH");
            long berberis = Sacks.countAnywhere(p, "WILTED_BERBERIS_BUNCH");
            long agaricus = Sacks.countAnywhere(p, "AGARICUS_CAP_BUNCH");
            boolean chicken = Sacks.countAnywhere(p, "PROTOCHICKEN") > 0;
            boolean charm = Timecharms.secured(p, "RIFT_TROPHY_CHICKEN_N_EGG") || Sacks.countAnywhere(p, "RIFT_TROPHY_CHICKEN_N_EGG") > 0;
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("Wand of Farming", wand ? "You have it. Hold it to farm the crops." : "Take it: you can't farm here without one",
                    0x5555FF, !wand, wand ? "Held" : "Take", RiftNpcs.icon(WAND), "", List.of(), -1, ""));
            boolean reclaim = given && !chicken && !charm;
            cards.add(new CardView.Card("Harvest for Shania", given ? (reclaim ? "Lost the Proto-Chicken? Take another." : "She has her harvest.")
                    : "Two bunches of each crop, for a Proto-Chicken", 0x55FF55, !given && caducous >= 2 && berberis >= 2 && agaricus >= 2 || reclaim,
                    given ? (reclaim ? "Reclaim" : "Done") : "Give", RiftNpcs.icon("PROTOCHICKEN"), "", given ? List.of()
                    : List.of(Chip.item(RiftNpcs.icon("CADUCOUS_STEM_BUNCH"), 2, caducous), Chip.item(RiftNpcs.icon("WILTED_BERBERIS_BUNCH"), 2, berberis),
                            Chip.item(RiftNpcs.icon("AGARICUS_CAP_BUNCH"), 2, agaricus)), -1, ""));
            cards.add(new CardView.Card("Chicken Leggs", done ? "Shania approves. Cluck." : "Wear a pair (Wyld Leggings, 32 Metaphoric Eggs, 16 Volt Crux)",
                    0xFFFF55, false, "", RiftNpcs.icon("CHICKEN_LEGGS"), "", List.of(), -1, done ? "Done" : ""));
            cards.add(new CardView.Card("Agaricus Cap", "Stare at a brown mushroom until it turns red, then click it", 0xFF5555, false, "",
                    RiftNpcs.icon("AGARICUS_CAP"), "", List.of(), -1, Sacks.countAnywhere(p, "AGARICUS_CAP") + " held"));
            cards.add(new CardView.Card("Caducous Stem", "Click a rose bush's top, then its bottom", 0xFF55FF, false, "",
                    RiftNpcs.icon("CADUCOUS_STEM"), "", List.of(), -1, Sacks.countAnywhere(p, "CADUCOUS_STEM") + " held"));
            cards.add(new CardView.Card("Wilted Berberis", "Break the dead bushes that sparkle purple", 0xAA55FF, false, "",
                    RiftNpcs.icon("WILTED_BERBERIS"), "", List.of(), -1, Sacks.countAnywhere(p, "WILTED_BERBERIS") + " held"));
            return new CardView("RIFT", "Shania", "Dreadfarm", npc, List.of(), cards,
                    "20 of a crop make a bunch. Punch a Not Chicken up, then shoot it with a Berberis Blowgun.", 1, 34);
        }, (p, card) -> {
            if (card == 0 && Sacks.countAnywhere(p, WAND) == 0) {
                WestVillage.give(p, WAND);
                say(p, "Here, take my Wand of Farming. The crops here are... peculiar. Collect them from top to bottom, and click the mushrooms when they're red!");
            } else if (card == 1) {
                boolean given = RiftFlags.has(p, "shania_harvest");
                if (given) {
                    if (Sacks.countAnywhere(p, "PROTOCHICKEN") == 0) {
                        WestVillage.give(p, "PROTOCHICKEN");
                    }
                    return "";
                }
                for (String bunch : List.of("CADUCOUS_STEM_BUNCH", "WILTED_BERBERIS_BUNCH", "AGARICUS_CAP_BUNCH")) {
                    if (Sacks.countAnywhere(p, bunch) < 2) {
                        return "You need two bunches of each crop.";
                    }
                }
                for (String bunch : List.of("CADUCOUS_STEM_BUNCH", "WILTED_BERBERIS_BUNCH", "AGARICUS_CAP_BUNCH")) {
                    Sacks.takeAnywhere(p, bunch, 2);
                }
                RiftFlags.set(p, "shania_harvest");
                WestVillage.give(p, "PROTOCHICKEN");
                Notice.of(Notice.Kind.SUCCESS).tag("SHANIA").send(p, Component.literal("Shania gives you a Proto-Chicken! Now, about those Chicken Leggs...")
                        .withStyle(ChatFormatting.GREEN));
                Msg.playSound(p, SoundEvents.CHICKEN_EGG, 1f, 1f);
            }
            return "";
        });
    }

    private static void say(ServerPlayer player, String line) {
        player.sendSystemMessage(Component.literal("[NPC] Shania: ").withStyle(ChatFormatting.YELLOW).append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }
}
