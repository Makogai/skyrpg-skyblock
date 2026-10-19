package com.skyrpg.skyblock;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;

/** Dying, as on Hypixel: you keep everything you carry, but lose half the coins in your purse (the bank is safe; a Booster Cookie or a Phoenix pet keeps them, Bank enchants keep part). */
final class Deaths {
    private Deaths() {
    }

    static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(Deaths::keepInventory);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                losePurse(player);
            }
        });
    }

    private static void keepInventory(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        if (!rules.get(GameRules.KEEP_INVENTORY)) {
            rules.set(GameRules.KEEP_INVENTORY, true, server);
            SkyRpgSkyblock.LOGGER.info("Turned keepInventory on: SkyBlock deaths cost coins, not items");
        }
    }

    private static void losePurse(ServerPlayer player) {
        // The Booster Cookie's buff keeps your coins.
        if (com.skyrpg.skyblock.cookie.BoosterCookie.active(player) || com.skyrpg.skyblock.pet.PetAbilities.has(player, "keep_coins")) {
            return;
        }
        long purse = Wallet.balance(player);
        // Bank on your armour keeps part of what you'd lose (100% in total keeps it all).
        long lost = Math.round(purse / 2 * (1 - com.skyrpg.skyblock.enchant.UltimateEnchants.bankSaved(player)));
        if (lost > 0 && Wallet.take(player, lost)) {
            player.sendSystemMessage(Component.literal("You died and lost " + Msg.number(lost) + " coins!").withStyle(ChatFormatting.RED));
        }
    }
}
