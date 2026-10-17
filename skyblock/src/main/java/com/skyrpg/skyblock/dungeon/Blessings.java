package com.skyrpg.skyblock.dungeon;

import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Hypixel's blessings: a solved puzzle, a beaten miniboss and some secret chests bless the whole party. Levels add up
 * (II + III = V). Per level, as a share of your stats in the run: Life +3% Health; Power +2% Strength and Crit Damage;
 * Stone +2% Defense; Wisdom +2% Intelligence and Speed; Time +2% Health, Defense, Strength and Intelligence.
 */
final class Blessings {
    static final List<String> KINDS = List.of("Life", "Power", "Stone", "Wisdom", "Time");
    private static final Map<String, ChatFormatting> COLORS = Map.of("Life", ChatFormatting.RED, "Power", ChatFormatting.DARK_RED, "Stone",
            ChatFormatting.GREEN, "Wisdom", ChatFormatting.AQUA, "Time", ChatFormatting.DARK_GREEN);

    private Blessings() {
    }

    static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Blessings";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                Run run = Catacombs.runOf(player);
                if (run == null || run.blessings.isEmpty() || !player.level().dimension().equals(Catacombs.LEVEL)) {
                    LAST.remove(player.getUUID());
                    return;
                }
                // Each stat's total share first (Life and Time both add Health), then one addition per stat.
                Map<StatType, Double> shares = new java.util.LinkedHashMap<>();
                run.blessings.forEach((kind, level) -> {
                    switch (kind) {
                        case "Life" -> shares.merge(StatTypes.HEALTH, 0.03 * level, Double::sum);
                        case "Power" -> {
                            shares.merge(StatTypes.STRENGTH, 0.02 * level, Double::sum);
                            shares.merge(StatTypes.CRIT_DAMAGE, 0.02 * level, Double::sum);
                        }
                        case "Stone" -> shares.merge(StatTypes.DEFENSE, 0.02 * level, Double::sum);
                        case "Wisdom" -> {
                            shares.merge(StatTypes.INTELLIGENCE, 0.02 * level, Double::sum);
                            shares.merge(StatTypes.SPEED, 0.02 * level, Double::sum);
                        }
                        default -> {
                            for (StatType stat : List.of(StatTypes.HEALTH, StatTypes.DEFENSE, StatTypes.STRENGTH, StatTypes.INTELLIGENCE)) {
                                shares.merge(stat, 0.02 * level, Double::sum);
                            }
                        }
                    }
                });
                double boost = EssencePerks.blessingBoost(player);
                shares.forEach((stat, share) -> share(player, out, stat, share * boost));
            }
        });
    }

    /** What the blessings added last time, so the next count works from the stats without them. */
    private static final Map<java.util.UUID, Map<StatType, Double>> LAST = new java.util.HashMap<>();

    /** Adds a share of the player's other stats (without what blessings added before, so they don't compound). */
    private static void share(ServerPlayer player, Stats.Contributor out, StatType stat, double share) {
        Map<StatType, Double> last = LAST.computeIfAbsent(player.getUUID(), k -> new java.util.HashMap<>());
        double base = Math.max(0, Stats.get(player, stat) - last.getOrDefault(stat, 0.0));
        double added = base * share;
        last.put(stat, added);
        out.add(stat, added);
    }

    /** The level a blessing has on this floor: I on Floor I, up to IV on Floor VII, one more in Master Mode. */
    static int levelFor(Floors.Floor floor) {
        return 1 + floor.number() / 2 + (floor.master() ? 1 : 0);
    }

    /** A random blessing for the whole party. */
    static void bless(Run run, ServerPlayer by) {
        String kind = KINDS.get(run.random.nextInt(KINDS.size()));
        int level = levelFor(run.floor);
        run.blessings.merge(kind, level, Integer::sum);
        run.broadcast(Component.literal("DUNGEON BUFF! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal((by == null ? "The party" : by.getScoreboardName()) + " found a ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("Blessing of " + kind + " " + Floors.roman(level)).withStyle(COLORS.getOrDefault(kind, ChatFormatting.GOLD)))
                .append(Component.literal("!").withStyle(ChatFormatting.WHITE)));
        for (ServerPlayer p : run.online()) {
            Msg.playSound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.6f);
            Stats.refresh(p);
        }
    }
}
