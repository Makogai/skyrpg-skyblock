package com.skyrpg.skyblock.pet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.pets.Pets;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's pet items ({@code skyrpg/skyblock_pet_items}, from {@code tools/skyblock/import_pet_items.py}): right-click
 * one while a pet is out to give it to that pet (a pet holds one; the item it held comes back to you). It's the pet's
 * {@code heldItem} in its petInfo. Effects: flat stats (Big Teeth +5 Crit Chance), a percent more of the pet's own stats
 * (Textbook), a percent more of all of them (Minos Relic), more pet XP for a skill or all of them, Tier Boost (the pet
 * counts as one rarity higher), stats for every few pet levels (Quick Claw), knockback immunity (Dwarf Turtle Shelmet),
 * Exp Share (a pet in your menu holding it earns a share of the pet out's XP), and items only one kind of pet can hold
 * (Flying Pig).
 */
public final class PetItems {
    /** {@code perLevels}: every {@code every} pet levels, {@code perLevelStats} (Quick Claw); {@code only}: the pet type it's for, or "". */
    public record Effect(String text, Map<StatType, Double> stats, Map<StatType, Double> multiply, Map<String, Double> exp, boolean tierBoost,
                         double multiplyAll, boolean knockbackImmune, String only, double expShare, int every, Map<StatType, Double> perLevelStats) {
    }

    private static Map<String, Effect> effects = Map.of();

    private PetItems() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("skyblock_pet_items"), "skyrpg/skyblock_pet_items", PetItems::load);
        Identifier early = SkyRpgSkyblock.id("pet_items_early");
        UseItemCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(early, (player, level, hand) -> use(player, hand));
        UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(early, (player, level, hand, hit) -> use(player, hand));
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, Effect> loaded = new HashMap<>();
        for (JsonElement file : files.values()) {
            file.getAsJsonObject().entrySet().forEach(entry -> {
                JsonObject e = entry.getValue().getAsJsonObject();
                Map<String, Double> exp = new HashMap<>();
                if (e.has("exp")) {
                    e.getAsJsonObject("exp").entrySet().forEach(x -> exp.put(x.getKey(), x.getValue().getAsDouble()));
                }
                JsonObject perLevels = e.has("per_levels") ? e.getAsJsonObject("per_levels") : null;
                loaded.put(entry.getKey(), new Effect(e.get("text").getAsString(), stats(e.getAsJsonObject("stats")),
                        stats(e.getAsJsonObject("multiply")), exp, e.has("tier_boost") && e.get("tier_boost").getAsBoolean(),
                        e.has("multiply_all") ? e.get("multiply_all").getAsDouble() : 0,
                        e.has("knockback_immune") && e.get("knockback_immune").getAsBoolean(), e.has("only") ? e.get("only").getAsString() : "",
                        e.has("exp_share") ? e.get("exp_share").getAsDouble() : 0, perLevels == null ? 0 : perLevels.get("every").getAsInt(),
                        perLevels == null ? Map.of() : stats(perLevels.getAsJsonObject("stats"))));
            });
        }
        effects = loaded;
        SkyRpgSkyblock.LOGGER.info("Loaded {} pet items", loaded.size());
    }

    private static Map<StatType, Double> stats(JsonObject json) {
        Map<StatType, Double> out = new LinkedHashMap<>();
        if (json != null) {
            json.entrySet().forEach(e -> {
                StatType stat = StatTypes.get(e.getKey());
                if (stat != null) {
                    out.put(stat, e.getValue().getAsDouble());
                }
            });
        }
        return out;
    }

    public static Effect effect(String item) {
        return item == null ? null : effects.get(item);
    }

    private static InteractionResult use(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        String id = SkyItems.skyblockId(held);
        if (id == null || !effects.containsKey(id)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            give(server, held, id);
        }
        return InteractionResult.SUCCESS;
    }

    private static void give(ServerPlayer player, ItemStack held, String id) {
        ItemStack pet = Pets.active(player);
        if (!SkyBlockPets.isPet(pet)) {
            Notice.send(player, Notice.Kind.ERROR, Component.literal("Summon a pet first to give it this item!"));
            return;
        }
        String only = effects.get(id).only();
        if (!only.isEmpty() && !only.equals(SkyBlockPets.type(pet))) {
            Notice.send(player, Notice.Kind.ERROR, Component.literal("This pet item can only be used on a "
                    + only.charAt(0) + only.substring(1).toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + " pet!"));
            return;
        }
        ItemStack updated = pet.copy();
        String old = SkyBlockPets.heldItem(updated);
        SkyBlockPets.setHeldItem(updated, id);
        SkyBlockPets.refreshLook(updated);
        player.setAttached(Pets.ACTIVE, updated);
        held.shrink(1);
        if (old != null) {
            ItemDefinition back = ItemRegistry.skyblock(old);
            if (back != null) {
                ItemStack returned = SkyItems.create(back, 1);
                if (!player.getInventory().add(returned)) {
                    player.drop(returned, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            }
        }
        Stats.refresh(player);
        ItemDefinition def = ItemRegistry.skyblock(id);
        Notice.of(Notice.Kind.PET).tag("PET ITEM").send(player, Component.literal("Your ").withStyle(ChatFormatting.GRAY)
                .append(SkyBlockPets.name(updated)).append(Component.literal(" is now holding ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(def == null ? id : def.name()).withColor(def == null ? 0xFFFFFF : def.rarity().color()))
                .append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.ALLAY_ITEM_GIVEN, 0.8f, 1.2f);
    }
}
