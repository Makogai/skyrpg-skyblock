package com.skyrpg.skyblock.accessory;

import com.mojang.serialization.Codec;
import com.skyrpg.core.progress.CollectionLevels;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.Rarity;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.reforge.Reforges;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.recipe.Recipes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Hypixel's Accessory Bag: talismans, rings, artifacts and relics in their own storage (pages of 45), sized by the
 * Redstone collection (+6 slots a tier from II). Every accessory you have, in the bag or your inventory, gives its
 * stats; only the highest of a line counts (the Speed Ring, not also its Talisman), and each accessory once. Accessories
 * in the bag also give Magical Power by rarity (after a Recombobulator): Common 3, Uncommon 5, Rare 8, Epic 12,
 * Legendary 16, Mythic 22, Special 3, Very Special 5. Magical Power scales the stats of your Accessory Power (Maxwell).
 */
public final class AccessoryBag {
    public static final int PAGE_SIZE = 45;
    /** The Redstone collection's +6 slots a tier, from tier II (tools/skyblock/import_collections.py data). */
    private static final int SLOTS_PER_TIER = 6;

    static final AttachmentType<List<ItemContainerContents>> PAGES = AttachmentRegistry.create(SkyRpgSkyblock.id("accessory_bag"),
            builder -> builder.initializer(List::of).persistent(ItemContainerContents.CODEC.listOf()).copyOnDeath());
    static final AttachmentType<String> POWER = AttachmentRegistry.create(SkyRpgSkyblock.id("accessory_power"),
            builder -> builder.initializer(() -> "").persistent(Codec.STRING).copyOnDeath());
    static final AttachmentType<List<String>> LEARNED = AttachmentRegistry.create(SkyRpgSkyblock.id("accessory_powers_learned"),
            builder -> builder.initializer(List::of).persistent(Codec.STRING.listOf()).copyOnDeath());

    private AccessoryBag() {
    }

    public static void init() {
        PAGES.identifier();
        POWER.identifier();
        LEARNED.identifier();
        AccessoryPowers.init();
        com.skyrpg.core.action.Actions.register("maxwell", (player, params) -> MaxwellMenu.open(player));
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Accessory Bag";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                for (ItemStack stack : counted(player, true)) {
                    ItemMods.stats(stack, SkyItems.definition(stack)).forEach((stat, value) -> {
                        if (StatTypes.isPlayerStat(stat)) {
                            out.add(stat, value);
                        }
                    });
                }
                powerStats(player, magicalPower(player)).forEach(out::add);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal("accessories").executes(ctx -> {
                open(ctx.getSource().getPlayerOrException(), 0);
                return 1;
            }));
            dispatcher.register(Commands.literal("maxwell").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                MaxwellMenu.open(ctx.getSource().getPlayerOrException());
                return 1;
            }));
        });
    }

    /** Whether this is an accessory (Hypixel's ACCESSORY category: talismans, rings, artifacts, relics...). */
    public static boolean isAccessory(ItemStack stack) {
        ItemDefinition def = SkyItems.definition(stack);
        return def != null && def.skyblock() && "ACCESSORY".equals(Reforges.category(def));
    }

    /** How many slots the bag has: 0 before Redstone II, then 6 more a tier. */
    public static int slots(Player player) {
        int tier = CollectionLevels.tier(player, Recipes.collectionId("REDSTONE"));
        return Math.max(0, tier - 1) * SLOTS_PER_TIER;
    }

    public static int pages(Player player) {
        return Math.max(1, (slots(player) + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    static NonNullList<ItemStack> page(ServerPlayer player, int page) {
        NonNullList<ItemStack> items = NonNullList.withSize(PAGE_SIZE, ItemStack.EMPTY);
        List<ItemContainerContents> pages = player.getAttachedOrElse(PAGES, List.of());
        if (page < pages.size()) {
            pages.get(page).copyInto(items);
        }
        return items;
    }

    static void save(ServerPlayer player, int page, List<ItemStack> items) {
        List<ItemContainerContents> pages = new ArrayList<>(player.getAttachedOrElse(PAGES, List.of()));
        while (pages.size() <= page) {
            pages.add(ItemContainerContents.EMPTY);
        }
        pages.set(page, ItemContainerContents.fromItems(items));
        player.setAttached(PAGES, List.copyOf(pages));
        Stats.refresh(player);
    }

    /** Everything in the bag. */
    static List<ItemStack> bag(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemContainerContents page : player.getAttachedOrElse(PAGES, List.of())) {
            page.nonEmptyItemCopyStream().forEach(out::add);
        }
        return out;
    }

    /**
     * The accessories that count: from the bag (and, for stats, the inventory too), each id once, and only the highest
     * of a line.
     */
    static List<ItemStack> counted(ServerPlayer player, boolean withInventory) {
        Map<String, ItemStack> byId = new LinkedHashMap<>();
        List<ItemStack> candidates = new ArrayList<>(bag(player));
        if (withInventory) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                candidates.add(player.getInventory().getItem(i));
            }
        }
        for (ItemStack stack : candidates) {
            if (!isAccessory(stack)) {
                continue;
            }
            String id = SkyItems.skyblockId(stack);
            if (AccessoryPowers.ignored(id)) {
                continue;
            }
            ItemStack had = byId.get(id);
            // Of two copies, the better one (a recombobulated one is a rarity up).
            if (had == null || rarity(stack).ordinal() > rarity(had).ordinal()) {
                byId.put(id, stack);
            }
        }
        List<ItemStack> out = new ArrayList<>();
        for (Map.Entry<String, ItemStack> entry : byId.entrySet()) {
            boolean outranked = byId.keySet().stream().anyMatch(other -> AccessoryPowers.outranks(other, entry.getKey()));
            if (!outranked) {
                out.add(entry.getValue());
            }
        }
        return out;
    }

    private static Rarity rarity(ItemStack stack) {
        ItemDefinition def = SkyItems.definition(stack);
        return def == null ? Rarity.COMMON : ItemMods.rarity(stack, def);
    }

    /** Magical Power from a rarity. */
    public static int magicalPower(Rarity rarity) {
        return switch (rarity) {
            case COMMON, SPECIAL -> 3;
            case UNCOMMON, VERY_SPECIAL -> 5;
            case RARE -> 8;
            case EPIC -> 12;
            case LEGENDARY -> 16;
            case MYTHIC, DIVINE -> 22;
            default -> 0;
        };
    }

    /** The player's Magical Power: the counted accessories in the bag. */
    public static int magicalPower(ServerPlayer player) {
        int total = 0;
        for (ItemStack stack : counted(player, false)) {
            total += magicalPower(rarity(stack));
        }
        return total;
    }

    /** What the selected power gives at this much Magical Power (base × multiplier, plus its unique bonus). */
    public static Map<StatType, Double> powerStats(ServerPlayer player, int magicalPower) {
        AccessoryPowers.Power power = AccessoryPowers.get(player.getAttachedOrElse(POWER, ""));
        if (power == null || !available(player, power)) {
            return Map.of();
        }
        return powerStats(power, magicalPower);
    }

    public static Map<StatType, Double> powerStats(AccessoryPowers.Power power, int magicalPower) {
        double multiplier = AccessoryPowers.multiplier(magicalPower);
        Map<StatType, Double> out = new HashMap<>();
        power.stats().forEach((stat, value) -> out.merge(stat, value * multiplier, Double::sum));
        power.unique().forEach((stat, value) -> out.merge(stat, value, Double::sum));
        return out;
    }

    /** Whether the player can use this power: a starter one or one they've learned, at the Combat level it needs. */
    public static boolean available(ServerPlayer player, AccessoryPowers.Power power) {
        boolean known = power.starter() || power.stone() == null || player.getAttachedOrElse(LEARNED, List.of()).contains(power.id());
        return known && SkillLevels.level(player, SkillLevels.COMBAT) >= power.combat();
    }

    public static void open(ServerPlayer player, int page) {
        if (slots(player) <= 0) {
            player.sendSystemMessage(Component.literal("The Accessory Bag unlocks at Redstone collection II.").withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> AccessoryBagMenu.server(id, inventory, player, page),
                Component.literal("Accessory Bag")));
    }
}
