package com.skyrpg.skyblock.rift;

import com.mojang.math.Transformation;
import com.skyrpg.core.text.Msg;
import com.skyrpg.hub.mixin.DisplayAccess;
import com.skyrpg.hub.mixin.ItemDisplayAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Mote orbs: pink orbs that float up out of the Rift's ground around you (a few at a time, 6 to 16 blocks away). Walk
 * into one for 25 Motes and 2 seconds of Rift Time (Hypixel's; 10 Motes and no time in Enigma's Crib, decided: not
 * kept apart here). Each lasts a minute.
 */
final class Orbs {
    static final String TAG = "skyrpg_rift_orb";
    private static final int PER_PLAYER = 3;
    private static final long LIFE_TICKS = 60 * 20;
    private static final int MOTES = 25;
    private static final int SECONDS = 2;
    private static final DustParticleOptions PINK = new DustParticleOptions(0xFF5CF0, 1.0f);

    private record Orb(Display.ItemDisplay entity, long born) {
    }

    private static final Map<UUID, List<Orb>> ORBS = new HashMap<>();

    private Orbs() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Orbs::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            // Orbs never outlive the session that made them.
            if (entity.entityTags().contains(TAG) && ORBS.values().stream().noneMatch(list -> list.stream().anyMatch(o -> o.entity() == entity))) {
                entity.discard();
            }
        });
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 4 != 0) {
            return;
        }
        ServerLevel level = Rift.level(server);
        long now = server.getTickCount();
        java.util.Set<UUID> present = new java.util.HashSet<>();
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                present.add(player.getUUID());
                List<Orb> orbs = ORBS.computeIfAbsent(player.getUUID(), id -> new ArrayList<>());
                orbs.removeIf(orb -> {
                    if (orb.entity().isRemoved() || now - orb.born() > LIFE_TICKS || orb.entity().distanceToSqr(player) > 40 * 40) {
                        orb.entity().discard();
                        return true;
                    }
                    if (orb.entity().distanceToSqr(player.getX(), player.getY() + 0.8, player.getZ()) < 1.6 * 1.6) {
                        pickUp(player, orb);
                        return true;
                    }
                    return false;
                });
                if (now % 20 == 0) {
                    for (Orb orb : orbs) {
                        level.sendParticles(player, PINK, false, false, orb.entity().getX(), orb.entity().getY() + 0.3, orb.entity().getZ(), 2,
                                0.15, 0.15, 0.15, 0);
                    }
                    if (orbs.size() < PER_PLAYER && player.getRandom().nextInt(3) == 0) {
                        spawn(level, player, orbs, now);
                    }
                }
            }
        }
        // Gone from the Rift: their orbs go too.
        ORBS.entrySet().removeIf(e -> {
            if (present.contains(e.getKey())) {
                return false;
            }
            e.getValue().forEach(o -> o.entity().discard());
            return true;
        });
    }

    private static void pickUp(ServerPlayer player, Orb orb) {
        orb.entity().discard();
        Motes.give(player, MOTES);
        Rift.addTime(player, SECONDS);
        player.sendOverlayMessage(Component.literal("+" + MOTES + " Motes  ·  +" + SECONDS + "s ф").withStyle(ChatFormatting.LIGHT_PURPLE));
        Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.6f);
    }

    /** An orb on open ground 6-16 blocks from you (none if there's no such spot this time), tracked before it's added. */
    private static void spawn(ServerLevel level, ServerPlayer player, List<Orb> orbs, long now) {
        RandomSource random = player.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = 6 + random.nextDouble() * 10;
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
        BlockPos found = null;
        for (int y = (int) player.getY() + 6; y >= (int) player.getY() - 8; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isSolidRender() && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.above(2)).isAir()) {
                found = pos.above();
                break;
            }
        }
        if (found == null || !level.isLoaded(found)) {
            return;
        }
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        display.snapTo(found.getX() + 0.5, found.getY() + 0.6, found.getZ() + 0.5, 0, 0);
        ((ItemDisplayAccess) display).skyrpg$setItemStack(new ItemStack(Items.STAINED_GLASS.pink()));
        DisplayAccess access = (DisplayAccess) display;
        access.skyrpg$setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateXYZ(0.6f, 0.6f, 0), new Vector3f(0.3f, 0.3f, 0.3f),
                new Quaternionf()));
        access.skyrpg$setBrightnessOverride(new net.minecraft.util.Brightness(15, 15));
        display.setGlowingTag(true);
        display.addTag(TAG);
        orbs.add(new Orb(display, now));
        level.addFreshEntity(display);
    }
}
