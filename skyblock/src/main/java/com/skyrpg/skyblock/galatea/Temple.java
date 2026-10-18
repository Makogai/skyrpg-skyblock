package com.skyrpg.skyblock.galatea;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Purse;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Forest Temple's altar (Hypixel's donations of coins and Enchanted Fig Logs for Foraging Fortune): five offerings,
 * each a permanent +5 Foraging Fortune. Hypixel's amounts weren't found; decided: 32 / 64 / 128 / 256 / 512 Enchanted
 * Fig Logs with 50k / 100k / 250k / 500k / 1M coins.
 */
final class Temple {
    private static final int[] LOGS = {32, 64, 128, 256, 512};
    private static final long[] COINS = {50_000, 100_000, 250_000, 500_000, 1_000_000};
    private static final int FORTUNE = 5;

    static final AttachmentType<Integer> OFFERED = AttachmentRegistry.create(SkyRpgSkyblock.id("forest_temple"),
            builder -> builder.persistent(Codec.INT).copyOnDeath());

    private static Vec3 altar;

    private Temple() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("galatea_temple"), "skyrpg/spawn_spots", Temple::load);
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Forest Temple";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                out.add(StatTypes.FORAGING_FORTUNE, FORTUNE * offered(player));
            }
        });
        Identifier first = SkyRpgSkyblock.id("forest_temple");
        UseBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(first, (player, level, hand, hit) -> {
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (!Galatea.isGalatea(level) || !(state.is(Blocks.CHISELED_STONE_BRICKS) || state.is(Blocks.CANDLE))) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer server && altar != null && Vec3.atCenterOf(hit.getBlockPos()).distanceTo(altar) < 3) {
                open(server);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    private static void load(Map<Identifier, JsonElement> files) {
        altar = null;
        files.forEach((id, element) -> {
            JsonObject root = element.getAsJsonObject();
            if (!root.has("dimension") || !root.get("dimension").getAsString().equals(Galatea.LEVEL.identifier().toString())) {
                return;
            }
            JsonObject spot = root.getAsJsonObject("spots").getAsJsonObject("forest_temple");
            if (spot != null) {
                var pos = spot.getAsJsonArray("pos");
                altar = new Vec3(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble());
            }
        });
    }

    static int offered(ServerPlayer player) {
        return player.getAttachedOrElse(OFFERED, 0);
    }

    private static long logs(ServerPlayer player) {
        long count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if ("ENCHANTED_FIG_LOG".equals(SkyItems.skyblockId(stack))) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void takeLogs(ServerPlayer player, int count) {
        int left = count;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (left > 0 && "ENCHANTED_FIG_LOG".equals(SkyItems.skyblockId(stack))) {
                int n = Math.min(left, stack.getCount());
                stack.shrink(n);
                left -= n;
            }
        }
    }

    static void open(ServerPlayer player) {
        CardMenu.open(player, p -> {
            int done = offered(p);
            long logs = logs(p);
            long coins = Purse.get(p);
            List<CardView.Card> cards = new ArrayList<>();
            for (int i = 0; i < LOGS.length; i++) {
                boolean given = i < done;
                boolean next = i == done;
                cards.add(new CardView.Card("Offering " + Msg.roman(i + 1), given ? "Given: +" + FORTUNE + " Foraging Fortune"
                        : "+" + FORTUNE + " Foraging Fortune, for good", given ? 0x55FF55 : next ? 0x7FE0B4 : 0xAAAAAA,
                        next && logs >= LOGS[i] && coins >= COINS[i], given ? "Given" : "Offer", new ItemStack(Items.CANDLE), "",
                        given ? List.of() : List.of(Chip.note(ItemStack.EMPTY, LOGS[i] + " Enchanted Fig Logs", logs >= LOGS[i]),
                                Chip.price("coins", COINS[i], coins)), -1, ""));
            }
            return new CardView("GALATEA", "Forest Temple", "The altar", -1,
                    List.of(Chip.note(new ItemStack(Items.JUNGLE_LOG), Msg.number(logs) + " Ench. Fig Logs", true)), cards,
                    "Each offering is +" + FORTUNE + " Foraging Fortune, for good.", 1, 40);
        }, (p, card) -> {
            int done = offered(p);
            if (card != done || done >= LOGS.length) {
                return "";
            }
            if (logs(p) < LOGS[done]) {
                return "You need " + LOGS[done] + " Enchanted Fig Logs.";
            }
            if (!Purse.trySpend(p, COINS[done])) {
                return "You need " + Msg.number(COINS[done]) + " coins.";
            }
            takeLogs(p, LOGS[done]);
            p.setAttached(OFFERED, done + 1);
            Stats.refresh(p);
            p.sendSystemMessage(Component.literal("The forest accepts your offering. ").withStyle(ChatFormatting.DARK_GREEN)
                    .append(Component.literal("+" + FORTUNE + " Foraging Fortune").withStyle(ChatFormatting.GOLD)));
            Msg.playSound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1f, 0.8f);
            return "";
        });
    }
}
