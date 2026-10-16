package com.skyrpg.minions;

import com.google.gson.JsonElement;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

/** Every minion type, and which SkyBlock item is which minion at which tier. */
public final class Minions {
    public static final String DIRECTORY = "skyrpg/minions";

    /** A minion item: its type and tier. */
    public record Placed(MinionType type, int tier) {
    }

    private static Map<String, MinionType> types = Map.of();
    private static Map<String, Placed> byItem = Map.of();

    private Minions() {
    }

    static void load(Map<Identifier, JsonElement> files) {
        Map<String, MinionType> loaded = new LinkedHashMap<>();
        Map<String, Placed> items = new LinkedHashMap<>();
        files.forEach((id, json) -> {
            try {
                MinionType type = MinionType.parse(json.getAsJsonObject());
                loaded.put(type.id(), type);
                for (MinionType.Tier tier : type.tiers()) {
                    items.put(tier.item(), new Placed(type, tier.tier()));
                }
            } catch (RuntimeException e) {
                SkyRpgMinions.LOGGER.error("Skipping invalid minion {}: {}", id, e.getMessage());
            }
        });
        types = loaded;
        byItem = items;
        SkyRpgMinions.LOGGER.info("Loaded {} minion types", loaded.size());
    }

    public static MinionType type(String id) {
        return types.get(id);
    }

    public static Collection<MinionType> all() {
        return types.values();
    }

    /** The minion this SkyBlock item is ("WHEAT_GENERATOR_3"), or null. */
    public static Placed fromItem(String skyblockId) {
        return skyblockId == null ? null : byItem.get(skyblockId);
    }
}
