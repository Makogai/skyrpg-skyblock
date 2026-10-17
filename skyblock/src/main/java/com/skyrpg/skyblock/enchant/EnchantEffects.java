package com.skyrpg.skyblock.enchant;

import com.skyrpg.combat.MobLevels;
import com.skyrpg.core.stats.Crits;
import com.skyrpg.core.stats.DamageBonus;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.economy.Coins;
import com.skyrpg.economy.Purse;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.enchant.Enchants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.IntToDoubleFunction;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * What Hypixel's enchantments do. Each level's number comes from its book's lore ({@link Enchants.Enchant#value}); a few
 * rules whose lore has no plain number are written here (Efficiency's Mining Speed, Life Steal's share of your health).
 * <ul>
 *   <li>Stats: armour enchants count in their slot (Protection, Growth, Big Brain...), weapon and tool enchants in the
 *       main hand (Critical, Vicious, Efficiency, Harvesting, Turbo-crops...).</li>
 *   <li>Damage: Sharpness, Power, Smite, Bane of Arthropods, Ender Slayer, Cubism, Impaling, Smoldering, Gravity, Dragon
 *       Hunter, First Strike, Triple Strike, Execute, Prosecute, Giant Killer, One For All and Swarm add their percent to
 *       the hit's additive bonus ({@link DamageBonus}).</li>
 *   <li>On a hit: Life Steal, Syphon, Mana Steal, Fire Aspect, Knockback, Thunderlord, Cleave. On a kill: Scavenger,
 *       Vampirism.</li>
 * </ul>
 * The rest show on the item but don't do anything yet.
 */
public final class EnchantEffects {
    /** Armour enchants that give a stat while worn: stat, and its amount at a level. */
    private static final Map<String, Bonus> ARMOR = Map.of(
            "protection", new Bonus(StatTypes.DEFENSE, null),
            "growth", new Bonus(StatTypes.HEALTH, null),
            "true_protection", new Bonus(StatTypes.TRUE_DEFENSE, null),
            "big_brain", new Bonus(StatTypes.INTELLIGENCE, level -> 5 * level),
            "smarty_pants", new Bonus(StatTypes.INTELLIGENCE, level -> 5 * level),
            "sugar_rush", new Bonus(StatTypes.SPEED, level -> 2 * level),
            "rejuvenate", new Bonus(StatTypes.HEALTH_REGEN, level -> 2 * level));
    /** Weapon and tool enchants that give a stat while held. */
    private static final Map<String, Bonus> HELD = Map.ofEntries(
            Map.entry("critical", new Bonus(StatTypes.CRIT_DAMAGE, null)),
            Map.entry("vicious", new Bonus(StatTypes.FEROCITY, level -> level)),
            Map.entry("divine_gift", new Bonus(StatTypes.MAGIC_FIND, level -> 2 * level)),
            Map.entry("efficiency", new Bonus(StatTypes.MINING_SPEED, level -> 10 + 20 * level)),
            Map.entry("harvesting", new Bonus(StatTypes.FARMING_FORTUNE, level -> 12.5 * level)),
            Map.entry("cultivating", new Bonus(StatTypes.FARMING_FORTUNE, level -> 2 * level)),
            Map.entry("compact", new Bonus(StatTypes.MINING_WISDOM, level -> level)),
            Map.entry("angler", new Bonus(StatTypes.SEA_CREATURE_CHANCE, level -> level)),
            Map.entry("luck_of_the_sea", new Bonus(StatTypes.TREASURE_CHANCE, level -> 0.5 * level)),
            Map.entry("expertise", new Bonus(StatTypes.FISHING_WISDOM, level -> 2 * level)),
            Map.entry("turbo_wheat", new Bonus(StatTypes.get("wheat_fortune"), level -> 5 * level)),
            Map.entry("turbo_carrot", new Bonus(StatTypes.get("carrot_fortune"), level -> 5 * level)),
            Map.entry("turbo_potato", new Bonus(StatTypes.get("potato_fortune"), level -> 5 * level)),
            Map.entry("turbo_pumpkin", new Bonus(StatTypes.get("pumpkin_fortune"), level -> 5 * level)),
            Map.entry("turbo_melon", new Bonus(StatTypes.get("melon_fortune"), level -> 5 * level)),
            Map.entry("turbo_mushrooms", new Bonus(StatTypes.get("mushroom_fortune"), level -> 5 * level)),
            Map.entry("turbo_cacti", new Bonus(StatTypes.get("cactus_fortune"), level -> 5 * level)),
            Map.entry("turbo_cane", new Bonus(StatTypes.get("sugar_cane_fortune"), level -> 5 * level)),
            Map.entry("turbo_warts", new Bonus(StatTypes.get("nether_stalk_fortune"), level -> 5 * level)),
            Map.entry("turbo_cocoa", new Bonus(StatTypes.get("cocoa_beans_fortune"), level -> 5 * level)));

    private static final Set<EntityType<?>> ENDER = Set.of(EntityTypes.ENDERMAN, EntityTypes.ENDERMITE, EntityTypes.ENDER_DRAGON);
    private static final Set<EntityType<?>> CUBIC = Set.of(EntityTypes.CREEPER, EntityTypes.SLIME, EntityTypes.MAGMA_CUBE);
    private static final Set<EntityType<?>> INFERNAL = Set.of(EntityTypes.BLAZE, EntityTypes.GHAST, EntityTypes.MAGMA_CUBE,
            EntityTypes.WITHER_SKELETON, EntityTypes.PIGLIN, EntityTypes.HOGLIN, EntityTypes.ZOMBIFIED_PIGLIN);
    private static final Set<EntityType<?>> AIRBORNE = Set.of(EntityTypes.BAT, EntityTypes.PHANTOM, EntityTypes.GHAST, EntityTypes.BLAZE,
            EntityTypes.VEX, EntityTypes.BEE, EntityTypes.PARROT, EntityTypes.ALLAY, EntityTypes.BREEZE);

    /** Hits each player has landed on each mob (First Strike, Triple Strike, Thunderlord's every third hit). */
    private static final Map<LivingEntity, Map<UUID, Integer>> HITS = new WeakHashMap<>();

    private record Bonus(StatType stat, IntToDoubleFunction amount) {
    }

    private EnchantEffects() {
    }

    public static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Enchantments";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                for (EquipmentSlot slot : SkyItems.ARMOR_SLOTS) {
                    ItemStack stack = player.getItemBySlot(slot);
                    ItemDefinition def = SkyItems.definition(stack);
                    if (def != null && SkyItems.armorSlot(def.item()) == slot) {
                        add(stack, ARMOR, out);
                    }
                }
                add(player.getMainHandItem(), HELD, out);
            }
        });
        DamageBonus.register(EnchantEffects::damagePercent);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (source.getEntity() instanceof ServerPlayer player && entity != player && taken > 0 && !(entity instanceof Player)) {
                onHit(player, entity, source, taken);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer player && !(entity instanceof Player)) {
                onKill(player, entity);
            }
            HITS.remove(entity);
        });
    }

    private static void add(ItemStack stack, Map<String, Bonus> table, Stats.Contributor out) {
        ItemMods.enchantments(stack).forEach((id, level) -> {
            Bonus bonus = table.get(id);
            if (bonus != null && bonus.stat() != null) {
                out.add(bonus.stat(), bonus.amount() != null ? bonus.amount().applyAsDouble(level) : value(id, level));
            }
        });
    }

    private static double value(String id, int level) {
        Enchants.Enchant enchant = Enchants.get(id);
        return enchant == null ? 0 : enchant.value(level);
    }

    /** The weapon the hit came from: the held item for melee, the held bow for arrows. */
    private static ItemStack weapon(ServerPlayer player) {
        return player.getMainHandItem();
    }

    private static double damagePercent(ServerPlayer player, LivingEntity target, DamageSource source) {
        Map<String, Integer> enchants = ItemMods.enchantments(weapon(player));
        if (enchants.isEmpty()) {
            return 0;
        }
        boolean arrow = source.getDirectEntity() instanceof AbstractArrow;
        double percent = 0;
        int hits = HITS.getOrDefault(target, Map.of()).getOrDefault(player.getUUID(), 0);
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            String id = entry.getKey();
            int level = entry.getValue();
            percent += switch (id) {
                case "sharpness" -> arrow ? 0 : value(id, level);
                case "power" -> arrow ? value(id, level) : 0;
                case "smite" -> tagged(target, EntityTypeTags.UNDEAD) ? value(id, level) : 0;
                case "bane_of_arthropods" -> tagged(target, EntityTypeTags.ARTHROPOD) ? value(id, level) : 0;
                case "ender_slayer" -> ENDER.contains(target.getType()) ? value(id, level) : 0;
                case "cubism" -> CUBIC.contains(target.getType()) ? value(id, level) : 0;
                case "impaling" -> tagged(target, EntityTypeTags.AQUATIC) ? value(id, level) : 0;
                case "smoldering" -> INFERNAL.contains(target.getType()) ? value(id, level) : 0;
                case "gravity" -> AIRBORNE.contains(target.getType()) ? value(id, level) : 0;
                case "dragon_hunter" -> target.getType() == EntityTypes.ENDER_DRAGON ? value(id, level) : 0;
                case "first_strike" -> hits == 0 && !arrow ? 25 * level : 0;
                case "triple_strike" -> hits < 3 && !arrow ? 10 * level : 0;
                case "execute" -> value(id, level) * missingPercent(target);
                case "prosecute" -> value(id, level) * (100 - missingPercent(target));
                case "giant_killer" -> giantKiller(player, target, id, level);
                case "ultimate_one_for_all" -> 500;
                case "ultimate_swarm" -> value(id, level) * Math.min(16, nearbyMobs(player, 10) - 1);
                default -> 0;
            };
        }
        return percent;
    }

    private static boolean tagged(LivingEntity target, net.minecraft.tags.TagKey<EntityType<?>> tag) {
        return target.getType().builtInRegistryHolder().is(tag);
    }

    private static double missingPercent(LivingEntity target) {
        return target.getMaxHealth() <= 0 ? 0 : (1 - target.getHealth() / target.getMaxHealth()) * 100;
    }

    /** Giant Killer: its percent for each percent of health the target has over you, up to its cap (the second number). */
    private static double giantKiller(ServerPlayer player, LivingEntity target, String id, int level) {
        double over = (target.getMaxHealth() - player.getMaxHealth()) / Math.max(1, player.getMaxHealth()) * 100;
        if (over <= 0) {
            return 0;
        }
        return Math.min(value(id, level) * over, 5.0 * level + 5);
    }

    private static int nearbyMobs(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius), Mob::isAlive).size();
    }

    private static void onHit(ServerPlayer player, LivingEntity target, DamageSource source, float taken) {
        Map<UUID, Integer> byPlayer = HITS.computeIfAbsent(target, t -> new HashMap<>());
        int hits = byPlayer.merge(player.getUUID(), 1, Integer::sum);
        Map<String, Integer> enchants = ItemMods.enchantments(weapon(player));
        if (enchants.isEmpty()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        float maxHealth = player.getMaxHealth();
        int lifeSteal = enchants.getOrDefault("life_steal", 0);
        if (lifeSteal > 0) {
            player.heal(maxHealth * 0.005f * lifeSteal);
        }
        int syphon = enchants.getOrDefault("syphon", 0);
        if (syphon > 0 && Crits.wasCrit()) {
            double critDamage = Math.min(1000, Math.max(0, Stats.get(player, StatTypes.CRIT_DAMAGE)));
            player.heal((float) (maxHealth * 0.001 * syphon * critDamage / 100));
        }
        int manaSteal = enchants.getOrDefault("mana_steal", 0);
        if (manaSteal > 0 && Stats.available()) {
            double max = Math.max(0, Stats.get(player, StatTypes.INTELLIGENCE));
            double room = Math.max(0, max - Stats.mana(player));
            Stats.spendMana(player, -Math.min(room, max * 0.0025 * manaSteal));
        }
        int fire = enchants.getOrDefault("fire_aspect", 0);
        if (fire > 0) {
            target.igniteForSeconds(3 + fire);
        }
        int knockback = enchants.getOrDefault("knockback", 0);
        if (knockback > 0) {
            target.knockback(0.4 * knockback, player.getX() - target.getX(), player.getZ() - target.getZ(), source, taken);
        }
        int thunderlord = enchants.getOrDefault("thunderlord", 0);
        if (thunderlord > 0 && hits % 3 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.8), target.getZ(), 30, 0.3, 0.8, 0.3, 0.2);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, player.getSoundSource(), 0.6f, 1.4f);
            target.hurtServer(level, level.damageSources().magic(), (float) (taken * value("thunderlord", thunderlord) / 100));
        }
        int cleave = enchants.getOrDefault("cleave", 0);
        if (cleave > 0 && !(source.getDirectEntity() instanceof AbstractArrow)) {
            double radius = 3.5 + 0.5 * cleave;
            List<Mob> around = level.getEntitiesOfClass(Mob.class, new AABB(target.blockPosition()).inflate(radius),
                    mob -> mob != target && mob.isAlive());
            for (Mob mob : around) {
                mob.hurtServer(level, level.damageSources().magic(), taken * 0.05f * cleave);
            }
        }
    }

    private static void onKill(ServerPlayer player, LivingEntity entity) {
        Map<String, Integer> enchants = ItemMods.enchantments(weapon(player));
        int scavenger = enchants.getOrDefault("scavenger", 0);
        if (scavenger > 0) {
            MobLevels.MobInfo info = MobLevels.info(entity);
            long coins = Math.round(0.3 * scavenger * (info == null ? 1 : Math.max(1, info.level())));
            if (coins > 0) {
                Purse.add(player, coins);
                player.sendOverlayMessage(Component.literal("Scavenger: +").withStyle(ChatFormatting.GOLD).append(Coins.text(coins)));
            }
        }
        int vampirism = enchants.getOrDefault("vampirism", 0);
        if (vampirism > 0) {
            player.heal((player.getMaxHealth() - player.getHealth()) * 0.01f * vampirism);
        }
    }
}
