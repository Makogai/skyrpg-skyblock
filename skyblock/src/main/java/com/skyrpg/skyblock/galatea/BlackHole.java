package com.skyrpg.skyblock.galatea;

import com.skyrpg.core.text.Msg;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's Pocket Black Holes (Small, Medium, Large): right-click to set one where you look (up to 10 blocks) for 180
 * seconds, one at a time. It pulls Galatea's wounded monsters in (under half health, within 10 blocks); one that reaches
 * it under a tenth of its health is captured: gone, and its shard in your Hunting Box. Decided where Hypixel's numbers
 * weren't found: the Medium pulls from 12 blocks and the Large from 14, and the Large has a 5% chance of a second shard.
 */
final class BlackHole {
    private static final List<String> TIERS = List.of("SMALL_POCKET_BLACK_HOLE", "MEDIUM_POCKET_BLACK_HOLE", "LARGE_POCKET_BLACK_HOLE");
    private static final int LIFE = 180 * 20;

    private record Hole(ServerLevel level, Vec3 at, int tier, long until) {
    }

    private static final Map<UUID, Hole> HOLES = new HashMap<>();

    private BlackHole() {
    }

    static void init() {
        Abilities.register("black_hole", (player, ability) -> place(player));
        ServerTickEvents.END_SERVER_TICK.register(BlackHole::tick);
    }

    private static boolean place(ServerPlayer player) {
        int tier = TIERS.indexOf(SkyItems.skyblockId(player.getMainHandItem()));
        if (tier < 0) {
            return false;
        }
        if (!Galatea.isGalatea(player.level())) {
            player.sendOverlayMessage(Component.literal("Black Holes capture Galatea's monsters: use it on Galatea.").withStyle(ChatFormatting.RED));
            return false;
        }
        HitResult hit = player.pick(10, 1f, false);
        Vec3 at = hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? Vec3.atCenterOf(block.getBlockPos().relative(block.getDirection())) : player.getEyePosition().add(player.getLookAngle().scale(6));
        ServerLevel level = (ServerLevel) player.level();
        HOLES.put(player.getUUID(), new Hole(level, at, tier, level.getServer().getTickCount() + LIFE));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PORTAL_TRIGGER, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.6f);
        player.sendOverlayMessage(Component.literal("Black Hole set: it'll pull in wounded monsters for 180s.").withStyle(ChatFormatting.DARK_PURPLE));
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (HOLES.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        HOLES.entrySet().removeIf(entry -> {
            Hole hole = entry.getValue();
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner == null || now > hole.until() || owner.level() != hole.level()) {
                return true;
            }
            Vec3 c = hole.at();
            if (now % 2 == 0) {
                double a = now * 0.3;
                for (int i = 0; i < 3; i++) {
                    double r = 0.6 + i * 0.35;
                    hole.level().sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a + i * 2.1) * r, c.y + 0.2 * i, c.z + Math.sin(a + i * 2.1) * r,
                            1, 0, 0, 0, 0);
                }
                hole.level().sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 1, 0.1, 0.1, 0.1, 0);
            }
            double reach = 10 + 2 * hole.tier();
            for (Mob mob : hole.level().getEntitiesOfClass(Mob.class, new AABB(c, c).inflate(reach))) {
                Hunting.Shard shard = Hunting.shardOf(mob);
                if (shard == null || mob.entityTags().contains(Critters.TAG) || mob.getHealth() > mob.getMaxHealth() * 0.5) {
                    continue;
                }
                Vec3 pull = c.subtract(mob.position());
                if (pull.length() < 2 && mob.getHealth() < mob.getMaxHealth() * 0.1) {
                    hole.level().sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 0.5, mob.getZ(), 30, 0.3, 0.5, 0.3, 0.2);
                    mob.discard();
                    int extra = hole.tier() == 2 && owner.getRandom().nextDouble() < 0.05 ? 1 : 0;
                    Hunting.catchShard(owner, shard, 1 + extra, "black hole");
                    Msg.playSound(owner, SoundEvents.PORTAL_TRAVEL, 0.3f, 1.8f);
                    continue;
                }
                mob.setDeltaMovement(mob.getDeltaMovement().add(pull.normalize().scale(0.12)));
                mob.needsSync = true;
            }
            return false;
        });
    }
}
