package com.skyrpg.skyblock.enchant;

import com.skyrpg.core.stats.Crits;
import com.skyrpg.core.stats.DamageBonus;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.economy.Purse;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.enchant.Enchants;
import com.skyrpg.pets.Pets;
import com.skyrpg.skyblock.pet.SkyBlockPets;
import com.skyrpg.progress.collections.CollectionDef;
import com.skyrpg.progress.collections.Collections;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Hypixel's ultimate enchantments (and the defensive enchants that work like them), with the numbers of each level's lore.
 * <ul>
 *   <li>Stats that follow a situation: Wisdom (Intelligence for your XP levels), Legion and Bobbin' Time (a percent of your
 *       stats for the players and bobbers near you), Chimera (the active pet's stats), Fatal Tempo (Ferocity for each hit
 *       in the last three seconds), Flowstate (Mining Speed for every block in a row), Last Stand, Refrigerate and Counter-Strike
 *       (Defense for a while), The One (per maxed collection), Respite.</li>
 *   <li>Damage: Combo (per kill), Habanero Tactics (slayer bosses). Swarm and One For All are in {@link EnchantEffects}.</li>
 *   <li>On a hit: Soul Eater (the last kill's damage on your next crit), Inferno (every tenth hit), Duplex (a second, burning
 *       arrow; the burning mob takes more fire damage), Thorns. When hit: No Pain No Gain (XP orbs), Thorns.</li>
 *   <li>On a kill: Bank (coins for every kill, and a share of your purse kept when you die).</li>
 * </ul>
 * Not here: Rend (a bow's left-click ability), Missile, First Impression and Sunset (Throwing Axe, Sweep and Overbloom
 * aren't in the game), Strong/Hardened/Ferocious Mana and Mana Vampire (nothing spends Vitality).
 */
public final class UltimateEnchants {
    /** Combat stats Legion raises, and the fishing stats Bobbin' Time raises (both also Magic Find). */
    private static final List<StatType> COMBAT = List.of(StatTypes.HEALTH, StatTypes.DEFENSE, StatTypes.TRUE_DEFENSE, StatTypes.STRENGTH,
            StatTypes.CRIT_CHANCE, StatTypes.CRIT_DAMAGE, StatTypes.ATTACK_SPEED, StatTypes.ABILITY_DAMAGE, StatTypes.FEROCITY,
            StatTypes.INTELLIGENCE, StatTypes.MAGIC_FIND);
    private static final List<StatType> FISHING = List.of(StatTypes.FISHING_SPEED, StatTypes.SEA_CREATURE_CHANCE, StatTypes.FISHING_FORTUNE,
            StatTypes.DOUBLE_HOOK_CHANCE, StatTypes.TREASURE_CHANCE, StatTypes.TROPHY_FISH_CHANCE, StatTypes.FISHING_WISDOM,
            StatTypes.MAGIC_FIND);
    private static final int TEMPO_TICKS = 60;
    private static final int FLOWSTATE_TICKS = 200;
    private static final int FLOWSTATE_CAP = 200;
    private static final int FIRE_MARK_TICKS = 1200;

    /** What a timed Defense (or Vitality) buff gives until it ends: Last Stand, Counter-Strike, Refrigerate. */
    private record Buff(long until, double percent, double flat, double vitality) {
    }

    /** What each player's Ultimate Enchantments source added last time, so a percent is taken of the stats without it. */
    private static final Map<UUID, Map<StatType, Double>> LAST = new HashMap<>();
    private static final Map<UUID, Deque<Long>> KILLS = new HashMap<>();
    private static final Map<UUID, int[]> TEMPO = new HashMap<>();
    private static final Map<UUID, long[]> FLOW = new HashMap<>();
    private static final Map<UUID, Buff> LAST_STAND = new HashMap<>();
    private static final Map<UUID, Long> LAST_STAND_READY = new HashMap<>();
    private static final Map<UUID, Buff> COUNTER = new HashMap<>();
    private static final Map<UUID, Long> COUNTER_READY = new HashMap<>();
    private static final Map<UUID, Buff> REFRIGERATE = new HashMap<>();
    private static final Map<UUID, Double> SOUL = new HashMap<>();
    /** Hits on a mob that holds Inferno's count, and the Duplex fire mark (until tick, multiplier). */
    private static final Map<LivingEntity, Integer> INFERNO = new WeakHashMap<>();
    private static final Map<LivingEntity, double[]> FIRE_MARK = new WeakHashMap<>();

    private UltimateEnchants() {
    }

    static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Ultimate Enchantments";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                stats(player, out);
            }
        });
        DamageBonus.register(UltimateEnchants::damagePercent);
        Stats.onManaSpent(UltimateEnchants::manaSpent);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity instanceof ServerPlayer player) {
                hurt(player, source, taken);
            } else if (source.getEntity() instanceof ServerPlayer player && entity != player && taken > 0 && !(entity instanceof Player)) {
                hit(player, entity, source, taken);
            }
            double[] mark = FIRE_MARK.get(entity);
            if (mark != null && source.is(DamageTypeTags.IS_FIRE) && entity.level() instanceof ServerLevel level) {
                if (level.getGameTime() > mark[0]) {
                    FIRE_MARK.remove(entity);
                } else {
                    entity.hurtServer(level, level.damageSources().magic(), (float) ((mark[1] - 1) * taken));
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer player && !(entity instanceof Player)) {
                kill(player, entity);
            }
            INFERNO.remove(entity);
            FIRE_MARK.remove(entity);
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer miner && ItemMods.enchantment(miner.getMainHandItem(), "ultimate_flowstate") > 0) {
                long[] flow = FLOW.computeIfAbsent(miner.getUUID(), id -> new long[2]);
                flow[0] = level.getGameTime() - flow[1] > FLOWSTATE_TICKS ? 1 : Math.min(FLOWSTATE_CAP, flow[0] + 1);
                flow[1] = level.getGameTime();
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUUID();
            for (Map<UUID, ?> map : List.<Map<UUID, ?>>of(LAST, KILLS, TEMPO, FLOW, LAST_STAND, LAST_STAND_READY, COUNTER, COUNTER_READY, REFRIGERATE, SOUL)) {
                map.remove(id);
            }
        });
    }

    // ---------------------------------------------------------------- stats

    private static void stats(ServerPlayer player, Stats.Contributor out) {
        Map<StatType, Double> mine = new HashMap<>();
        Stats.Contributor sink = (stat, value) -> {
            mine.merge(stat, value, Double::sum);
            out.add(stat, value);
        };
        long now = player.level().getGameTime();
        Map<StatType, Double> before = LAST.getOrDefault(player.getUUID(), Map.of());
        double legion = 0;
        double bobbin = 0;
        List<ItemStack> worn = EnchantEffects.worn(player);
        for (ItemStack stack : worn) {
            Map<String, Integer> enchants = ItemMods.enchantments(stack);
            if (enchants.isEmpty()) {
                continue;
            }
            int wisdom = enchants.getOrDefault("ultimate_wisdom", 0);
            if (wisdom > 0) {
                // value() is the Intelligence for every 5 levels; the cap is 20 a level.
                sink.add(StatTypes.INTELLIGENCE, Math.min(20.0 * wisdom, EnchantEffects.value("ultimate_wisdom", wisdom) * (player.experienceLevel / 5)));
            }
            int legionLevel = enchants.getOrDefault("ultimate_legion", 0);
            if (legionLevel > 0) {
                legion += EnchantEffects.value("ultimate_legion", legionLevel);
            }
            int bobbinLevel = enchants.getOrDefault("ultimate_bobbin_time", 0);
            if (bobbinLevel > 0) {
                bobbin += EnchantEffects.value("ultimate_bobbin_time", bobbinLevel);
            }
            int respite = enchants.getOrDefault("respite", 0);
            if (respite > 0 && !com.skyrpg.core.combat.CombatTag.isTagged(player)) {
                sink.add(StatTypes.HEALTH_REGEN, EnchantEffects.value("respite", respite));
            }
        }
        if (legion > 0) {
            double players = Math.min(20, nearbyPlayers(player, 30));
            for (StatType stat : COMBAT) {
                sink.add(stat, base(player, stat, before) * legion * players / 100);
            }
        }
        if (bobbin > 0) {
            double bobbers = Math.min(5, bobbers(player, 30));
            for (StatType stat : FISHING) {
                sink.add(stat, base(player, stat, before) * bobbin * bobbers / 100);
            }
        }
        buff(player, LAST_STAND, now, before, sink, StatTypes.DEFENSE);
        buff(player, COUNTER, now, before, sink, StatTypes.DEFENSE);
        buff(player, REFRIGERATE, now, before, sink, StatTypes.DEFENSE);

        ItemStack held = player.getMainHandItem();
        Map<String, Integer> enchants = ItemMods.enchantments(held);
        int chimera = enchants.getOrDefault("ultimate_chimera", 0);
        if (chimera > 0) {
            ItemStack pet = Pets.active(player);
            if (SkyBlockPets.isPet(pet)) {
                double share = EnchantEffects.value("ultimate_chimera", chimera) / 100;
                SkyBlockPets.stats(pet).forEach((stat, value) -> {
                    if (StatTypes.isPlayerStat(stat)) {
                        sink.add(stat, value * share);
                    }
                });
            }
        }
        int tempo = enchants.getOrDefault("ultimate_fatal_tempo", 0);
        int[] hits = TEMPO.get(player.getUUID());
        if (tempo > 0 && hits != null && now - hits[1] <= TEMPO_TICKS) {
            sink.add(StatTypes.FEROCITY, Math.min(200, EnchantEffects.value("ultimate_fatal_tempo", tempo) * hits[0]));
        }
        int flowstate = enchants.getOrDefault("ultimate_flowstate", 0);
        long[] flow = FLOW.get(player.getUUID());
        if (flowstate > 0 && flow != null && now - flow[1] <= FLOWSTATE_TICKS) {
            sink.add(StatTypes.MINING_SPEED, EnchantEffects.value("ultimate_flowstate", flowstate) * flow[0]);
        }
        for (ItemStack stack : EnchantEffects.equipment(player)) {
            int theOne = ItemMods.enchantment(stack, "ultimate_the_one");
            if (theOne > 0) {
                double[] numbers = EnchantEffects.numbers("ultimate_the_one", theOne);
                int maxed = maxedCollections(player);
                sink.add(StatTypes.HEALTH, EnchantEffects.value("ultimate_the_one", theOne) * maxed);
                sink.add(StatTypes.STRENGTH, (numbers.length > 1 ? numbers[1] : 0) * maxed);
            }
        }
        LAST.put(player.getUUID(), mine);
    }

    /** A stat without what this source added to it last time. */
    private static double base(ServerPlayer player, StatType stat, Map<StatType, Double> before) {
        return Math.max(0, Stats.get(player, stat) - before.getOrDefault(stat, 0.0));
    }

    private static void buff(ServerPlayer player, Map<UUID, Buff> buffs, long now, Map<StatType, Double> before, Stats.Contributor out, StatType stat) {
        Buff buff = buffs.get(player.getUUID());
        if (buff == null) {
            return;
        }
        if (now >= buff.until()) {
            buffs.remove(player.getUUID());
            return;
        }
        out.add(stat, base(player, stat, before) * buff.percent() / 100 + buff.flat());
        if (buff.vitality() > 0) {
            out.add(StatTypes.VITALITY, buff.vitality());
        }
    }

    private static int nearbyPlayers(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(radius),
                other -> other != player && !other.isSpectator()).size();
    }

    private static int bobbers(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(FishingHook.class, player.getBoundingBox().inflate(radius)).size();
    }

    private static int maxedCollections(ServerPlayer player) {
        int maxed = 0;
        for (CollectionDef def : Collections.all()) {
            if (def.tierCount() > 0 && Collections.tier(player, def.id()) >= def.tierCount()) {
                maxed++;
            }
        }
        return maxed;
    }

    // ---------------------------------------------------------------- mana

    /** Refrigerate: some of the mana an ability cost is Defense for ten seconds (the most it gives is in the lore). */
    private static void manaSpent(ServerPlayer player, double amount) {
        double percent = 0;
        double cap = 0;
        for (ItemStack stack : EnchantEffects.worn(player)) {
            int level = ItemMods.enchantment(stack, "ultimate_refrigerate");
            if (level > 0) {
                percent += EnchantEffects.value("ultimate_refrigerate", level);
                double[] numbers = EnchantEffects.numbers("ultimate_refrigerate", level);
                cap = Math.max(cap, numbers.length > 2 ? numbers[numbers.length - 1] : 150);
            }
        }
        if (percent > 0) {
            double defense = Math.min(cap, amount * percent / 100);
            REFRIGERATE.put(player.getUUID(), new Buff(player.level().getGameTime() + 200, 0, defense, 0));
            Stats.refresh(player);
        }
    }

    // ---------------------------------------------------------------- damage

    private static double damagePercent(ServerPlayer player, LivingEntity target, DamageSource source) {
        double percent = 0;
        ItemStack held = player.getMainHandItem();
        int combo = ItemMods.enchantment(held, "ultimate_combo");
        if (combo > 0) {
            double[] numbers = EnchantEffects.numbers("ultimate_combo", combo);
            if (numbers.length >= 3) {
                long window = (long) (numbers[2] * 20);
                long now = player.level().getGameTime();
                Deque<Long> kills = KILLS.get(player.getUUID());
                long count = kills == null ? 0 : kills.stream().filter(tick -> now - tick <= window).count();
                percent += numbers[0] * Math.min(count, (long) numbers[1]);
            }
        }
        if (target.entityTags().contains("skyrpg_slayer_boss")) {
            for (ItemStack stack : EnchantEffects.worn(player)) {
                int habanero = ItemMods.enchantment(stack, "ultimate_habanero_tactics");
                double[] numbers = habanero > 0 ? EnchantEffects.numbers("ultimate_habanero_tactics", habanero) : new double[0];
                percent += numbers.length > 1 ? numbers[1] : 0;
            }
        }
        return percent;
    }

    // ---------------------------------------------------------------- hits you land

    private static void hit(ServerPlayer player, LivingEntity target, DamageSource source, float taken) {
        ServerLevel level = (ServerLevel) player.level();
        ItemStack held = player.getMainHandItem();
        Map<String, Integer> enchants = ItemMods.enchantments(held);
        if (enchants.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        int tempo = enchants.getOrDefault("ultimate_fatal_tempo", 0);
        if (tempo > 0) {
            int[] hits = TEMPO.computeIfAbsent(player.getUUID(), id -> new int[2]);
            hits[0] = now - hits[1] > TEMPO_TICKS ? 1 : Math.min(40, hits[0] + 1);
            hits[1] = (int) now;
            Stats.refresh(player);
        }
        int soul = enchants.getOrDefault("ultimate_soul_eater", 0);
        Double stored = SOUL.get(player.getUUID());
        if (soul > 0 && stored != null && Crits.wasCrit()) {
            SOUL.remove(player.getUUID());
            double extra = Math.min(1_000_000 / StatTypes.HEALTH_SCALE, stored * EnchantEffects.value("ultimate_soul_eater", soul));
            level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
            target.hurtServer(level, level.damageSources().magic(), (float) extra);
        }
        int inferno = enchants.getOrDefault("ultimate_inferno", 0);
        if (inferno > 0) {
            double[] numbers = EnchantEffects.numbers("ultimate_inferno", inferno);
            int count = INFERNO.merge(target, 1, Integer::sum);
            if (numbers.length > 1 && count % (int) numbers[0] == 0) {
                level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY(0.5), target.getZ(), 40, 0.4, 0.6, 0.4, 0.05);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLAZE_SHOOT, player.getSoundSource(), 0.8f, 0.8f);
                target.igniteForSeconds(4);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 4));
                target.hurtServer(level, level.damageSources().magic(), (float) (taken * numbers[1] / 100));
            }
        }
        int duplex = enchants.getOrDefault("ultimate_reiterate", 0);
        if (duplex > 0 && source.getDirectEntity() instanceof AbstractArrow) {
            // The second arrow: a share of the first one's damage, setting the mob alight and making its fire hurt more.
            target.igniteForSeconds(4);
            FIRE_MARK.put(target, new double[] {now + FIRE_MARK_TICKS, 1 + 0.1 * duplex});
            target.hurtServer(level, level.damageSources().magic(), (float) (taken * EnchantEffects.value("ultimate_reiterate", duplex) / 100));
        }
    }

    // ---------------------------------------------------------------- hits you take

    private static void hurt(ServerPlayer player, DamageSource source, float taken) {
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        UUID id = player.getUUID();
        boolean mob = source.getEntity() instanceof Mob;
        for (ItemStack stack : EnchantEffects.worn(player)) {
            Map<String, Integer> enchants = ItemMods.enchantments(stack);
            if (enchants.isEmpty()) {
                continue;
            }
            int lastStand = enchants.getOrDefault("ultimate_last_stand", 0);
            if (lastStand > 0 && now >= LAST_STAND_READY.getOrDefault(id, 0L)) {
                double[] numbers = EnchantEffects.numbers("ultimate_last_stand", lastStand);
                if (numbers.length >= 5 && player.getHealth() <= player.getMaxHealth() * numbers[0] / 100) {
                    Buff buff = LAST_STAND.get(id);
                    double percent = numbers[1] + (buff != null && buff.until() > now ? buff.percent() : 0);
                    LAST_STAND.put(id, new Buff(now + (long) (numbers[2] * 20), percent, 0, numbers[3]));
                    LAST_STAND_READY.put(id, now + (long) (numbers[4] * 20));
                    Stats.refresh(player);
                }
            }
            int counter = enchants.getOrDefault("counter_strike", 0);
            if (counter > 0 && mob && now >= COUNTER_READY.getOrDefault(id, 0L) && !COUNTER.containsKey(id)) {
                double[] numbers = EnchantEffects.numbers("counter_strike", counter);
                if (numbers.length >= 2) {
                    COUNTER.put(id, new Buff(now + (long) (numbers[1] * 20), 0, numbers[0], 0));
                    COUNTER_READY.put(id, now + (long) (numbers[1] * 20) + 100);
                    Stats.refresh(player);
                }
            }
            int thorns = enchants.getOrDefault("thorns", 0);
            if (thorns > 0 && source.getEntity() instanceof LivingEntity attacker && attacker != player) {
                double[] numbers = EnchantEffects.numbers("thorns", thorns);
                if (numbers.length >= 2 && player.getRandom().nextDouble() * 100 < numbers[0]) {
                    attacker.hurtServer(level, level.damageSources().thorns(player), (float) (taken * numbers[1] / 100));
                }
            }
            int noPain = enchants.getOrDefault("ultimate_no_pain_no_gain", 0);
            if (noPain > 0 && mob) {
                double[] numbers = EnchantEffects.numbers("ultimate_no_pain_no_gain", noPain);
                if (numbers.length >= 2 && player.getRandom().nextDouble() * 100 < numbers[0]) {
                    ExperienceOrb.award(level, player.position(), (int) numbers[1]);
                }
            }
        }
    }

    // ---------------------------------------------------------------- kills

    private static void kill(ServerPlayer player, LivingEntity entity) {
        long now = player.level().getGameTime();
        ItemStack held = player.getMainHandItem();
        if (ItemMods.enchantment(held, "ultimate_combo") > 0) {
            Deque<Long> kills = KILLS.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
            kills.addLast(now);
            while (kills.size() > 12 || !kills.isEmpty() && now - kills.peekFirst() > 400) {
                kills.removeFirst();
            }
        }
        if (ItemMods.enchantment(held, "ultimate_soul_eater") > 0 && entity.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            SOUL.put(player.getUUID(), entity.getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
        // Bank: every kill drops a few coins for each piece with it (the half coin is a coin half the time).
        double coins = 0;
        for (ItemStack stack : EnchantEffects.worn(player)) {
            int bank = ItemMods.enchantment(stack, "ultimate_bank");
            double[] numbers = bank > 0 ? EnchantEffects.numbers("ultimate_bank", bank) : new double[0];
            coins += numbers.length > 1 ? numbers[1] : 0;
        }
        long whole = (long) coins + (player.getRandom().nextDouble() < coins - Math.floor(coins) ? 1 : 0);
        if (whole > 0) {
            Purse.add(player, whole);
        }
    }

    /** Bank: the share of the coins lost at death that your armour saves (0 to 1; 100% adds up over the pieces). */
    public static double bankSaved(ServerPlayer player) {
        double percent = 0;
        for (ItemStack stack : EnchantEffects.worn(player)) {
            int bank = ItemMods.enchantment(stack, "ultimate_bank");
            Enchants.Enchant enchant = Enchants.get("ultimate_bank");
            percent += bank > 0 && enchant != null ? enchant.value(bank) : 0;
        }
        return Math.min(1, percent / 100);
    }
}
