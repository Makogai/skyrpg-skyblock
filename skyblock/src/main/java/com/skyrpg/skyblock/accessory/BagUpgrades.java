package com.skyrpg.skyblock.accessory;

import com.mojang.serialization.Codec;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * The Accessory Bag's extra slots besides the Redstone collection's: the Redstone Miner's four (one for 128, 256, 512
 * and 1,024 Redstone, once you have Redstone II) and Jacobus's 198 (two slots a purchase, 99 purchases, for coins: 1.5M,
 * then 5M, 8M, 12M and 20M by how many you've bought). Each upgrade is 2 SkyBlock XP. Jacobus hands over his Register
 * after the fifth purchase. Also the Rift Prism's imbuing (200,000 coins for 11 Magical Power).
 */
public final class BagUpgrades {
    public static final int MINER_STEPS = 4;
    private static final int[] MINER_REDSTONE = {128, 256, 512, 1024};
    public static final int JACOBUS_MAX = 99;
    /** Coins for the Rift Prism's imbuing, and the Magical Power it gives. */
    public static final long IMBUE_COST = 200_000;
    public static final int IMBUE_POWER = 11;
    public static final String XP_REASON = "Accessory Bag Upgrades";

    private static final String MINER = "miner";
    private static final String JACOBUS = "jacobus";
    private static final String PRISM = "rift_prism";

    static final AttachmentType<Map<String, Integer>> UPGRADES = AttachmentRegistry.create(SkyRpgSkyblock.id("accessory_bag_upgrades"),
            builder -> builder.initializer(Map::of).persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    private BagUpgrades() {
    }

    static void init() {
        UPGRADES.identifier();
    }

    /** How many of the Redstone Miner's slots the player has. */
    public static int miner(ServerPlayer player) {
        return Math.min(MINER_STEPS, player.getAttachedOrElse(UPGRADES, Map.of()).getOrDefault(MINER, 0));
    }

    /** How many times they've bought from Jacobus. */
    public static int jacobus(ServerPlayer player) {
        return Math.min(JACOBUS_MAX, player.getAttachedOrElse(UPGRADES, Map.of()).getOrDefault(JACOBUS, 0));
    }

    /** Whether the Rift Prism has been imbued. */
    public static boolean imbued(ServerPlayer player) {
        return player.getAttachedOrElse(UPGRADES, Map.of()).getOrDefault(PRISM, 0) > 0;
    }

    /** The Redstone the Miner wants next, 0 when he's done. */
    public static int minerNeeds(ServerPlayer player) {
        int steps = miner(player);
        return steps >= MINER_STEPS ? 0 : MINER_REDSTONE[steps];
    }

    /** Jacobus's price for the purchase after {@code bought} of them. */
    public static long jacobusCost(int bought) {
        int number = bought + 1;
        return number == 1 ? 1_500_000 : number <= 5 ? 5_000_000 : number <= 10 ? 8_000_000 : number <= 20 ? 12_000_000 : 20_000_000;
    }

    private static void set(ServerPlayer player, String key, int value) {
        Map<String, Integer> map = new HashMap<>(player.getAttachedOrElse(UPGRADES, Map.of()));
        map.put(key, value);
        player.setAttached(UPGRADES, Map.copyOf(map));
    }

    private static void say(ServerPlayer player, String text, ChatFormatting color) {
        player.sendOverlayMessage(Component.literal(text).withStyle(color));
    }

    /** The Redstone Miner takes his Redstone and gives a slot. */
    static boolean giveRedstone(ServerPlayer player) {
        int steps = miner(player);
        if (steps >= MINER_STEPS) {
            say(player, "The Redstone Miner has nothing more to give you.", ChatFormatting.GRAY);
            return false;
        }
        if (com.skyrpg.core.progress.CollectionLevels.tier(player, com.skyrpg.skyblock.recipe.Recipes.collectionId("REDSTONE")) < 2) {
            say(player, "Reach Redstone collection II first, then talk to me again!", ChatFormatting.RED);
            return false;
        }
        int need = MINER_REDSTONE[steps];
        if (!SkyStacks.take(player.getInventory(), "REDSTONE", need)) {
            say(player, "Come back with " + need + " Redstone!", ChatFormatting.RED);
            return false;
        }
        set(player, MINER, steps + 1);
        SkyBlockXp.give(player, 2, XP_REASON);
        player.sendSystemMessage(Component.literal("You got an extra Accessory Bag slot from helping the Redstone Miner!").withStyle(ChatFormatting.GRAY));
        com.skyrpg.core.stats.Stats.refresh(player);
        return true;
    }

    /** Jacobus sells two slots for coins. */
    static boolean buySlots(ServerPlayer player) {
        int bought = jacobus(player);
        if (bought >= JACOBUS_MAX) {
            say(player, "You have every slot Jacobus sells.", ChatFormatting.GRAY);
            return false;
        }
        long cost = jacobusCost(bought);
        if (!Wallet.take(player, cost)) {
            say(player, "You need " + com.skyrpg.core.text.Msg.number(cost) + " coins.", ChatFormatting.RED);
            return false;
        }
        set(player, JACOBUS, bought + 1);
        SkyBlockXp.give(player, 2, XP_REASON);
        player.sendSystemMessage(Component.literal("Jacobus added 2 slots to your Accessory Bag!").withStyle(ChatFormatting.GREEN));
        if (bought + 1 == 5) {
            ItemDefinition register = ItemRegistry.skyblock("JACOBUS_REGISTER");
            if (register != null) {
                ItemStack stack = SkyItems.create(register, 1);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
                player.sendSystemMessage(Component.literal("Jacobus gave you his Register.").withStyle(ChatFormatting.GOLD));
            }
        }
        com.skyrpg.core.stats.Stats.refresh(player);
        return true;
    }

    /** Imbues the Rift Prism from the inventory: it's used up for good, and gives 11 Magical Power. */
    static boolean imbue(ServerPlayer player) {
        if (imbued(player)) {
            say(player, "Your Rift Prism is already imbued.", ChatFormatting.GRAY);
            return false;
        }
        if (SkyStacks.count(player.getInventory(), "RIFT_PRISM") < 1) {
            say(player, "Bring a Rift Prism in your inventory.", ChatFormatting.RED);
            return false;
        }
        if (!Wallet.take(player, IMBUE_COST)) {
            say(player, "You need " + com.skyrpg.core.text.Msg.number(IMBUE_COST) + " coins.", ChatFormatting.RED);
            return false;
        }
        SkyStacks.take(player.getInventory(), "RIFT_PRISM", 1);
        set(player, PRISM, 1);
        player.sendSystemMessage(Component.literal("The Rift Prism is imbued: +" + IMBUE_POWER + " Magical Power for good!").withStyle(ChatFormatting.GOLD));
        AccessoryBag.rewardPower(player);
        com.skyrpg.core.stats.Stats.refresh(player);
        return true;
    }
}
