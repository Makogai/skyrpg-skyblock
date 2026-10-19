package com.skyrpg.skyblock.hollows;

import com.skyrpg.core.action.Actions;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
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
 * The Hollows' quest NPCs, as card screens: King Yolkar (a Goblin Egg for his scent, 20 minutes), Professor Robot (six
 * Automaton Parts or a Precursor Apparatus for the Sapphire Crystal), Odawa (Sludge Juice for jungle goods, the Jungle
 * Key among them) and the Kalhuiki Door Guardian (a Jungle Key opens the temple's crystal).
 */
final class HollowsNpcs {
    static final long SCENT_MILLIS = 20L * 60 * 1000;
    private static final String[] EGGS = {"GOBLIN_EGG", "GOBLIN_EGG_BLUE", "GOBLIN_EGG_GREEN", "GOBLIN_EGG_RED", "GOBLIN_EGG_YELLOW"};
    private static final String[] PARTS = {"ELECTRON_TRANSMITTER", "FTX_3070", "ROBOTRON_REFLECTOR", "SUPERLITE_MOTOR", "CONTROL_SWITCH", "SYNTHETIC_HEART"};
    private static final String[] PART_NAMES = {"Electron Transmitter", "FTX 3070", "Robotron Reflector", "Superlite Motor", "Control Switch", "Synthetic Heart"};

    /** Odawa's wares: the item, how many, and the Sludge Juice it costs. */
    private record Ware(String item, int count, int price) {
    }

    private static final List<Ware> WARES = List.of(new Ware("JUNGLE_KEY", 1, 20), new Ware("ROUGH_AMETHYST_GEM", 32, 8),
            new Ware("KALHUIKI_MASK", 1, 12), new Ware("JUNGLE_AMULET", 1, 40), new Ware("TRIBAL_SPEAR", 1, 60), new Ware("JUNGLE_PICKAXE", 1, 120));

    private HollowsNpcs() {
    }

    static void init() {
        Actions.register("hollows_yolkar", (player, params) -> yolkar(player));
        Actions.register("hollows_robot", (player, params) -> robot(player));
        Actions.register("hollows_odawa", (player, params) -> odawa(player));
        Actions.register("hollows_guardian", (player, params) -> guardian(player));
    }

    private static void say(ServerPlayer player, String npc, String text) {
        player.sendSystemMessage(Component.literal("[NPC] " + npc + ": ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
    }

    private static CardView.Card done(String title, String detail, int color, ItemStack icon, boolean enabled, String button, List<Chip> chips) {
        return new CardView.Card(title, detail, color, enabled, button, icon, "", chips, -1, "");
    }

    // ---------------------------------------------------------------- King Yolkar

    private static String egg(ServerPlayer player) {
        for (String egg : EGGS) {
            if (HollowsState.count(player, egg) > 0) {
                return egg;
            }
        }
        return null;
    }

    private static void yolkar(ServerPlayer player) {
        CardMenu.open(player, p -> {
            long left = HollowsState.get(p, "scent") - System.currentTimeMillis();
            String egg = egg(p);
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(done("King's Scent", left > 0 ? Math.max(1, left / 60000) + " minutes of stench left. Hurry, and stay away from water!"
                    : "Bring me any Goblin Egg and I'll cover you in my foul stench for 20 minutes.", 0xFFAA00, HollowsState.stack("GOBLIN_EGG", 1),
                    egg != null, "Give an egg", List.of(Chip.item(HollowsState.stack(egg == null ? "GOBLIN_EGG" : egg, 1), 1, egg == null ? 0 : 1))));
            cards.add(CardView.Card.info("The Amber Crystal", "With the scent you can sneak past the Goblin Queen's guards into her den and take the Amber Crystal.",
                    0xFFAA00, HollowsState.stack("AMBER_CRYSTAL", 1)));
            return new CardView("MINING", "King Yolkar", "Goblin Holdout", -1, List.of(), cards, "Goblins drop eggs now and then.", 1, 44);
        }, (p, card) -> {
            String egg = egg(p);
            if (card != 0 || egg == null || !HollowsState.take(p, egg, 1)) {
                return "";
            }
            HollowsState.set(p, "scent", System.currentTimeMillis() + SCENT_MILLIS);
            say(p, "King Yolkar", "Well done, " + p.getName().getString() + "! I'm covering you in my foul stench. Get to the Queen before it fades!");
            Msg.playSound(p, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.8f, 0.8f);
            return null;
        });
    }

    // ---------------------------------------------------------------- Professor Robot

    private static int partsIn(ServerPlayer player) {
        int n = 0;
        for (String part : PARTS) {
            n += HollowsState.has(player, "part." + part) ? 1 : 0;
        }
        return n;
    }

    private static void robot(ServerPlayer player) {
        CardMenu.open(player, p -> {
            List<CardView.Card> cards = new ArrayList<>();
            int in = partsIn(p);
            for (int i = 0; i < PARTS.length; i++) {
                boolean given = HollowsState.has(p, "part." + PARTS[i]);
                int have = HollowsState.count(p, PARTS[i]);
                cards.add(done(PART_NAMES[i], given ? "Delivered." : "The Automatons carry it.", 0xFFFFFF, HollowsState.stack(PARTS[i], 1),
                        !given && have > 0, "Hand over", given ? List.of() : List.of(Chip.item(HollowsState.stack(PARTS[i], 1), 1, have))));
            }
            int apparatus = HollowsState.count(p, "PRECURSOR_APPARATUS");
            cards.add(done("Precursor Apparatus", "Will do instead of all six parts.", 0xFFFFFF, HollowsState.stack("PRECURSOR_APPARATUS", 1),
                    apparatus > 0, "Hand over", List.of(Chip.item(HollowsState.stack("PRECURSOR_APPARATUS", 1), 1, apparatus))));
            cards.add(new CardView.Card("The giant's storage", in + " of 6 parts delivered.", 0x55FFFF, false, "",
                    HollowsState.stack("SAPPHIRE_CRYSTAL", 1), "", List.of(), in / 6f, in + "/6"));
            return new CardView("MINING", "Professor Robot", "Lost Precursor City", -1, List.of(), cards,
                    "Automatons in the Precursor Remnants carry the parts.", 2, 44);
        }, (p, card) -> {
            boolean ok = false;
            if (card < PARTS.length) {
                ok = !HollowsState.has(p, "part." + PARTS[card]) && HollowsState.take(p, PARTS[card], 1);
                if (ok) {
                    HollowsState.set(p, "part." + PARTS[card], 1);
                    int left = 6 - partsIn(p);
                    if (left > 0) {
                        say(p, "Professor Robot", "Thanks for bringing me the " + PART_NAMES[card] + "! Bring me " + left + " more components to fix the giant!");
                    }
                }
            } else if (card == PARTS.length && HollowsState.take(p, "PRECURSOR_APPARATUS", 1)) {
                ok = true;
                say(p, "Professor Robot", "That's not one of the compon- Wait a minute. This will work just fine.");
                for (String part : PARTS) {
                    HollowsState.set(p, "part." + part, 1);
                }
            }
            if (ok && partsIn(p) == 6) {
                say(p, "Professor Robot", "I have uploaded them to the giant below... it appears it has opened its storage compartment!");
                Msg.playSound(p, SoundEvents.PISTON_EXTEND, 1.0f, 0.6f);
                Crystals.give(p, Crystals.Gem.SAPPHIRE);
                return null;
            }
            return "";
        });
    }

    // ---------------------------------------------------------------- Odawa

    private static void odawa(ServerPlayer player) {
        CardMenu.open(player, p -> {
            int juice = HollowsState.count(p, "SLUDGE_JUICE");
            List<CardView.Card> cards = new ArrayList<>();
            for (Ware ware : WARES) {
                ItemStack item = HollowsState.stack(ware.item(), ware.count());
                cards.add(new CardView.Card(item.isEmpty() ? ware.item() : item.getHoverName().getString(), "", 0xFFFFFF, !item.isEmpty() && juice >= ware.price(),
                        "", item, "", List.of(Chip.item(HollowsState.stack("SLUDGE_JUICE", 1), ware.price(), juice)), -1, ware.count() > 1 ? "x" + ware.count() : ""));
            }
            return new CardView("MINING", "Odawa", "Jungle", -1, List.of(Chip.note(HollowsState.stack("SLUDGE_JUICE", 1), Msg.number(juice) + " Sludge Juice", true)),
                    cards, "Don't hurt the younglings.", 2, 44);
        }, (p, card) -> {
            if (card < 0 || card >= WARES.size()) {
                return "";
            }
            Ware ware = WARES.get(card);
            if (HollowsState.stack(ware.item(), 1).isEmpty()) {
                return "Odawa doesn't have that.";
            }
            if (!HollowsState.take(p, "SLUDGE_JUICE", ware.price())) {
                return "You need " + ware.price() + " Sludge Juice!";
            }
            HollowsState.give(p, ware.item(), ware.count(), ItemGains.Source.OTHER);
            Msg.playSound(p, SoundEvents.VILLAGER_TRADE, 1.0f, 1.0f);
            return "";
        });
    }

    // ---------------------------------------------------------------- the Kalhuiki Door Guardian

    private static void guardian(ServerPlayer player) {
        CardMenu.open(player, p -> {
            boolean open = HollowsState.has(p, "temple");
            boolean claimed = HollowsState.has(p, "claimed.AMETHYST");
            int keys = HollowsState.count(p, "JUNGLE_KEY");
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(done("Open the temple", open ? "The door is open to you. Go to the altar for the Amethyst Crystal."
                    : claimed ? "You already carry the Amethyst Crystal. Place it in the Nucleus first."
                    : "Give me a Jungle Key and the temple's treasure is yours.", 0x55FF55, HollowsState.stack("JUNGLE_KEY", 1),
                    !open && !claimed && keys > 0, "Give the key", List.of(Chip.item(HollowsState.stack("JUNGLE_KEY", 1), 1, keys))));
            cards.add(CardView.Card.info("The Amethyst Crystal", "Past the temple's rooms, on the altar. Two loot chests wait beside it, with a better chance at a Jungle Heart.",
                    0xAA00AA, HollowsState.stack("AMETHYST_CRYSTAL", 1)));
            return new CardView("MINING", "Kalhuiki Door Guardian", "Jungle Temple", -1, List.of(), cards,
                    "Odawa trades Jungle Keys for Sludge Juice.", 1, 44);
        }, (p, card) -> {
            if (card != 0 || HollowsState.has(p, "temple") || HollowsState.has(p, "claimed.AMETHYST") || !HollowsState.take(p, "JUNGLE_KEY", 1)) {
                return "";
            }
            HollowsState.set(p, "temple", 1);
            say(p, "Kalhuiki Door Guardian", "The key is true. The temple is open to you.");
            Msg.playSound(p, SoundEvents.IRON_DOOR_OPEN, 1.0f, 0.7f);
            return null;
        });
    }
}
