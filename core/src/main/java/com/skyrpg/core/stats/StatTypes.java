package com.skyrpg.core.stats;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every stat in the game, in display order. Ids are what item, skill and set data files use. Symbols are
 * SkyRPG's own 8x8 icons (private-use characters drawn by the font in core's assets); code draws them in
 * the stat's colour.
 */
public final class StatTypes {
    private static final Map<String, StatType> TYPES = new LinkedHashMap<>();

    public static final StatType HEALTH = add("health", "Health", "\uE100", 0xFFFF5555, 100, false,
            "Your maximum health. 100 health is 10 hearts.");
    public static final StatType DEFENSE = add("defense", "Defense", "\uE101", 0xFF55FF55, 0, false,
            "Reduces damage you take. 100 defense blocks half of it.");
    public static final StatType STRENGTH = add("strength", "Strength", "\uE102", 0xFFE5534B, 0, false,
            "Each point adds 1% to your melee damage.");
    public static final StatType CRIT_CHANCE = add("crit_chance", "Crit Chance", "\uE103", 0xFF5C8DFF, 30, true,
            "Chance for a hit to be a critical hit.");
    public static final StatType CRIT_DAMAGE = add("crit_damage", "Crit Damage", "\uE104", 0xFF5C8DFF, 50, true,
            "Extra damage dealt by critical hits.");
    public static final StatType SPEED = add("speed", "Speed", "\uE105", 0xFFFFFFFF, 100, false,
            "How fast you walk. 100 is normal.");
    public static final StatType INTELLIGENCE = add("intelligence", "Intelligence", "\uE106", 0xFF55FFFF, 0, false,
            "Each point adds 1 to your mana (100 to start). Abilities cost mana.");
    public static final StatType MINING_SPEED = add("mining_speed", "Mining Speed", "\uE107", 0xFFFFAA00, 0, false,
            "How fast you break blocks. 100 makes it twice as fast.");
    public static final StatType MINING_FORTUNE = add("mining_fortune", "Mining Fortune", "\uE108", 0xFFFFAA00, 0, false,
            "Extra drops from ores. Every 100 is one more drop.");
    public static final StatType FARMING_FORTUNE = add("farming_fortune", "Farming Fortune", "\uE109", 0xFFFFAA00, 0, false,
            "Extra drops from crops. Every 100 is one more drop.");
    public static final StatType FORAGING_FORTUNE = add("foraging_fortune", "Foraging Fortune", "\uE10A", 0xFFFFAA00, 0, false,
            "Extra drops from logs. Every 100 is one more drop.");
    public static final StatType MAGIC_FIND = add("magic_find", "Magic Find", "\uE10B", 0xFF55FFFF, 0, false,
            "Better odds for rare drops from mobs and bosses.");
    public static final StatType FARMING_WISDOM = add("farming_wisdom", "Farming Wisdom", "\uE10C", 0xFF3FD0D4, 0, false,
            "Each point is 1% more Farming XP.");
    public static final StatType MINING_WISDOM = add("mining_wisdom", "Mining Wisdom", "\uE10D", 0xFF3FD0D4, 0, false,
            "Each point is 1% more Mining XP.");
    public static final StatType FORAGING_WISDOM = add("foraging_wisdom", "Foraging Wisdom", "\uE10E", 0xFF3FD0D4, 0, false,
            "Each point is 1% more Foraging XP.");
    public static final StatType COMBAT_WISDOM = add("combat_wisdom", "Combat Wisdom", "\uE10F", 0xFF3FD0D4, 0, false,
            "Each point is 1% more Combat XP.");
    public static final StatType FISHING_SPEED = add("fishing_speed", "Fishing Speed", "\uE114", 0xFF55FFFF, 0, false,
            "Fish bite sooner. Every 25 is one more level of Lure, up to four.");
    public static final StatType SEA_CREATURE_CHANCE = add("sea_creature_chance", "Sea Creature Chance", "\uE115", 0xFF3FA9F5, 5, true,
            "The chance that your line pulls up a sea creature instead of a fish.");
    public static final StatType FISHING_FORTUNE = add("fishing_fortune", "Fishing Fortune", "\uE116", 0xFFFFAA00, 0, false,
            "More Tidescales from your catches. Every 100 is one more.");
    public static final StatType FISHING_WISDOM = add("fishing_wisdom", "Fishing Wisdom", "\uE117", 0xFF3FD0D4, 0, false,
            "Each point is 1% more Fishing XP.");

    // Hypixel SkyBlock's other stats (symbols are Hypixel's). Some only take effect once their system lands.
    public static final StatType ATTACK_SPEED = add("attack_speed", "Bonus Attack Speed", "\u2694", 0xFFFFFF55, 0, true,
            "Makes you swing faster (up to 100%).");
    public static final StatType FEROCITY = add("ferocity", "Ferocity", "\u2AFD", 0xFFFF5555, 0, false,
            "A chance to hit again: every 100 is one extra strike.");
    public static final StatType ABILITY_DAMAGE = add("ability_damage", "Ability Damage", "\u0E51", 0xFFFF5555, 0, true,
            "Extra damage from item abilities.");
    public static final StatType TRUE_DEFENSE = add("true_defense", "True Defense", "\u2742", 0xFFFFFFFF, 0, false,
            "Reduces true damage, which ignores Defense.");
    public static final StatType PET_LUCK = add("pet_luck", "Pet Luck", "\u2663", 0xFFFF55FF, 0, false,
            "Better odds for rare pets and pet items.");
    public static final StatType HEALTH_REGEN = add("health_regen", "Health Regen", "\u2763", 0xFFFF5555, 100, false,
            "How fast your health comes back.");
    public static final StatType VITALITY = add("vitality", "Vitality", "\u2668", 0xFFAA0000, 100, false,
            "Healing you receive.");
    public static final StatType MENDING = add("mending", "Mending", "\u2604", 0xFF55FF55, 100, false,
            "Healing you give to others.");
    public static final StatType SWING_RANGE = add("swing_range", "Swing Range", "\u24C8", 0xFFFFFF55, 3, false,
            "How far your melee hits reach.");
    public static final StatType SWEEP = add("sweep", "Sweep", "\u222E", 0xFF55AA55, 0, false,
            "Hits more of the mobs in front of you.");
    public static final StatType FEAR = add("fear", "Fear", "\u2620", 0xFFAA00AA, 0, false,
            "Used during the Great Spook.");
    public static final StatType BREAKING_POWER = add("breaking_power", "Breaking Power", "\u24C5", 0xFF00AA00, 0, false,
            "Which blocks a tool can break.");
    public static final StatType PRISTINE = add("pristine", "Pristine", "\u2727", 0xFFAA00AA, 0, false,
            "A chance for gemstones to come out flawed instead of rough.");
    public static final StatType MINING_SPREAD = add("mining_spread", "Mining Spread", "\u259A", 0xFFFFFF55, 0, false,
            "A chance to break nearby blocks too.");
    public static final StatType ORE_FORTUNE = add("ore_fortune", "Ore Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra drops from ores.");
    public static final StatType BLOCK_FORTUNE = add("block_fortune", "Block Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra drops from stone and other blocks.");
    public static final StatType DWARVEN_METAL_FORTUNE = add("dwarven_metal_fortune", "Dwarven Metal Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Mithril and Titanium.");
    public static final StatType GEMSTONE_FORTUNE = add("gemstone_fortune", "Gemstone Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra gemstones.");
    public static final StatType HEAT_RESISTANCE = add("heat_resistance", "Heat Resistance", "\u2668", 0xFFFF5555, 0, false,
            "Protects you from Crystal Hollows heat.");
    public static final StatType COLD_RESISTANCE = add("cold_resistance", "Cold Resistance", "\u2744", 0xFF55FFFF, 0, false,
            "Protects you from Glacite Tunnels cold.");
    public static final StatType PRESSURE_RESISTANCE = add("pressure_resistance", "Pressure Resistance", "\u274D", 0xFF5555FF, 0, false,
            "Protects you from deep-water pressure.");
    public static final StatType RESPIRATION = add("respiration", "Respiration", "\u26B6", 0xFF5555FF, 0, false,
            "Lets you hold your breath longer.");
    public static final StatType DOUBLE_HOOK_CHANCE = add("double_hook_chance", "Double Hook Chance", "\u2693", 0xFF5555FF, 0, true,
            "A chance to hook two sea creatures at once.");
    public static final StatType TREASURE_CHANCE = add("treasure_chance", "Treasure Chance", "\u26C3", 0xFFFFAA00, 0, true,
            "Better odds of fishing up treasure.");
    public static final StatType TROPHY_FISH_CHANCE = add("trophy_fish_chance", "Trophy Fish Chance", "\u2654", 0xFFFFAA00, 0, true,
            "Better odds of catching trophy fish.");
    public static final StatType BONUS_PEST_CHANCE = add("bonus_pest_chance", "Bonus Pest Chance", "\u0D60", 0xFF55FF55, 0, true,
            "More pests appear in the Garden.");
    public static final StatType WEAPON_ABILITY_DAMAGE = add("weapon_ability_damage", "Weapon Ability Damage", "\u0E51", 0xFFFF5555, 0, false,
            "Base damage of a weapon's ability.");
    public static final StatType ALCHEMY_WISDOM = add("alchemy_wisdom", "Alchemy Wisdom", "\u262F", 0xFF55FFFF, 0, false,
            "Each point is 1% more Alchemy XP.");
    public static final StatType TRACKING = add("tracking", "Tracking", "\u2316", 0xFF55FFFF, 0, false,
            "Hunting: how well you track critters.");
    public static final StatType PULL = add("pull", "Pull", "\u16F7", 0xFF55FFFF, 0, false,
            "Hunting: how hard your lasso pulls.");
    public static final StatType RIFT_DAMAGE = add("rift_damage", "Rift Damage", "\u2741", 0xFFFF5555, 0, false,
            "Damage in the Rift.");
    public static final StatType RIFT_INTELLIGENCE = add("rift_intelligence", "Rift Intelligence", "\u270E", 0xFF55FFFF, 0, false,
            "Mana in the Rift.");
    public static final StatType RIFT_TIME = add("rift_time", "Rift Time", "\u0444", 0xFF55FF55, 0, false,
            "Extra seconds in the Rift.");
    public static final StatType RIFT_HEALTH = add("rift_health", "Rift Health", "\u2764", 0xFFFF5555, 0, false,
            "Health in the Rift.");
    public static final StatType RIFT_MANA_REGEN = add("rift_mana_regen", "Rift Mana Regen", "\u26A1", 0xFF55FFFF, 0, false,
            "Mana regeneration in the Rift.");
    public static final StatType RIFT_WALK_SPEED = add("rift_walk_speed", "Rift Speed", "\u2726", 0xFFFFFFFF, 0, false,
            "Speed in the Rift.");
    public static final StatType WHEAT_FORTUNE = add("wheat_fortune", "Wheat Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Wheat when you farm it.");
    public static final StatType CARROT_FORTUNE = add("carrot_fortune", "Carrot Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Carrot when you farm it.");
    public static final StatType POTATO_FORTUNE = add("potato_fortune", "Potato Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Potato when you farm it.");
    public static final StatType PUMPKIN_FORTUNE = add("pumpkin_fortune", "Pumpkin Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Pumpkin when you farm it.");
    public static final StatType MELON_FORTUNE = add("melon_fortune", "Melon Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Melon when you farm it.");
    public static final StatType MUSHROOM_FORTUNE = add("mushroom_fortune", "Mushroom Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Mushroom when you farm it.");
    public static final StatType CACTUS_FORTUNE = add("cactus_fortune", "Cactus Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Cactus when you farm it.");
    public static final StatType SUGAR_CANE_FORTUNE = add("sugar_cane_fortune", "Sugar Cane Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Sugar Cane when you farm it.");
    public static final StatType NETHER_STALK_FORTUNE = add("nether_stalk_fortune", "Nether Wart Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Nether Wart when you farm it.");
    public static final StatType COCOA_BEANS_FORTUNE = add("cocoa_beans_fortune", "Cocoa Beans Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Cocoa Beans when you farm it.");
    public static final StatType FIG_FORTUNE = add("fig_fortune", "Fig Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Fig logs when you chop them.");
    public static final StatType MANGROVE_FORTUNE = add("mangrove_fortune", "Mangrove Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Mangrove logs when you chop them.");
    public static final StatType HELIX_FORTUNE = add("helix_fortune", "Helix Fortune", "\u2618", 0xFFFFAA00, 0, false,
            "Extra Helix logs when you chop them.");
    /**
     * Weapon damage. Only items carry it (it sets the item's vanilla attack damage, 5 damage = 1 vanilla
     * point); it is not a player stat that sources add to.
     */
    public static final StatType DAMAGE = add("damage", "Damage", "\uE110", 0xFFFF5555, 0, false,
            "The weapon's base damage, before Strength and crits.");

    private StatTypes() {
    }

    private static StatType add(String id, String name, String symbol, int color, double base, boolean percent, String description) {
        StatType type = new StatType(id, name, symbol, color, base, percent, description);
        TYPES.put(id, type);
        return type;
    }

    public static StatType get(String id) {
        return TYPES.get(id);
    }

    public static Collection<StatType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    /** Player stats: everything except weapon-only {@link #DAMAGE}. */
    public static boolean isPlayerStat(StatType type) {
        return type != DAMAGE;
    }

    /** Icons that aren't stats, from the same font. */
    public static final String MANA_ICON = "\uE111";
    public static final String COIN_ICON = "\uE112";
    public static final String LEVEL_ICON = "\uE113";

    /** Vanilla health is our health / 5: 100 health = 20 vanilla HP = 10 hearts. */
    public static final double HEALTH_SCALE = 5;
}
