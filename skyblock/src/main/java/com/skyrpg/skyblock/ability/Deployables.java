package com.skyrpg.skyblock.ability;

import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.hub.mixin.ItemDisplayAccess;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.skyblock.crimson.CrimsonIsle;
import com.skyrpg.skyblock.crimson.Kuudra;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's deployables (the wiki's "Deployables", October 2026): Power Orbs float over you, Flares shoot up into the
 * sky, Lanterns and the Umberella hang by you. Each buffs the players nearest it (up to its count, within its range) for
 * its time, all read from the item's description ("Place an orb for 1m buffing up to 5 players within 18 blocks"). Only
 * one deployable buff applies to a player: the best one they're in (orbs over flares over lanterns over the Umberella,
 * then the rarer); a new one of yours replaces your old one.
 *
 * <p>Orbs heal their owner and others each second and give Health Regen, mana regen (more of your mana back each
 * second) and Strength; flares give Vitality, True Defense, Ferocity and the rest, healing only on the Crimson Isle and
 * in Kuudra's Hollow; lanterns give Mining Speed and Fortune and Heat/Cold Resistance; the Umberella Trophy Fish Chance.
 */
final class Deployables {
    private static final String TAG = "skyrpg_deployable";
    private static final Pattern DURATION = Pattern.compile("for (\\d+)\\s*(m|s)\\b");
    private static final Pattern RANGE = Pattern.compile("within (\\d+) blocks");
    private static final Pattern PLAYERS = Pattern.compile("up to (\\d+) players");

    /** What a deployable gives: its rank (the best applies), stats, heals a second (owner, others), mana regen bonus. */
    private record Kind(int rank, Map<StatType, Double> stats, double healSelf, double healOthers, double manaRegen, boolean crimsonHeal,
                        boolean flare, ParticleOptions particle) {
    }

    private static final Map<String, Kind> KINDS = Map.ofEntries(
            Map.entry("umberella", new Kind(1, Map.of(StatTypes.TROPHY_FISH_CHANCE, 5.0), 0, 0, 0, false, false, ParticleTypes.DRIPPING_WATER)),
            Map.entry("lantern", new Kind(2, Map.of(StatTypes.MINING_SPEED, 20.0), 0, 0, 0, false, false, ParticleTypes.SMALL_FLAME)),
            Map.entry("mithril_lantern", new Kind(3, Map.of(StatTypes.MINING_SPEED, 40.0, StatTypes.MINING_FORTUNE, 10.0), 0, 0, 0, false, false,
                    ParticleTypes.SMALL_FLAME)),
            Map.entry("titanium_lantern", new Kind(4, Map.of(StatTypes.MINING_SPEED, 60.0, StatTypes.MINING_FORTUNE, 15.0, StatTypes.HEAT_RESISTANCE, 5.0),
                    0, 0, 0, false, false, ParticleTypes.SMALL_FLAME)),
            Map.entry("glacite_lantern", new Kind(5, Map.of(StatTypes.MINING_SPEED, 80.0, StatTypes.MINING_FORTUNE, 20.0, StatTypes.HEAT_RESISTANCE, 10.0,
                    StatTypes.COLD_RESISTANCE, 5.0), 0, 0, 0, false, false, ParticleTypes.SNOWFLAKE)),
            Map.entry("will_o_wisp", new Kind(6, Map.of(StatTypes.MINING_SPEED, 100.0, StatTypes.MINING_FORTUNE, 25.0, StatTypes.HEAT_RESISTANCE, 20.0,
                    StatTypes.COLD_RESISTANCE, 10.0), 0, 0, 0, false, false, ParticleTypes.SOUL_FIRE_FLAME)),
            Map.entry("warning_flare", new Kind(7, Map.of(StatTypes.VITALITY, 10.0, StatTypes.TRUE_DEFENSE, 10.0), 30, 30, 0, true, true,
                    ParticleTypes.FLAME)),
            Map.entry("alert_flare", new Kind(8, Map.of(StatTypes.VITALITY, 15.0, StatTypes.TRUE_DEFENSE, 20.0, StatTypes.FEROCITY, 10.0), 40, 40, 0.5,
                    true, true, ParticleTypes.FLAME)),
            Map.entry("sos_flare", new Kind(9, Map.of(StatTypes.VITALITY, 25.0, StatTypes.TRUE_DEFENSE, 30.0, StatTypes.FEROCITY, 10.0,
                    StatTypes.ATTACK_SPEED, 5.0), 50, 50, 1.25, true, true, ParticleTypes.SOUL_FIRE_FLAME)),
            Map.entry("radiant_power_orb", new Kind(10, Map.of(StatTypes.HEALTH_REGEN, 25.0), 30, 15, 0, false, false, ParticleTypes.HAPPY_VILLAGER)),
            Map.entry("mana_flux_power_orb", new Kind(11, Map.of(StatTypes.HEALTH_REGEN, 50.0, StatTypes.STRENGTH, 10.0), 40, 20, 0.5, false, false,
                    ParticleTypes.ENCHANT)),
            Map.entry("overflux_power_orb", new Kind(12, Map.of(StatTypes.HEALTH_REGEN, 100.0, StatTypes.STRENGTH, 25.0), 50, 25, 1.0, false, false,
                    ParticleTypes.WITCH)),
            Map.entry("plasmaflux_power_orb", new Kind(13, Map.of(StatTypes.HEALTH_REGEN, 125.0, StatTypes.STRENGTH, 35.0), 60, 30, 1.25, false, false,
                    ParticleTypes.END_ROD)));

    private record Placed(UUID owner, Kind kind, ServerLevel level, Vec3 at, double range, int players, long until, Display.ItemDisplay display) {
    }

    /** Owner -> their deployable out now. */
    private static final Map<UUID, Placed> PLACED = new HashMap<>();
    /** Player -> the deployable buff they have now. */
    private static final Map<UUID, Kind> BUFFED = new HashMap<>();
    private static final Set<Display.ItemDisplay> LIVE = new HashSet<>();

    private Deployables() {
    }

    static void init() {
        Abilities.register("deploy", Deployables::deploy);
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Deployable";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                Kind kind = BUFFED.get(player.getUUID());
                if (kind != null) {
                    kind.stats().forEach(out::add);
                }
            }
        });
        // displays left in the world from before a restart
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && !(entity instanceof Display.ItemDisplay d && LIVE.contains(d))) {
                level.getServer().execute(entity::discard);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Deployables::tick);
    }

    private static boolean deploy(ServerPlayer player, ItemDefinition.Ability ability) {
        ItemStack held = player.getMainHandItem();
        ItemDefinition def = SkyItems.definition(held);
        Kind kind = def == null ? null : KINDS.get(def.id().getPath());
        if (kind == null) {
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getServer().getTickCount();
        String text = ability.description();
        Matcher d = DURATION.matcher(text);
        long seconds = d.find() ? Long.parseLong(d.group(1)) * (d.group(2).equals("m") ? 60 : 1) : 60;
        Matcher r = RANGE.matcher(text);
        double range = r.find() ? Double.parseDouble(r.group(1)) : 18;
        Matcher n = PLAYERS.matcher(text);
        int players = n.find() ? Integer.parseInt(n.group(1)) : 5;
        remove(PLACED.remove(player.getUUID()));
        // orbs and lanterns float a step in front of you at chest height; flares hang high in the sky
        Vec3 ahead = new Vec3(player.getLookAngle().x, 0, player.getLookAngle().z).normalize().scale(kind.flare() ? 0 : 1.5);
        Vec3 at = player.position().add(ahead).add(0, kind.flare() ? 8 : 1.3, 0);
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        ((ItemDisplayAccess) display).skyrpg$setItemStack(held.copyWithCount(1));
        display.snapTo(at.x, at.y, at.z, 0, 0);
        display.addTag(TAG);
        LIVE.add(display);
        level.addFreshEntity(display);
        PLACED.put(player.getUUID(), new Placed(player.getUUID(), kind, level, at, range, players, now + seconds * 20, display));
        level.playSound(null, player.blockPosition(), kind.flare() ? SoundEvents.FIREWORK_ROCKET_LAUNCH : SoundEvents.BEACON_ACTIVATE,
                SoundSource.PLAYERS, 1.0f, 1.2f);
        if (kind.flare()) {
            level.sendParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1, player.getZ(), 30, 0.1, 3, 0.1, 0.05);
        }
        return true;
    }

    private static void remove(Placed placed) {
        if (placed != null) {
            LIVE.remove(placed.display());
            placed.display().discard();
        }
    }

    private static void tick(MinecraftServer server) {
        if (PLACED.isEmpty() && BUFFED.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (Placed placed : PLACED.values()) {
            // a slow spin, and its glow
            placed.display().snapTo(placed.at().x, placed.at().y + Math.sin(now / 10.0) * 0.1, placed.at().z, (now * 6) % 360, 0);
            if (now % 4 == 0) {
                placed.level().sendParticles(placed.kind().particle(), placed.at().x, placed.at().y, placed.at().z, 2, 0.3, 0.3, 0.3, 0.01);
            }
        }
        if (now % 20 != 0) {
            return;
        }
        PLACED.values().removeIf(placed -> {
            if (now >= placed.until() || server.getPlayerList().getPlayer(placed.owner()) == null) {
                remove(placed);
                return true;
            }
            return false;
        });
        // each player's best deployable in reach (each deployable reaching its nearest few)
        Map<UUID, Placed> best = new HashMap<>();
        for (Placed placed : PLACED.values()) {
            List<ServerPlayer> near = new ArrayList<>(placed.level().players().stream()
                    .filter(p -> p.isAlive() && p.position().distanceTo(placed.at()) <= placed.range()).toList());
            near.sort(Comparator.comparingDouble(p -> p.position().distanceToSqr(placed.at())));
            for (ServerPlayer p : near.subList(0, Math.min(near.size(), placed.players()))) {
                Placed had = best.get(p.getUUID());
                if (had == null || placed.kind().rank() > had.kind().rank()) {
                    best.put(p.getUUID(), placed);
                }
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Placed placed = best.get(player.getUUID());
            Kind kind = placed == null ? null : placed.kind();
            if (kind != BUFFED.get(player.getUUID())) {
                if (kind == null) {
                    BUFFED.remove(player.getUUID());
                } else {
                    BUFFED.put(player.getUUID(), kind);
                }
                Stats.refresh(player);
            }
            if (kind == null) {
                continue;
            }
            boolean owner = placed.owner().equals(player.getUUID());
            boolean heals = !kind.crimsonHeal() || player.level().dimension() == CrimsonIsle.LEVEL || player.level().dimension() == Kuudra.LEVEL;
            double heal = heals ? (owner ? kind.healSelf() : kind.healOthers()) : 0;
            if (heal > 0) {
                player.heal((float) (heal / StatTypes.HEALTH_SCALE));
            }
            if (kind.manaRegen() > 0) {
                // the base 2% of max mana a second, this much more ("+50% base mana regen"); a negative spend gives mana
                double max = 100 + Math.max(0, Stats.get(player, StatTypes.INTELLIGENCE));
                Stats.spendMana(player, -max * 0.02 * kind.manaRegen());
            }
        }
    }
}
