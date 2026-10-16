package com.skyrpg.minions;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyStacks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * A minion's screen: its storage (take items out; nothing goes in), its fuel, shipping and two upgrade slots, and the
 * buttons to collect everything (and the coins its hopper made), upgrade it to the next tier, or pick it up. What the
 * screen shows about the minion comes in {@link View} when it opens (it reopens after an upgrade); the timer, the
 * generated count, the fuel left, the held coins and the speed are live data slots.
 */
public final class MinionMenu extends AbstractContainerMenu {
    public static final int COLLECT = 0;
    public static final int UPGRADE = 1;
    public static final int PICK_UP = 2;
    // Layout, shared with the screen.
    public static final int PAD = 8;
    public static final int TOP = 26;
    public static final int LEFT_W = 104;
    public static final int LEFT_H = 108;
    public static final int GRID_X = PAD + LEFT_W + 8;
    public static final int GRID_Y = TOP + 12;
    public static final int GRID_COLUMNS = 5;
    public static final int BUTTONS_X = GRID_X + GRID_COLUMNS * 18 + 8;
    public static final int BUTTON_W = 68;
    public static final int WIDTH = BUTTONS_X + BUTTON_W + PAD;
    public static final int INV_X = (WIDTH - 162) / 2 + 1;
    public static final int INV_Y = TOP + LEFT_H + 18;
    public static final int HEIGHT = INV_Y + 3 * 18 + 4 + 18 + PAD;
    /** The fuel, shipping and upgrade slots, in a row under the storage. */
    public static final int FUEL = MinionEntity.MAX_SLOTS;
    public static final int SHIPPING = FUEL + 1;
    public static final int UPGRADE_1 = FUEL + 2;
    public static final int PARTS_END = FUEL + 2 + MinionEntity.UPGRADE_SLOTS;
    public static final int PARTS_Y = GRID_Y + 3 * 18 + 22;
    private static final int[] PART_X = {0, 20, 52, 72};
    private static final int DATA = 10;

    /** Where a part slot's well is, from the window's corner (0 fuel, 1 shipping, 2 and 3 upgrades). */
    public static int partX(int part) {
        return GRID_X + PART_X[part];
    }

    /** One thing an upgrade costs: the item, how many it takes, and how many you have. */
    public record Cost(ItemStack item, int need, int have) {
        static final StreamCodec<RegistryFriendlyByteBuf, Cost> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Cost::item, ByteBufCodecs.VAR_INT, Cost::need, ByteBufCodecs.VAR_INT, Cost::have, Cost::new);
    }

    /**
     * What the screen shows about the minion, and what the client needs to know about fuel (the fuel data lives on the
     * server): which items are fuel, and which of those last forever (only those can be taken back out).
     */
    public record View(int entityId, String typeId, String name, String category, int tier, int maxTier, float actionSeconds, int storage,
                       int slots, List<Cost> upgrade, List<String> fuels, List<String> foreverFuels) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, View::entityId,
                ByteBufCodecs.STRING_UTF8, View::typeId,
                ByteBufCodecs.STRING_UTF8, View::name,
                ByteBufCodecs.STRING_UTF8, View::category,
                ByteBufCodecs.VAR_INT, View::tier,
                ByteBufCodecs.VAR_INT, View::maxTier,
                ByteBufCodecs.FLOAT, View::actionSeconds,
                ByteBufCodecs.VAR_INT, View::storage,
                ByteBufCodecs.VAR_INT, View::slots,
                Cost.CODEC.apply(ByteBufCodecs.list()), View::upgrade,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), View::fuels,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), View::foreverFuels,
                View::new);

        public boolean canUpgrade() {
            return tier < maxTier && !upgrade.isEmpty() && upgrade.stream().allMatch(c -> c.have() >= c.need());
        }

        public boolean isFuel(ItemStack stack) {
            return fuels.contains(SkyStacks.id(stack));
        }

        public boolean isUpgrade(ItemStack stack) {
            MinionUpgrade upgrade = MinionUpgrade.of(SkyItems.skyblockId(stack));
            return upgrade != null && upgrade.fits(typeId, category);
        }

        public static boolean isShipping(ItemStack stack) {
            return MinionUpgrade.sellPercent(SkyItems.skyblockId(stack)) > 0;
        }
    }

    private final View view;
    private final ContainerData data;
    private final MinionEntity minion;
    private final ServerPlayer owner;

    /** The client's copy. */
    public MinionMenu(int id, Inventory inventory, View view) {
        this(id, inventory, view, new SimpleContainer(MinionEntity.MAX_SLOTS), new SimpleContainer(PARTS_END - FUEL), new SimpleContainerData(DATA), null, null);
    }

    private MinionMenu(int id, Inventory inventory, View view, Container storage, Container parts, ContainerData data, MinionEntity minion,
                       ServerPlayer owner) {
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
                public void onTake(Player player, ItemStack stack) {
                    super.onTake(player, stack);
                    if (player instanceof ServerPlayer serverPlayer) {
                        ItemGains.GAINED.invoker().onGained(serverPlayer, stack.copy(), ItemGains.Source.OTHER);
                    }
                }

                @Override
                public boolean isActive() {
                    return index < view.slots();
                }
            });
        }
        // Fuel: one kind, stacked. What's burning can't come back out, unless it lasts forever (a Lava Bucket).
        addSlot(new Slot(parts, 0, partX(0) + 1, PARTS_Y + 1) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return view.isFuel(stack) && (getItem().isEmpty() || Objects.equals(SkyStacks.id(getItem()), SkyStacks.id(stack)));
            }

            @Override
            public boolean mayPickup(Player player) {
                return view.foreverFuels().contains(SkyStacks.id(getItem()));
            }
        });
        addSlot(new Slot(parts, 1, partX(1) + 1, PARTS_Y + 1) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return View.isShipping(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int i = 0; i < MinionEntity.UPGRADE_SLOTS; i++) {
            addSlot(new Slot(parts, 2 + i, partX(2 + i) + 1, PARTS_Y + 1) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return view.isUpgrade(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
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
        return halves(2);
    }

    /** Seconds of fuel left, -1 for fuel that lasts forever, 0 for none. */
    public long fuelSeconds() {
        long seconds = halves(4);
        return seconds == FOREVER ? -1 : seconds;
    }

    /** Coins the hopper has made, waiting to be collected. */
    public long heldCoins() {
        return halves(6);
    }

    /** How much faster than its tier's time it works now, in percent. */
    public int speedPercent() {
        return data.get(8);
    }

    /** What its output is multiplied by (a Catalyst's x3). */
    public float outputMultiplier() {
        return data.get(9) / 100f;
    }

    /** Seconds between actions now, with fuel and upgrades. */
    public float actionSeconds() {
        return Math.max(1f, view.actionSeconds() / (1 + speedPercent() / 100f));
    }

    private long halves(int index) {
        return (long) data.get(index) << 15 | data.get(index + 1);
    }

    private static final long FOREVER = (1L << 30) - 1;

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
                upgrade.add(new Cost(icon(cost.getKey()), cost.getValue(), have(player, cost.getKey())));
            }
        }
        List<String> fuels = new ArrayList<>();
        List<String> forever = new ArrayList<>();
        for (MinionFuels.Fuel fuel : MinionFuels.all()) {
            fuels.add(fuel.item());
            if (fuel.forever()) {
                forever.add(fuel.item());
            }
        }
        return new View(minion.getId(), type.id(), type.name(), type.category(), minion.tier(), type.maxTier(), (float) tier.actionSeconds(),
                tier.storage(), Math.min(MinionEntity.MAX_SLOTS, tier.slots()), upgrade, fuels, forever);
    }

    /** The coins an upgrade cost takes (tier XII's, which on Hypixel an NPC sells). */
    static final String COINS = "COINS";

    /** What a cost shows: the item, coins as a gold nugget, or a barrier named after something not in the game yet. */
    private static ItemStack icon(String id) {
        if (COINS.equals(id)) {
            ItemStack coins = new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET);
            coins.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.literal("Coins").withStyle(ChatFormatting.GOLD));
            return coins;
        }
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def != null) {
            return SkyItems.create(def, 1);
        }
        ItemStack missing = new ItemStack(net.minecraft.world.item.Items.BARRIER);
        missing.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.literal(id.replace('_', ' ') + " (not in the game yet)")
                .withStyle(ChatFormatting.RED));
        return missing;
    }

    private static int have(ServerPlayer player, String id) {
        return COINS.equals(id) ? (int) Math.min(Integer.MAX_VALUE, Wallet.balance(player)) : SkyStacks.count(player.getInventory(), id);
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
        Container parts = new Container() {
            @Override
            public int getContainerSize() {
                return PARTS_END - FUEL;
            }

            @Override
            public boolean isEmpty() {
                for (int i = 0; i < getContainerSize(); i++) {
                    if (!getItem(i).isEmpty()) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public ItemStack getItem(int index) {
                return switch (index) {
                    case 0 -> minion.fuel();
                    case 1 -> minion.shipping();
                    default -> minion.upgrades().get(index - 2);
                };
            }

            @Override
            public ItemStack removeItem(int index, int count) {
                ItemStack taken = getItem(index).split(count);
                if (getItem(index).isEmpty()) {
                    setItem(index, ItemStack.EMPTY);
                }
                return taken;
            }

            @Override
            public ItemStack removeItemNoUpdate(int index) {
                ItemStack taken = getItem(index);
                setItem(index, ItemStack.EMPTY);
                return taken;
            }

            @Override
            public void setItem(int index, ItemStack stack) {
                switch (index) {
                    case 0 -> minion.setFuel(stack);
                    case 1 -> minion.setShipping(stack);
                    default -> minion.upgrades().set(index - 2, stack);
                }
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
                    case 2, 3 -> half(minion.generated(), index - 2);
                    case 4, 5 -> {
                        long left = minion.fuelLeft();
                        yield half(left < 0 ? FOREVER : Math.min(FOREVER - 1, left / 1000), index - 4);
                    }
                    case 6, 7 -> half(Math.min(FOREVER, minion.heldCoins()), index - 6);
                    case 8 -> Math.min(0x7FFF, minion.speedPercent());
                    default -> (int) Math.round(minion.outputMultiplier() * 100);
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return DATA;
            }
        };
        return new MinionMenu(id, inventory, view, storage, parts, data, minion, owner);
    }

    /** The high (0) or low (1) 15 bits of a number, for a data slot. */
    private static int half(long value, int which) {
        return (int) (which == 0 ? value >> 15 & 0x7FFF : value & 0x7FFF);
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
                // Like on Hypixel, what you collect from a minion counts for your collections.
                ItemGains.give(owner, stack, ItemGains.Source.OTHER);
                if (stack.isEmpty()) {
                    minion.storage().set(i, ItemStack.EMPTY);
                }
            }
        }
        minion.storageChanged();
        giveCoins();
        owner.level().playSound(null, owner.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
    }

    /** The coins the hopper made, into the purse. */
    private void giveCoins() {
        long coins = minion.takeCoins();
        if (coins > 0) {
            Wallet.give(owner, coins);
            owner.sendSystemMessage(Component.literal("+" + Msg.number(coins) + " coins").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" from your minion's hopper").withStyle(ChatFormatting.GRAY)));
        }
    }

    private void upgrade() {
        MinionType type = minion.type();
        if (type == null || minion.tier() >= type.maxTier()) {
            return;
        }
        Map<String, Integer> cost = type.tier(minion.tier() + 1).cost();
        for (Map.Entry<String, Integer> entry : cost.entrySet()) {
            if (have(owner, entry.getKey()) < entry.getValue()) {
                owner.sendOverlayMessage(Component.literal("You don't have what the upgrade takes yet.").withStyle(ChatFormatting.RED));
                return;
            }
        }
        cost.forEach((item, count) -> {
            if (COINS.equals(item)) {
                Wallet.take(owner, count);
            } else {
                SkyStacks.take(owner.getInventory(), item, count);
            }
        });
        minion.setTier(minion.tier() + 1);
        // Upgrading counts as crafting that tier, as on Hypixel.
        MinionSlots.record(owner, type.tier(minion.tier()).item());
        owner.level().playSound(null, minion.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.4f);
        owner.sendSystemMessage(Component.literal("Upgraded to " + type.title(minion.tier()) + "!").withStyle(ChatFormatting.GREEN));
        // Reopen so the screen shows the new tier.
        owner.level().getServer().execute(() -> open(owner, minion));
    }

    private void pickUp() {
        MinionType type = minion.type();
        giveCoins();
        for (ItemStack stack : minion.takeAll()) {
            ItemGains.give(owner, stack, ItemGains.Source.OTHER);
            if (!stack.isEmpty()) {
                owner.drop(stack, false, Prediction.SERVER_ONLY);
            }
        }
        for (ItemStack stack : minion.takeParts()) {
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
        Slot from = slots.get(index);
        if (!from.hasItem() || !from.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = from.getItem();
        ItemStack before = stack.copy();
        boolean moved;
        if (index >= PARTS_END) {
            // From the inventory, only into the part it is: storage takes nothing by hand.
            if (view.isFuel(stack)) {
                moved = moveItemStackTo(stack, FUEL, FUEL + 1, false);
            } else if (View.isShipping(stack)) {
                moved = moveItemStackTo(stack, SHIPPING, SHIPPING + 1, false);
            } else if (view.isUpgrade(stack)) {
                moved = moveItemStackTo(stack, UPGRADE_1, PARTS_END, false);
            } else {
                moved = false;
            }
        } else {
            moved = moveItemStackTo(stack, PARTS_END, slots.size(), true);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (index == FUEL && stack.isEmpty() && minion != null) {
            minion.setFuel(ItemStack.EMPTY);
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
