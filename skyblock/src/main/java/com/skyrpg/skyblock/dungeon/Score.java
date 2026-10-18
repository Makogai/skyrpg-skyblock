package com.skyrpg.skyblock.dungeon;

/**
 * Hypixel's dungeon score: Skill + Explore + Speed + Bonus. Skill: 20 + 80 x rooms cleared, -2 a death, -14 a puzzle
 * failed or left unsolved. Explore: 60 x rooms cleared + 40 x secrets found against the floor's share of them. Speed: 100 within the
 * floor's time allowance, then -1 every 12 s down to 90, every 24 s to 80, every 30 s to 70, every 36 s after.
 * Bonus: crypts (up to 5) and the Mimic (2), when those come. Rank: S+ 300, S 270, A 230, B 160, C 100, else D.
 */
final class Score {
    record Result(int skill, int explore, int speed, int bonus, int total, String rank, int secretsFound, int secretsNeeded, double cleared,
                  int deaths, int seconds) {
    }

    private Score() {
    }

    static Result compute(Run run) {
        double cleared = run.clearedShare();
        int skill = clamp((int) Math.floor(20 + 80 * cleared) - 2 * run.deaths - 14 * run.failedPuzzles(), 0, 100);
        int total = run.secretsTotal();
        int needed = (int) Math.ceil(total * run.floor.secretShare());
        int found = run.secretsFound();
        double secretShare = needed == 0 ? 1 : Math.min(1, (double) found / needed);
        int explore = clamp((int) Math.floor(60 * cleared) + (int) Math.floor(40 * secretShare), 0, 100);
        int seconds = run.seconds();
        int speed = speed(seconds - run.floor.seconds());
        // Paul's EZPZ: +10
        int bonus = Math.min(5, run.crypts) + (run.mimicKilled ? 2 : 0) + (com.skyrpg.core.world.Mayor.active("ezpz") ? 10 : 0);
        int sum = skill + explore + speed + bonus;
        return new Result(skill, explore, speed, bonus, sum, rank(sum), found, needed, cleared, run.deaths, seconds);
    }

    static int speed(int over) {
        if (over <= 0) {
            return 100;
        }
        double score = 100;
        int[][] steps = {{120, 12}, {240, 24}, {300, 30}};
        for (int[] step : steps) {
            int used = Math.min(over, step[0]);
            score -= (double) used / step[1];
            over -= used;
            if (over <= 0) {
                return (int) Math.floor(score);
            }
        }
        return Math.max(0, (int) Math.floor(score - over / 36.0));
    }

    static String rank(int score) {
        return score >= 300 ? "S+" : score >= 270 ? "S" : score >= 230 ? "A" : score >= 160 ? "B" : score >= 100 ? "C" : "D";
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
