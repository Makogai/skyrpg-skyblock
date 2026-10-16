package com.skyrpg.skyblock.client;

import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.skyblock.area.Areas;
import com.skyrpg.skyblock.storage.StoragePayloads;
import com.skyrpg.skyblock.travel.FastTravel;
import com.skyrpg.skyblock.zone.Zones;
import com.skyrpg.skyblock.profile.ProfileListPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client side of SkyRPG SkyBlock: the SkyBlock pages of the K menu. */
public final class SkyRpgSkyblockClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SkyRpgMenu.registerPage(new SkyBlockMenuPage());
        SkyRpgMenu.registerPage(new ProfilesPage());
        SkyRpgMenu.registerPage(new FastTravelPage());
        ClientPlayNetworking.registerGlobalReceiver(FastTravel.WarpList.TYPE, (payload, context) -> FastTravelPage.set(payload));
        SkyRpgMenu.registerPage(new CalendarPage());
        SkyRpgMenu.registerPage(new ItemBrowserPage());
        StoragePage storage = new StoragePage();
        SkyRpgMenu.registerPage(storage);
        ClientPlayNetworking.registerGlobalReceiver(StoragePayloads.Summary.TYPE, (payload, context) -> StoragePage.set(payload));
        ClientPlayNetworking.registerGlobalReceiver(StoragePayloads.Show.TYPE, (payload, context) -> SkyRpgMenu.open(storage.id()));
        HudManager.register(new SidebarWidget());
        ClientPlayNetworking.registerGlobalReceiver(Zones.ZoneList.TYPE, (payload, context) -> Zones.replace(payload.zones()));
        ClientPlayNetworking.registerGlobalReceiver(Areas.AreaPayload.TYPE, (payload, context) -> SidebarWidget.setArea(payload.area()));
        ClientPlayNetworking.registerGlobalReceiver(ProfileListPayload.TYPE, (payload, context) -> ProfilesPage.set(payload));
    }
}
