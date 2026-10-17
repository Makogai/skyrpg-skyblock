package com.skyrpg.skyblock.experiment;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.enchant.EnchantMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

/**
 * Hypixel's Experimentation Table ({@code skyrpg/experiments}): placed on your island (it's furniture), right-click it for
 * Superpairs and its two add-ons, Chronomatron and Ultrasequencer. Each stake needs an Enchanting level and costs XP levels.
 * Superpairs uses one of 3 charges (each comes back 24 hours after it was used; one more can be bought with levels, three
 * a day). The add-ons' best results this charge add bonus clicks to the next Superpairs. Per profile.
 */
public final class Experiments {
    public static final Identifier ENCHANTING = EnchantMenu.ENCHANTING;
    public static final String TABLE_ID = "EXPERIMENTATION_TABLE";
    public static final String SERUM_ID = "METAPHYSICAL_SERUM";
    public static final int MAX_SERUMS = 3;

    /** A Superpairs reward: {@code kind} xp, item, book ("table" or "ultra" in {@code item}) or pet (with {@code tier}). */
    public record Reward(String kind, String item, int min, int max, String tier, double weight) {
    }

    public record Superpairs(String name, int enchanting, int cost, int width, int height, int clicks, int addonMultiplier,
                             List<Reward> rewards) {
    }

    /** Chronomatron ({@code colors}) or Ultrasequencer ({@code width} x {@code height}): Enchanting XP per step, bonus clicks by milestone. */
    public record Addon(String name, int enchanting, int cost, int colors, int width, int height, int xp, TreeMap<Integer, Integer> bonus) {
        /** Bonus clicks for reaching {@code reached}; each serum dose makes every milestone one shorter. */
        public int bonusFor(int reached, int serums) {
            int clicks = 0;
            for (Map.Entry<Integer, Integer> milestone : bonus.entrySet()) {
                if (reached >= milestone.getKey() - serums) {
                    clicks = milestone.getValue();
                }
            }
            return clicks;
        }
    }

    /** {@code used}: when each spent charge was used; the add-ons' banked bonus clicks; serum doses; charges bought on {@code boughtDay}. */
    public record State(List<Long> used, int chronoBonus, int ultraBonus, int serums, int bought, long boughtDay) {
        static final State EMPTY = new State(List.of(), 0, 0, 0, 0, 0);
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.listOf().fieldOf("used").forGetter(State::used),
                Codec.INT.fieldOf("chrono").forGetter(State::chronoBonus),
                Codec.INT.fieldOf("ultra").forGetter(State::ultraBonus),
                Codec.INT.fieldOf("serums").forGetter(State::serums),
                Codec.INT.fieldOf("bought").forGetter(State::bought),
                Codec.LONG.fieldOf("bought_day").forGetter(State::boughtDay)).apply(i, State::new));
    }

    public static final AttachmentType<State> STATE = AttachmentRegistry.create(SkyRpgSkyblock.id("experiments"),
            builder -> builder.persistent(State.CODEC).copyOnDeath());

    static int charges = 3;
    static long rechargeMillis = 24L * 3600 * 1000;
    static int[] extraChargeLevels = {50, 100, 200};
    static List<Superpairs> superpairs = List.of();
    static List<Addon> chronomatron = List.of();
    static List<Addon> ultrasequencer = List.of();

    private Experiments() {
    }

    public static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("experiments"), "skyrpg/experiments", Experiments::load);
        // Right-click a placed Experimentation Table (its head keeps the item's data).
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() || player.isSpectator() || !(level.getBlockEntity(hit.getBlockPos()) instanceof SkullBlockEntity skull)
                    || !TABLE_ID.equals(id(skull.components().get(DataComponents.CUSTOM_DATA)))) {
                return InteractionResult.PASS;
            }
            if (player.isSecondaryUseActive() && !player.getItemInHand(hand).isEmpty()) {
                return InteractionResult.PASS;
            }
            ExperimentMenu.openTable((ServerPlayer) player);
            return InteractionResult.SUCCESS;
        });
        // Furniture (the table) broken on your island comes back as its SkyBlock item; vanilla's head drop would lose its data.
        // Registered after the island rules, so only someone who may build there gets this far.
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!(blockEntity instanceof SkullBlockEntity skull)) {
                return true;
            }
            String id = id(skull.components().get(DataComponents.CUSTOM_DATA));
            com.skyrpg.items.ItemDefinition def = id.isEmpty() ? null : com.skyrpg.items.ItemRegistry.skyblock(id);
            if (def == null) {
                return true;
            }
            level.removeBlock(pos, false);
            ItemStack item = SkyItems.create(def, 1);
            if (!player.getInventory().add(item)) {
                player.drop(item, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            return false;
        });
        // Drinking a Metaphysical Serum (early, before the islands' and Hub's rules, and before a head would be worn).
        Identifier early = SkyRpgSkyblock.id("serum_early");
        UseItemCallback.EVENT.addPhaseOrdering(early, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(early, (player, level, hand) -> serum(player, hand));
        UseBlockCallback.EVENT.addPhaseOrdering(early, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(early, (player, level, hand, hit) -> serum(player, hand));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("sbexperiments")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    ExperimentMenu.openTable(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("peek").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    // Testing: the answers of the experiment this player has open.
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    String answer = target.containerMenu instanceof ExperimentMenu menu ? menu.peek() : "No experiment open.";
                    ctx.getSource().sendSuccess(() -> Component.literal(answer), false);
                    return 1;
                })))
                .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    // Testing: every charge back and the banked bonus clicks gone.
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    State state = state(target);
                    target.setAttached(STATE, new State(List.of(), 0, 0, state.serums(), 0, 0));
                    return 1;
                })))));
    }

    private static String id(CustomData data) {
        return data == null ? "" : data.copyTag().getStringOr("id", "");
    }

    private static void load(Map<Identifier, JsonElement> files) {
        for (JsonElement element : files.values()) {
            JsonObject json = element.getAsJsonObject();
            charges = json.get("charges").getAsInt();
            rechargeMillis = json.get("recharge_hours").getAsLong() * 3600 * 1000;
            var extra = json.getAsJsonArray("extra_charge_levels");
            extraChargeLevels = new int[extra.size()];
            for (int i = 0; i < extra.size(); i++) {
                extraChargeLevels[i] = extra.get(i).getAsInt();
            }
            List<Superpairs> pairs = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("superpairs")) {
                JsonObject s = e.getAsJsonObject();
                List<Reward> rewards = new ArrayList<>();
                for (JsonElement r : s.getAsJsonArray("rewards")) {
                    rewards.add(reward(r.getAsJsonObject()));
                }
                pairs.add(new Superpairs(s.get("name").getAsString(), s.get("enchanting").getAsInt(), s.get("cost").getAsInt(),
                        s.get("width").getAsInt(), s.get("height").getAsInt(), s.get("clicks").getAsInt(), s.get("addon_multiplier").getAsInt(),
                        List.copyOf(rewards)));
            }
            superpairs = List.copyOf(pairs);
            chronomatron = addons(json, "chronomatron");
            ultrasequencer = addons(json, "ultrasequencer");
        }
        SkyRpgSkyblock.LOGGER.info("Loaded the Experimentation Table: {} Superpairs, {} Chronomatron, {} Ultrasequencer stakes",
                superpairs.size(), chronomatron.size(), ultrasequencer.size());
    }

    private static Reward reward(JsonObject r) {
        double weight = r.get("weight").getAsDouble();
        if (r.has("xp")) {
            var xp = r.getAsJsonArray("xp");
            return new Reward("xp", "", xp.get(0).getAsInt(), xp.get(1).getAsInt(), "", weight);
        }
        if (r.has("book")) {
            return new Reward("book", r.get("book").getAsString(), 1, 1, "", weight);
        }
        if (r.has("pet")) {
            return new Reward("pet", r.get("pet").getAsString(), 1, 1, r.get("tier").getAsString(), weight);
        }
        var count = r.getAsJsonArray("count");
        return new Reward("item", r.get("item").getAsString(), count.get(0).getAsInt(), count.get(1).getAsInt(), "", weight);
    }

    private static List<Addon> addons(JsonObject json, String key) {
        List<Addon> out = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray(key)) {
            JsonObject a = e.getAsJsonObject();
            TreeMap<Integer, Integer> bonus = new TreeMap<>();
            a.getAsJsonObject("bonus").entrySet().forEach(b -> bonus.put(Integer.parseInt(b.getKey()), b.getValue().getAsInt()));
            out.add(new Addon(a.get("name").getAsString(), a.get("enchanting").getAsInt(), a.get("cost").getAsInt(),
                    a.has("colors") ? a.get("colors").getAsInt() : 0, a.has("width") ? a.get("width").getAsInt() : 0,
                    a.has("height") ? a.get("height").getAsInt() : 0, a.get("xp").getAsInt(), bonus));
        }
        return List.copyOf(out);
    }

    public static State state(ServerPlayer player) {
        State state = player.getAttached(STATE);
        return state == null ? State.EMPTY : state;
    }

    /** The charges in use (spent less than 24 hours ago), oldest first. */
    static List<Long> spent(ServerPlayer player) {
        long now = System.currentTimeMillis();
        return state(player).used().stream().filter(t -> t > now - rechargeMillis).sorted().toList();
    }

    public static int available(ServerPlayer player) {
        return Math.max(0, charges - spent(player).size());
    }

    /** Milliseconds until the next charge comes back (0 when all are there). */
    static long nextCharge(ServerPlayer player) {
        List<Long> spent = spent(player);
        return spent.isEmpty() ? 0 : Math.max(0, spent.getFirst() + rechargeMillis - System.currentTimeMillis());
    }

    static int enchanting(ServerPlayer player) {
        return SkillLevels.level(player, ENCHANTING);
    }

    /** Levels the next bought charge costs today, or -1 when three were bought today. */
    static int buyCost(ServerPlayer player) {
        State state = state(player);
        int bought = state.boughtDay() == today() ? state.bought() : 0;
        return bought < extraChargeLevels.length ? extraChargeLevels[bought] : -1;
    }

    private static long today() {
        return System.currentTimeMillis() / (24L * 3600 * 1000);
    }

    /** Buying a charge gives back the oldest spent one. "" when bought, else why not. */
    static String buyCharge(ServerPlayer player) {
        List<Long> spent = new ArrayList<>(spent(player));
        int cost = buyCost(player);
        if (spent.isEmpty()) {
            return "All your charges are ready!";
        }
        if (cost < 0) {
            return "You can't buy any more charges today!";
        }
        if (!takeLevels(player, cost)) {
            return "You need " + cost + " XP levels!";
        }
        spent.removeFirst();
        State state = state(player);
        int bought = state.boughtDay() == today() ? state.bought() : 0;
        player.setAttached(STATE, new State(List.copyOf(spent), state.chronoBonus(), state.ultraBonus(), state.serums(), bought + 1, today()));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.6f);
        return "";
    }

    static boolean takeLevels(ServerPlayer player, int levels) {
        if (player.isCreative()) {
            return true;
        }
        if (player.experienceLevel < levels) {
            return false;
        }
        player.giveExperienceLevels(-levels);
        return true;
    }

    /** Starting Superpairs: a charge is used and the banked bonus clicks go into it. */
    static int useCharge(ServerPlayer player, int addonMultiplier) {
        State state = state(player);
        List<Long> used = new ArrayList<>(spent(player));
        used.add(System.currentTimeMillis());
        player.setAttached(STATE, new State(List.copyOf(used), 0, 0, state.serums(), state.bought(), state.boughtDay()));
        return (state.chronoBonus() + state.ultraBonus()) * addonMultiplier;
    }

    /** An add-on finished: its bonus clicks are banked for the next Superpairs (the best result counts). */
    static void bankBonus(ServerPlayer player, boolean chrono, int clicks) {
        State s = state(player);
        player.setAttached(STATE, new State(s.used(), chrono ? Math.max(s.chronoBonus(), clicks) : s.chronoBonus(),
                chrono ? s.ultraBonus() : Math.max(s.ultraBonus(), clicks), s.serums(), s.bought(), s.boughtDay()));
    }

    private static InteractionResult serum(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SERUM_ID.equals(SkyItems.skyblockId(stack))) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            drinkSerum(serverPlayer, stack);
        }
        return InteractionResult.SUCCESS;
    }

    private static void drinkSerum(ServerPlayer player, ItemStack stack) {
        State s = state(player);
        if (s.serums() >= MAX_SERUMS) {
            player.sendSystemMessage(Component.literal("YUCKY! This serum seems to have lost its flavor!").withStyle(ChatFormatting.RED));
            return;
        }
        stack.shrink(1);
        player.setAttached(STATE, new State(s.used(), s.chronoBonus(), s.ultraBonus(), s.serums() + 1, s.bought(), s.boughtDay()));
        Notice.of(Notice.Kind.ENCHANT).tag("SERUM").send(player, Component.literal("Superpairs add-on milestones are now ")
                .append(Component.literal((s.serums() + 1) + " shorter").withStyle(ChatFormatting.LIGHT_PURPLE))
                .append(Component.literal(" (" + (s.serums() + 1) + "/" + MAX_SERUMS + " doses).").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.GENERIC_DRINK.value(), 1.0f, 1.0f);
    }
}
