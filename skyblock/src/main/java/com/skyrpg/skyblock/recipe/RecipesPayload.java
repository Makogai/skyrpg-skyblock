package com.skyrpg.skyblock.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import io.netty.buffer.ByteBuf;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server to client: every recipe, as its original JSON, gzipped (a few hundred KB of JSON packs down to well under a
 * packet). Sent on join and after a /reload changed them, so the Recipe Book shows what the server crafts.
 */
public record RecipesPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<RecipesPayload> TYPE = new Type<>(SkyRpgSkyblock.id("recipes"));
    public static final StreamCodec<ByteBuf, RecipesPayload> CODEC = ByteBufCodecs.byteArray(1_000_000).map(RecipesPayload::new, RecipesPayload::data);

    private static volatile boolean changed;

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player, current()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (changed) {
                changed = false;
                RecipesPayload payload = current();
                server.getPlayerList().getPlayers().forEach(player -> send(player, payload));
            }
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> changed = false);
    }

    /** The recipes were (re)loaded: send them out on the next tick. */
    static void changed() {
        changed = true;
    }

    private static void send(ServerPlayer player, RecipesPayload payload) {
        if (ServerPlayNetworking.canSend(player, TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public static RecipesPayload current() {
        JsonObject all = new JsonObject();
        Recipes.all().forEach(recipe -> all.add(recipe.id().toString(), recipe.json()));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(all.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new RecipesPayload(out.toByteArray());
    }

    /** Client side: loads the recipes into the client's {@link Recipes}. */
    public void apply() {
        String json;
        try (GZIPInputStream zip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            json = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            SkyRpgSkyblock.LOGGER.error("Couldn't read the recipes from the server", e);
            return;
        }
        Map<Identifier, JsonElement> files = new LinkedHashMap<>();
        JsonParser.parseString(json).getAsJsonObject().entrySet().forEach(entry -> files.put(Identifier.parse(entry.getKey()), entry.getValue()));
        Recipes.replace(files);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
