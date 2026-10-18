package com.skyrpg.skyblock.rift;

import com.mojang.serialization.Codec;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/** Story flags kept with you in the Rift ("deer": you talked to Deer; "eye:The Gill-Man": you calmed that eye...). */
final class RiftFlags {
    static final AttachmentType<List<String>> FLAGS = AttachmentRegistry.create(SkyRpgSkyblock.id("rift_flags"),
            builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

    private RiftFlags() {
    }

    /** Loads the class at startup: an attachment type made after a player's data is read drops their flags. */
    static void init() {
    }

    static boolean has(ServerPlayer player, String flag) {
        return player.getAttachedOrElse(FLAGS, List.of()).contains(flag);
    }

    /** Sets a flag; true if it wasn't set before. */
    static boolean set(ServerPlayer player, String flag) {
        List<String> flags = new ArrayList<>(player.getAttachedOrElse(FLAGS, List.of()));
        if (flags.contains(flag)) {
            return false;
        }
        flags.add(flag);
        player.setAttached(FLAGS, List.copyOf(flags));
        return true;
    }

    static long count(ServerPlayer player, String prefix) {
        return player.getAttachedOrElse(FLAGS, List.of()).stream().filter(f -> f.startsWith(prefix)).count();
    }
}
