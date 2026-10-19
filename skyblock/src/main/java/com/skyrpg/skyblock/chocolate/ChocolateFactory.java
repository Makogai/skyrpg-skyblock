package com.skyrpg.skyblock.chocolate;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.serialization.Codec;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Hypixel's Chocolate Factory ({@code /cf}): chocolate made every second by the rabbits you've found and the employees
 * you've hired, Hand-Baked Chocolate for clicking, the Rabbit Barn, the Time Tower, the Rabbit Shrine, Coach Jackrabbit,
 * prestige to the next Factory level, milestones, the Chocolate Shop. Rabbits come from Hoppity's Hunt
 * ({@link HoppitysHunt}) and Hoppity's shop ({@link Hoppity}). Each profile has its own factory (a player attachment).
 *
 * <p>Admins: {@code /sbchocolate give <amount>}, {@code /sbchocolate stray}, {@code /sbchocolate eggs} (testing).
 */
public final class ChocolateFactory {
    static final AttachmentType<String> DATA = AttachmentRegistry.create(SkyRpgSkyblock.id("chocolate_factory"),
            builder -> builder.persistent(Codec.STRING).copyOnDeath());
    private static final Gson GSON = new Gson();

    private ChocolateFactory() {
    }

    public static void init() {
        DATA.identifier();
        Rabbits.init();
        FactoryMenu.init();
        HoppitysHunt.init();
        Hoppity.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            for (String name : new String[]{"cf", "chocolatefactory"}) {
                dispatcher.register(Commands.literal(name).executes(ctx -> {
                    FactoryMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }));
            }
            dispatcher.register(Commands.literal("sbchocolate").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("give").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0)).executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        edit(player, f -> f.gain(DoubleArgumentType.getDouble(ctx, "amount")));
                        return 1;
                    })))
                    .then(Commands.literal("stray").executes(ctx -> {
                        FactoryMenu.stray(ctx.getSource().getPlayerOrException());
                        return 1;
                    }))
                    .then(Commands.literal("eggs").executes(ctx -> {
                        HoppitysHunt.list(ctx.getSource().getPlayerOrException());
                        return 1;
                    })));
        });
    }

    /** The player's factory, its production since last time added. */
    static Factory get(ServerPlayer player) {
        Factory factory = null;
        String json = player.getAttached(DATA);
        if (json != null) {
            try {
                factory = GSON.fromJson(json, Factory.class);
            } catch (JsonParseException e) {
                SkyRpgSkyblock.LOGGER.error("Bad Chocolate Factory data for {}: {}", player.getName().getString(), e.getMessage());
            }
        }
        if (factory == null) {
            factory = new Factory();
        }
        factory.settle(System.currentTimeMillis());
        return factory;
    }

    static void save(ServerPlayer player, Factory factory) {
        player.setAttached(DATA, GSON.toJson(factory));
    }

    /** Changes the player's factory and keeps it; milestones it reaches are handed out. */
    static Factory edit(ServerPlayer player, Consumer<Factory> change) {
        Factory factory = get(player);
        change.accept(factory);
        milestones(player, factory);
        save(player, factory);
        return factory;
    }

    // ---------------------------------------------------------------- rabbits

    /**
     * A rabbit found (an egg, a stray's surprise, Hoppity): new if you haven't got it and the barn has room; otherwise a
     * duplicate, worth some seconds of production ({@link Rabbits#DUPLICATE_SECONDS}, +10% a duplicate up to the tenth,
     * +30% with King). Says so in chat.
     */
    static void found(ServerPlayer player, Factory factory, Rabbits.Rabbit rabbit) {
        long now = System.currentTimeMillis();
        int had = factory.rabbits.getOrDefault(rabbit.name(), 0);
        factory.found++;
        MutableComponent rarity = Component.literal(Rabbits.rarityName(rabbit.rarity())).withStyle(rabbit.color(), ChatFormatting.BOLD);
        player.sendSystemMessage(Component.literal("HOPPITY'S HUNT ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("You found ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(rabbit.name()).withStyle(rabbit.color()))
                .append(Component.literal(" (").withStyle(ChatFormatting.GRAY)).append(rarity)
                .append(Component.literal(")!").withStyle(ChatFormatting.GRAY)));
        if (had == 0 && factory.rabbits.size() < factory.barnSize()) {
            factory.rabbits.put(rabbit.name(), 1);
            player.sendSystemMessage(Component.literal("NEW RABBIT! ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                    .append(Component.literal(rabbit.text()).withStyle(ChatFormatting.GRAY)));
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.6f);
            return;
        }
        int duplicates = had + 1;
        if (had > 0) {
            factory.rabbits.put(rabbit.name(), duplicates);
            factory.duplicates++;
        }
        double chocolate = factory.perSecond(now) * Rabbits.DUPLICATE_SECONDS[rabbit.rarity()] * (1 + Math.min(had > 0 ? duplicates - 1 : 0, 10) * 0.1)
                * (factory.has("King") ? 1.3 : 1);
        factory.gain(chocolate);
        String why = had > 0 ? "DUPLICATE RABBIT! " : "BARN FULL! ";
        player.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("+" + Msg.number(Math.round(chocolate)) + " Chocolate").withStyle(ChatFormatting.GOLD)));
        Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    /** All-time chocolate and shop milestones, duplicate counts, unique collections, an island's 15 egg spots: their rabbits. */
    static void milestones(ServerPlayer player, Factory factory) {
        for (int i = 0; i < Factory.MILESTONES.length; i++) {
            if (factory.allTime >= Factory.MILESTONES[i]) {
                give(player, factory, Factory.MILESTONE_RABBITS[i], "Chocolate Factory Milestone");
            }
        }
        for (int i = 0; i < Factory.SHOP_MILESTONES.length; i++) {
            if (factory.spent >= Factory.SHOP_MILESTONES[i]) {
                give(player, factory, Factory.SHOP_RABBITS[i], "Chocolate Shop Milestone");
            }
        }
        int[] duplicateSteps = {100, 500, 1000, 2000};
        String[] duplicateRabbits = {"Crush", "Squish", "Burst", "Implode"};
        for (int i = 0; i < duplicateSteps.length; i++) {
            if (factory.duplicates >= duplicateSteps[i]) {
                give(player, factory, duplicateRabbits[i], "duplicates found");
            }
        }
        int[] unique = new int[7];
        for (String name : factory.rabbits.keySet()) {
            Rabbits.Rabbit r = Rabbits.get(name);
            if (r != null) {
                unique[r.rarity()]++;
            }
        }
        Object[][] collections = {{"Average", 0, 100}, {"Mediocrity", 0, 200}, {"Modest", 1, 50}, {"Prudent", 1, 100}, {"Favor", 2, 50},
                {"Saga", 3, 25}, {"Extra", 4, 20}};
        for (Object[] c : collections) {
            if (unique[(int) c[1]] >= (int) c[2]) {
                give(player, factory, (String) c[0], "rabbits collected");
            }
        }
        if (factory.level >= 3) {
            give(player, factory, "Prestige", "Chocolate Factory III");
        }
        // 15 unique egg spots on an island: its own rabbit ("Find 15 unique egg locations in the Hub.")
        for (var entry : factory.eggSpots.entrySet()) {
            if (entry.getValue().size() >= 15) {
                for (Rabbits.Rabbit r : Rabbits.all()) {
                    if (r.source().equals("requirement") && r.requirement().startsWith("Find 15 unique egg locations")
                            && r.resident().equalsIgnoreCase(entry.getKey())) {
                        give(player, factory, r.name(), "15 egg spots on " + entry.getKey());
                    }
                }
            }
        }
    }

    private static void give(ServerPlayer player, Factory factory, String name, String why) {
        Rabbits.Rabbit rabbit = Rabbits.get(name);
        if (rabbit == null || factory.has(name) || factory.rabbits.size() >= factory.barnSize()) {
            return;
        }
        factory.rabbits.put(name, 1);
        player.sendSystemMessage(Component.literal("NEW RABBIT! ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal(name).withStyle(rabbit.color()))
                .append(Component.literal(" (" + why + ")").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.6f);
    }
}
