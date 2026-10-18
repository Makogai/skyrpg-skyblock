package com.skyrpg.skyblock.festival;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.hub.Hub;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.zone.Zones;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;

/**
 * The Spooky Festival's bats.
 *
 * <p>The <b>Bat Firework</b> (100 Green Candy at the Fear Mongerer): hold it and hit the floor or a wall in the Village
 * (or right-click). It goes up and bursts into 8-12 Bat Piñatas that roam the Hub dropping Green Candy for everyone, at
 * most 25 a player from one firework, not on the Candy Score; its launcher gets 5-64 Purple Candy (Hypixel's weights) and
 * needs a Trick or Treat Bag or Spooky Sack. 30 seconds' cooldown. Decided: the piñatas stay a minute.
 *
 * <p>The <b>Mega Bat</b> (Lv65, 100 HP, Shielded): turns up near players on any island during the festival and drops
 * candy on every hit instead of when it dies (21 coins and 97 Combat XP then). Decided: one an island at a time, every
 * three minutes or so. The <b>Echolocator</b>, held, points to the nearest one.
 */
final class SpookyBats {
    private static final String PINATA = "skyrpg_bat_pinata";
    private static final String MEGA = "skyrpg_mega_bat";
    private static final int COOLDOWN = 30 * 20;
    private static final int PINATA_LIFE = 60 * 20;
    private static final int PER_PLAYER = 25;
    private static final int[] PURPLE = {5, 10, 15, 20, 25, 64};
    private static final int[] PURPLE_WEIGHTS = {40, 40, 20, 10, 5, 1};

    /** One firework's piñatas: who got how much candy, and when they go. */
    private record Show(List<Bat> bats, Map<UUID, Integer> given, long until) {
    }

    /** A firework on its way up: where its piñatas come out, and when. */
    private record Launch(ServerLevel level, Vec3 at, long when) {
    }

    private static final List<Show> SHOWS = new ArrayList<>();
    private static final List<Launch> LAUNCHES = new ArrayList<>();
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    private SpookyBats() {
    }

    static void init() {
        Identifier first = SkyRpgSkyblock.id("bat_firework_first");
        UseItemCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(first, (player, level, hand) -> use(player, hand));
        UseBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(first, (player, level, hand, hit) -> use(player, hand));
        AttackBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(first, (player, level, hand, pos, direction) -> use(player, hand));
        // A Mega Bat drops candy each time it's hit.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (taken > 0 && entity.entityTags().contains(MEGA) && source.getEntity() instanceof ServerPlayer player && SpookyFestival.running()) {
                SpookyFestival.candy(player, player.getRandom().nextDouble() < 0.10, 1);
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.entityTags().contains(MEGA) && source.getEntity() instanceof ServerPlayer player) {
                Wallet.give(player, 21);
            }
        });
        // A piñata left from before a restart is gone.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(PINATA)) {
                level.getServer().execute(() -> {
                    if (SHOWS.stream().noneMatch(show -> show.bats().contains(entity))) {
                        entity.discard();
                    }
                });
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(SpookyBats::tick);
    }

    private static InteractionResult use(Player player, InteractionHand hand) {
        if (!"BAT_FIREWORK".equals(SkyItems.skyblockId(player.getItemInHand(hand)))) {
            return InteractionResult.PASS;
        }
        if (hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer server) {
            launch(server, hand);
        }
        return InteractionResult.SUCCESS;
    }

    // ---------------------------------------------------------------- the Bat Firework

    /** The Village: the Hub's plaza and the streets around it. */
    private static boolean inVillage(ServerPlayer player) {
        return Hub.isHub(player.level()) && player.getX() * player.getX() + player.getZ() * player.getZ() < 90 * 90;
    }

    private static void launch(ServerPlayer player, InteractionHand hand) {
        if (!inVillage(player)) {
            player.sendOverlayMessage(Component.literal("You can only use this in the Village!").withStyle(ChatFormatting.RED));
            return;
        }
        long now = player.level().getServer().getTickCount();
        long last = LAST_USE.getOrDefault(player.getUUID(), -COOLDOWN * 2L);
        if (now - last < COOLDOWN) {
            player.sendOverlayMessage(Component.literal("This item is on cooldown for " + ((COOLDOWN - (now - last)) / 20 + 1) + "s.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        if (Sacks.capacity(player, "PURPLE_CANDY") <= 0) {
            player.sendSystemMessage(Component.literal("You need a Trick or Treat Bag or a Spooky Sack to launch a Bat Firework!")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        LAST_USE.put(player.getUUID(), now);
        player.getItemInHand(hand).shrink(1);
        ServerLevel level = (ServerLevel) player.level();
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL,
                IntList.of(0xFF8C1A, 0x9A4CD8), IntList.of(0x2A2030), true, true))));
        level.addFreshEntity(new FireworkRocketEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), rocket));
        LAUNCHES.add(new Launch(level, player.position().add(0, 14, 0), now + 30));
        // the launcher's Purple Candy
        int total = 0;
        for (int w : PURPLE_WEIGHTS) {
            total += w;
        }
        int roll = player.getRandom().nextInt(total);
        int purple = PURPLE[0];
        for (int i = 0; i < PURPLE.length; i++) {
            roll -= PURPLE_WEIGHTS[i];
            if (roll < 0) {
                purple = PURPLE[i];
                break;
            }
        }
        SpookyFestival.give(player, "PURPLE_CANDY", purple);
        player.sendSystemMessage(Component.literal("You launched a Bat Firework and got ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(purple + " Purple Candy").withStyle(ChatFormatting.DARK_PURPLE))
                .append(Component.literal("!").withStyle(ChatFormatting.YELLOW)));
        Component told = Component.literal(player.getName().getString() + " ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal("launched a ").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("Bat Firework ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("in the Village. It's dropping candies for everyone!").withStyle(ChatFormatting.YELLOW));
        for (ServerPlayer other : level.players()) {
            if (other != player) {
                other.sendSystemMessage(told);
            }
        }
    }

    private static void burst(Launch launch, long now) {
        ServerLevel level = launch.level();
        // known before they're in the world (the load check would take them for leftovers)
        Show show = new Show(new ArrayList<>(), new HashMap<>(), now + PINATA_LIFE);
        SHOWS.add(show);
        List<Bat> bats = show.bats();
        int count = 8 + level.getRandom().nextInt(5);
        for (int i = 0; i < count; i++) {
            Bat bat = EntityTypes.BAT.create(level, EntitySpawnReason.EVENT);
            if (bat == null) {
                continue;
            }
            bat.snapTo(launch.at().x + level.getRandom().nextGaussian() * 2, launch.at().y, launch.at().z + level.getRandom().nextGaussian() * 2,
                    level.getRandom().nextFloat() * 360, 0);
            bat.setCustomName(Component.literal("Bat Piñata").withStyle(ChatFormatting.GOLD));
            bat.setCustomNameVisible(true);
            bat.setPermanentlyInvulnerable(true);
            bat.getAttribute(Attributes.SCALE).setBaseValue(1.6);
            bat.setResting(false);
            bat.addTag(PINATA);
            bats.add(bat);
            level.addFreshEntity(bat);
        }
        level.sendParticles(ParticleTypes.FIREWORK, launch.at().x, launch.at().y, launch.at().z, 60, 1.5, 1.5, 1.5, 0.15);
    }

    /** Every second and a half or so, each piñata drops a Green Candy on a player near it (each up to 25 a firework). */
    private static void rain(Show show) {
        for (Bat bat : show.bats()) {
            if (bat.isRemoved() || bat.getRandom().nextInt(30) != 0) {
                continue;
            }
            ServerLevel level = (ServerLevel) bat.level();
            ServerPlayer near = null;
            double best = 12 * 12;
            for (ServerPlayer player : level.players()) {
                double d = player.distanceToSqr(bat);
                if (d < best && show.given().getOrDefault(player.getUUID(), 0) < PER_PLAYER && !player.isSpectator()) {
                    best = d;
                    near = player;
                }
            }
            if (near == null) {
                continue;
            }
            show.given().merge(near.getUUID(), 1, Integer::sum);
            SpookyFestival.give(near, "GREEN_CANDY", 1);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Items.SLIME_BALL), bat.getX(), bat.getY(), bat.getZ(),
                    8, 0.2, 0.2, 0.2, 0.05);
            Msg.playSound(near, SoundEvents.ITEM_PICKUP, 0.4f, 1.6f);
        }
    }

    // ---------------------------------------------------------------- the Mega Bat

    private static void megaBats(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
            if (players.isEmpty() || !SkyBlockWorlds.isSkyBlock(level) || level.getRandom().nextInt(9) != 0) {
                continue;
            }
            boolean one = false;
            for (Entity entity : level.getAllEntities()) {
                if (entity.entityTags().contains(MEGA) && entity.isAlive()) {
                    one = true;
                    break;
                }
            }
            if (one) {
                continue;
            }
            spawnMega(level, players.get(level.getRandom().nextInt(players.size())));
        }
    }

    /** A Mega Bat in the air somewhere 10-18 blocks from this player (none if that spot isn't open air). */
    static boolean spawnMega(ServerLevel level, ServerPlayer near) {
        double angle = level.getRandom().nextDouble() * Math.PI * 2;
        double distance = 10 + level.getRandom().nextDouble() * 8;
        Vec3 at = near.position().add(Math.cos(angle) * distance, 5, Math.sin(angle) * distance);
        if (!level.getBlockState(net.minecraft.core.BlockPos.containing(at)).isAir()) {
            return false;
        }
        Bat bat = EntityTypes.BAT.create(level, EntitySpawnReason.EVENT);
        if (bat == null) {
            return false;
        }
        bat.snapTo(at.x, at.y, at.z, 0, 0);
        Zones.dressAs(bat, "Mega Bat", 65, 100, 0, 97);
        bat.getAttribute(Attributes.SCALE).setBaseValue(3);
        bat.setResting(false);
        bat.addTag(MEGA);
        bat.addTag(SpookyFestival.SPOOKY);
        bat.addTag(SpookyFestival.SHIELDED);
        bat.addTag(Zones.FIGHTABLE_TAG);
        bat.setPersistenceRequired();
        level.addFreshEntity(bat);
        return true;
    }

    /** The Echolocator, held during the festival: the way to the nearest Mega Bat. */
    private static void echolocate(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!"ECHOLOCATOR".equals(SkyItems.skyblockId(player.getMainHandItem())) || !SpookyFestival.running()) {
                continue;
            }
            Entity nearest = null;
            double best = Double.MAX_VALUE;
            for (Entity entity : player.level().getEntitiesOfClass(Bat.class, player.getBoundingBox().inflate(256))) {
                if (entity.entityTags().contains(MEGA) && entity.distanceToSqr(player) < best) {
                    best = entity.distanceToSqr(player);
                    nearest = entity;
                }
            }
            if (nearest == null) {
                player.sendOverlayMessage(Component.literal("No Mega Bat nearby...").withStyle(ChatFormatting.GRAY));
                continue;
            }
            double dx = nearest.getX() - player.getX();
            double dz = nearest.getZ() - player.getZ();
            double bearing = Math.toDegrees(Math.atan2(-dx, dz));
            double turn = ((bearing - player.getYRot()) % 360 + 540) % 360 - 180;
            String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
            String arrow = arrows[(int) Math.floorMod(Math.round(turn / 45), 8)];
            player.sendOverlayMessage(Component.literal("Mega Bat ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(arrow + " " + Math.round(Math.sqrt(best)) + "m").withStyle(ChatFormatting.YELLOW)));
        }
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        LAUNCHES.removeIf(launch -> {
            if (now >= launch.when()) {
                burst(launch, now);
                return true;
            }
            return false;
        });
        SHOWS.removeIf(show -> {
            if (now > show.until()) {
                show.bats().forEach(bat -> {
                    ((ServerLevel) bat.level()).sendParticles(ParticleTypes.POOF, bat.getX(), bat.getY(), bat.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                    bat.discard();
                });
                return true;
            }
            rain(show);
            return false;
        });
        if (now % 10 == 0) {
            echolocate(server);
        }
        if (now % 400 == 0 && SpookyFestival.running()) {
            megaBats(server);
        }
    }
}
