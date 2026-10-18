package com.skyrpg.core.world;

import java.util.function.Predicate;

/**
 * The Mayor's perks, for every mod: whether a perk ("slashed_pricing", "pet_xp_buff"...) is in effect right now. The
 * SkyBlock mod's elections install the answer; without them no perk is.
 */
public final class Mayor {
    private static Predicate<String> active = perk -> false;

    private Mayor() {
    }

    public static void install(Predicate<String> perks) {
        active = perks;
    }

    public static boolean active(String perk) {
        return active.test(perk);
    }
}
