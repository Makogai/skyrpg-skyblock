package com.skyrpg.skyblock.storage;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * What the Storage page needs: a summary of the player's Ender Chest pages and Wardrobe (sent on join and whenever
 * one closes), and the request to open one. Plus {@code /storage}, {@code /ec <page>}, {@code /wardrobe}, and
 * ender chests in the world opening Storage.
 */
public final class StoragePayloads {
    /** One Ender Chest page on the Storage page: how many slots are used and the first few items. */
    public record PagePreview(int used, List<ItemStack> items) {
        static final StreamCodec<RegistryFriendlyByteBuf, PagePreview> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PagePreview::used,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), PagePreview::items,
                PagePreview::new);
    }

    /** Server to client: the player's storage, for the Storage page. */
    public record Summary(int unlocked, List<PagePreview> pages, List<ItemStack> wardrobe, int equipped) implements CustomPacketPayload {
        public static final Type<Summary> TYPE = new Type<>(SkyRpgSkyblock.id("storage_summary"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Summary> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Summary::unlocked,
                PagePreview.CODEC.apply(ByteBufCodecs.list()), Summary::pages,
                ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), Summary::wardrobe,
                ByteBufCodecs.VAR_INT, Summary::equipped,
                Summary::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: show the Storage page (someone used an ender chest). */
    public record Show() implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(SkyRpgSkyblock.id("storage_show"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = StreamCodec.unit(new Show());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: open Ender Chest page {@code page} (0-based), or the Wardrobe for -1. */
    public record Open(int page) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(SkyRpgSkyblock.id("storage_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Open::page, Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final int PREVIEW = 9;

    private StoragePayloads() {
    }

    public static void init() {
        Storage.init();
        Wardrobe.init();
        PayloadTypeRegistry.clientboundPlay().register(Summary.TYPE, Summary.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Show.TYPE, Show.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Open.TYPE, Open.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Open.TYPE, (payload, context) -> open(context.player(), payload.page()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendSummary(handler.player));
        // Ender chests open Storage instead of the vanilla 27 slots.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.getBlockState(hit.getBlockPos()).is(Blocks.ENDER_CHEST) || player.isShiftKeyDown()) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer && ServerPlayNetworking.canSend(serverPlayer, Show.TYPE)) {
                sendSummary(serverPlayer);
                ServerPlayNetworking.send(serverPlayer, new Show());
            }
            return InteractionResult.SUCCESS;
        });
    }

    static void open(ServerPlayer player, int page) {
        if (page < 0) {
            Wardrobe.open(player);
        } else {
            Storage.open(player, page);
        }
    }

    public static void sendSummary(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, Summary.TYPE)) {
            return;
        }
        int unlocked = Storage.unlocked(player);
        List<PagePreview> pages = new ArrayList<>();
        for (int page = 0; page < unlocked; page++) {
            List<ItemStack> items = Storage.page(player, page);
            List<ItemStack> preview = new ArrayList<>();
            int used = 0;
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    used++;
                    if (preview.size() < PREVIEW) {
                        preview.add(stack.copy());
                    }
                }
            }
            pages.add(new PagePreview(used, preview));
        }
        ServerPlayNetworking.send(player, new Summary(unlocked, pages, Wardrobe.heads(player), Wardrobe.equipped(player)));
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wardrobe").executes(ctx -> {
            Wardrobe.open(ctx.getSource().getPlayerOrException());
            return 1;
        }));
        dispatcher.register(Commands.literal("ec")
                .executes(ctx -> {
                    Storage.open(ctx.getSource().getPlayerOrException(), 0);
                    return 1;
                })
                .then(Commands.argument("page", IntegerArgumentType.integer(1, Storage.MAX_PAGES)).executes(ctx -> {
                    Storage.open(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "page") - 1);
                    return 1;
                })));
        dispatcher.register(Commands.literal("storage")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    sendSummary(player);
                    if (ServerPlayNetworking.canSend(player, Show.TYPE)) {
                        ServerPlayNetworking.send(player, new Show());
                    }
                    return 1;
                })
                .then(Commands.literal("unlock").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("pages", IntegerArgumentType.integer(1, Storage.MAX_PAGES)).executes(ctx -> {
                                    int pages = IntegerArgumentType.getInteger(ctx, "pages");
                                    for (ServerPlayer player : EntityArgument.getPlayers(ctx, "players")) {
                                        Storage.setUnlocked(player, pages);
                                        sendSummary(player);
                                    }
                                    ctx.getSource().sendSuccess(() -> Component.literal("Ender Chest pages unlocked: " + pages), true);
                                    return 1;
                                })))));
    }
}
