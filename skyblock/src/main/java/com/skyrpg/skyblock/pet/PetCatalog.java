package com.skyrpg.skyblock.pet;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.Rarity;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Pets as SkyBlock ids, the way Hypixel's recipes name them ("BEE;4" is a Legendary Bee): the item registry answers
 * those ids with a definition (so recipes, the Recipe Book and the Auction House see pets as items) and making one
 * makes a new level 1 pet. The server knows every pet; clients get the list (name, skin, rarities) when they join.
 */
public final class PetCatalog {
    public record Entry(String type, String name, String skin, List<String> tiers) {
        static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::type,
                ByteBufCodecs.STRING_UTF8, Entry::name,
                ByteBufCodecs.STRING_UTF8, Entry::skin,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Entry::tiers,
                Entry::new);
    }

    /** Server to client: every pet. */
    public record CatalogPayload(List<Entry> pets) implements CustomPacketPayload {
        public static final Type<CatalogPayload> TYPE = new Type<>(SkyRpgSkyblock.id("pet_catalog"));
        public static final StreamCodec<ByteBuf, CatalogPayload> CODEC = StreamCodec.composite(
                Entry.CODEC.apply(ByteBufCodecs.list()), CatalogPayload::pets, CatalogPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        /** The client's copy. */
        public void apply() {
            replace(pets);
        }
    }

    private static Map<String, Entry> entries = Map.of();
    private static final Map<String, ItemDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private PetCatalog() {
    }

    static void init() {
        PayloadTypeRegistry.clientboundPlay().register(CatalogPayload.TYPE, CatalogPayload.CODEC);
        ItemRegistry.registerVirtual(PetCatalog::definition);
        SkyItems.registerFactory(PetCatalog::create);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> server.getPlayerList().getPlayers().forEach(PetCatalog::send));
    }

    static void replace(List<Entry> list) {
        Map<String, Entry> map = new LinkedHashMap<>();
        list.forEach(entry -> map.put(entry.type(), entry));
        entries = map;
        DEFINITIONS.clear();
    }

    private static void send(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, CatalogPayload.TYPE)) {
            ServerPlayNetworking.send(player, new CatalogPayload(List.copyOf(entries.values())));
        }
    }

    /** "BEE;4" -> a Legendary Bee's definition, or null when it names no pet we have at that rarity. */
    static ItemDefinition definition(String id) {
        int cut = id.indexOf(';');
        if (cut <= 0) {
            return null;
        }
        return DEFINITIONS.computeIfAbsent(id, key -> {
            Entry entry = entries.get(key.substring(0, cut));
            int index;
            try {
                index = Integer.parseInt(key.substring(cut + 1));
            } catch (NumberFormatException e) {
                return null;
            }
            if (entry == null || index < 0 || index >= SkyBlockPets.TIERS.length || !entry.tiers().contains(SkyBlockPets.TIERS[index])) {
                return null;
            }
            JsonObject json = new JsonObject();
            json.addProperty("skyblock_id", key);
            json.addProperty("name", entry.name());
            json.addProperty("category", "PET");
            json.addProperty("unstackable", true);
            JsonObject pet = new JsonObject();
            pet.addProperty("type", entry.type());
            pet.addProperty("tier", SkyBlockPets.TIERS[index]);
            json.add("pet", pet);
            return new ItemDefinition(SkyRpgSkyblock.id("pet/" + key.toLowerCase(java.util.Locale.ROOT).replace(';', '_')), key, Items.PLAYER_HEAD,
                    entry.name(), Rarity.values()[index], Map.of(), null, "", false, null, Map.of(), List.of(), json);
        });
    }

    /** A new level 1 pet for a pet definition (on a client without the pet data: its head and name). */
    static ItemStack create(ItemDefinition def, int count) {
        if (!def.json().has("pet")) {
            return null;
        }
        JsonObject pet = def.json().getAsJsonObject("pet");
        String type = pet.get("type").getAsString();
        String tier = pet.get("tier").getAsString();
        ItemStack made = SkyBlockPets.create(type, tier, 0);
        if (!made.isEmpty()) {
            return made;
        }
        Entry entry = entries.get(type);
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        if (entry != null && !entry.skin().isEmpty()) {
            UUID skinId = UUID.nameUUIDFromBytes(("skyrpg_pet:" + type).getBytes(StandardCharsets.UTF_8));
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(skinId, "",
                    new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", entry.skin()))))));
        }
        head.set(DataComponents.CUSTOM_NAME, Component.literal("[Lvl 1] ").withStyle(s -> s.withItalic(false).withColor(net.minecraft.ChatFormatting.GRAY))
                .append(Component.literal(def.name()).withStyle(s -> s.withItalic(false).withColor(SkyBlockPets.color(tier)))));
        return head;
    }
}
