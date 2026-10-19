package com.skyrpg.skyblock.leveling;

import com.mojang.serialization.Codec;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Hypixel's SkyBlock Leveling tasks: where SkyBlock XP comes from, in its categories (Core, Event, Dungeon, Essence
 * Shop, Slaying, Skill Related, Miscellaneous, Story, Consumables), each task with the most XP it can give (the wiki's,
 * 0.27.2). Every SkyBlock XP given through {@link SkyBlockXp#give} is tallied to its task by its reason: a task's own
 * name ("Pet Score"), or the reasons the older sources use ("Mining level", "collection tier"...). The SkyBlock
 * Leveling menu ({@link LevelingMenu}) shows the tally against each task's most.
 *
 * <p>Sources that pay XP for a state (Pet Score, powder, Heart of the Mountain tiers) {@link #sync} it: they say how much
 * the task is worth now and get the difference over what it's already paid. One-off rewards (a first clear, a first
 * kill) go through {@link #once}.
 *
 * <p>Decided: XP given before this tally existed isn't split into tasks (it stays in the level); tasks for systems we
 * don't have yet are listed all the same (as on Hypixel's menu), at 0.
 */
public final class LevelingTasks {
    /** A task category, with its menu icon and colour. */
    public enum Category {
        CORE("Core Tasks", Items.NETHER_STAR, 0x55FF55, "The main ways to level: skills, collections, minions..."),
        EVENT("Event Tasks", Items.CAKE, 0xFFAA00, "SkyBlock's events: festivals, contests, Hoppity's Hunt"),
        DUNGEON("Dungeon Tasks", Items.WITHER_SKELETON_SKULL, 0xFF5555, "The Catacombs: levels, classes and floors"),
        ESSENCE("Essence Shop Tasks", Items.NETHER_WART, 0xAA00AA, "Perks bought with essence"),
        SLAYING("Slaying Tasks", Items.GOLDEN_SWORD, 0xFF5555, "Slayers, the Bestiary, dragons, Kuudra..."),
        SKILL("Skill Related Tasks", Items.DIAMOND_PICKAXE, 0x55FFFF, "Heart of the Mountain, the Garden, Trophy Fish..."),
        MISC("Miscellaneous Tasks", Items.BOOK, 0xFFFF55, "The Rift, the Dojo, reputation, the Community Shop..."),
        STORY("Story Tasks", Items.WRITABLE_BOOK, 0xAAAAAA, "Objectives and the Rift Guide"),
        CONSUMABLES("Consumable Items", Items.COOKIE, 0xFF55FF, "Items that give SkyBlock XP once eaten or used");

        public final String title;
        public final Item icon;
        public final int color;
        public final String about;

        Category(String title, Item icon, int color, String about) {
            this.title = title;
            this.icon = icon;
            this.color = color;
            this.about = about;
        }
    }

    /** A task: its id, category, name, how it pays, the most XP it gives, and its icon. */
    public record Task(String id, Category category, String name, String how, int max, Item icon) {
    }

    private static final Map<String, Task> TASKS = new LinkedHashMap<>();
    private static final Map<String, String> BY_NAME = new HashMap<>();

    private static void task(String id, Category category, String name, String how, int max, Item icon) {
        Task task = new Task(id, category, name, how, max, icon);
        TASKS.put(id, task);
        BY_NAME.put(name.toLowerCase(java.util.Locale.ROOT), id);
    }

    static {
        Category c = Category.CORE;
        task("skill_level_up", c, "Skill Level Up", "Each skill level: 1-10 +5, 11-25 +10, 26-50 +20, 51-60 +30", 8710, Items.DIAMOND_SWORD);
        task("museum", c, "Museum Progression", "Donating items to your Museum", 3646, Items.GOLD_BLOCK);
        task("fairy_souls", c, "Fairy Souls", "+10 for every 5 Fairy Souls exchanged with Tia", 570, Items.GHAST_TEAR);
        task("accessory_bag", c, "Accessory Bag", "+1 per Magical Power in your Accessory Bag", 2184, Items.ENDER_EYE);
        task("pet_score", c, "Pet Score", "+3 per Pet Score (your pets' best rarities)", 1554, Items.BONE);
        task("collections", c, "Collections", "+4 per collection tier", 3172, Items.PAINTING);
        task("craft_minions", c, "Craft Minions", "Each unique minion tier: I-VI +1, VII +2, VIII +3, IX +4, X +6, XI +12, XII +24", 3164,
                Items.COBBLESTONE);
        task("bank_upgrades", c, "Bank Upgrades", "Gold +20, Deluxe +25, Super Deluxe +30, Premier +35, Luxurious +40, Palatial +50", 200,
                Items.GOLD_NUGGET);
        task("fast_travel", c, "Fast Travels Unlocked", "+15 per Travel Scroll used", 360, Items.MAP);

        c = Category.EVENT;
        task("mining_fiesta", c, "Mining Fiesta", "+1 per 5,000 ores mined during a Mining Fiesta", 200, Items.IRON_PICKAXE);
        task("fishing_festival", c, "Fishing Festival", "+1 per 50 Sharks killed during a Fishing Festival", 100, Items.FISHING_ROD);
        task("spooky_festival", c, "Spooky Festival", "Reach a bracket in a Spooky Festival: Wood +10 ... Emerald +75", 225, Items.JACK_O_LANTERN);
        task("chocolate_prestige", c, "Chocolate Factory Prestige", "+25 each time you prestige your Chocolate Factory", 125, Items.COCOA_BEANS);
        task("mythic_rabbits", c, "Mythic Chocolate Rabbits", "+10 for each Mythic rabbit you find", 110, Items.RABBIT_FOOT);
        task("divine_rabbits", c, "Divine Chocolate Rabbits", "+10 for each Divine rabbit you find", 50, Items.RABBIT_HIDE);
        task("jacob_gold", c, "Jacob's Farming Contest", "+10 for each crop you win a gold medal in", 130, Items.WHEAT);
        task("perk_shops", c, "Festival Perk Shops", "Perk tiers bought from Doug in the festivals' perk shops", 196, Items.CHEST);

        c = Category.DUNGEON;
        task("catacombs_level", c, "Catacombs Level Up", "Each Catacombs level: 1-39 +20, 40-50 +40", 1220, Items.WITHER_SKELETON_SKULL);
        task("class_level", c, "Class Level Up", "+4 per class level", 1000, Items.BOW);
        task("catacombs_floors", c, "Complete The Catacombs", "First clear: the Entrance to Floor IV +20, Floors V-VII +30", 190, Items.BONE);
        task("master_floors", c, "Complete Master Mode", "+50 for the first clear of each Master Mode floor", 350, Items.REDSTONE);

        c = Category.ESSENCE;
        task("essence_dragon", c, "Dragon Essence Shop", "Perk tiers: I +2, II +2, III +3, IV +5, V +7, VI-VIII +8, IX +9, X +10", 185,
                Items.DRAGON_BREATH);
        task("essence_undead", c, "Undead Essence Shop", "Perk tiers bought with Undead Essence", 128, Items.ROTTEN_FLESH);
        task("essence_ice", c, "Ice Essence Shop", "Perk tiers bought with Ice Essence", 121, Items.PACKED_ICE);
        task("essence_spider", c, "Spider Essence Shop", "Perk tiers bought with Spider Essence", 126, Items.SPIDER_EYE);
        task("essence_wither", c, "Wither Essence Shop", "Perk tiers bought with Wither Essence", 161, Items.COAL);
        task("essence_crimson", c, "Crimson Essence Shop", "Perk tiers bought with Crimson Essence", 194, Items.NETHER_WART);
        task("essence_gold", c, "Gold Essence Shop", "Perk tiers bought with Gold Essence", 188, Items.GOLD_INGOT);
        task("essence_diamond", c, "Diamond Essence Shop", "Perk tiers bought with Diamond Essence", 226, Items.DIAMOND);
        task("essence_forest", c, "Forest Essence Shop", "Perk tiers bought with Forest Essence", 171, Items.OAK_SAPLING);
        task("essence_fossil", c, "Fossil Essence Shop", "Perk tiers bought with Fossil Essence", 226, Items.BONE_BLOCK);
        task("essence_safari", c, "Safari Essence Shop", "Perk tiers bought with Safari Essence", 414, Items.LEATHER);

        c = Category.SLAYING;
        task("slayer_level_up", c, "Slayer Level Up", "Each slayer level: +15, +25, +35, +50, +70, +100, +125, +150, +150", 3825, Items.ROTTEN_FLESH);
        task("boss_collections", c, "Boss Collections", "Boss collection tiers (Catacombs and Kuudra bosses)", 1015, Items.PLAYER_HEAD);
        task("bestiary", c, "Bestiary Progress", "+1 per Bestiary tier, +10 every 10 milestones", 5635, Items.WRITABLE_BOOK);
        task("mythological", c, "Mythological Creatures", "+1 per 100 Mythological Creatures killed", 100, Items.GOLDEN_SHOVEL);
        task("slay_dragons", c, "Slay Dragons", "The first kill of each dragon: +25, Superior +50", 200, Items.DRAGON_HEAD);
        task("defeat_slayers", c, "Defeat Slayers", "The first kill of each slayer tier: I/II +25, III/IV +50, V +75", 1125, Items.IRON_SWORD);
        task("kuudra", c, "Defeat Kuudra", "The first clear of each tier: Basic +20 ... Infernal +100", 300, Items.MAGMA_CREAM);
        task("arachne", c, "Defeat Arachne", "The first kill of each tier: +20, +40", 60, Items.COBWEB);
        task("mob_types", c, "Collect Mob Types", "+1 per mob type you've killed, +10 for all of them", 35, Items.ZOMBIE_HEAD);

        c = Category.SKILL;
        task("hotm", c, "Heart of the Mountain", "HotM tiers: +35, +45, +60, +75, +90, +110, +130, +180, +210, +240", 1175, Items.PRISMARINE_CRYSTALS);
        task("powder", c, "Powder", "+1 per 2,400 Mithril or 2,500 Gemstone or Glacite Powder (up to 600,000 each)", 1942, Items.GLOWSTONE_DUST);
        task("commission_milestones", c, "Commission Milestones", "Milestones: +20, +30, +30, +50, +50, +75", 255, Items.WRITABLE_BOOK);
        task("nucleus_runs", c, "Crystal Nucleus Runs", "+4 per Crystal Nucleus run (up to 50 runs)", 200, Items.AMETHYST_SHARD);
        task("peak_of_the_mountain", c, "Peak of the Mountain", "Peak of the Mountain tiers: +25 ... +200", 1000, Items.REDSTONE_BLOCK);
        task("fossil_research", c, "Fossil Research", "+10 per unique fossil donated to the Research Center", 80, Items.BONE_BLOCK);
        task("rock_milestones", c, "Rock Milestones", "+20 per Rock pet milestone", 100, Items.COBBLESTONE);
        task("corpse_milestones", c, "Corpse Milestones", "Frozen Corpse milestones: +5 ... +35", 140, Items.BLUE_ICE);
        task("anita_upgrades", c, "Anita's Shop Upgrades", "+10 per upgrade tier (Farming level cap, extra drops)", 250, Items.GOLDEN_CARROT);
        task("garden_level", c, "Garden Level", "+10 per Garden level", 140, Items.MOSS_BLOCK);
        task("garden_visitors", c, "Garden Visitors Served", "+3 per unique visitor milestone", 45, Items.EMERALD);
        task("offers_accepted", c, "Offers Accepted", "+3 per offers accepted milestone", 90, Items.WHEAT_SEEDS);
        task("crop_upgrades", c, "Crop Upgrades", "+1 per Garden crop upgrade", 117, Items.GOLD_INGOT);
        task("crop_milestones", c, "Garden Crop Milestones", "+1 per crop milestone", 598, Items.CARROT);
        task("garden_plots", c, "Garden Unlocked Plots", "+5 per plot unlocked", 120, Items.GRASS_BLOCK);
        task("composter_upgrades", c, "Composter Upgrades", "Tiers 1-7 +1, 8-13 +2, 14-19 +3, 20-25 +4", 305, Items.COMPOSTER);
        task("greenhouse_upgrades", c, "Greenhouse Upgrades", "+5 per Greenhouse upgrade", 100, Items.GLASS);
        task("dna_milestones", c, "DNA Analysis Milestones", "+15 per DNA Analysis milestone", 90, Items.SLIME_BALL);
        task("farming_chips", c, "Farming Chips", "+1 per chip unlocked, +15 per Epic and +25 per Legendary upgrade", 410, Items.REDSTONE_TORCH);
        task("sowdust", c, "Sowdust", "+1 per 1,000,000 Sowdust", 250, Items.SUGAR);
        task("trophy_fish", c, "Trophy Fish", "The first of each trophy fish: Bronze +4, Silver +8, Gold +16, Diamond +32", 1080, Items.TROPICAL_FISH);
        task("trophy_frogs", c, "Trophy Frogs", "The first of each trophy frog: Bronze +3, Silver +6, Gold +12, Diamond +24", 540, Items.SLIME_BALL);
        task("dolphin_milestones", c, "Dolphin Milestones", "+20 per Dolphin pet milestone", 100, Items.HEART_OF_THE_SEA);
        task("ship", c, "Ship Parts and Crew", "+5 per ship part, +10 per crew member", 35, Items.OAK_BOAT);
        task("hotf", c, "Heart of the Forest", "Heart of the Forest tiers", 545, Items.OAK_LEAVES);
        task("cotf", c, "Center of the Forest", "Center of the Forest tiers", 255, Items.JUNGLE_LEAVES);
        task("tree_gifts", c, "Tree Gift Milestones", "+4 per Tree Gift milestone", 84, Items.OAK_LOG);
        task("safari", c, "Safari Milestones", "Safari milestones: +1 to +10", 220, Items.LEAD);

        c = Category.MISC;
        task("bag_upgrades", c, "Accessory Bag Upgrades", "+2 per Accessory Bag upgrade", 198, Items.LEATHER);
        task("timecharms", c, "Timecharms", "+100 per Timecharm secured in the Rift", 800, Items.CLOCK);
        task("powers", c, "Unlocking Powers", "+15 per Accessory Power learned", 330, Items.EMERALD);
        task("relays", c, "Upgraded Relays", "+5 per Relay Tower repaired", 45, Items.END_ROD);
        task("taming_cap", c, "Taming Cap Increases", "+10 per Taming level cap raised at George", 100, Items.LEAD);
        task("hootie", c, "Hootie Upgrades", "Hootie the Owl's slots and coin milestones", 65, Items.FEATHER);
        task("dojo", c, "The Dojo", "Dojo belts: White +20 ... Black +150", 425, Items.BLAZE_ROD);
        task("harp", c, "Harp Songs", "Melody's Harp songs at 50%, 70%, 80% and 90%", 236, Items.NOTE_BLOCK);
        task("abiphone", c, "Abiphone Contacts", "+10 per Abiphone contact", 850, Items.CLOCK);
        task("community_shop", c, "Community Shop", "+10 per Community Shop upgrade", 120, Items.EMERALD_BLOCK);
        task("personal_bank", c, "Personal Bank Upgrades", "Personal Bank II +25, III +35, IV +50", 110, Items.GOLD_BLOCK);
        task("faction_rep", c, "Faction Reputation", "Mage or Barbarian reputation ranks: +5, +10, +20, +40", 150, Items.BLAZE_POWDER);
        task("attributes", c, "Attribute Levels", "+1 per attribute level", 3240, Items.PRISMARINE_SHARD);
        task("galatea", c, "Galatea Progress", "Starlyn Sisters, the Moonglade Beacon, Coral's fish and Carrolyn's crops", 165, Items.MANGROVE_PROPAGULE);

        c = Category.STORY;
        task("rift_guide", c, "Rift Guide", "+3 per Rift Guide objective", 360, Items.DYE.lime());
        task("objectives", c, "Complete Objectives", "+5 per objective completed", 212, Items.PAPER);
        task("chapters", c, "Chapters", "+140 per chapter completed", 560, Items.WRITTEN_BOOK);

        c = Category.CONSUMABLES;
        task("consumables", c, "Consumable Items", "Metaphysical Serum +5, Reaper Pepper +10, McGrubber's Burger +5, Brain Food +5, "
                + "Dwarven O's +2 ... (each up to a few times)", 289, Items.COOKIE);
    }

    /** XP each task has given (by task id; "other" for XP the tasks don't know). */
    static final AttachmentType<Map<String, Integer>> TALLY = AttachmentRegistry.create(SkyRpgSkyblock.id("leveling_tasks"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).initializer(Map::of).copyOnDeath());
    /** Counters for the tasks that pay per so many of something (Mythological Creatures killed, Fiesta ores). */
    private static final AttachmentType<Map<String, Long>> COUNTS = AttachmentRegistry.create(SkyRpgSkyblock.id("leveling_counts"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).initializer(Map::of).copyOnDeath());
    /** One-off rewards already given (first clears, first kills). */
    private static final AttachmentType<List<String>> ONCE = AttachmentRegistry.create(SkyRpgSkyblock.id("leveling_once"),
            builder -> builder.persistent(Codec.STRING.listOf()).initializer(List::of).copyOnDeath());

    private static final String[] SKILLS = {"farming", "mining", "combat", "foraging", "fishing", "enchanting", "alchemy", "carpentry", "taming",
            "social", "runecrafting", "hunting"};

    private LevelingTasks() {
    }

    public static void init() {
        SkyBlockXp.onGiven((player, xp, reason) -> {
            Map<String, Integer> tally = new HashMap<>(player.getAttachedOrElse(TALLY, Map.of()));
            tally.merge(classify(reason), xp, Integer::sum);
            player.setAttached(TALLY, Map.copyOf(tally));
        });
        LevelingMenu.init();
        Emblems.init();
        // tasks that pay for a state catch up when you join (pets levelled or upgraded since, older saves)
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                server.execute(() -> syncAll(handler.player)));
    }

    /** Brings every state task up to date for a player. */
    public static void syncAll(ServerPlayer player) {
        sync(player, "pet_score", 3 * com.skyrpg.skyblock.pet.SkyBlockPets.petScore(player));
        com.skyrpg.skyblock.mining.Hotm.syncLeveling(player);
        com.skyrpg.skyblock.mining.Commissions.syncMilestones(player);
    }

    public static List<Task> tasks() {
        return List.copyOf(TASKS.values());
    }

    public static List<Task> tasks(Category category) {
        return TASKS.values().stream().filter(t -> t.category() == category).toList();
    }

    public static Task task(String id) {
        return TASKS.get(id);
    }

    /** The most XP a category gives. */
    public static int max(Category category) {
        return tasks(category).stream().mapToInt(Task::max).sum();
    }

    /** XP a task has given this player. */
    public static int earned(ServerPlayer player, String task) {
        return player.getAttachedOrElse(TALLY, Map.of()).getOrDefault(task, 0);
    }

    /** XP a category's tasks have given this player (each task counted up to its most). */
    public static int earned(ServerPlayer player, Category category) {
        int sum = 0;
        for (Task task : tasks(category)) {
            sum += Math.min(task.max(), earned(player, task.id()));
        }
        return sum;
    }

    /** Gives SkyBlock XP for a task (filed under it). */
    public static void give(ServerPlayer player, String task, int xp) {
        Task t = TASKS.get(task);
        SkyBlockXp.give(player, xp, t == null ? task : t.name());
    }

    /** Gives a task's XP once ever for {@code key} (a first clear, a first kill); true if it was given now. */
    public static boolean once(ServerPlayer player, String task, String key, int xp) {
        String full = task + ":" + key;
        List<String> done = player.getAttachedOrElse(ONCE, List.of());
        if (done.contains(full)) {
            return false;
        }
        List<String> now = new ArrayList<>(done);
        now.add(full);
        player.setAttached(ONCE, List.copyOf(now));
        give(player, task, xp);
        return true;
    }

    /** Counts {@code amount} more towards a task that pays +1 per {@code per} (up to its most). */
    public static void count(ServerPlayer player, String task, long amount, long per) {
        Map<String, Long> counts = new HashMap<>(player.getAttachedOrElse(COUNTS, Map.of()));
        long now = counts.merge(task, amount, Long::sum);
        player.setAttached(COUNTS, Map.copyOf(counts));
        sync(player, task, (int) Math.min(Integer.MAX_VALUE, now / per));
    }

    /** A task that pays for a state: it's worth {@code worth} XP now; gives what it hasn't paid yet (up to its most). */
    public static void sync(ServerPlayer player, String task, int worth) {
        Task t = TASKS.get(task);
        int capped = t == null ? worth : Math.min(t.max(), worth);
        int owed = capped - earned(player, task);
        if (owed > 0) {
            give(player, task, owed);
        }
    }

    /** SkyBlock XP for each essence perk tier bought (Hypixel's: I +2, II +2, III +3, IV +5, V +7, VI-VIII +8, IX +9, X +10). */
    private static final int[] PERK_TIER_SBXP = {2, 2, 3, 5, 7, 8, 8, 8, 9, 10};

    /** An essence perk bought up to {@code tier} with that essence ("undead", "wither", "crimson"...). */
    public static void essencePerk(ServerPlayer player, String essence, int tier) {
        String task = "essence_" + essence.toLowerCase(java.util.Locale.ROOT);
        if (TASKS.containsKey(task) && tier >= 1) {
            give(player, task, PERK_TIER_SBXP[Math.min(tier, PERK_TIER_SBXP.length) - 1]);
        }
    }

    /** Which task a reason belongs to: a task's own name, or the older sources' reasons. */
    static String classify(String reason) {
        String r = reason.toLowerCase(java.util.Locale.ROOT).trim();
        String exact = BY_NAME.get(r);
        if (exact != null) {
            return exact;
        }
        if (r.equals("catacombs level")) {
            return "catacombs_level";
        }
        if (r.equals("garden level")) {
            return "garden_level";
        }
        if (r.endsWith(" slayer level") || r.startsWith("vampire slayer")) {
            return "slayer_level_up";
        }
        if (r.endsWith(" level")) {
            String skill = r.substring(0, r.length() - " level".length());
            for (String s : SKILLS) {
                if (s.equals(skill)) {
                    return "skill_level_up";
                }
            }
        }
        if (r.equals("collection tier")) {
            return "collections";
        }
        if (r.equals("unique minion")) {
            return "craft_minions";
        }
        if (r.startsWith("bestiary")) {
            return "bestiary";
        }
        if (r.startsWith("dojo")) {
            return "dojo";
        }
        if (r.endsWith("reputation rank")) {
            return "faction_rep";
        }
        if (r.startsWith("kuudra")) {
            return "kuudra";
        }
        if (r.startsWith("fairy soul")) {
            return "fairy_souls";
        }
        if (r.equals("garden plot")) {
            return "garden_plots";
        }
        if (r.equals("new garden visitor")) {
            return "garden_visitors";
        }
        if (r.equals("fossil donation")) {
            return "fossil_research";
        }
        if (r.equals("museum donation")) {
            return "museum";
        }
        if (r.contains("timecharm")) {
            return "timecharms";
        }
        // the Garden's crops: "<crop> upgrade", "<crop> milestone"
        if (r.endsWith(" upgrade")) {
            return "crop_upgrades";
        }
        if (r.endsWith(" milestone")) {
            return "crop_milestones";
        }
        return "other";
    }

    /** Admin/test: what's been given so far (for /sblevel tasks debug). */
    static Map<String, Integer> tally(ServerPlayer player) {
        return player.getAttachedOrElse(TALLY, Map.of());
    }

    /** One-off keys given so far. */
    static Set<String> onceKeys(ServerPlayer player) {
        return new HashSet<>(player.getAttachedOrElse(ONCE, List.of()));
    }
}
