package com.skyrpg.core.world;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Tells SkyBlock dimensions (the Hub and every island) from the survival archive. */
public final class SkyBlockWorlds {
    private static final String HUB = "skyrpg_hub";
    private static final String ISLANDS = "skyrpg_skyblock";

    private SkyBlockWorlds() {
    }

    /** True in the Hub and in every skyrpg_skyblock dimension. */
    public static boolean isSkyBlock(Level level) {
        return isSkyBlock(level.dimension());
    }

    /** True for the Hub and for every skyrpg_skyblock dimension. */
    public static boolean isSkyBlock(ResourceKey<Level> dimension) {
        String namespace = dimension.identifier().getNamespace();
        return namespace.equals(HUB) || namespace.equals(ISLANDS);
    }
}
