package com.skyrpg.skyblock.hollows;

import com.mojang.serialization.Codec;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * What a player has done in this Crystal Hollows: the crystals they've claimed and placed, the parts they've handed in, the
 * King's Scent, the temple key, Divan's treasure hunt. Hypixel's lobbies are new each time, so everything here is
 * forgotten when the Hollows are made anew ({@link HollowsReset}), except the {@code total.} counters (Nucleus runs).
 */
public final class HollowsState {
    static final AttachmentType<Map<String, Long>> DATA = AttachmentRegistry.create(SkyRpgSkyblock.id("hollows_progress"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());
    /** When this Hollows was generated (set by {@link HollowsReset} before the worlds load). */
    static long epoch;

    private HollowsState() {
    }

    private static Map<String, Long> data(ServerPlayer player) {
        Map<String, Long> map = player.getAttachedOrElse(DATA, Map.of());
        if (map.getOrDefault("epoch", 0L) == epoch) {
            return map;
        }
        Map<String, Long> fresh = new HashMap<>();
        map.forEach((key, value) -> {
            if (key.startsWith("total.")) {
                fresh.put(key, value);
            }
        });
        fresh.put("epoch", epoch);
        player.setAttached(DATA, Map.copyOf(fresh));
        return fresh;
    }

    public static long get(ServerPlayer player, String key) {
        return data(player).getOrDefault(key, 0L);
    }

    public static boolean has(ServerPlayer player, String key) {
        return get(player, key) != 0;
    }

    public static void set(ServerPlayer player, String key, long value) {
        Map<String, Long> map = new HashMap<>(data(player));
        if (value == 0) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
        player.setAttached(DATA, Map.copyOf(map));
    }

    /** Forgets this run's keys that start with any of the prefixes. */
    static void clearRun(ServerPlayer player, String... prefixes) {
        Map<String, Long> map = new HashMap<>(data(player));
        map.keySet().removeIf(key -> {
            for (String prefix : prefixes) {
                if (key.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        });
        player.setAttached(DATA, Map.copyOf(map));
    }

    /** A SkyBlock item as a stack (empty if there is no such item). */
    public static ItemStack stack(String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? ItemStack.EMPTY : SkyItems.create(def, count);
    }

    /** Into the inventory (what doesn't fit drops at their feet). */
    public static void give(ServerPlayer player, ItemStack stack, ItemGains.Source source) {
        int left = stack.getCount();
        while (left > 0 && !stack.isEmpty()) {
            ItemStack part = stack.copyWithCount(Math.min(stack.getMaxStackSize(), left));
            left -= part.getCount();
            ItemGains.give(player, part, source);
            if (!part.isEmpty()) {
                player.drop(part, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
    }

    public static void give(ServerPlayer player, String id, int count, ItemGains.Source source) {
        give(player, stack(id, count), source);
    }

    public static int count(ServerPlayer player, String id) {
        return SkyStacks.count(player.getInventory(), id);
    }

    public static boolean take(ServerPlayer player, String id, int count) {
        return SkyStacks.take(player.getInventory(), id, count);
    }
}
