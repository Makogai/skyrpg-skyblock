package com.skyrpg.skyblock.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.essence.Essence;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Dungeon items (Hypixel's {@code dungeon_item}): in the Catacombs their stats go up by 10% for each star and 1% for each
 * Catacombs level. Stars (1 to 5) are bought from Malik with essence (and coins or items, from Hypixel's
 * {@code upgrade_costs} on the item).
 */
public final class DungeonItems {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET};

    private DungeonItems() {
    }

    static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Dungeon Items";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                if (!player.level().dimension().equals(Catacombs.LEVEL)) {
                    return;
                }
                int catacombs = DungeonXp.level(player, DungeonXp.CATACOMBS);
                for (EquipmentSlot slot : SLOTS) {
                    ItemStack stack = player.getItemBySlot(slot);
                    ItemDefinition def = SkyItems.definition(stack);
                    if (!ItemMods.dungeonItem(def)) {
                        continue;
                    }
                    double bonus = 0.10 * ItemMods.stars(stack) + 0.01 * catacombs;
                    for (Map.Entry<StatType, Double> stat : ItemMods.stats(stack, def).entrySet()) {
                        out.add(stat.getKey(), stat.getValue() * bonus);
                    }
                }
            }
        });
        com.skyrpg.core.action.Actions.register("dungeon_malik", (player, params) -> MalikMenu.open(player));
    }

    /** Hypixel's star costs for this item, star by star (each a list of costs). */
    static JsonArray costs(ItemDefinition def) {
        if (def == null || !def.json().has("hypixel")) {
            return new JsonArray();
        }
        JsonObject hypixel = def.json().getAsJsonObject("hypixel");
        return hypixel.has("upgrade_costs") ? hypixel.getAsJsonArray("upgrade_costs") : new JsonArray();
    }

    static int maxStars(ItemDefinition def) {
        return Math.min(5, costs(def).size());
    }

    /** One cost as text: "100 Wither Essence", "25,000 Coins", "Item x2". */
    static String costText(JsonObject cost) {
        String type = cost.get("type").getAsString();
        int amount = cost.has("amount") ? cost.get("amount").getAsInt() : 1;
        return switch (type) {
            case "ESSENCE" -> amount + " " + Essence.name(cost.get("essence_type").getAsString());
            case "COINS" -> Msg.number(amount) + " Coins";
            default -> {
                String id = cost.has("item_id") ? cost.get("item_id").getAsString() : "?";
                ItemDefinition item = ItemRegistry.skyblock(id);
                yield (item == null ? id : item.name()) + (amount > 1 ? " x" + amount : "");
            }
        };
    }

    /** The next star's costs as lines (empty when it's maxed or not upgradable). */
    static List<String> nextCost(ItemStack stack) {
        ItemDefinition def = SkyItems.definition(stack);
        JsonArray costs = costs(def);
        int stars = ItemMods.stars(stack);
        List<String> out = new ArrayList<>();
        if (stars < maxStars(def)) {
            for (JsonElement cost : costs.get(stars).getAsJsonArray()) {
                out.add(costText(cost.getAsJsonObject()));
            }
        }
        return out;
    }

    private static boolean has(ServerPlayer player, JsonObject cost) {
        String type = cost.get("type").getAsString();
        int amount = cost.has("amount") ? cost.get("amount").getAsInt() : 1;
        return switch (type) {
            case "ESSENCE" -> Essence.get(player, cost.get("essence_type").getAsString()) >= amount;
            case "COINS" -> Wallet.balance(player) >= amount;
            default -> SkyStacks.count(player.getInventory(), cost.has("item_id") ? cost.get("item_id").getAsString() : "") >= amount;
        };
    }

    private static void take(ServerPlayer player, JsonObject cost) {
        String type = cost.get("type").getAsString();
        int amount = cost.has("amount") ? cost.get("amount").getAsInt() : 1;
        switch (type) {
            case "ESSENCE" -> Essence.take(player, cost.get("essence_type").getAsString(), amount);
            case "COINS" -> Wallet.take(player, amount);
            default -> SkyStacks.take(player.getInventory(), cost.get("item_id").getAsString(), amount);
        }
    }

    /** Adds a star to the held item: "" when done, else why not. */
    static String upgrade(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        ItemDefinition def = SkyItems.definition(stack);
        if (def == null || maxStars(def) == 0) {
            return "Hold a dungeon item that takes stars.";
        }
        int stars = ItemMods.stars(stack);
        if (stars >= maxStars(def)) {
            return "This item already has every star.";
        }
        List<JsonObject> costs = new ArrayList<>();
        for (JsonElement cost : costs(def).get(stars).getAsJsonArray()) {
            costs.add(cost.getAsJsonObject());
        }
        for (JsonObject cost : costs) {
            if (!has(player, cost)) {
                return "You need " + costText(cost) + "!";
            }
        }
        costs.forEach(cost -> take(player, cost));
        ItemMods.setStars(stack, stars + 1);
        player.sendSystemMessage(Component.literal("You upgraded your ").withStyle(ChatFormatting.GREEN).append(stack.getHoverName())
                .append(Component.literal("!").withStyle(ChatFormatting.GREEN)));
        Msg.playSound(player, SoundEvents.ANVIL_USE, 0.8f, 1.2f);
        Stats.refresh(player);
        return "";
    }

    static String essenceLine(ServerPlayer player) {
        StringBuilder out = new StringBuilder();
        for (String kind : List.of("WITHER", "UNDEAD", "DRAGON", "SPIDER", "GOLD", "DIAMOND", "ICE", "CRIMSON")) {
            long have = Essence.get(player, kind);
            if (have > 0) {
                out.append(out.isEmpty() ? "" : "   ").append(kind.charAt(0)).append(kind.substring(1).toLowerCase(Locale.ROOT)).append(" ")
                        .append(Msg.number(have));
            }
        }
        return out.isEmpty() ? "No essence yet" : out.toString();
    }
}
