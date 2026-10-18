package com.skyrpg.skyblock.essence;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hypixel's essences: a currency per kind (Wither, Undead, Dragon, Spider, Gold, Diamond, Ice, Crimson, Fossil), kept on
 * the profile rather than as items. {@code /essence} lists yours; admins: {@code /sbessence give <player> <kind> <amount>}.
 */
public final class Essence {
    public static final String FOSSIL = "FOSSIL";
    /** Every kind, in Hypixel's order, with its colour. */
    public static final List<String> KINDS = List.of("WITHER", "UNDEAD", "DRAGON", "SPIDER", "GOLD", "DIAMOND", "ICE", "CRIMSON", FOSSIL, "SUN_GECKO", "FOREST");
    private static final Map<String, Integer> COLORS = Map.ofEntries(Map.entry("WITHER", 0x555555), Map.entry("UNDEAD", 0xAA00AA), Map.entry("DRAGON", 0xFF55FF), Map.entry("SPIDER", 0xAA0000), Map.entry("GOLD", 0xFFAA00),
            Map.entry("DIAMOND", 0x55FFFF), Map.entry("ICE", 0x55FFFF), Map.entry("CRIMSON", 0xFF5555), Map.entry(FOSSIL, 0xFF55FF), Map.entry("SUN_GECKO", 0xFFDD55), Map.entry("FOREST", 0x55AA55));

    /** How much of each essence the player has. */
    static final AttachmentType<Map<String, Long>> STORE = AttachmentRegistry.create(SkyRpgSkyblock.id("essence"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());

    private Essence() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal("essence").executes(ctx -> {
                show(ctx.getSource().getPlayerOrException());
                return 1;
            }));
            dispatcher.register(Commands.literal("sbessence").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("kind", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(KINDS.stream().map(k -> k.toLowerCase(Locale.ROOT)), builder))
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(ctx -> {
                                        String kind = StringArgumentType.getString(ctx, "kind").toUpperCase(Locale.ROOT);
                                        if (!KINDS.contains(kind)) {
                                            return 0;
                                        }
                                        give(EntityArgument.getPlayer(ctx, "player"), kind, IntegerArgumentType.getInteger(ctx, "amount"));
                                        return 1;
                                    }))))));
        });
    }

    public static long get(ServerPlayer player, String kind) {
        return player.getAttachedOrElse(STORE, Map.of()).getOrDefault(kind, 0L);
    }

    public static void give(ServerPlayer player, String kind, long amount) {
        Map<String, Long> store = new HashMap<>(player.getAttachedOrElse(STORE, Map.of()));
        store.merge(kind, amount, Long::sum);
        player.setAttached(STORE, Map.copyOf(store));
    }

    /** Takes {@code amount} if the player has that much; false (and nothing taken) if not. */
    public static boolean take(ServerPlayer player, String kind, long amount) {
        if (get(player, kind) < amount) {
            return false;
        }
        give(player, kind, -amount);
        return true;
    }

    /** "Fossil Essence". */
    public static String name(String kind) {
        if (kind.equals("SUN_GECKO")) {
            return "Sun Gecko Essence";
        }
        return kind.charAt(0) + kind.substring(1).toLowerCase(Locale.ROOT) + " Essence";
    }

    public static int color(String kind) {
        return COLORS.getOrDefault(kind, 0xFF55FF);
    }

    private static void show(ServerPlayer player) {
        Notice.Panel panel = Notice.of(Notice.Kind.INFO).tag("ESSENCE").color(0xFF55FF).panel();
        for (String kind : KINDS) {
            long have = get(player, kind);
            panel.line(Component.literal(name(kind) + ": ").withColor(color(kind))
                    .append(Component.literal(Msg.number(have)).withStyle(have > 0 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY)));
        }
        panel.send(player, Component.literal("Your essence").withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}
