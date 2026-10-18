package com.skyrpg.skyblock.galatea;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Galatea's island resources (Hypixel's): Lushlilac (the lilacs in the Reaches and the Wetlands) and Sea Lumies (the sea
 * pickles in the Murkwater Shallows). Hit one to pick it: the item, 15 Foraging XP (Hypixel's), a Lushlilac 9 Starlyn
 * Contest points (Hypixel's), the Heart of the Forest's Collector a chance of double; it grows back a minute later.
 * Lushlilac and its Bonbons are eaten (left-click at a block; right-click puts them in a sack) for Charm Chance a while: Hypixel's 0.5 for
 * 8h, 0.62 for 12h, 0.75 for 18h and 1 for 24h; a new one replaces the last.
 */
final class Resources {
    private static final int REGROW_TICKS = 60 * 20;
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final long HOUR = 3_600_000L;

    /** The Charm Chance a Lushlilac gives and for how many hours. */
    private record Charm(double amount, int hours) {
    }

    private static final Map<String, Charm> CHARMS = Map.of("LUSHLILAC", new Charm(0.5, 8), "LUSHLILAC_BONBON", new Charm(0.62, 12),
            "PRIME_LUSHLILAC_BONBON", new Charm(0.75, 18), "EXALTED_LUSHLILAC_BONBON", new Charm(1, 24));

    /** The Charm Chance you ate and until when (epoch millis). */
    record Buff(double charm, long until) {
        static final Codec<Buff> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("charm").forGetter(Buff::charm),
                Codec.LONG.fieldOf("until").forGetter(Buff::until)).apply(i, Buff::new));
    }

    static final AttachmentType<Buff> BUFF = AttachmentRegistry.create(SkyRpgSkyblock.id("lushlilac"),
            builder -> builder.persistent(Buff.CODEC).copyOnDeath());

    /** Picked resources waiting to grow back: where, what stood there, when. */
    private record Picked(BlockPos pos, Map<BlockPos, BlockState> blocks, long at) {
    }

    private static final List<Picked> PICKED = new ArrayList<>();

    private Resources() {
    }

    static void init() {
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Lushlilac";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                Buff buff = player.getAttached(BUFF);
                if (buff != null && System.currentTimeMillis() < buff.until()) {
                    out.add(StatTypes.CHARM_CHANCE, buff.charm());
                }
            }
        });
        Identifier first = SkyRpgSkyblock.id("galatea_resources");
        AttackBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(first, (player, level, hand, pos, direction) -> {
            if (eat(player, hand)) {
                return InteractionResult.SUCCESS;
            }
            BlockState state = level.getBlockState(pos);
            if (!Galatea.isGalatea(level) || !(state.is(Blocks.LILAC) || state.is(Blocks.SEA_PICKLE))) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer server) {
                pick(server, (ServerLevel) level, pos.immutable(), state);
            }
            return InteractionResult.SUCCESS;
        });
        UseItemCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(first, (player, level, hand) -> eat(player, hand) ? InteractionResult.SUCCESS : InteractionResult.PASS);
        ServerTickEvents.END_SERVER_TICK.register(Resources::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerLevel level = server.getLevel(Galatea.LEVEL);
            if (level != null) {
                PICKED.forEach(picked -> regrow(level, picked));
            }
            PICKED.clear();
        });
    }

    /** Eats the Lushlilac in hand (both sides say yes; the server does it). */
    private static boolean eat(Player player, InteractionHand hand) {
        String id = SkyItems.skyblockId(player.getItemInHand(hand));
        Charm charm = id == null ? null : CHARMS.get(id);
        if (charm == null) {
            return false;
        }
        if (player instanceof ServerPlayer server) {
            player.getItemInHand(hand).shrink(1);
            server.setAttached(BUFF, new Buff(charm.amount(), System.currentTimeMillis() + charm.hours() * HOUR));
            Stats.refresh(server);
            server.sendSystemMessage(Component.literal("Yum! ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal("+" + Msg.number(charm.amount()) + " Charm Chance for " + charm.hours() + " hours.")
                            .withStyle(ChatFormatting.GRAY)));
            Msg.playSound(server, SoundEvents.GENERIC_EAT.value(), 0.8f, 1.2f);
        }
        return true;
    }

    private static void pick(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        String item;
        int count;
        if (state.is(Blocks.LILAC)) {
            BlockPos lower = state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            blocks.put(lower, level.getBlockState(lower));
            blocks.put(lower.above(), level.getBlockState(lower.above()));
            item = "LUSHLILAC";
            count = 1;
            Starlyn.score(player, 9, "lushlilac");
        } else {
            blocks.put(pos, state);
            item = "SEA_LUMIES";
            count = state.getValue(SeaPickleBlock.PICKLES);
        }
        if (player.getRandom().nextDouble() * 100 < Hotf.number(player, "collector", "stat")) {
            count *= 2;
        }
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, e.getValue()), e.getKey().getX() + 0.5, e.getKey().getY() + 0.5,
                    e.getKey().getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
            boolean wet = e.getValue().hasProperty(SeaPickleBlock.WATERLOGGED) && e.getValue().getValue(SeaPickleBlock.WATERLOGGED);
            level.setBlock(e.getKey(), wet ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), QUIET);
        }
        PICKED.add(new Picked(pos, blocks, level.getServer().getTickCount()));
        GalateaTrees.give(player, item, count, ItemGains.Source.FORAGING);
        SkillLevels.giveXp(player, SkillLevels.FORAGING, 15.0 * count);
        level.playSound(null, pos, state.is(Blocks.LILAC) ? SoundEvents.AZALEA_LEAVES_BREAK : SoundEvents.SLIME_SQUISH_SMALL,
                net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1.2f);
    }

    private static void regrow(Level level, Picked picked) {
        picked.blocks().forEach((pos, state) -> level.setBlock(pos, state, QUIET));
    }

    private static void tick(MinecraftServer server) {
        if (PICKED.isEmpty() || server.getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel level = server.getLevel(Galatea.LEVEL);
        if (level == null) {
            return;
        }
        long now = server.getTickCount();
        PICKED.removeIf(picked -> {
            if (now - picked.at() < REGROW_TICKS) {
                return false;
            }
            regrow(level, picked);
            return true;
        });
    }
}
