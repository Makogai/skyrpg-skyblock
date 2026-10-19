package com.skyrpg.skyblock.end;

import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Dragon's Nest altar: the End Portal Frames around its middle in The End (0, 0 on our own End; on Hypixel's map
 * the spawn spot "dragon_altar" in spawn_spots/the_end.json says where). Right-click one with a Summoning Eye to place
 * it, right-click your own eye to take it back; the eighth eye hatches the dragon ({@link DragonFight}), and the frames
 * empty again when the fight is over. Who placed how many eyes counts for their loot.
 */
public final class DragonAltar {
    /** How far from 0, 0 a frame counts as the altar's. */
    private static final int RADIUS = 12;
    public static final int EYES = 8;
    /** Who placed the eye in each filled frame, in order. */
    private static final Map<BlockPos, UUID> PLACED = new LinkedHashMap<>();
    private static List<BlockPos> frames;
    /** The altar's middle (x, z). */
    private static BlockPos middle = BlockPos.ZERO;

    private DragonAltar() {
    }

    static void init() {
        com.skyrpg.core.data.JsonDataLoader.register(SkyRpgSkyblock.id("end_spots"), "skyrpg/spawn_spots", files -> {
            BlockPos found = BlockPos.ZERO;
            for (com.google.gson.JsonElement element : files.values()) {
                com.google.gson.JsonObject root = element.getAsJsonObject();
                if (root.has("dimension") && root.get("dimension").getAsString().equals(DragonFight.THE_END.identifier().toString())
                        && root.getAsJsonObject("spots").has("dragon_altar")) {
                    com.google.gson.JsonArray pos = root.getAsJsonObject("spots").getAsJsonObject("dragon_altar").getAsJsonArray("pos");
                    found = BlockPos.containing(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble());
                }
            }
            middle = found;
            frames = null;
        });
        // Before the Hub's protection, which refuses every block use on a public island.
        Identifier early = SkyRpgSkyblock.id("end_altar_early");
        UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(early, (player, level, hand, hit) -> {
            if (!level.dimension().equals(DragonFight.THE_END)) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.END_PORTAL_FRAME) || !inAltar(pos)) {
                return InteractionResult.PASS;
            }
            if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer server) || !(level instanceof ServerLevel world)) {
                return InteractionResult.SUCCESS;
            }
            use(server, world, pos.immutable(), state);
            return InteractionResult.SUCCESS;
        });
    }

    private static boolean inAltar(BlockPos pos) {
        return Math.abs(pos.getX()) <= RADIUS && Math.abs(pos.getZ()) <= RADIUS;
    }

    private static void use(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (DragonFight.active()) {
            Notice.send(player, Notice.Kind.ERROR, Component.literal("The dragon is already awake!"));
            return;
        }
        boolean filled = state.getValue(EndPortalFrameBlock.HAS_EYE);
        ItemStack held = player.getMainHandItem();
        if (filled) {
            UUID owner = PLACED.get(pos);
            if (owner == null || !owner.equals(player.getUUID())) {
                Notice.send(player, Notice.Kind.ERROR, Component.literal("You can only take back your own Summoning Eye!"));
                return;
            }
            level.setBlock(pos, state.setValue(EndPortalFrameBlock.HAS_EYE, false), Block.UPDATE_ALL);
            PLACED.remove(pos);
            ItemDefinition eye = ItemRegistry.skyblock("SUMMONING_EYE");
            if (eye != null) {
                ItemStack back = SkyItems.create(eye, 1);
                if (!player.getInventory().add(back)) {
                    player.drop(back, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
            Notice.send(player, Notice.Kind.INFO, Component.literal("You took back your Summoning Eye.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!SkyStacks.is(held, "SUMMONING_EYE")) {
            Notice.of(Notice.Kind.INFO).tag("ALTAR").send(player, Component.literal("Place a ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("Summoning Eye").withStyle(ChatFormatting.DARK_PURPLE))
                    .append(Component.literal(" here. Special Zealots drop them.").withStyle(ChatFormatting.GRAY)));
            return;
        }
        if (!player.isCreative()) {
            held.shrink(1);
        }
        level.setBlock(pos, state.setValue(EndPortalFrameBlock.HAS_EYE, true), Block.UPDATE_ALL);
        PLACED.put(pos, player.getUUID());
        int placed = count(level);
        for (ServerPlayer near : level.players()) {
            Notice.of(Notice.Kind.DUNGEON).tag("ALTAR").send(near, Component.literal(player.getName().getString()).withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(" placed a Summoning Eye! ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("(" + placed + "/" + EYES + ")").withStyle(placed >= EYES ? ChatFormatting.GREEN : ChatFormatting.YELLOW)));
        }
        Msg.playSound(player, SoundEvents.END_PORTAL_FRAME_FILL, 1.0f, 1.0f);
        if (placed >= EYES) {
            Map<UUID, Integer> eyes = new HashMap<>();
            PLACED.values().forEach(id -> eyes.merge(id, 1, Integer::sum));
            DragonFight.start(level, center(level), eyes);
        }
    }

    /** The altar's frames (found once: the ones around its middle). */
    private static List<BlockPos> frames(ServerLevel level) {
        if (frames == null) {
            List<BlockPos> found = new ArrayList<>();
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            for (int x = middle.getX() - RADIUS; x <= middle.getX() + RADIUS; x++) {
                for (int z = middle.getZ() - RADIUS; z <= middle.getZ() + RADIUS; z++) {
                    for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                        if (level.getBlockState(at.set(x, y, z)).is(Blocks.END_PORTAL_FRAME)) {
                            found.add(at.immutable());
                        }
                    }
                }
            }
            frames = found;
        }
        return frames;
    }

    private static int count(ServerLevel level) {
        int filled = 0;
        for (BlockPos pos : frames(level)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.END_PORTAL_FRAME) && state.getValue(EndPortalFrameBlock.HAS_EYE)) {
                filled++;
            }
        }
        return filled;
    }

    /** The middle of the altar, at the frames' height. */
    static BlockPos center(ServerLevel level) {
        List<BlockPos> all = frames(level);
        int y = all.isEmpty() ? level.getSeaLevel() : all.getFirst().getY();
        return new BlockPos(middle.getX(), y, middle.getZ());
    }

    /** Empties the frames (after a fight, or eyes left in them over a restart). */
    static void reset(ServerLevel level) {
        for (BlockPos pos : frames(level)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.END_PORTAL_FRAME) && state.getValue(EndPortalFrameBlock.HAS_EYE)) {
                level.setBlock(pos, state.setValue(EndPortalFrameBlock.HAS_EYE, false), Block.UPDATE_ALL);
            }
        }
        PLACED.clear();
        removeEgg(level);
    }

    /** Takes the Dragon Egg off the altar (it hatched, or the fight never started). */
    static void removeEgg(ServerLevel level) {
        BlockPos egg = center(level);
        if (level.getBlockState(egg).is(Blocks.DRAGON_EGG)) {
            level.setBlock(egg, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
