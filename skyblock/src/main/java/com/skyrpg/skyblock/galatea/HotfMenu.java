package com.skyrpg.skyblock.galatea;

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
 * The Heart of the Forest tree: your HotF tier and XP, Tokens of the Forest and Whispers, and every perk on its grid
 * spot. Button {@code i} clicks perk {@code i} (unlock, upgrade once or select an ability), {@link #TEN} {@code + i}
 * upgrades it ten times, {@link #TOGGLE} {@code + i} switches it on or off.
 */
public final class HotfMenu extends AbstractContainerMenu {
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

    public record View(int level, int maxLevel, long xp, long xpNext, int tokens, long forest, long desert, List<PerkView> perks) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(),
                        buf.readVarLong(), PerkView.CODEC.apply(ByteBufCodecs.list()).decode(buf));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                buf.writeVarInt(view.level());
                buf.writeVarInt(view.maxLevel());
                buf.writeVarLong(view.xp());
                buf.writeVarLong(view.xpNext());
                buf.writeVarInt(view.tokens());
                buf.writeVarLong(view.forest());
                buf.writeVarLong(view.desert());
                PerkView.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.perks());
            }
        };
    }

    private final View view;
    private final ServerPlayer owner;

    public HotfMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private HotfMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.HOTF, id);
        this.view = view;
        this.owner = owner;
    }

    public View view() {
        return view;
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
                return Component.literal("Heart of the Forest");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new HotfMenu(id, view, player);
            }
        });
    }

    private static View view(ServerPlayer player) {
        Hotf.State state = Hotf.state(player);
        int level = Hotf.tier(state.xp());
        List<PerkView> perks = new ArrayList<>();
        for (HotfTree.Perk perk : HotfTree.all()) {
            int at = state.perk(perk.id());
            int stateCode = at <= 0 ? (Hotf.unlockable(state, perk) ? 1 : 0) : at >= perk.max() ? 3 : 2;
            List<String> lore = new ArrayList<>(perk.loreAt(at <= 0 ? 0 : at));
            String cost;
            if (at <= 0) {
                cost = perk.tier() > level ? "§cRequires Heart of the Forest " + perk.tier() : "§7Cost: §21 Token of the Forest";
            } else if (at < perk.max() && !perk.single()) {
                String type = perk.powderAt(at);
                cost = "§7Next level: §" + powderCode(type) + Msg.number(perk.costAt(at)) + " " + Hotf.whisperName(type);
                if (!perk.values().isEmpty()) {
                    // What the next level gives, as Hypixel shows it.
                    List<String> next = perk.loreAt(at + 1);
                    if (!next.equals(perk.loreAt(at))) {
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
        long into = state.xp() - HotfTree.totalFor(level);
        long next = level >= HotfTree.maxLevel() ? 0 : HotfTree.xpFor(level);
        return new View(level, HotfTree.maxLevel(), into, next, Hotf.tokens(state) - Hotf.tokensSpent(state), state.whispers(Hotf.FOREST),
                state.whispers(Hotf.DESERT), perks);
    }

    private static String powderCode(String type) {
        return Hotf.DESERT.equals(type) ? "6" : "3";
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
            Hotf.toggle(owner, perk);
        } else {
            String problem = Hotf.click(owner, perk, id >= TEN ? 10 : 1);
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
