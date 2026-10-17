package com.skyrpg.skyblock.dungeon;

import com.skyrpg.core.event.ItemGains;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.essence.Essence;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a secret gives (Hypixel's secrets hold dungeon consumables and essence): Undead or Wither Essence most of the
 * time, else a Healing Tissue, a Spirit Leap, a Dungeon Trap, a Superboom TNT, Training Weights, or rarely a Revive Stone
 * or Dungeon Chest Key. Our weights.
 */
final class Secrets {
    private Secrets() {
    }

    static void reward(ServerPlayer player, Run run) {
        var random = player.getRandom();
        int roll = random.nextInt(100);
        if (roll < 55) {
            String kind = random.nextInt(3) == 0 ? "WITHER" : "UNDEAD";
            int amount = (1 + random.nextInt(3)) * (1 + run.floor.number());
            Essence.give(player, kind, amount);
            player.sendSystemMessage(Component.literal("ESSENCE! ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                    .append(Component.literal("You found " + amount + " " + Essence.name(kind) + "!").withColor(Essence.color(kind))));
            return;
        }
        String[] items = {"HEALING_TISSUE", "HEALING_TISSUE", "SPIRIT_LEAP", "SPIRIT_LEAP", "DUNGEON_TRAP", "SUPERBOOM_TNT", "TRAINING_WEIGHTS"};
        String id = roll >= 98 ? (random.nextBoolean() ? "REVIVE_STONE" : "DUNGEON_CHEST_KEY") : items[random.nextInt(items.length)];
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def != null) {
            var stack = SkyItems.create(def, 1);
            Component name = stack.getHoverName();
            ItemGains.give(player, stack, ItemGains.Source.OTHER);
            player.sendSystemMessage(Component.literal("SECRET! ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                    .append(Component.literal("You found ").withStyle(ChatFormatting.GRAY)).append(name));
        }
    }
}
