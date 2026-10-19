package com.skyrpg.skyblock.accessory;

import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyStacks;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Maxwell's "Accessory Bag Thaumaturgy", in three tabs: the Accessory Powers (every one with what it gives at your
 * Magical Power; click one you have to make it your power, a stone power is learned by handing Maxwell 9 of its Power
 * Stone), Stats Tuning (your Tuning Points on eight stats) and the bag's extra slots (the Redstone Miner, Jacobus, the
 * Rift Prism). Button {@code i} selects power {@code i}, {@code 1000 + i} learns it; the others are the constants below.
 * Jacobus and the Redstone Miner open the third tab.
 */
public final class MaxwellMenu extends AbstractContainerMenu {
    public static final int LEARN = 1000;
    public static final int POWERS = 0;
    public static final int TUNING = 1;
    public static final int BAGS = 2;
    /** Button 1900 + tab says which tab the player is on (so a click reopens the same one). */
    public static final int TAB = 1900;
    /** 2000 + stat: a Tuning Point on it; 2100 + stat: ten; 2200 and 2300: take one and ten off. */
    public static final int ADD = 2000;
    public static final int ADD_TEN = 2100;
    public static final int REMOVE = 2200;
    public static final int REMOVE_TEN = 2300;
    public static final int RESET = 2400;
    public static final int SAVE_TEMPLATE = 2401;
    public static final int LOAD_TEMPLATE = 2402;
    public static final int MINER = 3000;
    public static final int BUY = 3001;
    public static final int IMBUE = 3002;
    /** The Redstone collection's most slots (Redstone IX). */
    public static final int REDSTONE_MAX = 48;
    /** Power Stones it takes to learn a power. */
    public static final int STONES = 9;

    /** 0 = yours to pick, 1 = your power, 2 = learn it with stones, 3 = needs a higher Combat level. */
    public record PowerView(String id, String name, int state, String stats, String unique, ItemStack stone, int stonesHave, int combat) {
        static final StreamCodec<RegistryFriendlyByteBuf, PowerView> CODEC = new StreamCodec<>() {
            @Override
            public PowerView decode(RegistryFriendlyByteBuf buf) {
                return new PowerView(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readUtf(),
                        ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, PowerView view) {
                buf.writeUtf(view.id());
                buf.writeUtf(view.name());
                buf.writeVarInt(view.state());
                buf.writeUtf(view.stats());
                buf.writeUtf(view.unique());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, view.stone());
                buf.writeVarInt(view.stonesHave());
                buf.writeVarInt(view.combat());
            }
        };
    }

    /** One tunable stat: its id (a {@link AccessoryTuning.Stat}), points on it, what a point gives, what you have of it. */
    public record TuneView(String id, int points, double perPoint, double have) {
        static final StreamCodec<RegistryFriendlyByteBuf, TuneView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, TuneView::id,
                ByteBufCodecs.VAR_INT, TuneView::points,
                ByteBufCodecs.DOUBLE, TuneView::perPoint,
                ByteBufCodecs.DOUBLE, TuneView::have,
                TuneView::new);
    }

    /** The bag's slots: where they come from, what's next and what it takes. */
    public record BagView(int redstone, int slots, int miner, int minerNeeds, int minerHave, int jacobus, long jacobusCost, long purse,
                          boolean imbued, boolean prism, int contacts, boolean abicase) {
        static final StreamCodec<RegistryFriendlyByteBuf, BagView> CODEC = new StreamCodec<>() {
            @Override
            public BagView decode(RegistryFriendlyByteBuf buf) {
                return new BagView(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarLong(), buf.readVarLong(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, BagView view) {
                buf.writeVarInt(view.redstone());
                buf.writeVarInt(view.slots());
                buf.writeVarInt(view.miner());
                buf.writeVarInt(view.minerNeeds());
                buf.writeVarInt(view.minerHave());
                buf.writeVarInt(view.jacobus());
                buf.writeVarLong(view.jacobusCost());
                buf.writeVarLong(view.purse());
                buf.writeBoolean(view.imbued());
                buf.writeBoolean(view.prism());
                buf.writeVarInt(view.contacts());
                buf.writeBoolean(view.abicase());
            }
        };
    }

    public record View(int magicalPower, double multiplier, List<PowerView> powers, int tuningPoints, List<TuneView> tuning,
                       boolean template, BagView bags, int tab) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(buf.readVarInt(), buf.readDouble(), PowerView.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readVarInt(),
                        TuneView.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readBoolean(), BagView.CODEC.decode(buf), buf.readVarInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                buf.writeVarInt(view.magicalPower());
                buf.writeDouble(view.multiplier());
                PowerView.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.powers());
                buf.writeVarInt(view.tuningPoints());
                TuneView.CODEC.apply(ByteBufCodecs.list()).encode(buf, view.tuning());
                buf.writeBoolean(view.template());
                BagView.CODEC.encode(buf, view.bags());
                buf.writeVarInt(view.tab());
            }
        };

        /** Tuning Points not on a stat yet. */
        public int unassigned() {
            return tuningPoints - tuning.stream().mapToInt(TuneView::points).sum();
        }
    }

    private final View view;
    private final ServerPlayer owner;
    /** The tab the player is on, as the client last said. */
    private int tab;

    public MaxwellMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private MaxwellMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.MAXWELL, id);
        this.view = view;
        this.owner = owner;
        this.tab = view.tab();
    }

    public View view() {
        return view;
    }

    public static void open(ServerPlayer player) {
        open(player, POWERS);
    }

    public static void open(ServerPlayer player, int tab) {
        View view = view(player, tab);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Accessory Bag Thaumaturgy");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new MaxwellMenu(id, view, player);
            }
        });
    }

    private static View view(ServerPlayer player, int tab) {
        int mp = AccessoryBag.magicalPower(player);
        String selected = player.getAttachedOrElse(AccessoryBag.POWER, "");
        List<String> learned = player.getAttachedOrElse(AccessoryBag.LEARNED, List.of());
        int combat = SkillLevels.level(player, SkillLevels.COMBAT);
        List<PowerView> out = new ArrayList<>();
        for (AccessoryPowers.Power power : AccessoryPowers.all()) {
            boolean known = power.starter() || power.stone() == null || learned.contains(power.id());
            int state = combat < power.combat() ? 3 : !known ? 2 : power.id().equals(selected) ? 1 : 0;
            ItemStack stone = power.stone() == null || ItemRegistry.skyblock(power.stone()) == null ? ItemStack.EMPTY
                    : com.skyrpg.items.SkyItems.create(ItemRegistry.skyblock(power.stone()), 1);
            int have = power.stone() == null ? 0 : SkyStacks.count(player.getInventory(), power.stone());
            out.add(new PowerView(power.id(), power.name(), state, statLine(AccessoryBag.powerStats(power, mp), power.unique()),
                    statLine(power.unique(), Map.of()), stone, have, power.combat()));
        }
        // Yours first, then the ones you can pick, then the rest.
        out.sort((a, b) -> Integer.compare(order(a.state()), order(b.state())));
        Map<String, Integer> points = AccessoryTuning.points(player, mp);
        List<TuneView> tuning = new ArrayList<>();
        for (AccessoryTuning.Stat stat : AccessoryTuning.STATS) {
            tuning.add(new TuneView(stat.id(), points.getOrDefault(stat.id(), 0), stat.perPoint(),
                    com.skyrpg.core.stats.Stats.get(player, stat.type())));
        }
        int redstone = AccessoryBag.redstoneSlots(player);
        boolean abicase = AccessoryBag.countedIds(player).stream().anyMatch(id -> id != null && id.startsWith("ABICASE"));
        BagView bags = new BagView(redstone, AccessoryBag.slots(player), BagUpgrades.miner(player), BagUpgrades.minerNeeds(player),
                SkyStacks.count(player.getInventory(), "REDSTONE"), BagUpgrades.jacobus(player), BagUpgrades.jacobusCost(BagUpgrades.jacobus(player)),
                com.skyrpg.core.economy.Wallet.balance(player), BagUpgrades.imbued(player),
                SkyStacks.count(player.getInventory(), "RIFT_PRISM") > 0, AccessoryBag.contacts(player), abicase);
        return new View(mp, AccessoryPowers.multiplier(mp), out, AccessoryTuning.total(mp), tuning, AccessoryTuning.hasTemplate(player), bags, tab);
    }

    private static int order(int state) {
        return switch (state) {
            case 1 -> 0;
            case 0 -> 1;
            case 2 -> 2;
            default -> 3;
        };
    }

    /** "+12 Strength, +8 Crit Damage" (leaving out what's in {@code minus}: the unique bonus is shown on its own). */
    private static String statLine(Map<StatType, Double> stats, Map<StatType, Double> minus) {
        StringBuilder out = new StringBuilder();
        stats.forEach((stat, value) -> {
            double shown = value - minus.getOrDefault(stat, 0.0);
            if (Math.abs(shown) >= 0.5) {
                if (!out.isEmpty()) {
                    out.append(", ");
                }
                out.append(shown > 0 ? "+" : "").append(Msg.number(Math.round(shown))).append(" ").append(stat.name());
            }
        });
        return out.toString();
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        if (id >= TAB) {
            return tabButton(id);
        }
        boolean learn = id >= LEARN;
        int index = learn ? id - LEARN : id;
        if (index < 0 || index >= view.powers().size()) {
            return false;
        }
        AccessoryPowers.Power power = AccessoryPowers.get(view.powers().get(index).id());
        if (power == null) {
            return false;
        }
        if (learn) {
            if (power.stone() == null || owner.getAttachedOrElse(AccessoryBag.LEARNED, List.of()).contains(power.id())) {
                return false;
            }
            if (!SkyStacks.take(owner.getInventory(), power.stone(), STONES)) {
                owner.sendOverlayMessage(Component.literal("Maxwell needs " + STONES + " of that Power Stone.").withStyle(ChatFormatting.RED));
                return true;
            }
            List<String> learned = new ArrayList<>(owner.getAttachedOrElse(AccessoryBag.LEARNED, List.of()));
            learned.add(power.id());
            owner.setAttached(AccessoryBag.LEARNED, List.copyOf(learned));
            com.skyrpg.core.progress.SkyBlockXp.give(owner, 15, "Unlocking Powers");
            owner.sendSystemMessage(Component.literal("You learned the ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(power.name()).withStyle(ChatFormatting.GOLD))
                    .append(Component.literal(" power!").withStyle(ChatFormatting.GREEN)));
        } else {
            if (!AccessoryBag.available(owner, power)) {
                return false;
            }
            owner.setAttached(AccessoryBag.POWER, power.id());
            owner.sendOverlayMessage(Component.literal("Accessory Power: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(power.name()).withStyle(ChatFormatting.GOLD)));
        }
        com.skyrpg.core.stats.Stats.refresh(owner);
        owner.level().playSound(null, owner.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
        owner.level().getServer().execute(() -> open(owner, tab));
        return true;
    }

    /** The tab, tuning, slot and imbue buttons. */
    private boolean tabButton(int id) {
        boolean again = true;
        if (id < ADD) {
            if (id - TAB < 0 || id - TAB > BAGS) {
                return false;
            }
            tab = id - TAB;
            return true;
        } else if (id < ADD + 100 || id < ADD_TEN + 100 || id < REMOVE + 100 || id < REMOVE_TEN + 100) {
            int base = id - id % 100;
            int index = id % 100;
            if (index >= AccessoryTuning.STATS.size() || base < ADD || base > REMOVE_TEN) {
                return false;
            }
            String stat = AccessoryTuning.STATS.get(index).id();
            int moved = switch (base) {
                case ADD -> AccessoryTuning.add(owner, stat, 1);
                case ADD_TEN -> AccessoryTuning.add(owner, stat, 10);
                case REMOVE -> AccessoryTuning.remove(owner, stat, 1);
                default -> AccessoryTuning.remove(owner, stat, 10);
            };
            if (moved == 0) {
                owner.sendOverlayMessage(Component.literal(base < REMOVE ? "No Tuning Points left." : "No points on that stat.").withStyle(ChatFormatting.RED));
                return true;
            }
        } else if (id == RESET) {
            AccessoryTuning.reset(owner);
        } else if (id == SAVE_TEMPLATE) {
            AccessoryTuning.saveTemplate(owner);
            owner.sendOverlayMessage(Component.literal("Tuning Template saved.").withStyle(ChatFormatting.GREEN));
        } else if (id == LOAD_TEMPLATE) {
            if (!AccessoryTuning.loadTemplate(owner)) {
                owner.sendOverlayMessage(Component.literal("No Tuning Template saved yet.").withStyle(ChatFormatting.RED));
            }
        } else if (id == MINER) {
            again = BagUpgrades.giveRedstone(owner) || again;
        } else if (id == BUY) {
            BagUpgrades.buySlots(owner);
        } else if (id == IMBUE) {
            BagUpgrades.imbue(owner);
        } else {
            return false;
        }
        com.skyrpg.core.stats.Stats.refresh(owner);
        owner.level().playSound(null, owner.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.3f);
        if (again) {
            owner.level().getServer().execute(() -> open(owner, tab));
        }
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
