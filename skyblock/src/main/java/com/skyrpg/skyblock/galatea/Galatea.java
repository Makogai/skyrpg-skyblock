package com.skyrpg.skyblock.galatea;

import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Galatea (Hypixel's 2025 Foraging update): an archipelago in its own dimension, its first island Moonglade Marsh
 * (tools/builds/hub_areas/galatea_*.py). It opens at Foraging 12; the warp and the pad on The Park's Jungle Island lead there.
 */
public final class Galatea {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, SkyRpgSkyblock.id("galatea"));
    static final int FORAGING_NEEDED = 12;
    static final ResourceKey<Level> PARK = ResourceKey.create(Registries.DIMENSION, SkyRpgSkyblock.id("the_park"));

    private Galatea() {
    }

    public static void init() {
        GalateaTrees.init();
        Hunting.init();
        Critters.init();
        GalateaNpcs.init();
        Hotf.init();
        Rosemary.init();
        Starlyn.init();
        Huntraps.init();
        BlackHole.init();
        Resources.init();
        Temple.init();
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
            if (!isGalatea(to) || player.isCreative() || SkillLevels.level(player, SkillLevels.FORAGING) >= FORAGING_NEEDED) {
                return;
            }
            ServerLevel park = player.level().getServer().getLevel(PARK);
            if (park != null) {
                player.teleportTo(park, 194.5, 65, 74.5, Set.of(), 90, 0, true);
            }
            player.sendSystemMessage(Component.literal("Galatea opens at Foraging " + FORAGING_NEEDED + ".").withStyle(ChatFormatting.RED));
        });
    }

    public static boolean isGalatea(Level level) {
        return level.dimension().equals(LEVEL);
    }
}
