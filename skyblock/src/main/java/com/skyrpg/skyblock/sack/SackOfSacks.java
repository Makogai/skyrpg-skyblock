package com.skyrpg.skyblock.sack;

import com.skyrpg.core.action.Actions;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Sack of Sacks (Hypixel's bag for sacks, opened from the SkyBlock Menu; here {@code /sackofsacks}, a
 * {@code sack_of_sacks} action and a button in the Sacks screen): its slots (8 at Tropical Fish IV up to 29 at IX), the
 * sacks in them with their size and Pocket Sack-in-a-Sacks, a button to take one out, and one to open the Sacks.
 */
public final class SackOfSacks {
    private static final int OPEN = 0;
    private static final int INSERT = 1;
    private static final int FIRST_SACK = 2;

    private SackOfSacks() {
    }

    static void init() {
        Actions.register("sack_of_sacks", (player, params) -> open(player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("sackofsacks").executes(ctx -> {
            open(ctx.getSource().getPlayerOrException());
            return 1;
        })));
    }

    public static void open(ServerPlayer player) {
        CardMenu.open(player, SackOfSacks::view, SackOfSacks::click);
    }

    private static CardView view(ServerPlayer player) {
        List<Sacks.Owned> owned = Sacks.owned(player);
        int slots = Sacks.slots(player);
        List<CardView.Card> cards = new ArrayList<>();
        cards.add(new CardView.Card("Your Sacks", "Open them to see and take out what they hold.", 0xF2B33D, true, "Open",
                new ItemStack(Items.BUNDLE), "", List.of(), -1, ""));
        cards.add(new CardView.Card("Insert inventory", "Put everything your sacks hold from your inventory into them.", 0x5CD68A, true, "Insert",
                new ItemStack(Items.HOPPER), "", List.of(), -1, ""));
        for (Sacks.Owned sack : owned) {
            ItemDefinition def = ItemRegistry.skyblock(sack.id());
            ItemStack icon = def == null ? ItemStack.EMPTY : SkyItems.create(def, 1);
            String pockets = sack.pockets() == 0 ? "" : "  ·  " + sack.pockets() + "/" + Sacks.MAX_POCKETS + " Pocket Sack-in-a-Sacks";
            cards.add(new CardView.Card(def == null ? sack.id() : def.name(), "Holds " + Msg.number(Sacks.sizeOf(sack)) + " of each item" + pockets,
                    0xFFFFFF, true, "Take out", icon, "", List.of(), -1, ""));
        }
        for (int i = owned.size(); i < slots; i++) {
            cards.add(CardView.Card.info("Empty slot", "Right-click a sack to put it here.", 0x5E6779, new ItemStack(Items.GLASS_PANE)));
        }
        String subtitle = slots == 0 ? "Locked: Tropical Fish IV" : owned.size() + "/" + slots + " slots";
        String footer = slots == 0 ? "Collect Tropical Fish to unlock the Sack of Sacks (8 slots at IV, 29 at IX)."
                : "Sacks of one type add their sizes together. A Pocket Sack-in-a-Sack at the anvil triples one (up to 3).";
        return new CardView("SKYBLOCK", "Sack of Sacks", subtitle, -1, List.of(), cards, footer, 1, 34);
    }

    private static String click(ServerPlayer player, int card) {
        if (card == OPEN) {
            SacksMenu.open(player);
            return null;
        }
        if (card == INSERT) {
            if (Sacks.insertInventory(player) == 0) {
                return "Nothing in your inventory fits your sacks.";
            }
            return "";
        }
        if (card - FIRST_SACK < Sacks.owned(player).size()) {
            Sacks.remove(player, card - FIRST_SACK);
        }
        return "";
    }
}
