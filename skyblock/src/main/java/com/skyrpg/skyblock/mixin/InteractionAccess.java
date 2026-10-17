package com.skyrpg.skyblock.mixin;

import net.minecraft.world.entity.Interaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Sizes a click box (the Mineshaft portal is a tall one). */
@Mixin(Interaction.class)
public interface InteractionAccess {
    @Invoker("setWidth")
    void skyrpg$setWidth(float width);

    @Invoker("setHeight")
    void skyrpg$setHeight(float height);
}
