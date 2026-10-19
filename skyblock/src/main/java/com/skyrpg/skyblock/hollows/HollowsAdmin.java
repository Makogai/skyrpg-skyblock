package com.skyrpg.skyblock.hollows;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.skyrpg.core.event.ItemGains;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin commands for trying the Hollows' quests: {@code /sbhollows crystal <gem>} (give the crystal), {@code worm} and
 * {@code scatha} (one in front of you), {@code boss <corleone|bal>} (spawn now), {@code grotto} (carve one here),
 * {@code scent}, {@code key} (the temple), {@code parts} (all of Professor Robot's parts), {@code tools} (Divan's four
 * tools), {@code reset} (forget this run) and {@code state}.
 */
final class HollowsAdmin {
    private static final String[] PARTS = {"ELECTRON_TRANSMITTER", "FTX_3070", "ROBOTRON_REFLECTOR", "SUPERLITE_MOTOR", "CONTROL_SWITCH", "SYNTHETIC_HEART"};
    private static final String[] TOOLS = {"DWARVEN_DIAMOND_AXE", "DWARVEN_EMERALD_HAMMER", "DWARVEN_GOLD_HAMMER", "DWARVEN_LAPIS_SWORD"};

    private HollowsAdmin() {
    }

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("sbhollows")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("crystal").then(Commands.argument("gem", StringArgumentType.word()).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    for (Crystals.Gem gem : Crystals.Gem.values()) {
                        if (gem.name().equalsIgnoreCase(StringArgumentType.getString(ctx, "gem"))) {
                            HollowsState.set(player, "claimed." + gem.name(), 0);
                            return Crystals.give(player, gem) ? 1 : 0;
                        }
                    }
                    return say(ctx.getSource(), "Gems: jade, amber, topaz, sapphire, amethyst");
                })))
                .then(Commands.literal("worm").executes(ctx -> worm(ctx.getSource(), false)))
                .then(Commands.literal("scatha").executes(ctx -> worm(ctx.getSource(), true)))
                .then(Commands.literal("boss").then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                    boolean ok = HollowsBosses.spawnNow(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"));
                    return say(ctx.getSource(), ok ? "Spawned." : "corleone or bal (in the Crystal Hollows, with the chunk loaded)");
                })))
                .then(Commands.literal("grotto").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (player.level() instanceof ServerLevel level && level.dimension().equals(CrystalHollows.LEVEL)) {
                        FairyGrottos.place(level, player.blockPosition().above(5));
                        return say(ctx.getSource(), "A Fairy Grotto, carved 5 blocks above you.");
                    }
                    return say(ctx.getSource(), "In the Crystal Hollows only.");
                }))
                .then(Commands.literal("scent").executes(ctx -> {
                    HollowsState.set(ctx.getSource().getPlayerOrException(), "scent", System.currentTimeMillis() + HollowsNpcs.SCENT_MILLIS);
                    return say(ctx.getSource(), "King's Scent for 20 minutes.");
                }))
                .then(Commands.literal("key").executes(ctx -> {
                    HollowsState.set(ctx.getSource().getPlayerOrException(), "temple", 1);
                    return say(ctx.getSource(), "The temple is open to you.");
                }))
                .then(Commands.literal("parts").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    for (String part : PARTS) {
                        HollowsState.give(player, part, 1, ItemGains.Source.OTHER);
                    }
                    return say(ctx.getSource(), "The six Automaton Parts.");
                }))
                .then(Commands.literal("tools").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    for (String tool : TOOLS) {
                        HollowsState.give(player, tool, 1, ItemGains.Source.OTHER);
                    }
                    return say(ctx.getSource(), "Divan's four tools.");
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    HollowsState.clearRun(ctx.getSource().getPlayerOrException(), "placed.", "claimed.", "scent", "temple", "part.", "divan.");
                    return say(ctx.getSource(), "This run forgotten.");
                }))
                .then(Commands.literal("state").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    StringBuilder out = new StringBuilder("Hollows progress:");
                    for (String key : new String[] {"placed.JADE", "placed.AMBER", "placed.TOPAZ", "placed.SAPPHIRE", "placed.AMETHYST", "temple", "total.runs"}) {
                        out.append(' ').append(key).append('=').append(HollowsState.get(player, key));
                    }
                    return say(ctx.getSource(), out.toString());
                }))));
    }

    private static int worm(CommandSourceStack source, boolean scatha) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (player.level() instanceof ServerLevel level) {
            Worms.spawn(level, player, scatha);
        }
        return 1;
    }

    private static int say(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }
}
