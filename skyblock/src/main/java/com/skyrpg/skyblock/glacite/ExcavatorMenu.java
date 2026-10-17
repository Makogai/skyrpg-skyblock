package com.skyrpg.skyblock.glacite;

import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.essence.Essence;
import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import net.minecraft.world.item.ItemStack;

/**
 * The Fossil Excavator. The setup view shows your best chisel and your Suspicious Scrap; starting a dig takes one scrap
 * (the chisel is kept) and opens the dig view: a 9 x 6 bed of dirt, played on the server and sent to the screen through
 * data slots ({@link #CELLS} cells, then {@link #STATUS} numbers). Each click uses a chisel charge and uncovers a tile:
 * dirt, a treasure, or part of a fossil. When the charges run out (or you finish or close it) you get every treasure you
 * uncovered, and the fossil if you uncovered all of it (else Fossil Essence for the part you did).
 *
 * <p>Cell values: 0 hidden, 1 hidden and highlighted, 2 dirt, 3 fossil, 4 fossil left buried (when it's over), 1000 + n
 * treasure n of the palette.
 * Status: charges left, max charges, fossil percent uncovered (-1 none found yet), 1 when over.
 * Buttons: a cell's index; {@link #START}; {@link #FINISH}.
 */
public final class ExcavatorMenu extends AbstractContainerMenu {
    public static final int WIDTH = 9;
    public static final int HEIGHT = 6;
    public static final int CELLS = WIDTH * HEIGHT;
    public static final int STATUS = 4;
    public static final int SETUP = 0;
    public static final int DIG = 1;
    public static final int START = 900;
    public static final int FINISH = 901;

    /**
     * What the screen shows: the mode; your chisel (empty if none), its charges and highlight chance; your Suspicious
     * Scrap; the fossil hidden in this dig ("" if none, shown only once found); and the pictures of this dig's treasures.
     */
    public record View(int mode, ItemStack chisel, int charges, int highlight, int scrap, String fossil, List<ItemStack> palette) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = new StreamCodec<>() {
            @Override
            public View decode(RegistryFriendlyByteBuf buf) {
                return new View(buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readUtf(), ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf));
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, View view) {
                buf.writeVarInt(view.mode());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, view.chisel());
                buf.writeVarInt(view.charges());
                buf.writeVarInt(view.highlight());
                buf.writeVarInt(view.scrap());
                buf.writeUtf(view.fossil());
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, view.palette());
            }
        };
    }

    private final View view;
    private final ServerPlayer owner;
    private final Dig dig;
    private final ContainerData data;

    public ExcavatorMenu(int id, Inventory inventory, View view) {
        this(id, view, null, null, new SimpleContainerData(CELLS + STATUS));
    }

    private ExcavatorMenu(int id, View view, ServerPlayer owner, Dig dig, ContainerData data) {
        super(SkyMenus.EXCAVATOR, id);
        this.view = view;
        this.owner = owner;
        this.dig = dig;
        this.data = data;
        addDataSlots(data);
    }

    public View view() {
        return view;
    }

    public int cell(int index) {
        return data.get(index);
    }

    public int status(int index) {
        return data.get(CELLS + index);
    }

    /** The setup view. */
    static void openSetup(ServerPlayer player) {
        Fossils.Chisel chisel = Fossils.bestChisel(player);
        ItemStack shown = chisel == null ? ItemStack.EMPTY : chisel.stack().copyWithCount(1);
        open(player, new View(SETUP, shown, chisel == null ? 0 : chisel.charges(), chisel == null ? 0 : chisel.highlight(),
                SkyStacks.count(player.getInventory(), Fossils.SCRAP), "", List.of()), null);
    }

    private static void open(ServerPlayer player, View view, Dig dig) {
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Fossil Excavator");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                ContainerData data = new ContainerData() {
                    @Override
                    public int get(int index) {
                        return dig == null ? 0 : index < CELLS ? dig.cell(index) : dig.status(index - CELLS);
                    }

                    @Override
                    public void set(int index, int value) {
                    }

                    @Override
                    public int getCount() {
                        return CELLS + STATUS;
                    }
                };
                return new ExcavatorMenu(id, view, player, dig, data);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        if (dig != null) {
            if (id == FINISH) {
                dig.finish();
                owner.level().getServer().execute(owner::closeContainer);
            } else if (id >= 0 && id < CELLS) {
                dig.click(id);
            }
            return true;
        }
        if (id == START) {
            Fossils.Chisel chisel = Fossils.bestChisel(owner);
            if (chisel == null) {
                refuse("You need a Chisel! Researcher Beryl knows where to get one.");
            } else if (SkyStacks.count(owner.getInventory(), Fossils.SCRAP) < 1) {
                refuse("You need a Suspicious Scrap! Find them in the Glacite Mineshafts.");
            } else {
                SkyStacks.take(owner.getInventory(), Fossils.SCRAP, 1);
                Dig started = new Dig(owner, chisel);
                owner.level().getServer().execute(() -> open(owner, new View(DIG, chisel.stack().copyWithCount(1), chisel.charges(),
                        chisel.highlight(), 0, started.fossilName(), started.palette), started));
                Msg.playSound(owner, SoundEvents.BRUSH_GRAVEL, 1.0f, 1.0f);
            }
        }
        return true;
    }

    private void refuse(String why) {
        owner.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
        Msg.playSound(owner, SoundEvents.VILLAGER_NO, 0.8f, 1.0f);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (dig != null) {
            dig.finish();
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

    // ---------------------------------------------------------------- one dig

    /** One excavation: what's under each tile, what's been uncovered, and the charges left. */
    static final class Dig {
        private final ServerPlayer player;
        private final Fossils.Chisel chisel;
        private final RandomSource random;
        /** -1 dirt, -2 fossil, else the treasure's index in {@link #treasures}. */
        private final int[] under = new int[CELLS];
        private final boolean[] uncovered = new boolean[CELLS];
        private final boolean[] highlighted = new boolean[CELLS];
        private final List<Fossils.Treasure> treasures = new ArrayList<>();
        final List<ItemStack> palette = new ArrayList<>();
        private final Fossils.Fossil fossil;
        private final Set<Integer> fossilCells = new HashSet<>();
        private int charges;
        private boolean over;

        Dig(ServerPlayer player, Fossils.Chisel chisel) {
            this.player = player;
            this.chisel = chisel;
            this.random = player.getRandom();
            this.charges = chisel.charges();
            java.util.Arrays.fill(under, -1);
            // A fossil one dig in ten (Hypixel's), somewhere in the bed.
            Fossils.Fossil picked = random.nextInt(10) == 0 ? Fossils.FOSSILS.get(random.nextInt(Fossils.FOSSILS.size())) : null;
            if (picked != null) {
                int w = picked.shape()[0].length();
                int h = picked.shape().length;
                int ox = random.nextInt(WIDTH - w + 1);
                int oy = random.nextInt(HEIGHT - h + 1);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (picked.shape()[y].charAt(x) == 'X') {
                            int cell = (oy + y) * WIDTH + ox + x;
                            under[cell] = -2;
                            fossilCells.add(cell);
                        }
                    }
                }
            }
            this.fossil = picked;
            // 8 to 11 treasures in the dirt around it.
            int count = 8 + random.nextInt(4);
            List<Integer> free = new ArrayList<>();
            for (int i = 0; i < CELLS; i++) {
                if (under[i] == -1) {
                    free.add(i);
                }
            }
            for (int i = 0; i < count && !free.isEmpty(); i++) {
                int cell = free.remove(random.nextInt(free.size()));
                Fossils.Treasure treasure = Fossils.rollTreasure(random);
                under[cell] = treasures.size();
                treasures.add(treasure);
                palette.add(treasure.icon());
            }
        }

        String fossilName() {
            return fossil == null ? "" : fossil.name();
        }

        int cell(int index) {
            if (!uncovered[index]) {
                // Once it's over, the rest of the fossil shows where it lay.
                return over && under[index] == -2 ? 4 : highlighted[index] ? 1 : 0;
            }
            return under[index] == -1 ? 2 : under[index] == -2 ? 3 : 1000 + under[index];
        }

        int status(int index) {
            return switch (index) {
                case 0 -> charges;
                case 1 -> chisel.charges();
                case 2 -> fossilFound() ? fossilPercent() : -1;
                default -> over ? 1 : 0;
            };
        }

        private boolean fossilFound() {
            return fossilCells.stream().anyMatch(c -> uncovered[c]);
        }

        private int fossilPercent() {
            long found = fossilCells.stream().filter(c -> uncovered[c]).count();
            return fossilCells.isEmpty() ? 0 : (int) (100 * found / fossilCells.size());
        }

        void click(int index) {
            if (over || uncovered[index] || charges <= 0) {
                return;
            }
            uncovered[index] = true;
            charges--;
            int what = under[index];
            if (what >= 0) {
                Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
            } else if (what == -2) {
                Msg.playSound(player, SoundEvents.BONE_BLOCK_BREAK, 1.0f, 0.9f);
                if (fossilPercent() == 100) {
                    Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.4f);
                }
            } else {
                Msg.playSound(player, SoundEvents.BRUSH_GRAVEL_COMPLETED, 0.8f, 1.0f);
                // Uncovering plain dirt can show where something is: a treasure, or (once found) more of the fossil.
                if (random.nextInt(100) < chisel.highlight()) {
                    highlight();
                }
            }
            if (charges <= 0) {
                finish();
            }
        }

        private void highlight() {
            List<Integer> hidden = new ArrayList<>();
            boolean found = fossilFound();
            for (int i = 0; i < CELLS; i++) {
                if (!uncovered[i] && !highlighted[i] && (under[i] >= 0 || under[i] == -2 && found)) {
                    hidden.add(i);
                }
            }
            if (!hidden.isEmpty()) {
                highlighted[hidden.get(random.nextInt(hidden.size()))] = true;
            }
        }

        /** The end of the dig (once): everything uncovered goes to the player. */
        void finish() {
            if (over) {
                return;
            }
            over = true;
            Notice.Panel panel = Notice.of(Notice.Kind.MINING).tag("EXCAVATION").color(0xFFAA00).panel();
            for (int i = 0; i < CELLS; i++) {
                if (uncovered[i] && under[i] >= 0) {
                    Fossils.Treasure treasure = treasures.get(under[i]);
                    treasure.give(player);
                    panel.line(Component.literal(" ").append(treasure.text()));
                }
            }
            if (fossil != null && fossilFound()) {
                int percent = fossilPercent();
                if (percent >= 100) {
                    ItemStack stack = Fossils.stack(fossil.item(), 1);
                    panel.line(Component.literal(" FOSSIL! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).append(stack.getHoverName()));
                    ItemGains.give(player, stack, ItemGains.Source.MINING);
                    Notice.rareDrop(player, Fossils.stack(fossil.item(), 1).getHoverName().copy(), 0xFFAA00);
                } else {
                    // Half a point of Fossil Essence for each percent of the fossil uncovered.
                    int essence = Math.max(1, percent / 2);
                    Essence.give(player, Essence.FOSSIL, essence);
                    panel.line(Component.literal(" " + fossil.name() + " " + percent + "% uncovered: +" + essence + " Fossil Essence")
                            .withColor(Essence.color(Essence.FOSSIL)));
                }
            }
            if (!panel.hasLines()) {
                panel.line(Component.literal(" Nothing but dirt this time.").withStyle(ChatFormatting.GRAY));
            }
            panel.send(player, Component.literal("Excavation complete!").withStyle(ChatFormatting.GOLD));
        }
    }
}
