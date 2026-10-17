package com.skyrpg.skyblock.crimson;

import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.storage.SkyMenus;
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

/**
 * Kuudra's screens, as rows with a button each ({@link #ROW} + row): the Gatekeeper's tiers (start one), an Emissary's
 * keys (buy one) and the two reward chests after a win (open one).
 */
public final class KuudraMenu extends AbstractContainerMenu {
    public static final int ROW = 100;

    public enum Mode { TIERS, KEYS, REWARDS }

    /** A row: its title, the line under it, its colour, whether its button works, and the button's label. */
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

    /** The mode, the title and subtitle, the rows and a line at the bottom. */
    public record View(Mode mode, String title, String subtitle, List<Row> rows, String footer) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(Mode.values()[buf.readVarInt()], buf.readUtf(), buf.readUtf(), Row.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                        buf.readUtf());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                buf.writeVarInt(view.mode().ordinal());
                buf.writeUtf(view.title());
                buf.writeUtf(view.subtitle());
                Row.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.rows());
                buf.writeUtf(view.footer());
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

    static void open(ServerPlayer player, Mode mode) {
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
                    rows.add(new Row(tier.title(), locked != null ? locked : "Kuudra " + Msg.number((long) tier.kuudraHealth()) + "❤  ·  cleared "
                            + Kuudra.clears(player, tier.number()) + "x  ·  key: " + CrimsonIsle.itemName(tier.key()),
                            TIER_COLORS[tier.number() - 1], locked == null, "Start"));
                }
                footer = "Your party's leader starts the fight for everyone.";
            }
            case KEYS -> {
                String faction = Factions.faction(player);
                title = faction.isEmpty() ? "Emissary" : Factions.title(faction) + " Emissary";
                subtitle = "Kuudra Keys";
                int rep = faction.isEmpty() ? 0 : Factions.reputation(player, faction);
                for (Kuudra.Tier tier : Kuudra.TIERS) {
                    boolean ok = !faction.isEmpty() && rep >= tier.keyRep();
                    rows.add(new Row(CrimsonIsle.itemName(tier.key()), Msg.number(tier.keyCoins()) + " coins  ·  " + tier.keyMaterial() + " "
                            + (faction.isEmpty() ? "Enchanted Red Sand / Mycelium" : CrimsonIsle.itemName(Kuudra.material(faction)))
                            + "  ·  2 Corrupted Nether Stars" + (tier.keyRep() > 0 ? "  ·  " + Msg.number(tier.keyRep()) + " Rep" : ""),
                            TIER_COLORS[tier.number() - 1], ok, "Buy"));
                }
                footer = faction.isEmpty() ? "Join a faction to buy keys." : "Keys open the paid chest of their tier.";
            }
            default -> {
                Kuudra.Chests chests = Kuudra.CHESTS.get(player.getUUID());
                title = "Kuudra's Chests";
                subtitle = chests == null ? "" : chests.tier().title();
                if (chests != null) {
                    rows.add(new Row("Free Chest", Msg.number(chests.freeEssence()) + " Crimson Essence", 0xAAAAAA, !chests.opened()[0],
                            chests.opened()[0] ? "Opened" : "Open"));
                    StringBuilder paid = new StringBuilder(Msg.number(chests.paidEssence()) + " Crimson Essence");
                    for (ItemStack stack : chests.paid()) {
                        paid.append(", ").append(stack.getHoverName().getString()).append(stack.getCount() > 1 ? " x" + stack.getCount() : "");
                    }
                    rows.add(new Row("Paid Chest (" + CrimsonIsle.itemName(chests.tier().key()) + ")", paid.toString(), 0xFFAA00,
                            !chests.opened()[1], chests.opened()[1] ? "Opened" : "Open"));
                }
                footer = "You're sent back to the Forgotten Skull in a minute (/kuudra leave to go now).";
            }
        }
        View view = new View(mode, title, subtitle, rows, footer);
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
