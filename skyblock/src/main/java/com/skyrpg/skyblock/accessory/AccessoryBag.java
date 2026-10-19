package com.skyrpg.skyblock.accessory;

import com.mojang.serialization.Codec;
import com.skyrpg.core.progress.CollectionLevels;
import com.skyrpg.core.progress.SkyBlockXp;
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
 * Legendary 16, Mythic 22, Special 3, Very Special 5. Magical Power scales the stats of your Accessory Power (Maxwell),
 * pays Tuning Points (10 each, {@link AccessoryTuning}) and is SkyBlock XP (1 a point, once). Specials: the Hegemony
 * Artifact gives double its own, an Abicase 1 for every 2 contacts (the NPCs you've talked to, up to 80), the imbued Rift
 * Prism 11, and Dungeon accessories double inside the Catacombs. The bag's slots: the Redstone collection, the Redstone
 * Miner and Jacobus ({@link BagUpgrades}).
 */
public final class AccessoryBag {
    public static final int PAGE_SIZE = 45;
    /** The Redstone collection's +6 slots a tier, from tier II (tools/skyblock/import_collections.py data). */
    private static final int SLOTS_PER_TIER = 6;

    static final AttachmentType<List<ItemContainerContents>> PAGES = AttachmentRegistry.create(SkyRpgSkyblock.id("accessory_bag"),
            builder -> builder.initializer(List::of).persistent(ItemContainerContents.CODEC.listOf()).copyOnDeath());
    static final AttachmentType<String> POWER = AttachmentRegistry.create(SkyRpgSkyblock.id("accessory_power"),
            builder -> builder.initializer(() -> "").persistent(Codec.STRING).copyOnDeath());
    /** The Magical Power already paid out as SkyBlock XP (it only counts upwards). */
    static final AttachmentType<Integer> POWER_XP = AttachmentRegistry.create(SkyRpgSkyblock.id("accessory_power_xp"),
            builder -> builder.initializer(() -> 0).persistent(Codec.INT).copyOnDeath());
    /** The NPCs the player has talked to: their Abiphone contacts, for the Abicase. */
    static final AttachmentType<List<String>> CONTACTS = AttachmentRegistry.create(SkyRpgSkyblock.id("accessory_contacts"),
            builder -> builder.initializer(List::of).persistent(Codec.STRING.listOf()).copyOnDeath());
    /** Hypixel's most contacts an Abiphone holds. */
    private static final int MAX_CONTACTS = 80;
    static final AttachmentType<List<String>> LEARNED = AttachmentRegistry.create(SkyRpgSkyblock.id("accessory_powers_learned"),
            builder -> builder.initializer(List::of).persistent(Codec.STRING.listOf()).copyOnDeath());

    private AccessoryBag() {
    }

    public static void init() {
        PAGES.identifier();
        POWER.identifier();
        LEARNED.identifier();
        POWER_XP.identifier();
        CONTACTS.identifier();
        AccessoryPowers.init();
        AccessoryTuning.init();
        BagUpgrades.init();
        AccessoryEffects.init();
        EnrichmentSwapper.init();
        com.skyrpg.core.event.NpcInteractions.INTERACT.register((player, npc) -> {
            // Every NPC you speak to is an Abiphone contact.
            String id = npc.toString();
            List<String> contacts = player.getAttachedOrElse(CONTACTS, List.of());
            if (contacts.size() < MAX_CONTACTS && !contacts.contains(id)) {
                List<String> more = new ArrayList<>(contacts);
                more.add(id);
                player.setAttached(CONTACTS, List.copyOf(more));
            }
            return false;
        });
        // Magical Power is SkyBlock XP, which the bag also pays when a profile with accessories is loaded.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 200 == 0) {
                server.getPlayerList().getPlayers().forEach(AccessoryBag::rewardPower);
            }
        });
        com.skyrpg.core.action.Actions.register("jacobus", (player, params) -> MaxwellMenu.open(player, MaxwellMenu.BAGS));
        com.skyrpg.core.action.Actions.register("redstone_miner", (player, params) -> MaxwellMenu.open(player, MaxwellMenu.BAGS));
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
                int power = magicalPower(player);
                powerStats(player, power).forEach(out::add);
                AccessoryTuning.stats(player, power).forEach(out::add);
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

    /** The Redstone collection's slots: 0 before Redstone II, then 6 more a tier. */
    public static int redstoneSlots(Player player) {
        int tier = CollectionLevels.tier(player, Recipes.collectionId("REDSTONE"));
        return Math.max(0, tier - 1) * SLOTS_PER_TIER;
    }

    /** How many slots the bag has: the Redstone collection's, the Redstone Miner's and Jacobus's. */
    public static int slots(Player player) {
        int upgrades = player instanceof ServerPlayer server ? BagUpgrades.miner(server) + 2 * BagUpgrades.jacobus(server) : 0;
        return redstoneSlots(player) + upgrades;
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
        rewardPower(player);
        Stats.refresh(player);
    }

    /** Pays 1 SkyBlock XP for each point of Magical Power the player hasn't been paid for yet. */
    static void rewardPower(ServerPlayer player) {
        int power = magicalPower(player, false);
        int paid = player.getAttachedOrElse(POWER_XP, 0);
        if (power > paid) {
            player.setAttached(POWER_XP, power);
            SkyBlockXp.give(player, power - paid, "Accessory Bag");
        }
    }

    /** Everything in the bag. */
    static List<ItemStack> bag(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemContainerContents page : player.getAttachedOrElse(PAGES, List.of())) {
            page.nonEmptyItemCopyStream().forEach(out::add);
        }
        return out;
    }

    /** Whether this accessory counts for the player (in the bag or the inventory, and not outdone by a higher one of its line). */
    public static boolean has(ServerPlayer player, String skyblockId) {
        for (ItemStack stack : counted(player, true)) {
            if (skyblockId.equals(com.skyrpg.items.SkyItems.skyblockId(stack))) {
                return true;
            }
        }
        return false;
    }

    /** The ids of the accessories that count, in the bag or the inventory. */
    static List<String> countedIds(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (ItemStack stack : counted(player, true)) {
            out.add(SkyItems.skyblockId(stack));
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

    /** The Magical Power of one accessory: by rarity, twice for the Hegemony Artifact (and for a Dungeon one inside the Catacombs). */
    static int magicalPower(ItemStack stack, boolean inDungeon) {
        ItemDefinition def = SkyItems.definition(stack);
        int power = magicalPower(rarity(stack));
        if ("HEGEMONY_ARTIFACT".equals(SkyItems.skyblockId(stack)) || inDungeon && def != null && ItemMods.dungeonItem(def)) {
            power *= 2;
        }
        return power;
    }

    /** The player's Magical Power: the counted accessories in the bag, and the specials. */
    public static int magicalPower(ServerPlayer player) {
        return magicalPower(player, com.skyrpg.skyblock.dungeon.Catacombs.inRun(player));
    }

    private static int magicalPower(ServerPlayer player, boolean dungeon) {
        int total = 0;
        boolean abicase = false;
        for (ItemStack stack : counted(player, false)) {
            total += magicalPower(stack, dungeon);
            // Only one Abicase counts: 1 Magical Power for every 2 contacts.
            String id = SkyItems.skyblockId(stack);
            if (!abicase && id != null && id.startsWith("ABICASE")) {
                abicase = true;
                total += contacts(player) / 2;
            }
        }
        if (BagUpgrades.imbued(player)) {
            total += BagUpgrades.IMBUE_POWER;
        }
        return total;
    }

    /** How many Abiphone contacts (NPCs spoken to) the player has. */
    public static int contacts(ServerPlayer player) {
        return Math.min(MAX_CONTACTS, player.getAttachedOrElse(CONTACTS, List.of()).size());
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
