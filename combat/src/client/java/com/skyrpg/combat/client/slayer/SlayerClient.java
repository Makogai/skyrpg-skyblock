package com.skyrpg.combat.client.slayer;

import com.skyrpg.combat.slayer.SlayerPayloads;
import com.skyrpg.combat.slayer.Slayers;
import com.skyrpg.core.client.hud.HudManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Slayers on the client: the Slayer Master's screen, the quest HUD and the bosses' looks. */
public final class SlayerClient {
    private SlayerClient() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SlayerPayloads.View.TYPE, (payload, context) -> SlayerScreen.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(SlayerPayloads.Status.TYPE, (payload, context) -> SlayerWidget.status = payload);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SlayerWidget.status = SlayerPayloads.Status.NONE);
        ClientPlayNetworking.registerGlobalReceiver(SlayerPayloads.PageData.TYPE, (payload, context) -> {
            SlayersPage.data = payload.lines();
            SlayersPage.received = true;
        });
        // SkyBlock recipes locked behind a slayer level ask here on the client: your synced XP against the page data's thresholds.
        com.skyrpg.core.progress.SlayerLevels.installClient((player, key) -> {
            for (SlayerPayloads.PageLine line : SlayersPage.data) {
                if (line.key().equals(key)) {
                    return SlayersPage.level(line, Slayers.state(player).xp(line.id()));
                }
            }
            return 0;
        });
        com.skyrpg.core.client.menu.SkyRpgMenu.registerPage(new SlayersPage());
        HudManager.register(new SlayerWidget());
        SlayerRenderers.init();
    }
}
