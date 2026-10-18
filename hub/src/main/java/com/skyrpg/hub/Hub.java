package com.skyrpg.hub;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** The hub world and moving players in and out of it. */
public final class Hub {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, SkyRpgHub.id("hub"));

    /** Where a player was in the survival world when they last went to the hub. Cleared on death. */
    public record Location(ResourceKey<Level> dimension, Vec3 pos, float yaw, float pitch) {
        static final Codec<Location> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Location::dimension),
                Vec3.CODEC.fieldOf("pos").forGetter(Location::pos),
                Codec.FLOAT.fieldOf("yaw").forGetter(Location::yaw),
                Codec.FLOAT.fieldOf("pitch").forGetter(Location::pitch)
        ).apply(instance, Location::new));
    }

    static final AttachmentType<Location> LAST_SURVIVAL_LOCATION = AttachmentRegistry.create(SkyRpgHub.id("last_survival_location"),
            builder -> builder.persistent(Location.CODEC));

    /** The overworld's original world spawn, saved before the world spawn moved to the hub. Stored on the overworld. */
    static final AttachmentType<BlockPos> SURVIVAL_SPAWN = AttachmentRegistry.create(SkyRpgHub.id("survival_spawn"),
            builder -> builder.persistent(BlockPos.CODEC));

    private Hub() {
    }

    static void init() {
        // Loads the class so the attachments are registered during mod init.
    }

    public static boolean isHub(Level level) {
        return level.dimension() == LEVEL;
    }

    /** The Hub's dimension type: every public island (the Hub, the Barn, the Park...) is built in one of these. */
    public static final ResourceKey<net.minecraft.world.level.dimension.DimensionType> PUBLIC_TYPE =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION_TYPE, SkyRpgHub.id("hub"));
    /** The same for an island in an End sky (Hypixel's The End): the vanilla End's look, without its dragon fight. */
    public static final ResourceKey<net.minecraft.world.level.dimension.DimensionType> PUBLIC_END_TYPE =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION_TYPE, SkyRpgHub.id("public_end"));
    /** The same underground (Hypixel's Dwarven Mines): a dark cave sky and cave music. */
    public static final ResourceKey<net.minecraft.world.level.dimension.DimensionType> PUBLIC_MINES_TYPE =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION_TYPE, SkyRpgHub.id("public_mines"));
    /** The Glacite Tunnels: like the mines, lit a cold blue. */
    public static final ResourceKey<net.minecraft.world.level.dimension.DimensionType> PUBLIC_GLACITE_TYPE =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION_TYPE, SkyRpgHub.id("public_glacite"));
    /** The Crimson Isle: a nether island over a lava sea, under a red fog. */
    public static final ResourceKey<net.minecraft.world.level.dimension.DimensionType> PUBLIC_CRIMSON_TYPE =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION_TYPE, SkyRpgHub.id("public_crimson"));
    /** The Rift: a fractured land under a violet sky, lavender grass. */
    public static final ResourceKey<net.minecraft.world.level.dimension.DimensionType> PUBLIC_RIFT_TYPE =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION_TYPE, SkyRpgHub.id("public_rift"));
    /** The Catacombs (Hypixel's Dungeons): dark, with deep-dark music. */
    public static final ResourceKey<net.minecraft.world.level.dimension.DimensionType> PUBLIC_CATACOMBS_TYPE =
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION_TYPE, SkyRpgHub.id("public_catacombs"));

    /**
     * The Hub or another public island (Hypixel's Farming Islands, The Park, the Gold Mine...): protected like the Hub,
     * nobody hungry, the void sends you back. Known on both sides, from the dimension's type.
     */
    public static boolean isPublic(Level level) {
        return level.dimension() == LEVEL || level.dimensionTypeRegistration().is(PUBLIC_TYPE) || level.dimensionTypeRegistration().is(PUBLIC_END_TYPE)
                || level.dimensionTypeRegistration().is(PUBLIC_MINES_TYPE) || level.dimensionTypeRegistration().is(PUBLIC_GLACITE_TYPE)
                || level.dimensionTypeRegistration().is(PUBLIC_CATACOMBS_TYPE) || level.dimensionTypeRegistration().is(PUBLIC_CRIMSON_TYPE)
                || level.dimensionTypeRegistration().is(PUBLIC_RIFT_TYPE);
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    /** Blocks players may break in the protected hub anyway (SkyBlock's zones: the Farm, the Forest, the Coal Mine). */
    public interface BreakRule {
        boolean mayBreak(Level level, BlockPos pos, BlockState state, Player player);
    }

    private static BreakRule breakRule = (level, pos, state, player) -> false;

    public static BreakRule breakRule() {
        return breakRule;
    }

    /** Sets which hub blocks may be broken; called on both sides, so the client doesn't refuse what the server allows. */
    public static void setBreakRule(BreakRule rule) {
        breakRule = rule;
    }

    /** Where in the hub fighting is on (SkyBlock's mob zones, like the Graveyard): entities there can be hit and hurt. */
    public interface CombatRule {
        boolean allowsCombat(Entity entity);
    }

    private static CombatRule combatRule = entity -> false;

    public static CombatRule combatRule() {
        return combatRule;
    }

    /** Sets where fighting is allowed; called on both sides. Players still can't hurt each other. */
    public static void setCombatRule(CombatRule rule) {
        combatRule = rule;
    }

    /** Where the hub's portal leads, and what it says on the way. SkyBlock points it at your island. */
    public record Gate(String message, Consumer<ServerPlayer> destination) {
    }

    private static Gate gate = new Gate("Sending you to the survival world...", Hub::sendToSurvival);

    public static Gate gate() {
        return gate;
    }

    public static void setGate(String message, Consumer<ServerPlayer> destination) {
        gate = new Gate(message, destination);
    }

    public static void sendToHub(ServerPlayer player) {
        ServerLevel hub = level(player.level().getServer());
        if (hub == null) {
            return;
        }
        if (!isHub(player.level())) {
            player.setAttached(LAST_SURVIVAL_LOCATION, new Location(player.level().dimension(), player.position(), player.getYRot(), player.getXRot()));
        }
        HubConfig config = HubConfig.get();
        teleport(player, hub, config.spawn(), config.spawnYaw(), config.spawnPitch());
    }

    /** Back to where the player left the survival world, or to the survival spawn if there's nowhere to go back to. */
    public static void sendToSurvival(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        Location last = player.removeAttached(LAST_SURVIVAL_LOCATION);
        ServerLevel lastLevel = last == null ? null : server.getLevel(last.dimension());
        if (lastLevel != null) {
            teleport(player, lastLevel, last.pos(), last.yaw(), last.pitch());
            return;
        }
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getAttachedOrElse(SURVIVAL_SPAWN, BlockPos.ZERO);
        // An unloaded chunk's heightmap reads as the bottom of the world, which sent players into the void.
        overworld.getChunk(spawn);
        BlockPos surface = overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
        teleport(player, overworld, Vec3.atBottomCenterOf(surface), player.getYRot(), 0);
    }

    static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch) {
        player.teleport(new TeleportTransition(level, pos, Vec3.ZERO, yaw, pitch, Set.of(), TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
    }
}
