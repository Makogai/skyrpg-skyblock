package com.skyrpg.skyblock.rift;

import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.recipe.CraftMenu;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Mirrorverse (Hypixel's): Dr. Emmett's eight rooms behind the door in West Village's courtyard (the Supreme
 * Timecharm and 3:00 of Rift Time; time stands still inside, and nothing hurts). Each room is split by a glass mirror
 * and the reflection shows what your side hides; the rooms are built in tools/builds/hub_areas/rift_mirrorverse.py and
 * read here by reflecting your position across the glass. Finishing pays 60,000 Motes once, and Dr. Emmett's
 * reflection, ttemme .rD, sells the mrahcemiT esrevrorriM (10,000 Motes and 10 Shadow Crux).
 */
final class Mirrorverse {
    static final String ZONE = "Mirrorverse";
    static final String CHARM = "RIFT_TROPHY_MIRRORED";
    private static final String MOB_TAG = "skyrpg_mirrorverse";
    private static final String REFLECTION_TAG = "skyrpg_mirrorverse_reflection";
    private static final int ROOMS = 8;
    private static final long REWARD = 60_000;
    private static final int BEAT = 50;
    private static final int DANCE_OPENS = 7;
    /** Hypixel's dance calls; each step is a few of them at once. */
    private static final List<List<String>> DANCE = List.of(List.of("Move!"), List.of("Move!", "Sneak!"), List.of("Move!", "Stand!"),
            List.of("Sneak!", "Jump!"), List.of("Stand!", "Don't jump!"), List.of("Move!", "Jump!"), List.of("Sneak!", "Don't jump!"),
            List.of("Stand!", "Jump!"), List.of("Move!", "Punch!"), List.of("Sneak!", "Punch!"), List.of("Jump!", "Punch!"),
            List.of("Move!", "Stand!", "Punch!"));

    /** A room: its number, interior x range, floor y. */
    private record Room(int n, int x0, int x1, int floor) {
    }

    private static final class Dance {
        int step = -1;
        int ticks = -40;
        BlockState tile;
        boolean jumped;
        boolean punched;
    }

    private static final class Climb {
        final Set<Integer> reached = new HashSet<>();
        boolean usedLever;
    }

    private record Sale(String item, int count, long motes, String costItem, int costCount, boolean needsFinish) {
    }

    private static final List<Sale> SHOP = List.of(new Sale(CHARM, 1, 10_000, "SHADOW_CRUX", 10, true),
            new Sale("ETALPTSEHC_NORI", 1, 0, "WYLD_CHESTPLATE", 1, true),
            new Sale("ENCHANTED_BOOK_BUNDLE_REFLECTION", 1, 5_000, "SHADOW_CRUX", 16, true));

    private static List<Room> rooms = List.of();
    private static Map<String, Rift.Spot> loadedFrom;
    private static int zc;
    private static final Map<UUID, Dance> DANCES = new HashMap<>();
    private static final Map<UUID, Climb> CLIMBS = new HashMap<>();
    private static final Map<UUID, Long> NOTICED = new HashMap<>();
    private static final Map<UUID, Long> SWITCHED = new HashMap<>();
    private static final Map<UUID, Long> DOOR = new HashMap<>();
    /** Room 4's platforms that dropped, and when they come back. */
    private static final Map<BlockPos, Long> DROPPED = new HashMap<>();
    /** Room 3's invisible mobs and their reflections. */
    private static final List<Mob[]> MOBS = new ArrayList<>();
    private static long roomThreeSeen;

    private Mirrorverse() {
    }

    static void init() {
        Identifier first = SkyRpgSkyblock.id("rift_first");
        UseBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(first, (player, level, hand, hit) -> {
            if (!Rift.isRift(level)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                // The Hub's protection turns these clicks away on the client: send them on, the server decides.
                BlockState state = level.getBlockState(hit.getBlockPos());
                return state.is(Blocks.GLASS) || state.is(Blocks.BARRIER) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return player instanceof ServerPlayer server && use(server, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(first, (entity, source, amount) -> {
            if (entity.entityTags().contains(REFLECTION_TAG)) {
                return false;
            }
            if (entity instanceof ServerPlayer player && inside(player)) {
                player.clearFire();
                return false;
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.entityTags().contains(MOB_TAG) && source.getEntity() instanceof ServerPlayer killer) {
                String drop = entity instanceof Slime ? "MIRRORED_SLIME_BALL" : entity instanceof Zombie ? "MIRRORED_WOOD" : "MIRRORED_STRING";
                give(killer, drop, entity instanceof Slime ? 1 : entity instanceof Zombie ? 4 : 3);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if ((entity.entityTags().contains(MOB_TAG) || entity.entityTags().contains(REFLECTION_TAG))
                    && MOBS.stream().noneMatch(pair -> pair[0] == entity || pair[1] == entity)) {
                entity.discard();
            }
        });
        Abilities.register("lazer", Mirrorverse::lazer);
        ServerTickEvents.END_SERVER_TICK.register(Mirrorverse::tick);
    }

    // ---------------------------------------------------------------- the rooms

    private static List<Room> rooms() {
        Map<String, Rift.Spot> spots = Rift.spots();
        if (spots != loadedFrom) {
            loadedFrom = spots;
            List<Room> list = new ArrayList<>();
            for (int n = 1; n <= ROOMS; n++) {
                Rift.Spot start = spots.get("mv_start_" + n);
                Rift.Spot end = spots.get("mv_end_" + n);
                if (start == null || end == null) {
                    break;
                }
                list.add(new Room(n, (int) Math.floor(start.pos().x) - 1, (int) Math.floor(end.pos().x), (int) Math.floor(start.pos().y) - 1));
            }
            Rift.Spot plane = spots.get("mv_plane");
            zc = plane == null ? 0 : (int) Math.floor(plane.pos().z);
            rooms = List.copyOf(list);
        }
        return rooms;
    }

    private static boolean inside(ServerPlayer player) {
        Zones.Zone zone = Zones.at(player.level(), player.blockPosition());
        return zone != null && zone.name().equals(ZONE);
    }

    private static Room roomOf(double x) {
        for (Room room : rooms()) {
            if (x >= room.x0() - 1 && x < room.x1() + 2) {
                return room;
            }
        }
        return null;
    }

    private static int mirror(int z) {
        return 2 * zc - z;
    }

    private static void startOf(ServerPlayer player, int n) {
        Rift.Spot start = Rift.spots().get("mv_start_" + n);
        if (start != null) {
            player.teleportTo((ServerLevel) player.level(), start.pos().x, start.pos().y, start.pos().z, java.util.Set.of(), start.yaw(), 0, true);
            player.clearFire();
        }
    }

    private static boolean noticed(ServerPlayer player, long now) {
        if (now - NOTICED.getOrDefault(player.getUUID(), -100L) < 40) {
            return false;
        }
        NOTICED.put(player.getUUID(), now);
        return true;
    }

    /** Through the door in West Village's courtyard. */
    static void enter(ServerPlayer player) {
        if (!Timecharms.secured(player, Timecharms.SUPREME)) {
            player.sendOverlayMessage(Component.literal("The Mirrorverse needs the " + Timecharms.name(Timecharms.SUPREME) + " secured with Elise.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (Rift.timeLeft(player) < 180) {
            player.sendOverlayMessage(Component.literal("You need at least 3:00 of Rift Time left to enter the Mirrorverse.").withStyle(ChatFormatting.RED));
            return;
        }
        RiftFlags.set(player, "mv:room:1");
        startOf(player, 1);
        Msg.playSound(player, SoundEvents.PORTAL_TRAVEL, 0.25f, 1.6f);
        emmettSays(player, "Welcome to the Mirrorverse! Time stands still in here. Look in the mirror: it knows the way.");
    }

    private static void advance(ServerPlayer player, Room room) {
        int next = room.n() + 1;
        if (next > ROOMS) {
            return;
        }
        RiftFlags.set(player, "mv:room:" + next);
        startOf(player, next);
        Msg.playSound(player, SoundEvents.IRON_DOOR_OPEN, 1f, 1f);
        player.sendOverlayMessage(Component.literal("Room " + next + ": " + HINTS.get(next - 1)).withStyle(ChatFormatting.AQUA));
    }

    private static final List<String> HINTS = List.of("match your levers to the mirror's", "the mirror shows the safe path",
            "the mirror shows what you can't see; craft the remmaH yniT", "the mirror shows the safe platforms",
            "click the mirror to step through it", "do what Dr. Emmett calls", "the mirror shows the invisible steps",
            "Dr. Emmett is waiting");

    /** Whether the room's door opens for you, or why not. */
    private static String locked(ServerPlayer player, Room room) {
        ServerLevel level = (ServerLevel) player.level();
        return switch (room.n()) {
            case 1 -> {
                for (int x = room.x0(); x <= room.x1(); x++) {
                    BlockState mine = level.getBlockState(new BlockPos(x, room.floor() + 2, zc - 7));
                    BlockState theirs = level.getBlockState(new BlockPos(x, room.floor() + 2, zc + 7));
                    if (mine.is(Blocks.LEVER) && theirs.is(Blocks.LEVER)
                            && mine.getValue(BlockStateProperties.POWERED) != theirs.getValue(BlockStateProperties.POWERED)) {
                        yield "Your levers don't match the mirror's.";
                    }
                }
                yield null;
            }
            case 3 -> Sacks.countAnywhere(player, "TINY_HAMMER") > 0 ? null : "The door won't budge. Something hammer-shaped might help.";
            case 6 -> RiftFlags.has(player, "mv:dance") ? null : "Dance first! Step onto a coloured tile.";
            default -> null;
        };
    }

    // ---------------------------------------------------------------- right-clicks: the mirror (room 5), the table (room 3), levers (room 7)

    private static boolean use(ServerPlayer player, BlockPos pos) {
        if (!inside(player)) {
            return false;
        }
        Room room = roomOf(player.getX());
        if (room == null) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockState state = level.getBlockState(pos);
        Rift.Spot table = Rift.spots().get("mv_table");
        if (room.n() == 3 && table != null && pos.equals(BlockPos.containing(table.pos()))) {
            CraftMenu.open(player);
            return true;
        }
        if (room.n() == 5 && pos.getZ() == zc && state.is(Blocks.GLASS)) {
            long now = level.getServer().getTickCount();
            if (now - SWITCHED.getOrDefault(player.getUUID(), -100L) < 8) {
                return true;
            }
            double dz = 2 * (zc + 0.5) - 2 * player.getZ();
            if (!level.noCollision(player, player.getBoundingBox().move(0, 0, dz))) {
                player.sendOverlayMessage(Component.literal("Something's in the way on the other side.").withStyle(ChatFormatting.RED));
                return true;
            }
            SWITCHED.put(player.getUUID(), now);
            Vec3 motion = player.getDeltaMovement();
            // through the looking glass: you come out facing back the way you looked
            player.teleportTo(level, player.getX(), player.getY(), player.getZ() + dz, java.util.Set.of(), 180 - player.getYRot(), player.getXRot(), true);
            player.setDeltaMovement(motion.x, motion.y, -motion.z);
            player.syncVelocity = true;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
            Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.5f);
            return true;
        }
        if (room.n() == 7 && state.is(Blocks.LEVER)) {
            int cp = switch (zc - pos.getZ()) {
                case 2 -> 1;
                case 4 -> 2;
                case 6 -> 3;
                default -> 0;
            };
            Climb climb = CLIMBS.computeIfAbsent(player.getUUID(), id -> new Climb());
            Rift.Spot spot = Rift.spots().get("mv_cp_" + cp);
            if (cp == 0 || spot == null) {
                return true;
            }
            if (!climb.reached.contains(cp)) {
                player.sendOverlayMessage(Component.literal("You haven't reached checkpoint " + cp + " yet.").withStyle(ChatFormatting.RED));
                return true;
            }
            climb.usedLever = true;
            player.teleportTo(spot.pos().x, spot.pos().y, spot.pos().z);
            Msg.playSound(player, SoundEvents.ENDERMAN_TELEPORT, 0.6f, 1.4f);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- the tick

    private static void tick(MinecraftServer server) {
        ServerLevel level = Rift.level(server);
        if (level == null || rooms().isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        Rift.Spot door = Rift.spots().get("mirrorverse_door");
        boolean roomThree = false;
        for (ServerPlayer player : level.players()) {
            if (door != null && player.position().distanceToSqr(door.pos()) < 1.3 * 1.3 && now - DOOR.getOrDefault(player.getUUID(), -100L) > 40) {
                DOOR.put(player.getUUID(), now);
                enter(player);
                continue;
            }
            if (!inside(player) || player.isSpectator()) {
                DANCES.remove(player.getUUID());
                continue;
            }
            Room room = roomOf(player.getX());
            if (room == null) {
                continue;
            }
            roomThree |= room.n() == 3;
            if (player.isInLava() || player.getY() < room.floor() - 3) {
                startOf(player, room.n());
                player.sendOverlayMessage(Component.literal("OH NO! THE LAVA OOFED YOU BACK TO THE START!").withStyle(ChatFormatting.RED));
                Msg.playSound(player, SoundEvents.LAVA_EXTINGUISH, 1f, 1f);
                continue;
            }
            BlockPos feet = player.blockPosition();
            int bx = feet.getX();
            int bz = feet.getZ();
            if (room.n() == 2 && player.onGround() && bz < zc && bx >= room.x0() + 3 && bx <= room.x1() - 3
                    && level.getBlockState(new BlockPos(bx, room.floor(), mirror(bz))).is(Blocks.LAVA)) {
                startOf(player, 2);
                player.sendOverlayMessage(Component.literal("The mirror says that was lava. Back to the start!").withStyle(ChatFormatting.RED));
                Msg.playSound(player, SoundEvents.LAVA_EXTINGUISH, 1f, 1f);
                continue;
            }
            if (room.n() == 4 && player.onGround() && bz < zc) {
                BlockPos under = new BlockPos(bx, room.floor(), bz);
                if (level.getBlockState(under).is(Blocks.CRIMSON_PLANKS) && !level.getBlockState(new BlockPos(bx, room.floor(), mirror(bz))).is(Blocks.CRIMSON_PLANKS)) {
                    drop(level, room, under, now);
                }
            }
            if (room.n() == 5) {
                Rift.Spot brain = Rift.spots().get("mv_brain");
                Rift.Spot after = Rift.spots().get("mv_after_pit");
                if (brain != null && after != null && player.position().distanceToSqr(brain.pos()) < 2.2 * 2.2) {
                    if (RiftFlags.set(player, "mv:brain")) {
                        give(player, "BIG_BRAIN_TALISMAN", 1);
                        player.sendSystemMessage(Component.literal("Big brain! You found the Big Brain Talisman.").withStyle(ChatFormatting.LIGHT_PURPLE));
                    }
                    player.teleportTo(after.pos().x, after.pos().y, after.pos().z);
                }
            }
            if (room.n() == 6) {
                dance(player, level, room, now);
            }
            if (room.n() == 7) {
                climb(player, room);
            }
            if (room.n() == 8) {
                Rift.Spot portal = Rift.spots().get("mv_portal");
                if (portal != null && player.position().distanceToSqr(portal.pos()) < 1.6 * 1.6) {
                    leave(player);
                    continue;
                }
            }
            // the exit door
            Rift.Spot end = Rift.spots().get(room.n() == 7 ? "mv_top" : "mv_end_" + room.n());
            if (room.n() < ROOMS && end != null && player.getX() > end.pos().x - 0.2 && Math.abs(player.getZ() - end.pos().z) < 1.6
                    && Math.abs(player.getY() - end.pos().y) < 1.5) {
                String why = locked(player, room);
                if (why == null) {
                    advance(player, room);
                } else if (noticed(player, now)) {
                    player.sendOverlayMessage(Component.literal(why).withStyle(ChatFormatting.RED));
                }
            }
        }
        if (roomThree) {
            roomThreeSeen = now;
        }
        mobs(level, now);
        if (now % 10 == 0 && !DROPPED.isEmpty()) {
            DROPPED.entrySet().removeIf(e -> {
                if (e.getValue() > now) {
                    return false;
                }
                level.setBlock(e.getKey(), Blocks.CRIMSON_PLANKS.defaultBlockState(), 3);
                return true;
            });
        }
    }

    /** A fake platform (one the mirror doesn't show) gives way under you, and comes back after 3 seconds. */
    private static void drop(ServerLevel level, Room room, BlockPos under, long now) {
        int start = room.x0() + 3;
        int xa = start + (under.getX() - start) / 3 * 3;
        int d = zc - under.getZ();
        int da = d >= 5 ? 6 : 2;
        for (int x = xa; x <= xa + 1; x++) {
            for (int dd = da; dd <= da + 1; dd++) {
                BlockPos pos = new BlockPos(x, room.floor(), zc - dd);
                if (level.getBlockState(pos).is(Blocks.CRIMSON_PLANKS)) {
                    level.setBlock(pos, Blocks.LAVA.defaultBlockState(), 3);
                    DROPPED.put(pos, now + 60);
                }
            }
        }
        level.playSound(null, under, SoundEvents.WOOD_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.7f);
    }

    static void leave(ServerPlayer player) {
        Rift.Spot door = Rift.spots().get("mirrorverse_door");
        DANCES.remove(player.getUUID());
        if (door != null) {
            player.teleportTo((ServerLevel) player.level(), door.pos().x, door.pos().y, door.pos().z + 3, java.util.Set.of(), 0, 0, true);
            DOOR.put(player.getUUID(), (long) player.level().getServer().getTickCount());
        }
        Msg.playSound(player, SoundEvents.PORTAL_TRAVEL, 0.25f, 1.8f);
    }

    // ---------------------------------------------------------------- room 3: the invisible mobs

    private static void mobs(ServerLevel level, long now) {
        Room room = rooms().size() >= 3 ? rooms().get(2) : null;
        MOBS.removeIf(pair -> {
            if (pair[0].isAlive() && now - roomThreeSeen < 30 * 20) {
                return false;
            }
            pair[0].discard();
            pair[1].discard();
            return true;
        });
        if (room == null || now - roomThreeSeen > 20 || now % 20 != 0 || MOBS.size() >= 9) {
            for (Mob[] pair : MOBS) {
                reflect(pair);
            }
            return;
        }
        long zombies = MOBS.stream().filter(p -> p[0] instanceof Zombie).count();
        long slimes = MOBS.stream().filter(p -> p[0] instanceof Slime).count();
        long spiders = MOBS.size() - zombies - slimes;
        var type = zombies < 4 ? EntityTypes.ZOMBIE : spiders < 3 ? EntityTypes.CAVE_SPIDER : slimes < 2 ? EntityTypes.SLIME : null;
        if (type == null) {
            return;
        }
        double x = room.x0() + 1.5 + level.getRandom().nextDouble() * (room.x1() - room.x0() - 4);
        double z = zc - 1.5 - level.getRandom().nextDouble() * 5;
        Mob real = (Mob) type.create(level, EntitySpawnReason.EVENT);
        Mob shown = (Mob) type.create(level, EntitySpawnReason.EVENT);
        if (real == null || shown == null) {
            return;
        }
        for (Mob mob : List.of(real, shown)) {
            if (mob instanceof Zombie zombie) {
                zombie.setBaby(true);
            }
            if (mob instanceof Slime slime) {
                slime.setSize(1, true);
            }
            mob.setNoAi(true);
            mob.setSilent(true);
            mob.setPersistenceRequired();
        }
        real.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2);
        real.setHealth(2);
        real.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.INVISIBILITY, -1, 0, false, false));
        real.addTag(MOB_TAG);
        real.addTag(Zones.FIGHTABLE_TAG);
        shown.setPermanentlyInvulnerable(true);
        shown.addTag(REFLECTION_TAG);
        float yaw = level.getRandom().nextFloat() * 360;
        real.snapTo(x, room.floor() + 1, z, yaw, 0);
        Mob[] pair = {real, shown};
        MOBS.add(pair);
        reflect(pair);
        level.addFreshEntity(real);
        level.addFreshEntity(shown);
    }

    private static void reflect(Mob[] pair) {
        Mob real = pair[0];
        pair[1].snapTo(real.getX(), real.getY(), 2 * (zc + 0.5) - real.getZ(), 180 - real.getYRot(), 0);
        pair[1].setYHeadRot(180 - real.getYRot());
    }

    // ---------------------------------------------------------------- room 6: the dance

    private static BlockState tile(ServerLevel level, ServerPlayer player, Room room) {
        BlockState under = level.getBlockState(new BlockPos(player.getBlockX(), room.floor(), player.getBlockZ()));
        return under.is(Blocks.CONCRETE.blue()) || under.is(Blocks.CONCRETE.red()) || under.is(Blocks.CONCRETE.pink()) || under.is(Blocks.CONCRETE.lime())
                ? under : null;
    }

    private static void dance(ServerPlayer player, ServerLevel level, Room room, long now) {
        Dance dance = DANCES.get(player.getUUID());
        BlockState tile = tile(level, player, room);
        if (dance == null) {
            if (tile != null && player.getZ() < zc && !RiftFlags.has(player, "mv:dance_full") && noticed(player, now)) {
                DANCES.put(player.getUUID(), new Dance());
                player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 40, 5));
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Get ready to dance!").withColor(0xFF55FF)));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Stay on the coloured tiles").withColor(0xAAAAAA)));
            }
            return;
        }
        dance.ticks++;
        if (dance.ticks < 0) {
            return;
        }
        if (dance.ticks == 0) {
            dance.step++;
            dance.tile = tile;
            dance.jumped = false;
            dance.punched = false;
            List<String> calls = DANCE.get(dance.step);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, BEAT - 6, 4));
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(String.join(" ", calls)).withColor(0xFFFF55)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Step " + (dance.step + 1) + "/" + DANCE.size()).withColor(0xAAAAAA)));
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 0.8f + dance.step * 0.08f);
            return;
        }
        if (!player.onGround()) {
            dance.jumped = true;
        }
        if (player.isSwinging()) {
            dance.punched = true;
        }
        if (dance.ticks < BEAT) {
            return;
        }
        List<String> calls = DANCE.get(dance.step);
        String missed = tile == null ? "You stepped off the tiles!" : null;
        for (String call : calls) {
            if (missed != null) {
                break;
            }
            missed = switch (call) {
                case "Move!" -> tile.equals(dance.tile) ? "You didn't move!" : null;
                case "Sneak!" -> player.isShiftKeyDown() ? null : "You weren't sneaking!";
                case "Stand!" -> player.isShiftKeyDown() ? "You were sneaking!" : null;
                case "Jump!" -> dance.jumped ? null : "You didn't jump!";
                case "Don't jump!" -> dance.jumped ? "You jumped!" : null;
                case "Punch!" -> dance.punched ? null : "You didn't punch!";
                default -> null;
            };
        }
        if (missed != null) {
            DANCES.remove(player.getUUID());
            boolean open = dance.step >= DANCE_OPENS;
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(missed).withColor(0xFF5555)));
            player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(open ? "The door is still open." : "The dance starts over.")
                    .withColor(0xAAAAAA)));
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_BASS.value(), 1f, 0.5f);
            return;
        }
        dance.ticks = -6;
        Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        if (dance.step + 1 == DANCE_OPENS && RiftFlags.set(player, "mv:dance")) {
            player.sendSystemMessage(Component.literal("You completed the dance! Keep going to earn the Tiny Dancer talisman!").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (dance.step + 1 == DANCE.size()) {
            DANCES.remove(player.getUUID());
            RiftFlags.set(player, "mv:dance");
            if (RiftFlags.set(player, "mv:dance_full")) {
                give(player, "TINY_DANCER", 1);
            }
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("Amazing!").withColor(0x55FF55)));
            player.sendSystemMessage(Component.literal("You completed the full dance! Amazing! (Tiny Dancer)").withStyle(ChatFormatting.LIGHT_PURPLE));
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1f, 1.2f);
        }
    }

    // ---------------------------------------------------------------- room 7: the climb and the Emmett Pointer

    private static void climb(ServerPlayer player, Room room) {
        Climb climb = CLIMBS.computeIfAbsent(player.getUUID(), id -> new Climb());
        if (player.onGround() && player.getY() < room.floor() + 1.5) {
            climb.usedLever = false;
        }
        for (int cp = 1; cp <= 3; cp++) {
            Rift.Spot spot = Rift.spots().get("mv_cp_" + cp);
            if (spot != null && !climb.reached.contains(cp) && player.position().distanceToSqr(spot.pos()) < 2.0 * 2.0) {
                climb.reached.add(cp);
                player.sendOverlayMessage(Component.literal("Checkpoint " + cp + "! The lever below brings you back here.").withStyle(ChatFormatting.GREEN));
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1f + cp * 0.2f);
            }
        }
        Rift.Spot top = Rift.spots().get("mv_top");
        if (top != null && player.position().distanceToSqr(top.pos()) < 9 && !climb.usedLever && RiftFlags.set(player, "mv:tubulator")) {
            give(player, "MINIATURIZED_TUBULATOR", 1);
            player.sendSystemMessage(Component.literal("All the way up without a checkpoint: the Miniaturized Tubulator is yours!").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** The Emmett Pointer's Lazer: a green beam that lights up the invisible blocks it touches. */
    private static boolean lazer(ServerPlayer player, ItemDefinition.Ability ability) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(32));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 to = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
        DustParticleOptions green = new DustParticleOptions(0x55FF55, 0.9f);
        int steps = (int) (to.distanceTo(eye) * 3);
        for (int i = 2; i <= steps; i++) {
            Vec3 at = eye.lerp(to, i / (double) steps);
            level.sendParticles(player, green, true, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockParticleOption marker = new BlockParticleOption(ParticleTypes.BLOCK_MARKER, Blocks.BARRIER.defaultBlockState());
            BlockPos centre = hit.getBlockPos();
            for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-3, -3, -3), centre.offset(3, 3, 3))) {
                if (level.getBlockState(pos).is(Blocks.BARRIER)) {
                    level.sendParticles(player, marker, true, false, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
                }
            }
        }
        Msg.playSound(player, SoundEvents.BEACON_ACTIVATE, 0.4f, 2f);
        return true;
    }

    // ---------------------------------------------------------------- Dr. Emmett and ttemme .rD

    /** Dr. Emmett outside, by the door. */
    static void emmettOutside(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            boolean supreme = Timecharms.secured(p, Timecharms.SUPREME);
            boolean done = RiftFlags.has(p, "mirrorverse");
            long reached = RiftFlags.count(p, "mv:room:");
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("Enter the Mirrorverse", supreme ? "Walk through the door behind me" : "Needs the Wyldly Supreme Timecharm",
                    0x55FFFF, supreme, "Enter", new ItemStack(Items.GLASS), "", List.of(), reached / (float) ROOMS, reached + "/" + ROOMS + " rooms"));
            cards.add(new CardView.Card("Finish the Mirrorverse", done ? "Done! Dr. Emmett paid you 60,000 Motes." : "60,000 Motes, once", 0xFFAA00,
                    false, "", RiftNpcs.icon(CHARM), "", List.of(), done ? 1 : 0, done ? "Done" : ""));
            return new CardView("RIFT", "Dr. Emmett", "The Mirrorverse", npc, List.of(), cards, "Rift Time stands still in the Mirrorverse.", 1, 36);
        }, (p, card) -> {
            if (card == 0) {
                enter(p);
                return null;
            }
            return "";
        });
    }

    /** Dr. Emmett in a room (1 to 7): what to do, and fast travel back to the rooms you've reached. */
    static void emmettInside(ServerPlayer player, int room, int npc) {
        CardMenu.open(player, p -> {
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("Room " + room, HINTS.get(room - 1), 0x55FFFF, false, "", new ItemStack(Items.GLASS_PANE), "", List.of(), -1, ""));
            if (room == 7) {
                boolean has = Sacks.countAnywhere(p, "EMMETT_POINTER") > 0;
                cards.add(new CardView.Card("Emmett Pointer", "Its lazer lights up the invisible steps", 0x55FF55, !has, has ? "Held" : "Take",
                        RiftNpcs.icon("EMMETT_POINTER"), "", List.of(), -1, ""));
            }
            for (int n = 1; n <= ROOMS; n++) {
                boolean reached = RiftFlags.has(p, "mv:room:" + n);
                cards.add(new CardView.Card("Room " + n, reached ? HINTS.get(n - 1) : "Not reached yet", reached ? 0xFFFFFF : 0x777777,
                        reached && n != room, reached ? "Travel" : "", new ItemStack(reached ? Items.ENDER_PEARL : Items.DYE.gray()), "", List.of(), -1, ""));
            }
            cards.add(new CardView.Card("Leave", "Back to West Village", 0xFF5555, true, "Leave", new ItemStack(Items.OAK_DOOR), "", List.of(), -1, ""));
            return new CardView("RIFT", "Dr. Emmett", "The Mirrorverse", npc, List.of(), cards, "Look in the mirror. It knows the way.", 2, 34);
        }, (p, card) -> {
            int index = card;
            if (room == 7) {
                if (index == 1) {
                    if (Sacks.countAnywhere(p, "EMMETT_POINTER") == 0) {
                        give(p, "EMMETT_POINTER", 1);
                        emmettSays(p, "Here, my pointer. Right-click and the invisible shows itself.");
                    }
                    return "";
                }
                index--;
            }
            if (index >= 1 && index <= ROOMS) {
                if (RiftFlags.has(p, "mv:room:" + index)) {
                    startOf(p, index);
                    return null;
                }
                return "";
            }
            if (index == ROOMS + 1) {
                leave(p);
                return null;
            }
            return "";
        });
    }

    /** The finale's Dr. Emmett: the reward for finishing. */
    static void emmettFinale(ServerPlayer player, int npc) {
        if (RiftFlags.set(player, "mirrorverse")) {
            Motes.give(player, REWARD);
            Notice.of(Notice.Kind.SUCCESS).tag("MIRRORVERSE").panel()
                    .line(Component.literal(" +" + Msg.number(REWARD) + " Motes").withStyle(ChatFormatting.LIGHT_PURPLE))
                    .line(Component.literal(" My reflection sells the mrahcemiT esrevrorriM. Don't stare at him.").withStyle(ChatFormatting.GRAY))
                    .send(player, Component.literal("You finished the Mirrorverse!").withStyle(ChatFormatting.AQUA));
            Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            return;
        }
        emmettInside(player, 8, npc);
    }

    /** ttemme .rD, Dr. Emmett's reflection, and his shop. */
    static void ttemme(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            long motes = Motes.get(p);
            boolean done = RiftFlags.has(p, "mirrorverse");
            List<CardView.Card> cards = new ArrayList<>();
            for (Sale sale : SHOP) {
                List<Chip> price = new ArrayList<>();
                if (sale.motes() > 0) {
                    price.add(Chip.price("motes", sale.motes(), motes));
                }
                price.add(Chip.item(RiftNpcs.icon(sale.costItem()), sale.costCount(), Sacks.countAnywhere(p, sale.costItem())));
                ItemStack item = RiftNpcs.icon(sale.item());
                cards.add(CardView.Card.offer(item, price));
            }
            return new CardView("RIFT", "ttemme .rD", done ? "pohS" : ".tsrif esrevrorriM eht hsiniF", npc, List.of(Chip.balance("motes", motes)), cards,
                    ".uoy ot sredro ni sgniht llas I", 1, 32);
        }, (p, card) -> {
            Sale sale = SHOP.get(card);
            if (sale.needsFinish() && !RiftFlags.has(p, "mirrorverse")) {
                return "Talk to Dr. Emmett first.";
            }
            if (sale.item().equals(CHARM) && (Timecharms.secured(p, CHARM) || Sacks.countAnywhere(p, CHARM) > 0)) {
                return "You already have it.";
            }
            if (Motes.get(p) < sale.motes() || Sacks.countAnywhere(p, sale.costItem()) < sale.costCount()) {
                return "You can't afford that.";
            }
            Motes.take(p, sale.motes());
            Sacks.takeAnywhere(p, sale.costItem(), sale.costCount());
            give(p, sale.item(), sale.count());
            p.sendSystemMessage(Component.literal("Bought " + RiftNpcs.icon(sale.item()).getHoverName().getString() + ".").withStyle(ChatFormatting.GREEN));
            return "";
        });
    }

    private static void emmettSays(ServerPlayer player, String line) {
        player.sendSystemMessage(Component.literal("[NPC] Dr. Emmett: ").withStyle(ChatFormatting.YELLOW).append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    private static void give(ServerPlayer player, String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        ItemStack stack = SkyItems.create(def, count);
        ItemGains.give(player, stack, ItemGains.Source.OTHER);
        if (!stack.isEmpty()) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

}
