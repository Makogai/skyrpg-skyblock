package com.skyrpg.skyblock.crimson;

import com.mojang.serialization.Codec;
import com.skyrpg.core.stats.FortuneBonus;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.essence.Essence;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Vulcan's Crimson Essence shop (Hypixel's eight perks, NEU's costs, 37,500 essence for all): Strongarm, Fresh Tools,
 * Headstart and Kuudra Master help in Kuudra; Fungus and Harena Fortuna are Mining Fortune on Mycelium and Red Sand;
 * Crimson Training is Combat Wisdom on the Crimson Isle; Wither Piper makes the Vanquisher likelier. Decided where
 * Hypixel's per-level numbers weren't found: Fortuna +2 a level, Training +3 / +6 / +10, Piper +2% a level, and the
 * Kuudra perks work on our Kuudra's own steps (Strongarm: crates carried faster; Fresh Tools: the ballista builds faster;
 * Kuudra Master: +0.1% damage to Kuudra a fuel cell, up to 10%; Headstart waits for Kuudra's token shop).
 */
final class CrimsonPerks {
    record Perk(String id, String name, String effect, int[] costs) {
    }

    static final List<Perk> PERKS = List.of(
            new Perk("strongarm", "Strongarm", "Carry Kuudra's supplies faster: +10% a level", new int[] {1000, 2000}),
            new Perk("fresh_tools", "Fresh Tools", "Build the ballista faster: +3% a level", new int[] {200, 400, 600, 800, 1000}),
            new Perk("headstart", "Headstart", "Start Kuudra with 10 bonus tokens a level (with its token shop)", new int[] {100, 200, 300, 400, 500}),
            new Perk("kuudra_master", "Kuudra Master", "+0.1% damage to Kuudra a shot, up to 10%", new int[] {5000}),
            new Perk("fungus_fortuna", "Fungus Fortuna", "+2 Mining Fortune on Mycelium a level", new int[] {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000}),
            new Perk("harena_fortuna", "Harena Fortuna", "+2 Mining Fortune on Red Sand a level", new int[] {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000}),
            new Perk("crimson_training", "Crimson Training", "Combat Wisdom on the Crimson Isle: +3 / +6 / +10", new int[] {250, 1250, 5000}),
            new Perk("wither_piper", "Wither Piper", "Vanquishers 2% likelier a level", new int[] {500, 750, 1250, 2000, 3000}));

    static final AttachmentType<Map<String, Integer>> LEVELS = AttachmentRegistry.create(SkyRpgSkyblock.id("crimson_perks"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    private CrimsonPerks() {
    }

    static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Crimson Training";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                int training = level(player, "crimson_training");
                if (training > 0 && CrimsonIsle.isCrimson(player.level())) {
                    out.add(StatTypes.COMBAT_WISDOM, new int[] {0, 3, 6, 10}[training]);
                }
            }
        });
        FortuneBonus.register((player, state) -> state.is(Blocks.MYCELIUM) ? 2.0 * level(player, "fungus_fortuna")
                : state.is(Blocks.RED_SAND) ? 2.0 * level(player, "harena_fortuna") : 0);
    }

    static int level(ServerPlayer player, String id) {
        return player.getAttachedOrElse(LEVELS, Map.of()).getOrDefault(id, 0);
    }

    /** Wither Piper's bonus to the Vanquisher's chance, in percent. */
    static double piper(ServerPlayer player) {
        return 2.0 * level(player, "wither_piper");
    }

    /** Vulcan: his Crimson stars (Malik's screen) and his essence shop. */
    static void vulcan(ServerPlayer player) {
        CardMenu.open(player, p -> {
            long essence = Essence.get(p, "CRIMSON");
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("Crimson stars", "Star up your Kuudra armour and belts", 0xFF5555, true, "Open",
                    new ItemStack(Items.NETHER_STAR), "", List.of(), -1, ""));
            for (Perk perk : PERKS) {
                int level = level(p, perk.id());
                boolean maxed = level >= perk.costs().length;
                int cost = maxed ? 0 : perk.costs()[level];
                cards.add(new CardView.Card(perk.name() + (level > 0 ? " " + Msg.roman(level) : ""), perk.effect(), maxed ? 0x55FF55 : 0xFFAA00,
                        !maxed && essence >= cost, maxed ? "Maxed" : "Upgrade", new ItemStack(Items.BLAZE_POWDER), "",
                        maxed ? List.of() : List.of(new Chip("", ItemStack.EMPTY, Msg.number(cost) + " Crimson Essence", essence >= cost)),
                        level / (float) perk.costs().length, level + "/" + perk.costs().length));
            }
            return new CardView("CRIMSON", "Vulcan", "Crimson Essence", -1, List.of(new Chip("", ItemStack.EMPTY, Msg.number(essence) + " Crimson Essence", true)),
                    cards, "Kuudra's chests and the Isle's minibosses drop Crimson Essence.", 1, 36);
        }, (p, card) -> {
            if (card == 0) {
                com.skyrpg.skyblock.dungeon.MalikMenu.open(p, "Vulcan");
                return null;
            }
            Perk perk = PERKS.get(card - 1);
            int level = level(p, perk.id());
            if (level >= perk.costs().length) {
                return "";
            }
            if (!Essence.take(p, "CRIMSON", perk.costs()[level])) {
                return "You need " + Msg.number(perk.costs()[level]) + " Crimson Essence.";
            }
            Map<String, Integer> levels = new HashMap<>(p.getAttachedOrElse(LEVELS, Map.of()));
            levels.put(perk.id(), level + 1);
            p.setAttached(LEVELS, Map.copyOf(levels));
            Stats.refresh(p);
            p.sendSystemMessage(Component.literal(perk.name() + " " + Msg.roman(level + 1) + "!").withStyle(ChatFormatting.GOLD));
            Msg.playSound(p, SoundEvents.BLAZE_SHOOT, 0.8f, 1.2f);
            return "";
        });
    }
}
