package com.skyrpg.skyblock.garden;

import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Garden's Composter (Hypixel's numbers from NEU and the wiki): it turns 4,000 Organic Matter and 2,000 Fuel into a
 * Compost every 10 minutes, while you're away too. Organic Matter comes from crops (decided per item, enchanted ones 160
 * times that), Fuel from Biofuel (3,000), Oil Barrels and Volta (10,000). Five upgrades, 25 tiers each, for Copper and
 * an item: Speed (+20% a tier), Multi Drop (+3% for an extra Compost), Fuel Cap (100,000 + 30,000 a tier), Organic Matter
 * Cap (40,000 + 30,000 a tier) and Cost Reduction (-1% a tier).
 */
final class Composter {
    static final long BASE_MS = 10 * 60 * 1000L;
    static final double ORGANIC = 4000;
    static final double FUEL = 2000;
    static final List<String> UPGRADES = List.of("speed", "multi_drop", "fuel_cap", "organic_matter_cap", "cost_reduction");
    static final List<String> UPGRADE_NAMES = List.of("Composter Speed", "Multi Drop", "Fuel Cap", "Organic Matter Cap", "Cost Reduction");
    static final int[] COPPER = {100, 150, 200, 250, 300, 350, 400, 500, 600, 700, 800, 900, 1000, 1200, 1400, 1600, 1800, 2000, 2250, 2500, 2750,
            3000, 3300, 3600, 4000};
    private static final int[][] AMOUNTS = {
            {128, 2, 256, 4, 512, 8, 8, 16, 12, 32, 16, 40, 24, 48, 32, 64, 40, 96, 48, 128, 64, 192, 96, 256, 128},
            {1, 64, 2, 1, 4, 2, 8, 4, 16, 8, 32, 16, 48, 24, 64, 32, 80, 48, 112, 64, 144, 96, 176, 128, 224},
            {1, 4, 2, 8, 4, 16, 8, 32, 16, 40, 32, 48, 40, 64, 48, 96, 64, 128, 96, 192, 144, 288, 192, 384, 256},
            {1, 3, 2, 6, 4, 12, 7, 24, 10, 48, 16, 64, 24, 96, 32, 128, 40, 160, 48, 208, 64, 256, 96, 320, 128},
            {32, 1, 2, 2, 4, 4, 8, 8, 16, 16, 24, 32, 32, 48, 48, 64, 64, 80, 96, 112, 128, 160, 160, 224, 192}};

    /** Organic Matter per crop item (decided where Hypixel's wasn't found); enchanted crops are 160 of these. */
    static final Map<String, Double> ORGANIC_MATTER = Map.ofEntries(
            Map.entry("WHEAT", 1.0), Map.entry("SEEDS", 1.0), Map.entry("CARROT_ITEM", 0.29), Map.entry("POTATO_ITEM", 0.33),
            Map.entry("PUMPKIN", 1.0), Map.entry("MELON", 0.2), Map.entry("SUGAR_CANE", 0.5), Map.entry("CACTUS", 0.5),
            Map.entry("INK_SACK:3", 0.33), Map.entry("RED_MUSHROOM", 1.0), Map.entry("BROWN_MUSHROOM", 1.0), Map.entry("NETHER_STALK", 0.33),
            Map.entry("ENCHANTED_WHEAT", 160.0), Map.entry("ENCHANTED_CARROT", 46.4), Map.entry("ENCHANTED_POTATO", 52.8),
            Map.entry("ENCHANTED_PUMPKIN", 160.0), Map.entry("ENCHANTED_MELON", 32.0), Map.entry("ENCHANTED_SUGAR", 80.0),
            Map.entry("ENCHANTED_CACTUS_GREEN", 80.0), Map.entry("ENCHANTED_CACTUS", 12800.0), Map.entry("ENCHANTED_COCOA", 52.8), Map.entry("ENCHANTED_RED_MUSHROOM", 160.0),
            Map.entry("ENCHANTED_BROWN_MUSHROOM", 160.0), Map.entry("ENCHANTED_NETHER_STALK", 52.8));
    static final Map<String, Double> FUELS = Map.of("BIOFUEL", 3000.0, "OIL_BARREL", 10000.0, "VOLTA", 10000.0);

    private Composter() {
    }

    static void init() {
    }

    static int tier(GardenData.State state, String upgrade) {
        return state.composter.getOrDefault(upgrade, 0);
    }

    static double organicCap(GardenData.State state) {
        return 40_000 + 30_000.0 * tier(state, "organic_matter_cap");
    }

    static double fuelCap(GardenData.State state) {
        return 100_000 + 30_000.0 * tier(state, "fuel_cap");
    }

    static long cycleMs(GardenData.State state) {
        return (long) (BASE_MS / (1 + 0.2 * tier(state, "speed")));
    }

    static double cost(GardenData.State state, double base) {
        return base * (1 - 0.01 * tier(state, "cost_reduction"));
    }

    /** Runs the Composter up to now: one Compost per cycle while it has the Organic Matter and Fuel. */
    static void update(GardenData.State state, java.util.Random random) {
        long now = System.currentTimeMillis();
        if (state.composterAt == 0) {
            state.composterAt = now;
            return;
        }
        long cycle = cycleMs(state);
        while (now - state.composterAt >= cycle) {
            if (state.organic < cost(state, ORGANIC) || state.fuel < cost(state, FUEL)) {
                state.composterAt = now;
                return;
            }
            state.organic -= cost(state, ORGANIC);
            state.fuel -= cost(state, FUEL);
            state.compost += 1 + (random.nextDouble() < 0.03 * tier(state, "multi_drop") ? 1 : 0);
            state.composterAt += cycle;
        }
    }

    /** Puts every crop (or every fuel) the player carries into the Composter, up to its cap. Returns what went in. */
    static double insert(ServerPlayer player, GardenData.State state, boolean fuel) {
        Map<String, Double> values = fuel ? FUELS : ORGANIC_MATTER;
        double cap = fuel ? fuelCap(state) : organicCap(state);
        double added = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ItemDefinition def = SkyItems.definition(stack);
            Double value = def == null || def.skyblockId() == null ? null : values.get(def.skyblockId());
            if (value == null) {
                continue;
            }
            double have = fuel ? state.fuel : state.organic;
            int fits = (int) Math.min(stack.getCount(), Math.floor((cap - have) / value));
            if (fits <= 0) {
                continue;
            }
            stack.shrink(fits);
            double amount = fits * value;
            if (fuel) {
                state.fuel += amount;
            } else {
                state.organic += amount;
            }
            added += amount;
        }
        return added;
    }

    /** The Compost waiting, into the player's inventory. */
    static int collect(ServerPlayer player, GardenData.State state) {
        int count = state.compost;
        ItemDefinition def = ItemRegistry.skyblock("COMPOST");
        if (count <= 0 || def == null) {
            return 0;
        }
        int left = count;
        while (left > 0) {
            int n = Math.min(64, left);
            left -= n;
            ItemStack stack = SkyItems.create(def, n);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        state.compost = 0;
        return count;
    }

    /** An upgrade's next tier cost as lines (empty at 25). */
    static List<String> nextCost(GardenData.State state, int branch) {
        int tier = tier(state, UPGRADES.get(branch));
        List<String> out = new ArrayList<>();
        if (tier >= 25) {
            return out;
        }
        out.add(Msg.number(COPPER[tier]) + " Copper");
        out.add(AMOUNTS[branch][tier] + " " + itemName(item(branch, tier)));
        return out;
    }

    /** The item a branch's tier (0-based) asks for, as NEU lists them. */
    static String item(int branch, int tier) {
        boolean odd = tier % 2 == 0;
        return switch (branch) {
            case 0 -> odd ? (tier < 6 ? "ENCHANTED_WHEAT" : "ENCHANTED_HAY_BALE") : "ENCHANTED_GOLDEN_CARROT";
            case 1 -> odd ? "ENCHANTED_BAKED_POTATO" : tier == 1 ? "ENCHANTED_PUMPKIN" : "POLISHED_PUMPKIN";
            case 2 -> odd ? "ENCHANTED_SUGAR_CANE" : "ENCHANTED_MELON_BLOCK";
            case 3 -> odd ? "ENCHANTED_CACTUS" : "ENCHANTED_COOKIE";
            default -> tier == 0 ? "ENCHANTED_BROWN_MUSHROOM" : !odd ? "MUTANT_NETHER_STALK"
                    : tier % 4 == 2 ? "ENCHANTED_HUGE_MUSHROOM_2" : "ENCHANTED_HUGE_MUSHROOM_1";
        };
    }

    static String itemName(String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? id : def.name();
    }

    /** Buys the next tier of an upgrade: "" when done, else why not. */
    static String upgrade(ServerPlayer player, GardenData.State state, int branch) {
        String key = UPGRADES.get(branch);
        int tier = tier(state, key);
        if (tier >= 25) {
            return "That upgrade is maxed.";
        }
        String item = item(branch, tier);
        int amount = AMOUNTS[branch][tier];
        if (state.copper < COPPER[tier]) {
            return "You need " + Msg.number(COPPER[tier]) + " Copper.";
        }
        if (com.skyrpg.skyblock.sack.Sacks.countAnywhere(player, item) < amount) {
            return "You need " + amount + " " + itemName(item) + ".";
        }
        state.copper -= COPPER[tier];
        com.skyrpg.skyblock.sack.Sacks.takeAnywhere(player, item, amount);
        state.composter.put(key, tier + 1);
        player.sendSystemMessage(Component.literal(UPGRADE_NAMES.get(branch) + " upgraded to " + (tier + 1) + "!").withStyle(ChatFormatting.GREEN));
        return "";
    }
}
