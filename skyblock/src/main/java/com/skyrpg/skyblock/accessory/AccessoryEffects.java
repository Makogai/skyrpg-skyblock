package com.skyrpg.skyblock.accessory;

import com.skyrpg.combat.MobLevels;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.skyblock.fishing.SkySeaCreatures;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * What accessories do besides their stats (Hypixel's lore, for the ones that are simple to make real), from the bag or
 * the inventory, the highest of a line only:
 * <ul>
 *   <li>Wards: the Zombie, Skeleton, Spider, Wolf, Blaze/Nether, Ender, Wither and Sea Creature accessories cut the
 *       damage mobs of their kind deal you.</li>
 *   <li>Feather Talisman, Ring and Artifact: fall that many blocks further before it hurts (the Ring and Artifact also
 *       cut what still lands by 5% and 15%).</li>
 *   <li>Haste Ring and Artifact: permanent Haste II and III. Night Vision Charm: Night Vision. Fire Talisman: no fire damage.</li>
 *   <li>Scavenger Talisman, Ring and Artifact: coins per level of what you kill.</li>
 * </ul>
 * The slayer-level requirements some of Hypixel's rings and artifacts have aren't checked.
 */
public final class AccessoryEffects {
    /** One effect. {@code kind} is "ward", "feather", "haste", "night_vision", "fire" or "scavenger"; {@code group} the mob kind of a ward. */
    public record Effect(String kind, String group, double value) {
        /** The tooltip's words for it. */
        String text() {
            return switch (kind) {
                case "ward" -> group.equals("sea") ? "Take " + pct() + "% less damage from Sea Creatures."
                        : "Reduces the damage taken from " + groupName() + " mobs by " + pct() + "%.";
                case "feather" -> "Increases how high you can fall before taking fall damage by " + (int) value + " blocks."
                        + (group.isEmpty() ? "" : " Fall damage is also reduced by " + group + "%.");
                case "haste" -> "Grants permanent Haste " + (value >= 3 ? "III" : "II") + ".";
                case "night_vision" -> "Grants Night Vision while this item is in your inventory.";
                case "fire" -> "Provides immunity against damage from Fire.";
                default -> "Grants +" + value + " coins per level of the killed enemy.";
            };
        }

        private String pct() {
            return String.valueOf((int) Math.round(value * 100));
        }

        private String groupName() {
            return switch (group) {
                case "undead" -> "Undead";
                case "skeleton" -> "Skeletal";
                case "arthropod" -> "Arthropod";
                case "animal" -> "Animal";
                case "infernal" -> "Infernal";
                case "ender" -> "Ender";
                default -> "Wither";
            };
        }
    }

    private static final Map<String, Effect> EFFECTS = new LinkedHashMap<>();
    private static final Set<String> INFERNAL = Set.of("blaze", "ghast", "magma_cube");
    private static final Set<String> ENDER = Set.of("enderman", "endermite", "ender_dragon");
    private static final Set<String> WITHER = Set.of("wither", "wither_skeleton");

    static {
        ward("zombie", "undead", 0.05, 0.10, 0.15);
        ward("spider", "arthropod", 0.05, 0.10, 0.15);
        ward("sea_creature", "sea", 0.05, 0.10, 0.15);
        ward("wolf", "animal", 0.05, 0.10, 0);
        EFFECTS.put("SKELETON_TALISMAN", new Effect("ward", "skeleton", 0.05));
        EFFECTS.put("BLAZE_TALISMAN", new Effect("ward", "infernal", 0.05));
        EFFECTS.put("NETHER_ARTIFACT", new Effect("ward", "infernal", 0.05));
        EFFECTS.put("ENDER_ARTIFACT", new Effect("ward", "ender", 0.20));
        EFFECTS.put("ENDER_RELIC", new Effect("ward", "ender", 0.25));
        EFFECTS.put("WITHER_ARTIFACT", new Effect("ward", "wither", 0.20));
        EFFECTS.put("WITHER_RELIC", new Effect("ward", "wither", 0.25));
        EFFECTS.put("FEATHER_TALISMAN", new Effect("feather", "", 5));
        EFFECTS.put("FEATHER_RING", new Effect("feather", "5", 7));
        EFFECTS.put("FEATHER_ARTIFACT", new Effect("feather", "15", 10));
        EFFECTS.put("HASTE_RING", new Effect("haste", "", 2));
        EFFECTS.put("HASTE_ARTIFACT", new Effect("haste", "", 3));
        EFFECTS.put("NIGHT_VISION_CHARM", new Effect("night_vision", "", 1));
        EFFECTS.put("FIRE_TALISMAN", new Effect("fire", "", 1));
        EFFECTS.put("SCAVENGER_TALISMAN", new Effect("scavenger", "", 0.5));
        EFFECTS.put("SCAVENGER_RING", new Effect("scavenger", "", 0.6));
        EFFECTS.put("SCAVENGER_ARTIFACT", new Effect("scavenger", "", 0.75));
    }

    private AccessoryEffects() {
    }

    private static void ward(String line, String group, double talisman, double ring, double artifact) {
        String name = line.toUpperCase(java.util.Locale.ROOT);
        EFFECTS.put(name + "_TALISMAN", new Effect("ward", group, talisman));
        if (ring > 0) {
            EFFECTS.put(name + "_RING", new Effect("ward", group, ring));
        }
        if (artifact > 0) {
            EFFECTS.put(name + "_ARTIFACT", new Effect("ward", group, artifact));
        }
    }

    public static Effect get(String skyblockId) {
        return skyblockId == null ? null : EFFECTS.get(skyblockId);
    }

    /** The tooltip lines for an accessory's effect (empty when it has none), as short lines. */
    public static List<String> describe(String skyblockId) {
        Effect effect = get(skyblockId);
        List<String> out = new ArrayList<>();
        if (effect == null) {
            return out;
        }
        StringBuilder line = new StringBuilder();
        for (String word : effect.text().split(" ")) {
            if (line.length() + word.length() + 1 > 38 && !line.isEmpty()) {
                out.add(line.toString());
                line.setLength(0);
            }
            line.append(line.isEmpty() ? "" : " ").append(word);
        }
        out.add(line.toString());
        return out;
    }

    /** The effects of the accessories that count for this player. */
    private static List<Effect> active(ServerPlayer player) {
        List<Effect> out = new ArrayList<>();
        for (String id : AccessoryBag.countedIds(player)) {
            Effect effect = EFFECTS.get(id);
            if (effect != null) {
                out.add(effect);
            }
        }
        return out;
    }

    static void init() {
        // Wards and the Feather line's softer landing.
        IncomingDamage.registerHit((target, source) -> {
            if (!(target instanceof ServerPlayer player)) {
                return 1;
            }
            boolean fall = source.is(DamageTypeTags.IS_FALL);
            Entity attacker = attacker(source);
            if (!fall && attacker == null) {
                return 1;
            }
            double factor = 1;
            for (Effect effect : active(player)) {
                if (effect.kind().equals("ward") && attacker != null && matches(effect.group(), attacker)) {
                    factor *= 1 - effect.value();
                } else if (effect.kind().equals("feather") && fall && !effect.group().isEmpty()) {
                    factor *= 1 - Integer.parseInt(effect.group()) / 100.0;
                }
            }
            return factor;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player)) {
                return true;
            }
            if (source.is(DamageTypeTags.IS_FIRE)) {
                return active(player).stream().noneMatch(effect -> effect.kind().equals("fire"));
            }
            if (source.is(DamageTypeTags.IS_FALL)) {
                // Vanilla's fall damage is one a block past three: more safe blocks cancel a short fall.
                int blocks = active(player).stream().filter(effect -> effect.kind().equals("feather")).mapToInt(effect -> (int) effect.value()).max().orElse(0);
                return amount > blocks;
            }
            return true;
        });
        // Scavenger: coins per level of what you kill.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer player) || entity instanceof Player) {
                return;
            }
            double perLevel = active(player).stream().filter(effect -> effect.kind().equals("scavenger")).mapToDouble(Effect::value).max().orElse(0);
            if (perLevel > 0) {
                Wallet.give(player, Math.round(perLevel * Math.max(1, MobLevels.level(entity))));
            }
        });
        // Haste and Night Vision, renewed every few seconds.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 40 != 0) {
                return;
            }
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int haste = 0;
                boolean vision = false;
                for (Effect effect : active(player)) {
                    if (effect.kind().equals("haste")) {
                        haste = Math.max(haste, (int) effect.value());
                    } else if (effect.kind().equals("night_vision")) {
                        vision = true;
                    }
                }
                if (haste > 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.HASTE, 120, haste - 1, true, false, false));
                }
                if (vision) {
                    player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
                }
            }
        });
    }

    /** Who dealt the hit: the attacker itself, or a projectile's owner. */
    private static Entity attacker(DamageSource source) {
        if (source.getEntity() != null) {
            return source.getEntity();
        }
        return source.getDirectEntity() instanceof Projectile projectile ? projectile.getOwner() : null;
    }

    /** Whether this mob is of the ward's kind. */
    private static boolean matches(String group, Entity mob) {
        if (!(mob instanceof LivingEntity) || mob instanceof Player) {
            return false;
        }
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
        return switch (group) {
            case "undead" -> mob.is(EntityTypeTags.UNDEAD) && !mob.is(EntityTypeTags.SKELETONS) && !WITHER.contains(type);
            case "skeleton" -> mob.is(EntityTypeTags.SKELETONS);
            case "arthropod" -> mob.is(EntityTypeTags.ARTHROPOD);
            case "animal" -> mob instanceof Animal;
            case "infernal" -> INFERNAL.contains(type);
            case "ender" -> ENDER.contains(type);
            case "wither" -> WITHER.contains(type);
            case "sea" -> SkySeaCreatures.of(mob) != null;
            default -> false;
        };
    }
}
