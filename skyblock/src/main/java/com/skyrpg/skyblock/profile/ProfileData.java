package com.skyrpg.skyblock.profile;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Every profile, which profiles each player has and which one they play next; saved with the overworld. */
final class ProfileData extends SavedData {
    /** A player's profiles, in the order they got them, and the one they load into on their next login. */
    record Player(List<UUID> profiles, UUID selected) {
        static final Codec<Player> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.listOf().fieldOf("profiles").forGetter(Player::profiles),
                UUIDUtil.STRING_CODEC.fieldOf("selected").forGetter(Player::selected)).apply(i, Player::new));

        Player {
            profiles = List.copyOf(profiles);
        }

        Player with(UUID profile) {
            List<UUID> more = new ArrayList<>(profiles);
            if (!more.contains(profile)) {
                more.add(profile);
            }
            return new Player(more, profile);
        }

        Player selecting(UUID profile) {
            return new Player(profiles, profile);
        }
    }

    static final Codec<ProfileData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Profile.CODEC.listOf().optionalFieldOf("profiles", List.of()).forGetter(d -> List.copyOf(d.profiles.values())),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Player.CODEC).optionalFieldOf("players", Map.of()).forGetter(d -> d.players),
            Codec.INT.optionalFieldOf("next_island", 0).forGetter(d -> d.nextIsland)).apply(i, ProfileData::new));
    static final SavedDataType<ProfileData> TYPE = new SavedDataType<>(SkyRpgSkyblock.id("profiles"), ProfileData::new, CODEC, null);

    final Map<UUID, Profile> profiles = new LinkedHashMap<>();
    final Map<UUID, Player> players = new HashMap<>();
    private final Map<Integer, UUID> byIsland = new HashMap<>();
    int nextIsland;

    private ProfileData() {
    }

    private ProfileData(List<Profile> profiles, Map<UUID, Player> players, int nextIsland) {
        profiles.forEach(this::put);
        this.players.putAll(players);
        this.nextIsland = nextIsland;
    }

    static ProfileData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    void put(Profile profile) {
        profiles.put(profile.id(), profile);
        byIsland.put(profile.island(), profile.id());
        setDirty();
    }

    void putPlayer(UUID player, Player data) {
        players.put(player, data);
        setDirty();
    }

    Profile byIsland(int island) {
        UUID id = byIsland.get(island);
        return id == null ? null : profiles.get(id);
    }
}
