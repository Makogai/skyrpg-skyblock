package com.skyrpg.skyblock.mining;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.essence.Essence;
import com.skyrpg.skyblock.zone.Zones;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's rare Dwarven Mines spawns, rolled when a player mines an ore here. The <b>Golden Goblin</b> (0.1% of ores;
 * 3.33% of them are <b>Diamond Goblins</b> instead): a timid goblin that runs from you, yours alone to hit, three hits
 * (five for the Diamond one) to kill, gone after 30 seconds; it drops Mithril, Mithril Powder (and Gemstone Powder for
 * the Diamond one), essence and coins. The <b>Powder Ghast</b> (1 in 5,000 Mithril, one every 10 minutes at most, only
 * where the Cliffside Veins, Rampart's Quarry, Divan's Gateway or the Forge are): it hovers where it appeared, melee
 * can't touch it, every arrow that hits pays 100-250 Mithril Powder, and it leaves after a minute without a hit.
 *
 * <p>Decided: Hypixel's "N hits" means the mob dies on its Nth hit whatever the damage, so those mobs hold their health
 * and the last hit sets it to 1 (the kill, XP and drops stay the killer's). The Ghast doesn't fly away from players
 * (it stays put), Luck of the Cave / Sky Mall / Tunnel Vision don't raise the chances, the Pure Mithril Gem drop and
 * Hypixel's Goblin Egg spawns are not here.
 */
public final class MinesGoblins {
    private static final String TAG = "skyrpg_mines_rare";
    /** Mobs out now: who they belong to, how many hits are left, when they leave, and what they are. */
    private static final Map<UUID, Rare> LIVE = new HashMap<>();
    private static long lastGhast;

    /** A spawned rare mob: its owner, hits taken and needed (0 = no limit), the time it leaves and what it is. */
    private static final class Rare {
        final UUID owner;
        final Kind kind;
        int hits;
        long leaves;

        Rare(UUID owner, Kind kind, long leaves) {
            this.owner = owner;
            this.kind = kind;
            this.leaves = leaves;
        }
    }

    private enum Kind {
        GOLDEN("Golden Goblin", 3, 99, 148, 33), DIAMOND("Diamond Goblin", 5, 109, 163, 36), GHAST("Powder Ghast", 100, 55, 82, 18);

        final String title;
        final int hitsNeeded;
        final int level;
        final double xp;
        final int coins;

        Kind(String title, int hitsNeeded, int level, double xp, int coins) {
            this.title = title;
            this.hitsNeeded = hitsNeeded;
            this.level = level;
            this.xp = xp;
            this.coins = coins;
        }
    }

    private MinesGoblins() {
    }

    static void init() {
        SkyMining.MINED.register((player, state, zone) -> mined(player, state, zone));
        ServerTickEvents.END_SERVER_TICK.register(MinesGoblins::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Rare rare = LIVE.get(entity.getUUID());
            if (rare == null) {
                return true;
            }
            if (!(source.getEntity() instanceof ServerPlayer player)) {
                return false;
            }
            if (rare.kind == Kind.GHAST) {
                return ghastHit(rare, entity, source, player);
            }
            if (!player.getUUID().equals(rare.owner)) {
                return false;
            }
            rare.hits++;
            if (rare.hits >= rare.kind.hitsNeeded) {
                entity.setHealth(1.0f);
                return true;
            }
            hurtEffects(entity);
            return false;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Rare rare = LIVE.remove(entity.getUUID());
            if (rare != null && rare.kind != Kind.GHAST && source.getEntity() instanceof ServerPlayer player) {
                drops(player, rare.kind);
            }
        });
        // Leftovers from before a restart.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && !LIVE.containsKey(entity.getUUID())) {
                level.getServer().execute(entity::discard);
            }
        });
    }

    private static void mined(ServerPlayer player, BlockState state, String zone) {
        if (!(player.level() instanceof ServerLevel level) || !level.dimension().equals(SkyMining.DWARVEN_MINES)) {
            return;
        }
        SkyMining.Info info = SkyMining.info(level, state);
        if (info == null || !info.ore()) {
            return;
        }
        if (level.getRandom().nextDouble() < 0.001) {
            spawnGoblin(level, player, level.getRandom().nextDouble() < 0.0333 ? Kind.DIAMOND : Kind.GOLDEN);
        }
        long now = level.getServer().getTickCount();
        SkyMining.Metal metal = SkyMining.metal(level, state);
        boolean veins = zone.equals("Cliffside Veins") || zone.equals("Rampart's Quarry") || zone.equals("Divan's Gateway") || zone.equals("The Forge");
        if (metal != null && metal.item().equals("MITHRIL_ORE") && veins && now - lastGhast > 10 * 60 * 20
                && level.getRandom().nextDouble() < 1.0 / 5000) {
            lastGhast = now;
            spawnGhast(level, player, zone);
        }
    }

    /** Spawns a goblin of this kind near the player (admin command and the 0.1% roll). */
    private static void spawnGoblin(ServerLevel level, ServerPlayer player, Kind kind) {
        net.minecraft.world.entity.monster.zombie.Zombie goblin = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
        if (goblin == null) {
            return;
        }
        Vec3 at = groundNear(level, player.blockPosition(), 6);
        goblin.snapTo(at.x, at.y, at.z, player.getYRot() + 180, 0);
        boolean diamond = kind == Kind.DIAMOND;
        Zones.dressAs(goblin, kind.title, kind.level, 1_000, 250, kind.xp);
        goblin.setItemSlot(EquipmentSlot.HEAD, new ItemStack(diamond ? Items.DIAMOND_HELMET : Items.GOLDEN_HELMET));
        goblin.setItemSlot(EquipmentSlot.CHEST, new ItemStack(diamond ? Items.DIAMOND_CHESTPLATE : Items.GOLDEN_CHESTPLATE));
        goblin.setDropChance(EquipmentSlot.HEAD, 0);
        goblin.setDropChance(EquipmentSlot.CHEST, 0);
        goblin.setBaby(false);
        goblin.addTag(TAG);
        goblin.addTag(Zones.FIGHTABLE_TAG);
        goblin.setPersistenceRequired();
        goblin.setCustomNameVisible(true);
        LIVE.put(goblin.getUUID(), new Rare(player.getUUID(), kind, level.getServer().getTickCount() + 30 * 20));
        level.addFreshEntity(goblin);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 1, at.z, 20, 0.4, 0.6, 0.4, 0.02);
        player.sendSystemMessage(Component.literal("A ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(diamond ? "Diamond Goblin" : "Golden Goblin").withStyle(diamond ? ChatFormatting.AQUA : ChatFormatting.GOLD))
                .append(Component.literal(" has spawned!").withStyle(ChatFormatting.GOLD)));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.6f);
    }

    static void spawnGhast(ServerLevel level, ServerPlayer player, String zone) {
        net.minecraft.world.entity.monster.Ghast ghast = EntityTypes.GHAST.create(level, EntitySpawnReason.EVENT);
        if (ghast == null) {
            return;
        }
        Vec3 at = player.position().add(player.getLookAngle().multiply(8, 0, 8)).add(0, 6, 0);
        ghast.snapTo(at.x, at.y, at.z, player.getYRot() + 180, 0);
        Zones.dressAs(ghast, Kind.GHAST.title, Kind.GHAST.level, 50, 1, Kind.GHAST.xp);
        ghast.setNoAi(true);
        ghast.addTag(TAG);
        ghast.addTag(Zones.FIGHTABLE_TAG);
        ghast.setPersistenceRequired();
        ghast.setCustomNameVisible(true);
        LIVE.put(ghast.getUUID(), new Rare(player.getUUID(), Kind.GHAST, level.getServer().getTickCount() + 60 * 20));
        level.addFreshEntity(ghast);
        for (ServerPlayer p : level.players()) {
            p.sendSystemMessage(Component.literal("The sound of pickaxes clashing against the rock has attracted the attention of the ")
                    .withStyle(ChatFormatting.GOLD).append(Component.literal("POWDER GHAST").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                    .append(Component.literal("!").withStyle(ChatFormatting.GOLD)));
            p.sendSystemMessage(Component.literal("Find the ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("Powder Ghast").withStyle(ChatFormatting.GOLD)).append(Component.literal(" near the ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(zone).withStyle(ChatFormatting.AQUA)).append(Component.literal("!").withStyle(ChatFormatting.YELLOW)));
        }
    }

    /** An arrow (or any thrown shot) pays 100-250 Mithril Powder; melee is refused; the hundredth hit kills it. */
    private static boolean ghastHit(Rare rare, LivingEntity ghast, net.minecraft.world.damagesource.DamageSource source, ServerPlayer player) {
        if (!(source.getDirectEntity() instanceof Projectile)) {
            player.sendOverlayMessage(Component.literal("The Powder Ghast is immune to melee damage! Try arrows!").withStyle(ChatFormatting.RED));
            return false;
        }
        int powder = 100 + player.getRandom().nextInt(151);
        Hotm.givePowder(player, Hotm.MITHRIL, powder);
        player.sendOverlayMessage(Component.literal("+" + powder + " " + Hotm.powderName(Hotm.MITHRIL)).withColor(Hotm.powderColor(Hotm.MITHRIL)));
        rare.hits++;
        rare.leaves = ((ServerLevel) ghast.level()).getServer().getTickCount() + 60 * 20;
        if (rare.hits >= rare.kind.hitsNeeded) {
            ghast.setHealth(1.0f);
            return true;
        }
        hurtEffects(ghast);
        return false;
    }

    private static void hurtEffects(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
            level.playSound(null, entity.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
        }
    }

    private static void drops(ServerPlayer player, Kind kind) {
        boolean diamond = kind == Kind.DIAMOND;
        give(player, "MITHRIL_ORE", diamond ? 20 : 5);
        Hotm.givePowder(player, Hotm.MITHRIL, diamond ? 1_000 : 500);
        Notice.Panel panel = Notice.of(Notice.Kind.DROP).tag(kind.title.toUpperCase(java.util.Locale.ROOT)).panel();
        panel.line(Component.literal("+" + (diamond ? 1_000 : 500) + " " + Hotm.powderName(Hotm.MITHRIL)).withColor(Hotm.powderColor(Hotm.MITHRIL)));
        if (diamond) {
            Hotm.givePowder(player, Hotm.GEMSTONE, 1_000);
            panel.line(Component.literal("+1,000 " + Hotm.powderName(Hotm.GEMSTONE)).withColor(Hotm.powderColor(Hotm.GEMSTONE)));
            Essence.give(player, "DIAMOND", 20);
            panel.line(Component.literal("+20 Diamond Essence").withStyle(ChatFormatting.AQUA));
        } else {
            int essence = 2 + (player.getRandom().nextDouble() < 0.3 ? 1 : 0);
            Essence.give(player, "GOLD", essence);
            panel.line(Component.literal("+" + essence + " Gold Essence").withStyle(ChatFormatting.GOLD));
        }
        panel.line(Component.literal("+" + (diamond ? 20 : 5) + " Mithril").withStyle(ChatFormatting.GREEN));
        Wallet.give(player, kind.coins);
        panel.send(player, Component.literal(kind.title + " slain!").withStyle(diamond ? ChatFormatting.AQUA : ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private static void give(ServerPlayer player, String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def != null) {
            ItemGains.give(player, SkyItems.create(def, count), ItemGains.Source.COMBAT);
        }
    }

    private static void tick(MinecraftServer server) {
        if (LIVE.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        boolean flee = now % 10 == 0;
        LIVE.entrySet().removeIf(entry -> {
            Rare rare = entry.getValue();
            Mob mob = null;
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(entry.getKey()) instanceof Mob found) {
                    mob = found;
                    break;
                }
            }
            if (mob == null || !mob.isAlive()) {
                return true;
            }
            if (now >= rare.leaves) {
                if (mob.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 1, mob.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                }
                mob.discard();
                return true;
            }
            // Timid: run from the player it belongs to.
            if (flee && rare.kind != Kind.GHAST && mob.level() instanceof ServerLevel level && level.getPlayerByUUID(rare.owner) != null) {
                Vec3 away = mob.position().subtract(level.getPlayerByUUID(rare.owner).position()).multiply(1, 0, 1);
                if (away.lengthSqr() < 400) {
                    Vec3 to = mob.position().add(away.normalize().scale(8));
                    mob.setTarget(null);
                    mob.getNavigation().moveTo(to.x, to.y, to.z, 1.5);
                }
            }
            return false;
        });
    }

    /** A standing spot near a block: a few tries for air with a floor, else the block itself. */
    static Vec3 groundNear(ServerLevel level, BlockPos center, int radius) {
        for (int i = 0; i < 24; i++) {
            BlockPos pos = center.offset(level.getRandom().nextInt(radius * 2 + 1) - radius, level.getRandom().nextInt(5) - 2,
                    level.getRandom().nextInt(radius * 2 + 1) - radius);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && level.getBlockState(pos.below()).isSolid()) {
                return Vec3.atBottomCenterOf(pos);
            }
        }
        return Vec3.atBottomCenterOf(center.above());
    }

    /** Admin: spawn one now ("golden", "diamond" or "ghast"). */
    static boolean spawnFor(ServerPlayer player, String what) {
        ServerLevel level = (ServerLevel) player.level();
        switch (what) {
            case "golden" -> spawnGoblin(level, player, Kind.GOLDEN);
            case "diamond" -> spawnGoblin(level, player, Kind.DIAMOND);
            case "ghast" -> spawnGhast(level, player, "Cliffside Veins");
            default -> {
                return false;
            }
        }
        return true;
    }
}
