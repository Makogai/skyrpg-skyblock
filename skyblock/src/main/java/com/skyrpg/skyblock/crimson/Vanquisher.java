package com.skyrpg.skyblock.crimson;

import com.mojang.serialization.Codec;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Vanquisher (Hypixel's): any kill on the Crimson Isle has a 1 in 640 chance of calling one up beside you (twice,
 * three and four times as likely after 640, 960 and 1,280 kills without one; Vulcan's Wither Piper adds 2-10%). A
 * Wither of level 235 with 5,000,000 health and 3,000 damage; it drops 3 Corrupted Nether Stars, 352 Combat XP and 78
 * coins (its Combat XP comes with the kill, like any SkyBlock mob's). It leaves after five minutes with nobody near.
 */
final class Vanquisher {
    static final String TAG = "skyrpg_vanquisher";
    private static final double CHANCE = 1 / 640.0;

    /** Kills on the Isle since your last Vanquisher. */
    static final AttachmentType<Integer> KILLS = AttachmentRegistry.create(SkyRpgSkyblock.id("vanquisher_kills"),
            builder -> builder.persistent(Codec.INT).copyOnDeath());

    private record Live(Mob mob, UUID caller, Vec3 home, long[] lonely) {
    }

    private static final List<Live> LIVE = new ArrayList<>();

    private Vanquisher() {
    }

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer killer) || !CrimsonIsle.isCrimson(entity.level()) || entity instanceof Player) {
                return;
            }
            if (entity.entityTags().contains(TAG)) {
                killed(killer, entity);
                return;
            }
            int kills = killer.getAttachedOrElse(KILLS, 0) + 1;
            double pity = kills >= 1_280 ? 4 : kills >= 960 ? 3 : kills >= 640 ? 2 : 1;
            double chance = CHANCE * pity * (1 + CrimsonPerks.piper(killer) / 100);
            if (killer.getRandom().nextDouble() < chance) {
                killer.setAttached(KILLS, 0);
                spawn((ServerLevel) entity.level(), killer, entity.position());
            } else {
                killer.setAttached(KILLS, kills);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && LIVE.stream().noneMatch(l -> l.mob() == entity)) {
                entity.discard();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Vanquisher::tick);
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(
                net.minecraft.commands.Commands.literal("sbvanquisher").requires(net.minecraft.commands.Commands.hasPermission(
                        net.minecraft.commands.Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    if (CrimsonIsle.isCrimson(player.level())) {
                        spawn((ServerLevel) player.level(), player, player.position().add(player.getLookAngle().scale(6)));
                    }
                    return 1;
                })));
    }

    static void spawn(ServerLevel level, ServerPlayer caller, Vec3 at) {
        Mob mob = EntityTypes.WITHER.create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return;
        }
        mob.snapTo(at.x, at.y + 2, at.z, caller.getYRot() + 180, 0);
        Zones.dressAs(mob, "Vanquisher", 235, 5_000_000, 3_000, 352);
        mob.addTag(TAG);
        mob.addTag(Zones.FIGHTABLE_TAG);
        mob.setPersistenceRequired();
        mob.setCustomNameVisible(true);
        LIVE.add(new Live(mob, caller.getUUID(), at, new long[] {0}));
        level.addFreshEntity(mob);
        level.playSound(null, mob.blockPosition(), SoundEvents.WITHER_SPAWN, net.minecraft.sounds.SoundSource.HOSTILE, 1f, 0.8f);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 2, at.z, 60, 1.5, 1.5, 1.5, 0.05);
        Notice.of(Notice.Kind.WARN).tag("VANQUISHER").send(caller, Component.literal("A Vanquisher is spawning nearby!").withStyle(ChatFormatting.DARK_PURPLE));
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0 || LIVE.isEmpty()) {
            return;
        }
        LIVE.removeIf(live -> {
            Mob mob = live.mob();
            if (!mob.isAlive()) {
                return true;
            }
            // Leashed near where it rose; gone after five minutes with nobody close.
            if (mob.position().distanceTo(live.home()) > 40) {
                mob.teleportTo(live.home().x, live.home().y + 2, live.home().z);
            }
            boolean near = !((ServerLevel) mob.level()).getPlayers(p -> p.distanceTo(mob) < 64).isEmpty();
            live.lonely()[0] = near ? 0 : live.lonely()[0] + 1;
            if (live.lonely()[0] > 300) {
                mob.discard();
                return true;
            }
            return false;
        });
    }

    private static void killed(ServerPlayer killer, Entity vanquisher) {
        ItemDefinition star = ItemRegistry.skyblock("CORRUPTED_NETHER_STAR");
        if (star != null) {
            ItemStack stack = SkyItems.create(star, 3);
            if (!killer.getInventory().add(stack)) {
                killer.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        Wallet.give(killer, 78);
        Notice.of(Notice.Kind.SUCCESS).tag("VANQUISHER").panel()
                .line(Component.literal(" 3 Corrupted Nether Stars  ·  +352 Combat XP  ·  +78 coins").withStyle(ChatFormatting.GRAY))
                .send(killer, Component.literal("You killed the Vanquisher!").withStyle(ChatFormatting.DARK_PURPLE));
        Msg.playSound(killer, SoundEvents.WITHER_DEATH, 0.6f, 1.2f);
    }
}
