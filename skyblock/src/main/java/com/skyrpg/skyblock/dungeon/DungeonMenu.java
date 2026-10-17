package com.skyrpg.skyblock.dungeon;

import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.ArrayList;
import java.util.List;
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
 * Mort's screen in the Dungeon Hub: your Catacombs and class levels, the class you'll play (click one), and the floors
 * (click one to take your party in). Buttons: {@link #CLASS} + class index; {@link #FLOOR} + floor number.
 */
public final class DungeonMenu extends AbstractContainerMenu {
    public static final int CLASS = 100;
    public static final int FLOOR = 200;

    /** A floor as shown: number, title, boss, Catacombs level needed, open to you. */
    public record FloorView(int number, String title, String boss, int requires, boolean open) {
        static final StreamCodec<RegistryFriendlyByteBuf, FloorView> CODEC = new StreamCodec<>() {
            @Override
            public FloorView decode(RegistryFriendlyByteBuf buf) {
                return new FloorView(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readBoolean());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, FloorView floor) {
                buf.writeVarInt(floor.number());
                buf.writeUtf(floor.title());
                buf.writeUtf(floor.boss());
                buf.writeVarInt(floor.requires());
                buf.writeBoolean(floor.open());
            }
        };
    }

    /** Catacombs level and XP into it; each class's level; the picked class; the floors; party size; leader or solo. */
    public record View(int catacombs, int classLevels0, int classLevels1, int classLevels2, int classLevels3, int classLevels4, String picked,
                       List<FloorView> floors, int party, boolean leader) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
                        FloorView.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readVarInt(), buf.readBoolean());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                buf.writeVarInt(view.catacombs());
                buf.writeVarInt(view.classLevels0());
                buf.writeVarInt(view.classLevels1());
                buf.writeVarInt(view.classLevels2());
                buf.writeVarInt(view.classLevels3());
                buf.writeVarInt(view.classLevels4());
                buf.writeUtf(view.picked());
                FloorView.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.floors());
                buf.writeVarInt(view.party());
                buf.writeBoolean(view.leader());
            }
        };

        public int classLevel(int index) {
            return switch (index) {
                case 0 -> classLevels0;
                case 1 -> classLevels1;
                case 2 -> classLevels2;
                case 3 -> classLevels3;
                default -> classLevels4;
            };
        }
    }

    public static final String[] BOSSES = {"The Watcher", "Bonzo", "Scarf", "The Professor", "Thorn", "Livid", "Sadan", "Necron"};

    private final View view;
    private final ServerPlayer owner;

    public DungeonMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private DungeonMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.DUNGEON, id);
        this.view = view;
        this.owner = owner;
    }

    public View view() {
        return view;
    }

    public static void open(ServerPlayer player) {
        int level = DungeonXp.level(player, DungeonXp.CATACOMBS);
        List<FloorView> floors = new ArrayList<>();
        for (Floors.Floor floor : Floors.ALL) {
            floors.add(new FloorView(floor.number(), floor.number() == 0 ? "Entrance" : "Floor " + Floors.roman(floor.number()),
                    BOSSES[floor.number()], floor.catacombs(), level >= floor.catacombs()));
        }
        com.skyrpg.party.PartyData.Party party = com.skyrpg.party.Parties.partyOf(player.getUUID());
        int[] classes = new int[5];
        for (int i = 0; i < 5; i++) {
            classes[i] = DungeonXp.level(player, DungeonXp.CLASSES.get(i));
        }
        View view = new View(level, classes[0], classes[1], classes[2], classes[3], classes[4], DungeonXp.selected(player), floors,
                party == null ? 1 : Math.min(5, party.members.size()), party == null || player.getUUID().equals(party.leader));
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("The Catacombs");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new DungeonMenu(id, view, player);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        if (id >= CLASS && id < CLASS + DungeonXp.CLASSES.size()) {
            DungeonXp.select(owner, DungeonXp.CLASSES.get(id - CLASS));
            owner.level().getServer().execute(() -> open(owner));
            return true;
        }
        if (id >= FLOOR && id < FLOOR + Floors.ALL.size()) {
            if (!view.leader()) {
                owner.sendSystemMessage(Component.literal("Only your party's leader can start a dungeon!").withStyle(ChatFormatting.RED));
                return true;
            }
            int floor = id - FLOOR;
            owner.level().getServer().execute(() -> {
                owner.closeContainer();
                Catacombs.start(owner, floor, false);
            });
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
