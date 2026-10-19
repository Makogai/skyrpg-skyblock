package com.skyrpg.skyblock.museum;

import com.mojang.serialization.Codec;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Museum Milestones: every 100 SkyBlock XP the Museum has paid is one milestone (40 in all, 4,000 XP), and each
 * gives +1% Bits Multiplier and +2% Bank interest, with bonus rewards at some (Bank upgrades, a Travel Scroll, the Hex,
 * Dust the Fish...). The XP is what donating pays ({@link Museum#xp}); a borrowed item still counts. What can be handed
 * over is: the items (claimed at the Curator, once); the Bank interest is the Bank's own (a hook); the rest (Bank
 * upgrade tiers, the Hex, Shen's, emblems, the Flower Minion XII recipe, the Bits Multiplier) are listed as unlocked
 * for the systems that aren't in yet.
 */
public final class MuseumMilestones {
    /** A bonus reward: its name and colour, and the SkyBlock item it gives when there is one (claimed once). */
    public record Reward(String name, int color, String item, int count) {
        static Reward unlock(String name, int color) {
            return new Reward(name, color, "", 0);
        }

        static Reward item(String id, int count) {
            ItemDefinition def = ItemRegistry.skyblock(id);
            return new Reward(def == null ? id : def.name(), def == null ? 0xFFFFFF : def.rarity().color(), id, count);
        }
    }

    public record Milestone(int number, int xp, List<Reward> bonus) {
    }

    public static final int XP_PER = 100;
    public static final int MAX = 40;
    /** Each milestone: +1% Bits Multiplier. */
    public static final int BITS_PER = 1;
    /** Each milestone: +2% Bank interest. */
    public static final int BANK_PER = 2;

    /** The milestones you've claimed the items of. */
    public static final AttachmentType<List<Integer>> CLAIMED = AttachmentRegistry.create(SkyRpgSkyblock.id("museum_claimed"),
            builder -> builder.initializer(List::of).persistent(Codec.INT.listOf()).copyOnDeath());

    private MuseumMilestones() {
    }

    /** The bonus rewards of a milestone (1 to 40), from Hypixel's list. */
    public static List<Reward> bonus(int n) {
        return switch (n) {
            case 3 -> List.of(Reward.unlock("Deluxe Bank Upgrade", 0xFF55FF), Reward.unlock("Museum Appraisal Service", 0xFFFFFF));
            case 5 -> List.of(Reward.item("MUSEUM_TRAVEL_SCROLL", 1));
            case 6 -> List.of(Reward.unlock("Super Deluxe Bank Upgrade", 0xAA00AA));
            case 8 -> List.of(Reward.unlock("The Hex", 0xFF55FF));
            case 10 -> List.of(Reward.item("DUST_THE_FISH", 1), Reward.unlock("Premier Bank Upgrades", 0xFF5555),
                    Reward.unlock("Shen's Auction", 0x5555FF), Reward.unlock("Flower Minion XII Recipe", 0xFFFFFF));
            case 12 -> List.of(Reward.unlock("Collecting Top Hat Emblem", 0x55FF55));
            case 14 -> List.of(Reward.unlock("Luxurious Bank Upgrade", 0x00AAAA));
            case 18 -> List.of(Reward.unlock("Palatial Bank Upgrade", 0xAA0000));
            case 20 -> List.of(Reward.item("EON_THE_FISH", 1), Reward.unlock("Shen's Special", 0x55FFFF));
            case 24 -> List.of(Reward.unlock("Golden Collecting Top Hat Emblem", 0x55FF55));
            default -> List.of();
        };
    }

    public static Milestone get(int n) {
        return new Milestone(n, n * XP_PER, bonus(n));
    }

    /** How many milestones the player has reached. */
    public static int reached(ServerPlayer player) {
        return reached(Museum.xp(player));
    }

    public static int reached(int xp) {
        return Math.min(MAX, xp / XP_PER);
    }

    /** The Bits Multiplier (percent) the milestones give; there's no Bits system to use it yet. */
    public static int bitsMultiplier(ServerPlayer player) {
        return reached(player) * BITS_PER;
    }

    /** The Bank's extra interest as a fraction (0.24 = 24% more). */
    public static double bankBonus(ServerPlayer player) {
        return reached(player) * BANK_PER / 100.0;
    }

    /** Whether the player has reached the milestone that gives a bonus reward by name (the Hex, Shen's Auction...). */
    public static boolean unlocked(ServerPlayer player, String reward) {
        int reached = reached(player);
        for (int n = 1; n <= reached; n++) {
            if (bonus(n).stream().anyMatch(r -> r.name().equals(reward))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the milestone has items that you haven't claimed. */
    public static boolean claimable(ServerPlayer player, int n) {
        return n <= reached(player) && bonus(n).stream().anyMatch(r -> !r.item().isEmpty()) && !player.getAttachedOrElse(CLAIMED, List.of()).contains(n);
    }

    public static int claimableCount(ServerPlayer player) {
        int count = 0;
        for (int n = 1; n <= reached(player); n++) {
            if (claimable(player, n)) {
                count++;
            }
        }
        return count;
    }

    /** Gives the items of one milestone (or all of them with 0): how many milestones it paid out. */
    public static int claim(ServerPlayer player, int only) {
        List<Integer> claimed = new ArrayList<>(player.getAttachedOrElse(CLAIMED, List.of()));
        int paid = 0;
        for (int n = 1; n <= MAX; n++) {
            if ((only != 0 && n != only) || !claimable(player, n)) {
                continue;
            }
            for (Reward reward : bonus(n)) {
                ItemDefinition def = ItemRegistry.skyblock(reward.item());
                if (def == null) {
                    continue;
                }
                ItemStack stack = SkyItems.create(def, reward.count());
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false, Prediction.SERVER_ONLY);
                }
                player.sendSystemMessage(Component.literal("You claimed ").withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(def.name()).withColor(def.rarity().color()))
                        .append(Component.literal(" for Museum Milestone " + n + "!").withStyle(ChatFormatting.GREEN)));
            }
            claimed.add(n);
            paid++;
        }
        if (paid > 0) {
            player.setAttached(CLAIMED, List.copyOf(claimed));
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.2f);
        }
        return paid;
    }
}
