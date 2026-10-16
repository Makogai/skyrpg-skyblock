package com.skyrpg.items.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads a SkyBlock id from custom data without {@code copyTag()} copying the whole tag (it runs for every stack, often). */
@Mixin(CustomData.class)
public interface CustomDataAccess {
    @Accessor("tag")
    CompoundTag skyrpg$tag();
}
