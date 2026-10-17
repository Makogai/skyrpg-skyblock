package com.skyrpg.skyblock.mixin;

import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** SkyBlock's brewing ({@code SkyBrewing}) runs the stand's timer itself and shows it through the stand's own data. */
@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandAccess {
    @Accessor("brewTime")
    int skyrpg$brewTime();

    @Accessor("brewTime")
    void skyrpg$setBrewTime(int ticks);

    @Accessor("totalBrewTime")
    void skyrpg$setTotalBrewTime(int ticks);

    @Accessor("fuel")
    void skyrpg$setFuel(int fuel);

    @Accessor("dataAccess")
    ContainerData skyrpg$data();
}
