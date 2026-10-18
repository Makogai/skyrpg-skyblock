package com.skyrpg.skyblock.rift;

import com.mojang.math.Transformation;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.hub.mixin.DisplayAccess;
import com.skyrpg.hub.mixin.ItemDisplayAccess;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * Montezuma, Sad Jacquelle's lost cat (Hypixel's Rift pet): his soul broke into nine pieces hidden around the Rift (the
 * builds' "montezuma_N" spots). Take a piece and bring it to Jacquelle. Decided until a Montezuma pet exists: every piece
 * returned is +15 Rift Time, and from 6 pieces each is also +2 Mana Regen in the Rift (Hypixel's Nine Lives and Trickery,
 * which need the pet out).
 */
final class Montezuma {
    static final String TAG = "skyrpg_montezuma_piece";
    private static final Map<String, Entity[]> LIVE = new HashMap<>();
    private static final Map<UUID, String> BY_ENTITY = new HashMap<>();

    private Montezuma() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Montezuma::tick);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer server) || level.isClientSide() || !entity.entityTags().contains(TAG)) {
                return InteractionResult.PASS;
            }
            String key = BY_ENTITY.get(entity.getUUID());
            if (key != null) {
                take(server, key);
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
                return "Montezuma";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                long returned = RiftFlags.count(player, "montezuma_returned:");
                if (returned > 0) {
                    out.add(StatTypes.RIFT_TIME, 15.0 * returned);
                    if (returned >= 6 && Rift.isRift(player.level())) {
                        out.add(StatTypes.RIFT_MANA_REGEN, 2.0 * returned);
                    }
                }
            }
        });
    }

    private static void take(ServerPlayer player, String key) {
        if (!RiftFlags.set(player, "montezuma:" + key)) {
            player.sendOverlayMessage(Component.literal("You already have this piece of Montezuma's soul.").withStyle(ChatFormatting.GRAY));
            return;
        }
        Notice.of(Notice.Kind.SUCCESS).tag("MONTEZUMA").send(player, Component.literal("You found a piece of Montezuma's soul! Bring it to Sad Jacquelle.")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1f);
    }

    /** Sad Jacquelle: hand over the pieces you carry. */
    static void jacquelle(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            long found = RiftFlags.count(p, "montezuma:");
            long returned = RiftFlags.count(p, "montezuma_returned:");
            List<CardView.Card> cards = List.of(
                    new CardView.Card("Montezuma's soul", returned + " of 9 pieces back with Jacquelle", 0xFF55FF, false, "", new ItemStack(Items.STRING),
                            "", List.of(), returned / 9f, "+" + 15 * returned + "s"),
                    new CardView.Card("Give her your pieces", found > returned ? (found - returned) + " to give" : "Find more pieces around the Rift",
                            0xFFAA00, found > returned, "Give", new ItemStack(Items.PINK_PETALS), "", List.of(), -1, ""));
            return new CardView("RIFT", "Sad Jacquelle", "Montezuma", npc, List.of(), cards,
                    "Every piece back: +15 Rift Time. From six, Mana Regen in the Rift too.", 1, 34);
        }, (p, card) -> {
            if (card != 1) {
                return "";
            }
            int given = 0;
            for (int n = 1; n <= 9; n++) {
                String key = "montezuma_" + n;
                if (RiftFlags.has(p, "montezuma:" + key) && RiftFlags.set(p, "montezuma_returned:" + key)) {
                    given++;
                }
            }
            if (given > 0) {
                Stats.refresh(p);
                p.sendSystemMessage(Component.literal("Sad Jacquelle: Oh, Montezuma! Thank you... " + given + " more piece" + (given == 1 ? "" : "s")
                        + " of him back.").withStyle(ChatFormatting.LIGHT_PURPLE));
                Msg.playSound(p, SoundEvents.AMETHYST_CLUSTER_PLACE, 1f, 1f);
            }
            return "";
        });
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel level = Rift.level(server);
        if (level == null) {
            return;
        }
        for (Map.Entry<String, Rift.Spot> e : Rift.spots().entrySet()) {
            if (!e.getKey().startsWith("montezuma_")) {
                continue;
            }
            Rift.Spot spot = e.getValue();
            boolean near = !level.getPlayers(p -> p.position().distanceToSqr(spot.pos()) < 40 * 40).isEmpty();
            Entity[] live = LIVE.get(e.getKey());
            if (!near || !level.isLoaded(net.minecraft.core.BlockPos.containing(spot.pos()))) {
                if (live != null) {
                    despawn(e.getKey(), live);
                }
                continue;
            }
            if (live == null || live[0].isRemoved()) {
                if (live != null) {
                    despawn(e.getKey(), live);
                }
                spawn(level, e.getKey(), spot);
            }
            level.sendParticles(ParticleTypes.HEART, spot.pos().x, spot.pos().y + 0.9, spot.pos().z, 1, 0.2, 0.2, 0.2, 0);
        }
    }

    private static void spawn(ServerLevel level, String key, Rift.Spot spot) {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        display.snapTo(spot.pos().x, spot.pos().y + 0.3, spot.pos().z, 0, 0);
        ((ItemDisplayAccess) display).skyrpg$setItemStack(new ItemStack(Items.STRING));
        DisplayAccess access = (DisplayAccess) display;
        access.skyrpg$setTransformation(new Transformation(new Vector3f(0, 0.3f, 0), new Quaternionf(), new Vector3f(0.6f, 0.6f, 0.6f), new Quaternionf()));
        access.skyrpg$setBrightnessOverride(new net.minecraft.util.Brightness(15, 15));
        display.setGlowingTag(true);
        display.addTag(TAG);
        Interaction box = EntityTypes.INTERACTION.create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        Entity[] live = box == null ? new Entity[] {display} : new Entity[] {display, box};
        LIVE.put(key, live);
        BY_ENTITY.put(display.getUUID(), key);
        if (box != null) {
            box.snapTo(spot.pos().x, spot.pos().y, spot.pos().z, 0, 0);
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
