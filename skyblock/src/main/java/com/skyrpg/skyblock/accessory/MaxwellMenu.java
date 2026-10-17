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
 * Maxwell's "Accessory Bag Thaumaturgy": every Accessory Power with what it gives at your Magical Power. Click one you
 * have to make it your power; a stone power is learned by handing Maxwell 9 of its Power Stone. Button {@code i} selects
 * power {@code i}, {@code 1000 + i} learns it.
 */
public final class MaxwellMenu extends AbstractContainerMenu {
    public static final int LEARN = 1000;
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

    public record View(int magicalPower, double multiplier, List<PowerView> powers) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, View::magicalPower,
                ByteBufCodecs.DOUBLE, View::multiplier,
                PowerView.CODEC.apply(ByteBufCodecs.list()), View::powers,
                View::new);
    }

    private final View view;
    private final ServerPlayer owner;

    public MaxwellMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private MaxwellMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.MAXWELL, id);
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
                return Component.literal("Accessory Bag Thaumaturgy");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new MaxwellMenu(id, view, player);
            }
        });
    }

    private static View view(ServerPlayer player) {
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
        return new View(mp, AccessoryPowers.multiplier(mp), out);
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
