package com.skyrpg.skyblock.mining;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.zone.Zones;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * Hypixel's Dwarven Mines events, on a schedule everybody shares: 2x Powder (every powder gain doubles), Gone with the
 * Wind (+600 Mining Speed while you face the wind, +300 when you're close to it), Better Together (+250 Mining Speed and
 * +20 Mining Fortune for each player in your zone, up to 5) and the Goblin Raid ({@link GoblinRaid}). Each lasts as long
 * as on Hypixel (the Raid 5 minutes, Gone with the Wind 15, Better Together 20, 2x Powder 10), is announced 20 seconds
 * ahead to everyone in the Mines, and shows in the sidebar there while it runs.
 *
 * <p>Decided: the schedule is the clock (every half hour of real time has a 2 in 3 chance of an event, and which one is
 * drawn from that half hour's number), so it needs no saved state and every server agrees; Hypixel's random timing isn't
 * public. Raffle (needs 10 players and a ticket hunt) and Mithril Gourmand (Tasty Mithril and Don Expresso's errand) are
 * not here. The events run only on the Dwarven Mines island, not the Crystal Hollows.
 * {@code /sbminingevent start <event> | stop | status} (admins) starts one now (announced at once) for testing.
 */
public final class MiningEvents {
    /** One line of sidebar text (colour codes in), synced to its player while an event runs on their island. */
    public static final AttachmentType<String> SIDEBAR = AttachmentRegistry.create(SkyRpgSkyblock.id("mining_event_sidebar"),
            builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));

    public enum Kind {
        DOUBLE_POWDER("2x Powder", "double_powder", ChatFormatting.AQUA, 10 * 60, "Passive Active Event",
                "Everybody gets twice as much powder of all types!"),
        WIND("Gone with the Wind", "wind", ChatFormatting.BLUE, 15 * 60, "Passive Active Event",
                "The cavern is windy today! Mining in the direction of the wind will give +600 Mining Speed."),
        BETTER_TOGETHER("Better Together", "better_together", ChatFormatting.LIGHT_PURPLE, 20 * 60, "Passive Active Event",
                "While this event is active, gain +250 Mining Speed and +20 Mining Fortune for each player in the same zone as you! (Max 5 players)"),
        GOBLIN_RAID("Goblin Raid", "goblin_raid", ChatFormatting.RED, 5 * 60, "Mining Event",
                "Goblins are raiding the Goblin Burrows! Slay them all before time runs out, and slay at least 25 yourself for extra rewards.");

        final String title;
        final String key;
        final ChatFormatting color;
        final int seconds;
        final String type;
        final String text;

        Kind(String title, String key, ChatFormatting color, int seconds, String type, String text) {
            this.title = title;
            this.key = key;
            this.color = color;
            this.seconds = seconds;
            this.type = type;
            this.text = text;
        }
    }

    /** An event on now: what, when it began and when it ends (real milliseconds). */
    record Active(Kind kind, long start, long end) {
    }

    /** One slot of the schedule: half an hour. */
    private static final long SLOT = 30 * 60_000L;
    private static final long WARNING = 20_000L;
    private static final String[] COMPASS = {"south", "west", "north", "east"};

    private static Active forced;
    /** The half hour whose scheduled event an admin stopped. */
    private static long stoppedSlot = -1;
    private static Active current;
    private static long warnedSlot = -1;
    /** What each player gets from Gone with the Wind and Better Together now: Mining Speed, Mining Fortune. */
    private static final Map<UUID, double[]> BUFFS = new HashMap<>();

    private MiningEvents() {
    }

    static void init() {
        SIDEBAR.identifier();
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Mining Event";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                double[] buff = BUFFS.get(player.getUUID());
                if (buff != null) {
                    out.add(StatTypes.MINING_SPEED, buff[0]);
                    out.add(StatTypes.MINING_FORTUNE, buff[1]);
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(MiningEvents::tick);
        GoblinRaid.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("sbminingevent")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("start").then(Commands.argument("event", StringArgumentType.word())
                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                java.util.Arrays.stream(Kind.values()).map(k -> k.key), builder))
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "event");
                            for (Kind kind : Kind.values()) {
                                if (kind.key.equals(name)) {
                                    long now = System.currentTimeMillis();
                                    forced = new Active(kind, now, now + kind.seconds * 1000L);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Started " + kind.title + " for "
                                            + kind.seconds / 60 + " minutes."), true);
                                    return 1;
                                }
                            }
                            ctx.getSource().sendFailure(Component.literal("Events: " + String.join(", ",
                                    java.util.Arrays.stream(Kind.values()).map(k -> k.key).toList())));
                            return 0;
                        })))
                .then(Commands.literal("stop").executes(ctx -> {
                    forced = null;
                    stoppedSlot = Math.floorDiv(System.currentTimeMillis(), SLOT);
                    ctx.getSource().sendSuccess(() -> Component.literal("Stopped the running event."), true);
                    return 1;
                }))
                .then(Commands.literal("spawn").then(Commands.argument("what", StringArgumentType.word()).executes(ctx -> {
                    String what = StringArgumentType.getString(ctx, "what");
                    boolean ok = MinesGoblins.spawnFor(ctx.getSource().getPlayerOrException(), what);
                    if (!ok) {
                        ctx.getSource().sendFailure(Component.literal("golden, diamond or ghast"));
                    }
                    return ok ? 1 : 0;
                })))
                .then(Commands.literal("status").executes(ctx -> {
                    Active now = at(System.currentTimeMillis());
                    ctx.getSource().sendSuccess(() -> Component.literal(now == null ? "No event now. Next: " + describeNext(System.currentTimeMillis())
                            : now.kind.title + ", " + (now.end - System.currentTimeMillis()) / 1000 + "s left."), false);
                    return 1;
                }))));
    }

    /** Whether 2x Powder is on (for the island's powder gains). */
    public static boolean doublePowder() {
        return current != null && current.kind == Kind.DOUBLE_POWDER;
    }

    static Active current() {
        return current;
    }

    // ---------------------------------------------------------------- the schedule

    private static Kind scheduled(long slot) {
        Random random = new Random(slot * 0x9E3779B97F4A7C15L + 17);
        if (random.nextInt(3) == 0) {
            return null;
        }
        return Kind.values()[random.nextInt(Kind.values().length)];
    }

    /** The event on at this moment (a forced one first), or null. */
    private static Active at(long now) {
        if (forced != null && now < forced.end) {
            return forced;
        }
        long slot = Math.floorDiv(now, SLOT);
        Kind kind = slot == stoppedSlot ? null : scheduled(slot);
        if (kind == null) {
            return null;
        }
        long start = slot * SLOT;
        long end = start + kind.seconds * 1000L;
        return now < end ? new Active(kind, start, end) : null;
    }

    private static String describeNext(long now) {
        for (long slot = Math.floorDiv(now, SLOT) + 1; slot < Math.floorDiv(now, SLOT) + 20; slot++) {
            Kind kind = scheduled(slot);
            if (kind != null) {
                return kind.title + " in " + (slot * SLOT - now) / 60_000 + " minutes";
            }
        }
        return "none soon";
    }

    // ---------------------------------------------------------------- running

    private static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (tick % 20 == 0) {
            long now = System.currentTimeMillis();
            Active next = at(now);
            if (!java.util.Objects.equals(next, current)) {
                if (current != null) {
                    ended(server, current);
                }
                current = next;
                if (next != null) {
                    started(server, next);
                }
            }
            // The 20 second warning for the next slot's event.
            long slot = Math.floorDiv(now, SLOT) + 1;
            if (slot != warnedSlot && slot * SLOT - now <= WARNING && scheduled(slot) != null) {
                warnedSlot = slot;
                announce(server, scheduled(slot));
            }
            sidebar(server, now);
            GoblinRaid.tick(server, current);
        }
        if (tick % 10 == 0) {
            buffs(server);
        }
    }

    private static void announce(MinecraftServer server, Kind kind) {
        ServerLevel mines = server.getLevel(SkyMining.DWARVEN_MINES);
        if (mines == null) {
            return;
        }
        for (ServerPlayer player : mines.players()) {
            player.sendSystemMessage(Component.literal("⚑ ").withStyle(kind.color)
                    .append(Component.literal("The ").withStyle(ChatFormatting.YELLOW)).append(Component.literal(kind.title).withStyle(kind.color))
                    .append(Component.literal(" event starts in ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("20").withStyle(ChatFormatting.GREEN)).append(Component.literal(" seconds!").withStyle(ChatFormatting.YELLOW)));
            player.sendSystemMessage(Component.literal(kind == Kind.GOBLIN_RAID ? "This is an active event! Head to the Goblin Burrows."
                    : "This is a passive event! It's happening everywhere in the Dwarven Mines!").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void started(MinecraftServer server, Active event) {
        ServerLevel mines = server.getLevel(SkyMining.DWARVEN_MINES);
        if (mines == null) {
            return;
        }
        String extra = event.kind == Kind.WIND ? " It blows from the " + windName(event) + "." : "";
        for (ServerPlayer player : mines.players()) {
            Notice.Panel panel = Notice.of(Notice.Kind.EVENT).tag("MINING EVENT").panel();
            panel.line(Component.literal(event.kind.type).withStyle(ChatFormatting.GRAY));
            panel.line(Component.literal(event.kind.text + extra).withStyle(ChatFormatting.WHITE));
            panel.send(player, Component.literal(event.kind.title.toUpperCase(Locale.ROOT) + " STARTED!").withStyle(event.kind.color, ChatFormatting.BOLD));
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.0f);
        }
    }

    private static void ended(MinecraftServer server, Active event) {
        ServerLevel mines = server.getLevel(SkyMining.DWARVEN_MINES);
        if (mines == null) {
            return;
        }
        String line = switch (event.kind) {
            case DOUBLE_POWDER -> "Powder amount is back to normal, but there will be another event soon!";
            case WIND -> "The wind has died down leading to normal mining speeds.";
            case BETTER_TOGETHER -> "The festival has ended, but the Dwarven cooperative spirit never dies.";
            case GOBLIN_RAID -> "The Goblins have gone back to their burrows.";
        };
        for (ServerPlayer player : mines.players()) {
            Notice.Panel panel = Notice.of(Notice.Kind.EVENT).tag("MINING EVENT").panel();
            panel.line(Component.literal(event.kind.type).withStyle(ChatFormatting.GRAY));
            panel.line(Component.literal(line).withStyle(ChatFormatting.WHITE));
            panel.send(player, Component.literal(event.kind.title.toUpperCase(Locale.ROOT) + " ENDED!").withStyle(event.kind.color, ChatFormatting.BOLD));
        }
    }

    // ---------------------------------------------------------------- the wind and the crowd

    /** The way the wind blows: a yaw (0 south, 90 west, 180 north, 270 east), from the event's start. */
    private static int windYaw(Active event) {
        return new Random(event.start).nextInt(4) * 90;
    }

    private static String windName(Active event) {
        return COMPASS[windYaw(event) / 90];
    }

    private static void buffs(MinecraftServer server) {
        ServerLevel mines = server.getLevel(SkyMining.DWARVEN_MINES);
        Active event = current;
        Map<UUID, double[]> now = new HashMap<>();
        if (mines != null && event != null && (event.kind == Kind.WIND || event.kind == Kind.BETTER_TOGETHER)) {
            Map<String, Integer> zones = new HashMap<>();
            for (ServerPlayer player : mines.players()) {
                zones.merge(zoneOf(mines, player), 1, Integer::sum);
            }
            for (ServerPlayer player : mines.players()) {
                if (event.kind == Kind.WIND) {
                    double speed = windSpeed(event, player);
                    if (speed > 0) {
                        now.put(player.getUUID(), new double[] {speed, 0});
                    }
                } else {
                    int crowd = Math.min(5, zones.getOrDefault(zoneOf(mines, player), 1));
                    now.put(player.getUUID(), new double[] {250.0 * crowd, 20.0 * crowd});
                }
            }
        }
        // Players whose buff changed (or went) get their stats recomputed now.
        java.util.Set<UUID> changed = new java.util.HashSet<>();
        for (Map.Entry<UUID, double[]> entry : now.entrySet()) {
            double[] before = BUFFS.get(entry.getKey());
            if (before == null || before[0] != entry.getValue()[0] || before[1] != entry.getValue()[1]) {
                changed.add(entry.getKey());
            }
        }
        for (UUID gone : BUFFS.keySet()) {
            if (!now.containsKey(gone)) {
                changed.add(gone);
            }
        }
        BUFFS.clear();
        BUFFS.putAll(now);
        for (UUID id : changed) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                Stats.refresh(player);
            }
        }
    }

    private static String zoneOf(ServerLevel level, ServerPlayer player) {
        Zones.Zone zone = Zones.at(level, player.blockPosition());
        return zone == null ? "" : zone.name();
    }

    private static double windSpeed(Active event, ServerPlayer player) {
        double off = Math.abs(Mth.wrapDegrees(player.getYRot() - windYaw(event)));
        return off <= 30 ? 600 : off <= 60 ? 300 : 0;
    }

    // ---------------------------------------------------------------- the sidebar

    private static void sidebar(MinecraftServer server, long now) {
        Active event = current;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String line = null;
            if (event != null && player.level().dimension().equals(SkyMining.DWARVEN_MINES)) {
                long left = Math.max(0, (event.end - now) / 1000);
                StringBuilder text = new StringBuilder("§6Event: " + color(event.kind) + event.kind.title);
                text.append("\n§fEnds in §a").append(left / 60).append(":").append(String.format("%02d", left % 60));
                if (event.kind == Kind.WIND) {
                    double speed = windSpeed(event, player);
                    text.append("\n").append(speed >= 600 ? "§2" : speed >= 300 ? "§a" : "§f").append("≈ §7Wind from the ")
                            .append(windName(event));
                } else if (event.kind == Kind.GOBLIN_RAID) {
                    text.append(GoblinRaid.sidebar(player));
                }
                line = text.toString();
            }
            if (line == null) {
                if (player.getAttached(SIDEBAR) != null) {
                    player.removeAttached(SIDEBAR);
                }
            } else if (!line.equals(player.getAttached(SIDEBAR))) {
                player.setAttached(SIDEBAR, line);
            }
        }
    }

    private static String color(Kind kind) {
        return kind.color.toString();
    }
}
