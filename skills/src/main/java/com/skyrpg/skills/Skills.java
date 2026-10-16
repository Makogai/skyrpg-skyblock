package com.skyrpg.skills;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

/** All loaded skill definitions, in display order. See {@link Skill} for the file format. */
public final class Skills {
    public static final String DIRECTORY = "skyrpg/skills";

    private static Map<Identifier, Skill> skills = Map.of();

    private Skills() {
    }

    public static Iterable<Skill> all() {
        return skills.values();
    }

    public static Skill get(Identifier id) {
        return skills.get(id);
    }

    /** Matches "skyrpg_skills:farming" exactly, or just "farming" by name. */
    public static Skill find(String nameOrId) {
        Identifier id = Identifier.tryParse(nameOrId);
        if (id != null && skills.containsKey(id)) {
            return skills.get(id);
        }
        return skills.values().stream().filter(skill -> skill.id().getPath().equals(nameOrId)).findFirst().orElse(null);
    }

    static void load(Map<Identifier, JsonElement> files) {
        List<Skill> loaded = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                JsonObject object = json.getAsJsonObject();
                String name = object.get("name").getAsString();
                TextColor color = object.has("color")
                        ? TextColor.parseColor(object.get("color").getAsString()).getOrThrow()
                        : TextColor.parseColor("white").getOrThrow();
                int order = object.has("order") ? object.get("order").getAsInt() : 0;
                List<Long> levels = Skill.DEFAULT_LEVELS;
                if (object.has("levels")) {
                    levels = new ArrayList<>();
                    for (JsonElement level : object.getAsJsonArray("levels")) {
                        levels.add(level.getAsLong());
                    }
                }
                Identifier icon = Identifier.parse(object.has("icon") ? object.get("icon").getAsString() : "minecraft:book");
                String description = object.has("description") ? object.get("description").getAsString() : "";
                Map<String, Double> rewards = new LinkedHashMap<>();
                if (object.has("rewards")) {
                    object.getAsJsonObject("rewards").entrySet().forEach(entry -> rewards.put(entry.getKey(), entry.getValue().getAsDouble()));
                }
                List<Skill.LevelReward> levelRewards = new ArrayList<>();
                if (object.has("level_rewards")) {
                    for (JsonElement element : object.getAsJsonArray("level_rewards")) {
                        JsonObject reward = element.getAsJsonObject();
                        Map<String, Double> stats = new LinkedHashMap<>();
                        if (reward.has("stats")) {
                            reward.getAsJsonObject("stats").entrySet().forEach(entry -> stats.put(entry.getKey(), entry.getValue().getAsDouble()));
                        }
                        levelRewards.add(new Skill.LevelReward(Map.copyOf(stats), reward.has("coins") ? reward.get("coins").getAsLong() : 0,
                                reward.has("sbxp") ? reward.get("sbxp").getAsInt() : 0));
                    }
                }
                loaded.add(new Skill(id, name, color, order, levels, icon, description, rewards,
                        object.has("perk") ? object.get("perk").getAsString() : "",
                        object.has("perk_text") ? object.get("perk_text").getAsString() : "", levelRewards));
            } catch (RuntimeException e) {
                SkyRpgSkills.LOGGER.error("Skipping invalid skill {}: {}", id, e.getMessage());
            }
        });
        loaded.sort(Comparator.comparingInt(Skill::order).thenComparing(skill -> skill.id().toString()));
        Map<Identifier, Skill> byId = new LinkedHashMap<>();
        loaded.forEach(skill -> byId.put(skill.id(), skill));
        skills = byId;
        SkyRpgSkills.LOGGER.info("Loaded {} skills", skills.size());
    }
}
