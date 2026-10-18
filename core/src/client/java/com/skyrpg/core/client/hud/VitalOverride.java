package com.skyrpg.core.client.hud;

import java.util.function.Supplier;

/**
 * Something that takes the health bar's place for a while (the Rift shows your Rift Time there): its text, how full it
 * is and its colour. Mods set {@link #HEALTH}; it returns null when the normal bar should show.
 */
public final class VitalOverride {
    /** A bar: "ф 7:59", 0..1, ARGB. */
    public record Bar(String text, float fill, int color) {
    }

    public static volatile Supplier<Bar> HEALTH = () -> null;

    private VitalOverride() {
    }
}
