package com.skyrpg.skyblock.hollows;

import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.enchant.Books;
import com.skyrpg.skyblock.essence.Essence;
import com.skyrpg.skyblock.mining.Hotm;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * The Crystal Nucleus Loot Bundle (Hypixel's table and weights): 14 to 19 rolls, one of every Fine gemstone of the Hollows,
 * and 800 HotM XP (or 2,000-7,000 Mithril or Gemstone Powder once Heart of the Mountain is level 10).
 */
final class NucleusLoot {
    /** {@code id} is a SkyBlock item, {@code essence:KIND} or {@code book:enchant:level}. */
    record Loot(String id, int amount, int weight) {
    }

    static final List<Loot> TABLE = List.of(
        new Loot("FLAWLESS_JASPER_GEM", 1, 5),
        new Loot("FLAWLESS_RUBY_GEM", 1, 15),
        new Loot("FLAWLESS_AMETHYST_GEM", 1, 15),
        new Loot("FLAWLESS_JADE_GEM", 1, 15),
        new Loot("FLAWLESS_AMBER_GEM", 1, 15),
        new Loot("FLAWLESS_SAPPHIRE_GEM", 1, 15),
        new Loot("FLAWLESS_TOPAZ_GEM", 1, 15),
        new Loot("DIVAN_ALLOY", 1, 17),
        new Loot("PET_ITEM_QUICK_CLAW", 1, 50),
        new Loot("FINE_JASPER_GEM", 1, 200),
        new Loot("TREASURITE", 10, 200),
        new Loot("RECALL_POTION", 1, 250),
        new Loot("WISHING_COMPASS", 3, 500),
        new Loot("PRECIOUS_PEARL", 1, 500),
        new Loot("CLAW_FOSSIL", 1, 500),
        new Loot("FLAWED_JASPER_GEM", 32, 700),
        new Loot("GEMSTONE_MIXTURE", 1, 700),
        new Loot("book:fortune:4", 1, 700),
        new Loot("essence:GOLD", 10, 750),
        new Loot("essence:DIAMOND", 10, 750),
        new Loot("FINE_RUBY_GEM", 1, 900),
        new Loot("FINE_AMETHYST_GEM", 1, 900),
        new Loot("FINE_JADE_GEM", 1, 900),
        new Loot("FINE_AMBER_GEM", 1, 900),
        new Loot("FINE_SAPPHIRE_GEM", 1, 900),
        new Loot("FINE_TOPAZ_GEM", 1, 900),
        new Loot("JADERALD", 1, 1450),
        new Loot("DWARVEN_OS_GEMSTONE_GRAHAMS", 1, 1450),
        new Loot("FLAWED_RUBY_GEM", 40, 2000),
        new Loot("FLAWED_AMETHYST_GEM", 40, 2000),
        new Loot("FLAWED_JADE_GEM", 40, 2000),
        new Loot("FLAWED_AMBER_GEM", 40, 2000),
        new Loot("FLAWED_SAPPHIRE_GEM", 40, 2000),
        new Loot("FLAWED_TOPAZ_GEM", 40, 2000),
        new Loot("FLAWED_JASPER_GEM", 24, 2000),
        new Loot("TREASURITE", 5, 2000),
        new Loot("RUBY_CRYSTAL", 1, 2500),
        new Loot("JASPER_CRYSTAL", 1, 2500),
        new Loot("HELIX", 1, 4000),
        new Loot("book:lapidary:1", 1, 4000),
        new Loot("FLAWED_RUBY_GEM", 24, 5000),
        new Loot("FLAWED_AMETHYST_GEM", 24, 5000),
        new Loot("FLAWED_JADE_GEM", 24, 5000),
        new Loot("FLAWED_AMBER_GEM", 24, 5000),
        new Loot("FLAWED_SAPPHIRE_GEM", 24, 5000),
        new Loot("FLAWED_TOPAZ_GEM", 24, 5000),
        new Loot("FLAWED_JASPER_GEM", 12, 5000),
        new Loot("PREHISTORIC_EGG", 1, 5000),
        new Loot("DIVAN_FRAGMENT", 1, 6000),
        new Loot("OIL_BARREL", 1, 8000),
        new Loot("FLAWED_RUBY_GEM", 12, 10000),
        new Loot("FLAWED_AMETHYST_GEM", 12, 10000),
        new Loot("FLAWED_JADE_GEM", 12, 10000),
        new Loot("FLAWED_AMBER_GEM", 12, 10000),
        new Loot("FLAWED_SAPPHIRE_GEM", 12, 10000),
        new Loot("FLAWED_TOPAZ_GEM", 12, 10000),
        new Loot("FLAWED_JASPER_GEM", 6, 10000),
        new Loot("PICKONIMBUS", 1, 10000),
        new Loot("BOB_OMB", 20, 10000),
        new Loot("FLAWED_RUBY_GEM", 6, 20000),
        new Loot("FLAWED_AMETHYST_GEM", 6, 20000),
        new Loot("FLAWED_JADE_GEM", 6, 20000),
        new Loot("FLAWED_AMBER_GEM", 6, 20000),
        new Loot("FLAWED_SAPPHIRE_GEM", 6, 20000),
        new Loot("FLAWED_TOPAZ_GEM", 6, 20000),
        new Loot("DIVAN_ALLOY", 1, 17)
    );
    private static final int TOTAL = TABLE.stream().mapToInt(Loot::weight).sum();
    /** Gems whose Fine tier is in every bundle. */
    private static final List<String> GEMS = List.of("RUBY", "AMBER", "TOPAZ", "JADE", "SAPPHIRE", "AMETHYST", "JASPER");

    private NucleusLoot() {
    }

    static void give(ServerPlayer player) {
        RandomSource random = player.getRandom();
        int rolls = 14 + random.nextInt(6);
        Map<String, Integer> gains = new LinkedHashMap<>();
        List<Component> lines = new ArrayList<>();
        for (String gem : GEMS) {
            gains.merge("FINE_" + gem + "_GEM", 1, Integer::sum);
        }
        for (int i = 0; i < rolls; i++) {
            Loot loot = pick(random);
            if (loot.id().startsWith("essence:")) {
                String kind = loot.id().substring(8);
                Essence.give(player, kind, loot.amount());
                lines.add(Component.literal("+" + loot.amount() + " " + Essence.name(kind) + " Essence").withColor(Essence.color(kind)));
            } else {
                gains.merge(loot.id(), loot.amount(), Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> gain : gains.entrySet()) {
            ItemStack stack = stackOf(gain.getKey(), gain.getValue());
            if (stack.isEmpty()) {
                continue;
            }
            Component name = stack.getHoverName().copy();
            HollowsState.give(player, stack, ItemGains.Source.MINING);
            lines.add(Component.literal(gain.getValue() > 1 ? gain.getValue() + "x " : "").append(name));
        }
        if (Hotm.level(player) >= 10) {
            long amount = 2000 + random.nextInt(5001);
            String type = random.nextBoolean() ? Hotm.MITHRIL : Hotm.GEMSTONE;
            Hotm.givePowder(player, type, amount);
            lines.add(Component.literal("+" + Msg.number(amount) + " " + Hotm.powderName(type)).withColor(Hotm.powderColor(type)));
        } else {
            Hotm.giveXp(player, 800);
            lines.add(Component.literal("+800 HotM XP").withStyle(ChatFormatting.DARK_GREEN));
        }
        Notice.Panel panel = Notice.of(Notice.Kind.MINING).tag("CRYSTAL NUCLEUS").panel();
        lines.forEach(panel::line);
        panel.send(player, Component.literal("You've earned a Crystal Loot Bundle!").withStyle(ChatFormatting.LIGHT_PURPLE));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    private static Loot pick(RandomSource random) {
        int roll = random.nextInt(TOTAL);
        for (Loot loot : TABLE) {
            roll -= loot.weight();
            if (roll < 0) {
                return loot;
            }
        }
        return TABLE.get(TABLE.size() - 1);
    }

    private static ItemStack stackOf(String id, int count) {
        if (id.startsWith("book:")) {
            String[] parts = id.split(":");
            return Books.create(parts[1], Integer.parseInt(parts[2]), count);
        }
        return HollowsState.stack(id, count);
    }
}
