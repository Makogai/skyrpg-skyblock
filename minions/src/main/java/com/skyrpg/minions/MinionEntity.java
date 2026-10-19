package com.skyrpg.minions;

import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.island.IslandRules;
import com.skyrpg.skyblock.recipe.Recipes;
import com.skyrpg.skyblock.time.SkyBlockTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * A placed minion: a small armour stand in its tier's head, working a 5 by 5 area around it. Every action it either
 * prepares (places its block, plants, grows a tree, spawns its mob) or harvests (mines it, gathers, chops, kills), so
 * two actions make one harvest. Time is real time, so a minion keeps working while its island isn't loaded: when it
 * loads again it catches up (without the show), up to its storage. Right-click opens it.
 *
 * <p>Fuel ({@link MinionFuels}) makes it work faster while it burns, one item after another in real time; Catalysts
 * multiply what it makes instead. The two upgrade slots ({@link MinionUpgrade}) smelt, compact, spread diamonds or add
 * speed, and a hopper in the shipping slot sells what doesn't fit into storage, holding the coins until you collect.
 */
public class MinionEntity extends ArmorStand {
    public static final int RADIUS = 2;
    private static final String MOB_TAG = "skyrpg_minion_mob";

    private String typeId = "";
    private int tier = 1;
    private UUID profile;
    /** The storage grid; a tier uses the first {@link MinionType.Tier#slots()} of these. */
    public static final int MAX_SLOTS = 15;
    private final NonNullList<ItemStack> storage = NonNullList.withSize(MAX_SLOTS, ItemStack.EMPTY);
    private long generated;
    private long lastAction;
    private boolean full;
    /** The fuel slot (one kind, stacked) and when the item burning now runs out (0: nothing burning, or it lasts forever). */
    private ItemStack fuel = ItemStack.EMPTY;
    private long fuelEnds;
    private ItemStack shipping = ItemStack.EMPTY;
    public static final int UPGRADE_SLOTS = 2;
    private final NonNullList<ItemStack> upgrades = NonNullList.withSize(UPGRADE_SLOTS, ItemStack.EMPTY);
    /** What the hopper sold, waiting to be collected. */
    private double heldCoins;
    /** When a Soulflow Engine makes its next Raw Soulflow. */
    private long soulflowAt;

    public MinionEntity(EntityType<? extends MinionEntity> type, Level level) {
        super(type, level);
        entityData.set(DATA_CLIENT_FLAGS, (byte) (CLIENT_FLAG_SMALL | CLIENT_FLAG_SHOW_ARMS | CLIENT_FLAG_NO_BASEPLATE));
        setNoGravity(true);
    }

    /** A new minion of this type and tier, placed by someone playing {@code profile}. */
    void init(MinionType type, int tier, UUID profile) {
        this.typeId = type.id();
        this.tier = tier;
        this.profile = profile;
        this.lastAction = System.currentTimeMillis();
        dress(type);
    }

    public MinionType type() {
        return Minions.type(typeId);
    }

    /** Time passes for this minion: what it did in the last {@code millis} it does now (the Enchanted Clock). */
    void skipTime(long millis) {
        if (lastAction != 0) {
            lastAction -= millis;
        }
        if (fuelEnds != 0) {
            fuelEnds -= millis;
        }
        if (soulflowAt != 0) {
            soulflowAt -= millis;
        }
    }

    public int tier() {
        return tier;
    }

    public UUID profile() {
        return profile;
    }

    public NonNullList<ItemStack> storage() {
        return storage;
    }

    public long generated() {
        return generated;
    }

    public boolean isFull() {
        return full;
    }

    public ItemStack fuel() {
        return fuel;
    }

    /** Puts fuel in (or more of the same); the first item starts burning now. */
    void setFuel(ItemStack stack) {
        boolean wasEmpty = fuel.isEmpty();
        fuel = stack;
        MinionFuels.Fuel burning = burning();
        if (burning == null || burning.forever()) {
            fuelEnds = 0;
        } else if (wasEmpty || fuelEnds == 0) {
            fuelEnds = System.currentTimeMillis() + burning.millis();
        }
    }

    /** The fuel burning now, or null. */
    public MinionFuels.Fuel burning() {
        return fuel.isEmpty() ? null : MinionFuels.get(SkyStacks.id(fuel));
    }

    /** Real milliseconds of fuel left (all the stacked items), -1 for fuel that lasts forever, 0 for none. */
    public long fuelLeft() {
        MinionFuels.Fuel burning = burning();
        if (burning == null) {
            return 0;
        }
        if (burning.forever()) {
            return -1;
        }
        return Math.max(0, fuelEnds - System.currentTimeMillis()) + (fuel.getCount() - 1L) * burning.millis();
    }

    public ItemStack shipping() {
        return shipping;
    }

    void setShipping(ItemStack stack) {
        shipping = stack;
    }

    public NonNullList<ItemStack> upgrades() {
        return upgrades;
    }

    public long heldCoins() {
        return (long) heldCoins;
    }

    /** Takes the whole coins the hopper made (the fraction stays). */
    long takeCoins() {
        long coins = (long) heldCoins;
        heldCoins -= coins;
        return coins;
    }

    /** The upgrades that count: ones that fit this minion, and a second of a kind only for speed upgrades. */
    public List<MinionUpgrade> activeUpgrades() {
        MinionType type = type();
        List<MinionUpgrade> out = new ArrayList<>();
        Set<MinionUpgrade> seen = EnumSet.noneOf(MinionUpgrade.class);
        for (ItemStack stack : upgrades) {
            MinionUpgrade upgrade = MinionUpgrade.of(SkyItems.skyblockId(stack));
            if (upgrade != null && type != null && upgrade.fits(type) && (seen.add(upgrade) || upgrade.stacks())) {
                out.add(upgrade);
            }
        }
        return out;
    }

    /** How much faster than its tier's time it works, in percent: fuel plus speed upgrades. */
    public int speedPercent() {
        MinionFuels.Fuel burning = burning();
        int speed = burning == null ? 0 : burning.speed();
        for (MinionUpgrade upgrade : activeUpgrades()) {
            speed += upgrade.speed();
            if (upgrade == MinionUpgrade.SOLAR_PANEL && SkyBlockTime.now().isDay()) {
                speed += 25;
            }
        }
        return speed;
    }

    /** What its output is multiplied by: a Catalyst's x3, halved by a Soulflow Engine. */
    public double outputMultiplier() {
        MinionFuels.Fuel burning = burning();
        double output = burning == null ? 1 : burning.multiplier();
        List<MinionUpgrade> active = activeUpgrades();
        if (active.contains(MinionUpgrade.LESSER_SOULFLOW_ENGINE) || active.contains(MinionUpgrade.SOULFLOW_ENGINE)) {
            output *= 0.5;
        }
        // Derpy's TURBO MINIONS!!!
        if (com.skyrpg.core.world.Mayor.active("turbo_minions")) {
            output *= 2;
        }
        return output;
    }

    /** The share of an NPC price its hopper sells for, 0 without one. */
    public int sellPercent() {
        return MinionUpgrade.sellPercent(SkyItems.skyblockId(shipping));
    }

    /** How far it reaches: 2 blocks (a 5 by 5), and one more per Minion Expander. */
    public int radius() {
        int radius = RADIUS;
        for (MinionUpgrade upgrade : activeUpgrades()) {
            if (upgrade == MinionUpgrade.MINION_EXPANDER) {
                radius++;
            }
        }
        return radius;
    }

    /** Seconds between actions now, with fuel and upgrades. */
    public float actionSeconds() {
        MinionType type = type();
        return type == null ? 0 : actionMillis(type) / 1000f;
    }

    /** 0-1 of the way to the next action, for the screen's timer. */
    public float progress() {
        MinionType type = type();
        if (type == null) {
            return 0;
        }
        long actionMs = actionMillis(type);
        return Math.min(1f, (System.currentTimeMillis() - lastAction) / (float) actionMs);
    }

    private long actionMillis(MinionType type) {
        // Cole's Prospection: mining minions 25% faster
        double prospection = type.category().equals("mining") && com.skyrpg.core.world.Mayor.active("prospection") ? 25 : 0;
        return Math.max(1000L, Math.round(type.tier(tier).actionSeconds() * 1000 / (1 + (speedPercent() + prospection) / 100.0)));
    }

    /** Its tier's head, leather in its category's colour, and the tool of its trade. */
    void dress(MinionType type) {
        ItemDefinition head = ItemRegistry.skyblock(type.tier(tier).item());
        if (head != null) {
            setItemSlot(EquipmentSlot.HEAD, SkyItems.create(head, 1));
        }
        int color = switch (type.category()) {
            case "mining" -> 0x8C8C8C;
            case "farming" -> 0xC9B44C;
            case "combat", "slayer" -> 0xA83232;
            case "foraging" -> 0x7A5230;
            case "fishing" -> 0x3F76E4;
            default -> 0x8A5FC9;
        };
        setItemSlot(EquipmentSlot.CHEST, dyed(new ItemStack(Items.LEATHER_CHESTPLATE), color));
        setItemSlot(EquipmentSlot.LEGS, dyed(new ItemStack(Items.LEATHER_LEGGINGS), color));
        setItemSlot(EquipmentSlot.FEET, dyed(new ItemStack(Items.LEATHER_BOOTS), color));
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(switch (type.kind()) {
            case BLOCK -> Items.WOODEN_PICKAXE;
            case CROP -> Items.WOODEN_HOE;
            case TREE -> Items.WOODEN_AXE;
            case MOB -> Items.WOODEN_SWORD;
            case FISHING -> Items.FISHING_ROD;
        }));
    }

    private static ItemStack dyed(ItemStack stack, int color) {
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0xFF000000 | color));
        return stack;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel server) {
            work(server);
        }
    }

    private void work(ServerLevel level) {
        MinionType type = type();
        if (type == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastAction == 0) {
            lastAction = now;
        }
        soulflow(type, now);
        // Up to now in stretches: the speed changes when a fuel item runs out (the next one starts, or none is left).
        while (true) {
            MinionFuels.Fuel burning = burning();
            if (burning != null && !burning.forever() && fuelEnds == 0) {
                fuelEnds = lastAction + burning.millis(); // fuel put in some other way (a command): it starts now
            }
            long end = burning != null && !burning.forever() && fuelEnds > 0 ? Math.min(now, fuelEnds) : now;
            long actionMs = actionMillis(type);
            long due = (end - lastAction) / actionMs;
            if (due > 2) {
                // It was working while nobody was here: two actions make one harvest.
                produce(type, due / 2, level.getRandom());
                lastAction += due * actionMs;
            } else if (due > 0) {
                lastAction += actionMs;
                act(level, type);
            }
            if (end >= now) {
                return;
            }
            burnNext(end);
        }
    }

    /** The burning fuel item ran out at {@code at}: the next one in the slot starts. */
    private void burnNext(long at) {
        MinionFuels.Fuel burning = burning();
        fuel.shrink(1);
        if (fuel.isEmpty() || burning == null) {
            fuel = ItemStack.EMPTY;
            fuelEnds = 0;
        } else {
            fuelEnds = at + burning.millis();
        }
    }

    /** A Soulflow Engine makes Raw Soulflow on its own clock, however fast the minion works. */
    private void soulflow(MinionType type, long now) {
        long every = 0;
        for (MinionUpgrade upgrade : activeUpgrades()) {
            if (upgrade.soulflowMillis() > 0 && (every == 0 || upgrade.soulflowMillis() < every)) {
                every = upgrade.soulflowMillis();
            }
        }
        if (every == 0) {
            soulflowAt = 0;
            return;
        }
        if (soulflowAt == 0) {
            soulflowAt = now + every;
        }
        if (now >= soulflowAt) {
            long made = (now - soulflowAt) / every + 1;
            soulflowAt += made * every;
            store(type, "RAW_SOULFLOW", made, activeUpgrades());
        }
    }

    /** One visible action. */
    private void act(ServerLevel level, MinionType type) {
        if (storedItems() >= type.tier(tier).storage() && sellPercent() == 0) {
            setFull(true);
            return;
        }
        setFull(false);
        BlockPos feet = blockPosition();
        RandomSource random = level.getRandom();
        boolean worked = switch (type.kind()) {
            case BLOCK -> blockStep(level, type, feet.below(), random);
            case CROP -> cropStep(level, type, feet, random);
            case TREE -> treeStep(level, type, feet, random);
            case MOB -> mobStep(level, type, feet, random);
            case FISHING -> {
                if (random.nextBoolean()) {
                    produce(type, 1, random);
                    level.sendParticles(ParticleTypes.SPLASH, getX(), getY(), getZ() + 1, 6, 0.4, 0.1, 0.4, 0.05);
                }
                yield true;
            }
        };
        if (worked) {
            ItemStack tool = getMainHandItem();
            swing(InteractionHand.MAIN_HAND, tool.getOrDefault(DataComponents.ATTACK_ANIMATION, SwingAnimation.DEFAULT), true);
        }
    }

    /** Places its block on an empty spot of the layer it stands on, or mines one it placed. */
    private boolean blockStep(ServerLevel level, MinionType type, BlockPos layer, RandomSource random) {
        List<BlockPos> empty = new ArrayList<>();
        List<BlockPos> ready = new ArrayList<>();
        for (BlockPos pos : area(layer)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                // Never into someone standing there (it pushed a player off the island once).
                if (level.isUnobstructed(type.block().defaultBlockState(), pos, CollisionContext.empty())) {
                    empty.add(pos);
                }
            } else if (state.is(type.block())) {
                ready.add(pos);
            }
        }
        if (!empty.isEmpty()) {
            BlockPos pos = empty.get(random.nextInt(empty.size()));
            level.setBlock(pos, type.block().defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, pos, type.block().defaultBlockState().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6f, 1f);
            return true;
        }
        if (!ready.isEmpty()) {
            BlockPos pos = ready.get(random.nextInt(ready.size()));
            mine(level, pos);
            produce(type, 1, random);
            return true;
        }
        return false;
    }

    /** Plants on soil around it (fully grown, ready next time), or harvests what it planted. */
    private boolean cropStep(ServerLevel level, MinionType type, BlockPos layer, RandomSource random) {
        List<BlockPos> empty = new ArrayList<>();
        List<BlockPos> ready = new ArrayList<>();
        for (BlockPos pos : area(layer)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(type.block())) {
                ready.add(pos);
            } else if (state.isAir() && !level.getBlockState(pos.below()).isAir()) {
                empty.add(pos);
            }
        }
        if (!empty.isEmpty()) {
            BlockPos pos = empty.get(random.nextInt(empty.size()));
            BlockState below = level.getBlockState(pos.below());
            if (type.block() instanceof CropBlock && (below.is(Blocks.DIRT) || below.is(Blocks.GRASS_BLOCK))) {
                level.setBlock(pos.below(), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
            }
            BlockState crop = type.block() instanceof CropBlock cropBlock ? cropBlock.getStateForAge(cropBlock.getMaxAge()) : type.block().defaultBlockState();
            level.setBlock(pos, crop, Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.6f, 1f);
            return true;
        }
        if (!ready.isEmpty()) {
            BlockPos pos = ready.get(random.nextInt(ready.size()));
            mine(level, pos);
            produce(type, 1, random);
            return true;
        }
        return false;
    }

    /** Grows a short trunk of its wood on the ground around it, or chops one down. */
    private boolean treeStep(ServerLevel level, MinionType type, BlockPos layer, RandomSource random) {
        Block log = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(type.wood() + "_log"));
        List<BlockPos> empty = new ArrayList<>();
        List<BlockPos> ready = new ArrayList<>();
        for (BlockPos pos : area(layer)) {
            if (Math.abs(pos.getX() - layer.getX()) < 2 && Math.abs(pos.getZ() - layer.getZ()) < 2) {
                continue; // trees stand on the outer ring
            }
            BlockState state = level.getBlockState(pos);
            if (state.is(log)) {
                ready.add(pos);
            } else if (state.isAir() && level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.below()).isAir()
                    && level.isUnobstructed(log.defaultBlockState(), pos, CollisionContext.empty())
                    && level.isUnobstructed(log.defaultBlockState(), pos.above(), CollisionContext.empty())) {
                empty.add(pos);
            }
        }
        if (!empty.isEmpty()) {
            BlockPos pos = empty.get(random.nextInt(empty.size()));
            level.setBlock(pos, log.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(pos.above(), log.defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6f, 1f);
            return true;
        }
        if (!ready.isEmpty()) {
            BlockPos pos = ready.get(random.nextInt(ready.size()));
            mine(level, pos.above());
            mine(level, pos);
            produce(type, 1, random);
            return true;
        }
        return false;
    }

    /** Spawns its mob on the ground nearby, or finishes off the one it spawned. */
    private boolean mobStep(ServerLevel level, MinionType type, BlockPos feet, RandomSource random) {
        if (type.mob() == null) {
            return false;
        }
        AABB box = new AABB(feet).inflate(radius() + 1, 2, radius() + 1);
        List<Entity> mine = level.getEntities((Entity) null, box, e -> e.isAlive() && e.entityTags().contains(MOB_TAG + "." + getUUID()));
        if (mine.isEmpty()) {
            List<BlockPos> spots = new ArrayList<>();
            for (BlockPos pos : area(feet)) {
                if (level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.below()).isAir()) {
                    spots.add(pos);
                }
            }
            if (spots.isEmpty()) {
                return false;
            }
            Entity mob = type.mob().spawn(level, spots.get(random.nextInt(spots.size())), EntitySpawnReason.SPAWNER);
            if (mob != null) {
                mob.addTag(MOB_TAG);
                mob.addTag(MOB_TAG + "." + getUUID());
                if (mob instanceof Mob m) {
                    m.setNoAi(true);
                    m.setPersistenceRequired();
                }
                mob.setSilent(true);
            }
            return true;
        }
        Entity target = mine.getFirst();
        level.sendParticles(ParticleTypes.POOF, target.getX(), target.getY() + 0.5, target.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
        target.discard();
        produce(type, 1, random);
        return true;
    }

    private void mine(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                12, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.6f, 1f);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** The 5 by 5 (7 by 7 with an expander...) around this layer's centre, without the centre (where the minion is). */
    private List<BlockPos> area(BlockPos center) {
        List<BlockPos> out = new ArrayList<>();
        int radius = radius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx != 0 || dz != 0) {
                    out.add(center.offset(dx, 0, dz));
                }
            }
        }
        return out;
    }

    /**
     * Adds {@code harvests} harvests' worth of drops, as far as storage allows (a hopper sells the rest). Many at once
     * are worked out on average. Upgrades smelt and swap drops first, spreading adds its extras, and compactors squeeze
     * storage down so there's room again.
     */
    private void produce(MinionType type, long harvests, RandomSource random) {
        List<MinionUpgrade> active = activeUpgrades();
        boolean smelt = active.contains(MinionUpgrade.AUTO_SMELTER) || active.contains(MinionUpgrade.DWARVEN_COMPACTOR);
        double output = outputMultiplier();
        long made = 0;
        for (MinionType.Drop drop : type.drops()) {
            String item = drop.item();
            if (active.contains(MinionUpgrade.FLINT_SHOVEL) && "GRAVEL".equals(item)) {
                item = "FLINT";
            }
            if (smelt) {
                item = MinionUpgrade.SMELTS.getOrDefault(item, item);
            }
            double amount;
            if (harvests == 1) {
                if (random.nextDouble() >= drop.chance()) {
                    continue;
                }
                amount = drop.min() + random.nextDouble() * (drop.max() - drop.min());
            } else {
                amount = (drop.min() + drop.max()) / 2 * drop.chance() * harvests;
            }
            made += store(type, item, round(amount * output, random), active);
        }
        if (active.contains(MinionUpgrade.ENCHANTED_EGG)) {
            made += store(type, "EGG", round(harvests * output, random), active);
        }
        if (active.contains(MinionUpgrade.ENCHANTED_SHEARS)) {
            made += store(type, "WOOL", round(harvests * output, random), active);
        }
        // Spreading goes by how many items it made: a Snow Minion's 4 snowballs a harvest spread 4 times as fast.
        if (active.contains(MinionUpgrade.DIAMOND_SPREADING)) {
            store(type, "DIAMOND", round(made * 0.1, random), active);
        }
        if (active.contains(MinionUpgrade.POTATO_SPREADING)) {
            store(type, "POTATO_ITEM", round(made * 0.05, random), active);
        }
        if (active.contains(MinionUpgrade.KRAMPUS_HELMET)) {
            store(type, "RED_GIFT", round(made * 0.000045, random), active);
        }
        setFull(sellPercent() == 0 && storedItems() >= type.tier(tier).storage());
    }

    /** {@code value} as a whole number, the fraction rounding up by chance (2.3 is 3 three times in ten). */
    private static long round(double value, RandomSource random) {
        long whole = (long) Math.floor(value);
        return whole + (random.nextDouble() < value - whole ? 1 : 0);
    }

    /**
     * Puts {@code amount} of an item into storage. Compactors make room when it's full, then the hopper sells what
     * still doesn't fit (without one it's lost). Returns how many were made: stored or sold.
     */
    private long store(MinionType type, String item, long amount, List<MinionUpgrade> active) {
        if (amount <= 0) {
            return 0;
        }
        int capacity = type.tier(tier).storage();
        long left = amount;
        for (int round = 0; left > 0 && round < 64; round++) {
            int room = Math.max(0, capacity - storedItems());
            left -= add(item, (int) Math.min(left, room));
            if (left <= 0 || !compact(active)) {
                break;
            }
        }
        compact(active);
        long sold = left > 0 ? sell(item, left) : 0;
        long madeHere = amount - left + sold;
        generated += madeHere;
        return madeHere;
    }

    /** Sells {@code count} of an item through the hopper; returns how many it sold (0 without a hopper). */
    private long sell(String item, long count) {
        int percent = sellPercent();
        if (percent == 0) {
            return 0;
        }
        heldCoins += count * SkyStacks.npcPrice(item) * percent / 100.0;
        return count;
    }

    /**
     * Compactors at work on storage: the Compactor makes block forms (9 Coal, a Coal Block), the Super Compactor
     * enchanted forms (160 Cobblestone, an Enchanted Cobblestone), going through a block form when that's the way
     * (Snowballs into Snow Blocks into Enchanted Snow Blocks). Returns whether anything changed.
     */
    private boolean compact(List<MinionUpgrade> active) {
        boolean blocks = active.contains(MinionUpgrade.COMPACTOR);
        boolean enchant = active.contains(MinionUpgrade.SUPER_COMPACTOR) || active.contains(MinionUpgrade.DWARVEN_COMPACTOR);
        if (!blocks && !enchant) {
            return false;
        }
        boolean any = false;
        for (int round = 0; round < 8; round++) {
            boolean changed = false;
            for (String item : storedIds()) {
                Recipes.Compaction rule = null;
                if (enchant) {
                    rule = Recipes.enchanted(item);
                    Recipes.Compaction block = rule == null ? Recipes.block(item) : null;
                    if (block != null && Recipes.enchanted(block.into()) != null) {
                        rule = block;
                    }
                }
                if (rule == null && blocks) {
                    rule = Recipes.block(item);
                }
                if (rule == null) {
                    continue;
                }
                int times = countStored(item) / rule.amount();
                if (times > 0) {
                    removeStored(item, times * rule.amount());
                    add(rule.into(), times * rule.count());
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
            any = true;
        }
        return any;
    }

    private Set<String> storedIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (ItemStack stack : storage) {
            String id = SkyItems.skyblockId(stack);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private int countStored(String skyblockId) {
        int total = 0;
        for (ItemStack stack : storage) {
            if (skyblockId.equals(SkyItems.skyblockId(stack))) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private void removeStored(String skyblockId, int count) {
        int left = count;
        for (int i = MAX_SLOTS - 1; i >= 0 && left > 0; i--) {
            ItemStack stack = storage.get(i);
            if (skyblockId.equals(SkyItems.skyblockId(stack))) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
                if (stack.isEmpty()) {
                    storage.set(i, ItemStack.EMPTY);
                }
            }
        }
    }

    /** Puts {@code count} of a SkyBlock item into storage, filling stacks first; returns how many went in. */
    private int add(String skyblockId, int count) {
        ItemDefinition def = ItemRegistry.skyblock(skyblockId);
        if (def == null || count <= 0) {
            return 0;
        }
        MinionType type = type();
        int slots = type == null ? 1 : Math.min(MAX_SLOTS, type.tier(tier).slots());
        int left = count;
        for (int i = 0; i < slots && left > 0; i++) {
            ItemStack stack = storage.get(i);
            if (skyblockId.equals(SkyItems.skyblockId(stack)) && stack.getCount() < stack.getMaxStackSize()) {
                int move = Math.min(left, stack.getMaxStackSize() - stack.getCount());
                stack.grow(move);
                left -= move;
            }
        }
        for (int i = 0; i < slots && left > 0; i++) {
            if (storage.get(i).isEmpty()) {
                ItemStack fresh = SkyItems.create(def, 1);
                int move = Math.min(left, fresh.getMaxStackSize());
                fresh.setCount(move);
                storage.set(i, fresh);
                left -= move;
            }
        }
        return count - left;
    }

    public int storedItems() {
        int total = 0;
        for (ItemStack stack : storage) {
            total += stack.getCount();
        }
        return total;
    }

    /** Takes everything out of storage (for Collect All and picking the minion up). */
    public List<ItemStack> takeAll() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (!storage.get(i).isEmpty()) {
                out.add(storage.get(i));
                storage.set(i, ItemStack.EMPTY);
            }
        }
        setFull(false);
        return out;
    }

    /** The fuel, shipping and upgrade items, taken out (for picking the minion up). The fuel burning now is used up. */
    List<ItemStack> takeParts() {
        MinionFuels.Fuel burning = burning();
        if (burning != null && !burning.forever()) {
            fuel.shrink(1);
        }
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack stack : List.of(fuel, shipping, upgrades.get(0), upgrades.get(1))) {
            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }
        fuel = ItemStack.EMPTY;
        fuelEnds = 0;
        shipping = ItemStack.EMPTY;
        upgrades.set(0, ItemStack.EMPTY);
        upgrades.set(1, ItemStack.EMPTY);
        return out;
    }

    /** After something was taken out of storage by hand. */
    void storageChanged() {
        MinionType type = type();
        setFull(type != null && sellPercent() == 0 && storedItems() >= type.tier(tier).storage());
    }

    void setTier(int tier) {
        this.tier = tier;
        MinionType type = type();
        if (type != null) {
            dress(type);
        }
    }

    private void setFull(boolean full) {
        if (this.full == full) {
            return;
        }
        this.full = full;
        setCustomName(full ? Component.literal("/!\\ My storage is full! :(").withStyle(ChatFormatting.RED) : null);
        setCustomNameVisible(full);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        if (player instanceof ServerPlayer serverPlayer && hand == InteractionHand.MAIN_HAND) {
            if (IslandRules.isMember(serverPlayer, blockPosition()) || serverPlayer.isCreative()) {
                MinionMenu.open(serverPlayer, this);
            } else {
                serverPlayer.sendOverlayMessage(Component.literal("That's not your minion.").withStyle(ChatFormatting.RED));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // Nothing hurts a minion, but /kill (which gets through invulnerability) still removes it.
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("minion", typeId);
        output.putInt("tier", tier);
        output.putLong("generated", generated);
        output.putLong("last_action", lastAction);
        if (profile != null) {
            output.store("profile", UUIDUtil.CODEC, profile);
        }
        output.store("storage", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(storage));
        output.store("fuel", ItemStack.OPTIONAL_CODEC, fuel);
        output.putLong("fuel_ends", fuelEnds);
        output.store("shipping", ItemStack.OPTIONAL_CODEC, shipping);
        output.store("upgrades", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(upgrades));
        output.putDouble("held_coins", heldCoins);
        output.putLong("soulflow_at", soulflowAt);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        typeId = input.getStringOr("minion", "");
        tier = input.getIntOr("tier", 1);
        generated = input.getLongOr("generated", 0);
        lastAction = input.getLongOr("last_action", 0);
        Optional<UUID> owner = input.read("profile", UUIDUtil.CODEC);
        profile = owner.orElse(null);
        for (int i = 0; i < MAX_SLOTS; i++) {
            storage.set(i, ItemStack.EMPTY);
        }
        input.read("storage", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> {
            for (int i = 0; i < Math.min(MAX_SLOTS, list.size()); i++) {
                storage.set(i, list.get(i));
            }
        });
        fuel = input.read("fuel", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        fuelEnds = input.getLongOr("fuel_ends", 0);
        shipping = input.read("shipping", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        upgrades.set(0, ItemStack.EMPTY);
        upgrades.set(1, ItemStack.EMPTY);
        input.read("upgrades", ItemStack.OPTIONAL_CODEC.listOf()).ifPresent(list -> {
            for (int i = 0; i < Math.min(UPGRADE_SLOTS, list.size()); i++) {
                upgrades.set(i, list.get(i));
            }
        });
        heldCoins = input.getDoubleOr("held_coins", 0);
        soulflowAt = input.getLongOr("soulflow_at", 0);
        MinionType type = type();
        full = type != null && sellPercent() == 0 && storedItems() >= type.tier(tier).storage();
    }
}
