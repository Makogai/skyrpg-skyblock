package com.skyrpg.skyblock.festival;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.time.CalendarEvents;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;

/**
 * The calendar's events that play out in the game (the schedule is {@code CalendarEvents}): the Spooky Festival, the
 * Traveling Zoo and the New Year Celebration.
 *
 * <p>{@code /sbevent start|stop <event>} starts a calendar event now for its usual length, or ends one started that way
 * (admins, testing); {@code /sbspooky score <green> <purple>} sets your Candy Contest score and {@code /sbspooky chest}
 * puts a Trick or Treat Chest in front of you.
 */
public final class Festivals {
    /** The sidebar's festival lines (the Spooky Festival's timer and your candy), synced to its player. */
    public static final AttachmentType<String> SIDEBAR = AttachmentRegistry.create(SkyRpgSkyblock.id("festival_sidebar"),
            builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));

    private Festivals() {
    }

    public static void init() {
        // touch the attachment types at start-up so saved data loads
        NewYear.CLAIMED.identifier();
        SIDEBAR.identifier();
        NewYear.init();
        SpookyFestival.init();
        TravelingZoo.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal("spookyfestival").executes(ctx -> {
                SpookyFestival.open(ctx.getSource().getPlayerOrException());
                return 1;
            }));
            dispatcher.register(Commands.literal("sbevent").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("start").then(Commands.argument("event", StringArgumentType.greedyString()).executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "event");
                        boolean known = CalendarEvents.force(name, true, System.currentTimeMillis());
                        ctx.getSource().sendSuccess(() -> Component.literal(known ? "Started " + name + "." : "No event called " + name + "."), true);
                        return known ? 1 : 0;
                    })))
                    .then(Commands.literal("stop").then(Commands.argument("event", StringArgumentType.greedyString()).executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "event");
                        boolean known = CalendarEvents.force(name, false, System.currentTimeMillis());
                        ctx.getSource().sendSuccess(() -> Component.literal(known ? "Stopped " + name + " (if an admin started it)."
                                : "No event called " + name + "."), true);
                        return known ? 1 : 0;
                    }))));
            dispatcher.register(Commands.literal("sbspooky").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("chest").executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        SpookyFestival.spawnChest(player, player.position().add(player.getLookAngle().multiply(2, 0, 2)));
                        return 1;
                    }))
                    .then(Commands.literal("score").then(Commands.argument("green", IntegerArgumentType.integer(0))
                            .then(Commands.argument("purple", IntegerArgumentType.integer(0)).executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                SpookyFestival.setScore(player, IntegerArgumentType.getInteger(ctx, "green"), IntegerArgumentType.getInteger(ctx, "purple"));
                                ctx.getSource().sendSuccess(() -> Component.literal("Set your Candy Contest score."), false);
                                return 1;
                            })))));
        });
    }

    /** Sets (or with null clears) a player's festival sidebar lines. */
    static void sidebar(ServerPlayer player, String lines) {
        if (lines == null) {
            if (player.getAttached(SIDEBAR) != null) {
                player.removeAttached(SIDEBAR);
            }
        } else if (!lines.equals(player.getAttached(SIDEBAR))) {
            player.setAttached(SIDEBAR, lines);
        }
    }
}
