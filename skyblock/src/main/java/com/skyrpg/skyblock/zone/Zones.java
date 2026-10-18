package com.skyrpg.skyblock.zone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.hub.Hub;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.island.Islands;
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
 * {@code regrow} and {@code every} are seconds (a negative {@code regrow}: never grows back). A zone with mobs is a fighting zone: in the protected Hub, things
 * there can be hit and hurt (never by another player). A zone's name is also the area name the sidebar shows inside it.
 *
 * <p>A mob can be one of Hypixel's, with its own name, level, health, damage (SkyBlock units), Combat XP and coins, at
 * a time of day, in a part of the zone, and never burning in daylight:
 * <pre>
 * {"type": "minecraft:zombie", "name": "Crypt Ghoul", "level": 30, "health": 2000, "damage": 350, "xp": 36, "coins": 13,
 *  "time": "night", "from": [..], "to": [..], "max": 6, "every": 4, "indoors": true}
 * </pre>
 * Mobs stand on the ground of their box under the open sky (with {@code indoors}, on cave and crypt floors instead).
 * Night mobs leave at sunrise, day mobs at dusk.
 */
public final class Zones {
    public static final String DIRECTORY = "skyrpg/zones";
    private static final String TAG = "skyrpg_zone";

    /** What breaking one kind of block does here: it {@code becomes} this for {@code regrowTicks}, then grows back. */
    public record Rule(BlockState becomes, int regrowTicks) {
    }

    /**
     * One kind of mob a zone keeps up: its own stats when {@code name} is set (health and damage in SkyBlock units,
     * {@code xp} its Combat XP), when ({@code time}: any, day or night) and where ({@code box}, or the zone's).
     */
    public record MobSpawn(EntityType<?> type, int max, int everyTicks, String name, int level, double health, double damage, double xp,
                           long coins, String time, AABB box, double speed, boolean indoors, List<Drop> drops) {
        /** {@code "drops": [{"item": "ENDER_PEARL", "chance": 1, "min": 2, "max": 4}]}: SkyBlock items, into the killer's inventory. */
        public record Drop(String item, double chance, int min, int max) {
        }

        boolean now(ServerLevel level) {
            boolean day = level.isBrightOutside();
            return switch (time) {
                case "day" -> day;
                case "night" -> !day;
                default -> true;
            };
        }
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
            // Bosses that roam past a zone's edges (The End's dragons) can be fought anywhere.
            if (entity.entityTags().contains(FIGHTABLE_TAG)) {
                return true;
            }
            Zone zone = at(entity.level(), entity.blockPosition());
            return zone != null && !zone.mobs().isEmpty();
        });
        // Slayer bosses rise where you fight: outside the Hub, or in one of its combat zones (Hypixel's Graveyard).
        com.skyrpg.core.combat.FightAreas.install(player -> {
            if (!player.level().dimension().identifier().getNamespace().equals("skyrpg_hub")) {
                return !Islands.isIslands(player.level());
            }
            Zone zone = at(player.level(), player.blockPosition());
            return zone != null && !zone.mobs().isEmpty();
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            Rule rule = rule(level, pos, state);
            if (rule != null && level instanceof ServerLevel serverLevel) {
                serverLevel.setBlock(pos, rule.becomes(), Block.UPDATE_ALL);
                // A negative regrow time never grows back (the Crystal Hollows, like Hypixel's).
                if (rule.regrowTicks() >= 0) {
                    REGROWING.add(new Regrowth(level.dimension(), pos.immutable(), state, rule.becomes(),
                            serverLevel.getServer().getTickCount() + rule.regrowTicks()));
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Zones::tick);
        // Hypixel's mobs drop coins straight into the killer's purse.
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer killer) || killer.isCreative()) {
                return;
            }
            MobSpawn spec = specOf(entity);
            if (spec != null && spec.coins() > 0) {
                // In the Rift a mob's "coins" are Motes.
                if (com.skyrpg.skyblock.rift.Rift.isRift(killer.level())) {
                    com.skyrpg.skyblock.rift.Motes.give(killer, spec.coins());
                } else {
                    com.skyrpg.core.economy.Wallet.give(killer, spec.coins());
                }
            }
            if (spec != null && !spec.drops().isEmpty()) {
                giveDrops(killer, spec, entity);
            }
        });
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

    /**
     * Breaks a zone block the way mining it does, for features that handle the break themselves (Mithril): it becomes
     * {@code standIn} and turns back into {@code original} after {@code ticks}.
     */
    public static void deplete(ServerLevel level, BlockPos pos, BlockState original, BlockState standIn, int ticks) {
        level.setBlock(pos, standIn, Block.UPDATE_ALL);
        if (ticks >= 0) {
            REGROWING.add(new Regrowth(level.dimension(), pos.immutable(), original, standIn, level.getServer().getTickCount() + ticks));
        }
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
            // Neutral mobs here are hostile (the Ruins' wolves, the Deep Caverns' pigmen): they go for whoever comes near.
            for (Entity entity : alive) {
                if (entity instanceof net.minecraft.world.entity.NeutralMob neutral && entity instanceof Mob mob && !spawn.name().isEmpty()
                        && mob.getTarget() == null) {
                    ServerPlayer near = (ServerPlayer) level.getNearestPlayer(mob, 12);
                    if (near != null && !near.isCreative() && !near.isSpectator()) {
                        neutral.setPersistentAngerTarget(net.minecraft.world.entity.EntityReference.of(near));
                        neutral.startPersistentAngerTimer();
                        mob.setTarget(near);
                    }
                }
            }
            if (!spawn.now(level)) {
                // Night mobs leave at sunrise (and day mobs at dusk), a few at a time.
                alive.stream().limit(2).forEach(Entity::discard);
                continue;
            }
            if (alive.size() >= spawn.max()) {
                continue;
            }
            AABB box = spawn.box() != null ? spawn.box() : zone.box();
            for (int attempt = 0; attempt < 8; attempt++) {
                BlockPos spot = swims(spawn.type()) ? waterSpot(level, box, random) : standingSpot(level, box, random, spawn.indoors());
                if (spot == null) {
                    continue;
                }
                // Not a spawner: these are the island's own mobs (full rewards, they count for slayer quests).
                Entity mob = spawn.type().spawn(level, spot, EntitySpawnReason.NATURAL);
                if (mob != null) {
                    mob.addTag(tag);
                    if (mob instanceof Mob m) {
                        m.setPersistenceRequired();
                        dress(m, spawn);
                    }
                    LAST_SPAWN.put(tag, now);
                    break;
                }
            }
        }
    }

    /**
     * A random column's lowest spot in the box a mob can stand on: solid below, two blocks of room. Lowest, so mobs stand
     * on the ground or a cave floor, not in tree tops; under the open sky unless they're {@code indoors} (crypt) mobs,
     * so none turn up inside houses.
     */
    private static BlockPos standingSpot(ServerLevel level, AABB box, RandomSource random, boolean indoors) {
        int x = (int) Math.floor(box.minX + random.nextDouble() * box.getXsize());
        int z = (int) Math.floor(box.minZ + random.nextDouble() * box.getZsize());
        for (int y = (int) box.minY + 1; y < (int) box.maxY; y++) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos below = feet.below();
            if (level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
                    && level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)) {
                return indoors || level.canSeeSky(feet) ? feet : null;
            }
        }
        return null;
    }

    private static boolean swims(EntityType<?> type) {
        var category = type.getCategory();
        return category == net.minecraft.world.entity.MobCategory.WATER_CREATURE || category == net.minecraft.world.entity.MobCategory.UNDERGROUND_WATER_CREATURE
                || category == net.minecraft.world.entity.MobCategory.WATER_AMBIENT || category == net.minecraft.world.entity.MobCategory.AXOLOTLS;
    }

    /** For a water mob (squids, fish): a random column's lowest spot with water above it, in a pool, a lake or a cave. */
    private static BlockPos waterSpot(ServerLevel level, AABB box, RandomSource random) {
        int x = (int) Math.floor(box.minX + random.nextDouble() * box.getXsize());
        int z = (int) Math.floor(box.minZ + random.nextDouble() * box.getZsize());
        for (int y = (int) box.minY + 1; y < (int) box.maxY; y++) {
            BlockPos at = new BlockPos(x, y, z);
            if (level.getFluidState(at).isSource() && level.getFluidState(at.above()).isSource()) {
                return at;
            }
        }
        return null;
    }

    /** A named zone mob: Hypixel's name, level, health and damage, its Combat XP, and no burning in daylight. */
    /** A mob with this tag can be fought anywhere on a public island, in or out of a zone. */
    public static final String FIGHTABLE_TAG = "skyrpg_fightable";

    /** Gives a mob that a feature spawns itself (not a zone) Hypixel's look and stats, as zone mobs get them. */
    public static void dressAs(Mob mob, String name, int level, double health, double damage, double xp) {
        dress(mob, new MobSpawn(mob.getType(), 1, 0, name, level, health, damage, xp, 0, "any", null, 1, false, List.of()));
    }

    private static void dress(Mob mob, MobSpawn spawn) {
        mob.addTag(com.skyrpg.core.world.MobTags.NO_SUNBURN);
        if (spawn.name().isEmpty()) {
            return;
        }
        // Derpy's DOUBLE MOBS HP!!! (monsters only)
        double health = spawn.health() / com.skyrpg.core.stats.StatTypes.HEALTH_SCALE
                * (mob instanceof net.minecraft.world.entity.monster.Enemy && com.skyrpg.core.world.Mayor.active("double_mobs_hp") ? 2 : 1);
        setBase(mob, net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, Math.min(1024, health));
        if (spawn.damage() > 0) {
            setBase(mob, net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, spawn.damage() / com.skyrpg.core.stats.StatTypes.HEALTH_SCALE);
        }
        if (spawn.speed() != 1) {
            net.minecraft.world.entity.ai.attributes.AttributeInstance speed = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(speed.getBaseValue() * spawn.speed());
            }
        }
        mob.setHealth(mob.getMaxHealth());
        // The level is shown, not applied: health and damage are Hypixel's (MobLevels.set would raise them per level).
        mob.setAttached(com.skyrpg.combat.MobLevels.INFO, new com.skyrpg.combat.MobLevels.MobInfo(Math.max(1, spawn.level()), false,
                (float) Math.max(1, health / 1024)));
        mob.setCustomName(net.minecraft.network.chat.Component.literal(spawn.name()));
        if (spawn.xp() >= 0) {
            com.skyrpg.core.world.MobTags.setCombatXp(mob, spawn.xp());
        }
        if (mob instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie) {
            // Hypixel's zombies are grown-ups.
            zombie.setBaby(false);
        }
        // No random armour or weapons, and nothing picked up: they look like Hypixel's.
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (slot.getType() != net.minecraft.world.entity.EquipmentSlot.Type.ANIMAL_ARMOR && slot != net.minecraft.world.entity.EquipmentSlot.SADDLE) {
                mob.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
        mob.setCanPickUpLoot(false);
    }

    private static void setBase(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /** The zone mob spec a mob was spawned from (by its tag), or null. */
    private static MobSpawn specOf(Entity entity) {
        for (String tag : entity.entityTags()) {
            if (!tag.startsWith(TAG + ".")) {
                continue;
            }
            for (Zone zone : zones) {
                String prefix = TAG + "." + zone.id().replace(':', '.') + ".";
                if (tag.startsWith(prefix)) {
                    try {
                        int index = Integer.parseInt(tag.substring(prefix.length()));
                        return index < zone.mobs().size() ? zone.mobs().get(index) : null;
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
        }
        return null;
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
                AABB mobBox = null;
                if (mob.has("from") && mob.has("to")) {
                    JsonArray a = mob.getAsJsonArray("from");
                    JsonArray b = mob.getAsJsonArray("to");
                    mobBox = new AABB(Math.min(a.get(0).getAsInt(), b.get(0).getAsInt()), Math.min(a.get(1).getAsInt(), b.get(1).getAsInt()),
                            Math.min(a.get(2).getAsInt(), b.get(2).getAsInt()), Math.max(a.get(0).getAsInt(), b.get(0).getAsInt()) + 1,
                            Math.max(a.get(1).getAsInt(), b.get(1).getAsInt()) + 1, Math.max(a.get(2).getAsInt(), b.get(2).getAsInt()) + 1);
                }
                mobs.add(new MobSpawn(type, mob.has("max") ? mob.get("max").getAsInt() : 4,
                        (int) Math.round((mob.has("every") ? mob.get("every").getAsDouble() : 10) * 20),
                        mob.has("name") ? mob.get("name").getAsString() : "", mob.has("level") ? mob.get("level").getAsInt() : 1,
                        mob.has("health") ? mob.get("health").getAsDouble() : 0, mob.has("damage") ? mob.get("damage").getAsDouble() : 0,
                        mob.has("xp") ? mob.get("xp").getAsDouble() : -1, mob.has("coins") ? mob.get("coins").getAsLong() : 0,
                        mob.has("time") ? mob.get("time").getAsString() : "any", mobBox, mob.has("speed") ? mob.get("speed").getAsDouble() : 1,
                        mob.has("indoors") && mob.get("indoors").getAsBoolean(), drops(mob)));
            }
        }
        return new Zone(id, json.get("name").getAsString(),
                ResourceKey.create(Registries.DIMENSION, Identifier.parse(json.get("dimension").getAsString())), box,
                Map.copyOf(blocks), List.copyOf(mobs), text);
    }

    private static List<MobSpawn.Drop> drops(JsonObject mob) {
        if (!mob.has("drops")) {
            return List.of();
        }
        List<MobSpawn.Drop> out = new ArrayList<>();
        for (var element : mob.getAsJsonArray("drops")) {
            JsonObject drop = element.getAsJsonObject();
            int min = drop.has("min") ? drop.get("min").getAsInt() : 1;
            out.add(new MobSpawn.Drop(drop.get("item").getAsString(), drop.has("chance") ? drop.get("chance").getAsDouble() : 1,
                    min, drop.has("max") ? drop.get("max").getAsInt() : min));
        }
        return List.copyOf(out);
    }

    /** A zone mob's drops for its killer, straight into their inventory (Hypixel's default Telekinesis); what doesn't fit drops. */
    private static void giveDrops(ServerPlayer killer, MobSpawn spec, Entity entity) {
        for (MobSpawn.Drop drop : spec.drops()) {
            if (killer.getRandom().nextDouble() >= drop.chance()) {
                continue;
            }
            com.skyrpg.items.ItemDefinition def = com.skyrpg.items.ItemRegistry.skyblock(drop.item());
            if (def == null) {
                continue;
            }
            int count = drop.min() + (drop.max() > drop.min() ? killer.getRandom().nextInt(drop.max() - drop.min() + 1) : 0);
            for (int i = 0; i < count; i++) {
                net.minecraft.world.item.ItemStack stack = com.skyrpg.items.SkyItems.create(def, 1);
                if (!killer.getInventory().add(stack)) {
                    killer.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
        }
    }
}
