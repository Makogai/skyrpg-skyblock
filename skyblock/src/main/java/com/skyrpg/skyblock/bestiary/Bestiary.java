package com.skyrpg.skyblock.bestiary;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Hypixel's Bestiary ({@code skyrpg/bestiary}, from {@code tools/skyblock/import_bestiary.py}): 255 mob families on 19
 * islands. Every kill counts for the family named like the mob (Goblin variants for the Goblin family, every dragon for
 * Dragon, unnamed vanilla mobs for the Private Island's families); its kills climb Hypixel's tiers (a bracket of kill
 * counts, up to the family's cap). Each new tier gives 1 SkyBlock XP; every 10 tiers across all families is a Milestone,
 * +2 Health each. {@code /bestiary} shows it all. Per profile.
 */
public final class Bestiary {
    public record Family(String id, String name, String island, String texture, int[] thresholds) {
        public int maxTier() {
            return thresholds.length;
        }

        public int tier(long kills) {
            int tier = 0;
            while (tier < thresholds.length && kills >= thresholds[tier]) {
                tier++;
            }
            return tier;
        }
    }

    public record Island(String id, String name, String icon, List<Family> families) {
    }

    public static final AttachmentType<Map<String, Long>> KILLS = AttachmentRegistry.create(SkyRpgSkyblock.id("bestiary"),
            builder -> builder.initializer(Map::of).persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());
    private static final int TIERS_PER_MILESTONE = 10;
    private static final double HEALTH_PER_MILESTONE = 2;
    /** Our mob names that count for another family. */
    private static final Map<String, String> ALIASES = Map.of(
            "knifethrower", "goblin", "fireslinger", "goblin", "weakling", "goblin", "zealot bruiser", "zealot",
            "special zealot", "zealot", "treasure hoarder", "treasure hoarder");

    private static List<Island> islands = List.of();
    private static Map<String, Family> byName = Map.of();
    private static Map<String, Family> byId = Map.of();

    private Bestiary() {
    }

    public static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("bestiary"), "skyrpg/bestiary", Bestiary::load);
        BestiaryMenu.init();
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getEntity() instanceof ServerPlayer player && !(entity instanceof Player) && SkyBlockWorlds.isSkyBlock(player.level())) {
                Family family = family(entity);
                if (family != null) {
                    kill(player, family);
                }
            }
        });
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Bestiary";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                if (SkyBlockWorlds.isSkyBlock(player.level())) {
                    int milestones = milestone(player);
                    if (milestones > 0) {
                        out.add(StatTypes.HEALTH, milestones * HEALTH_PER_MILESTONE);
                    }
                }
            }
        });
    }

    private static void load(Map<Identifier, JsonElement> files) {
        List<Island> loaded = new ArrayList<>();
        Map<String, Family> names = new HashMap<>();
        Map<String, Family> ids = new LinkedHashMap<>();
        for (JsonElement file : files.values()) {
            JsonObject json = file.getAsJsonObject();
            JsonObject brackets = json.getAsJsonObject("brackets");
            for (JsonElement element : json.getAsJsonArray("islands")) {
                JsonObject island = element.getAsJsonObject();
                String islandName = island.get("name").getAsString();
                List<Family> families = new ArrayList<>();
                for (JsonElement f : island.getAsJsonArray("families")) {
                    JsonObject family = f.getAsJsonObject();
                    int cap = family.get("cap").getAsInt();
                    List<Integer> kills = new ArrayList<>();
                    var bracket = brackets.getAsJsonArray(String.valueOf(family.get("bracket").getAsInt()));
                    if (bracket != null) {
                        for (JsonElement k : bracket) {
                            if (k.getAsInt() > cap && cap > 0) {
                                break;
                            }
                            kills.add(k.getAsInt());
                        }
                    }
                    Family parsed = new Family(family.get("id").getAsString(), family.get("name").getAsString(), islandName,
                            family.get("texture").getAsString(), kills.stream().mapToInt(Integer::intValue).toArray());
                    families.add(parsed);
                    names.putIfAbsent(parsed.name().toLowerCase(Locale.ROOT), parsed);
                    ids.put(parsed.id(), parsed);
                }
                loaded.add(new Island(island.get("id").getAsString(), islandName, island.get("icon").getAsString(), List.copyOf(families)));
            }
        }
        islands = List.copyOf(loaded);
        byName = names;
        byId = ids;
        SkyRpgSkyblock.LOGGER.info("Loaded {} Bestiary families", ids.size());
    }

    public static List<Island> islands() {
        return islands;
    }

    /** The family a mob counts for: by its name (Hypixel's), or by its type when it has none. */
    static Family family(LivingEntity entity) {
        String name = entity.getCustomName() != null ? entity.getCustomName().getString() : entity.getType().getDescription().getString();
        String key = name.toLowerCase(Locale.ROOT).trim();
        key = ALIASES.getOrDefault(key, key);
        Family family = byName.get(key);
        if (family == null && key.endsWith(" dragon")) {
            family = byName.get("dragon");
        }
        return family;
    }

    public static long kills(ServerPlayer player, Family family) {
        return player.getAttachedOrElse(KILLS, Map.of()).getOrDefault(family.id(), 0L);
    }

    /** Every family tier this profile has, summed. */
    public static int totalTiers(ServerPlayer player) {
        int total = 0;
        for (Map.Entry<String, Long> entry : player.getAttachedOrElse(KILLS, Map.of()).entrySet()) {
            Family family = byId.get(entry.getKey());
            if (family != null) {
                total += family.tier(entry.getValue());
            }
        }
        return total;
    }

    public static int milestone(ServerPlayer player) {
        return totalTiers(player) / TIERS_PER_MILESTONE;
    }

    private static void kill(ServerPlayer player, Family family) {
        Map<String, Long> kills = new HashMap<>(player.getAttachedOrElse(KILLS, Map.of()));
        long before = kills.getOrDefault(family.id(), 0L);
        int milestoneBefore = milestone(player);
        kills.put(family.id(), before + 1);
        player.setAttached(KILLS, Map.copyOf(kills));
        int tierBefore = family.tier(before);
        int tierAfter = family.tier(before + 1);
        if (tierAfter <= tierBefore) {
            return;
        }
        SkyBlockXp.give(player, tierAfter - tierBefore, "Bestiary " + family.name());
        Notice.Panel panel = Notice.of(Notice.Kind.COMBAT).tag("BESTIARY").panel();
        panel.line(Component.literal("+" + (tierAfter - tierBefore) + " SkyBlock XP").withStyle(ChatFormatting.AQUA));
        int milestoneAfter = milestone(player);
        if (milestoneAfter > milestoneBefore) {
            panel.line(Component.literal("Milestone " + milestoneAfter + "! +" + Msg.number(HEALTH_PER_MILESTONE) + " Health")
                    .withStyle(ChatFormatting.RED));
            Stats.refresh(player);
        }
        panel.send(player, Component.literal(family.name() + " ").withStyle(ChatFormatting.WHITE)
                .append(Component.literal((tierBefore == 0 ? "0" : Msg.roman(tierBefore)) + " → " + Msg.roman(tierAfter))
                        .withStyle(ChatFormatting.GREEN)));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
    }
}
