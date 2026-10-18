package com.skyrpg.skyblock.galatea;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hunting (Hypixel's 2025 skill): shards caught go into your Hunting Box; syphon them into their attribute, levels I-X
 * (Hypixel's shards a level by rarity: Common 1, 3, 5, 6, 7, 8, 10, 14, 18, 24 ... Legendary 1, 1, 1, 2, 2, 2, 3, 3, 4,
 * 5). The 325 shards come from NEU (tools/skyblock/import_shards.py); the ones whose effect is a plain stat work as that
 * stat (level x the level-I amount), the rest show their effect. A shard caught is Hunting XP (Common 75, Uncommon 150,
 * Rare 300, Epic 500, Legendary 1,000); Hunting Fortune catches more at once. Syphoning needs Hunting 0 / 5 / 10 / 15 / 20
 * by rarity.
 */
public final class Hunting {
    public record Shard(String id, String key, String name, String attribute, String rarity, String alignment, List<String> family, String effect,
                        String stat, double perLevel) {
    }

    static final Map<String, Shard> SHARDS = new LinkedHashMap<>();
    static final Map<String, int[]> LEVELLING = new HashMap<>();
    static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY");
    static final int[] RARITY_COLORS = {0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00};
    static final long[] XP = {75, 150, 300, 500, 1000};
    static final int[] HUNTING_NEEDED = {0, 5, 10, 15, 20};

    /** Shards waiting in your Hunting Box, by key. */
    static final AttachmentType<Map<String, Long>> BOX = AttachmentRegistry.create(SkyRpgSkyblock.id("hunting_box"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());
    /** Each attribute's level (by shard key); a negative level is switched off. */
    static final AttachmentType<Map<String, Integer>> LEVELS = AttachmentRegistry.create(SkyRpgSkyblock.id("hunting_attributes"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    private Hunting() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("hunting_shards"), "skyrpg/hunting", Hunting::load);
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Attributes";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                for (Map.Entry<String, Integer> e : player.getAttachedOrElse(LEVELS, Map.of()).entrySet()) {
                    Shard shard = SHARDS.get(e.getKey());
                    if (shard == null || shard.stat() == null || e.getValue() <= 0) {
                        continue;
                    }
                    StatType stat = StatTypes.get(shard.stat());
                    if (stat != null) {
                        out.add(stat, shard.perLevel() * e.getValue());
                    }
                }
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal("huntingbox").executes(ctx -> {
                open(ctx.getSource().getPlayerOrException(), 0);
                return 1;
            }));
            dispatcher.register(Commands.literal("sbshard").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.argument("key", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(ctx -> {
                                Shard shard = SHARDS.get(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "key").toUpperCase(java.util.Locale.ROOT));
                                if (shard != null) {
                                    catchShard(ctx.getSource().getPlayerOrException(), shard,
                                            com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"), "a command");
                                }
                                return 1;
                            }))));
        });
    }

    private static void load(Map<Identifier, JsonElement> files) {
        SHARDS.clear();
        LEVELLING.clear();
        files.forEach((id, element) -> {
            JsonObject root = element.getAsJsonObject();
            if (root.has("levelling")) {
                root.getAsJsonObject("levelling").entrySet().forEach(e -> {
                    JsonArray steps = e.getValue().getAsJsonArray();
                    int[] need = new int[steps.size()];
                    for (int i = 0; i < need.length; i++) {
                        need[i] = steps.get(i).getAsInt();
                    }
                    LEVELLING.put(e.getKey(), need);
                });
            }
            if (root.has("shards")) {
                for (JsonElement el : root.getAsJsonArray("shards")) {
                    JsonObject s = el.getAsJsonObject();
                    List<String> family = new ArrayList<>();
                    s.getAsJsonArray("family").forEach(f -> family.add(f.getAsString()));
                    Shard shard = new Shard(s.get("id").getAsString(), s.get("key").getAsString(), s.get("name").getAsString(),
                            s.get("attribute").getAsString(), s.get("rarity").getAsString(), s.get("alignment").getAsString(), List.copyOf(family),
                            s.get("effect").getAsString(), s.has("stat") ? s.get("stat").getAsString() : null,
                            s.has("per_level") ? s.get("per_level").getAsDouble() : 0);
                    SHARDS.put(shard.key(), shard);
                }
            }
        });
        SkyRpgSkyblock.LOGGER.info("Loaded {} Hunting shards", SHARDS.size());
    }

    /** The shard named like this (a critter's or mob's name), or null. */
    static Shard byName(String name) {
        for (Shard shard : SHARDS.values()) {
            if (shard.name().equalsIgnoreCase(name)) {
                return shard;
            }
        }
        return null;
    }

    static int rarity(Shard shard) {
        return Math.max(0, RARITIES.indexOf(shard.rarity()));
    }

    static long box(ServerPlayer player, String key) {
        return player.getAttachedOrElse(BOX, Map.of()).getOrDefault(key, 0L);
    }

    private static void addBox(ServerPlayer player, String key, long n) {
        Map<String, Long> box = new HashMap<>(player.getAttachedOrElse(BOX, Map.of()));
        box.merge(key, n, Long::sum);
        if (box.get(key) <= 0) {
            box.remove(key);
        }
        player.setAttached(BOX, Map.copyOf(box));
    }

    static int level(ServerPlayer player, String key) {
        return player.getAttachedOrElse(LEVELS, Map.of()).getOrDefault(key, 0);
    }

    /** A shard caught: into the box (Hunting Fortune for extras), Hunting XP for each. */
    public static void catchShard(ServerPlayer player, Shard shard, int base, String how) {
        double fortune = Stats.get(player, StatTypes.HUNTING_FORTUNE);
        int count = base + (int) (fortune / 100) + (player.getRandom().nextDouble() < (fortune % 100) / 100 ? 1 : 0);
        addBox(player, shard.key(), count);
        int r = rarity(shard);
        SkillLevels.giveXp(player, SkillLevels.HUNTING, XP[r] * count);
        player.sendSystemMessage(Component.literal("HUNT! ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal("+" + count + " ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(shard.name() + " Shard").withColor(RARITY_COLORS[r]))
                .append(Component.literal(" (" + how + ")").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.AMETHYST_CLUSTER_PLACE, 1f, 1.2f + r * 0.1f);
    }

    /** Syphons box shards into the attribute, as many levels as they cover. */
    static String syphon(ServerPlayer player, Shard shard) {
        int r = rarity(shard);
        if (SkillLevels.level(player, SkillLevels.HUNTING) < HUNTING_NEEDED[r]) {
            return "Syphoning a " + shard.rarity().toLowerCase(java.util.Locale.ROOT) + " shard needs Hunting " + HUNTING_NEEDED[r] + ".";
        }
        int[] need = LEVELLING.getOrDefault(shard.rarity(), new int[] {1});
        int level = Math.abs(level(player, shard.key()));
        int gained = 0;
        while (level < need.length && box(player, shard.key()) >= need[level]) {
            addBox(player, shard.key(), -need[level]);
            level++;
            gained++;
        }
        if (gained == 0) {
            return level >= need.length ? "That attribute is maxed." : "You need " + need[level] + " " + shard.name() + " Shards for the next level.";
        }
        Map<String, Integer> levels = new HashMap<>(player.getAttachedOrElse(LEVELS, Map.of()));
        levels.put(shard.key(), level);
        player.setAttached(LEVELS, Map.copyOf(levels));
        Stats.refresh(player);
        player.sendSystemMessage(Component.literal(shard.attribute() + " " + Msg.roman(level) + "!").withColor(RARITY_COLORS[r]));
        Msg.playSound(player, SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        return "";
    }

    /** The Hunting Box: every shard you've caught, its attribute and level, a page at a time. */
    static void open(ServerPlayer player, int page) {
        CardMenu.open(player, p -> {
            List<Shard> owned = owned(p);
            int pages = Math.max(1, (owned.size() + 11) / 12);
            int at = Math.min(page, pages - 1);
            List<CardView.Card> cards = new ArrayList<>();
            for (Shard shard : owned.subList(at * 12, Math.min(owned.size(), at * 12 + 12))) {
                int r = rarity(shard);
                int level = Math.abs(level(p, shard.key()));
                int[] need = LEVELLING.getOrDefault(shard.rarity(), new int[] {1});
                long have = box(p, shard.key());
                boolean maxed = level >= need.length;
                String effect = shard.stat() != null && level > 0 ? "+" + Msg.number(shard.perLevel() * level) + " " + StatTypes.get(shard.stat()).name()
                        : shard.effect();
                // (a white title would show the icon's own name instead)
                cards.add(new CardView.Card(shard.name() + " (" + shard.id() + ")", shard.attribute() + (level > 0 ? " " + Msg.roman(level) : "") + ": " + effect,
                        r == 0 ? 0xEEEEEE : RARITY_COLORS[r], !maxed && have >= need[Math.min(level, need.length - 1)], maxed ? "Max" : "Syphon",
                        icon(shard), "", maxed ? List.of() : List.of(new Chip("", ItemStack.EMPTY, have + "/" + need[level], have >= need[level])),
                        level / (float) need.length, level + "/" + need.length));
            }
            if (pages > 1) {
                cards.add(new CardView.Card("Page " + (at + 1) + " of " + pages, "Next page", 0xAAAAAA, true, "Next", new ItemStack(Items.ARROW), "",
                        List.of(), -1, ""));
            }
            if (owned.isEmpty()) {
                cards.add(new CardView.Card("Empty", "Catch shards on Galatea: lasso critters, net water creatures, charm mobs.", 0xAAAAAA, false, "",
                        new ItemStack(Items.LEAD), "", List.of(), -1, ""));
            }
            long waiting = p.getAttachedOrElse(BOX, Map.of()).values().stream().mapToLong(Long::longValue).sum();
            return new CardView("GALATEA", "Hunting Box", owned.size() + (owned.size() == 1 ? " kind" : " kinds") + " of shard", -1,
                    List.of(Chip.note(new ItemStack(Items.LEAD), "Hunting " + SkillLevels.level(p, SkillLevels.HUNTING), true),
                            Chip.note(new ItemStack(Items.PRISMARINE_SHARD), Msg.number(waiting) + (waiting == 1 ? " shard" : " shards") + " waiting", true),
                            Chip.note(new ItemStack(Items.RABBIT_FOOT), Msg.number(Stats.get(p, StatTypes.HUNTING_FORTUNE)) + " Hunting Fortune", true),
                            Chip.note(new ItemStack(Items.AMETHYST_SHARD), Msg.number(Stats.get(p, StatTypes.CHARM_CHANCE)) + "% Charm", true)),
                    cards,
                    "Syphon shards into their attribute: each level needs more of them.", 1, 34);
        }, (p, card) -> {
            List<Shard> owned = owned(p);
            int pages = Math.max(1, (owned.size() + 11) / 12);
            int at = Math.min(page, pages - 1);
            int index = at * 12 + card;
            if (card == Math.min(12, owned.size() - at * 12) && pages > 1) {
                open(p, (at + 1) % pages);
                return null;
            }
            if (index >= owned.size()) {
                return "";
            }
            return syphon(p, owned.get(index));
        });
    }

    /** A shard's icon by its alignment: forest shards amethyst, water ones prismarine, the rest echo shards. */
    private static ItemStack icon(Shard shard) {
        return new ItemStack(switch (shard.alignment()) {
            case "Forest" -> Items.AMETHYST_SHARD;
            case "Water" -> Items.PRISMARINE_SHARD;
            default -> Items.ECHO_SHARD;
        });
    }

    private static List<Shard> owned(ServerPlayer player) {
        Map<String, Long> box = player.getAttachedOrElse(BOX, Map.of());
        Map<String, Integer> levels = player.getAttachedOrElse(LEVELS, Map.of());
        return SHARDS.values().stream().filter(s -> box.containsKey(s.key()) || levels.getOrDefault(s.key(), 0) != 0).toList();
    }
}
