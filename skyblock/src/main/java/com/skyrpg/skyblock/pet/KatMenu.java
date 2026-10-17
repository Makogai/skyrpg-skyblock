package com.skyrpg.skyblock.pet;

import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.pets.Pets;
import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * Kat: the pet she's looking after (with its time left, or ready to take back), or your pets with what each one's next
 * rarity costs. Button {@code i} gives her pet {@code i}; {@link #CLAIM} takes yours back.
 */
public final class KatMenu extends AbstractContainerMenu {
    public static final int CLAIM = 999;

    /** A pet you could give Kat: it, its next rarity ("" = none), coins, seconds, items (count = needed) and how many you have. */
    public record PetView(ItemStack pet, String next, long coins, long seconds, List<ItemStack> items, List<Integer> have) {
        static final StreamCodec<RegistryFriendlyByteBuf, PetView> CODEC = new StreamCodec<>() {
            @Override
            public PetView decode(RegistryFriendlyByteBuf buf) {
                return new PetView(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readUtf(), buf.readVarLong(), buf.readVarLong(),
                        ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).decode(buf));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, PetView view) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, view.pet());
                buf.writeUtf(view.next());
                buf.writeVarLong(view.coins());
                buf.writeVarLong(view.seconds());
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, view.items());
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(buf, view.have());
            }
        };

        public boolean ready() {
            for (int i = 0; i < items.size(); i++) {
                if (have.get(i) < items.get(i).getCount()) {
                    return false;
                }
            }
            return !next.isEmpty();
        }
    }

    /** {@code with}: the pet with Kat (empty if none), its new rarity and millis left. */
    public record View(ItemStack with, String tier, long left, List<PetView> pets) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, View::with,
                ByteBufCodecs.STRING_UTF8, View::tier,
                ByteBufCodecs.VAR_LONG, View::left,
                PetView.CODEC.apply(ByteBufCodecs.list()), View::pets,
                View::new);
    }

    private final View view;
    private final ServerPlayer owner;
    /** The pets behind the view's, in order, and whether each is the one out. */
    private final List<ItemStack> pets;
    private final int activeIndex;

    public KatMenu(int id, Inventory inventory, View view) {
        this(id, view, null, List.of(), -1);
    }

    private KatMenu(int id, View view, ServerPlayer owner, List<ItemStack> pets, int activeIndex) {
        super(SkyMenus.KAT, id);
        this.view = view;
        this.owner = owner;
        this.pets = pets;
        this.activeIndex = activeIndex;
    }

    public View view() {
        return view;
    }

    public static void open(ServerPlayer player) {
        Optional<Kat.Job> job = Kat.job(player);
        List<ItemStack> pets = new ArrayList<>();
        int active = -1;
        ItemStack out = Pets.active(player);
        if (SkyBlockPets.isPet(out)) {
            pets.add(out);
            active = 0;
        }
        pets.addAll(SkyBlockPets.stored(player));
        List<PetView> views = new ArrayList<>();
        for (ItemStack pet : pets) {
            Optional<Kat.Upgrade> upgrade = Kat.upgrade(pet);
            List<ItemStack> items = new ArrayList<>();
            List<Integer> have = new ArrayList<>();
            upgrade.ifPresent(u -> u.items().forEach(cost -> {
                String[] parts = cost.split(":");
                ItemDefinition def = ItemRegistry.skyblock(parts[0]);
                ItemStack stack = def == null ? ItemStack.EMPTY : SkyItems.create(def, 1);
                stack.setCount(Math.min(99, Integer.parseInt(parts[1])));
                items.add(stack);
                have.add(SkyStacks.count(player.getInventory(), parts[0]));
            }));
            views.add(new PetView(pet.copy(), upgrade.map(u -> SkyBlockPets.TIERS[u.to()]).orElse(""), upgrade.map(Kat.Upgrade::coins).orElse(0L),
                    upgrade.map(Kat.Upgrade::seconds).orElse(0L), items, have));
        }
        View view = new View(job.map(Kat.Job::pet).orElse(ItemStack.EMPTY), job.map(Kat.Job::tier).orElse(""),
                job.map(j -> Math.max(0, j.ready() - System.currentTimeMillis())).orElse(0L), views);
        int activeIndex = active;
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Kat");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new KatMenu(id, view, player, pets, activeIndex);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        String problem;
        if (id == CLAIM) {
            problem = Kat.claim(owner);
        } else if (id >= 0 && id < pets.size()) {
            problem = Kat.start(owner, pets.get(id), id == activeIndex);
        } else {
            return false;
        }
        if (!problem.isEmpty()) {
            owner.sendOverlayMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
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
