package com.skyrpg.skyblock.rift;

import com.skyrpg.core.text.Msg;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Rift Gallery (Hypixel's): the pad in the Rift Wizard Tower takes you there once the Supreme Timecharm is
 * secured. Each of its eight cases shows the Timecharm you've secured for that slot (its block, on your screen only);
 * Elise waits by the pad home. Rift Time doesn't drain inside.
 */
final class Gallery {
    /** The cases in rift_gallery.py's order, with the block each charm shows. */
    private static final List<String> CHARMS = List.of(Timecharms.SUPREME, "RIFT_TROPHY_MIRRORED", "RIFT_TROPHY_CHICKEN_N_EGG",
            "RIFT_TROPHY_CITIZEN", "RIFT_TROPHY_LAZY_LIVING", "RIFT_TROPHY_SLIME", "RIFT_TROPHY_VAMPIRIC", "RIFT_TROPHY_MOUNTAIN");
    private static final List<Block> BLOCKS = List.of(Blocks.OAK_LEAVES, Blocks.GLASS, Blocks.SOUL_SAND, Blocks.JUKEBOX, Blocks.LAPIS_ORE,
            Blocks.SLIME_BLOCK, Blocks.REDSTONE_BLOCK, Blocks.LAPIS_BLOCK);
    private static final Map<UUID, Long> MOVED = new HashMap<>();
    private static final Map<UUID, Long> TOLD = new HashMap<>();

    private Gallery() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Gallery::tick);
    }

    private static boolean on(ServerPlayer player, Rift.Spot spot) {
        double dx = player.getX() - spot.pos().x;
        double dz = player.getZ() - spot.pos().z;
        return dx * dx + dz * dz < 0.9 * 0.9 && Math.abs(player.getY() - spot.pos().y) < 1.2;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) {
            return;
        }
        ServerLevel level = Rift.level(server);
        Map<String, Rift.Spot> spots = Rift.spots();
        Rift.Spot pad = spots.get("gallery_pad");
        Rift.Spot arrive = spots.get("gallery_arrive");
        Rift.Spot exit = spots.get("gallery_exit");
        if (level == null || pad == null || arrive == null || exit == null) {
            return;
        }
        long now = server.getTickCount();
        for (Vec3 at : List.of(pad.pos(), exit.pos())) {
            level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 0.3, at.z, 6, 0.35, 0.1, 0.35, 0.3);
        }
        for (ServerPlayer player : level.players()) {
            boolean recent = now - MOVED.getOrDefault(player.getUUID(), -100L) < 40;
            if (!recent && on(player, pad)) {
                if (!Timecharms.secured(player, Timecharms.SUPREME)) {
                    if (now - TOLD.getOrDefault(player.getUUID(), -100L) > 60) {
                        TOLD.put(player.getUUID(), now);
                        player.sendOverlayMessage(Component.literal("The Rift Gallery opens once Elise has your Wyldly Supreme Timecharm.")
                                .withStyle(ChatFormatting.LIGHT_PURPLE));
                    }
                    continue;
                }
                MOVED.put(player.getUUID(), now);
                player.teleportTo(level, arrive.pos().x, arrive.pos().y, arrive.pos().z, Set.of(), arrive.yaw(), 0, true);
                Msg.playSound(player, SoundEvents.PORTAL_TRAVEL, 0.2f, 1.7f);
                show(player, spots);
                player.sendOverlayMessage(Component.literal("The Rift Gallery").withStyle(ChatFormatting.LIGHT_PURPLE));
            } else if (!recent && on(player, exit)) {
                MOVED.put(player.getUUID(), now);
                player.teleportTo(level, pad.pos().x, pad.pos().y, pad.pos().z + 2, Set.of(), 0, 0, true);
                Msg.playSound(player, SoundEvents.PORTAL_TRAVEL, 0.2f, 1.9f);
            } else if (now % 40 == 0 && player.position().distanceToSqr(arrive.pos()) < 40 * 40) {
                show(player, spots);
            }
        }
    }

    /** The cases, as this player sees them: their secured charms, empty for the rest. */
    private static void show(ServerPlayer player, Map<String, Rift.Spot> spots) {
        for (int k = 0; k < CHARMS.size(); k++) {
            Rift.Spot slot = spots.get("gallery_slot_" + (k + 1));
            if (slot != null) {
                Block block = Timecharms.secured(player, CHARMS.get(k)) ? BLOCKS.get(k) : Blocks.AIR;
                player.connection.send(new ClientboundBlockUpdatePacket(BlockPos.containing(slot.pos()), block.defaultBlockState()));
            }
        }
    }
}
