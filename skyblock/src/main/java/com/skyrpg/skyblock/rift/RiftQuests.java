package com.skyrpg.skyblock.rift;

import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * The Rift's ways to the later Timecharms (Hypixel's): Living Metal from the Living Cave's lapis (with the
 * Self-Recursive Pickaxe: 1 a block, 2 in Living Stillness, times a multiplier that climbs 1x to 5x every 10 blocks you
 * mine), and Barry's Montgray Pen (calm his seven protesters) for the SkyBlock Citizen Timecharm (16 Scribe Crux and the
 * pen). The Living Timecharm is then crafted (8 x 20 Living Metal around 20 Frosty Crux).
 */
final class RiftQuests {
    private static final Map<UUID, Integer> MINED = new HashMap<>();

    private RiftQuests() {
    }

    static void init() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayer server) || !Rift.isRift(level) || !state.is(Blocks.LAPIS_ORE)) {
                return;
            }
            Zones.Zone zone = Zones.at(level, pos);
            if (zone == null || !(zone.name().equals("Living Cave") || zone.name().equals("Living Stillness"))) {
                return;
            }
            ItemDefinition tool = SkyItems.definition(server.getMainHandItem());
            if (tool == null || !"SELF_RECURSIVE_PICKAXE".equals(tool.skyblockId())) {
                server.sendOverlayMessage(Component.literal("Only a Self-Recursive Pickaxe pulls Living Metal out of the lapis.")
                        .withStyle(ChatFormatting.GRAY));
                return;
            }
            int mined = MINED.merge(server.getUUID(), 1, Integer::sum);
            int multiplier = Math.min(5, 1 + (mined - 1) / 10);
            int amount = multiplier * (zone.name().equals("Living Stillness") ? 2 : 1);
            give(server, "LIVING_METAL", amount);
            server.sendOverlayMessage(Component.literal("+" + amount + " Living Metal  ·  " + multiplier + "x").withStyle(ChatFormatting.AQUA));
        });
    }

    /** Leaving the Rift resets the Self-Recursive Pickaxe's multiplier. */
    static void reset(ServerPlayer player) {
        MINED.remove(player.getUUID());
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

    // ---------------------------------------------------------------- Barry and his protesters

    /** A protester calms down when you talk to them. */
    static void protester(ServerPlayer player, String name) {
        boolean first = RiftFlags.set(player, "protest:" + name);
        player.sendSystemMessage(Component.literal("[NPC] Protester: ").withStyle(ChatFormatting.YELLOW).append(Component.literal(first
                ? "...Fine. You make a fair point. I'll put the sign down."
                : "I'm done protesting. Mostly.").withStyle(ChatFormatting.WHITE)));
        if (first && RiftFlags.count(player, "protest:") >= 7) {
            player.sendSystemMessage(Component.literal("Every protester has calmed down. Barry will want to thank you.").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Barry's office: the protest, his pen, and the SkyBlock Citizen Timecharm. */
    static void barry(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            long calmed = Math.min(7, RiftFlags.count(p, "protest:"));
            boolean pen = RiftFlags.has(p, "barry_pen");
            long crux = Sacks.countAnywhere(p, "SCRIBE_CRUX");
            long pens = Sacks.countAnywhere(p, "BARRY_PEN");
            boolean charm = Timecharms.secured(p, "RIFT_TROPHY_CITIZEN") || Sacks.countAnywhere(p, "RIFT_TROPHY_CITIZEN") > 0;
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("The protest", calmed >= 7 ? "Everyone's gone home." : "Talk the protesters down outside", 0xFF5555, false, "",
                    new ItemStack(Items.OAK_SIGN), "", List.of(), calmed / 7f, calmed + "/7"));
            cards.add(new CardView.Card("Barry's Montgray Pen", pen ? "Barry gave you his pen." : "Calm all seven protesters first", 0xFFAA00,
                    calmed >= 7 && !pen, pen ? "Given" : "Ask", RiftNpcs.icon("BARRY_PEN"), "", List.of(), -1, ""));
            cards.add(new CardView.Card("SkyBlock Citizen Timecharm", charm ? "You have it." : "Opens the Living Cave", 0xFF55FF,
                    !charm && pens > 0 && crux >= 16, charm ? "Done" : "Trade", RiftNpcs.icon("RIFT_TROPHY_CITIZEN"), "", charm ? List.of()
                    : List.of(Chip.item(RiftNpcs.icon("SCRIBE_CRUX"), 16, crux), Chip.item(RiftNpcs.icon("BARRY_PEN"), 1, pens)), -1, ""));
            return new CardView("RIFT", "Barry", "Barry Center", npc, List.of(), cards, "Scribes in the Plaza's alleys drop Scribe Crux.", 1, 34);
        }, (p, card) -> {
            if (card == 1) {
                if (RiftFlags.count(p, "protest:") < 7 || !RiftFlags.set(p, "barry_pen")) {
                    return "";
                }
                give(p, "BARRY_PEN", 1);
                p.sendSystemMessage(Component.literal("[NPC] Barry: Peace at last! Here, take my Montgray Pen. I can't sign anything for you without it anyway.")
                        .withStyle(ChatFormatting.YELLOW));
                return "";
            }
            if (card == 2) {
                if (Sacks.countAnywhere(p, "BARRY_PEN") < 1 || Sacks.countAnywhere(p, "SCRIBE_CRUX") < 16) {
                    return "You need Barry's Montgray Pen and 16 Scribe Crux.";
                }
                Sacks.takeAnywhere(p, "BARRY_PEN", 1);
                Sacks.takeAnywhere(p, "SCRIBE_CRUX", 16);
                give(p, "RIFT_TROPHY_CITIZEN", 1);
                Notice.of(Notice.Kind.SUCCESS).tag("TIMECHARM").send(p, Component.literal("Barry signs you in: the SkyBlock Citizen Timecharm! Secure it with Elise.")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                Msg.playSound(p, SoundEvents.VILLAGER_YES, 1f, 1f);
            }
            return "";
        });
    }
}
