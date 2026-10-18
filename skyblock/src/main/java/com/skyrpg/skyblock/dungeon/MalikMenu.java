package com.skyrpg.skyblock.dungeon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.essence.Essence;
import com.skyrpg.skyblock.storage.SkyMenus;
import com.skyrpg.skyblock.ui.Chip;
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
import net.minecraft.world.item.Items;

/** Malik's screen: the held dungeon item, its stars and the next star's cost; your essence. Button {@link #UPGRADE}. */
public final class MalikMenu extends AbstractContainerMenu {
    public static final int UPGRADE = 1;
    public static final int TAB = 2;
    public static final int PERK = 100;
    /** Who's on the essence shop tab. */
    private static final java.util.Set<java.util.UUID> SHOP = new java.util.HashSet<>();

    /** A perk on the essence shop: its name, level and maximum, what it does now, the next level's price (empty: maxed), icon and colour. */
    public record PerkRow(String name, int level, int max, String effect, List<Chip> price, ItemStack icon, int color) {
        static final StreamCodec<RegistryFriendlyByteBuf, PerkRow> CODEC = new StreamCodec<>() {
            @Override
            public PerkRow decode(RegistryFriendlyByteBuf buf) {
                return new PerkRow(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), Chip.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, PerkRow row) {
                buf.writeUtf(row.name());
                buf.writeVarInt(row.level());
                buf.writeVarInt(row.max());
                buf.writeUtf(row.effect());
                Chip.CODEC.apply(ByteBufCodecs.list()).encode(buf, row.price());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, row.icon());
                buf.writeInt(row.color());
            }
        };
    }

    /**
     * The held item, its stars and maximum, the next star's costs as chips, your essence as a line, the shop tab, the shop's
     * perks, who you're talking to, that NPC's entity id (-1: none) and your balances.
     */
    public record View(ItemStack item, int stars, int max, List<Chip> cost, String essence, boolean shop, List<PerkRow> perks, String who, int npc,
                       List<Chip> wallet) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
                        Chip.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readUtf(), buf.readBoolean(),
                        PerkRow.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readUtf(), buf.readVarInt(),
                        Chip.CODEC.apply(ByteBufCodecs.list()).decode(buf));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, view.item());
                buf.writeVarInt(view.stars());
                buf.writeVarInt(view.max());
                Chip.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.cost());
                buf.writeUtf(view.essence());
                buf.writeBoolean(view.shop());
                PerkRow.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.perks());
                buf.writeUtf(view.who());
                buf.writeVarInt(view.npc());
                Chip.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.wallet());
            }
        };
    }

    private final View view;
    private final ServerPlayer owner;

    public MalikMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private MalikMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.MALIK, id);
        this.view = view;
        this.owner = owner;
    }

    public View view() {
        return view;
    }

    static void open(ServerPlayer player) {
        open(player, "Malik");
    }

    /** An essence's icon: a vanilla item that suits it. */
    private static ItemStack essenceIcon(String kind) {
        return new ItemStack(switch (kind) {
            case "WITHER" -> Items.WITHER_SKELETON_SKULL;
            case "UNDEAD" -> Items.ROTTEN_FLESH;
            case "DRAGON" -> Items.DRAGON_BREATH;
            case "SPIDER" -> Items.SPIDER_EYE;
            case "GOLD" -> Items.GOLD_INGOT;
            case "DIAMOND" -> Items.DIAMOND;
            case "ICE" -> Items.BLUE_ICE;
            case "CRIMSON" -> Items.CRIMSON_FUNGUS;
            default -> Items.BONE;
        });
    }

    private static ItemStack itemIcon(String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? new ItemStack(Items.BARRIER) : SkyItems.create(def, 1);
    }

    /** The next star's costs as chips (red when you can't pay them). */
    private static List<Chip> costChips(ServerPlayer player, ItemStack stack) {
        List<Chip> out = new ArrayList<>();
        ItemDefinition def = SkyItems.definition(stack);
        int stars = ItemMods.stars(stack);
        if (stars >= 5 && stars < 10 && DungeonItems.maxStars(def) == 10 && !DungeonItems.crimson(def)) {
            String star = DungeonItems.MASTER_STARS[stars - 5];
            out.add(Chip.item(itemIcon(star), 1, SkyStacks.count(player.getInventory(), star)));
            return out;
        }
        if (stars < DungeonItems.maxStars(def)) {
            for (JsonElement element : DungeonItems.costs(def).get(stars).getAsJsonArray()) {
                JsonObject cost = element.getAsJsonObject();
                int amount = cost.has("amount") ? cost.get("amount").getAsInt() : 1;
                switch (cost.get("type").getAsString()) {
                    case "ESSENCE" -> {
                        String kind = cost.get("essence_type").getAsString();
                        long have = Essence.get(player, kind);
                        out.add(Chip.note(essenceIcon(kind), Msg.number(amount) + " " + kind.charAt(0) + kind.substring(1).toLowerCase(java.util.Locale.ROOT),
                                have >= amount));
                    }
                    case "COINS" -> out.add(Chip.price("coins", amount, Wallet.balance(player)));
                    default -> {
                        String id = cost.has("item_id") ? cost.get("item_id").getAsString() : "";
                        out.add(Chip.item(itemIcon(id), amount, SkyStacks.count(player.getInventory(), id)));
                    }
                }
            }
        }
        return out;
    }

    private static List<PerkRow> perkRows(ServerPlayer player) {
        List<PerkRow> rows = new ArrayList<>();
        for (EssencePerks.Perk perk : EssencePerks.PERKS) {
            int level = EssencePerks.level(player, perk.id());
            int max = perk.costs().length;
            String effect = perk.stat().isEmpty() ? "+" + (int) (perk.perLevel() * level) + "% blessings"
                    : "+" + (int) (perk.perLevel() * level) + (perk.percent() ? "% " : " ") + perk.stat().replace("_", " ");
            List<Chip> price = new ArrayList<>();
            if (level < max) {
                long cost = perk.costs()[level];
                String kind = perk.essence();
                price.add(Chip.note(essenceIcon(kind), Msg.number(cost) + " " + kind.charAt(0) + kind.substring(1).toLowerCase(java.util.Locale.ROOT),
                        Essence.get(player, kind) >= cost));
            }
            rows.add(new PerkRow(perk.name(), level, max, effect, price, essenceIcon(perk.essence()), Essence.color(perk.essence())));
        }
        return rows;
    }

    /** Malik's screen, or Vulcan's (the Crimson Isle's: Kuudra armour's Crimson stars, no essence shop). */
    public static void open(ServerPlayer player, String who) {
        boolean vulcan = who.equals("Vulcan");
        ItemStack held = player.getMainHandItem();
        ItemDefinition def = SkyItems.definition(held);
        int max = DungeonItems.crimson(def) == vulcan ? DungeonItems.maxStars(def) : 0;
        List<Chip> wallet = new ArrayList<>();
        wallet.add(Chip.balance("coins", Wallet.balance(player)));
        for (String kind : Essence.KINDS) {
            long have = Essence.get(player, kind);
            if (have > 0 && (!vulcan || kind.equals("CRIMSON"))) {
                wallet.add(Chip.note(essenceIcon(kind), Msg.number(have) + " " + kind.charAt(0) + kind.substring(1).toLowerCase(java.util.Locale.ROOT), true));
            }
        }
        View view = new View(max > 0 ? held.copy() : ItemStack.EMPTY, ItemMods.stars(held), max, max > 0 ? costChips(player, held) : List.of(),
                DungeonItems.essenceLine(player), !vulcan && SHOP.contains(player.getUUID()),
                vulcan ? List.of() : perkRows(player), who, FactionMenu.npcNear(player, who), wallet);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal(who);
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new MalikMenu(id, view, player);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        if (id == TAB) {
            if (!SHOP.remove(owner.getUUID())) {
                SHOP.add(owner.getUUID());
            }
            owner.level().getServer().execute(() -> open(owner, view.who()));
            return true;
        }
        boolean vulcan = view.who().equals("Vulcan");
        String problem = id >= PERK && !vulcan ? EssencePerks.buy(owner, EssencePerks.PERKS.get(Math.min(id - PERK, EssencePerks.PERKS.size() - 1)).id())
                : id == UPGRADE ? DungeonItems.upgrade(owner, vulcan) : "";
        if (!problem.isEmpty()) {
            owner.sendSystemMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
        }
        owner.level().getServer().execute(() -> open(owner, view.who()));
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
