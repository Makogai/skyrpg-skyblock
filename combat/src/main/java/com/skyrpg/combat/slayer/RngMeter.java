package com.skyrpg.combat.slayer;

import java.util.List;

/**
 * Hypixel's Slayer RNG Meter: slayer XP from Tier III and higher bosses fills a meter per slayer, and the one rare drop you
 * picked gets likelier as it fills (1x to 3x its chance), and is guaranteed once the meter holds the XP it asks for. That
 * price is {@code 500 x 100 / the highest chance (in percent) the drop has on a tier you've beaten}.
 */
public final class RngMeter {
    /** Slayer XP per percent of chance (Hypixel: 500, 250 for the Vampire slayer). */
    public static final double MULTIPLIER = 500;
    /** The lowest tier whose slayer XP fills the meter, and that must be beaten before the meter opens. */
    public static final int MIN_TIER = 3;
    /** Drops at or above this chance (Occasional ones and up) can't be picked. */
    private static final double MAX_PICKABLE = 0.1;

    private RngMeter() {
    }

    /** Whether a drop can be picked: rolled at under 10% and not a token. */
    public static boolean pickable(SlayerLine.Drop drop) {
        double best = 0;
        for (double[] at : drop.tiers().values()) {
            best = Math.max(best, at[2]);
        }
        return best > 0 && best < MAX_PICKABLE;
    }

    /** The chance of a drop on each tier (index 0 is tier I), 0 where it doesn't drop. */
    public static List<Double> chances(SlayerLine line, SlayerLine.Drop drop) {
        List<Double> out = new java.util.ArrayList<>();
        for (int tier = 1; tier <= line.tiers().size(); tier++) {
            double best = 0;
            // An item the data lists twice (two rows of the same drop) counts as one.
            for (SlayerLine.Drop other : line.drops()) {
                double[] at = other.tiers().get(tier);
                if (other.item().equals(drop.item()) && at != null) {
                    best = Math.max(best, at[2]);
                }
            }
            out.add(best);
        }
        return out;
    }

    /** Whether the meter is open: a Tier III or higher boss of the line has been slain. */
    public static boolean open(SlayerLine line, SlayerState state) {
        for (int tier = MIN_TIER; tier <= line.tiers().size(); tier++) {
            if (state.kills(line.id(), tier) > 0) {
                return true;
            }
        }
        return false;
    }

    /** The slayer XP a drop's meter asks for, from its best chance on the tiers beaten, or 0 if none counts. */
    public static double required(String lineId, List<Double> chances, SlayerState state) {
        double best = 0;
        for (int i = 0; i < chances.size(); i++) {
            if (state.kills(lineId, i + 1) > 0) {
                best = Math.max(best, chances.get(i));
            }
        }
        return best <= 0 ? 0 : MULTIPLIER / best;
    }

    public static double required(SlayerLine line, SlayerLine.Drop drop, SlayerState state) {
        return required(line.id(), chances(line, drop), state);
    }

    /** The chance after the meter: 1x to 3x as it fills, guaranteed when full. */
    public static double boosted(double chance, double stored, double required) {
        if (required <= 0) {
            return chance;
        }
        if (stored >= required) {
            return 1;
        }
        return chance * (1 + Math.min(2 * stored / required, 2));
    }

    /** The drop picked on a line, or null if none (or one that can no longer be picked). */
    public static SlayerLine.Drop picked(SlayerLine line, SlayerState state, int level) {
        String id = state.pick(line.id());
        if (id.isEmpty() || !open(line, state)) {
            return null;
        }
        for (SlayerLine.Drop drop : line.drops()) {
            if (drop.item().equals(id) && pickable(drop) && level >= drop.level() && required(line, drop, state) > 0) {
                return drop;
            }
        }
        return null;
    }
}
