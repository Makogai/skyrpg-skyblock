package com.skyrpg.skyblock.rift;

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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Mountaintop's way to the Celestial Timecharm (Hypixel's): Youngite ore (light blue panes) that the Time Gun ages
 * into Timite (2 seconds) and Timite into Obsolite (purple), mined with the Anti-Sentient, Eon and Chrono Pickaxes; Madame
 * Eleanor Q. Goldsworth X's Shen's Ringalia (25,000 Motes, 64 Puff Crux, 20 each of Youngite, Timite and Obsolite, and
 * the Disinfestor Gloves), which Obo wants to see at the Cerebral Citadel's door; and Shen's Hive Mind inside (seven
 * lamps, seven buttons, five sequences): light every lamp of Shen Citrinitas' sequence for the Celestial Timecharm, Shen
 * Magnum Opus' for the Golden Belt.
 */
final class Mountaintop {
    private static final Block YOUNGITE = Blocks.STAINED_GLASS_PANE.lightBlue();
    private static final Block TIMITE = Blocks.STAINED_GLASS_PANE.blue();
    private static final Block OBSOLITE = Blocks.STAINED_GLASS_PANE.purple();
    private static final int AGE_TICKS = 40;
    private static final int REGROW_TICKS = 30 * 20;
    /** The Cerebral Citadel (rift_mountaintop.py: x 19..39, z 14..31 on the summit plateau at y 152). */
    private static final AABB CITADEL = new AABB(19, 152, 14, 40, 162, 32);
    private static final String RINGALIA = "SHENS_RINGALIA";
    private static final List<String> SHENS = List.of("Shen Chaos", "Shen Albedo", "Shen Citrinitas", "Shen Rubedo", "Shen Magnum Opus");
    /** Which lamps each button flips, per sequence (lamps and buttons 1..7). Hypixel's answers: every button; 2, 4, 6;
     *  2, 5, 6, 7; 3, 4, 7; 3, 5, 6, 7 (decided: the wiring, which makes each of those the only answer). */
    private static final int[][][] WIRING = {
            {{1}, {2}, {3}, {4}, {5}, {6}, {7}},
            {{1, 7}, {1, 2, 3}, {3, 6}, {4}, {5, 7}, {5, 6, 7}, {6, 7}},
            {{1, 7}, {1, 2, 3}, {3, 4}, {4, 6}, {4, 5}, {6}, {7}},
            {{1, 5}, {2, 7}, {1, 2, 3}, {4, 5}, {3, 5}, {1, 6}, {6, 7}},
            {{1, 6}, {2, 5}, {1, 2, 3}, {1, 4}, {4, 5}, {6}, {7}}};
    private static final int ALL_LIT = 0b1111111;

    private static final Map<BlockPos, Long> AGED = new HashMap<>();
    private static final Map<BlockPos, Long> MINED = new HashMap<>();
    private static final Map<UUID, BlockPos> AIM = new HashMap<>();
    private static final Map<UUID, long[]> AIM_TIME = new HashMap<>();
    /** Shen's Hive Mind: each player's lamps per sequence, and the sequence they're on. */
    private static final Map<UUID, int[]> LAMPS = new HashMap<>();
    private static final Map<UUID, Integer> SEQUENCE = new HashMap<>();
    private static final Map<UUID, Long> TURNED_BACK = new HashMap<>();

    private Mountaintop() {
    }

    static void init() {
        Identifier first = SkyRpgSkyblock.id("rift_first");
        AttackBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(first, (player, level, hand, pos, direction) -> {
            if (!Rift.isRift(level) || !inMountain(pos)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                return isOre(level.getBlockState(pos)) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return player instanceof ServerPlayer server && mine(server, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(first, (player, level, hand, hit) ->
                player instanceof ServerPlayer server && Rift.isRift(level) && hive(server, hit.getBlockPos()) ? InteractionResult.SUCCESS
                        : InteractionResult.PASS);
        Abilities.register("quantum_projection", (player, ability) -> timeGun(player));
        ServerTickEvents.END_SERVER_TICK.register(Mountaintop::tick);
        // Nothing stays aged or mined over a restart.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerLevel level = Rift.level(server);
            if (level != null) {
                for (BlockPos pos : List.copyOf(MINED.keySet())) {
                    regrow(level, pos);
                }
                for (BlockPos pos : List.copyOf(AGED.keySet())) {
                    regrow(level, pos);
                }
            }
            MINED.clear();
            AGED.clear();
        });
    }

    private static void regrow(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Block.updateFromNeighbourShapes(YOUNGITE.defaultBlockState(), level, pos), 3);
    }

    private static boolean inMountain(BlockPos pos) {
        return pos.getX() >= -10 && pos.getX() <= 199 && pos.getZ() >= -30 && pos.getZ() <= 59;
    }

    private static boolean isOre(BlockState state) {
        return state.is(YOUNGITE) || state.is(TIMITE) || state.is(OBSOLITE);
    }

    private static String held(ServerPlayer player) {
        ItemDefinition def = SkyItems.definition(player.getMainHandItem());
        return def == null ? "" : def.skyblockId();
    }

    // ---------------------------------------------------------------- Timite

    private static boolean mine(ServerPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        BlockState state = level.getBlockState(pos);
        if (!isOre(state)) {
            return false;
        }
        String tool = held(player);
        boolean anti = tool.equals("ANTI_SENTIENT_PICKAXE");
        boolean eon = tool.equals("EON_PICKAXE");
        boolean chrono = tool.equals("CHRONO_PICKAXE");
        String drop;
        if (state.is(YOUNGITE)) {
            drop = anti || eon || chrono ? "YOUNGITE" : null;
            if (drop == null) {
                player.sendOverlayMessage(Component.literal("Youngite needs an Anti-Sentient Pickaxe (Chipper McPickaxe sells them).").withStyle(ChatFormatting.RED));
            }
        } else if (state.is(TIMITE)) {
            drop = eon || chrono ? "TIMITE" : null;
            if (drop == null) {
                player.sendOverlayMessage(Component.literal("Timite needs an Eon or Chrono Pickaxe.").withStyle(ChatFormatting.RED));
            }
        } else {
            drop = chrono ? "OBSOLITE" : null;
            if (drop == null) {
                player.sendOverlayMessage(Component.literal("Obsolite needs a Chrono Pickaxe.").withStyle(ChatFormatting.RED));
            }
        }
        if (drop == null) {
            return true;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        AGED.remove(pos);
        MINED.put(pos.immutable(), (long) level.getServer().getTickCount() + REGROW_TICKS);
        ItemDefinition def = ItemRegistry.skyblock(drop);
        if (def != null) {
            ItemStack stack = SkyItems.create(def, 1);
            ItemGains.give(player, stack, ItemGains.Source.MINING);
            if (!stack.isEmpty()) {
                player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        if (drop.equals("TIMITE")) {
            Rift.addTime(player, 2);
        }
        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5, 12, 0.25, 0.25, 0.25, 0.1);
        Msg.playSound(player, SoundEvents.GLASS_BREAK, 0.8f, drop.equals("YOUNGITE") ? 1.4f : drop.equals("TIMITE") ? 1.1f : 0.8f);
        player.sendOverlayMessage(Component.literal("+1 " + (def == null ? drop : def.name()) + (drop.equals("TIMITE") ? "  ·  +2s ф Rift Time" : ""))
                .withStyle(ChatFormatting.AQUA));
        return true;
    }

    /** The Time Gun's Quantum Projection: hold it on an ore for 2 seconds to age it one step. */
    private static boolean timeGun(ServerPlayer player) {
        if (!Rift.isRift(player.level())) {
            player.sendOverlayMessage(Component.literal("The Time Gun only works in the Rift.").withStyle(ChatFormatting.RED));
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(24)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                player));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(player.getLookAngle().scale(24)) : hit.getLocation();
        DustParticleOptions beam = new DustParticleOptions(0x55CCFF, 0.7f);
        for (int i = 2; i <= 12; i++) {
            Vec3 at = eye.lerp(end, i / 12.0);
            level.sendParticles(player, beam, true, false, at.x, at.y - 0.2, at.z, 1, 0, 0, 0, 0);
        }
        BlockPos pos = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
        BlockState state = pos == null ? null : level.getBlockState(pos);
        if (pos == null || !isOre(state) || !inMountain(pos)) {
            AIM.remove(player.getUUID());
            return true;
        }
        long now = level.getServer().getTickCount();
        long[] times = AIM_TIME.computeIfAbsent(player.getUUID(), id -> new long[2]);
        if (!pos.equals(AIM.get(player.getUUID())) || now - times[1] > 8) {
            AIM.put(player.getUUID(), pos.immutable());
            times[0] = now;
        }
        times[1] = now;
        if (state.is(OBSOLITE)) {
            player.sendOverlayMessage(Component.literal("PARADOX! This ore has maximum time wackification!").withStyle(ChatFormatting.LIGHT_PURPLE));
            return true;
        }
        long held = now - times[0];
        int bars = (int) Math.min(10, held * 10 / AGE_TICKS);
        player.sendOverlayMessage(Component.literal("Quantum Projection ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal("■".repeat(bars)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("■".repeat(10 - bars)).withStyle(ChatFormatting.DARK_GRAY)));
        if (held >= AGE_TICKS) {
            level.setBlock(pos, (state.is(YOUNGITE) ? TIMITE : OBSOLITE).withPropertiesOf(state), 3);
            AGED.put(pos.immutable(), now + REGROW_TICKS);
            times[0] = now;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 15, 0.3, 0.3, 0.3, 0.05);
            Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, state.is(YOUNGITE) ? 1.2f : 0.7f);
        }
        return true;
    }

    // ---------------------------------------------------------------- Shen's Hive Mind

    private static boolean hive(ServerPlayer player, BlockPos pos) {
        Rift.Spot spot = Rift.spots().get("hive_mind");
        if (spot == null) {
            return false;
        }
        int px = (int) Math.floor(spot.pos().x) - 1;
        int py = (int) Math.floor(spot.pos().y) + 1;
        if (pos.getX() != px || pos.getY() != py || pos.getZ() < 19 || pos.getZ() > 29) {
            return false;
        }
        int[] lamps = LAMPS.computeIfAbsent(player.getUUID(), id -> new int[5]);
        int seq = SEQUENCE.getOrDefault(player.getUUID(), 0);
        if (pos.getZ() == 19) {
            seq = (seq + 1) % 5;
            SEQUENCE.put(player.getUUID(), seq);
            Msg.playSound(player, SoundEvents.WOODEN_BUTTON_CLICK_ON, 1f, 1f);
        } else if (pos.getZ() == 29) {
            lamps[seq] = 0;
            Msg.playSound(player, SoundEvents.WOODEN_BUTTON_CLICK_ON, 1f, 0.7f);
        } else if (pos.getZ() >= 21 && pos.getZ() <= 27) {
            for (int lamp : WIRING[seq][pos.getZ() - 21]) {
                lamps[seq] ^= 1 << (lamp - 1);
            }
            Msg.playSound(player, SoundEvents.STONE_BUTTON_CLICK_ON, 1f, 1f);
            if (lamps[seq] == ALL_LIT && RiftFlags.set(player, "shen:" + (seq + 1))) {
                player.sendSystemMessage(Component.literal("Every lamp blazes. " + SHENS.get(seq) + " will see you now.").withStyle(ChatFormatting.GOLD));
                Msg.playSound(player, SoundEvents.BEACON_POWER_SELECT, 1f, 1.2f);
            }
        } else {
            return true;
        }
        showLamps(player, px - 1, py + 2, lamps[seq]);
        boolean solved = RiftFlags.has(player, "shen:" + (seq + 1));
        player.sendOverlayMessage(Component.literal("Sequence " + (seq + 1) + ": " + SHENS.get(seq) + (solved ? "  ·  solved" : "")).withStyle(ChatFormatting.YELLOW));
        return true;
    }

    /** Your lamps, lit on your screen only. */
    private static void showLamps(ServerPlayer player, int x, int y, int bits) {
        for (int k = 0; k < 7; k++) {
            BlockState lamp = Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, (bits >> k & 1) == 1);
            player.connection.send(new ClientboundBlockUpdatePacket(new BlockPos(x, y, 21 + k), lamp));
        }
    }

    // ---------------------------------------------------------------- the tick: ores grow back, Obo turns people away

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel level = Rift.level(server);
        if (level == null) {
            return;
        }
        long now = server.getTickCount();
        for (Map<BlockPos, Long> map : List.of(AGED, MINED)) {
            map.entrySet().removeIf(e -> {
                if (e.getValue() > now || !level.isLoaded(e.getKey())) {
                    return false;
                }
                regrow(level, e.getKey());
                return true;
            });
        }
        Rift.Spot door = Rift.spots().get("citadel_door");
        for (ServerPlayer player : level.players()) {
            if (door == null || player.isCreative() || !CITADEL.contains(player.position()) || hasRingalia(player)) {
                continue;
            }
            player.teleportTo(level, door.pos().x, door.pos().y, door.pos().z, Set.of(), 180, 0, true);
            if (now - TURNED_BACK.getOrDefault(player.getUUID(), -200L) > 100) {
                TURNED_BACK.put(player.getUUID(), now);
                obo(player, "Where do you think you're going? Only those marked by Shen may enter.");
            }
        }
    }

    private static boolean hasRingalia(ServerPlayer player) {
        return RiftFlags.has(player, "ringalia") || Sacks.countAnywhere(player, RINGALIA) > 0;
    }

    private static void obo(ServerPlayer player, String line) {
        player.sendSystemMessage(Component.literal("[NPC] Obo: ").withStyle(ChatFormatting.YELLOW).append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    // ---------------------------------------------------------------- the NPCs

    static void talkObo(ServerPlayer player) {
        obo(player, hasRingalia(player) ? "I see you have Shen's Ringalia. You can go in." : "Where do you think you're going? Show me Shen's mark first.");
    }

    static void oldTimer(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("[NPC] Old Timer: ").withStyle(ChatFormatting.YELLOW).append(Component.literal(
                "Youngite, Timite, Obsolite: same ore, different ages. Aim a Time Gun at the light blue ones for a couple of seconds and "
                        + "they grow up. Blowgun and 32 Youngite make a Time Gun. Ever tried one on your tea? Don't.").withStyle(ChatFormatting.WHITE)));
    }

    /** Madame Eleanor Q. Goldsworth X: Shen's Ringalia. */
    static void eleanor(ServerPlayer player, int npc) {
        record Cost(String item, int count) {
        }
        List<Cost> costs = List.of(new Cost("PUFF_CRUX", 64), new Cost("YOUNGITE", 20), new Cost("TIMITE", 20), new Cost("OBSOLITE", 20),
                new Cost("DISINFESTOR_GLOVES", 1));
        long price = 25_000;
        CardMenu.open(player, p -> {
            boolean has = hasRingalia(p);
            long motes = Motes.get(p);
            List<Chip> chips = new ArrayList<>();
            chips.add(Chip.price("motes", price, motes));
            for (Cost cost : costs) {
                chips.add(Chip.item(RiftNpcs.icon(cost.item()), cost.count(), Sacks.countAnywhere(p, cost.item())));
            }
            List<CardView.Card> cards = List.of(new CardView.Card("Shen's Ringalia", has ? "Shen has marked you." : "+50 Rift Time. Obo lets its wearer into the Citadel.",
                    0xFFAA00, !has, has ? "Owned" : "Buy", RiftNpcs.icon(RINGALIA), "", has ? List.of() : chips, -1, ""));
            return new CardView("RIFT", "Madame Eleanor Q. Goldsworth X", "Shen's Ringalia", npc, List.of(Chip.balance("motes", motes)), cards,
                    "You must now seek my master, at the end of the road.", 1, 44);
        }, (p, card) -> {
            if (hasRingalia(p)) {
                return "";
            }
            if (Motes.get(p) < price) {
                return "You need " + Msg.number(price) + " Motes.";
            }
            for (Cost cost : costs) {
                if (Sacks.countAnywhere(p, cost.item()) < cost.count()) {
                    return "You need " + cost.count() + " " + RiftNpcs.icon(cost.item()).getHoverName().getString() + ".";
                }
            }
            Motes.take(p, price);
            for (Cost cost : costs) {
                Sacks.takeAnywhere(p, cost.item(), cost.count());
            }
            RiftFlags.set(p, "ringalia");
            give(p, RINGALIA);
            Notice.of(Notice.Kind.SUCCESS).tag("SHEN").send(p, Component.literal("Shen's Ringalia: Obo will let you into the Cerebral Citadel.")
                    .withStyle(ChatFormatting.GOLD));
            return "";
        });
    }

    /** A Shen: his reward once his sequence of the Hive Mind is lit. */
    static void shen(ServerPlayer player, String name) {
        int seq = SHENS.indexOf(name) + 1;
        String line;
        if (!RiftFlags.has(player, "shen:" + seq)) {
            line = "Light every lamp of my sequence on the Hive Mind. Sequence " + seq + ". The west wall.";
        } else if (seq == 3) {
            boolean has = Timecharms.secured(player, "RIFT_TROPHY_MOUNTAIN") || Sacks.countAnywhere(player, "RIFT_TROPHY_MOUNTAIN") > 0;
            if (!has && RiftFlags.set(player, "shen:charm")) {
                give(player, "RIFT_TROPHY_MOUNTAIN");
                Notice.of(Notice.Kind.SUCCESS).tag("TIMECHARM").send(player, Component.literal("Shen Citrinitas gives you the Celestial Timecharm! Secure it with Elise.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            } else if (!has) {
                give(player, "RIFT_TROPHY_MOUNTAIN");
            }
            line = "The Celestial Timecharm. Show it at the Wizardman Bureau; they'll understand.";
        } else if (seq == 5) {
            if (RiftFlags.set(player, "shen:belt")) {
                give(player, "GOLDEN_BELT");
            }
            line = "The great work, complete. Wear the Golden Belt; time will bend a little for you.";
        } else {
            line = switch (seq) {
                case 1 -> "Chaos is a ladder. The Celestial Timecharm hangs at the top of mine.";
                case 2 -> "Every dimension has its Shen. In yours, I am rich. In this one, richer.";
                default -> "Albedo wants clarity, Citrinitas light, Magnum Opus completion. I want them all.";
            };
        }
        player.sendSystemMessage(Component.literal("[NPC] " + name + ": ").withStyle(ChatFormatting.YELLOW).append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    private static void give(ServerPlayer player, String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        ItemStack stack = SkyItems.create(def, 1);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }
}
