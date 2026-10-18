package com.skyrpg.skyblock.galatea;

import com.mojang.serialization.Codec;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.crimson.FactionMenu;
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

/**
 * Rosemary's Forest Essence shop at the Murkwater Outpost (Hypixel's list: Wooden Bait, Frog's Spirit, the Common Hunting
 * Wisdom Booster, Drone Mod: Grungle and the Fig Hew, Figstone Splitter and Helix Chopper) and her six perks with
 * Hypixel's level counts and costs. Decided where Hypixel's numbers weren't found: the axes' prices, and what the perks
 * do (Trapped: Huntraps 2% faster a level; Axed: a thrown axe recharges twice as fast; Extreme Pressure: +2 Pressure
 * Resistance a level; Lumberjack: +5 Foraging Fortune a level on Galatea; Tasty: Tree Gifts give 2% more Forest Essence
 * a level; Forest Training: Foraging Wisdom on Galatea, +3 / +6 / +10).
 */
final class Rosemary {
    record Sale(String item, int count, long essence) {
    }

    record Perk(String id, String name, String effect, int[] costs) {
    }

    static final List<Sale> SALES = List.of(new Sale("WOODEN_BAIT", 16, 100), new Sale("HUNTING_WISDOM_BOOSTER_COMMON", 1, 250),
            new Sale("GRUNGLE", 1, 2_500), new Sale("FROGS_SPIRIT_SCROLL", 1, 5_000), new Sale("FIG_AXE", 1, 1_500),
            new Sale("FIGSTONE_AXE", 1, 6_000), new Sale("HELIX_CHOPPER", 1, 25_000));

    static final List<Perk> PERKS = List.of(
            new Perk("trapped", "Trapped", "Huntraps catch 2% faster a level", new int[] {100, 200, 300, 400, 500}),
            new Perk("axed", "Axed", "A thrown axe recharges twice as fast", new int[] {10_000}),
            new Perk("extreme_pressure", "Extreme Pressure", "+2 Pressure Resistance a level",
                    new int[] {100, 200, 300, 400, 500, 600, 700, 800, 900, 1_000}),
            new Perk("lumberjack", "Lumberjack", "+5 Foraging Fortune a level on Galatea",
                    new int[] {125, 250, 500, 750, 1_000, 1_500, 2_000, 2_500, 3_000, 4_000}),
            new Perk("tasty", "Tasty", "Tree Gifts give 2% more Forest Essence a level", new int[] {125, 250, 500, 1_000, 1_500}),
            new Perk("forest_training", "Forest Training", "Foraging Wisdom on Galatea: +3 / +6 / +10", new int[] {250, 1_250, 5_000}));

    static final AttachmentType<Map<String, Integer>> LEVELS = AttachmentRegistry.create(SkyRpgSkyblock.id("forest_perks"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    private Rosemary() {
    }

    static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Rosemary's perks";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                out.add(StatTypes.PRESSURE_RESISTANCE, 2.0 * level(player, "extreme_pressure"));
                if (Galatea.isGalatea(player.level())) {
                    out.add(StatTypes.FORAGING_FORTUNE, 5.0 * level(player, "lumberjack"));
                    out.add(StatTypes.FORAGING_WISDOM, new int[] {0, 3, 6, 10}[level(player, "forest_training")]);
                }
            }
        });
    }

    static int level(ServerPlayer player, String id) {
        return player.getAttachedOrElse(LEVELS, Map.of()).getOrDefault(id, 0);
    }

    /** Tasty: the Forest Essence factor on a Tree Gift. */
    static double tasty(ServerPlayer player) {
        return 1 + 0.02 * level(player, "tasty");
    }

    static void open(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Rosemary");
        CardMenu.open(player, p -> {
            long essence = Essence.get(p, "FOREST");
            List<CardView.Card> cards = new ArrayList<>();
            for (Sale sale : SALES) {
                ItemDefinition def = ItemRegistry.skyblock(sale.item());
                ItemStack stack = def == null ? new ItemStack(Items.BARRIER) : SkyItems.create(def, sale.count());
                cards.add(CardView.Card.offer(stack, List.of(new Chip("", ItemStack.EMPTY, Msg.number(sale.essence()) + " Forest Essence",
                        essence >= sale.essence()))));
            }
            for (Perk perk : PERKS) {
                int level = level(p, perk.id());
                boolean maxed = level >= perk.costs().length;
                int cost = maxed ? 0 : perk.costs()[level];
                cards.add(new CardView.Card(perk.name() + (level > 0 ? " " + Msg.roman(level) : ""), perk.effect(), maxed ? 0x55FF55 : 0x7FE0B4,
                        !maxed && essence >= cost, maxed ? "Maxed" : "Upgrade", new ItemStack(Items.FLOWERING_AZALEA), "",
                        maxed ? List.of() : List.of(new Chip("", ItemStack.EMPTY, Msg.number(cost) + " Forest Essence", essence >= cost)),
                        level / (float) perk.costs().length, level + "/" + perk.costs().length));
            }
            return new CardView("GALATEA", "Rosemary", "Forest Essence", npc,
                    List.of(Chip.note(new ItemStack(Items.FLOWERING_AZALEA), Msg.number(essence) + " Forest Essence", true)), cards,
                    "Tree Gifts and Starlyn Contests give Forest Essence.", 1, 36);
        }, (p, card) -> {
            if (card < SALES.size()) {
                return buy(p, SALES.get(card));
            }
            Perk perk = PERKS.get(card - SALES.size());
            int level = level(p, perk.id());
            if (level >= perk.costs().length) {
                return "";
            }
            if (!Essence.take(p, "FOREST", perk.costs()[level])) {
                return "You need " + Msg.number(perk.costs()[level]) + " Forest Essence.";
            }
            Map<String, Integer> levels = new HashMap<>(p.getAttachedOrElse(LEVELS, Map.of()));
            levels.put(perk.id(), level + 1);
            p.setAttached(LEVELS, Map.copyOf(levels));
            Stats.refresh(p);
            p.sendSystemMessage(Component.literal(perk.name() + " " + Msg.roman(level + 1) + "!").withStyle(ChatFormatting.GREEN));
            Msg.playSound(p, SoundEvents.AZALEA_LEAVES_PLACE, 1f, 1.2f);
            return "";
        });
    }

    private static String buy(ServerPlayer player, Sale sale) {
        ItemDefinition def = ItemRegistry.skyblock(sale.item());
        if (def == null) {
            return "";
        }
        if (!Essence.take(player, "FOREST", sale.essence())) {
            return "You need " + Msg.number(sale.essence()) + " Forest Essence.";
        }
        ItemStack stack = SkyItems.create(def, sale.count());
        Component name = stack.getHoverName().copy();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        player.sendSystemMessage(Component.literal("You bought ").withStyle(ChatFormatting.GRAY).append(name)
                .append(Component.literal(sale.count() > 1 ? " x" + sale.count() : "").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.VILLAGER_YES, 0.8f, 1.1f);
        return "";
    }
}
