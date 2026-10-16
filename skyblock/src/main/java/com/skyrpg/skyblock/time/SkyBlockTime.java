package com.skyrpg.skyblock.time;

import java.util.List;

/**
 * Hypixel's SkyBlock calendar, on Hypixel's own clock, so the date here is the date on Hypixel. A SkyBlock day lasts
 * 20 real minutes (a Minecraft day), a month 31 days, a year 12 months (124 real hours). Year 1 began on Early Spring
 * 1st at {@link #EPOCH} (June 11, 2019, 17:55 UTC).
 */
public final class SkyBlockTime {
    public static final long EPOCH = 1_560_275_700_000L;
    public static final long HOUR = 50_000L;
    public static final long DAY = 24 * HOUR;
    public static final int DAYS_PER_MONTH = 31;
    public static final long MONTH = DAYS_PER_MONTH * DAY;
    public static final long YEAR = 12 * MONTH;
    public static final List<String> MONTHS = List.of("Early Spring", "Spring", "Late Spring", "Early Summer", "Summer",
            "Late Summer", "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter");

    /**
     * A moment on the SkyBlock calendar.
     *
     * @param month 0 (Early Spring) to 11 (Late Winter)
     * @param day 1 to 31
     * @param hour 0 to 23; {@code minute} 0 to 59
     */
    public record Date(int year, int month, int day, int hour, int minute) {
        public String monthName() {
            return MONTHS.get(month);
        }

        /** "Late Spring 13th". */
        public String dayText() {
            return monthName() + " " + ordinal(day);
        }

        /** "8:20am", in Hypixel's 10-minute steps. */
        public String clock() {
            int shown = minute / 10 * 10;
            int h12 = hour % 12 == 0 ? 12 : hour % 12;
            return h12 + ":" + (shown < 10 ? "0" : "") + shown + (hour < 12 ? "am" : "pm");
        }

        /** Daytime runs from 6am to 7pm, as on Hypixel's scoreboard (☀ or ☽). */
        public boolean isDay() {
            return hour >= 6 && hour < 19;
        }

        /** Real milliseconds at the start of this date's day. */
        public long dayStart() {
            return start(year, month, day);
        }
    }

    private SkyBlockTime() {
    }

    public static Date now() {
        return at(System.currentTimeMillis());
    }

    public static Date at(long millis) {
        long since = Math.max(0, millis - EPOCH);
        int year = (int) (since / YEAR) + 1;
        long inYear = since % YEAR;
        int month = (int) (inYear / MONTH);
        long inMonth = inYear % MONTH;
        int day = (int) (inMonth / DAY) + 1;
        long inDay = inMonth % DAY;
        int hour = (int) (inDay / HOUR);
        int minute = (int) (inDay % HOUR * 60 / HOUR);
        return new Date(year, month, day, hour, minute);
    }

    /** Real milliseconds when this SkyBlock day begins (midnight). */
    public static long start(int year, int month, int day) {
        return EPOCH + (year - 1) * YEAR + month * MONTH + (day - 1) * DAY;
    }

    /** The Minecraft time of day (0 = 6am) for this moment, so the sky matches the SkyBlock clock. */
    public static long minecraftTimeOfDay(long millis) {
        long inDay = Math.floorMod(millis - EPOCH, DAY);
        return Math.floorMod(inDay * 24000 / DAY - 6000, 24000);
    }

    /** "1st", "2nd", "3rd", "4th", "11th", "21st". */
    public static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix = mod100 >= 11 && mod100 <= 13 ? "th" : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }

    /** "2h 13m", "45s": a short countdown. */
    public static String countdown(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86400;
        long hours = seconds % 86400 / 3600;
        long minutes = seconds % 3600 / 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes > 0 ? minutes + "m " + seconds % 60 + "s" : seconds % 60 + "s";
    }
}
