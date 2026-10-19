package com.skyrpg.skyblock.hollows;

import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;

/**
 * The five Crystals and the Crystal Nucleus. Each comes from its structure's quest (Jade: Divan's four lost tools,
 * Amber: King Yolkar's scent and the Queen's Den, Amethyst: a Jungle Key and the temple, Sapphire: Professor Robot's
 * parts, Topaz: Bal) and is placed by right-clicking its statue in the Nucleus. All five make the Loot Bundle
 * ({@link NucleusLoot}) and count as a Nucleus run. No second crystal of a kind until all five are placed, as on Hypixel.
 */
public final class Crystals {
    /** A crystal: its name, colour and statue (the pedestal's x and z in the Nucleus; its top block is at y 113). */
    public enum Gem {
        JADE("Jade", 0x55FF55, 6, -8),
        AMBER("Amber", 0xFFAA00, -10, 3),
        TOPAZ("Topaz", 0xFFFF55, 0, 10),
        SAPPHIRE("Sapphire", 0x55FFFF, 10, 3),
        AMETHYST("Amethyst", 0xAA00AA, -6, -8);

        public final String title;
        public final int color;
        final int x;
        final int z;

        Gem(String title, int color, int x, int z) {
            this.title = title;
            this.color = color;
            this.x = x;
            this.z = z;
        }

        public String item() {
            return name() + "_CRYSTAL";
        }

        Component named() {
            return Component.literal(title + " Crystal").withColor(color);
        }
    }

    /** Where a crystal is claimed that isn't handed over by an NPC: the Divan pedestal, the Queen's throne, the temple altar. */
    private record Spot(Gem gem, BlockPos pos) {
    }

    private static final Spot[] SPOTS = {new Spot(Gem.JADE, new BlockPos(145, 103, -145)), new Spot(Gem.AMBER, new BlockPos(-128, 102, 121)),
            new Spot(Gem.AMETHYST, new BlockPos(-140, 101, -158))};

    private Crystals() {
    }

    static void init() {
        Identifier early = SkyRpgSkyblock.id("crystals_early");
        UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(early, (player, level, hand, hit) -> {
            if (!level.dimension().equals(CrystalHollows.LEVEL) || !(player instanceof ServerPlayer server) || hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            BlockPos pos = hit.getBlockPos();
            for (Gem gem : Gem.values()) {
                if (Math.abs(pos.getX() - gem.x) <= 1 && Math.abs(pos.getZ() - gem.z) <= 1 && pos.getY() >= 111 && pos.getY() <= 114) {
                    place(server, gem);
                    return InteractionResult.SUCCESS;
                }
            }
            for (Spot spot : SPOTS) {
                if (Math.abs(pos.getX() - spot.pos().getX()) <= 2 && Math.abs(pos.getZ() - spot.pos().getZ()) <= 2
                        && Math.abs(pos.getY() - spot.pos().getY()) <= 3) {
                    claimAt(server, spot.gem());
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(Crystals::tick);
    }

    /** Whether the player may be given this crystal now (not holding it, not placed it, till all five are placed). */
    public static boolean claimable(ServerPlayer player, Gem gem) {
        return !HollowsState.has(player, "claimed." + gem.name());
    }

    /** Hands the player the crystal (the quest is done). Returns false if they already have it this run. */
    public static boolean give(ServerPlayer player, Gem gem) {
        if (!claimable(player, gem)) {
            Notice.send(player, Notice.Kind.WARN, Component.literal("You haven't placed the " + gem.title + " Crystal yet! Take it to the Crystal Nucleus.")
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        HollowsState.set(player, "claimed." + gem.name(), 1);
        HollowsState.give(player, gem.item(), 1, ItemGains.Source.OTHER);
        Notice.of(Notice.Kind.MINING).tag("CRYSTAL").send(player, Component.literal("You claimed the ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(gem.named()).append(Component.literal("! Place it in the Crystal Nucleus.").withStyle(ChatFormatting.LIGHT_PURPLE)));
        Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.0f);
        return true;
    }

    /** Right-click at a claim spot: Jade, Amber and Amethyst, if their quest is done. */
    private static void claimAt(ServerPlayer player, Gem gem) {
        switch (gem) {
            case JADE -> {
                if (!Keepers.complete(player)) {
                    Notice.send(player, Notice.Kind.WARN, Component.literal("Divan's four lost tools are still missing. Ask the Keepers.").withStyle(ChatFormatting.RED));
                    return;
                }
                give(player, gem);
            }
            case AMBER -> {
                if (HollowsState.get(player, "scent") <= System.currentTimeMillis()) {
                    Notice.send(player, Notice.Kind.WARN, Component.literal("The Goblin Queen's guards spot you! King Yolkar's scent would have hidden you.")
                            .withStyle(ChatFormatting.RED));
                    GoblinQueen.guards(player);
                    return;
                }
                if (give(player, gem)) {
                    HollowsState.set(player, "scent", 0);
                    player.sendSystemMessage(Component.literal("King's Scent faded away.").withStyle(ChatFormatting.GRAY));
                }
            }
            case AMETHYST -> {
                if (!HollowsState.has(player, "temple")) {
                    Notice.send(player, Notice.Kind.WARN, Component.literal("You haven't earned this Crystal! Come back when you have >:-)").withStyle(ChatFormatting.RED));
                    return;
                }
                if (give(player, gem)) {
                    HollowsState.set(player, "temple", 0);
                    TreasureChests.reward(player, "Jungle", 2, true);
                    TreasureChests.reward(player, "Jungle", 2, true);
                }
            }
            default -> {
            }
        }
    }

    /** Right-click on a statue. */
    private static void place(ServerPlayer player, Gem gem) {
        if (HollowsState.has(player, "placed." + gem.name())) {
            player.sendOverlayMessage(Component.literal("The " + gem.title + " Crystal is already placed.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!HollowsState.take(player, gem.item(), 1)) {
            player.sendOverlayMessage(Component.literal("Place the ").withStyle(ChatFormatting.GRAY).append(gem.named())
                    .append(Component.literal(" here. Find it in the Crystal Hollows!").withStyle(ChatFormatting.GRAY)));
            return;
        }
        HollowsState.set(player, "placed." + gem.name(), 1);
        Notice.of(Notice.Kind.MINING).tag("CRYSTAL NUCLEUS").send(player, Component.literal("You placed the ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(gem.named()).append(Component.literal("! Keep exploring the Crystal Hollows to find the rest!").withStyle(ChatFormatting.LIGHT_PURPLE)));
        Msg.playSound(player, SoundEvents.BEACON_ACTIVATE, 1.0f, 1.4f);
        for (Gem each : Gem.values()) {
            if (!HollowsState.has(player, "placed." + each.name())) {
                return;
            }
        }
        complete(player);
    }

    /** All five placed: the bundle, the run counted, and the quests ready to be done again. */
    private static void complete(ServerPlayer player) {
        long runs = HollowsState.get(player, "total.runs") + 1;
        HollowsState.set(player, "total.runs", runs);
        HollowsState.clearRun(player, "placed.", "claimed.", "scent", "temple", "part.", "divan.");
        if (runs <= 50) {
            SkyBlockXp.give(player, 4, "Crystal Nucleus Runs");
        }
        NucleusLoot.give(player);
        player.sendSystemMessage(Component.literal("Crystal Nucleus runs: " + runs).withStyle(ChatFormatting.GRAY));
    }

    private static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        ServerLevel level = server.getLevel(CrystalHollows.LEVEL);
        if (level == null || tick % 20 != 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isInWater() && HollowsState.get(player, "scent") > 0) {
                HollowsState.set(player, "scent", 0);
                player.sendSystemMessage(Component.literal("The water washed King's Scent away!").withStyle(ChatFormatting.RED));
            }
            for (Spot spot : SPOTS) {
                if (player.blockPosition().distSqr(spot.pos()) < 25 && claimable(player, spot.gem()) && ready(player, spot.gem())) {
                    player.sendOverlayMessage(Component.literal("Right-click to claim the ").withStyle(ChatFormatting.GRAY).append(spot.gem().named()));
                }
            }
        }
    }

    private static boolean ready(ServerPlayer player, Gem gem) {
        return switch (gem) {
            case JADE -> Keepers.complete(player);
            case AMBER -> HollowsState.get(player, "scent") > System.currentTimeMillis();
            case AMETHYST -> HollowsState.has(player, "temple");
            default -> false;
        };
    }
}
