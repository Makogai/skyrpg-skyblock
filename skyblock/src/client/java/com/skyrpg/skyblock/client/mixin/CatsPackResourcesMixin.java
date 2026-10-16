package com.skyrpg.skyblock.client.mixin;

import com.skyrpg.skyblock.client.FurfSkyFilter;
import java.io.InputStream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Catharsis serves a pack like FurfSky Reborn folder by folder; this keeps only what {@link FurfSkyFilter} allows.
 * Pseudo: does nothing when Catharsis isn't installed.
 */
@Pseudo
@Mixin(targets = "me.owdding.catharsis.features.pack.CatsPackResources", remap = false)
public abstract class CatsPackResourcesMixin {
    @Shadow
    @Final
    private String prefix;

    @Inject(method = "getResource(Lnet/minecraft/server/packs/PackType;Lnet/minecraft/resources/Identifier;)Lnet/minecraft/server/packs/resources/IoSupplier;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void skyrpg$onlyItems(PackType type, Identifier id, CallbackInfoReturnable<IoSupplier<InputStream>> cir) {
        if (type == PackType.CLIENT_RESOURCES && !FurfSkyFilter.allowed(prefix, id.getNamespace(), id.getPath())) {
            cir.setReturnValue(null);
        }
    }

    @ModifyVariable(method = "listResources", at = @At("HEAD"), argsOnly = true, remap = false)
    private PackResources.ResourceOutput skyrpg$onlyItemsListed(PackResources.ResourceOutput output, PackType type) {
        if (type != PackType.CLIENT_RESOURCES) {
            return output;
        }
        return (id, supplier) -> {
            if (FurfSkyFilter.allowed(prefix, id.getNamespace(), id.getPath())) {
                output.accept(id, supplier);
            }
        };
    }
}
