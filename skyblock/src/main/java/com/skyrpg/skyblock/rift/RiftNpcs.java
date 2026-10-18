package com.skyrpg.skyblock.rift;

import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.time.SkyBlockTime;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Rift's people, on themed card screens: the Hub's Wizard (Dimensional Infusions), Enigma (the cloak and its
 * upgrades), the shops (Hypixel's Motes prices from NEU: the Argofay Traffickers' Wyld armour, Threebrothers' sword,
 * the Bugshopper, Shifted's elixirs, Kay's Lil' Pads, Roy's Enigma Soul), the Motes Grubber (eats Rift items for Motes)
 * and everyone else's lines (Tel Kar gives an Enigma Soul once you've heard him out).
 */
final class RiftNpcs {
    /** A Motes sale: the item, how many, Motes, and an item cost (id, count) or null. */
    record Sale(String item, int count, long motes, String costItem, int costCount) {
    }

    private static final Map<String, List<Sale>> SHOPS = Map.of(
            "Argofay Trafficker 1", List.of(new Sale("WYLD_BOOTS", 1, 50, null, 0)),
            "Argofay Trafficker 2", List.of(new Sale("WYLD_LEGGINGS", 1, 1_000, null, 0)),
            "Argofay Trafficker 3", List.of(new Sale("WYLD_HELMET", 1, 1_000, null, 0)),
            "Argofay Trafficker 4", List.of(new Sale("WYLD_CHESTPLATE", 1, 2_000, null, 0)),
            "Argofay Threebrothers", List.of(new Sale("WYLD_SWORD", 1, 333, "SHAME_CRUX", 3)),
            "Argofay Bugshopper", List.of(new Sale("LARVA_HOOK", 1, 0, "SHAME_CRUX", 6), new Sale("EMPTY_ODONATA_BOTTLE", 1, 400, "SHAME_CRUX", 2)),
            "Shifted", List.of(new Sale("RIFT_SPEED_ELIXIR", 1, 10_000, "SHAME_CRUX", 5), new Sale("RIFT_JUMP_ELIXIR", 1, 5_000, "SHAME_CRUX", 5),
                    new Sale("RIFT_STRENGTH_ELIXIR", 1, 12_000, "SHAME_CRUX", 5)),
            "Kay", List.of(new Sale("LIL_PAD", 1, 10, null, 0), new Sale("LIL_PAD", 16, 160, null, 0)),
            "Seymour", List.of(new Sale("EXCEEDINGLY_COMFY_SNEAKERS", 1, 49_999, "GUNTHER_SNEAKERS", 1)),
            "Chipper McPickaxe", List.of(new Sale("ANTI_SENTIENT_PICKAXE", 1, 24_999, null, 0)));

    /** What the Motes Grubber pays for one of each Rift item (decided: Hypixel's table wasn't found). */
    private static final Map<String, Long> GRUBBER = Map.of("LARVA_SILK", 8L, "MUTED_BARK", 15L, "DEADGEHOG_SPINE", 20L, "LIL_PAD", 4L,
            "SHAME_CRUX", 25L, "LEECH_SUPREME_FRAGMENT", 150L, "BOTTLED_ODONATA", 60L);

    private static final Map<String, List<String>> LINES = Map.ofEntries(
            Map.entry("Tel Kar", List.of("Time runs differently here. Every second outside the tower is one you lose.",
                    "The monsters don't hurt you. They take your time. That's worse, if you think about it.",
                    "The Rift was torn open long ago. Pieces of other worlds fell through: the woods, the lagoon, a whole village.",
                    "You listened all the way through. Few do. Take this: it was never mine to keep.")),
            Map.entry("Sad Jacquelle", List.of("My cat Montezuma... his soul shattered into nine pieces across the Rift.",
                    "If you find a piece, bring it back. He'd be so happy.")),
            Map.entry("Inverted Sirius", List.of(".sdlrow neewteb smeti evom I", "Rift-Transferable items, only. The rest stays where it is.")),
            Map.entry("Porhtal", List.of("Seven eyes, seven places. Calm an eye and I'll carry you there.", "The first eye watches the Wizard Tower.")),
            Map.entry("Argofay Bughunter", List.of("The Odonata dart over the water. Beautiful.", "Bottle one for me and I'll make it worth your while.")),
            Map.entry("Reed", List.of("My boat broke apart and the pieces scattered all over the Rift.", "Powerful glue first. Then wood. Then a lot more.")),
            Map.entry("Dr. Edwin", List.of("The Leech Supreme sleeps under the lagoon.", "When you're ready, the tunnel east of here goes down to its lair.")),
            Map.entry("Dr. Phear", List.of("Leeches. Leeches everywhere.", "The way up is behind me when you've had enough.")),
            Map.entry("Mushroom Guy", List.of("The mushrooms grow when you come close. They like you.", "There's a soul in the grove, if you know where to look.")),
            Map.entry("The Gill-Man", List.of("Blub.", "Blub blub.")),
            Map.entry("Shania", List.of("The crops won't grow right here. Nothing does.", "If only someone had a proper pair of Chicken Leggs...")),
            Map.entry("Sick Farmer", List.of("*cough* Don't eat the berries.", "The Volts came with the storm and never left.")),
            Map.entry("The Baba Yaga", List.of("Hee hee hee.", "The eye sees every field.")),
            Map.entry("Barry", List.of("Please, everyone, calm down!", "My pen... has anyone seen my Montgray Pen? I can't sign anything without it.")),
            Map.entry("Protester", List.of("Down with Barry!", "We want answers!", "What do we want? Answers! When do we want them? Yesterday!")),
            Map.entry("Cowboy Nick", List.of("Howdy, partner. My rabbits are hungry.", "Feed 'em carrots and I'll lend you my Horsezooka.")),
            Map.entry("Detective Amog", List.of("There's been a murder. Or there will be. Or there was.", "Time runs backwards around that house.")),
            Map.entry("Seraphine", List.of("I was... something else, once.", "An Anti-Morph Potion would set me right.")),
            Map.entry("Damia", List.of("Shen's Auctions open once a SkyBlock year.", "Bids in Motes only, dear.")),
            Map.entry("Taylor", List.of("Hems, seams, rifts. I mend them all.", "Mind the needles.")),
            Map.entry("Lonely Avaeikx", List.of("Would you... sit with me a while?", "Nobody ever stays.")),
            Map.entry("The Bankster", List.of("Interest compounds. So do eyes.", "The eye watches the vault.")),
            Map.entry("Gunther", List.of("Fancy a race? Ten checkpoints round the village.", "Under a minute and I'll have something for you.")),
            Map.entry("Chef", List.of("Hot dogs! Fifty in a minute and you're a legend.", "Joey's got the buns.")),
            Map.entry("Joey McPizza", List.of("Buns, buns, buns.", "Chef says you look hungry.")),
            Map.entry("Unhinged Kloon", List.of("The terminals! They're all the wrong colour!", "Rainbow. They should be rainbow.")),
            Map.entry("Kat", List.of("The vermin have taken over my house.", "Bring me Silverfish and I'll make you a key.")),
            Map.entry("Sorcerer Okron", List.of("The glyphs ward the village. Mostly.", "Trace them right and the trinket is yours.")),
            Map.entry("Alchemist", List.of("Potions brewed from things that shouldn't exist.", "Come back when I've got stock.")),
            Map.entry("Finplex", List.of("Zoop the Fish. Ninety-nine thousand nine hundred ninety-nine Motes.", "Worth every Mote.")),
            Map.entry("Plumber Joe", List.of("Leaky pipes everywhere in this place.", "A Plumber's Bucket never runs dry.")),
            Map.entry("Rock", List.of("We rocks hear the Living Metal hum.", "The snakes carry hearts of metal. Stop one, mine its tail.")),
            Map.entry("Chipper McPickaxe", List.of("Lapis here grows Living Metal, if you mine it right.", "The Anti-Sentient Pickaxe breaks Youngite.")),
            Map.entry("Colosseum Guard", List.of("Bacte waits in the arena.", "Punch the Globowls into its tentacles.")),
            Map.entry("Photon Keeper", List.of("The light holds the path together.", "Don't look down.")),
            Map.entry("Maddox the Slayer", List.of("Vampires, friend. The Riftstalker Bloodfiend.", "I'll have contracts for you soon. Bring Motes.")),
            Map.entry("Julian", List.of("Get me out of here!", "The way out is past the guards. Run!")),
            Map.entry("Oubliette Guard", List.of("No one leaves the Oubliette.", "Unless they get past us.")),
            Map.entry("Ubik Von Neumann", List.of("Three games, three souls.", "Split or steal? Choose wisely.")),
            Map.entry("Old Timer", List.of("Timite grows older or younger depending on how you look at it.", "Come back with a Time Gun.")),
            Map.entry("Plate", List.of("The Trials test speed, wit and might.", "The Bureau opens for those with the Celestial charm.")),
            Map.entry("Shen Albedo", List.of("I am Shen. The richest man in the multiverse.", "Puzzles first. Riches after.")),
            Map.entry("Shen Chaos", List.of("I am Shen, also. Chaos is a ladder.", "Puzzles first. Riches after.")),
            Map.entry("Shen Citrinitas", List.of("I am Shen. I keep the Celestial Timecharm.", "Prove yourself and it's yours.")),
            Map.entry("Shen Magnum Opus", List.of("I am Shen, the great work.", "The Golden Belt waits for the patient.")),
            Map.entry("Shen Rubedo", List.of("I am Shen. Red is the colour of completion.", "Puzzles first. Riches after.")),
            Map.entry("Madame Eleanor Q. Goldsworth I", List.of("Darling, the outside world needs your skills.", "Bring me proof of your talents.")),
            Map.entry("Oruo the Almighty", List.of("The Time Chamber drains twice as fast.", "The Sun Gecko waits inside.")),
            Map.entry("Dr. Emmett", List.of("The Mirrorverse lies through that door. Everything there is backwards.", "Bring the Supreme Timecharm's proof and step through.")),
            Map.entry("Roy", List.of("Found an Enigma Soul in the mud. Interested?")),
            Map.entry("Grumblefoot", List.of("Scrolls! Scrolls for the weary!")));

    /** How far through each NPC's lines you are. */
    private static final Map<UUID, Map<String, Integer>> HEARD = new HashMap<>();

    private RiftNpcs() {
    }

    static void init() {
    }

    /** An NPC's shown name: without a trailing number or "(Rift)". */
    static String display(String name) {
        return name.replaceAll(" \\(.*\\)$| \\d+$", "");
    }

    static ItemStack icon(String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? ItemStack.EMPTY : SkyItems.create(def, 1);
    }

    /** The nearest Mannequin named like the NPC (for its portrait), or -1. */
    private static int npcNear(ServerPlayer player, String name) {
        String base = display(name);
        Entity best = null;
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(8), e -> e instanceof Mannequin)) {
            if (entity.getCustomName() != null && entity.getCustomName().getString().contains(base)
                    && (best == null || entity.distanceToSqr(player) < best.distanceToSqr(player))) {
                best = entity;
            }
        }
        return best == null ? -1 : best.getId();
    }

    private static void give(ServerPlayer player, String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        ItemStack stack = SkyItems.create(def, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    // ---------------------------------------------------------------- the Wizard (Hub)

    static void wizard(ServerPlayer player) {
        int npc = npcNear(player, "Wizard");
        CardMenu.open(player, p -> {
            int stored = Rift.infusions(p);
            long next = Rift.nextInfusion(p);
            int level = SkyBlockXp.level(p);
            long bottles = Sacks.countAnywhere(p, "GRAND_EXP_BOTTLE");
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("Dimensional Infusions", next == 0 ? "Full" : "Next free one in " + SkyBlockTime.countdown(next), 0xFF55FF,
                    false, "", new ItemStack(Items.ENDER_EYE), "", List.of(), stored / (float) Rift.MAX_INFUSIONS, stored + "/" + Rift.MAX_INFUSIONS));
            cards.add(new CardView.Card("Enter the Rift", level >= Rift.LEVEL_NEEDED ? "Walk into the portal behind me." : "Needs SkyBlock Level "
                    + Rift.LEVEL_NEEDED + " (you're " + level + ")", level >= Rift.LEVEL_NEEDED ? 0x55FF55 : 0xFF5555, false, "",
                    new ItemStack(Items.AMETHYST_CLUSTER), "", List.of(), -1, ""));
            cards.add(new CardView.Card("Buy an infusion", "32 Grand Experience Bottles", 0xAA55FF, stored < Rift.MAX_INFUSIONS && bottles >= 32,
                    "Buy", icon("GRAND_EXP_BOTTLE"), "", List.of(Chip.item(icon("GRAND_EXP_BOTTLE"), 32, bottles)), -1, ""));
            return new CardView("RIFT", "Wizard", "The way into the Rift", npc, List.of(), cards,
                    "One free infusion every 4 hours, up to 3. It's used when you leave the Rift's tower.", 1, 34);
        }, (p, card) -> {
            if (card != 2) {
                return "";
            }
            if (Rift.infusions(p) >= Rift.MAX_INFUSIONS) {
                return "You can't hold more than " + Rift.MAX_INFUSIONS + " infusions.";
            }
            if (!Sacks.takeAnywhere(p, "GRAND_EXP_BOTTLE", 32)) {
                return "You need 32 Grand Experience Bottles.";
            }
            Rift.setInfusions(p, Rift.infusions(p) + 1);
            p.sendSystemMessage(Component.literal("You bought a Dimensional Infusion.").withStyle(ChatFormatting.LIGHT_PURPLE));
            return "";
        });
    }

    // ---------------------------------------------------------------- Enigma

    static void enigma(ServerPlayer player) {
        int npc = npcNear(player, "Enigma");
        CardMenu.open(player, p -> {
            int found = EnigmaSouls.found(p);
            int total = EnigmaSouls.total();
            boolean cloak = EnigmaSouls.hasCloak(p);
            int upgrades = EnigmaSouls.upgrades(p);
            int maxUpgrades = found / 4;
            long motes = Motes.get(p);
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("Enigma Cloak", cloak ? "+20 ф Rift Time for every soul you find" : "Claim it to make your souls count",
                    0xAA00AA, !cloak && motes >= 100, cloak ? "Claimed" : "Claim", icon("ENIGMA_CLOAK"), "", cloak ? List.of() : List.of(Chip.price("motes", 100, motes)),
                    -1, cloak ? "+" + 20 * found + "s" : ""));
            cards.add(new CardView.Card("Enigma Souls", found + " of " + total + " found", 0xFF55FF, false, "", new ItemStack(Items.AMETHYST_SHARD), "",
                    List.of(), total == 0 ? 0 : found / (float) total, ""));
            long cost = 250L * (upgrades + 1);
            boolean can = cloak && upgrades < maxUpgrades;
            cards.add(new CardView.Card("Cloak upgrade " + (upgrades + 1), can ? "+2 Speed and +4% Mana Regen in the Rift"
                    : upgrades >= maxUpgrades ? "Find " + (4 * (upgrades + 1) - found) + " more souls for the next one" : "Claim the cloak first",
                    0x55FFFF, can && motes >= cost, "Upgrade", new ItemStack(Items.FEATHER), "", can ? List.of(Chip.price("motes", cost, motes)) : List.of(),
                    -1, upgrades + " bought"));
            return new CardView("RIFT", "Enigma", "Enigma Souls", npc, List.of(Chip.balance("motes", motes)), cards,
                    "Souls glow violet all over the Rift. Right-click one to take it.", 1, 34);
        }, (p, card) -> {
            if (card == 0 && !EnigmaSouls.hasCloak(p)) {
                if (!Motes.take(p, 100)) {
                    return "You need 100 Motes.";
                }
                EnigmaSouls.setCloak(p, "cloak", 1);
                give(p, "ENIGMA_CLOAK", 1);
                p.sendSystemMessage(Component.literal("You claimed the Enigma Cloak!").withStyle(ChatFormatting.LIGHT_PURPLE));
                Msg.playSound(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.3f);
            } else if (card == 2) {
                int upgrades = EnigmaSouls.upgrades(p);
                if (!EnigmaSouls.hasCloak(p) || upgrades >= EnigmaSouls.found(p) / 4) {
                    return "Find more Enigma Souls first.";
                }
                if (!Motes.take(p, 250L * (upgrades + 1))) {
                    return "You need " + Msg.number(250L * (upgrades + 1)) + " Motes.";
                }
                EnigmaSouls.setCloak(p, "upgrades", upgrades + 1);
                p.sendSystemMessage(Component.literal("Your Enigma Cloak grows stronger.").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            return "";
        });
    }

    // ---------------------------------------------------------------- everyone else

    static void talk(ServerPlayer player, String name) {
        if (name.equals("Grumblefoot")) {
            com.google.gson.JsonObject params = new com.google.gson.JsonObject();
            params.addProperty("shop", "grumblefoot");
            com.skyrpg.core.action.Actions.run("skyblock_shop", player, params);
            return;
        }
        if (Porhtal.EYES.contains(name)) {
            player.sendSystemMessage(Component.literal("[NPC] " + name + ": ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(RiftFlags.has(player, "eye:" + name) ? "..." : "The eye glares at you. Calm it: hit it until it settles.")
                            .withStyle(ChatFormatting.WHITE)));
            return;
        }
        if (name.equals("Porhtal")) {
            Porhtal.open(player, npcNear(player, "Porhtal"));
            return;
        }
        if (name.equals("Sad Jacquelle")) {
            Montezuma.jacquelle(player, npcNear(player, "Sad Jacquelle"));
            return;
        }
        if (name.equals("Deer")) {
            boolean first = RiftFlags.set(player, "deer");
            player.sendSystemMessage(Component.literal("[NPC] Deer: ").withStyle(ChatFormatting.YELLOW).append(Component.literal(first
                    ? "You made it across. The Chateau's doors will open for you now. Mind the vampires."
                    : "The Chateau is open to you. Mind the vampires.").withStyle(ChatFormatting.WHITE)));
            return;
        }
        if (name.startsWith("Maddox the Slayer")) {
            VampireSlayer.maddox(player, npcNear(player, "Maddox"));
            return;
        }
        if (name.equals("Damia")) {
            ShenAuction.open(player, npcNear(player, "Damia"));
            return;
        }
        if (name.equals("Cowboy Nick")) {
            CowboyNick.open(player, npcNear(player, "Cowboy Nick"));
            return;
        }
        if (name.equals("Oruo the Almighty")) {
            SunGecko.oruo(player, npcNear(player, "Oruo"));
            return;
        }
        if (name.equals("Obo")) {
            Mountaintop.talkObo(player);
            return;
        }
        if (name.equals("Old Timer")) {
            Mountaintop.oldTimer(player);
            return;
        }
        if (name.equals("Madame Eleanor Q. Goldsworth X")) {
            Mountaintop.eleanor(player, npcNear(player, "Eleanor"));
            return;
        }
        if (name.startsWith("Shen ")) {
            Mountaintop.shen(player, name);
            return;
        }
        if (name.startsWith("Kat")) {
            Kat.open(player, npcNear(player, "Kat"));
            return;
        }
        if (name.equals("Reed")) {
            Reed.open(player, npcNear(player, "Reed"));
            return;
        }
        if (name.equals("Ubik Von Neumann")) {
            Ubik.open(player, npcNear(player, "Ubik"));
            return;
        }
        if (name.equals("Dr. Emmett")) {
            Mirrorverse.emmettOutside(player, npcNear(player, "Dr. Emmett"));
            return;
        }
        if (name.equals("Dr. Emmett (Mirrorverse)")) {
            Mirrorverse.emmettFinale(player, npcNear(player, "Dr. Emmett"));
            return;
        }
        if (name.startsWith("Dr. Emmett (Mirrorverse ")) {
            Mirrorverse.emmettInside(player, Integer.parseInt(name.replaceAll("\\D", "")), npcNear(player, "Dr. Emmett"));
            return;
        }
        if (name.equals("ttemme .rD")) {
            Mirrorverse.ttemme(player, npcNear(player, "ttemme"));
            return;
        }
        if (name.startsWith("Shania")) {
            Dreadfarm.shania(player, npcNear(player, "Shania"));
            return;
        }
        if (name.equals("Gunther")) {
            WestVillage.gunther(player, npcNear(player, "Gunther"));
            return;
        }
        if (name.equals("Chef") || name.equals("Chef (Rift)")) {
            WestVillage.chef(player, npcNear(player, "Chef"));
            return;
        }
        if (name.startsWith("Joey McPizza")) {
            WestVillage.joey(player);
            return;
        }
        if (name.startsWith("Protester")) {
            RiftQuests.protester(player, name);
            return;
        }
        if (name.equals("Barry")) {
            RiftQuests.barry(player, npcNear(player, "Barry"));
            return;
        }
        if (name.equals("Elise")) {
            Timecharms.elise(player, npcNear(player, "Elise"));
            return;
        }
        if (name.startsWith("Motes Grubber")) {
            grubber(player);
            return;
        }
        if (name.equals("Roy")) {
            soulSale(player, "Roy", "npc:Roy", 4_000, "DEADGEHOG_SPINE", 8, "Deadgehogs drop spines in the lagoon.");
            return;
        }
        if (name.equals("Fairylosopher")) {
            soulSale(player, "Fairylosopher", "npc:Fairylosopher", 10_000, "SPLATTER_CRUX", 32, "Splatters drop their crux around the Chateau.");
            return;
        }
        if (name.equals("Cryptosis")) {
            soulSale(player, "Cryptosis", "npc:Cryptosis", 15_000, "SCRIBE_CRUX", 10, "Scribes drop their crux in the Plaza's alleys.");
            return;
        }
        if (SHOPS.containsKey(name)) {
            shop(player, name);
            return;
        }
        List<String> lines = LINES.getOrDefault(display(name), List.of("..."));
        Map<String, Integer> heard = HEARD.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        int at = heard.getOrDefault(name, 0);
        player.sendSystemMessage(Component.literal("[NPC] " + display(name) + ": ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(lines.get(at % lines.size())).withStyle(ChatFormatting.WHITE)));
        heard.put(name, at + 1);
        // Tel Kar's whole story is worth an Enigma Soul.
        if (name.equals("Tel Kar") && at + 1 == lines.size()) {
            EnigmaSouls.find(player, "npc:Tel Kar");
        }
    }

    private static void shop(ServerPlayer player, String name) {
        int npc = npcNear(player, name);
        List<Sale> sales = SHOPS.get(name);
        CardMenu.open(player, p -> {
            long motes = Motes.get(p);
            List<CardView.Card> cards = new ArrayList<>();
            for (Sale sale : sales) {
                List<Chip> price = new ArrayList<>();
                if (sale.motes() > 0) {
                    price.add(Chip.price("motes", sale.motes(), motes));
                }
                if (sale.costItem() != null) {
                    price.add(Chip.item(icon(sale.costItem()), sale.costCount(), Sacks.countAnywhere(p, sale.costItem())));
                }
                ItemStack item = icon(sale.item());
                cards.add(CardView.Card.offer(item.isEmpty() ? item : item.copyWithCount(sale.count()), price));
            }
            return new CardView("RIFT", display(name), "Motes shop", npc, List.of(Chip.balance("motes", motes)), cards,
                    "Motes come from Rift mobs, mote orbs and the Motes Grubber.", 1, 32);
        }, (p, card) -> {
            Sale sale = sales.get(card);
            if (Motes.get(p) < sale.motes()) {
                return "You need " + Msg.number(sale.motes()) + " Motes.";
            }
            if (sale.costItem() != null && Sacks.countAnywhere(p, sale.costItem()) < sale.costCount()) {
                return "You need " + sale.costCount() + " " + icon(sale.costItem()).getHoverName().getString() + ".";
            }
            Motes.take(p, sale.motes());
            if (sale.costItem() != null) {
                Sacks.takeAnywhere(p, sale.costItem(), sale.costCount());
            }
            give(p, sale.item(), sale.count());
            p.sendSystemMessage(Component.literal("Bought " + icon(sale.item()).getHoverName().getString() + ".").withStyle(ChatFormatting.GREEN));
            return "";
        });
    }

    /** An NPC who sells an Enigma Soul (Roy: 4,000 Motes + 8 Deadgehog Spines; Cryptosis: 15,000 + 10 Scribe Crux). */
    private static void soulSale(ServerPlayer player, String name, String soul, long motesCost, String item, int count, String hint) {
        int npc = npcNear(player, name);
        CardMenu.open(player, p -> {
            long motes = Motes.get(p);
            boolean had = EnigmaSouls.has(p, soul);
            List<CardView.Card> cards = List.of(new CardView.Card(name + "'s Enigma Soul", had ? "You already bought it." : "Yours, for a price.",
                    0xFF55FF, !had, had ? "Sold" : "Buy", new ItemStack(Items.AMETHYST_SHARD), "", had ? List.of()
                    : List.of(Chip.price("motes", motesCost, motes), Chip.item(icon(item), count, Sacks.countAnywhere(p, item))), -1, ""));
            return new CardView("RIFT", name, "Enigma Soul", npc, List.of(Chip.balance("motes", motes)), cards, hint, 1, 34);
        }, (p, card) -> {
            if (EnigmaSouls.has(p, soul)) {
                return "";
            }
            if (Motes.get(p) < motesCost || Sacks.countAnywhere(p, item) < count) {
                return "You need " + Msg.number(motesCost) + " Motes and " + count + " " + icon(item).getHoverName().getString() + ".";
            }
            Motes.take(p, motesCost);
            Sacks.takeAnywhere(p, item, count);
            EnigmaSouls.find(p, soul);
            return "";
        });
    }

    /** The Motes Grubber eats the Rift items you carry, one kind at a time or everything. */
    private static void grubber(ServerPlayer player) {
        int npc = npcNear(player, "Motes Grubber");
        CardMenu.open(player, p -> {
            List<CardView.Card> cards = new ArrayList<>();
            long all = 0;
            for (Map.Entry<String, Long> e : GRUBBER.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                int have = count(p, e.getKey());
                long pay = have * e.getValue();
                all += pay;
                ItemStack item = icon(e.getKey());
                cards.add(new CardView.Card(item.isEmpty() ? e.getKey() : item.getHoverName().getString(), have == 0 ? "You have none"
                        : "You have " + have + "  ·  " + e.getValue() + " Motes each", 0xFFFFFF, have > 0, "Sell", item, "",
                        List.of(new Chip("motes", ItemStack.EMPTY, "+" + Msg.number(pay), true)), -1, ""));
            }
            cards.add(0, new CardView.Card("Feed it everything", all == 0 ? "Nothing to eat" : "Everything above", 0xFF55FF, all > 0, "Sell all",
                    new ItemStack(Items.GLISTERING_MELON_SLICE), "", List.of(new Chip("motes", ItemStack.EMPTY, "+" + Msg.number(all), true)), -1, ""));
            return new CardView("RIFT", "Motes Grubber", "Eats Rift items", npc, List.of(Chip.balance("motes", Motes.get(p))), cards,
                    "It only eats what came from the Rift.", 1, 30);
        }, (p, card) -> {
            List<String> ids = GRUBBER.keySet().stream().sorted().toList();
            List<String> eat = card == 0 ? ids : List.of(ids.get(card - 1));
            long paid = 0;
            for (String id : eat) {
                int have = count(p, id);
                if (have > 0 && Sacks.takeAnywhere(p, id, have)) {
                    paid += have * GRUBBER.get(id);
                }
            }
            if (paid > 0) {
                Motes.give(p, paid);
                p.sendSystemMessage(Component.literal("The Motes Grubber munches happily. +" + Msg.number(paid) + " Motes").withStyle(ChatFormatting.LIGHT_PURPLE));
                Msg.playSound(p, SoundEvents.GENERIC_EAT.value(), 1f, 0.8f);
            }
            return "";
        });
    }

    private static int count(ServerPlayer player, String id) {
        return (int) Math.min(Integer.MAX_VALUE, Sacks.countAnywhere(player, id));
    }
}
