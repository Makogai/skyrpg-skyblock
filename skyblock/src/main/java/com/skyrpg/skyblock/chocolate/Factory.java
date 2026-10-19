package com.skyrpg.skyblock.chocolate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A profile's Chocolate Factory (kept as JSON on the player), with Hypixel's numbers. Production comes in whether you're
 * online or not: {@link #settle} adds what was made since last time (the Time Tower's hour counted at its rate).
 */
final class Factory {
    static final String[] EMPLOYEES = {"Rabbit Bro", "Rabbit Cousin", "Rabbit Sis", "Rabbit Daddy", "Rabbit Granny", "Rabbit Uncle", "Rabbit Dog"};
    static final String[] LEVEL_NAMES = {"I", "II", "III", "IV", "V", "VI"};
    /** Each Factory level's multiplier, the chocolate (made since the last prestige) to reach it, and its chocolate cap. */
    static final double[] LEVEL_MULT = {1, 1.1, 1.25, 1.5, 2, 2.5};
    static final double[] PRESTIGE_NEED = {0, 150e6, 1e9, 4e9, 10e9, 30e9};
    static final double[] MAX_CHOCOLATE = {500e6, 1.2e9, 5e9, 25e9, 50e9, 100e9};
    /** Rabbit Bro's first ten upgrades cost the base times these; everything else grows 5% a level. */
    private static final double[] BRO_FIRST = {1, 2, 4, 6, 8, 9, 9.5, 10, 10.5, 11};
    /** The Time Tower's upgrade to each level (2 to 15), times its base. */
    private static final double[] TOWER_MULT = {0, 0, 1, 2, 3, 4, 6, 8, 10, 12, 14, 16, 20, 24, 30, 40};
    /** The Rabbit Shrine's and Coach Jackrabbit's upgrades, in millions. */
    private static final double[] SHRINE_COST = {10, 20, 30, 40, 60, 80, 100, 120, 150, 200, 250, 300, 350, 400, 450, 500, 550, 600, 650, 700};
    private static final double[] COACH_COST = {2.2, 3.9, 5.3, 7.2, 9.7, 13, 18, 24, 32, 43, 59, 79, 110, 140, 190, 260, 350, 480, 650, 870};
    static final long HOUR = 3_600_000L;

    /** All-time chocolate milestones and their rabbits. */
    static final double[] MILESTONES = {1e3, 10e3, 100e3, 1e6, 2.5e6, 5e6, 10e6, 25e6, 50e6, 100e6, 250e6, 500e6, 1e9, 2.5e9, 5e9, 10e9, 25e9,
            50e9, 100e9, 150e9, 200e9, 300e9, 400e9, 500e9, 600e9, 700e9};
    static final String[] MILESTONE_RABBITS = {"Almond Amaretto", "Buttercream Blossom", "Cocoa Comet", "Dulce Drizzle", "Espresso Eclair",
            "Fudge Fountain", "Ginger Glaze", "Honey Hazelnut", "Icing Ivy", "Jasmine Jello", "Kiwi Kiss", "Lavender Lemon", "Maple Mirage",
            "Nougat Nebula", "Orange Obsidian", "Peppermint Pearl", "Quince Quark", "Raspberry Ripple", "Strawberry Swirl", "Toffee Tulip",
            "Ube Unicorn", "Vanilla Vortex", "Walnut Whirl", "Xoco Xanudu", "Yogurt Yucca", "Zest Zephyr"};
    /** Chocolate spent in the shop: milestones and rabbits. */
    static final double[] SHOP_MILESTONES = {2e6, 10e6, 25e6, 50e6, 100e6, 250e6, 500e6, 1e9, 2.5e9, 5e9, 10e9, 25e9, 50e9, 100e9, 150e9,
            200e9, 250e9, 300e9, 350e9, 400e9, 500e9, 600e9, 700e9, 800e9};
    static final String[] SHOP_RABBITS = {"Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta", "Iota", "Kappa", "Lambda", "Mu",
            "Nu", "Xi", "Omicron", "Pi", "Rho", "Sigma", "Tau", "Upsilon", "Phi", "Chi", "Psi", "Omega"};

    double chocolate;
    double allTime;
    double thisPrestige;
    double spent;
    int level = 1;
    int handBaked = 1;
    int[] employees = new int[7];
    int barn = 1;
    int tower;
    int towerCharges;
    long towerNextCharge;
    long towerUntil;
    int shrine;
    int coach;
    /** Rabbits found: name to how many times (1 = found once, more = duplicates). */
    Map<String, Integer> rabbits = new LinkedHashMap<>();
    int found;
    int duplicates;
    /** The egg spots found on each island (unique locations, for the island's rabbit). */
    Map<String, List<Integer>> eggSpots = new HashMap<>();
    /** The egg spawns claimed (keys), the last few. */
    List<Long> eggs = new ArrayList<>();
    int hoppityYear = -1;
    List<String> hoppityOffers = new ArrayList<>();
    List<Boolean> hoppityBought = new ArrayList<>();
    Map<String, Integer> shopBought = new HashMap<>();
    int shopYear = -1;
    long last;

    boolean has(String rabbit) {
        return rabbits.containsKey(rabbit);
    }

    int barnSize() {
        return 20 + 2 * (barn - 1);
    }

    int mythics() {
        int n = 0;
        for (String name : rabbits.keySet()) {
            Rabbits.Rabbit r = Rabbits.get(name);
            if (r != null && r.rarity() == 5) {
                n++;
            }
        }
        return n;
    }

    int divines() {
        int n = 0;
        for (String name : rabbits.keySet()) {
            Rabbits.Rabbit r = Rabbits.get(name);
            if (r != null && r.rarity() == 6) {
                n++;
            }
        }
        return n;
    }

    int maxEmployeeLevel() {
        return 120 + 20 * (level - 1) + divines();
    }

    /** Chocolate a second before multipliers: the employees and the rabbits. */
    double base() {
        double base = 0;
        for (int i = 0; i < 7; i++) {
            base += employees[i] * (i + 1);
        }
        for (String name : rabbits.keySet()) {
            Rabbits.Rabbit r = Rabbits.get(name);
            if (r != null) {
                base += Rabbits.FLAT[r.rarity()];
            }
        }
        base += has("Dante") ? 50 : 0;
        base += has("Zest Zephyr") ? 30 : 0;
        base += has("Sigma") ? 5 * mythics() : 0;
        return base;
    }

    boolean towerActive(long now) {
        return now < towerUntil;
    }

    double towerBonus() {
        return 0.1 * tower + (has("Mu") ? 0.7 : 0);
    }

    /** The multiplier: the Factory level's, the rabbits', Coach Jackrabbit's and, while it's on, the Time Tower's. */
    double multiplier(long now) {
        double mult = LEVEL_MULT[level - 1];
        for (String name : rabbits.keySet()) {
            Rabbits.Rabbit r = Rabbits.get(name);
            if (r != null) {
                mult += Rabbits.MULT[r.rarity()];
            }
        }
        mult += has("Galaxy") ? 0.02 : 0;
        mult += has("Zest Zephyr") ? 0.03 : 0;
        mult += coach * 0.01;
        if (towerActive(now)) {
            mult += towerBonus();
        }
        return mult;
    }

    double perSecond(long now) {
        return base() * multiplier(now);
    }

    /** Adds what the factory made since the last settle (online or not), and the Time Tower's charges. */
    void settle(long now) {
        if (last == 0) {
            last = now;
        }
        if (now > last) {
            // the Time Tower's part of the time, at its rate
            long boosted = Math.max(0, Math.min(now, towerUntil) - last);
            long plain = (now - last) - boosted;
            double baseRate = base();
            double mult = multiplier(Long.MIN_VALUE);
            gain(baseRate * mult * plain / 1000.0 + baseRate * (mult + towerBonus()) * boosted / 1000.0);
            last = now;
        }
        if (tower > 0) {
            long charge = has("Einstein") ? 7 * HOUR : 8 * HOUR;
            if (towerCharges >= 3) {
                towerNextCharge = 0;
            } else {
                if (towerNextCharge == 0) {
                    towerNextCharge = now + charge;
                }
                while (towerCharges < 3 && now >= towerNextCharge) {
                    towerCharges++;
                    towerNextCharge += charge;
                }
                if (towerCharges >= 3) {
                    towerNextCharge = 0;
                }
            }
        }
    }

    /** Chocolate made (production, clicks, rabbits): counts for milestones and the next prestige; the cap holds it. */
    void gain(double amount) {
        if (amount <= 0) {
            return;
        }
        chocolate = Math.min(MAX_CHOCOLATE[level - 1], chocolate + amount);
        allTime += amount;
        thisPrestige += amount;
    }

    boolean spend(double amount) {
        if (chocolate < amount) {
            return false;
        }
        chocolate -= amount;
        return true;
    }

    // ---------------------------------------------------------------- upgrades

    boolean employeeUnlocked(int i) {
        return i == 0 || employees[i - 1] >= 20;
    }

    double employeeCost(int i) {
        int lvl = employees[i];
        double cost;
        if (i == 0 && lvl < BRO_FIRST.length) {
            cost = (30 + 20 * level) * BRO_FIRST[lvl];
        } else {
            cost = (216 + 144 * level) * (i + 1) * (i + 1) * Math.pow(1.05, lvl);
        }
        return Math.round(cost * (has("Zeta") ? 0.99 : 1));
    }

    /** Hand-Baked Chocolate's next level (2 to 10). */
    double handBakedCost() {
        return 500.0 * handBaked;
    }

    double barnCost() {
        return Math.round(5000 * Math.pow(1.05, barn - 1));
    }

    /** The Time Tower's next level (the first comes with Factory II). */
    double towerCost() {
        return (4.5e6 + 0.5e6 * level) * TOWER_MULT[Math.min(15, tower + 1)];
    }

    double shrineCost() {
        return SHRINE_COST[Math.min(19, shrine)] * 1e6;
    }

    double coachCost() {
        return COACH_COST[Math.min(19, coach)] * 1e6;
    }

    boolean canPrestige() {
        return level < 6 && thisPrestige >= PRESTIGE_NEED[level];
    }

    /** The next Factory level: everything but the Rabbit Barn and the rabbits starts over. */
    void prestige() {
        level++;
        chocolate = 0;
        thisPrestige = 0;
        employees = new int[7];
        handBaked = 1;
        tower = level >= 2 ? 1 : 0;
        towerCharges = level == 2 ? 3 : towerCharges;
        shrine = 0;
        coach = 0;
    }
}
