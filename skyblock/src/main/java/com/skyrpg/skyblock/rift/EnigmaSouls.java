package com.skyrpg.skyblock.rift;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.hub.mixin.DisplayAccess;
import com.skyrpg.hub.mixin.ItemDisplayAccess;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Enigma Souls (Hypixel's Rift counterpart of Fairy Souls): violet souls hidden all over the Rift (the builds' "enigma:"
 * spots) plus a few earned from NPCs (Tel Kar's story, Roy's soul for Motes). Each is found once. With the Enigma Cloak
 * (100 Motes from Enigma) every soul is +20 Rift Time; every 4 souls Enigma sells an upgrade: +2 Speed and +4% Mana
 * Regen in the Rift (decided: 250 Motes times the upgrade's number, Hypixel's price wasn't found).
 */
final class EnigmaSouls {
    static final String TAG = "skyrpg_enigma_soul";
    /** Souls not placed in the world: given by NPCs. */
    static final List<String> EXTRA = List.of("npc:Tel Kar", "npc:Roy", "npc:Cryptosis", "npc:Fairylosopher");
    private static final double NEAR = 48;

    /** Souls found ("enigma:Two Plates", "npc:Roy"). */
    static final AttachmentType<List<String>> FOUND = AttachmentRegistry.create(SkyRpgSkyblock.id("enigma_souls"),
            builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
    /** The cloak (1 once claimed) and its upgrades. */
    static final AttachmentType<Map<String, Integer>> CLOAK = AttachmentRegistry.create(SkyRpgSkyblock.id("enigma_cloak"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    private static final Map<String, Entity[]> LIVE = new HashMap<>();
    private static final Map<UUID, String> BY_ENTITY = new HashMap<>();

    private EnigmaSouls() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(EnigmaSouls::tick);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer server) || level.isClientSide() || !entity.entityTags().contains(TAG)) {
                return InteractionResult.PASS;
            }
            String key = BY_ENTITY.get(entity.getUUID());
            String locked = key == null || has(server, key) ? null : locked(server, key);
            if (locked != null) {
                server.sendOverlayMessage(Component.literal(locked).withStyle(ChatFormatting.RED));
            } else if (key != null) {
                find(server, key);
            }
            return InteractionResult.SUCCESS;
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && !BY_ENTITY.containsKey(entity.getUUID())) {
                entity.discard();
            }
        });
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Enigma Cloak";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                if (!hasCloak(player)) {
                    return;
                }
                out.add(StatTypes.RIFT_TIME, 20.0 * found(player));
                int upgrades = upgrades(player);
                if (upgrades > 0 && Rift.isRift(player.level())) {
                    out.add(StatTypes.SPEED, 2.0 * upgrades);
                    out.add(StatTypes.RIFT_MANA_REGEN, 4.0 * upgrades);
                }
            }
        });
    }

    /** Why a placed soul can't be taken yet (its puzzle isn't done), or null. */
    static String locked(ServerPlayer player, String key) {
        if (key.equals(WestVillage.HOT_DOG_SOUL) && !RiftFlags.has(player, "hotdog")) {
            return "Win Joey McPizza's hot dog contest for this soul (ask the Chef).";
        }
        boolean shania = RiftFlags.has(player, "shania_harvest");
        if (key.equals(Dreadfarm.BUTTON_SOUL) && !(shania && RiftFlags.has(player, "buttons"))) {
            return shania ? "Press every button on the wall first." : "Bring Shania her harvest first, then press every button.";
        }
        if (Dreadfarm.BALLOON_SOULS.contains(key) && !RiftFlags.has(player, "balloons:" + key)) {
            boolean needsShania = key.contains("Farm") || key.contains("Plaza");
            return needsShania && !shania ? "Bring Shania her harvest first, then pop every balloon here with a Berberis Blowgun."
                    : "Pop every balloon here with a Berberis Blowgun first.";
        }
        return null;
    }

    static List<String> placed() {
        return Rift.spots().keySet().stream().filter(k -> k.startsWith("enigma:")).sorted().toList();
    }

    static int total() {
        return placed().size() + EXTRA.size();
    }

    static int found(ServerPlayer player) {
        return player.getAttachedOrElse(FOUND, List.of()).size();
    }

    static boolean has(ServerPlayer player, String key) {
        return player.getAttachedOrElse(FOUND, List.of()).contains(key);
    }

    static boolean hasCloak(ServerPlayer player) {
        return player.getAttachedOrElse(CLOAK, Map.of()).getOrDefault("cloak", 0) > 0;
    }

    static int upgrades(ServerPlayer player) {
        return player.getAttachedOrElse(CLOAK, Map.of()).getOrDefault("upgrades", 0);
    }

    static void setCloak(ServerPlayer player, String key, int value) {
        Map<String, Integer> cloak = new HashMap<>(player.getAttachedOrElse(CLOAK, Map.of()));
        cloak.put(key, value);
        player.setAttached(CLOAK, Map.copyOf(cloak));
        Stats.refresh(player);
    }

    /** Finds a soul (once): a placed one by clicking it, an NPC's by finishing what they ask. */
    static void find(ServerPlayer player, String key) {
        if (has(player, key)) {
            player.sendOverlayMessage(Component.literal("You already found this Enigma Soul.").withStyle(ChatFormatting.GRAY));
            return;
        }
        List<String> found = new ArrayList<>(player.getAttachedOrElse(FOUND, List.of()));
        found.add(key);
        player.setAttached(FOUND, List.copyOf(found));
        Stats.refresh(player);
        String name = key.substring(key.indexOf(':') + 1);
        Notice.of(Notice.Kind.SUCCESS).tag("ENIGMA SOUL").panel()
                .line(Component.literal(" " + name + "  ·  " + found.size() + "/" + total() + " found").withStyle(ChatFormatting.GRAY))
                .line(Component.literal(hasCloak(player) ? " +20 ф Rift Time from your Enigma Cloak" : " Claim Enigma's Cloak to make souls count")
                        .withStyle(ChatFormatting.GRAY))
                .send(player, Component.literal("You found an Enigma Soul!").withStyle(ChatFormatting.LIGHT_PURPLE));
        Msg.playSound(player, SoundEvents.AMETHYST_CLUSTER_BREAK, 1f, 1.4f);
    }

    // ---------------------------------------------------------------- the souls in the world

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel level = Rift.level(server);
        if (level == null) {
            return;
        }
        for (String key : placed()) {
            Rift.Spot spot = Rift.spots().get(key);
            List<ServerPlayer> near = level.getPlayers(p -> p.position().distanceToSqr(spot.pos()) < NEAR * NEAR);
            Entity[] live = LIVE.get(key);
            if (near.isEmpty() || !level.isLoaded(net.minecraft.core.BlockPos.containing(spot.pos()))) {
                if (live != null) {
                    despawn(key, live);
                }
                continue;
            }
            if (live == null || live[0].isRemoved()) {
                if (live != null) {
                    despawn(key, live);
                }
                spawn(level, key, spot);
            }
            for (ServerPlayer player : near) {
                if (!has(player, key)) {
                    level.sendParticles(player, ParticleTypes.WITCH, false, false, spot.pos().x, spot.pos().y + 0.9, spot.pos().z, 3, 0.25, 0.3,
                            0.25, 0.01);
                }
            }
        }
    }

    private static void spawn(ServerLevel level, String key, Rift.Spot spot) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        display.snapTo(spot.pos().x, spot.pos().y + 0.2, spot.pos().z, 0, 0);
        ((ItemDisplayAccess) display).skyrpg$setItemStack(new ItemStack(Items.STAINED_GLASS.purple()));
        DisplayAccess access = (DisplayAccess) display;
        access.skyrpg$setTransformation(new Transformation(new Vector3f(0, 0.55f, 0), new Quaternionf().rotateXYZ(0.785f, 0.6f, 0),
                new Vector3f(0.45f, 0.45f, 0.45f), new Quaternionf()));
        access.skyrpg$setBrightnessOverride(new net.minecraft.util.Brightness(15, 15));
        display.setGlowingTag(true);
        display.addTag(TAG);
        Interaction box = EntityTypes.INTERACTION.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        Entity[] live = box == null ? new Entity[] {display} : new Entity[] {display, box};
        LIVE.put(key, live);
        BY_ENTITY.put(display.getUUID(), key);
        if (box != null) {
            box.snapTo(spot.pos().x, spot.pos().y + 0.4, spot.pos().z, 0, 0);
            box.addTag(TAG);
            BY_ENTITY.put(box.getUUID(), key);
        }
        level.addFreshEntity(display);
        if (box != null) {
            level.addFreshEntity(box);
        }
    }

    private static void despawn(String key, Entity[] live) {
        for (Entity entity : live) {
            BY_ENTITY.remove(entity.getUUID());
            entity.discard();
        }
        LIVE.remove(key);
    }
}
