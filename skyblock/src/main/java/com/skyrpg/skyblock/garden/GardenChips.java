package com.skyrpg.skyblock.garden;

import com.mojang.serialization.Codec;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Garden Chips (Hypixel's): ten chips, each redeemed from its item (right-click; sneak to redeem the whole stack). The
 * first redeem unlocks it at Rare, 5 make it Epic, 21 Legendary; rarity scales what each of its 20 levels gives. Levels
 * cost Sowdust, which harvesting Garden crops earns (one per crop, two for Sugar Cane and Cactus; decided). They work in
 * the Garden: Cropshot (Farming Fortune), Sowledge (Farming Wisdom), Vermin Vaporizer (Bonus Pest Chance), Overdrive
 * (Farming Fortune during Jacob's Contests), Quickdraw (visitors come sooner), Synthesis (more Copper); Hypercharge,
 * Mechamind, Evergreen and Rarefinder wait for what they boost (Farming Fortune buffs, tool experience, the Greenhouse).
 */
public final class GardenChips {
    /** A chip: its id, name, what a level gives at Rare, Epic and Legendary, and the stat's words. */
    record Kind(String id, String name, double rare, double epic, double legendary, String effect, String source) {
        double per(int rarity) {
            return rarity >= 3 ? legendary : rarity == 2 ? epic : rare;
        }
    }

    static final List<Kind> KINDS = List.of(
            new Kind("CROPSHOT", "Cropshot", 3, 4, 5, "Farming Fortune", "SkyMart, 500 Copper"),
            new Kind("SOWLEDGE", "Sowledge", 1, 1.25, 1.5, "Farming Wisdom", "SkyMart, 200 Copper"),
            new Kind("HYPERCHARGE", "Hypercharge", 3, 4, 5, "% stronger Farming Fortune buffs", "Garden visitors (2%)"),
            new Kind("QUICKDRAW", "Quickdraw", 1.5, 2, 2.5, "% shorter wait for visitors", "Garden visitors (2%)"),
            new Kind("MECHAMIND", "Mechamind", 1.5, 2, 2.5, "% tool experience", "Anita, 1 Gold Medal"),
            new Kind("OVERDRIVE", "Overdrive", 5, 6, 7, "Farming Fortune in Jacob's Contests", "Anita, 2 Gold Medals"),
            new Kind("SYNTHESIS", "Synthesis", 1, 1.5, 2, "% Copper", "the Greenhouse (later)"),
            new Kind("EVERGREEN", "Evergreen", 2, 2.5, 3, "% Greenhouse crops", "the Greenhouse (later)"),
            new Kind("VERMIN_VAPORIZER", "Vermin Vaporizer", 3, 4, 5, "Bonus Pest Chance", "pests (1%)"),
            new Kind("RAREFINDER", "Rarefinder", 1.5, 2, 2.5, "Overbloom", "harvesting crops (very rare)"));
    static final int MAX_LEVEL = 20;
    /** Sowdust for each level (Hypixel's: 25 million to max a chip). */
    static final long[] COST = {100_000, 100_000, 200_000, 300_000, 400_000, 550_000, 700_000, 850_000, 1_000_000, 1_150_000, 1_300_000,
            1_450_000, 1_600_000, 1_750_000, 1_900_000, 2_050_000, 2_200_000, 2_350_000, 2_500_000, 2_650_000};
    static final String[] RARITIES = {"", "Rare", "Epic", "Legendary"};
    static final int[] RARITY_COLORS = {0x777777, 0x5555FF, 0xAA00AA, 0xFFAA00};

    /** "<ID>:count" chips redeemed, "<ID>:level" levels, "sowdust". */
    static final AttachmentType<Map<String, Long>> DATA = AttachmentRegistry.create(SkyRpgSkyblock.id("garden_chips"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());

    private GardenChips() {
    }

    static void init() {
        // Ahead of SkyblockItemUse, which turns plain SkyBlock items' right-clicks away (on the client too).
        net.minecraft.resources.Identifier early = SkyRpgSkyblock.id("garden_chips_early");
        UseItemCallback.EVENT.addPhaseOrdering(early, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(early, (player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            ItemDefinition def = SkyItems.definition(held);
            if (def == null || def.skyblockId() == null || !def.skyblockId().endsWith("_GARDEN_CHIP") || def.skyblockId().startsWith("TUTORIAL")) {
                return InteractionResult.PASS;
            }
            if (!(player instanceof ServerPlayer server) || level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            String id = def.skyblockId().substring(0, def.skyblockId().length() - "_GARDEN_CHIP".length());
            Kind kind = KINDS.stream().filter(k -> k.id().equals(id)).findFirst().orElse(null);
            if (kind == null) {
                return InteractionResult.PASS;
            }
            int n = server.isShiftKeyDown() ? held.getCount() : 1;
            int before = rarity(server, kind);
            add(server, kind.id() + ":count", n);
            held.shrink(n);
            int after = rarity(server, kind);
            Notice.of(Notice.Kind.FARMING).tag("GARDEN CHIP").send(server, Component.literal(before == 0 ? kind.name() + " Chip unlocked!"
                    : after > before ? kind.name() + " Chip is now " + RARITIES[after] + "!" : "Redeemed " + n + " " + kind.name() + " Chip" + (n == 1 ? "" : "s") + ".")
                    .withColor(RARITY_COLORS[after]));
            Msg.playSound(server, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.2f + after * 0.2f);
            Stats.refresh(server);
            return InteractionResult.SUCCESS;
        });
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Garden Chips";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                if (!Garden.isGarden(player.level())) {
                    return;
                }
                out.add(StatTypes.FARMING_FORTUNE, value(player, "CROPSHOT") + (Contests.running() ? value(player, "OVERDRIVE") : 0));
                out.add(StatTypes.FARMING_WISDOM, value(player, "SOWLEDGE"));
                out.add(StatTypes.BONUS_PEST_CHANCE, value(player, "VERMIN_VAPORIZER"));
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("chips").executes(ctx -> {
            open(ctx.getSource().getPlayerOrException());
            return 1;
        })));
    }

    static long get(ServerPlayer player, String key) {
        return player.getAttachedOrElse(DATA, Map.of()).getOrDefault(key, 0L);
    }

    static void add(ServerPlayer player, String key, long amount) {
        Map<String, Long> map = new HashMap<>(player.getAttachedOrElse(DATA, Map.of()));
        map.merge(key, amount, Long::sum);
        player.setAttached(DATA, Map.copyOf(map));
    }

    static int rarity(ServerPlayer player, Kind kind) {
        long count = get(player, kind.id() + ":count");
        return count >= 21 ? 3 : count >= 5 ? 2 : count >= 1 ? 1 : 0;
    }

    static int level(ServerPlayer player, String id) {
        return (int) get(player, id + ":level");
    }

    /** What a chip gives this player now. */
    static double value(ServerPlayer player, String id) {
        Kind kind = KINDS.stream().filter(k -> k.id().equals(id)).findFirst().orElseThrow();
        int rarity = rarity(player, kind);
        return rarity == 0 ? 0 : kind.per(rarity) * level(player, id);
    }

    /** Sowdust for a harvest: a grain per crop, two for Sugar Cane and Cactus. */
    static void harvested(ServerPlayer player, Garden.Crop crop, long count) {
        add(player, "sowdust", count * (crop.key().equals("sugar_cane") || crop.key().equals("cactus") ? 2 : 1));
        if (player.getRandom().nextDouble() < 0.000_005) {
            give(player, "RAREFINDER_GARDEN_CHIP");
            player.sendSystemMessage(Component.literal("RARE DROP! Rarefinder Chip").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    static void give(ServerPlayer player, String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        ItemStack stack = SkyItems.create(def, 1);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    /** The Garden Chips screen: every chip, its rarity and level, and the Sowdust to level it. */
    static void open(ServerPlayer player) {
        CardMenu.open(player, p -> {
            long sowdust = get(p, "sowdust");
            List<CardView.Card> cards = new ArrayList<>();
            for (Kind kind : KINDS) {
                int rarity = rarity(p, kind);
                int level = level(p, kind.id());
                ItemDefinition def = ItemRegistry.skyblock(kind.id() + "_GARDEN_CHIP");
                ItemStack icon = def == null ? new ItemStack(Items.PAPER) : SkyItems.create(def, 1);
                boolean maxed = level >= MAX_LEVEL;
                long cost = maxed ? 0 : COST[level];
                String detail = rarity == 0 ? "Redeem one to unlock  ·  " + kind.source()
                        : "+" + fmt(kind.per(rarity)) + " " + kind.effect() + " a level  ·  " + RARITIES[rarity] + " (" + get(p, kind.id() + ":count") + " redeemed)";
                List<Chip> price = rarity == 0 || maxed ? List.of() : List.of(new Chip("", ItemStack.EMPTY, Msg.compact(cost) + " Sowdust", sowdust >= cost));
                cards.add(new CardView.Card(kind.name() + " Chip", detail, RARITY_COLORS[rarity], rarity > 0 && !maxed && sowdust >= cost,
                        rarity == 0 ? "Locked" : maxed ? "Max" : "Level up", icon, "", price, level / (float) MAX_LEVEL,
                        rarity == 0 ? "" : "Lv " + level + "/" + MAX_LEVEL + (level > 0 ? "  ·  +" + fmt(kind.per(rarity) * level) : "")));
            }
            return new CardView("FARMING", "Garden Chips", "Sowdust from Garden crops", -1,
                    List.of(new Chip("", ItemStack.EMPTY, Msg.number(sowdust) + " Sowdust", true)), cards,
                    "Right-click a chip to redeem it (sneak for the stack): 5 make it Epic, 21 Legendary.", 1, 40);
        }, (p, card) -> {
            Kind kind = KINDS.get(card);
            int rarity = rarity(p, kind);
            int level = level(p, kind.id());
            if (rarity == 0 || level >= MAX_LEVEL) {
                return "";
            }
            long cost = COST[level];
            if (get(p, "sowdust") < cost) {
                return "You need " + Msg.number(cost) + " Sowdust.";
            }
            add(p, "sowdust", -cost);
            add(p, kind.id() + ":level", 1);
            Stats.refresh(p);
            Msg.playSound(p, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.5f);
            return "";
        });
    }

    private static String fmt(double v) {
        return v % 1 == 0 ? String.valueOf((long) v) : String.valueOf(Math.round(v * 100) / 100.0);
    }
}
