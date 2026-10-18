package com.skyrpg.skyblock.garden;

import com.mojang.serialization.Codec;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.time.CalendarEvents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Jacob's Farming Contests, on the calendar's schedule (every third SkyBlock day, 20 real minutes): three of the ten
 * Garden crops (drawn from the contest's start). You compete in the first of them you harvest on your Garden (Farming X).
 * At the end, everyone with at least 100 is ranked: with ten or more in a crop, by Hypixel's brackets (Diamond top 2%,
 * Platinum 5%, Gold 10%, Silver 30%, Bronze 60%); with fewer, by score (decided: 5,000 / 15,000 / 40,000 / 70,000 /
 * 100,000 Wheat-equivalent crops for Bronze to Diamond). Rewards, claimed from Jacob: Jacob's Tickets (1, then 10 / 15 /
 * 25 / 30 / 35) and medals (Bronze, Silver, Gold; Platinum adds a Bronze, Diamond a Silver) to spend at Anita.
 */
public final class Contests {
    static final String[] BRACKETS = {"Participation", "Bronze", "Silver", "Gold", "Platinum", "Diamond"};
    private static final int[] TICKETS = {1, 10, 15, 25, 30, 35};
    private static final double[] TOP = {1.0, 0.60, 0.30, 0.10, 0.05, 0.02};
    private static final long[] SCORE = {100, 5_000, 15_000, 40_000, 70_000, 100_000};
    private static final String[] COLORS = {"§e", "§c", "§f", "§6", "§3", "§b"};

    /** Medals and tickets waiting to be claimed, medals spent at Anita: "tickets", "bronze", "silver", "gold", "pending_*". */
    static final AttachmentType<Map<String, Integer>> MEDALS = AttachmentRegistry.create(SkyRpgSkyblock.id("jacob_medals"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());
    /** The sidebar's contest line (synced to its player). */
    public static final AttachmentType<String> SIDEBAR = AttachmentRegistry.create(SkyRpgSkyblock.id("jacob_sidebar"),
            builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));

    /** The contest running: when it started and ends, its crops, everyone's crop and score. */
    private static long start;
    private static long end;
    private static List<String> crops = List.of();
    private static final Map<UUID, String> ENTERED = new HashMap<>();
    private static final Map<UUID, Long> SCORES = new HashMap<>();

    private Contests() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Contests::tick);
    }

    static boolean running() {
        return System.currentTimeMillis() < end && start > 0;
    }

    static List<String> crops() {
        return crops;
    }

    /** The current or next contest's crops (from its start time). */
    static List<String> cropsFor(long startMillis) {
        List<String> all = new ArrayList<>();
        for (Garden.Crop crop : Garden.CROPS) {
            all.add(crop.key());
        }
        java.util.Collections.shuffle(all, new Random(startMillis));
        return List.copyOf(all.subList(0, 3));
    }

    static String cropName(String key) {
        return Garden.CROPS.stream().filter(c -> c.key().equals(key)).map(Garden.Crop::name).findFirst().orElse(key);
    }

    /** A harvested Garden crop counts in a running contest (your first crop of it). */
    static void harvested(ServerPlayer player, Garden.Crop crop, long count) {
        if (!running() || !crops.contains(crop.key()) || SkillLevels.level(player, SkillLevels.FARMING) < 10) {
            return;
        }
        String entered = ENTERED.get(player.getUUID());
        if (entered == null) {
            ENTERED.put(player.getUUID(), crop.key());
            player.sendSystemMessage(Component.literal("You entered Jacob's Farming Contest with " + crop.name() + "!").withStyle(ChatFormatting.YELLOW));
            entered = crop.key();
        }
        if (entered.equals(crop.key())) {
            SCORES.merge(player.getUUID(), count, Long::sum);
        }
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        CalendarEvents.Occurrence active = CalendarEvents.upcoming(now, 0).stream()
                .filter(o -> o.event().name().equals("Jacob's Farming Contest") && o.active(now)).findFirst().orElse(null);
        if (active != null && active.start() != start) {
            if (running()) {
                finish(server);
            }
            start = active.start();
            end = active.end();
            crops = cropsFor(start);
            ENTERED.clear();
            SCORES.clear();
            announce(server);
        } else if (start > 0 && now >= end) {
            finish(server);
        }
        // Sidebar lines.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String line = null;
            if (running() && Garden.isGarden(player.level())) {
                String crop = ENTERED.get(player.getUUID());
                long score = SCORES.getOrDefault(player.getUUID(), 0L);
                long left = (end - now) / 1000;
                StringBuilder text = new StringBuilder("§eJacob's Contest §a" + left / 60 + ":" + String.format("%02d", left % 60));
                if (crop == null) {
                    crops.forEach(c -> text.append("\n§7○ §f").append(cropName(c)));
                } else {
                    text.append("\n§7○ §f").append(cropName(crop));
                    text.append(ranked(crop, score) ? "\n " + COLORS[bracket(crop, score)] + BRACKETS[bracket(crop, score)] + " §7with §e"
                            + Msg.number(score) : "\n §7Collected §e" + Msg.number(score));
                }
                line = text.toString();
            }
            if (line == null) {
                if (player.getAttached(SIDEBAR) != null) {
                    player.removeAttached(SIDEBAR);
                }
            } else if (!line.equals(player.getAttached(SIDEBAR))) {
                player.setAttached(SIDEBAR, line);
            }
        }
    }

    private static void announce(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Notice.of(Notice.Kind.FARMING).tag("JACOB'S CONTEST").send(player, Component.literal("Started! "
                    + String.join(", ", crops.stream().map(Contests::cropName).toList()) + " for 20 minutes.").withStyle(ChatFormatting.YELLOW));
        }
    }

    /** Whether a score has reached the 100 crops a reward needs. */
    private static boolean ranked(String crop, long score) {
        return Garden.CROPS.stream().filter(c -> c.key().equals(crop)).anyMatch(c -> score >= SCORE[0] * c.perBlock());
    }

    /** The bracket a score is in (by score; at the end, by standing when ten or more took part). */
    private static int bracket(String crop, long score) {
        Garden.Crop c = Garden.CROPS.stream().filter(x -> x.key().equals(crop)).findFirst().orElse(Garden.CROPS.get(0));
        int bracket = -1;
        for (int i = 0; i < SCORE.length; i++) {
            if (score >= SCORE[i] * c.perBlock()) {
                bracket = i;
            }
        }
        return Math.max(0, bracket);
    }

    private static void finish(MinecraftServer server) {
        Map<String, List<Map.Entry<UUID, Long>>> byCrop = new HashMap<>();
        for (Map.Entry<UUID, Long> e : SCORES.entrySet()) {
            Garden.Crop crop = Garden.CROPS.stream().filter(c -> c.key().equals(ENTERED.get(e.getKey()))).findFirst().orElse(null);
            if (crop != null && e.getValue() >= 100 * crop.perBlock()) {
                byCrop.computeIfAbsent(crop.key(), k -> new ArrayList<>()).add(e);
            }
        }
        for (Map.Entry<String, List<Map.Entry<UUID, Long>>> entry : byCrop.entrySet()) {
            List<Map.Entry<UUID, Long>> ranked = entry.getValue();
            ranked.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            for (int i = 0; i < ranked.size(); i++) {
                UUID id = ranked.get(i).getKey();
                int bracket = bracket(entry.getKey(), ranked.get(i).getValue());
                if (ranked.size() >= 10) {
                    double share = (i + 1) / (double) ranked.size();
                    int byStanding = 0;
                    for (int b = TOP.length - 1; b >= 1; b--) {
                        if (share <= TOP[b]) {
                            byStanding = b;
                            break;
                        }
                    }
                    bracket = Math.max(bracket, byStanding);
                }
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    reward(player, entry.getKey(), ranked.get(i).getValue(), bracket, i + 1, ranked.size());
                }
            }
        }
        start = 0;
        end = 0;
        ENTERED.clear();
        SCORES.clear();
    }

    private static void reward(ServerPlayer player, String crop, long score, int bracket, int place, int of) {
        Map<String, Integer> medals = new HashMap<>(player.getAttachedOrElse(MEDALS, Map.of()));
        medals.merge("pending_tickets", TICKETS[bracket], Integer::sum);
        switch (bracket) {
            case 1 -> medals.merge("pending_bronze", 1, Integer::sum);
            case 2 -> medals.merge("pending_silver", 1, Integer::sum);
            case 3 -> medals.merge("pending_gold", 1, Integer::sum);
            case 4 -> {
                medals.merge("pending_gold", 1, Integer::sum);
                medals.merge("pending_bronze", 1, Integer::sum);
            }
            case 5 -> {
                medals.merge("pending_gold", 1, Integer::sum);
                medals.merge("pending_silver", 1, Integer::sum);
            }
            default -> {
            }
        }
        medals.merge("best_" + crop, 0, (a, b) -> (int) Math.max(a, Math.min(Integer.MAX_VALUE, score)));
        player.setAttached(MEDALS, Map.copyOf(medals));
        Notice.of(Notice.Kind.FARMING).tag("JACOB'S CONTEST").panel()
                .line(Component.literal(" " + cropName(crop) + ": " + Msg.number(score) + "  ·  #" + place + " of " + of).withStyle(ChatFormatting.GRAY))
                .line(Component.literal(" Claim your rewards from Jacob.").withStyle(ChatFormatting.GRAY))
                .send(player, Component.literal(BRACKETS[bracket] + "!").withStyle(ChatFormatting.GOLD));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1f);
    }

    /** Jacob hands over what's waiting: tickets as items, medals to spend at Anita. */
    static String claim(ServerPlayer player) {
        Map<String, Integer> medals = new HashMap<>(player.getAttachedOrElse(MEDALS, Map.of()));
        int tickets = medals.getOrDefault("pending_tickets", 0);
        int bronze = medals.getOrDefault("pending_bronze", 0);
        int silver = medals.getOrDefault("pending_silver", 0);
        int gold = medals.getOrDefault("pending_gold", 0);
        if (tickets + bronze + silver + gold == 0) {
            return "Nothing to claim. Take part in a contest!";
        }
        ItemDefinition def = ItemRegistry.skyblock("JACOBS_TICKET");
        if (def != null && tickets > 0) {
            ItemStack stack = SkyItems.create(def, tickets);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        medals.remove("pending_tickets");
        medals.remove("pending_bronze");
        medals.remove("pending_silver");
        medals.remove("pending_gold");
        medals.merge("bronze", bronze, Integer::sum);
        medals.merge("silver", silver, Integer::sum);
        medals.merge("gold", gold, Integer::sum);
        player.setAttached(MEDALS, Map.copyOf(medals));
        List<String> got = new ArrayList<>();
        if (gold > 0) {
            got.add(gold + " Gold");
        }
        if (silver > 0) {
            got.add(silver + " Silver");
        }
        if (bronze > 0) {
            got.add(bronze + " Bronze");
        }
        player.sendSystemMessage(Component.literal("Claimed " + tickets + " Jacob's Ticket" + (tickets == 1 ? "" : "s") + (got.isEmpty() ? "" : " and "
                + String.join(", ", got) + " medal" + (gold + silver + bronze == 1 ? "" : "s")) + ".").withStyle(ChatFormatting.GREEN));
        return "";
    }

    static int medals(ServerPlayer player, String kind) {
        return player.getAttachedOrElse(MEDALS, Map.of()).getOrDefault(kind, 0);
    }

    /** Anita's shop (NEU's prices): tickets and medals for farming gear. */
    record Sale(String item, int count, int tickets, int bronze, int silver, int gold, String needs) {
    }

    static final List<Sale> ANITA = List.of(new Sale("INFINIDIRT_WAND", 1, 1, 0, 0, 0, ""), new Sale("PRISMAPUMP", 4, 2, 1, 0, 0, ""),
            new Sale("HOE_OF_GREAT_TILLING", 1, 5, 1, 0, 0, ""), new Sale("HOE_OF_GREATER_TILLING", 1, 10, 0, 1, 0, ""),
            new Sale("HOE_OF_GREATEST_TILLING", 1, 20, 0, 2, 0, ""), new Sale("BASKET_OF_SEEDS", 1, 30, 0, 2, 0, ""),
            new Sale("NETHER_WART_POUCH", 1, 30, 0, 2, 0, ""), new Sale("ANITA_TALISMAN", 1, 32, 2, 0, 0, ""),
            new Sale("ANITA_RING", 1, 64, 0, 2, 0, "ANITA_TALISMAN"), new Sale("ANITA_ARTIFACT", 1, 100, 0, 0, 2, "ANITA_RING"));

    static String price(Sale sale) {
        List<String> parts = new ArrayList<>();
        parts.add(sale.tickets() + (sale.tickets() == 1 ? " ticket" : " tickets"));
        if (sale.gold() > 0) {
            parts.add(sale.gold() + " Gold");
        }
        if (sale.silver() > 0) {
            parts.add(sale.silver() + " Silver");
        }
        if (sale.bronze() > 0) {
            parts.add(sale.bronze() + " Bronze");
        }
        if (!sale.needs().isEmpty()) {
            parts.add("your " + Visitors.name(sale.needs()));
        }
        return String.join(" + ", parts);
    }

    static String buy(ServerPlayer player, int row) {
        if (row == ANITA.size() || row == ANITA.size() + 1) {
            // Medals trade down: a Gold for 2 Silver, a Silver for 2 Bronze.
            String from = row == ANITA.size() ? "gold" : "silver";
            String to = row == ANITA.size() ? "silver" : "bronze";
            if (medals(player, from) < 1) {
                return "You need a " + (from.equals("gold") ? "Gold" : "Silver") + " medal.";
            }
            Map<String, Integer> medals = new HashMap<>(player.getAttachedOrElse(MEDALS, Map.of()));
            medals.merge(from, -1, Integer::sum);
            medals.merge(to, 2, Integer::sum);
            player.setAttached(MEDALS, Map.copyOf(medals));
            player.sendSystemMessage(Component.literal("Traded a " + (from.equals("gold") ? "Gold" : "Silver") + " medal for 2 "
                    + (to.equals("silver") ? "Silver" : "Bronze") + " medals.").withStyle(ChatFormatting.GREEN));
            return "";
        }
        if (row < 0 || row >= ANITA.size()) {
            return "";
        }
        Sale sale = ANITA.get(row);
        ItemDefinition def = ItemRegistry.skyblock(sale.item());
        if (def == null) {
            return "";
        }
        if (com.skyrpg.skyblock.sack.Sacks.countAnywhere(player, "JACOBS_TICKET") < sale.tickets() || medals(player, "bronze") < sale.bronze()
                || medals(player, "silver") < sale.silver() || medals(player, "gold") < sale.gold()
                || !sale.needs().isEmpty() && com.skyrpg.skyblock.sack.Sacks.countAnywhere(player, sale.needs()) < 1) {
            return "You need " + price(sale) + ".";
        }
        com.skyrpg.skyblock.sack.Sacks.takeAnywhere(player, "JACOBS_TICKET", sale.tickets());
        if (!sale.needs().isEmpty()) {
            com.skyrpg.skyblock.sack.Sacks.takeAnywhere(player, sale.needs(), 1);
        }
        Map<String, Integer> medals = new HashMap<>(player.getAttachedOrElse(MEDALS, Map.of()));
        medals.merge("bronze", -sale.bronze(), Integer::sum);
        medals.merge("silver", -sale.silver(), Integer::sum);
        medals.merge("gold", -sale.gold(), Integer::sum);
        player.setAttached(MEDALS, Map.copyOf(medals));
        ItemStack stack = SkyItems.create(def, sale.count());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        player.sendSystemMessage(Component.literal("Bought " + def.name() + ".").withStyle(ChatFormatting.GREEN));
        return "";
    }

    /** Admin: start a contest now for 20 minutes. */
    static void startNow(MinecraftServer server) {
        start = System.currentTimeMillis();
        end = start + 20 * 60 * 1000L;
        crops = cropsFor(start);
        ENTERED.clear();
        SCORES.clear();
        announce(server);
    }

    /** Admin: add to your score in the running contest (entering its first crop if you haven't entered). */
    static void addScore(ServerPlayer player, long amount) {
        if (running()) {
            ENTERED.putIfAbsent(player.getUUID(), crops.get(0));
            SCORES.merge(player.getUUID(), amount, Long::sum);
        }
    }

    static void endNow(MinecraftServer server) {
        if (running()) {
            finish(server);
        }
    }
}
