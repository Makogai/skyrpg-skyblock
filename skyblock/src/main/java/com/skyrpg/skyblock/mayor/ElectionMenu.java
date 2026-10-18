package com.skyrpg.skyblock.mayor;

import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Purse;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.time.SkyBlockTime;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Election (Clerk Seraphine in the Hub, {@code /election}): the Mayor and Minister in office, and while an
 * election is open its five candidates (their perks, the votes and your vote; click to vote, again to change it).
 * The Mayor page ({@code /mayor}): every perk in effect. Scorpius's Bribe is claimed here: Hypixel's coins by your
 * playtime (50k under 50 hours to 1M from 300).
 */
final class ElectionMenu {
    private ElectionMenu() {
    }

    static ItemStack icon(Mayors.Mayor mayor) {
        if (mayor == null) {
            return new ItemStack(Items.PAPER);
        }
        var item = BuiltInRegistries.ITEM.getValue(Identifier.parse(mayor.icon()));
        return new ItemStack(item == null || item == Items.AIR ? Items.PAPER : item);
    }

    private static String perkNames(Mayors.Mayor mayor, List<String> perks) {
        return String.join("  ·  ", perks.stream().map(id -> {
            Mayors.Perk perk = mayor.perk(id);
            return perk == null ? id : perk.name();
        }).toList());
    }

    static void open(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Seraphine");
        CardMenu.open(player, p -> {
            Mayors.Result now = Mayors.current();
            Mayors.Mayor mayor = Mayors.mayor(now.mayor());
            Mayors.Mayor minister = now.minister().isEmpty() ? null : Mayors.mayor(now.minister());
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("Mayor " + (mayor == null ? now.mayor() : mayor.name()), mayor == null ? "" : perkNames(mayor, now.perks()),
                    mayor == null ? 0xFFFFFF : mayor.color(), true, "Perks", icon(mayor), "", List.of(), -1, "In office"));
            if (minister != null) {
                Mayors.Perk perk = minister.perk(now.ministerPerk());
                cards.add(new CardView.Card("Minister " + minister.name(), perk == null ? "" : perk.name() + ": " + perk.description(),
                        minister.color(), false, "", icon(minister), "", List.of(), -1, "Minister"));
            }
            if (Mayors.bribeWaiting(p)) {
                cards.add(new CardView.Card("Scorpius's Bribe", "You voted for the winner. Here's a little something.", 0xAA00AA, true, "Claim",
                        new ItemStack(Items.GOLD_INGOT), "", List.of(), -1, ""));
            }
            int year = Mayors.openElection();
            String footer;
            if (year >= 0) {
                List<Mayors.Candidate> candidates = Mayors.candidates(year);
                Map<String, Integer> tally = Mayors.tally(year);
                int total = Math.max(1, tally.values().stream().mapToInt(Integer::intValue).sum());
                String mine = Mayors.vote(p);
                for (Mayors.Candidate c : candidates) {
                    Mayors.Mayor m = Mayors.mayor(c.key());
                    int votes = tally.getOrDefault(c.key(), 0);
                    boolean chosen = c.key().equals(mine);
                    Mayors.Perk ministerPerk = m.perk(c.minister());
                    cards.add(new CardView.Card(m.name() + (m.special() ? " (special)" : ""), perkNames(m, c.perks())
                            + (ministerPerk == null ? "" : "  ·  Minister: " + ministerPerk.name()), m.color(), !chosen, chosen ? "Voted" : "Vote", icon(m), "",
                            List.of(Chip.note(ItemStack.EMPTY, votes + (votes == 1 ? " vote" : " votes"), true)), votes / (float) total,
                            Math.round(100f * votes / total) + "%"));
                }
                footer = "Year " + year + " election  ·  closes in " + SkyBlockTime.countdown(Mayors.closes(year) - System.currentTimeMillis())
                        + "  ·  your vote can change";
            } else {
                footer = "The next election opens in " + SkyBlockTime.countdown(Mayors.opens(SkyBlockTime.now().year()) - System.currentTimeMillis()) + ".";
            }
            String mine = Mayors.vote(p);
            Mayors.Mayor voted = mine.isEmpty() ? null : Mayors.mayor(mine);
            return new CardView("SKYBLOCK", "Election", year >= 0 ? "Year " + year + " election" : "Between elections", npc,
                    List.of(Chip.note(icon(mayor), "Mayor " + (mayor == null ? "?" : mayor.name()), true),
                            Chip.note(new ItemStack(Items.PAPER), voted == null ? "No vote yet" : "You: " + voted.name(), true)),
                    cards, footer, 1, 40);
        }, (p, card) -> {
            Mayors.Result now = Mayors.current();
            if (card == 0) {
                mayor(p);
                return null;
            }
            int index = card - (now.minister().isEmpty() ? 1 : 2);
            if (Mayors.bribeWaiting(p)) {
                if (index == 0) {
                    return bribe(p);
                }
                index--;
            }
            int year = Mayors.openElection();
            if (year < 0 || index < 0) {
                return "";
            }
            List<Mayors.Candidate> candidates = Mayors.candidates(year);
            if (index >= candidates.size()) {
                return "";
            }
            Mayors.Candidate c = candidates.get(index);
            if (Mayors.vote(p, c.key())) {
                p.sendSystemMessage(Component.literal("You voted for ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(Mayors.mayor(c.key()).name()).withColor(Mayors.mayor(c.key()).color()))
                        .append(Component.literal("! You can change your vote until the election closes.").withStyle(ChatFormatting.GREEN)));
                Msg.playSound(p, SoundEvents.VILLAGER_YES, 0.8f, 1.2f);
            }
            return "";
        });
    }

    private static String bribe(ServerPlayer player) {
        double hours = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 20.0 / 3600;
        long coins = hours < 50 ? 50_000 : hours < 100 ? 100_000 : hours < 150 ? 250_000 : hours < 300 ? 500_000 : 1_000_000;
        Mayors.bribed(player);
        Purse.add(player, coins);
        player.sendSystemMessage(Component.literal("Scorpius slips you ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal(Msg.number(coins) + " coins").withStyle(ChatFormatting.GOLD))
                .append(Component.literal(". Don't tell anyone.").withStyle(ChatFormatting.DARK_PURPLE)));
        Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
        return "";
    }

    /** The Mayor page: every perk in effect, the Minister's, and Jerry's Perkpocalypse turn. */
    static void mayor(ServerPlayer player) {
        CardMenu.open(player, p -> {
            Mayors.Result now = Mayors.current();
            Mayors.Mayor mayor = Mayors.mayor(now.mayor());
            List<CardView.Card> cards = new ArrayList<>();
            if (mayor != null) {
                for (String id : now.perks()) {
                    Mayors.Perk perk = mayor.perk(id);
                    if (perk != null) {
                        cards.add(new CardView.Card(perk.name(), perk.description(), mayor.color(), false, "", icon(mayor), "", List.of(), -1, mayor.name()));
                    }
                }
            }
            Mayors.Mayor minister = now.minister().isEmpty() ? null : Mayors.mayor(now.minister());
            if (minister != null && minister.perk(now.ministerPerk()) != null) {
                Mayors.Perk perk = minister.perk(now.ministerPerk());
                cards.add(new CardView.Card(perk.name(), perk.description(), minister.color(), false, "", icon(minister), "", List.of(), -1,
                        "Minister " + minister.name()));
            }
            long left = Mayors.closes(now.year() + 1) - System.currentTimeMillis();
            return new CardView("SKYBLOCK", "Mayor " + (mayor == null ? now.mayor() : mayor.name()), "Elected in year " + now.year(), -1,
                    List.of(Chip.note(icon(mayor), mayor == null ? "?" : mayor.name(), true)), cards,
                    "In office for " + SkyBlockTime.countdown(left) + " more.", 1, 34);
        }, (p, card) -> "");
    }
}
