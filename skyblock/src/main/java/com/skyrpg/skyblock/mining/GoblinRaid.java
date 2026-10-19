package com.skyrpg.skyblock.mining;

import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The Goblin Raid, the active Dwarven Mines event (see {@link MiningEvents}): for five minutes goblins pour into the
 * Goblin Burrows' halls, and the server's players have to slay a goal's worth of them. Raiders: Weakling and Fireslinger
 * (200 health), Creeperlobber and Pitfighter (30,000), the rare Murderlover (50,000) and the Superprotectron, an Iron
 * Golem that takes 12 hits and counts for 10 kills. At the end everybody with a kill gets Hypixel's rewards for their
 * kills (25 or more gets the bonus) and for whether the goal was reached: Mining XP, HotM XP and Mithril Powder.
 *
 * <p>Decided: the goal is 100 kills for each player in the Mines (Hypixel's 500-1,000 is for a full lobby), at least 100
 * and at most 1,000. The raiders don't throw creepers or fire and the Superprotectron doesn't shield its goblins. The
 * Bits and Mining Item rewards (Mining Exp Boost, Flowstate, Dwarven O's Ore Oats, Dwarven Treasure, Titanium Minecart)
 * are left out.
 */
final class GoblinRaid {
    private static final String TAG = "skyrpg_goblin_raider";
    /** The hall in the Goblin Burrows the raiders pour into. */
    private static final BlockPos FROM = new BlockPos(-124, 129, 148);
    private static final BlockPos TO = new BlockPos(-100, 133, 165);

    private record Kind(String name, int weight, double health, double damage, int level, double xp, double speed, int hits, int counts) {
    }

    private static final List<Kind> KINDS = List.of(
            new Kind("Weakling", 40, 200, 20, 13, 8, 1.0, 0, 1),
            new Kind("Fireslinger", 25, 200, 20, 13, 8, 1.0, 0, 1),
            new Kind("Creeperlobber", 12, 30_000, 10, 35, 40, 1.0, 0, 1),
            new Kind("Pitfighter", 12, 30_000, 20, 35, 40, 0.6, 0, 1),
            new Kind("Murderlover", 3, 50_000, 50, 45, 60, 1.2, 0, 1),
            new Kind("Superprotectron", 5, 1_000, 50, 50, 50, 0.9, 12, 10));

    private static boolean running;
    private static MiningEvents.Active event;
    private static int goal;
    private static int total;
    private static boolean won;
    /** The raid event whose goal was reached (it doesn't start again). */
    private static MiningEvents.Active beaten;
    private static final Map<UUID, Integer> KILLS = new HashMap<>();
    /** Raiders out now, and for the Superprotectrons the hits they've taken. */
    private static final Set<UUID> RAIDERS = new HashSet<>();
    private static final Map<UUID, Integer> HITS = new HashMap<>();

    private GoblinRaid() {
    }

    static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!entity.entityTags().contains(TAG) || !(entity instanceof Mob)) {
                return true;
            }
            Integer taken = HITS.get(entity.getUUID());
            if (taken == null) {
                return true;
            }
            if (!(source.getEntity() instanceof ServerPlayer)) {
                return false;
            }
            HITS.put(entity.getUUID(), taken + 1);
            if (taken + 1 >= 12) {
                entity.setHealth(1.0f);
                return true;
            }
            return false;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!RAIDERS.remove(entity.getUUID())) {
                return;
            }
            HITS.remove(entity.getUUID());
            if (running && source.getEntity() instanceof ServerPlayer player) {
                int counts = entity.getType().equals(EntityTypes.IRON_GOLEM) ? 10 : 1;
                total += counts;
                KILLS.merge(player.getUUID(), counts, Integer::sum);
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && !RAIDERS.contains(entity.getUUID())) {
                level.getServer().execute(entity::discard);
            }
        });
    }

    static void tick(MinecraftServer server, MiningEvents.Active current) {
        boolean on = current != null && current.kind() == MiningEvents.Kind.GOBLIN_RAID;
        if (running && (!on || !current.equals(event))) {
            finish(server);
        }
        if (!on) {
            return;
        }
        ServerLevel level = server.getLevel(SkyMining.DWARVEN_MINES);
        if (level == null) {
            return;
        }
        if (!running && !current.equals(beaten)) {
            begin(level, current);
        }
        if (!running) {
            return;
        }
        if (total >= goal) {
            won = true;
            beaten = current;
            finish(server);
            won = false;
            return;
        }
        RAIDERS.removeIf(id -> level.getEntity(id) == null);
        int cap = Math.min(40, 10 + 4 * level.players().size());
        for (int i = 0; i < 3 && RAIDERS.size() < cap; i++) {
            spawn(level);
        }
    }

    private static void begin(ServerLevel level, MiningEvents.Active current) {
        running = true;
        event = current;
        total = 0;
        KILLS.clear();
        goal = Math.max(100, Math.min(1_000, 100 * level.players().size()));
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.literal("Kill all ").withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(String.valueOf(goal)).withStyle(ChatFormatting.RED))
                    .append(Component.literal(" goblins to win! Earn extra rewards by slaying at least ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("25").withStyle(ChatFormatting.RED)).append(Component.literal(" yourself! Go to the ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("Goblin Burrows").withStyle(ChatFormatting.AQUA)).append(Component.literal(".").withStyle(ChatFormatting.WHITE)));
        }
    }

    private static void spawn(ServerLevel level) {
        int pick = level.getRandom().nextInt(KINDS.stream().mapToInt(Kind::weight).sum());
        Kind kind = KINDS.get(0);
        for (Kind k : KINDS) {
            pick -= k.weight();
            if (pick < 0) {
                kind = k;
                break;
            }
        }
        BlockPos at = null;
        for (int i = 0; i < 12 && at == null; i++) {
            BlockPos pos = new BlockPos(FROM.getX() + level.getRandom().nextInt(TO.getX() - FROM.getX() + 1), FROM.getY() + level.getRandom().nextInt(TO.getY() - FROM.getY() + 1),
                    FROM.getZ() + level.getRandom().nextInt(TO.getZ() - FROM.getZ() + 1));
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.below()).isSolid()) {
                at = pos;
            }
        }
        if (at == null) {
            return;
        }
        EntityType<? extends Mob> type = kind.hits() > 0 ? EntityTypes.IRON_GOLEM : EntityTypes.ZOMBIE;
        Mob mob = type.create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return;
        }
        mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360, 0);
        Zones.dressAs(mob, kind.name(), kind.level(), kind.health(), kind.damage(), kind.xp());
        if (kind.speed() != 1.0 && mob.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(mob.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue() * kind.speed());
        }
        mob.addTag(TAG);
        mob.addTag(Zones.FIGHTABLE_TAG);
        mob.setPersistenceRequired();
        mob.setCustomNameVisible(true);
        RAIDERS.add(mob.getUUID());
        if (kind.hits() > 0) {
            HITS.put(mob.getUUID(), 0);
        }
        level.addFreshEntity(mob);
    }

    /** Ends the raid (goal reached, time up or stopped): clears the raiders and pays out. */
    private static void finish(MinecraftServer server) {
        running = false;
        ServerLevel level = server.getLevel(SkyMining.DWARVEN_MINES);
        for (UUID id : new ArrayList<>(RAIDERS)) {
            if (level != null && level.getEntity(id) != null) {
                level.getEntity(id).discard();
            }
        }
        RAIDERS.clear();
        HITS.clear();
        for (Map.Entry<UUID, Integer> entry : KILLS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && entry.getValue() > 0) {
                reward(player, entry.getValue(), won);
            }
        }
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                if (!KILLS.containsKey(player.getUUID())) {
                    player.sendSystemMessage(Component.literal(won ? "The raid is beaten! Slay goblins to share the next one's rewards."
                            : "The raid is over.").withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        KILLS.clear();
    }

    private static void reward(ServerPlayer player, int kills, boolean success) {
        boolean bonus = kills >= 25;
        int miningXp;
        int hotm;
        int powder;
        if (bonus) {
            miningXp = success ? 30_000 : 20_000;
            hotm = success ? 250 : 200;
            powder = (success ? 20_000 : 10_000) + (success ? 100 : 50) * kills;
        } else {
            miningXp = success ? 10_000 : 0;
            hotm = 0;
            powder = (success ? 2_000 : 0) + 50 * kills;
        }
        Notice.Panel panel = Notice.of(Notice.Kind.EVENT).tag("GOBLIN RAID").panel();
        panel.line(Component.literal("You slew " + kills + " Goblins" + (bonus ? ", the bonus!" : ".")).withStyle(ChatFormatting.GRAY));
        panel.heading("Rewards", 0x55FF55);
        if (miningXp > 0) {
            SkillLevels.giveXp(player, SkillLevels.MINING, miningXp);
            panel.line(Component.literal("+" + Msg.number(miningXp) + " Mining Experience").withStyle(ChatFormatting.DARK_AQUA));
        }
        if (hotm > 0) {
            Hotm.giveXp(player, hotm);
            panel.line(Component.literal("+" + hotm + " HotM Experience").withStyle(ChatFormatting.DARK_PURPLE));
        }
        Hotm.givePowder(player, Hotm.MITHRIL, powder);
        panel.line(Component.literal("+" + Msg.number(powder) + " " + Hotm.powderName(Hotm.MITHRIL)).withColor(Hotm.powderColor(Hotm.MITHRIL)));
        panel.send(player, Component.literal(success ? "GOBLIN RAID BEATEN!" : "GOBLIN RAID OVER").withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    /** The raid's sidebar lines (each starts with a new line). */
    static String sidebar(ServerPlayer player) {
        if (!running) {
            return "";
        }
        return "\n§cGoblins: §f" + total + "/" + goal + "\n§7You: §a" + KILLS.getOrDefault(player.getUUID(), 0);
    }
}
