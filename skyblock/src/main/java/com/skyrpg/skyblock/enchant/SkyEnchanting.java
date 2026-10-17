package com.skyrpg.skyblock.enchant;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.enchant.Books;
import com.skyrpg.items.enchant.Enchants;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Hypixel enchanting in SkyBlock: an enchanting table (in the Hub or on your island) opens the Enchantment Table, and so
 * does the NPC action {@code skyblock_enchanting}. In the survival archive the old Arcane Enchanting stays.
 * {@code /sbenchant} for admins: enchant the held item, or get a book.
 */
public final class SkyEnchanting {
    private SkyEnchanting() {
    }

    public static void init() {
        EnchantEffects.init();
        Actions.register("skyblock_enchanting", (player, params) -> EnchantMenu.open(player));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!SkyBlockWorlds.isSkyBlock(level) || player.isSpectator() || !level.getBlockState(hit.getBlockPos()).is(Blocks.ENCHANTING_TABLE)
                    || player.isSecondaryUseActive() && !player.getItemInHand(hand).isEmpty()) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                EnchantMenu.open(serverPlayer);
            }
            return InteractionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("sbenchant")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    EnchantMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.argument("enchant", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Enchants.all().stream().map(Enchants.Enchant::id), builder))
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 10))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "enchant");
                                    int level = IntegerArgumentType.getInteger(ctx, "level");
                                    ItemStack held = player.getMainHandItem();
                                    if (Enchants.get(id) == null || SkyItems.definition(held) == null) {
                                        ctx.getSource().sendFailure(Component.literal("Hold a SkyBlock item and name a known enchantment."));
                                        return 0;
                                    }
                                    ItemMods.setEnchantment(held, id, level);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Now: ").append(held.getHoverName()), false);
                                    return 1;
                                })))
                .then(Commands.literal("book").then(Commands.argument("enchant", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Enchants.all().stream().map(Enchants.Enchant::id), builder))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 10))
                                .executes(ctx -> {
                                    ItemStack book = Books.create(StringArgumentType.getString(ctx, "enchant"), IntegerArgumentType.getInteger(ctx, "level"), 1);
                                    if (book.isEmpty()) {
                                        return 0;
                                    }
                                    ctx.getSource().getPlayerOrException().getInventory().add(book);
                                    return 1;
                                }))))));
    }
}
