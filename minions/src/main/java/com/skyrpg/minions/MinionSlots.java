package com.skyrpg.minions;

import com.mojang.serialization.Codec;
import com.skyrpg.skyblock.recipe.Crafting;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * How many minions a profile can place: 5 to start, more for every few unique minions crafted (each type and tier
 * counts once, crafted or upgraded in the minion's screen), Hypixel's table up to 26. The unique minions are saved
 * with the player, so each profile has its own.
 */
public final class MinionSlots {
    /** Unique minions needed for each placeable count from 5 up: 0 for 5, 5 for 6, 15 for 7... 650 for 26. */
    private static final int[] NEEDED = {0, 5, 15, 30, 50, 75, 100, 125, 150, 175, 200, 225, 250, 275, 300, 350, 400, 450, 500, 550, 600, 650};
    public static final int BASE = 5;

    /** The minion items ("COBBLESTONE_GENERATOR_3") this player has made. */
    static final AttachmentType<List<String>> CRAFTED = AttachmentRegistry.create(SkyRpgMinions.id("unique_minions"),
            builder -> builder.initializer(List::of).persistent(Codec.STRING.listOf()).copyOnDeath());

    private MinionSlots() {
    }

    static void init() {
        CRAFTED.identifier();
        Crafting.CRAFTED.register((player, id, count) -> {
            if (Minions.fromItem(id) != null) {
                record(player, id);
            }
        });
    }

    public static int unique(ServerPlayer player) {
        return player.getAttachedOrCreate(CRAFTED).size();
    }

    /** How many minions this player can have placed. */
    public static int slots(ServerPlayer player) {
        return slotsFor(unique(player));
    }

    static int slotsFor(int unique) {
        int slots = BASE;
        for (int i = 1; i < NEEDED.length && unique >= NEEDED[i]; i++) {
            slots = BASE + i;
        }
        return slots;
    }

    /** Unique minions still to craft for the next slot, or 0 at the most there is. */
    public static int toNext(ServerPlayer player) {
        int unique = unique(player);
        for (int needed : NEEDED) {
            if (needed > unique) {
                return needed - unique;
            }
        }
        return 0;
    }

    /** The player made this minion item: a new unique one counts, and may open a slot. */
    static void record(ServerPlayer player, String minionItem) {
        List<String> crafted = player.getAttachedOrCreate(CRAFTED);
        if (crafted.contains(minionItem)) {
            return;
        }
        int before = slotsFor(crafted.size());
        List<String> updated = new ArrayList<>(crafted);
        updated.add(minionItem);
        player.setAttached(CRAFTED, List.copyOf(updated));
        Minions.Placed placed = Minions.fromItem(minionItem);
        String name = placed == null ? minionItem : placed.type().title(placed.tier());
        player.sendSystemMessage(Component.literal("NEW UNIQUE MINION! ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (" + updated.size() + " crafted)").withStyle(ChatFormatting.DARK_GRAY)));
        int after = slotsFor(updated.size());
        if (after > before) {
            player.sendSystemMessage(Component.literal("You can now place " + after + " minions on your island!").withStyle(ChatFormatting.GREEN));
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.2f);
        }
    }
}
