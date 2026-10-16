package com.skyrpg.skyblock.profile;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.skyrpg.core.combat.Warmups;
import com.skyrpg.skyblock.island.Islands;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

/**
 * <ul>
 *   <li>{@code /profiles}: your profiles, with Switch buttons; {@code /profiles switch <name>}, {@code /profiles create}</li>
 *   <li>{@code /is} (also {@code /island}, {@code /warp home}): to your island</li>
 *   <li>{@code /visit <player>}: to the island of the profile they're playing</li>
 *   <li>{@code /coopadd <player>} invites someone into your profile; they {@code /coop accept <you>} within 5 minutes</li>
 * </ul>
 */
public final class ProfileCommands {
    private static final long INVITE_MILLIS = 5 * 60 * 1000;
    /** Invitee -> (inviter -> invite). */
    private static final Map<UUID, Map<UUID, Invite>> INVITES = new ConcurrentHashMap<>();

    private record Invite(UUID profile, long expires) {
    }

    private ProfileCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("profiles")
                .executes(ctx -> list(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("create").executes(ctx -> Profiles.create(ctx.getSource().getPlayerOrException()) != null ? 1 : 0))
                .then(Commands.literal("switch").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                Profiles.of(ctx.getSource().getPlayerOrException().getUUID()).stream().map(Profile::name), builder))
                        .executes(ProfileCommands::switchTo))));
        dispatcher.register(Commands.literal("is").executes(ctx -> home(ctx.getSource().getPlayerOrException())));
        dispatcher.register(Commands.literal("island").executes(ctx -> home(ctx.getSource().getPlayerOrException())));
        dispatcher.register(Commands.literal("warp").then(Commands.literal("home").executes(ctx -> home(ctx.getSource().getPlayerOrException()))));
        dispatcher.register(Commands.literal("visit").then(Commands.argument("player", EntityArgument.player()).executes(ProfileCommands::visit)));
        dispatcher.register(Commands.literal("coopadd").then(Commands.argument("player", EntityArgument.player()).executes(ProfileCommands::invite)));
        dispatcher.register(Commands.literal("coop").then(Commands.literal("accept")
                .then(Commands.argument("player", EntityArgument.player()).executes(ProfileCommands::accept))));
    }

    private static int list(ServerPlayer player) {
        Profile current = Profiles.current(player);
        List<Profile> mine = Profiles.of(player.getUUID());
        MutableComponent out = Component.literal("Your profiles (" + mine.size() + "/" + Profiles.MAX + "):").withStyle(ChatFormatting.GOLD);
        SimpleDateFormat date = new SimpleDateFormat("MMM d, yyyy");
        for (Profile profile : mine) {
            boolean playing = current != null && current.id().equals(profile.id());
            out.append("\n ").append(Component.literal(profile.name()).withStyle(playing ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            if (profile.isCoop()) {
                out.append(Component.literal(" (co-op, " + profile.members().size() + " members)").withStyle(ChatFormatting.AQUA));
            }
            out.append(Component.literal("  since " + date.format(new Date(profile.created()))).withStyle(ChatFormatting.DARK_GRAY));
            if (playing) {
                out.append(Component.literal("  playing").withStyle(ChatFormatting.GREEN));
            } else {
                out.append(Component.literal("  [Switch]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.RunCommand("/profiles switch " + profile.name()))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Play your " + profile.name() + " profile")))));
            }
        }
        if (mine.size() < Profiles.MAX) {
            out.append("\n ").append(Component.literal("[Create a new profile]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent.RunCommand("/profiles create"))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("A fresh start on a new island: new inventory, skills and coins")))));
        }
        player.sendSystemMessage(out);
        return mine.size();
    }

    private static int switchTo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Profile profile = Profiles.named(player.getUUID(), StringArgumentType.getString(ctx, "name"));
        if (profile == null) {
            ctx.getSource().sendFailure(Component.literal("You have no profile called " + StringArgumentType.getString(ctx, "name") + ". See /profiles."));
            return 0;
        }
        return Profiles.switchTo(player, profile) ? 1 : 0;
    }

    private static int home(ServerPlayer player) {
        return Warmups.request(player, "your island", true, () -> {
            player.sendSystemMessage(Component.literal("Warping you to your island...").withStyle(ChatFormatting.GRAY));
            Islands.sendHome(player);
        }) ? 1 : 0;
    }

    private static int visit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Profile profile = Profiles.current(target);
        if (profile == null) {
            ctx.getSource().sendFailure(Component.literal(target.getPlainTextName() + " has no island yet."));
            return 0;
        }
        return Warmups.request(player, target.getPlainTextName() + "'s island", true,
                () -> Islands.visit(player, profile, target.getPlainTextName())) ? 1 : 0;
    }

    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Profile profile = Profiles.current(player);
        if (target == player || profile == null) {
            ctx.getSource().sendFailure(Component.literal("Invite someone else to your co-op."));
            return 0;
        }
        if (profile.hasMember(target.getUUID())) {
            ctx.getSource().sendFailure(Component.literal(target.getPlainTextName() + " is already in this co-op."));
            return 0;
        }
        INVITES.computeIfAbsent(target.getUUID(), id -> new ConcurrentHashMap<>())
                .put(player.getUUID(), new Invite(profile.id(), System.currentTimeMillis() + INVITE_MILLIS));
        player.sendSystemMessage(Component.literal("Invited " + target.getPlainTextName() + " to your " + profile.name()
                + " co-op. The invite lasts 5 minutes.").withStyle(ChatFormatting.GREEN));
        target.sendSystemMessage(Component.literal(player.getPlainTextName() + " invited you to their co-op on " + profile.name()
                        + ": you'd share their island, and get a fresh inventory and skills on it. ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("[Accept]").withStyle(style -> style.withColor(ChatFormatting.GREEN).withBold(true)
                        .withClickEvent(new ClickEvent.RunCommand("/coop accept " + player.getPlainTextName()))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Join the co-op (a new profile for you)"))))));
        return 1;
    }

    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer inviter = EntityArgument.getPlayer(ctx, "player");
        Map<UUID, Invite> mine = INVITES.getOrDefault(player.getUUID(), Map.of());
        Invite invite = mine.get(inviter.getUUID());
        if (invite == null || invite.expires() < System.currentTimeMillis()) {
            ctx.getSource().sendFailure(Component.literal("You have no co-op invite from " + inviter.getPlainTextName() + "."));
            return 0;
        }
        Profile profile = Profiles.get(invite.profile());
        if (profile == null) {
            return 0;
        }
        mine.remove(inviter.getUUID());
        if (!Profiles.joinCoop(player, profile)) {
            return 0;
        }
        inviter.sendSystemMessage(Component.literal(player.getPlainTextName() + " joined your co-op!").withStyle(ChatFormatting.GREEN));
        ProfileListPayload.send(inviter);
        return 1;
    }
}
