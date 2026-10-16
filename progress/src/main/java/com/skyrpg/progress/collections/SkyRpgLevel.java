package com.skyrpg.progress.collections;

import com.mojang.serialization.Codec;
import com.skyrpg.core.Features;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.event.BossKills;
import com.skyrpg.core.progress.SkyBlockLevels;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.progress.SkyRpgProgress;
import com.skyrpg.vfx.Vfx;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The SkyBlock Level: every 100 SkyBlock XP is a level, without a cap. XP comes from quests, collection tiers (4 each), skill
 * levels (5 each), slayer levels (10 each), first dungeon clears (20 to 60) and first boss kills (15). The XP lives in an
 * attachment synced to every client (chat badge, Profile page); the chat mod reads it by id ({@code skyrpg_progress:level}).
 * Every 10th level pays coins and plays the level-up effect.
 */
public final class SkyRpgLevel {
    public static final int XP_PER_LEVEL = 100;
    public static final int SKILL_LEVEL_XP = 5;
    public static final int SLAYER_LEVEL_XP = 10;
    public static final int BOSS_FIRST_KILL_XP = 15;
    private static final Identifier LEVEL_UP_EFFECT = Identifier.fromNamespaceAndPath("skyrpg_vfx", "level_up");

    /** Total SkyBlock XP; synced to everyone. */
    public static final AttachmentType<Integer> XP = AttachmentRegistry.create(SkyRpgProgress.id("level"), builder -> builder
            .persistent(Codec.INT)
            .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all())
            .initializer(() -> 0)
            .copyOnDeath());

    /** Bosses this player has already been rewarded a first kill for (ids as strings). */
    private static final AttachmentType<Set<String>> FIRSTS = AttachmentRegistry.create(SkyRpgProgress.id("first_kills"), builder -> builder
            .persistent(Codec.STRING.listOf().xmap(list -> (Set<String>) new HashSet<>(list), set -> java.util.List.copyOf(set)))
            .initializer(HashSet::new)
            .copyOnDeath());

    private SkyRpgLevel() {
    }

    /** The bosses this player has been rewarded a first kill for (ids as strings); the titles seed their kill counts from it. */
    public static Set<String> firstKills(ServerPlayer player) {
        return player.getAttachedOrElse(FIRSTS, Set.of());
    }

    static void init() {
        SkyBlockXp.install(SkyRpgLevel::addXp);
        SkyBlockXp.installXp(SkyRpgLevel::xp);
        // SkyBlock Levels give +5 Health a level and +1 Strength every 5 levels (Hypixel).
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "SkyBlock Level";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                if (Features.on(Features.SURVIVAL_LEVEL)) {
                    return;
                }
                int level = level(player);
                StatType health = StatTypes.get("health");
                StatType strength = StatTypes.get("strength");
                if (health != null && level > 0) {
                    out.add(health, SkyBlockLevels.health(level));
                }
                if (strength != null && SkyBlockLevels.strength(level) > 0) {
                    out.add(strength, SkyBlockLevels.strength(level));
                }
            }
        });
        SkillLevels.LEVEL_UP.register((player, skill, oldLevel, newLevel) -> {
            int gained = newLevel - oldLevel;
            if (gained <= 0 || !Features.on(Features.SURVIVAL_LEVEL)) {
                return; // SkyBlock's skills pay their own SkyBlock XP
            }
            boolean slayer = skill.getNamespace().equals("skyrpg_combat") && skill.getPath().startsWith("slayer/");
            addXp(player, gained * (slayer ? SLAYER_LEVEL_XP : SKILL_LEVEL_XP), slayer ? "slayer level" : "skill level");
        });
        BossKills.KILLED.register((player, boss, level, kind) -> {
            Set<String> done = player.getAttachedOrCreate(FIRSTS);
            if (!done.add(boss.toString())) {
                return;
            }
            if (kind == BossKills.Kind.DUNGEON_CHAMPION) {
                addXp(player, dungeonClearXp(level), "dungeon first clear");
            } else {
                addXp(player, BOSS_FIRST_KILL_XP, "first boss kill");
            }
        });
    }

    /** 20 to 60 XP by how tough the dungeon's champion is. */
    static int dungeonClearXp(int bossLevel) {
        if (bossLevel < 25) {
            return 20;
        }
        if (bossLevel < 40) {
            return 30;
        }
        if (bossLevel < 52) {
            return 40;
        }
        if (bossLevel < 62) {
            return 50;
        }
        return 60;
    }

    /** The player's SkyBlock Level (0 for a new player). Works on both sides; it's synced to every client. */
    public static int level(Player player) {
        return xp(player) / XP_PER_LEVEL;
    }

    /** Total SkyBlock XP. */
    public static int xp(Player player) {
        return Math.max(0, player.getAttachedOrElse(XP, 0));
    }

    /** XP gathered towards the next level (0 to 99). */
    public static int xpIntoLevel(Player player) {
        return xp(player) % XP_PER_LEVEL;
    }

    /** Gives SkyBlock XP; {@code reason} is for logs and debugging. Every 10th level reached pays coins and plays the effect. */
    public static void addXp(ServerPlayer player, int xp, String reason) {
        if (xp <= 0) {
            return;
        }
        int before = xp(player);
        long total = Math.min(Integer.MAX_VALUE, (long) before + xp);
        player.setAttached(XP, (int) total);
        int oldLevel = before / XP_PER_LEVEL;
        int newLevel = (int) (total / XP_PER_LEVEL);
        if (!Features.on(Features.SURVIVAL_LEVEL)) {
            if (newLevel > oldLevel) {
                skyblockLevelUp(player, oldLevel, newLevel);
            } else {
                player.sendOverlayMessage(Component.literal("+" + xp + " SkyBlock XP").withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(" (" + xpIntoLevel(player) + "/" + XP_PER_LEVEL + ")").withStyle(ChatFormatting.DARK_AQUA)));
            }
            return;
        }
        for (int level = oldLevel + 1; level <= newLevel; level++) {
            levelReached(player, level, level == newLevel);
        }
    }

    /** Hypixel's SkyBlock level up: one panel with the levels' Health and Strength, and what they open up. */
    private static void skyblockLevelUp(ServerPlayer player, int oldLevel, int newLevel) {
        ChatFormatting tier = SkyBlockLevels.prefixColor(newLevel);
        Notice.Panel panel = Notice.of(Notice.Kind.SKILL).icon(Notice.STAR).tag("SKYBLOCK LEVEL UP").color(0x55FFFF)
                .hover(Component.literal("Open SkyBlock Leveling")).click("/sblevel").panel();
        panel.heading("Rewards", 0x55FF55);
        StatType health = StatTypes.get("health");
        StatType strength = StatTypes.get("strength");
        int healthGained = SkyBlockLevels.health(newLevel) - SkyBlockLevels.health(oldLevel);
        int strengthGained = SkyBlockLevels.strength(newLevel) - SkyBlockLevels.strength(oldLevel);
        if (health != null && healthGained > 0) {
            panel.line(Component.literal("  +" + healthGained + " " + health.symbol() + " Health").withColor(health.color()));
        }
        if (strength != null && strengthGained > 0) {
            panel.line(Component.literal("  +" + strengthGained + " " + strength.symbol() + " Strength").withColor(strength.color()));
        }
        for (SkyBlockLevels.Unlock unlock : SkyBlockLevels.UNLOCKS) {
            if (unlock.level() > oldLevel && unlock.level() <= newLevel) {
                panel.line(Component.literal("  " + unlock.name()).withStyle(unlock.color()));
            }
        }
        if (newLevel / 40 > oldLevel / 40) {
            panel.line(Component.literal("  New prefix colour: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("[" + newLevel + "] " + SkyBlockLevels.prefixName(newLevel)).withStyle(tier)));
        }
        panel.send(player, Msg.text("Level ", ChatFormatting.GRAY)
                .append(Msg.text(oldLevel + " ➜ ", ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.valueOf(newLevel)).withStyle(tier, ChatFormatting.BOLD)));
        Stats.refresh(player);
        Msg.playSound(player, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
        if (Vfx.exists(LEVEL_UP_EFFECT)) {
            Vfx.play(player, LEVEL_UP_EFFECT);
        }
    }

    private static void levelReached(ServerPlayer player, int level, boolean latest) {
        if (level % 10 != 0) {
            if (latest) {
                player.sendOverlayMessage(Component.literal("SkyBlock Level " + level).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            }
            return;
        }
        long coins = 500L * level;
        Wallet.give(player, coins);
        Notice.of(Notice.Kind.SKILL).icon(Notice.STAR).tag("LEVEL UP").color(0xFFE14D).send(player,
                Msg.text("SkyBlock Level ", ChatFormatting.GRAY).append(Msg.text(String.valueOf(level), ChatFormatting.GOLD, ChatFormatting.BOLD))
                        .append(Msg.text("  +" + Msg.number(coins) + " coins", ChatFormatting.GOLD)));
        Msg.playSound(player, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
        if (Vfx.exists(LEVEL_UP_EFFECT)) {
            Vfx.play(player, LEVEL_UP_EFFECT);
        }
    }

    /** For commands and tests: sets total XP without rewards. */
    static void setXp(ServerPlayer player, int xp) {
        player.setAttached(XP, Math.max(0, xp));
    }
}
