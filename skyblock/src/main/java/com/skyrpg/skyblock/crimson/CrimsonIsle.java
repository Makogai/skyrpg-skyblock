package com.skyrpg.skyblock.crimson;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Crimson Isle (Hypixel's nether island, Combat XXII, by launch pad from the Spider's Den): three regions over a lava
 * sea, the Barbarians' Dragontail in the west and the Mages' Scarleton in the east, the Stronghold, the Blazing Volcano
 * and the Crimson Fields between. Here: Elle, the faction leaders and their people (NPC actions), the spots the island's
 * build recorded (minibosses, geysers, Kuudra's entrance), and the lava sea spitting fallen players back to land.
 * Factions and their daily quests: {@link Factions}, {@link FactionQuests}; the minibosses: {@link Minibosses}.
 */
public final class CrimsonIsle {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, SkyRpgSkyblock.id("crimson_isle"));
    /** The lava sea's top. */
    static final int LAVA = 59;
    /** Spots the build recorded, by name ("Bladesoul", "geyser 1"): position and yaw. */
    public record Spot(Vec3 pos, float yaw) {
    }

    private static Map<String, Spot> spots = Map.of();
    private static final Map<java.util.UUID, Integer> IN_LAVA = new HashMap<>();

    private CrimsonIsle() {
    }

    public static void init() {
        Factions.init();
        FactionQuests.init();
        Minibosses.init();
        Kuudra.init();
        Dojo.init();
        JsonDataLoader.register(SkyRpgSkyblock.id("crimson_spots"), "skyrpg/spawn_spots", CrimsonIsle::load);
        Actions.register("crimson_elle", (player, params) -> elle(player));
        Actions.register("crimson_leader", (player, params) -> leader(player, params.get("faction").getAsString()));
        Actions.register("crimson_npc", (player, params) -> person(player, params.get("name").getAsString()));
        Actions.register("open_auction", (player, params) -> command(player, "ah"));
        Actions.register("open_bazaar", (player, params) -> command(player, "bazaar"));
        // Vulcan adds Crimson stars to Kuudra armour; each star is +2% of the piece's stats (decided: Hypixel's figure wasn't found).
        Actions.register("vulcan", (player, params) -> com.skyrpg.skyblock.dungeon.MalikMenu.open(player, "Vulcan"));
        com.skyrpg.core.stats.Stats.registerSource(new com.skyrpg.core.stats.Stats.Source() {
            @Override
            public String name() {
                return "Crimson Stars";
            }

            @Override
            public void contribute(ServerPlayer player, com.skyrpg.core.stats.Stats.Contributor out) {
                for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
                        net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                        net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                    net.minecraft.world.item.ItemStack stack = player.getItemBySlot(slot);
                    ItemDefinition def = com.skyrpg.items.SkyItems.definition(stack);
                    int stars = com.skyrpg.items.ItemMods.stars(stack);
                    if (stars <= 0 || !com.skyrpg.skyblock.dungeon.DungeonItems.crimson(def)) {
                        continue;
                    }
                    com.skyrpg.items.ItemMods.stats(stack, def).forEach((stat, value) -> out.add(stat, value * 0.02 * stars));
                }
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(CrimsonIsle::tick);
        // The volcano's magma blocks are scenery: they don't burn your feet.
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer && isCrimson(entity.level()) && source.is(net.minecraft.world.damagesource.DamageTypes.HOT_FLOOR)));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(Commands.literal("sbcrimson")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("rep").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("faction", StringArgumentType.word()).then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    Factions.set(target, StringArgumentType.getString(ctx, "faction"), IntegerArgumentType.getInteger(ctx, "amount"));
                                    ctx.getSource().sendSuccess(() -> Component.literal("Reputation set."), false);
                                    return 1;
                                })))))
                .then(Commands.literal("elle").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("step", IntegerArgumentType.integer(0, 3)).executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            int step = IntegerArgumentType.getInteger(ctx, "step");
                            Factions.setFlag(target, "elle", step);
                            if (step >= Factions.ELLE_LEADERS) {
                                Factions.setFlag(target, "met_mage", 1);
                                Factions.setFlag(target, "met_barbarian", 1);
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Elle's step set."), false);
                            return 1;
                        }))))
                .then(Commands.literal("join").then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("faction", StringArgumentType.word()).executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            String faction = StringArgumentType.getString(ctx, "faction");
                            if (!faction.equals(Factions.MAGE) && !faction.equals(Factions.BARBARIAN)) {
                                ctx.getSource().sendFailure(Component.literal("mage or barbarian"));
                                return 0;
                            }
                            Factions.setFlag(target, "met_mage", 1);
                            Factions.setFlag(target, "met_barbarian", 1);
                            Factions.join(target, faction);
                            return 1;
                        }))))
                .then(Commands.literal("boss").then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                    boolean ok = Minibosses.spawnNow(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"));
                    ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Spawned." : "No such miniboss or spot."), false);
                    return ok ? 1 : 0;
                })))));
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, Spot> loaded = new HashMap<>();
        files.forEach((id, element) -> {
            JsonObject root = element.getAsJsonObject();
            if (!root.has("dimension") || !root.get("dimension").getAsString().equals(LEVEL.identifier().toString())) {
                return;
            }
            root.getAsJsonObject("spots").entrySet().forEach(e -> {
                JsonObject spot = e.getValue().getAsJsonObject();
                var pos = spot.getAsJsonArray("pos");
                loaded.put(e.getKey(), new Spot(new Vec3(pos.get(0).getAsDouble(), pos.get(1).getAsDouble(), pos.get(2).getAsDouble()),
                        spot.has("yaw") ? spot.get("yaw").getAsFloat() : 0));
            });
        });
        spots = Map.copyOf(loaded);
    }

    static Spot spot(String name) {
        return spots.get(name);
    }

    public static Map<String, Spot> spots() {
        return spots;
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    public static boolean isCrimson(Level level) {
        return level.dimension().equals(LEVEL);
    }

    /** A SkyBlock item's display name ("Magma Chunk"), or the id. */
    static String itemName(String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? id : def.name();
    }

    private static void command(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    /** The Blazing Volcano erupts: fire and smoke from the crater and every geyser. */
    public static void erupt(ServerLevel level) {
        Vec3 crater = new Vec3(0.5, 146, -164.5);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, crater.x, crater.y, crater.z, 120, 8, 4, 8, 0);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, crater.x, crater.y + 4, crater.z, 80, 6, 6, 6, 0.05);
        level.playSound(null, net.minecraft.core.BlockPos.containing(crater), SoundEvents.GENERIC_EXPLODE.value(),
                net.minecraft.sounds.SoundSource.AMBIENT, 8f, 0.4f);
        spots.forEach((name, spot) -> {
            if (name.startsWith("geyser")) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, spot.pos().x, spot.pos().y + 1, spot.pos().z, 60, 0.4, 3, 0.4, 0.08);
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, spot.pos().x, spot.pos().y + 3, spot.pos().z, 30, 0.6, 2, 0.6, 0.02);
            }
        });
    }

    // ---------------------------------------------------------------- Elle and the factions' people

    private static void say(ServerPlayer player, String who, String line, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(who + ": ").withStyle(ChatFormatting.GOLD).append(Component.literal(line).withStyle(color)));
    }

    /** Elle's questline: her supplies, then the two leaders, then the Forgotten Skull. */
    static void elle(ServerPlayer player) {
        int step = Factions.elle(player);
        switch (step) {
            case Factions.ELLE_NEW -> {
                say(player, "Elle", "You made it to the Crimson Isle! I'm Elle. The Blazes have us pinned in this Stronghold.", ChatFormatting.WHITE);
                say(player, "Elle", "Bring me 4 Magma Chunks, 3 Blaze Ashes and 2 Spectre Dust: the Magma Cubes, Blazes and Wither Spectres drop them.",
                        ChatFormatting.YELLOW);
                Factions.setFlag(player, "elle", Factions.ELLE_SUPPLIES);
                Msg.playSound(player, SoundEvents.VILLAGER_YES, 1.0f, 1.0f);
            }
            case Factions.ELLE_SUPPLIES -> {
                int chunks = SkyStacks.count(player.getInventory(), "MAGMA_CHUNK");
                int ashes = SkyStacks.count(player.getInventory(), "BLAZE_ASHES");
                int dust = SkyStacks.count(player.getInventory(), "SPECTRE_DUST");
                if (chunks >= 4 && ashes >= 3 && dust >= 2) {
                    SkyStacks.take(player.getInventory(), "MAGMA_CHUNK", 4);
                    SkyStacks.take(player.getInventory(), "BLAZE_ASHES", 3);
                    SkyStacks.take(player.getInventory(), "SPECTRE_DUST", 2);
                    Factions.setFlag(player, "elle", Factions.ELLE_LEADERS);
                    say(player, "Elle", "Perfect, thank you! Now, the Isle is split between two factions.", ChatFormatting.WHITE);
                    say(player, "Elle", "Meet Queen Nyx of the Mages in Scarleton (east) and Chief Scorn of the Barbarians in Dragontail (west), then pick a side.",
                            ChatFormatting.YELLOW);
                    Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
                } else {
                    say(player, "Elle", "Still need: Magma Chunks " + Math.min(chunks, 4) + "/4, Blaze Ashes " + Math.min(ashes, 3) + "/3, Spectre Dust "
                            + Math.min(dust, 2) + "/2.", ChatFormatting.YELLOW);
                }
            }
            case Factions.ELLE_LEADERS -> {
                if (Factions.canJoin(player)) {
                    say(player, "Elle", "You've met them both. Go back to the one you trust and join them!", ChatFormatting.YELLOW);
                } else {
                    String left = Factions.flag(player, "met_mage") == 0 ? "Queen Nyx in Scarleton" : "Chief Scorn in Dragontail";
                    say(player, "Elle", "Go and meet " + left + ".", ChatFormatting.YELLOW);
                }
            }
            default -> say(player, "Elle", "The " + Factions.plural(Factions.faction(player))
                    + " are lucky to have you. Find me at the Forgotten Skull when you're ready for Kuudra.", ChatFormatting.WHITE);
        }
    }

    /** A faction leader: meeting them counts for Elle's questline; then their screen. */
    static void leader(ServerPlayer player, String faction) {
        if (Factions.elle(player) >= Factions.ELLE_LEADERS && Factions.flag(player, "met_" + faction) == 0) {
            Factions.setFlag(player, "met_" + faction, 1);
            say(player, Factions.leader(faction), faction.equals(Factions.MAGE)
                    ? "Elle sent you? The Mages could use someone with your talents."
                    : "Elle sent you? Good. The Barbarians need fighters, not thinkers.", ChatFormatting.WHITE);
        }
        FactionMenu.open(player, faction);
    }

    /** One of a faction's people: today's errand for them, if you have one. */
    static void person(ServerPlayer player, String name) {
        if (FactionQuests.errand(player, name)) {
            return;
        }
        say(player, name, Factions.faction(player).isEmpty() ? "Join a faction and I might have work for you."
                : "Nothing for you today. Check your faction quests.", ChatFormatting.GRAY);
    }

    // ---------------------------------------------------------------- the lava sea

    /** Twice a second: a player swimming in the lava sea for a second and a half is thrown back to the nearest warp. */
    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        ServerLevel level = level(server);
        if (level == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            boolean sea = player.getY() < LAVA + 1.5 && player.isEyeInFluid(FluidTags.LAVA) || player.getY() < LAVA - 2;
            if (!sea || player.isCreative() || player.isSpectator()) {
                IN_LAVA.remove(player.getUUID());
                continue;
            }
            int ticks = IN_LAVA.merge(player.getUUID(), 1, Integer::sum);
            if (ticks >= 3) {
                IN_LAVA.remove(player.getUUID());
                rescue(player);
            }
        }
    }

    private static void rescue(ServerPlayer player) {
        Spot best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<String, Spot> e : spots.entrySet()) {
            if (!e.getKey().startsWith("warp:")) {
                continue;
            }
            double d = e.getValue().pos().distanceToSqr(player.position());
            if (d < bestDistance) {
                bestDistance = d;
                best = e.getValue();
            }
        }
        player.clearFire();
        if (best == null) {
            ServerLevel level = (ServerLevel) player.level();
            player.teleportTo(level, 0.5, 67, 205.5, java.util.Set.of(), 180, 0, true);
        } else {
            player.teleportTo((ServerLevel) player.level(), best.pos().x, best.pos().y, best.pos().z, java.util.Set.of(), best.yaw(), 0, true);
        }
        player.sendOverlayMessage(Component.literal("The lava spat you back out!").withStyle(ChatFormatting.RED));
        Msg.playSound(player, SoundEvents.LAVA_EXTINGUISH, 1.0f, 1.0f);
    }
}
