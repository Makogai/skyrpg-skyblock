package com.skyrpg.skyblock.end;

import com.skyrpg.combat.MobLevels;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Hypixel's Ender Dragon fight in the Dragon's Nest: 8 Summoning Eyes on the altar ({@link DragonAltar}) hatch one of
 * seven dragons (Protector, Old, Wise, Unstable, Young and Strong 16% each, Superior 4%) with Hypixel's health and
 * damage. It's vanilla's dragon (the altar sits at 0, 0, the point its flight circles), its health in SkyBlock numbers
 * (toughness, as for other big mobs) and on a boss bar; Wise, Unstable, Strong and Superior dragons also call down
 * lightning. When it dies, everyone who hit it or placed an eye gets loot by Hypixel's Dragon Weight: 100 per eye, a
 * bonus for their damage rank and 100 × their share of the top damage, one major drop at most (armour, the Aspect of the
 * Dragons, a Dragon Claw...) and the rest of their weight as fragments and pearls.
 */
public final class DragonFight {
    public static final String DRAGON_TAG = "skyrpg_end_dragon";
    static final ResourceKey<Level> THE_END = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, SkyRpgSkyblock.id("the_end"));

    /** A dragon: its name (and item prefix), Hypixel health and damage, spawn weight, and whether it casts lightning. */
    record Type(String name, String prefix, double health, double damage, int weight, boolean lightning, double spellBonus) {
    }

    private static final List<Type> TYPES = List.of(
            new Type("Protector", "PROTECTOR", 9_000_000, 1_100, 16, false, 1.0),
            new Type("Old", "OLD", 15_000_000, 1_100, 16, false, 1.0),
            new Type("Wise", "WISE", 9_000_000, 2_200, 16, true, 1.2),
            new Type("Unstable", "UNSTABLE", 9_000_000, 1_100, 16, true, 1.0),
            new Type("Young", "YOUNG", 7_500_000, 1_100, 16, false, 1.0),
            new Type("Strong", "STRONG", 9_000_000, 1_100, 16, true, 1.2),
            new Type("Superior", "SUPERIOR", 12_000_000, 1_650, 4, true, 1.15));
    /** A dragon's melee hit in vanilla points, for scaling it to its Hypixel damage. */
    private static final double VANILLA_HIT = 10;

    private static final class Fight {
        final ServerLevel level;
        final BlockPos center;
        final Map<UUID, Integer> eyes;
        final Map<UUID, Double> damage = new HashMap<>();
        Type type;
        EnderDragon dragon;
        ServerBossEvent bar;
        long hatchAt;
        long nextLightning;
        long lastSeen;

        Fight(ServerLevel level, BlockPos center, Map<UUID, Integer> eyes, long now) {
            this.level = level;
            this.center = center;
            this.eyes = eyes;
            this.hatchAt = now + 160;
            this.lastSeen = now;
        }
    }

    private static Fight fight;

    private DragonFight() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(DragonFight::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (fight != null && entity == fight.dragon && source.getEntity() instanceof ServerPlayer player && taken > 0) {
                fight.damage.merge(player.getUUID(), taken * toughness(fight.type) * StatTypes.HEALTH_SCALE, Double::sum);
            }
        });
        // The dragon hits as hard as Hypixel's: its vanilla hits (and fireballs) scaled up to its damage.
        IncomingDamage.registerHit((target, source) -> {
            if (fight == null || fight.dragon == null || !(target instanceof ServerPlayer)) {
                return 1;
            }
            Entity from = source.getEntity();
            if (from == fight.dragon || source.getDirectEntity() instanceof Projectile p && p.getOwner() == fight.dragon) {
                return fight.type.damage() / StatTypes.HEALTH_SCALE / VANILLA_HIT;
            }
            return 1;
        });
    }

    public static boolean active() {
        return fight != null;
    }

    /** The altar is full: the egg hatches in 8 seconds. */
    static void start(ServerLevel level, BlockPos center, Map<UUID, Integer> eyes) {
        if (fight != null) {
            return;
        }
        fight = new Fight(level, center, eyes, level.getServer().getTickCount());
        if (level.getBlockState(center).isAir()) {
            level.setBlock(center, net.minecraft.world.level.block.Blocks.DRAGON_EGG.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
        for (ServerPlayer player : level.players()) {
            Notice.of(Notice.Kind.WARN).tag("DRAGON").send(player, Component.literal("The Dragon Egg has spawned!").withStyle(ChatFormatting.LIGHT_PURPLE));
            Msg.playSound(player, SoundEvents.ENDER_DRAGON_GROWL, 1.0f, 0.8f);
        }
    }

    private static double toughness(Type type) {
        return type.health() / StatTypes.HEALTH_SCALE / 1024.0;
    }

    private static Type roll(net.minecraft.util.RandomSource random) {
        int total = TYPES.stream().mapToInt(Type::weight).sum();
        int pick = random.nextInt(total);
        for (Type type : TYPES) {
            pick -= type.weight();
            if (pick < 0) {
                return type;
            }
        }
        return TYPES.getFirst();
    }

    private static void hatch(Fight f) {
        f.type = roll(f.level.getRandom());
        DragonAltar.removeEgg(f.level);
        EnderDragon dragon = EntityTypes.ENDER_DRAGON.create(f.level, EntitySpawnReason.EVENT);
        if (dragon == null) {
            end(f, false);
            return;
        }
        dragon.snapTo(f.center.getX() + 0.5, f.center.getY() + 40, f.center.getZ() + 0.5, 0, 0);
        dragon.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1024);
        dragon.setHealth(1024);
        dragon.setAttached(MobLevels.INFO, new MobLevels.MobInfo(100, false, (float) toughness(f.type)));
        dragon.setCustomName(Component.literal(f.type.name() + " Dragon").withStyle(ChatFormatting.RED));
        dragon.setCustomNameVisible(true);
        dragon.addTag(DRAGON_TAG);
        dragon.addTag(Zones.FIGHTABLE_TAG);
        dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
        f.level.addFreshEntity(dragon);
        f.dragon = dragon;
        f.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(f.type.name() + " Dragon").withStyle(ChatFormatting.RED), BossEvent.BossBarColor.PURPLE,
                BossEvent.BossBarOverlay.PROGRESS);
        f.nextLightning = f.level.getServer().getTickCount() + 200;
        for (ServerPlayer player : f.level.players()) {
            Notice.of(Notice.Kind.WARN).tag("DRAGON").send(player, Component.literal("The ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(f.type.name() + " Dragon").withStyle(ChatFormatting.RED, ChatFormatting.BOLD))
                    .append(Component.literal(" has spawned!").withStyle(ChatFormatting.GRAY)));
            Msg.playSound(player, SoundEvents.ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        }
    }

    private static void tick(MinecraftServer server) {
        Fight f = fight;
        if (f == null) {
            return;
        }
        long now = server.getTickCount();
        if (f.dragon == null) {
            if (now >= f.hatchAt) {
                hatch(f);
            }
            return;
        }
        // Its last hit puts it in its dying flight (health back to 1 until it lands), or it dies where it sits.
        if (f.dragon.isDeadOrDying() || f.dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.DYING) {
            killed(f);
            return;
        }
        if (f.dragon.isRemoved()) {
            end(f, false);
            return;
        }
        if (now % 10 == 0 && f.bar != null) {
            f.bar.setProgress(Math.max(0, f.dragon.getHealth() / f.dragon.getMaxHealth()));
            for (ServerPlayer player : f.level.players()) {
                boolean near = player.blockPosition().closerThan(f.center, 160);
                if (near && !f.bar.getPlayers().contains(player)) {
                    f.bar.addPlayer(player);
                } else if (!near) {
                    f.bar.removePlayer(player);
                }
                if (near) {
                    f.lastSeen = now;
                }
            }
            // Nobody left to fight it for two minutes: it flies away.
            if (now - f.lastSeen > 2400) {
                f.dragon.discard();
                end(f, false);
                return;
            }
        }
        if (f.type.lightning() && now >= f.nextLightning && !f.dragon.isDeadOrDying()) {
            f.nextLightning = now + 160 + f.level.getRandom().nextInt(120);
            List<ServerPlayer> targets = new ArrayList<>();
            for (ServerPlayer player : f.level.players()) {
                if (!player.isSpectator() && !player.isCreative() && player.blockPosition().closerThan(f.center, 80)) {
                    targets.add(player);
                }
            }
            if (!targets.isEmpty()) {
                ServerPlayer target = targets.get(f.level.getRandom().nextInt(targets.size()));
                LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(f.level, EntitySpawnReason.EVENT);
                if (bolt != null) {
                    bolt.snapTo(target.getX(), target.getY(), target.getZ(), 0, 0);
                    bolt.setVisualOnly(true);
                    f.level.addFreshEntity(bolt);
                }
                DamageSource source = f.level.damageSources().mobAttack(f.dragon);
                // Pre-scaled: the dragon's hits get multiplied up to its damage, so this is one hit's worth times the spell bonus.
                target.hurtServer(f.level, source, (float) (VANILLA_HIT * f.type.spellBonus()));
            }
        }
    }

    private static void killed(Fight f) {
        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(f.damage.entrySet());
        ranking.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        double top = ranking.isEmpty() ? 1 : Math.max(1, ranking.getFirst().getValue());
        MinecraftServer server = f.level.getServer();
        List<UUID> everyone = new ArrayList<>(f.damage.keySet());
        f.eyes.keySet().forEach(id -> {
            if (!everyone.contains(id)) {
                everyone.add(id);
            }
        });
        Notice.Panel board = Notice.of(Notice.Kind.COMBAT).tag("DRAGON DOWN").panel();
        String[] places = {"1st Damager", "2nd Damager", "3rd Damager"};
        for (int i = 0; i < Math.min(3, ranking.size()); i++) {
            ServerPlayer p = server.getPlayerList().getPlayer(ranking.get(i).getKey());
            board.line(Component.literal(places[i] + " - ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(p == null ? "?" : p.getName().getString()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" - " + Msg.number(Math.round(ranking.get(i).getValue()))).withStyle(ChatFormatting.GRAY)));
        }
        Component slain = Component.literal(f.type.name() + " Dragon").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                .append(Component.literal(" slain!").withStyle(ChatFormatting.GRAY));
        for (ServerPlayer player : f.level.players()) {
            board.send(player, slain);
            Msg.playSound(player, SoundEvents.ENDER_DRAGON_DEATH, 0.7f, 1.0f);
        }
        for (UUID id : everyone) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }
            int rank = -1;
            for (int i = 0; i < ranking.size(); i++) {
                if (ranking.get(i).getKey().equals(id)) {
                    rank = i + 1;
                }
            }
            double dealt = f.damage.getOrDefault(id, 0.0);
            int eyes = f.eyes.getOrDefault(id, 0);
            double weight = rankBonus(rank, dealt) + 100.0 * eyes + 100.0 * dealt / top;
            loot(player, f.type, weight, eyes);
        }
        end(f, true);
    }

    private static double rankBonus(int rank, double dealt) {
        if (rank < 1) {
            return dealt >= 1 ? 70 : 10;
        }
        return switch (rank) {
            case 1 -> 300;
            case 2 -> 250;
            case 3 -> 200;
            case 4 -> 125;
            case 5 -> 110;
            case 6, 7, 8 -> 100;
            case 9, 10 -> 90;
            case 11, 12 -> 80;
            default -> 70;
        };
    }

    /** One major drop at most (best first, each needing that much weight and passing its chance), then fragments and pearls. */
    private static void loot(ServerPlayer player, Type type, double weight, int eyes) {
        record Major(String item, int need, double chance) {
        }
        double total = weight;
        String p = type.prefix();
        boolean superior = p.equals("SUPERIOR");
        List<Major> majors = new ArrayList<>();
        majors.add(new Major("ENDER_DRAGON;4", 450, eyes * 0.0001));
        majors.add(new Major("ENDER_DRAGON;3", 449, eyes * 0.0005));
        if (superior) {
            majors.add(new Major("DRAGON_HORN", 448, 0.30));
        } else {
            majors.add(new Major("ASPECT_OF_THE_DRAGON", 447, eyes * 0.03));
        }
        majors.add(new Major("DRAGON_CLAW", 446, eyes * 0.02));
        majors.add(new Major(p + "_DRAGON_CHESTPLATE", 410, 0.30));
        majors.add(new Major(p + "_DRAGON_LEGGINGS", 360, 0.30));
        if (p.equals("YOUNG")) {
            majors.add(new Major("DRAGON_SCALE", 296, 0.30));
        }
        majors.add(new Major(p + "_DRAGON_HELMET", 295, 0.30));
        majors.add(new Major(p + "_DRAGON_BOOTS", 290, 0.30));
        if (p.equals("UNSTABLE")) {
            majors.add(new Major("TRAVEL_SCROLL_TO_DRAGONS_NEST", 250, 0.35));
        }
        List<Component> got = new ArrayList<>();
        for (Major major : majors) {
            if (weight >= major.need() && player.getRandom().nextDouble() < major.chance() && give(player, major.item(), 1)) {
                weight -= major.need();
                got.add(name(major.item(), 1));
                break;
            }
        }
        int fragments = (int) Math.min(32, Math.floor(weight / 22));
        weight -= fragments * 22;
        int enchantedPearls = (int) Math.min(4, Math.floor(weight / 15));
        weight -= enchantedPearls * 15;
        int pearls = (int) Math.min(16, Math.max(10, Math.floor(weight / 5)));
        if (fragments > 0 && give(player, p + "_FRAGMENT", fragments)) {
            got.add(name(p + "_FRAGMENT", fragments));
        }
        if (enchantedPearls > 0 && give(player, "ENCHANTED_ENDER_PEARL", enchantedPearls)) {
            got.add(name("ENCHANTED_ENDER_PEARL", enchantedPearls));
        }
        if (give(player, "ENDER_PEARL", pearls)) {
            got.add(name("ENDER_PEARL", pearls));
        }
        Notice.Panel panel = Notice.of(Notice.Kind.DROP).tag("DRAGON LOOT").panel();
        got.forEach(panel::line);
        panel.send(player, Component.literal("Your Dragon Weight: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(Msg.number(Math.round(total))).withStyle(ChatFormatting.LIGHT_PURPLE)));
    }

    private static boolean give(ServerPlayer player, String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            ItemStack stack = SkyItems.create(def, 1);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        return true;
    }

    private static Component name(String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        String label = (count > 1 ? count + "× " : "") + (def == null ? id : def.name());
        return Component.literal(label).withColor(def == null ? 0xFFFFFF : def.rarity().color());
    }

    private static void end(Fight f, boolean killed) {
        if (f.bar != null) {
            f.bar.removeAllPlayers();
        }
        DragonAltar.reset(f.level);
        fight = null;
        if (!killed) {
            SkyRpgSkyblock.LOGGER.info("The dragon fight ended without a kill");
        }
    }

    /** Whether this entity is the fight's dragon. */
    static boolean isDragon(Entity entity) {
        return fight != null && entity == fight.dragon;
    }

    static Identifier id(String path) {
        return SkyRpgSkyblock.id(path);
    }
}
