package com.skyrpg.items;

import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Rod Parts: a fishing rod takes one hook, one line and one sinker. Which are on a rod is kept in its custom data
 * under {@code hook}, {@code line} and {@code sinker} (the parts' SkyBlock ids). Lines add stats (through
 * {@link ItemMods#stats}); hooks and sinkers have effects that SkyBlock's fishing reads ({@code SkyFishing}, {@code SkyHotspots}).
 */
public final class RodParts {
    public static final String HOOK = "hook";
    public static final String LINE = "line";
    public static final String SINKER = "sinker";
    public static final List<String> SLOTS = List.of(HOOK, LINE, SINKER);

    /** A part: its slot, what it does in Hypixel's words (with §-codes), and the stats it gives the rod. */
    public record Part(String id, String slot, String effect, Map<StatType, Double> stats) {
    }

    private static final Map<String, Part> PARTS = new LinkedHashMap<>();

    static {
        hook("COMMON_HOOK", "§7Increases the chance to catch §fCommon §7Sea Creatures by §a25%§7.");
        hook("PUDDLE_JUMPER_HOOK", "§7Increases the chance to catch a §aPuddle Jumper §7by §a400%§7.");
        hook("PHANTOM_HOOK", "§7Grants a §a+20% §7chance to catch §6Spooky Sea Creatures §7during the §6Spooky Festival§7.");
        hook("HOTSPOT_HOOK", "§7Grants a §a+20% §7chance to catch §dHotspot Sea Creatures §7while fishing in a §dHotspot§7.");
        hook("TREASURE_HOOK", "§7Only allows you to catch items and §6Treasure§7.");
        line("SPEEDY_LINE", "§7Grants §b+10 Fishing Speed §7to Fishing Rods.", StatTypes.FISHING_SPEED, 10);
        line("TROPHY_LINE", "§7Grants §6+5 Trophy Fish Chance §7to Fishing Rods.", StatTypes.TROPHY_FISH_CHANCE, 5);
        PARTS.put("SHREDDED_LINE", new Part("SHREDDED_LINE", LINE, "§7Grants §c+250 Damage §7and §c+50 Ferocity §7to Fishing Rods.",
                Map.of(StatTypes.DAMAGE, 250.0, StatTypes.FEROCITY, 50.0)));
        line("TITAN_LINE", "§7Grants §9+2 Double Hook Chance §7to Fishing Rods.", StatTypes.DOUBLE_HOOK_CHANCE, 2);
        sinker("PRISMARINE_SINKER", "§7Materializes §aPrismarine Shard §7or §aPrismarine Crystals §7in your inventory whenever you catch something.");
        sinker("ICY_SINKER", "§7Materializes §fIce §7in your inventory whenever you catch something.");
        sinker("FESTIVE_SINKER", "§7Grants a §a5% §7chance to materialize Gifts in your inventory whenever you catch something while Jerry's Workshop is open.");
        sinker("SPONGE_SINKER", "§7Materializes §eSponge §7in your inventory whenever you catch something.");
        sinker("CHUM_SINKER", "§7Materializes §aChum §7in your inventory whenever you catch something.");
        sinker("JUNK_SINKER", "§7Grants §6+10 Treasure Chance §7while in the Backwater Bayou, and replaces all caught §6Treasures §7with §2Junk§7.");
        sinker("STINGY_SINKER", "§7Grants a §a10% §7chance to not consume §aBait§7.");
        sinker("LOTUS_SINKER", "§7Materializes §aLotus §7in your inventory whenever you catch something.");
        sinker("HOTSPOT_SINKER", "§7Increases the bonuses of Fishing §dHotspots §7by §a100%§7.");
    }

    private RodParts() {
    }

    private static void hook(String id, String effect) {
        PARTS.put(id, new Part(id, HOOK, effect, Map.of()));
    }

    private static void line(String id, String effect, StatType stat, double value) {
        PARTS.put(id, new Part(id, LINE, effect, Map.of(stat, value)));
    }

    private static void sinker(String id, String effect) {
        PARTS.put(id, new Part(id, SINKER, effect, Map.of()));
    }

    public static Part part(String id) {
        return id == null ? null : PARTS.get(id);
    }

    /** The part this item is, or null. */
    public static Part of(ItemStack stack) {
        return part(SkyItems.skyblockId(stack));
    }

    /** True for a SkyBlock fishing rod. */
    public static boolean isRod(ItemStack stack) {
        return stack.getItem() instanceof FishingRodItem && SkyItems.definition(stack) != null;
    }

    /** The id of the part on this rod in a slot, or "". */
    public static String on(ItemStack rod, String slot) {
        CompoundTag tag = ItemMods.tag(rod);
        return tag == null ? "" : tag.getStringOr(slot, "");
    }

    /** The parts on this rod, hook then line then sinker. */
    public static List<Part> parts(ItemStack rod) {
        List<Part> out = new ArrayList<>();
        for (String slot : SLOTS) {
            Part part = part(on(rod, slot));
            if (part != null) {
                out.add(part);
            }
        }
        return out;
    }

    /** True if this rod has this part (by id). */
    public static boolean has(ItemStack rod, String id) {
        Part part = part(id);
        return part != null && !rod.isEmpty() && id.equals(on(rod, part.slot()));
    }

    /** What its line adds. */
    public static Map<StatType, Double> stats(ItemStack rod) {
        Part line = part(on(rod, LINE));
        return line == null ? Map.of() : line.stats();
    }

    public static void apply(ItemStack rod, Part part) {
        ItemMods.edit(rod, tag -> tag.putString(part.slot(), part.id()));
        SkyItems.normalize(rod);
    }

    public static void remove(ItemStack rod, String slot) {
        ItemMods.edit(rod, tag -> tag.remove(slot));
        SkyItems.normalize(rod);
    }
}
