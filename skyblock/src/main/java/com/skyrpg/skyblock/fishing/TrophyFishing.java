package com.skyrpg.skyblock.fishing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.skyrpg.core.action.Actions;
import com.skyrpg.fishing.Catching;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's Trophy Fishing on the Crimson Isle ({@code skyrpg/trophy_fish/trophy_fish.json}, written by
 * {@code tools/skyblock/trophy_fish.py}). A lava catch with a lava rod that isn't a sea creature tries each fish whose
 * condition holds, rarest first, at its chance (more with Trophy Fish Chance); the first that bites is caught, in a tier:
 * Bronze 73.353%, Silver 24.451%, Gold 1.996%, Diamond 0.2% (Charm raises the better ones), with Hypixel's pity (a Gold
 * by the 100th of a fish without one, a Diamond by the 600th). Odger keeps count, ranks you and fillets fish into
 * Magmafish ({@link OdgerMenu}).
 *
 * <p>The Blazing Volcano erupts every 20 minutes; Gushers bite for 10 minutes after.
 */
public final class TrophyFishing {
    public static final List<String> TIERS = List.of("BRONZE", "SILVER", "GOLD", "DIAMOND");
    public static final int[] TIER_COLORS = {0x8A6A4A, 0xC0C0C0, 0xFFAA00, 0x55FFFF};
    /** The rods that fish the Crimson Isle's lava. */
    static final Set<String> LAVA_RODS = Set.of("STARTER_LAVA_ROD", "ELLES_LAVA_ROD", "POLISHED_TOPAZ_ROD", "MAGMA_ROD", "INFERNO_ROD",
            "HELLFIRE_ROD");
    private static final List<String> RARITIES = List.of("common", "uncommon", "rare", "epic", "legendary");
    private static final int ERUPTION_TICKS = 20 * 60 * 20;
    private static final int GUSHER_TICKS = 10 * 60 * 20;

    /** One fish: its id, name, rarity, base chance, condition and its parameters, how it's caught, Magmafish per tier. */
    public record Fish(String id, String name, String rarity, double chance, String condition, JsonObject params, String how, int[] fillet) {
        public int color() {
            return switch (rarity) {
                case "uncommon" -> 0x55FF55;
                case "rare" -> 0x5555FF;
                case "epic" -> 0xAA00AA;
                case "legendary" -> 0xFFAA00;
                default -> 0xFFFFFF;
            };
        }
    }

    private static List<Fish> fish = List.of();
    private static double[] odds = {73.353, 24.451, 1.996, 0.2};

    /** Counts per "BLOBFISH_BRONZE", pity counters per "BLOBFISH:gold" / "BLOBFISH:diamond", and the rank claimed ("rank"). */
    static final AttachmentType<Map<String, Integer>> CAUGHT = AttachmentRegistry.create(SkyRpgSkyblock.id("trophy_fish"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    /** Fishing streaks for the Golden Fish: when it began and the last catch. */
    private static final Map<UUID, long[]> STREAKS = new HashMap<>();
    private static long lastEruption = -GUSHER_TICKS - 1;

    private TrophyFishing() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("trophy_fish"), "skyrpg/trophy_fish", TrophyFishing::load);
        Actions.register("odger", (player, params) -> OdgerMenu.open(player));
        ServerTickEvents.END_SERVER_TICK.register(TrophyFishing::tick);
    }

    private static void load(Map<Identifier, JsonElement> files) {
        List<Fish> loaded = new ArrayList<>();
        files.values().forEach(element -> {
            JsonObject root = element.getAsJsonObject();
            if (root.has("tiers")) {
                JsonObject tiers = root.getAsJsonObject("tiers");
                odds = new double[] {tiers.get("bronze").getAsDouble(), tiers.get("silver").getAsDouble(), tiers.get("gold").getAsDouble(),
                        tiers.get("diamond").getAsDouble()};
            }
            for (JsonElement e : root.getAsJsonArray("fish")) {
                JsonObject f = e.getAsJsonObject();
                int[] fillet = new int[4];
                for (int i = 0; i < 4; i++) {
                    fillet[i] = f.getAsJsonArray("fillet").get(i).getAsInt();
                }
                loaded.add(new Fish(f.get("id").getAsString(), f.get("name").getAsString(), f.get("rarity").getAsString(),
                        f.get("chance").getAsDouble(), f.get("condition").getAsString(), f.getAsJsonObject("params"),
                        f.get("how").getAsString(), fillet));
            }
        });
        fish = List.copyOf(loaded);
    }

    public static List<Fish> fish() {
        return fish;
    }

    static Fish fish(String id) {
        for (Fish f : fish) {
            if (f.id().equals(id)) {
                return f;
            }
        }
        return null;
    }

    /** The trophy fish behind an item id ("BLOBFISH_GOLD" -> Blobfish, 2), or null. */
    static Object[] parse(String skyblockId) {
        for (int t = 0; t < TIERS.size(); t++) {
            String suffix = "_" + TIERS.get(t);
            if (skyblockId != null && skyblockId.endsWith(suffix)) {
                Fish f = fish(skyblockId.substring(0, skyblockId.length() - suffix.length()));
                if (f != null) {
                    return new Object[] {f, t};
                }
            }
        }
        return null;
    }

    static boolean lavaRod(ServerPlayer player) {
        ItemDefinition def = SkyItems.definition(player.getMainHandItem());
        return def != null && def.skyblockId() != null && LAVA_RODS.contains(def.skyblockId());
    }

    // ---------------------------------------------------------------- the volcano

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % ERUPTION_TICKS != ERUPTION_TICKS / 2) {
            return;
        }
        ServerLevel level = com.skyrpg.skyblock.crimson.CrimsonIsle.level(server);
        if (level == null) {
            return;
        }
        lastEruption = now;
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.literal("The Blazing Volcano erupts! Gushers are biting.").withStyle(ChatFormatting.GOLD));
            Msg.playSound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 0.5f);
        }
        com.skyrpg.skyblock.crimson.CrimsonIsle.erupt(level);
    }

    // ---------------------------------------------------------------- catching

    /**
     * A lava catch on the Crimson Isle: a trophy fish if one bites (given to the player and returned for the catch
     * event), else empty.
     */
    static ItemStack tryCatch(ServerPlayer player, FishingHook hook, SkyFishing.Bait bait, String zone) {
        if (!lavaRod(player)) {
            return ItemStack.EMPTY;
        }
        long now = player.level().getServer().getTickCount();
        long[] streak = STREAKS.computeIfAbsent(player.getUUID(), k -> new long[] {now, now});
        if (now - streak[1] > 2 * 60 * 20) {
            streak[0] = now;
        }
        streak[1] = now;
        double boost = 1 + Stats.get(player, StatTypes.TROPHY_FISH_CHANCE) / 100.0;
        List<Fish> order = new ArrayList<>(fish);
        order.sort((a, b) -> Integer.compare(RARITIES.indexOf(b.rarity()), RARITIES.indexOf(a.rarity())));
        for (Fish f : order) {
            if (!bites(f, player, hook, bait, zone, now, streak[0]) || player.getRandom().nextDouble() * 100 >= f.chance() * boost) {
                continue;
            }
            if (f.condition().equals("carried")) {
                takeOne(player, f.params().get("item").getAsString());
            }
            return caught(player, f);
        }
        return ItemStack.EMPTY;
    }

    private static boolean bites(Fish f, ServerPlayer player, FishingHook hook, SkyFishing.Bait bait, String zone, long now, long streakStart) {
        JsonObject p = f.params();
        if (p.has("zones")) {
            boolean in = false;
            for (JsonElement z : p.getAsJsonArray("zones")) {
                in |= z.getAsString().equals(zone);
            }
            if (!in) {
                return false;
            }
        }
        return switch (f.condition()) {
            case "anywhere", "zone" -> true;
            case "eruption" -> now - lastEruption <= GUSHER_TICKS;
            case "bait" -> bait != null && bait.id().equals(p.get("bait").getAsString());
            case "geyser" -> nearGeyser(hook.position(), p.get("radius").getAsDouble());
            case "near_block" -> nearBlock((ServerLevel) hook.level(), hook.blockPosition(), p.get("block").getAsString(), p.get("radius").getAsInt());
            case "below" -> player.getY() - hook.getY() >= p.get("blocks").getAsDouble();
            case "slow" -> hook.tickCount >= p.get("seconds").getAsInt() * 20;
            case "mana" -> Stats.mana(player) >= p.get("mana").getAsDouble();
            case "plain_rod" -> {
                ItemDefinition def = SkyItems.definition(player.getMainHandItem());
                yield def != null && p.get("rod").getAsString().equals(def.skyblockId()) && ItemMods.enchantments(player.getMainHandItem()).isEmpty();
            }
            case "carried" -> carries(player, p.get("item").getAsString());
            case "streak" -> now - streakStart >= p.get("minutes").getAsLong() * 60 * 20;
            default -> false;
        };
    }

    private static boolean nearGeyser(Vec3 at, double radius) {
        for (Map.Entry<String, com.skyrpg.skyblock.crimson.CrimsonIsle.Spot> e : com.skyrpg.skyblock.crimson.CrimsonIsle.spots().entrySet()) {
            if (e.getKey().startsWith("geyser") && e.getValue().pos().distanceTo(at) <= radius + 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearBlock(ServerLevel level, BlockPos at, String id, int radius) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        for (BlockPos pos : BlockPos.betweenClosed(at.offset(-radius, -radius, -radius), at.offset(radius, radius, radius))) {
            if (level.getBlockState(pos).is(block)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player carries any tier of this trophy fish (Obfuscated fish are the next one's bait). */
    private static boolean carries(ServerPlayer player, String fishId) {
        for (String tier : TIERS) {
            if (com.skyrpg.items.SkyStacks.count(player.getInventory(), fishId + "_" + tier) > 0) {
                return true;
            }
        }
        return false;
    }

    private static void takeOne(ServerPlayer player, String fishId) {
        for (String tier : TIERS) {
            if (com.skyrpg.items.SkyStacks.count(player.getInventory(), fishId + "_" + tier) > 0) {
                com.skyrpg.items.SkyStacks.take(player.getInventory(), fishId + "_" + tier, 1);
                return;
            }
        }
    }

    private static ItemStack caught(ServerPlayer player, Fish f) {
        Map<String, Integer> counts = new HashMap<>(player.getAttachedOrElse(CAUGHT, Map.of()));
        int charm = ItemMods.enchantment(player.getMainHandItem(), "charm");
        double silver = odds[1] * (1 + 0.0195 * charm);
        double gold = odds[2] * (1 + 0.02 * charm);
        double diamond = odds[3] * (1 + 0.02 * charm);
        double roll = player.getRandom().nextDouble() * 100;
        int tier = roll < diamond ? 3 : roll < diamond + gold ? 2 : roll < diamond + gold + silver ? 1 : 0;
        // Pity: a Gold by the 100th catch without one, a Diamond by the 600th.
        int sinceGold = counts.getOrDefault(f.id() + ":gold", 0) + 1;
        int sinceDiamond = counts.getOrDefault(f.id() + ":diamond", 0) + 1;
        if (tier < 3 && sinceDiamond >= 600) {
            tier = 3;
        } else if (tier < 2 && sinceGold >= 100) {
            tier = 2;
        }
        counts.put(f.id() + ":gold", tier >= 2 ? 0 : sinceGold);
        counts.put(f.id() + ":diamond", tier >= 3 ? 0 : sinceDiamond);
        String key = f.id() + "_" + TIERS.get(tier);
        boolean first = counts.getOrDefault(key, 0) == 0;
        counts.merge(key, 1, Integer::sum);
        player.setAttached(CAUGHT, Map.copyOf(counts));

        ItemDefinition def = ItemRegistry.skyblock(key);
        ItemStack stack = def == null ? ItemStack.EMPTY : SkyItems.create(def, 1);
        ItemStack shown = stack.copy();
        if (!stack.isEmpty()) {
            Catching.give(player, stack);
        }
        Notice.of(Notice.Kind.FISHING).tag("TROPHY FISH!").color(0xFFAA00).send(player, Component.literal("You caught a ")
                .append(Component.literal(f.name() + " ").withColor(f.color()))
                .append(Component.literal(TIERS.get(tier)).withColor(TIER_COLORS[tier]).withStyle(ChatFormatting.BOLD))
                .append(Component.literal(first ? ". New!" : ".")));
        SkillLevels.giveXp(player, SkillLevels.FISHING, 50.0 * (1 << RARITIES.indexOf(f.rarity())));
        Msg.playSound(player, tier >= 2 ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.2f + tier * 0.1f);
        return shown;
    }

    // ---------------------------------------------------------------- Odger's ranks

    /** The best tier caught of each fish (-1 none). */
    static int best(Map<String, Integer> counts, Fish f) {
        for (int t = 3; t >= 0; t--) {
            if (counts.getOrDefault(f.id() + "_" + TIERS.get(t), 0) > 0) {
                return t;
            }
        }
        return -1;
    }

    /** The rank earned: 1 Novice (Bronze or better of 15 fish), 2 Adept (Silver of all), 3 Expert (Gold of all), 4 Master (Diamond of all). */
    static int earned(Map<String, Integer> counts) {
        int bronze = 0;
        int lowest = 3;
        for (Fish f : fish) {
            int best = best(counts, f);
            if (best >= 0) {
                bronze++;
            }
            lowest = Math.min(lowest, best);
        }
        if (fish.isEmpty() || bronze < 15) {
            return 0;
        }
        return lowest >= 3 ? 4 : lowest >= 2 ? 3 : lowest >= 1 ? 2 : 1;
    }
}
