package com.skyrpg.skyblock.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;

/**
 * A SkyBlock profile: one island and everything shared on it, with one or more members (more than one is a co-op).
 * Each member has their own player data in it (inventory, skills, coins in the purse...), stored by
 * {@link ProfileFiles}; switching profiles swaps that data.
 *
 * @param name a fruit, as on Hypixel ("Banana"); unique among each member's profiles
 * @param island the island's cell in the islands dimension (see {@code Islands})
 * @param built whether the starter island has been placed in that cell yet
 */
public record Profile(UUID id, String name, List<UUID> members, int island, boolean built, long created) {
    public static final Codec<Profile> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(Profile::id),
            Codec.STRING.fieldOf("name").forGetter(Profile::name),
            UUIDUtil.STRING_CODEC.listOf().fieldOf("members").forGetter(Profile::members),
            Codec.INT.fieldOf("island").forGetter(Profile::island),
            Codec.BOOL.optionalFieldOf("built", false).forGetter(Profile::built),
            Codec.LONG.fieldOf("created").forGetter(Profile::created)).apply(i, Profile::new));

    public Profile {
        members = List.copyOf(members);
    }

    public boolean isCoop() {
        return members.size() > 1;
    }

    public boolean hasMember(UUID player) {
        return members.contains(player);
    }

    /** The first member, who created it. */
    public UUID owner() {
        return members.getFirst();
    }

    Profile withMember(UUID player) {
        List<UUID> more = new ArrayList<>(members);
        if (!more.contains(player)) {
            more.add(player);
        }
        return new Profile(id, name, more, island, built, created);
    }

    Profile asBuilt() {
        return new Profile(id, name, members, island, true, created);
    }
}
