package com.skyrpg.skyblock.rift;

import com.mojang.serialization.Codec;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import com.skyrpg.skyblock.sack.Sacks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * West Village's quests (Hypixel's): Gunther's race, a lap of ten checkpoints round the village and back to the start
 * line (under a minute: a Dark Pebble; under RACE_LICHEN: the Gunthesizer Lichen, which turns Wyld Boots into
 * Gunther's Sneakers), and Joey McPizza's hot dog contest (the Chef's stall flings hot dogs for a minute: carry 50 to
 * Joey's plate for an Enigma Soul).
 */
final class WestVillage {
    static final int CHECKPOINTS = 10;
    /** Hypixel's thresholds, in ticks. */
    static final int RACE_PEBBLE = 60 * 20;
    static final int RACE_LICHEN = 35 * 20;
    private static final int RACE_GIVE_UP = 3 * 60 * 20;
    private static final int COUNTDOWN = 60;
    private static final double REACH = 3.2;

    /** Your best lap, in ticks (0 for none). */
    static final AttachmentType<Integer> RACE_BEST = AttachmentRegistry.create(SkyRpgSkyblock.id("rift_race_best"),
            builder -> builder.persistent(Codec.INT).copyOnDeath());

    private static final class Run {
        int ticks = -COUNTDOWN;
        int next = 1;
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();

    static final int HOT_DOGS = 50;
    private static final int CONTEST_TICKS = 60 * 20;
    private static final String HOT_DOG = "HOT_DOG";
    static final String HOT_DOG_SOUL = "enigma:Hot Dog Contest";

    private static final class Contest {
        int ticks;
        int delivered;
        Vec3 stall;
        /** Where the Chef faces (radians, Minecraft's yaw): the hot dogs fly out over his counter. */
        double facing;
        final List<ItemEntity> dogs = new ArrayList<>();
    }

    private static final Map<UUID, Contest> CONTESTS = new HashMap<>();

    private WestVillage() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(WestVillage::tick);
    }

    // ---------------------------------------------------------------- Gunther's race

    static void gunther(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            int best = p.getAttachedOrElse(RACE_BEST, 0);
            boolean pebble = RiftFlags.has(p, "race:pebble");
            boolean lichen = RiftFlags.has(p, "race:lichen");
            boolean racing = RUNS.containsKey(p.getUUID());
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card(racing ? "Racing!" : "Start the race", racing ? "Run through the glowing checkpoints"
                    : "Ten checkpoints round the village, then back here", 0x55FF55, !racing, racing ? "Go!" : "Race",
                    new ItemStack(Items.LEATHER_BOOTS), "", List.of(), -1, best == 0 ? "No lap yet" : "Best " + time(best)));
            cards.add(new CardView.Card("Under a minute", pebble ? "Gunther gave you a Dark Pebble." : "Finish in under 1:00.0", 0xAAAAAA, false, "",
                    RiftNpcs.icon("DARK_PEBBLE"), "", List.of(), pebble ? 1 : best == 0 ? 0 : Math.min(1f, RACE_PEBBLE / (float) best),
                    pebble ? "Done" : ""));
            cards.add(new CardView.Card("Under " + RACE_LICHEN / 20 + " seconds", lichen ? "Gunther gave you his Gunthesizer Lichen."
                    : "Finish in under 0:" + RACE_LICHEN / 20 + ".0", 0x55FFFF, false, "", RiftNpcs.icon("GUNTHESIZER_LICHEN"), "", List.of(),
                    lichen ? 1 : best == 0 ? 0 : Math.min(1f, RACE_LICHEN / (float) best), lichen ? "Done" : ""));
            cards.add(new CardView.Card("Gunther's Sneakers", "Wyld Boots, the Gunthesizer Lichen and 8 Shadow Crux: craft them", 0x5555FF,
                    false, "", RiftNpcs.icon("GUNTHER_SNEAKERS"), "", List.of(), -1, "+30s, +4 Speed"));
            return new CardView("RIFT", "Gunther", "Rift Race", npc, List.of(), cards, "Sprint and jump; the timer starts on GO.", 1, 36);
        }, (p, card) -> {
            if (card != 0 || RUNS.containsKey(p.getUUID())) {
                return "";
            }
            Rift.Spot start = Rift.spots().get("race_start");
            if (start == null) {
                return "The race track isn't built here.";
            }
            p.teleportTo((ServerLevel) p.level(), start.pos().x, start.pos().y, start.pos().z, java.util.Set.of(), start.yaw(), 0, true);
            RUNS.put(p.getUUID(), new Run());
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 22, 4));
            return null;
        });
    }

    private static void tick(MinecraftServer server) {
        if (!CONTESTS.isEmpty()) {
            contests(server);
        }
        if (RUNS.isEmpty()) {
            return;
        }
        ServerLevel level = Rift.level(server);
        Map<String, Rift.Spot> spots = Rift.spots();
        for (UUID id : List.copyOf(RUNS.keySet())) {
            Run run = RUNS.get(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || level == null || player.level() != level || player.isDeadOrDying() || run.ticks > RACE_GIVE_UP) {
                RUNS.remove(id);
                if (player != null) {
                    player.sendOverlayMessage(Component.literal("Race cancelled.").withStyle(ChatFormatting.RED));
                }
                continue;
            }
            run.ticks++;
            if (run.ticks <= 0) {
                if (run.ticks % 20 == -COUNTDOWN % 20 || run.ticks == 0) {
                    int left = -run.ticks / 20;
                    player.connection.send(new ClientboundSetTitleTextPacket(left > 0 ? Component.literal(String.valueOf(left)).withColor(0xFFFF55)
                            : Component.literal("GO!").withColor(0x55FF55)));
                    Msg.playSound(player, left > 0 ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.PLAYER_LEVELUP, 1f, left > 0 ? 1f : 1.6f);
                }
                if (run.ticks < 0) {
                    Rift.Spot start = spots.get("race_start");
                    if (start != null && player.position().distanceToSqr(start.pos()) > 4) {
                        player.teleportTo(start.pos().x, start.pos().y, start.pos().z);
                    }
                }
                continue;
            }
            Rift.Spot target = spots.get(run.next > CHECKPOINTS ? "race_start" : "race_" + run.next);
            if (target == null) {
                RUNS.remove(id);
                continue;
            }
            Vec3 at = target.pos();
            if (run.ticks % 4 == 0) {
                level.sendParticles(player, ParticleTypes.END_ROD, true, false, at.x, at.y + 1.5, at.z, 6, 0.4, 1.4, 0.4, 0.01);
                Rift.Spot after = spots.get(run.next >= CHECKPOINTS ? "race_start" : "race_" + (run.next + 1));
                if (after != null && run.next <= CHECKPOINTS) {
                    level.sendParticles(player, ParticleTypes.HAPPY_VILLAGER, true, false, after.pos().x, after.pos().y + 1.5, after.pos().z, 2, 0.4,
                            1.2, 0.4, 0);
                }
            }
            double dx = player.getX() - at.x;
            double dz = player.getZ() - at.z;
            if (dx * dx + dz * dz < REACH * REACH && Math.abs(player.getY() - at.y) < 4) {
                if (run.next > CHECKPOINTS) {
                    RUNS.remove(id);
                    finish(player, run.ticks);
                    continue;
                }
                Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f + run.next * 0.08f);
                run.next++;
            }
            if (run.ticks % 2 == 0) {
                String where = run.next > CHECKPOINTS ? "Back to the start line!" : "Checkpoint " + run.next + "/" + CHECKPOINTS;
                player.sendOverlayMessage(Component.literal("Gunther's Race  ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(time(run.ticks)).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("  ·  " + where).withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    private static void finish(ServerPlayer player, int ticks) {
        int best = player.getAttachedOrElse(RACE_BEST, 0);
        boolean record = best == 0 || ticks < best;
        if (record) {
            player.setAttached(RACE_BEST, ticks);
        }
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(time(ticks)).withColor(record ? 0x55FF55 : 0xFFFF55)));
        var panel = Notice.of(Notice.Kind.SUCCESS).tag("RACE").panel()
                .line(Component.literal(" Lap: " + time(ticks) + (record ? "  ·  new best!" : "  ·  best " + time(best))).withStyle(ChatFormatting.GRAY));
        if (ticks < RACE_PEBBLE && RiftFlags.set(player, "race:pebble")) {
            give(player, "DARK_PEBBLE");
            panel.line(Component.literal(" Under a minute: Gunther hands you a Dark Pebble.").withStyle(ChatFormatting.GRAY));
        }
        if (ticks < RACE_LICHEN && RiftFlags.set(player, "race:lichen")) {
            give(player, "GUNTHESIZER_LICHEN");
            panel.line(Component.literal(" Blazing! Gunther gives you his Gunthesizer Lichen.").withStyle(ChatFormatting.GRAY));
        }
        panel.send(player, Component.literal("You finished Gunther's race!").withStyle(ChatFormatting.YELLOW));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1f, 1.2f);
    }

    // ---------------------------------------------------------------- Joey McPizza's hot dog contest

    /** The Chef runs the contest. */
    static void chef(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            boolean won = RiftFlags.has(p, "hotdog");
            Contest contest = CONTESTS.get(p.getUUID());
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card(contest != null ? "Contest on!" : "Hot Dog Contest", contest != null ? "Grab the hot dogs, take them to Joey"
                    : "60 seconds: carry " + HOT_DOGS + " hot dogs to Joey", 0xFFAA00, contest == null, contest != null ? "Go!" : "Start",
                    RiftNpcs.icon(HOT_DOG), "", List.of(), contest == null ? -1 : contest.delivered / (float) HOT_DOGS,
                    contest == null ? "" : contest.delivered + "/" + HOT_DOGS));
            cards.add(new CardView.Card("Joey's life goal", won ? "Done: Joey ate more than science says he can." : "Fifty hot dogs. Make it happen.",
                    0xFF55FF, false, "", new ItemStack(Items.AMETHYST_SHARD), "", List.of(), won ? 1 : 0, "Enigma Soul"));
            return new CardView("RIFT", "Chef", "Hot dogs", npc, List.of(), cards, "Right-click Joey to hand over the hot dogs you carry.", 1, 40);
        }, (p, card) -> {
            if (card != 0 || CONTESTS.containsKey(p.getUUID())) {
                return "";
            }
            Contest contest = new Contest();
            net.minecraft.world.entity.Entity chef = npc < 0 ? null : p.level().getEntity(npc);
            contest.stall = chef != null ? chef.position().add(0, 1.3, 0) : p.position().add(0, 1.5, 0);
            contest.facing = Math.toRadians(chef != null ? chef.getYRot() : p.getYRot() + 180);
            CONTESTS.put(p.getUUID(), contest);
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 6));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal("HOT DOGS!").withColor(0xFFAA00)));
            chefSays(p, "Ready? The hot dogs are flying! Get them to Joey!");
            return null;
        });
    }

    /** Joey takes the hot dogs you carry (during a contest). */
    static void joey(ServerPlayer player) {
        Contest contest = CONTESTS.get(player.getUUID());
        if (contest == null) {
            player.sendSystemMessage(Component.literal("[NPC] Joey McPizza: ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("Sorry, I can't talk right now. I'm mentally preparing.").withStyle(ChatFormatting.WHITE)));
            return;
        }
        int carried = (int) Math.min(Integer.MAX_VALUE, Sacks.countAnywhere(player, HOT_DOG));
        if (carried == 0) {
            player.sendOverlayMessage(Component.literal("*nom nom* ...more!").withStyle(ChatFormatting.YELLOW));
            return;
        }
        Sacks.takeAnywhere(player, HOT_DOG, carried);
        contest.delivered += carried;
        Msg.playSound(player, SoundEvents.GENERIC_EAT.value(), 1f, 0.9f + player.getRandom().nextFloat() * 0.3f);
        player.sendOverlayMessage(Component.literal("*nom nom nom* ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(contest.delivered + "/" + HOT_DOGS).withStyle(ChatFormatting.WHITE)));
        if (contest.delivered >= HOT_DOGS) {
            end(player, contest, true);
        }
    }

    private static void contests(MinecraftServer server) {
        for (UUID id : List.copyOf(CONTESTS.keySet())) {
            Contest contest = CONTESTS.get(id);
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !Rift.isRift(player.level())) {
                contest.dogs.forEach(net.minecraft.world.entity.Entity::discard);
                CONTESTS.remove(id);
                continue;
            }
            contest.ticks++;
            ServerLevel level = (ServerLevel) player.level();
            if (contest.ticks % 10 == 0) {
                // The stall flings a hot dog out over the counter, only for you.
                net.minecraft.util.RandomSource random = level.getRandom();
                double yaw = contest.facing + (random.nextDouble() - 0.5) * 1.6;
                double speed = 0.2 + random.nextDouble() * 0.15;
                ItemDefinition def = ItemRegistry.skyblock(HOT_DOG);
                ItemStack stack = def == null ? new ItemStack(Items.COOKED_PORKCHOP) : SkyItems.create(def, 1);
                ItemEntity dog = new ItemEntity(level, contest.stall.x, contest.stall.y, contest.stall.z, stack, -Math.sin(yaw) * speed,
                        0.35 + random.nextDouble() * 0.12, Math.cos(yaw) * speed);
                dog.setTarget(id);
                dog.setPickUpDelay(8);
                level.addFreshEntity(dog);
                contest.dogs.add(dog);
                level.playSound(null, BlockPos.containing(contest.stall), SoundEvents.CHICKEN_EGG, net.minecraft.sounds.SoundSource.NEUTRAL, 0.6f, 1.4f);
            }
            if (contest.ticks % 10 == 0) {
                int left = (CONTEST_TICKS - contest.ticks) / 20;
                player.sendOverlayMessage(Component.literal("Hot Dog Contest  ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(contest.delivered + "/" + HOT_DOGS).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("  ·  " + left + "s left").withStyle(left <= 10 ? ChatFormatting.RED : ChatFormatting.GRAY)));
            }
            if (contest.ticks >= CONTEST_TICKS) {
                end(player, contest, false);
            }
        }
    }

    private static void end(ServerPlayer player, Contest contest, boolean won) {
        CONTESTS.remove(player.getUUID());
        contest.dogs.forEach(net.minecraft.world.entity.Entity::discard);
        long left = Sacks.countAnywhere(player, HOT_DOG);
        if (left > 0) {
            Sacks.takeAnywhere(player, HOT_DOG, (int) left);
        }
        if (won) {
            player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("FIFTY!").withColor(0x55FF55)));
            chefSays(player, "Fifty hot dogs! That surpasses the scientists' theoretical maximum!");
            RiftFlags.set(player, "hotdog");
            if (!EnigmaSouls.has(player, HOT_DOG_SOUL)) {
                EnigmaSouls.find(player, HOT_DOG_SOUL);
            }
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1f, 1f);
        } else {
            chefSays(player, "Time! Joey ate " + contest.delivered + ". Not bad, but Joey dreams of " + HOT_DOGS + ".");
            Msg.playSound(player, SoundEvents.VILLAGER_NO, 1f, 1f);
        }
    }

    private static void chefSays(ServerPlayer player, String line) {
        player.sendSystemMessage(Component.literal("[NPC] Chef: ").withStyle(ChatFormatting.YELLOW).append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    static String time(int ticks) {
        int tenths = ticks / 2;
        return (tenths / 600) + ":" + String.format("%02d", tenths / 10 % 60) + "." + tenths % 10;
    }

    static void give(ServerPlayer player, String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        ItemStack stack = SkyItems.create(def, 1);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

}
