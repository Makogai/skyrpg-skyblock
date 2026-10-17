package com.skyrpg.skyblock.dungeon;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.hub.HubAreas;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's Catacombs: every run is a {@link Run} in its own slot of the {@code skyrpg_skyblock:catacombs} world. Starting
 * one takes the leader's party (up to 5; everyone must meet the floor's Catacombs level); the map is built a room a tick,
 * then everyone arrives in the Entrance and the timer starts after 5 seconds. Dying makes you a ghost (flying,
 * invisible, can't fight) until the boss room. Leaving: {@code /sbdungeon leave}. Admins: {@code /sbdungeon start <floor>
 * [player]}.
 */
public final class Catacombs {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            SkyRpgSkyblock.id("catacombs"));
    public static final ResourceKey<Level> DUNGEON_HUB = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            SkyRpgSkyblock.id("dungeon_hub"));
    static final String TAG = "skyrpg_catacombs_entity";
    static final String BOSS = "skyrpg_dungeon_boss";
    static final String BOSS_PORTAL = "skyrpg_dungeon_boss_portal";
    static final String REWARDS = "skyrpg_dungeon_rewards";
    private static final int SLOTS = 64;
    private static final int MAX_PARTY = 5;
    private static final int TIME_LIMIT = 60 * 60 * 20;

    /** The dungeon's scoreboard lines for a player in a run (time, cleared, secrets, keys, the team), synced to them. */
    public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<List<String>> HUD = net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
            .create(SkyRpgSkyblock.id("dungeon_hud"), builder -> builder.syncWith(
                    net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(net.minecraft.network.codec.ByteBufCodecs.list()),
                    net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.targetOnly()));

    private static final Map<Integer, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Run> BY_PLAYER = new HashMap<>();
    private static long nextId = System.currentTimeMillis();

    private Catacombs() {
    }

    public static void init() {
        DungeonXp.init();
        Classes.init();
        Rewards.init();
        Blessings.init();
        DungeonItems.init();
        ServerLifecycleEvents.SERVER_STARTED.register(RoomLibrary::load);
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, ok) -> RoomLibrary.load(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(Catacombs::stopAll);
        ServerTickEvents.END_SERVER_TICK.register(Catacombs::tick);
        com.skyrpg.core.action.Actions.register("dungeon_mort", (player, params) -> DungeonMenu.open(player));
        // Before the Hub's rules: locked doors and secret chests are clicked here.
        Identifier early = SkyRpgSkyblock.id("catacombs_early");
        UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(early, (player, level, hand, hit) -> {
            if (!(player instanceof ServerPlayer server) || level.isClientSide() || !level.dimension().equals(LEVEL)) {
                return InteractionResult.PASS;
            }
            Run run = BY_PLAYER.get(player.getUUID());
            if (run == null || run.ghosts.contains(player.getUUID())) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            for (Run.RoomState state : run.rooms) {
                if (state.puzzle != null && state.puzzle.built && state.puzzle.useBlock(server, pos)) {
                    return InteractionResult.SUCCESS;
                }
            }
            if (run.useDoor(server, pos)) {
                return InteractionResult.SUCCESS;
            }
            if (level.getBlockState(pos).is(Blocks.CHEST)) {
                for (Run.RoomState state : run.rooms) {
                    if (state.secretChests.remove(pos)) {
                        run.secretFound(state, server);
                        if (pos.equals(run.mimicChest)) {
                            run.releaseMimic(server, pos);
                            return InteractionResult.SUCCESS;
                        }
                        level.blockEvent(pos, Blocks.CHEST, 1, 1);
                        Msg.playSound(server, SoundEvents.CHEST_OPEN, 0.8f, 1.0f);
                        // A fifth of secret chests hold a blessing.
                        if (run.random.nextInt(5) == 0) {
                            Blessings.bless(run, server);
                        }
                        return InteractionResult.SUCCESS;
                    }
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer server) || level.isClientSide() || !level.dimension().equals(LEVEL)) {
                return InteractionResult.PASS;
            }
            Run run = BY_PLAYER.get(player.getUUID());
            if (run == null || !entity.entityTags().contains(run.tag)) {
                return InteractionResult.PASS;
            }
            for (Run.RoomState state : run.rooms) {
                if (state.puzzle != null && state.puzzle.useEntity(server, entity)) {
                    return InteractionResult.SUCCESS;
                }
            }
            if (run.boss != null && run.boss.useEntity(server, entity)) {
                return InteractionResult.SUCCESS;
            }
            if (entity.entityTags().contains(BOSS_PORTAL)) {
                run.enterBoss();
                return InteractionResult.SUCCESS;
            }
            if (entity.entityTags().contains(REWARDS)) {
                RewardsMenu.open(server, run);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                BY_PLAYER.get(player.getUUID()) instanceof Run run && run.ghosts.contains(player.getUUID()) ? InteractionResult.FAIL : InteractionResult.PASS);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!entity.level().dimension().equals(LEVEL)) {
                return;
            }
            Run run = runOf(entity);
            if (run != null) {
                run.mobDied(entity, source.getEntity() instanceof ServerPlayer killer ? killer : null);
            }
        });
        // A boss's shields and phases.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!entity.level().dimension().equals(LEVEL) || entity instanceof ServerPlayer) {
                return true;
            }
            Run run = runOf(entity);
            return run == null || run.boss == null || run.boss.allowDamage(entity, source);
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!entity.level().dimension().equals(LEVEL)) {
                return true;
            }
            if (entity instanceof ServerPlayer player) {
                Run run = BY_PLAYER.get(player.getUUID());
                if (run != null) {
                    ghost(run, player);
                    return false;
                }
                return true;
            }
            Run run = runOf(entity);
            return run == null || run.boss == null || run.boss.allowDeath(entity);
        });
        // Leaving the Catacombs world (a warp, the Dungeon Hub) leaves the run.
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
            if (from.dimension().equals(LEVEL) && !to.dimension().equals(LEVEL)) {
                Run run = BY_PLAYER.remove(player.getUUID());
                player.removeAttached(HUD);
                if (run != null) {
                    revive(run, player);
                    run.broadcast(Component.literal(player.getScoreboardName() + " left the dungeon.").withStyle(ChatFormatting.GRAY));
                }
            }
        });
        // Anything left from a run that's over (a mob in a chunk that was saved) goes when it loads.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (level.dimension().equals(LEVEL) && entity.entityTags().stream().anyMatch(t -> t.startsWith(Run.RUN_TAG_PREFIX)) && runOf(entity) == null) {
                entity.discard();
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("sbdungeon")
                .then(Commands.literal("leave").executes(ctx -> {
                    leave(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("class").then(Commands.argument("class", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(DungeonXp.CLASSES, builder))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String picked = StringArgumentType.getString(ctx, "class").toLowerCase(java.util.Locale.ROOT);
                            if (!DungeonXp.CLASSES.contains(picked)) {
                                return 0;
                            }
                            DungeonXp.select(player, picked);
                            player.sendSystemMessage(Notice.build(Notice.Kind.DUNGEON, Component.literal("You picked the " + DungeonXp.title(picked)
                                    + " class.").withStyle(ChatFormatting.GREEN)));
                            return 1;
                        })))
                .then(Commands.literal("master").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("floor", IntegerArgumentType.integer(1, 7))
                                .then(Commands.argument("player", EntityArgument.player()).executes(ctx ->
                                        start(EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "floor"), true, true)))))
                .then(Commands.literal("start").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("floor", IntegerArgumentType.integer(0, 7))
                                .executes(ctx -> start(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "floor"), true))
                                .then(Commands.argument("player", EntityArgument.player()).executes(ctx ->
                                        start(EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "floor"), true)))))
                .then(Commands.literal("menu").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            DungeonMenu.open(EntityArgument.getPlayer(ctx, "player"));
                            return 1;
                        })))
                .then(Commands.literal("boss").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            Run run = BY_PLAYER.get(EntityArgument.getPlayer(ctx, "player").getUUID());
                            if (run == null || run.phase == Run.Phase.BUILDING) {
                                return 0;
                            }
                            run.enterBoss();
                            return 1;
                        })))
                .then(Commands.literal("debug").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            Run run = BY_PLAYER.get(EntityArgument.getPlayer(ctx, "player").getUUID());
                            if (run == null) {
                                return 0;
                            }
                            StringBuilder out = new StringBuilder("phase " + run.phase + ", origin " + run.origin.toShortString() + "\n" + run.layout.draw());
                            for (Run.RoomState state : run.rooms) {
                                out.append(state.room.type()).append(" #").append(state.room.id()).append(" ").append(state.template).append(" stars ")
                                        .append(state.stars.size()).append("/").append(state.starCount).append(state.cleared ? " cleared" : "")
                                        .append(" secrets ").append(state.secretsFound).append("/").append(state.secrets);
                                state.secretChests.forEach(p -> out.append(" chest@").append(p.toShortString()));
                                out.append("\n");
                            }
                            run.lockedDoorBlocks.forEach((i, blocks) -> out.append("door ").append(i).append(" ")
                                    .append(run.layout.doors.get(i).lock()).append(" @").append(blocks.getFirst().toShortString()).append("\n"));
                            String text = out.toString();
                            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
                            return 1;
                        })))
                .then(Commands.literal("xp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    DungeonXp.give(EntityArgument.getPlayer(ctx, "player"), DungeonXp.CATACOMBS, IntegerArgumentType.getInteger(ctx, "amount"));
                                    return 1;
                                }))))));
    }

    /** The run an entity belongs to (by its run tag), or null. */
    static Run runOf(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return BY_PLAYER.get(player.getUUID());
        }
        for (Run run : RUNS.values()) {
            if (entity.entityTags().contains(run.tag)) {
                return run;
            }
        }
        return null;
    }

    /** "The Catacombs (F1)", for the sidebar. */
    public static String areaName(ServerPlayer player) {
        Run run = BY_PLAYER.get(player.getUUID());
        return run == null ? "The Catacombs" : "The Catacombs (" + run.floor.code() + ")";
    }

    public static boolean inRun(ServerPlayer player) {
        return BY_PLAYER.containsKey(player.getUUID());
    }

    /**
     * Starts a run of a floor for this player and their party (the leader's call). {@code admin}: skips the level
     * requirements. Returns 1 when it started.
     */
    public static int start(ServerPlayer leader, int number, boolean admin) {
        return start(leader, number, false, admin);
    }

    public static int start(ServerPlayer leader, int number, boolean master, boolean admin) {
        Floors.Floor floor = Floors.get(number, master);
        ServerLevel level = leader.level().getServer().getLevel(LEVEL);
        if (floor == null || level == null) {
            return 0;
        }
        List<ServerPlayer> party = new ArrayList<>();
        party.add(leader);
        com.skyrpg.party.PartyData.Party p = com.skyrpg.party.Parties.partyOf(leader.getUUID());
        if (p != null) {
            for (com.skyrpg.party.PartyData.Member member : p.members) {
                ServerPlayer player = leader.level().getServer().getPlayerList().getPlayer(member.id);
                if (player != null && player != leader && party.size() < MAX_PARTY) {
                    party.add(player);
                }
            }
        }
        for (ServerPlayer player : party) {
            if (BY_PLAYER.containsKey(player.getUUID())) {
                leader.sendSystemMessage(Component.literal(player.getScoreboardName() + " is already in a dungeon!").withStyle(ChatFormatting.RED));
                return 0;
            }
            if (!admin && floor.master() && DungeonXp.completions(player, "f7") == 0) {
                leader.sendSystemMessage(Component.literal(player.getScoreboardName() + " hasn't beaten Floor VII yet!").withStyle(ChatFormatting.RED));
                return 0;
            }
            if (!admin && com.skyrpg.core.skills.SkillLevels.level(player, com.skyrpg.core.skills.SkillLevels.COMBAT) < floor.combat()) {
                leader.sendSystemMessage(Component.literal(player.getScoreboardName() + " needs Combat " + floor.combat() + " for " + floor.title() + "!")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            if (!admin && DungeonXp.level(player, DungeonXp.CATACOMBS) < floor.catacombs()) {
                leader.sendSystemMessage(Component.literal(player.getScoreboardName() + " needs Catacombs " + floor.catacombs() + " for " + floor.title() + "!")
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
        }
        int slot = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (!RUNS.containsKey(i)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            leader.sendSystemMessage(Component.literal("Every dungeon is busy right now. Try again soon!").withStyle(ChatFormatting.RED));
            return 0;
        }
        List<UUID> ids = party.stream().map(Entity::getUUID).toList();
        Run run = new Run(nextId++, slot, floor, level, ids, level.getRandom());
        RUNS.put(slot, run);
        for (ServerPlayer player : party) {
            BY_PLAYER.put(player.getUUID(), run);
            run.classes.put(player.getUUID(), DungeonXp.selected(player));
            player.sendSystemMessage(Notice.build(Notice.Kind.DUNGEON, Component.literal("Starting " + floor.title() + "...")
                    .withStyle(ChatFormatting.RED)));
        }
        SkyRpgSkyblock.LOGGER.info("Catacombs run {} ({}) in slot {}:\n{}", run.id, floor.title(), slot, run.layout.draw());
        return 1;
    }

    private static void tick(MinecraftServer server) {
        if (RUNS.isEmpty()) {
            return;
        }
        long tick = server.getTickCount();
        for (Run run : List.copyOf(RUNS.values())) {
            long now = run.level.getGameTime();
            if (run.phase == Run.Phase.BUILDING) {
                if (run.buildStep()) {
                    arrive(run);
                }
                continue;
            }
            if (run.phase == Run.Phase.STARTING && now >= run.started) {
                run.phase = Run.Phase.CLEARING;
                run.broadcast(Component.literal("[NPC] Mort").withStyle(ChatFormatting.YELLOW).append(Component.literal(
                        ": Here we go! Good luck in there.").withStyle(ChatFormatting.WHITE)));
            }
            if (tick % 10 == 0) {
                run.tick(now);
                ghostTick(run);
                hud(run);
            }
            if (run.started >= 0 && run.phase != Run.Phase.DONE && now - run.started > TIME_LIMIT) {
                run.broadcast(Component.literal("You ran out of time!").withStyle(ChatFormatting.RED));
                run.finish(true);
            }
            if (run.failed && run.phase == Run.Phase.DONE && now - run.finished == 200) {
                for (ServerPlayer player : run.online()) {
                    toDungeonHub(player);
                }
            }
            // Nobody left in it for a minute: it's over.
            if (run.online().isEmpty()) {
                if (run.emptySince < 0) {
                    run.emptySince = now;
                } else if (now - run.emptySince > 60 * 20) {
                    close(run);
                }
            } else {
                run.emptySince = -1;
            }
        }
        // Anyone in the Catacombs world without a run goes to the Dungeon Hub.
        ServerLevel level = server.getLevel(LEVEL);
        if (level != null && tick % 20 == 0) {
            for (ServerPlayer player : List.copyOf(level.players())) {
                if (!BY_PLAYER.containsKey(player.getUUID())) {
                    toDungeonHub(player);
                }
            }
        }
    }

    /** The map is built: everyone arrives in the Entrance, and the timer starts in 5 seconds. */
    private static void arrive(Run run) {
        Vec3 at = run.spawnPoint();
        for (UUID id : run.players) {
            ServerPlayer player = run.level.getServer().getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            player.teleport(new net.minecraft.world.level.portal.TeleportTransition(run.level, at, Vec3.ZERO, 0, 0, Set.of(),
                    net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
            player.resetFallDistance();
            player.setHealth(player.getMaxHealth());
            Notice.of(Notice.Kind.DUNGEON).tag(run.floor.title().toUpperCase(java.util.Locale.ROOT)).panel()
                    .line(Component.literal(" Class: " + DungeonXp.title(run.classes.getOrDefault(id, "berserk"))).withStyle(ChatFormatting.GREEN))
                    .line(Component.literal(" Clear the rooms, find the keys, open the Blood room, beat the boss.").withStyle(ChatFormatting.GRAY))
                    .send(player, Component.literal("Welcome to the Catacombs!").withStyle(ChatFormatting.RED));
        }
        run.enter(run.state(Layout.Type.ENTRANCE));
        run.phase = Run.Phase.STARTING;
        run.started = run.level.getGameTime() + 100;
    }

    /** Hypixel's dungeon scoreboard: time, rooms cleared, secrets, keys, and the team with their class and health. */
    private static void hud(Run run) {
        List<String> lines = new ArrayList<>();
        lines.add("§7Time Elapsed: §a" + Rewards.time(run.seconds()));
        lines.add("§7Cleared: §c" + Math.round(run.clearedShare() * 100) + "%");
        lines.add("§7Secrets: §b" + run.secretsFound() + "§7/§b" + run.secretsTotal());
        lines.add("§7Crypts: §a" + run.crypts + (run.mimicKilled ? "  §cMimic ✔" : ""));
        lines.add("§7Keys: §c■ " + (run.bloodKey ? "§a✔" : "§c✖") + " §8■ §fx" + run.witherKeys);
        lines.add("");
        for (UUID id : run.players) {
            ServerPlayer member = run.level.getServer().getPlayerList().getPlayer(id);
            String name = member == null ? "?" : member.getScoreboardName();
            String letter = run.classes.getOrDefault(id, "berserk").substring(0, 1).toUpperCase(java.util.Locale.ROOT);
            String state = member == null || runOf(member) != run ? "§8LEFT" : run.ghosts.contains(id) ? "§cDEAD"
                    : "§a" + Msg.number(Math.round(com.skyrpg.core.stats.Stats.get(member, com.skyrpg.core.stats.StatTypes.HEALTH)
                    * member.getHealth() / Math.max(1, member.getMaxHealth()))) + "§c❤";
            lines.add("§e[" + letter + "] §f" + name + " " + state);
        }
        for (ServerPlayer player : run.online()) {
            if (!lines.equals(player.getAttached(HUD))) {
                player.setAttached(HUD, List.copyOf(lines));
            }
        }
    }

    // ---------------------------------------------------------------- ghosts

    private static void ghost(Run run, ServerPlayer player) {
        if (run.ghosts.contains(player.getUUID())) {
            player.setHealth(player.getMaxHealth());
            return;
        }
        run.ghosts.add(player.getUUID());
        run.deaths++;
        run.deathsBy.merge(player.getUUID(), 1, Integer::sum);
        player.setHealth(player.getMaxHealth());
        player.setInvisible(true);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
        player.removeAllEffects();
        run.broadcast(Component.literal(" ☠ ").withStyle(ChatFormatting.DARK_GRAY).append(Component.literal(player.getScoreboardName()
                + " died and became a ghost.").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.WITHER_DEATH, 0.4f, 1.6f);
        if (run.online().stream().allMatch(p -> run.ghosts.contains(p.getUUID()))) {
            run.broadcast(Component.literal("Everyone is dead! The run failed.").withStyle(ChatFormatting.RED));
            run.finish(true);
        }
    }

    static void revive(Run run, ServerPlayer player) {
        if (!run.ghosts.remove(player.getUUID())) {
            return;
        }
        player.setInvisible(false);
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.getAbilities().invulnerable = false;
        }
        player.onUpdateAbilities();
        player.setHealth(player.getMaxHealth());
        player.sendSystemMessage(Component.literal("You were revived!").withStyle(ChatFormatting.GREEN));
    }

    private static void ghostTick(Run run) {
        for (ServerPlayer player : run.online()) {
            if (run.ghosts.contains(player.getUUID())) {
                player.sendOverlayMessage(Component.literal("You are a ghost").withStyle(ChatFormatting.GRAY));
                run.level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 1, 0.2, 0.3, 0.2, 0);
            }
        }
    }

    // ---------------------------------------------------------------- leaving

    public static void leave(ServerPlayer player) {
        player.removeAttached(HUD);
        Run run = BY_PLAYER.remove(player.getUUID());
        if (run == null) {
            player.sendSystemMessage(Component.literal("You're not in a dungeon.").withStyle(ChatFormatting.RED));
            return;
        }
        revive(run, player);
        run.broadcast(Component.literal(player.getScoreboardName() + " left the dungeon.").withStyle(ChatFormatting.GRAY));
        toDungeonHub(player);
    }

    static void toDungeonHub(ServerPlayer player) {
        player.removeAttached(HUD);
        MinecraftServer server = player.level().getServer();
        ServerLevel hub = server.getLevel(DUNGEON_HUB);
        HubAreas.Arrival arrival = HubAreas.arrival(DUNGEON_HUB);
        if (hub == null || arrival == null) {
            hub = server.getLevel(com.skyrpg.hub.Hub.LEVEL);
            arrival = hub == null ? null : HubAreas.arrival(com.skyrpg.hub.Hub.LEVEL);
        }
        if (hub == null) {
            return;
        }
        Vec3 at = arrival != null ? arrival.pos() : Vec3.atBottomCenterOf(hub.getRespawnData().pos());
        player.teleport(new net.minecraft.world.level.portal.TeleportTransition(hub, at, Vec3.ZERO, arrival != null ? arrival.yaw() : 0, 0, Set.of(),
                net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
    }

    private static void close(Run run) {
        if (run.boss != null) {
            run.boss.close();
        }
        RUNS.remove(run.slot);
        BY_PLAYER.values().removeIf(r -> r == run);
        Rewards.CHESTS_BY_RUN.remove(run.id);
        for (Entity entity : run.level.getAllEntities()) {
            if (entity.entityTags().contains(run.tag)) {
                entity.discard();
            }
        }
        SkyRpgSkyblock.LOGGER.info("Catacombs run {} closed", run.id);
    }

    private static void stopAll(MinecraftServer server) {
        ServerLevel level = server.getLevel(LEVEL);
        if (level != null) {
            for (ServerPlayer player : List.copyOf(level.players())) {
                Run run = BY_PLAYER.remove(player.getUUID());
                if (run != null) {
                    revive(run, player);
                }
                toDungeonHub(player);
            }
        }
        List.copyOf(RUNS.values()).forEach(Catacombs::close);
    }
}
