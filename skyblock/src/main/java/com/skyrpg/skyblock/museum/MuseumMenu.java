package com.skyrpg.skyblock.museum;

import com.skyrpg.core.action.Actions;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
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
 * The Museum: category tabs and the chosen category's donations (donated, ready to donate from your inventory, or not
 * yet found). Button {@code i} (under {@link #ENTRY}) picks category {@code i}; {@link #ENTRY} {@code + n} donates entry
 * {@code n}.
 */
public final class MuseumMenu extends AbstractContainerMenu {
    public static final int ENTRY = 100;
    public static final List<String> NAMES = List.of("Combat", "Farming", "Mining", "Fishing", "Foraging", "Dungeoneering", "Hunting", "Special");

    /** 0 = not found, 1 = in your inventory, 2 = donated. */
    public record EntryView(String name, ItemStack icon, int xp, String stage, int state, int pieces) {
        static final StreamCodec<RegistryFriendlyByteBuf, EntryView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, EntryView::name,
                ItemStack.OPTIONAL_STREAM_CODEC, EntryView::icon,
                ByteBufCodecs.VAR_INT, EntryView::xp,
                ByteBufCodecs.STRING_UTF8, EntryView::stage,
                ByteBufCodecs.VAR_INT, EntryView::state,
                ByteBufCodecs.VAR_INT, EntryView::pieces,
                EntryView::new);
    }

    public record View(int selected, List<Integer> done, List<Integer> totals, List<EntryView> entries) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, View::selected,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), View::done,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), View::totals,
                EntryView.CODEC.apply(ByteBufCodecs.list()), View::entries,
                View::new);
    }

    private final View view;
    private final ServerPlayer owner;
    private final List<String> ids;

    public MuseumMenu(int id, Inventory inventory, View view) {
        this(id, view, null, List.of());
    }

    private MuseumMenu(int id, View view, ServerPlayer owner, List<String> ids) {
        super(SkyMenus.MUSEUM, id);
        this.view = view;
        this.owner = owner;
        this.ids = ids;
    }

    public View view() {
        return view;
    }

    static void init() {
        Actions.register("museum", (player, params) -> open(player, 0));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("museum").executes(ctx -> {
            open(ctx.getSource().getPlayerOrException(), 0);
            return 1;
        })));
    }

    public static void open(ServerPlayer player, int selected) {
        List<Museum.Category> categories = Museum.categories();
        int pick = Math.max(0, Math.min(selected, categories.size() - 1));
        Map<String, int[]> progress = Museum.progress(player);
        List<Integer> done = new ArrayList<>();
        List<Integer> totals = new ArrayList<>();
        for (Museum.Category category : categories) {
            int[] p = progress.getOrDefault(category.id(), new int[] {0, 0});
            done.add(p[0]);
            totals.add(p[1]);
        }
        Set<String> donated = Museum.donated(player);
        List<EntryView> entries = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        if (!categories.isEmpty()) {
            List<Museum.Entry> list = new ArrayList<>(categories.get(pick).entries());
            // Ready to donate first, then the ones still to find, then the donated.
            list.sort((a, b) -> Integer.compare(order(player, donated, a), order(player, donated, b)));
            for (Museum.Entry entry : list) {
                ItemDefinition def = ItemRegistry.skyblock(entry.items().getFirst());
                ItemStack icon = def == null ? ItemStack.EMPTY : SkyItems.create(def, 1);
                int state = donated.contains(entry.id()) ? 2 : Museum.canDonate(player, entry) ? 1 : 0;
                entries.add(new EntryView(Museum.name(entry), icon, entry.xp(), entry.stage(), state, entry.items().size()));
                ids.add(entry.id());
            }
        }
        View view = new View(pick, done, totals, entries);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Museum");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new MuseumMenu(id, view, player, ids);
            }
        });
    }

    private static int order(ServerPlayer player, Set<String> donated, Museum.Entry entry) {
        return donated.contains(entry.id()) ? 2 : Museum.canDonate(player, entry) ? 0 : 1;
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        int selected = view.selected();
        if (id < ENTRY) {
            selected = id;
        } else if (id - ENTRY < ids.size()) {
            String problem = Museum.donate(owner, ids.get(id - ENTRY));
            if (!problem.isEmpty()) {
                owner.sendOverlayMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
            }
        }
        int pick = selected;
        owner.level().getServer().execute(() -> open(owner, pick));
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
