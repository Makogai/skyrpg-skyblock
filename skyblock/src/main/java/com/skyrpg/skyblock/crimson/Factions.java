package com.skyrpg.skyblock.crimson;

import com.mojang.serialization.Codec;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * The Crimson Isle's two factions, the Mages (Queen Nyx, Scarleton) and the Barbarians (Chief Scorn, Dragontail), and
 * your reputation with each. Reputation is kept per faction, so switching sides doesn't lose it. Ranks are Hypixel's:
 * Neutral, Friendly 1,000, Trusted 3,000, Honored 7,000, Hero 12,000 (SkyBlock XP 5 / 10 / 20 / 40 on reaching them).
 *
 * <p>Elle's questline comes first ({@link #elle}): her supplies (4 Magma Chunks, 3 Blaze Ashes, 2 Spectre Dust), then
 * meeting both leaders; after that either leader takes you in.
 */
public final class Factions {
    public static final String MAGE = "mage";
    public static final String BARBARIAN = "barbarian";
    public static final List<String> RANKS = List.of("Neutral", "Friendly", "Trusted", "Honored", "Hero");
    public static final int[] RANK_REP = {0, 1_000, 3_000, 7_000, 12_000};
    private static final int[] RANK_SBXP = {0, 5, 10, 20, 40};

    /** The faction you've joined ("mage" or "barbarian"). */
    static final AttachmentType<String> FACTION = AttachmentRegistry.create(SkyRpgSkyblock.id("crimson_faction"),
            builder -> builder.persistent(Codec.STRING).copyOnDeath());
    /** Reputation with each faction. */
    static final AttachmentType<Map<String, Integer>> REPUTATION = AttachmentRegistry.create(SkyRpgSkyblock.id("crimson_reputation"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());
    /** Elle's questline and other flags: "elle" (the step), "met_mage", "met_barbarian". */
    static final AttachmentType<Map<String, Integer>> FLAGS = AttachmentRegistry.create(SkyRpgSkyblock.id("crimson_flags"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    /** Elle's steps. */
    static final int ELLE_NEW = 0;
    static final int ELLE_SUPPLIES = 1;
    static final int ELLE_LEADERS = 2;
    static final int ELLE_DONE = 3;

    private Factions() {
    }

    /** Registers the attachments at startup (before any player's saved data is read). */
    static void init() {
    }

    public static String faction(ServerPlayer player) {
        return player.getAttachedOrElse(FACTION, "");
    }

    public static String title(String faction) {
        return faction.equals(MAGE) ? "Mage" : faction.equals(BARBARIAN) ? "Barbarian" : "None";
    }

    public static String plural(String faction) {
        return faction.equals(MAGE) ? "Mages" : "Barbarians";
    }

    public static int color(String faction) {
        return faction.equals(MAGE) ? 0xAA55FF : faction.equals(BARBARIAN) ? 0xFF7733 : 0xAAAAAA;
    }

    public static String other(String faction) {
        return faction.equals(MAGE) ? BARBARIAN : MAGE;
    }

    public static String leader(String faction) {
        return faction.equals(MAGE) ? "Queen Nyx" : "Chief Scorn";
    }

    public static String town(String faction) {
        return faction.equals(MAGE) ? "Scarleton" : "Dragontail";
    }

    public static int reputation(ServerPlayer player, String faction) {
        return player.getAttachedOrElse(REPUTATION, Map.of()).getOrDefault(faction, 0);
    }

    public static int rank(int rep) {
        int rank = 0;
        while (rank + 1 < RANK_REP.length && rep >= RANK_REP[rank + 1]) {
            rank++;
        }
        return rank;
    }

    public static String rankName(int rep) {
        return RANKS.get(rank(rep));
    }

    static int flag(ServerPlayer player, String key) {
        return player.getAttachedOrElse(FLAGS, Map.of()).getOrDefault(key, 0);
    }

    static void setFlag(ServerPlayer player, String key, int value) {
        Map<String, Integer> all = new HashMap<>(player.getAttachedOrElse(FLAGS, Map.of()));
        all.put(key, value);
        player.setAttached(FLAGS, Map.copyOf(all));
    }

    static int elle(ServerPlayer player) {
        return flag(player, "elle");
    }

    /** Elle's work is done and both leaders were met: either one takes you in. */
    static boolean canJoin(ServerPlayer player) {
        return elle(player) >= ELLE_LEADERS && flag(player, "met_mage") > 0 && flag(player, "met_barbarian") > 0;
    }

    static void join(ServerPlayer player, String faction) {
        String before = faction(player);
        player.setAttached(FACTION, faction);
        if (elle(player) < ELLE_DONE) {
            setFlag(player, "elle", ELLE_DONE);
        }
        Notice.Panel panel = Notice.of(Notice.Kind.COMBAT).tag("CRIMSON ISLE").panel();
        if (before.isEmpty()) {
            panel.line(Component.literal(" Daily faction quests are open: talk to " + leader(faction) + " in " + town(faction) + ".")
                    .withStyle(ChatFormatting.GRAY));
            panel.line(Component.literal(" Elle waits at the Forgotten Skull, where Kuudra sleeps.").withStyle(ChatFormatting.GRAY));
        } else {
            panel.line(Component.literal(" Your reputation with the " + plural(before) + " stays with them.").withStyle(ChatFormatting.GRAY));
        }
        panel.send(player, Component.literal("You joined the " + plural(faction) + "!").withColor(color(faction)));
        Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
        FactionQuests.reset(player);
    }

    /** Gives reputation with your faction (nothing without one); rank-ups are announced with their SkyBlock XP. */
    public static void give(ServerPlayer player, int amount, String reason) {
        String faction = faction(player);
        if (faction.isEmpty() || amount <= 0) {
            return;
        }
        Map<String, Integer> all = new HashMap<>(player.getAttachedOrElse(REPUTATION, Map.of()));
        int before = all.getOrDefault(faction, 0);
        int after = before + amount;
        all.put(faction, after);
        player.setAttached(REPUTATION, Map.copyOf(all));
        player.sendSystemMessage(Component.literal("+" + Msg.number(amount) + " " + title(faction) + " Reputation")
                .withColor(color(faction)).append(Component.literal(" (" + reason + ")").withStyle(ChatFormatting.GRAY)));
        for (int rank = rank(before) + 1; rank <= rank(after); rank++) {
            SkyBlockXp.give(player, RANK_SBXP[rank], title(faction) + " reputation rank");
            Notice.of(Notice.Kind.COMBAT).tag("REPUTATION").panel()
                    .line(Component.literal(" +" + RANK_SBXP[rank] + " SkyBlock XP").withStyle(ChatFormatting.AQUA))
                    .send(player, Component.literal(title(faction) + " rank: " + RANKS.get(rank - 1) + " ➜ " + RANKS.get(rank)).withColor(color(faction)));
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    /** Admin: sets reputation with a faction. */
    static void set(ServerPlayer player, String faction, int amount) {
        Map<String, Integer> all = new HashMap<>(player.getAttachedOrElse(REPUTATION, Map.of()));
        all.put(faction, Math.max(0, amount));
        player.setAttached(REPUTATION, Map.copyOf(all));
    }
}
