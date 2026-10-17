package com.skyrpg.skyblock.dungeon;

import java.util.ArrayList;
import java.util.List;

/**
 * The Catacombs' floors: Hypixel's map sizes and level requirements ({@code docs/design/research/dungeons.md}), and our
 * numbers where Hypixel's aren't known (see {@code docs/design/catacombs.md}): time allowances, mob scaling, chest prices,
 * Catacombs XP. Master Mode floors (M1-M7, open once Floor VII is beaten) are the same maps with Master Mode's bosses,
 * mobs past Floor VII's, 8 minutes for the Speed score, double chest prices and three times the XP.
 */
public final class Floors {
    /**
     * A floor: number (0 = Entrance), name, map size, puzzles, traps, Wither doors, Combat and Catacombs requirements,
     * the Speed score's time allowance (seconds), the share of secrets that counts as all of them, the mob strength
     * factor (0 at F1, 1 at F7: health and damage go between Hypixel's F1 and F7 numbers), the boss, Catacombs XP for an
     * S+ run, the chests' prices (Gold, Diamond, Emerald, Obsidian, Bedrock; -1 = none on this floor), and Master Mode.
     */
    public record Floor(int number, String name, int width, int depth, int puzzles, int traps, int witherDoors, int combat, int catacombs,
                        int seconds, double secretShare, double strength, String boss, int xp, long[] chests, boolean master) {
        public String title() {
            return number == 0 ? "The Catacombs - Entrance" : "The Catacombs - " + (master ? "Master Mode " : "") + "Floor " + roman(number);
        }

        /** "F1", "M3", "E". */
        public String code() {
            return number == 0 ? "E" : (master ? "M" : "F") + number;
        }
    }

    public static final List<Floor> ALL = List.of(
            new Floor(0, "Entrance", 4, 4, 0, 0, 1, 15, 0, 1200, 0.3, 0.0, "watcher", 40, new long[] {-1, -1, -1, -1, -1}, false),
            new Floor(1, "Floor I", 4, 5, 1, 0, 1, 1, 1, 600, 0.3, 0.0, "bonzo", 80, new long[] {25_000, 50_000, -1, -1, -1}, false),
            new Floor(2, "Floor II", 5, 5, 2, 0, 2, 3, 3, 600, 0.4, 1 / 6.0, "scarf", 160, new long[] {25_000, 50_000, 100_000, -1, -1}, false),
            new Floor(3, "Floor III", 5, 5, 2, 1, 2, 5, 5, 600, 0.5, 2 / 6.0, "professor", 400, new long[] {50_000, 100_000, 150_000, 250_000, -1}, false),
            new Floor(4, "Floor IV", 6, 5, 2, 1, 3, 9, 9, 720, 0.6, 3 / 6.0, "thorn", 1_420, new long[] {75_000, 150_000, 250_000, 500_000, -1}, false),
            new Floor(5, "Floor V", 6, 5, 3, 1, 3, 14, 14, 600, 0.7, 4 / 6.0, "livid", 2_000,
                    new long[] {100_000, 200_000, 350_000, 750_000, 2_000_000}, false),
            new Floor(6, "Floor VI", 6, 6, 3, 1, 4, 19, 19, 720, 0.85, 5 / 6.0, "sadan", 4_000,
                    new long[] {100_000, 250_000, 400_000, 1_000_000, 2_000_000}, false),
            new Floor(7, "Floor VII", 6, 6, 3, 1, 4, 24, 24, 840, 1.0, 1.0, "necron", 20_000,
                    new long[] {100_000, 250_000, 500_000, 1_000_000, 2_000_000}, false));

    /** Master Mode Floors I-VII: Catacombs (and Combat) 24 for M1 up to 36 for M7. */
    public static final List<Floor> MASTER;

    static {
        List<Floor> master = new ArrayList<>();
        for (Floor f : ALL.subList(1, ALL.size())) {
            long[] chests = new long[f.chests().length];
            for (int i = 0; i < chests.length; i++) {
                chests[i] = f.chests()[i] < 0 ? -1 : f.chests()[i] * 2;
            }
            int level = 22 + 2 * f.number();
            master.add(new Floor(f.number(), "Master Mode " + f.name(), f.width(), f.depth(), f.puzzles(), f.traps(), f.witherDoors(), level, level,
                    480, 1.0, 1.0, f.boss(), f.xp() * 3, chests, true));
        }
        MASTER = List.copyOf(master);
    }

    private Floors() {
    }

    public static Floor get(int number) {
        return number >= 0 && number < ALL.size() ? ALL.get(number) : null;
    }

    public static Floor get(int number, boolean master) {
        return master ? (number >= 1 && number <= MASTER.size() ? MASTER.get(number - 1) : null) : get(number);
    }

    public static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }
}
