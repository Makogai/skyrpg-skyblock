package com.skyrpg.skyblock.garden;

import com.mojang.serialization.Codec;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.profile.Profile;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * The Sprayonator (Hypixel's): right-click sprays the selected material onto the Garden plot you're on (one of it a
 * spray, 3 s apart); sneak + right-click picks the material (Hypixel's Configure is a left click). A sprayed plot draws
 * pests for 30 minutes (45 with the Juicy Sprayonator): +25 Bonus Pest Chance (+50) and 1.5x the spawns (2x), and its
 * material's pests are far likelier (+1,000 spawn weight against 100). Pests drop the materials (10%).
 */
public final class Sprays {
    /** A material and the pests it draws. */
    record Material(String item, String name, List<String> pests) {
    }

    static final List<Material> MATERIALS = List.of(new Material("COMPOST", "Compost", List.of("Earthworm", "Mosquito")),
            new Material("PLANT_MATTER", "Plant Matter", List.of("Locust", "Slug")), new Material("DUNG", "Dung", List.of("Beetle", "Fly")),
            new Material("HONEY_JAR", "Honey Jar", List.of("Moth", "Cricket")), new Material("CHEESE_FUEL", "Tasty Cheese", List.of("Rat", "Mite")),
            new Material("JELLY", "Jelly", List.of("Praying Mantis", "Dragonfly", "Firefly")));

    /** A sprayed plot: until when, with what, juicy or not. */
    record Spray(long until, String material, boolean juicy) {
    }

    static final AttachmentType<String> SELECTED = AttachmentRegistry.create(SkyRpgSkyblock.id("sprayonator_material"),
            builder -> builder.persistent(Codec.STRING).copyOnDeath());
    /** By "profile:plot". */
    private static final Map<String, Spray> SPRAYED = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private Sprays() {
    }

    static void init() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(player instanceof ServerPlayer server) || level.isClientSide()) {
                return InteractionResult.PASS;
            }
            ItemDefinition def = SkyItems.definition(player.getItemInHand(hand));
            if (def == null || !"SPRAYONATOR".equals(def.skyblockId()) && !"JUICY_SPRAYONATOR".equals(def.skyblockId())) {
                return InteractionResult.PASS;
            }
            if (server.isShiftKeyDown()) {
                configure(server);
            } else {
                spray(server, "JUICY_SPRAYONATOR".equals(def.skyblockId()));
            }
            return InteractionResult.SUCCESS;
        });
        ServerTickEvents.END_SERVER_TICK.register(Sprays::tick);
    }

    static Material selected(ServerPlayer player) {
        String id = player.getAttachedOrElse(SELECTED, "COMPOST");
        return MATERIALS.stream().filter(m -> m.item().equals(id)).findFirst().orElse(MATERIALS.get(0));
    }

    private static String key(Profile owner, int plot) {
        return owner.id() + ":" + plot;
    }

    /** The spray on the plot at this position, or null. */
    static Spray at(BlockPos pos) {
        Profile owner = Garden.ownerAt(pos);
        int[] grid = Garden.gridAt(pos);
        if (owner == null || grid == null) {
            return null;
        }
        Spray spray = SPRAYED.get(key(owner, Garden.plotNumber(grid[0], grid[1])));
        return spray == null || spray.until() < System.currentTimeMillis() ? null : spray;
    }

    private static void spray(ServerPlayer player, boolean juicy) {
        long now = System.currentTimeMillis();
        if (now - LAST.getOrDefault(player.getUUID(), 0L) < 3_000) {
            player.sendOverlayMessage(Component.literal("The Sprayonator is recharging.").withStyle(ChatFormatting.RED));
            return;
        }
        GardenData.State state = Garden.state(player);
        if (!Garden.isGarden(player.level()) || state == null) {
            player.sendOverlayMessage(Component.literal("Spray your plots in the Garden.").withStyle(ChatFormatting.RED));
            return;
        }
        if (Garden.level(state) < 5) {
            player.sendOverlayMessage(Component.literal("The Sprayonator needs Garden Level 5.").withStyle(ChatFormatting.RED));
            return;
        }
        Profile owner = Garden.ownerAt(player.blockPosition());
        int[] grid = Garden.gridAt(player.blockPosition());
        int plot = grid == null ? 0 : Garden.plotNumber(grid[0], grid[1]);
        if (owner == null || plot <= 0) {
            player.sendOverlayMessage(Component.literal("Stand on one of your plots to spray it.").withStyle(ChatFormatting.RED));
            return;
        }
        Material material = selected(player);
        if (!Sacks.takeAnywhere(player, material.item(), 1)) {
            player.sendOverlayMessage(Component.literal("You don't have any " + material.name() + ". Sneak + right-click to pick another.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        LAST.put(player.getUUID(), now);
        SPRAYED.put(key(owner, plot), new Spray(now + (juicy ? 45 : 30) * 60_000L, material.item(), juicy));
        ServerLevel level = (ServerLevel) player.level();
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, player.getX(), player.getY() + 1, player.getZ(), 40, 4, 1, 4, 0);
        Msg.playSound(player, SoundEvents.BREWING_STAND_BREW, 1f, 1.4f);
        player.sendSystemMessage(Component.literal("SPRAYONATOR! ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("You sprayed Plot - " + plot + " with " + material.name() + " (" + (juicy ? 45 : 30)
                        + "m): it draws " + String.join(" and ", material.pests()) + ".").withStyle(ChatFormatting.GRAY)));
    }

    private static void configure(ServerPlayer player) {
        CardMenu.open(player, p -> {
            Material chosen = selected(p);
            List<CardView.Card> cards = new ArrayList<>();
            for (Material material : MATERIALS) {
                ItemDefinition def = ItemRegistry.skyblock(material.item());
                ItemStack icon = def == null ? ItemStack.EMPTY : SkyItems.create(def, 1);
                long have = Sacks.countAnywhere(p, material.item());
                boolean on = material == chosen;
                cards.add(new CardView.Card(material.name(), "Draws " + String.join(", ", material.pests()), on ? 0x55FF55 : 0xEEEEEE, !on,
                        on ? "Selected" : "Select", icon, "", List.of(new Chip("", ItemStack.EMPTY, Msg.number(have) + " held", have > 0)), -1, ""));
            }
            return new CardView("FARMING", "Sprayonator", "Pick a material", -1, List.of(), cards,
                    "A spray lasts 30 minutes: +25 Bonus Pest Chance, 1.5x pests, and its pests come far more often.", 1, 34);
        }, (p, card) -> {
            p.setAttached(SELECTED, MATERIALS.get(card).item());
            return "";
        });
    }

    /** Sprayed plots mist a little, for whoever's in the Garden. */
    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0 || SPRAYED.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        SPRAYED.values().removeIf(s -> s.until() < now);
        ServerLevel level = Garden.level(server);
        if (level == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            Spray spray = at(player.blockPosition());
            if (spray != null) {
                level.sendParticles(player, ParticleTypes.SPORE_BLOSSOM_AIR, false, false, player.getX(), player.getY() + 2, player.getZ(), 6, 6, 1.5, 6, 0);
            }
        }
    }
}
