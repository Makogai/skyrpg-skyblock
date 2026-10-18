package com.skyrpg.skyblock.time;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Hypixel's SkyBlock events by date: the yearly festivals, and the ones that come round every few days. This is the
 * schedule only; each event's gameplay arrives with its system (the Spooky Festival with its mobs, and so on).
 */
public final class CalendarEvents {
    /**
     * An event on the calendar.
     *
     * @param month 0-11 and {@code day} 1-31 where it starts; {@code days} how many SkyBlock days it lasts
     * @param everyDays for repeating events, how often (in SkyBlock days, counted from the year's first day); 0 = yearly
     * @param perk the Mayor's perk the event needs ("fishing_festival"), or empty for every year's
     */
    public record Event(String name, String description, int color, int month, int day, int days, int everyDays, String perk) {
        public Event(String name, String description, int color, int month, int day, int days, int everyDays) {
            this(name, description, color, month, day, days, everyDays, "");
        }

        public long length() {
            return days * SkyBlockTime.DAY;
        }
    }

    /** Which Mayor's perks are in effect (the server's elections; on the client, what the server sent). */
    private static Predicate<String> perks = perk -> false;

    public static void perks(Predicate<String> active) {
        perks = active;
    }

    private static boolean on(Event event) {
        return event.perk().isEmpty() || perks.test(event.perk());
    }

    /** One time an event happens: real milliseconds it starts and ends. */
    public record Occurrence(Event event, long start, long end) {
        public boolean active(long now) {
            return now >= start && now < end;
        }
    }

    public static final List<Event> EVENTS = List.of(
            new Event("Hoppity's Hunt", "Chocolate eggs to find all over the islands, all spring long.", 0xFFFF88CC, 0, 1, 93, 0),
            new Event("Traveling Zoo", "Oringo visits the Hub with pets to sell.", 0xFF55FF55, 3, 1, 3, 0),
            new Event("Election Booth Opens", "Vote for the next mayor in the Hub.", 0xFFFFAA00, 5, 27, 1, 0),
            new Event("Spooky Festival", "Spooky mobs, candy and Fear Mongerer's shop.", 0xFFFF8800, 7, 29, 3, 0),
            new Event("Traveling Zoo", "Oringo visits the Hub with pets to sell.", 0xFF55FF55, 9, 1, 3, 0),
            new Event("Jerry's Workshop", "The winter island is open for the season.", 0xFF55FFFF, 11, 1, 31, 0),
            new Event("Season of Jerry", "Defend Jerry's Workshop from Magma Boss's raid.", 0xFF5555FF, 11, 24, 3, 0),
            new Event("New Year Celebration", "Cake for everyone at the end of the year.", 0xFFFF55FF, 11, 29, 3, 0),
            new Event("Mayor Elected", "The election closes and the new mayor takes office.", 0xFFFFAA00, 2, 27, 1, 0),
            // Every third day from the 2nd: on the real clock, each hour at :15, as on Hypixel.
            new Event("Jacob's Farming Contest", "Farm one of three crops for 20 minutes; the best win medals.", 0xFFFFFF55, 0, 2, 1, 3),
            // The Mayor's events: Marina's Fishing Festival the first 3 days of every month, Diana's ritual and Cole's
            // Mining Fiesta all term (from the 27th of Late Spring).
            new Event("Fishing Festival", "Sharks bite all over SkyBlock. Marina's perk.", 0xFF55FFFF, 0, 1, 3, 31, "fishing_festival"),
            new Event("Mythological Ritual", "Diana's Griffin burrows in the Hub, all year.", 0xFF55FF55, 2, 27, 372, 372, "mythological_ritual"),
            new Event("Mining Fiesta", "+25 Mining Wisdom and Fortune and rare drops. Cole's perk.", 0xFFFFAA00, 2, 27, 372, 372, "mining_fiesta"));

    /** Events an admin started early ({@code /sbevent start}): name to when it started; each runs its usual length. */
    private static final Map<String, Long> FORCED = new ConcurrentHashMap<>();

    /** Starts an event now (true) or ends a forced one (false); false when no event has that name. */
    public static boolean force(String name, boolean on, long now) {
        if (EVENTS.stream().noneMatch(e -> e.name().equalsIgnoreCase(name))) {
            return false;
        }
        String exact = EVENTS.stream().filter(e -> e.name().equalsIgnoreCase(name)).findFirst().orElseThrow().name();
        Event event = EVENTS.stream().filter(e -> e.name().equals(exact)).findFirst().orElseThrow();
        if (on) {
            FORCED.put(exact, now);
        } else if (FORCED.containsKey(exact)) {
            // it ends now (and its NPCs stay their hour after, as at a normal end)
            FORCED.put(exact, now - event.length());
        }
        return true;
    }

    /** Whether an event of this name runs now or within {@code millis} before it starts or after it ends (event NPCs). */
    public static boolean near(long now, String name, long millis) {
        return upcoming(now - millis, 2 * millis).stream()
                .anyMatch(o -> o.event().name().equals(name) && o.start() - millis <= now && now < o.end() + millis);
    }

    /** The occurrence of this event running now, or null. */
    public static Occurrence occurrence(long now, String name) {
        return upcoming(now, 0).stream().filter(o -> o.event().name().equals(name) && o.active(now)).findFirst().orElse(null);
    }

    /** Whether an event of this name is running now ("Spooky Festival"). */
    public static boolean running(long now, String name) {
        return upcoming(now, 0).stream().anyMatch(o -> o.event().name().equals(name) && o.active(now));
    }

    /** The event running now with this perk, or null (the Fishing Festival during its 3 days...). */
    public static Occurrence running(String perk, long now) {
        return upcoming(now, 0).stream().filter(o -> o.event().perk().equals(perk) && o.active(now)).findFirst().orElse(null);
    }

    private CalendarEvents() {
    }

    /** Every occurrence that is running or starts within {@code withinMillis} of {@code now}, soonest first. */
    public static List<Occurrence> upcoming(long now, long withinMillis) {
        List<Occurrence> out = new ArrayList<>();
        SkyBlockTime.Date today = SkyBlockTime.at(now);
        long yearStart = SkyBlockTime.start(today.year(), 0, 1);
        for (Event event : EVENTS) {
            if (!on(event)) {
                continue;
            }
            if (event.everyDays() > 0) {
                long period = event.everyDays() * SkyBlockTime.DAY;
                long first = yearStart + (event.month() * SkyBlockTime.DAYS_PER_MONTH + event.day() - 1) * SkyBlockTime.DAY;
                long n = Math.floorDiv(now - first, period);
                for (long start = first + n * period; start < now + withinMillis; start += period) {
                    if (start + event.length() > now) {
                        out.add(new Occurrence(event, start, start + event.length()));
                    }
                }
                continue;
            }
            for (int year = today.year() - 1; year <= today.year() + 1; year++) {
                long start = SkyBlockTime.start(year, event.month(), event.day());
                long end = start + event.length();
                if (end > now && start < now + withinMillis) {
                    out.add(new Occurrence(event, start, end));
                }
            }
        }
        FORCED.forEach((name, start) -> EVENTS.stream().filter(e -> e.name().equals(name)).findFirst().ifPresent(event -> {
            if (start + event.length() > now && start < now + withinMillis) {
                out.add(new Occurrence(event, start, start + event.length()));
            } else if (start + event.length() + 3_600_000L <= now) {
                FORCED.remove(name, start);
            }
        }));
        out.sort(Comparator.comparingLong(Occurrence::start));
        return out;
    }

    /** The yearly events in a month (0-11), for the calendar grid. */
    public static List<Event> inMonth(int month) {
        return EVENTS.stream().filter(e -> on(e) && e.everyDays() == 0 && (e.month() == month
                || month > e.month() && (month - e.month()) * SkyBlockTime.DAYS_PER_MONTH < e.day() - 1 + e.days())).toList();
    }
}
