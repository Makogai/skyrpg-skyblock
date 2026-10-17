package com.skyrpg.skyblock.cookie;

import com.mojang.serialization.Codec;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Booster Cookie: eat one (right-click) for 4 days of the Cookie Buff, in real time; more cookies add more
 * days. While it lasts: +15 Magic Find, +5 Wisdom in every skill, and you keep your coins when you die. {@code /cookie}
 * says how long it has left. Per profile.
 */
public final class BoosterCookie {
    public static final long DAYS = 4;
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;
    private static final double MAGIC_FIND = 15;
    private static final double WISDOM = 5;
    private static final List<StatType> WISDOMS = List.of(StatTypes.FARMING_WISDOM, StatTypes.MINING_WISDOM, StatTypes.FORAGING_WISDOM,
            StatTypes.COMBAT_WISDOM, StatTypes.FISHING_WISDOM, StatTypes.ALCHEMY_WISDOM);
    /** When the buff runs out (epoch millis). */
    public static final AttachmentType<Long> UNTIL = AttachmentRegistry.create(SkyRpgSkyblock.id("booster_cookie"),
            builder -> builder.persistent(Codec.LONG).copyOnDeath());

    private BoosterCookie() {
    }

    public static void init() {
        Identifier early = SkyRpgSkyblock.id("cookie_early");
        UseItemCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(early, (player, level, hand) -> use(player, hand));
        UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(early, (player, level, hand, hit) -> use(player, hand));
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Booster Cookie";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                if (active(player) && SkyBlockWorlds.isSkyBlock(player.level())) {
                    out.add(StatTypes.MAGIC_FIND, MAGIC_FIND);
                    WISDOMS.forEach(stat -> out.add(stat, WISDOM));
                }
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("cookie").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            long left = left(player);
            Notice.of(Notice.Kind.INFO).tag("COOKIE").send(player, left > 0
                    ? Component.literal("Your Cookie Buff has " + duration(left) + " left.").withStyle(ChatFormatting.LIGHT_PURPLE)
                    : Component.literal("You don't have the Cookie Buff. Eat a Booster Cookie!").withStyle(ChatFormatting.GRAY));
            return 1;
        })));
    }

    private static InteractionResult use(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!"BOOSTER_COOKIE".equals(SkyItems.skyblockId(held))) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            eat(server, held);
        }
        return InteractionResult.SUCCESS;
    }

    private static void eat(ServerPlayer player, ItemStack cookie) {
        long now = System.currentTimeMillis();
        long from = Math.max(now, player.getAttachedOrElse(UNTIL, 0L));
        player.setAttached(UNTIL, from + DAYS * DAY_MILLIS);
        cookie.shrink(1);
        Stats.refresh(player);
        Notice.Panel panel = Notice.of(Notice.Kind.SUCCESS).tag("COOKIE BUFF").panel();
        panel.line(Component.literal("+" + Msg.number(MAGIC_FIND) + " Magic Find").withStyle(ChatFormatting.AQUA));
        panel.line(Component.literal("+" + Msg.number(WISDOM) + " Wisdom in every skill").withStyle(ChatFormatting.DARK_AQUA));
        panel.line(Component.literal("Keep your coins when you die").withStyle(ChatFormatting.GOLD));
        panel.send(player, Component.literal("You ate a Booster Cookie! ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(duration(left(player)) + " left").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.PLAYER_BURP, 1.0f, 1.0f);
    }

    public static long left(ServerPlayer player) {
        return Math.max(0, player.getAttachedOrElse(UNTIL, 0L) - System.currentTimeMillis());
    }

    public static boolean active(ServerPlayer player) {
        return left(player) > 0;
    }

    private static String duration(long millis) {
        long minutes = millis / 60000;
        long days = minutes / (60 * 24);
        long hours = minutes / 60 % 24;
        return days > 0 ? days + "d " + hours + "h" : hours > 0 ? hours + "h " + minutes % 60 + "m" : minutes + "m";
    }
}
