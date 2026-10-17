package com.skyrpg.skyblock.crimson;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.essence.Essence;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * Kuudra, the Crimson Isle's party boss (up to four players), in five tiers: Basic, Hot, Burning, Fiery and Infernal.
 * The Kuudra Gatekeeper at the Forgotten Skull takes you in (you must be in a faction; each tier needs the one before it
 * beaten). Every fight is a {@link KuudraRun} in its own slot of the {@code skyrpg_skyblock:kuudra} world. Beating it:
 * reputation (10 to 50), SkyBlock XP on a tier's first clear (20 to 100), and two chests: the free one (Crimson Essence)
 * and the paid one, opened with that tier's Kuudra Key (more essence, a Crimson, Aurora, Terror, Hollow or Fervor armour
 * piece of the tier, Kuudra Teeth in Fiery and Infernal, sometimes a chunk or a core). The Emissaries in Dragontail and
 * Scarleton sell the keys (coins, Enchanted Red Sand or Mycelium, Corrupted Nether Stars, and faction reputation).
 */
public final class Kuudra {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, SkyRpgSkyblock.id("kuudra"));
    static final String TAG = "skyrpg_kuudra";
    static final String BOSS_TAG = "skyrpg_kuudra_boss";
    static final int MAX_PARTY = 4;
    private static final int SLOTS = 32;

    /**
     * A tier: Kuudra's health, its mobs' level and wave timer, its slam, the mobs' health and damage factors, the free
     * chest's essence, reputation and first-clear SkyBlock XP, the key (id, coins, materials) and the reputation it needs.
     */
    record Tier(int number, String name, double kuudraHealth, int mobLevel, int waveSeconds, double slam, double healthFactor,
                double damageFactor, int essence, int rep, int sbxp, String key, long keyCoins, int keyMaterial, int keyRep) {
        double mobHealth(double base) {
            return base * healthFactor;
        }

        double mobDamage(double base) {
            return base * damageFactor;
        }

        String title() {
            return name + " Tier";
        }

        /** The armour prefix of the tier's Crimson-family pieces ("", "HOT_", ...). */
        String prefix() {
            return number == 1 ? "" : name.toUpperCase(java.util.Locale.ROOT) + "_";
        }
    }

    static final List<Tier> TIERS = List.of(
            new Tier(1, "Basic", 100_000, 100, 35, 1_500, 1, 1, 60, 10, 20, "KUUDRA_TIER_KEY", 200_000, 2, 0),
            new Tier(2, "Hot", 500_000, 200, 30, 4_000, 4, 2.5, 120, 20, 40, "KUUDRA_HOT_TIER_KEY", 400_000, 6, 1_000),
            new Tier(3, "Burning", 3_000_000, 300, 25, 10_000, 16, 6, 250, 30, 60, "KUUDRA_BURNING_TIER_KEY", 750_000, 20, 3_000),
            new Tier(4, "Fiery", 15_000_000, 400, 20, 25_000, 50, 15, 500, 40, 80, "KUUDRA_FIERY_TIER_KEY", 1_500_000, 60, 7_000),
            new Tier(5, "Infernal", 240_000_000, 500, 15, 60_000, 150, 35, 1_000, 50, 100, "KUUDRA_INFERNAL_TIER_KEY", 3_000_000, 120, 12_000));

    /** One of Kuudra's mobs: its name, entity and Basic-tier health and damage. The last only comes from Hot up. */
    record MobKind(String name, EntityType<? extends Mob> type, double health, double damage) {
    }

    static final List<MobKind> MOBS = List.of(
            new MobKind("Kuudra Follower", EntityTypes.ZOMBIE, 25_000, 400),
            new MobKind("Kuudra Knocker", EntityTypes.VINDICATOR, 40_000, 600),
            new MobKind("Magma Follower", EntityTypes.MAGMA_CUBE, 15_000, 300),
            new MobKind("Wandering Blaze", EntityTypes.BLAZE, 30_000, 500),
            new MobKind("Kuudra Berserker", EntityTypes.PIGLIN_BRUTE, 60_000, 900));

    private static final List<String> SETS = List.of("CRIMSON", "AURORA", "TERROR", "HOLLOW", "FERVOR");
    private static final List<String> PIECES = List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    /** Clears per tier ("1".."5"). */
    static final AttachmentType<Map<String, Integer>> CLEARS = AttachmentRegistry.create(SkyRpgSkyblock.id("kuudra_clears"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    private static final Map<Integer, KuudraRun> RUNS = new HashMap<>();
    private static final Map<UUID, KuudraRun> BY_PLAYER = new HashMap<>();
    /** Chests still to open, per player: what's in the free one, what's in the paid one (and its key). */
    record Chests(KuudraRun run, Tier tier, List<ItemStack> free, int freeEssence, List<ItemStack> paid, int paidEssence, boolean[] opened) {
    }

    static final Map<UUID, Chests> CHESTS = new HashMap<>();
    private static int nextId = 1;

    private Kuudra() {
    }

    static void init() {
        Actions.register("kuudra", (player, params) -> KuudraMenu.open(player, KuudraMenu.Mode.TIERS));
        Actions.register("kuudra_emissary", (player, params) -> KuudraMenu.open(player, KuudraMenu.Mode.KEYS));
        ServerTickEvents.END_SERVER_TICK.register(Kuudra::tick);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer server) || level.isClientSide() || !(entity instanceof Interaction box)
                    || !entity.entityTags().contains(TAG)) {
                return InteractionResult.PASS;
            }
            KuudraRun run = BY_PLAYER.get(server.getUUID());
            if (run == null) {
                return InteractionResult.SUCCESS;
            }
            if (box == run.ballista) {
                run.fire(server);
            } else {
                run.take(server, box);
            }
            return InteractionResult.SUCCESS;
        });
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player) || !entity.level().dimension().equals(LEVEL)) {
                return true;
            }
            KuudraRun run = BY_PLAYER.get(player.getUUID());
            if (run == null) {
                return true;
            }
            down(run, player);
            return false;
        });
        // Kuudra can only be hurt while the ballista has it stunned.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!entity.entityTags().contains(BOSS_TAG)) {
                return true;
            }
            for (KuudraRun run : RUNS.values()) {
                if (run.kuudra == entity) {
                    return run.stunned(run.level.getGameTime());
                }
            }
            return false;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.entityTags().contains(BOSS_TAG)) {
                for (KuudraRun run : RUNS.values()) {
                    if (run.kuudra == entity) {
                        run.finish(false);
                    }
                }
            }
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
            if (from.dimension().equals(LEVEL) && !to.dimension().equals(LEVEL)) {
                left(player);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && RUNS.values().stream().noneMatch(r -> entity.entityTags().contains(r.tag))) {
                entity.discard();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (KuudraRun run : List.copyOf(RUNS.values())) {
                for (ServerPlayer player : run.online()) {
                    toSkull(player);
                }
                close(run);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            dispatcher.register(Commands.literal("kuudra").then(Commands.literal("leave").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (!BY_PLAYER.containsKey(player.getUUID())) {
                    player.sendSystemMessage(Component.literal("You're not fighting Kuudra.").withStyle(ChatFormatting.RED));
                    return 0;
                }
                try {
                    toSkull(player);
                } catch (RuntimeException e) {
                    SkyRpgSkyblock.LOGGER.error("Leaving Kuudra failed", e);
                    throw e;
                }
                return 1;
            })).then(Commands.literal("chests").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                if (CHESTS.containsKey(player.getUUID())) {
                    KuudraMenu.open(player, KuudraMenu.Mode.REWARDS);
                }
                return 1;
            })));
            dispatcher.register(Commands.literal("sbkuudra").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("start").then(Commands.argument("tier", IntegerArgumentType.integer(1, 5)).executes(ctx ->
                            start(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "tier"), true))))
                    .then(Commands.literal("skip").executes(ctx -> {
                        KuudraRun run = BY_PLAYER.get(ctx.getSource().getPlayerOrException().getUUID());
                        if (run == null) {
                            return 0;
                        }
                        switch (run.phase) {
                            case SUPPLIES -> {
                                run.delivered = KuudraRun.SUPPLIES;
                                run.phase = KuudraRun.Phase.BUILD;
                                run.pickups.keySet().forEach(Entity::discard);
                                run.pickups.values().forEach(p -> p.parts().forEach(Entity::discard));
                                run.pickups.clear();
                            }
                            case BUILD -> run.built = 100;
                            case KUUDRA -> run.fuel += 3;
                            default -> {
                            }
                        }
                        return 1;
                    })));
        });
    }

    static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    static int clears(ServerPlayer player, int tier) {
        return player.getAttachedOrElse(CLEARS, Map.of()).getOrDefault(String.valueOf(tier), 0);
    }

    /** Why this player can't go into this tier, or null. */
    static String locked(ServerPlayer player, Tier tier) {
        if (Factions.faction(player).isEmpty()) {
            return "Join a faction first";
        }
        if (tier.number() > 1 && clears(player, tier.number() - 1) == 0) {
            return "Beat the " + TIERS.get(tier.number() - 2).title() + " first";
        }
        return null;
    }

    // ---------------------------------------------------------------- starting and ending

    static int start(ServerPlayer leader, int number, boolean admin) {
        Tier tier = TIERS.get(number - 1);
        ServerLevel level = level(leader.level().getServer());
        if (level == null) {
            return 0;
        }
        List<ServerPlayer> party = new ArrayList<>();
        party.add(leader);
        com.skyrpg.party.PartyData.Party p = com.skyrpg.party.Parties.partyOf(leader.getUUID());
        if (p != null) {
            if (!leader.getUUID().equals(p.leader) && !admin) {
                leader.sendSystemMessage(Component.literal("Only your party's leader can start the fight.").withStyle(ChatFormatting.RED));
                return 0;
            }
            for (com.skyrpg.party.PartyData.Member member : p.members) {
                ServerPlayer player = leader.level().getServer().getPlayerList().getPlayer(member.id);
                if (player != null && player != leader && party.size() < MAX_PARTY) {
                    party.add(player);
                }
            }
        }
        for (ServerPlayer player : party) {
            if (BY_PLAYER.containsKey(player.getUUID())) {
                leader.sendSystemMessage(Component.literal(player.getScoreboardName() + " is already fighting Kuudra!").withStyle(ChatFormatting.RED));
                return 0;
            }
            String why = admin ? null : locked(player, tier);
            if (why != null) {
                leader.sendSystemMessage(Component.literal(player.getScoreboardName() + ": " + why + ".").withStyle(ChatFormatting.RED));
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
            leader.sendSystemMessage(Component.literal("Kuudra's Hollow is full right now. Try again soon!").withStyle(ChatFormatting.RED));
            return 0;
        }
        KuudraRun run = new KuudraRun(nextId++, slot, tier, level, party.stream().map(Entity::getUUID).toList());
        RUNS.put(slot, run);
        buildArena(run);
        Vec3 arrival = run.at(KuudraRun.ARRIVAL);
        for (ServerPlayer player : party) {
            BY_PLAYER.put(player.getUUID(), run);
            CHESTS.remove(player.getUUID());
            player.teleport(new net.minecraft.world.level.portal.TeleportTransition(level, arrival, Vec3.ZERO, -90, 0, Set.of(),
                    net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
            player.sendSystemMessage(Notice.build(Notice.Kind.COMBAT, Component.literal("Kuudra: " + tier.title()).withStyle(ChatFormatting.RED)));
        }
        run.begin();
        return 1;
    }

    /** The arena: placed once per slot (it stays in the world; only solid blocks, the lava is the generator's). */
    private static void buildArena(KuudraRun run) {
        Vec3 origin = run.at(Vec3.ZERO);
        BlockPos o = BlockPos.containing(origin);
        for (int cx = o.getX() >> 4; cx <= (o.getX() + 129) >> 4; cx++) {
            for (int cz = o.getZ() >> 4; cz <= (o.getZ() + 129) >> 4; cz++) {
                run.level.getChunk(cx, cz);
            }
        }
        if (!run.level.getBlockState(BlockPos.containing(run.at(KuudraRun.CENTER)).below()).isAir()) {
            return;
        }
        StructureTemplate template = run.level.getServer().getStructureTemplateManager()
                .get(Identifier.fromNamespaceAndPath("skyrpg_skyblock", "kuudra/arena")).orElse(null);
        if (template != null) {
            template.placeInWorld(run.level, o, o, new StructurePlaceSettings(), run.random, Block.UPDATE_CLIENTS);
        } else {
            // No template: a plain platform to stand on.
            BlockPos c = BlockPos.containing(run.at(KuudraRun.CENTER));
            for (int dx = -13; dx <= 13; dx++) {
                for (int dz = -13; dz <= 13; dz++) {
                    if (dx * dx + dz * dz <= 169) {
                        run.level.setBlock(c.offset(dx, -1, dz), net.minecraft.world.level.block.Blocks.BLACKSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** A player goes down: a ghost for 10 seconds, then back on the platform. */
    private static void down(KuudraRun run, ServerPlayer player) {
        run.dropped(player);
        player.setHealth(player.getMaxHealth());
        if (run.dead.containsKey(player.getUUID())) {
            return;
        }
        run.dead.put(player.getUUID(), run.level.getGameTime() + 200);
        player.setInvisible(true);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
        player.removeAllEffects();
        Vec3 above = run.at(KuudraRun.CENTER).add(0, 8, 0);
        player.teleportTo(run.level, above.x, above.y, above.z, Set.of(), player.getYRot(), 30, true);
        run.broadcast(Component.literal(" ☠ ").withStyle(ChatFormatting.DARK_GRAY).append(Component.literal(player.getScoreboardName()
                + " went down! Back in 10 seconds.").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.WITHER_DEATH, 0.4f, 1.6f);
    }

    static void respawn(KuudraRun run, ServerPlayer player) {
        player.setInvisible(false);
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.getAbilities().invulnerable = false;
        }
        player.onUpdateAbilities();
        player.setHealth(player.getMaxHealth());
        Vec3 at = run.at(KuudraRun.ARRIVAL);
        player.teleportTo(run.level, at.x, at.y, at.z, Set.of(), -90, 0, true);
    }

    private static void left(ServerPlayer player) {
        KuudraRun run = BY_PLAYER.remove(player.getUUID());
        if (run == null) {
            return;
        }
        run.bar.removePlayer(player);
        run.dropped(player);
        if (run.dead.remove(player.getUUID()) != null) {
            respawn(run, player);
        }
        player.setInvisible(false);
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.getAbilities().invulnerable = false;
            player.onUpdateAbilities();
        }
        run.broadcast(Component.literal(player.getScoreboardName() + " left Kuudra's Hollow.").withStyle(ChatFormatting.GRAY));
        if (BY_PLAYER.values().stream().noneMatch(r -> r == run)) {
            close(run);
        }
    }

    private static void close(KuudraRun run) {
        RUNS.remove(run.slot);
        BY_PLAYER.values().removeIf(r -> r == run);
        run.bar.removeAllPlayers();
        for (Entity entity : com.google.common.collect.Lists.newArrayList(run.level.getAllEntities())) {
            if (entity.entityTags().contains(run.tag)) {
                entity.discard();
            }
        }
    }

    /** Back to the Forgotten Skull. */
    static void toSkull(ServerPlayer player) {
        ServerLevel isle = CrimsonIsle.level(player.level().getServer());
        CrimsonIsle.Spot spot = CrimsonIsle.spot("warp:Kuudra Passage");
        if (isle == null) {
            return;
        }
        Vec3 at = spot == null ? new Vec3(0.5, 67, 205.5) : spot.pos();
        player.teleport(new net.minecraft.world.level.portal.TeleportTransition(isle, at, Vec3.ZERO, spot == null ? 180 : spot.yaw(), 0, Set.of(),
                net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
    }

    private static void tick(MinecraftServer server) {
        for (KuudraRun run : List.copyOf(RUNS.values())) {
            run.tick();
            if (run.phase == KuudraRun.Phase.DONE && run.level.getGameTime() - run.doneAt > 1200) {
                for (ServerPlayer player : run.online()) {
                    toSkull(player);
                }
                close(run);
            } else if (run.online().isEmpty() && run.level.getGameTime() - run.started > 200) {
                close(run);
            }
        }
    }

    // ---------------------------------------------------------------- winning

    static void won(KuudraRun run) {
        Tier tier = run.tier;
        run.broadcast(Component.literal("KUUDRA DOWN! (" + tier.title() + ")").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (ServerPlayer player : run.online()) {
            Map<String, Integer> clears = new HashMap<>(player.getAttachedOrElse(CLEARS, Map.of()));
            boolean first = clears.getOrDefault(String.valueOf(tier.number()), 0) == 0;
            clears.merge(String.valueOf(tier.number()), 1, Integer::sum);
            player.setAttached(CLEARS, Map.copyOf(clears));
            Notice.Panel panel = Notice.of(Notice.Kind.COMBAT).tag("KUUDRA").panel();
            if (first) {
                SkyBlockXp.give(player, tier.sbxp(), "Kuudra " + tier.title());
                panel.line(Component.literal(" +" + tier.sbxp() + " SkyBlock XP (first clear)").withStyle(ChatFormatting.AQUA));
            }
            panel.line(Component.literal(" Two chests wait (reopen them with /kuudra chests)").withStyle(ChatFormatting.GRAY));
            panel.send(player, Component.literal("You defeated Kuudra (" + tier.title() + ")!").withStyle(ChatFormatting.GOLD));
            Factions.give(player, tier.rep(), "Kuudra");
            CHESTS.put(player.getUUID(), roll(run, player));
            KuudraMenu.open(player, KuudraMenu.Mode.REWARDS);
        }
        run.online().forEach(p -> Msg.playSound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f));
    }

    private static Chests roll(KuudraRun run, ServerPlayer player) {
        Tier tier = run.tier;
        java.util.Random random = new java.util.Random(player.getRandom().nextLong());
        List<ItemStack> paid = new ArrayList<>();
        String set = SETS.get(random.nextInt(SETS.size()));
        String piece = PIECES.get(random.nextInt(PIECES.size()));
        add(paid, tier.prefix() + set + "_" + piece, 1);
        if (tier.number() >= 4) {
            add(paid, "KUUDRA_TEETH", tier.number() == 5 ? 3 + random.nextInt(2) : 1 + random.nextInt(2));
        }
        String[] chunks = {"UNCOMMON_KUUDRA_CHUNK", "RARE_KUUDRA_CHUNK", "EPIC_KUUDRA_CHUNK", "LEGENDARY_KUUDRA_CHUNK"};
        if (random.nextDouble() < 0.25) {
            add(paid, chunks[Math.min(3, (tier.number() - 1) * 3 / 4)], 1);
        }
        if (tier.number() >= 3 && random.nextDouble() < 0.02) {
            add(paid, new String[] {"BURNING_KUUDRA_CORE", "FIERY_KUUDRA_CORE", "INFERNAL_KUUDRA_CORE"}[tier.number() - 3], 1);
        }
        return new Chests(run, tier, List.of(), tier.essence(), paid, tier.essence() * 2 + (tier.number() == 5 ? 2_000 : 0), new boolean[2]);
    }

    private static void add(List<ItemStack> list, String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def != null) {
            list.add(SkyItems.create(def, count));
        }
    }

    /** Opening a chest: the free one any time, the paid one with the tier's key. */
    static void open(ServerPlayer player, boolean paid) {
        Chests chests = CHESTS.get(player.getUUID());
        if (chests == null || chests.opened()[paid ? 1 : 0]) {
            return;
        }
        if (paid) {
            if (SkyStacks.count(player.getInventory(), chests.tier().key()) < 1) {
                player.sendSystemMessage(Component.literal("You need a " + CrimsonIsle.itemName(chests.tier().key()) + " for this chest.")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            SkyStacks.take(player.getInventory(), chests.tier().key(), 1);
        }
        chests.opened()[paid ? 1 : 0] = true;
        int essence = paid ? chests.paidEssence() : chests.freeEssence();
        Essence.give(player, "CRIMSON", essence);
        Notice.Panel panel = Notice.of(Notice.Kind.COMBAT).tag(paid ? "PAID CHEST" : "FREE CHEST").panel()
                .line(Component.literal(" +" + Msg.number(essence) + " Crimson Essence").withStyle(ChatFormatting.RED));
        for (ItemStack stack : paid ? chests.paid() : chests.free()) {
            panel.line(Component.literal(" ").append(stack.getHoverName()).append(stack.getCount() > 1 ? " x" + stack.getCount() : ""));
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy)) {
                player.drop(copy, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        panel.send(player, Component.literal("Kuudra's " + (paid ? "paid" : "free") + " chest").withStyle(ChatFormatting.GOLD));
        Msg.playSound(player, SoundEvents.CHEST_OPEN, 1f, 0.9f);
        if (chests.opened()[0] && chests.opened()[1]) {
            CHESTS.remove(player.getUUID());
        }
    }

    /** Buying a key from an Emissary: coins, your faction's material (Red Sand / Mycelium), Corrupted Nether Stars, rank. */
    static void buyKey(ServerPlayer player, Tier tier) {
        String faction = Factions.faction(player);
        if (faction.isEmpty()) {
            player.sendSystemMessage(Component.literal("Join a faction first.").withStyle(ChatFormatting.RED));
            return;
        }
        String material = material(faction);
        int rep = Factions.reputation(player, faction);
        if (rep < tier.keyRep()) {
            player.sendSystemMessage(Component.literal("You need " + Msg.number(tier.keyRep()) + " " + Factions.title(faction)
                    + " Reputation for this key.").withStyle(ChatFormatting.RED));
            return;
        }
        if (Wallet.balance(player) < tier.keyCoins() || SkyStacks.count(player.getInventory(), material) < tier.keyMaterial()
                || SkyStacks.count(player.getInventory(), "CORRUPTED_NETHER_STAR") < 2) {
            player.sendSystemMessage(Component.literal("You need " + Msg.number(tier.keyCoins()) + " coins, " + tier.keyMaterial() + " "
                    + CrimsonIsle.itemName(material) + " and 2 Corrupted Nether Stars.").withStyle(ChatFormatting.RED));
            return;
        }
        Wallet.take(player, tier.keyCoins());
        SkyStacks.take(player.getInventory(), material, tier.keyMaterial());
        SkyStacks.take(player.getInventory(), "CORRUPTED_NETHER_STAR", 2);
        ItemDefinition def = ItemRegistry.skyblock(tier.key());
        if (def != null) {
            ItemStack key = SkyItems.create(def, 1);
            if (!player.getInventory().add(key)) {
                player.drop(key, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        player.sendSystemMessage(Component.literal("You bought a " + CrimsonIsle.itemName(tier.key()) + "!").withStyle(ChatFormatting.GREEN));
        Msg.playSound(player, SoundEvents.VILLAGER_YES, 1f, 1f);
    }

    static String material(String faction) {
        return faction.equals(Factions.MAGE) ? "ENCHANTED_MYCELIUM" : "ENCHANTED_RED_SAND";
    }
}
