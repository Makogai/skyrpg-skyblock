package com.skyrpg.skyblock.galatea;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Hypixel's Huntraps (Dax's): right-click with one on Galatea to set it on the ground in the woods (a trapdoor) or on the
 * water of the lakes (a lily pad), up to five each. A trap holds the shard of a critter that lives there (rolled when it's
 * set, by the critters' rarity weights) and catches it after Hypixel's real hours (Common 8-12, Uncommon 10-15, Rare
 * 12-18, Epic 14-21, Legendary 16-24), less with a better trap (Medium -10%, Large -20%, Greater -35%, Astral -50%) and
 * Rosemary's Trapped. A ready trap: click it (or collect it from Dax) for the shard; the trap comes back to you.
 */
final class Huntraps {
    static final List<String> TIERS = List.of("RETIA_BASICA", "RETIA_MELIORA", "RETIA_ROBUSTA", "RETIA_FORTA", "RETIA_SUPREMA");
    private static final double[] FASTER = {1, 0.9, 0.8, 0.65, 0.5};
    private static final int[][] HOURS = {{8, 12}, {10, 15}, {12, 18}, {14, 21}, {16, 24}};
    private static final int PER_PLAYER = 5;
    private static final long HOUR = 3_600_000L;

    record Trap(UUID owner, BlockPos pos, int tier, boolean water, String shard, long readyAt, boolean told) {
        static final Codec<Trap> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Trap::owner),
                BlockPos.CODEC.fieldOf("pos").forGetter(Trap::pos),
                Codec.INT.fieldOf("tier").forGetter(Trap::tier),
                Codec.BOOL.fieldOf("water").forGetter(Trap::water),
                Codec.STRING.fieldOf("shard").forGetter(Trap::shard),
                Codec.LONG.fieldOf("ready_at").forGetter(Trap::readyAt),
                Codec.BOOL.optionalFieldOf("told", false).forGetter(Trap::told)).apply(i, Trap::new));

        boolean ready() {
            return System.currentTimeMillis() >= readyAt;
        }
    }

    /** Every trap set on Galatea. */
    static final class Book extends SavedData {
        static final Codec<Book> CODEC = Trap.CODEC.listOf().xmap(Book::new, b -> b.traps);
        static final SavedDataType<Book> TYPE = new SavedDataType<>(SkyRpgSkyblock.id("galatea_huntraps"), Book::new, CODEC, null);

        final List<Trap> traps;

        private Book() {
            this(List.of());
        }

        private Book(List<Trap> traps) {
            this.traps = new ArrayList<>(traps);
        }

        static Book get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(TYPE);
        }
    }

    private static boolean placed;

    private Huntraps() {
    }

    static void init() {
        Abilities.register("retentio", (player, ability) -> set(player));
        Identifier first = SkyRpgSkyblock.id("huntraps_first");
        UseBlockCallback.EVENT.addPhaseOrdering(first, Identifier.fromNamespaceAndPath("skyrpg_items", "abilities"));
        UseBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(first, (player, level, hand, hit) -> {
            BlockState state = level.getBlockState(hit.getBlockPos());
            if (!Galatea.isGalatea(level) || !(state.is(Blocks.SPRUCE_TRAPDOOR) || state.is(Blocks.LILY_PAD))) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return player instanceof ServerPlayer server && click(server, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(Huntraps::tick);
        // For testing: /sbhuntrap ready <player> makes their traps ready now.
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(net.minecraft.commands.Commands.literal("sbhuntrap")
                        .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .then(net.minecraft.commands.Commands.literal("ready")
                                .then(net.minecraft.commands.Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                            Book book = Book.get(target.level().getServer());
                                            book.traps.replaceAll(t -> t.owner().equals(target.getUUID())
                                                    ? new Trap(t.owner(), t.pos(), t.tier(), t.water(), t.shard(), System.currentTimeMillis(), false) : t);
                                            book.setDirty();
                                            return 1;
                                        })))));
    }

    private static List<Trap> mine(ServerPlayer player) {
        return Book.get(player.level().getServer()).traps.stream().filter(t -> t.owner().equals(player.getUUID())).toList();
    }

    // ---------------------------------------------------------------- setting a trap

    private static boolean set(ServerPlayer player) {
        int tier = TIERS.indexOf(SkyItems.skyblockId(player.getMainHandItem()));
        if (tier < 0) {
            return false;
        }
        if (!Galatea.isGalatea(player.level())) {
            player.sendOverlayMessage(Component.literal("Huntraps catch Galatea's critters: set it on Galatea.").withStyle(ChatFormatting.RED));
            return true;
        }
        ServerLevel level = (ServerLevel) player.level();
        if (mine(player).size() >= PER_PLAYER) {
            player.sendOverlayMessage(Component.literal("You already have " + PER_PLAYER + " Huntraps set. Collect one first!").withStyle(ChatFormatting.RED));
            return true;
        }
        HitResult hit = player.pick(5, 1f, true);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            player.sendOverlayMessage(Component.literal("Look at the ground or the water nearby to set it.").withStyle(ChatFormatting.GRAY));
            return true;
        }
        BlockPos at = block.getBlockPos();
        boolean water = level.getFluidState(at).isSource();
        BlockPos trap = at.above();
        if (!level.getBlockState(trap).isAir() || !water && !level.getBlockState(at).isFaceSturdy(level, at, net.minecraft.core.Direction.UP)) {
            player.sendOverlayMessage(Component.literal("There's no room for a trap there.").withStyle(ChatFormatting.RED));
            return true;
        }
        Zones.Zone zone = Zones.at(level, trap);
        String name = zone == null ? "" : zone.name();
        if (water ? !Critters.WATER_ZONES.contains(name) : !Critters.FOREST_ZONES.contains(name)) {
            player.sendOverlayMessage(Component.literal(water ? "No critters swim here." : "No critters live here: try the woods or the lakes.")
                    .withStyle(ChatFormatting.RED));
            return true;
        }
        Hunting.Shard shard = Critters.roll(water, level.getRandom());
        if (shard == null) {
            return true;
        }
        int r = Hunting.rarity(shard);
        double hours = HOURS[r][0] + level.getRandom().nextDouble() * (HOURS[r][1] - HOURS[r][0]);
        double factor = FASTER[tier] * (1 - 0.02 * Rosemary.level(player, "trapped"));
        long readyAt = System.currentTimeMillis() + Math.round(hours * factor * HOUR);
        level.setBlock(trap, water ? Blocks.LILY_PAD.defaultBlockState() : Blocks.SPRUCE_TRAPDOOR.defaultBlockState(), 3);
        Book book = Book.get(level.getServer());
        book.traps.add(new Trap(player.getUUID(), trap, tier, water, shard.key(), readyAt, false));
        book.setDirty();
        player.getMainHandItem().shrink(1);
        player.sendSystemMessage(Component.literal("HUNTRAP ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal("Set! It'll catch something in about " + Math.max(1, Math.round(hours * factor)) + " hours.")
                        .withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.WOODEN_TRAPDOOR_CLOSE, 1f, 0.9f);
        return true;
    }

    // ---------------------------------------------------------------- collecting

    private static boolean click(ServerPlayer player, BlockPos pos) {
        Book book = Book.get(player.level().getServer());
        Trap trap = book.traps.stream().filter(t -> t.pos().equals(pos)).findFirst().orElse(null);
        if (trap == null) {
            return false;
        }
        if (!trap.owner().equals(player.getUUID())) {
            player.sendOverlayMessage(Component.literal("This is someone else's Huntrap.").withStyle(ChatFormatting.GRAY));
            return true;
        }
        if (!trap.ready()) {
            player.sendOverlayMessage(Component.literal("Huntrap: catches something in " + left(trap) + ".").withStyle(ChatFormatting.YELLOW));
            return true;
        }
        collect(player, trap);
        return true;
    }

    private static String left(Trap trap) {
        long ms = Math.max(0, trap.readyAt() - System.currentTimeMillis());
        long hours = ms / HOUR;
        long minutes = ms % HOUR / 60_000;
        return hours > 0 ? hours + "h " + minutes + "m" : Math.max(1, minutes) + "m";
    }

    /** The shard to the Hunting Box, the trap back, its block gone. */
    private static void collect(ServerPlayer player, Trap trap) {
        Book book = Book.get(player.level().getServer());
        book.traps.remove(trap);
        book.setDirty();
        ServerLevel level = player.level().getServer().getLevel(Galatea.LEVEL);
        if (level != null) {
            level.setBlock(trap.pos(), Blocks.AIR.defaultBlockState(), 3);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, trap.pos().getX() + 0.5, trap.pos().getY() + 0.3, trap.pos().getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0);
        }
        Hunting.Shard shard = Hunting.SHARDS.get(trap.shard());
        if (shard != null) {
            Hunting.catchShard(player, shard, 1, "huntrap");
        }
        ItemDefinition def = ItemRegistry.skyblock(TIERS.get(trap.tier()));
        if (def != null) {
            ItemStack back = SkyItems.create(def, 1);
            if (!player.getInventory().add(back)) {
                player.drop(back, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
    }

    // ---------------------------------------------------------------- the tick: put the blocks back after a restart, say when traps are ready

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0) {
            return;
        }
        ServerLevel level = server.getLevel(Galatea.LEVEL);
        if (level == null) {
            return;
        }
        Book book = Book.get(server);
        if (!placed) {
            placed = true;
            for (Trap trap : book.traps) {
                if (level.getBlockState(trap.pos()).isAir()) {
                    level.setBlock(trap.pos(), trap.water() ? Blocks.LILY_PAD.defaultBlockState() : Blocks.SPRUCE_TRAPDOOR.defaultBlockState(), 3);
                }
            }
        }
        for (int i = 0; i < book.traps.size(); i++) {
            Trap trap = book.traps.get(i);
            ServerPlayer owner = server.getPlayerList().getPlayer(trap.owner());
            if (!trap.told() && trap.ready() && owner != null) {
                book.traps.set(i, new Trap(trap.owner(), trap.pos(), trap.tier(), trap.water(), trap.shard(), trap.readyAt(), true));
                book.setDirty();
                owner.sendSystemMessage(Component.literal("HUNT! ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                        .append(Component.literal("One of your Huntraps caught something! Collect it at the trap or from Dax.")
                                .withStyle(ChatFormatting.GRAY)));
            }
            if (trap.ready() && level.isLoaded(trap.pos())) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, trap.pos().getX() + 0.5, trap.pos().getY() + 0.5, trap.pos().getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0);
            }
        }
    }

    // ---------------------------------------------------------------- Dax

    /** Dax: your traps (collect the ready ones from here) and a Small Huntrap for sale. */
    static void dax(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Dax");
        CardMenu.open(player, p -> {
            List<Trap> traps = mine(p);
            List<CardView.Card> cards = new ArrayList<>();
            for (Trap trap : traps) {
                Hunting.Shard shard = Hunting.SHARDS.get(trap.shard());
                ItemDefinition def = ItemRegistry.skyblock(TIERS.get(trap.tier()));
                String where = (trap.water() ? "On the water" : "In the woods") + " at " + trap.pos().getX() + ", " + trap.pos().getZ();
                cards.add(new CardView.Card(def == null ? "Huntrap" : def.name(), trap.ready() ? where + "  ·  caught a "
                        + (shard == null ? "shard" : shard.name() + " Shard") + "!" : where + "  ·  " + left(trap) + " left",
                        trap.ready() ? 0x55FF55 : 0xEEEEEE, trap.ready(), trap.ready() ? "Collect" : "Waiting", new ItemStack(Items.SPRUCE_TRAPDOOR), "",
                        List.of(), -1, ""));
            }
            if (traps.isEmpty()) {
                cards.add(new CardView.Card("No traps set", "Right-click with a Huntrap on Galatea, on the ground in the woods or on the water.",
                        0xAAAAAA, false, "", new ItemStack(Items.SPRUCE_TRAPDOOR), "", List.of(), -1, ""));
            }
            ItemDefinition small = ItemRegistry.skyblock("RETIA_BASICA");
            cards.add(CardView.Card.offer(small == null ? ItemStack.EMPTY : SkyItems.create(small, 1),
                    List.of(Chip.price("coins", 25_000, com.skyrpg.economy.Purse.get(p)))));
            return new CardView("GALATEA", "Dax", "Huntraps", npc, List.of(Chip.note(new ItemStack(Items.SPRUCE_TRAPDOOR),
                    traps.size() + "/" + PER_PLAYER + " traps set", true)), cards, "Better traps, made with Tender Wood, catch faster.", 1, 36);
        }, (p, card) -> {
            List<Trap> traps = mine(p);
            if (card < traps.size()) {
                Trap trap = traps.get(card);
                if (trap.ready()) {
                    collect(p, trap);
                }
                return "";
            }
            if (card == Math.max(1, traps.size())) {
                if (!com.skyrpg.economy.Purse.trySpend(p, 25_000)) {
                    return "You need 25,000 coins.";
                }
                ItemDefinition small = ItemRegistry.skyblock("RETIA_BASICA");
                if (small != null && !p.getInventory().add(SkyItems.create(small, 1))) {
                    p.drop(SkyItems.create(small, 1), false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
                Msg.playSound(p, SoundEvents.VILLAGER_YES, 0.8f, 1.1f);
            }
            return "";
        });
    }
}
