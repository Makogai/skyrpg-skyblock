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
    FARMING("farming", 0xFFE5C34A, 0xFFF3EAD7, 0xFFB5A386, 0xFF7D705C, 0xFF8BD45A, 0xFF120E09, 10),
    /** SkyRPG's own look for general screens: dark slate with gold trim and ruby corner ornaments. */
    SKYBLOCK("skyblock", 0xFFF2B33D, 0xFFE9EDF4, 0xFF9AA3B5, 0xFF5E6779, 0xFF5CD68A, 0xFF0A0D12, 10),
    /** Mining: stone bricks with ore flecks, gem corners, a minecart-rail divider, stone buttons. */
    MINING("mining", 0xFF5CE1E6, 0xFFE6EDF2, 0xFF9AA6B2, 0xFF5E6874, 0xFF5CE1E6, 0xFF0B0D10, 10),
    /** The Crimson Isle: nether bricks with lava, gilded blackstone corners, a blaze-rod divider, magma buttons. */
    CRIMSON("crimson", 0xFFFF8C3A, 0xFFF5E6E1, 0xFFB89A94, 0xFF7A5F5A, 0xFFFF8C3A, 0xFF0F0708, 10),
    /** Dungeons: mossy stone bricks, skull corners, a chain with torches, stone-brick buttons. */
    DUNGEON("dungeon", 0xFFD94A4A, 0xFFEDE9E4, 0xFFA7A3A0, 0xFF6A6764, 0xFF5CD68A, 0xFF0B0B0C, 10),
    /** Enchanting, experiments, brewing, Maxwell: indigo with gold filigree, a rune divider, lapis buttons. */
    MAGIC("magic", 0xFFE8C35A, 0xFFEEE8FF, 0xFFA99CC9, 0xFF6B6088, 0xFFB98CFF, 0xFF0B0814, 10),
    /** Fishing: driftwood and rope, a wave divider, prismarine buttons. */
    FISHING("fishing", 0xFF5CA8FF, 0xFFE4F1FA, 0xFF93AFC4, 0xFF56708A, 0xFF5CA8FF, 0xFF070D13, 10);

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
