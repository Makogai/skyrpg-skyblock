package com.skyrpg.skyblock.crimson;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.storage.SkyMenus;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Crimson Isle's list screens, as rows with a button each ({@link #ROW} + row): the Kuudra Gatekeeper's tiers (start
 * one), an Emissary's keys (buy one), the two reward chests after a Kuudra win (open one), and Master Tao's Dojo tests
 * (take one).
 */
public final class KuudraMenu extends AbstractContainerMenu {
    public static final int ROW = 100;

    public enum Mode { TIERS, KEYS, REWARDS, DOJO }

    /**
     * A row: its title, the line under it, its colour, whether its button works, the button's label, its icon, its price or
     * requirement chips, a progress bar (-1: none), a note on the right, and reward items shown as icons.
     */
    public record Row(String title, String detail, int color, boolean enabled, String button, ItemStack icon, List<Chip> chips, float progress,
                      String note, List<ItemStack> items) {
        static final StreamCodec<RegistryFriendlyByteBuf, Row> CODEC = new StreamCodec<>() {
            @Override
            public Row decode(RegistryFriendlyByteBuf buf) {
                return new Row(buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readBoolean(), buf.readUtf(),
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), Chip.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readFloat(), buf.readUtf(),
                        ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Row row) {
                buf.writeUtf(row.title());
                buf.writeUtf(row.detail());
                buf.writeInt(row.color());
                buf.writeBoolean(row.enabled());
                buf.writeUtf(row.button());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, row.icon());
                Chip.CODEC.apply(ByteBufCodecs.list()).encode(buf, row.chips());
                buf.writeFloat(row.progress());
                buf.writeUtf(row.note());
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, row.items());
            }
        };
    }

    /** The mode, the title and subtitle, the rows, a line at the bottom, the NPC's entity id (-1: none) and your balances. */
    public record View(Mode mode, String title, String subtitle, List<Row> rows, String footer, int npc, List<Chip> wallet) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(Mode.values()[buf.readVarInt()], buf.readUtf(), buf.readUtf(), Row.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                        buf.readUtf(), buf.readVarInt(), Chip.CODEC.apply(ByteBufCodecs.list()).decode(buf));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                buf.writeVarInt(view.mode().ordinal());
                buf.writeUtf(view.title());
                buf.writeUtf(view.subtitle());
                Row.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.rows());
                buf.writeUtf(view.footer());
                buf.writeVarInt(view.npc());
                Chip.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.wallet());
            }
        };
    }

    private static final int[] TIER_COLORS = {0xFFAA00, 0xFF8800, 0xFF5500, 0xFF2200, 0xAA0000};

    private final View view;
    private final ServerPlayer owner;

    public KuudraMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private KuudraMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.KUUDRA, id);
        this.view = view;
        this.owner = owner;
    }

    public View view() {
        return view;
    }

    private static ItemStack icon(String id, net.minecraft.world.item.Item fallback) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? new ItemStack(fallback) : SkyItems.create(def, 1);
    }

    private static final List<net.minecraft.world.item.Item> TEST_ICONS = List.of(Items.IRON_SWORD, Items.GOLDEN_APPLE, Items.DIAMOND_SWORD,
            Items.BOOK, Items.SUGAR, Items.BOW, Items.SHIELD);

    static void open(ServerPlayer player, Mode mode) {
        long coins = Wallet.balance(player);
        List<Chip> wallet = new ArrayList<>(List.of(Chip.balance("coins", coins)));
        String npcName = switch (mode) {
            case TIERS -> "Gatekeeper";
            case KEYS -> "Emissary";
            case DOJO -> "Tao";
            default -> null;
        };
        int npc = npcName == null ? -1 : FactionMenu.npcNear(player, npcName);
        List<Row> rows = new ArrayList<>();
        String title;
        String subtitle;
        String footer;
        switch (mode) {
            case TIERS -> {
                title = "Kuudra's Hollow";
                subtitle = "Up to 4 players";
                for (Kuudra.Tier tier : Kuudra.TIERS) {
                    String locked = Kuudra.locked(player, tier);
                    ItemStack key = icon(tier.key(), Items.TRIPWIRE_HOOK);
                    rows.add(new Row(tier.title(), locked != null ? locked : "Kuudra " + Msg.number((long) tier.kuudraHealth()) + "❤",
                            TIER_COLORS[tier.number() - 1], locked == null, "Start", key,
                            List.of(Chip.item(key, 1, SkyStacks.count(player.getInventory(), tier.key()))), -1,
                            Kuudra.clears(player, tier.number()) + "x cleared", List.of()));
                }
                footer = "Your party's leader starts the fight for everyone.";
            }
            case KEYS -> {
                String faction = Factions.faction(player);
                title = faction.isEmpty() ? "Emissary" : Factions.title(faction) + " Emissary";
                subtitle = "Kuudra Keys";
                int rep = faction.isEmpty() ? 0 : Factions.reputation(player, faction);
                String material = faction.isEmpty() ? "ENCHANTED_RED_SAND" : Kuudra.material(faction);
                ItemStack materialIcon = icon(material, Items.RED_SAND);
                ItemStack star = icon("CORRUPTED_NETHER_STAR", Items.NETHER_STAR);
                int materials = SkyStacks.count(player.getInventory(), material);
                int stars = SkyStacks.count(player.getInventory(), "CORRUPTED_NETHER_STAR");
                wallet.add(Chip.item(materialIcon, materials, materials));
                wallet.add(Chip.item(star, stars, stars));
                for (Kuudra.Tier tier : Kuudra.TIERS) {
                    boolean ok = !faction.isEmpty() && rep >= tier.keyRep();
                    List<Chip> chips = new ArrayList<>();
                    chips.add(Chip.price("coins", tier.keyCoins(), coins));
                    chips.add(Chip.item(materialIcon, tier.keyMaterial(), materials));
                    chips.add(Chip.item(star, 2, stars));
                    rows.add(new Row(CrimsonIsle.itemName(tier.key()), tier.keyRep() > 0 ? "Needs " + Msg.number(tier.keyRep()) + " "
                            + (faction.isEmpty() ? "faction" : Factions.title(faction)) + " Reputation" : "No reputation needed",
                            TIER_COLORS[tier.number() - 1], ok, "Buy", icon(tier.key(), Items.TRIPWIRE_HOOK), chips,
                            tier.keyRep() > 0 ? Math.min(1f, (float) rep / tier.keyRep()) : -1, ok ? "" : "Locked", List.of()));
                }
                footer = faction.isEmpty() ? "Join a faction to buy keys." : "Keys open the paid chest of their tier.";
            }
            case DOJO -> {
                title = "Master Tao";
                int belt = Dojo.belt(player);
                subtitle = Dojo.beltName(belt);
                for (String test : Dojo.TESTS) {
                    int best = Dojo.best(player, test);
                    rows.add(new Row(test, best > 0 ? "Best " + Msg.number(best) : "Not taken yet",
                            best >= 1_000 ? 0xFFAA00 : 0xFFFF55, !Dojo.busy(), "Test",
                            new ItemStack(TEST_ICONS.get(Math.min(TEST_ICONS.size() - 1, Dojo.TESTS.indexOf(test)))), List.of(),
                            Math.min(1f, best / 1_000f), best > 0 ? "Grade " + Dojo.grade(best) : "", List.of()));
                }
                int total = Dojo.total(player);
                int next = -1;
                for (int i = 1; i < Dojo.BELT_POINTS.length; i++) {
                    if (total < Dojo.BELT_POINTS[i]) {
                        next = i;
                        break;
                    }
                }
                footer = "Your best scores add up to " + Msg.number(total) + (next > 0 ? ": the " + Dojo.beltName(next + 1) + " at "
                        + Msg.number(Dojo.BELT_POINTS[next]) : ": every belt") + ". Empty hands help.";
            }
            default -> {
                Kuudra.Chests chests = Kuudra.CHESTS.get(player.getUUID());
                title = "Kuudra's Chests";
                subtitle = chests == null ? "" : chests.tier().title();
                if (chests != null) {
                    ItemStack essence = new ItemStack(Items.CRIMSON_FUNGUS);
                    rows.add(new Row("Free Chest", "Every run pays out its essence", 0xAAAAAA, !chests.opened()[0],
                            chests.opened()[0] ? "Opened" : "Open", new ItemStack(Items.CHEST),
                            List.of(Chip.note(essence, Msg.number(chests.freeEssence()) + " Crimson Essence", true)), -1,
                            chests.opened()[0] ? "Opened" : "Free", chests.free()));
                    ItemStack keyIcon = icon(chests.tier().key(), Items.TRIPWIRE_HOOK);
                    rows.add(new Row("Paid Chest", "Opens with a " + CrimsonIsle.itemName(chests.tier().key()), 0xFFAA00,
                            !chests.opened()[1], chests.opened()[1] ? "Opened" : "Open", new ItemStack(Items.ENDER_CHEST),
                            List.of(Chip.note(essence, Msg.number(chests.paidEssence()) + " Crimson Essence", true),
                                    Chip.item(keyIcon, 1, SkyStacks.count(player.getInventory(), chests.tier().key()))), -1,
                            chests.opened()[1] ? "Opened" : "Paid", chests.paid()));
                }
                footer = "You're sent back to the Forgotten Skull in a minute (/kuudra leave to go now).";
            }
        }
        View view = new View(mode, title, subtitle, rows, footer, npc, wallet);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal(view.title());
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new KuudraMenu(id, view, player);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        int row = id - ROW;
        if (row < 0 || row >= view.rows().size() || !view.rows().get(row).enabled()) {
            return false;
        }
        switch (view.mode()) {
            case TIERS -> {
                owner.closeContainer();
                Kuudra.start(owner, row + 1, false);
            }
            case KEYS -> {
                Kuudra.buyKey(owner, Kuudra.TIERS.get(row));
                owner.level().getServer().execute(() -> open(owner, Mode.KEYS));
            }
            case DOJO -> {
                owner.closeContainer();
                Dojo.start(owner, Dojo.TESTS.get(row));
            }
            default -> {
                Kuudra.open(owner, row == 1);
                owner.level().getServer().execute(() -> open(owner, Mode.REWARDS));
            }
        }
        return true;
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
