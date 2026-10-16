package com.skyrpg.skyblock.zone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.hub.Hub;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Hypixel's public islands are protected, except where they're meant to be worked: the Farm's crops, the Forest's
 * trees, the Coal Mine's ore. A zone is a box where named blocks can be broken and grow back after a while
 * (standing in as another block meanwhile, like bedrock for mined ore), and where mobs keep spawning up to a count.
 * Zones come from {@code data/<ns>/skyrpg/zones/<id>.json}, and are sent to clients so they don't refuse a break
 * the server allows:
 * <pre>
 * {"name": "Coal Mine", "dimension": "skyrpg_hub:hub", "from": [-30, 60, 40], "to": [10, 90, 80],
 *  "blocks": {"minecraft:coal_ore": {"becomes": "minecraft:bedrock", "regrow": 7},
 *             "minecraft:stone": {"becomes": "minecraft:bedrock", "regrow": 3}},
 *  "mobs": [{"type": "minecraft:zombie", "max": 4, "every": 8}]}
 * </pre>
 * {@code regrow} and {@code every} are seconds. A zone with mobs is a fighting zone: in the protected Hub, things
 * there can be hit and hurt (never by another player). A zone's name is also the area name the sidebar shows inside it.
 */
public final class Zones {
    public static final String DIRECTORY = "skyrpg/zones";
    private static final String TAG = "skyrpg_zone";

    /** What breaking one kind of block does here: it {@code becomes} this for {@code regrowTicks}, then grows back. */
    public record Rule(BlockState becomes, int regrowTicks) {
    }

    public record MobSpawn(EntityType<?> type, int max, int everyTicks) {
    }

    public record Zone(String id, String name, ResourceKey<Level> dimension, AABB box, Map<Block, Rule> blocks, List<MobSpawn> mobs, String json) {
        public boolean contains(Level level, BlockPos pos) {
            return level.dimension().equals(dimension) && box.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
    }

    /** Server to client: every zone, as JSON, for the client's break check. */
    public record ZoneList(Map<String, String> zones) implements CustomPacketPayload {
        public static final Type<ZoneList> TYPE = new Type<>(SkyRpgSkyblock.id("zones"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ZoneList> CODEC = StreamCodec.composite(
                ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), ZoneList::zones, ZoneList::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private record Regrowth(ResourceKey<Level> dimension, BlockPos pos, BlockState original, BlockState standIn, long due) {
    }

    private static List<Zone> zones = List.of();
    private static final List<Regrowth> REGROWING = new ArrayList<>();
    private static final Map<String, Long> LAST_SPAWN = new HashMap<>();

    private Zones() {
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(ZoneList.TYPE, ZoneList.CODEC);
        JsonDataLoader.register(SkyRpgSkyblock.id("zones"), DIRECTORY, files -> {
            Map<String, String> raw = new LinkedHashMap<>();
            files.forEach((id, json) -> raw.put(id.toString(), json.toString()));
            zones = parseAll(raw);
            SkyRpgSkyblock.LOGGER.info("Loaded {} zones", zones.size());
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) ->
                server.getPlayerList().getPlayers().forEach(Zones::send));
        Hub.setBreakRule((level, pos, state, player) -> rule(level, pos, state) != null);
        Hub.setCombatRule(entity -> {
            Zone zone = at(entity.level(), entity.blockPosition());
            return zone != null && !zone.mobs().isEmpty();
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            Rule rule = rule(level, pos, state);
            if (rule != null && level instanceof ServerLevel serverLevel) {
                serverLevel.setBlock(pos, rule.becomes(), Block.UPDATE_ALL);
                REGROWING.add(new Regrowth(level.dimension(), pos.immutable(), state, rule.becomes(),
                        serverLevel.getServer().getTickCount() + rule.regrowTicks()));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Zones::tick);
        // Nothing stays mined over a restart: put every block back on the way down.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            REGROWING.forEach(r -> regrow(server, r));
            REGROWING.clear();
        });
    }

    /** The client's copy of the zones (from {@link ZoneList}). */
    public static void replace(Map<String, String> raw) {
        zones = parseAll(raw);
    }

    /** The zone at this position, or null. */
    public static Zone at(Level level, BlockPos pos) {
        for (Zone zone : zones) {
            if (zone.contains(level, pos)) {
                return zone;
            }
        }
        return null;
    }

    /** What breaking this block here does, or null if it can't be broken (not in a zone, or not one of its blocks). */
    public static Rule rule(Level level, BlockPos pos, BlockState state) {
        Zone zone = at(level, pos);
        return zone == null ? null : zone.blocks().get(state.getBlock());
    }

    private static void send(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, ZoneList.TYPE)) {
            return;
        }
        Map<String, String> raw = new LinkedHashMap<>();
        zones.forEach(zone -> raw.put(zone.id(), zone.json()));
        ServerPlayNetworking.send(player, new ZoneList(raw));
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        REGROWING.removeIf(r -> {
            if (r.due() > now) {
                return false;
            }
            regrow(server, r);
            return true;
        });
        if (now % 20 == 0) {
            for (Zone zone : zones) {
                spawnMobs(server, zone, now);
            }
        }
    }

    private static void regrow(MinecraftServer server, Regrowth r) {
        ServerLevel level = server.getLevel(r.dimension());
        // Only if it's still the stand-in: never overwrite what someone put there since.
        if (level != null && level.isLoaded(r.pos()) && level.getBlockState(r.pos()).equals(r.standIn())) {
            level.setBlock(r.pos(), r.original(), Block.UPDATE_ALL);
        }
    }

    private static void spawnMobs(MinecraftServer server, Zone zone, long now) {
        if (zone.mobs().isEmpty()) {
            return;
        }
        ServerLevel level = server.getLevel(zone.dimension());
        if (level == null || level.players().stream().noneMatch(p -> p.getBoundingBox().intersects(zone.box().inflate(48)))) {
            return; // nobody near: leave it be
        }
        RandomSource random = level.getRandom();
        for (int i = 0; i < zone.mobs().size(); i++) {
            MobSpawn spawn = zone.mobs().get(i);
            String tag = TAG + "." + zone.id().replace(':', '.') + "." + i;
            long last = LAST_SPAWN.getOrDefault(tag, 0L);
            if (now - last < spawn.everyTicks()) {
                continue;
            }
            List<Entity> alive = level.getEntities((Entity) null, zone.box().inflate(16), e -> e.isAlive() && e.entityTags().contains(tag));
            if (alive.size() >= spawn.max()) {
                continue;
            }
            for (int attempt = 0; attempt < 8; attempt++) {
                int x = (int) Math.floor(zone.box().minX + random.nextDouble() * zone.box().getXsize());
                int z = (int) Math.floor(zone.box().minZ + random.nextDouble() * zone.box().getZsize());
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (top.getY() < zone.box().minY || top.getY() > zone.box().maxY || !level.getBlockState(top).isAir()) {
                    continue;
                }
                Entity mob = spawn.type().spawn(level, top, EntitySpawnReason.SPAWNER);
                if (mob != null) {
                    mob.addTag(tag);
                    if (mob instanceof Mob m) {
                        m.setPersistenceRequired();
                    }
                    LAST_SPAWN.put(tag, now);
                    break;
                }
            }
        }
    }

    private static List<Zone> parseAll(Map<String, String> raw) {
        List<Zone> out = new ArrayList<>();
        raw.forEach((id, text) -> {
            try {
                out.add(parse(id, text));
            } catch (RuntimeException e) {
                SkyRpgSkyblock.LOGGER.error("Skipping invalid zone {}: {}", id, e.getMessage());
            }
        });
        return List.copyOf(out);
    }

    private static Zone parse(String id, String text) {
        JsonObject json = JsonParser.parseString(text).getAsJsonObject();
        JsonArray from = json.getAsJsonArray("from");
        JsonArray to = json.getAsJsonArray("to");
        AABB box = new AABB(Math.min(from.get(0).getAsInt(), to.get(0).getAsInt()), Math.min(from.get(1).getAsInt(), to.get(1).getAsInt()),
                Math.min(from.get(2).getAsInt(), to.get(2).getAsInt()), Math.max(from.get(0).getAsInt(), to.get(0).getAsInt()) + 1,
                Math.max(from.get(1).getAsInt(), to.get(1).getAsInt()) + 1, Math.max(from.get(2).getAsInt(), to.get(2).getAsInt()) + 1);
        Map<Block, Rule> blocks = new HashMap<>();
        if (json.has("blocks")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("blocks").entrySet()) {
                JsonObject rule = entry.getValue().getAsJsonObject();
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(entry.getKey()));
                Block becomes = BuiltInRegistries.BLOCK.getValue(Identifier.parse(rule.has("becomes") ? rule.get("becomes").getAsString() : "minecraft:bedrock"));
                blocks.put(block, new Rule(becomes.defaultBlockState(), (int) Math.round((rule.has("regrow") ? rule.get("regrow").getAsDouble() : 5) * 20)));
            }
        }
        List<MobSpawn> mobs = new ArrayList<>();
        if (json.has("mobs")) {
            for (JsonElement element : json.getAsJsonArray("mobs")) {
                JsonObject mob = element.getAsJsonObject();
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(mob.get("type").getAsString()));
                mobs.add(new MobSpawn(type, mob.has("max") ? mob.get("max").getAsInt() : 4,
                        (int) Math.round((mob.has("every") ? mob.get("every").getAsDouble() : 10) * 20)));
            }
        }
        return new Zone(id, json.get("name").getAsString(),
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(json.get("dimension").getAsString())), box,
                Map.copyOf(blocks), List.copyOf(mobs), text);
    }
}
