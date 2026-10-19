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
 * The Museum: category tabs, the milestone bar and the chosen category's donations (donated, ready to donate from your
 * inventory, out on loan, or not yet found). Button {@code i} (under {@link #ENTRY}) picks category {@code i};
 * {@link #CURATOR} opens the milestones; {@link #ENTRY} {@code + n} donates entry {@code n}, borrows it back or returns
 * it, by what it is now.
 */
public final class MuseumMenu extends AbstractContainerMenu {
    public static final int ENTRY = 100;
    public static final int CURATOR = 99;
    public static final List<String> NAMES = List.of("Combat", "Farming", "Mining", "Fishing", "Foraging", "Dungeoneering", "Hunting", "Special");

    /** An entry's state: not found. */
    public static final int NOT_FOUND = 0;
    /** In your inventory: click to donate. */
    public static final int CAN_DONATE = 1;
    /** In the Museum: click to borrow it. */
    public static final int DONATED = 2;
    /** On loan, and you have it with you: click to return it. */
    public static final int RETURNABLE = 3;
    /** On loan, and you don't have it with you. */
    public static final int BORROWED = 4;
    /** In the Museum, only counted (a higher donation covers it): nothing to lend. */
    public static final int COUNTED = 5;

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

    /** {@code next}: the next milestone's rewards, as coloured lines for its tooltip (empty at the last). */
    public record View(int selected, List<Integer> done, List<Integer> totals, List<EntryView> entries, int xp, int milestone, int claimable,
                       List<String> next) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, View::selected,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), View::done,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), View::totals,
                EntryView.CODEC.apply(ByteBufCodecs.list()), View::entries,
                ByteBufCodecs.VAR_INT, View::xp,
                ByteBufCodecs.VAR_INT, View::milestone,
                ByteBufCodecs.VAR_INT, View::claimable,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), View::next,
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
        Set<String> borrowed = Museum.borrowed(player);
        Map<String, List<ItemStack>> stored = Museum.stored(player);
        List<EntryView> entries = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        if (!categories.isEmpty()) {
            List<Museum.Entry> list = new ArrayList<>(categories.get(pick).entries());
            // Ready to donate or return first, then the ones still to find, then the donated.
            list.sort((a, b) -> Integer.compare(order(state(player, donated, borrowed, stored, a)), order(state(player, donated, borrowed, stored, b))));
            for (Museum.Entry entry : list) {
                ItemDefinition def = ItemRegistry.skyblock(entry.items().getFirst());
                ItemStack icon = def == null ? ItemStack.EMPTY : SkyItems.create(def, 1);
                entries.add(new EntryView(Museum.name(entry), icon, entry.xp(), entry.stage(), state(player, donated, borrowed, stored, entry), entry.items().size()));
                ids.add(entry.id());
            }
        }
        int xp = Museum.xp(player);
        int reached = MuseumMilestones.reached(xp);
        List<String> next = new ArrayList<>();
        if (reached < MuseumMilestones.MAX) {
            next.add("§7Next: §eMilestone " + (reached + 1) + " §7at §b" + (reached + 1) * MuseumMilestones.XP_PER + " XP");
            next.add("§8+§b" + MuseumMilestones.BITS_PER + "% Bits Multiplier");
            next.add("§8+§6" + MuseumMilestones.BANK_PER + "% Bank Interest");
            for (MuseumMilestones.Reward reward : MuseumMilestones.bonus(reached + 1)) {
                next.add("§d" + reward.name());
            }
        }
        View view = new View(pick, done, totals, entries, xp, reached, MuseumMilestones.claimableCount(player), next);
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

    /** What an entry is for this player now (one of the states above). */
    private static int state(ServerPlayer player, Set<String> donated, Set<String> borrowed, Map<String, List<ItemStack>> stored, Museum.Entry entry) {
        if (borrowed.contains(entry.id())) {
            return Museum.canDonate(player, entry) ? RETURNABLE : BORROWED;
        }
        if (donated.contains(entry.id())) {
            return stored.containsKey(entry.id()) ? DONATED : COUNTED;
        }
        return Museum.canDonate(player, entry) ? CAN_DONATE : NOT_FOUND;
    }

    private static int order(int state) {
        return switch (state) {
            case CAN_DONATE, RETURNABLE -> 0;
            case NOT_FOUND, BORROWED -> 1;
            default -> 2;
        };
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        if (id == CURATOR) {
            Curator.open(owner);
            return true;
        }
        int selected = view.selected();
        if (id < ENTRY) {
            selected = id;
        } else if (id - ENTRY < ids.size()) {
            String entry = ids.get(id - ENTRY);
            Museum.Entry parsed = Museum.entry(entry);
            String problem = "";
            if (parsed != null) {
                int state = state(owner, Museum.donated(owner), Museum.borrowed(owner), Museum.stored(owner), parsed);
                problem = switch (state) {
                    case CAN_DONATE -> Museum.donate(owner, entry);
                    case DONATED -> Museum.borrow(owner, entry);
                    case RETURNABLE -> Museum.giveBack(owner, entry);
                    case BORROWED -> parsed.set() ? "Bring the whole set back to return it." : "Bring it back to return it.";
                    default -> "";
                };
            }
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
