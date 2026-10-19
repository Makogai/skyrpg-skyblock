package com.skyrpg.skyblock.ability;

import com.google.gson.JsonObject;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.dungeon.Catacombs;
import com.skyrpg.skyblock.rift.Rift;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * The Maddox Batphone's "Whassup?": call Maddox from anywhere. It rings three times, then he picks up and his slayer screen
 * opens (Hypixel shows a clickable OPEN MENU; decided: it opens by itself, as nothing else would run for you). After a call
 * he's busy for 20 seconds: the phone rings out with one of Hypixel's lines, and the third try in a row is scolded. There's
 * no reception in the Catacombs or the Rift.
 */
final class Batphone {
    private static final int COOLDOWN = 20 * 20;
    private static final int RING = 20;
    private static final List<String> ANSWERS = List.of("Hello?", "Someone answers!", "How does a lobster answer? Shello!", "Hey what do you need?",
            "You hear the line pick up...", "You again? What do you want this time?");
    private static final List<String> BUSY = List.of("Please leave your message after the beep.",
            "How can you tell if a bee is on the phone? You get a buzzy signal!", "The phone keeps ringing, is it broken?",
            "The phone picks up but it immediately hangs up!", "What did the cat say on the phone? Can you hear meow?", "No answer.",
            "Seems like it's not picking up!", "\"Your call is important to us, please stay on the line\", so you hang up.");

    /** A call in progress: when it started. */
    private static final Map<UUID, Long> RINGING = new HashMap<>();
    /** Player -> server tick Maddox is free again. */
    private static final Map<UUID, Long> BUSY_UNTIL = new HashMap<>();
    /** Player -> calls turned away in a row. */
    private static final Map<UUID, Integer> TRIES = new HashMap<>();

    private Batphone() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Batphone::tick);
    }

    /** The ability: false (no cooldown, no "Whassup?!") when nobody picks up. */
    static boolean call(ServerPlayer player) {
        long now = player.level().getServer().getTickCount();
        if (Rift.isRift(player.level()) || Catacombs.inRun(player)) {
            player.sendSystemMessage(Component.literal("✆ There is no reception in here!").withStyle(ChatFormatting.RED));
            return false;
        }
        if (RINGING.containsKey(player.getUUID()) || now < BUSY_UNTIL.getOrDefault(player.getUUID(), 0L)) {
            int tries = TRIES.merge(player.getUUID(), 1, Integer::sum);
            String line = tries >= 3 ? "HEY IT'S NOT PICKING UP STOP TRYING!" : BUSY.get(player.getRandom().nextInt(BUSY.size()));
            player.sendSystemMessage(Component.literal("✆ " + line).withStyle(ChatFormatting.RED));
            return false;
        }
        TRIES.remove(player.getUUID());
        RINGING.put(player.getUUID(), now);
        BUSY_UNTIL.put(player.getUUID(), now + COOLDOWN);
        return true;
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, Long>> calls = RINGING.entrySet().iterator();
        while (calls.hasNext()) {
            Map.Entry<UUID, Long> call = calls.next();
            ServerPlayer player = server.getPlayerList().getPlayer(call.getKey());
            long age = now - call.getValue();
            if (player == null || !player.isAlive()) {
                calls.remove();
                continue;
            }
            if (age % RING != 0) {
                continue;
            }
            int step = (int) (age / RING);
            if (step < 3) {
                player.sendSystemMessage(Component.literal("✆ " + String.join(" ", java.util.Collections.nCopies(step + 1, "Ring...")))
                        .withStyle(ChatFormatting.YELLOW));
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f, 1.2f);
            } else {
                player.sendSystemMessage(Component.literal("✆ " + ANSWERS.get(player.getRandom().nextInt(ANSWERS.size()))).withStyle(ChatFormatting.GREEN));
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 0.8f, 1.6f);
                Actions.run("open_slayer", player, new JsonObject());
                calls.remove();
            }
        }
    }
}
