package com.skyrpg.skyblock.crimson;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.hub.mixin.DisplayAccess;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.mixin.BlockDisplayAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Master Tao's Dojo on the Crimson Isle: seven tests in the walled 31 x 31 arena (one player at a time), each scored in
 * points and graded S (1,000+), A (800), B (600), C (400), D (200) or F. Your best score in each adds up to your belt:
 * White for taking a test, Yellow 1,000, Green 2,000, Blue 4,000, Brown 6,000, Black 7,000 (each with Combat XP: 80k
 * to 160k). The first S in each test gives 5 SkyBlock XP.
 *
 * <p>Hypixel's arena is a platform over lava; ours is walled, so the tests that throw things into the lava use the wall
 * ring instead (decided):
 * <ul>
 *   <li>Force: knock helmeted zombies into the wall ring (iron 10, gold 20, diamond 30; leather ones -15).</li>
 *   <li>Stamina: walls of fire sweep across the arena with a gap; points for every second, -50 when one catches you.</li>
 *   <li>Mastery: targets light up on the walls for a few seconds; shoot them (green 8, yellow 16, red 32). Two minutes.</li>
 *   <li>Discipline: zombies in leather, iron, gold and diamond helmets; hit each with the matching sword (+25, -10 wrong).</li>
 *   <li>Swiftness: tiles light up green (stand on them: +10 every half second) and red (-10).</li>
 *   <li>Control: keep your eyes on the circling skeleton (+1 every tick you look within 10 degrees).</li>
 *   <li>Tenacity: fire rains down on marked rings (-100 a hit), more and more often; points for every second.</li>
 * </ul>
 */
public final class Dojo {
    static final String TAG = "skyrpg_dojo";
    private static final String ITEM_TAG = "skyrpg_dojo_item";
    public static final List<String> TESTS = List.of("Force", "Stamina", "Mastery", "Discipline", "Swiftness", "Control", "Tenacity");
    static final String[] BELTS = {"WHITE", "YELLOW", "GREEN", "BLUE", "BROWN", "BLACK"};
    static final int[] BELT_POINTS = {0, 1_000, 2_000, 4_000, 6_000, 7_000};
    private static final long[] BELT_XP = {0, 80_000, 100_000, 120_000, 140_000, 160_000};
    private static final int HALF = 14;

    /** Best score per test, the belt earned ("belt": 0 none, 1 White ... 6 Black) and first S's ("s:Force"). */
    static final AttachmentType<Map<String, Integer>> SCORES = AttachmentRegistry.create(SkyRpgSkyblock.id("dojo"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    /** The test going on. */
    private static final class Test {
        final ServerPlayer player;
        final String name;
        final long end;
        final List<Entity> parts = new ArrayList<>();
        final Map<Mob, Integer> mobs = new HashMap<>();
        final ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.YELLOW,
                BossEvent.BossBarOverlay.PROGRESS);
        int score;
        long next;
        long started;
        // Stamina: the sweeping wall (axis, position, gap, direction); Tenacity: marked rings and when they land.
        boolean wallAlongX;
        double wall;
        double wallStep;
        int gap;
        final List<double[]> rings = new ArrayList<>();
        // Mastery: lit targets (block position, points, until).
        final List<long[]> targets = new ArrayList<>();
        // Swiftness: tiles (tile x, tile z) -> +1 green, -1 red, until.
        final Map<Long, int[]> tiles = new HashMap<>();
        final Map<Long, Display.BlockDisplay> tileLooks = new HashMap<>();

        Test(ServerPlayer player, String name, long now, int seconds) {
            this.player = player;
            this.name = name;
            this.started = now;
            this.end = now + seconds * 20L;
        }
    }

    private static Test current;

    private Dojo() {
    }

    static void init() {
        Actions.register("dojo", (player, params) -> KuudraMenu.open(player, KuudraMenu.Mode.DOJO));
        ServerTickEvents.END_SERVER_TICK.register(Dojo::tick);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (current == null || player != current.player || !entity.entityTags().contains(TAG) || !(entity instanceof Mob mob)) {
                return InteractionResult.PASS;
            }
            hit(current, mob);
            return InteractionResult.SUCCESS;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity.entityTags().contains(TAG)) {
                return false;
            }
            return current == null || entity != current.player;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (current != null && current.player == handler.player) {
                end(current, true);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (current != null) {
                end(current, true);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && (current == null || !current.parts.contains(entity) && !current.mobs.containsKey(entity))) {
                entity.discard();
            }
        });
    }

    static Vec3 center() {
        CrimsonIsle.Spot spot = CrimsonIsle.spot("Dojo");
        return spot == null ? null : spot.pos();
    }

    static int best(ServerPlayer player, String test) {
        return player.getAttachedOrElse(SCORES, Map.of()).getOrDefault(test, 0);
    }

    static int total(ServerPlayer player) {
        int total = 0;
        for (String test : TESTS) {
            total += best(player, test);
        }
        return total;
    }

    static int belt(ServerPlayer player) {
        return player.getAttachedOrElse(SCORES, Map.of()).getOrDefault("belt", 0);
    }

    static String grade(int score) {
        return score >= 1_000 ? "S" : score >= 800 ? "A" : score >= 600 ? "B" : score >= 400 ? "C" : score >= 200 ? "D" : "F";
    }

    static String beltName(int belt) {
        return belt <= 0 ? "None" : BELTS[belt - 1].charAt(0) + BELTS[belt - 1].substring(1).toLowerCase(java.util.Locale.ROOT) + " Belt";
    }

    // ---------------------------------------------------------------- starting and ending

    static void start(ServerPlayer player, String name) {
        Vec3 c = center();
        if (c == null || !player.level().dimension().equals(CrimsonIsle.LEVEL)) {
            return;
        }
        if (current != null) {
            player.sendSystemMessage(Component.literal("Master Tao: Patience. " + current.player.getScoreboardName() + " is being tested.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Test test = new Test(player, name, now, name.equals("Mastery") ? 120 : 60);
        current = test;
        test.next = now + 40;
        player.teleportTo(level, c.x, c.y, c.z, java.util.Set.of(), 180, 0, true);
        switch (name) {
            case "Mastery" -> {
                ItemStack bow = tagged(new ItemStack(Items.BOW));
                bow.enchant(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.INFINITY), 1);
                give(player, bow);
                give(player, tagged(new ItemStack(Items.ARROW)));
            }
            case "Discipline" -> {
                for (Item sword : new Item[] {Items.WOODEN_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD}) {
                    give(player, tagged(new ItemStack(sword)));
                }
            }
            default -> {
            }
        }
        player.sendSystemMessage(Component.literal("Master Tao: " + intro(name)).withStyle(ChatFormatting.YELLOW));
        Msg.playSound(player, SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1f);
    }

    private static String intro(String name) {
        return switch (name) {
            case "Force" -> "Knock the zombies into the walls. Iron 10, gold 20, diamond 30. Leave the leather ones alone!";
            case "Stamina" -> "Walls of fire are coming. Stand in their gap!";
            case "Mastery" -> "Shoot the targets on the walls before they go dark. Green 8, yellow 16, red 32.";
            case "Discipline" -> "Hit each zombie with the sword that matches its helmet.";
            case "Swiftness" -> "Stand on the green tiles. Never on the red.";
            case "Control" -> "Keep your eyes on the skeleton. Never look away.";
            default -> "Fire falls from the sky. Stay out of the rings!";
        };
    }

    private static ItemStack tagged(ItemStack stack) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean(ITEM_TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        return stack;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.sendSystemMessage(Component.literal("Make room in your inventory for the Dojo's gear!").withStyle(ChatFormatting.RED));
        }
    }

    /** Takes back the Dojo's bow, arrow and swords. */
    private static void takeBack(ServerPlayer player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null && data.copyTag().getBooleanOr(ITEM_TAG, false)) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void end(Test test, boolean quit) {
        current = null;
        test.bar.removeAllPlayers();
        test.parts.forEach(Entity::discard);
        test.mobs.keySet().forEach(Entity::discard);
        test.tileLooks.values().forEach(Entity::discard);
        ServerPlayer player = test.player;
        takeBack(player);
        if (player.level() instanceof ServerLevel level) {
            for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, arena(), a -> a.getOwner() == player)) {
                arrow.discard();
            }
        }
        if (quit) {
            player.sendSystemMessage(Component.literal("Your " + test.name + " test was cut short.").withStyle(ChatFormatting.GRAY));
            return;
        }
        int score = Math.max(0, test.score);
        Map<String, Integer> all = new HashMap<>(player.getAttachedOrElse(SCORES, Map.of()));
        int before = all.getOrDefault(test.name, 0);
        all.put(test.name, Math.max(before, score));
        Notice.Panel panel = Notice.of(Notice.Kind.COMBAT).tag("DOJO").panel()
                .line(Component.literal(" Score: " + Msg.number(score) + "  ·  Grade " + grade(score)
                        + (score > before ? "  ·  new best!" : "  ·  best " + Msg.number(before))).withStyle(ChatFormatting.GRAY));
        if (score >= 1_000 && all.getOrDefault("s:" + test.name, 0) == 0) {
            all.put("s:" + test.name, 1);
            SkyBlockXp.give(player, 5, "Dojo: " + test.name + " S");
            panel.line(Component.literal(" +5 SkyBlock XP (first S)").withStyle(ChatFormatting.AQUA));
        }
        player.setAttached(SCORES, Map.copyOf(all));
        // Belts: White for taking a test, then by the total of your bests.
        int total = total(player);
        int belt = all.getOrDefault("belt", 0);
        int earned = 1;
        for (int i = 1; i < BELTS.length; i++) {
            if (total >= BELT_POINTS[i]) {
                earned = i + 1;
            }
        }
        for (int b = belt + 1; b <= earned; b++) {
            ItemDefinition def = ItemRegistry.skyblock("DOJO_" + BELTS[b - 1] + "_BELT");
            if (def != null) {
                ItemStack item = SkyItems.create(def, 1);
                if (!player.getInventory().add(item)) {
                    player.drop(item, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
            if (BELT_XP[b - 1] > 0) {
                SkillLevels.giveXp(player, SkillLevels.COMBAT, BELT_XP[b - 1]);
            }
            panel.line(Component.literal(" " + beltName(b) + "!" + (BELT_XP[b - 1] > 0 ? "  +" + Msg.number(BELT_XP[b - 1]) + " Combat XP" : ""))
                    .withStyle(ChatFormatting.GOLD));
        }
        if (earned > belt) {
            all.put("belt", earned);
            player.setAttached(SCORES, Map.copyOf(all));
        }
        panel.send(player, Component.literal(test.name + " test complete!").withStyle(ChatFormatting.YELLOW));
        Msg.playSound(player, score >= 1_000 ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, 0.8f, 1f);
    }

    static AABB arena() {
        Vec3 c = center();
        return c == null ? new AABB(0, 0, 0, 0, 0, 0) : new AABB(c.x - HALF - 1, c.y - 1, c.z - HALF - 1, c.x + HALF + 1, c.y + 8, c.z + HALF + 1);
    }

    // ---------------------------------------------------------------- every tick

    private static void tick(MinecraftServer server) {
        Test test = current;
        if (test == null) {
            return;
        }
        ServerPlayer player = test.player;
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        Vec3 c = center();
        if (c == null || !player.isAlive() || !level.dimension().equals(CrimsonIsle.LEVEL) || !arena().contains(player.position())) {
            end(test, true);
            return;
        }
        if (now >= test.end) {
            end(test, false);
            return;
        }
        test.bar.addPlayer(player);
        test.bar.setName(Component.literal(test.name + ": " + Msg.number(Math.max(0, test.score)) + " points  ·  " + grade(test.score)
                + "  ·  " + (test.end - now + 19) / 20 + "s").withStyle(ChatFormatting.YELLOW));
        test.bar.setProgress(Math.max(0, Math.min(1, (test.end - now) / (float) (test.end - test.started))));
        if (now < test.started + 40) {
            return;
        }
        RandomSource random = level.getRandom();
        switch (test.name) {
            case "Force" -> force(test, level, c, now, random);
            case "Stamina" -> stamina(test, level, c, now, random);
            case "Mastery" -> mastery(test, level, c, now, random);
            case "Discipline" -> discipline(test, level, c, now, random);
            case "Swiftness" -> swiftness(test, level, c, now, random);
            case "Control" -> control(test, level, c, now);
            default -> tenacity(test, level, c, now, random);
        }
    }

    private static Mob zombie(Test test, ServerLevel level, Vec3 at, Item helmet, boolean baby, int points) {
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
        if (zombie == null) {
            return null;
        }
        zombie.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
        zombie.setBaby(baby);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(helmet));
        zombie.setDropChance(EquipmentSlot.HEAD, 0);
        zombie.setPersistenceRequired();
        zombie.addTag(TAG);
        zombie.addTag(com.skyrpg.core.world.MobTags.NO_SUNBURN);
        zombie.addTag(com.skyrpg.skyblock.zone.Zones.FIGHTABLE_TAG);
        level.addFreshEntity(zombie);
        zombie.setTarget(test.player);
        test.mobs.put(zombie, points);
        return zombie;
    }

    /** A swing at a test's mob: Force knocks it back; Discipline checks the sword. */
    private static void hit(Test test, Mob mob) {
        Integer points = test.mobs.get(mob);
        if (points == null) {
            return;
        }
        ServerPlayer player = test.player;
        if (test.name.equals("Force")) {
            Vec3 push = mob.position().subtract(player.position()).multiply(1, 0, 1).normalize().scale(1.6);
            mob.push(push.x, 0.45, push.z);
            Msg.playSound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
        } else if (test.name.equals("Discipline")) {
            Item held = player.getMainHandItem().getItem();
            Item needed = switch (points) {
                case 1 -> Items.WOODEN_SWORD;
                case 2 -> Items.IRON_SWORD;
                case 3 -> Items.GOLDEN_SWORD;
                default -> Items.DIAMOND_SWORD;
            };
            if (held == needed) {
                test.score += 25;
                mob.discard();
                test.mobs.remove(mob);
                Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
            } else {
                test.score -= 10;
                Msg.playSound(player, SoundEvents.VILLAGER_NO, 1f, 1f);
            }
        }
    }

    private static void force(Test test, ServerLevel level, Vec3 c, long now, RandomSource random) {
        test.mobs.entrySet().removeIf(e -> {
            Mob mob = e.getKey();
            if (!mob.isAlive()) {
                return true;
            }
            if (Math.max(Math.abs(mob.getX() - c.x), Math.abs(mob.getZ() - c.z)) >= HALF - 1.5) {
                test.score += e.getValue();
                level.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 1, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
                Msg.playSound(test.player, e.getValue() > 0 ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO, 1f, 1.2f);
                mob.discard();
                return true;
            }
            return false;
        });
        if (test.mobs.size() < 5 && now % 10 == 0) {
            int roll = random.nextInt(10);
            Item helmet = roll < 4 ? Items.IRON_HELMET : roll < 7 ? Items.GOLDEN_HELMET : roll < 8 ? Items.DIAMOND_HELMET : Items.LEATHER_HELMET;
            int points = helmet == Items.IRON_HELMET ? 10 : helmet == Items.GOLDEN_HELMET ? 20 : helmet == Items.DIAMOND_HELMET ? 30 : -15;
            zombie(test, level, c.add(random.nextInt(9) - 4, 0, random.nextInt(9) - 4), helmet, helmet == Items.DIAMOND_HELMET, points);
        }
    }

    private static void stamina(Test test, ServerLevel level, Vec3 c, long now, RandomSource random) {
        if (now % 20 == 0) {
            test.score += 17;
        }
        if (test.wallStep == 0 && now >= test.next) {
            test.wallAlongX = random.nextBoolean();
            boolean forward = random.nextBoolean();
            test.wall = forward ? -HALF : HALF;
            test.wallStep = forward ? 0.5 : -0.5;
            test.gap = random.nextInt(HALF * 2 - 6) - (HALF - 3);
        }
        if (test.wallStep != 0) {
            test.wall += test.wallStep;
            for (int i = -HALF; i <= HALF; i++) {
                if (Math.abs(i - test.gap) <= 1) {
                    continue;
                }
                double x = test.wallAlongX ? c.x + i : c.x + test.wall;
                double z = test.wallAlongX ? c.z + test.wall : c.z + i;
                level.sendParticles(ParticleTypes.FLAME, x, c.y + 0.5, z, 1, 0.1, 0.4, 0.1, 0);
            }
            ServerPlayer player = test.player;
            double across = test.wallAlongX ? player.getZ() - c.z : player.getX() - c.x;
            double along = test.wallAlongX ? player.getX() - c.x : player.getZ() - c.z;
            if (Math.abs(across - test.wall) < 0.6 && Math.abs(along - test.gap) > 1.5) {
                test.score -= 50;
                player.push(test.wallAlongX ? 0 : Math.signum(test.wallStep) * 0.8, 0.4, test.wallAlongX ? Math.signum(test.wallStep) * 0.8 : 0);
                player.syncVelocity = true;
                Msg.playSound(player, SoundEvents.PLAYER_HURT_ON_FIRE, 1f, 1f);
                test.wall += test.wallStep * 3;
            }
            if (Math.abs(test.wall) > HALF) {
                test.wallStep = 0;
                test.next = now + 20;
            }
        }
    }

    private static void mastery(Test test, ServerLevel level, Vec3 c, long now, RandomSource random) {
        // A new target every 1.5 seconds, lit for 4: a wool block on a wall.
        if (now >= test.next) {
            test.next = now + 30;
            int side = random.nextInt(4);
            int along = random.nextInt(HALF * 2 - 3) - (HALF - 2);
            int y = 1 + random.nextInt(3);
            int x = side == 0 ? HALF + 1 : side == 1 ? -HALF - 1 : along;
            int z = side == 2 ? HALF + 1 : side == 3 ? -HALF - 1 : along;
            BlockPos pos = BlockPos.containing(c.x + x, c.y + y - 1, c.z + z);
            int roll = random.nextInt(10);
            int points = roll < 5 ? 8 : roll < 8 ? 16 : 32;
            test.targets.add(new long[] {pos.asLong(), points, now + 80});
            Display.BlockDisplay look = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
            look.snapTo(pos.getX() - 0.01, pos.getY() - 0.01, pos.getZ() - 0.01, 0, 0);
            ((BlockDisplayAccess) look).skyrpg$setBlockState(Blocks.CONCRETE.pick(points == 8 ? net.minecraft.world.item.DyeColor.LIME
                    : points == 16 ? net.minecraft.world.item.DyeColor.YELLOW : net.minecraft.world.item.DyeColor.RED).defaultBlockState());
            ((DisplayAccess) look).skyrpg$setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.02f, 1.02f, 1.02f),
                    new Quaternionf()));
            look.addTag(TAG);
            level.addFreshEntity(look);
            test.parts.add(look);
            test.tileLooks.put(pos.asLong(), look);
        }
        // Arrows that stuck in a lit target.
        for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, arena().inflate(2), a -> a.getOwner() == test.player)) {
            for (long[] target : List.copyOf(test.targets)) {
                BlockPos pos = BlockPos.of(target[0]);
                if (arrow.getBoundingBox().inflate(0.3).intersects(new AABB(pos))) {
                    test.score += (int) target[1];
                    Msg.playSound(test.player, SoundEvents.ARROW_HIT_PLAYER, 1f, 1.2f);
                    remove(test, target);
                    arrow.discard();
                    break;
                }
            }
            if (arrow.isRemoved() || arrow.tickCount > 60) {
                arrow.discard();
            }
        }
        for (long[] target : List.copyOf(test.targets)) {
            if (now >= target[2]) {
                remove(test, target);
            }
        }
    }

    private static void remove(Test test, long[] target) {
        test.targets.remove(target);
        Display.BlockDisplay look = test.tileLooks.remove(target[0]);
        if (look != null) {
            look.discard();
            test.parts.remove(look);
        }
    }

    private static void discipline(Test test, ServerLevel level, Vec3 c, long now, RandomSource random) {
        test.mobs.keySet().removeIf(mob -> !mob.isAlive());
        if (test.mobs.size() < 4 && now % 15 == 0) {
            int kind = 1 + random.nextInt(4);
            Item helmet = kind == 1 ? Items.LEATHER_HELMET : kind == 2 ? Items.IRON_HELMET : kind == 3 ? Items.GOLDEN_HELMET : Items.DIAMOND_HELMET;
            Mob mob = zombie(test, level, c.add(random.nextInt(17) - 8, 0, random.nextInt(17) - 8), helmet, false, kind);
            if (mob != null) {
                mob.setNoAi(true);
            }
        }
    }

    private static void swiftness(Test test, ServerLevel level, Vec3 c, long now, RandomSource random) {
        // Every 3 seconds: two green tiles and two red ones (6 x 6 each, of the 5 x 5 grid).
        if (now >= test.next) {
            test.next = now + 60;
            test.tileLooks.values().forEach(Entity::discard);
            test.parts.removeAll(test.tileLooks.values());
            test.tileLooks.clear();
            test.tiles.clear();
            List<Integer> cells = new ArrayList<>();
            for (int i = 0; i < 25; i++) {
                cells.add(i);
            }
            java.util.Collections.shuffle(cells, new java.util.Random(random.nextLong()));
            for (int k = 0; k < 4; k++) {
                int cell = cells.get(k);
                int tx = cell % 5;
                int tz = cell / 5;
                int kind = k < 2 ? 1 : -1;
                long key = (long) tx * 31 + tz;
                test.tiles.put(key, new int[] {tx, tz, kind});
                Display.BlockDisplay look = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
                look.snapTo(c.x - 15 + tx * 6, c.y + 0.001, c.z - 15 + tz * 6, 0, 0);
                ((BlockDisplayAccess) look).skyrpg$setBlockState(Blocks.WOOL.pick(kind > 0 ? net.minecraft.world.item.DyeColor.LIME
                        : net.minecraft.world.item.DyeColor.RED).defaultBlockState());
                ((DisplayAccess) look).skyrpg$setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(6, 0.06f, 6),
                        new Quaternionf()));
                look.addTag(TAG);
                level.addFreshEntity(look);
                test.parts.add(look);
                test.tileLooks.put(key, look);
            }
        }
        if (now % 10 == 0) {
            ServerPlayer player = test.player;
            int tx = (int) Math.floor((player.getX() - (c.x - 15)) / 6);
            int tz = (int) Math.floor((player.getZ() - (c.z - 15)) / 6);
            int[] tile = test.tiles.get((long) tx * 31 + tz);
            if (tile != null) {
                test.score += tile[2] * 10;
                Msg.playSound(player, tile[2] > 0 ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 1.5f);
            }
        }
    }

    private static void control(Test test, ServerLevel level, Vec3 c, long now) {
        Mob skeleton = test.mobs.keySet().stream().findFirst().orElse(null);
        if (skeleton == null) {
            skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.EVENT);
            if (skeleton == null) {
                return;
            }
            skeleton.setNoAi(true);
            skeleton.setNoGravity(true);
            skeleton.setPersistenceRequired();
            skeleton.addTag(TAG);
            skeleton.addTag(com.skyrpg.core.world.MobTags.NO_SUNBURN);
            level.addFreshEntity(skeleton);
            test.mobs.put(skeleton, 0);
        }
        double t = (now - test.started) / 20.0;
        double angle = t * (0.6 + 0.4 * Math.sin(t * 0.7));
        double radius = 9 + 3 * Math.sin(t * 0.9);
        skeleton.teleportTo(c.x + Math.cos(angle) * radius, c.y + 1.5 + Math.sin(t * 1.3), c.z + Math.sin(angle) * radius);
        ServerPlayer player = test.player;
        Vec3 to = skeleton.getEyePosition().subtract(player.getEyePosition()).normalize();
        if (player.getLookAngle().dot(to) > Math.cos(Math.toRadians(10))) {
            test.score += 1;
            if (now % 10 == 0) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, skeleton.getX(), skeleton.getY() + 2.2, skeleton.getZ(), 2, 0.2, 0.2, 0.2, 0);
            }
        }
    }

    private static void tenacity(Test test, ServerLevel level, Vec3 c, long now, RandomSource random) {
        if (now % 20 == 0) {
            test.score += 17;
        }
        long elapsed = now - test.started;
        if (now >= test.next) {
            // Faster and faster: every 1.5 seconds down to 0.5, one ring on you and one or two elsewhere.
            test.next = now + Math.max(10, 30 - elapsed / 60);
            ServerPlayer player = test.player;
            test.rings.add(new double[] {player.getX(), player.getZ(), now + 25});
            for (int i = 0; i < 1 + (elapsed > 600 ? 1 : 0); i++) {
                test.rings.add(new double[] {c.x + random.nextInt(25) - 12, c.z + random.nextInt(25) - 12, now + 25});
            }
        }
        for (double[] ring : List.copyOf(test.rings)) {
            if (now % 3 == 0) {
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6;
                    level.sendParticles(ParticleTypes.FLAME, ring[0] + Math.cos(a) * 2, c.y + 0.1, ring[1] + Math.sin(a) * 2, 1, 0, 0, 0, 0);
                }
            }
            if (now >= ring[2]) {
                test.rings.remove(ring);
                level.sendParticles(ParticleTypes.EXPLOSION, ring[0], c.y + 0.5, ring[1], 3, 0.6, 0.2, 0.6, 0);
                level.playSound(null, BlockPos.containing(ring[0], c.y, ring[1]), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.8f, 0.8f);
                ServerPlayer player = test.player;
                if (Math.hypot(player.getX() - ring[0], player.getZ() - ring[1]) <= 2.2) {
                    test.score -= 100;
                    player.push(0, 0.5, 0);
                    player.syncVelocity = true;
                    Msg.playSound(player, SoundEvents.PLAYER_HURT_ON_FIRE, 1f, 1f);
                }
            }
        }
    }

    static boolean busy() {
        return current != null;
    }

}
