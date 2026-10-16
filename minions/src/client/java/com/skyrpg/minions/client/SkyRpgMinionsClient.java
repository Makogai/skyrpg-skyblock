package com.skyrpg.minions.client;

import com.skyrpg.minions.SkyRpgMinions;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.ArmorStandRenderer;

/** Client side of SkyRPG Minions: minions look like small armour stands, and have their own screen. */
public final class SkyRpgMinionsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(SkyRpgMinions.MINION, ArmorStandRenderer::new);
        MenuScreens.register(SkyRpgMinions.MENU, MinionScreen::new);
    }
}
