package com.skyrpg.skyblock.mining;

import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.world.BlockStrength;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.zone.Zones;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hypixel's mining on the mining islands (every public SkyBlock island but the Hub; not private islands): each block has
 * a Block Strength and a Breaking Power. A tool with less Breaking Power can't break it at all; otherwise it takes
 * round(30 × strength / Mining Speed) ticks, never under 4, unless Mining Speed is over 30 × strength (60 × for ores,
 * pure ores, obsidian and Dwarven metals), which mines it at once. The client uses the same rule for the cracks.
 *
 * <p>Mithril and Titanium are mined here too: Hypixel's items (Mithril 1/2/5 by tier, Titanium 2) with Mining Fortune,
 * Mining XP, Mithril Powder in the Dwarven Mines, and a chance (0.5%, more with Titanium Insanium) that a Mithril block
 * turns into Titanium.
 */
public final class SkyMining {
    public static final ResourceKey<Level> DWARVEN_MINES = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            SkyRpgSkyblock.id("dwarven_mines"));
    public static final ResourceKey<Level> CRYSTAL_HOLLOWS = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
            SkyRpgSkyblock.id("crystal_hollows"));
    private static final Identifier PRIVATE_ISLANDS = SkyRpgSkyblock.id("islands");

    /** A mining block: strength, Breaking Power, whether it's an ore (instamined only at 60 × strength). */
    public record Info(int strength, int power, boolean ore) {
    }

    /** What a block mined here by hand gives: the item and how many, powder (of which kind), Mining XP. */
    public record Metal(String name, String item, int count, int powder, double xp, String powderType) {
        Metal(String name, String item, int count, int powder, double xp) {
            this(name, item, count, powder, xp, Hotm.MITHRIL);
        }
    }

    private static final Map<Block, Info> BLOCKS = new HashMap<>();
    private static final Map<Block, Metal> METALS = new HashMap<>();
    /** The Crystal Hollows' own numbers (Hard Stone is stone there; gemstones). */
    private static final Map<Block, Info> HOLLOWS_BLOCKS = new HashMap<>();
    private static final Map<Block, Metal> HOLLOWS_METALS = new HashMap<>();

    static {
        put(15, 0, false, Blocks.STONE);
        put(20, 0, false, Blocks.COBBLESTONE);
        put(30, 1, true, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
        put(30, 2, true, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.LAPIS_ORE,
                Blocks.DEEPSLATE_LAPIS_ORE, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
        put(30, 3, true, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.NETHER_QUARTZ_ORE);
        put(600, 3, true, Blocks.COAL_BLOCK, Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.LAPIS_BLOCK, Blocks.REDSTONE_BLOCK,
                Blocks.EMERALD_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.QUARTZ_BLOCK);
        put(500, 4, true, Blocks.OBSIDIAN);
        put(30, 1, false, Blocks.END_STONE);
        put(8, 1, false, Blocks.NETHERRACK);
        put(500, 4, true, Blocks.WOOL.pick(DyeColor.GRAY), Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN));
        put(800, 4, true, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE);
        put(1500, 4, true, Blocks.WOOL.pick(DyeColor.LIGHT_BLUE));
        put(2000, 5, true, Blocks.POLISHED_DIORITE);
        metal(new Metal("Mithril", "MITHRIL_ORE", 1, 1, 45), Blocks.WOOL.pick(DyeColor.GRAY), Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN));
        metal(new Metal("Mithril", "MITHRIL_ORE", 2, 2, 45), Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE);
        metal(new Metal("Mithril", "MITHRIL_ORE", 5, 5, 45), Blocks.WOOL.pick(DyeColor.LIGHT_BLUE));
        metal(new Metal("Titanium", "TITANIUM_ORE", 2, 0, 100), Blocks.POLISHED_DIORITE);
        HOLLOWS_BLOCKS.put(Blocks.STONE, new Info(50, 4, false));
        HOLLOWS_METALS.put(Blocks.STONE, new Metal("Hard Stone", "HARD_STONE", 1, 0, 1));
        gem(DyeColor.RED, "Ruby", 2300, 6);
        gem(DyeColor.PURPLE, "Amethyst", 3000, 7);
        gem(DyeColor.LIME, "Jade", 3000, 7);
        gem(DyeColor.ORANGE, "Amber", 3000, 7);
        gem(DyeColor.LIGHT_BLUE, "Sapphire", 3000, 7);
        gem(DyeColor.YELLOW, "Topaz", 3800, 8);
        gem(DyeColor.MAGENTA, "Jasper", 4800, 9);
    }

    /** A gemstone: its stained glass and pane, Hypixel's strength and Breaking Power, a Rough gem and 2 Gemstone Powder. */
    private static void gem(DyeColor color, String name, int strength, int power) {
        Metal metal = new Metal(name, "ROUGH_" + name.toUpperCase(java.util.Locale.ROOT) + "_GEM", 1, 2, 20, Hotm.GEMSTONE);
        for (Block block : new Block[] {Blocks.STAINED_GLASS.pick(color), Blocks.STAINED_GLASS_PANE.pick(color)}) {
            HOLLOWS_BLOCKS.put(block, new Info(strength, power, true));
            HOLLOWS_METALS.put(block, metal);
        }
    }

    private static void put(int strength, int power, boolean ore, Block... blocks) {
        for (Block block : blocks) {
            BLOCKS.put(block, new Info(strength, power, ore));
        }
    }

    private static void metal(Metal metal, Block... blocks) {
        for (Block block : blocks) {
            METALS.put(block, metal);
        }
    }

    /** Someone mined a block on a mining island (for commissions): what it was and the zone it was in. */
    @FunctionalInterface
    public interface Mined {
        void onMined(ServerPlayer player, BlockState state, String zone);
    }

    public static final Event<Mined> MINED = EventFactory.createArrayBacked(Mined.class, listeners -> (player, state, zone) -> {
        for (Mined listener : listeners) {
            listener.onMined(player, state, zone);
        }
    });

    /** Titanium that took the place of a mined Mithril block: the Mithril it goes back to. */
    private static final Map<GlobalPos, BlockState> TITANIUM = new HashMap<>();
    private static final Map<UUID, Long> WARNED = new HashMap<>();
    /** How long Titanium that nobody mines stays before it's Mithril again. */
    private static final int TITANIUM_TICKS = 60 * 20;

    private SkyMining() {
    }

    public static void init() {
        BlockStrength.install(SkyMining::progress);
        // After the Hub's protection has said yes: a Dwarven metal's break is handled here (its own drops, no vanilla wool).
        Identifier late = SkyRpgSkyblock.id("mining_late");
        PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(Event.DEFAULT_PHASE, late);
        PlayerBlockBreakEvents.BEFORE.register(late, (level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer miner) || !miningIsland(level.dimension())) {
                return true;
            }
            Metal metal = metal(level, state);
            Zones.Rule rule = Zones.rule(level, pos, state);
            if (metal == null || rule == null) {
                return true;
            }
            mineMetal(server, miner, pos, state, metal, rule);
            return false;
        });
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer miner && miningIsland(level.dimension()) && BLOCKS.containsKey(state.getBlock())) {
                MINED.invoker().onMined(miner, state, zoneName(level, pos));
            }
        });
        // Too little Breaking Power: say so instead of silently not breaking.
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (player instanceof ServerPlayer miner && !player.isCreative()) {
                Info info = info(level, level.getBlockState(pos));
                if (info != null && Stats.get(player, StatTypes.BREAKING_POWER) < info.power()
                        && Zones.rule(level, pos, level.getBlockState(pos)) != null) {
                    long now = level.getGameTime();
                    if (now - WARNED.getOrDefault(miner.getUUID(), -100L) > 40) {
                        WARNED.put(miner.getUUID(), now);
                        miner.sendOverlayMessage(Component.literal("You need a tool with a Breaking Power of " + info.power()
                                + " to mine " + level.getBlockState(pos).getBlock().getName().getString() + "!").withStyle(ChatFormatting.RED));
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }

    /** Hypixel's mining rules are on: a public SkyBlock island other than the Hub. */
    public static boolean miningIsland(ResourceKey<Level> dimension) {
        Identifier id = dimension.identifier();
        return id.getNamespace().equals(SkyRpgSkyblock.MOD_ID) && !id.equals(PRIVATE_ISLANDS);
    }

    /** A block's mining numbers here, or null when vanilla rules apply. */
    public static Info info(Level level, BlockState state) {
        if (!miningIsland(level.dimension())) {
            return null;
        }
        Info hollows = level.dimension().equals(CRYSTAL_HOLLOWS) ? HOLLOWS_BLOCKS.get(state.getBlock()) : null;
        return hollows != null ? hollows : BLOCKS.get(state.getBlock());
    }

    /** What mining a block by hand gives here (Mithril, Titanium, Hard Stone, gemstones), or null for normal drops. */
    static Metal metal(Level level, BlockState state) {
        Metal hollows = level.dimension().equals(CRYSTAL_HOLLOWS) ? HOLLOWS_METALS.get(state.getBlock()) : null;
        return hollows != null ? hollows : METALS.get(state.getBlock());
    }

    private static float progress(Player player, net.minecraft.world.level.BlockGetter getter, BlockPos pos, BlockState state, float vanilla) {
        Info info = info(player.level(), state);
        if (info == null || player.isCreative()) {
            return vanilla;
        }
        if (Stats.get(player, StatTypes.BREAKING_POWER) < info.power()) {
            return 0.0f;
        }
        double speed = Math.max(1, Stats.get(player, StatTypes.MINING_SPEED));
        if (speed > (info.ore() ? 60 : 30) * info.strength()) {
            return 1.0f;
        }
        long ticks = Math.max(4, Math.round(30.0 * info.strength() / speed));
        return 1.0f / ticks;
    }

    private static String zoneName(Level level, BlockPos pos) {
        Zones.Zone zone = Zones.at(level, pos);
        return zone == null ? "" : zone.name();
    }

    private static void mineMetal(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, Metal metal, Zones.Rule rule) {
        GlobalPos where = GlobalPos.of(level.dimension(), pos.immutable());
        BlockState original = TITANIUM.containsKey(where) && state.is(Blocks.POLISHED_DIORITE) ? TITANIUM.remove(where) : state;
        boolean titanium = false;
        if (metal.item().equals("MITHRIL_ORE")) {
            Hotm.State hotm = Hotm.state(player);
            HotmTree.Perk insanium = HotmTree.get("titanium_insanium");
            double chance = hotm.active("titanium_insanium") > 0 && insanium != null
                    ? insanium.number("stat", hotm.active("titanium_insanium"), false) / 100 : 0.005;
            titanium = level.getRandom().nextDouble() < chance;
        }
        if (titanium) {
            level.setBlock(pos, Blocks.POLISHED_DIORITE.defaultBlockState(), Block.UPDATE_ALL);
            Zones.deplete(level, pos, original, Blocks.POLISHED_DIORITE.defaultBlockState(), TITANIUM_TICKS);
            TITANIUM.put(where, original);
            player.sendSystemMessage(Component.literal("TITANIUM! ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                    .append(Component.literal("You found Titanium Ore!").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        } else {
            Zones.deplete(level, pos, original, rule.becomes(), rule.regrowTicks());
        }
        // Drops, with Mining Fortune (100 = one more each), straight into the inventory.
        // Gemstones use Gemstone Fortune; Mithril and Titanium Mining Fortune plus Dwarven Metal Fortune (Hypixel's).
        boolean gem = metal.powderType().equals(Hotm.GEMSTONE);
        double fortune = Math.max(0, gem ? Stats.get(player, StatTypes.GEMSTONE_FORTUNE)
                : Stats.get(player, StatTypes.MINING_FORTUNE) + (metal.item().equals("HARD_STONE") ? 0 : Stats.get(player, StatTypes.DWARVEN_METAL_FORTUNE)));
        double amount = metal.count() * (1 + fortune / 100);
        int count = (int) amount + (level.getRandom().nextDouble() < amount - (int) amount ? 1 : 0);
        ItemDefinition def = ItemRegistry.skyblock(metal.item());
        if (def != null && count > 0) {
            ItemGains.give(player, SkyItems.create(def, count), ItemGains.Source.MINING);
        }
        SkillLevels.giveXp(player, SkillLevels.MINING, metal.xp());
        boolean powderHere = level.dimension().equals(DWARVEN_MINES) || level.dimension().equals(CRYSTAL_HOLLOWS);
        if (metal.powder() > 0 && powderHere) {
            Hotm.State hotm = Hotm.state(player);
            boolean gemstone = metal.powderType().equals(Hotm.GEMSTONE);
            int core = hotm.perk("core_of_the_mountain");
            int powder = metal.powder() + (gemstone ? (core >= 6 ? 2 : 0) : (core >= 4 ? 1 : 0));
            HotmTree.Perk buff = HotmTree.get("powder_buff");
            if (buff != null && hotm.active("powder_buff") > 0) {
                powder = (int) Math.round(powder * (1 + buff.number("stat", hotm.active("powder_buff"), false) / 100));
            }
            Hotm.givePowder(player, metal.powderType(), powder);
            player.sendOverlayMessage(Component.literal("+" + powder + " " + Hotm.powderName(metal.powderType()))
                    .withColor(Hotm.powderColor(metal.powderType())));
        }
        if (metal.item().equals("HARD_STONE")) {
            com.skyrpg.skyblock.hollows.TreasureChests.dug(level, player, pos);
        }
        MINED.invoker().onMined(player, state, zoneName(level, pos));
    }
}
