package com.skyrpg.skyblock;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/**
 * Admin tools for SkyBlock items:
 * <ul>
 *   <li>{@code /sbitem <id> [count]}: gives you one ({@code aspect_of_the_end}; {@code wool-6} for {@code WOOL:6})</li>
 *   <li>{@code /sbitem give <players> <id> [count]}</li>
 *   <li>{@code /sbitem search <text>}: items whose name or id contains it, click one to get it</li>
 * </ul>
 */
final class SbItemCommand {
    private static final int SEARCH_LIMIT = 40;

    private SbItemCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sbitem")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("search")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> search(ctx.getSource(), StringArgumentType.getString(ctx, "text")))))
                .then(Commands.literal("give")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(itemArgument()
                                        .executes(ctx -> give(ctx, EntityArgument.getPlayers(ctx, "players"), 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> give(ctx, EntityArgument.getPlayers(ctx, "players"),
                                                        IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(itemArgument()
                        .executes(ctx -> give(ctx, List.of(ctx.getSource().getPlayerOrException()), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> give(ctx, List.of(ctx.getSource().getPlayerOrException()),
                                        IntegerArgumentType.getInteger(ctx, "count"))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> itemArgument() {
        return Commands.argument("item", StringArgumentType.word())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        ItemRegistry.items().stream().filter(ItemDefinition::skyblock).map(SbItemCommand::typed), builder));
    }

    /** What you type for an item: its SkyBlock id in lower case, ':' as '-' (a command word can't hold ':'). */
    static String typed(ItemDefinition def) {
        return def.skyblockId().toLowerCase(Locale.ROOT).replace(':', '-');
    }

    static ItemDefinition lookup(String typed) {
        return ItemRegistry.skyblock(typed.toUpperCase(Locale.ROOT).replace('-', ':')) instanceof ItemDefinition def
                ? def : ItemRegistry.skyblock(typed.toUpperCase(Locale.ROOT));
    }

    private static int give(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> players, int count) throws CommandSyntaxException {
        String typed = StringArgumentType.getString(ctx, "item");
        ItemDefinition def = lookup(typed);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("No SkyBlock item " + typed + ". Try /sbitem search <name>."));
            return 0;
        }
        for (ServerPlayer player : players) {
            // Unstackable items get one stack each (each with its own uuid).
            int stacks = SkyItems.unstackable(def) ? count : 1;
            for (int i = 0; i < stacks; i++) {
                ItemStack stack = SkyItems.create(def, SkyItems.unstackable(def) ? 1 : count);
                if (!player.getInventory().add(stack)) {
                    player.spawnAtLocation(player.level(), stack);
                }
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + count + "x ").append(name(def))
                .append(" to " + players.size() + " player(s)"), true);
        return players.size();
    }

    private static int search(CommandSourceStack source, String text) {
        String needle = text.toLowerCase(Locale.ROOT).trim();
        List<ItemDefinition> found = ItemRegistry.items().stream()
                .filter(ItemDefinition::skyblock)
                .filter(def -> def.name().toLowerCase(Locale.ROOT).contains(needle) || typed(def).contains(needle.replace(' ', '_')))
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();
        MutableComponent out = Component.literal(found.size() + " SkyBlock item" + (found.size() == 1 ? "" : "s")
                + " matching \"" + text + "\"" + (found.size() > SEARCH_LIMIT ? " (first " + SEARCH_LIMIT + ")" : "") + ":")
                .withStyle(ChatFormatting.GOLD);
        for (ItemDefinition def : found.subList(0, Math.min(SEARCH_LIMIT, found.size()))) {
            ItemStack stack = SkyItems.create(def, 1);
            out.append("\n ").append(name(def).copy().withStyle(style -> style
                            .withClickEvent(new ClickEvent.RunCommand("/sbitem " + typed(def)))
                            .withHoverEvent(new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(stack)))))
                    .append(Component.literal("  " + def.skyblockId()).withStyle(ChatFormatting.DARK_GRAY));
        }
        source.sendSuccess(() -> out, false);
        return found.size();
    }

    private static Component name(ItemDefinition def) {
        return Component.literal(def.name()).withColor(def.rarity().color());
    }
}
