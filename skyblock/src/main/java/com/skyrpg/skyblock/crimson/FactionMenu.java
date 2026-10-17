package com.skyrpg.skyblock.crimson;

import com.skyrpg.skyblock.storage.SkyMenus;
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
 * A faction leader's screen (Queen Nyx, Chief Scorn): your reputation and rank with their faction, and, as a member,
 * today's five quests. Not yet a member: join (once Elle's work is done and you've met both leaders) or switch sides.
 * Button {@link #JOIN}.
 */
public final class FactionMenu extends AbstractContainerMenu {
    public static final int JOIN = 1;

    /** What the screen offers: quests (a member), joining, switching, or nothing yet (Elle first). */
    public enum State { MEMBER, JOIN, SWITCH, LOCKED }

    /** A quest row: its text, where to do it, progress, the reputation it pays, and whether it's done. */
    public record QuestRow(String text, String where, int progress, int amount, int rep, boolean done) {
        static final StreamCodec<RegistryFriendlyByteBuf, QuestRow> CODEC = new StreamCodec<>() {
            @Override
            public QuestRow decode(RegistryFriendlyByteBuf buf) {
                return new QuestRow(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, QuestRow row) {
                buf.writeUtf(row.text());
                buf.writeUtf(row.where());
                buf.writeVarInt(row.progress());
                buf.writeVarInt(row.amount());
                buf.writeVarInt(row.rep());
                buf.writeBoolean(row.done());
            }
        };
    }

    /**
     * The leader's faction ("mage", "barbarian"), the leader's name, your reputation with that faction, the screen's state,
     * a note (why you can't join yet), the quests, and the seconds until new ones.
     */
    public record View(String faction, String leader, int rep, State state, String note, List<QuestRow> quests, long reset) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(buf.readUtf(), buf.readUtf(), buf.readVarInt(), State.values()[buf.readVarInt()], buf.readUtf(),
                        QuestRow.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readVarLong());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                buf.writeUtf(view.faction());
                buf.writeUtf(view.leader());
                buf.writeVarInt(view.rep());
                buf.writeVarInt(view.state().ordinal());
                buf.writeUtf(view.note());
                QuestRow.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.quests());
                buf.writeVarLong(view.reset());
            }
        };
    }

    private final View view;
    private final ServerPlayer owner;

    public FactionMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private FactionMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.FACTION, id);
        this.view = view;
        this.owner = owner;
    }

    public View view() {
        return view;
    }

    static void open(ServerPlayer player, String faction) {
        String mine = Factions.faction(player);
        State state;
        String note = "";
        if (mine.equals(faction)) {
            state = State.MEMBER;
        } else if (Factions.canJoin(player)) {
            state = mine.isEmpty() ? State.JOIN : State.SWITCH;
        } else {
            state = State.LOCKED;
            note = Factions.elle(player) < Factions.ELLE_LEADERS ? "Elle, at the Stronghold, has work for you first."
                    : "Meet " + Factions.leader(Factions.other(faction)) + " in " + Factions.town(Factions.other(faction)) + " too, then choose.";
        }
        View view = new View(faction, Factions.leader(faction), Factions.reputation(player, faction), state, note,
                state == State.MEMBER ? FactionQuests.rows(player) : List.of(), FactionQuests.secondsLeft());
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal(Factions.leader(faction));
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new FactionMenu(id, view, player);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner || id != JOIN) {
            return false;
        }
        if ((view.state() == State.JOIN || view.state() == State.SWITCH) && Factions.canJoin(owner)) {
            Factions.join(owner, view.faction());
            owner.level().getServer().execute(() -> open(owner, view.faction()));
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
