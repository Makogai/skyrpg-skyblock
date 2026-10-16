package com.skyrpg.skyblock.client;

import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.skyblock.profile.ProfileListPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client side of SkyRPG SkyBlock: the SkyBlock pages of the K menu. */
public final class SkyRpgSkyblockClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SkyRpgMenu.registerPage(new ProfilesPage());
        ClientPlayNetworking.registerGlobalReceiver(ProfileListPayload.TYPE, (payload, context) -> ProfilesPage.set(payload));
    }
}
