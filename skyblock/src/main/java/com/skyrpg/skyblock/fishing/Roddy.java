package com.skyrpg.skyblock.fishing;

import com.skyrpg.core.action.Actions;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.RodParts;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Roddy: puts Rod Parts on your fishing rods ({@link RodParts}). Pick a rod from your inventory, then a part you
 * carry for each of its three slots (hook, line, sinker); a slot holds one part until you take it off for coins by its rarity.
 */
final class Roddy {
    /** What taking a part off costs, by its rarity (Hypixel's). */
    private static final long[] REMOVAL = {10_000, 25_000, 50_000, 100_000, 250_000, 500_000, 1_000_000};
    /** The inventory slot of the rod each player is working on (-1: none). */
    private static final Map<UUID, Integer> PICKED = new HashMap<>();

    /** One card on the screen and what clicking it does (it returns "" when done or why not). */
    private record Entry(CardView.Card card, Supplier<String> click) {
    }

    private Roddy() {
    }

    static void init() {
        Actions.register("roddy", (player, params) -> {
            PICKED.remove(player.getUUID());
            open(player);
        });
    }

    static void open(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Roddy");
        CardMenu.open(player, p -> {
            List<CardView.Card> cards = entries(p).stream().map(Entry::card).toList();
            ItemStack picked = picked(p);
            return new CardView("SKYBLOCK", "Roddy", picked.isEmpty() ? "Pick a fishing rod" : "Rod Parts", npc,
                    List.of(Chip.balance("coins", Wallet.balance(p))), cards,
                    picked.isEmpty() ? "A rod takes a hook, a line and a sinker." : "Taking a part off costs coins; you get the part back.", 1, 40);
        }, (p, card) -> {
            List<Entry> entries = entries(p);
            return card < entries.size() ? entries.get(card).click().get() : "";
        });
    }

    private static ItemStack picked(ServerPlayer player) {
        int slot = PICKED.getOrDefault(player.getUUID(), -1);
        if (slot < 0 || slot >= player.getInventory().getContainerSize() || !RodParts.isRod(player.getInventory().getItem(slot))) {
            PICKED.remove(player.getUUID());
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(slot);
    }

    private static List<Entry> entries(ServerPlayer player) {
        List<Entry> out = new ArrayList<>();
        ItemStack rod = picked(player);
        if (rod.isEmpty()) {
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!RodParts.isRod(stack)) {
                    continue;
                }
                int index = slot;
                int on = RodParts.parts(stack).size();
                out.add(new Entry(new CardView.Card(stack.getHoverName().getString(), on + "/3 parts on", 0xFFFFFF, true, "Select", stack.copy(), "", List.of(), -1, ""), () -> {
                    PICKED.put(player.getUUID(), index);
                    return "";
                }));
            }
            if (out.isEmpty()) {
                out.add(new Entry(CardView.Card.info("No fishing rod", "Bring a fishing rod to put parts on.", 0xAAAAAA,
                        new ItemStack(net.minecraft.world.item.Items.FISHING_ROD)), () -> ""));
            }
            return out;
        }
        out.add(new Entry(new CardView.Card("Choose another rod", "", 0xFFFFFF, true, "Back", rod.copy(), "", List.of(), -1, ""), () -> {
            PICKED.remove(player.getUUID());
            return "";
        }));
        for (String slot : RodParts.SLOTS) {
            String label = Character.toUpperCase(slot.charAt(0)) + slot.substring(1);
            RodParts.Part on = RodParts.part(RodParts.on(rod, slot));
            ItemDefinition onDef = on == null ? null : ItemRegistry.skyblock(on.id());
            if (onDef != null) {
                long cost = REMOVAL[Math.min(REMOVAL.length - 1, onDef.rarity().ordinal())];
                out.add(new Entry(new CardView.Card(label + ": " + onDef.name(), "Take it off.", 0xFFFFFF, true, "Remove", SkyItems.create(onDef, 1), "",
                        List.of(Chip.price("coins", cost, Wallet.balance(player))), -1, ""), () -> remove(player, slot, cost)));
                continue;
            }
            boolean any = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                RodParts.Part part = RodParts.of(stack);
                if (part == null || !part.slot().equals(slot)) {
                    continue;
                }
                any = true;
                out.add(new Entry(new CardView.Card(label + ": " + stack.getHoverName().getString(), "Put it on the rod.", 0xFFFFFF, true, "Apply",
                        stack.copyWithCount(1), "", List.of(), -1, ""), () -> apply(player, part)));
            }
            if (!any) {
                out.add(new Entry(CardView.Card.info(label + ": none", "You carry no " + slot + " to put on.", 0xAAAAAA, ItemStack.EMPTY), () -> ""));
            }
        }
        return out;
    }

    private static String apply(ServerPlayer player, RodParts.Part part) {
        ItemStack rod = picked(player);
        if (rod.isEmpty()) {
            return "Pick a rod first.";
        }
        if (!RodParts.on(rod, part.slot()).isEmpty()) {
            return "Take off its " + part.slot() + " first.";
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (RodParts.of(stack) == part) {
                stack.shrink(1);
                RodParts.apply(rod, part);
                Msg.playSound(player, SoundEvents.ANVIL_USE, 0.6f, 1.4f);
                player.getInventory().setChanged();
                return "";
            }
        }
        return "You no longer carry that part.";
    }

    private static String remove(ServerPlayer player, String slot, long cost) {
        ItemStack rod = picked(player);
        ItemDefinition def = rod.isEmpty() ? null : ItemRegistry.skyblock(RodParts.on(rod, slot));
        if (def == null) {
            return "";
        }
        if (!Wallet.take(player, cost)) {
            return "You need " + Msg.number(cost) + " Coins.";
        }
        RodParts.remove(rod, slot);
        ItemStack part = SkyItems.create(def, 1);
        if (!player.getInventory().add(part)) {
            player.drop(part, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        Msg.playSound(player, SoundEvents.ANVIL_PLACE, 0.6f, 1.2f);
        return "";
    }
}
