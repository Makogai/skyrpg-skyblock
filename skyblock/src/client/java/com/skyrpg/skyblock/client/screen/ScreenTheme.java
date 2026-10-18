package com.skyrpg.skyblock.client.screen;

import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import net.minecraft.resources.Identifier;

/**
 * A screen's look: its own frame, background, header divider, cards, buttons and tabs (GUI sprites drawn by
 * {@code tools/ui/themes.py} under {@code theme/<name>/}), and its colours. {@link #DEFAULT} is SkyRPG's plain dark
 * window, drawn without sprites.
 */
public enum ScreenTheme {
    DEFAULT(null, Theme.ACCENT, Theme.TEXT, Theme.MUTED, Theme.DIM, Theme.GREEN, Theme.TRACK, 4),
    /** The Garden, the farming islands, farming NPCs: oak planks and iron, a straw-rope divider, grass-block buttons. */
    FARMING("farming", 0xFFE5C34A, 0xFFF3EAD7, 0xFFB5A386, 0xFF7D705C, 0xFF8BD45A, 0xFF120E09, 10);

    private final String sprites;
    public final int accent;
    public final int text;
    public final int muted;
    public final int dim;
    public final int good;
    public final int track;
    /** How far in from the window's edge the content starts. */
    public final int inset;

    ScreenTheme(String sprites, int accent, int text, int muted, int dim, int good, int track, int inset) {
        this.sprites = sprites;
        this.accent = accent;
        this.text = text;
        this.muted = muted;
        this.dim = dim;
        this.good = good;
        this.track = track;
        this.inset = inset;
    }

    public boolean sprited() {
        return sprites != null;
    }

    Identifier sprite(String piece) {
        return SkyRpgSkyblock.id("theme/" + sprites + "/" + piece);
    }
}
