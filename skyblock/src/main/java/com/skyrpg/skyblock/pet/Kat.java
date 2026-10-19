package com.skyrpg.skyblock.pet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.pets.Pets;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Kat, the pet sitter: she takes one pet at a time up one rarity, for coins and sometimes items, and it's
 * ready after the upgrade's time (real time, even offline). Kat's 216 upgrades come from NEU
 * ({@code skyrpg/kat}, {@code tools/skyblock/import_kat.py}). Per profile. A Kat Flower skips a day of the wait and a Kat
 * Bouquet five: right-click Kat with one while she has your pet.
 */
public final class Kat {
    public record Upgrade(String pet, int from, int to, long coins, long seconds, List<String> items) {
    }

    /** The pet with Kat, the rarity it's becoming and when it's ready (epoch millis). */
    public record Job(ItemStack pet, String tier, long ready) {
        static final Codec<Job> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("pet").forGetter(Job::pet),
                Codec.STRING.fieldOf("tier").forGetter(Job::tier),
                Codec.LONG.fieldOf("ready").forGetter(Job::ready)).apply(i, Job::new));
    }

    public static final AttachmentType<Job> JOB = AttachmentRegistry.create(SkyRpgSkyblock.id("kat"),
            builder -> builder.persistent(Job.CODEC).copyOnDeath());

    /** Pet -> its upgrades by the rarity they start from. */
    private static Map<String, Map<Integer, Upgrade>> upgrades = Map.of();
    private static final long DAY_MILLIS = 86_400_000L;

    private Kat() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("kat"), "skyrpg/kat", Kat::load);
        Actions.register("kat", (player, params) -> KatMenu.open(player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(Commands.literal("kat").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                    KatMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }).then(Commands.literal("finish").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                        .executes(ctx -> {
                            // Testing: the pet with Kat is ready now.
                            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                            Job job = target.getAttached(JOB);
                            if (job == null) {
                                return 0;
                            }
                            target.setAttached(JOB, new Job(job.pet(), job.tier(), System.currentTimeMillis()));
                            return 1;
                        })))));
        // Before Kat's own menu opens: a flower or a bouquet in hand pays her for time instead.
        Identifier first = SkyRpgSkyblock.id("kat_gifts_first");
        UseEntityCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(first, (player, level, hand, entity, hit) -> {
            String id = SkyItems.skyblockId(player.getItemInHand(hand));
            boolean kat = entity.entityTags().stream().anyMatch(tag -> tag.startsWith("skyrpg_npc:") && tag.endsWith(":kat"));
            if (!kat || !"KAT_FLOWER".equals(id) && !"KAT_BOUQUET".equals(id)) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer server) {
                skipTime(server, player.getItemInHand(hand), "KAT_BOUQUET".equals(id) ? 5 : 1);
            }
            return InteractionResult.SUCCESS;
        });
        // "Your pet is ready" once, when it is.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 200 != 0) {
                return;
            }
            long now = System.currentTimeMillis();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Job job = player.getAttached(JOB);
                if (job != null && job.ready() <= now && job.ready() > now - 10_000) {
                    Notice.of(Notice.Kind.PET).tag("KAT").send(player, Component.literal("Your ").withStyle(ChatFormatting.GRAY)
                            .append(SkyBlockPets.name(job.pet())).append(Component.literal(" is ready! Visit Kat to get it back.")
                                    .withStyle(ChatFormatting.GRAY)));
                }
            }
        });
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, Map<Integer, Upgrade>> loaded = new HashMap<>();
        for (JsonElement file : files.values()) {
            for (JsonElement element : file.getAsJsonObject().getAsJsonArray("upgrades")) {
                JsonObject u = element.getAsJsonObject();
                List<String> items = new ArrayList<>();
                u.getAsJsonArray("items").forEach(e -> items.add(e.getAsString()));
                Upgrade upgrade = new Upgrade(u.get("pet").getAsString(), u.get("from").getAsInt(), u.get("to").getAsInt(),
                        u.get("coins").getAsLong(), u.get("seconds").getAsLong(), List.copyOf(items));
                loaded.computeIfAbsent(upgrade.pet(), k -> new HashMap<>()).put(upgrade.from(), upgrade);
            }
        }
        upgrades = loaded;
        SkyRpgSkyblock.LOGGER.info("Loaded Kat's upgrades for {} pets", loaded.size());
    }

    /** The upgrade this pet can have now, if any. */
    public static Optional<Upgrade> upgrade(ItemStack pet) {
        int tier = List.of(SkyBlockPets.TIERS).indexOf(SkyBlockPets.tier(pet));
        return Optional.ofNullable(upgrades.getOrDefault(SkyBlockPets.type(pet), Map.of()).get(tier));
    }

    public static Optional<Job> job(ServerPlayer player) {
        return Optional.ofNullable(player.getAttached(JOB));
    }

    /** Gives Kat this pet (from the pet menu or the one out): "" when she took it, else why not. */
    static String start(ServerPlayer player, ItemStack pet, boolean active) {
        if (job(player).isPresent()) {
            return "Kat is already looking after a pet!";
        }
        Optional<Upgrade> upgrade = upgrade(pet);
        if (upgrade.isEmpty()) {
            return "Kat can't upgrade this pet any further.";
        }
        Upgrade u = upgrade.get();
        for (String cost : u.items()) {
            String[] parts = cost.split(":");
            if (SkyStacks.count(player.getInventory(), parts[0]) < Integer.parseInt(parts[1])) {
                return "You don't have the items for this upgrade!";
            }
        }
        if (u.coins() > 0 && !Wallet.take(player, u.coins())) {
            return "You need " + Msg.number(u.coins()) + " coins for this upgrade!";
        }
        for (String cost : u.items()) {
            String[] parts = cost.split(":");
            SkyStacks.take(player.getInventory(), parts[0], Integer.parseInt(parts[1]));
        }
        if (active) {
            player.removeAttached(Pets.ACTIVE);
        } else {
            List<ItemStack> stored = SkyBlockPets.stored(player);
            stored.removeIf(stack -> ItemStack.isSameItemSameComponents(stack, pet));
            SkyBlockPets.setStored(player, stored);
        }
        player.setAttached(JOB, new Job(pet.copy(), SkyBlockPets.TIERS[u.to()], System.currentTimeMillis() + u.seconds() * 1000));
        com.skyrpg.core.stats.Stats.refresh(player);
        Notice.of(Notice.Kind.PET).tag("KAT").send(player, Component.literal("Kat is taking care of your ").withStyle(ChatFormatting.GRAY)
                .append(SkyBlockPets.name(pet)).append(Component.literal("! Come back in " + duration(u.seconds() * 1000) + ".")
                        .withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.ALLAY_ITEM_GIVEN, 1.0f, 0.9f);
        return "";
    }

    /** A Kat Flower (1 day) or Bouquet (5 days) pays for time of the upgrade: Kat has your pet that much sooner. */
    private static void skipTime(ServerPlayer player, ItemStack gift, int days) {
        Job job = player.getAttached(JOB);
        long now = System.currentTimeMillis();
        if (job == null || job.ready() <= now) {
            Notice.send(player, Notice.Kind.ERROR, Component.literal(job == null ? "Kat isn't looking after a pet of yours!"
                    : "Your pet is already ready, no need to wait!"));
            return;
        }
        player.setAttached(JOB, new Job(job.pet(), job.tier(), Math.max(now, job.ready() - days * DAY_MILLIS)));
        gift.shrink(1);
        Notice.of(Notice.Kind.PET).tag("KAT").send(player, Component.literal("Kat skipped " + days + (days == 1 ? " day" : " days")
                + " of the wait for your ").withStyle(ChatFormatting.GRAY).append(SkyBlockPets.name(job.pet()))
                .append(Component.literal("! " + (job.ready() - days * DAY_MILLIS <= now ? "It's ready now."
                        : duration(job.ready() - days * DAY_MILLIS - now) + " to go.")).withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.ALLAY_ITEM_GIVEN, 1.0f, 1.4f);
    }

    /** Takes the upgraded pet back. */
    static String claim(ServerPlayer player) {
        Job job = player.getAttached(JOB);
        if (job == null) {
            return "";
        }
        if (job.ready() > System.currentTimeMillis()) {
            return "Your pet isn't ready yet!";
        }
        ItemStack pet = job.pet().copy();
        SkyBlockPets.setTier(pet, job.tier());
        player.removeAttached(JOB);
        SkyBlockPets.add(player, pet);
        Notice.of(Notice.Kind.PET).tag("KAT").send(player, Component.literal("Your ").withStyle(ChatFormatting.GRAY)
                .append(SkyBlockPets.name(pet)).append(Component.literal(" is now " + job.tier().charAt(0)
                        + job.tier().substring(1).toLowerCase(java.util.Locale.ROOT) + "!").withStyle(ChatFormatting.GREEN)));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.2f);
        return "";
    }

    static String duration(long millis) {
        long minutes = Math.max(0, millis / 60000);
        long days = minutes / 1440;
        long hours = minutes / 60 % 24;
        if (days > 0) {
            return days + "d" + (hours > 0 ? " " + hours + "h" : "");
        }
        return hours > 0 ? hours + "h " + minutes % 60 + "m" : Math.max(1, minutes) + "m";
    }
}
