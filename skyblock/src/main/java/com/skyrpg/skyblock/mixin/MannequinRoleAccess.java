package com.skyrpg.skyblock.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The Garden's Mannequins (visitors, SkyMart, Phillip) show a role line under their name instead of "NPC". */
@Mixin(Mannequin.class)
public interface MannequinRoleAccess {
    @Invoker("setDescription")
    void skyrpg$setRole(Component description);

    @Invoker("setHideDescription")
    void skyrpg$setHideRole(boolean hide);

    @Invoker("setImmovable")
    void skyrpg$setStill(boolean immovable);
}
