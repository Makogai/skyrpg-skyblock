package com.skyrpg.minions;

import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyStacks;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A minion's screen: its storage (take items out; nothing goes in), and the buttons to collect everything, upgrade
 * it to the next tier, or pick it up. What the screen shows about the minion comes in {@link View} when it opens
 * (it reopens after an upgrade); the timer and the generated count are live data slots.
 */
public final class MinionMenu extends AbstractContainerMenu {
    public static final int COLLECT = 0;
    public static final int UPGRADE = 1;
    public static final int PICK_UP = 2;
    // Layout, shared with the screen.
    public static final int PAD = 8;
    public static final int TOP = 26;
    public static final int LEFT_W = 104;
    public static final int LEFT_H = 104;
    public static final int GRID_X = PAD + LEFT_W + 8;
    public static final int GRID_Y = TOP + 12;
    public static final int GRID_COLUMNS = 5;
    public static final int BUTTONS_X = GRID_X + GRID_COLUMNS * 18 + 8;
    public static final int BUTTON_W = 68;
    public static final int WIDTH = BUTTONS_X + BUTTON_W + PAD;
    public static final int INV_X = (WIDTH - 162) / 2 + 1;
    public static final int INV_Y = TOP + LEFT_H + 18;
    public static final int HEIGHT = INV_Y + 3 * 18 + 4 + 18 + PAD;

    /** One thing an upgrade costs: the item, how many it takes, and how many you have. */
    public record Cost(ItemStack item, int need, int have) {
        static final StreamCodec<RegistryFriendlyByteBuf, Cost> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Cost::item, ByteBufCodecs.VAR_INT, Cost::need, ByteBufCodecs.VAR_INT, Cost::have, Cost::new);
    }

    /** What the screen shows about the minion. */
    public record View(int entityId, String name, String category, int tier, int maxTier, float actionSeconds, int storage, int slots,
                       List<Cost> upgrade) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, View::entityId,
                ByteBufCodecs.STRING_UTF8, View::name,
                ByteBufCodecs.STRING_UTF8, View::category,
                ByteBufCodecs.VAR_INT, View::tier,
                ByteBufCodecs.VAR_INT, View::maxTier,
                ByteBufCodecs.FLOAT, View::actionSeconds,
                ByteBufCodecs.VAR_INT, View::storage,
                ByteBufCodecs.VAR_INT, View::slots,
                Cost.CODEC.apply(ByteBufCodecs.list()), View::upgrade,
                View::new);

        public boolean canUpgrade() {
            return tier < maxTier && !upgrade.isEmpty() && upgrade.stream().allMatch(c -> c.have() >= c.need());
        }
    }

    private final View view;
    private final ContainerData data;
    private final MinionEntity minion;
    private final ServerPlayer owner;

    /** The client's copy. */
    public MinionMenu(int id, Inventory inventory, View view) {
        this(id, inventory, view, new SimpleContainer(MinionEntity.MAX_SLOTS), new SimpleContainerData(4), null, null);
    }

    private MinionMenu(int id, Inventory inventory, View view, Container storage, ContainerData data, MinionEntity minion, ServerPlayer owner) {
        super(SkyRpgMinions.MENU, id);
        this.view = view;
        this.data = data;
        this.minion = minion;
        this.owner = owner;
        for (int i = 0; i < MinionEntity.MAX_SLOTS; i++) {
            int index = i;
            addSlot(new Slot(storage, i, GRID_X + 1 + (i % GRID_COLUMNS) * 18, GRID_Y + 1 + (i / GRID_COLUMNS) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean isActive() {
                    return index < view.slots();
                }
            });
        }
        addStandardInventorySlots(inventory, INV_X, INV_Y);
        addDataSlots(data);
    }

    public View view() {
        return view;
    }

    /** 0-1 of the way to the next action. */
    public float progress() {
        return data.get(0) / 1000f;
    }

    public boolean full() {
        return data.get(1) == 1;
    }

    public long generated() {
        return (long) data.get(2) << 15 | data.get(3);
    }

    static void open(ServerPlayer player, MinionEntity minion) {
        MinionType type = minion.type();
        if (type == null) {
            return;
        }
        View view = view(player, minion, type);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal(type.title(minion.tier()));
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return server(id, inventory, player, minion, view);
            }
        });
    }

    private static View view(ServerPlayer player, MinionEntity minion, MinionType type) {
        MinionType.Tier tier = type.tier(minion.tier());
        List<Cost> upgrade = new ArrayList<>();
        if (minion.tier() < type.maxTier()) {
            for (Map.Entry<String, Integer> cost : type.tier(minion.tier() + 1).cost().entrySet()) {
                ItemDefinition def = ItemRegistry.skyblock(cost.getKey());
                ItemStack icon = def == null ? ItemStack.EMPTY : SkyItems.create(def, 1);
                upgrade.add(new Cost(icon, cost.getValue(), SkyStacks.count(player.getInventory(), cost.getKey())));
            }
        }
        return new View(minion.getId(), type.name(), type.category(), minion.tier(), type.maxTier(), (float) tier.actionSeconds(),
                tier.storage(), Math.min(MinionEntity.MAX_SLOTS, tier.slots()), upgrade);
    }

    private static MinionMenu server(int id, Inventory inventory, ServerPlayer owner, MinionEntity minion, View view) {
        Container storage = new Container() {
            @Override
            public int getContainerSize() {
                return MinionEntity.MAX_SLOTS;
            }

            @Override
            public boolean isEmpty() {
                return minion.storedItems() == 0;
            }

            @Override
            public ItemStack getItem(int index) {
                return minion.storage().get(index);
            }

            @Override
            public ItemStack removeItem(int index, int count) {
                ItemStack taken = minion.storage().get(index).split(count);
                minion.storageChanged();
                return taken;
            }

            @Override
            public ItemStack removeItemNoUpdate(int index) {
                ItemStack taken = minion.storage().get(index);
                minion.storage().set(index, ItemStack.EMPTY);
                minion.storageChanged();
                return taken;
            }

            @Override
            public void setItem(int index, ItemStack stack) {
                minion.storage().set(index, stack);
                minion.storageChanged();
            }

            @Override
            public void setChanged() {
            }

            @Override
            public boolean stillValid(Player player) {
                return minion.isAlive() && player.distanceToSqr(minion) < 64;
            }

            @Override
            public void clearContent() {
            }
        };
        ContainerData data = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> Math.round(minion.progress() * 1000);
                    case 1 -> minion.isFull() ? 1 : 0;
                    case 2 -> (int) (minion.generated() >> 15 & 0x7FFF);
                    default -> (int) (minion.generated() & 0x7FFF);
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 4;
            }
        };
        return new MinionMenu(id, inventory, view, storage, data, minion, owner);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (owner == null || minion == null || !minion.isAlive()) {
            return false;
        }
        switch (id) {
            case COLLECT -> collect();
            case UPGRADE -> upgrade();
            case PICK_UP -> pickUp();
            default -> {
                return false;
            }
        }
        return true;
    }

    private void collect() {
        for (int i = 0; i < MinionEntity.MAX_SLOTS; i++) {
            ItemStack stack = minion.storage().get(i);
            if (!stack.isEmpty()) {
                owner.getInventory().add(stack);
                if (stack.isEmpty()) {
                    minion.storage().set(i, ItemStack.EMPTY);
                }
            }
        }
        minion.storageChanged();
        owner.level().playSound(null, owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    private void upgrade() {
        MinionType type = minion.type();
        if (type == null || minion.tier() >= type.maxTier()) {
            return;
        }
        Map<String, Integer> cost = type.tier(minion.tier() + 1).cost();
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            if (SkyStacks.count(owner.getInventory(), entry.getKey()) < entry.getValue()) {
                owner.sendSystemMessage(Component.literal("You don't have what the upgrade takes yet.").withStyle(ChatFormatting.RED));
                return;
            }
        }
        cost.forEach((item, count) -> SkyStacks.take(owner.getInventory(), item, count));
        minion.setTier(minion.tier() + 1);
        owner.level().playSound(null, minion.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f);
        owner.sendSystemMessage(Component.literal("Upgraded to " + type.title(minion.tier()) + "!").withStyle(ChatFormatting.GREEN));
        // Reopen so the screen shows the new tier.
        owner.level().getServer().execute(() -> open(owner, minion));
    }

    private void pickUp() {
        MinionType type = minion.type();
        for (ItemStack stack : minion.takeAll()) {
            if (!owner.getInventory().add(stack)) {
                owner.drop(stack, false, Prediction.SERVER_ONLY);
            }
        }
        if (type != null) {
            ItemDefinition def = ItemRegistry.skyblock(type.tier(minion.tier()).item());
            if (def != null) {
                ItemStack item = SkyItems.create(def, 1);
                if (!owner.getInventory().add(item)) {
                    owner.drop(item, false, Prediction.SERVER_ONLY);
                }
            }
        }
        minion.discard();
        owner.closeContainer();
        owner.level().playSound(null, owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8f, 0.9f);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= MinionEntity.MAX_SLOTS) {
            return ItemStack.EMPTY; // nothing goes into a minion by hand
        }
        Slot from = slots.get(index);
        if (!from.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = from.getItem();
        ItemStack before = stack.copy();
        if (!moveItemStackTo(stack, MinionEntity.MAX_SLOTS, slots.size(), true)) {
            return ItemStack.EMPTY;
        }
        from.setChanged();
        if (minion != null) {
            minion.storageChanged();
        }
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return minion == null || minion.isAlive() && player.distanceToSqr(minion) < 64;
    }
}
