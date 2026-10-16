package com.skyrpg.skyblock.profile;

import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.Util;

/**
 * Each member's player data in each profile: {@code <world>/skyrpg_profiles/<profile>/<player>.dat}, written the way
 * vanilla writes {@code players/data/<player>.dat} (a temp file swapped in, the previous one kept as {@code .dat_old}).
 */
final class ProfileFiles {
    private ProfileFiles() {
    }

    static Path file(Path root, UUID profile, UUID player) {
        return root.resolve(profile.toString()).resolve(player + ".dat");
    }

    static void write(Path file, CompoundTag tag) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = Files.createTempFile(file.getParent(), file.getFileName().toString().replace(".dat", "-"), ".dat");
            NbtIo.writeCompressed(tag, temp);
            Util.safeReplaceFile(file, temp, file.resolveSibling(file.getFileName() + "_old"));
        } catch (IOException e) {
            SkyRpgSkyblock.LOGGER.error("Failed to save profile data {}", file, e);
        }
    }

    /** The data in this file, else its {@code .dat_old} backup, else nothing (a new member of the profile). */
    static Optional<CompoundTag> read(Path file) {
        for (Path candidate : new Path[]{file, file.resolveSibling(file.getFileName() + "_old")}) {
            if (Files.isRegularFile(candidate)) {
                try {
                    return Optional.of(NbtIo.readCompressed(candidate, NbtAccounter.unlimitedHeap()));
                } catch (IOException e) {
                    SkyRpgSkyblock.LOGGER.error("Failed to load profile data {}", candidate, e);
                }
            }
        }
        return Optional.empty();
    }

    /** Starts a profile file from an existing player file (the player's data from before profiles existed). */
    static void seed(Path file, Path from) {
        if (!Files.isRegularFile(from) || Files.exists(file)) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.copy(from, file);
        } catch (IOException e) {
            SkyRpgSkyblock.LOGGER.error("Failed to copy {} into the first profile", from, e);
        }
    }
}
