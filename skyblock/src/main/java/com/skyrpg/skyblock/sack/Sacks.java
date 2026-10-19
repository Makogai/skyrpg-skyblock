package com.skyrpg.skyblock.sack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.progress.CollectionLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.recipe.Recipes;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Sacks: right-click a sack (Small Mining Sack...) to put it in your Sack of Sacks (its slots come with the
 * Tropical Fish collection); from then on what you gather that its type holds goes straight into it, up to its size per
 * item ("[Sacks] +128 items" every few seconds). Sacks of one type add their sizes together; a Pocket Sack-in-a-Sack (at
 * the anvil) makes one hold three times as much, up to three of them (seven times in all). {@code /sacks} shows them and
 * takes items out; {@code /gfs <item> <amount>} gets items from them; {@code /sackofsacks} manages the sacks themselves.
 * The recipe book crafts from them too. 34 types from NEU, the Rune Sack and the Gold and Diamond Trophy Fishing Sacks
 * ({@code skyrpg/sacks}, {@code tools/skyblock/import_sacks.py}). Per profile.
 */
public final class Sacks {
    /** A sack type: what it holds and how much each of its sacks holds per item. */
    public record Type(String name, String icon, List<String> items, Map<String, Long> sizes) {
    }

    /** One sack in the Sack of Sacks. */
    public record Owned(String id, int pockets) {
    }

    /** Server to client: what your sacks hold, so the recipe book can count it (sent a few times a second while it changes). */
    public record Contents(Map<String, Long> items) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Contents> TYPE = new CustomPacketPayload.Type<>(SkyRpgSkyblock.id("sack_contents"));
        public static final StreamCodec<ByteBuf, Contents> CODEC = ByteBufCodecs.<ByteBuf, String, Long, HashMap<String, Long>>map(HashMap::new,
                ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_LONG).map(map -> new Contents(Map.copyOf(map)), contents -> new HashMap<>(contents.items()));

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** What's in your sacks (item id -> count). */
    public static final AttachmentType<Map<String, Long>> CONTENTS = AttachmentRegistry.create(SkyRpgSkyblock.id("sacks"),
            builder -> builder.initializer(Map::of).persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());
    /** The sacks in your Sack of Sacks: an id, or {@code ID:n} for a sack with n Pocket Sack-in-a-Sacks on it. */
    public static final AttachmentType<List<String>> OWNED = AttachmentRegistry.create(SkyRpgSkyblock.id("sacks_owned"),
            builder -> builder.initializer(List::of).persistent(Codec.STRING.listOf()).copyOnDeath());

    public static final String POCKET = "POCKET_SACK_IN_A_SACK";
    /** The custom-data key of a sack item's Pocket Sack-in-a-Sacks. */
    public static final String POCKETS = "sack_pockets";
    public static final int MAX_POCKETS = 3;
    /** Sack of Sacks slots by Tropical Fish collection tier (none below IV). */
    private static final int[] SLOTS = {0, 0, 0, 0, 8, 12, 16, 20, 24, 29};

    private static volatile Map<String, Long> clientContents = Map.of();
    /** Players whose contents changed since the last {@link Contents} was sent. */
    private static final Set<UUID> DIRTY = new HashSet<>();
    private static List<Type> types = List.of();
    /** Sack item id -> its type. */
    private static Map<String, Type> bySack = Map.of();
    /** Item id -> the types that hold it. */
    private static Map<String, List<Type>> byItem = Map.of();
    /** Items gone into sacks lately, for the "[Sacks] +N items" message. */
    private static final Map<UUID, Long> RECENT = new HashMap<>();

    private Sacks() {
    }

    public static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("sacks"), "skyrpg/sacks", Sacks::load);
        SacksMenu.init();
        SackOfSacks.init();
        PayloadTypeRegistry.clientboundPlay().register(Contents.TYPE, Contents.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> DIRTY.add(handler.player.getUUID()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> DIRTY.remove(handler.player.getUUID()));
        // Before the Hub's protection (a sack can look like a block item): adding a sack works anywhere.
        Identifier early = SkyRpgSkyblock.id("sacks_early");
        UseItemCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(early, (player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            String id = SkyItems.skyblockId(held);
            if (id != null && level.isClientSide()) {
                return looksLikeSack(held, id) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            if (id == null || !bySack.containsKey(id)) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer server) {
                add(server, held, id);
            }
            return InteractionResult.SUCCESS;
        });
        // Right-clicking with it while looking at a block counts too (the Hub's protection would refuse the block use).
        UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(early, (player, level, hand, hit) -> {
            ItemStack held = player.getItemInHand(hand);
            String id = SkyItems.skyblockId(held);
            if (id != null && level.isClientSide()) {
                return looksLikeSack(held, id) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            if (id == null || !bySack.containsKey(id)) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer server) {
                add(server, held, id);
            }
            return InteractionResult.SUCCESS;
        });
        ItemGains.GAINED.register((player, stack, source) -> {
            if (source != ItemGains.Source.OTHER && SkyBlockWorlds.isSkyBlock(player.level())) {
                store(player, stack);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Sacks::tick);
        ServerTickEvents.END_SERVER_TICK.register(Sacks::sync);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal("sacks").executes(ctx -> {
                SacksMenu.open(ctx.getSource().getPlayerOrException());
                return 1;
            }));
            dispatcher.register(Commands.literal("gfs").then(Commands.argument("item", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                            ctx.getSource().getPlayer() == null ? List.of() : contents(ctx.getSource().getPlayer()).keySet().stream()
                                    .map(id -> id.toLowerCase(Locale.ROOT)).toList(), builder))
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 2304)).executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String id = StringArgumentType.getString(ctx, "item").toUpperCase(Locale.ROOT);
                        int got = take(player, id, IntegerArgumentType.getInteger(ctx, "amount"));
                        if (got <= 0) {
                            player.sendSystemMessage(Component.literal("You don't have any of that in your sacks!").withStyle(ChatFormatting.RED));
                        }
                        return got;
                    }))));
        });
    }

    /**
     * On the client, which has no sack data: whether this looks like a sack. It must answer SUCCESS for one so the click
     * reaches the server; otherwise the Hub's protection refuses a head (a block item) on the client and nothing's sent.
     */
    private static boolean looksLikeSack(ItemStack held, String id) {
        ItemDefinition def = SkyItems.definition(held);
        return id.equals("TRICK_OR_TREAT_BAG") || def != null && def.json() != null && def.json().has("category")
                && def.json().get("category").getAsString().equals("SACK");
    }

    private static void load(Map<Identifier, JsonElement> files) {
        List<Type> loaded = new ArrayList<>();
        for (JsonElement file : files.values()) {
            for (JsonElement element : file.getAsJsonObject().getAsJsonArray("sacks")) {
                JsonObject s = element.getAsJsonObject();
                List<String> items = new ArrayList<>();
                s.getAsJsonArray("items").forEach(e -> items.add(e.getAsString()));
                Map<String, Long> sizes = new LinkedHashMap<>();
                s.getAsJsonObject("sizes").entrySet().forEach(e -> sizes.put(e.getKey(), e.getValue().getAsLong()));
                loaded.add(new Type(s.get("type").getAsString(), s.get("icon").getAsString(), List.copyOf(items), Map.copyOf(sizes)));
            }
        }
        Map<String, Type> sacks = new HashMap<>();
        Map<String, List<Type>> items = new HashMap<>();
        for (Type type : loaded) {
            type.sizes().keySet().forEach(id -> sacks.put(id, type));
            type.items().forEach(id -> items.computeIfAbsent(id, k -> new ArrayList<>()).add(type));
        }
        types = List.copyOf(loaded);
        bySack = sacks;
        byItem = items;
        SkyRpgSkyblock.LOGGER.info("Loaded {} sack types", loaded.size());
    }

    public static List<Type> types() {
        return types;
    }

    /** What the sacks hold: the real thing on the server, what the server last sent on the client. */
    public static Map<String, Long> contents(Player player) {
        return player.level().isClientSide() ? clientContents : player.getAttachedOrElse(CONTENTS, Map.of());
    }

    /** Client side: what the server says the sacks hold. */
    public static void apply(Contents contents) {
        clientContents = contents.items();
    }

    private static void setContents(ServerPlayer player, Map<String, Long> now) {
        player.setAttached(CONTENTS, Map.copyOf(now));
        DIRTY.add(player.getUUID());
    }

    /** The sacks in the Sack of Sacks, in the order they went in. */
    public static List<Owned> owned(Player player) {
        List<Owned> out = new ArrayList<>();
        for (String entry : player.getAttachedOrElse(OWNED, List.of())) {
            int colon = entry.indexOf(':');
            out.add(colon < 0 ? new Owned(entry, 0) : new Owned(entry.substring(0, colon), Integer.parseInt(entry.substring(colon + 1))));
        }
        return out;
    }

    private static void setOwned(ServerPlayer player, List<Owned> owned) {
        player.setAttached(OWNED, owned.stream().map(o -> o.pockets() > 0 ? o.id() + ":" + o.pockets() : o.id()).toList());
    }

    /** How many sacks the Sack of Sacks holds: by the Tropical Fish collection tier (8 at IV up to 29 at IX). */
    public static int slots(Player player) {
        int tier = CollectionLevels.tier(player, Recipes.collectionId("RAW_FISH:2"));
        return SLOTS[Math.max(0, Math.min(tier, SLOTS.length - 1))];
    }

    /** How many Pocket Sack-in-a-Sacks a sack item has on it. */
    public static int pockets(ItemStack sack) {
        CompoundTag tag = ItemMods.tag(sack);
        return tag == null ? 0 : tag.getIntOr(POCKETS, 0);
    }

    /** How much one sack holds of each item: its size, plus twice that for each Pocket Sack-in-a-Sack on it. */
    public static long sizeOf(Owned owned) {
        Type type = bySack.get(owned.id());
        return type == null ? 0 : type.sizes().getOrDefault(owned.id(), 0L) * (1 + 2L * owned.pockets());
    }

    /** Whether this item is a sack that can go in the Sack of Sacks. */
    public static boolean isSack(String id) {
        return id != null && bySack.containsKey(id);
    }

    /** How many of a type's items this profile's sacks hold each: its sacks of that type added together (0 = none). */
    public static long size(Player player, Type type) {
        long size = 0;
        for (Owned owned : owned(player)) {
            if (bySack.get(owned.id()) == type) {
                size += sizeOf(owned);
            }
        }
        return size;
    }

    /** How many of an item your sacks can hold in all (sacks of different types don't add up: the biggest type counts). */
    public static long capacity(Player player, String item) {
        long size = 0;
        for (Type type : byItem.getOrDefault(item, List.of())) {
            size = Math.max(size, size(player, type));
        }
        return size;
    }

    private static void add(ServerPlayer player, ItemStack held, String id) {
        Type type = bySack.get(id);
        List<Owned> owned = new ArrayList<>(owned(player));
        if (!player.isCreative() && owned.size() >= slots(player)) {
            player.sendSystemMessage(Component.literal(slots(player) == 0 ? "Your Sack of Sacks unlocks at Tropical Fish IV!"
                    : "Your Sack of Sacks is full! Take a sack out of it first (/sackofsacks).").withStyle(ChatFormatting.RED));
            return;
        }
        owned.add(new Owned(id, pockets(held)));
        setOwned(player, owned);
        held.shrink(1);
        ItemDefinition def = ItemRegistry.skyblock(id);
        player.sendSystemMessage(Component.literal("You added your ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(def == null ? id : def.name()).withColor(def == null ? 0xFFFFFF : def.rarity().color()))
                .append(Component.literal(" to your Sack of Sacks! Your " + type.name() + " Sacks hold " + Msg.number(size(player, type)) + " of each item.")
                        .withStyle(ChatFormatting.GREEN)));
        Msg.playSound(player, SoundEvents.BUNDLE_INSERT, 1.0f, 1.0f);
        // What you're carrying that it holds goes in now.
        insertInventory(player);
    }

    /** Takes the sack at {@code index} out of the Sack of Sacks into the inventory (what's in your sacks stays there). */
    static void remove(ServerPlayer player, int index) {
        List<Owned> owned = new ArrayList<>(owned(player));
        if (index < 0 || index >= owned.size()) {
            return;
        }
        Owned out = owned.remove(index);
        ItemDefinition def = ItemRegistry.skyblock(out.id());
        if (def == null) {
            return;
        }
        setOwned(player, owned);
        ItemStack stack = SkyItems.create(def, 1);
        if (out.pockets() > 0) {
            ItemMods.edit(stack, tag -> tag.putInt(POCKETS, out.pockets()));
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, Prediction.SERVER_ONLY);
        }
        Msg.playSound(player, SoundEvents.BUNDLE_REMOVE_ONE, 1.0f, 1.0f);
    }

    /** Moves what a sack holds out of a gathered stack (now in the inventory) into the sacks, up to their size. */
    private static void store(ServerPlayer player, ItemStack gained) {
        String id = SkyStacks.id(gained);
        if (id == null || !byItem.containsKey(id)) {
            return;
        }
        ItemDefinition def = SkyItems.definition(gained);
        if (!material(def)) {
            return;
        }
        long room = capacity(player, id) - contents(player).getOrDefault(id, 0L);
        int move = (int) Math.min(room, gained.getCount());
        if (move <= 0 || !SkyStacks.take(player.getInventory(), id, move)) {
            return;
        }
        put(player, id, move);
        RECENT.merge(player.getUUID(), (long) move, Long::sum);
    }

    /** A material (no stats, no ability), the kind sacks hold; gear never goes in. */
    private static boolean material(ItemDefinition def) {
        return def == null || def.stats().isEmpty() && def.ability() == null;
    }

    private static void put(ServerPlayer player, String id, long amount) {
        Map<String, Long> now = new HashMap<>(contents(player));
        now.merge(id, amount, Long::sum);
        setContents(player, now);
    }

    /** Takes up to {@code amount} of an item out of the sacks into the inventory; how many it took. */
    public static int take(ServerPlayer player, String id, int amount) {
        long have = contents(player).getOrDefault(id, 0L);
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (have <= 0 || def == null) {
            return 0;
        }
        int want = (int) Math.min(have, amount);
        int given = 0;
        while (given < want) {
            ItemStack stack = SkyItems.create(def, Math.min(64, want - given));
            int size = stack.getCount();
            player.getInventory().add(stack);
            int moved = size - stack.getCount();
            given += moved;
            if (!stack.isEmpty()) {
                break;
            }
        }
        if (given > 0) {
            Map<String, Long> now = new HashMap<>(contents(player));
            long left = have - given;
            if (left <= 0) {
                now.remove(id);
            } else {
                now.put(id, left);
            }
            setContents(player, now);
        }
        return given;
    }

    /** How many of an item the player has in the inventory and the sacks together. */
    public static long countAnywhere(Player player, String id) {
        return SkyStacks.count(player.getInventory(), id) + contents(player).getOrDefault(id, 0L);
    }

    /** Takes {@code amount} from the inventory first, then the sacks (NPC trades, costs and crafting take from both, as on Hypixel). */
    public static boolean takeAnywhere(ServerPlayer player, String id, int amount) {
        if (countAnywhere(player, id) < amount) {
            return false;
        }
        int fromInventory = Math.min(amount, SkyStacks.count(player.getInventory(), id));
        if (fromInventory > 0) {
            SkyStacks.take(player.getInventory(), id, fromInventory);
        }
        int rest = amount - fromInventory;
        if (rest > 0) {
            Map<String, Long> now = new HashMap<>(contents(player));
            long left = now.getOrDefault(id, 0L) - rest;
            if (left <= 0) {
                now.remove(id);
            } else {
                now.put(id, left);
            }
            setContents(player, now);
        }
        return true;
    }

    /** Puts everything in the inventory that the sacks hold into them. */
    public static int insertInventory(ServerPlayer player) {
        int moved = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            String id = SkyStacks.id(stack);
            if (id == null || !byItem.containsKey(id)) {
                continue;
            }
            ItemDefinition def = SkyItems.definition(stack);
            if (!material(def)) {
                continue;
            }
            long room = capacity(player, id) - contents(player).getOrDefault(id, 0L);
            int move = (int) Math.min(room, stack.getCount());
            if (move > 0) {
                stack.shrink(move);
                put(player, id, move);
                moved += move;
            }
        }
        if (moved > 0) {
            player.getInventory().setChanged();
            player.sendSystemMessage(Component.literal("[Sacks] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("+" + Msg.number(moved) + " items put into your sacks.").withStyle(ChatFormatting.GRAY)));
        }
        return moved;
    }

    /** Sends the sacks' contents to whoever's changed (a few times a second at most). */
    private static void sync(MinecraftServer server) {
        if (DIRTY.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }
        for (UUID uuid : DIRTY) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && ServerPlayNetworking.canSend(player, Contents.TYPE)) {
                ServerPlayNetworking.send(player, new Contents(contents(player)));
            }
        }
        DIRTY.clear();
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 100 != 0 || RECENT.isEmpty()) {
            return;
        }
        RECENT.forEach((uuid, amount) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(Component.literal("[Sacks] ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("+" + Msg.number(amount) + " item" + (amount == 1 ? "" : "s") + ".").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" (Last 5s.)").withStyle(ChatFormatting.DARK_GRAY)));
            }
        });
        RECENT.clear();
    }
}
