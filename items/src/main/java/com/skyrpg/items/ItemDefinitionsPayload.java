package com.skyrpg.items;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sends the server's item and set definitions to a client, as the original JSON, so tooltips can be built
 * from them by the same parsing code. Sent on join and after every /reload.
 * <p>
 * With every SkyBlock item that's megabytes of JSON, so it goes gzipped and split into parts under the 1 MB
 * packet limit; the client puts them back together when the last part arrives.
 */
public record ItemDefinitionsPayload(int part, int parts, byte[] data) implements CustomPacketPayload {
    public static final Type<ItemDefinitionsPayload> TYPE = new Type<>(SkyRpgItems.id("definitions"));
    private static final int PART_SIZE = 768 * 1024;

    public static final StreamCodec<ByteBuf, ItemDefinitionsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ItemDefinitionsPayload::part,
            ByteBufCodecs.VAR_INT, ItemDefinitionsPayload::parts,
            ByteBufCodecs.byteArray(PART_SIZE), ItemDefinitionsPayload::data,
            ItemDefinitionsPayload::new);

    /** Parts received so far (client side), in order. */
    private static final List<byte[]> received = new ArrayList<>();

    /** The current definitions as the parts to send, in order. */
    public static List<ItemDefinitionsPayload> current() {
        JsonObject items = new JsonObject();
        ItemRegistry.items().forEach(def -> items.add(def.id().toString(), def.json()));
        JsonObject sets = new JsonObject();
        ItemRegistry.sets().forEach(set -> sets.add(set.id().toString(), set.json()));
        JsonObject all = new JsonObject();
        all.add("items", items);
        all.add("sets", sets);
        byte[] zipped = gzip(all.toString().getBytes(StandardCharsets.UTF_8));
        int parts = Math.max(1, (zipped.length + PART_SIZE - 1) / PART_SIZE);
        List<ItemDefinitionsPayload> out = new ArrayList<>(parts);
        for (int part = 0; part < parts; part++) {
            out.add(new ItemDefinitionsPayload(part, parts,
                    Arrays.copyOfRange(zipped, part * PART_SIZE, Math.min(zipped.length, (part + 1) * PART_SIZE))));
        }
        return out;
    }

    /** Collects this part (client side); with the last one, loads the definitions into the client's {@link ItemRegistry}. */
    public void apply() {
        if (part == 0) {
            received.clear();
        }
        if (part != received.size()) {
            SkyRpgItems.LOGGER.error("Item definitions part {} of {} arrived out of order; dropping them", part + 1, parts);
            received.clear();
            return;
        }
        received.add(data);
        if (received.size() < parts) {
            return;
        }
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        received.forEach(joined::writeBytes);
        received.clear();
        JsonObject all = JsonParser.parseString(new String(gunzip(joined.toByteArray()), StandardCharsets.UTF_8)).getAsJsonObject();
        ItemRegistry.replace(parse(all.getAsJsonObject("items")), parse(all.getAsJsonObject("sets")));
    }

    private static Map<Identifier, JsonElement> parse(JsonObject files) {
        Map<Identifier, JsonElement> parsed = new LinkedHashMap<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> parsed.put(Identifier.parse(entry.getKey()), entry.getValue()));
        return parsed;
    }

    private static byte[] gzip(byte[] raw) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 4);
        try (GZIPOutputStream zip = new GZIPOutputStream(out)) {
            zip.write(raw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] zipped) {
        try (GZIPInputStream zip = new GZIPInputStream(new ByteArrayInputStream(zipped))) {
            return zip.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
