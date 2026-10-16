package com.skyrpg.skyblock.client;

import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.theme.ToggledPacks;
import com.skyrpg.skyblock.area.Areas;
import com.skyrpg.skyblock.storage.StoragePayloads;
import com.skyrpg.skyblock.travel.FastTravel;
import com.skyrpg.skyblock.zone.Zones;
import com.skyrpg.skyblock.profile.ProfileListPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client side of SkyRPG SkyBlock: the SkyBlock pages of the K menu. */
public final class SkyRpgSkyblockClient implements ClientModInitializer {
    /** FurfSky Reborn as the modpack downloads it from Modrinth (see tools/pack/modrinth.json). */
    private static final String FURFSKY = "file/FurfSkyReborn.cats.zip";

    @Override
    public void onInitializeClient() {
        // Every SkyBlock item looks as on Hypixel with FurfSky Reborn (read by Catharsis), on unless switched off.
        ToggledPacks.registerFile(FURFSKY, "interface.furfsky", SkyRpgSettings.INTERFACE, "FurfSky Reborn item looks",
                "SkyBlock items and armour look the way FurfSky Reborn draws them, as on Hypixel. Without it you see the plain item underneath",
                true).section("SkyBlock").keywords("texture", "resource pack", "furf", "catharsis");
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
