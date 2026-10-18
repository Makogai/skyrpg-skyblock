package com.skyrpg.skyblock.garden;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.profile.Profile;
import com.skyrpg.skyblock.profile.Profiles;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.storage.SkyMenus;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Garden's screens. The Desk's tabs: Plots (the 5 x 5 grid; unlock the next plot), Crops (milestones and crop
 * upgrades), the Composter (fill it, collect Compost, buy upgrades) and SkyMart (the Copper shop). The NPCs' own views:
 * SkyMart, Pesthunter Phillip, Jacob, Anita and a visitor's offer, drawn with the NPC beside them. Buttons:
 * {@link #TAB} + tab, {@link #ROW} + row.
 */
public final class GardenMenu extends AbstractContainerMenu {
    public static final int TAB = 10;
    public static final int ROW = 100;

    public enum Tab { PLOTS, MILESTONES, COMPOSTER, SKYMART, VISITOR, PHILLIP, JACOB, ANITA }

    /**
     * A card: its title and the line under it, a colour (the title's, when there's no item), whether it can be clicked,
     * its button ("" for a card you click as a whole), an item icon or a currency sprite, chips (prices, rewards), a
     * progress bar (-1 for none) and a note at its top right.
     */
    public record Row(String title, String detail, int color, boolean enabled, String button, ItemStack icon, String sprite, List<Chip> chips,
                      float progress, String note) {
        Row(String title, String detail, int color, boolean enabled, String button) {
            this(title, detail, color, enabled, button, ItemStack.EMPTY, "", List.of(), -1, "");
        }

        static final StreamCodec<RegistryFriendlyByteBuf, Row> CODEC = new StreamCodec<>() {
            @Override
            public Row decode(RegistryFriendlyByteBuf buf) {
                return new Row(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readBoolean(), buf.readUtf(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                        buf.readUtf(), Chip.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readFloat(), buf.readUtf());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Row row) {
                buf.writeUtf(row.title());
                buf.writeUtf(row.detail());
                buf.writeInt(row.color());
                buf.writeBoolean(row.enabled());
                buf.writeUtf(row.button());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, row.icon());
                buf.writeUtf(row.sprite());
                Chip.CODEC.apply(ByteBufCodecs.list()).encode(buf, row.chips());
                buf.writeFloat(row.progress());
                buf.writeUtf(row.note());
            }
        };
    }

    /**
     * The tab; a header (the Desk's Garden level line, or the NPC's role); the cards; the plot grid (25 cells: 0 Barn,
     * 1 unlocked, 2 next, 3 locked); a line at the bottom; the visitor's index on a visitor's view; the NPC's entity id
     * (-1 on the Desk), its name, and your balances.
     */
    public record View(Tab tab, String header, List<Row> rows, List<Integer> grid, String footer, int visitor, int npc, String npcName,
                       List<Chip> wallet) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(Tab.values()[buf.readVarInt()], buf.readUtf(), Row.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).decode(buf), buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                        buf.readUtf(), Chip.CODEC.apply(ByteBufCodecs.list()).decode(buf));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                buf.writeVarInt(view.tab().ordinal());
                buf.writeUtf(view.header());
                Row.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.rows());
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(buf, view.grid());
                buf.writeUtf(view.footer());
                buf.writeVarInt(view.visitor());
                buf.writeVarInt(view.npc());
                buf.writeUtf(view.npcName());
                Chip.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.wallet());
            }
        };

        /** Whether this is an NPC's view (drawn with the NPC) rather than the Desk's tabs. */
        public boolean npcView() {
            return npc >= 0 || tab.ordinal() >= 4;
        }
    }

    /** SkyMart: item, price, and whether the price is in coins (else Copper). From NEU's skymart.json. */
    record Sale(String item, long price, boolean coins) {
    }

    static final List<Sale> SKYMART = List.of(
            new Sale("BIOFUEL", 20_000, true), new Sale("SAM_SCYTHE", 2_000, true), new Sale("GARDEN_SCYTHE", 20, false),
            new Sale("BUILDERS_RULER", 20, false), new Sale("BASIC_GARDENING_HOE", 5, false), new Sale("BASIC_GARDENING_AXE", 5, false),
            new Sale("ADVANCED_GARDENING_HOE", 25, false), new Sale("ADVANCED_GARDENING_AXE", 25, false),
            new Sale("LOTUS_BRACELET", 50, false), new Sale("LOTUS_BELT", 100, false), new Sale("LOTUS_NECKLACE", 200, false),
            new Sale("LOTUS_CLOAK", 500, false), new Sale("LARGE_WALNUT", 150, false), new Sale("YELLOW_BANDANA", 300, false),
            new Sale("COPPER_TALISMAN", 5_000, false), new Sale("COPPER_RING", 25_000, false), new Sale("PLANT_DIAGNOSTICS_TOOL", 50, false),
            new Sale("THEORETICAL_HOE_WHEAT_1", 250, false), new Sale("THEORETICAL_HOE_CARROT_1", 250, false),
            new Sale("THEORETICAL_HOE_POTATO_1", 250, false), new Sale("PUMPKIN_DICER", 250, false),
            new Sale("THEORETICAL_HOE_CANE_1", 250, false), new Sale("MELON_DICER", 250, false), new Sale("CACTUS_KNIFE", 250, false),
            new Sale("COCO_CHOPPER", 250, false), new Sale("FUNGI_CUTTER", 250, false), new Sale("THEORETICAL_HOE_WARTS_1", 250, false),
            new Sale("FARMING_TOOLKIT", 500, false), new Sale("SKYMART_VACUUM", 5_000, true), new Sale("SKYMART_TURBO_VACUUM", 200, false),
            new Sale("SKYMART_HYPER_VACUUM", 500, false), new Sale("INFINI_VACUUM", 1_000, false), new Sale("SPRAYONATOR", 25, false),
            new Sale("PEST_REPELLENT", 15, false), new Sale("PEST_REPELLENT_MAX", 25, false));

    /** The Composter upgrades' icons, in {@link Composter#UPGRADES} order. */
    private static final List<Item> UPGRADE_ICONS = List.of(Items.CLOCK, Items.CHEST, Items.LAVA_BUCKET, Items.COMPOSTER, Items.GOLD_NUGGET);

    private final View view;
    private final ServerPlayer owner;

    public GardenMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private GardenMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.GARDEN, id);
        this.view = view;
        this.owner = owner;
    }

    public View view() {
        return view;
    }

    static void init() {
    }

    static List<Sale> sales() {
        return SKYMART.stream().filter(s -> ItemRegistry.skyblock(s.item()) != null).toList();
    }

    /** One of a SkyBlock item, for an icon (empty if we don't have it). */
    static ItemStack icon(String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? ItemStack.EMPTY : SkyItems.create(def, 1);
    }

    private static ItemStack iconOr(String id, Item fallback) {
        ItemStack stack = icon(id);
        return stack.isEmpty() ? new ItemStack(fallback) : stack;
    }

    static void open(ServerPlayer player, Tab tab) {
        open(player, tab, -1, -1);
    }

    /** An NPC's view, drawn with that NPC ({@code npc} is its entity id). */
    static void openFrom(ServerPlayer player, Tab tab, int npc) {
        open(player, tab, -1, npc);
    }

    static void openVisitor(ServerPlayer player, int index, int npc) {
        open(player, Tab.VISITOR, index, npc);
    }

    private static void open(ServerPlayer player, Tab tab, int visitor, int npc) {
        Profile profile = Profiles.current(player);
        if (profile == null) {
            return;
        }
        GardenData.State state = Garden.state(player.level().getServer(), profile);
        int level = Garden.level(state);
        long into = state.xp;
        for (int i = 1; i < level; i++) {
            into -= Garden.LEVEL_XP[i];
        }
        String header = "Garden " + Msg.roman(level) + (level < 15 ? "  ·  " + Msg.number(into) + "/" + Msg.number(Garden.LEVEL_XP[level]) + " XP" : "");
        String npcName = "";
        List<Row> rows = new ArrayList<>();
        List<Integer> grid = new ArrayList<>();
        List<Chip> wallet = new ArrayList<>();
        wallet.add(Chip.balance("copper", state.copper));
        String footer = "";
        switch (tab) {
            case PLOTS -> {
                for (int row = 0; row < 5; row++) {
                    for (int col = 0; col < 5; col++) {
                        int plot = Garden.plotNumber(col, row);
                        grid.add(plot == 0 ? 0 : plot <= state.plots ? 1 : plot == state.plots + 1 ? 2 : 3);
                    }
                }
                wallet.add(Chip.note(iconOr("COMPOST", Items.BONE_MEAL), Msg.number(Sacks.countAnywhere(player, "COMPOST")), true));
                wallet.add(Chip.note(iconOr("ENCHANTED_COMPOST", Items.BONE_BLOCK), Msg.number(Sacks.countAnywhere(player, "ENCHANTED_COMPOST")), true));
                if (state.plots < Garden.PLOTS.length) {
                    int next = state.plots + 1;
                    int cost = Garden.PLOTS[state.plots][2];
                    String item = next >= 12 ? "ENCHANTED_COMPOST" : "COMPOST";
                    int needLevel = Garden.plotLevel(next);
                    ItemStack costIcon = iconOr(item, Items.BONE_MEAL);
                    rows.add(new Row("Plot " + next, level < needLevel ? "Needs Garden " + Msg.roman(needLevel) : "+3 Farming Fortune in the Garden",
                            0x55FF55, level >= needLevel, "Unlock", costIcon, "", List.of(Chip.item(costIcon, cost, Sacks.countAnywhere(player, item))),
                            -1, level >= needLevel ? "" : "Garden " + Msg.roman(needLevel)));
                } else {
                    rows.add(new Row("Every plot unlocked", "+72 Farming Fortune in the Garden", 0x55FF55, false, "", iconOr("COMPOST", Items.BONE_MEAL),
                            "", List.of(), -1, ""));
                }
                footer = state.plots + "/24 plots  ·  +" + (3 * state.plots) + " Farming Fortune in the Garden";
            }
            case MILESTONES -> {
                for (Garden.Crop crop : Garden.CROPS) {
                    long count = state.crops.getOrDefault(crop.key(), 0L);
                    int lvl = Garden.milestone(crop, count);
                    long left = Garden.toNext(crop, count);
                    long step = Garden.step(crop, lvl);
                    int tier = state.upgrades.getOrDefault(crop.key(), 0);
                    boolean maxed = tier >= Garden.CROP_UPGRADE.length;
                    List<Chip> chips = maxed ? List.of() : List.of(Chip.price("copper", Garden.CROP_UPGRADE[tier], state.copper));
                    rows.add(new Row(crop.name() + "  " + lvl, left > 0 ? Msg.number(left) + " to " + (lvl + 1) : "Milestones maxed", 0xFFFF55,
                            !maxed && chips.stream().allMatch(Chip::enough),
                            maxed ? "Maxed" : "Upgrade", icon(crop.item()), "", chips, left > 0 ? 1f - (float) left / Math.max(1, step) : 1f,
                            "+" + 5 * tier + " Fortune"));
                }
                footer = "Milestones: harvest here. Upgrades: +5 crop Fortune a tier, everywhere.";
            }
            case COMPOSTER -> {
                Composter.update(state, new java.util.Random());
                double organicCap = Composter.organicCap(state);
                double fuelCap = Composter.fuelCap(state);
                ItemStack fuel = iconOr("BIOFUEL", Items.BLAZE_POWDER);
                rows.add(new Row("Organic Matter", Msg.number((long) state.organic) + " / " + Msg.number((long) organicCap), 0x55FF55, true,
                        "Add crops", new ItemStack(Items.WHEAT), "", List.of(), (float) (state.organic / organicCap), ""));
                rows.add(new Row("Fuel", Msg.number((long) state.fuel) + " / " + Msg.number((long) fuelCap), 0xFFAA00, true, "Add fuel",
                        fuel, "", List.of(), (float) (state.fuel / fuelCap), ""));
                rows.add(new Row("Compost  x" + state.compost, "one every " + Composter.cycleMs(state) / 60_000 + " min", 0xE07B39, state.compost > 0,
                        "Collect", iconOr("COMPOST", Items.BONE_MEAL), "", List.of(
                                Chip.note(new ItemStack(Items.WHEAT), Msg.number((long) Composter.cost(state, Composter.ORGANIC)), true),
                                Chip.note(fuel, Msg.number((long) Composter.cost(state, Composter.FUEL)), true)), -1, ""));
                for (int b = 0; b < Composter.UPGRADES.size(); b++) {
                    int tier = Composter.tier(state, Composter.UPGRADES.get(b));
                    List<Chip> chips = new ArrayList<>();
                    if (tier < 25) {
                        chips.add(Chip.price("copper", Composter.COPPER[tier], state.copper));
                        String item = Composter.item(b, tier);
                        chips.add(Chip.item(iconOr(item, Items.BARRIER), Composter.AMOUNTS[b][tier], Sacks.countAnywhere(player, item)));
                    }
                    rows.add(new Row(Composter.UPGRADE_NAMES.get(b), tier >= 25 ? "Maxed" : "", 0x55FFFF, tier < 25 && chips.stream().allMatch(Chip::enough),
                            tier >= 25 ? "Maxed" : "Upgrade",
                            new ItemStack(UPGRADE_ICONS.get(b)), "", chips, tier / 25f, tier + "/25"));
                }
                footer = "4,000 Organic Matter and 2,000 Fuel make one Compost, also while you're away.";
            }
            case SKYMART -> {
                long coins = Wallet.balance(player);
                wallet.add(Chip.balance("coins", coins));
                for (Sale sale : sales()) {
                    rows.add(shopRow(icon(sale.item()), List.of(Chip.price(sale.coins() ? "coins" : "copper", sale.price(), sale.coins() ? coins : state.copper))));
                }
                if (npc >= 0) {
                    header = "Garden supplies";
                }
                npcName = "SkyMart";
                footer = "Copper comes from visitors' offers.";
            }
            case PHILLIP -> {
                wallet.clear();
                wallet.add(Chip.balance("pests", state.pests));
                for (Pests.Sale sale : Pests.PHILLIP) {
                    rows.add(shopRow(icon(sale.item()), List.of(Chip.price("pests", sale.pests(), state.pests))));
                }
                header = "Pest hunter";
                npcName = "Pesthunter Phillip";
                footer = "Every pest you vacuum up is one to spend here.";
            }
            case JACOB -> {
                wallet.clear();
                anitaWallet(player, wallet);
                long now = System.currentTimeMillis();
                List<String> crops;
                String when;
                if (Contests.running()) {
                    crops = Contests.crops();
                    when = "Running now! Farm one of these on your Garden.";
                } else {
                    long next = com.skyrpg.skyblock.time.CalendarEvents.upcoming(now, 3 * 60 * 60 * 1000L).stream()
                            .filter(o -> o.event().name().equals("Jacob's Farming Contest")).mapToLong(com.skyrpg.skyblock.time.CalendarEvents.Occurrence::start)
                            .findFirst().orElse(0);
                    crops = Contests.cropsFor(next);
                    when = next == 0 ? "Soon" : "Starts in " + com.skyrpg.skyblock.time.SkyBlockTime.countdown(next - now);
                }
                List<Chip> cropChips = new ArrayList<>();
                for (String crop : crops) {
                    Garden.Crop c = Garden.crop(crop);
                    cropChips.add(Chip.note(c == null ? new ItemStack(Items.WHEAT) : iconOr(c.item(), Items.WHEAT), Contests.cropName(crop), true));
                }
                rows.add(new Row(Contests.running() ? "Contest running" : "Next contest", when, 0xFFFF55, false, "", new ItemStack(Items.CLOCK), "",
                        cropChips, -1, "20 minutes"));
                List<Chip> pending = new ArrayList<>();
                int tickets = Contests.medals(player, "pending_tickets");
                if (tickets > 0) {
                    pending.add(Chip.balance("tickets", tickets));
                }
                for (String medal : List.of("gold", "silver", "bronze")) {
                    int n = Contests.medals(player, "pending_" + medal);
                    if (n > 0) {
                        pending.add(Chip.balance("medal_" + medal, n));
                    }
                }
                rows.add(new Row("Rewards waiting", pending.isEmpty() ? "Nothing yet. Take part in a contest!" : "From your last contests", 0x55FF55,
                        !pending.isEmpty(), "Claim", ItemStack.EMPTY, "tickets", pending, -1, ""));
                List<Chip> bests = new ArrayList<>();
                for (Garden.Crop c : Garden.CROPS) {
                    int best = Contests.medals(player, "best_" + c.key());
                    if (best > 0) {
                        bests.add(Chip.note(iconOr(c.item(), Items.WHEAT), Msg.number(best), true));
                    }
                }
                rows.add(new Row("Personal bests", bests.isEmpty() ? "Your best score in each crop shows here." : "", 0xFFAA00, false, "",
                        new ItemStack(Items.GOLDEN_HOE), "", bests, -1, ""));
                header = "Farming contests";
                npcName = "Jacob";
                footer = "Farming X to take part; 100 crops for a reward.";
            }
            case ANITA -> {
                wallet.clear();
                anitaWallet(player, wallet);
                long tickets = Sacks.countAnywhere(player, "JACOBS_TICKET");
                for (Contests.Sale sale : Contests.ANITA) {
                    List<Chip> chips = new ArrayList<>();
                    chips.add(Chip.price("tickets", sale.tickets(), tickets));
                    if (sale.gold() > 0) {
                        chips.add(Chip.price("medal_gold", sale.gold(), Contests.medals(player, "gold")));
                    }
                    if (sale.silver() > 0) {
                        chips.add(Chip.price("medal_silver", sale.silver(), Contests.medals(player, "silver")));
                    }
                    if (sale.bronze() > 0) {
                        chips.add(Chip.price("medal_bronze", sale.bronze(), Contests.medals(player, "bronze")));
                    }
                    if (!sale.needs().isEmpty()) {
                        chips.add(Chip.item(iconOr(sale.needs(), Items.BARRIER), 1, Sacks.countAnywhere(player, sale.needs())));
                    }
                    ItemStack item = icon(sale.item());
                    rows.add(shopRow(item.isEmpty() ? item : item.copyWithCount(sale.count()), chips));
                }
                rows.add(new Row("2 Silver medals", "Trade a Gold medal", 0xFFFFFF, true, "", ItemStack.EMPTY, "medal_silver",
                        List.of(Chip.price("medal_gold", 1, Contests.medals(player, "gold"))), -1, "Trade"));
                rows.add(new Row("2 Bronze medals", "Trade a Silver medal", 0xFF8844, true, "", ItemStack.EMPTY, "medal_bronze",
                        List.of(Chip.price("medal_silver", 1, Contests.medals(player, "silver"))), -1, "Trade"));
                header = "Contest shop";
                npcName = "Anita";
                footer = "Tickets and medals come from Jacob's contests.";
            }
            case VISITOR -> {
                if (visitor >= 0 && visitor < state.visitors.size()) {
                    Visitors.Offer offer = Visitors.Offer.decode(state.visitors.get(visitor));
                    header = Visitors.rarity(offer.rarity()) + " visitor";
                    npcName = offer.name();
                    for (Map.Entry<String, Integer> want : offer.wants().entrySet()) {
                        long have = Sacks.countAnywhere(player, want.getKey());
                        rows.add(new Row(Visitors.name(want.getKey()), "You have " + Msg.number(have) + " of " + Msg.number(want.getValue()),
                                have >= want.getValue() ? 0x55FF55 : 0xFF5555, false, "", iconOr(want.getKey(), Items.WHEAT), "", List.of(),
                                Math.min(1f, (float) have / want.getValue()), "x" + Msg.number(want.getValue())));
                    }
                    rows.add(new Row("Rewards", "", 0xE07B39, false, "", new ItemStack(Items.CHEST), "", List.of(Chip.balance("copper", offer.copper()),
                            Chip.note(new ItemStack(Items.EXPERIENCE_BOTTLE), Msg.number(offer.gardenXp()) + " Garden XP", true),
                            Chip.note(new ItemStack(Items.GOLDEN_HOE), Msg.number(offer.farmingXp()) + " Farming XP", true)), -1, ""));
                    rows.add(new Row("Accept", "Hand over the items", 0x55FF55, true, "Accept"));
                    rows.add(new Row("Decline", "They leave", 0xFF5555, true, "Decline"));
                }
            }
        }
        View view = new View(tab, header, rows, grid, footer, visitor, npc, npcName, wallet);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal(switch (tab) {
                    case SKYMART -> "SkyMart";
                    case VISITOR -> "Visitor";
                    case PHILLIP -> "Phillip";
                    case JACOB -> "Jacob";
                    case ANITA -> "Anita";
                    default -> "Desk";
                });
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new GardenMenu(id, view, player);
            }
        });
    }

    /** A shop card: the item (its name and rarity colour on the card) and its price chips; red chips mean you're short. */
    private static Row shopRow(ItemStack item, List<Chip> price) {
        return new Row(item.isEmpty() ? "?" : item.getHoverName().getString(), "", 0xFFFFFF, !item.isEmpty(), "", item, "", price, -1, "");
    }

    /** Jacob's Tickets and the three medals you hold. */
    private static void anitaWallet(ServerPlayer player, List<Chip> wallet) {
        wallet.add(Chip.balance("tickets", Sacks.countAnywhere(player, "JACOBS_TICKET")));
        wallet.add(Chip.balance("medal_gold", Contests.medals(player, "gold")));
        wallet.add(Chip.balance("medal_silver", Contests.medals(player, "silver")));
        wallet.add(Chip.balance("medal_bronze", Contests.medals(player, "bronze")));
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        Profile profile = Profiles.current(owner);
        if (profile == null) {
            return false;
        }
        GardenData.State state = Garden.state(owner.level().getServer(), profile);
        if (id >= TAB && id < TAB + 4 && !view.npcView()) {
            owner.level().getServer().execute(() -> open(owner, Tab.values()[id - TAB]));
            return true;
        }
        int row = id - ROW;
        String problem = "";
        switch (view.tab()) {
            case PLOTS -> problem = unlock(owner, profile, state);
            case MILESTONES -> problem = Garden.upgradeCrop(owner, state, row);
            case COMPOSTER -> {
                switch (row) {
                    case 0 -> say(owner, "Added " + Msg.number((long) Composter.insert(owner, state, false)) + " Organic Matter.");
                    case 1 -> say(owner, "Added " + Msg.number((long) Composter.insert(owner, state, true)) + " Fuel.");
                    case 2 -> say(owner, "Collected " + Composter.collect(owner, state) + " Compost.");
                    default -> problem = Composter.upgrade(owner, state, row - 3);
                }
            }
            case SKYMART -> problem = buy(owner, state, row);
            case PHILLIP -> problem = Pests.buy(owner, state, row);
            case JACOB -> problem = Contests.claim(owner);
            case ANITA -> problem = Contests.buy(owner, row);
            case VISITOR -> {
                int rows = view.rows().size();
                if (row == rows - 2) {
                    problem = Visitors.accept(owner, profile, view.visitor());
                    if (problem.isEmpty()) {
                        owner.closeContainer();
                        return true;
                    }
                } else if (row == rows - 1) {
                    Visitors.decline(owner, profile, view.visitor());
                    owner.closeContainer();
                    return true;
                }
            }
        }
        GardenData.get(owner.level().getServer()).setDirty();
        if (!problem.isEmpty()) {
            owner.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
        }
        owner.level().getServer().execute(() -> open(owner, view.tab(), view.visitor(), view.npc()));
        return true;
    }

    private static void say(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GREEN));
    }

    private static String unlock(ServerPlayer player, Profile profile, GardenData.State state) {
        if (state.plots >= Garden.PLOTS.length) {
            return "Every plot is unlocked.";
        }
        int next = state.plots + 1;
        if (Garden.level(state) < Garden.plotLevel(next)) {
            return "Plot " + next + " needs Garden " + Msg.roman(Garden.plotLevel(next)) + ".";
        }
        int cost = Garden.PLOTS[state.plots][2];
        String item = next >= 12 ? "ENCHANTED_COMPOST" : "COMPOST";
        if (Sacks.countAnywhere(player, item) < cost) {
            return "You need " + cost + " " + Visitors.name(item) + ".";
        }
        Sacks.takeAnywhere(player, item, cost);
        Garden.unlock(player, profile, state);
        return "";
    }

    private static String buy(ServerPlayer player, GardenData.State state, int row) {
        List<Sale> sales = sales();
        if (row < 0 || row >= sales.size()) {
            return "";
        }
        Sale sale = sales.get(row);
        if (sale.coins() ? Wallet.balance(player) < sale.price() : state.copper < sale.price()) {
            return "You need " + Msg.number(sale.price()) + (sale.coins() ? " coins." : " Copper.");
        }
        if (sale.coins()) {
            Wallet.take(player, sale.price());
        } else {
            state.copper -= sale.price();
        }
        ItemStack stack = SkyItems.create(ItemRegistry.skyblock(sale.item()), 1);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        say(player, "Bought " + stack.getHoverName().getString() + ".");
        return "";
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
