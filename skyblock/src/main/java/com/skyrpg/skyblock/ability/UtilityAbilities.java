package com.skyrpg.skyblock.ability;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.skyblock.galatea.Critters;
import com.skyrpg.skyblock.galatea.Galatea;
import com.skyrpg.skyblock.island.IslandRules;
import com.skyrpg.skyblock.island.Islands;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The tool and tracker abilities: Critter Capsules (Galatea's critters), Bubbles of Air, the Dead Cat Detector (the
 * nearest piece of Montezuma's soul in the Rift), the Royal Compass (the nearest Emissary in the Dwarven Mines), the
 * Recall Potion (the Crystal Hollows: sneak to save a spot, use to go back), the builder's tools on your own island
 * (Builder's Wand, Builder's Ruler, Block Zapper, Infinidirt Wand, Snow Shovel), the tank's Stun and Absorption Potions,
 * and the Fish Hat's splash and the Sunflower Head's sparkles.
 */
final class UtilityAbilities {
    /** The most blocks a builder's tool works on at once (Hypixel's Builder's Wand: 164). */
    private static final int MAX_BLOCKS = 164;
    private static final Map<UUID, Vec3> RECALL = new HashMap<>();

    private UtilityAbilities() {
    }

    static void init() {
        Abilities.register("capture", (p, a) -> consumeAfter(p, Critters.capsule(p, false)));
        Abilities.register("certain_capture", (p, a) -> consumeAfter(p, Critters.capsule(p, true)));
        Abilities.register("inhale", (p, a) -> {
            if (!Galatea.isGalatea(p.level()) || !p.isUnderWater()) {
                p.sendOverlayMessage(Msg.text("Use it under water on Galatea!", ChatFormatting.RED));
                return false;
            }
            p.setAirSupply(p.getMaxAirSupply());
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 1.5, p.getZ(), 30, 0.4, 0.4, 0.4, 0.1);
            return consumeAfter(p, true);
        });
        Abilities.register("meownar", (p, a) -> track(p, e -> e.entityTags().contains("skyrpg_montezuma_piece"), 200, "a piece of Montezuma's soul"));
        Abilities.register("true_dwarth", (p, a) -> track(p, e -> e instanceof Mannequin && e.getCustomName() != null
                && e.getCustomName().getString().contains("Emissary"), 500, "the nearest Emissary"));
        Abilities.register("ancient_recall", UtilityAbilities::recall);
        Abilities.register("stun_potion", (p, a) -> {
            ServerLevel level = (ServerLevel) p.level();
            for (Mob mob : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(10), Mob::isAlive)) {
                mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 9));
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 9));
            }
            level.sendParticles(ParticleTypes.WITCH, p.getX(), p.getY() + 1, p.getZ(), 60, 4, 1, 4, 0.1);
            level.playSound(null, p.blockPosition(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
            return true;
        });
        Abilities.register("absorption_potion", (p, a) -> {
            ServerLevel level = (ServerLevel) p.level();
            for (ServerPlayer mate : level.players().stream().filter(o -> o.distanceTo(p) <= 10).map(o -> (ServerPlayer) o).toList()) {
                // 200 Health for 3 seconds: Absorption X (absorption needs the effect to raise its cap)
                mate.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60, 9, false, false, true));
            }
            level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1, p.getZ(), 20, 4, 1, 4, 0);
            level.playSound(null, p.blockPosition(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 1.0f, 1.2f);
            return true;
        });
        Abilities.register("splash", (p, a) -> {
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1.8, p.getZ(), 60, 0.6, 0.3, 0.6, 0.2);
            p.level().playSound(null, p.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.6f, 1.2f);
            return true;
        });
        Abilities.register("sparkle", (p, a) -> {
            ServerPlayer target = ((ServerLevel) p.level()).players().stream().filter(o -> o != p && o.distanceTo(p) < 8)
                    .min(Comparator.comparingDouble(o -> o.distanceToSqr(p))).map(o -> (ServerPlayer) o).orElse(p);
            ((ServerLevel) p.level()).sendParticles(ParticleTypes.HAPPY_VILLAGER, target.getX(), target.getY() + 1, target.getZ(), 25, 0.5, 0.8, 0.5, 0);
            return true;
        });
        // the builder's tools: your own island only
        Abilities.register("place_dirt", (p, a) -> build(p, (player, level, hit) -> {
            if (!Wallet.take(player, 1)) {
                return 0;
            }
            BlockPos at = hit.getBlockPos().relative(hit.getDirection());
            return place(player, level, at, Blocks.DIRT.defaultBlockState()) ? 1 : 0;
        }));
        Abilities.register("snow_placer", (p, a) -> build(p, (player, level, hit) -> {
            int placed = 0;
            BlockPos center = hit.getBlockPos().above();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos at = center.offset(dx, 0, dz);
                    BlockState here = level.getBlockState(at);
                    if (here.is(Blocks.SNOW) && here.getValue(SnowLayerBlock.LAYERS) < 8) {
                        placed += place(player, level, at, here.setValue(SnowLayerBlock.LAYERS, here.getValue(SnowLayerBlock.LAYERS) + 1)) ? 1 : 0;
                    } else if (here.isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, at)) {
                        placed += place(player, level, at, Blocks.SNOW.defaultBlockState()) ? 1 : 0;
                    }
                }
            }
            return placed;
        }));
        Abilities.register("grand_architect", (p, a) -> build(p, UtilityAbilities::extendFaces));
        Abilities.register("area_planner", (p, a) -> build(p, UtilityAbilities::line));
        Abilities.register("grand_zapper", (p, a) -> build(p, UtilityAbilities::zap));
    }

    private static boolean consumeAfter(ServerPlayer player, boolean used) {
        if (used && !player.isCreative()) {
            player.getMainHandItem().shrink(1);
        }
        return used;
    }

    /** A trail of particles toward the nearest entity that matches, and how far it is. */
    private static boolean track(ServerPlayer player, Predicate<Entity> what, double range, String name) {
        ServerLevel level = (ServerLevel) player.level();
        Entity nearest = null;
        for (Entity e : level.getAllEntities()) {
            if (e != null && what.test(e) && e.distanceTo(player) <= range && (nearest == null || e.distanceToSqr(player) < nearest.distanceToSqr(player))) {
                nearest = e;
            }
        }
        if (nearest == null) {
            player.sendOverlayMessage(Msg.text("Nothing found nearby.", ChatFormatting.RED));
            return true;
        }
        Vec3 from = player.getEyePosition();
        Vec3 dir = nearest.position().subtract(from).normalize();
        for (double d = 1; d < Math.min(12, nearest.distanceTo(player)); d += 0.5) {
            Vec3 at = from.add(dir.scale(d));
            level.sendParticles(player, ParticleTypes.END_ROD, false, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        player.sendOverlayMessage(Msg.text(Msg.number(Math.round(nearest.distanceTo(player))) + " blocks to " + name, ChatFormatting.AQUA));
        return true;
    }

    /** The Recall Potion, in the Crystal Hollows: sneak to save where you are; use it to go back there. */
    private static boolean recall(ServerPlayer player, ItemDefinition.Ability ability) {
        if (!player.level().dimension().identifier().getPath().equals("crystal_hollows")) {
            player.sendOverlayMessage(Msg.text("This only works in the Crystal Hollows!", ChatFormatting.RED));
            return false;
        }
        if (player.isShiftKeyDown()) {
            RECALL.put(player.getUUID(), player.position());
            player.sendOverlayMessage(Msg.text("Location saved!", ChatFormatting.GREEN));
            return true;
        }
        Vec3 saved = RECALL.get(player.getUUID());
        if (saved == null) {
            player.sendOverlayMessage(Msg.text("No saved location: sneak and use it to save one.", ChatFormatting.RED));
            return false;
        }
        player.teleport(new TeleportTransition((ServerLevel) player.level(), saved, Vec3.ZERO, player.getYRot(), player.getXRot(), Set.of(),
                TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        return true;
    }

    // ---------------------------------------------------------------- builder's tools

    private interface Builder {
        int work(ServerPlayer player, ServerLevel level, BlockHitResult hit);
    }

    /** Runs a builder's tool on the block you're looking at, on an island you may build on. */
    private static boolean build(ServerPlayer player, Builder builder) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(6)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        if (!Islands.isIslands(level) || !IslandRules.isMember(player, hit.getBlockPos())) {
            player.sendOverlayMessage(Msg.text("You can only use this on your own island!", ChatFormatting.RED));
            return false;
        }
        int done = builder.work(player, level, hit);
        if (done > 0) {
            level.playSound(null, hit.getBlockPos(), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return done > 0;
    }

    private static boolean place(ServerPlayer player, ServerLevel level, BlockPos at, BlockState state) {
        if (!level.getBlockState(at).canBeReplaced() && !level.getBlockState(at).is(Blocks.SNOW) || !IslandRules.isMember(player, at)) {
            return false;
        }
        level.setBlock(at, state, Block.UPDATE_ALL);
        return true;
    }

    /** The blocks of a kind you carry (creative: plenty). */
    private static int carried(ServerPlayer player, Block block) {
        if (player.isCreative()) {
            return MAX_BLOCKS;
        }
        int count = 0;
        for (ItemStack stack : player.getInventory()) {
            if (stack.getItem() instanceof BlockItem item && item.getBlock() == block) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void take(ServerPlayer player, Block block, int count) {
        if (player.isCreative()) {
            return;
        }
        for (ItemStack stack : player.getInventory()) {
            if (count <= 0) {
                return;
            }
            if (stack.getItem() instanceof BlockItem item && item.getBlock() == block) {
                int used = Math.min(count, stack.getCount());
                stack.shrink(used);
                count -= used;
            }
        }
    }

    /** Builder's Wand: every connected block of that kind on that face grows one block outward (from your inventory). */
    private static int extendFaces(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
        BlockState state = level.getBlockState(hit.getBlockPos());
        Direction face = hit.getDirection();
        List<BlockPos> faces = connected(level, hit.getBlockPos(), state, face, p -> level.getBlockState(p.relative(face)).canBeReplaced());
        int budget = Math.min(faces.size(), carried(player, state.getBlock()));
        int placed = 0;
        for (BlockPos pos : faces) {
            if (placed >= budget) {
                break;
            }
            if (place(player, level, pos.relative(face), state)) {
                placed++;
            }
        }
        take(player, state.getBlock(), placed);
        return placed;
    }

    /** Builder's Ruler: a line of the clicked kind of block along the way you face, up to 32 (from your inventory). */
    private static int line(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
        BlockState state = level.getBlockState(hit.getBlockPos());
        Direction way = player.getDirection();
        int budget = Math.min(32, carried(player, state.getBlock()));
        int placed = 0;
        BlockPos at = hit.getBlockPos().above();
        for (int i = 0; i < 32 && placed < budget; i++, at = at.relative(way)) {
            if (place(player, level, at, state)) {
                placed++;
            }
        }
        take(player, state.getBlock(), placed);
        return placed;
    }

    /** Block Zapper: every connected block of the clicked kind (164 at most) is gone. */
    private static int zap(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (state.getDestroySpeed(level, hit.getBlockPos()) < 0) {
            return 0;
        }
        int zapped = 0;
        for (BlockPos pos : connected(level, hit.getBlockPos(), state, null, p -> IslandRules.isMember(player, p))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            zapped++;
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 30, 1, 1, 1, 0.1);
        return zapped;
    }

    /** Blocks of the same state touching each other (on one face's plane when {@code face} is set), 164 at most. */
    private static List<BlockPos> connected(ServerLevel level, BlockPos start, BlockState state, Direction face, Predicate<BlockPos> keep) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(start));
        while (!queue.isEmpty() && out.size() < MAX_BLOCKS) {
            BlockPos pos = queue.poll();
            if (!seen.add(pos) || level.getBlockState(pos) != state || !keep.test(pos)) {
                continue;
            }
            out.add(pos);
            for (Direction d : Direction.values()) {
                if (face == null || d.getAxis() != face.getAxis()) {
                    queue.add(pos.relative(d));
                }
            }
        }
        return out;
    }

}
