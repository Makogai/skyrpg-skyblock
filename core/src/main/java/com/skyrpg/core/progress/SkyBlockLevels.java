package com.skyrpg.core.progress;

import java.util.List;
import net.minecraft.ChatFormatting;

/**
 * What Hypixel's SkyBlock Levels give, shared by the server (rewards, stats) and the client (the SkyBlock Leveling
 * page): +5 Health every level, +1 Strength every 5 levels, a new prefix colour every 40 levels, and features that
 * open up at set levels.
 */
public final class SkyBlockLevels {
    public static final int HEALTH_PER_LEVEL = 5;
    public static final int LEVELS_PER_STRENGTH = 5;

    /** A feature a level opens up. */
    public record Unlock(int level, String name, ChatFormatting color) {
    }

    public static final List<Unlock> UNLOCKS = List.of(
            new Unlock(3, "Access to the Community Shop", ChatFormatting.AQUA),
            new Unlock(5, "Access to the Garden", ChatFormatting.DARK_GREEN),
            new Unlock(5, "Access to the Wardrobe", ChatFormatting.GREEN),
            new Unlock(6, "Auto-pickup of block and mob drops", ChatFormatting.GREEN),
            new Unlock(7, "Access to the Bazaar", ChatFormatting.GOLD),
            new Unlock(12, "Access to the Wizard Portal", ChatFormatting.LIGHT_PURPLE),
            new Unlock(50, "Daily coins trading limit of 1B", ChatFormatting.GREEN),
            new Unlock(100, "Daily coins trading limit of 10B", ChatFormatting.GREEN));

    /** The prefix colour for each 40 levels, as on Hypixel: grey, then white at 40 ... dark red at 480. */
    private static final ChatFormatting[] PREFIX = {
            ChatFormatting.GRAY, ChatFormatting.WHITE, ChatFormatting.YELLOW, ChatFormatting.GREEN, ChatFormatting.DARK_GREEN,
            ChatFormatting.AQUA, ChatFormatting.DARK_AQUA, ChatFormatting.BLUE, ChatFormatting.LIGHT_PURPLE, ChatFormatting.DARK_PURPLE,
            ChatFormatting.GOLD, ChatFormatting.RED, ChatFormatting.DARK_RED};
    private static final String[] PREFIX_NAMES = {"Grey", "White", "Yellow", "Green", "Dark Green", "Aqua", "Cyan", "Blue", "Pink",
            "Purple", "Gold", "Red", "Dark Red"};

    /** The Wardrobe opens at this level. */
    public static final int WARDROBE = 5;

    private SkyBlockLevels() {
    }

    public static ChatFormatting prefixColor(int level) {
        return PREFIX[Math.min(PREFIX.length - 1, Math.max(0, level) / 40)];
    }

    public static String prefixName(int level) {
        return PREFIX_NAMES[Math.min(PREFIX_NAMES.length - 1, Math.max(0, level) / 40)];
    }

    /** Health from SkyBlock Levels at this level. */
    public static int health(int level) {
        return HEALTH_PER_LEVEL * level;
    }

    /** Strength from SkyBlock Levels at this level. */
    public static int strength(int level) {
        return level / LEVELS_PER_STRENGTH;
    }
}
