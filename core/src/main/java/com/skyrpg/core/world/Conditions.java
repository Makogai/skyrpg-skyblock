package com.skyrpg.core.world;

import java.util.function.Predicate;

/**
 * Game conditions other mods can ask about by name: "event:Spooky Festival" (a calendar event is running),
 * "perk:mythological_ritual" (a Mayor's perk is in effect). The SkyBlock mod installs the answers; anything unknown is
 * true. NPCs use it for "when" (event NPCs).
 */
public final class Conditions {
    private static Predicate<String> check = condition -> true;

    private Conditions() {
    }

    public static void install(Predicate<String> answers) {
        check = answers;
    }

    public static boolean test(String condition) {
        return condition == null || condition.isEmpty() || check.test(condition);
    }
}
