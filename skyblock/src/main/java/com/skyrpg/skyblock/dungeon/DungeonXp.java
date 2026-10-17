package com.skyrpg.skyblock.dungeon;

import com.mojang.serialization.Codec;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * The Catacombs level and the five class levels, on Hypixel's table (NEU's {@code leveling.json}, levels 1-50; the classes
 * use the same one). Kept here rather than as skills so they stay out of the Skill Average, as on Hypixel.
 */
public final class DungeonXp {
    public static final String CATACOMBS = "catacombs";
    public static final List<String> CLASSES = List.of("healer", "mage", "berserk", "archer", "tank");
    private static final long[] TABLE = {50, 75, 110, 160, 230, 330, 470, 670, 950, 1340, 1890, 2665, 3760, 5260, 7380, 10300, 14400, 20000, 27600,
            38000, 52500, 71500, 97000, 132000, 180000, 243000, 328000, 445000, 600000, 800000, 1065000, 1410000, 1900000, 2500000, 3300000,
            4300000, 5600000, 7200000, 9200000, 12000000, 15000000, 19000000, 24000000, 30000000, 38000000, 48000000, 60000000, 75000000,
            93000000, 116250000};

    /** XP in the Catacombs and each class. */
    static final AttachmentType<Map<String, Double>> XP = AttachmentRegistry.create(SkyRpgSkyblock.id("dungeon_xp"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)).copyOnDeath());
    /** The class picked for the next run. */
    static final AttachmentType<String> SELECTED = AttachmentRegistry.create(SkyRpgSkyblock.id("dungeon_class"),
            builder -> builder.persistent(Codec.STRING).copyOnDeath());

    private DungeonXp() {
    }

    /** Registers the attachments at startup (before any player's saved data is read). */
    static void init() {
    }

    public static double xp(ServerPlayer player, String key) {
        return player.getAttachedOrElse(XP, Map.of()).getOrDefault(key, 0.0);
    }

    public static int level(ServerPlayer player, String key) {
        return levelFor(xp(player, key));
    }

    public static int levelFor(double xp) {
        int level = 0;
        double left = xp;
        while (level < TABLE.length && left >= TABLE[level]) {
            left -= TABLE[level];
            level++;
        }
        return level;
    }

    public static String selected(ServerPlayer player) {
        return player.getAttachedOrElse(SELECTED, "berserk");
    }

    public static void select(ServerPlayer player, String dungeonClass) {
        if (CLASSES.contains(dungeonClass)) {
            player.setAttached(SELECTED, dungeonClass);
        }
    }

    public static String title(String key) {
        return key.substring(0, 1).toUpperCase(Locale.ROOT) + key.substring(1);
    }

    /** Gives XP; a level-up is announced (the Catacombs' with its SkyBlock XP: 20 a level, 40 from level 40). */
    public static void give(ServerPlayer player, String key, double amount) {
        give(player, key, amount, false);
    }

    /** Gives XP; {@code quiet}: no level-up message (the classes you didn't play get a quarter, silently). */
    public static void give(ServerPlayer player, String key, double amount, boolean quiet) {
        Map<String, Double> all = new HashMap<>(player.getAttachedOrElse(XP, Map.of()));
        double before = all.getOrDefault(key, 0.0);
        all.put(key, before + amount);
        player.setAttached(XP, Map.copyOf(all));
        int from = levelFor(before);
        int to = levelFor(before + amount);
        for (int level = from + 1; level <= to && !quiet; level++) {
            String name = key.equals(CATACOMBS) ? "The Catacombs" : title(key);
            Notice.Panel panel = Notice.of(Notice.Kind.DUNGEON).tag("LEVEL UP").panel();
            if (key.equals(CATACOMBS)) {
                int sbxp = level >= 40 ? 40 : 20;
                SkyBlockXp.give(player, sbxp, "Catacombs level");
                panel.line(Component.literal(" +" + sbxp + " SkyBlock XP").withStyle(ChatFormatting.AQUA));
            }
            panel.send(player, Component.literal(name + " " + (level - 1) + " ➜ " + level).withStyle(ChatFormatting.DARK_PURPLE));
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }
}
