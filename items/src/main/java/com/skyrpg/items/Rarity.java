package com.skyrpg.items;

import java.util.Locale;

/**
 * Item rarity: sets the name colour and the "Rare Sword" line at the bottom of tooltips. Hypixel's order and colours;
 * its {@code SUPREME} is the old name for Divine and {@code UNOBTAINABLE} reads as Special.
 */
public enum Rarity {
    COMMON(0xFFFFFFFF),
    UNCOMMON(0xFF55FF55),
    RARE(0xFF5555FF),
    EPIC(0xFFAA00AA),
    LEGENDARY(0xFFFFAA00),
    MYTHIC(0xFFFF55FF),
    DIVINE(0xFF55FFFF),
    SPECIAL(0xFFFF5555),
    VERY_SPECIAL(0xFFFF5555),
    ULTIMATE(0xFFAA0000),
    ADMIN(0xFFAA0000);

    private final int color;

    Rarity(int color) {
        this.color = color;
    }

    /** ARGB colour for names and the rarity line. */
    public int color() {
        return color;
    }

    /** "Very Special", as the rarity line shows it. */
    public String label() {
        StringBuilder out = new StringBuilder();
        for (String word : name().toLowerCase(Locale.ROOT).split("_")) {
            out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    /** One rarity up (what a Recombobulator does); Divine, Very Special and above stay put. */
    public Rarity upgraded() {
        return switch (this) {
            case COMMON -> UNCOMMON;
            case UNCOMMON -> RARE;
            case RARE -> EPIC;
            case EPIC -> LEGENDARY;
            case LEGENDARY -> MYTHIC;
            case MYTHIC -> DIVINE;
            case SPECIAL -> VERY_SPECIAL;
            default -> this;
        };
    }

    public static Rarity parse(String name) {
        return switch (name.toUpperCase(Locale.ROOT).replace(' ', '_')) {
            case "SUPREME" -> DIVINE;
            case "UNOBTAINABLE" -> SPECIAL;
            case String other -> valueOf(other);
        };
    }
}
