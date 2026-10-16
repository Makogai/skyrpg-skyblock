package com.skyrpg.combat.client.slayer;

import com.skyrpg.combat.slayer.SlayerEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** The slayer bosses' own 3D models, one per line, each showing its tier (see the four *Model classes). */
final class SlayerRenderers {
    private SlayerRenderers() {
    }

    static void init() {
        ModelLayerRegistry.registerModelLayer(GraveTyrantRenderer.LAYER, GraveTyrantModel::createLayer);
        BroodmotherBossRenderer.register();
        BoneMarshalBossRenderer.register();
        VoidStalkerBossRenderer.register();
        // SkyBlock's bosses look like Hypixel's: the mob itself (the Revenant Horror in its armour). The survival models
        // above stay registered for their layers; these renderers take the entities.
        EntityRendererRegistry.register(SlayerEntities.GRAVE_TYRANT, net.minecraft.client.renderer.entity.ZombieRenderer::new);
        EntityRendererRegistry.register(SlayerEntities.BROODMOTHER, net.minecraft.client.renderer.entity.SpiderRenderer::new);
        EntityRendererRegistry.register(SlayerEntities.VOID_STALKER, net.minecraft.client.renderer.entity.EndermanRenderer::new);
        EntityRendererRegistry.register(SlayerEntities.SVEN, net.minecraft.client.renderer.entity.WolfRenderer::new);
    }
}
