package com.skyrpg.skyblock.garden;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.profile.Profile;
import com.skyrpg.skyblock.profile.Profiles;
import com.skyrpg.skyblock.storage.SkyMenus;
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
import net.minecraft.world.item.ItemStack;

/**
 * The Garden's screens. The Desk's tabs: Plots (the 5 x 5 grid; unlock the next plot), Crop Milestones, the Composter
 * (fill it, collect Compost, buy upgrades) and SkyMart (the Copper shop, which the SkyMart NPC opens too). A visitor's
 * offer has its own view (accept or decline). Buttons: {@link #TAB} + tab, {@link #ROW} + row.
 */
public final class GardenMenu extends AbstractContainerMenu {
    public static final int TAB = 10;
    public static final int ROW = 100;

    public enum Tab { PLOTS, MILESTONES, COMPOSTER, SKYMART, VISITOR, PHILLIP, JACOB, ANITA }

    /** A row: title, the line under it, colour, whether its button works, the button's label. */
    public record Row(String title, String detail, int color, boolean enabled, String button) {
        static final StreamCodec<RegistryFriendlyByteBuf, Row> CODEC = new StreamCodec<>() {
            @Override
            public Row decode(RegistryFriendlyByteBuf buf) {
                return new Row(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readBoolean(), buf.readUtf());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Row row) {
                buf.writeUtf(row.title());
                buf.writeUtf(row.detail());
                buf.writeInt(row.color());
                buf.writeBoolean(row.enabled());
                buf.writeUtf(row.button());
            }
        };
    }

    /**
     * The tab, a header line (Garden level, XP, Copper), rows, the plot grid (25 cells: 0 Barn, 1 unlocked, 2 next, 3 locked)
     * and a line at the bottom; {@code visitor} is the visitor's index on the visitor view.
     */
    public record View(Tab tab, String header, List<Row> rows, List<Integer> grid, String footer, int visitor) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(Tab.values()[buf.readVarInt()], buf.readUtf(), Row.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).decode(buf), buf.readUtf(), buf.readVarInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                buf.writeVarInt(view.tab().ordinal());
                buf.writeUtf(view.header());
                Row.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.rows());
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(buf, view.grid());
                buf.writeUtf(view.footer());
                buf.writeVarInt(view.visitor());
            }
        };
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

    static void open(ServerPlayer player, Tab tab) {
        open(player, tab, -1);
    }

    static void openVisitor(ServerPlayer player, int index) {
        open(player, Tab.VISITOR, index);
    }

    private static void open(ServerPlayer player, Tab tab, int visitor) {
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
        String header = "Garden " + Msg.roman(level) + (level < 15 ? "  ·  " + Msg.number(into) + "/" + Msg.number(Garden.LEVEL_XP[level]) + " XP" : "")
                + "  ·  " + Msg.number(state.copper) + " Copper";
        List<Row> rows = new ArrayList<>();
        List<Integer> grid = new ArrayList<>();
        String footer = "";
        switch (tab) {
            case PLOTS -> {
                for (int row = 0; row < 5; row++) {
                    for (int col = 0; col < 5; col++) {
                        int plot = Garden.plotNumber(col, row);
                        grid.add(plot == 0 ? 0 : plot <= state.plots ? 1 : plot == state.plots + 1 ? 2 : 3);
                    }
                }
                if (state.plots < Garden.PLOTS.length) {
                    int next = state.plots + 1;
                    int cost = Garden.PLOTS[state.plots][2];
                    boolean bundles = next >= 12;
                    int needLevel = Garden.plotLevel(next);
                    String costText = cost + " " + (bundles ? "Compost Bundle" + (cost > 1 ? "s" : "") : "Compost");
                    rows.add(new Row("Plot " + next, level < needLevel ? "Needs Garden " + Msg.roman(needLevel) : "Costs " + costText,
                            0x55FF55, level >= needLevel, "Unlock"));
                }
                footer = state.plots + "/24 plots  ·  +" + (3 * state.plots) + " Farming Fortune in the Garden";
            }
            case MILESTONES -> {
                for (Garden.Crop crop : Garden.CROPS) {
                    long count = state.crops.getOrDefault(crop.key(), 0L);
                    int lvl = Garden.milestone(crop, count);
                    long left = Garden.toNext(crop, count);
                    rows.add(new Row(crop.name() + "  " + lvl, Msg.number(count) + " harvested" + (left > 0 ? "  ·  " + Msg.number(left) + " to "
                            + (lvl + 1) : "  ·  maxed"), lvl > 0 ? 0xFFFF55 : 0xAAAAAA, false, ""));
                }
                footer = "Harvest Garden crops here to raise their milestones.";
            }
            case COMPOSTER -> {
                Composter.update(state, new java.util.Random());
                rows.add(new Row("Organic Matter", Msg.number((long) state.organic) + " / " + Msg.number((long) Composter.organicCap(state))
                        + "  ·  crops from your inventory", 0x55FF55, true, "Add crops"));
                rows.add(new Row("Fuel", Msg.number((long) state.fuel) + " / " + Msg.number((long) Composter.fuelCap(state))
                        + "  ·  Biofuel, Oil Barrels, Volta", 0xFFAA00, true, "Add fuel"));
                rows.add(new Row("Compost: " + state.compost, "one every " + Composter.cycleMs(state) / 60_000 + " min for "
                        + Msg.number((long) Composter.cost(state, Composter.ORGANIC)) + " Organic Matter and "
                        + Msg.number((long) Composter.cost(state, Composter.FUEL)) + " Fuel", 0xE07B39, state.compost > 0, "Collect"));
                for (int b = 0; b < Composter.UPGRADES.size(); b++) {
                    int tier = Composter.tier(state, Composter.UPGRADES.get(b));
                    List<String> cost = Composter.nextCost(state, b);
                    rows.add(new Row(Composter.UPGRADE_NAMES.get(b) + "  " + tier + "/25", cost.isEmpty() ? "Maxed" : String.join(", ", cost),
                            0x55FFFF, !cost.isEmpty(), "Upgrade"));
                }
            }
            case SKYMART -> {
                for (Sale sale : sales()) {
                    ItemDefinition def = ItemRegistry.skyblock(sale.item());
                    rows.add(new Row(def.name(), Msg.number(sale.price()) + (sale.coins() ? " coins" : " Copper"),
                            sale.coins() ? 0xFFAA00 : 0xE07B39, true, "Buy"));
                }
                footer = "Copper comes from visitors' offers.";
            }
            case PHILLIP -> {
                header = state.pests + (state.pests == 1 ? " pest" : " pests") + " to spend";
                for (Pests.Sale sale : Pests.PHILLIP) {
                    ItemDefinition def = ItemRegistry.skyblock(sale.item());
                    rows.add(new Row(def == null ? sale.item() : def.name(), sale.pests() + " pests", 0x55FF55,
                            def != null && state.pests >= sale.pests(), "Buy"));
                }
                footer = "Vacuum up pests in your Garden; they're my currency.";
            }
            case JACOB -> {
                header = "Jacob's Farming Contests";
                if (Contests.running()) {
                    rows.add(new Row("Contest running", String.join(", ", Contests.crops().stream().map(Contests::cropName).toList())
                            + "  ·  farm one of them on your Garden", 0xFFFF55, false, ""));
                } else {
                    long next = com.skyrpg.skyblock.time.CalendarEvents.upcoming(System.currentTimeMillis(), 3 * 60 * 60 * 1000L).stream()
                            .filter(o -> o.event().name().equals("Jacob's Farming Contest")).mapToLong(com.skyrpg.skyblock.time.CalendarEvents.Occurrence::start)
                            .findFirst().orElse(0);
                    rows.add(new Row("Next contest", next == 0 ? "soon" : "in " + com.skyrpg.skyblock.time.SkyBlockTime.countdown(next - System.currentTimeMillis())
                            + "  ·  " + String.join(", ", Contests.cropsFor(next).stream().map(Contests::cropName).toList()), 0xFFFF55, false, ""));
                }
                int pending = Contests.medals(player, "pending_tickets");
                rows.add(new Row("Rewards waiting", pending > 0 ? pending + " tickets and your medals" : "nothing yet", 0x55FF55, pending > 0, "Claim"));
                rows.add(new Row("Your medals", Contests.medals(player, "gold") + " Gold  ·  " + Contests.medals(player, "silver") + " Silver  ·  "
                        + Contests.medals(player, "bronze") + " Bronze", 0xFFAA00, false, ""));
                footer = "Farming X to take part; 100 crops for a reward. Anita sells gear for tickets and medals.";
            }
            case ANITA -> {
                header = Contests.medals(player, "gold") + " Gold, " + Contests.medals(player, "silver") + " Silver, "
                        + Contests.medals(player, "bronze") + " Bronze";
                for (Contests.Sale sale : Contests.ANITA) {
                    ItemDefinition def = ItemRegistry.skyblock(sale.item());
                    rows.add(new Row((sale.count() > 1 ? sale.count() + "x " : "") + (def == null ? sale.item() : def.name()), Contests.price(sale),
                            0xFFFF55, def != null, "Buy"));
                }
                rows.add(new Row("2 Silver medals", "for 1 Gold medal", 0xFFFFFF, true, "Trade"));
                rows.add(new Row("2 Bronze medals", "for 1 Silver medal", 0xFF5555, true, "Trade"));
                footer = "Jacob's Tickets and medals come from his Farming Contests.";
            }
            case VISITOR -> {
                if (visitor >= 0 && visitor < state.visitors.size()) {
                    Visitors.Offer offer = Visitors.Offer.decode(state.visitors.get(visitor));
                    header = offer.name() + "  ·  " + Visitors.rarity(offer.rarity()) + " visitor";
                    for (Map.Entry<String, Integer> want : offer.wants().entrySet()) {
                        long have = com.skyrpg.skyblock.sack.Sacks.countAnywhere(player, want.getKey());
                        rows.add(new Row(want.getValue() + "x " + Visitors.name(want.getKey()), "You have " + Msg.number(have),
                                have >= want.getValue() ? 0x55FF55 : 0xFF5555, false, ""));
                    }
                    rows.add(new Row("Rewards", "+" + offer.copper() + " Copper  ·  +" + Msg.number(offer.gardenXp()) + " Garden XP  ·  +"
                            + Msg.number(offer.farmingXp()) + " Farming XP", 0xE07B39, false, ""));
                    rows.add(new Row("Accept the offer", "Hand over the items", 0x55FF55, true, "Accept"));
                    rows.add(new Row("Decline", "They leave", 0xFF5555, true, "Decline"));
                }
            }
        }
        View view = new View(tab, header, rows, grid, footer, visitor);
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
        if (id >= TAB && id < TAB + 4 && view.tab().ordinal() < 4) {
            owner.level().getServer().execute(() -> open(owner, Tab.values()[id - TAB]));
            return true;
        }
        int row = id - ROW;
        String problem = "";
        switch (view.tab()) {
            case PLOTS -> problem = unlock(owner, profile, state);
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
            default -> {
            }
        }
        GardenData.get(owner.level().getServer()).setDirty();
        if (!problem.isEmpty()) {
            owner.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
        }
        owner.level().getServer().execute(() -> open(owner, view.tab(), view.visitor()));
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
        if (com.skyrpg.skyblock.sack.Sacks.countAnywhere(player, item) < cost) {
            return "You need " + cost + " " + Visitors.name(item) + ".";
        }
        com.skyrpg.skyblock.sack.Sacks.takeAnywhere(player, item, cost);
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
