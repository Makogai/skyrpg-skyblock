package com.skyrpg.skyblock.dungeon;

import com.mojang.serialization.Codec;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.essence.Essence;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Malik's essence shops (Hypixel's Undead and Wither Essence perks, costs from NEU's {@code essenceshops.json}). Every
 * perk works in the Catacombs. Undead: +2 Health, +1 Defense, +1 Strength, +2 Intelligence a level, Critical +1 Crit
 * Damage a level. Wither ("Forbidden"): +1% Health, Defense, Strength, Intelligence a level, +1 Speed a level, and
 * blessings 1% stronger a level. Our per-level numbers (Hypixel's weren't retrievable).
 */
final class EssencePerks {
    /** A perk: id, name, essence, its stat (or "" for blessings), the bonus a level (flat or %), and each level's cost. */
    record Perk(String id, String name, String essence, String stat, double perLevel, boolean percent, int[] costs) {
    }

    static final List<Perk> PERKS = List.of(
            new Perk("health", "Health Essence", "UNDEAD", "health", 2, false, new int[] {1000, 2500, 5000, 10000, 25000}),
            new Perk("defense", "Defense Essence", "UNDEAD", "defense", 1, false, new int[] {1000, 4000, 6000, 8000, 10000}),
            new Perk("strength", "Strength Essence", "UNDEAD", "strength", 1, false, new int[] {1000, 4000, 6000, 8000, 10000}),
            new Perk("intelligence", "Intelligence Essence", "UNDEAD", "intelligence", 2, false, new int[] {1000, 4000, 6000, 8000, 10000}),
            new Perk("critical", "Critical Essence", "UNDEAD", "crit_damage", 1, false, new int[] {1000, 3000, 10000, 20000, 50000}),
            new Perk("forbidden_health", "Forbidden Health", "WITHER", "health", 1, true, new int[] {100, 250, 500, 1000, 1500}),
            new Perk("forbidden_defense", "Forbidden Defense", "WITHER", "defense", 1, true, new int[] {100, 250, 500, 1000, 1500}),
            new Perk("forbidden_strength", "Forbidden Strength", "WITHER", "strength", 1, true, new int[] {100, 250, 500, 1000, 1500}),
            new Perk("forbidden_intelligence", "Forbidden Intelligence", "WITHER", "intelligence", 1, true, new int[] {100, 250, 500, 1000, 1500}),
            new Perk("forbidden_speed", "Forbidden Speed", "WITHER", "speed", 1, false, new int[] {100, 250}),
            new Perk("forbidden_blessing", "Forbidden Blessing", "WITHER", "", 1, true,
                    new int[] {200, 400, 600, 800, 1000, 1200, 1400, 1600, 1800, 2000}));

    static final AttachmentType<Map<String, Integer>> LEVELS = AttachmentRegistry.create(SkyRpgSkyblock.id("essence_perks"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    private EssencePerks() {
    }

    static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Essence Perks";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                if (!player.level().dimension().equals(Catacombs.LEVEL)) {
                    return;
                }
                for (Perk perk : PERKS) {
                    int level = level(player, perk.id());
                    StatType stat = perk.stat().isEmpty() ? null : StatTypes.get(perk.stat());
                    if (level <= 0 || stat == null) {
                        continue;
                    }
                    out.add(stat, perk.percent() ? Math.max(0, Stats.get(player, stat)) * perk.perLevel() * level / 100 / (1 + perk.perLevel() * level / 100)
                            : perk.perLevel() * level);
                }
            }
        });
    }

    static int level(ServerPlayer player, String id) {
        return player.getAttachedOrElse(LEVELS, Map.of()).getOrDefault(id, 0);
    }

    /** Blessings' strength from Forbidden Blessing (1 + 1% a level) and Paul's Benediction (+25%). */
    static double blessingBoost(ServerPlayer player) {
        return 1 + level(player, "forbidden_blessing") / 100.0 + (com.skyrpg.core.world.Mayor.active("benediction") ? 0.25 : 0);
    }

    /** Buys the next level of a perk: "" when done, else why not. */
    static String buy(ServerPlayer player, String id) {
        Perk perk = PERKS.stream().filter(p -> p.id().equals(id)).findFirst().orElse(null);
        if (perk == null) {
            return "";
        }
        int level = level(player, id);
        if (level >= perk.costs().length) {
            return perk.name() + " is maxed!";
        }
        int cost = perk.costs()[level];
        if (!Essence.take(player, perk.essence(), cost)) {
            return "You need " + Msg.number(cost) + " " + Essence.name(perk.essence()) + "!";
        }
        Map<String, Integer> levels = new HashMap<>(player.getAttachedOrElse(LEVELS, Map.of()));
        levels.put(id, level + 1);
        player.setAttached(LEVELS, Map.copyOf(levels));
        com.skyrpg.skyblock.leveling.LevelingTasks.essencePerk(player, perk.essence(), level + 1);
        player.sendSystemMessage(Component.literal("You upgraded " + perk.name() + " to level " + (level + 1) + "!").withStyle(ChatFormatting.GREEN));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.4f);
        Stats.refresh(player);
        return "";
    }

    /** One line about a perk for the shop: "Health Essence 2/5 · +4 Health · next: 5,000 Undead Essence". */
    static String describe(ServerPlayer player, Perk perk) {
        int level = level(player, perk.id());
        String effect = perk.stat().isEmpty() ? "+" + (int) (perk.perLevel() * level) + "% blessings"
                : "+" + (int) (perk.perLevel() * level) + (perk.percent() ? "% " : " ") + perk.stat().replace("_", " ");
        String next = level >= perk.costs().length ? "maxed" : "next " + Msg.number(perk.costs()[level]) + " " + Essence.name(perk.essence());
        return perk.name() + " " + level + "/" + perk.costs().length + " · " + effect + " · " + next;
    }
}
