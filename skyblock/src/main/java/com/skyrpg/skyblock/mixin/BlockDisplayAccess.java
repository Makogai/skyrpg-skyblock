package com.skyrpg.skyblock.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Sets what a block display shows (the Mineshaft portal's pane). */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccess {
    @Invoker("setBlockState")
    void skyrpg$setBlockState(BlockState state);
}
