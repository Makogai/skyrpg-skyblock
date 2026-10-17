package com.skyrpg.skyblock.fishing;

import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Odger's screen: every trophy fish and how many of each tier you've caught, your Trophy Fisher rank and its rewards
 * ({@link #CLAIM}), and filleting the trophy fish you carry into Magmafish ({@link #FILLET}).
 *
 * <p>Ranks: Novice (Bronze or better of 15 fish): the Bronze Hunter armour, Odger's Bronze Tooth and the Bronze Trophy
 * Fishing Sack; Adept (Silver of all 18): the Silver set, tooth and sack; Expert (Gold of all): the Gold ones; Master
 * (Diamond of all): the Diamond ones and Diamond the Fish.
 */
public final class OdgerMenu extends AbstractContainerMenu {
    public static final int FILLET = 1;
    public static final int CLAIM = 2;
    public static final List<String> RANKS = List.of("None", "Novice", "Adept", "Expert", "Master");
    private static final List<String> METALS = List.of("BRONZE", "SILVER", "GOLD", "DIAMOND");

    /** One fish row: its name, colour, how it's caught, and the count of each tier. */
    public record Row(String name, int color, String how, List<Integer> counts) {
        static final StreamCodec<RegistryFriendlyByteBuf, Row> CODEC = new StreamCodec<>() {
            @Override
            public Row decode(RegistryFriendlyByteBuf buf) {
                return new Row(buf.readUtf(), buf.readInt(), buf.readUtf(), ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).decode(buf));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Row row) {
                buf.writeUtf(row.name());
                buf.writeInt(row.color());
                buf.writeUtf(row.how());
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(buf, row.counts());
            }
        };
    }

    /** The rows, the rank claimed and earned, and how many Magmafish the carried trophy fish would fillet into. */
    public record View(List<Row> rows, int claimed, int earned, int fillet) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(Row.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                Row.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.rows());
                buf.writeVarInt(view.claimed());
                buf.writeVarInt(view.earned());
                buf.writeVarInt(view.fillet());
            }
        };
    }

    private final View view;
    private final ServerPlayer owner;

    public OdgerMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private OdgerMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.ODGER, id);
        this.view = view;
        this.owner = owner;
    }

    public View view() {
        return view;
    }

    static void open(ServerPlayer player) {
        Map<String, Integer> counts = player.getAttachedOrElse(TrophyFishing.CAUGHT, Map.of());
        List<Row> rows = new ArrayList<>();
        for (TrophyFishing.Fish f : TrophyFishing.fish()) {
            List<Integer> tiers = new ArrayList<>();
            for (String tier : TrophyFishing.TIERS) {
                tiers.add(counts.getOrDefault(f.id() + "_" + tier, 0));
            }
            rows.add(new Row(f.name(), f.color(), f.how(), tiers));
        }
        View view = new View(rows, counts.getOrDefault("rank", 0), TrophyFishing.earned(counts), fillet(player, false));
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Odger");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new OdgerMenu(id, view, player);
            }
        });
    }

    /** The Magmafish the carried trophy fish are worth; {@code take}: fillet them now. */
    private static int fillet(ServerPlayer player, boolean take) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ItemDefinition def = SkyItems.definition(stack);
            Object[] parsed = def == null ? null : TrophyFishing.parse(def.skyblockId());
            if (parsed == null) {
                continue;
            }
            total += ((TrophyFishing.Fish) parsed[0]).fillet()[(int) parsed[1]] * stack.getCount();
            if (take) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        return total;
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        if (id == FILLET) {
            int magmafish = fillet(owner, true);
            if (magmafish > 0) {
                give(owner, "MAGMA_FISH", magmafish);
                Notice.of(Notice.Kind.FISHING).tag("ODGER").send(owner, Component.literal("Filleted into ")
                        .append(Component.literal(Msg.number(magmafish) + " Magmafish").withStyle(ChatFormatting.GOLD)).append("."));
                Msg.playSound(owner, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.4f);
            }
        } else if (id == CLAIM) {
            Map<String, Integer> counts = new java.util.HashMap<>(owner.getAttachedOrElse(TrophyFishing.CAUGHT, Map.of()));
            int claimed = counts.getOrDefault("rank", 0);
            int earned = TrophyFishing.earned(counts);
            if (earned > claimed) {
                Notice.Panel panel = Notice.of(Notice.Kind.FISHING).tag("TROPHY FISHER").panel();
                for (int rank = claimed + 1; rank <= earned; rank++) {
                    String metal = METALS.get(rank - 1);
                    for (String piece : List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) {
                        give(owner, metal + "_HUNTER_" + piece, 1);
                    }
                    give(owner, "ODGERS_" + metal + "_TOOTH", 1);
                    give(owner, metal + "_TROPHY_FISHING_SACK", 1);
                    String extra = "";
                    if (rank == 4) {
                        give(owner, "DIAMOND_THE_FISH", 1);
                        extra = ", Diamond the Fish";
                    }
                    String title = metal.charAt(0) + metal.substring(1).toLowerCase(java.util.Locale.ROOT);
                    panel.line(Component.literal(" " + RANKS.get(rank) + ": " + title + " Hunter armour, Odger's " + title + " Tooth, the "
                            + title + " Trophy Fishing Sack" + extra).withStyle(ChatFormatting.GRAY));
                }
                counts.put("rank", earned);
                owner.setAttached(TrophyFishing.CAUGHT, Map.copyOf(counts));
                panel.send(owner, Component.literal("You're now a " + RANKS.get(earned) + " Trophy Fisher!").withStyle(ChatFormatting.GOLD));
                Msg.playSound(owner, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
            }
        } else {
            return false;
        }
        owner.level().getServer().execute(() -> open(owner));
        return true;
    }

    private static void give(ServerPlayer player, String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        int left = count;
        while (left > 0) {
            int n = Math.min(left, 64);
            left -= n;
            ItemStack stack = SkyItems.create(def, n);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
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
