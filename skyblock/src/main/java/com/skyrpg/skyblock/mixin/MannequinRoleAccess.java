package com.skyrpg.skyblock.mixin;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.decoration.Mannequin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
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

    /** The synced role line, so a screen's portrait can leave it out. */
    @Accessor("DATA_DESCRIPTION")
    static EntityDataAccessor<Optional<Component>> skyrpg$roleData() {
        throw new AssertionError();
    }
}
