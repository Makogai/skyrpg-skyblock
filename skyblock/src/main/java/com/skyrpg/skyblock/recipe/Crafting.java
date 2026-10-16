package com.skyrpg.skyblock.recipe;

import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Crafting a SkyBlock recipe straight from your inventory (Hypixel's Supercraft): the Recipe Book's Craft button, and
 * the crafting screen. The server checks the recipe is unlocked and the materials are there, takes them, and gives
 * what it makes.
 */
public final class Crafting {
    /** Someone crafted {@code count} of a SkyBlock item (minions count unique crafts for their slots). */
    @FunctionalInterface
    public interface Crafted {
        void onCrafted(ServerPlayer player, String skyblockId, int count);
    }

    public static final Event<Crafted> CRAFTED = EventFactory.createArrayBacked(Crafted.class, listeners -> (player, id, count) -> {
        for (Crafted listener : listeners) {
            listener.onCrafted(player, id, count);
        }
    });

    /** Most crafts in one go (shift-click), so one click can't empty a whole inventory into a single item. */
    public static final int MAX_CRAFTS = 64;

    /** Client to server: craft this recipe once, or as many times as the materials allow ({@code max}). */
    public record Craft(Identifier recipe, boolean max) implements CustomPacketPayload {
        public static final Type<Craft> TYPE = new Type<>(SkyRpgSkyblock.id("craft"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Craft> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Craft::recipe, ByteBufCodecs.BOOL, Craft::max, Craft::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private Crafting() {
    }

    public static void init() {
        RecipesPayload.init();
        PayloadTypeRegistry.serverboundPlay().register(Craft.TYPE, Craft.CODEC);
        // A crafting table opens SkyBlock's crafting screen, as on Hypixel.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.getBlockState(hit.getBlockPos()).is(Blocks.CRAFTING_TABLE) || player.isSecondaryUseActive()) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                CraftMenu.open(serverPlayer);
            }
            return InteractionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("craft")
                .executes(ctx -> {
                    CraftMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
        ServerPlayNetworking.registerGlobalReceiver(Craft.TYPE, (payload, context) -> {
            Recipes.Recipe recipe = Recipes.get(payload.recipe());
            if (recipe != null) {
                craft(context.player(), recipe, payload.max());
            }
        });
    }

    /** Carpentry XP for a craft: 3% of what its ingredients sell for to an NPC, as on Hypixel. */
    public static final double CARPENTRY_SHARE = 0.03;

    /**
     * Gives what {@code times} crafts of the recipe made (unique items one by one, the rest in stacks), the Carpentry XP
     * they earn, and tells {@link #CRAFTED}.
     */
    static void give(ServerPlayer player, ItemDefinition output, Recipes.Recipe recipe, int times) {
        int made = recipe.count() * times;
        double worth = 0;
        for (Map.Entry<String, Integer> need : recipe.ingredients().entrySet()) {
            worth += SkyStacks.npcPrice(need.getKey()) * need.getValue();
        }
        if (worth > 0) {
            SkillLevels.giveXpQuietly(player, SkillLevels.CARPENTRY, worth * times * CARPENTRY_SHARE);
        }
        CRAFTED.invoker().onCrafted(player, output.skyblockId(), made);
        boolean unique = SkyItems.unstackable(output);
        int left = made;
        while (left > 0) {
            ItemStack stack = SkyItems.create(output, 1);
            int count = unique ? 1 : Math.min(left, stack.getMaxStackSize());
            stack.setCount(count);
            left -= count;
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, Prediction.SERVER_ONLY);
            }
        }
    }

    /** How many times the inventory holds this recipe's materials. */
    public static int craftable(Inventory inventory, Recipes.Recipe recipe) {
        int times = Integer.MAX_VALUE;
        for (Map.Entry<String, Integer> need : recipe.ingredients().entrySet()) {
            times = Math.min(times, SkyStacks.count(inventory, need.getKey()) / need.getValue());
        }
        return times == Integer.MAX_VALUE ? 0 : times;
    }

    /** Crafts the recipe once (or up to {@link #MAX_CRAFTS} times); returns how many times it did. */
    public static int craft(ServerPlayer player, Recipes.Recipe recipe, boolean max) {
        if (!Recipes.unlocked(player, recipe)) {
            player.sendOverlayMessage(Component.literal("Locked: requires " + recipe.requires().describe()).withStyle(ChatFormatting.RED));
            return 0;
        }
        ItemDefinition output = ItemRegistry.skyblock(recipe.output());
        if (output == null) {
            return 0;
        }
        Inventory inventory = player.getInventory();
        int times = Math.min(craftable(inventory, recipe), max ? MAX_CRAFTS : 1);
        if (times <= 0) {
            player.sendOverlayMessage(Component.literal("You don't have the materials for that.").withStyle(ChatFormatting.RED));
            player.level().playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.5f, 1f);
            return 0;
        }
        for (Map.Entry<String, Integer> need : recipe.ingredients().entrySet()) {
            SkyStacks.take(inventory, need.getKey(), need.getValue() * times);
        }
        give(player, output, recipe, times);
        player.containerMenu.broadcastChanges();
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.PLAYERS, 0.6f, 1.2f);
        player.sendOverlayMessage(Component.literal("Crafted " + recipe.count() * times + "x ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(output.name()).withColor(output.rarity().color())));
        return times;
    }
}
