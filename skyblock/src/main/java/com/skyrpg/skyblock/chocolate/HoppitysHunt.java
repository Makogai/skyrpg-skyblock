package com.skyrpg.skyblock.chocolate;

import com.mojang.serialization.Codec;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.hub.HubAreas;
import com.skyrpg.hub.mixin.ItemDisplayAccess;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.time.CalendarEvents;
import com.skyrpg.skyblock.time.SkyBlockTime;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's Hoppity's Hunt (all spring: Early Spring 1st to Late Spring 31st): Chocolate Eggs turn up on every island
 * at 7am (Breakfast, or Brunch), 2pm (Lunch, or Déjeuner) and 9pm (Dinner, or Supper), the names taking turns by day.
 * The newest six are out; each spawn is on every island at once, at one of that island's spots, and each player can
 * find each spawn once. An egg holds a Chocolate Rabbit ({@link Rabbits#roll}). Finding 15 different spots on an island
 * gets its own rabbit. The Egglocator (the Chocolate Shop) points at the nearest egg you haven't found.
 *
 * <p>Decided where Hypixel's map spots don't fit our islands: each island's 15 spots are picked once, at random, on open
 * ground in its zones (kept in the world), at least 8 blocks apart.
 */
final class HoppitysHunt {
    static final String EVENT = "Hoppity's Hunt";
    private static final String TAG = "skyrpg_chocolate_egg";
    private static final int SPOTS = 15;
    private static final int[] HOURS = {7, 14, 21};
    private static final String[][] NAMES = {{"Breakfast", "Lunch", "Dinner"}, {"Brunch", "Déjeuner", "Supper"}};
    private static final ChatFormatting[] COLORS = {ChatFormatting.GOLD, ChatFormatting.BLUE, ChatFormatting.GREEN};
    /** The islands eggs turn up on: our dimension, Hypixel's island name. */
    private static final Map<String, String> ISLANDS = Map.ofEntries(
            Map.entry("skyrpg_hub:hub", "Hub"), Map.entry("skyrpg_skyblock:farming_islands", "The Farming Islands"),
            Map.entry("skyrpg_skyblock:gold_mine", "Gold Mine"), Map.entry("skyrpg_skyblock:deep_caverns", "Deep Caverns"),
            Map.entry("skyrpg_skyblock:dwarven_mines", "Dwarven Mines"), Map.entry("skyrpg_skyblock:crystal_hollows", "Crystal Hollows"),
            Map.entry("skyrpg_skyblock:spiders_den", "Spider's Den"), Map.entry("skyrpg_skyblock:the_end", "The End"),
            Map.entry("skyrpg_skyblock:crimson_isle", "Crimson Isle"), Map.entry("skyrpg_skyblock:the_park", "The Park"),
            Map.entry("skyrpg_skyblock:dungeon_hub", "Dungeon Hub"), Map.entry("skyrpg_skyblock:galatea", "Moonglade Marsh"));

    /** Every island's egg spots. */
    static final class Spots extends SavedData {
        static final Codec<Spots> CODEC = Codec.unboundedMap(Codec.STRING, BlockPos.CODEC.listOf()).xmap(Spots::new, s -> s.spots);
        static final SavedDataType<Spots> TYPE = new SavedDataType<>(SkyRpgSkyblock.id("chocolate_egg_spots"), Spots::new, CODEC, null);
        final Map<String, List<BlockPos>> spots;

        Spots() {
            this(Map.of());
        }

        Spots(Map<String, List<BlockPos>> spots) {
            this.spots = new HashMap<>();
            spots.forEach((k, v) -> this.spots.put(k, new ArrayList<>(v)));
        }

        static Spots of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(TYPE);
        }
    }

    /** An egg out in the world: its spawn, island, spot and parts. */
    private record Egg(long key, String dimension, int spot, List<Entity> parts) {
    }

    private static final Map<String, Egg> LIVE = new HashMap<>();
    private static final Map<UUID, Long> LOCATOR = new HashMap<>();
    private static long announced;

    private HoppitysHunt() {
    }

    static void init() {
        Identifier first = SkyRpgSkyblock.id("chocolate_egg_first");
        UseEntityCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(first, (player, level, hand, entity, hit) -> click(player, entity));
        AttackEntityCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(first, (player, level, hand, entity, hit) -> click(player, entity));
        UseItemCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(first, (player, level, hand) -> {
            if (!"EGGLOCATOR".equals(SkyItems.skyblockId(player.getItemInHand(hand)))) {
                return InteractionResult.PASS;
            }
            if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer server) {
                locate(server);
            }
            return InteractionResult.SUCCESS;
        });
        // An egg left from before a restart is gone (they come back while someone's near).
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && LIVE.values().stream().noneMatch(e -> e.parts().contains(entity))) {
                level.getServer().execute(entity::discard);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(HoppitysHunt::tick);
    }

    private static InteractionResult click(Player player, Entity entity) {
        if (!entity.entityTags().contains(TAG)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            for (Egg egg : LIVE.values()) {
                if (egg.parts().contains(entity)) {
                    claim(server, egg);
                    break;
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- the schedule

    static boolean running(long now) {
        return CalendarEvents.running(now, EVENT);
    }

    /** The spawn's real time (SkyBlock day * 3 + meal). */
    static long time(long key) {
        long day = Math.floorDiv(key, 3);
        int meal = (int) Math.floorMod(key, 3);
        return SkyBlockTime.EPOCH + day * SkyBlockTime.DAY + HOURS[meal] * SkyBlockTime.HOUR;
    }

    /** The eggs out now: the newest six spawns, during the Hunt. */
    static List<Long> active(long now) {
        List<Long> out = new ArrayList<>();
        CalendarEvents.Occurrence hunt = CalendarEvents.occurrence(now, EVENT);
        if (hunt == null) {
            return out;
        }
        long day = Math.floorDiv(now - SkyBlockTime.EPOCH, SkyBlockTime.DAY);
        for (long key = day * 3 + 2; key >= day * 3 - 6 && out.size() < 6; key--) {
            long t = time(key);
            if (t <= now && t >= hunt.start() - SkyBlockTime.DAY) {
                out.add(key);
            }
        }
        return out;
    }

    static String name(long key) {
        int meal = (int) Math.floorMod(key, 3);
        int alt = (int) Math.floorMod(Math.floorDiv(key, 3), 2);
        return NAMES[alt][meal];
    }

    static ChatFormatting color(long key) {
        return COLORS[(int) Math.floorMod(key, 3)];
    }

    /** Which of the island's spots a spawn uses. */
    /**
     * The spot an egg is at on an island. Spawns are numbered in a row (day x 3 + meal), so stepping through the
     * island's spots by a stride that shares no factor with their count gives every egg out at once its own spot.
     */
    private static int spotOf(long key, String dimension, int count) {
        int stride = 1;
        for (int s : new int[] {7, 11, 13, 4}) {
            if (java.math.BigInteger.valueOf(s).gcd(java.math.BigInteger.valueOf(count)).intValue() == 1) {
                stride = s;
                break;
            }
        }
        return (int) Math.floorMod(key * stride + dimension.hashCode(), (long) count);
    }

    // ---------------------------------------------------------------- spots and eggs

    private static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (tick % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Long> active = active(now);
        // a new spawn: everyone hears of it
        if (!active.isEmpty() && active.getFirst() != announced) {
            boolean first = announced == 0;
            announced = active.getFirst();
            if (!first) {
                long key = announced;
                Component line = Component.literal("HOPPITY'S HUNT ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                        .append(Component.literal("A ").withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal("Chocolate " + name(key) + " Egg").withStyle(color(key)))
                        .append(Component.literal(" has appeared!").withStyle(ChatFormatting.LIGHT_PURPLE));
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (SkyBlockWorlds.isSkyBlock(player.level())) {
                        player.sendSystemMessage(line);
                    }
                }
            }
        }
        Spots spots = Spots.of(server);
        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().identifier().toString();
            if (!ISLANDS.containsKey(dimension) || level.players().isEmpty()) {
                continue;
            }
            List<BlockPos> mine = spots.spots.computeIfAbsent(dimension, k -> new ArrayList<>());
            if (mine.size() < SPOTS && pickSpot(level, mine)) {
                spots.setDirty();
            }
            if (mine.isEmpty()) {
                continue;
            }
            for (long key : active) {
                String id = dimension + "|" + key;
                if (LIVE.containsKey(id)) {
                    continue;
                }
                int spot = spotOf(key, dimension, mine.size());
                BlockPos pos = mine.get(spot);
                if (level.isLoaded(pos) && level.players().stream().anyMatch(p -> p.blockPosition().closerThan(pos, 48))) {
                    spawnEgg(level, id, key, dimension, spot, pos);
                }
            }
        }
        // spawns that are over, and eggs nobody's near any more
        LIVE.entrySet().removeIf(entry -> {
            Egg egg = entry.getValue();
            boolean gone = !active.contains(egg.key()) || egg.parts().stream().anyMatch(Entity::isRemoved);
            if (!gone) {
                Entity first = egg.parts().getFirst();
                gone = ((ServerLevel) first.level()).players().stream().noneMatch(p -> p.distanceToSqr(first) < 64 * 64);
            }
            if (gone) {
                egg.parts().forEach(Entity::discard);
            }
            return gone;
        });
    }

    /** One try at a new spot: open ground in one of the island's zones (or around its arrival), away from the others. */
    /** Admins: how many egg spots this island has, and where the eggs out now are. */
    static void list(ServerPlayer player) {
        String dimension = player.level().dimension().identifier().toString();
        List<BlockPos> mine = Spots.of(player.level().getServer()).spots.getOrDefault(dimension, List.of());
        player.sendSystemMessage(Component.literal(mine.size() + "/" + SPOTS + " egg spots here" + (mine.isEmpty() ? "." : ". Out now:"))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (mine.isEmpty()) {
            return;
        }
        for (long key : active(System.currentTimeMillis())) {
            BlockPos pos = mine.get(spotOf(key, dimension, mine.size()));
            player.sendSystemMessage(Component.literal(" " + name(key) + ": " + pos.toShortString()).withStyle(color(key)));
        }
    }

    private static boolean pickSpot(ServerLevel level, List<BlockPos> spots) {
        List<AABB> boxes = new ArrayList<>();
        for (Zones.Zone zone : Zones.all()) {
            if (zone.dimension().equals(level.dimension())) {
                boxes.add(zone.box());
            }
        }
        if (boxes.isEmpty()) {
            HubAreas.Arrival arrival = HubAreas.arrival(level.dimension());
            if (arrival == null) {
                return false;
            }
            boxes.add(new AABB(arrival.pos(), arrival.pos()).inflate(40, 20, 40));
        }
        AABB box = boxes.get(level.getRandom().nextInt(boxes.size()));
        int x = (int) Math.floor(box.minX + level.getRandom().nextDouble() * box.getXsize());
        int z = (int) Math.floor(box.minZ + level.getRandom().nextDouble() * box.getZsize());
        if (!level.isLoaded(new BlockPos(x, (int) box.minY, z))) {
            return false;
        }
        BlockPos spot = Zones.standingSpot(level, new AABB(x, box.minY, z, x + 1, box.maxY, z + 1), level.getRandom(), false);
        if (spot == null || spots.stream().anyMatch(s -> s.closerThan(spot, 8))) {
            return false;
        }
        spots.add(spot);
        return true;
    }

    /** Puts an egg out (known before its parts are in the world: the load check would take them for leftovers). */
    private static void spawnEgg(ServerLevel level, String id, long key, String dimension, int spot, BlockPos pos) {
        Vec3 at = Vec3.atBottomCenterOf(pos);
        Interaction box = EntityTypes.INTERACTION.create(level, EntitySpawnReason.EVENT);
        Display.ItemDisplay egg = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        List<Entity> parts = new ArrayList<>();
        Egg made = new Egg(key, dimension, spot, parts);
        int meal = (int) Math.floorMod(key, 3);
        ((ItemDisplayAccess) egg).skyrpg$setItemStack(new ItemStack(meal == 0 ? Items.BROWN_EGG : meal == 1 ? Items.BLUE_EGG : Items.EGG));
        egg.snapTo(at.x, at.y + 0.35, at.z, level.getRandom().nextFloat() * 360, 0);
        parts.add(egg);
        if (box != null) {
            ((com.skyrpg.skyblock.mixin.InteractionAccess) box).skyrpg$setWidth(1.0f);
            ((com.skyrpg.skyblock.mixin.InteractionAccess) box).skyrpg$setHeight(1.0f);
            box.snapTo(at.x, at.y, at.z, 0, 0);
            parts.add(0, box);
        }
        LIVE.put(id, made);
        for (Entity part : List.copyOf(parts)) {
            part.addTag(TAG);
            level.addFreshEntity(part);
        }
    }

    // ---------------------------------------------------------------- finding them

    private static String place(ServerLevel level, BlockPos pos) {
        Zones.Zone zone = Zones.at(level, pos);
        if (zone != null) {
            return "in the " + zone.name();
        }
        String island = ISLANDS.get(level.dimension().identifier().toString());
        return "on " + (island == null ? "this island" : island);
    }

    private static void claim(ServerPlayer player, Egg egg) {
        String eggName = "Chocolate " + name(egg.key()) + " Egg";
        Factory factory = ChocolateFactory.get(player);
        if (factory.eggs.contains(egg.key())) {
            player.sendOverlayMessage(Component.literal("You have already found this " + eggName + "!").withStyle(ChatFormatting.RED));
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = egg.parts().getFirst().blockPosition();
        String island = ISLANDS.get(egg.dimension());
        factory.eggs.add(egg.key());
        while (factory.eggs.size() > 30) {
            factory.eggs.removeFirst();
        }
        List<Integer> spots = factory.eggSpots.computeIfAbsent(island, k -> new ArrayList<>());
        if (!spots.contains(egg.spot())) {
            spots.add(egg.spot());
        }
        player.sendSystemMessage(Component.literal("HOPPITY'S HUNT ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("You found a ").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(eggName).withStyle(color(egg.key())))
                .append(Component.literal(" " + place(level, pos)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("!").withStyle(ChatFormatting.LIGHT_PURPLE)));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 15, 0.3, 0.3, 0.3, 0);
        Rabbits.Rabbit rabbit = Rabbits.roll(factory, island == null ? "" : island, player.getRandom());
        if (rabbit != null) {
            ChocolateFactory.found(player, factory, rabbit);
        }
        ChocolateFactory.milestones(player, factory);
        ChocolateFactory.save(player, factory);
    }

    /** The Egglocator: a trail of sparks towards the nearest egg out on this island that you haven't found. */
    private static void locate(ServerPlayer player) {
        long tick = player.level().getServer().getTickCount();
        Long last = LOCATOR.get(player.getUUID());
        if (last != null && tick - last < 100) {
            player.sendOverlayMessage(Component.literal("This item is on cooldown.").withStyle(ChatFormatting.RED));
            return;
        }
        LOCATOR.put(player.getUUID(), tick);
        ServerLevel level = (ServerLevel) player.level();
        String dimension = level.dimension().identifier().toString();
        List<BlockPos> spots = Spots.of(level.getServer()).spots.getOrDefault(dimension, List.of());
        if (!ISLANDS.containsKey(dimension) || spots.isEmpty()) {
            player.sendSystemMessage(Component.literal("This island doesn't spawn Chocolate Rabbit Eggs!").withStyle(ChatFormatting.RED));
            return;
        }
        Factory factory = ChocolateFactory.get(player);
        BlockPos best = null;
        for (long key : active(System.currentTimeMillis())) {
            if (factory.eggs.contains(key)) {
                continue;
            }
            BlockPos pos = spots.get(spotOf(key, dimension, spots.size()));
            if (best == null || pos.distSqr(player.blockPosition()) < best.distSqr(player.blockPosition())) {
                best = pos;
            }
        }
        if (best == null) {
            player.sendSystemMessage(Component.literal("There are no Chocolate Rabbit Eggs nearby! Try again later!").withStyle(ChatFormatting.RED));
            return;
        }
        Vec3 from = player.getEyePosition();
        Vec3 to = Vec3.atCenterOf(best);
        Vec3 step = to.subtract(from).normalize().scale(0.5);
        Vec3 at = from;
        for (int i = 0; i < 40 && at.distanceTo(to) > 1; i++) {
            at = at.add(step);
            level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        player.sendOverlayMessage(Component.literal("Egg " + Math.round(Math.sqrt(best.distSqr(player.blockPosition()))) + " blocks away")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.4f);
    }
}
