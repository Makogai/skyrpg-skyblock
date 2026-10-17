package com.skyrpg.skyblock.experiment;

import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.enchant.Books;
import com.skyrpg.items.enchant.Enchants;
import com.skyrpg.skyblock.pet.SkyBlockPets;
import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Experimentation Table and its three experiments. The table view lists every stake; a game view is one Superpairs,
 * Chronomatron or Ultrasequencer run, played on the server: its board goes to the screen through data slots every tick
 * ({@link #CELLS} cells, then {@link #STATUS} numbers), so the shown notes and numbers move without reopening the menu.
 * Buttons: a cell's index; {@link #BUY}, {@link #BACK}; {@link #START} + game * 100 + stake.
 */
public final class ExperimentMenu extends AbstractContainerMenu {
    public static final int TABLE = 0;
    public static final int SUPERPAIRS = 1;
    public static final int CHRONOMATRON = 2;
    public static final int ULTRASEQUENCER = 3;
    public static final int CELLS = 36;
    public static final int STATUS = 4;
    public static final int PHASE_PLAY = 0;
    public static final int PHASE_WATCH = 1;
    public static final int PHASE_OVER = 2;
    public static final int BUY = 900;
    public static final int BACK = 901;
    public static final int START = 1000;
    /** Chronomatron's notes, in order. */
    public static final DyeColor[] NOTES = {DyeColor.LIME, DyeColor.YELLOW, DyeColor.LIGHT_BLUE, DyeColor.RED, DyeColor.PURPLE,
            DyeColor.ORANGE, DyeColor.PINK, DyeColor.CYAN, DyeColor.MAGENTA, DyeColor.WHITE};

    /** One stake on the table: its game, index, name, Enchanting level and XP level cost. */
    public record TierView(int game, int index, String name, int enchanting, int cost) {
        static final StreamCodec<RegistryFriendlyByteBuf, TierView> CODEC = new StreamCodec<>() {
            @Override
            public TierView decode(RegistryFriendlyByteBuf buf) {
                return new TierView(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, TierView tier) {
                buf.writeVarInt(tier.game());
                buf.writeVarInt(tier.index());
                buf.writeUtf(tier.name());
                buf.writeVarInt(tier.enchanting());
                buf.writeVarInt(tier.cost());
            }
        };
    }

    /**
     * What the screen shows: the mode, and for a game its stake, board size, Enchanting XP per step and the pictures on its
     * cells; for the table every stake, your charges, banked bonus clicks, serum doses, the next charge's price (-1: none
     * today), your Enchanting level and XP levels.
     */
    public record View(int mode, String name, int width, int height, int xpPerStep, List<ItemStack> palette, List<TierView> tiers,
                       int charges, int maxCharges, long nextCharge, int chronoBonus, int ultraBonus, int serums, int buyCost,
                       int enchanting, int levels) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf), TierView.CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list())
                        .decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readInt(), buf.readVarInt(), buf.readVarInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                buf.writeVarInt(view.mode());
                buf.writeUtf(view.name());
                buf.writeVarInt(view.width());
                buf.writeVarInt(view.height());
                buf.writeVarInt(view.xpPerStep());
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, view.palette());
                TierView.CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()).encode(buf, view.tiers());
                buf.writeVarInt(view.charges());
                buf.writeVarInt(view.maxCharges());
                buf.writeVarLong(view.nextCharge());
                buf.writeVarInt(view.chronoBonus());
                buf.writeVarInt(view.ultraBonus());
                buf.writeVarInt(view.serums());
                buf.writeInt(view.buyCost());
                buf.writeVarInt(view.enchanting());
                buf.writeVarInt(view.levels());
            }
        };
    }

    private final View view;
    private final ServerPlayer owner;
    private final Game game;
    private final ContainerData data;

    public ExperimentMenu(int id, Inventory inventory, View view) {
        this(id, view, null, null, new SimpleContainerData(CELLS + STATUS));
    }

    private ExperimentMenu(int id, View view, ServerPlayer owner, Game game, ContainerData data) {
        super(SkyMenus.EXPERIMENT, id);
        this.view = view;
        this.owner = owner;
        this.game = game;
        this.data = data;
        addDataSlots(data);
    }

    public View view() {
        return view;
    }

    /** A cell's value (see the screen for what each game's values mean). */
    public int cell(int index) {
        return data.get(index);
    }

    public int status(int index) {
        return data.get(CELLS + index);
    }

    public static void openTable(ServerPlayer player) {
        List<TierView> tiers = new ArrayList<>();
        for (int i = 0; i < Experiments.superpairs.size(); i++) {
            Experiments.Superpairs s = Experiments.superpairs.get(i);
            tiers.add(new TierView(SUPERPAIRS, i, s.name(), s.enchanting(), s.cost()));
        }
        addTiers(tiers, CHRONOMATRON, Experiments.chronomatron);
        addTiers(tiers, ULTRASEQUENCER, Experiments.ultrasequencer);
        Experiments.State state = Experiments.state(player);
        View view = new View(TABLE, "Experimentation Table", 0, 0, 0, List.of(), tiers, Experiments.available(player), Experiments.charges,
                Experiments.nextCharge(player) / 1000, state.chronoBonus(), state.ultraBonus(), state.serums(), Experiments.buyCost(player),
                Experiments.enchanting(player), player.experienceLevel);
        open(player, view, null);
    }

    private static void addTiers(List<TierView> tiers, int game, List<Experiments.Addon> addons) {
        for (int i = 0; i < addons.size(); i++) {
            Experiments.Addon a = addons.get(i);
            tiers.add(new TierView(game, i, a.name(), a.enchanting(), a.cost()));
        }
    }

    private static void open(ServerPlayer player, View view, Game game) {
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal(view.name());
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                ContainerData data = new ContainerData() {
                    @Override
                    public int get(int index) {
                        if (game == null) {
                            return 0;
                        }
                        return index < CELLS ? game.cell(index) : game.status(index - CELLS);
                    }

                    @Override
                    public void set(int index, int value) {
                    }

                    @Override
                    public int getCount() {
                        return CELLS + STATUS;
                    }
                };
                return new ExperimentMenu(id, view, player, game, data);
            }
        });
    }

    /** Starts a stake: "" when it started, else why not. */
    private static String start(ServerPlayer player, int game, int index) {
        int enchanting = Experiments.enchanting(player);
        if (Experiments.available(player) <= 0) {
            return "You have no charges left! Wait for one, or buy one.";
        }
        if (game == SUPERPAIRS && index < Experiments.superpairs.size()) {
            Experiments.Superpairs stake = Experiments.superpairs.get(index);
            if (enchanting < stake.enchanting()) {
                return "Requires Enchanting " + stake.enchanting() + "!";
            }
            if (!Experiments.takeLevels(player, stake.cost())) {
                return "You need " + stake.cost() + " XP levels!";
            }
            int bonus = Experiments.useCharge(player, stake.addonMultiplier());
            SuperpairsGame run = new SuperpairsGame(player, stake, bonus);
            open(player, new View(SUPERPAIRS, stake.name() + " Superpairs", stake.width(), stake.height(), 0, run.palette, List.of(),
                    0, 0, 0, 0, 0, 0, 0, 0, 0), run);
            if (bonus > 0) {
                player.sendOverlayMessage(Component.literal("+" + bonus + " clicks from your add-on experiments!").withStyle(ChatFormatting.AQUA));
            }
            return "";
        }
        List<Experiments.Addon> addons = game == CHRONOMATRON ? Experiments.chronomatron : Experiments.ultrasequencer;
        if (index >= addons.size()) {
            return "";
        }
        Experiments.Addon stake = addons.get(index);
        if (enchanting < stake.enchanting()) {
            return "Requires Enchanting " + stake.enchanting() + "!";
        }
        if (!Experiments.takeLevels(player, stake.cost())) {
            return "You need " + stake.cost() + " XP levels!";
        }
        if (game == CHRONOMATRON) {
            List<ItemStack> palette = new ArrayList<>();
            for (int i = 0; i < stake.colors(); i++) {
                palette.add(new ItemStack(Items.STAINED_GLASS.pick(NOTES[i])));
            }
            open(player, new View(CHRONOMATRON, stake.name() + " Chronomatron", stake.colors(), 1, stake.xp(), palette, List.of(), 0, 0, 0, 0, 0,
                    0, 0, 0, 0), new ChronomatronGame(player, stake));
        } else {
            open(player, new View(ULTRASEQUENCER, stake.name() + " Ultrasequencer", stake.width(), stake.height(), stake.xp(), List.of(),
                    List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0), new UltrasequencerGame(player, stake));
        }
        return "";
    }

    /** Testing: the open game's answers (Superpairs' pictures by tile, Chronomatron's chain, Ultrasequencer's numbers by square). */
    public String peek() {
        return game == null ? "No experiment open." : game.peek();
    }

    @Override
    public void broadcastChanges() {
        if (game != null && owner != null) {
            game.tick();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        if (game != null) {
            if (id == BACK) {
                game.finish();
                owner.level().getServer().execute(() -> openTable(owner));
            } else if (id >= 0 && id < CELLS) {
                game.click(id);
            }
            return true;
        }
        String problem = "";
        if (id == BUY) {
            problem = Experiments.buyCharge(owner);
        } else if (id >= START) {
            problem = start(owner, (id - START) / 100, (id - START) % 100);
            if (problem.isEmpty()) {
                return true;
            }
        } else {
            return false;
        }
        if (!problem.isEmpty()) {
            owner.sendOverlayMessage(Component.literal(problem).withStyle(ChatFormatting.RED));
            Msg.playSound(owner, SoundEvents.VILLAGER_NO, 0.8f, 1.0f);
        }
        owner.level().getServer().execute(() -> openTable(owner));
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (game != null && owner != null) {
            game.finish();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || player == owner && owner.isAlive();
    }

    // ---- the games (server side) ------------------------------------------------------------------------------

    private abstract static class Game {
        final ServerPlayer player;
        final RandomSource random;
        int tick;
        private boolean finished;

        Game(ServerPlayer player) {
            this.player = player;
            this.random = player.getRandom();
        }

        abstract int cell(int index);

        abstract int status(int index);

        abstract void click(int index);

        abstract void tick();

        /** Gives what the run earned (once, when it ends or the screen closes). */
        abstract void reward();

        abstract String peek();

        final void finish() {
            if (!finished) {
                finished = true;
                reward();
            }
        }
    }

    /** A prize on a Superpairs pair: what it gives (an item, or Enchanting XP) and what its tile shows. */
    private record Prize(ItemStack give, int xp, ItemStack display) {
    }

    /**
     * Superpairs: pairs of prizes face down, and a few power-ups (Feather: +3 clicks; Diamond: the next tile finds its pair).
     * Each tile turned costs a click; two of a kind in a row win the prize. Out of clicks, the board is shown and the run ends.
     * Cells: 0 face down, else state * 1000 + picture + 1 (state 1 turned, 2 won or used, 3 shown at the end).
     */
    private static final class SuperpairsGame extends Game {
        final List<ItemStack> palette = new ArrayList<>();
        final List<Prize> prizes = new ArrayList<>();
        final int tiles;
        final int[] picture;
        final int[] state;
        final int feather;
        final int diamond;
        final List<Prize> won = new ArrayList<>();
        int clicks;
        int first = -1;
        int second = -1;
        int flipAt = -1;
        boolean instant;
        boolean over;

        SuperpairsGame(ServerPlayer player, Experiments.Superpairs stake, int bonusClicks) {
            super(player);
            tiles = Math.min(CELLS, stake.width() * stake.height());
            int powerUps = 2;
            int pairs = (tiles - powerUps) / 2;
            for (int i = 0; i < pairs; i++) {
                Prize prize = roll(stake);
                prizes.add(prize);
                palette.add(prize.display());
            }
            feather = palette.size();
            palette.add(named(new ItemStack(Items.FEATHER), "Gained +3 Clicks", ChatFormatting.AQUA));
            diamond = palette.size();
            palette.add(named(new ItemStack(Items.DIAMOND), "Instant Find", ChatFormatting.AQUA));
            List<Integer> layout = new ArrayList<>();
            for (int i = 0; i < pairs; i++) {
                layout.add(i);
                layout.add(i);
            }
            layout.add(feather);
            layout.add(diamond);
            while (layout.size() < tiles) {
                layout.add(feather);
            }
            Collections.shuffle(layout, new java.util.Random(random.nextLong()));
            picture = layout.stream().mapToInt(Integer::intValue).toArray();
            state = new int[tiles];
            clicks = stake.clicks() + bonusClicks;
        }

        private Prize roll(Experiments.Superpairs stake) {
            double total = stake.rewards().stream().mapToDouble(Experiments.Reward::weight).sum();
            double pick = random.nextDouble() * total;
            Experiments.Reward reward = stake.rewards().getLast();
            for (Experiments.Reward r : stake.rewards()) {
                pick -= r.weight();
                if (pick < 0) {
                    reward = r;
                    break;
                }
            }
            int amount = reward.min() + (reward.max() > reward.min() ? random.nextInt(reward.max() - reward.min() + 1) : 0);
            switch (reward.kind()) {
                case "xp" -> {
                    int xp = Math.round(amount / 100f) * 100;
                    return new Prize(ItemStack.EMPTY, xp, xpPicture(xp));
                }
                case "book" -> {
                    ItemStack book = book(reward.item().equals("ultra"));
                    return book.isEmpty() ? xpPrize(5000) : new Prize(book, 0, book.copy());
                }
                case "pet" -> {
                    ItemStack pet = SkyBlockPets.known(reward.item()) ? SkyBlockPets.create(reward.item(), reward.tier(), 0) : ItemStack.EMPTY;
                    return pet.isEmpty() ? xpPrize(5000) : new Prize(pet, 0, pet.copy());
                }
                default -> {
                    ItemDefinition def = ItemRegistry.skyblock(reward.item());
                    if (def == null) {
                        return xpPrize(5000);
                    }
                    ItemStack stack = SkyItems.create(def, amount);
                    return new Prize(stack, 0, stack.copy());
                }
            }
        }

        private Prize xpPrize(int xp) {
            return new Prize(ItemStack.EMPTY, xp, xpPicture(xp));
        }

        /** A book of a random table enchant at the table's top level; an ultra-rare one is a level above it. */
        private ItemStack book(boolean ultra) {
            List<Enchants.Enchant> pool = new ArrayList<>();
            for (Enchants.Enchant e : Enchants.all()) {
                if (e.tableMax() > 0 && !e.ultimate() && (!ultra || e.max() > e.tableMax())) {
                    pool.add(e);
                }
            }
            if (pool.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Enchants.Enchant pick = pool.get(random.nextInt(pool.size()));
            return Books.create(pick.id(), ultra ? pick.tableMax() + 1 : pick.tableMax(), 1);
        }

        @Override
        int cell(int index) {
            return index >= tiles ? -1 : state[index] == 0 ? 0 : state[index] * 1000 + picture[index] + 1;
        }

        @Override
        String peek() {
            return java.util.Arrays.toString(picture) + " feather " + feather + " diamond " + diamond;
        }

        @Override
        int status(int index) {
            return switch (index) {
                case 0 -> over ? PHASE_OVER : PHASE_PLAY;
                case 1 -> clicks;
                case 2 -> won.size();
                default -> instant ? 1 : 0;
            };
        }

        @Override
        void click(int index) {
            if (over || flipAt >= 0 || index >= tiles || state[index] != 0 || clicks <= 0) {
                return;
            }
            clicks--;
            int p = picture[index];
            if (p == feather || p == diamond) {
                state[index] = 2;
                if (p == feather) {
                    clicks += 3;
                } else {
                    instant = true;
                }
                Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.8f);
            } else if (instant) {
                instant = false;
                int other = partner(index);
                state[index] = 2;
                state[other] = 2;
                if (first == other) {
                    first = -1;
                }
                win(p);
            } else if (first < 0) {
                first = index;
                state[index] = 1;
                Msg.playSound(player, SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1.4f);
            } else if (picture[first] == p) {
                state[first] = 2;
                state[index] = 2;
                first = -1;
                win(p);
            } else {
                state[index] = 1;
                second = index;
                flipAt = tick + 20;
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.8f);
            }
            boolean anyLeft = false;
            for (int i = 0; i < tiles && !anyLeft; i++) {
                anyLeft = state[i] == 0;
            }
            if (clicks <= 0 && flipAt < 0 || !anyLeft && flipAt < 0) {
                end();
            }
        }

        private int partner(int index) {
            for (int i = 0; i < tiles; i++) {
                if (i != index && picture[i] == picture[index]) {
                    return i;
                }
            }
            return index;
        }

        private void win(int p) {
            won.add(prizes.get(p));
            Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }

        private void end() {
            over = true;
            for (int i = 0; i < tiles; i++) {
                if (state[i] < 2) {
                    state[i] = 3;
                }
            }
            Msg.playSound(player, SoundEvents.ANVIL_LAND, 0.4f, 1.6f);
        }

        @Override
        void tick() {
            tick++;
            if (flipAt >= 0 && tick >= flipAt) {
                state[first] = 0;
                state[second] = 0;
                first = -1;
                second = -1;
                flipAt = -1;
                if (clicks <= 0) {
                    end();
                }
            }
        }

        @Override
        void reward() {
            Notice.Panel panel = Notice.of(Notice.Kind.ENCHANT).tag("SUPERPAIRS").panel();
            int xp = 0;
            for (Prize prize : won) {
                xp += prize.xp();
                if (!prize.give().isEmpty()) {
                    ItemStack stack = prize.give().copy();
                    panel.line(Component.literal(" " + (stack.getCount() > 1 ? stack.getCount() + "x " : "")).withStyle(ChatFormatting.GRAY)
                            .append(stack.getHoverName()).append(bookName(stack)));
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                    }
                }
            }
            if (xp > 0) {
                SkillLevels.giveXp(player, Experiments.ENCHANTING, xp);
                panel.line(Component.literal(String.format(Locale.ROOT, " +%,d Enchanting Exp", xp)).withStyle(ChatFormatting.DARK_AQUA));
            }
            panel.send(player, Component.literal(won.isEmpty() ? "No pairs this time." : won.size() + (won.size() == 1 ? " pair!" : " pairs!"))
                    .withStyle(won.isEmpty() ? ChatFormatting.GRAY : ChatFormatting.GREEN));
        }
    }

    /**
     * Chronomatron: a chain of notes plays (each lights its pad and sounds); click the pads in the same order and the chain
     * plays again with one more note. A wrong pad, or 49 notes, ends it. Cells: 1 when the pad is lit.
     * Status: phase, the longest chain repeated, your place in this one, the chain's length.
     */
    private static final class ChronomatronGame extends Game {
        final Experiments.Addon stake;
        final List<Integer> chain = new ArrayList<>();
        final int[] lit;
        int phase = PHASE_WATCH;
        int shown;
        int timer = 20;
        int input;
        int completed;

        ChronomatronGame(ServerPlayer player, Experiments.Addon stake) {
            super(player);
            this.stake = stake;
            this.lit = new int[stake.colors()];
            chain.add(random.nextInt(stake.colors()));
        }

        @Override
        int cell(int index) {
            return index < lit.length ? (lit[index] > 0 ? 1 : 0) : -1;
        }

        @Override
        String peek() {
            return chain.toString();
        }

        @Override
        int status(int index) {
            return switch (index) {
                case 0 -> phase;
                case 1 -> completed;
                case 2 -> input;
                default -> chain.size();
            };
        }

        private void sound(int note) {
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 0.6f + note * 0.12f);
        }

        @Override
        void tick() {
            for (int i = 0; i < lit.length; i++) {
                if (lit[i] > 0) {
                    lit[i]--;
                }
            }
            if (phase != PHASE_WATCH || --timer > 0) {
                return;
            }
            if (shown < chain.size()) {
                int note = chain.get(shown++);
                lit[note] = 10;
                sound(note);
                timer = 14;
            } else {
                phase = PHASE_PLAY;
                input = 0;
            }
        }

        @Override
        void click(int index) {
            if (phase != PHASE_PLAY || index >= lit.length) {
                return;
            }
            lit[index] = 5;
            if (chain.get(input) != index) {
                phase = PHASE_OVER;
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
                return;
            }
            sound(index);
            if (++input == chain.size()) {
                completed = chain.size();
                if (completed >= 49) {
                    phase = PHASE_OVER;
                    return;
                }
                chain.add(random.nextInt(lit.length));
                phase = PHASE_WATCH;
                shown = 0;
                timer = 25;
            }
        }

        @Override
        void reward() {
            addonReward(player, stake, true, completed, Math.min(completed, 15) * stake.xp(), "CHRONOMATRON", "Longest chain: " + completed);
        }
    }

    /**
     * Ultrasequencer: numbers 1 to n show on the board for a moment; click their squares in order. Then again with one more
     * number (from 3). A wrong square ends it. Cells: while shown, the number; then 1000 + number once clicked; at the end
     * 2000 + number for the ones you missed. Status: phase, the longest sequence done, the next number, n.
     */
    private static final class UltrasequencerGame extends Game {
        final Experiments.Addon stake;
        final int squares;
        final int[] number;
        final boolean[] done;
        int length = 3;
        int phase;
        int timer;
        int next;
        int completed;

        UltrasequencerGame(ServerPlayer player, Experiments.Addon stake) {
            super(player);
            this.stake = stake;
            this.squares = Math.min(CELLS, stake.width() * stake.height());
            this.number = new int[squares];
            this.done = new boolean[squares];
            round();
        }

        @Override
        String peek() {
            return java.util.Arrays.toString(number);
        }

        private void round() {
            java.util.Arrays.fill(number, 0);
            java.util.Arrays.fill(done, false);
            List<Integer> spots = new ArrayList<>();
            for (int i = 0; i < squares; i++) {
                spots.add(i);
            }
            Collections.shuffle(spots, new java.util.Random(random.nextLong()));
            for (int n = 1; n <= length; n++) {
                number[spots.get(n - 1)] = n;
            }
            phase = PHASE_WATCH;
            timer = 40 + 5 * length;
            next = 1;
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.7f, 1.2f);
        }

        @Override
        int cell(int index) {
            if (index >= squares || number[index] == 0) {
                return index >= squares ? -1 : 0;
            }
            if (phase == PHASE_WATCH && timer > 0 && next == 1) {
                return number[index];
            }
            if (done[index]) {
                return 1000 + number[index];
            }
            return phase == PHASE_OVER ? 2000 + number[index] : 0;
        }

        @Override
        int status(int index) {
            return switch (index) {
                case 0 -> phase;
                case 1 -> completed;
                case 2 -> next;
                default -> length;
            };
        }

        @Override
        void tick() {
            if (phase != PHASE_WATCH || --timer > 0) {
                return;
            }
            if (next > length) {
                // The pause after a finished round: the next, one number longer.
                length++;
                round();
            } else {
                phase = PHASE_PLAY;
            }
        }

        @Override
        void click(int index) {
            if (phase != PHASE_PLAY || index >= squares || done[index]) {
                return;
            }
            if (number[index] != next) {
                phase = PHASE_OVER;
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
                return;
            }
            done[index] = true;
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 0.7f + next * 0.06f);
            if (++next > length) {
                completed = length;
                if (length >= squares) {
                    phase = PHASE_OVER;
                    return;
                }
                phase = PHASE_WATCH;
                timer = 20;
            }
        }

        @Override
        void reward() {
            addonReward(player, stake, false, completed, Math.min(completed, 20) * stake.xp(), "ULTRASEQUENCER", "Longest sequence: " + completed);
        }
    }

    /** " (Sharpness V)" for an enchanted book, else nothing. */
    private static Component bookName(ItemStack stack) {
        var enchants = com.skyrpg.items.ItemMods.enchantments(stack);
        if (!Items.ENCHANTED_BOOK.equals(stack.getItem()) || enchants.isEmpty()) {
            return Component.empty();
        }
        var first = enchants.entrySet().iterator().next();
        Enchants.Enchant enchant = Enchants.get(first.getKey());
        return Component.literal(" (" + (enchant == null ? first.getKey() : enchant.label(first.getValue())) + ")").withStyle(ChatFormatting.BLUE);
    }

    /** An add-on's Enchanting XP, and its bonus clicks banked for the next Superpairs. */
    private static void addonReward(ServerPlayer player, Experiments.Addon stake, boolean chrono, int reached, int xp, String tag, String line) {
        int clicks = stake.bonusFor(reached, Experiments.state(player).serums());
        Experiments.bankBonus(player, chrono, clicks);
        if (xp > 0) {
            SkillLevels.giveXp(player, Experiments.ENCHANTING, xp);
        }
        Notice.of(Notice.Kind.ENCHANT).tag(tag).panel()
                .line(Component.literal(" " + line).withStyle(ChatFormatting.GRAY))
                .line(Component.literal(String.format(Locale.ROOT, " +%,d Enchanting Exp", xp)).withStyle(ChatFormatting.DARK_AQUA))
                .line(Component.literal(clicks > 0 ? " +" + clicks + " Superpairs clicks" : " No bonus clicks").withStyle(clicks > 0
                        ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY))
                .send(player, Component.literal(stake.name() + " done!").withStyle(ChatFormatting.GREEN));
    }

    /** An Enchanting Exp prize's picture: a glinting Bottle o' Enchanting, unlike the bottle items. */
    private static ItemStack xpPicture(int xp) {
        ItemStack stack = named(new ItemStack(Items.EXPERIENCE_BOTTLE), String.format(Locale.ROOT, "+%,d Enchanting Exp", xp), ChatFormatting.DARK_AQUA);
        stack.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    private static ItemStack named(ItemStack stack, String name, ChatFormatting color) {
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(color)
                .withStyle(style -> style.withItalic(false)));
        return stack;
    }
}
