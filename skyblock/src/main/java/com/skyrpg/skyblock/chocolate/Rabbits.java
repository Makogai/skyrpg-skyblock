package com.skyrpg.skyblock.chocolate;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.util.RandomSource;

/**
 * Hypixel's Chocolate Rabbits ({@code skyrpg/chocolate/rabbits.json}, from tools/skyblock/import_rabbits.py): each one's
 * rarity, the island it lives on (a Resident), and how it's found ("egg": Chocolate Eggs and Hoppity; "milestone",
 * "shop", "requirement": other ways). What a rabbit gives depends on its rarity, and the Mythics each do their own thing.
 */
public final class Rabbits {
    public static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE");
    /** Chocolate a second each rarity adds, and multiplier (Hypixel's). */
    static final int[] FLAT = {1, 2, 4, 10, 0, 0, 0};
    static final double[] MULT = {0.002, 0.003, 0.004, 0.005, 0.02, 0, 0.025};
    /** A duplicate's (or a full barn's) chocolate: this many seconds of production. */
    static final int[] DUPLICATE_SECONDS = {150, 300, 750, 1500, 4000, 8000, 15000};
    /** Egg rarity weights (Hypixel's), and the Factory level each needs. */
    static final double[] WEIGHTS = {608, 250, 100, 30, 10, 4, 1};
    static final int[] LEVEL_NEEDED = {1, 1, 2, 3, 4, 5, 6};

    public record Rabbit(String name, int rarity, String resident, String source, String text, String requirement) {
        public ChatFormatting color() {
            return Rabbits.color(rarity);
        }
    }

    private static List<Rabbit> all = List.of();
    private static Map<String, Rabbit> byName = Map.of();

    private Rabbits() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("chocolate_rabbits"), "skyrpg/chocolate", files -> {
            List<Rabbit> loaded = new ArrayList<>();
            for (JsonElement file : files.values()) {
                if (!file.getAsJsonObject().has("rabbits")) {
                    continue;
                }
                for (JsonElement element : file.getAsJsonObject().getAsJsonArray("rabbits")) {
                    JsonObject r = element.getAsJsonObject();
                    int rarity = RARITIES.indexOf(r.get("rarity").getAsString());
                    if (rarity < 0) {
                        continue; // Chocobits and the like (Factory VI's specials) aren't in yet
                    }
                    loaded.add(new Rabbit(r.get("name").getAsString(), rarity, r.get("resident").getAsString(), r.get("source").getAsString(),
                            r.get("text").getAsString(), r.has("requirement") ? r.get("requirement").getAsString() : ""));
                }
            }
            Map<String, Rabbit> names = new HashMap<>();
            loaded.forEach(r -> names.put(r.name(), r));
            all = List.copyOf(loaded);
            byName = Map.copyOf(names);
            SkyRpgSkyblock.LOGGER.info("Loaded {} chocolate rabbits", all.size());
        });
    }

    public static List<Rabbit> all() {
        return all;
    }

    public static Rabbit get(String name) {
        return byName.get(name);
    }

    static ChatFormatting color(int rarity) {
        return switch (rarity) {
            case 0 -> ChatFormatting.WHITE;
            case 1 -> ChatFormatting.GREEN;
            case 2 -> ChatFormatting.BLUE;
            case 3 -> ChatFormatting.DARK_PURPLE;
            case 4 -> ChatFormatting.GOLD;
            case 5 -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.AQUA;
        };
    }

    static String rarityName(int rarity) {
        return RARITIES.get(rarity);
    }

    /**
     * The rabbit a Chocolate Egg holds for this factory, on this island: a rarity by Hypixel's weights (only those the
     * Factory level has unlocked; the Rabbit Shrine makes Uncommon and up likelier, Napoleon Rare and up; every 250th
     * rabbit is Mythic, every 1,250th Divine, once unlocked), then any egg rabbit of it, Residents only on their island.
     */
    static Rabbit roll(Factory factory, String island, RandomSource random) {
        int unlocked = 0;
        for (int i = 0; i < RARITIES.size(); i++) {
            if (factory.level >= LEVEL_NEEDED[i]) {
                unlocked = i;
            }
        }
        int rarity = -1;
        if (factory.found + 1 > 0 && (factory.found + 1) % 1250 == 0 && unlocked >= 6) {
            rarity = 6;
        } else if ((factory.found + 1) % 250 == 0 && unlocked >= 5) {
            rarity = 5;
        }
        if (rarity < 0) {
            double[] weights = new double[RARITIES.size()];
            double total = 0;
            for (int i = 0; i <= unlocked; i++) {
                double w = WEIGHTS[i];
                if (i >= 1) {
                    w *= 1 + 0.02 * factory.shrine;
                }
                if (i >= 2 && factory.has("Napoleon")) {
                    w *= 1.1;
                }
                weights[i] = w;
                total += w;
            }
            double pick = random.nextDouble() * total;
            for (int i = 0; i <= unlocked; i++) {
                pick -= weights[i];
                if (pick < 0) {
                    rarity = i;
                    break;
                }
            }
            if (rarity < 0) {
                rarity = 0;
            }
        }
        for (int r = rarity; r >= 0; r--) {
            int at = r;
            List<Rabbit> pool = all.stream().filter(rabbit -> rabbit.rarity() == at && rabbit.source().equals("egg")
                    && (rabbit.resident().isEmpty() || rabbit.resident().equalsIgnoreCase(island))).toList();
            if (!pool.isEmpty()) {
                return pool.get(random.nextInt(pool.size()));
            }
        }
        return null;
    }

    /** Hoppity's offer for a slot: a random egg rabbit (no Residents) of a rarity the slot can hold. */
    static Rabbit any(int rarity, RandomSource random) {
        List<Rabbit> pool = all.stream().filter(r -> r.rarity() == rarity && r.source().equals("egg") && r.resident().isEmpty()).toList();
        return pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
