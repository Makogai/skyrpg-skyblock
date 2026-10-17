package com.skyrpg.skyblock.dungeon;

import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Party Finder, at Mort: a party leader lists their party for a floor; anyone can join a listing that has
 * room (the leader's party invites them and they accept). Listings go when the leader leaves, the party fills, or after
 * 30 minutes. Buttons: {@link #JOIN} + row; {@link #LIST}; {@link #UNLIST}; {@link #FLOOR_PREV} / {@link #FLOOR_NEXT}.
 */
public final class PartyFinder extends AbstractContainerMenu {
    public static final int JOIN = 100;
    public static final int LIST = 1;
    public static final int UNLIST = 2;
    public static final int FLOOR_PREV = 3;
    public static final int FLOOR_NEXT = 4;
    private static final long LIFETIME = 30 * 60 * 1000L;

    /** A listing: the leader, the floor ("F3", "M1"), when it was made. */
    record Listing(UUID leader, String name, String floor, long created) {
    }

    private static final Map<UUID, Listing> LISTINGS = new LinkedHashMap<>();
    /** The floor each player has picked for their own listing (index into the codes). */
    private static final Map<UUID, Integer> PICKED = new HashMap<>();
    private static final List<String> CODES = List.of("E", "F1", "F2", "F3", "F4", "F5", "F6", "F7", "M1", "M2", "M3", "M4", "M5", "M6", "M7");

    /** One row: the text, and whether you can join it. */
    public record Row(String text, boolean joinable) {
        static final StreamCodec<RegistryFriendlyByteBuf, Row> CODEC = new StreamCodec<>() {
            @Override
            public Row decode(RegistryFriendlyByteBuf buf) {
                return new Row(buf.readUtf(), buf.readBoolean());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Row row) {
                buf.writeUtf(row.text());
                buf.writeBoolean(row.joinable());
            }
        };
    }

    /** The listings, whether you have one, and the floor you'd list for. */
    public record View(List<Row> rows, boolean listed, String floor) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(Row.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readBoolean(), buf.readUtf());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                Row.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.rows());
                buf.writeBoolean(view.listed());
                buf.writeUtf(view.floor());
            }
        };
    }

    private final View view;
    private final ServerPlayer owner;
    private final List<UUID> order;

    public PartyFinder(int id, Inventory inventory, View view) {
        this(id, view, null, List.of());
    }

    private PartyFinder(int id, View view, ServerPlayer owner, List<UUID> order) {
        super(SkyMenus.PARTY_FINDER, id);
        this.view = view;
        this.owner = owner;
        this.order = order;
    }

    public View view() {
        return view;
    }

    private static void prune(MinecraftServer server) {
        long now = System.currentTimeMillis();
        LISTINGS.values().removeIf(l -> now - l.created() > LIFETIME || server.getPlayerList().getPlayer(l.leader()) == null
                || size(l.leader()) >= 5);
    }

    private static int size(UUID leader) {
        com.skyrpg.party.PartyData.Party party = com.skyrpg.party.Parties.partyOf(leader);
        return party == null ? 1 : party.members.size();
    }

    static void open(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        prune(server);
        List<Row> rows = new ArrayList<>();
        List<UUID> order = new ArrayList<>();
        for (Listing listing : LISTINGS.values()) {
            com.skyrpg.party.PartyData.Party party = com.skyrpg.party.Parties.partyOf(listing.leader());
            List<String> classes = new ArrayList<>();
            if (party != null) {
                for (com.skyrpg.party.PartyData.Member m : party.members) {
                    ServerPlayer member = server.getPlayerList().getPlayer(m.id);
                    if (member != null) {
                        classes.add(DungeonXp.title(DungeonXp.selected(member)));
                    }
                }
            } else {
                ServerPlayer leader = server.getPlayerList().getPlayer(listing.leader());
                if (leader != null) {
                    classes.add(DungeonXp.title(DungeonXp.selected(leader)));
                }
            }
            boolean mine = listing.leader().equals(player.getUUID()) || com.skyrpg.party.Parties.sameParty(listing.leader(), player.getUUID());
            rows.add(new Row(listing.name() + "  ·  " + listing.floor() + "  ·  " + size(listing.leader()) + "/5  ·  "
                    + String.join(", ", classes), !mine));
            order.add(listing.leader());
        }
        String floor = CODES.get(PICKED.getOrDefault(player.getUUID(), 1));
        View view = new View(rows, LISTINGS.containsKey(player.getUUID()), floor);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Party Finder");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new PartyFinder(id, view, player, order);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        MinecraftServer server = owner.level().getServer();
        switch (id) {
            case FLOOR_PREV, FLOOR_NEXT -> PICKED.put(owner.getUUID(),
                    Math.floorMod(PICKED.getOrDefault(owner.getUUID(), 1) + (id == FLOOR_NEXT ? 1 : -1), CODES.size()));
            case LIST -> {
                com.skyrpg.party.PartyData.Party party = com.skyrpg.party.Parties.partyOf(owner.getUUID());
                if (party != null && !owner.getUUID().equals(party.leader)) {
                    owner.sendSystemMessage(Component.literal("Only your party's leader can list it.").withStyle(ChatFormatting.RED));
                } else {
                    LISTINGS.put(owner.getUUID(), new Listing(owner.getUUID(), owner.getScoreboardName(),
                            CODES.get(PICKED.getOrDefault(owner.getUUID(), 1)), System.currentTimeMillis()));
                    owner.sendSystemMessage(Component.literal("Your party is listed in the Party Finder.").withStyle(ChatFormatting.GREEN));
                }
            }
            case UNLIST -> LISTINGS.remove(owner.getUUID());
            default -> {
                int row = id - JOIN;
                if (row >= 0 && row < order.size()) {
                    join(owner, order.get(row));
                }
            }
        }
        server.execute(() -> open(owner));
        return true;
    }

    private static void join(ServerPlayer player, UUID leaderId) {
        MinecraftServer server = player.level().getServer();
        ServerPlayer leader = server.getPlayerList().getPlayer(leaderId);
        if (leader == null || !LISTINGS.containsKey(leaderId)) {
            player.sendSystemMessage(Component.literal("That listing is gone.").withStyle(ChatFormatting.RED));
            return;
        }
        if (com.skyrpg.party.Parties.partyOf(player.getUUID()) != null) {
            player.sendSystemMessage(Component.literal("Leave your party first (/party leave).").withStyle(ChatFormatting.RED));
            return;
        }
        com.skyrpg.party.Parties.invite(leader, player);
        com.skyrpg.party.Parties.accept(player, leader.getScoreboardName());
        leader.sendSystemMessage(Component.literal(player.getScoreboardName() + " joined from the Party Finder ("
                + DungeonXp.title(DungeonXp.selected(player)) + ").").withStyle(ChatFormatting.GREEN));
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
