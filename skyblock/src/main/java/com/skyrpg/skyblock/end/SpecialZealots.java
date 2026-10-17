package com.skyrpg.skyblock.end;

import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.zone.Zones;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Hypixel's Special Zealots: a Zealot you kill in The End has a 1 in 420 chance (a Zealot Bruiser 1 in 210) to leave one
 * behind, an Enderman holding an End Portal Frame. Killing it gives the Summoning Eye, for the Dragon's Nest altar.
 */
public final class SpecialZealots {
    private static final String TAG = "skyrpg_special_zealot";

    private SpecialZealots() {
    }

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof Enderman enderman) || !(entity.level() instanceof ServerLevel level) || !level.dimension().equals(DragonFight.THE_END)
                    || !(source.getEntity() instanceof ServerPlayer killer)) {
                return;
            }
            if (enderman.entityTags().contains(TAG)) {
                eye(killer);
                return;
            }
            String name = enderman.getCustomName() == null ? "" : enderman.getCustomName().getString();
            int odds = name.equals("Zealot Bruiser") ? 210 : name.equals("Zealot") ? 420 : 0;
            if (odds > 0 && level.getRandom().nextInt(odds) == 0) {
                spawn(level, enderman, killer);
            }
        });
    }

    private static void spawn(ServerLevel level, Enderman where, ServerPlayer killer) {
        Enderman special = EntityTypes.ENDERMAN.create(level, EntitySpawnReason.EVENT);
        if (special == null) {
            return;
        }
        special.snapTo(where.getX(), where.getY(), where.getZ(), where.getYRot(), 0);
        special.setPersistenceRequired();
        Zones.dressAs(special, "Special Zealot", 55, 2_000, 1_250, 40);
        special.setCarriedBlock(Blocks.END_PORTAL_FRAME.defaultBlockState());
        special.addTag(TAG);
        special.addTag(Zones.FIGHTABLE_TAG);
        level.addFreshEntity(special);
        Notice.of(Notice.Kind.COMBAT).tag("ZEALOT").send(killer, Component.literal("A special ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("Zealot").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" has spawned nearby!").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(killer, SoundEvents.ENDERMAN_SCREAM, 1.0f, 0.6f);
    }

    private static void eye(ServerPlayer killer) {
        ItemDefinition def = ItemRegistry.skyblock("SUMMONING_EYE");
        if (def == null) {
            return;
        }
        ItemStack eye = SkyItems.create(def, 1);
        if (!killer.getInventory().add(eye)) {
            killer.drop(eye, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        Notice.send(killer, Notice.Kind.DROP, Component.literal("Summoning Eye").withColor(def.rarity().color()));
        Msg.playSound(killer, SoundEvents.PLAYER_LEVELUP, 1.0f, 0.5f);
    }
}
