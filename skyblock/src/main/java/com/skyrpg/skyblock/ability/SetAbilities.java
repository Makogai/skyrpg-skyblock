package com.skyrpg.skyblock.ability;

import com.skyrpg.combat.MobLevels;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SetBonuses;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.progress.collections.Collections;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.crimson.Kuudra;
import com.skyrpg.skyblock.dungeon.Catacombs;
import com.skyrpg.skyblock.fairy.FairySouls;
import com.skyrpg.skyblock.island.Islands;
import com.skyrpg.skyblock.mining.SkyMining;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's armour set bonuses (the lines {@code import_items.py} reads from NEU's lore into each item's
 * {@code set_bonuses}; {@link SetBonuses} counts what's worn and the tooltip shows "(Active)" once the set is whole).
 * Pieces are counted by the item's id ({@code young_dragon_boots}, its {@code starred_} form too) for the sets whose
 * bonus depends on the exact set, and by the bonus's name where several items share one (belts, necklaces, cloaks).
 * <ul>
 *   <li>Dragons: Young Blood (+70 Speed above half health), Superior Blood (+5% to all stats, +50% Aspect of the Dragons
 *       damage), Strong Blood (+75 damage with the Aspect of the End), Wise Blood (abilities cost 2/3), Old Blood (more
 *       from Growth, Protection, Sugar Rush, True Protection), Protective Blood (+1% of each piece's Defense per missing
 *       percent of health), Unstable Blood (3,000-damage lightning on mobs within 8 blocks every 15s), Holy Blood (+75
 *       Health Regen for you and players within 10 blocks).</li>
 *   <li>Stats: Speedster, Miner Outfit, Glacite (Mining Speed by Mining level), Goblin (Intelligence into Mining
 *       Speed), Dwarven Mines and Crystal Hollows sets, Heavy, Mastiff, Adaptive (+2% per 5 Catacombs levels),
 *       Crystal (by light), the Lord sets (by time in a dungeon), Shadow Assassin (kills in a dungeon), Final
 *       Destination (sneaking in combat), Fairy, Emerald, Angler, Star Fisher, Arachne, Lord's Blessing, Berserker.</li>
 *   <li>Damage: Skeleton Master and Soldier (+25% arrows each), Tarantula and Primordial (Octodexterity), Thunder's Static
 *       Charge, Tuxedos (Dashing), Reaper and Revenant, Cactus (Deflect), Mastiff, the sea creature sets, Molten Core,
 *       Bouncing Arrow, Zombie Knight, Blaze and Frozen Blaze (Blazing Aura), Witherborn's wither.</li>
 *   <li>Effects: Mushroom's Night Vision, Rabbit's Springsneak, Ember's fire immunity, Golem's Absorption, Zombie's
 *       Projectile Absorption, Mercenary's Death Tax, Reaper's Enrage, the Shadow Assassin's piece bonuses on a
 *       teleport, the nearby-wearers sets (Armor of the Pack, Battalion, Shoal).</li>
 * </ul>
 * Decided where the text is loose: stats that are a share of your total (Superior, Mastiff, Smart Miner, Heavy) are
 * worked out from the last recompute with this source's own part taken out, so they don't feed on themselves; a
 * Tiered Bonus is on with two or more pieces; a teleport is a jump of 5 blocks or more in one tick; "in combat" is
 * the 5 seconds after you hit or are hit; Intelligence-scaled damage uses {@link HypixelAbilities#abilityDamage}.
 * Skipped: counters kept on the pieces (Bulwarks, Absorb, Training, Skeletor), Soulflow, Greenhouse drops,
 * necromancy, candy, and the Speed cap.
 */
final class SetAbilities {
    private static final String SEA_CREATURE_TAG = "skyrpg_sea_creature";
    private static final String KUUDRA_TAG = "skyrpg_kuudra";
    private static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};
    private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** The sets that have fewer than four pieces. */
    private static final Map<String, Integer> SMALL_SETS = Map.of("reaper", 3, "revenant", 3, "zombie", 3, "cheap_tuxedo", 3,
            "fancy_tuxedo", 3, "elegant_tuxedo", 3);
    private static final StatType[] ALL_STATS = {StatTypes.HEALTH, StatTypes.DEFENSE, StatTypes.STRENGTH, StatTypes.INTELLIGENCE,
            StatTypes.CRIT_CHANCE, StatTypes.CRIT_DAMAGE, StatTypes.SPEED, StatTypes.ATTACK_SPEED, StatTypes.ABILITY_DAMAGE,
            StatTypes.TRUE_DEFENSE, StatTypes.FEROCITY, StatTypes.MAGIC_FIND};

    /** What a player has on this tick: each bonus's piece count, and the armour's item ids by slot. */
    private record Gear(long tick, Map<String, Integer> bonuses, String[] armour) {
        /** Pieces of an armour set worn: {@code young_dragon} matches {@code young_dragon_boots} and {@code starred_young_dragon_boots}. */
        int set(String base) {
            int count = 0;
            for (String path : armour) {
                if (path.isEmpty()) {
                    continue;
                }
                String bare = path.startsWith("starred_") ? path.substring(8) : path;
                for (String piece : PIECES) {
                    if (bare.equals(base + "_" + piece)) {
                        count++;
                        break;
                    }
                }
            }
            return count;
        }

        boolean full(String base) {
            return set(base) >= SMALL_SETS.getOrDefault(base, 4);
        }

        int bonus(String key) {
            return bonuses.getOrDefault(key, 0);
        }

        /** A Tiered Bonus works from two pieces. */
        boolean tiered(String key) {
            return bonus(key) >= 2;
        }
    }

    /** What a player is up to between ticks. */
    private static final class State {
        long dungeonTicks;
        int dungeonKills;
        Vec3 lastPos;
        long lastTeleport = -1000;
        long salubriousAt;
        long sinewUntil;
        long pursuitUntil;
        long bloodrushUntil;
        long enrageUntil;
        long enrageReadyAt;
        long lastCombat = -1000;
        long nextLightning;
        long nextWither;
        long nextCharge;
        int charges;
        int melee;
        boolean sneaking;
        boolean wasInDungeon;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<UUID, Gear> GEAR = new HashMap<>();
    /** What this source added to each stat last time, so a share of the total can leave it out. */
    private static final Map<UUID, Map<StatType, Double>> MINE = new HashMap<>();

    private SetAbilities() {
    }

    static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Set bonuses";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                Map<StatType, Double> mine = new HashMap<>();
                stats(player, (stat, value) -> {
                    mine.merge(stat, value, Double::sum);
                    out.add(stat, value);
                });
                MINE.put(player.getUUID(), mine);
            }
        });
        IncomingDamage.registerHit(SetAbilities::damageFactor);
        Abilities.manaCostFactor(player -> gear(player).full("wise_dragon") ? 2.0 / 3 : 1);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(SetAbilities::afterDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register(SetAbilities::killed);
        ServerTickEvents.END_SERVER_TICK.register(SetAbilities::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            STATES.remove(handler.player.getUUID());
            GEAR.remove(handler.player.getUUID());
            MINE.remove(handler.player.getUUID());
        });
    }

    // ---------------------------------------------------------------- what's worn

    private static Gear gear(ServerPlayer player) {
        long tick = player.level().getServer().getTickCount();
        Gear cached = GEAR.get(player.getUUID());
        if (cached != null && cached.tick() == tick) {
            return cached;
        }
        String[] armour = new String[4];
        for (int i = 0; i < 4; i++) {
            ItemDefinition def = SkyItems.definition(player.getItemBySlot(WORN[i]));
            armour[i] = def != null && SkyItems.armorSlot(def.item()) == WORN[i] ? def.id().getPath() : "";
        }
        Gear gear = new Gear(tick, SetBonuses.worn(player), armour);
        GEAR.put(player.getUUID(), gear);
        return gear;
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    /** A stat's total without this source's part of it. */
    private static double without(ServerPlayer player, StatType stat) {
        Map<StatType, Double> mine = MINE.get(player.getUUID());
        return Stats.get(player, stat) - (mine == null ? 0 : mine.getOrDefault(stat, 0.0));
    }

    private static boolean holding(ServerPlayer player, String skyblockId) {
        ItemDefinition def = SkyItems.definition(player.getMainHandItem());
        return def != null && skyblockId.equals(def.skyblockId());
    }

    private static boolean inCombat(ServerPlayer player) {
        return player.level().getServer().getTickCount() - state(player).lastCombat < 100;
    }

    private static boolean in(ServerPlayer player, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        return player.level().dimension().equals(dimension);
    }

    /** The other players near this one (this one too), on the same level. */
    private static List<ServerPlayer> near(ServerPlayer player, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other.level() == player.level() && other.distanceToSqr(player) <= radius * radius && !other.isSpectator()) {
                out.add(other);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- stats

    private static void stats(ServerPlayer player, Stats.Contributor out) {
        Gear gear = gear(player);
        if (gear.bonuses().isEmpty()) {
            return;
        }
        State state = state(player);
        long minutes = state.dungeonTicks / 1200;

        // Dragons
        if (gear.full("young_dragon") && player.getHealth() > player.getMaxHealth() * 0.5) {
            out.add(StatTypes.SPEED, 70);
        }
        if (gear.full("superior_dragon")) {
            for (StatType stat : ALL_STATS) {
                out.add(stat, 0.05 * without(player, stat));
            }
        }
        if (gear.full("old_dragon")) {
            // Growth 15 to 25 Health a level, Protection 3 to 5 Defense, Sugar Rush 2 to 3 Speed, True Protection 5 to 8.
            for (ItemStack stack : armour(player)) {
                Map<String, Integer> enchants = ItemMods.enchantments(stack);
                out.add(StatTypes.HEALTH, 10 * enchants.getOrDefault("growth", 0));
                out.add(StatTypes.DEFENSE, 2 * enchants.getOrDefault("protection", 0));
                out.add(StatTypes.SPEED, enchants.getOrDefault("sugar_rush", 0));
                out.add(StatTypes.TRUE_DEFENSE, 3 * enchants.getOrDefault("true_protection", 0));
            }
        }
        if (gear.full("protector_dragon")) {
            double missing = 100 * (1 - player.getHealth() / player.getMaxHealth());
            double defense = 0;
            for (ItemStack stack : armour(player)) {
                ItemDefinition def = SkyItems.definition(stack);
                defense += def == null ? 0 : ItemMods.stats(stack, def).getOrDefault(StatTypes.DEFENSE, 0.0);
            }
            out.add(StatTypes.DEFENSE, defense * missing / 100);
        }
        if (gear.full("holy_dragon")) {
            // Holy Blood gives +75 to you and the players near you who wear it, once however many wear it near you.
            out.add(StatTypes.HEALTH_REGEN, 75);
        } else {
            for (ServerPlayer other : near(player, 10)) {
                if (other != player && gear(other).full("holy_dragon")) {
                    out.add(StatTypes.HEALTH_REGEN, 75);
                    break;
                }
            }
        }

        // Mining
        if (gear.full("speedster")) {
            out.add(StatTypes.SPEED, 20);
        }
        if (gear.full("miner_outfit")) {
            out.add(StatTypes.MINING_SPEED, 40);
        }
        if (gear.full("glacite")) {
            out.add(StatTypes.MINING_SPEED, 2 * SkillLevels.level(player, SkillLevels.MINING));
        }
        if (gear.full("goblin")) {
            double intelligence = Math.max(0, without(player, StatTypes.INTELLIGENCE));
            out.add(StatTypes.INTELLIGENCE, -intelligence);
            out.add(StatTypes.MINING_SPEED, Math.floor(intelligence / 15));
        }
        if (gear.bonus("dwarf_wannabe") >= 4 && in(player, SkyMining.DWARVEN_MINES)) {
            out.add(StatTypes.MINING_SPEED, 30);
            out.add(StatTypes.MINING_FORTUNE, 5);
        }
        if (gear.bonus("true_dwarf") >= 4 && in(player, SkyMining.DWARVEN_MINES)) {
            out.add(StatTypes.MINING_SPEED, 50);
            out.add(StatTypes.MINING_FORTUNE, 40);
        }
        if (gear.bonus("gemstone_gatherer") >= 4 && in(player, SkyMining.CRYSTAL_HOLLOWS)) {
            out.add(StatTypes.MINING_FORTUNE, 25);
        }

        // Combat
        if (gear.full("heavy") || gear.full("super_heavy")) {
            out.add(StatTypes.SPEED, Math.floor(Stats.get(player, StatTypes.DEFENSE) / 50));
        }
        if (gear.full("mastiff")) {
            double defense = without(player, StatTypes.DEFENSE);
            double crit = without(player, StatTypes.CRIT_DAMAGE);
            out.add(StatTypes.HEALTH, without(player, StatTypes.HEALTH) * Math.min(40, Math.floor(crit / 25)) / 100);
            out.add(StatTypes.CRIT_DAMAGE, Math.min(300, Math.max(0, defense)) / 2);
            out.add(StatTypes.DEFENSE, -Math.max(0, defense - 300));
        }
        if (gear.bonus("efficient_training") > 0) {
            // Every 5 Catacombs levels each such piece gains +2% of its stats.
            double bonus = 0.02 * (com.skyrpg.skyblock.dungeon.DungeonXp.level(player, com.skyrpg.skyblock.dungeon.DungeonXp.CATACOMBS) / 5);
            for (ItemStack stack : armour(player)) {
                ItemDefinition def = SkyItems.definition(stack);
                if (def != null && hasBonus(def, "efficient_training")) {
                    ItemMods.stats(stack, def).forEach((stat, value) -> out.add(stat, value * bonus));
                }
            }
        }
        if (gear.full("crystal")) {
            // The armour's stats go from nothing in the dark to double in full light.
            double factor = player.level().getMaxLocalRawBrightness(player.blockPosition()) / 15.0 * 2;
            for (ItemStack stack : armour(player)) {
                ItemDefinition def = SkyItems.definition(stack);
                if (def != null && hasBonus(def, "refraction")) {
                    ItemMods.stats(stack, def).forEach((stat, value) -> out.add(stat, value * (factor - 1)));
                }
            }
        }
        if (gear.full("skeleton_lord")) {
            out.add(StatTypes.STRENGTH, 5 * minutes);
            out.add(StatTypes.CRIT_DAMAGE, 10 * minutes);
        }
        if (gear.full("zombie_lord")) {
            out.add(StatTypes.STRENGTH, 10 * minutes);
            out.add(StatTypes.DEFENSE, 10 * minutes);
        }
        if (gear.bonus("shadow_assassin") >= 4) {
            out.add(StatTypes.STRENGTH, state.dungeonKills);
        }
        if (gear.full("zombie_knight") && holding(player, "ZOMBIE_KNIGHT_SWORD")) {
            out.add(StatTypes.DEFENSE, 50);
        }
        if (gear.set("zombie_knight") >= 4 && holding(player, "ZOMBIE_KNIGHT_SWORD")) {
            out.add(StatTypes.STRENGTH, 30);
        }
        if (gear.bonus("vivacious_darkness") >= 4 && player.isShiftKeyDown() && inCombat(player)) {
            out.add(StatTypes.STRENGTH, 30);
            out.add(StatTypes.ATTACK_SPEED, 20);
            out.add(StatTypes.SPEED, 10);
            out.add(StatTypes.INTELLIGENCE, 0.25 * without(player, StatTypes.INTELLIGENCE));
        }
        if (state.enrageUntil > player.level().getServer().getTickCount()) {
            out.add(StatTypes.SPEED, 100);
            out.add(StatTypes.STRENGTH, 100);
        }
        if (gear.tiered("berserk")) {
            out.add(StatTypes.HEALTH, -0.5 * without(player, StatTypes.HEALTH));
            out.add(StatTypes.DEFENSE, -0.5 * without(player, StatTypes.DEFENSE));
        }
        long now = player.level().getServer().getTickCount();
        if (state.sinewUntil > now) {
            out.add(StatTypes.STRENGTH, 10);
        }
        if (state.pursuitUntil > now) {
            out.add(StatTypes.SPEED, 20);
        }

        // Nearby wearers
        if (gear.bonus("armor_of_the_pack") >= 4) {
            long wearers = Math.min(3, near(player, 30).stream().filter(p -> gear(p).bonus("armor_of_the_pack") >= 4).count());
            out.add(StatTypes.STRENGTH, 35 * wearers);
            out.add(StatTypes.DEFENSE, 80 * wearers);
        }
        if (gear.bonus("shoal") >= 4) {
            out.add(StatTypes.DEFENSE, 30 * near(player, 30).stream().filter(p -> gear(p).bonus("shoal") >= 4).count());
        }
        long battalion = near(player, 30).stream().filter(p -> gear(p).bonus("battalion") >= 4).count();
        if (battalion > 0) {
            out.add(StatTypes.MAGIC_FIND, Math.min(5, battalion));
            out.add(StatTypes.DEFENSE, 50 * Math.min(5, battalion));
        }

        // Collections and progress
        if (gear.full("fairy")) {
            out.add(StatTypes.HEALTH, FairySouls.found(player));
        }
        if (gear.full("emerald_armor")) {
            double emerald = Math.min(350, Math.floor(Collections.count(player, SkyRpgSkyblock.id("emerald")) / 3000.0));
            out.add(StatTypes.HEALTH, emerald);
            out.add(StatTypes.DEFENSE, emerald);
        }
        if (gear.full("sponge") || gear.full("shark_scale")) {
            if (player.isInWater()) {
                out.add(StatTypes.DEFENSE, without(player, StatTypes.DEFENSE));
            }
        }

        // Fishing
        if (gear.tiered("deepness_within")) {
            out.add(StatTypes.HEALTH, 6 * SkillLevels.level(player, SkillLevels.FISHING));
        }
        for (ItemStack stack : armour(player)) {
            ItemDefinition def = SkyItems.definition(stack);
            if (def != null && hasBonus(def, "star_fisher") && gear.bonus("star_fisher") >= 4) {
                String path = def.id().getPath();
                double per = path.startsWith("bronze_hunter") ? 0.2 : path.startsWith("silver_hunter") ? 0.4 : path.startsWith("gold_hunter") ? 0.7 : 1;
                out.add(StatTypes.FISHING_SPEED, per * ItemMods.stars(stack));
            }
        }
        if (gear.tiered("lord_s_blessing")) {
            out.add(StatTypes.SEA_CREATURE_CHANCE, 1);
        }
        if (gear.tiered("arachne_s_faithful")) {
            out.add(StatTypes.HEALTH, 5);
            out.add(StatTypes.DEFENSE, 5);
        }

        // Dashing: max health is set
        if (gear.full("cheap_tuxedo") || gear.full("fancy_tuxedo") || gear.full("elegant_tuxedo")) {
            double max = gear.full("cheap_tuxedo") ? 75 : gear.full("fancy_tuxedo") ? 150 : 250;
            out.add(StatTypes.HEALTH, max - without(player, StatTypes.HEALTH));
        }
    }

    private static List<ItemStack> armour(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>(4);
        for (EquipmentSlot slot : WORN) {
            ItemStack stack = player.getItemBySlot(slot);
            ItemDefinition def = SkyItems.definition(stack);
            if (def != null && SkyItems.armorSlot(def.item()) == slot) {
                out.add(stack);
            }
        }
        return out;
    }

    private static boolean hasBonus(ItemDefinition def, String key) {
        for (SetBonuses.Bonus bonus : SetBonuses.of(def)) {
            if (bonus.key().equals(key)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- damage

    /** Damage you deal and take, as one multiplier. */
    private static double damageFactor(LivingEntity target, DamageSource source) {
        if (target instanceof ServerPlayer player) {
            return damageTaken(player, source);
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 1;
        }
        Gear gear = gear(player);
        if (gear.bonuses().isEmpty() && !holding(player, "ASPECT_OF_THE_END")) {
            return 1;
        }
        double factor = 1;
        boolean melee = source.is(DamageTypes.PLAYER_ATTACK);
        if (melee) {
            State state = state(player);
            state.lastCombat = player.level().getServer().getTickCount();
            state.melee++;
            if (gear.full("strong_dragon") && holding(player, "ASPECT_OF_THE_END")) {
                factor *= 1.75;
            }
            if (gear.bonus("octodexterity") >= 4) {
                boolean primordial = gear.set("primordial") >= 4;
                if (state.melee % (primordial ? 3 : 4) == 0) {
                    factor *= primordial ? 1.5 : 2;
                }
            }
            long now = player.level().getServer().getTickCount();
            if (state.bloodrushUntil > now) {
                state.bloodrushUntil = 0;
                factor *= 1.1;
            }
            if (state.charges > 0 && gear.tiered("static_charge")) {
                factor *= 1 + chargeDamage(gear.bonus("static_charge")) * state.charges;
                state.charges = 0;
                ServerLevel level = (ServerLevel) player.level();
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.5), target.getZ(), 16, 0.3, 0.4, 0.3, 0.2);
            }
        } else if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            if (gear.full("skeleton_master")) {
                factor *= 1.25;
            }
            if (gear.full("skeleton_soldier")) {
                factor *= 1.25;
            }
        }
        if (gear.full("cheap_tuxedo")) {
            factor *= 1.5;
        } else if (gear.full("fancy_tuxedo")) {
            factor *= 2;
        } else if (gear.full("elegant_tuxedo")) {
            factor *= 2.5;
        }
        if (gear.full("reaper")) {
            // Trolling The Reaper: double to the undead, a hundredth to the rest.
            factor *= target.is(EntityTypeTags.UNDEAD) ? 2 : 0.01;
        }
        if (gear.tiered("swamp_soldier") && target.entityTags().contains(SEA_CREATURE_TAG)) {
            factor *= 1.05;
        }
        if (gear.bonus("vivacious_darkness") >= 4 && player.isShiftKeyDown() && inCombat(player) && isEnder(target)) {
            factor *= 2;
        }
        if (state(player).enrageUntil > player.level().getServer().getTickCount()) {
            factor *= 2;
        }
        return factor;
    }

    private static boolean isEnder(Entity entity) {
        EntityType<?> type = entity.getType();
        return type == EntityTypes.ENDERMAN || type == EntityTypes.ENDERMITE || type == EntityTypes.ENDER_DRAGON;
    }

    private static double chargeDamage(int pieces) {
        return new double[] {0.15, 0.15, 0.20, 0.25, 0.40}[Math.max(0, Math.min(4, pieces - 1))];
    }

    private static double damageTaken(ServerPlayer player, DamageSource source) {
        Gear gear = gear(player);
        if (gear.bonuses().isEmpty()) {
            return 1;
        }
        Entity attacker = source.getEntity();
        double factor = 1;
        if (attacker != null) {
            State state = state(player);
            state.lastCombat = player.level().getServer().getTickCount();
            if (gear.full("mastiff") && attacker instanceof Animal) {
                factor *= 0.8;
            }
            if (gear.tiered("depth_champion") && attacker.entityTags().contains(SEA_CREATURE_TAG)) {
                factor *= 0.97;
            }
            if (gear.tiered("molten_core") && (attacker.entityTags().contains(KUUDRA_TAG) || in(player, Kuudra.LEVEL))) {
                factor *= 0.95;
            }
            if (gear.full("revenant") && attacker.is(EntityTypeTags.ZOMBIES)) {
                // +100 Defense against Zombies
                double defense = Math.max(0, Stats.get(player, StatTypes.DEFENSE));
                factor *= (100 + defense) / (200 + defense);
            }
            if (gear.full("reaper") && attacker.is(EntityTypeTags.UNDEAD)) {
                double defense = Math.max(0, Stats.get(player, StatTypes.DEFENSE));
                factor *= (100 + defense) / (200 + defense);
            }
        }
        return factor;
    }

    // ---------------------------------------------------------------- hits and kills

    private static void afterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
        // Hit you take: Deflect, Projectile Absorption
        if (entity instanceof ServerPlayer player && taken > 0) {
            Gear gear = gear(player);
            if (gear.bonuses().isEmpty()) {
                return;
            }
            if (gear.full("cactus") && source.getEntity() instanceof LivingEntity attacker && attacker != player && attacker instanceof Mob
                    && attacker.level() instanceof ServerLevel level) {
                attacker.hurtServer(level, level.damageSources().thorns(player), taken * 0.33f);
            }
            if (gear.full("zombie") && source.is(DamageTypeTags.IS_PROJECTILE)) {
                MinecraftServer server = player.level().getServer();
                for (int i = 1; i <= 5; i++) {
                    MoreAbilities.later(server, i * 20L, () -> {
                        if (player.isAlive()) {
                            player.heal((float) (10 / StatTypes.HEALTH_SCALE));
                        }
                    });
                }
            }
            return;
        }
        if (!(source.getEntity() instanceof ServerPlayer player) || !(entity instanceof Mob) || entity == player) {
            return;
        }
        Gear gear = gear(player);
        // Bouncing Arrow: a quarter of arrows go on to another mob.
        if (gear.full("bouncy") && source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof AbstractArrow
                && player.getRandom().nextDouble() < 0.25 && entity.level() instanceof ServerLevel level) {
            level.getEntitiesOfClass(Mob.class, entity.getBoundingBox().inflate(8), m -> m.isAlive() && m != entity).stream()
                    .min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(entity))).ifPresent(next -> {
                        HypixelAbilities.hurt(player, next, taken * StatTypes.HEALTH_SCALE);
                        Vec3 from = entity.getBoundingBox().getCenter();
                        Vec3 to = next.getBoundingBox().getCenter();
                        for (double d = 0; d < from.distanceTo(to); d += 0.5) {
                            Vec3 at = from.add(to.subtract(from).normalize().scale(d));
                            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                        }
                    });
        }
    }

    private static void killed(LivingEntity entity, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !(entity instanceof Mob)) {
            return;
        }
        Gear gear = gear(player);
        if (gear.bonuses().isEmpty()) {
            return;
        }
        State state = state(player);
        long now = player.level().getServer().getTickCount();
        if (in(player, Catacombs.LEVEL)) {
            state.dungeonKills++;
        }
        if (gear.full("golem_armor")) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 400, 2));
        }
        if (gear.full("mercenary") && MobLevels.level(entity) >= 10) {
            Wallet.give(player, 5);
            player.heal((float) (20 / StatTypes.HEALTH_SCALE));
        }
        // Salubrious: on a kill, heal 15 (3s cooldown)
        if (gear.bonus("salubrious") > 0 && now >= state.salubriousAt) {
            state.salubriousAt = now + 60;
            player.heal((float) (15 / StatTypes.HEALTH_SCALE));
        }
    }

    // ---------------------------------------------------------------- ticks

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            Gear gear = gear(player);
            State state = STATES.get(player.getUUID());
            if (gear.bonuses().isEmpty() && state == null) {
                continue;
            }
            state = state(player);
            dungeon(player, state);
            teleports(player, gear, state, now);
            sneaks(player, gear, state, now);
            if (gear.bonuses().isEmpty()) {
                continue;
            }
            if (now % 20 == 0) {
                everySecond(player, gear, state, now);
            }
        }
    }

    /** Minutes and kills in the dungeon, from the moment you step in. */
    private static void dungeon(ServerPlayer player, State state) {
        boolean inside = in(player, Catacombs.LEVEL);
        if (inside && !state.wasInDungeon) {
            state.dungeonTicks = 0;
            state.dungeonKills = 0;
        }
        if (inside) {
            state.dungeonTicks++;
        } else if (state.wasInDungeon) {
            state.dungeonTicks = 0;
            state.dungeonKills = 0;
            Stats.refresh(player);
        }
        state.wasInDungeon = inside;
    }

    /** The Shadow Assassin's pieces: a jump of 5 blocks or more in a tick counts as a teleport. */
    private static void teleports(ServerPlayer player, Gear gear, State state, long now) {
        Vec3 here = player.position();
        Vec3 before = state.lastPos;
        state.lastPos = here;
        if (before == null || gear.bonuses().isEmpty() || here.distanceToSqr(before) < 25 || now - state.lastTeleport < 60) {
            return;
        }
        boolean fired = false;
        if (gear.bonus("fluxation") > 0) {
            Stats.spendMana(player, -10);
            fired = true;
        }
        if (gear.bonus("sinew") > 0) {
            state.sinewUntil = now + 200;
            fired = true;
        }
        if (gear.bonus("bloodrush") > 0) {
            state.bloodrushUntil = now + 100;
            fired = true;
        }
        if (gear.bonus("pursuit") > 0) {
            state.pursuitUntil = now + 200;
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200, 0, true, false));
            fired = true;
        }
        if (fired) {
            state.lastTeleport = now;
            Stats.refresh(player);
        }
    }

    /** Starting to sneak: the Reaper's Enrage. */
    private static void sneaks(ServerPlayer player, Gear gear, State state, long now) {
        boolean sneaking = player.isShiftKeyDown();
        boolean started = sneaking && !state.sneaking;
        state.sneaking = sneaking;
        if (started && gear.bonus("enrage") >= 3 && gear.set("reaper") >= 3) {
            if (now < state.enrageReadyAt) {
                return;
            }
            state.enrageUntil = now + 120;
            state.enrageReadyAt = now + 500;
            player.sendOverlayMessage(Msg.text("Enrage! 100 Speed, Damage and Strength for 6s", ChatFormatting.RED));
            ((ServerLevel) player.level()).playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8f, 1.2f);
            Stats.refresh(player);
        } else if (state.enrageUntil != 0 && now >= state.enrageUntil) {
            state.enrageUntil = 0;
            Stats.refresh(player);
        }
    }

    private static void everySecond(ServerPlayer player, Gear gear, State state, long now) {
        ServerLevel level = (ServerLevel) player.level();
        if (gear.full("mushroom")) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 320, 0, true, false, false));
        }
        if (gear.full("rabbit") && player.isShiftKeyDown()) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 40, 1, true, false, false));
        }
        if (gear.full("ember") || (gear.tiered("fireproof") && in(player, com.skyrpg.skyblock.crimson.CrimsonIsle.LEVEL))) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false, false));
            player.clearFire();
        }
        if (gear.full("blaze") || gear.full("frozen_blaze")) {
            blazingAura(player, gear.full("frozen_blaze"));
        }
        if (gear.full("unstable_dragon") && !Islands.isIslands(level)) {
            if (state.nextLightning == 0) {
                state.nextLightning = now + 300;
            }
            if (now >= state.nextLightning) {
                state.nextLightning = now + 300;
                lightning(player, level);
            }
        }
        if (gear.bonus("witherborn") >= 4) {
            if (state.nextWither == 0) {
                state.nextWither = now + 600;
            }
            if (now >= state.nextWither && witherExplosion(player, level)) {
                state.nextWither = now + 600;
            }
        }
        if (gear.tiered("static_charge")) {
            int pieces = gear.bonus("static_charge");
            int interval = new int[] {600, 600, 500, 400, 200}[Math.min(4, pieces - 1)] / 1;
            if (state.nextCharge == 0 || state.charges >= pieces) {
                state.nextCharge = now + interval;
            } else if (now >= state.nextCharge) {
                state.nextCharge = now + interval;
                state.charges++;
                player.sendOverlayMessage(Msg.text("Static Charge x" + state.charges, ChatFormatting.YELLOW));
            }
        }
    }

    /** Blazing Aura: every mob within 5 blocks takes 3% of its health a second (at most a cap that grows with Blaze Rods). */
    private static void blazingAura(ServerPlayer player, boolean frozen) {
        ServerLevel level = (ServerLevel) player.level();
        long rods = Collections.count(player, SkyRpgSkyblock.id("blaze_rod"));
        double cap = Math.min(5000, 100 + 100 * Math.floor(rods / 5000.0));
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(5), Mob::isAlive)) {
            double damage = Math.min(cap, mob.getMaxHealth() * StatTypes.HEALTH_SCALE * 0.03) + (frozen ? 300 : 0);
            HypixelAbilities.hurt(player, mob, damage);
            if (frozen) {
                mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 0));
            }
        }
        level.sendParticles(frozen ? ParticleTypes.SNOWFLAKE : ParticleTypes.FLAME, player.getX(), player.getY() + 0.3, player.getZ(), 24, 2.5, 0.2, 2.5, 0.02);
    }

    /** Unstable Blood: lightning on every mob within 8 blocks, 3,000 damage each. */
    private static void lightning(ServerPlayer player, ServerLevel level) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(8), Mob::isAlive)) {
            bolt(level, mob.position());
            HypixelAbilities.hurt(player, mob, 3000);
        }
    }

    private static void bolt(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.setPos(at.x, at.y, at.z);
            level.addFreshEntity(bolt);
        }
    }

    /** Witherborn: the wither flies to the nearest mob within 16 blocks and explodes (1,000 ability damage in 5 blocks). */
    private static boolean witherExplosion(ServerPlayer player, ServerLevel level) {
        Mob target = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16), Mob::isAlive).stream()
                .min(java.util.Comparator.comparingDouble(m -> m.distanceToSqr(player))).orElse(null);
        if (target == null) {
            return false;
        }
        Vec3 from = player.position().add(0, 2.2, 0);
        Vec3 to = target.getBoundingBox().getCenter();
        for (double d = 0; d < from.distanceTo(to); d += 0.5) {
            Vec3 at = from.add(to.subtract(from).normalize().scale(d));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.01);
        }
        double damage = HypixelAbilities.abilityDamage(player, 1000);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(5), Mob::isAlive)) {
            HypixelAbilities.hurt(player, mob, damage);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        level.playSound(null, target.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 1f, 0.8f);
        return true;
    }

    // ---------------------------------------------------------------- other abilities

    /** Superior Blood's 50% more damage for the Aspect of the Dragons' ability (used by {@link HypixelAbilities#abilityDamage}). */
    static double abilityFactor(ServerPlayer player) {
        return holding(player, "ASPECT_OF_THE_DRAGON") && gear(player).full("superior_dragon") ? 1.5 : 1;
    }
}
