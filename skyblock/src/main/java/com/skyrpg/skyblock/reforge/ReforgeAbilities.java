package com.skyrpg.skyblock.reforge;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.stats.Crits;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.reforge.Reforges;
import com.skyrpg.skyblock.crimson.CrimsonIsle;
import com.skyrpg.skyblock.crimson.Kuudra;
import com.skyrpg.skyblock.dungeon.Catacombs;
import com.skyrpg.skyblock.dungeon.DungeonXp;
import com.skyrpg.skyblock.garden.Garden;
import com.skyrpg.skyblock.mining.SkyMining;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hypixel's reforge abilities (the {@code ability} text of {@code skyrpg/reforges/*.json}), for the reforge of the item in
 * your main hand and of each armour piece you wear; every item that has the reforge counts, so two Perfect pieces give
 * 2% each.
 * <ul>
 *   <li>Stats: Ancient (+1 Crit Damage per Catacombs level), Withered (+1 Strength per Catacombs level), Loving (+5
 *       Ability Damage), Perfect (+2% Defense), Renowned (+1 Magic Find and +1% of Strength, Crit Damage, Crit Chance,
 *       Defense and Health), Empowered (+10 Mending in the Catacombs), Fruitful (+75 Defense on the mining islands),
 *       Fleet (+50 Mining Spread), Auspicious (+1% Mining Fortune).</li>
 *   <li>Damage: Calcified (+25 Defense against sea creatures), Cubic (-2% damage from Crimson Isle and Kuudra mobs),
 *       Undead (-2% from undead), Fanged (every 7th melee hit +100%), Headstrong (+8%) and Precise (+10%) for arrows
 *       that hit a mob's head, Buzzing (x2 against Garden pests), Fabled (a crit deals up to +15% more).</li>
 *   <li>Rewards: Gilded (a kill may pay coins), Bountiful (+0.2 coins a crop harvested), Magnetic (double the XP orbs
 *       a mined block gives), Refined and Blessed (0.25% to find the enchanted form of a mined ore or harvested
 *       crop), Stiff (+1 Chum from a sea creature you kill).</li>
 * </ul>
 *
 * <p>Decided where the text is loose or a system differs: the percent stats (Perfect, Renowned, Auspicious) are a share
 * of what your held item and armour give (not of the total, which would feed back into itself); "Infernal mobs" are
 * the mobs of Crimson Isle and Kuudra; Fabled rolls its extra damage anywhere from 0 to 15% on every crit; a Gilded kill
 * has a 1% chance of 50,000 coins for the killer; "a mob's head" is an arrow landing within 0.3 blocks of the eyes or
 * above them. Skipped: the reforges whose systems aren't in (Heat, Cold, Wisp, Griffin burrows, Wormholes, the Feast,
 * the Spooky Festival's candy, Mining Events, Fallen Stars, Hunting shards, Thorns, Jerry's Workshop and the rest).
 */
public final class ReforgeAbilities {
    private static final List<String> MINING_ISLANDS = List.of("gold_mine", "deep_caverns", "dwarven_mines", "crystal_hollows",
            "glacite_tunnels", "glacite_mineshafts");
    private static final String SEA_CREATURE_TAG = "skyrpg_sea_creature";
    private static final String PEST_TAG = "skyrpg_garden_pest";
    private static final String KUUDRA_TAG = "skyrpg_kuudra";
    private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Each player's melee hits so far (Fanged), and the coins Bountiful hasn't paid out yet. */
    private static final Map<UUID, Integer> HITS = new HashMap<>();
    private static final Map<UUID, Double> COIN_CHANGE = new HashMap<>();
    /** The tick a player last broke a block with Magnetic, whose XP orbs it doubles. */
    private static final Map<UUID, Long> BROKE = new HashMap<>();
    private static final ThreadLocal<Boolean> DOUBLING = ThreadLocal.withInitial(() -> false);

    private ReforgeAbilities() {
    }

    public static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Reforge abilities";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                stats(player, out);
            }
        });
        IncomingDamage.registerHit(ReforgeAbilities::damageFactor);
        ServerLivingEntityEvents.AFTER_DEATH.register(ReforgeAbilities::killed);
        Garden.HARVESTED.register(ReforgeAbilities::harvested);
        SkyMining.MINED.register((player, state, zone) -> mined(player, state));
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer miner && count(miner, "magnetic") > 0) {
                BROKE.put(miner.getUUID(), level.getGameTime());
            }
            return true;
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ExperienceOrb orb && !DOUBLING.get()) {
                doubleXp(orb, level);
            }
        });
    }

    // ---------------------------------------------------------------- what counts

    /** The stacks whose reforge counts: the main hand and the worn armour. */
    private static List<ItemStack> stacks(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>(5);
        out.add(player.getMainHandItem());
        for (EquipmentSlot slot : WORN) {
            out.add(player.getItemBySlot(slot));
        }
        return out;
    }

    /** How many of those items have this reforge. */
    private static int count(ServerPlayer player, String modifier) {
        int count = 0;
        for (ItemStack stack : stacks(player)) {
            Reforges.Reforge reforge = stack.isEmpty() ? null : ItemMods.reforge(stack);
            if (reforge != null && reforge.modifier().equalsIgnoreCase(modifier)) {
                count++;
            }
        }
        return count;
    }

    /** What the held item and armour give of a stat before any ability, for the percent reforges. */
    private static double base(ServerPlayer player, StatType stat) {
        double total = 0;
        for (ItemStack stack : stacks(player)) {
            ItemDefinition def = stack.isEmpty() ? null : SkyItems.definition(stack);
            if (def != null) {
                total += ItemMods.stats(stack, def).getOrDefault(stat, 0.0);
            }
        }
        return total;
    }

    private static boolean miningIsland(Level level) {
        return MINING_ISLANDS.contains(level.dimension().identifier().getPath());
    }

    // ---------------------------------------------------------------- stats

    private static void stats(ServerPlayer player, Stats.Contributor out) {
        int ancient = count(player, "ancient");
        int withered = count(player, "withered");
        if (ancient + withered > 0) {
            int level = DungeonXp.level(player, DungeonXp.CATACOMBS);
            out.add(StatTypes.CRIT_DAMAGE, ancient * level);
            out.add(StatTypes.STRENGTH, withered * level);
        }
        out.add(StatTypes.ABILITY_DAMAGE, 5 * count(player, "loving"));
        int perfect = count(player, "perfect");
        if (perfect > 0) {
            out.add(StatTypes.DEFENSE, 0.02 * perfect * base(player, StatTypes.DEFENSE));
        }
        int renowned = count(player, "renowned");
        if (renowned > 0) {
            out.add(StatTypes.MAGIC_FIND, renowned);
            for (StatType stat : new StatType[] {StatTypes.STRENGTH, StatTypes.CRIT_DAMAGE, StatTypes.CRIT_CHANCE, StatTypes.DEFENSE,
                    StatTypes.HEALTH}) {
                out.add(stat, 0.01 * renowned * base(player, stat));
            }
        }
        if (player.level().dimension().equals(Catacombs.LEVEL)) {
            out.add(StatTypes.MENDING, 10 * count(player, "empowered"));
        }
        if (miningIsland(player.level())) {
            out.add(StatTypes.DEFENSE, 75 * count(player, "fruitful"));
        }
        out.add(StatTypes.MINING_SPREAD, 50 * count(player, "fleet"));
        int auspicious = count(player, "auspicious");
        if (auspicious > 0) {
            out.add(StatTypes.MINING_FORTUNE, 0.01 * auspicious * base(player, StatTypes.MINING_FORTUNE));
        }
    }

    // ---------------------------------------------------------------- damage

    private static double damageFactor(LivingEntity target, DamageSource source) {
        if (target instanceof ServerPlayer player) {
            return damageTaken(player, source.getEntity());
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 1;
        }
        double factor = 1;
        if (source.is(DamageTypes.PLAYER_ATTACK)) {
            int fanged = count(player, "fanged");
            if (fanged > 0 && HITS.merge(player.getUUID(), 1, Integer::sum) % 7 == 0) {
                factor *= 1 + fanged;
            }
        } else if (source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof AbstractArrow arrow
                && arrow.getY() >= target.getEyeY() - 0.3) {
            factor *= Math.pow(1.08, count(player, "headstrong")) * Math.pow(1.10, count(player, "precise"));
        }
        if (target.entityTags().contains(PEST_TAG)) {
            factor *= Math.pow(2, count(player, "buzzing"));
        }
        if (Crits.wasCrit() && count(player, "fabled") > 0) {
            factor *= 1 + player.getRandom().nextDouble() * 0.15;
        }
        return factor;
    }

    /** Calcified's Defense against sea creatures, Cubic's and Undead's -2%. */
    private static double damageTaken(ServerPlayer player, Entity attacker) {
        if (attacker == null || attacker == player) {
            return 1;
        }
        double factor = 1;
        int calcified = count(player, "calcified");
        if (calcified > 0 && attacker.entityTags().contains(SEA_CREATURE_TAG)) {
            double defense = Math.max(0, Stats.get(player, StatTypes.DEFENSE));
            factor *= (100 + defense) / (100 + defense + 25 * calcified);
        }
        if (attacker.entityTags().contains(KUUDRA_TAG) || attacker.level().dimension().equals(CrimsonIsle.LEVEL)
                || attacker.level().dimension().equals(Kuudra.LEVEL)) {
            factor *= Math.pow(0.98, count(player, "cubic"));
        }
        if (attacker.is(EntityTypeTags.UNDEAD)) {
            factor *= Math.pow(0.98, count(player, "undead"));
        }
        return factor;
    }

    // ---------------------------------------------------------------- rewards

    private static void killed(LivingEntity entity, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player) || !(entity instanceof Mob)) {
            return;
        }
        int gilded = count(player, "gilded");
        if (gilded > 0 && player.getRandom().nextDouble() < 0.01 * gilded) {
            Wallet.give(player, 50_000);
            player.sendSystemMessage(Component.literal("Gilded: +50,000 coins").withStyle(ChatFormatting.GOLD));
        }
        if (entity.entityTags().contains(SEA_CREATURE_TAG) && hasStiffRod(player)) {
            give(player, "CHUM", ItemGains.Source.FISHING);
        }
    }

    /** Stiff counts from a fishing rod anywhere in your inventory, as the text says "with this rod". */
    private static boolean hasStiffRod(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            Reforges.Reforge reforge = stack.isEmpty() ? null : ItemMods.reforge(stack);
            if (reforge != null && reforge.modifier().equalsIgnoreCase("stiff")) {
                return true;
            }
        }
        return false;
    }

    private static void harvested(ServerPlayer player, String crop, long count) {
        int bountiful = count(player, "bountiful");
        if (bountiful > 0) {
            double change = COIN_CHANGE.merge(player.getUUID(), 0.2 * bountiful * count, Double::sum);
            if (change >= 1) {
                Wallet.give(player, (long) change);
                COIN_CHANGE.put(player.getUUID(), change - (long) change);
            }
        }
        int blessed = count(player, "blessed");
        String enchanted = Garden.enchantedForm(crop);
        if (blessed > 0 && enchanted != null && player.getRandom().nextDouble() < 0.0025 * blessed) {
            give(player, enchanted, ItemGains.Source.FARMING);
        }
    }

    private static void mined(ServerPlayer player, BlockState state) {
        int refined = count(player, "refined");
        if (refined == 0 || player.getRandom().nextDouble() >= 0.0025 * refined) {
            return;
        }
        SkyMining.Metal metal = SkyMining.metal(player.level(), state);
        String name = metal != null ? metal.item() : state.getBlock().builtInRegistryHolder().key().identifier().getPath()
                .replace("deepslate_", "").replace("nether_", "").replace("_ore", "");
        if (name.equals("lapis")) {
            name = "lapis_lazuli";
        }
        give(player, "ENCHANTED_" + name.toUpperCase(Locale.ROOT), ItemGains.Source.MINING);
    }

    /** One of an item, if SkyBlock id exists. */
    private static void give(ServerPlayer player, String skyblockId, ItemGains.Source source) {
        ItemDefinition def = ItemRegistry.skyblock(skyblockId);
        if (def != null) {
            ItemGains.give(player, SkyItems.create(def, 1), source);
        }
    }

    /** Magnetic: an XP orb from the block you just broke comes with another as big. */
    private static void doubleXp(ExperienceOrb orb, ServerLevel level) {
        if (BROKE.isEmpty()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            Long tick = BROKE.get(player.getUUID());
            if (tick != null && tick == level.getGameTime() && player.distanceToSqr(orb) < 64) {
                DOUBLING.set(true);
                try {
                    ExperienceOrb.award(level, orb.position(), orb.getValue() * count(player, "magnetic"));
                } finally {
                    DOUBLING.set(false);
                }
                return;
            }
        }
    }
}
