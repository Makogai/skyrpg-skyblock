package com.skyrpg.skyblock.client;

import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FurfSky Reborn is our item look, and only that: its item and armour textures (and how weapons sit in the hand),
 * plus its hotbar selection border. Everything else it ships (inventory and menu themes, the hotbar with a SkyBlock
 * Menu slot we don't have, fonts, block retextures) is left out, so SkyRPG's own screens stay ours.
 * <p>
 * The pack comes in Catharsis's container, split into feature folders ({@code item_melee/}, {@code armor_model/},
 * {@code gui_vanilla_hud_hotbar/}...); Catharsis serves each folder separately, and the {@code CatsPackResourcesMixin}
 * asks this class about every file.
 */
public final class FurfSkyFilter {
    /** Feature folders that are item looks. */
    private static final String[] ITEM_FOLDERS = {"item_", "armor_", "helmet_", "equipment_", "legacy_"};
    /** Namespaces in the pack's root that are item rendering. */
    private static final Set<String> ROOT_NAMESPACES = Set.of("held_model", "legacy_held_model", "gui_model");
    private static final String SELECTION = "textures/gui/sprites/hud/hotbar_selection.png";
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private FurfSkyFilter() {
    }

    /**
     * Whether a file from a Catharsis pack may load.
     *
     * @param prefix the feature folder Catharsis serves it from ("" or "/" for the pack's root, else e.g. "item_melee/")
     */
    public static boolean allowed(String prefix, String namespace, String path) {
        String folder = prefix.replace("/", "").toLowerCase(Locale.ROOT);
        if (LOGGED.add(folder)) {
            SkyRpgSkyblock.LOGGER.debug("Catharsis pack folder '{}'", folder);
        }
        if (folder.isEmpty()) {
            return ROOT_NAMESPACES.contains(namespace) || namespace.equals("skyblock");
        }
        for (String items : ITEM_FOLDERS) {
            if (folder.startsWith(items)) {
                return true;
            }
        }
        if (folder.startsWith("gui_vanilla_hud_hotbar")) {
            return namespace.equals("minecraft") && (path.equals(SELECTION) || path.equals(SELECTION + ".mcmeta"));
        }
        return false;
    }
}
