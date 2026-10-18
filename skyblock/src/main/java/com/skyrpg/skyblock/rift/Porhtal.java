package com.skyrpg.skyblock.rift;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Porhtal's seven eyes (Hypixel's): each eye is an NPC somewhere in the Rift; hit it for 300 Rift Damage in all to calm
 * it, for permanent Rift Time (+10 for the first eye up to +70 for the seventh) and a stop on Porhtal's fast travel. A
 * trip costs 5 seconds of Rift Time (Hypixel's 5 per warp). The eyes' places come from the builds ("eye:<name>" spots).
 */
final class Porhtal {
    /** The eyes in order, with the Rift Time each gives once calmed. */
    static final List<String> EYES = List.of("The Intruder", "The Gill-Man", "The Baba Yaga", "The Bankster", "Gooey", "The Prince", "The 7th Sin");
    private static final double CALM = 300;
    private static final int TRIP_SECONDS = 5;
    private static final Map<UUID, Map<String, Double>> DAMAGE = new HashMap<>();

    private Porhtal() {
    }

    static void init() {
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer server) || !Rift.isRift(level) || entity.getCustomName() == null) {
                return InteractionResult.PASS;
            }
            String name = entity.getCustomName().getString();
            if (!EYES.contains(name)) {
                return InteractionResult.PASS;
            }
            if (RiftFlags.has(server, "eye:" + name)) {
                server.sendOverlayMessage(Component.literal(name + " is calm.").withStyle(ChatFormatting.LIGHT_PURPLE));
                return InteractionResult.SUCCESS;
            }
            double done = DAMAGE.computeIfAbsent(server.getUUID(), id -> new HashMap<>()).merge(name, Rift.damage(server), Double::sum);
            ((ServerLevel) level).sendParticles(ParticleTypes.WITCH, entity.getX(), entity.getEyeY(), entity.getZ(), 6, 0.3, 0.3, 0.3, 0);
            if (done >= CALM) {
                calm(server, name);
            } else {
                server.sendOverlayMessage(Component.literal("Calming " + name + ": " + (int) done + "/" + (int) CALM).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            return InteractionResult.SUCCESS;
        });
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Porhtal's Eyes";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                double time = 0;
                for (int i = 0; i < EYES.size(); i++) {
                    if (RiftFlags.has(player, "eye:" + EYES.get(i))) {
                        time += 10 * (i + 1);
                    }
                }
                if (time > 0) {
                    out.add(StatTypes.RIFT_TIME, time);
                }
            }
        });
    }

    private static void calm(ServerPlayer player, String eye) {
        RiftFlags.set(player, "eye:" + eye);
        Stats.refresh(player);
        int bonus = 10 * (EYES.indexOf(eye) + 1);
        Notice.of(Notice.Kind.SUCCESS).tag("PORHTAL").panel()
                .line(Component.literal(" +" + bonus + "s ф Rift Time, for good").withStyle(ChatFormatting.GRAY))
                .line(Component.literal(" Porhtal can carry you here now.").withStyle(ChatFormatting.GRAY))
                .send(player, Component.literal("You calmed " + eye + "!").withStyle(ChatFormatting.LIGHT_PURPLE));
        Msg.playSound(player, SoundEvents.ENDER_EYE_DEATH, 1f, 0.8f);
    }

    /** Porhtal's menu: every eye, and a trip to each one you've calmed. */
    static void open(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            List<CardView.Card> cards = new ArrayList<>();
            for (int i = 0; i < EYES.size(); i++) {
                String eye = EYES.get(i);
                boolean calm = RiftFlags.has(p, "eye:" + eye);
                boolean placed = Rift.spots().containsKey("eye:" + eye);
                cards.add(new CardView.Card(eye, calm ? (placed ? "A trip costs " + TRIP_SECONDS + "s of Rift Time" : "Calm, but out of reach")
                        : "Find it and hit it for " + (int) CALM + " Rift Damage", calm ? 0xFF55FF : 0xAAAAAA, calm && placed, calm ? "Travel" : "",
                        new ItemStack(calm ? Items.ENDER_EYE : Items.ENDER_PEARL), "", List.of(), -1, "+" + 10 * (i + 1) + "s"));
            }
            long calmed = RiftFlags.count(p, "eye:");
            return new CardView("RIFT", "Porhtal", calmed + "/7 eyes calmed", npc, List.of(), cards, "Each calmed eye gives Rift Time for good.", 1, 26);
        }, (p, card) -> {
            String eye = EYES.get(card);
            Rift.Spot spot = Rift.spots().get("eye:" + eye);
            if (!RiftFlags.has(p, "eye:" + eye) || spot == null) {
                return "";
            }
            if (Rift.timeLeft(p) <= TRIP_SECONDS + 10) {
                return "Not enough Rift Time for the trip.";
            }
            Rift.takeTime(p, TRIP_SECONDS);
            p.teleportTo(spot.pos().x + 1.5, spot.pos().y, spot.pos().z + 1.5);
            Msg.playSound(p, SoundEvents.ENDERMAN_TELEPORT, 1f, 1f);
            return null;
        });
    }
}
