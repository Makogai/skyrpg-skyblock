package com.skyrpg.skyblock.mining;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;

/**
 * The Dwarven Mines' Ghosts (The Mist): Hypixel's are invisible charged creepers that never explode and hit you when
 * they touch you. Zone mobs named "Ghost" get charged and invisible here, never swell, and strike players next to them.
 */
public final class MinesMobs {
    private static final String GHOST = "skyrpg_ghost";
    private static final List<Creeper> GHOSTS = new ArrayList<>();

    private MinesMobs() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(MinesMobs::tick);
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(SkyMining.DWARVEN_MINES);
        if (level == null) {
            GHOSTS.clear();
            return;
        }
        long now = server.getTickCount();
        if (now % 20 == 0) {
            GHOSTS.removeIf(ghost -> ghost.isRemoved() || !ghost.isAlive());
            for (Creeper creeper : level.getEntities(EntityTypes.CREEPER, c -> c.getCustomName() != null
                    && c.getCustomName().getString().equals("Ghost") && !c.entityTags().contains(GHOST))) {
                haunt(level, creeper);
            }
        }
        for (Creeper ghost : GHOSTS) {
            ghost.setSwellDir(-1);
            if (now % 20 != (ghost.getId() & 15)) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (!player.isCreative() && !player.isSpectator() && player.distanceToSqr(ghost) < 2.25) {
                    player.hurtServer(level, level.damageSources().mobAttack(ghost), (float) ghost.getAttributeValue(Attributes.ATTACK_DAMAGE));
                }
            }
        }
    }

    private static void haunt(ServerLevel level, Creeper creeper) {
        creeper.addTag(GHOST);
        // Charged the way vanilla charges them; its fire and lost health don't count.
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (bolt != null) {
            creeper.thunderHit(level, bolt);
        }
        creeper.clearFire();
        creeper.setHealth(creeper.getMaxHealth());
        creeper.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        GHOSTS.add(creeper);
    }
}
