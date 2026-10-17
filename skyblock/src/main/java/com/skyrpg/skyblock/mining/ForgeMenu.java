package com.skyrpg.skyblock.mining;

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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The Forge: your slots (what's forging and how long it has left, or ready to take) and every recipe with what it needs
 * and what you have. Button {@code i} starts recipe {@code i} in a free slot; {@link #CLAIM} {@code + n} takes slot
 * {@code n}'s finished item.
 */
public final class ForgeMenu extends AbstractContainerMenu {
    public static final int CLAIM = 5000;
    /** Hypixel's most slots. */
    public static final int MAX_SLOTS = 7;

    /** A slot: open (or not yet), what's in it and the millis it has left of how many. */
    public record SlotView(boolean open, ItemStack output, long left, long total) {
        static final StreamCodec<RegistryFriendlyByteBuf, SlotView> CODEC = new StreamCodec<>() {
            @Override
            public SlotView decode(RegistryFriendlyByteBuf buf) {
                return new SlotView(buf.readBoolean(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarLong());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, SlotView view) {
                buf.writeBoolean(view.open());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, view.output());
                buf.writeVarLong(view.left());
                buf.writeVarLong(view.total());
            }
        };
    }

    /** A recipe: the item, its materials (count = needed) and how many of each you have, coins, seconds, HotM tier. */
    public record RecipeView(ItemStack output, List<ItemStack> inputs, List<Integer> have, long coins, long seconds, int hotm) {
        static final StreamCodec<RegistryFriendlyByteBuf, RecipeView> CODEC = new StreamCodec<>() {
            @Override
            public RecipeView decode(RegistryFriendlyByteBuf buf) {
                return new RecipeView(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf),
                        ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).decode(buf), buf.readVarLong(), buf.readVarLong(), buf.readVarInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, RecipeView view) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, view.output());
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, view.inputs());
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(buf, view.have());
                buf.writeVarLong(view.coins());
                buf.writeVarLong(view.seconds());
                buf.writeVarInt(view.hotm());
            }
        };

        /** Whether you have every material. */
        public boolean ready() {
            for (int i = 0; i < inputs.size(); i++) {
                if (have.get(i) < inputs.get(i).getCount()) {
                    return false;
                }
            }
            return true;
        }
    }

    public record View(int hotm, List<SlotView> slots, List<RecipeView> recipes) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, View::hotm,
                SlotView.CODEC.apply(ByteBufCodecs.list()), View::slots,
                RecipeView.CODEC.apply(ByteBufCodecs.list()), View::recipes,
                View::new);
    }

    private final View view;
    private final ServerPlayer owner;

    public ForgeMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private ForgeMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.FORGE, id);
        this.view = view;
        this.owner = owner;
    }

    public View view() {
        return view;
    }

    public static void open(ServerPlayer player) {
        if (Hotm.level(player) < 2) {
            player.sendSystemMessage(Component.literal("You need Heart of the Mountain 2 to use the Forge!").withStyle(ChatFormatting.RED));
            return;
        }
        View view = view(player);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("The Forge");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new ForgeMenu(id, view, player);
            }
        });
    }

    private static View view(ServerPlayer player) {
        Map<String, Forge.Job> jobs = player.getAttachedOrElse(Forge.JOBS, Map.of());
        int open = Forge.slots(player);
        long now = System.currentTimeMillis();
        List<SlotView> slots = new ArrayList<>();
        for (int i = 0; i < MAX_SLOTS; i++) {
            Forge.Job job = jobs.get(String.valueOf(i));
            if (job == null) {
                slots.add(new SlotView(i < open, ItemStack.EMPTY, 0, 0));
            } else {
                slots.add(new SlotView(true, Forge.stack(job.output(), job.count()), Math.max(0, job.end() - now), job.end() - job.start()));
            }
        }
        List<RecipeView> recipes = new ArrayList<>();
        for (Forge.Recipe recipe : Forge.recipes()) {
            List<ItemStack> inputs = new ArrayList<>();
            List<Integer> have = new ArrayList<>();
            for (Forge.Input input : recipe.inputs()) {
                ItemStack stack = Forge.stack(input.item(), 1);
                stack.setCount(Math.min(input.count(), 99));
                inputs.add(stack);
                have.add(SkyStacks.count(player.getInventory(), input.item()));
            }
            recipes.add(new RecipeView(Forge.stack(recipe.output(), recipe.count()), inputs, have, recipe.coins(),
                    Forge.seconds(player, recipe), recipe.hotm()));
        }
        return new View(Hotm.level(player), slots, recipes);
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        String problem;
        if (id >= CLAIM) {
            problem = Forge.claim(owner, id - CLAIM);
        } else if (id >= 0 && id < Forge.recipes().size()) {
            problem = Forge.start(owner, Forge.recipes().get(id));
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
