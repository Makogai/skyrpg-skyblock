package com.skyrpg.skyblock.sack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Sacks: right-click a sack (Small Mining Sack...) to add it to your sacks; from then on what you gather that
 * its type holds goes straight into it, up to its size per item ("[Sacks] +128 items" every few seconds). {@code /sacks}
 * shows them and takes items out; {@code /gfs <item> <amount>} gets items from them. 28 types from NEU
 * ({@code skyrpg/sacks}, {@code tools/skyblock/import_sacks.py}). Per profile.
 */
public final class Sacks {
    /** A sack type: what it holds and how much each of its sacks holds per item. */
    public record Type(String name, String icon, List<String> items, Map<String, Long> sizes) {
    }

    /** What's in your sacks (item id -> count). */
    public static final AttachmentType<Map<String, Long>> CONTENTS = AttachmentRegistry.create(SkyRpgSkyblock.id("sacks"),
            builder -> builder.initializer(Map::of).persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());
    /** The sack items you've added. */
    public static final AttachmentType<List<String>> OWNED = AttachmentRegistry.create(SkyRpgSkyblock.id("sacks_owned"),
            builder -> builder.initializer(List::of).persistent(Codec.STRING.listOf()).copyOnDeath());

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
        // Before the Hub's protection (a sack can look like a block item): adding a sack works anywhere.
        Identifier early = SkyRpgSkyblock.id("sacks_early");
        UseItemCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(early, (player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            String id = SkyItems.skyblockId(held);
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

    public static Map<String, Long> contents(ServerPlayer player) {
        return player.getAttachedOrElse(CONTENTS, Map.of());
    }

    /** How many of a type's items this profile's sacks hold each (its biggest sack of that type; 0 = none). */
    public static long size(ServerPlayer player, Type type) {
        long size = 0;
        for (String owned : player.getAttachedOrElse(OWNED, List.of())) {
            size = Math.max(size, type.sizes().getOrDefault(owned, 0L));
        }
        return size;
    }

    /** How many of an item your sacks can hold in all. */
    public static long capacity(ServerPlayer player, String item) {
        long size = 0;
        for (Type type : byItem.getOrDefault(item, List.of())) {
            size = Math.max(size, size(player, type));
        }
        return size;
    }

    private static void add(ServerPlayer player, ItemStack held, String id) {
        Type type = bySack.get(id);
        List<String> owned = new ArrayList<>(player.getAttachedOrElse(OWNED, List.of()));
        long size = type.sizes().get(id);
        if (size <= size(player, type)) {
            player.sendSystemMessage(Component.literal("You already have a " + type.name() + " Sack this big or bigger!").withStyle(ChatFormatting.RED));
            return;
        }
        owned.add(id);
        player.setAttached(OWNED, List.copyOf(owned));
        held.shrink(1);
        ItemDefinition def = ItemRegistry.skyblock(id);
        player.sendSystemMessage(Component.literal("You added your ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(def == null ? id : def.name()).withColor(def == null ? 0xFFFFFF : def.rarity().color()))
                .append(Component.literal(" to your Sacks! It holds " + Msg.number(size) + " of each item.").withStyle(ChatFormatting.GREEN)));
        Msg.playSound(player, SoundEvents.BUNDLE_INSERT, 1.0f, 1.0f);
        // What you're carrying that it holds goes in now.
        insertInventory(player);
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
        player.setAttached(CONTENTS, Map.copyOf(now));
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
            player.setAttached(CONTENTS, Map.copyOf(now));
        }
        return given;
    }

    /** How many of an item the player has in the inventory and the sacks together. */
    public static long countAnywhere(ServerPlayer player, String id) {
        return SkyStacks.count(player.getInventory(), id) + contents(player).getOrDefault(id, 0L);
    }

    /** Takes {@code amount} from the inventory first, then the sacks (NPC trades and costs take from both, as on Hypixel). */
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
            player.setAttached(CONTENTS, Map.copyOf(now));
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
