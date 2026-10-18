package com.skyrpg.skyblock.festival;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.hub.Hub;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's Headless Horseman: light a Horseman's Candle (32 Purple Candy at the Fear Mongerer) at night in the Hub's
 * Graveyard or Wilderness and he rides out. Lv259, 3,000,000 HP, 600 damage; arrows and other projectiles don't hurt
 * him (they reel their shooter in and blind them for 3s). In turn every 15 seconds: the Headless Zombie Swarm (4 baby
 * Headless Zombies, 20,000 HP each) and the Bat Swarm (bats throwing 10-damage potions). When he falls: 647 coins and
 * 1,942 Combat XP to his killer; for everyone who fought, 0-2 Green and 0-2 Purple Candy; a Spooky Fragment to the
 * summoner and the top damager, and half the time to the second; the top three in chat.
 *
 * <p>Decided where Hypixel's numbers weren't found: the zombies hit for 300 and are Lv100; he rides off after 10
 * minutes; the Wounded effect (half healing) isn't in yet; the ground isn't turned to obsidian (the Hub build stays).
 */
final class HeadlessHorseman {
    private static final String TAG = "skyrpg_horseman";
    private static final String PART = "skyrpg_horseman_part";
    private static final int LIFE = 10 * 60 * 20;
    private static final int ABILITY_EVERY = 15 * 20;

    private static final class Fight {
        final UUID summoner;
        final ServerLevel level;
        final Zombie rider;
        final SkeletonHorse horse;
        final ServerBossEvent bar;
        final Map<UUID, Double> damage = new HashMap<>();
        final List<Mob> minions = new ArrayList<>();
        final long until;
        long nextAbility;
        long batsUntil;
        boolean swarmNext = true;

        Fight(UUID summoner, ServerLevel level, Zombie rider, SkeletonHorse horse, long now) {
            this.summoner = summoner;
            this.level = level;
            this.rider = rider;
            this.horse = horse;
            this.until = now + LIFE;
            this.nextAbility = now + 10 * 20;
            this.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal("Headless Horseman").withStyle(ChatFormatting.GOLD),
                    BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
        }

        void end() {
            bar.removeAllPlayers();
            minions.forEach(Entity::discard);
            if (!horse.isRemoved()) {
                horse.discard();
            }
            if (!rider.isRemoved()) {
                rider.discard();
            }
        }
    }

    private static final List<Fight> FIGHTS = new ArrayList<>();

    private HeadlessHorseman() {
    }

    static void init() {
        Identifier first = SkyRpgSkyblock.id("horseman_candle_first");
        UseItemCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(first, (player, level, hand) -> candle(player, hand));
        UseBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(first, (player, level, hand, hit) -> candle(player, hand));
        // Arrows and other projectiles: no damage, and the shooter is reeled in and blinded.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity.entityTags().contains(PART) && !entity.entityTags().contains(TAG)) {
                return false; // the horse
            }
            if (!entity.entityTags().contains(TAG) || !(source.getDirectEntity() instanceof Projectile)) {
                return true;
            }
            if (source.getEntity() instanceof ServerPlayer shooter) {
                Vec3 pull = entity.position().subtract(shooter.position()).normalize().scale(1.6);
                shooter.setDeltaMovement(pull.x, 0.5, pull.z);
                shooter.syncVelocity = true;
                shooter.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                shooter.sendOverlayMessage(Component.literal("The Headless Horseman is immune to projectiles!").withStyle(ChatFormatting.RED));
            }
            return false;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (entity.entityTags().contains(TAG) && source.getEntity() instanceof ServerPlayer player) {
                Fight fight = of(entity);
                if (fight != null) {
                    // in SkyBlock damage: his 3,000,000 HP are vanilla's 1,024 times his toughness
                    com.skyrpg.combat.MobLevels.MobInfo info = entity.getAttached(com.skyrpg.combat.MobLevels.INFO);
                    double toughness = info == null ? 1 : Math.max(1, info.toughness());
                    fight.damage.merge(player.getUUID(), (double) taken * StatTypes.HEALTH_SCALE * toughness, Double::sum);
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.entityTags().contains(TAG)) {
                Fight fight = of(entity);
                if (fight != null) {
                    // the killing blow lands after this (and a one-hit kill would leave nobody on the list)
                    if (source.getEntity() instanceof ServerPlayer killer) {
                        fight.damage.putIfAbsent(killer.getUUID(), 0.0);
                    }
                    FIGHTS.remove(fight);
                    defeated(fight, source.getEntity() instanceof ServerPlayer killer ? killer : null);
                }
            }
        });
        // Parts left over from before a restart are gone.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(PART)) {
                level.getServer().execute(() -> {
                    if (FIGHTS.stream().noneMatch(f -> f.rider == entity || f.horse == entity || f.minions.contains(entity))) {
                        entity.discard();
                    }
                });
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            FIGHTS.forEach(Fight::end);
            FIGHTS.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(HeadlessHorseman::tick);
    }

    private static Fight of(Entity entity) {
        for (Fight fight : FIGHTS) {
            if (fight.rider == entity) {
                return fight;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- the candle

    private static InteractionResult candle(Player player, InteractionHand hand) {
        if (!"HORSEMAN_CANDLE".equals(SkyItems.skyblockId(player.getItemInHand(hand)))) {
            return InteractionResult.PASS;
        }
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer server) {
            summon(server, hand);
        }
        return InteractionResult.SUCCESS;
    }

    private static void summon(ServerPlayer player, InteractionHand hand) {
        ServerLevel level = (ServerLevel) player.level();
        int area = -1;
        if (Hub.isHub(level)) {
            for (int i = 0; i < SpookyFestival.AREAS.size(); i++) {
                if (SpookyFestival.AREAS.get(i).inflate(4).contains(player.position())) {
                    area = i;
                }
            }
        }
        if (area < 0) {
            player.sendSystemMessage(Component.literal("The Headless Horseman can only be summoned in the Graveyard or the Wilderness!")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        int hour = com.skyrpg.skyblock.time.SkyBlockTime.now().hour();
        if (hour >= 5 && hour < 19) {
            player.sendSystemMessage(Component.literal("This item can only be used at night!").withStyle(ChatFormatting.RED));
            return;
        }
        if (FIGHTS.stream().anyMatch(f -> f.summoner.equals(player.getUUID()))) {
            player.sendSystemMessage(Component.literal("You've already summoned the Headless Horseman!").withStyle(ChatFormatting.RED));
            return;
        }
        player.getItemInHand(hand).shrink(1);
        Vec3 at = player.position().add(player.getLookAngle().multiply(6, 0, 6));
        SkeletonHorse horse = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.EVENT);
        Zombie rider = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
        if (horse == null || rider == null) {
            return;
        }
        horse.snapTo(at.x, player.getY(), at.z, player.getYRot() + 180, 0);
        horse.setTamed(true);
        horse.setItemSlot(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        horse.setPermanentlyInvulnerable(true);
        horse.setPersistenceRequired();
        horse.addTag(PART);
        rider.snapTo(at.x, player.getY(), at.z, player.getYRot() + 180, 0);
        Zones.dressAs(rider, "Headless Horseman", 259, 3_000_000, 600, 1942);
        rider.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.JACK_O_LANTERN));
        rider.setItemSlot(EquipmentSlot.CHEST, leather(Items.LEATHER_CHESTPLATE, 0x1A1A1A));
        rider.setItemSlot(EquipmentSlot.LEGS, leather(Items.LEATHER_LEGGINGS, 0x1A1A1A));
        rider.setItemSlot(EquipmentSlot.FEET, leather(Items.LEATHER_BOOTS, 0x3A2A1E));
        rider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            rider.setDropChance(slot, 0);
        }
        rider.addTag(TAG);
        rider.addTag(PART);
        rider.addTag(Zones.FIGHTABLE_TAG);
        rider.setPersistenceRequired();
        long now = level.getServer().getTickCount();
        Fight fight = new Fight(player.getUUID(), level, rider, horse, now);
        FIGHTS.add(fight);
        level.addFreshEntity(horse);
        level.addFreshEntity(rider);
        rider.startRiding(horse, true, true);
        rider.setTarget(player);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, player.getY() + 1, at.z, 60, 0.6, 1, 0.6, 0.05);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WITHER_SPAWN, net.minecraft.sounds.SoundSource.HOSTILE, 0.8f, 1.4f);
        String where = area == 0 ? "the Graveyard" : "the Wilderness";
        Component told = Component.literal(player.getName().getString()).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" has summoned the ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("Headless Horseman").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(Component.literal(" in " + where + "!").withStyle(ChatFormatting.YELLOW));
        level.players().forEach(p -> p.sendSystemMessage(told));
    }

    private static ItemStack leather(net.minecraft.world.item.Item item, int color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0xFF000000 | color));
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return stack;
    }

    // ---------------------------------------------------------------- the fight

    private static void tick(MinecraftServer server) {
        if (FIGHTS.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        FIGHTS.removeIf(fight -> {
            if (fight.rider.isRemoved() || now > fight.until) {
                if (now > fight.until) {
                    Component gone = Component.literal("The Headless Horseman rode off into the night...").withStyle(ChatFormatting.GRAY);
                    fight.level.players().stream().filter(p -> p.distanceToSqr(fight.rider) < 64 * 64).forEach(p -> p.sendSystemMessage(gone));
                }
                fight.end();
                return true;
            }
            Zombie rider = fight.rider;
            fight.minions.removeIf(Entity::isRemoved);
            if (now > fight.batsUntil) {
                fight.minions.removeIf(m -> {
                    if (m instanceof Bat) {
                        m.discard();
                        return true;
                    }
                    return false;
                });
            }
            if (now % 10 == 0) {
                fight.bar.setProgress(Math.max(0, rider.getHealth() / rider.getMaxHealth()));
                for (ServerPlayer player : fight.level.players()) {
                    if (player.distanceToSqr(rider) < 48 * 48) {
                        fight.bar.addPlayer(player);
                    } else {
                        fight.bar.removePlayer(player);
                    }
                }
                // he goes for the nearest player
                Player nearest = fight.level.getNearestPlayer(rider, 32);
                if (nearest != null && !nearest.isCreative() && !nearest.isSpectator()) {
                    rider.setTarget(nearest);
                }
            }
            if (now >= fight.nextAbility) {
                fight.nextAbility = now + ABILITY_EVERY;
                if (fight.swarmNext) {
                    zombieSwarm(fight);
                } else {
                    batSwarm(fight);
                }
                fight.swarmNext = !fight.swarmNext;
            }
            // the bats throw their potions
            if (now % 30 == 0) {
                for (Mob minion : fight.minions) {
                    if (minion instanceof Bat bat) {
                        ServerPlayer target = (ServerPlayer) fight.level.getNearestPlayer(bat, 10);
                        if (target != null && !target.isCreative() && !target.isSpectator()) {
                            fight.level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + 1, target.getZ(), 12, 0.3, 0.5, 0.3, 0.05);
                            target.hurtServer(fight.level, fight.level.damageSources().indirectMagic(bat, rider), (float) (10 / StatTypes.HEALTH_SCALE));
                        }
                    }
                }
            }
            return false;
        });
    }

    private static void zombieSwarm(Fight fight) {
        for (int i = 0; i < 4; i++) {
            Zombie zombie = EntityTypes.ZOMBIE.create(fight.level, EntitySpawnReason.EVENT);
            if (zombie == null) {
                continue;
            }
            double angle = Math.PI / 2 * i;
            zombie.snapTo(fight.rider.getX() + Math.cos(angle) * 2, fight.rider.getY(), fight.rider.getZ() + Math.sin(angle) * 2, 0, 0);
            Zones.dressAs(zombie, "Headless Zombie", 100, 20_000, 300, 0);
            zombie.setBaby(true);
            zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            zombie.setDropChance(EquipmentSlot.HEAD, 0);
            zombie.addTag(PART);
            zombie.addTag(Zones.FIGHTABLE_TAG);
            Player nearest = fight.level.getNearestPlayer(zombie, 24);
            if (nearest != null) {
                zombie.setTarget(nearest);
            }
            fight.minions.add(zombie);
            fight.level.addFreshEntity(zombie);
        }
        say(fight, "Headless Zombie Swarm!");
    }

    private static void batSwarm(Fight fight) {
        for (int i = 0; i < 5; i++) {
            Bat bat = EntityTypes.BAT.create(fight.level, EntitySpawnReason.EVENT);
            if (bat == null) {
                continue;
            }
            bat.snapTo(fight.rider.getX(), fight.rider.getY() + 2.5, fight.rider.getZ(), 0, 0);
            bat.setResting(false);
            bat.setPermanentlyInvulnerable(true);
            bat.addTag(PART);
            fight.minions.add(bat);
            fight.level.addFreshEntity(bat);
        }
        // the bats fly for ten seconds
        fight.batsUntil = fight.level.getServer().getTickCount() + 10 * 20;
        say(fight, "Bat Swarm!");
    }

    private static void say(Fight fight, String ability) {
        Component line = Component.literal("Headless Horseman: ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(ability).withStyle(ChatFormatting.RED));
        fight.level.players().stream().filter(p -> p.distanceToSqr(fight.rider) < 48 * 48).forEach(p -> p.sendSystemMessage(line));
    }

    private static void defeated(Fight fight, ServerPlayer killer) {
        fight.end();
        if (killer != null) {
            Wallet.give(killer, 647);
        }
        List<Map.Entry<UUID, Double>> ranked = fight.damage.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()).toList();
        MutableComponent summary = Component.literal("HEADLESS HORSEMAN DOWN!").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (killer != null) {
            summary.append(Component.literal("\n Killer: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(killer.getName().getString()).withStyle(ChatFormatting.WHITE));
        }
        String[] places = {"1st", "2nd", "3rd"};
        for (int i = 0; i < Math.min(3, ranked.size()); i++) {
            ServerPlayer who = fight.level.getServer().getPlayerList().getPlayer(ranked.get(i).getKey());
            summary.append(Component.literal("\n " + places[i] + " Damager - ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal((who == null ? "?" : who.getName().getString())).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (" + Msg.number(Math.round(ranked.get(i).getValue())) + ")").withStyle(ChatFormatting.GRAY));
        }
        for (int i = 0; i < ranked.size(); i++) {
            ServerPlayer player = fight.level.getServer().getPlayerList().getPlayer(ranked.get(i).getKey());
            if (player == null) {
                continue;
            }
            player.sendSystemMessage(summary);
            int green = player.getRandom().nextInt(3);
            int purple = player.getRandom().nextInt(3);
            if (green > 0) {
                SpookyFestival.give(player, "GREEN_CANDY", green);
            }
            if (purple > 0) {
                SpookyFestival.give(player, "PURPLE_CANDY", purple);
            }
            boolean fragment = player.getUUID().equals(fight.summoner) || i == 0 || i == 1 && player.getRandom().nextBoolean();
            if (fragment) {
                Component name = SpookyFestival.give(player, "SPOOKY_SHARD", 1);
                player.sendSystemMessage(Component.literal("RARE DROP! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).append(name));
            }
            Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        Vec3 at = fight.rider.position();
        fight.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
    }
}
