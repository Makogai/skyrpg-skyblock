package com.skyrpg.skyblock.enchant;

import com.skyrpg.items.ItemMods;
import com.skyrpg.items.enchant.Enchants;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * The enchantments that change what a kill or a broken block gives:
 * <ul>
 *   <li>Looting (swords, rods) and Chance (bows): the mob's loot table is rolled once more at their percent (Hypixel's
 *   drop chance times 1 + 15% a level, so 75% is a three in four chance of a second roll).</li>
 *   <li>Luck: mobs drop the armour and weapon they carry (vanilla's 8.5% base) at that many percent points more.</li>
 *   <li>Experience: mobs and ores give double experience at its chance.</li>
 * </ul>
 */
final class EnchantDrops {
    /** Vanilla's chance that a mob drops a piece of its equipment. */
    private static final double BASE_EQUIPMENT_DROP = 0.085;

    /** A kill or block whose experience orbs this tick should be doubled. */
    private record Doubling(long tick, net.minecraft.world.phys.Vec3 at) {
    }

    private static final Map<UUID, Doubling> DOUBLING = new HashMap<>();
    private static final ThreadLocal<Boolean> AWARDING = ThreadLocal.withInitial(() -> false);

    private EnchantDrops() {
    }

    static void init() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            ServerPlayer player = killer(source);
            if (player != null && entity instanceof Mob && chance(weapon(player), "experience")) {
                DOUBLING.put(player.getUUID(), new Doubling(entity.level().getGameTime(), entity.position()));
            }
            return true;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer miner && !state.isAir() && chance(miner.getMainHandItem(), "experience")) {
                DOUBLING.put(miner.getUUID(), new Doubling(level.getGameTime(), net.minecraft.world.phys.Vec3.atCenterOf(pos)));
            }
            return true;
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!(entity instanceof ExperienceOrb orb) || DOUBLING.isEmpty() || AWARDING.get()) {
                return;
            }
            for (var entry : DOUBLING.entrySet()) {
                Doubling doubling = entry.getValue();
                if (doubling.tick() == level.getGameTime() && doubling.at().distanceToSqr(orb.position()) < 36) {
                    DOUBLING.remove(entry.getKey());
                    AWARDING.set(true);
                    try {
                        ExperienceOrb.award(level, orb.position(), orb.getValue());
                    } finally {
                        AWARDING.set(false);
                    }
                    return;
                }
            }
            DOUBLING.values().removeIf(doubling -> doubling.tick() < level.getGameTime() - 2);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            ServerPlayer player = killer(source);
            if (player == null || !(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
                return;
            }
            boolean arrow = source.getDirectEntity() instanceof Projectile;
            ItemStack weapon = weapon(player);
            // Looting on melee, Chance on arrows: another roll of the mob's loot.
            int looting = ItemMods.enchantment(weapon, arrow ? "chance" : "looting");
            Enchants.Enchant enchant = Enchants.get(arrow ? "chance" : "looting");
            if (looting > 0 && enchant != null && player.getRandom().nextDouble() * 100 < enchant.value(looting)) {
                rollAgain(level, mob, source, player);
            }
            int luck = ItemMods.enchantment(weapon, "luck");
            if (luck > 0 && Enchants.get("luck") != null) {
                double extra = Enchants.get("luck").value(luck) / 100;
                // What it still carries wasn't dropped: each piece gets another chance.
                for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                    ItemStack carried = mob.getItemBySlot(slot);
                    if (!carried.isEmpty() && mob.getDropChances().byEquipment(slot) > 0
                            && player.getRandom().nextDouble() < extra * (1 - BASE_EQUIPMENT_DROP)) {
                        mob.spawnAtLocation(level, carried.copy());
                        mob.setItemSlot(slot, ItemStack.EMPTY);
                    }
                }
            }
        });
    }

    private static ServerPlayer killer(DamageSource source) {
        return source.getEntity() instanceof ServerPlayer player ? player
                : source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner ? owner : null;
    }

    private static ItemStack weapon(ServerPlayer player) {
        return player.getMainHandItem();
    }

    /** Whether this item's enchant (its lore number is a percent chance) triggers now. */
    private static boolean chance(ItemStack stack, String id) {
        int level = ItemMods.enchantment(stack, id);
        Enchants.Enchant enchant = Enchants.get(id);
        return level > 0 && enchant != null && Math.random() * 100 < enchant.value(level);
    }

    /** The mob's loot table rolled one more time, dropped where it died. */
    private static void rollAgain(ServerLevel level, Mob mob, DamageSource source, ServerPlayer player) {
        var key = mob.getLootTable();
        java.util.Set<String> tags = mob.entityTags();
        // Sea creatures, Glacite Maniacs and dungeon mobs drop only Hypixel's drops, never the vanilla table.
        if (key.isEmpty() || tags.contains(com.skyrpg.skyblock.fishing.SkySeaCreatures.TAG)
                || tags.contains(com.skyrpg.skyblock.glacite.Mineshafts.MANIAC) || tags.contains(com.skyrpg.skyblock.dungeon.DungeonMobs.TAG)) {
            return;
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key.get());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.ENTITY);
        for (ItemStack stack : table.getRandomItems(params)) {
            mob.spawnAtLocation(level, stack);
        }
    }
}
