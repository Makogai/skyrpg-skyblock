package com.skyrpg.skyblock.fishing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.action.Catches;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.fishing.Catching;
import com.skyrpg.fishing.LavaFishing;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.enchant.Books;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.island.Islands;
import com.skyrpg.skyblock.mining.SkyMining;
import com.skyrpg.skyblock.pet.SkyBlockPets;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel SkyBlock fishing, in every SkyBlock world (it replaces the survival fishing there):
 * <ul>
 *   <li>The wait: 10 to 20 seconds, shortened by Fishing Speed (300 makes it nothing).</li>
 *   <li>A bite is a sea creature at Sea Creature Chance (20% to start; a quarter of it on a private island), see
 *       {@link SkySeaCreatures}; Double Hook Chance brings up two.</li>
 *   <li>Otherwise treasure at Treasure Chance (Fishing levels give it): a Good (89%), Great (10%) or Outstanding (1%)
 *       catch.</li>
 *   <li>Otherwise a fish (lava: lava's catches). Items give the Fishing XP Hypixel gives for them.</li>
 *   <li>Bait: the first bait in the inventory counts for the cast and one is used up by each catch.</li>
 *   <li>Any rod fishes in the Crystal Hollows' lava, for its lava sea creatures.</li>
 * </ul>
 * Catches, treasure and baits come from {@code skyrpg/skyblock_fishing/catches.json} ({@code tools/skyblock/import_sea_creatures.py}).
 */
public final class SkyFishing {
    /** Hypixel's Fishing Speed cap: this much and fish bite as soon as the bobber lands. */
    private static final double SPEED_CAP = 300;
    /** Fishing XP for a treasure item Hypixel gives none for, by tier. */
    private static final Map<String, Double> TREASURE_XP = Map.of("good", 50.0, "great", 200.0, "outstanding", 800.0);

    /** A Hypixel bait: what it adds while it's the bait you fish with. */
    public record Bait(String id, double speed, double seaCreatureChance, double treasureChance, String rare, double carrotKing,
                       double blessed, boolean hollowsOnly) {
    }

    /** One catch in a pool: a SkyBlock item ({@code pet:SQUID:EPIC}, {@code coins:min-max}), how many, its weight. */
    record Catch(String item, int count, double weight) {
    }

    private static Map<String, List<Catch>> normal = Map.of();
    private static Map<String, List<Catch>> treasure = Map.of();
    private static Map<String, Double> tiers = Map.of();
    private static Map<String, Bait> baits = Map.of();

    private SkyFishing() {
    }

    public static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("skyblock_fishing"), "skyrpg/skyblock_fishing", SkyFishing::load);
        Catching.installSkyBlock(new Catching.SkyBlockFishing() {
            @Override
            public int waitTicks(FishingHook hook, ServerPlayer player) {
                return SkyFishing.waitTicks(hook, player);
            }

            @Override
            public boolean reel(FishingHook hook, ServerPlayer player, ItemStack rod) {
                return SkyFishing.reel(hook, player);
            }
        });
        // Any rod's bobber floats on the Crystal Hollows' lava; the Crimson Isle's takes a lava rod (on both sides: the client
        // moves the bobber too; the client's check reads the rod's SkyBlock id the same way).
        LavaFishing.allowElsewhere((player, level) -> level.dimension().equals(SkyMining.CRYSTAL_HOLLOWS)
                && (player.getMainHandItem().getItem() instanceof FishingRodItem || player.getOffhandItem().getItem() instanceof FishingRodItem)
                || level.dimension().equals(com.skyrpg.skyblock.crimson.CrimsonIsle.LEVEL) && lavaRod(player));
        TrophyFishing.init();
        SkySeaCreatures.init();
        // Testing: reel in the line a player has out as if a fish bit, or bring up a given sea creature at it.
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(
                net.minecraft.commands.Commands.literal("sbfishing").requires(net.minecraft.commands.Commands.hasPermission(
                        net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .then(net.minecraft.commands.Commands.literal("reel").then(net.minecraft.commands.Commands.argument("player",
                                net.minecraft.commands.arguments.EntityArgument.player()).executes(ctx -> {
                                    ServerPlayer player = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                    FishingHook hook = player.fishing;
                                    if (hook == null) {
                                        ctx.getSource().sendFailure(Component.literal(player.getScoreboardName() + " has no line out."));
                                        return 0;
                                    }
                                    boolean caught = reel(hook, player);
                                    hook.discard();
                                    return caught ? 1 : 0;
                                })))
                        .then(net.minecraft.commands.Commands.literal("creature").then(net.minecraft.commands.Commands.argument("player",
                                net.minecraft.commands.arguments.EntityArgument.player()).then(net.minecraft.commands.Commands.argument("id",
                                        com.mojang.brigadier.arguments.StringArgumentType.word()).suggests((ctx, builder) ->
                                        net.minecraft.commands.SharedSuggestionProvider.suggest(SkySeaCreatures.all().stream()
                                                .map(SkySeaCreatures.Creature::id).toList(), builder)).executes(ctx -> {
                                    ServerPlayer player = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                    String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                                    FishingHook hook = player.fishing;
                                    SkySeaCreatures.Creature creature = SkySeaCreatures.all().stream().filter(c -> c.id().equals(id)).findFirst()
                                            .orElse(null);
                                    if (hook == null || creature == null) {
                                        ctx.getSource().sendFailure(Component.literal(hook == null ? "No line out." : "No sea creature " + id + "."));
                                        return 0;
                                    }
                                    SkySeaCreatures.surface(creature, hook, player, false);
                                    hook.discard();
                                    return 1;
                                }))))));
    }

    private static void load(Map<Identifier, JsonElement> files) {
        files.forEach((id, element) -> {
            JsonObject json = element.getAsJsonObject();
            if (id.getPath().endsWith("sea_creatures")) {
                SkySeaCreatures.load(json);
            } else if (id.getPath().endsWith("catches")) {
                loadCatches(json);
            }
        });
    }

    private static void loadCatches(JsonObject json) {
        Map<String, List<Catch>> loadedNormal = new HashMap<>();
        json.getAsJsonObject("normal").entrySet().forEach(e -> loadedNormal.put(e.getKey(), pool(e.getValue())));
        Map<String, List<Catch>> loadedTreasure = new HashMap<>();
        json.getAsJsonObject("treasure").entrySet().forEach(e -> loadedTreasure.put(e.getKey(), pool(e.getValue())));
        Map<String, Double> loadedTiers = new HashMap<>();
        json.getAsJsonObject("tiers").entrySet().forEach(e -> loadedTiers.put(e.getKey(), e.getValue().getAsDouble()));
        Map<String, Bait> loadedBaits = new HashMap<>();
        json.getAsJsonObject("baits").entrySet().forEach(e -> {
            JsonObject b = e.getValue().getAsJsonObject();
            loadedBaits.put(e.getKey(), new Bait(e.getKey(), number(b, "speed"), number(b, "sea_creature_chance"), number(b, "treasure_chance"),
                    b.has("rare") ? b.get("rare").getAsString() : "", number(b, "carrot_king"), number(b, "blessed"),
                    b.has("hollows") && b.get("hollows").getAsBoolean()));
        });
        normal = loadedNormal;
        treasure = loadedTreasure;
        tiers = loadedTiers;
        baits = loadedBaits;
    }

    private static List<Catch> pool(JsonElement element) {
        List<Catch> out = new ArrayList<>();
        for (JsonElement e : element.getAsJsonArray()) {
            JsonObject c = e.getAsJsonObject();
            out.add(new Catch(c.get("item").getAsString(), c.has("count") ? c.get("count").getAsInt() : 1, c.get("weight").getAsDouble()));
        }
        return List.copyOf(out);
    }

    private static double number(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsDouble() : 0;
    }

    /** The first bait in the inventory (Hypixel uses up baits in inventory order), or null. */
    public static Bait bait(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemDefinition def = SkyItems.definition(inventory.getItem(i));
            Bait bait = def == null ? null : baits.get(def.skyblockId());
            if (bait != null) {
                return bait;
            }
        }
        return null;
    }

    private static void useBait(ServerPlayer player, Bait bait) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemDefinition def = SkyItems.definition(inventory.getItem(i));
            if (def != null && def.skyblockId().equals(bait.id())) {
                inventory.getItem(i).shrink(1);
                return;
            }
        }
    }

    /** The bait's bonuses count here (Worm Bait only in the Crystal Hollows). */
    private static boolean counts(Bait bait, ServerLevel level) {
        return bait != null && (!bait.hollowsOnly() || level.dimension().equals(SkyMining.CRYSTAL_HOLLOWS));
    }

    /** Hypixel's wait: 200 to 400 ticks, less by Fishing Speed over its cap of 300. */
    static int waitTicks(FishingHook hook, ServerPlayer player) {
        Bait bait = bait(player);
        double speed = Stats.get(player, StatTypes.FISHING_SPEED) + (counts(bait, player.level()) ? bait.speed() : 0);
        int base = 200 + player.getRandom().nextInt(201);
        return (int) Math.round(base * Math.max(0, 1 - speed / SPEED_CAP));
    }

    /** A lava rod (Starter Lava Rod, Magma Rod...) in either hand, by its SkyBlock id. */
    private static boolean lavaRod(net.minecraft.world.entity.player.Player player) {
        for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
            ItemDefinition def = com.skyrpg.items.SkyItems.definition(stack);
            if (def != null && def.skyblockId() != null && TrophyFishing.LAVA_RODS.contains(def.skyblockId())) {
                return true;
            }
        }
        return false;
    }

    /** Reeling in a bite: a sea creature, a trophy fish (the Crimson Isle's lava), treasure or a catch, the bait used up. */
    static boolean reel(FishingHook hook, ServerPlayer player) {
        ServerLevel level = player.level();
        boolean lava = LavaFishing.inLava(hook);
        if (!lava && !hook.isInWater()) {
            return false;
        }
        RandomSource random = player.getRandom();
        Bait bait = bait(player);
        boolean baitCounts = counts(bait, level);
        Zones.Zone zone = Zones.at(level, hook.blockPosition());
        String zoneName = zone == null ? "" : zone.name();
        double chance = Stats.get(player, StatTypes.SEA_CREATURE_CHANCE) + (baitCounts ? bait.seaCreatureChance() : 0);
        if (Islands.isIslands(level)) {
            chance /= 4;
        }
        boolean creature = false;
        if (random.nextDouble() * 100 < chance && SkySeaCreatures.room(player)) {
            SkySeaCreatures.Creature first = SkySeaCreatures.roll(player, level, lava, zoneName, baitCounts ? bait : null);
            if (first != null) {
                creature = true;
                SkySeaCreatures.surface(first, hook, player, false);
                if (random.nextDouble() * 100 < Stats.get(player, StatTypes.DOUBLE_HOOK_CHANCE) && SkySeaCreatures.room(player)) {
                    SkySeaCreatures.Creature second = SkySeaCreatures.roll(player, level, lava, zoneName, baitCounts ? bait : null);
                    if (second != null) {
                        SkySeaCreatures.surface(second, hook, player, true);
                    }
                }
            }
        }
        ItemStack caught = ItemStack.EMPTY;
        if (!creature && lava && level.dimension().equals(com.skyrpg.skyblock.crimson.CrimsonIsle.LEVEL)) {
            caught = TrophyFishing.tryCatch(player, hook, baitCounts ? bait : null, zoneName);
        }
        if (!creature && caught.isEmpty()) {
            double treasureChance = Stats.get(player, StatTypes.TREASURE_CHANCE) + (baitCounts ? bait.treasureChance() : 0);
            caught = !lava && random.nextDouble() * 100 < treasureChance ? treasure(player, baitCounts ? bait : null)
                    : fish(player, lava ? "lava" : "water");
        }
        if (bait != null) {
            useBait(player, bait);
        }
        Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
        Catches.CAUGHT.invoker().onCaught(player, caught, creature);
        return true;
    }

    /** A fish (or lava's catch), with the Fishing XP Hypixel gives for it. */
    private static ItemStack fish(ServerPlayer player, String fluid) {
        Catch pick = pick(player.getRandom(), normal.getOrDefault(fluid, List.of()));
        if (pick == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = stack(pick.item(), pick.count());
        if (stack.isEmpty()) {
            return stack;
        }
        SkillLevels.giveXp(player, SkillLevels.FISHING, fishXp(pick.item(), 25));
        ItemStack shown = stack.copy();
        Catching.give(player, stack);
        return shown;
    }

    /** Good, Great or Outstanding (Blessed Bait makes the better two likelier), and one thing from that tier's pool. */
    private static ItemStack treasure(ServerPlayer player, Bait bait) {
        RandomSource random = player.getRandom();
        double boost = 1 + (bait == null ? 0 : bait.blessed());
        double great = tiers.getOrDefault("great", 10.0) * boost;
        double outstanding = tiers.getOrDefault("outstanding", 1.0) * boost;
        double roll = random.nextDouble() * (tiers.getOrDefault("good", 89.0) + great + outstanding);
        String tier = roll < outstanding ? "outstanding" : roll < outstanding + great ? "great" : "good";
        Catch pick = pick(random, treasure.getOrDefault(tier, List.of()));
        if (pick == null) {
            return ItemStack.EMPTY;
        }
        String title = tier.toUpperCase(Locale.ROOT) + " CATCH!";
        int color = switch (tier) {
            case "outstanding" -> 0xFF55FF;
            case "great" -> 0xAA00AA;
            default -> 0xFFAA00;
        };
        double xp = TREASURE_XP.getOrDefault(tier, 50.0);
        ItemStack shown = ItemStack.EMPTY;
        if (pick.item().startsWith("coins:")) {
            String[] range = pick.item().substring(6).split("-");
            long low = Long.parseLong(range[0]);
            long high = Long.parseLong(range[1]);
            long coins = low + (long) (random.nextDouble() * (high - low + 1));
            Wallet.give(player, coins);
            Notice.of(Notice.Kind.FISHING).tag(title).color(color).send(player, Component.literal("You found ")
                    .append(Component.literal(Msg.number(coins) + " Coins").withColor(0xFFAA00)).append("."));
        } else {
            ItemStack stack = stack(pick.item(), pick.count());
            if (stack.isEmpty()) {
                return stack;
            }
            shown = stack.copy();
            xp = Math.max(xp, fishXp(pick.item(), 0));
            Notice.of(Notice.Kind.FISHING).tag(title).color(color).send(player, Component.literal("You found ")
                    .append(stack.getCount() > 1 ? Component.literal(stack.getCount() + "x ") : Component.empty())
                    .append(stack.getHoverName()).append("."));
            Catching.give(player, stack);
        }
        SkillLevels.giveXp(player, SkillLevels.FISHING, xp);
        Msg.playSound(player, tier.equals("good") ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.PLAYER_LEVELUP, 0.8f,
                tier.equals("outstanding") ? 0.8f : 1.4f);
        return shown;
    }

    private static Catch pick(RandomSource random, List<Catch> pool) {
        double total = 0;
        for (Catch c : pool) {
            total += c.weight();
        }
        double roll = random.nextDouble() * total;
        for (Catch c : pool) {
            roll -= c.weight();
            if (roll < 0) {
                return c;
            }
        }
        return null;
    }

    /** The Fishing XP Hypixel gives for catching this item ({@code experience.fishing.FISH_ITEM}), or {@code fallback}. */
    private static double fishXp(String item, double fallback) {
        ItemDefinition def = ItemRegistry.skyblock(item);
        if (def == null || !def.json().has("hypixel")) {
            return fallback;
        }
        JsonObject hypixel = def.json().getAsJsonObject("hypixel");
        if (!hypixel.has("experience") || !hypixel.getAsJsonObject("experience").has("fishing")) {
            return fallback;
        }
        JsonObject fishing = hypixel.getAsJsonObject("experience").getAsJsonObject("fishing");
        return fishing.has("FISH_ITEM") ? fishing.get("FISH_ITEM").getAsDouble() : fallback;
    }

    /** A SkyBlock item, a pet ({@code pet:SQUID:EPIC}) or an enchanted book ({@code book:angler:6}); empty if unknown. */
    static ItemStack stack(String item, int count) {
        if (item.startsWith("pet:")) {
            String[] parts = item.split(":");
            return SkyBlockPets.known(parts[1]) ? SkyBlockPets.create(parts[1], parts[2], 0) : ItemStack.EMPTY;
        }
        if (item.startsWith("book:")) {
            String[] parts = item.split(":");
            return Books.create(parts[1], Integer.parseInt(parts[2]), count);
        }
        ItemDefinition def = ItemRegistry.skyblock(item);
        return def == null ? ItemStack.EMPTY : SkyItems.create(def, count);
    }

    static int rarityColor(String rarity) {
        try {
            return com.skyrpg.items.Rarity.valueOf(rarity).color() & 0xFFFFFF;
        } catch (IllegalArgumentException e) {
            return 0xFFFFFF;
        }
    }

    static int rarityColor(ItemStack stack) {
        ItemDefinition def = SkyItems.definition(stack);
        return def == null ? 0xFFFFFF : def.rarity().color() & 0xFFFFFF;
    }
}
