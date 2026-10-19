package com.skyrpg.skyblock.pet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.pets.Pets;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.island.Islands;
import com.skyrpg.skyblock.mining.Hotm;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * What the pet you have out does with its abilities ({@code skyrpg/skyblock_pet_abilities}, read from each ability's text by
 * {@code tools/skyblock/import_pet_abilities.py}): stats (some only in a place, at night, per nearby player, per skill
 * level, per digit of a collection), a percent of your own stats, stats from other stats (Elephant's Defense per Speed),
 * more damage to a kind of mob or in a situation (Ender Dragon, T-Rex, Megalodon), and less damage taken (Enderman,
 * Turtle). The numbers are the pet's at its level and rarity (Tier Boost included). Abilities with a mechanic of their own
 * are {@code perk}s, which {@link PetPerks} makes work (wisdom towards a mob, coins, minion speed, drops, Last Stand,
 * Laserbeam, the Phoenix's Rekindle...). Abilities the importer doesn't know stay text.
 *
 * <p>Percents and conversions read your stats from the last time they were added up, leaving out what the abilities
 * themselves added, so they don't feed on themselves.
 */
public final class PetAbilities {
    record Effect(String ability, String kind, String stat, List<String> stats, int num, double amount, String of, double per, int perNum,
                  double above, boolean pct, double cap, int capNum, String where, String when, String mobs, int perPlayer, int maxPlayers,
                  List<String> perLevel, String perDigit, String perk, String arg, int num2, double seconds, String perMob) {
    }

    /** One perk of the pet out: its effect, the effect's number at the pet's level, and the pet. */
    record Perk(Effect effect, double value, ItemStack pet) {
        /** The effect's second number ({@code num2}) at the pet's level. */
        double second() {
            return effect.num2() >= 0 ? SkyBlockPets.number(pet, effect.num2()) : 0;
        }

        /** Its limit: the pet's number {@code cap_num}, else the fixed {@code cap}. */
        double cap() {
            return effect.capNum() >= 0 ? SkyBlockPets.number(pet, effect.capNum()) : effect.cap();
        }
    }

    /** Pet type -> rarity -> its effects. */
    private static Map<String, Map<String, List<Effect>>> effects = Map.of();
    /** What the abilities added last time, per player, so percents and conversions can leave it out. */
    private static final Map<UUID, Map<StatType, Double>> LAST = new ConcurrentHashMap<>();
    private static final List<String> MINING_ISLANDS = List.of("gold_mine", "deep_caverns", "dwarven_mines", "crystal_hollows");

    private PetAbilities() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("skyblock_pet_abilities"), "skyrpg/skyblock_pet_abilities", PetAbilities::load);
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Pet abilities";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                Map<StatType, Double> added = stats(player);
                LAST.put(player.getUUID(), added);
                added.forEach(out::add);
            }
        });
        IncomingDamage.registerHit(PetAbilities::damage);
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, Map<String, List<Effect>>> loaded = new HashMap<>();
        for (JsonElement file : files.values()) {
            file.getAsJsonObject().getAsJsonObject("pets").entrySet().forEach(pet -> {
                Map<String, List<Effect>> tiers = new HashMap<>();
                pet.getValue().getAsJsonObject().entrySet().forEach(tier -> {
                    List<Effect> list = new ArrayList<>();
                    for (JsonElement e : tier.getValue().getAsJsonArray()) {
                        list.add(effect(e.getAsJsonObject()));
                    }
                    tiers.put(tier.getKey(), List.copyOf(list));
                });
                loaded.put(pet.getKey(), tiers);
            });
        }
        effects = loaded;
        SkyRpgSkyblock.LOGGER.info("Loaded pet abilities for {} pets", loaded.size());
    }

    private static Effect effect(JsonObject e) {
        List<String> stats = new ArrayList<>();
        if (e.has("stats")) {
            e.getAsJsonArray("stats").forEach(s -> stats.add(s.getAsString()));
        }
        List<String> perLevel = new ArrayList<>();
        if (e.has("per_level")) {
            e.getAsJsonArray("per_level").forEach(s -> perLevel.add(s.getAsString()));
        }
        return new Effect(string(e, "ability"), string(e, "kind"), string(e, "stat"), List.copyOf(stats), e.has("num") ? e.get("num").getAsInt() : -1,
                number(e, "amount"), string(e, "of"), number(e, "per"), e.has("per_num") ? e.get("per_num").getAsInt() : -1, number(e, "above"),
                e.has("pct") && e.get("pct").getAsBoolean(), number(e, "cap"), e.has("cap_num") ? e.get("cap_num").getAsInt() : -1,
                string(e, "where"), string(e, "when"), string(e, "mobs"), (int) number(e, "per_player"), (int) number(e, "max_players"),
                List.copyOf(perLevel), string(e, "per_digit"), string(e, "perk"), string(e, "arg"), e.has("num2") ? e.get("num2").getAsInt() : -1,
                number(e, "seconds"), string(e, "per_mob"));
    }

    private static String string(JsonObject e, String key) {
        return e.has(key) ? e.get(key).getAsString() : "";
    }

    private static double number(JsonObject e, String key) {
        return e.has(key) ? e.get(key).getAsDouble() : 0;
    }

    /** The effects of the pet this player has out (at the rarity its stats use). */
    static List<Effect> active(ServerPlayer player, ItemStack[] petOut) {
        ItemStack pet = Pets.active(player);
        if (!SkyBlockPets.isPet(pet)) {
            return List.of();
        }
        petOut[0] = pet;
        return effects.getOrDefault(SkyBlockPets.type(pet), Map.of()).getOrDefault(SkyBlockPets.statTier(pet), List.of());
    }

    /** The perks of this name that the pet out has now. */
    static List<Perk> perks(ServerPlayer player, String name) {
        ItemStack[] pet = new ItemStack[1];
        List<Perk> out = new ArrayList<>();
        for (Effect e : active(player, pet)) {
            if (e.kind().equals("perk") && e.perk().equals(name) && applies(player, e)) {
                out.add(new Perk(e, value(e, pet[0]), pet[0]));
            }
        }
        return out;
    }

    /** What the pet out's perks of this name add up to for this argument ("" = any), 0 without one. */
    public static double perk(ServerPlayer player, String name, String arg) {
        double total = 0;
        for (Perk perk : perks(player, name)) {
            if (arg.isEmpty() || perk.effect().arg().equals(arg)) {
                total += perk.value();
            }
        }
        return total;
    }

    /** Whether the pet out has a perk of this name (for the ones without a number). */
    public static boolean has(ServerPlayer player, String name) {
        return !perks(player, name).isEmpty();
    }

    static double value(Effect e, ItemStack pet) {
        return e.num() >= 0 ? SkyBlockPets.number(pet, e.num()) : e.amount();
    }

    /** A stat as it was last added up, without what the abilities added. */
    private static double others(ServerPlayer player, String id) {
        StatType stat = StatTypes.get(id);
        if (stat == null) {
            return 0;
        }
        return Stats.get(player, stat) - LAST.getOrDefault(player.getUUID(), Map.of()).getOrDefault(stat, 0.0);
    }

    private static Map<StatType, Double> stats(ServerPlayer player) {
        ItemStack[] pet = new ItemStack[1];
        Map<StatType, Double> out = new HashMap<>();
        for (Effect e : active(player, pet)) {
            if (!applies(player, e)) {
                continue;
            }
            double v = value(e, pet[0]);
            switch (e.kind()) {
                case "stat" -> add(out, e.stat(), v * times(player, e));
                case "perk" -> PetPerks.contribute(player, e, v, pet[0], (id, value) -> add(out, id, value));
                case "stat_pct" -> {
                    double percent = v;
                    if (!e.of().isEmpty() && e.per() > 0) {
                        // Golden Dragon: a percent per 5 Magic Find, up to its cap.
                        percent = v * Math.floor(Math.max(0, others(player, e.of())) / e.per());
                        if (e.capNum() >= 0) {
                            percent = Math.min(percent, SkyBlockPets.number(pet[0], e.capNum()));
                        }
                    }
                    List<String> targets = e.stat().isEmpty() ? e.stats() : List.of(e.stat());
                    for (String target : targets) {
                        add(out, target, Math.max(0, others(player, target)) * percent / 100);
                    }
                }
                case "convert" -> {
                    double base = others(player, e.of()) - e.above();
                    if (base <= 0) {
                        continue;
                    }
                    double per = e.perNum() >= 0 ? SkyBlockPets.number(pet[0], e.perNum()) : e.per();
                    double gain = e.pct() ? base * v / 100 : per > 0 ? Math.floor(base / per) * v : 0;
                    add(out, e.stat(), e.cap() > 0 ? Math.min(gain, e.cap()) : gain);
                }
                default -> {
                }
            }
        }
        return out;
    }

    private static void add(Map<StatType, Double> out, String id, double value) {
        StatType stat = StatTypes.get(id);
        if (stat != null && value != 0) {
            out.merge(stat, value, Double::sum);
        }
    }

    /** How many times a stat counts: per player near you (you too), per skill level, per digit of a collection; else once. */
    private static double times(ServerPlayer player, Effect e) {
        if (e.perPlayer() > 0) {
            double r2 = (double) e.perPlayer() * e.perPlayer();
            long near = player.level().players().stream().filter(p -> p.distanceToSqr(player) <= r2).count();
            return Math.min(e.maxPlayers() > 0 ? e.maxPlayers() : near, near);
        }
        if (!e.perMob().isEmpty()) {
            return Math.min(e.maxPlayers(), PetPerks.nearby(player, e.perMob(), e.perPlayer()));
        }
        if (!e.perLevel().isEmpty()) {
            double levels = 0;
            for (String skill : e.perLevel()) {
                levels += skill.equals("hotm") ? Hotm.level(player)
                        : SkillLevels.level(player, Identifier.fromNamespaceAndPath("skyrpg_skills", skill));
            }
            return levels;
        }
        if (!e.perDigit().isEmpty()) {
            long count = Math.min(100_000_000L, com.skyrpg.progress.collections.Collections.count(player, SkyRpgSkyblock.id(e.perDigit())));
            return count <= 0 ? 0 : Long.toString(count).length();
        }
        return 1;
    }

    /** Its place and time: a place we don't have yet (the Crimson Isle, the Garden...) never counts. */
    static boolean applies(ServerPlayer player, Effect e) {
        ServerLevel level = player.level();
        if (e.when().equals("night") && level.isBrightOutside()) {
            return false;
        }
        if (e.when().equals("health_above_85") && player.getHealth() <= player.getMaxHealth() * 0.85f) {
            return false;
        }
        if (e.where().isEmpty()) {
            return true;
        }
        String dimension = level.dimension().identifier().getPath();
        return switch (e.where()) {
            case "public" -> com.skyrpg.hub.Hub.isHub(level) || com.skyrpg.hub.Hub.isPublic(level);
            case "private_island" -> Islands.isIslands(level);
            case "crystal_hollows", "dwarven_mines", "the_park" -> dimension.equals(e.where());
            case "near_water", "near_fluid" -> PetPerks.nearFluid(player, e.where().equals("near_fluid"));
            case "mining_islands" -> MINING_ISLANDS.contains(dimension);
            case "magma_fields" -> {
                Zones.Zone zone = Zones.at(level, player.blockPosition());
                yield zone != null && zone.name().equals("Magma Fields");
            }
            default -> false;
        };
    }

    /** More damage dealt by a player with the pet out, less damage taken by one. */
    private static double damage(LivingEntity target, DamageSource source) {
        double multiplier = 1;
        ItemStack[] pet = new ItemStack[1];
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != target) {
            for (Effect e : active(attacker, pet)) {
                if (e.kind().equals("damage_vs")) {
                    multiplier *= 1 + more(attacker, target, source, e, value(e, pet[0]), pet[0]) / 100;
                }
            }
            multiplier *= PetPerks.dealt(attacker, target, source);
        }
        if (target instanceof ServerPlayer defender) {
            multiplier *= PetPerks.taken(defender, source);
            Entity attacker = source.getEntity();
            for (Effect e : active(defender, pet)) {
                if (!e.kind().equals("damage_from") || e.when().equals("low_health") && defender.getHealth() >= defender.getMaxHealth() * 0.4f) {
                    continue;
                }
                if (e.mobs().equals("all") ? attacker != null && attacker != defender : attacker != null && is(attacker, e.mobs())) {
                    multiplier *= Math.max(0, 1 - value(e, pet[0]) / 100);
                }
            }
        }
        return multiplier;
    }

    /** The percent more this hit deals for one damage_vs effect (0 when it doesn't apply). */
    private static double more(ServerPlayer attacker, LivingEntity target, DamageSource source, Effect e, double v, ItemStack pet) {
        boolean melee = source.getDirectEntity() == attacker;
        return switch (e.mobs()) {
            case "close" -> melee && attacker.distanceTo(target) <= 1.5 + target.getBbWidth() / 2 ? v : 0;
            case "far" -> melee && attacker.distanceTo(target) > 3 ? v : 0;
            case "missing_health" -> v * (1 - target.getHealth() / Math.max(1, target.getMaxHealth()));
            case "arrows" -> source.getDirectEntity() instanceof AbstractArrow ? v : 0;
            case "attacked_you" -> target.getLastHurtMob() == attacker ? v : 0;
            case "alone" -> target.level().getEntitiesOfClass(Mob.class, new AABB(target.blockPosition()).inflate(15),
                    m -> m != target && m instanceof Enemy && m.isAlive()).isEmpty() ? v : 0;
            case "level_50" -> {
                int level = com.skyrpg.combat.MobLevels.level(target);
                yield level >= 50 && level < 100 ? v : 0;
            }
            case "level_100" -> com.skyrpg.combat.MobLevels.level(target) >= 100 ? v : 0;
            case "bank" -> {
                double percent = v * Math.floor(com.skyrpg.economy.bank.Bank.balance(attacker) / 1_000_000.0);
                yield e.capNum() >= 0 ? Math.min(percent, SkyBlockPets.number(pet, e.capNum())) : percent;
            }
            default -> is(target, e.mobs()) ? v : 0;
        };
    }

    /** Hypixel's mob kinds, by vanilla type. */
    static boolean is(Entity entity, String kind) {
        EntityType<?> type = entity.getType();
        return switch (kind) {
            case "ender" -> type == EntityTypes.ENDERMAN || type == EntityTypes.ENDERMITE || type == EntityTypes.ENDER_DRAGON
                    || type == EntityTypes.SHULKER;
            case "undead" -> type.builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
            case "skeletal" -> type.builtInRegistryHolder().is(EntityTypeTags.SKELETONS);
            case "wither" -> type == EntityTypes.WITHER || type == EntityTypes.WITHER_SKELETON;
            case "zombies" -> type.builtInRegistryHolder().is(EntityTypeTags.ZOMBIES);
            case "wolves" -> type == EntityTypes.WOLF;
            case "slimes" -> type == EntityTypes.SLIME || type == EntityTypes.MAGMA_CUBE;
            case "blaze" -> type == EntityTypes.BLAZE;
            case "enderman" -> type == EntityTypes.ENDERMAN;
            case "zombie" -> type.builtInRegistryHolder().is(EntityTypeTags.ZOMBIES);
            case "spider" -> type == EntityTypes.SPIDER || type == EntityTypes.CAVE_SPIDER;
            case "wolf" -> type == EntityTypes.WOLF;
            default -> false;
        };
    }
}
