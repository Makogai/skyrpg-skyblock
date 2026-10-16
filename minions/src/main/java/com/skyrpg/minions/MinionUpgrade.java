package com.skyrpg.minions;

import java.util.Map;

/**
 * What goes into a minion's two upgrade slots, as on Hypixel. Speed upgrades add up with each other and the fuel;
 * the others change what the minion makes. Shipping (the hoppers) has its own slot: see {@link #sellPercent}.
 */
public enum MinionUpgrade {
    AUTO_SMELTER("AUTO_SMELTER", 0, "Smelts what it makes"),
    COMPACTOR("COMPACTOR", 0, "Makes block forms"),
    SUPER_COMPACTOR("SUPER_COMPACTOR_3000", 0, "Makes enchanted forms"),
    DWARVEN_COMPACTOR("DWARVEN_COMPACTOR", 0, "Smelts, then makes enchanted forms"),
    DIAMOND_SPREADING("DIAMOND_SPREADING", 0, "1 Diamond per 10 items"),
    POTATO_SPREADING("POTATO_SPREADING", 0, "1 Potato per 20 items"),
    KRAMPUS_HELMET("KRAMPUS_HELMET", 0, "Rarely makes Red Gifts"),
    MINION_EXPANDER("MINION_EXPANDER", 5, "+1 block of reach"),
    FLYCATCHER("FLYCATCHER_UPGRADE", 20, "No flies"),
    BERBERIS_FUEL_INJECTOR("BERBERIS_FUEL_INJECTOR", 15, "Farming minions only"),
    SOLAR_PANEL("SOLAR_PANEL", 0, "+25% speed by day"),
    ENCHANTED_EGG("ENCHANTED_EGG", 0, "An egg from every chicken"),
    FLINT_SHOVEL("FLINT_SHOVEL", 0, "Always flint from gravel"),
    ENCHANTED_SHEARS("ENCHANTED_SHEARS", 0, "Shears every sheep"),
    LESSER_SOULFLOW_ENGINE("LESSER_SOULFLOW_ENGINE", 0, "Half output, Raw Soulflow every 3m"),
    SOULFLOW_ENGINE("SOULFLOW_ENGINE", 0, "Half output, Raw Soulflow every 90s");

    /** What the Auto Smelter (and the Dwarven Super Compactor) turns each item into. */
    public static final Map<String, String> SMELTS = Map.ofEntries(
            Map.entry("COBBLESTONE", "STONE"),
            Map.entry("IRON_ORE", "IRON_INGOT"),
            Map.entry("GOLD_ORE", "GOLD_INGOT"),
            Map.entry("SAND", "GLASS"),
            Map.entry("SAND:1", "GLASS"),
            Map.entry("LOG", "CHARCOAL"),
            Map.entry("LOG:1", "CHARCOAL"),
            Map.entry("LOG:2", "CHARCOAL"),
            Map.entry("LOG:3", "CHARCOAL"),
            Map.entry("LOG_2", "CHARCOAL"),
            Map.entry("LOG_2:1", "CHARCOAL"),
            Map.entry("CACTUS", "INK_SACK:2"),
            Map.entry("CLAY_BALL", "CLAY_BRICK"),
            Map.entry("NETHERRACK", "NETHER_BRICK_ITEM"),
            Map.entry("POTATO_ITEM", "BAKED_POTATO"),
            Map.entry("RAW_FISH", "COOKED_FISH"),
            Map.entry("RAW_FISH:1", "COOKED_FISH:1"),
            Map.entry("RAW_CHICKEN", "COOKED_CHICKEN"),
            Map.entry("RAW_BEEF", "COOKED_BEEF"),
            Map.entry("PORK", "GRILLED_PORK"),
            Map.entry("MUTTON", "COOKED_MUTTON"),
            Map.entry("RABBIT", "COOKED_RABBIT"));

    private final String item;
    private final int speed;
    private final String effect;

    MinionUpgrade(String item, int speed, String effect) {
        this.item = item;
        this.speed = speed;
        this.effect = effect;
    }

    /** The SkyBlock item that is this upgrade. */
    public String item() {
        return item;
    }

    /** How much faster it makes the minion work, in percent. */
    public int speed() {
        return speed;
    }

    /** A few words on what it does, for the minion screen. */
    public String effect() {
        return effect;
    }

    /** Whether two of it in one minion both count (speed upgrades); for the rest a second one does nothing. */
    public boolean stacks() {
        return this == MINION_EXPANDER || this == FLYCATCHER;
    }

    /** Whether this minion can use it at all (an Enchanted Egg is for Chicken Minions). */
    public boolean fits(MinionType type) {
        return fits(type.id(), type.category());
    }

    /** {@link #fits(MinionType)} by the minion's type id and category (the screen has those, not the type). */
    public boolean fits(String typeId, String category) {
        return switch (this) {
            case BERBERIS_FUEL_INJECTOR -> "farming".equals(category);
            case ENCHANTED_EGG -> "CHICKEN".equals(typeId);
            case FLINT_SHOVEL -> "GRAVEL".equals(typeId);
            case ENCHANTED_SHEARS -> "SHEEP".equals(typeId);
            default -> true;
        };
    }

    /** How often a Soulflow Engine makes a Raw Soulflow, in real milliseconds (0 for everything else). */
    public long soulflowMillis() {
        return switch (this) {
            case LESSER_SOULFLOW_ENGINE -> 180_000L;
            case SOULFLOW_ENGINE -> 90_000L;
            default -> 0L;
        };
    }

    /** The upgrade this SkyBlock item is, or null. */
    public static MinionUpgrade of(String skyblockId) {
        if (skyblockId == null) {
            return null;
        }
        for (MinionUpgrade upgrade : values()) {
            if (upgrade.item.equals(skyblockId)) {
                return upgrade;
            }
        }
        return null;
    }

    /**
     * For the shipping slot: the share of an item's NPC price a hopper sells what doesn't fit for (Budget Hopper 50%,
     * Enchanted Hopper 70%), or 0 if this isn't a hopper.
     */
    public static int sellPercent(String skyblockId) {
        if ("BUDGET_HOPPER".equals(skyblockId)) {
            return 50;
        }
        return "ENCHANTED_HOPPER".equals(skyblockId) ? 70 : 0;
    }
}
