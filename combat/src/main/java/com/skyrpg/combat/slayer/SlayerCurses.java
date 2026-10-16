package com.skyrpg.combat.slayer;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * What Hypixel's slayer bosses do to the players fighting them, as a stat source: the Revenant's Pestilence shreds
 * Defense, the Tarantula's Noxious Paralysis lowers Vitality (healing) and stacks a Speed loss every fifth hit, and the
 * Voidgloom's Broken Heart Radiation lowers healing for 90 seconds. Everything goes when the fight ends.
 */
final class SlayerCurses {
    private static final class Curse {
        double defenseShred;
        long shredUntil;
        double vitalityLoss;
        int speedStacks;
        double speedPerStack;
        long heartbreakUntil;
    }

    private static final Map<UUID, Curse> CURSES = new HashMap<>();

    private SlayerCurses() {
    }

    static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Slayer boss";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                Curse curse = CURSES.get(player.getUUID());
                if (curse == null) {
                    return;
                }
                long now = SlayerBosses.now();
                if (curse.shredUntil > now && curse.defenseShred > 0) {
                    out.add(StatTypes.DEFENSE, -curse.defenseShred);
                }
                double vitality = curse.vitalityLoss + (curse.heartbreakUntil > now ? 12 : 0);
                if (vitality > 0) {
                    out.add(StatTypes.VITALITY, -Math.min(100, vitality));
                }
                if (curse.speedStacks > 0) {
                    // Hypixel's debuff takes a share of the 400 speed cap.
                    out.add(StatTypes.SPEED, -curse.speedStacks * curse.speedPerStack * 4);
                }
            }
        });
    }

    private static Curse curse(ServerPlayer player) {
        return CURSES.computeIfAbsent(player.getUUID(), uuid -> new Curse());
    }

    /** Pestilence: a share of the player's Defense, gone after {@code ticks}. */
    static void shred(ServerPlayer player, double share, int ticks) {
        Curse curse = curse(player);
        boolean fresh = curse.shredUntil <= SlayerBosses.now();
        if (fresh) {
            // Measured from the Defense the player has without the shred.
            curse.defenseShred = Math.max(0, Stats.get(player, StatTypes.DEFENSE)) * share;
        }
        curse.shredUntil = SlayerBosses.now() + ticks;
        if (fresh) {
            Stats.refresh(player);
        }
    }

    /** Noxious Paralysis: lower healing while the boss lives. */
    static void vitality(ServerPlayer player, double loss) {
        Curse curse = curse(player);
        if (curse.vitalityLoss != loss) {
            curse.vitalityLoss = loss;
            Stats.refresh(player);
        }
    }

    /** Noxious Paralysis: one more stack of the Speed loss. */
    static void slow(ServerPlayer player, double percent) {
        Curse curse = curse(player);
        curse.speedStacks++;
        curse.speedPerStack = percent;
        Stats.refresh(player);
    }

    /** Broken Heart Radiation: 12% less healing for 90 seconds. */
    static void heartbreak(ServerPlayer player) {
        curse(player).heartbreakUntil = SlayerBosses.now() + 1800;
        Stats.refresh(player);
    }

    /** The fight is over: the boss's curses lift (Broken Heart Radiation runs out on its own). */
    static void lift(ServerPlayer player) {
        Curse curse = CURSES.get(player.getUUID());
        if (curse == null) {
            return;
        }
        if (curse.heartbreakUntil > SlayerBosses.now()) {
            curse.defenseShred = 0;
            curse.vitalityLoss = 0;
            curse.speedStacks = 0;
        } else {
            CURSES.remove(player.getUUID());
        }
        Stats.refresh(player);
    }
}
