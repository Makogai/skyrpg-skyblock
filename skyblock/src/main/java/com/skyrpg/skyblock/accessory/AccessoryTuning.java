package com.skyrpg.skyblock.accessory;

import com.mojang.serialization.Codec;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hypixel's Stats Tuning (Maxwell): every 10 Magical Power is a Tuning Point, spent one at a time on one of eight stats
 * (Health +5, Defense +1, Speed +1.5, Strength +1, Crit Damage +1, Crit Chance +0.2, Attack Speed +0.3, Intelligence
 * +2 a point) and taken back out again for free. The points are on the player, so they go with the profile. One
 * template (Hypixel's first, which is free) saves a spread and loads it back.
 */
public final class AccessoryTuning {
    /** One tunable stat and what a point gives. */
    public record Stat(String id, StatType type, double perPoint) {
    }

    public static final List<Stat> STATS = List.of(
            new Stat("health", StatTypes.HEALTH, 5),
            new Stat("defense", StatTypes.DEFENSE, 1),
            new Stat("speed", StatTypes.SPEED, 1.5),
            new Stat("strength", StatTypes.STRENGTH, 1),
            new Stat("crit_damage", StatTypes.CRIT_DAMAGE, 1),
            new Stat("crit_chance", StatTypes.CRIT_CHANCE, 0.2),
            new Stat("attack_speed", StatTypes.ATTACK_SPEED, 0.3),
            new Stat("intelligence", StatTypes.INTELLIGENCE, 2));
    /** Magical Power for one Tuning Point. */
    public static final int POWER_PER_POINT = 10;

    static final AttachmentType<Map<String, Integer>> POINTS = AttachmentRegistry.create(SkyRpgSkyblock.id("accessory_tuning"),
            builder -> builder.initializer(Map::of).persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());
    static final AttachmentType<Map<String, Integer>> TEMPLATE = AttachmentRegistry.create(SkyRpgSkyblock.id("accessory_tuning_template"),
            builder -> builder.initializer(Map::of).persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    private AccessoryTuning() {
    }

    static void init() {
        POINTS.identifier();
        TEMPLATE.identifier();
    }

    /** Tuning Points a Magical Power gives. */
    public static int total(int magicalPower) {
        return Math.max(0, magicalPower) / POWER_PER_POINT;
    }

    /** The points on each stat, cut back to what the Magical Power pays for (it can drop when an accessory leaves). */
    public static Map<String, Integer> points(ServerPlayer player, int magicalPower) {
        Map<String, Integer> stored = player.getAttachedOrElse(POINTS, Map.of());
        Map<String, Integer> out = new LinkedHashMap<>();
        int left = total(magicalPower);
        for (Stat stat : STATS) {
            int points = Math.min(left, Math.max(0, stored.getOrDefault(stat.id(), 0)));
            out.put(stat.id(), points);
            left -= points;
        }
        return out;
    }

    public static int spent(Map<String, Integer> points) {
        return points.values().stream().mapToInt(Integer::intValue).sum();
    }

    /** What the tuning adds to the player's stats. */
    public static Map<StatType, Double> stats(ServerPlayer player, int magicalPower) {
        Map<StatType, Double> out = new LinkedHashMap<>();
        Map<String, Integer> points = points(player, magicalPower);
        for (Stat stat : STATS) {
            int count = points.getOrDefault(stat.id(), 0);
            if (count > 0) {
                out.put(stat.type(), count * stat.perPoint());
            }
        }
        return out;
    }

    /** Puts up to {@code count} points on a stat (what's left unspent is the limit); returns how many went in. */
    static int add(ServerPlayer player, String id, int count) {
        Map<String, Integer> points = points(player, AccessoryBag.magicalPower(player));
        int free = total(AccessoryBag.magicalPower(player)) - spent(points);
        int added = Math.min(count, free);
        if (added <= 0 || STATS.stream().noneMatch(stat -> stat.id().equals(id))) {
            return 0;
        }
        points.merge(id, added, Integer::sum);
        player.setAttached(POINTS, Map.copyOf(points));
        return added;
    }

    /** Takes up to {@code count} points off a stat; returns how many came off. */
    static int remove(ServerPlayer player, String id, int count) {
        Map<String, Integer> points = points(player, AccessoryBag.magicalPower(player));
        int removed = Math.min(count, points.getOrDefault(id, 0));
        if (removed <= 0) {
            return 0;
        }
        points.merge(id, -removed, Integer::sum);
        player.setAttached(POINTS, Map.copyOf(points));
        return removed;
    }

    static void reset(ServerPlayer player) {
        player.setAttached(POINTS, Map.of());
    }

    static void saveTemplate(ServerPlayer player) {
        player.setAttached(TEMPLATE, Map.copyOf(points(player, AccessoryBag.magicalPower(player))));
    }

    /** Loads the template, as many of its points as the Magical Power pays for. Returns false when none is saved. */
    static boolean loadTemplate(ServerPlayer player) {
        Map<String, Integer> template = player.getAttachedOrElse(TEMPLATE, Map.of());
        if (template.isEmpty()) {
            return false;
        }
        player.setAttached(POINTS, Map.copyOf(template));
        return true;
    }

    static boolean hasTemplate(ServerPlayer player) {
        return spent(player.getAttachedOrElse(TEMPLATE, Map.of())) > 0;
    }
}
