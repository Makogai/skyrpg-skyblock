package com.skyrpg.skyblock.profile;

import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.mixin.CommonListenerAccess;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;

/**
 * SkyBlock profiles, as on Hypixel: a player has up to {@link #MAX} of them, each with its own island and its own
 * copy of everything the player carries and has learned. Everything SkyRPG keeps about a player lives in the
 * player's data (inventory, ender chest, attachments for skills, purse, pets...), so a profile is that data: the
 * server's player-data loading and saving is pointed at the selected profile's file (see the
 * {@code PlayerDataStorageMixin}), and switching reconnects the player into the other one (Minecraft's transfer
 * packet back to the same server, so it's seamless).
 * <p>
 * The first time a player joins, their existing data becomes their first profile.
 */
public final class Profiles {
    public static final int MAX = 5;
    /** Hypixel's profile names. */
    static final List<String> FRUITS = List.of("Apple", "Banana", "Blueberry", "Coconut", "Cucumber", "Grapes", "Kiwi",
            "Lemon", "Lime", "Mango", "Orange", "Papaya", "Peach", "Pear", "Pineapple", "Pomegranate", "Raspberry",
            "Strawberry", "Tomato", "Watermelon", "Zucchini");

    private static MinecraftServer server;
    /** The profile each online player's data came from, so saves go back there even after they select another. */
    private static final Map<UUID, UUID> LOADED = new ConcurrentHashMap<>();
    /** Players whose profile had no data for them yet (a new profile, or a new co-op member). */
    private static final Set<UUID> FRESH = ConcurrentHashMap.newKeySet();

    private Profiles() {
    }

    public static void start(MinecraftServer started) {
        server = started;
    }

    public static void stop() {
        server = null;
        LOADED.clear();
        FRESH.clear();
    }

    private static Path root() {
        return server.getWorldPath(LevelResource.ROOT).resolve("skyrpg_profiles");
    }

    // ---- loading and saving (called by the PlayerDataStorageMixin) ----

    /** Whether profiles are in charge of player data (once the server is up; never on a client). */
    public static boolean active() {
        return server != null;
    }

    /**
     * The data for a player logging in: their selected profile's file. A player seen for the first time gets a first
     * profile started from their vanilla player file ({@code vanillaFile}), if they have one.
     */
    public static synchronized Optional<CompoundTag> load(NameAndId who, Path vanillaFile) {
        ProfileData data = ProfileData.get(server);
        UUID player = who.id();
        ProfileData.Player mine = data.players.get(player);
        if (mine == null || mine.profiles().isEmpty()) {
            Profile first = newProfile(data, player);
            ProfileFiles.seed(ProfileFiles.file(root(), first.id(), player), vanillaFile);
            mine = new ProfileData.Player(List.of(first.id()), first.id());
            data.putPlayer(player, mine);
            SkyRpgSkyblock.LOGGER.info("{} starts on profile {}", who.name(), first.name());
        }
        UUID selected = data.profiles.containsKey(mine.selected()) ? mine.selected() : mine.profiles().getFirst();
        LOADED.put(player, selected);
        Optional<CompoundTag> tag = ProfileFiles.read(ProfileFiles.file(root(), selected, player));
        if (tag.isEmpty()) {
            FRESH.add(player);
        } else {
            FRESH.remove(player);
        }
        return tag;
    }

    /** Saves a player's data into the profile it was loaded from. */
    public static void save(Player player, CompoundTag tag) {
        UUID profile = LOADED.get(player.getUUID());
        if (profile == null) {
            ProfileData.Player mine = ProfileData.get(server).players.get(player.getUUID());
            if (mine == null) {
                return; // never loaded through profiles (shouldn't happen); vanilla's file is left alone
            }
            profile = mine.selected();
        }
        ProfileFiles.write(ProfileFiles.file(root(), profile, player.getUUID()), tag);
    }

    /** True once for a player who just joined a profile that had no data for them: they start on its island. */
    public static boolean takeFresh(ServerPlayer player) {
        return FRESH.remove(player.getUUID());
    }

    // ---- queries ----

    /** The profile this player is playing right now. */
    public static Profile current(ServerPlayer player) {
        UUID id = LOADED.get(player.getUUID());
        return id == null ? null : ProfileData.get(server).profiles.get(id);
    }

    /** This player's profiles, in the order they got them. */
    public static List<Profile> of(UUID player) {
        ProfileData data = ProfileData.get(server);
        ProfileData.Player mine = data.players.get(player);
        List<Profile> out = new ArrayList<>();
        if (mine != null) {
            mine.profiles().stream().map(data.profiles::get).filter(p -> p != null).forEach(out::add);
        }
        return out;
    }

    public static Profile get(UUID profile) {
        return ProfileData.get(server).profiles.get(profile);
    }

    /** The profile whose island is in this cell, or null. */
    public static Profile byIsland(int island) {
        return ProfileData.get(server).byIsland(island);
    }

    public static Profile named(UUID player, String name) {
        return of(player).stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    /** Records that a profile's island has been placed. */
    public static void built(Profile profile) {
        ProfileData.get(server).put(profile.asBuilt());
    }

    // ---- changes ----

    /** Starts a new profile for this player and moves them into it; null (with a message) if they can't. */
    public static Profile create(ServerPlayer player) {
        ProfileData data = ProfileData.get(server);
        ProfileData.Player mine = data.players.get(player.getUUID());
        if (mine != null && mine.profiles().size() >= MAX) {
            player.sendSystemMessage(Component.literal("You already have " + MAX + " profiles, the most you can have.").withStyle(ChatFormatting.RED));
            return null;
        }
        Profile profile = newProfile(data, player.getUUID());
        data.putPlayer(player.getUUID(), mine == null ? new ProfileData.Player(List.of(profile.id()), profile.id()) : mine.with(profile.id()));
        reconnect(player, "Creating your new profile, " + profile.name() + "...");
        return profile;
    }

    /** Moves this player into another of their profiles. */
    public static boolean switchTo(ServerPlayer player, Profile profile) {
        if (!profile.hasMember(player.getUUID())) {
            return false;
        }
        if (profile.id().equals(LOADED.get(player.getUUID()))) {
            player.sendSystemMessage(Component.literal("You're already playing on " + profile.name() + ".").withStyle(ChatFormatting.RED));
            return false;
        }
        ProfileData data = ProfileData.get(server);
        data.putPlayer(player.getUUID(), data.players.get(player.getUUID()).selecting(profile.id()));
        reconnect(player, "Switching to your " + profile.name() + " profile...");
        return true;
    }

    /** Adds a player to a co-op profile as a new member and moves them into it. */
    public static boolean joinCoop(ServerPlayer player, Profile profile) {
        ProfileData data = ProfileData.get(server);
        ProfileData.Player mine = data.players.get(player.getUUID());
        if (mine != null && mine.profiles().size() >= MAX) {
            player.sendSystemMessage(Component.literal("You already have " + MAX + " profiles; leave one before joining a co-op.").withStyle(ChatFormatting.RED));
            return false;
        }
        Profile joined = profile.withMember(player.getUUID());
        data.put(joined);
        data.putPlayer(player.getUUID(), mine == null ? new ProfileData.Player(List.of(joined.id()), joined.id()) : mine.with(joined.id()));
        reconnect(player, "Joining the co-op on " + joined.name() + "...");
        return true;
    }

    private static Profile newProfile(ProfileData data, UUID player) {
        Set<String> taken = new HashSet<>();
        ProfileData.Player mine = data.players.get(player);
        if (mine != null) {
            mine.profiles().stream().map(data.profiles::get).filter(p -> p != null).forEach(p -> taken.add(p.name()));
        }
        List<String> free = FRUITS.stream().filter(f -> !taken.contains(f)).toList();
        String name = free.isEmpty() ? "Profile " + (taken.size() + 1) : free.get(RandomSource.create().nextInt(free.size()));
        Profile profile = new Profile(UUID.randomUUID(), name, List.of(player), data.nextIsland++, false, System.currentTimeMillis());
        data.put(profile);
        return profile;
    }

    /**
     * Sends the player back to this server so they load their selected profile: a transfer to the address they
     * connected with, or (if the server doesn't take transfers) a disconnect asking them to rejoin.
     */
    private static void reconnect(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.YELLOW));
        ConnectionHost host = (ConnectionHost) ((CommonListenerAccess) player.connection).skyrpg$connection();
        if (server.acceptsTransfers() && host.skyrpg$host() != null) {
            player.connection.send(new ClientboundTransferPacket(host.skyrpg$host(), host.skyrpg$port()));
        } else {
            player.connection.disconnect(Component.literal(message.replace("...", ".")).append("\nRejoin to play it."));
        }
    }

    /** What a player connected with (host name and port), remembered from their handshake for {@link #reconnect}. */
    public interface ConnectionHost {
        String skyrpg$host();

        int skyrpg$port();

        void skyrpg$setAddress(String host, int port);
    }
}
