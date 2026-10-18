package com.skyrpg.skyblock.rift;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Reed's boat (Hypixel's), in the Black Lagoon's hut: five parts from five areas, each one permanent Rift Time
 * (Larva Silk from the woods, Wilted Berberis from the Dreadfarm, Half-Eaten Carrots from the Plaza, a Living Metal
 * Anchor from the Living Cave, Bacte Fragments from the Colosseum), then the finished Rift Boat for 25,000 Motes.
 */
final class Reed {
    record Part(String name, String area, String item, int count, int seconds) {
    }

    static final List<Part> PARTS = List.of(new Part("Powerful Glue", "Wyld Woods", "LARVA_SILK", 16, 20),
            new Part("Replacement Wood", "Dreadfarm", "WILTED_BERBERIS", 64, 20),
            new Part("Tasty Provisions", "Village Plaza", "HALF_EATEN_CARROT", 128, 30),
            new Part("Living Anchor", "Living Cave", "LIVING_METAL_ANVIL", 1, 30),
            new Part("More Powerful Glue", "Colosseum", "BACTE_FRAGMENT", 8, 30));
    static final long BOAT_PRICE = 25_000;

    private Reed() {
    }

    static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Reed's Boat";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                double seconds = 0;
                for (int i = 0; i < PARTS.size(); i++) {
                    if (RiftFlags.has(player, "reed:" + (i + 1))) {
                        seconds += PARTS.get(i).seconds();
                    }
                }
                if (seconds > 0) {
                    out.add(StatTypes.RIFT_TIME, seconds);
                }
            }
        });
    }

    static void open(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            List<CardView.Card> cards = new ArrayList<>();
            int done = 0;
            for (int i = 0; i < PARTS.size(); i++) {
                Part part = PARTS.get(i);
                boolean given = RiftFlags.has(p, "reed:" + (i + 1));
                boolean next = !given && i == done;
                long have = Sacks.countAnywhere(p, part.item());
                done += given ? 1 : 0;
                cards.add(new CardView.Card((i + 1) + ". " + part.name(), given ? "In the boat. +" + part.seconds() + "s Rift Time"
                        : "From the " + part.area() + "  ·  +" + part.seconds() + "s Rift Time", given ? 0x55FF55 : next ? 0xFFAA00 : 0x777777,
                        next && have >= part.count(), given ? "Done" : next ? "Give" : "Later", RiftNpcs.icon(part.item()), "",
                        given ? List.of() : List.of(Chip.item(RiftNpcs.icon(part.item()), part.count(), have)), -1, ""));
            }
            boolean all = done == PARTS.size();
            boolean boat = RiftFlags.has(p, "reed:boat");
            long motes = Motes.get(p);
            cards.add(new CardView.Card("Rift Boat", boat ? "It floats! Mostly." : all ? "Finished. Reed will sell it to you, of course."
                    : "Bring every part first", 0x55FFFF, all && !boat && motes >= BOAT_PRICE, boat ? "Bought" : "Buy", RiftNpcs.icon("REED_BOAT"), "",
                    boat ? List.of() : List.of(Chip.price("motes", BOAT_PRICE, motes)), done / (float) PARTS.size(), done + "/" + PARTS.size()));
            return new CardView("RIFT", "Reed", "The boat", npc, List.of(Chip.balance("motes", motes)), cards,
                    "Kay and I will soon escape the Rift. Any day now.", 1, 34);
        }, (p, card) -> {
            if (card < PARTS.size()) {
                Part part = PARTS.get(card);
                if (RiftFlags.has(p, "reed:" + (card + 1)) || card > 0 && !RiftFlags.has(p, "reed:" + card)) {
                    return "";
                }
                if (!Sacks.takeAnywhere(p, part.item(), part.count())) {
                    return "You need " + part.count() + " " + RiftNpcs.icon(part.item()).getHoverName().getString() + ".";
                }
                RiftFlags.set(p, "reed:" + (card + 1));
                Stats.refresh(p);
                Notice.of(Notice.Kind.SUCCESS).tag("REED'S BOAT").send(p, Component.literal(part.name() + " is in! +" + part.seconds()
                        + "s ф Rift Time, for good.").withStyle(ChatFormatting.AQUA));
                Msg.playSound(p, SoundEvents.ANVIL_USE, 0.6f, 1.4f);
                return "";
            }
            if (RiftFlags.has(p, "reed:boat") || RiftFlags.count(p, "reed:") < PARTS.size()) {
                return "";
            }
            if (!Motes.take(p, BOAT_PRICE)) {
                return "You need " + Msg.number(BOAT_PRICE) + " Motes.";
            }
            RiftFlags.set(p, "reed:boat");
            ItemDefinition def = ItemRegistry.skyblock("REED_BOAT");
            if (def != null) {
                ItemStack boat = SkyItems.create(def, 1);
                if (!p.getInventory().add(boat)) {
                    p.drop(boat, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
            p.sendSystemMessage(Component.literal("[NPC] Reed: ").withStyle(ChatFormatting.YELLOW).append(Component.literal(
                    "Our boat is finally repaired! Well, your boat. That'll be " + Msg.number(BOAT_PRICE) + " Motes. Pleasure doing business.")
                    .withStyle(ChatFormatting.WHITE)));
            return "";
        });
    }
}
