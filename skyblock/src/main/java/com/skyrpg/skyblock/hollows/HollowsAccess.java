package com.skyrpg.skyblock.hollows;

import com.mojang.serialization.Codec;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.hub.HubAreas;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.mining.Hotm;
import com.skyrpg.skyblock.mining.SkyMining;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Getting into the Crystal Hollows, Hypixel's way: Gwendolyn in the Dwarven Mines sells a Crystal Hollows Pass (10,000
 * coins, 6 hours of real time) to anyone with Mining XII and HotM 4, and sends you down. When it runs out you're taken
 * back to the Dwarven Mines. The pad in the Nucleus goes back any time.
 */
public final class HollowsAccess {
    public static final long PRICE = 10_000;
    public static final long PASS_MILLIS = 6L * 60 * 60 * 1000;
    public static final int MINING = 12;
    public static final int HOTM = 4;
    /** When this profile's pass runs out (epoch millis). */
    public static final AttachmentType<Long> PASS = AttachmentRegistry.create(SkyRpgSkyblock.id("hollows_pass"),
            builder -> builder.persistent(Codec.LONG).copyOnDeath());

    private HollowsAccess() {
    }

    static void init() {
        Actions.register("hollows_pass", (player, params) -> talk(player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("hollows")
                .then(Commands.literal("buy").executes(ctx -> {
                    buy(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("go").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (left(player) > 0 && player.level().dimension().equals(SkyMining.DWARVEN_MINES)) {
                        send(player);
                    }
                    return 1;
                }))));
        ServerTickEvents.END_SERVER_TICK.register(HollowsAccess::tick);
    }

    /** Millis left on the pass (0 = none). */
    public static long left(ServerPlayer player) {
        return Math.max(0, player.getAttachedOrElse(PASS, 0L) - System.currentTimeMillis());
    }

    private static String problem(ServerPlayer player) {
        if (SkillLevels.level(player, SkillLevels.MINING) < MINING) {
            return "You need Mining " + Msg.roman(MINING) + " to enter the Crystal Hollows!";
        }
        if (Hotm.level(player) < HOTM) {
            return "You need Heart of the Mountain " + Msg.roman(HOTM) + " to enter the Crystal Hollows!";
        }
        return "";
    }

    private static void talk(ServerPlayer player) {
        String problem = problem(player);
        if (!problem.isEmpty()) {
            Notice.send(player, Notice.Kind.ERROR, Component.literal(problem));
            return;
        }
        long left = left(player);
        if (left > 0) {
            Notice.of(Notice.Kind.MINING).tag("GWENDOLYN").send(player, Component.literal("Your pass has " + duration(left) + " left. Off you go!")
                    .withStyle(ChatFormatting.GRAY));
            send(player);
            return;
        }
        Component buy = Component.literal("[Buy a pass: " + Msg.number(PRICE) + " coins]").withStyle(style -> style.withColor(ChatFormatting.GOLD)
                .withBold(true).withClickEvent(new ClickEvent.RunCommand("/hollows buy"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("6 hours in the Crystal Hollows"))));
        Notice.of(Notice.Kind.MINING).tag("GWENDOLYN").send(player, Component.literal("The Crystal Hollows lie below. A pass lasts 6 hours. ")
                .withStyle(ChatFormatting.GRAY).append(buy));
    }

    private static void buy(ServerPlayer player) {
        String problem = problem(player);
        if (!problem.isEmpty() || !player.level().dimension().equals(SkyMining.DWARVEN_MINES)) {
            Notice.send(player, Notice.Kind.ERROR, Component.literal(problem.isEmpty() ? "Talk to Gwendolyn in the Dwarven Mines." : problem));
            return;
        }
        if (left(player) <= 0) {
            if (!Wallet.take(player, PRICE)) {
                Notice.send(player, Notice.Kind.ERROR, Component.literal("You need " + Msg.number(PRICE) + " coins for a pass!"));
                return;
            }
            player.setAttached(PASS, System.currentTimeMillis() + PASS_MILLIS);
            Notice.of(Notice.Kind.MINING).tag("PASS").send(player, Component.literal("You bought a Crystal Hollows Pass for 6 hours!")
                    .withStyle(ChatFormatting.GREEN));
        }
        send(player);
    }

    /** Down to the Nucleus. */
    private static void send(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel hollows = server.getLevel(CrystalHollows.LEVEL);
        HubAreas.Area nucleus = HubAreas.all().get("hollows_nucleus");
        if (hollows == null || nucleus == null || nucleus.arrival() == null) {
            Notice.send(player, Notice.Kind.ERROR, Component.literal("The Crystal Hollows aren't open yet."));
            return;
        }
        Msg.playSound(player, SoundEvents.MINECART_RIDING, 0.8f, 1.0f);
        player.teleport(new TeleportTransition(hollows, nucleus.arrival().pos(), Vec3.ZERO, nucleus.arrival().yaw(), 0, java.util.Set.of(),
                TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 600 != 0) {
            return;
        }
        ServerLevel hollows = server.getLevel(CrystalHollows.LEVEL);
        ServerLevel mines = server.getLevel(SkyMining.DWARVEN_MINES);
        if (hollows == null || mines == null) {
            return;
        }
        HubAreas.Area lift = HubAreas.all().get("mines_village");
        for (ServerPlayer player : java.util.List.copyOf(hollows.players())) {
            if (player.isCreative() || player.isSpectator() || left(player) > 0 || lift == null || lift.arrival() == null) {
                continue;
            }
            Notice.send(player, Notice.Kind.WARN, Component.literal("Your Crystal Hollows Pass ran out!"));
            player.teleport(new TeleportTransition(mines, lift.arrival().pos(), Vec3.ZERO, lift.arrival().yaw(), 0, java.util.Set.of(),
                    TeleportTransition.DO_NOTHING));
        }
    }

    private static String duration(long millis) {
        long minutes = millis / 60000;
        return minutes >= 60 ? minutes / 60 + "h " + minutes % 60 + "m" : minutes + "m";
    }
}
