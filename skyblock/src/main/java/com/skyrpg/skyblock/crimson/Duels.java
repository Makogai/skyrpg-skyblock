package com.skyrpg.skyblock.crimson;

import com.mojang.serialization.Codec;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.zone.Zones;
import com.skyrpg.skyblock.time.SkyBlockTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Rollim (Scarleton, for Mages) and Kaus (Dragontail, for Barbarians), Hypixel's: seven daily training duels, a day
 * apart, each one tougher (50k health and 200 damage on the first day, 25M and 2,500 on the seventh). Each win is 25
 * reputation; the seventh, a Bag of Gold. Once in a duel, at a quarter of his health he eats a golden apple and is back
 * to three quarters (decided: Hypixel's wording was unclear). Leave or lose and the duel's off.
 */
final class Duels {
    static final String TAG = "skyrpg_duelist";
    private static final double[] HEALTH = {50_000, 100_000, 250_000, 1_000_000, 2_500_000, 10_000_000, 25_000_000};
    private static final double[] DAMAGE = {200, 300, 400, 500, 750, 1_000, 2_500};
    private static final long DAY = 24 * 60 * 60 * 1000L;

    /** "<who>:day" duels won, "<who>:last" when (ms). */
    static final AttachmentType<Map<String, Long>> DATA = AttachmentRegistry.create(SkyRpgSkyblock.id("crimson_duels"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());

    private static final class Duel {
        UUID player;
        String who;
        Mob mob;
        ServerBossEvent bar;
        boolean ate;
        int day;
    }

    private static final List<Duel> LIVE = new ArrayList<>();

    private Duels() {
    }

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            for (Duel duel : List.copyOf(LIVE)) {
                if (duel.mob == entity) {
                    LIVE.remove(duel);
                    duel.bar.removeAllPlayers();
                    if (source.getEntity() instanceof ServerPlayer killer && killer.getUUID().equals(duel.player)) {
                        won(killer, duel);
                    }
                } else if (entity instanceof ServerPlayer player && player.getUUID().equals(duel.player)) {
                    end(duel, player, "You were beaten. " + name(duel.who) + " helps you up.");
                }
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && LIVE.stream().noneMatch(d -> d.mob == entity)) {
                entity.discard();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Duels::tick);
    }

    private static String name(String who) {
        return who.equals("rollim") ? "Rollim" : "Kaus";
    }

    private static long get(ServerPlayer player, String key) {
        return player.getAttachedOrElse(DATA, Map.of()).getOrDefault(key, 0L);
    }

    private static void set(ServerPlayer player, String key, long value) {
        Map<String, Long> map = new HashMap<>(player.getAttachedOrElse(DATA, Map.of()));
        map.put(key, value);
        player.setAttached(DATA, Map.copyOf(map));
    }

    /** Rollim or Kaus. */
    static void open(ServerPlayer player, String who, int npc) {
        String faction = who.equals("rollim") ? Factions.MAGE : Factions.BARBARIAN;
        CardMenu.open(player, p -> {
            int day = (int) get(p, who + ":day");
            long wait = get(p, who + ":last") + DAY - System.currentTimeMillis();
            boolean member = faction.equals(Factions.faction(p));
            boolean busy = LIVE.stream().anyMatch(d -> d.player.equals(p.getUUID()));
            List<CardView.Card> cards = new ArrayList<>();
            String status = !member ? "Only " + Factions.plural(faction) + " train with " + name(who) + "."
                    : day >= 7 ? "You've beaten him all seven days." : wait > 0 ? "Again in " + SkyBlockTime.countdown(wait)
                    : "Day " + (day + 1) + ": " + Msg.compact(HEALTH[day]) + " health, " + Msg.compact(DAMAGE[day]) + " damage";
            cards.add(new CardView.Card("Duel " + name(who), status, Factions.color(faction), member && day < 7 && wait <= 0 && !busy,
                    day >= 7 ? "Done" : "Duel", new ItemStack(who.equals("rollim") ? Items.BLAZE_ROD : Items.IRON_AXE), "", List.of(), day / 7f, day + "/7"));
            cards.add(new CardView.Card("Rewards", "25 " + Factions.title(faction) + " Reputation a win; a Bag of Gold on the seventh day", 0xFFAA00,
                    false, "", new ItemStack(Items.GOLD_INGOT), "", List.of(), -1, ""));
            return new CardView("CRIMSON", name(who), "Training duels", npc, List.of(), cards, "One duel a day, seven days, each one tougher.", 1, 40);
        }, (p, card) -> {
            if (card != 0) {
                return "";
            }
            int day = (int) get(p, who + ":day");
            if (!faction.equals(Factions.faction(p)) || day >= 7 || get(p, who + ":last") + DAY > System.currentTimeMillis()
                    || LIVE.stream().anyMatch(d -> d.player.equals(p.getUUID()))) {
                return "";
            }
            start(p, who, day, npc);
            return null;
        });
    }

    private static void start(ServerPlayer player, String who, int day, int npc) {
        ServerLevel level = (ServerLevel) player.level();
        Mob mob = (who.equals("rollim") ? EntityTypes.VINDICATOR : EntityTypes.PIGLIN_BRUTE).create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return;
        }
        var at = player.position().add(player.getLookAngle().multiply(3, 0, 3));
        mob.snapTo(at.x, player.getY(), at.z, player.getYRot() + 180, 0);
        Zones.dressAs(mob, name(who), 100 + 50 * day, HEALTH[day], DAMAGE[day], -1);
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
        if (mob instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin piglin) {
            piglin.setImmuneToZombification(true);
        }
        mob.addTag(TAG);
        mob.addTag(Zones.FIGHTABLE_TAG);
        mob.setPersistenceRequired();
        mob.setCustomNameVisible(true);
        mob.setTarget(player);
        Duel duel = new Duel();
        duel.player = player.getUUID();
        duel.who = who;
        duel.mob = mob;
        duel.day = day;
        duel.bar = new ServerBossEvent(UUID.randomUUID(), Component.literal(name(who) + " · Day " + (day + 1)).withStyle(ChatFormatting.GOLD),
                BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10);
        duel.bar.addPlayer(player);
        LIVE.add(duel);
        level.addFreshEntity(mob);
        player.sendSystemMessage(Component.literal("[NPC] " + name(who) + ": ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("Day " + (day + 1) + ". Show me what you've got!").withStyle(ChatFormatting.WHITE)));
        Msg.playSound(player, SoundEvents.RAID_HORN.value(), 0.6f, 1.2f);
    }

    private static void tick(MinecraftServer server) {
        if (LIVE.isEmpty()) {
            return;
        }
        for (Duel duel : List.copyOf(LIVE)) {
            ServerPlayer player = server.getPlayerList().getPlayer(duel.player);
            Mob mob = duel.mob;
            if (player == null || !mob.isAlive() || player.level() != mob.level() || player.distanceTo(mob) > 40) {
                end(duel, player, "You walked away from the duel.");
                continue;
            }
            duel.bar.setProgress(Math.max(0, mob.getHealth() / mob.getMaxHealth()));
            if (!(mob.getTarget() instanceof Player)) {
                mob.setTarget(player);
            }
            if (!duel.ate && mob.getHealth() < mob.getMaxHealth() * 0.25f) {
                duel.ate = true;
                mob.setHealth(mob.getMaxHealth() * 0.75f);
                ((ServerLevel) mob.level()).sendParticles(ParticleTypes.HEART, mob.getX(), mob.getY() + 2, mob.getZ(), 6, 0.4, 0.3, 0.4, 0);
                mob.level().playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EAT.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1f, 1f);
                player.sendOverlayMessage(Component.literal(name(duel.who) + " eats a Golden Apple!").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    private static void end(Duel duel, ServerPlayer player, String why) {
        LIVE.remove(duel);
        duel.bar.removeAllPlayers();
        duel.mob.discard();
        if (player != null) {
            player.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.RED));
        }
    }

    private static void won(ServerPlayer player, Duel duel) {
        int day = duel.day + 1;
        set(player, duel.who + ":day", day);
        set(player, duel.who + ":last", System.currentTimeMillis());
        Factions.give(player, 25, name(duel.who) + "'s duel, day " + day);
        var panel = Notice.of(Notice.Kind.SUCCESS).tag("DUEL").panel();
        if (day == 7) {
            ItemDefinition def = ItemRegistry.skyblock("BAG_OF_GOLD");
            if (def != null) {
                ItemStack bag = SkyItems.create(def, 1);
                if (!player.getInventory().add(bag)) {
                    player.drop(bag, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
            panel.line(Component.literal(" Seven days, seven wins: a Bag of Gold!").withStyle(ChatFormatting.GOLD));
        } else {
            panel.line(Component.literal(" Come back tomorrow for day " + (day + 1) + ".").withStyle(ChatFormatting.GRAY));
        }
        panel.send(player, Component.literal("You beat " + name(duel.who) + "!").withStyle(ChatFormatting.GREEN));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1f, 1f);
    }
}
