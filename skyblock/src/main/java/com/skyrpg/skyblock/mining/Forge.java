package com.skyrpg.skyblock.mining;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Forge (in the Dwarven Mines, from HotM 2): pick a recipe for a free slot, the Forger takes its materials (and
 * coins, for some), and the item is ready after the recipe's time, counted in real time even while you're away. Slots:
 * 2 at HotM 2, one more each tier to 6, one from Core of the Mountain 2. Quick Forge takes its percentage off the time.
 * Recipes are NEU's 123 ({@code skyrpg/forge}, from {@code tools/skyblock/import_forge.py}).
 */
public final class Forge {
    public record Input(String item, int count) {
    }

    public record Recipe(String output, int count, List<Input> inputs, long coins, long seconds, int hotm) {
    }

    /** One forge slot's work: what, how many, started and done (epoch millis), and whether we've said it's ready. */
    public record Job(String output, int count, long start, long end, boolean told) {
        static final Codec<Job> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("output").forGetter(Job::output),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Job::count),
                Codec.LONG.fieldOf("start").forGetter(Job::start),
                Codec.LONG.fieldOf("end").forGetter(Job::end),
                Codec.BOOL.optionalFieldOf("told", false).forGetter(Job::told)).apply(i, Job::new));
    }

    /** Slot number -> job, as strings so the codec stays a map. */
    public static final AttachmentType<Map<String, Job>> JOBS = AttachmentRegistry.create(SkyRpgSkyblock.id("forge"),
            builder -> builder.initializer(Map::of).persistent(Codec.unboundedMap(Codec.STRING, Job.CODEC)).copyOnDeath());

    private static List<Recipe> recipes = List.of();

    private Forge() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("forge"), "skyrpg/forge", Forge::load);
        Actions.register("forge", (player, params) -> ForgeMenu.open(player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(Commands.literal("sbforge").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                    ForgeMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }).then(Commands.literal("finish").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                        .executes(ctx -> {
                            // Testing: everything forging is done now.
                            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                            Map<String, Job> done = new java.util.HashMap<>();
                            target.getAttachedOrElse(JOBS, Map.<String, Job>of()).forEach((slot, job) ->
                                    done.put(slot, new Job(job.output(), job.count(), job.start(), System.currentTimeMillis(), job.told())));
                            target.setAttached(JOBS, Map.copyOf(done));
                            return done.size();
                        })))));
        // Tell players when something's done (every few seconds).
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 != 0) {
                return;
            }
            long now = System.currentTimeMillis();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Map<String, Job> jobs = player.getAttachedOrElse(JOBS, Map.of());
                if (jobs.values().stream().noneMatch(job -> !job.told() && job.end() <= now)) {
                    continue;
                }
                Map<String, Job> told = new java.util.HashMap<>(jobs);
                jobs.forEach((slot, job) -> {
                    if (!job.told() && job.end() <= now) {
                        told.put(slot, new Job(job.output(), job.count(), job.start(), job.end(), true));
                        Notice.of(Notice.Kind.MINING).tag("FORGE").send(player, name(job.output(), job.count())
                                .append(Component.literal(" is ready at the Forge!").withStyle(ChatFormatting.GRAY)));
                    }
                });
                player.setAttached(JOBS, Map.copyOf(told));
            }
        });
    }

    private static void load(Map<Identifier, JsonElement> files) {
        List<Recipe> loaded = new ArrayList<>();
        for (JsonElement file : files.values()) {
            for (JsonElement element : file.getAsJsonObject().getAsJsonArray("recipes")) {
                JsonObject r = element.getAsJsonObject();
                List<Input> inputs = new ArrayList<>();
                r.getAsJsonArray("inputs").forEach(in -> inputs.add(new Input(in.getAsJsonObject().get("item").getAsString(),
                        in.getAsJsonObject().get("count").getAsInt())));
                loaded.add(new Recipe(r.get("output").getAsString(), r.get("count").getAsInt(), List.copyOf(inputs),
                        r.get("coins").getAsLong(), r.get("seconds").getAsLong(), r.get("hotm").getAsInt()));
            }
        }
        recipes = List.copyOf(loaded);
        SkyRpgSkyblock.LOGGER.info("Loaded {} Forge recipes", loaded.size());
    }

    public static List<Recipe> recipes() {
        return recipes;
    }

    /** How many slots this profile's Forge has. */
    public static int slots(ServerPlayer player) {
        Hotm.State state = Hotm.state(player);
        int level = Hotm.level(state.xp());
        if (level < 2) {
            return 0;
        }
        return Math.min(level, 6) + (state.perk("core_of_the_mountain") >= 2 ? 1 : 0);
    }

    /** A recipe's time for this player, after Quick Forge. */
    public static long seconds(ServerPlayer player, Recipe recipe) {
        Hotm.State state = Hotm.state(player);
        HotmTree.Perk quick = HotmTree.get("quick_forge");
        int level = state.active("quick_forge");
        double cut = quick == null || level <= 0 ? 0 : quick.number("stat", level, false) / 100;
        return Math.max(1, Math.round(recipe.seconds() * (1 - cut)));
    }

    public static ItemStack stack(String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? ItemStack.EMPTY : SkyItems.create(def, Math.max(1, count));
    }

    /** "2x Refined Mithril" in its rarity's colour. */
    static MutableComponent name(String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        String label = (count > 1 ? count + "x " : "") + (def == null ? id : def.name());
        return Component.literal(label).withColor(def == null ? 0xFFFFFF : def.rarity().color());
    }

    /** Starts a recipe in a free slot: "" when it started, else why not. */
    static String start(ServerPlayer player, Recipe recipe) {
        int level = Hotm.level(player);
        if (level < recipe.hotm()) {
            return "You need Heart of the Mountain " + recipe.hotm() + " to forge this!";
        }
        Map<String, Job> jobs = player.getAttachedOrElse(JOBS, Map.of());
        int free = -1;
        for (int i = 0; i < slots(player); i++) {
            if (!jobs.containsKey(String.valueOf(i))) {
                free = i;
                break;
            }
        }
        if (free < 0) {
            return "All your Forge slots are busy!";
        }
        for (Input input : recipe.inputs()) {
            if (SkyStacks.count(player.getInventory(), input.item()) < input.count()) {
                return "You don't have the materials for this!";
            }
        }
        if (recipe.coins() > 0 && Wallet.balance(player) < recipe.coins()) {
            return "You don't have enough coins!";
        }
        for (Input input : recipe.inputs()) {
            SkyStacks.take(player.getInventory(), input.item(), input.count());
        }
        if (recipe.coins() > 0) {
            Wallet.take(player, recipe.coins());
        }
        long now = System.currentTimeMillis();
        Map<String, Job> more = new java.util.HashMap<>(jobs);
        more.put(String.valueOf(free), new Job(recipe.output(), recipe.count(), now, now + seconds(player, recipe) * 1000, false));
        player.setAttached(JOBS, Map.copyOf(more));
        Notice.of(Notice.Kind.MINING).tag("FORGE").send(player, Component.literal("Started forging ").withStyle(ChatFormatting.GRAY)
                .append(name(recipe.output(), recipe.count())).append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.0f);
        return "";
    }

    /** Takes a finished item out of a slot. */
    static String claim(ServerPlayer player, int slot) {
        Map<String, Job> jobs = player.getAttachedOrElse(JOBS, Map.of());
        Job job = jobs.get(String.valueOf(slot));
        if (job == null) {
            return "";
        }
        if (job.end() > System.currentTimeMillis()) {
            return "That isn't done yet!";
        }
        ItemStack made = stack(job.output(), job.count());
        if (made.isEmpty()) {
            return "That item doesn't exist any more.";
        }
        ItemGains.give(player, made, ItemGains.Source.OTHER);
        if (!made.isEmpty()) {
            player.drop(made, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        Map<String, Job> rest = new java.util.HashMap<>(jobs);
        rest.remove(String.valueOf(slot));
        player.setAttached(JOBS, Map.copyOf(rest));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.4f);
        return "";
    }
}
