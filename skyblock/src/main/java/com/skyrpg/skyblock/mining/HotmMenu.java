package com.skyrpg.skyblock.mining;

import com.skyrpg.core.text.Msg;
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
 * The Heart of the Mountain tree: your HotM level and XP, Tokens of the Mountain and powder, and every perk on its grid
 * spot. Button {@code i} clicks perk {@code i} (unlock, upgrade once or select an ability), {@link #TEN} {@code + i}
 * upgrades it ten times, {@link #TOGGLE} {@code + i} switches it on or off.
 */
public final class HotmMenu extends AbstractContainerMenu {
    public static final int TEN = 1000;
    public static final int TOGGLE = 2000;

    /** 0 = can't be unlocked yet, 1 = can be unlocked, 2 = unlocked, 3 = maxed. */
    public record PerkView(String id, String name, int x, int y, int level, int max, int state, boolean enabled, boolean ability,
            boolean selected, List<String> lore, String cost) {
        static final StreamCodec<RegistryFriendlyByteBuf, PerkView> CODEC = new StreamCodec<>() {
            @Override
            public PerkView decode(RegistryFriendlyByteBuf buf) {
                return new PerkView(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf), buf.readUtf());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, PerkView view) {
                buf.writeUtf(view.id());
                buf.writeUtf(view.name());
                buf.writeVarInt(view.x());
                buf.writeVarInt(view.y());
                buf.writeVarInt(view.level());
                buf.writeVarInt(view.max());
                buf.writeVarInt(view.state());
                buf.writeBoolean(view.enabled());
                buf.writeBoolean(view.ability());
                buf.writeBoolean(view.selected());
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, view.lore());
                buf.writeUtf(view.cost());
            }
        };
    }

    public record View(int level, int maxLevel, long xp, long xpNext, int tokens, long mithril, long gemstone, long glacite,
            List<PerkView> perks) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(),
                        buf.readVarLong(), buf.readVarLong(), PerkView.CODEC.apply(ByteBufCodecs.list()).decode(buf));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                buf.writeVarInt(view.level());
                buf.writeVarInt(view.maxLevel());
                buf.writeVarLong(view.xp());
                buf.writeVarLong(view.xpNext());
                buf.writeVarInt(view.tokens());
                buf.writeVarLong(view.mithril());
                buf.writeVarLong(view.gemstone());
                buf.writeVarLong(view.glacite());
                PerkView.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.perks());
            }
        };
    }

    private final View view;
    private final ServerPlayer owner;

    public HotmMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private HotmMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.HOTM, id);
        this.view = view;
        this.owner = owner;
    }

    public View view() {
        return view;
    }

    static void init() {
        // The menu type is registered with the other SkyBlock menus (SkyMenus.HOTM).
    }

    public static void open(ServerPlayer player) {
        View view = view(player);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Heart of the Mountain");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new HotmMenu(id, view, player);
            }
        });
    }

    private static View view(ServerPlayer player) {
        Hotm.State state = Hotm.state(player);
        int level = Hotm.level(state.xp());
        boolean peak = state.perk("core_of_the_mountain") >= 2;
        List<PerkView> perks = new ArrayList<>();
        for (HotmTree.Perk perk : HotmTree.all()) {
            int at = state.perk(perk.id());
            int shown = Math.max(1, at);
            int stateCode = at <= 0 ? (Hotm.unlockable(state, perk) ? 1 : 0) : at >= perk.max() ? 3 : 2;
            List<String> lore = new ArrayList<>(perk.loreAt(at <= 0 ? 0 : at, peak));
            String cost;
            if (at <= 0) {
                cost = perk.tier() > level ? "§cRequires HotM " + perk.tier() : "§7Cost: §51 Token of the Mountain";
            } else if (at < perk.max() && !perk.single()) {
                String type = perk.powderAt(at);
                cost = "§7Next level: §" + powderCode(type) + Msg.number(perk.costAt(at)) + " " + Hotm.powderName(type);
                if (!perk.values().isEmpty()) {
                    // What the next level gives, as Hypixel shows it.
                    List<String> next = perk.loreAt(at + 1, peak);
                    if (!next.equals(perk.loreAt(at, peak))) {
                        lore.add("");
                        lore.add("§7At level " + (at + 1) + ":");
                        lore.addAll(next);
                    }
                }
            } else {
                cost = "";
            }
            perks.add(new PerkView(perk.id(), perk.name(), perk.x(), perk.y(), at, perk.max(), stateCode, !state.disabled().contains(perk.id()),
                    perk.ability(), perk.id().equals(state.ability()), lore, cost));
        }
        long into = state.xp() - HotmTree.totalFor(level);
        long next = level >= HotmTree.maxLevel() ? 0 : HotmTree.xpFor(level);
        return new View(level, HotmTree.maxLevel(), into, next, Hotm.tokens(state) - Hotm.tokensSpent(state), state.powder(Hotm.MITHRIL),
                state.powder(Hotm.GEMSTONE), state.powder(Hotm.GLACITE), perks);
    }

    private static String powderCode(String type) {
        return switch (type) {
            case Hotm.GEMSTONE -> "d";
            case Hotm.GLACITE -> "b";
            default -> "2";
        };
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        int index = id % 1000;
        if (index < 0 || index >= view.perks().size()) {
            return false;
        }
        String perk = view.perks().get(index).id();
        if (id >= TOGGLE) {
            Hotm.toggle(owner, perk);
        } else {
            String problem = Hotm.click(owner, perk, id >= TEN ? 10 : 1);
            if (!problem.isEmpty()) {
                owner.sendOverlayMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
            }
        }
        owner.level().getServer().execute(() -> open(owner));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || player == owner && owner.isAlive();
    }
}
