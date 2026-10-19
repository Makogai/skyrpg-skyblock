package com.skyrpg.skyblock.dungeon;

import com.mojang.math.Transformation;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.hub.mixin.DisplayAccess;
import com.skyrpg.hub.mixin.TextDisplayAccess;
import com.skyrpg.skyblock.mixin.EntityDataAccess;
import com.skyrpg.skyblock.mixin.InteractionAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * One party's run of a floor: its map ({@link Layout}) built from the room library in a slot of the Catacombs world, one
 * room a tick; the rooms' mobs (spawned when someone first walks in), secrets, keys and locked doors; the Blood room and
 * its Watcher; the boss; deaths; and the score. Ticked by {@link Catacombs}.
 */
final class Run {
    enum Phase {
        BUILDING, STARTING, CLEARING, BLOOD, BOSS, DONE
    }

    static final int SPACING = 2048;
    static final int Y = 64;
    static final int CELL = 32;
    static final int FLOOR = 8;
    static final String TEMPLATE_TAG = "skyrpg_catacombs";
    static final String RUN_TAG_PREFIX = "skyrpg_run_";
    private static final String[] BLOOD_NAMES = {"Revoker", "Psycho", "Reaper", "Cannibal", "Mute", "Ooze", "Putrid", "Freak", "Leech",
            "Tear", "Parasite", "Flamer", "Skull", "Mr. Dead", "Vader", "Frost", "Walker", "Wandering Soul"};

    /** A marker read from a placed room: what it is, where, and its data. */
    record Spot(String kind, Vec3 pos, CompoundTag data) {
    }

    /** A room as built: its template, where it is, its markers, and how far it's been cleared. */
    static final class RoomState {
        final Layout.Room room;
        Identifier template;
        BlockPos min;
        BlockPos max;
        final List<Spot> spots = new ArrayList<>();
        final Set<UUID> stars = new HashSet<>();
        int starCount;
        boolean entered;
        boolean cleared;
        int secrets;
        int secretsFound;
        final Set<BlockPos> secretChests = new HashSet<>();
        Puzzles.Puzzle puzzle;

        RoomState(Layout.Room room) {
            this.room = room;
        }

        Vec3 middle() {
            return new Vec3((min.getX() + max.getX()) / 2.0 + 0.5, min.getY() + FLOOR + 1, (min.getZ() + max.getZ()) / 2.0 + 0.5);
        }
    }

    final long id;
    final String tag;
    final int slot;
    final Floors.Floor floor;
    final Layout layout;
    final ServerLevel level;
    final BlockPos origin;
    final BlockPos bossOrigin;
    /** Hypixel's arena's middle and way in (its markers), when it's installed; null for ours. */
    Vec3 arenaCenter;
    Vec3 arenaArrival;
    final List<UUID> players;
    final Map<UUID, String> classes = new LinkedHashMap<>();
    final RoomState[] rooms;
    final RandomSource random;
    Phase phase = Phase.BUILDING;
    private int buildStep;
    long created;
    long started = -1;
    long finished = -1;
    long emptySince = -1;
    int witherKeys;
    boolean bloodKey;
    final Set<Integer> openedDoors = new HashSet<>();
    final Map<Integer, List<BlockPos>> lockedDoorBlocks = new HashMap<>();
    /** Keys lying in rooms: the display entities to pick up, and which lock each is for. */
    final Map<UUID, Layout.Lock> keyEntities = new HashMap<>();
    final Set<UUID> ghosts = new HashSet<>();
    final Map<UUID, Integer> deathsBy = new HashMap<>();
    int deaths;
    /** Secrets found as entities (items, bats): their UUIDs. */
    final Set<UUID> secretItems = new HashSet<>();
    final Set<UUID> secretBats = new HashSet<>();
    final Map<UUID, RoomState> secretRoom = new HashMap<>();
    // The Blood room.
    final Set<UUID> bloodMobs = new HashSet<>();
    int bloodLeft;
    long nextBlood;
    final List<Entity> watcher = new ArrayList<>();
    boolean bloodDone;
    // The boss.
    BossFight boss;
    Score.Result result;
    boolean failed;
    /** Blessings the party found, by kind (levels add up). */
    final Map<String, Integer> blessings = new LinkedHashMap<>();
    /** Crypt Undead alive, and how many were killed (bonus score, up to 5). */
    final Set<UUID> cryptMobs = new HashSet<>();
    int crypts;
    final Set<UUID> minibosses = new HashSet<>();
    /** Floor VI+: the secret chest that's a Mimic, and the Mimic once it's out. */
    BlockPos mimicChest;
    UUID mimic;
    boolean mimicKilled;
    /** The Fairy room's fairy has revived the dead (once a run). */
    boolean fairyUsed;

    Run(long id, int slot, Floors.Floor floor, ServerLevel level, List<UUID> players, RandomSource random) {
        this.id = id;
        this.tag = RUN_TAG_PREFIX + id;
        this.slot = slot;
        this.floor = floor;
        this.level = level;
        this.players = List.copyOf(players);
        this.random = random;
        this.origin = new BlockPos(slot * SPACING, Y, 0);
        this.bossOrigin = origin.offset(6 * CELL + 64, 0, 0);
        int puzzles = Math.min(floor.puzzles(), RoomLibrary.count("puzzle"));
        int traps = Math.min(floor.traps(), RoomLibrary.count("trap"));
        this.layout = Layout.generate(floor.width(), floor.depth(), puzzles, traps, floor.witherDoors(), random);
        this.rooms = new RoomState[layout.rooms.size()];
        for (Layout.Room room : layout.rooms) {
            rooms[room.id()] = new RoomState(room);
        }
        this.created = level.getGameTime();
    }

    // ---------------------------------------------------------------- building

    /** Builds a little more: one room a tick, then the walls between rooms and the doors, then the boss room. */
    boolean buildStep() {
        if (buildStep < rooms.length) {
            placeRoom(rooms[buildStep]);
            buildStep++;
            return false;
        }
        if (buildStep == rooms.length) {
            gaps();
            doors();
            buildStep++;
            return false;
        }
        if (buildStep == rooms.length + 1) {
            BossFight.buildArena(this);
            // Floor VI and up: one secret chest is a Mimic.
            if (floor.number() >= 6) {
                List<BlockPos> chests = new ArrayList<>();
                for (RoomState state : rooms) {
                    chests.addAll(state.secretChests);
                }
                if (!chests.isEmpty()) {
                    mimicChest = chests.get(random.nextInt(chests.size()));
                }
            }
            buildStep++;
            return false;
        }
        return true;
    }

    private void loadChunks(BlockPos min, BlockPos max) {
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
    }

    /** The room templates this run has built (no room twice while there are others, so no two of the same puzzle). */
    private final java.util.Set<Identifier> usedTemplates = new java.util.HashSet<>();

    private void placeRoom(RoomState state) {
        Layout.Room room = state.room;
        String category = RoomLibrary.category(room);
        Identifier template = RoomLibrary.pick(category, random, usedTemplates);
        if (template != null) {
            usedTemplates.add(template);
        }
        state.min = origin.offset(room.x() * CELL, 0, room.z() * CELL);
        int cellsX = room.cells().stream().mapToInt(c -> c[0]).max().orElse(0) - room.x() + 1;
        int cellsZ = room.cells().stream().mapToInt(c -> c[1]).max().orElse(0) - room.z() + 1;
        state.max = state.min.offset(cellsX * CELL - 2, CELL - 1, cellsZ * CELL - 2);
        loadChunks(state.min, state.max);
        if (template == null) {
            plainRoom(state);
            return;
        }
        StructureTemplate structure = level.getServer().getStructureTemplateManager().get(template).orElse(null);
        if (structure == null) {
            plainRoom(state);
            return;
        }
        state.template = template;
        int turns = room.turns();
        // A room that's the same turned halfway (1×N, 2×2) or any way (1×1, 2×2) can be turned more, for variety.
        if (room.shape() == Layout.Shape.ONE || room.shape() == Layout.Shape.SQUARE) {
            turns = random.nextInt(4);
        } else if (room.shape() != Layout.Shape.L && random.nextBoolean()) {
            turns = (turns + 2) & 3;
        }
        Rotation rotation = switch (turns) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        int sx = structure.getSize().getX();
        int sz = structure.getSize().getZ();
        BlockPos offset = switch (turns) {
            case 1 -> state.min.offset(sz - 1, 0, 0);
            case 2 -> state.min.offset(sx - 1, 0, sz - 1);
            case 3 -> state.min.offset(0, 0, sx - 1);
            default -> state.min;
        };
        // Hypixel's rooms start deeper (basements), so their floor lines up with ours
        int drop = RoomLibrary.isHypixel(template) ? RoomLibrary.HYPIXEL_DROP : 0;
        offset = offset.below(drop);
        if (drop > 0) {
            loadChunks(state.min.below(drop), state.max.above(structure.getSize().getY()));
        }
        structure.placeInWorld(level, offset, offset, new StructurePlaceSettings().setRotation(rotation), random,
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        // Read the room's markers, then remove them.
        AABB box = new AABB(state.min.getX(), state.min.getY() - drop, state.min.getZ(), state.max.getX() + 1,
                Math.max(state.max.getY() + 1, offset.getY() + structure.getSize().getY()), state.max.getZ() + 1);
        for (Marker marker : level.getEntitiesOfClass(Marker.class, box, m -> m.entityTags().contains(TEMPLATE_TAG))) {
            CompoundTag data = ((EntityDataAccess) marker).skyrpg$customData().copyTag();
            String kind = data.getStringOr("catacombs", "");
            state.spots.add(new Spot(kind, marker.position(), data));
            if (kind.equals("secret_chest")) {
                state.secretChests.add(marker.blockPosition());
            }
            if (kind.equals("puzzle")) {
                state.puzzle = Puzzles.create(data.getStringOr("puzzle", ""), this, state, marker.position());
            }
            if (kind.startsWith("secret_")) {
                state.secrets++;
            }
            marker.discard();
        }
    }

    /** A bare room for a shape with no template yet: walls, floor, light, three star mobs. */
    private void plainRoom(RoomState state) {
        BlockPos min = state.min;
        BlockPos max = state.max;
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int y = 0; y < CELL; y++) {
                    boolean edge = x == min.getX() || x == max.getX() || z == min.getZ() || z == max.getZ();
                    BlockState block = y == 0 ? Blocks.BEDROCK.defaultBlockState() : y <= FLOOR || edge || y == CELL - 1
                            ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    set(x, min.getY() + y, z, block);
                }
            }
        }
        Vec3 mid = state.middle();
        set((int) mid.x, min.getY() + CELL - 2, (int) mid.z, Blocks.GLOWSTONE.defaultBlockState());
        if (state.room.type() == Layout.Type.ENTRANCE) {
            state.spots.add(new Spot("spawn", mid, new CompoundTag()));
        } else if (state.room.type() == Layout.Type.BLOOD) {
            state.spots.add(new Spot("watcher", mid.add(0, 2, 0), new CompoundTag()));
        } else if (state.room.type() == Layout.Type.FAIRY) {
            state.spots.add(new Spot("fairy", mid, new CompoundTag()));
        } else {
            for (int i = 0; i < 3; i++) {
                CompoundTag data = new CompoundTag();
                data.putString("role", "zombie");
                data.putBoolean("star", true);
                state.spots.add(new Spot("mob", mid.add(i * 3 - 3, 0, 2), data));
            }
        }
    }

    private void set(int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /** The 1-block gaps between two different rooms: solid wall. */
    private void gaps() {
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        for (int cx = 0; cx < layout.width; cx++) {
            for (int cz = 0; cz < layout.depth; cz++) {
                Layout.Room here = layout.roomAt(cx, cz);
                Layout.Room east = layout.roomAt(cx + 1, cz);
                Layout.Room south = layout.roomAt(cx, cz + 1);
                int x0 = origin.getX() + cx * CELL;
                int z0 = origin.getZ() + cz * CELL;
                if (east != null && east.id() != here.id()) {
                    for (int z = z0; z < z0 + CELL; z++) {
                        for (int y = 0; y < CELL; y++) {
                            set(x0 + CELL - 1, Y + y, z, wall);
                        }
                    }
                }
                if (south != null && south.id() != here.id()) {
                    for (int x = x0; x < x0 + CELL; x++) {
                        for (int y = 0; y < CELL; y++) {
                            set(x, Y + y, z0 + CELL - 1, wall);
                        }
                    }
                }
            }
        }
    }

    /** Cuts every door: open ones are a 3 × 4 doorway; Wither doors are coal, the Blood door red terracotta. */
    private void doors() {
        for (int i = 0; i < layout.doors.size(); i++) {
            Layout.Door door = layout.doors.get(i);
            List<BlockPos> blocks = new ArrayList<>();
            boolean alongX = door.ax() != door.bx();
            int gapX = alongX ? origin.getX() + Math.max(door.ax(), door.bx()) * CELL - 1 : 0;
            int gapZ = alongX ? 0 : origin.getZ() + Math.max(door.az(), door.bz()) * CELL - 1;
            for (int along = 14; along <= 16; along++) {
                for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
                    for (int across = -1; across <= 1; across++) {
                        BlockPos pos = alongX
                                ? new BlockPos(gapX + across, Y + y, origin.getZ() + door.az() * CELL + along)
                                : new BlockPos(origin.getX() + door.ax() * CELL + along, Y + y, gapZ + across);
                        boolean gap = across == 0;
                        BlockState state = Blocks.AIR.defaultBlockState();
                        if (gap && door.lock() == Layout.Lock.WITHER) {
                            state = Blocks.COAL_BLOCK.defaultBlockState();
                            blocks.add(pos);
                        } else if (gap && door.lock() == Layout.Lock.BLOOD) {
                            state = Blocks.DYED_TERRACOTTA.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState();
                            blocks.add(pos);
                        }
                        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
                    }
                }
                // Floor under the doorway.
                BlockPos under = alongX ? new BlockPos(gapX, Y + FLOOR, origin.getZ() + door.az() * CELL + along)
                        : new BlockPos(origin.getX() + door.ax() * CELL + along, Y + FLOOR, gapZ);
                level.setBlock(under, Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            if (!blocks.isEmpty()) {
                lockedDoorBlocks.put(i, blocks);
            }
        }
    }

    // ---------------------------------------------------------------- the run

    RoomState state(Layout.Type type) {
        Layout.Room room = layout.room(type);
        return room == null ? null : rooms[room.id()];
    }

    Vec3 spawnPoint() {
        RoomState entrance = state(Layout.Type.ENTRANCE);
        for (Spot spot : entrance.spots) {
            if (spot.kind().equals("spawn")) {
                return spot.pos();
            }
        }
        return entrance.middle();
    }

    /** The room a position is in, or null. */
    RoomState roomAt(Vec3 pos) {
        int cx = Math.floorDiv((int) Math.floor(pos.x) - origin.getX(), CELL);
        int cz = Math.floorDiv((int) Math.floor(pos.z) - origin.getZ(), CELL);
        Layout.Room room = layout.roomAt(cx, cz);
        return room == null ? null : rooms[room.id()];
    }

    /** Someone walked into a room for the first time: its mobs and secrets appear. */
    void enter(RoomState state) {
        if (state.entered) {
            return;
        }
        state.entered = true;
        if (state.puzzle != null && !state.puzzle.built) {
            state.puzzle.built = true;
            state.puzzle.build();
        }
        switch (state.room.type()) {
            case ENTRANCE, FAIRY -> state.cleared = true;
            default -> {
            }
        }
        if (state.room.type() == Layout.Type.FAIRY) {
            Vec3 at = fairySpot(state);
            net.minecraft.world.entity.animal.allay.Allay fairy = net.minecraft.world.entity.EntityTypes.ALLAY.create(level,
                    net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (fairy != null) {
                fairy.snapTo(at.x, at.y + 1, at.z, 0, 0);
                fairy.setNoAi(true);
                fairy.setNoGravity(true);
                fairy.setPermanentlyInvulnerable(true);
                fairy.setPersistenceRequired();
                fairy.setCustomName(Component.literal("Fairy").withStyle(ChatFormatting.LIGHT_PURPLE));
                fairy.addTag(tag);
                fairy.addTag(Catacombs.TAG);
                level.addFreshEntity(fairy);
            }
        }
        // From Floor II a room's elite can be a miniboss; any normal room can hide a Crypt Undead (up to 8 a run).
        boolean miniboss = state.room.type() == Layout.Type.NORMAL && floor.number() >= 2 && random.nextInt(5) < 2;
        if (state.room.type() == Layout.Type.NORMAL && cryptMobs.size() + crypts < 8 && random.nextInt(10) < 3) {
            List<Spot> mobSpots = state.spots.stream().filter(s -> s.kind().equals("mob")).toList();
            if (!mobSpots.isEmpty()) {
                Mob crypt = DungeonMobs.spawn(level, mobSpots.get(random.nextInt(mobSpots.size())).pos(), "crypt", false, floor, tag);
                if (crypt != null) {
                    crypt.setCustomName(Component.literal("Crypt Undead").withStyle(ChatFormatting.DARK_GREEN));
                    cryptMobs.add(crypt.getUUID());
                }
            }
        }
        for (Spot spot : state.spots) {
            switch (spot.kind()) {
                case "mob" -> {
                    boolean star = spot.data().getBooleanOr("star", true);
                    String role = spot.data().getStringOr("role", "zombie");
                    Mob mob = miniboss && role.equals("elite") ? DungeonMobs.miniboss(level, spot.pos(), floor, tag)
                            : DungeonMobs.spawn(level, spot.pos(), role, star, floor, tag);
                    if (mob != null && miniboss && role.equals("elite")) {
                        minibosses.add(mob.getUUID());
                    }
                    if (mob != null && star) {
                        state.stars.add(mob.getUUID());
                        state.starCount++;
                    }
                }
                case "secret_item" -> {
                    Display.ItemDisplay item = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
                    item.snapTo(spot.pos().x, spot.pos().y + 0.4, spot.pos().z, 0, 0);
                    ((com.skyrpg.hub.mixin.ItemDisplayAccess) item).skyrpg$setItemStack(new ItemStack(Items.SPLASH_POTION));
                    ((DisplayAccess) item).skyrpg$setBillboard(Display.BillboardConstraints.VERTICAL);
                    item.addTag(tag);
                    item.addTag(Catacombs.TAG);
                    level.addFreshEntity(item);
                    secretItems.add(item.getUUID());
                    secretRoom.put(item.getUUID(), state);
                }
                case "secret_bat" -> {
                    Bat bat = EntityTypes.BAT.create(level, EntitySpawnReason.EVENT);
                    if (bat != null) {
                        bat.snapTo(spot.pos().x, spot.pos().y, spot.pos().z, 0, 0);
                        bat.setPersistenceRequired();
                        bat.addTag(tag);
                        bat.addTag(Catacombs.TAG);
                        bat.addTag(com.skyrpg.skyblock.zone.Zones.FIGHTABLE_TAG);
                        level.addFreshEntity(bat);
                        secretBats.add(bat.getUUID());
                        secretRoom.put(bat.getUUID(), state);
                    }
                }
                default -> {
                }
            }
        }
        if (state.room.type() == Layout.Type.NORMAL && state.starCount == 0) {
            clear(state);
        }
    }

    /** A dungeon mob of this run died. */
    void mobDied(Entity entity, ServerPlayer killer) {
        UUID id = entity.getUUID();
        if (bloodMobs.remove(id)) {
            return;
        }
        if (secretBats.remove(id)) {
            secretFound(secretRoom.remove(id), killer);
            return;
        }
        if (cryptMobs.remove(id)) {
            crypts++;
            broadcast(Component.literal("Crypt Undead killed! (" + Math.min(5, crypts) + "/5 bonus)").withStyle(ChatFormatting.DARK_GREEN));
            return;
        }
        if (id.equals(mimic)) {
            mimicKilled = true;
            broadcast(Component.literal("Mimic Killed!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        }
        if (minibosses.remove(id)) {
            broadcast(Component.literal(entity.getName().getString().replace(DungeonMobs.STAR_GLYPH, "") + " has been defeated!")
                    .withStyle(ChatFormatting.YELLOW));
            Blessings.bless(this, killer);
        }
        for (RoomState state : rooms) {
            if (state.puzzle != null) {
                state.puzzle.died(entity, killer);
            }
        }
        if (boss != null && entity instanceof net.minecraft.world.entity.LivingEntity living) {
            boss.died(living);
        }
        for (RoomState state : rooms) {
            if (state.stars.remove(id) && state.stars.isEmpty() && !state.cleared) {
                clear(state);
            }
        }
    }

    private void clear(RoomState state) {
        state.cleared = true;
        for (Layout.Key key : layout.keys) {
            if (key.room() == state.room.id()) {
                dropKey(state, key.lock());
            }
        }
        for (ServerPlayer player : online()) {
            if (roomAt(player.position()) == state) {
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7f, 1.6f);
            }
        }
    }

    private void dropKey(RoomState state, Layout.Lock lock) {
        Vec3 at = state.middle();
        for (Spot spot : state.spots) {
            if (spot.kind().equals("key")) {
                at = spot.pos();
            }
        }
        boolean blood = lock == Layout.Lock.BLOOD;
        Display.ItemDisplay key = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        key.snapTo(at.x, at.y + 0.6, at.z, 0, 0);
        ((com.skyrpg.hub.mixin.ItemDisplayAccess) key).skyrpg$setItemStack(new ItemStack(blood ? Items.REDSTONE_BLOCK : Items.WITHER_SKELETON_SKULL));
        ((DisplayAccess) key).skyrpg$setBillboard(Display.BillboardConstraints.VERTICAL);
        key.addTag(tag);
        key.addTag(Catacombs.TAG);
        level.addFreshEntity(key);
        keyEntities.put(key.getUUID(), lock);
        Display.TextDisplay label = label(at.add(0, 1.4, 0), Component.literal(blood ? "Blood Key" : "Wither Key")
                .withStyle(blood ? ChatFormatting.RED : ChatFormatting.DARK_GRAY, ChatFormatting.BOLD));
        keyEntities.put(label.getUUID(), null);
        broadcast(Component.literal("A " + (blood ? "Blood" : "Wither") + " Key has appeared!").withStyle(blood ? ChatFormatting.RED : ChatFormatting.GRAY));
    }

    Display.TextDisplay label(Vec3 at, Component text) {
        Display.TextDisplay display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
        display.snapTo(at.x, at.y, at.z, 0, 0);
        ((TextDisplayAccess) display).skyrpg$setText(text);
        ((TextDisplayAccess) display).skyrpg$setBackgroundColor(0x800B1018);
        ((DisplayAccess) display).skyrpg$setBillboard(Display.BillboardConstraints.CENTER);
        display.addTag(tag);
        display.addTag(Catacombs.TAG);
        level.addFreshEntity(display);
        return display;
    }

    private Vec3 fairySpot(RoomState state) {
        for (Spot spot : state.spots) {
            if (spot.kind().equals("fairy")) {
                return spot.pos();
            }
        }
        return state.middle();
    }

    /** Every tick: the puzzles you walk through (a running player crosses a block in a few ticks). */
    void tickPuzzles() {
        for (RoomState state : rooms) {
            if (state.puzzle != null && state.puzzle.built && !state.puzzle.solved && !state.puzzle.failed) {
                state.puzzle.tick();
            }
        }
    }

    /** Every half second: rooms walked into, keys and secret items picked up, the Blood room's waves. */
    void tick(long now) {
        // The fairy revives everyone who's dead when a living teammate reaches it (once a run).
        RoomState fairyRoom = state(Layout.Type.FAIRY);
        if (!fairyUsed && !ghosts.isEmpty() && fairyRoom != null && fairyRoom.entered) {
            Vec3 at = fairySpot(fairyRoom);
            ServerPlayer near = online().stream().filter(p -> !ghosts.contains(p.getUUID()) && p.position().distanceTo(at) < 5).findFirst().orElse(null);
            if (near != null) {
                fairyUsed = true;
                for (ServerPlayer ghost : online()) {
                    if (ghosts.contains(ghost.getUUID())) {
                        Catacombs.revive(this, ghost);
                        ghost.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), ghost.getYRot(), ghost.getXRot(), true);
                    }
                }
                broadcast(Component.literal("The Fairy revived the fallen!").withStyle(ChatFormatting.LIGHT_PURPLE));
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, at.x, at.y + 1, at.z, 20, 1, 1, 1, 0);
            }
        }
        for (ServerPlayer player : online()) {
            if (ghosts.contains(player.getUUID())) {
                continue;
            }
            RoomState state = roomAt(player.position());
            if (state != null && !state.entered && player.getY() > origin.getY()) {
                enter(state);
            }
            // Keys and secret items: picked up by walking into them.
            for (Entity entity : level.getEntities(player, player.getBoundingBox().inflate(1.0), e -> e.entityTags().contains(tag))) {
                Layout.Lock lock = keyEntities.get(entity.getUUID());
                if (lock != null) {
                    pickUpKey(player, entity, lock);
                } else if (secretItems.remove(entity.getUUID())) {
                    entity.discard();
                    secretFound(secretRoom.remove(entity.getUUID()), player);
                }
            }
        }
        if (phase == Phase.BLOOD) {
            bloodTick(now);
        }
        if (phase == Phase.BOSS && boss != null) {
            boss.tick(now);
        }
    }

    private void pickUpKey(ServerPlayer player, Entity key, Layout.Lock lock) {
        // The label near it goes too.
        for (Entity near : level.getEntities(key, key.getBoundingBox().inflate(0.5, 2, 0.5), e -> keyEntities.containsKey(e.getUUID()))) {
            keyEntities.remove(near.getUUID());
            near.discard();
        }
        keyEntities.remove(key.getUUID());
        key.discard();
        if (lock == Layout.Lock.BLOOD) {
            bloodKey = true;
        } else {
            witherKeys++;
        }
        broadcast(Component.literal(player.getScoreboardName()).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" has obtained the " + (lock == Layout.Lock.BLOOD ? "Blood Key" : "Wither Key") + "!")
                        .withStyle(lock == Layout.Lock.BLOOD ? ChatFormatting.RED : ChatFormatting.GRAY)));
        for (ServerPlayer p : online()) {
            Msg.playSound(p, SoundEvents.ITEM_PICKUP, 1.0f, 0.8f);
        }
    }

    /** A secret was found (a chest opened, an item picked up, a bat killed). */
    void secretFound(RoomState state, ServerPlayer finder) {
        if (state == null) {
            return;
        }
        state.secretsFound++;
        if (finder != null) {
            Secrets.reward(finder, this);
            finder.sendOverlayMessage(Component.literal(state.secretsFound + "/" + state.secrets + " Secrets").withStyle(ChatFormatting.AQUA));
            Msg.playSound(finder, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
        }
    }

    /** The Mimic: a secret chest that turns out to be alive. */
    void releaseMimic(ServerPlayer opener, BlockPos pos) {
        mimicChest = null;
        level.removeBlock(pos, false);
        Mob mimic = (Mob) net.minecraft.world.entity.EntityTypes.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (mimic == null) {
            return;
        }
        mimic.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        double health = floor.master() ? 150_000_000 : 1_000_000 + 1_000_000 * Math.max(0, floor.number() - 6);
        com.skyrpg.skyblock.zone.Zones.dressAs(mimic, "Mimic", 200 + (floor.master() ? 200 : 0), health, 2_000 * Math.pow(1.5, floor.number()), 100);
        if (mimic instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie) {
            zombie.setBaby(true);
        }
        mimic.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CHEST));
        mimic.setPersistenceRequired();
        mimic.addTag(DungeonMobs.TAG);
        mimic.addTag(tag);
        mimic.addTag(com.skyrpg.skyblock.zone.Zones.FIGHTABLE_TAG);
        level.addFreshEntity(mimic);
        mimic.setTarget(opener);
        this.mimic = mimic.getUUID();
        broadcast(Component.literal("That chest was a Mimic!").withStyle(ChatFormatting.RED));
        Msg.playSound(opener, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 1.0f, 0.6f);
    }

    /** Opens a locked door if the party has its key: true when the click was on one. */
    boolean useDoor(ServerPlayer player, BlockPos pos) {
        for (Map.Entry<Integer, List<BlockPos>> entry : lockedDoorBlocks.entrySet()) {
            if (!entry.getValue().contains(pos) || openedDoors.contains(entry.getKey())) {
                continue;
            }
            Layout.Lock lock = layout.doors.get(entry.getKey()).lock();
            boolean have = lock == Layout.Lock.BLOOD ? bloodKey : witherKeys > 0;
            if (!have) {
                player.sendOverlayMessage(Component.literal("You need the " + (lock == Layout.Lock.BLOOD ? "Blood Key" : "Wither Key")
                        + " to open this door!").withStyle(ChatFormatting.RED));
                Msg.playSound(player, SoundEvents.VILLAGER_NO, 0.8f, 1.0f);
                return true;
            }
            if (lock == Layout.Lock.BLOOD) {
                bloodKey = false;
            } else {
                witherKeys--;
            }
            openedDoors.add(entry.getKey());
            for (BlockPos block : entry.getValue()) {
                level.destroyBlock(block, false);
            }
            broadcast(Component.literal(player.getScoreboardName()).withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(" opened a " + (lock == Layout.Lock.BLOOD ? "BLOOD" : "WITHER") + " door!")
                            .withStyle(lock == Layout.Lock.BLOOD ? ChatFormatting.RED : ChatFormatting.DARK_GRAY)));
            if (lock == Layout.Lock.BLOOD) {
                startBlood();
            }
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- the Blood room

    private Vec3 watcherSpot() {
        RoomState blood = state(Layout.Type.BLOOD);
        for (Spot spot : blood.spots) {
            if (spot.kind().equals("watcher")) {
                return spot.pos();
            }
        }
        return blood.middle().add(0, 2, 0);
    }

    private void startBlood() {
        phase = Phase.BLOOD;
        RoomState blood = state(Layout.Type.BLOOD);
        blood.entered = true;
        Vec3 at = watcherSpot();
        Display.ItemDisplay head = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        head.snapTo(at.x, at.y + 1, at.z, 0, 0);
        ((com.skyrpg.hub.mixin.ItemDisplayAccess) head).skyrpg$setItemStack(new ItemStack(Items.WITHER_SKELETON_SKULL));
        ((DisplayAccess) head).skyrpg$setBillboard(Display.BillboardConstraints.CENTER);
        ((DisplayAccess) head).skyrpg$setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2.5f, 2.5f, 2.5f),
                new Quaternionf()));
        head.addTag(tag);
        head.addTag(Catacombs.TAG);
        level.addFreshEntity(head);
        watcher.add(head);
        watcher.add(label(at.add(0, 2.8, 0), Component.literal("The Watcher").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
        bloodLeft = 6 + floor.number() * 2;
        nextBlood = level.getGameTime() + 60;
        watcherSays("Things feel a little more roomy now, eh?");
        watcherSays("Let's see how you can handle this.");
    }

    void watcherSays(String text) {
        broadcast(Component.literal("[BOSS] The Watcher").withStyle(ChatFormatting.RED).append(Component.literal(": " + text)
                .withStyle(ChatFormatting.WHITE)));
    }

    private void bloodTick(long now) {
        bloodMobs.removeIf(id -> {
            Entity e = level.getEntity(id);
            return e == null || !e.isAlive();
        });
        if (bloodLeft > 0 && now >= nextBlood) {
            Vec3 at = watcherSpot();
            for (int i = 0; i < 2 && bloodLeft > 0; i++) {
                Vec3 spot = new Vec3(at.x + random.nextInt(9) - 4, state(Layout.Type.BLOOD).min.getY() + FLOOR + 1, at.z + random.nextInt(9) - 4);
                Mob mob = DungeonMobs.blood(level, spot, floor, tag, BLOOD_NAMES[random.nextInt(BLOOD_NAMES.length)]);
                if (mob != null) {
                    bloodMobs.add(mob.getUUID());
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, spot.x, spot.y + 1, spot.z, 20, 0.3, 0.6, 0.3, 0.02);
                }
                bloodLeft--;
            }
            nextBlood = now + 60;
        }
        if (bloodLeft == 0 && bloodMobs.isEmpty() && !bloodDone) {
            bloodDone = true;
            state(Layout.Type.BLOOD).cleared = true;
            if (floor.number() == 0) {
                watcherSays("You have proven yourself. You may pass.");
                finish(false);
                return;
            }
            watcherSays("You have proven yourself. You may pass.");
            Vec3 at = watcherSpot();
            Interaction portal = EntityTypes.INTERACTION.create(level, EntitySpawnReason.EVENT);
            if (portal != null) {
                ((InteractionAccess) portal).skyrpg$setWidth(2f);
                ((InteractionAccess) portal).skyrpg$setHeight(3f);
                Vec3 floorAt = standingSpot(at);
                portal.snapTo(floorAt.x, floorAt.y, floorAt.z, 0, 0);
                portal.addTag(tag);
                portal.addTag(Catacombs.TAG);
                portal.addTag(Catacombs.BOSS_PORTAL);
                level.addFreshEntity(portal);
                label(floorAt.add(0, 3.2, 0), Component.literal("Boss Room").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        .append(Component.literal("\nright-click").withStyle(ChatFormatting.GRAY)));
            }
            watcher.forEach(Entity::discard);
        }
    }

    /** The first spot to stand on at this x/z, from the floor up (the top of the Watcher's dais). */
    private Vec3 standingSpot(Vec3 at) {
        int bx = (int) Math.floor(at.x);
        int bz = (int) Math.floor(at.z);
        for (int y = origin.getY() + FLOOR + 1; y < origin.getY() + CELL - 3; y++) {
            BlockPos pos = new BlockPos(bx, y, bz);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.below()).isAir()) {
                return new Vec3(at.x, y, at.z);
            }
        }
        return new Vec3(at.x, origin.getY() + FLOOR + 1, at.z);
    }

    // ---------------------------------------------------------------- boss and the end

    void enterBoss() {
        if (phase == Phase.BOSS || phase == Phase.DONE) {
            return;
        }
        phase = Phase.BOSS;
        // The dead come back for the boss.
        for (ServerPlayer player : online()) {
            Catacombs.revive(this, player);
        }
        boss = BossFight.start(this);
        for (ServerPlayer player : online()) {
            boss.teleportIn(player);
        }
    }

    void finish() {
        finish(false);
    }

    /** The end: the score and rewards; a failed run (everyone dead, out of time) gives a quarter of the XP and no chests. */
    void finish(boolean failed) {
        if (phase == Phase.DONE) {
            return;
        }
        phase = Phase.DONE;
        this.failed = failed;
        finished = level.getGameTime();
        result = Score.compute(this);
        Rewards.complete(this);
    }

    List<ServerPlayer> online() {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : players) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == level && Catacombs.runOf(player) == this) {
                out.add(player);
            }
        }
        return out;
    }

    void broadcast(Component message) {
        for (ServerPlayer player : online()) {
            player.sendSystemMessage(message);
        }
    }

    /** Seconds since the timer started. */
    int seconds() {
        if (started < 0) {
            return 0;
        }
        long end = finished >= 0 ? finished : level.getGameTime();
        return (int) ((end - started) / 20);
    }

    /** The share of rooms cleared, 0 to 1. */
    double clearedShare() {
        int done = 0;
        for (RoomState state : rooms) {
            if (state.cleared) {
                done++;
            }
        }
        return rooms.length == 0 ? 0 : (double) done / rooms.length;
    }

    /** Puzzles not solved (failed, or never done): each costs Skill score. */
    int failedPuzzles() {
        int failed = 0;
        for (RoomState state : rooms) {
            if (state.puzzle != null && !state.puzzle.solved) {
                failed++;
            }
        }
        return failed;
    }

    int secretsFound() {
        int found = 0;
        for (RoomState state : rooms) {
            found += state.secretsFound;
        }
        return found;
    }

    int secretsTotal() {
        int total = 0;
        for (RoomState state : rooms) {
            total += state.secrets;
        }
        return total;
    }

    /** The whole run's box (rooms and the boss arena), for clearing it away. */
    AABB area() {
        // Hypixel's rooms go 30 deeper and its arenas up to 120 high
        return new AABB(origin.getX() - 4, origin.getY() - 4 - RoomLibrary.HYPIXEL_DROP, origin.getZ() - 4, bossOrigin.getX() + 160, origin.getY() + 130,
                origin.getZ() + 6 * CELL + 64);
    }
}
