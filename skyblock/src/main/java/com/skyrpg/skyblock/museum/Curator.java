package com.skyrpg.skyblock.museum;

import com.skyrpg.core.action.Actions;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Purse;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Museum's two NPCs as card screens. The Curator ({@code museum_curator} action, {@code /curator}): your Museum XP,
 * the 40 milestones with their rewards, and a button to claim the items. Madame Eleanor Q. Goldsworth III
 * ({@code museum_madame}): the Museum, the Appraisal Service (10M coins once, from Milestone 3: the Museum's value) and
 * the Special items' room. Hypixel's Museum building isn't built: the NPCs stand in the Hub's market.
 */
public final class Curator {
    private static final int GOLD = 0xF2B33D;
    private static final int GREEN = 0x55FF55;
    private static final int GRAY = 0x9AA3B5;

    private Curator() {
    }

    static void init() {
        Actions.register("museum_curator", (player, params) -> {
            say(player, "Curator", dialogue(player));
            open(player);
        });
        Actions.register("museum_madame", (player, params) -> {
            say(player, "Madame Eleanor Q. Goldsworth III", "Would you like to help me fill the Museum? I can even reward you for your contributions!");
            openMadame(player);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("curator").executes(ctx -> {
            open(ctx.getSource().getPlayerOrException());
            return 1;
        })));
    }

    private static void say(ServerPlayer player, String npc, String line) {
        player.sendSystemMessage(Component.literal("[NPC] ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(npc).withStyle(npc.equals("Curator") ? ChatFormatting.AQUA : ChatFormatting.GOLD))
                .append(Component.literal(": " + line).withStyle(ChatFormatting.WHITE)));
    }

    private static String dialogue(ServerPlayer player) {
        int milestone = MuseumMilestones.reached(player);
        if (milestone >= 20) {
            return "The museum is almost complete! Your tireless efforts have brought us to the brink of greatness.";
        }
        if (milestone >= 10) {
            return "Your incredible contributions have transformed the museum into a landmark of cultural heritage.";
        }
        return "Welcome! The museum is just starting to take shape. Your first few contributions are the seeds of something magnificent!";
    }

    /** The Curator's milestones screen. */
    public static void open(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Curator");
        CardMenu.open(player, p -> view(p, npc), Curator::click);
    }

    private static CardView view(ServerPlayer player, int npc) {
        int xp = Museum.xp(player);
        int reached = MuseumMilestones.reached(xp);
        int claimable = MuseumMilestones.claimableCount(player);
        List<CardView.Card> cards = new ArrayList<>();
        float toNext = reached >= MuseumMilestones.MAX ? 1 : (xp % MuseumMilestones.XP_PER) / (float) MuseumMilestones.XP_PER;
        cards.add(new CardView.Card("Museum Milestones", "Milestone " + reached + "/" + MuseumMilestones.MAX + "  ·  +" + reached * MuseumMilestones.BITS_PER
                + "% Bits Multiplier  ·  +" + reached * MuseumMilestones.BANK_PER + "% Bank Interest", GOLD, false, "", new ItemStack(Items.GOLD_BLOCK), "",
                List.of(), toNext, xp + " XP"));
        cards.add(new CardView.Card("Your Museum", "Donate, borrow and return items.", 0x55FFFF, true, "Open", new ItemStack(Items.BOOKSHELF), "", List.of(), -1, ""));
        cards.add(new CardView.Card(claimable > 0 ? "Claim rewards" : "Nothing to claim", claimable > 0
                ? claimable + " milestone" + (claimable == 1 ? " has" : "s have") + " items waiting for you." : "Items from milestones you reach show up here.",
                claimable > 0 ? GREEN : GRAY, claimable > 0, claimable > 0 ? "Claim" : "", new ItemStack(Items.CHEST), "", List.of(), -1, ""));
        for (int n = 1; n <= MuseumMilestones.MAX; n++) {
            cards.add(milestone(player, n, xp, reached));
        }
        return new CardView("SKYBLOCK", "Curator", "Museum Milestones", npc, List.of(), cards,
                "Every 100 SkyBlock XP the Museum pays is a milestone. Special items pay no XP.", 1, 34);
    }

    private static CardView.Card milestone(ServerPlayer player, int n, int xp, int reached) {
        MuseumMilestones.Milestone milestone = MuseumMilestones.get(n);
        List<String> lines = new ArrayList<>(List.of("+" + MuseumMilestones.BITS_PER + "% Bits Multiplier", "+" + MuseumMilestones.BANK_PER + "% Bank Interest"));
        List<Chip> chips = new ArrayList<>();
        ItemStack icon = new ItemStack(n <= reached ? Items.GOLD_BLOCK : Items.IRON_BLOCK);
        boolean claimed = !MuseumMilestones.claimable(player, n) && n <= reached;
        for (MuseumMilestones.Reward reward : milestone.bonus()) {
            lines.add(reward.name());
            ItemDefinition def = reward.item().isEmpty() ? null : ItemRegistry.skyblock(reward.item());
            if (def != null) {
                ItemStack stack = SkyItems.create(def, reward.count());
                icon = stack;
                chips.add(Chip.note(stack, reward.count() + " " + reward.name() + (claimed ? " (claimed)" : ""), true));
            }
        }
        boolean claimable = MuseumMilestones.claimable(player, n);
        int color = n <= reached ? GREEN : n == reached + 1 ? GOLD : GRAY;
        float progress = n == reached + 1 ? (xp % MuseumMilestones.XP_PER) / (float) MuseumMilestones.XP_PER : -1;
        return new CardView.Card("Milestone " + n, String.join("  ·  ", lines), color, claimable, claimable ? "Claim" : "", icon, "", chips, progress,
                n <= reached ? "Reached" : milestone.xp() + " XP");
    }

    private static String click(ServerPlayer player, int card) {
        if (card == 1) {
            MuseumMenu.open(player, 0);
            return null;
        }
        if (card == 2) {
            return MuseumMilestones.claim(player, 0) == 0 ? "Nothing to claim." : "";
        }
        if (card >= 3) {
            MuseumMilestones.claim(player, card - 2);
        }
        return "";
    }

    /** Madame Goldsworth: the Museum, the Appraisal Service and the Special items. */
    public static void openMadame(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Goldsworth");
        CardMenu.open(player, p -> madame(p, npc), Curator::clickMadame);
    }

    private static CardView madame(ServerPlayer player, int npc) {
        List<CardView.Card> cards = new ArrayList<>();
        cards.add(new CardView.Card("Your Museum", "Donate items for SkyBlock XP. You can borrow them back whenever you like.", 0x55FFFF, true, "Open",
                new ItemStack(Items.BOOKSHELF), "", List.of(), -1, ""));
        boolean appraised = player.getAttachedOrElse(Museum.APPRAISED, false);
        boolean unlocked = MuseumMilestones.unlocked(player, "Museum Appraisal Service");
        if (appraised) {
            cards.add(new CardView.Card("Museum Appraisal Service", "Your Museum is worth " + Msg.number(Museum.value(player))
                    + " coins (what the items on display fetch from an NPC; borrowed ones don't count).", GREEN, false, "", new ItemStack(Items.DIAMOND), "",
                    List.of(Chip.balance("coins", Museum.value(player))), -1, "Appraised"));
        } else {
            long coins = Purse.get(player);
            cards.add(new CardView.Card("Museum Appraisal Service", unlocked ? "I'll put a value on your Museum, once, for a fee."
                    : "Reach Museum Milestone 3 and I'll put a value on your Museum.", unlocked ? GOLD : GRAY, unlocked && coins >= Museum.APPRAISAL_COST,
                    unlocked ? "Appraise" : "Locked", new ItemStack(Items.DIAMOND), "", List.of(Chip.price("coins", Museum.APPRAISAL_COST, coins)), -1,
                    unlocked ? "" : "Milestone 3"));
        }
        int specials = Museum.specials(Museum.donated(player));
        cards.add(new CardView.Card("Special Items", specials + "/" + Museum.SPECIAL_LIMIT + " on display. They pay no SkyBlock XP.", 0xFF55FF, false, "",
                new ItemStack(Items.CAKE), "", List.of(), specials / (float) Museum.SPECIAL_LIMIT, ""));
        return new CardView("SKYBLOCK", "Madame Goldsworth", "The Museum", npc, List.of(Chip.balance("coins", Purse.get(player))), cards,
                "Every item you donate stays yours: borrow it back from your Museum.", 1, 40);
    }

    private static String clickMadame(ServerPlayer player, int card) {
        if (card == 0) {
            MuseumMenu.open(player, 0);
            return null;
        }
        if (card == 1 && !player.getAttachedOrElse(Museum.APPRAISED, false)) {
            if (!MuseumMilestones.unlocked(player, "Museum Appraisal Service")) {
                return "Reach Museum Milestone 3 first.";
            }
            if (!Purse.trySpend(player, Museum.APPRAISAL_COST)) {
                return "You need " + Msg.number(Museum.APPRAISAL_COST) + " coins.";
            }
            player.setAttached(Museum.APPRAISED, true);
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
            say(player, "Madame Eleanor Q. Goldsworth III", "Truly magnificent! I value your Museum at " + Msg.number(Museum.value(player)) + " coins.");
        }
        return "";
    }
}
