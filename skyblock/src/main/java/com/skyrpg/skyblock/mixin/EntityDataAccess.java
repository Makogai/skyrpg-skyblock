package com.skyrpg.skyblock.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** An entity's custom data (its "data" tag): where the Catacombs' room markers keep what they are. */
@Mixin(Entity.class)
public interface EntityDataAccess {
    @Accessor("customData")
    CustomData skyrpg$customData();
}
