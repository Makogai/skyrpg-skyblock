package com.skyrpg.skyblock.rift;

import com.mojang.serialization.Codec;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Cowboy Nick and the Half-Eaten Cave (Hypixel's), in the Village Plaza. Nick lends you his Horsezooka: its Shoot
 * launches a horse that clears hay out of the way; the cave is stuffed with hay that grows back (and squashes you, for 60
 * seconds of Rift Time, if you're standing where it grows). Clear 3,000 bales and Nick trusts you with one of his
 * rabbits: it follows you, and nibbles the carrot patches at the back of the cave into Half-Eaten Carrots (Reed wants 128;
 * 80 make a Nearly-Whole Carrot). Bring Nick seven Nearly-Whole Carrots, one for each of his bunnies.
 */
final class CowboyNick {
    static final String RABBIT_TAG = "skyrpg_nick_rabbit";
    private static final int HAY_NEEDED = 3_000;
    private static final int HAY_REGROW = 30 * 20;
    private static final int CARROT_REGROW = 60 * 20;
    private static final double SQUASH_TIME = 60;
    /** Around the Half-Eaten Cave (rift_village_plaza.py: the cave at x 35..51, z -66..-49, floor y 68). */
    private static final AABB CAVE = new AABB(30, 66, -70, 56, 76, -45);
    private static final List<String> NAMES = List.of("Aaron", "Biscuit", "Clover", "Dusty", "Hopper", "Juniper", "Marshmallow", "Nibbles", "Pickles",
            "Thumper", "Waffles", "Zorro", "Appollo", "Bubles", "Dutchess", "Lilly");

    /** "hay": bales cleared; "rabbit": 1 while yours is alive. */
    static final AttachmentType<Map<String, Integer>> DATA = AttachmentRegistry.create(SkyRpgSkyblock.id("rift_cowboy_nick"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    private record Horse(Mob horse, UUID owner, Vec3 step, long until) {
    }

    private static final List<Horse> HORSES = new ArrayList<>();
    private static final Map<BlockPos, Long> HAY = new HashMap<>();
    private static final Map<BlockPos, Long> CARROTS = new HashMap<>();
    private static final Map<UUID, Mob> RABBITS = new HashMap<>();
    private static final Map<UUID, Long> NIBBLED = new HashMap<>();

    private CowboyNick() {
    }

    static void init() {
        Abilities.register("shoot", (player, ability) -> shoot(player));
        Identifier first = SkyRpgSkyblock.id("rift_first");
        AttackBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(first, (player, level, hand, pos, direction) -> {
            if (!Rift.isRift(level) || !CAVE.contains(Vec3.atCenterOf(pos)) || !level.getBlockState(pos).is(Blocks.CARROTS)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer server) {
                nibble(server, (ServerLevel) level, pos, "You pull a Half-Eaten Carrot out of the patch.");
            }
            return InteractionResult.SUCCESS;
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if ((entity.entityTags().contains(RABBIT_TAG) || entity.entityTags().contains("skyrpg_horsezooka"))
                    && !RABBITS.containsValue(entity) && HORSES.stream().noneMatch(h -> h.horse() == entity)) {
                entity.discard();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(CowboyNick::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerLevel level = Rift.level(server);
            if (level != null) {
                HAY.keySet().forEach(pos -> level.setBlock(pos, Blocks.HAY_BLOCK.defaultBlockState(), 3));
                CARROTS.keySet().forEach(pos -> level.setBlock(pos, Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7), 3));
            }
            HAY.clear();
            CARROTS.clear();
        });
    }

    private static int data(ServerPlayer player, String key) {
        return player.getAttachedOrElse(DATA, Map.of()).getOrDefault(key, 0);
    }

    private static void set(ServerPlayer player, String key, int value) {
        Map<String, Integer> map = new HashMap<>(player.getAttachedOrElse(DATA, Map.of()));
        map.put(key, value);
        player.setAttached(DATA, Map.copyOf(map));
    }

    // ---------------------------------------------------------------- the Horsezooka and the hay

    private static boolean shoot(ServerPlayer player) {
        if (!Rift.isRift(player.level())) {
            player.sendOverlayMessage(Component.literal("The Horsezooka only works in the Rift.").withStyle(ChatFormatting.RED));
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        Mob horse = EntityTypes.HORSE.create(level, EntitySpawnReason.EVENT);
        if (horse == null) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        Vec3 at = player.getEyePosition().add(look.scale(1.5)).subtract(0, 0.8, 0);
        horse.snapTo(at.x, at.y, at.z, player.getYRot(), 0);
        horse.setNoAi(true);
        horse.setNoGravity(true);
        horse.setPermanentlyInvulnerable(true);
        horse.setSilent(true);
        horse.addTag("skyrpg_horsezooka");
        HORSES.add(new Horse(horse, player.getUUID(), look.scale(0.9), level.getServer().getTickCount() + 30));
        level.addFreshEntity(horse);
        level.playSound(null, player.blockPosition(), SoundEvents.HORSE_GALLOP, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1.3f);
        return true;
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = Rift.level(server);
        if (level == null) {
            return;
        }
        long now = server.getTickCount();
        for (Iterator<Horse> it = HORSES.iterator(); it.hasNext();) {
            Horse h = it.next();
            if (now > h.until() || !h.horse().isAlive()) {
                level.sendParticles(ParticleTypes.POOF, h.horse().getX(), h.horse().getY() + 1, h.horse().getZ(), 12, 0.5, 0.5, 0.5, 0.02);
                h.horse().discard();
                it.remove();
                continue;
            }
            Vec3 next = h.horse().position().add(h.step());
            h.horse().snapTo(next.x, next.y, next.z, h.horse().getYRot(), 0);
            ServerPlayer owner = server.getPlayerList().getPlayer(h.owner());
            int cleared = 0;
            BlockPos centre = BlockPos.containing(next.add(0, 0.8, 0));
            for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-2, -1, -2), centre.offset(2, 2, 2))) {
                if (pos.distToCenterSqr(next.add(0, 0.8, 0)) <= 2.3 * 2.3 && CAVE.contains(Vec3.atCenterOf(pos)) && level.getBlockState(pos).is(Blocks.HAY_BLOCK)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    HAY.put(pos.immutable(), now + HAY_REGROW + level.getRandom().nextInt(100));
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.HAY_BLOCK.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5,
                            pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
                    cleared++;
                }
            }
            if (cleared > 0 && owner != null) {
                int hay = data(owner, "hay") + cleared;
                set(owner, "hay", hay);
                if (hay - cleared < HAY_NEEDED && hay >= HAY_NEEDED) {
                    owner.sendSystemMessage(Component.literal("3,000 hay bales! Cowboy Nick will want to hear about this.").withStyle(ChatFormatting.GOLD));
                } else if (hay < HAY_NEEDED && now % 4 == 0) {
                    owner.sendOverlayMessage(Component.literal("Hay cleared: " + Msg.number(hay) + "/" + Msg.number(HAY_NEEDED)).withStyle(ChatFormatting.YELLOW));
                }
                level.playSound(null, centre, SoundEvents.GRASS_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.8f);
            }
        }
        if (now % 10 == 0) {
            regrow(level, now);
        }
        rabbits(server, level, now);
    }

    private static List<BlockPos> patches;

    /** The carrot patches (each marked by a light block over it), set back up when they're missing. */
    private static void patches(ServerLevel level) {
        if (patches == null) {
            if (!level.isLoaded(BlockPos.containing(CAVE.getCenter()))) {
                return;
            }
            List<BlockPos> found = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed((int) CAVE.minX, 69, (int) CAVE.minZ, (int) CAVE.maxX, 72, (int) CAVE.maxZ)) {
                if (level.getBlockState(pos).is(Blocks.LIGHT)) {
                    found.add(pos.below().immutable());
                }
            }
            patches = List.copyOf(found);
        }
        for (BlockPos pos : patches) {
            if (!CARROTS.containsKey(pos) && !level.getBlockState(pos).is(Blocks.CARROTS)) {
                level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7), 3);
                level.setBlock(pos, Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
            }
        }
    }

    private static void regrow(ServerLevel level, long now) {
        if (now % 100 == 0 && !level.getPlayers(p -> CAVE.inflate(32).contains(p.position())).isEmpty()) {
            patches(level);
        }
        HAY.entrySet().removeIf(e -> {
            if (e.getValue() > now || !level.isLoaded(e.getKey())) {
                return false;
            }
            BlockPos pos = e.getKey();
            AABB box = new AABB(pos);
            for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, box)) {
                Rift.takeTime(player, SQUASH_TIME);
                Rift.Spot mouth = Rift.spots().get("half_eaten_cave");
                if (mouth != null) {
                    player.teleportTo(level, mouth.pos().x, mouth.pos().y, mouth.pos().z, Set.of(), mouth.yaw(), 0, true);
                }
                player.sendSystemMessage(Component.literal("SPLOOSH! You've been squashed by hay! -" + (int) SQUASH_TIME + "s ф Rift Time")
                        .withStyle(ChatFormatting.RED));
            }
            for (Map.Entry<UUID, Mob> rabbit : List.copyOf(RABBITS.entrySet())) {
                if (rabbit.getValue().getBoundingBox().intersects(box)) {
                    ServerPlayer owner = level.getServer().getPlayerList().getPlayer(rabbit.getKey());
                    rabbit.getValue().discard();
                    RABBITS.remove(rabbit.getKey());
                    if (owner != null) {
                        set(owner, "rabbit", 0);
                        owner.sendSystemMessage(Component.literal("SPLASH! Your rabbit has been squashed! Cowboy Nick has more.").withStyle(ChatFormatting.RED));
                    }
                }
            }
            level.setBlock(pos, Blocks.HAY_BLOCK.defaultBlockState(), 3);
            return true;
        });
        CARROTS.entrySet().removeIf(e -> {
            if (e.getValue() > now || !level.isLoaded(e.getKey())) {
                return false;
            }
            if (level.getBlockState(e.getKey()).is(Blocks.CARROTS)) {
                level.setBlock(e.getKey(), Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, 7), 3);
            }
            return true;
        });
    }

    // ---------------------------------------------------------------- the rabbit

    private static void rabbits(MinecraftServer server, ServerLevel level, long now) {
        for (ServerPlayer player : level.players()) {
            if (data(player, "rabbit") == 0) {
                continue;
            }
            Mob rabbit = RABBITS.get(player.getUUID());
            if (rabbit == null || !rabbit.isAlive() || rabbit.level() != level) {
                if (now % 20 != 0) {
                    continue;
                }
                rabbit = EntityTypes.RABBIT.create(level, EntitySpawnReason.EVENT);
                if (rabbit == null) {
                    continue;
                }
                String name = NAMES.get(Math.floorMod(player.getUUID().hashCode(), NAMES.size()));
                rabbit.setCustomName(Component.literal(name).withStyle(ChatFormatting.GOLD));
                rabbit.setCustomNameVisible(true);
                rabbit.setPermanentlyInvulnerable(true);
                rabbit.setPersistenceRequired();
                rabbit.addTag(RABBIT_TAG);
                rabbit.snapTo(player.getX(), player.getY(), player.getZ(), 0, 0);
                RABBITS.put(player.getUUID(), rabbit);
                level.addFreshEntity(rabbit);
            }
            double distance = rabbit.distanceTo(player);
            if (distance > 24) {
                rabbit.snapTo(player.getX(), player.getY(), player.getZ(), 0, 0);
            } else if (distance > 2.5 && now % 10 == 0) {
                rabbit.getNavigation().moveTo(player, 1.6);
            }
            if (now % 20 == 0 && CAVE.contains(rabbit.position()) && now - NIBBLED.getOrDefault(player.getUUID(), 0L) >= 40) {
                BlockPos at = rabbit.blockPosition();
                for (BlockPos pos : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.CARROTS) && state.getValue(CropBlock.AGE) == 7) {
                        NIBBLED.put(player.getUUID(), now);
                        nibble(player, level, pos.immutable(), rabbit.getCustomName().getString() + " nibbles a carrot.");
                        break;
                    }
                }
            }
        }
        RABBITS.entrySet().removeIf(e -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(e.getKey());
            if (owner == null || owner.level() != level) {
                e.getValue().discard();
                return true;
            }
            return false;
        });
    }

    private static void nibble(ServerPlayer player, ServerLevel level, BlockPos pos, String what) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.CARROTS) || state.getValue(CropBlock.AGE) < 7) {
            player.sendOverlayMessage(Component.literal("This patch needs time to grow back.").withStyle(ChatFormatting.GRAY));
            return;
        }
        level.setBlock(pos, state.setValue(CropBlock.AGE, 0), 3);
        CARROTS.put(pos.immutable(), (long) level.getServer().getTickCount() + CARROT_REGROW);
        ItemDefinition def = ItemRegistry.skyblock("HALF_EATEN_CARROT");
        if (def != null) {
            ItemStack stack = SkyItems.create(def, 1);
            ItemGains.give(player, stack, ItemGains.Source.FARMING);
            if (!stack.isEmpty()) {
                player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0);
        Msg.playSound(player, SoundEvents.GENERIC_EAT.value(), 0.6f, 1.6f);
        player.sendOverlayMessage(Component.literal(what + " +1 Half-Eaten Carrot").withStyle(ChatFormatting.GOLD));
    }

    // ---------------------------------------------------------------- Nick

    static void open(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            boolean zooka = Sacks.countAnywhere(p, "HORSEZOOKA") > 0;
            int hay = data(p, "hay");
            boolean rabbit = data(p, "rabbit") > 0;
            boolean done = RiftFlags.has(p, "nick:done");
            long whole = Sacks.countAnywhere(p, "NEARLY_WHOLE_CARROT");
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("Horsezooka", zooka ? "It launches a horse. Clears hay right out." : "Borrow it, partner", 0xAA55FF, !zooka,
                    zooka ? "Held" : "Borrow", RiftNpcs.icon("HORSEZOOKA"), "", List.of(), -1, ""));
            cards.add(new CardView.Card("Clear the hay", hay >= HAY_NEEDED ? "Mighty fine work." : "The Half-Eaten Cave is full of it", 0xFFDD55, false, "",
                    new ItemStack(net.minecraft.world.item.Items.HAY_BLOCK), "", List.of(), Math.min(1f, hay / (float) HAY_NEEDED),
                    Msg.number(Math.min(hay, HAY_NEEDED)) + "/" + Msg.number(HAY_NEEDED)));
            cards.add(new CardView.Card("A rabbit of your own", rabbit ? "Hungry as ever. Walk it to the carrots in the cave."
                    : hay >= HAY_NEEDED ? "It'll follow you and eat the cave's carrots" : "Clear the hay first", 0xFFAA00, hay >= HAY_NEEDED && !rabbit,
                    rabbit ? "Yours" : "Adopt", new ItemStack(net.minecraft.world.item.Items.RABBIT_FOOT), "", List.of(), -1, ""));
            cards.add(new CardView.Card("Seven bunnies, seven carrots", done ? "Every bunny's fed. Yeehaw!" : "80 Half-Eaten Carrots make a Nearly-Whole one",
                    0x55FF55, !done && whole >= 7, done ? "Done" : "Give", RiftNpcs.icon("NEARLY_WHOLE_CARROT"), "",
                    done ? List.of() : List.of(Chip.item(RiftNpcs.icon("NEARLY_WHOLE_CARROT"), 7, whole)), -1, ""));
            return new CardView("RIFT", "Cowboy Nick", "Rabbits", npc, List.of(), cards, "Stand clear when the hay grows back. It doesn't ask.", 1, 36);
        }, (p, card) -> {
            switch (card) {
                case 0 -> {
                    if (Sacks.countAnywhere(p, "HORSEZOOKA") == 0) {
                        give(p, "HORSEZOOKA", 1);
                        say(p, "Here's my Horsezooka. Point it at hay and let her rip. That cave won't clear itself.");
                    }
                }
                case 2 -> {
                    if (data(p, "hay") >= HAY_NEEDED && data(p, "rabbit") == 0) {
                        set(p, "rabbit", 1);
                        say(p, "This here's " + NAMES.get(Math.floorMod(p.getUUID().hashCode(), NAMES.size()))
                                + ". Take 'em to the carrots in the cave. Mind the hay.");
                    }
                }
                case 3 -> {
                    if (RiftFlags.has(p, "nick:done")) {
                        return "";
                    }
                    if (!Sacks.takeAnywhere(p, "NEARLY_WHOLE_CARROT", 7)) {
                        return "You need 7 Nearly-Whole Carrots.";
                    }
                    RiftFlags.set(p, "nick:done");
                    Notice.of(Notice.Kind.SUCCESS).tag("COWBOY NICK").send(p, Component.literal("Every bunny's fed! Nick tells you a rabbit joke. It's terrible.")
                            .withStyle(ChatFormatting.GOLD));
                    say(p, "What do you call a rabbit with fleas? Bugs Bunny. Yeehaw!");
                }
                default -> {
                }
            }
            return "";
        });
    }

    private static void say(ServerPlayer player, String line) {
        player.sendSystemMessage(Component.literal("[NPC] Cowboy Nick: ").withStyle(ChatFormatting.YELLOW).append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    private static void give(ServerPlayer player, String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        ItemStack stack = SkyItems.create(def, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }
}
