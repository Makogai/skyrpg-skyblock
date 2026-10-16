package com.skyrpg.combat.slayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * One slayer line, from {@code data/<ns>/skyrpg/slayers/<id>.json}:
 * <pre>
 * {"name": "Grave Tyrant", "color": "#7FBF5F", "icon": "minecraft:rotten_flesh", "boss": "skyrpg_combat:grave_tyrant",
 *  "kind": "grave_tyrant", "counts": ["#minecraft:zombies", "skyrpg_combat:tide_lurker"], "counts_text": "zombies",
 *  "order": 1,
 *  "token": "skyrpg_combat:tyrants_ash", "rare": "skyrpg_combat:plague_heart", "level_stat": {"health": 3},
 *  "levels": [5, 15, 50, ...],
 *  "tiers": [{"fee": 1000, "xp": 100, "health": 600, "damage": 1.0, "level": 10, "minutes": 3, "slayer_xp": 5,
 *             "requires": 0, "tokens": [1, 2], "rare_chance": 0, "egg_chance": 0}, ...]}
 * </pre>
 * {@code kind} picks the boss's tricks (see {@link SlayerBosses}); {@code counts} are the mobs whose kills fill the quest
 * bar ({@code #} marks a tag).
 *
 * <p>Hypixel's slayers ({@code tools/skyblock/import_slayers.py}) write {@code counts} as each mob's Combat XP, the bar
 * filling with the Combat XP of what you kill ({@code {"minecraft:zombie": 6}}); list {@code drops} (SkyBlock items, the
 * amount and chance per tier, the slayer level each needs) and {@code level_rewards} (each slayer level's stats, SkyBlock
 * XP and unlocks); and give tiers a Combat level and an earlier boss to beat first ({@code combat_level},
 * {@code requires_boss}) and the boss's own Combat XP ({@code combat_xp}).
 */
public record SlayerLine(String id, String name, int color, Item icon, Identifier boss, String kind, List<Identifier> countTypes,
                         List<TagKey<EntityType<?>>> countTags, String countsText, Item token, Item rare, Map<String, Double> levelStat,
                         List<Integer> levels, List<Tier> tiers, Map<Identifier, Double> countXp, List<Drop> drops, List<LevelReward> levelRewards,
                         String key) {
    public static final List<Integer> DEFAULT_LEVELS = List.of(5, 15, 50, 120, 300, 700, 1500, 3000, 6000);

    /** One tier of the line's quest. */
    public record Tier(long fee, int xp, double health, double damage, int level, int minutes, int slayerXp, int requires,
                       int tokensMin, int tokensMax, double rareChance, double eggChance, int combatLevel, double combatXp,
                       String requiresLine, int requiresTier) {
    }

    /**
     * A boss drop: a SkyBlock item, the slayer level it needs, Hypixel's odds label ("Guaranteed" to "RNGesus
     * Incarnate", or empty), and for each tier {min, max, chance}.
     */
    public record Drop(String item, int level, String odds, Map<Integer, double[]> tiers) {
        /** The odds label, from the data or else guessed from the chance. */
        public String oddsAt(double chance) {
            if (!odds.isEmpty()) {
                return odds;
            }
            return chance >= 1 ? "Guaranteed" : chance >= 0.1 ? "Occasional" : chance >= 0.02 ? "Rare" : chance >= 0.003 ? "Extraordinary"
                    : chance > 0.0003 ? "Pray RNGesus" : "RNGesus Incarnate";
        }
    }

    /** What reaching a slayer level gives. */
    public record LevelReward(Map<String, Double> stats, int sbxp, List<String> unlocks) {
    }

    /** Hypixel's slayers: drops and level rewards come from the data rather than a token and a rare. */
    public boolean hypixel() {
        return !drops.isEmpty() || !levelRewards.isEmpty();
    }

    /** What a kill adds to the quest bar: the mob's Combat XP for Hypixel's slayers, or -1 to use the kill count. */
    public double questXp(Entity entity) {
        Double xp = countXp.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        if (xp == null) {
            return -1;
        }
        // A named mob's own Combat XP (a Crypt Ghoul gives 36, not a zombie's 6).
        double own = com.skyrpg.core.world.MobTags.combatXp(entity);
        return own >= 0 ? own : xp;
    }

    /** Every stat the levels up to {@code level} give together. */
    public Map<String, Double> statsAt(int level) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (levelRewards.isEmpty()) {
            levelStat.forEach((stat, per) -> out.put(stat, per * level));
            return out;
        }
        for (int i = 0; i < Math.min(level, levelRewards.size()); i++) {
            levelRewards.get(i).stats().forEach((stat, value) -> out.merge(stat, value, Double::sum));
        }
        return out;
    }

    public LevelReward levelReward(int level) {
        return level >= 1 && level <= levelRewards.size() ? levelRewards.get(level - 1) : null;
    }

    /** Whether killing this entity fills this line's quest bar. */
    public boolean counts(Entity entity) {
        if (countTypes.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) {
            return true;
        }
        for (TagKey<EntityType<?>> tag : countTags) {
            if (entity.typeHolder().is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** The slayer level for this much XP (0 to the number of thresholds). */
    public int level(double xp) {
        int level = 0;
        for (int threshold : levels) {
            if (xp >= threshold) {
                level++;
            }
        }
        return level;
    }

    /** XP needed for the next level, or -1 at the top. */
    public int nextLevelXp(double xp) {
        for (int threshold : levels) {
            if (xp < threshold) {
                return threshold;
            }
        }
        return -1;
    }

    public Tier tier(int tier) {
        return tier >= 1 && tier <= tiers.size() ? tiers.get(tier - 1) : null;
    }

    static SlayerLine parse(Identifier id, JsonObject json) {
        List<Identifier> types = new ArrayList<>();
        List<TagKey<EntityType<?>>> tags = new ArrayList<>();
        Map<Identifier, Double> countXp = new LinkedHashMap<>();
        if (json.get("counts").isJsonObject()) {
            json.getAsJsonObject("counts").entrySet().forEach(e -> {
                types.add(Identifier.parse(e.getKey()));
                countXp.put(Identifier.parse(e.getKey()), e.getValue().getAsDouble());
            });
        } else {
            for (JsonElement element : json.getAsJsonArray("counts")) {
                String entry = element.getAsString();
                if (entry.startsWith("#")) {
                    tags.add(TagKey.create(Registries.ENTITY_TYPE, Identifier.parse(entry.substring(1))));
                } else {
                    types.add(Identifier.parse(entry));
                }
            }
        }
        List<Drop> drops = new ArrayList<>();
        if (json.has("drops")) {
            for (JsonElement element : json.getAsJsonArray("drops")) {
                JsonObject d = element.getAsJsonObject();
                Map<Integer, double[]> perTier = new LinkedHashMap<>();
                d.getAsJsonObject("tiers").entrySet().forEach(e -> {
                    JsonArray a = e.getValue().getAsJsonArray();
                    perTier.put(Integer.parseInt(e.getKey()), new double[] {a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()});
                });
                drops.add(new Drop(d.get("item").getAsString(), d.has("level") ? d.get("level").getAsInt() : 0,
                        d.has("odds") ? d.get("odds").getAsString() : "", Map.copyOf(perTier)));
            }
        }
        List<LevelReward> levelRewards = new ArrayList<>();
        if (json.has("level_rewards")) {
            for (JsonElement element : json.getAsJsonArray("level_rewards")) {
                JsonObject r = element.getAsJsonObject();
                Map<String, Double> stats = new LinkedHashMap<>();
                r.getAsJsonObject("stats").entrySet().forEach(e -> stats.put(e.getKey(), e.getValue().getAsDouble()));
                List<String> unlocks = new ArrayList<>();
                if (r.has("unlocks")) {
                    r.getAsJsonArray("unlocks").forEach(u -> unlocks.add(u.getAsString()));
                }
                levelRewards.add(new LevelReward(Map.copyOf(stats), r.has("sbxp") ? r.get("sbxp").getAsInt() : 0, List.copyOf(unlocks)));
            }
        }
        Map<String, Double> levelStat = new LinkedHashMap<>();
        if (json.has("level_stat")) {
            json.getAsJsonObject("level_stat").entrySet().forEach(e -> levelStat.put(e.getKey(), e.getValue().getAsDouble()));
        }
        List<Integer> levels = new ArrayList<>();
        if (json.has("levels")) {
            json.getAsJsonArray("levels").forEach(e -> levels.add(e.getAsInt()));
        }
        List<Tier> tiers = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("tiers")) {
            JsonObject t = element.getAsJsonObject();
            JsonArray tokens = t.has("tokens") ? t.getAsJsonArray("tokens") : null;
            tiers.add(new Tier(t.get("fee").getAsLong(), t.get("xp").getAsInt(), t.get("health").getAsDouble(),
                    t.has("damage") ? t.get("damage").getAsDouble() : 1, t.has("level") ? t.get("level").getAsInt() : 10,
                    t.has("minutes") ? t.get("minutes").getAsInt() : 3, t.get("slayer_xp").getAsInt(),
                    t.has("requires") ? t.get("requires").getAsInt() : 0,
                    tokens == null ? 0 : tokens.get(0).getAsInt(), tokens == null ? 0 : tokens.get(tokens.size() - 1).getAsInt(),
                    t.has("rare_chance") ? t.get("rare_chance").getAsDouble() : 0, t.has("egg_chance") ? t.get("egg_chance").getAsDouble() : 0,
                    t.has("combat_level") ? t.get("combat_level").getAsInt() : 0, t.has("combat_xp") ? t.get("combat_xp").getAsDouble() : 0,
                    t.has("requires_boss") ? t.getAsJsonObject("requires_boss").get("line").getAsString() : "",
                    t.has("requires_boss") ? t.getAsJsonObject("requires_boss").get("tier").getAsInt() : 0));
        }
        return new SlayerLine(id.getPath(), json.get("name").getAsString(),
                json.has("color") ? TextColor.parseColor(json.get("color").getAsString()).getOrThrow().getValue() | 0xFF000000 : 0xFFE5534B,
                item(json, "icon", Items.ROTTEN_FLESH), Identifier.parse(json.get("boss").getAsString()),
                json.has("kind") ? json.get("kind").getAsString() : id.getPath(), List.copyOf(types), List.copyOf(tags),
                json.has("counts_text") ? json.get("counts_text").getAsString() : "its kind of mob",
                item(json, "token", Items.AIR), item(json, "rare", Items.AIR), Map.copyOf(levelStat),
                levels.isEmpty() ? DEFAULT_LEVELS : List.copyOf(levels), List.copyOf(tiers), Map.copyOf(countXp), List.copyOf(drops),
                List.copyOf(levelRewards), json.has("key") ? json.get("key").getAsString() : id.getPath());
    }

    private static Item item(JsonObject json, String key, Item fallback) {
        return json.has(key) ? BuiltInRegistries.ITEM.getOptional(Identifier.parse(json.get(key).getAsString())).orElse(fallback) : fallback;
    }
}
