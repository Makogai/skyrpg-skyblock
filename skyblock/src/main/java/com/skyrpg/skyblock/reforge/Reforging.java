package com.skyrpg.skyblock.reforge;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyrpg.core.action.Actions;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.reforge.Reforges;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's reforging and anvil: the Blacksmith's {@code reforge} NPC action opens "Reforge Item", and any anvil opens
 * SkyBlock's "Combine Items" instead of vanilla's. {@code /sbitemmod} for admins sets a held item's reforge, recombobulates
 * it or adds Potato Books, for testing.
 */
public final class Reforging {
    private Reforging() {
    }

    public static void init() {
        Actions.register("reforge", (player, params) -> ItemWorkMenu.open(player, ItemWork.Mode.REFORGE));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.getBlockState(hit.getBlockPos()).is(BlockTags.ANVIL) || player.isSecondaryUseActive()) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                ItemWorkMenu.open(serverPlayer, ItemWork.Mode.ANVIL);
            }
            return InteractionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("sbitemmod")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("reforge").then(Commands.argument("modifier", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Reforges.all().stream().map(Reforges.Reforge::modifier), builder))
                        .executes(ctx -> held(ctx.getSource().getPlayerOrException(), stack -> {
                            Reforges.Reforge reforge = Reforges.byModifier(StringArgumentType.getString(ctx, "modifier"));
                            ItemMods.setReforge(stack, reforge);
                            return reforge != null;
                        }))))
                .then(Commands.literal("recombobulate").executes(ctx -> held(ctx.getSource().getPlayerOrException(), stack -> {
                    ItemMods.recombobulate(stack);
                    return true;
                })))
                .then(Commands.literal("potato").executes(ctx -> held(ctx.getSource().getPlayerOrException(), stack -> {
                    ItemMods.addPotatoBook(stack);
                    return true;
                })))
                .then(Commands.literal("menu").then(Commands.literal("reforge").executes(ctx -> {
                    ItemWorkMenu.open(ctx.getSource().getPlayerOrException(), ItemWork.Mode.REFORGE);
                    return 1;
                })).then(Commands.literal("anvil").executes(ctx -> {
                    ItemWorkMenu.open(ctx.getSource().getPlayerOrException(), ItemWork.Mode.ANVIL);
                    return 1;
                })))));
    }

    private static int held(ServerPlayer player, java.util.function.Predicate<ItemStack> change) {
        ItemStack stack = player.getMainHandItem();
        if (SkyItems.definition(stack) == null || !change.test(stack)) {
            player.sendSystemMessage(Component.literal("Hold a SkyBlock item (and name a known reforge)."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("Now: ").append(stack.getHoverName()));
        return 1;
    }
}
