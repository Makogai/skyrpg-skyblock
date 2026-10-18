package com.skyrpg.skyblock.galatea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Galatea's people: Hina's welcome, Auryon (your first lasso) and Jaeger (your first fishing net), David Hunterborough
 * (the Hunting Box and the Hunting Toolkit), Rosemary (Forest Essence), Agatha (Starlyn Contests), the shops (Amaury's
 * axes and Canopy armour, Nemo's snorkeling gear, Sawyer's booster) and everyone else's lines. The Hunting Box in the Lodge opens your box too.
 */
public final class GalateaNpcs {
    static final AttachmentType<List<String>> FLAGS = AttachmentRegistry.create(SkyRpgSkyblock.id("galatea_flags"),
            builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
    private static final Map<String, String> SHOPS = Map.of("Amaury", "amaury", "Nemo", "nemo", "Sawyer", "sawyer");
    private static final Map<String, List<String>> LINES = Map.ofEntries(
            Map.entry("Auryon", List.of("Throw the lasso on a critter and keep your eyes on it. Look away and it slips free.",
                    "Watch its stamina drain. When I say REEL, right-click again. Not before!",
                    "Rarer critters have more stamina to wear down. Better lassos tire them faster: Vinesap makes better lassos.")),
            Map.entry("Jaeger", List.of("Look at a fish, swing the net. Simple as that.",
                    "Some critters are too quick for a Basic net: Lumisquid, Salamanders, Bullfrogs. A Medium net or better will do.",
                    "The Loch and the Shallows are full of them. The Wetlands too, if you don't mind the mud.")),
            Map.entry("Kiara", List.of("Fresh from the Reaches! Well, fresh-ish.", "Lushlilac grows by the water. Some say it charms the critters.")),
            Map.entry("Oden", List.of("The library keeps every shard's story. Three hundred and twenty-five, at last count.",
                    "Shards of one family fuse best. Read before you fuse.")),
            Map.entry("Alan", List.of("They say a dragon lives up north. A jade one.", "I'd stay away from the Bogged after dark. Or any time.")));

    /** Hina's guide: a title and a line each. */
    private static final List<String[]> HINA = List.of(
            new String[] {"Fig trees", "On the Evergreen Plateau. Cut the trunk first, then the branches."},
            new String[] {"Mangroves", "In the Loch. Branches, then the trunk, then the roots."},
            new String[] {"Tree Gifts", "Cut a share of a tree for Essence, Whispers and more."},
            new String[] {"Throw your axe", "Right-click with an axe to throw it at a tree."},
            new String[] {"Hunting", "Auryon: lassos. Jaeger: nets. David: the Hunting Box."},
            new String[] {"Starlyn Contests", "Agatha, on the Loch's pier, scores all of it daily."});

    private static final Map<UUID, Map<String, Integer>> HEARD = new HashMap<>();
    private static Vec3 huntingBox;

    private GalateaNpcs() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("galatea_spots"), "skyrpg/spawn_spots", GalateaNpcs::load);
        Actions.register("galatea_npc", (player, params) -> talk(player, params.get("name").getAsString()));
        Identifier first = SkyRpgSkyblock.id("galatea_first");
        UseBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(first, (player, level, hand, hit) -> {
            if (!Galatea.isGalatea(level) || !level.getBlockState(hit.getBlockPos()).is(Blocks.BARREL)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            BlockPos pos = hit.getBlockPos();
            if (player instanceof ServerPlayer server && huntingBox != null && Vec3.atBottomCenterOf(pos).distanceTo(huntingBox) < 2.5) {
                Hunting.open(server, 0);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    private static void load(Map<Identifier, JsonElement> files) {
        huntingBox = null;
        files.forEach((id, element) -> {
            JsonObject root = element.getAsJsonObject();
            if (!root.has("dimension") || !root.get("dimension").getAsString().equals(Galatea.LEVEL.identifier().toString())) {
                return;
            }
            JsonObject spot = root.getAsJsonObject("spots").getAsJsonObject("hunting_box");
            if (spot != null) {
                var pos = spot.getAsJsonArray("pos");
                huntingBox = new Vec3(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble());
            }
        });
    }

    static boolean has(ServerPlayer player, String flag) {
        return player.getAttachedOrElse(FLAGS, List.of()).contains(flag);
    }

    /** Sets a flag; true if it wasn't set before. */
    static boolean set(ServerPlayer player, String flag) {
        List<String> flags = new ArrayList<>(player.getAttachedOrElse(FLAGS, List.of()));
        if (flags.contains(flag)) {
            return false;
        }
        flags.add(flag);
        player.setAttached(FLAGS, List.copyOf(flags));
        return true;
    }

    private static void say(ServerPlayer player, String name, String line) {
        player.sendSystemMessage(Component.literal("[NPC] " + name + ": ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    private static void give(ServerPlayer player, String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        ItemStack stack = SkyItems.create(def, 1);
        // the name first: adding the stack to the inventory empties it
        Component name = stack.getHoverName().copy();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        player.sendSystemMessage(Component.literal("You received ").withStyle(ChatFormatting.GRAY)
                .append(name).append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.ITEM_PICKUP, 1f, 1f);
    }

    static void talk(ServerPlayer player, String name) {
        if (SHOPS.containsKey(name)) {
            JsonObject params = new JsonObject();
            params.addProperty("shop", SHOPS.get(name));
            Actions.run("skyblock_shop", player, params);
            return;
        }
        if (name.equals("David Hunterborough")) {
            david(player);
            return;
        }
        if (name.equals("Rosemary")) {
            Rosemary.open(player);
            return;
        }
        if (name.equals("Hina")) {
            hina(player);
            return;
        }
        if (name.equals("Kysha")) {
            Fusion.open(player);
            return;
        }
        if (name.equals("Dax")) {
            Huntraps.dax(player);
            return;
        }
        if (name.equals("Jade Dragon")) {
            jadeDragon(player);
            return;
        }
        if (name.equals("Agatha")) {
            Starlyn.open(player);
            return;
        }
        if (name.equals("Auryon") && set(player, "lasso")) {
            say(player, name, "A new hunter! Take this lasso. Find a critter in the woods and throw it.");
            give(player, "ABYSMAL_LASSO");
            return;
        }
        if (name.equals("Jaeger") && set(player, "net")) {
            say(player, name, "You'll want a net for the water critters. Here, this one's yours.");
            give(player, "BASIC_FISHING_NET");
            return;
        }
        List<String> lines = LINES.getOrDefault(name, List.of("..."));
        Map<String, Integer> heard = HEARD.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
        int at = heard.getOrDefault(name, 0);
        say(player, name, lines.get(at % lines.size()));
        heard.put(name, at + 1);
    }

    /** Hina: the Heart of the Forest, and her guide to Galatea as cards. */
    private static void hina(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Hina");
        CardMenu.open(player, p -> {
            Hotf.State state = Hotf.state(p);
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("Heart of the Forest", "Tier " + Hotf.tier(state.xp()) + "  ·  " + com.skyrpg.core.text.Msg.number(state.whispers(Hotf.FOREST))
                    + " Forest Whispers. Perks for Sweep, Fortune and more.", 0x55FF55, true, "Open",
                    new ItemStack(net.minecraft.world.item.Items.OAK_SAPLING), "", List.of(), -1, ""));
            for (String[] tip : HINA) {
                cards.add(new CardView.Card(tip[0], tip[1], 0x9BE07A, false, "", new ItemStack(net.minecraft.world.item.Items.AZALEA_LEAVES), "",
                        List.of(), -1, ""));
            }
            return new CardView("GALATEA", "Hina", "Welcome to Galatea", npc, List.of(), cards,
                    "Tree Gifts and Starlyn Contests raise your Heart of the Forest.", 1, 34);
        }, (p, card) -> {
            if (card == 0) {
                HotfMenu.open(p);
                return null;
            }
            return "";
        });
    }

    /** The Jade Dragon sells its own kind: an egg that hatches at level 100 (decided: 25M coins, Heart of the Forest 3). */
    private static void jadeDragon(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Jade Dragon");
        long price = 25_000_000;
        CardMenu.open(player, p -> {
            boolean tier = Hotf.tier(p) >= 3;
            long coins = com.skyrpg.economy.Purse.get(p);
            ItemStack egg = com.skyrpg.skyblock.pet.SkyBlockPets.create("JADE_DRAGON", "LEGENDARY", 0);
            List<CardView.Card> cards = List.of(
                    new CardView.Card("Jade Dragon", "A Foraging pet. It starts as an egg and hatches at level 100.", 0xFFAA00, tier && coins >= price,
                            tier ? "Buy" : "Locked", egg, "", List.of(com.skyrpg.skyblock.ui.Chip.price("coins", price, coins)), -1, ""),
                    new CardView.Card(tier ? "You may buy" : "Heart of the Forest 3", tier ? "The dragon deems you worthy." : "Come back when the Forest knows your name.",
                            tier ? 0x55FF55 : 0xFF5555, false, "", new ItemStack(net.minecraft.world.item.Items.OAK_SAPLING), "", List.of(), -1, ""));
            return new CardView("GALATEA", "Jade Dragon", "Dragon's Lair", npc, List.of(), cards, "Hmm. A visitor. Few climb this far.", 1, 40);
        }, (p, card) -> {
            if (card != 0) {
                return "";
            }
            if (Hotf.tier(p) < 3) {
                return "Reach Heart of the Forest 3 first.";
            }
            if (!com.skyrpg.economy.Purse.trySpend(p, price)) {
                return "You need " + com.skyrpg.core.text.Msg.number(price) + " coins.";
            }
            ItemStack egg = com.skyrpg.skyblock.pet.SkyBlockPets.create("JADE_DRAGON", "LEGENDARY", 0);
            if (!p.getInventory().add(egg)) {
                p.drop(egg, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            p.sendSystemMessage(Component.literal("You bought a Jade Dragon!").withStyle(ChatFormatting.GOLD));
            Msg.playSound(p, SoundEvents.ENDER_DRAGON_GROWL, 0.5f, 1.4f);
            return "";
        });
    }

    /** David Hunterborough: your Hunting Box, your Hunting level and his Toolkit. */
    private static void david(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "David");
        CardMenu.open(player, p -> {
            int level = SkillLevels.level(p, SkillLevels.HUNTING);
            long kinds = Hunting.SHARDS.keySet().stream().filter(k -> Hunting.box(p, k) > 0).count();
            List<CardView.Card> cards = List.of(
                    new CardView.Card("Hunting Box", kinds + " kinds of shard waiting. Syphon them into attributes.", 0x7FE0B4, true, "Open",
                            new ItemStack(net.minecraft.world.item.Items.BARREL), "", List.of(), -1, ""),
                    new CardView.Card("Hunting " + level, "Catch shards with the lasso, the net or Charm Chance to level up.", 0x9BE07A, false, "",
                            new ItemStack(net.minecraft.world.item.Items.LEAD), "", List.of(), -1, ""),
                    new CardView.Card("Shop", "The Hunting Toolkit: holds your lassos, nets and traps.", 0xEEEEEE, true, "Browse",
                            new ItemStack(net.minecraft.world.item.Items.BUNDLE), "", List.of(), -1, ""));
            return new CardView("GALATEA", "David Hunterborough", "Hunting Lodge", npc, List.of(), cards,
                    "Every shard you catch lands in your Hunting Box.", 1, 40);
        }, (p, card) -> {
            if (card == 0) {
                Hunting.open(p, 0);
            } else if (card == 2) {
                JsonObject params = new JsonObject();
                params.addProperty("shop", "david_hunterborough");
                Actions.run("skyblock_shop", p, params);
            }
            return "";
        });
    }
}
