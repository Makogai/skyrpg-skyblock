package com.skyrpg.skyblock.hollows;

import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.mining.Hotm;
import com.skyrpg.skyblock.mining.HotmTree;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Hypixel's dug treasure chests in the Crystal Hollows: every Hard Stone you mine has a 1 in 500 chance (more with the
 * Great Explorer perk) to uncover a chest where it was. Only you can open it (right-click), within a minute: 3 to 6
 * rolls of Gemstone Powder, Mithril Powder, the zone's gemstones, Treasurite and the odd rare item.
 */
public final class TreasureChests {
    private static final double CHANCE = 0.002;
    private static final int LIFETIME = 60 * 20;

    /** A dug chest: its owner, zone, when it goes, and its lockpick (locks left of {@code locks}, the spot to aim at). */
    private static final class Chest {
        final UUID owner;
        final String zone;
        final long expires;
        int locks = -1;
        int total;
        Direction face = Direction.UP;
        Vec3 point = Vec3.ZERO;

        Chest(UUID owner, String zone, long expires) {
            this.owner = owner;
            this.zone = zone;
            this.expires = expires;
        }
    }

    private static final Map<BlockPos, Chest> CHESTS = new HashMap<>();

    private TreasureChests() {
    }

    static void init() {
        Identifier early = SkyRpgSkyblock.id("treasure_early");
        UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(early, (player, level, hand, hit) -> {
            if (!level.dimension().equals(CrystalHollows.LEVEL) || !CHESTS.containsKey(hit.getBlockPos())) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer server && level instanceof ServerLevel world) {
                open(world, server, hit.getBlockPos(), hit.getDirection(), hit.getLocation());
            }
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(TreasureChests::tick);
    }

    /** Hard Stone was just mined here (now air): maybe a chest. */
    public static void dug(ServerLevel level, ServerPlayer player, BlockPos pos) {
        Hotm.State hotm = Hotm.state(player);
        HotmTree.Perk explorer = HotmTree.get("great_explorer");
        double bonus = explorer == null || hotm.active("great_explorer") <= 0 ? 0 : explorer.number("chance", hotm.active("great_explorer"), false) / 100;
        if (level.getRandom().nextDouble() >= CHANCE * (1 + bonus) || !level.getBlockState(pos).isAir()) {
            return;
        }
        Direction facing = player.getDirection().getOpposite();
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_ALL);
        CHESTS.put(pos.immutable(), new Chest(player.getUUID(), HollowsBiomes.zone(pos.getX(), pos.getY(), pos.getZ()),
                level.getServer().getTickCount() + LIFETIME));
        Notice.of(Notice.Kind.MINING).tag("TREASURE").send(player, Component.literal("You uncovered a treasure chest!").withStyle(ChatFormatting.GOLD));
        Msg.playSound(player, SoundEvents.CHEST_LOCKED, 1.0f, 1.2f);
    }

    private static void open(ServerLevel level, ServerPlayer player, BlockPos pos, Direction face, Vec3 click) {
        Chest chest = CHESTS.get(pos);
        if (!chest.owner.equals(player.getUUID())) {
            player.sendOverlayMessage(Component.literal("This isn't your treasure chest!").withStyle(ChatFormatting.RED));
            return;
        }
        if (chest.locks < 0) {
            // Hypixel's lockpick: aim at the sparkles on the chest five times in a row; Great Explorer takes locks away.
            int explorer = Hotm.state(player).active("great_explorer");
            chest.total = Math.max(0, 5 - explorer / 4);
            chest.locks = chest.total;
            newLock(chest, pos, level.getRandom());
        }
        if (chest.locks > 0) {
            if (face == chest.face && click.distanceTo(chest.point) < 0.2) {
                chest.locks--;
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.8f + 0.2f * (chest.total - chest.locks));
                newLock(chest, pos, level.getRandom());
            } else {
                chest.locks = chest.total;
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.6f);
            }
            if (chest.locks > 0) {
                player.sendOverlayMessage(Component.literal("Locks left: " + chest.locks).withStyle(ChatFormatting.BLUE));
                return;
            }
        }
        CHESTS.remove(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        reward(player, chest.zone, 3 + level.getRandom().nextInt(4), false);
        com.skyrpg.skyblock.mining.Commissions.chestOpened(player);
        Msg.playSound(player, SoundEvents.CHEST_OPEN, 1.0f, 1.0f);
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
    }

    /** A new lock: a random spot on a random face of the chest (never the bottom). */
    private static void newLock(Chest chest, BlockPos pos, RandomSource random) {
        Direction[] faces = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        chest.face = faces[random.nextInt(faces.length)];
        double u = 0.2 + random.nextDouble() * 0.6;
        double v = 0.2 + random.nextDouble() * 0.6;
        chest.point = switch (chest.face) {
            case UP -> new Vec3(pos.getX() + u, pos.getY() + 0.875, pos.getZ() + v);
            case NORTH -> new Vec3(pos.getX() + u, pos.getY() + v * 0.875, pos.getZ());
            case SOUTH -> new Vec3(pos.getX() + u, pos.getY() + v * 0.875, pos.getZ() + 1);
            case EAST -> new Vec3(pos.getX() + 1, pos.getY() + v * 0.875, pos.getZ() + u);
            default -> new Vec3(pos.getX(), pos.getY() + v * 0.875, pos.getZ() + u);
        };
    }

    /**
     * {@code rolls} rolls of a chest's loot for the player: Gemstone Powder, Mithril Powder, the zone's gems, Treasurite
     * and rares; {@code temple} chests (the Jungle Temple's) also have a chance at a Jungle Heart each roll.
     */
    static void reward(ServerPlayer player, String zone, int rolls, boolean temple) {
        RandomSource random = player.getRandom();
        String gem = gemOf(zone);
        Map<String, Integer> items = new HashMap<>();
        long gemstonePowder = 0;
        long mithrilPowder = 0;
        for (int i = 0; i < rolls; i++) {
            int roll = random.nextInt(100);
            if (temple && random.nextInt(8) == 0) {
                items.merge("JUNGLE_HEART", 1, Integer::sum);
                continue;
            }
            if (roll < 40) {
                gemstonePowder += 200 + random.nextInt(301);
            } else if (roll < 65) {
                items.merge("ROUGH_" + gem + "_GEM", 20 + random.nextInt(31), Integer::sum);
            } else if (roll < 75) {
                items.merge("FLAWED_" + gem + "_GEM", 1 + random.nextInt(3), Integer::sum);
            } else if (roll < 85) {
                mithrilPowder += 200 + random.nextInt(401);
            } else if (roll < 90) {
                items.merge("TREASURITE", 1 + random.nextInt(3), Integer::sum);
            } else {
                items.merge(rare(random), 1, Integer::sum);
            }
        }
        Notice.Panel panel = Notice.of(Notice.Kind.MINING).tag("TREASURE CHEST").panel();
        if (gemstonePowder > 0) {
            Hotm.givePowder(player, Hotm.GEMSTONE, gemstonePowder);
            panel.line(Component.literal("+" + Msg.number(gemstonePowder) + " Gemstone Powder").withColor(0xFF55FF));
        }
        if (mithrilPowder > 0) {
            Hotm.givePowder(player, Hotm.MITHRIL, mithrilPowder);
            panel.line(Component.literal("+" + Msg.number(mithrilPowder) + " Mithril Powder").withColor(0x00AA00));
        }
        List<String> order = new ArrayList<>(items.keySet());
        order.sort(null);
        for (String id : order) {
            ItemDefinition def = ItemRegistry.skyblock(id);
            if (def == null) {
                continue;
            }
            int count = items.get(id);
            int left = count;
            while (left > 0) {
                var stack = SkyItems.create(def, Math.min(64, left));
                left -= stack.getCount();
                ItemGains.give(player, stack, ItemGains.Source.MINING);
                if (!stack.isEmpty()) {
                    player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
            panel.line(Component.literal((count > 1 ? count + "x " : "") + def.name()).withColor(def.rarity().color()));
        }
        panel.send(player, Component.literal("Opened!").withStyle(ChatFormatting.GOLD));
    }

    /** The gemstone each zone's chests give. */
    private static String gemOf(String zone) {
        return switch (zone) {
            case "Jungle" -> "AMETHYST";
            case "Mithril Deposits" -> "JADE";
            case "Goblin Holdout" -> "AMBER";
            case "Precursor Remnants" -> "SAPPHIRE";
            case "Magma Fields" -> "TOPAZ";
            default -> "RUBY";
        };
    }

    private static String rare(RandomSource random) {
        if (random.nextInt(200) == 0) {
            return "PICKONIMBUS";
        }
        String[] items = {"OIL_BARREL", "SLUDGE_JUICE", "ASCENSION_ROPE", "WISHING_COMPASS", "JUNGLE_KEY", "GOBLIN_EGG"};
        return items[random.nextInt(items.length)];
    }

    private static void tick(MinecraftServer server) {
        if (CHESTS.isEmpty()) {
            return;
        }
        ServerLevel level = server.getLevel(CrystalHollows.LEVEL);
        if (server.getTickCount() % 5 == 0 && level != null) {
            // The lock to aim at sparkles.
            CHESTS.values().forEach(chest -> {
                if (chest.locks > 0) {
                    level.sendParticles(ParticleTypes.CRIT, chest.point.x, chest.point.y, chest.point.z, 2, 0.02, 0.02, 0.02, 0);
                }
            });
        }
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        long now = server.getTickCount();
        CHESTS.entrySet().removeIf(entry -> {
            if (entry.getValue().expires > now) {
                return false;
            }
            if (level != null && level.getBlockState(entry.getKey()).is(Blocks.CHEST)) {
                level.setBlock(entry.getKey(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            return true;
        });
    }
}
