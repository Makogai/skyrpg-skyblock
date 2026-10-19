package com.skyrpg.skyblock.hollows;

import com.skyrpg.core.action.Actions;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.mining.Hotm;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The Mines of Divan: the four Keepers (Diamond, Emerald, Gold, Lapis) give a Metal Detector, and each wants back one of
 * Divan's lost tools. The tools lie in treasures buried around the Mines: hold the detector (it shows the distance and
 * beeps faster the closer you are) and right-click within 3 blocks of the spot to dig it up. All four tools handed in
 * open the Jade Crystal on the pedestal in the middle ({@link Crystals}).
 *
 * <p>Decided: the treasure is dug up with a right-click of the detector instead of being found under gold blocks; each
 * player has one treasure at a time at a random open floor spot in the Mines' box.
 */
public final class Keepers {
    private static final String[] GEMS = {"Diamond", "Emerald", "Gold", "Lapis"};
    private static final String[] TOOLS = {"DWARVEN_DIAMOND_AXE", "DWARVEN_EMERALD_HAMMER", "DWARVEN_GOLD_HAMMER", "DWARVEN_LAPIS_SWORD"};
    private static final String DETECTOR = "DWARVEN_METAL_DETECTOR";
    /** The Mines of Divan's box. */
    private static final int MIN_X = 112;
    private static final int MAX_X = 178;
    private static final int MIN_Z = -178;
    private static final int MAX_Z = -112;
    private static final int MIN_Y = 91;
    private static final int MAX_Y = 138;

    private Keepers() {
    }

    static void init() {
        Actions.register("hollows_keeper", (player, params) -> open(player, params.get("gem").getAsString()));
        Identifier early = SkyRpgSkyblock.id("detector_early");
        UseItemCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(early, (player, level, hand) -> {
            if (level instanceof ServerLevel world && player instanceof ServerPlayer server && world.dimension().equals(CrystalHollows.LEVEL)
                    && isDetector(player.getItemInHand(hand))) {
                dig(world, server);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(Keepers::tick);
    }

    private static boolean isDetector(ItemStack stack) {
        var def = SkyItems.definition(stack);
        return def != null && DETECTOR.equals(def.skyblockId());
    }

    /** Whether all four of Divan's tools are handed in. */
    public static boolean complete(ServerPlayer player) {
        for (String gem : GEMS) {
            if (!HollowsState.has(player, "divan." + gem)) {
                return false;
            }
        }
        return true;
    }

    private static int delivered(ServerPlayer player) {
        int n = 0;
        for (String gem : GEMS) {
            n += HollowsState.has(player, "divan." + gem) ? 1 : 0;
        }
        return n;
    }

    // ---------------------------------------------------------------- the Keepers' menu

    private static void open(ServerPlayer player, String gem) {
        int index = List.of(GEMS).indexOf(gem);
        if (index < 0) {
            return;
        }
        String tool = TOOLS[index];
        CardMenu.open(player, p -> {
            List<CardView.Card> cards = new ArrayList<>();
            boolean has = HollowsState.count(p, DETECTOR) > 0;
            cards.add(new CardView.Card("Metal Detector", has ? "You have one." : "Hold it and listen: it beeps faster near a buried treasure.",
                    0xFFFFFF, !has, "Take", HollowsState.stack(DETECTOR, 1), "", List.of(), -1, ""));
            boolean done = HollowsState.has(p, "divan." + gem);
            int have = HollowsState.count(p, tool);
            cards.add(new CardView.Card("Scavenged " + tool(gem), done ? "Handed in." : "Find it in a treasure and bring it to me.", 0xFFFFFF,
                    !done && have > 0, "Hand over", HollowsState.stack(tool, 1), "", List.of(Chip.item(HollowsState.stack(tool, 1), 1, have)), -1, ""));
            cards.add(CardView.Card.info("Divan's Tools", delivered(p) + " of 4 handed in to the Keepers.", 0x55FF55, new ItemStack(Items.EMERALD)));
            cards.add(new CardView.Card("Jade Crystal", complete(p) ? "Take it from the pedestal in the middle of the Mines." : "Opens when all four tools are back.",
                    0x55FF55, false, "", HollowsState.stack("JADE_CRYSTAL", 1), "", List.of(), delivered(p) / 4f, delivered(p) + "/4"));
            return new CardView("MINING", "Keeper of " + gem, "Mines of Divan", -1, List.of(), cards,
                    "Treasures lie buried in the Mines. The detector knows where.", 1, 40);
        }, (p, card) -> {
            if (card == 0 && HollowsState.count(p, DETECTOR) == 0) {
                HollowsState.give(p, DETECTOR, 1, ItemGains.Source.OTHER);
                p.sendSystemMessage(Component.literal("[NPC] Keeper of " + gem + ": ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("Take this Metal Detector and find what was lost.").withStyle(ChatFormatting.WHITE)));
            } else if (card == 1 && !HollowsState.has(p, "divan." + gem) && HollowsState.take(p, tool, 1)) {
                HollowsState.set(p, "divan." + gem, 1);
                p.sendSystemMessage(Component.literal("[NPC] Keeper of " + gem + ": ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(complete(p) ? "That is the last one! The Jade Crystal waits on the pedestal in the middle."
                                : "Divan's tool, home again. " + (4 - delivered(p)) + " to go.").withStyle(ChatFormatting.WHITE)));
                Msg.playSound(p, SoundEvents.ANVIL_USE, 0.8f, 1.2f);
            }
            return "";
        });
    }

    private static String tool(String gem) {
        return switch (gem) {
            case "Diamond" -> "Diamond Axe";
            case "Emerald" -> "Emerald Hammer";
            case "Gold" -> "Golden Hammer";
            default -> "Lapis Sword";
        };
    }

    // ---------------------------------------------------------------- the treasure hunt

    private static BlockPos treasure(ServerPlayer player) {
        long packed = HollowsState.get(player, "divan.treasure");
        return packed == 0 ? null : BlockPos.of(packed);
    }

    /** A random open floor spot in the Mines (2 air above solid ground), or null if none was found. */
    private static BlockPos newSpot(ServerLevel level, RandomSource random) {
        for (int tries = 0; tries < 60; tries++) {
            int x = MIN_X + random.nextInt(MAX_X - MIN_X);
            int z = MIN_Z + random.nextInt(MAX_Z - MIN_Z);
            if (!level.hasChunkAt(new BlockPos(x, MIN_Y, z))) {
                continue;
            }
            for (int y = MAX_Y; y > MIN_Y; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (level.getBlockState(pos.below()).isSolid() && level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()) {
                    return pos;
                }
            }
        }
        return null;
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(CrystalHollows.LEVEL);
        if (level == null || server.getTickCount() % 4 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!isDetector(player.getMainHandItem())
                    || player.getX() < MIN_X - 8 || player.getX() > MAX_X + 8 || player.getZ() < MIN_Z - 8 || player.getZ() > MAX_Z + 8) {
                continue;
            }
            BlockPos spot = treasure(player);
            if (spot == null) {
                spot = newSpot(level, player.getRandom());
                if (spot == null) {
                    continue;
                }
                HollowsState.set(player, "divan.treasure", spot.asLong());
            }
            double distance = player.position().distanceTo(Vec3.atCenterOf(spot));
            player.sendOverlayMessage(Component.literal("TREASURE ").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.BOLD)
                    .append(Component.literal(Math.round(distance) + "m").withStyle(ChatFormatting.AQUA)));
            long interval = Math.max(4, Math.min(40, Math.round(distance)));
            if (server.getTickCount() % (interval / 4 * 4 + 4) == 0) {
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f, (float) Math.max(0.5, 2.0 - distance / 40.0));
            }
        }
    }

    private static void dig(ServerLevel level, ServerPlayer player) {
        BlockPos spot = treasure(player);
        if (spot == null || player.position().distanceTo(Vec3.atCenterOf(spot)) > 3.5) {
            player.sendOverlayMessage(Component.literal("Nothing here. Follow the beeps!").withStyle(ChatFormatting.GRAY));
            return;
        }
        HollowsState.set(player, "divan.treasure", 0);
        RandomSource random = player.getRandom();
        double roll = random.nextDouble() * 100;
        Notice.Panel panel = Notice.of(Notice.Kind.MINING).tag("TREASURE").panel();
        if (roll < 0.01) {
            String[] gems = {"AMBER", "JADE", "AMETHYST", "SAPPHIRE", "RUBY", "TOPAZ"};
            ItemStack fine = HollowsState.stack("FINE_" + gems[random.nextInt(gems.length)] + "_GEM", 2);
            panel.line(Component.literal("2x ").append(fine.getHoverName()));
            HollowsState.give(player, fine, ItemGains.Source.MINING);
        } else if (roll < 0.07) {
            HollowsState.give(player, "PICKONIMBUS", 1, ItemGains.Source.MINING);
            panel.line(Component.literal("Pickonimbus 2000").withStyle(ChatFormatting.GOLD));
        } else if (roll < 18.07 && scavenged(player, panel)) {
            // handed out above
        } else if (roll < 26.07) {
            long amount = 500 + random.nextInt(1001);
            Hotm.givePowder(player, Hotm.MITHRIL, amount);
            panel.line(Component.literal("+" + Msg.number(amount) + " Mithril Powder").withColor(Hotm.powderColor(Hotm.MITHRIL)));
        } else if (roll < 32.07) {
            long amount = 500 + random.nextInt(1001);
            Hotm.givePowder(player, Hotm.GEMSTONE, amount);
            panel.line(Component.literal("+" + Msg.number(amount) + " Gemstone Powder").withColor(Hotm.powderColor(Hotm.GEMSTONE)));
        } else {
            String[] gems = {"RUBY", "JADE", "AMBER", "SAPPHIRE", "AMETHYST", "TOPAZ"};
            int count = 4 << random.nextInt(5);
            String id = "ROUGH_" + gems[random.nextInt(gems.length)] + "_GEM";
            ItemStack stack = HollowsState.stack(id, count);
            if (!stack.isEmpty()) {
                panel.line(Component.literal(count + "x ").append(stack.getHoverName()));
                HollowsState.give(player, stack, ItemGains.Source.MINING);
            }
        }
        panel.send(player, Component.literal("You found a treasure!").withStyle(ChatFormatting.GOLD));
        Msg.playSound(player, SoundEvents.CHEST_OPEN, 1.0f, 1.0f);
    }

    /** A tool you don't have yet (neither handed in nor in your pack); false if you have them all. */
    private static boolean scavenged(ServerPlayer player, Notice.Panel panel) {
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < GEMS.length; i++) {
            if (!HollowsState.has(player, "divan." + GEMS[i]) && HollowsState.count(player, TOOLS[i]) == 0) {
                missing.add(i);
            }
        }
        if (missing.isEmpty()) {
            return false;
        }
        int pick = missing.get(player.getRandom().nextInt(missing.size()));
        ItemStack stack = HollowsState.stack(TOOLS[pick], 1);
        panel.line(stack.getHoverName().copy().withStyle(ChatFormatting.YELLOW));
        HollowsState.give(player, stack, ItemGains.Source.MINING);
        return true;
    }
}
