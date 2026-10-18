package com.skyrpg.skyblock.rift;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Rift (Hypixel's, SkyBlock Level 12): a dimension you visit on a timer. You step through the portal on the top
 * floor of the Hub's Wizard Tower with a Dimensional Infusion (one free every 4 hours, up to 3), arrive in the Rift's
 * Wizard Tower and have Rift Time (8:00 plus your gear's) that runs down a second a second everywhere but the tower.
 * Nothing hurts you there: monsters' hits take Rift Time instead, and your hits deal your Rift Damage (20 plus your
 * gear's). At 10 seconds left the Rift collapses on you, at 0 you're back at the Hub's Wizard Tower; walking out
 * through the tower's portal is the way home before that. The infusion is used when you first leave the tower.
 */
public final class Rift {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, SkyRpgSkyblock.id("rift"));
    static final int BASE_TIME = 480;
    static final int BASE_DAMAGE = 20;
    static final int LEVEL_NEEDED = 12;
    static final int MAX_INFUSIONS = 3;
    static final long INFUSION_MS = 4L * 60 * 60 * 1000;
    /** The zone where Rift Time stands still. */
    static final String TOWER = "Wizard Tower";

    /** A spot the builds recorded: position and yaw. */
    public record Spot(Vec3 pos, float yaw) {
    }

    /** Rift Time left and the most you had this visit ("123/480"), synced to you while you're in the Rift. */
    public static final AttachmentType<String> HUD = AttachmentRegistry.create(SkyRpgSkyblock.id("rift_hud"),
            builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));
    /** Dimensional Infusions: [stored, when the last free one was counted]. */
    static final AttachmentType<List<Long>> INFUSIONS = AttachmentRegistry.create(SkyRpgSkyblock.id("rift_infusions"),
            builder -> builder.persistent(Codec.LONG.listOf()).copyOnDeath());

    /** A visit in progress. */
    static final class Visit {
        double left;
        double max;
        boolean infusionUsed;
        boolean warned;
        long lastHit;
    }

    private static Map<String, Spot> spots = Map.of();
    private static Map<String, Spot> hubSpots = Map.of();
    private static final Map<UUID, Visit> VISITS = new HashMap<>();

    private Rift() {
    }

    public static void init() {
        Motes.init();
        EnigmaSouls.init();
        Orbs.init();
        RiftNpcs.init();
        JsonDataLoader.register(SkyRpgSkyblock.id("rift_spots"), "skyrpg/spawn_spots", Rift::load);
        ServerTickEvents.END_SERVER_TICK.register(Rift::tick);
        // Your hits in the Rift deal your Rift Damage, whatever you hold.
        IncomingDamage.registerOverride((target, source) -> {
            if (isRift(target.level()) && source.getEntity() instanceof ServerPlayer attacker && !(target instanceof Player)) {
                return damage(attacker) / StatTypes.HEALTH_SCALE;
            }
            return Double.NaN;
        });
        // Nothing hurts you in the Rift: a monster's hit takes that many seconds of Rift Time instead.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player) || !isRift(player.level())) {
                return true;
            }
            Visit visit = VISITS.get(player.getUUID());
            long tick = player.level().getServer().getTickCount();
            // A hit takes half its damage in seconds, at most one hit every half second (cancelled hits leave no
            // invulnerability frames behind). Decided: Hypixel's per-mob figures weren't found.
            if (visit != null && source.getEntity() != null && source.getEntity() != player && visit.left > 10 && tick - visit.lastHit >= 10) {
                visit.lastHit = tick;
                long seconds = Math.max(1, Math.round(amount * StatTypes.HEALTH_SCALE / 2));
                visit.left = Math.max(10, visit.left - seconds);
                player.sendOverlayMessage(Component.literal("-" + seconds + "s ф Rift Time").withStyle(ChatFormatting.RED));
            }
            return false;
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
            if (isRift(from) && !isRift(to)) {
                end(player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> VISITS.remove(handler.player.getUUID()));
        // Logged out in the Rift: you wake up at the Hub's Wizard Tower.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            if (isRift(handler.player.level()) && !VISITS.containsKey(handler.player.getUUID())) {
                sendHome(handler.player, "The Rift closed while you were away.");
            }
        }));
        Actions.register("rift_wizard", (player, params) -> RiftNpcs.wizard(player));
        Actions.register("enigma", (player, params) -> RiftNpcs.enigma(player));
        Actions.register("rift_npc", (player, params) -> RiftNpcs.talk(player, params.get("name").getAsString()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(Commands.literal("sbrift")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("enter").executes(ctx -> {
                    enter(ctx.getSource().getPlayerOrException(), true);
                    return 1;
                }))
                .then(Commands.literal("leave").executes(ctx -> {
                    sendHome(ctx.getSource().getPlayerOrException(), "You left the Rift.");
                    return 1;
                }))
                .then(Commands.literal("time").then(Commands.argument("seconds", IntegerArgumentType.integer(0)).executes(ctx -> {
                    Visit visit = VISITS.get(ctx.getSource().getPlayerOrException().getUUID());
                    if (visit != null) {
                        visit.left = IntegerArgumentType.getInteger(ctx, "seconds");
                        visit.warned = false;
                    }
                    return 1;
                })))
                .then(Commands.literal("motes").then(Commands.argument("amount", IntegerArgumentType.integer(0)).executes(ctx -> {
                    Motes.give(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "amount"));
                    return 1;
                })))
                .then(Commands.literal("infusion").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    setInfusions(player, Math.min(MAX_INFUSIONS, infusions(player) + 1));
                    return 1;
                }))));
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, Spot> rift = new HashMap<>();
        Map<String, Spot> hub = new HashMap<>();
        files.forEach((id, element) -> {
            JsonObject root = element.getAsJsonObject();
            if (!root.has("dimension")) {
                return;
            }
            String dimension = root.get("dimension").getAsString();
            Map<String, Spot> into = dimension.equals(LEVEL.identifier().toString()) ? rift : dimension.equals("skyrpg_hub:hub") ? hub : null;
            if (into == null) {
                return;
            }
            root.getAsJsonObject("spots").entrySet().forEach(e -> {
                JsonObject spot = e.getValue().getAsJsonObject();
                var pos = spot.getAsJsonArray("pos");
                into.put(e.getKey(), new Spot(new Vec3(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble()),
                        spot.has("yaw") ? spot.get("yaw").getAsFloat() : 0));
            });
        });
        spots = Map.copyOf(rift);
        hubSpots = Map.copyOf(hub);
    }

    static Map<String, Spot> spots() {
        return spots;
    }

    public static boolean isRift(Level level) {
        return level.dimension().equals(LEVEL);
    }

    static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    /** Your Rift Damage: 20 and your gear's. */
    static double damage(ServerPlayer player) {
        return BASE_DAMAGE + Stats.get(player, StatTypes.RIFT_DAMAGE);
    }

    /** Your Rift Time at the start of a visit: 8:00 and your gear's (Enigma souls, Wyld armour...). */
    static double startTime(ServerPlayer player) {
        return BASE_TIME + Stats.get(player, StatTypes.RIFT_TIME);
    }

    // ---------------------------------------------------------------- infusions

    /** Infusions stored now, counting the free ones that came in (one every 4 hours, up to 3). */
    static int infusions(ServerPlayer player) {
        List<Long> state = player.getAttachedOrElse(INFUSIONS, List.of((long) MAX_INFUSIONS, System.currentTimeMillis()));
        long stored = state.get(0);
        long since = state.get(1);
        long now = System.currentTimeMillis();
        if (stored < MAX_INFUSIONS) {
            long gained = (now - since) / INFUSION_MS;
            stored = Math.min(MAX_INFUSIONS, stored + gained);
            since = stored >= MAX_INFUSIONS ? now : since + gained * INFUSION_MS;
        } else {
            since = now;
        }
        player.setAttached(INFUSIONS, List.of(stored, since));
        return (int) stored;
    }

    /** When the next free infusion comes (ms from now), 0 when full. */
    static long nextInfusion(ServerPlayer player) {
        if (infusions(player) >= MAX_INFUSIONS) {
            return 0;
        }
        return Math.max(0, player.getAttachedOrElse(INFUSIONS, List.of(0L, 0L)).get(1) + INFUSION_MS - System.currentTimeMillis());
    }

    static void setInfusions(ServerPlayer player, int stored) {
        infusions(player);
        player.setAttached(INFUSIONS, List.of((long) stored, player.getAttachedOrElse(INFUSIONS, List.of(0L, System.currentTimeMillis())).get(1)));
    }

    // ---------------------------------------------------------------- going in and out

    /** Through the Hub's portal (or {@code force} for admins): SkyBlock Level 12 and an infusion. */
    static void enter(ServerPlayer player, boolean force) {
        ServerLevel rift = level(player.level().getServer());
        Spot arrival = spots.get("warp:rift");
        if (rift == null || arrival == null) {
            player.sendSystemMessage(Component.literal("The Rift isn't open yet.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!force && SkyBlockXp.level(player) < LEVEL_NEEDED) {
            player.sendSystemMessage(Component.literal("The Rift opens at SkyBlock Level " + LEVEL_NEEDED + ".").withStyle(ChatFormatting.RED));
            pushBack(player);
            return;
        }
        if (!force && infusions(player) <= 0) {
            player.sendSystemMessage(Component.literal("You need a Dimensional Infusion. Talk to the Wizard.").withStyle(ChatFormatting.RED));
            pushBack(player);
            return;
        }
        Visit visit = new Visit();
        visit.left = startTime(player);
        visit.max = visit.left;
        visit.infusionUsed = force;
        VISITS.put(player.getUUID(), visit);
        player.teleportTo(rift, arrival.pos().x, arrival.pos().y, arrival.pos().z, java.util.Set.of(), arrival.yaw(), 0, true);
        Notice.of(Notice.Kind.INFO).tag("THE RIFT").panel()
                .line(Component.literal(" Rift Time runs out outside the Wizard Tower: " + clock(visit.left) + ".").withStyle(ChatFormatting.GRAY))
                .line(Component.literal(" Monsters take time, not health. Mind the clock.").withStyle(ChatFormatting.GRAY))
                .send(player, Component.literal("You entered the Rift!").withStyle(ChatFormatting.LIGHT_PURPLE));
        Msg.playSound(player, SoundEvents.PORTAL_TRAVEL, 0.4f, 1.3f);
        hud(player, visit);
    }

    /** Back to the Hub's Wizard Tower. */
    static void sendHome(ServerPlayer player, String why) {
        VISITS.remove(player.getUUID());
        player.removeAttached(HUD);
        ServerLevel hub = player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse("skyrpg_hub:hub")));
        Spot back = hubSpots.get("rift_return");
        if (hub != null && back != null) {
            player.teleportTo(hub, back.pos().x, back.pos().y, back.pos().z, java.util.Set.of(), back.yaw(), 0, true);
        } else if (hub != null) {
            player.teleportTo(hub, 0.5, 65, 0.5, java.util.Set.of(), 0, 0, true);
        }
        player.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private static void end(ServerPlayer player) {
        VISITS.remove(player.getUUID());
        player.removeAttached(HUD);
    }

    /** A step back from a portal you can't use yet. */
    private static void pushBack(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        player.teleportTo(player.getX() - look.x * 1.5, player.getY(), player.getZ() - look.z * 1.5);
    }

    /** Whether you're walking into the portal a spot faces (the frame stands about two blocks in front of it). */
    private static boolean inPortal(ServerPlayer player, Spot spot) {
        double yaw = Math.toRadians(spot.yaw());
        double x = spot.pos().x - Math.sin(yaw) * 2.2;
        double z = spot.pos().z + Math.cos(yaw) * 2.2;
        double dx = player.getX() - x;
        double dz = player.getZ() - z;
        double dy = player.getY() - spot.pos().y;
        return dx * dx + dz * dz < 1.7 && dy > -0.5 && dy < 3;
    }

    // ---------------------------------------------------------------- the clock

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) {
            return;
        }
        boolean second = server.getTickCount() % 20 == 0;
        // The Hub's portal.
        Spot portal = hubSpots.get("rift_portal");
        if (portal != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level().dimension().identifier().toString().equals("skyrpg_hub:hub") && inPortal(player, portal)) {
                    enter(player, false);
                }
            }
        }
        ServerLevel rift = level(server);
        if (rift == null) {
            return;
        }
        Spot exit = spots.get("exit_portal");
        for (ServerPlayer player : new ArrayList<>(rift.players())) {
            Visit visit = VISITS.computeIfAbsent(player.getUUID(), id -> {
                Visit v = new Visit();
                v.left = startTime(player);
                v.max = v.left;
                return v;
            });
            if (exit != null && inPortal(player, exit)) {
                sendHome(player, "You left the Rift.");
                continue;
            }
            // Fell off the edge of the world: back to the tower, ten seconds poorer.
            if (player.getY() < -10 && spots.get("warp:rift") != null) {
                Spot arrival = spots.get("warp:rift");
                player.teleportTo(rift, arrival.pos().x, arrival.pos().y, arrival.pos().z, java.util.Set.of(), arrival.yaw(), 0, true);
                visit.left = Math.max(1, visit.left - 10);
            }
            if (!second) {
                continue;
            }
            Zones.Zone zone = Zones.at(rift, player.blockPosition());
            boolean tower = zone != null && zone.name().equals(TOWER);
            if (!tower) {
                if (!visit.infusionUsed) {
                    visit.infusionUsed = true;
                    setInfusions(player, Math.max(0, infusions(player) - 1));
                    player.sendSystemMessage(Component.literal("Your Dimensional Infusion fades: the Rift's clock is ticking.").withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                if (!player.isCreative()) {
                    visit.left -= 1;
                }
            }
            if (visit.left <= 10 && !visit.warned) {
                visit.warned = true;
                Notice.of(Notice.Kind.INFO).tag("THE RIFT").send(player, Component.literal("THE RIFT IS COLLAPSING. You only have 10 seconds left!")
                        .withStyle(ChatFormatting.RED));
                Msg.playSound(player, SoundEvents.WARDEN_HEARTBEAT, 1f, 0.8f);
            }
            if (visit.left <= 0) {
                sendHome(player, "The Rift collapsed. You were sent back to the Wizard Tower.");
                continue;
            }
            hud(player, visit);
        }
    }

    /** Rift Time gained (mote orbs, kills). */
    static void addTime(ServerPlayer player, double seconds) {
        Visit visit = VISITS.get(player.getUUID());
        if (visit != null) {
            visit.left += seconds;
            visit.max = Math.max(visit.max, visit.left);
            if (visit.left > 10) {
                visit.warned = false;
            }
            hud(player, visit);
        }
    }

    private static void hud(ServerPlayer player, Visit visit) {
        String line = (int) Math.ceil(visit.left) + "/" + (int) Math.ceil(visit.max);
        if (!line.equals(player.getAttached(HUD))) {
            player.setAttached(HUD, line);
        }
    }

    /** "7:59". */
    static String clock(double seconds) {
        int s = (int) Math.ceil(Math.max(0, seconds));
        return s / 60 + ":" + String.format("%02d", s % 60);
    }
}
