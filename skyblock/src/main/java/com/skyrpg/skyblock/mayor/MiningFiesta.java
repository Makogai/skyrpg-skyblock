package com.skyrpg.skyblock.mayor;

import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.mining.SkyMining;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cole's Mining Fiesta: while it runs, an ore or Dwarven Metal mined on a mining island can drop a Refined Mineral, and
 * a gemstone a Glossy Gemstone, Hypixel's chance: the block's Breaking Power / 5000 (Mithril 0.08%, Glacite 0.18%).
 * (Its +25 Mining Wisdom and Fortune are the Mayor stat source's.)
 */
final class MiningFiesta {
    private MiningFiesta() {
    }

    static void init() {
        SkyMining.MINED.register((player, state, zone) -> mined(player, state));
    }

    private static void mined(ServerPlayer player, BlockState state) {
        if (!Mayors.active("mining_fiesta")) {
            return;
        }
        SkyMining.Info info = SkyMining.info(player.level(), state);
        if (info == null || player.getRandom().nextDouble() >= info.power() / 5000.0) {
            return;
        }
        boolean gem = state.getBlock() instanceof StainedGlassBlock || state.getBlock() instanceof StainedGlassPaneBlock;
        if (!gem && !info.ore() && !SkyMining.isMetal(player.level(), state)) {
            return;
        }
        ItemDefinition def = ItemRegistry.skyblock(gem ? "GLOSSY_GEMSTONE" : "REFINED_MINERAL");
        if (def == null) {
            return;
        }
        ItemStack drop = SkyItems.create(def, 1);
        Component name = drop.getHoverName().copy();
        if (!player.getInventory().add(drop)) {
            player.drop(drop, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        player.sendSystemMessage(Component.literal("FIESTA DROP! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).append(name));
        Msg.playSound(player, SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f, 1.6f);
    }
}
