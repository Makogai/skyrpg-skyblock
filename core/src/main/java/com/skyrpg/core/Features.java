package com.skyrpg.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Switches for whole systems a game mode has no use for, so one mod can turn off another's without depending on
 * it: SkyBlock switches off the survival Contracts and Journal quests ({@code Features.off(CONTRACTS)}), and those
 * systems check {@link #on} before they do anything. Everything is on unless switched off.
 */
public final class Features {
    public static final String CONTRACTS = "skyrpg_events:contracts";
    public static final String QUESTS = "skyrpg_progress:quests";

    private static final Set<String> OFF = ConcurrentHashMap.newKeySet();

    private Features() {
    }

    public static void off(String feature) {
        OFF.add(feature);
    }

    public static boolean on(String feature) {
        return !OFF.contains(feature);
    }
}
