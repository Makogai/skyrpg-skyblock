package com.skyrpg.skyblock.galatea;

import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Kysha's Fusion Machine (Hypixel's fusion): two kinds of shard from your Hunting Box go in, a new one comes out. Hypixel's
 * input counts (five of each; two of a Reptile, Amphibian or Elemental shard) and fusion Hunting XP (18.75 to 250 by the
 * output's rarity). Hypixel's recipe tables weren't found, so the recipes are decided: an ID fusion gives the shard of
 * the first's alignment and the higher rarity picked by both IDs (never an input); two shards of one family make a
 * family fusion instead: two shards of that family, a rarity up (when there is one).
 */
final class Fusion {
    private static final Set<String> CHEAP = Set.of("Reptile", "Amphibian", "Elemental");
    private static final double[] XP = {18.75, 37.5, 75, 125, 250};
    private static final int PAGE = 10;

    /** Where you are in the machine: the first pick, the second, and the page you're on. */
    private record Pick(String first, String second, int page) {
    }

    private static final Map<UUID, Pick> PICKS = new HashMap<>();

    private Fusion() {
    }

    static int need(Hunting.Shard shard) {
        return shard.family().stream().anyMatch(CHEAP::contains) ? 2 : 5;
    }

    /** The fusion's result: the shard and how many. */
    record Result(Hunting.Shard shard, int count, boolean family) {
    }

    static Result result(Hunting.Shard a, Hunting.Shard b) {
        int rarity = Math.max(Hunting.rarity(a), Hunting.rarity(b));
        // a family fusion: both of one family, a rarity up
        for (String family : a.family()) {
            if (b.family().contains(family) && rarity < 4) {
                Hunting.Shard up = Hunting.SHARDS.values().stream()
                        .filter(s -> s.family().contains(family) && Hunting.rarity(s) == rarity + 1)
                        .min(Comparator.comparingInt(Fusion::number)).orElse(null);
                if (up != null) {
                    return new Result(up, 2, true);
                }
            }
        }
        List<Hunting.Shard> pool = Hunting.SHARDS.values().stream()
                .filter(s -> s.alignment().equals(a.alignment()) && Hunting.rarity(s) == rarity && s != a && s != b)
                .sorted(Comparator.comparingInt(Fusion::number)).toList();
        if (pool.isEmpty()) {
            return null;
        }
        return new Result(pool.get((number(a) + number(b)) % pool.size()), 1, false);
    }

    private static int number(Hunting.Shard shard) {
        try {
            return Integer.parseInt(shard.id().substring(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static void open(ServerPlayer player) {
        PICKS.put(player.getUUID(), new Pick("", "", 0));
        show(player);
    }

    private static void show(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Kysha");
        CardMenu.open(player, p -> view(p, npc), Fusion::click);
    }

    /** The shards the player has enough of to put in. */
    private static List<Hunting.Shard> usable(ServerPlayer player, String not) {
        return Hunting.SHARDS.values().stream().filter(s -> !s.key().equals(not) && Hunting.box(player, s.key()) >= need(s)).toList();
    }

    private static CardView view(ServerPlayer player, int npc) {
        Pick pick = PICKS.getOrDefault(player.getUUID(), new Pick("", "", 0));
        List<CardView.Card> cards = new ArrayList<>();
        String subtitle;
        if (pick.second().isEmpty()) {
            List<Hunting.Shard> shards = usable(player, pick.first());
            subtitle = pick.first().isEmpty() ? "Pick the first shard" : "Pick the second shard";
            int pages = Math.max(1, (shards.size() + PAGE - 1) / PAGE);
            int page = Math.min(pick.page(), pages - 1);
            for (Hunting.Shard shard : shards.subList(page * PAGE, Math.min(shards.size(), page * PAGE + PAGE))) {
                int r = Hunting.rarity(shard);
                cards.add(new CardView.Card(shard.name() + " (" + shard.id() + ")", shard.alignment() + (shard.family().isEmpty() ? ""
                        : "  ·  " + String.join(", ", shard.family())) + "  ·  uses " + need(shard), r == 0 ? 0xEEEEEE : Hunting.RARITY_COLORS[r], true,
                        "Pick", Hunting.icon(shard), "", List.of(Chip.note(ItemStack.EMPTY,
                        Msg.number(Hunting.box(player, shard.key())) + " in box", true)), -1, ""));
            }
            if (pages > 1) {
                cards.add(new CardView.Card("Page " + (page + 1) + " of " + pages, "Next page", 0xAAAAAA, true, "Next", new ItemStack(Items.ARROW), "",
                        List.of(), -1, ""));
            }
            if (shards.isEmpty()) {
                cards.add(new CardView.Card("Not enough shards", "Fusing takes five of a shard (two of a Reptile, Amphibian or Elemental).",
                        0xAAAAAA, false, "", new ItemStack(Items.CAULDRON), "", List.of(), -1, ""));
            }
        } else {
            Hunting.Shard a = Hunting.SHARDS.get(pick.first());
            Hunting.Shard b = Hunting.SHARDS.get(pick.second());
            Result result = result(a, b);
            subtitle = "Fuse?";
            cards.add(card(a, "In: " + need(a)));
            cards.add(card(b, "In: " + need(b)));
            if (result != null) {
                int r = Hunting.rarity(result.shard());
                cards.add(new CardView.Card((result.family() ? "Family fusion: " : "ID fusion: ") + result.count() + "x " + result.shard().name(),
                        result.shard().attribute() + ": " + result.shard().effect(), r == 0 ? 0xEEEEEE : Hunting.RARITY_COLORS[r], true, "Fuse",
                        new ItemStack(Items.AMETHYST_CLUSTER), "", List.of(Chip.note(ItemStack.EMPTY, "+" + Msg.number(XP[r] * result.count())
                        + " Hunting XP", true)), -1, ""));
            }
            cards.add(new CardView.Card("Start over", "Pick two other shards.", 0xAAAAAA, true, "Back", new ItemStack(Items.ARROW), "", List.of(), -1, ""));
        }
        return new CardView("GALATEA", "Fusion Machine", subtitle, npc, List.of(Chip.note(new ItemStack(Items.BREWING_STAND),
                "Hunting " + SkillLevels.level(player, SkillLevels.HUNTING), true)), cards, "Two shards in, a new one out. The inputs are used up.", 1, 34);
    }

    private static CardView.Card card(Hunting.Shard shard, String note) {
        int r = Hunting.rarity(shard);
        return new CardView.Card(shard.name() + " (" + shard.id() + ")", shard.attribute(), r == 0 ? 0xEEEEEE : Hunting.RARITY_COLORS[r], false, "",
                Hunting.icon(shard), "", List.of(Chip.note(ItemStack.EMPTY, note, true)), -1, "");
    }

    private static String click(ServerPlayer player, int card) {
        Pick pick = PICKS.getOrDefault(player.getUUID(), new Pick("", "", 0));
        if (pick.second().isEmpty()) {
            List<Hunting.Shard> shards = usable(player, pick.first());
            int pages = Math.max(1, (shards.size() + PAGE - 1) / PAGE);
            int page = Math.min(pick.page(), pages - 1);
            int index = page * PAGE + card;
            if (card == Math.min(PAGE, shards.size() - page * PAGE) && pages > 1) {
                PICKS.put(player.getUUID(), new Pick(pick.first(), "", (page + 1) % pages));
                return "";
            }
            if (index >= shards.size()) {
                return "";
            }
            String key = shards.get(index).key();
            PICKS.put(player.getUUID(), pick.first().isEmpty() ? new Pick(key, "", 0) : new Pick(pick.first(), key, 0));
            return "";
        }
        if (card == 3 || card == 2 && result(Hunting.SHARDS.get(pick.first()), Hunting.SHARDS.get(pick.second())) == null) {
            PICKS.put(player.getUUID(), new Pick("", "", 0));
            return "";
        }
        if (card != 2) {
            return "";
        }
        return fuse(player, pick);
    }

    private static String fuse(ServerPlayer player, Pick pick) {
        Hunting.Shard a = Hunting.SHARDS.get(pick.first());
        Hunting.Shard b = Hunting.SHARDS.get(pick.second());
        Result result = result(a, b);
        if (result == null) {
            return "";
        }
        if (Hunting.box(player, a.key()) < need(a) || Hunting.box(player, b.key()) < need(b)) {
            return "You don't have enough of those shards anymore.";
        }
        Hunting.take(player, a.key(), need(a));
        Hunting.take(player, b.key(), need(b));
        Hunting.addShards(player, result.shard().key(), result.count());
        int r = Hunting.rarity(result.shard());
        SkillLevels.giveXp(player, SkillLevels.HUNTING, XP[r] * result.count());
        player.sendSystemMessage(Component.literal("FUSION! ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("+" + result.count() + " ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(result.shard().name() + " Shard").withColor(Hunting.RARITY_COLORS[r]))
                .append(Component.literal(" (" + (result.family() ? "family fusion" : "ID fusion") + ")").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.BREWING_STAND_BREW, 1f, 1.2f);
        PICKS.put(player.getUUID(), new Pick("", "", 0));
        return "";
    }
}
