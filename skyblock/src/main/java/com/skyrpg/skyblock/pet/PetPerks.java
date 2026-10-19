package com.skyrpg.skyblock.pet;

import com.skyrpg.combat.MobLevels;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.stats.TrueDamage;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.MinionSpeed;
import com.skyrpg.core.world.MobTags;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.pets.Pets;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.garden.Garden;
import com.skyrpg.skyblock.island.Islands;
import com.skyrpg.skyblock.profile.Profile;
import com.skyrpg.skyblock.profile.Profiles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The pet abilities with a mechanic of their own ({@code perk}s in {@code skyrpg/skyblock_pet_abilities}), each on the
 * hook it needs:
 * <ul>
 *   <li>On a kill: Combat XP towards a kind of mob (Blazes, Endermen, Zombies, Spiders) or more of it against Wolves, coins
 *   (a flat amount, or per level of the mob), an Egg from every Chicken, an extra drop from animals, double Squid drops,
 *   and stacks (Horse's Stampede).</li>
 *   <li>On breaking a block: a drop chance on crops (Mushroom, Dung, Jelly, Honey Jar) and an extra End Stone.</li>
 *   <li>On a hit: Last Stand (a shield and +40% damage for 12s under 20% health, 60s cooldown), Toss (every 5th hit deals
 *   5x and throws the mob up), the Ankylosaurus's club (every 5th hit hurts its neighbours), Golem's ricochet, a slow
 *   (Spider), the wither effect, Fourth Flare (the Phoenix ignites on the 4th hit) and bow stacks (Skeleton's Combo).</li>
 *   <li>Taking damage: less from falls (Chicken), with absorption (Blue Whale), from mobs near a Snowman; a fall of 20
 *   blocks is absorbed and tramples (Horse); the Phoenix's Rekindle saves you from death once a minute.</li>
 *   <li>Every second or so: knockback immunity, permanent Jump Boost, flying on your island, the Snowman's slow, Guardian's
 *   Laserbeam, the Skeleton's arrow, burning mobs.</li>
 *   <li>Tools: stats while you hold one (Pigman Sword, Aspect of the Dragon, Drills, Axes).</li>
 *   <li>Elsewhere: minion speed on your island (the minions mod asks {@link MinionSpeed}), skill XP, mana costs, and
 *   Mithril/Gemstone/Glacite Powder ({@code Hotm.givePowder} asks {@link PetAbilities#perk}).</li>
 * </ul>
 * Decided: the stacks and cooldowns live in memory (a restart clears them), like the Hotm's Mining Speed Boost.
 */
final class PetPerks {
    private static final ThreadLocal<Boolean> GUARD = ThreadLocal.withInitial(() -> false);
    private static final Identifier KNOCKBACK = SkyRpgSkyblock.id("pet_knockback_immunity");
    private static final TagKey<Block> FARMING = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("skyrpg_stats", "farming_fortune_blocks"));

    /** Stack expiries (server ticks) per player and kind ("kill", "bow_hit"). */
    private static final Map<UUID, Map<String, List<Long>>> STACKS = new ConcurrentHashMap<>();
    /** Hits counted for Toss, the club and Fourth Flare, and cooldowns, as "player:name" -> value. */
    private static final Map<String, Long> COUNTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_STAND = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> REKINDLE = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> TOSSED = new ConcurrentHashMap<>();
    private static final Map<UUID, long[]> FLUID = new ConcurrentHashMap<>();
    private static final Map<UUID, Cached> MINION = new ConcurrentHashMap<>();
    private static final Set<UUID> FLYING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Ignite> IGNITED = new HashMap<>();

    private record Cached(long tick, List<PetAbilities.Perk> perks) {
    }

    private record Ignite(LivingEntity target, UUID owner, long until, double damage) {
    }

    private PetPerks() {
    }

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            ServerPlayer player = killer(source);
            if (player != null && !(entity instanceof Player)) {
                onKill(player, entity);
            }
        });
        PlayerBlockBreakEvents.AFTER.register(PetPerks::onBreak);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(PetPerks::afterDamage);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(PetPerks::allowDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register(PetPerks::allowDeath);
        SkillLevels.XP_EARNED.register(PetPerks::moreXp);
        Stats.registerManaCost((player, cost) -> {
            double kept = 1 - PetAbilities.perk(player, "mana_cost", "") / 100;
            return player.getRandom().nextDouble() * 100 < PetAbilities.perk(player, "mana_free", "") ? 0 : cost * Math.max(0, kept);
        });
        MinionSpeed.register(PetPerks::minionSpeed);
        ServerTickEvents.END_SERVER_TICK.register(PetPerks::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUUID();
            STACKS.remove(id);
            LAST_STAND.remove(id);
            REKINDLE.remove(id);
            TOSSED.remove(id);
            FLUID.remove(id);
            MINION.remove(id);
            FLYING.remove(id);
        });
    }

    private static long now(ServerPlayer player) {
        return player.level().getServer().getTickCount();
    }

    private static ServerPlayer killer(DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        return source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner ? owner : null;
    }

    // ---------------------------------------------------------------- stats

    /** The stats a perk gives right now (tools and stacks; Rekindle's Strength). */
    static void contribute(ServerPlayer player, PetAbilities.Effect e, double value, ItemStack pet, BiConsumer<String, Double> add) {
        switch (e.perk()) {
            case "tool_buff" -> {
                if (holds(player.getMainHandItem(), e.arg())) {
                    add.accept(e.stat(), value);
                }
            }
            case "stacks" -> {
                double cap = e.capNum() >= 0 ? SkyBlockPets.number(pet, e.capNum()) : e.cap();
                int count = (int) Math.min(cap, stacks(player, e.arg()));
                if (count > 0) {
                    add.accept(e.stat(), value * count);
                }
            }
            case "rekindle" -> {
                if (REKINDLE.getOrDefault(player.getUUID(), 0L) > now(player)) {
                    add.accept("strength", value);
                }
            }
            default -> {
            }
        }
    }

    /** A tool a "tool_buff" is for: "item:<SkyBlock id>", "tag:axes" or "contains:DRILL". */
    private static boolean holds(ItemStack stack, String kind) {
        if (stack.isEmpty()) {
            return false;
        }
        String id = SkyItems.skyblockId(stack);
        if (kind.startsWith("item:")) {
            return kind.substring(5).equals(id);
        }
        if (kind.startsWith("contains:")) {
            return id != null && id.contains(kind.substring(9));
        }
        return kind.equals("tag:axes") && stack.is(ItemTags.AXES);
    }

    private static int stacks(ServerPlayer player, String kind) {
        Map<String, List<Long>> own = STACKS.get(player.getUUID());
        List<Long> list = own == null ? null : own.get(kind);
        if (list == null) {
            return 0;
        }
        long now = now(player);
        return (int) list.stream().filter(until -> until > now).count();
    }

    private static void addStack(ServerPlayer player, String kind) {
        for (PetAbilities.Perk perk : PetAbilities.perks(player, "stacks")) {
            if (perk.effect().arg().equals(kind)) {
                STACKS.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>()).computeIfAbsent(kind, k -> new ArrayList<>())
                        .add(now(player) + (long) (perk.effect().seconds() * 20));
                Stats.refresh(player);
                return;
            }
        }
    }

    static boolean nearFluid(ServerPlayer player, boolean lava) {
        long now = now(player);
        long[] cached = FLUID.get(player.getUUID());
        if (cached == null || now - cached[0] >= 20) {
            boolean water = false;
            boolean hot = false;
            BlockPos at = player.blockPosition();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int dx = -3; dx <= 3 && !(water && hot); dx++) {
                for (int dy = -1; dy <= 2; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        var fluid = player.level().getFluidState(pos.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz));
                        water |= fluid.is(FluidTags.WATER);
                        hot |= fluid.is(FluidTags.LAVA);
                    }
                }
            }
            cached = new long[] {now, water ? 1 : 0, hot ? 1 : 0};
            FLUID.put(player.getUUID(), cached);
        }
        return cached[1] == 1 || lava && cached[2] == 1;
    }

    /** How many living things of this kind are within {@code radius} blocks of the player. */
    static int nearby(ServerPlayer player, String kind, int radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && PetAbilities.is(e, kind)).size();
    }

    // ---------------------------------------------------------------- kills

    private static double baseXp(Entity entity) {
        double own = MobTags.combatXp(entity);
        return own >= 0 ? own : MobLevels.level(entity) * 1.5;
    }

    private static void onKill(ServerPlayer player, LivingEntity mob) {
        double base = baseXp(mob);
        for (PetAbilities.Perk perk : PetAbilities.perks(player, "wisdom_vs")) {
            if (base > 0 && PetAbilities.is(mob, perk.effect().arg())) {
                SkillLevels.giveXp(player, SkillLevels.COMBAT, base * perk.value() / 100);
            }
        }
        for (PetAbilities.Perk perk : PetAbilities.perks(player, "xp_mult_vs")) {
            if (base > 0 && PetAbilities.is(mob, perk.effect().arg()) && perk.value() > 1) {
                SkillLevels.giveXp(player, SkillLevels.COMBAT, base * (perk.value() - 1));
            }
        }
        long coins = Math.round(PetAbilities.perk(player, "coins_kill", "")) + Math.round(PetAbilities.perk(player, "coins_level", "") * MobLevels.level(mob));
        if (coins > 0) {
            Wallet.give(player, coins);
        }
        if (mob.level() instanceof ServerLevel level) {
            if (mob.getType() == EntityTypes.CHICKEN && PetAbilities.has(player, "egg_drop")) {
                mob.spawnAtLocation(level, new ItemStack(Items.EGG));
            }
            if (mob instanceof Animal && player.getRandom().nextDouble() * 100 < PetAbilities.perk(player, "animal_extra", "")) {
                Item extra = animalDrop(mob);
                if (extra != null) {
                    mob.spawnAtLocation(level, new ItemStack(extra));
                }
            }
            if (mob.getType() == EntityTypes.SQUID && player.getRandom().nextDouble() * 100 < PetAbilities.perk(player, "mob_double", "squid")) {
                mob.spawnAtLocation(level, new ItemStack(Items.INK_SAC, 1 + player.getRandom().nextInt(3)));
            }
        }
        addStack(player, "kill");
    }

    /** The drop of one kind of animal that "an additional item" gives. */
    private static Item animalDrop(LivingEntity animal) {
        var type = animal.getType();
        if (type == EntityTypes.CHICKEN) {
            return Items.FEATHER;
        } else if (type == EntityTypes.COW || type == EntityTypes.MOOSHROOM) {
            return Items.LEATHER;
        } else if (type == EntityTypes.PIG) {
            return Items.PORKCHOP;
        } else if (type == EntityTypes.SHEEP) {
            return Items.MUTTON;
        } else if (type == EntityTypes.RABBIT) {
            return Items.RABBIT_HIDE;
        }
        return null;
    }

    // ---------------------------------------------------------------- blocks

    private static void onBreak(Level level, Player who, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel server) || !(who instanceof ServerPlayer player) || player.isCreative()) {
            return;
        }
        if (state.is(FARMING) && (!(state.getBlock() instanceof CropBlock crop) || crop.isMaxAge(state))) {
            for (PetAbilities.Perk perk : PetAbilities.perks(player, "crop_drop")) {
                if (player.getRandom().nextDouble() * 100 < perk.value()) {
                    String[] items = perk.effect().arg().split(",");
                    ItemStack drop = drop(items[player.getRandom().nextInt(items.length)]);
                    if (!drop.isEmpty()) {
                        Block.popResource(server, pos, drop);
                    }
                }
            }
        }
        String block = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        for (PetAbilities.Perk perk : PetAbilities.perks(player, "block_extra")) {
            if (perk.effect().arg().equals(block) && player.getRandom().nextDouble() * 100 < perk.value()) {
                for (ItemStack extra : Block.getDrops(state, server, pos, blockEntity, player, player.getMainHandItem())) {
                    Block.popResource(server, pos, extra);
                }
            }
        }
    }

    /** A vanilla item ("minecraft:red_mushroom") or a SkyBlock one ("DUNG"). */
    private static ItemStack drop(String id) {
        if (id.contains(":")) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
        }
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? ItemStack.EMPTY : SkyItems.create(def, 1);
    }

    // ---------------------------------------------------------------- hits

    /** The multiplier on a hit a player deals: Last Stand's +40%, Toss's 5x. */
    static double dealt(ServerPlayer attacker, LivingEntity target, DamageSource source) {
        double multiplier = 1;
        long now = now(attacker);
        if (LAST_STAND.getOrDefault(attacker.getUUID(), 0L) > now && PetAbilities.has(attacker, "last_stand")) {
            multiplier *= 1.4;
        }
        if (melee(source, attacker) && PetAbilities.has(attacker, "toss")) {
            long hits = COUNTS.merge(attacker.getUUID() + ":toss", 1L, Long::sum);
            if (hits >= 5 && COUNTS.getOrDefault(attacker.getUUID() + ":toss_ready", 0L) <= now) {
                COUNTS.put(attacker.getUUID() + ":toss", 0L);
                COUNTS.put(attacker.getUUID() + ":toss_ready", now + 100);
                TOSSED.put(attacker.getUUID(), target.getUUID());
                multiplier *= 5;
            }
        }
        return multiplier;
    }

    /** The multiplier on damage a player takes. */
    static double taken(ServerPlayer defender, DamageSource source) {
        double multiplier = 1;
        if (source.is(DamageTypeTags.IS_FALL)) {
            multiplier *= 1 - Math.min(100, PetAbilities.perk(defender, "fall_reduction", "")) / 100;
        }
        if (defender.getAbsorptionAmount() > 0) {
            multiplier *= 1 - PetAbilities.perk(defender, "absorption_reduction", "") / 100;
        }
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != defender) {
            for (PetAbilities.Perk perk : PetAbilities.perks(defender, "blizzard")) {
                if (attacker.distanceTo(defender) <= perk.value()) {
                    multiplier *= 1 - perk.second() / 100;
                }
            }
        }
        if (PetAbilities.has(defender, "last_stand")) {
            long now = now(defender);
            boolean low = defender.getHealth() < defender.getMaxHealth() * 0.2f;
            boolean active = LAST_STAND.getOrDefault(defender.getUUID(), 0L) > now;
            if (low && !active && COUNTS.getOrDefault(defender.getUUID() + ":last_stand", 0L) <= now) {
                LAST_STAND.put(defender.getUUID(), now + 240);
                COUNTS.put(defender.getUUID() + ":last_stand", now + 1200);
                defender.setAbsorptionAmount(defender.getMaxHealth() * 0.4f);
                Notice.of(Notice.Kind.PET).tag("LAST STAND").send(defender, Component.literal("Your Golem shields you for 12 seconds!")
                        .withStyle(ChatFormatting.GRAY));
                active = true;
            }
            if (low || active) {
                multiplier *= 0.8;
            }
        }
        return multiplier;
    }

    private static boolean melee(DamageSource source, ServerPlayer attacker) {
        return source.getDirectEntity() == attacker && source.is(DamageTypes.PLAYER_ATTACK);
    }

    private static void afterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
        if (taken <= 0 || GUARD.get()) {
            return;
        }
        if (source.getEntity() instanceof ServerPlayer attacker && entity != attacker && !(entity instanceof Player)) {
            if (melee(source, attacker)) {
                onMelee(attacker, entity, source, taken);
            } else if (source.getDirectEntity() instanceof AbstractArrow) {
                addStack(attacker, "bow_hit");
            }
        }
        if (entity instanceof ServerPlayer defender && source.getEntity() instanceof LivingEntity hitter && hitter != defender
                && entity.level() instanceof ServerLevel level) {
            for (PetAbilities.Perk perk : PetAbilities.perks(defender, "ricochet")) {
                if (defender.getRandom().nextDouble() * 100 < perk.value()) {
                    GUARD.set(true);
                    try {
                        hitter.hurtServer(level, defender.damageSources().thorns(defender), taken);
                    } finally {
                        GUARD.set(false);
                    }
                }
            }
        }
    }

    private static void onMelee(ServerPlayer attacker, LivingEntity target, DamageSource source, float taken) {
        long now = now(attacker);
        UUID tossed = TOSSED.remove(attacker.getUUID());
        if (target.getUUID().equals(tossed)) {
            target.push(0, 0.9, 0);
        }
        for (PetAbilities.Perk perk : PetAbilities.perks(attacker, "hit_slow")) {
            int level = Math.max(0, (int) Math.round(perk.value() / 15) - 1);
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, level, false, true));
        }
        if (PetAbilities.has(attacker, "hit_wither") && !target.hasEffect(MobEffects.WITHER)) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0, false, true));
        }
        for (PetAbilities.Perk perk : PetAbilities.perks(attacker, "tail_slam")) {
            if (COUNTS.merge(attacker.getUUID() + ":tail", 1L, Long::sum) >= 5) {
                COUNTS.put(attacker.getUUID() + ":tail", 0L);
                GUARD.set(true);
                try {
                    for (LivingEntity other : attacker.level().getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(5),
                            e -> e != target && e != attacker && e.isAlive() && e instanceof Enemy)) {
                        TrueDamage.ability(other, attacker.damageSources().indirectMagic(attacker, attacker),
                                taken * StatTypes.HEALTH_SCALE * perk.value() / 100);
                    }
                } finally {
                    GUARD.set(false);
                }
            }
        }
        for (PetAbilities.Perk perk : PetAbilities.perks(attacker, "fourth_flare")) {
            if (COUNTS.merge(attacker.getUUID() + ":flare", 1L, Long::sum) >= 4) {
                COUNTS.put(attacker.getUUID() + ":flare", 0L);
                IGNITED.put(target.getUUID(), new Ignite(target, attacker.getUUID(), now + (long) (perk.second() * 20),
                        perk.value() * Stats.get(attacker, StatTypes.CRIT_DAMAGE)));
            }
        }
    }

    /** Falling 20 or more blocks with a Horse out: the fall is absorbed and tramples the mobs around you. */
    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL) || amount < 17) {
            return true;
        }
        List<PetAbilities.Perk> trample = PetAbilities.perks(player, "trample");
        if (trample.isEmpty()) {
            return true;
        }
        double fallen = amount + 3;
        double hit = Stats.get(player, StatTypes.DAMAGE) * trample.get(0).value() / 100 * fallen;
        for (LivingEntity near : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3),
                e -> e != player && e.isAlive() && e instanceof Enemy)) {
            TrueDamage.ability(near, player.damageSources().indirectMagic(player, player), hit);
        }
        return false;
    }

    /** The Phoenix's Rekindle: before death, you're immune and stronger for a few seconds (60s cooldown). */
    private static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return true;
        }
        List<PetAbilities.Perk> rekindle = PetAbilities.perks(player, "rekindle");
        long now = now(player);
        if (rekindle.isEmpty() || COUNTS.getOrDefault(player.getUUID() + ":rekindle", 0L) > now) {
            return true;
        }
        double seconds = rekindle.get(0).second();
        COUNTS.put(player.getUUID() + ":rekindle", now + 1200);
        REKINDLE.put(player.getUUID(), now + (long) (seconds * 20));
        player.setHealth(Math.max(1, player.getMaxHealth() * 0.2f));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, (int) (seconds * 20), 4, false, false, true));
        Stats.refresh(player);
        Notice.of(Notice.Kind.PET).tag("REKINDLE").send(player, Component.literal("Your Phoenix saved you from death! You are immune for "
                + (int) seconds + " seconds.").withStyle(ChatFormatting.GOLD));
        return false;
    }

    // ---------------------------------------------------------------- elsewhere

    /** More skill XP (Silverfish's Magnetic for Mining, Bingo's Fast Learner for all). */
    private static void moreXp(ServerPlayer player, Identifier skill, double xp) {
        if (GUARD.get() || xp <= 0) {
            return;
        }
        double percent = PetAbilities.perk(player, "skill_xp", skill.getPath()) + PetAbilities.perk(player, "skill_xp", "all");
        if (percent <= 0) {
            return;
        }
        // The wisdom that was in xp is added again when the bonus is given: take it out.
        StatType wisdom = StatTypes.get(skill.getPath() + "_wisdom");
        double wise = wisdom == null ? 1 : 1 + Stats.get(player, wisdom) / 100;
        GUARD.set(true);
        try {
            SkillLevels.giveXp(player, skill, xp * percent / 100 / wise);
        } finally {
            GUARD.set(false);
        }
    }

    private static double minionSpeed(UUID profile, String type, String category, Level level, BlockPos pos) {
        Profile owner = Profiles.get(profile);
        if (owner == null || !(level instanceof ServerLevel server) || !Islands.isIslands(level)) {
            return 0;
        }
        double best = 0;
        for (UUID member : owner.members()) {
            ServerPlayer player = server.getServer().getPlayerList().getPlayer(member);
            Profile at = player == null ? null : Islands.ownerAt(player.blockPosition());
            if (player == null || player.level() != level || at == null || !at.id().equals(profile)) {
                continue;
            }
            Cached cached = MINION.get(member);
            long now = now(player);
            if (cached == null || now - cached.tick() >= 20) {
                cached = new Cached(now, PetAbilities.perks(player, "minion_speed"));
                MINION.put(member, cached);
            }
            double total = 0;
            for (PetAbilities.Perk perk : cached.perks()) {
                if (minionIs(perk.effect().arg(), type, category)) {
                    total += perk.value();
                }
            }
            best = Math.max(best, total);
        }
        return best;
    }

    /** "category:foraging" or "type:slime,magma_cube". */
    private static boolean minionIs(String kind, String type, String category) {
        if (kind.startsWith("category:")) {
            return kind.substring(9).equalsIgnoreCase(category);
        }
        for (String id : kind.substring(kind.indexOf(':') + 1).split(",")) {
            if (id.equalsIgnoreCase(type)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- ticks

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % 20 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            knockback(player);
            flight(player);
            jumpBoost(player);
            blizzard(player);
            purge(player, now);
            if (now % 60 == 0) {
                laserbeam(player);
            }
            if (now % 100 == 0) {
                skeletalDefense(player);
            }
        }
        burn(server, now);
    }

    private static void knockback(ServerPlayer player) {
        PetItems.Effect held = PetItems.effect(SkyBlockPets.heldItem(Pets.active(player)));
        AttributeInstance attribute = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (attribute == null) {
            return;
        }
        if (PetAbilities.has(player, "knockback_immune") || held != null && held.knockbackImmune()) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(KNOCKBACK, 1.0, AttributeModifier.Operation.ADD_VALUE));
        } else if (attribute.getModifier(KNOCKBACK) != null) {
            attribute.removeModifier(KNOCKBACK);
        }
    }

    /** The Phoenix's Magic Bird: flight on your island and garden. */
    private static void flight(ServerPlayer player) {
        boolean wants = PetAbilities.has(player, "fly_island") && (Islands.isIslands(player.level()) || Garden.isGarden(player.level()));
        if (wants && !player.isCreative()) {
            if (FLYING.add(player.getUUID()) || !player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
        } else if (FLYING.remove(player.getUUID()) && !player.isCreative()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    private static void jumpBoost(ServerPlayer player) {
        double level = 0;
        for (PetAbilities.Perk perk : PetAbilities.perks(player, "jump_boost")) {
            level = Math.max(level, perk.effect().amount());
        }
        if (level > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, (int) level - 1, true, false, false));
        }
    }

    /** The Snowman's Blizzard: enemies within its reach are slowed. */
    private static void blizzard(ServerPlayer player) {
        for (PetAbilities.Perk perk : PetAbilities.perks(player, "blizzard")) {
            for (LivingEntity near : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(perk.value()),
                    e -> e != player && e.isAlive() && e instanceof Enemy)) {
                near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, false, false));
            }
        }
    }

    /** Takes out stacks that ran out, and Rekindle's Strength, and tells the stats. */
    private static void purge(ServerPlayer player, long now) {
        boolean changed = false;
        Map<String, List<Long>> own = STACKS.get(player.getUUID());
        if (own != null) {
            for (List<Long> list : own.values()) {
                changed |= list.removeIf(until -> until <= now);
            }
        }
        Long rekindle = REKINDLE.get(player.getUUID());
        if (rekindle != null && rekindle <= now) {
            REKINDLE.remove(player.getUUID());
            changed = true;
        }
        if (changed) {
            Stats.refresh(player);
        }
    }

    private static List<LivingEntity> enemies(ServerPlayer player, double range) {
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                e -> e != player && e.isAlive() && e instanceof Enemy && e.distanceTo(player) <= range).stream()
                .sorted(Comparator.comparingDouble(e -> e.distanceToSqr(player))).toList();
    }

    /** The Guardian's Laserbeam: every 3 seconds, a zap of some times your Intelligence to the nearest enemies. */
    private static void laserbeam(ServerPlayer player) {
        for (PetAbilities.Perk perk : PetAbilities.perks(player, "laserbeam")) {
            double damage = perk.value() * Stats.get(player, StatTypes.INTELLIGENCE);
            enemies(player, 8).stream().limit(3).forEach(target ->
                    TrueDamage.ability(target, player.damageSources().indirectMagic(player, player), damage));
        }
    }

    /** The Skeleton's arrow, 30x your Crit Damage at whatever gets close (every 5 seconds). */
    private static void skeletalDefense(ServerPlayer player) {
        for (PetAbilities.Perk perk : PetAbilities.perks(player, "skeletal_defense")) {
            enemies(player, 4).stream().findFirst().ifPresent(target ->
                    TrueDamage.ability(target, player.damageSources().indirectMagic(player, player),
                            perk.value() * Stats.get(player, StatTypes.CRIT_DAMAGE)));
        }
    }

    /** Fourth Flare's burning mobs: its Crit Damage multiple every second. */
    private static void burn(MinecraftServer server, long now) {
        IGNITED.values().removeIf(fire -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(fire.owner());
            if (owner == null || !fire.target().isAlive() || fire.target().isRemoved() || fire.until() <= now) {
                return true;
            }
            fire.target().setRemainingFireTicks(30);
            TrueDamage.ability(fire.target(), owner.damageSources().indirectMagic(owner, owner), fire.damage());
            return false;
        });
    }
}
