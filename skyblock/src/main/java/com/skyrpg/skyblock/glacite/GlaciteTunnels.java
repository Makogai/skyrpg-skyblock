package com.skyrpg.skyblock.glacite;

import com.mojang.serialization.Codec;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.hub.HubAreas;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.mining.Hotm;
import com.skyrpg.skyblock.mining.SkyMining;
import com.skyrpg.skyblock.zone.Zones;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's Glacite Tunnels (a generated world, {@code tools/skyblock/gen_glacite.py}) and the Dwarven Base Camp at their
 * north end. The Ticket Master in the Dwarven Mines takes you there from Heart of the Mountain 7, once you've handed him
 * a Secret Railroad Pass (forged at the Forge); the one at the Base Camp takes you back.
 *
 * <p>Cold: in the Tunnels it builds by 1 every 5 seconds (longer with Cold Resistance: 5 x (1 + Cold Resistance / 100);
 * four times as fast in water). Each point of Cold over your Cold Resistance takes 0.5% off your Mining Speed; at 75 you
 * slow down, at 90 frostbite, at 100 you freeze and wake up at the Base Camp. The Base Camp and leaving the Tunnels take
 * it away. It shows on the sidebar.
 */
public final class GlaciteTunnels {
    public static final int REQUIRED_HOTM = 7;
    public static final String PASS = "SECRET_RAILROAD_PASS";
    public static final String BASE_CAMP = "Dwarven Base Camp";
    /** The player's Cold, 0 to 100 (shown on the sidebar). */
    public static final AttachmentType<Integer> COLD = AttachmentRegistry.create(SkyRpgSkyblock.id("cold"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
    /** The Secret Railroad Pass was handed in: the Base Camp is open. */
    public static final AttachmentType<Boolean> RAILROAD = AttachmentRegistry.create(SkyRpgSkyblock.id("railroad_pass"),
            builder -> builder.persistent(Codec.BOOL).copyOnDeath());

    private static final Map<UUID, Double> COLD_EXACT = new HashMap<>();
    /** The Mining Speed Cold took off last time, so the next count leaves it out. */
    private static final Map<UUID, Double> PENALTY = new HashMap<>();

    private GlaciteTunnels() {
    }

    public static void init() {
        Actions.register("base_camp", (player, params) -> travel(player));
        Actions.register("leave_base_camp", (player, params) -> leave(player));
        Mineshafts.init();
        Fossils.init();
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                server.getPlayerList().getPlayers().forEach(GlaciteTunnels::second);
            }
        });
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Cold";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                int cold = cold(player);
                double over = cold - Math.max(0, Stats.get(player, StatTypes.COLD_RESISTANCE));
                if (over <= 0) {
                    PENALTY.remove(player.getUUID());
                    return;
                }
                StatType speed = StatTypes.MINING_SPEED;
                double base = Stats.get(player, speed) + PENALTY.getOrDefault(player.getUUID(), 0.0);
                double penalty = Math.max(0, base) * Math.min(1, over * 0.005);
                PENALTY.put(player.getUUID(), penalty);
                out.add(speed, -penalty);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("sbtunnels")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("rebuild").executes(ctx -> {
                    // After the Tunnels' world was made anew: place the Base Camp again.
                    HubAreas.replaceAll(ctx.getSource().getServer(), SkyMining.GLACITE_TUNNELS);
                    return 1;
                }))
                .then(Commands.literal("go").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    toBaseCamp(EntityArgument.getPlayer(ctx, "player"));
                    return 1;
                })))
                .then(Commands.literal("cold").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("cold", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 100)).executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            setCold(target, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "cold"));
                            return 1;
                        }))))));
    }

    public static int cold(ServerPlayer player) {
        return player.getAttachedOrElse(COLD, 0);
    }

    private static void setCold(ServerPlayer player, double cold) {
        COLD_EXACT.put(player.getUUID(), cold);
        int shown = (int) Math.floor(cold);
        if (player.getAttachedOrElse(COLD, 0) != shown) {
            player.setAttached(COLD, shown);
            Stats.refresh(player);
        }
    }

    /** The Ticket Master in the Dwarven Mines: HotM 7 and, the first time, the Secret Railroad Pass. */
    private static void travel(ServerPlayer player) {
        if (Hotm.level(player) < REQUIRED_HOTM) {
            refuse(player, "You need Heart of the Mountain " + REQUIRED_HOTM + " to ride down to the Glacite Tunnels!");
            return;
        }
        if (!player.getAttachedOrElse(RAILROAD, false)) {
            if (SkyStacks.count(player.getInventory(), PASS) < 1) {
                refuse(player, "You need a Secret Railroad Pass! Forge one at the Forge.");
                return;
            }
            SkyStacks.take(player.getInventory(), PASS, 1);
            player.setAttached(RAILROAD, true);
            Notice.of(Notice.Kind.MINING).tag("TICKET MASTER").send(player, Component.literal("Your pass is good. The Dwarven Base Camp is open to you!")
                    .withStyle(ChatFormatting.AQUA));
        }
        toBaseCamp(player);
    }

    private static void refuse(ServerPlayer player, String why) {
        player.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
        Msg.playSound(player, SoundEvents.VILLAGER_NO, 0.8f, 1.0f);
    }

    static void toBaseCamp(ServerPlayer player) {
        go(player, SkyMining.GLACITE_TUNNELS);
        Msg.playSound(player, SoundEvents.MINECART_RIDING, 0.6f, 1.0f);
    }

    /** The Ticket Master at the Base Camp: back up to the Dwarven Mines. */
    private static void leave(ServerPlayer player) {
        go(player, SkyMining.DWARVEN_MINES);
    }

    private static void go(ServerPlayer player, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server.getLevel(dimension);
        HubAreas.Arrival arrival = HubAreas.arrival(dimension);
        if (level == null || arrival == null) {
            refuse(player, "The way there isn't built yet.");
            return;
        }
        player.teleport(new net.minecraft.world.level.portal.TeleportTransition(level, arrival.pos(), Vec3.ZERO, arrival.yaw(), 0, Set.of(),
                net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
    }

    /** Every second: Cold builds in the Tunnels (not at the Base Camp) and the Mineshafts, goes away elsewhere. */
    private static void second(ServerPlayer player) {
        ServerLevel level = player.level();
        boolean shaft = level.dimension().equals(Mineshafts.LEVEL);
        if (!level.dimension().equals(SkyMining.GLACITE_TUNNELS) && !shaft) {
            if (COLD_EXACT.remove(player.getUUID()) != null || cold(player) > 0) {
                setCold(player, 0);
            }
            return;
        }
        Zones.Zone zone = Zones.at(level, player.blockPosition());
        double cold = COLD_EXACT.getOrDefault(player.getUUID(), (double) cold(player));
        if (zone != null && zone.name().equals(BASE_CAMP) || player.isCreative() || player.isSpectator()) {
            setCold(player, 0);
            return;
        }
        double resistance = Math.max(0, Stats.get(player, StatTypes.COLD_RESISTANCE));
        double vulnerability = (player.isInWater() ? 3 : 0) + (shaft ? Mineshafts.vulnerability(player) : 0);
        int before = (int) cold;
        cold = Math.min(100, cold + (1 + vulnerability) / (5 * (1 + resistance / 100)));
        int now = (int) cold;
        setCold(player, cold);
        if (now != before) {
            warn(player, before, now);
        }
        if (now >= 75) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, now >= 90 ? 1 : 0, true, false, false));
        }
        if (now >= 100) {
            freeze(player);
        }
    }

    private static void warn(ServerPlayer player, int before, int now) {
        int[] marks = {25, 50, 75, 90};
        String[] texts = {"It's getting really cold in here!", "It's so cold that you can barely feel your fingers.",
                "Your movement slows to a crawl...", "You're freezing!"};
        for (int i = 0; i < marks.length; i++) {
            if (before < marks[i] && now >= marks[i]) {
                Notice.of(Notice.Kind.MINING).tag("BRRR!").color(0x55FFFF).send(player, Component.literal(texts[i]).withStyle(ChatFormatting.AQUA));
                Msg.playSound(player, SoundEvents.POWDER_SNOW_STEP, 1.0f, 0.8f);
                if (marks[i] == 90) {
                    player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("FROSTBITE").withColor(0x55FFFF)));
                    player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("Get back to the Base Camp!")
                            .withStyle(ChatFormatting.GRAY)));
                }
            }
        }
    }

    /** At 100 Cold you freeze, and wake up at the Base Camp. */
    private static void freeze(ServerPlayer player) {
        setCold(player, 0);
        player.sendSystemMessage(Component.literal("You froze to death!").withStyle(ChatFormatting.RED));
        Msg.playSound(player, SoundEvents.PLAYER_HURT_FREEZE, 1.0f, 0.8f);
        toBaseCamp(player);
    }
}
