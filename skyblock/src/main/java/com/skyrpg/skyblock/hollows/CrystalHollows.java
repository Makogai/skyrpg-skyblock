package com.skyrpg.skyblock.hollows;

import com.skyrpg.skyblock.SkyRpgSkyblock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Hypixel's Crystal Hollows: a generated cave world under the Dwarven Mines ({@link HollowsBiomes}). */
public final class CrystalHollows {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, SkyRpgSkyblock.id("crystal_hollows"));

    private CrystalHollows() {
    }

    public static void init() {
        Registry.register(BuiltInRegistries.BIOME_SOURCE, SkyRpgSkyblock.id("hollows"), HollowsBiomes.CODEC);
        TreasureChests.init();
        HollowsAccess.init();
        HollowsReset.init();
    }
}
