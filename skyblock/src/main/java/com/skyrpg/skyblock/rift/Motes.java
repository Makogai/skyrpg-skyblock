package com.skyrpg.skyblock.rift;

import com.mojang.serialization.Codec;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;

/**
 * Motes, the Rift's currency (Hypixel's): kept with you, earned from Rift mobs, mote orbs, the Motes Grubber and quests,
 * spent only in the Rift. The sidebar shows them there instead of the purse.
 */
public final class Motes {
    public static final AttachmentType<Long> MOTES = AttachmentRegistry.create(SkyRpgSkyblock.id("motes"),
            builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()).copyOnDeath());

    private Motes() {
    }

    static void init() {
        // Registering the attachment is all (it happens when the class loads).
    }

    public static long get(Player player) {
        return player.getAttachedOrElse(MOTES, 0L);
    }

    public static void give(Player player, long amount) {
        if (amount > 0) {
            player.setAttached(MOTES, get(player) + amount);
        }
    }

    /** Takes {@code amount} if you have it; false (and nothing taken) if not. */
    public static boolean take(Player player, long amount) {
        long have = get(player);
        if (have < amount) {
            return false;
        }
        player.setAttached(MOTES, have - amount);
        return true;
    }
}
