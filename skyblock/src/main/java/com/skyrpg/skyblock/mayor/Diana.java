package com.skyrpg.skyblock.mayor;

import com.skyrpg.core.action.Actions;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.economy.Purse;
import com.skyrpg.hub.Hub;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.pets.Pets;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.pet.SkyBlockPets;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Diana's Mythological Ritual (her perk; Hypixel's, after its Oct 2025 rework). Hold a spade in the Hub and Griffin
 * burrows show on its grass: start burrows (green). Left-click one with the spade to dig it; that opens a chain of 4,
 * 6 or 8 burrows (Ancestral, Archaic, Deific Spade), the spade's Echo (right-click) pointing the way to the next one.
 * Every later burrow is 75% a mob (red) and 25% treasure (gold): the spade's treasure table, or one of the twelve
 * Mythological mobs your Griffin pet's rarity can call up (Hypixel's levels, health, damage, coins, XP and drops), whom
 * you must defeat before the burrow counts, and only with a Griffin at least as rare as the one that called it. Diana
 * sells the Griffin and the Ancestral Spade. Decided: burrows come 15 to 50 blocks from you; the trail's colour is
 * the distance (yellow under 25, red under 60, grey beyond); the mobs' special attacks are later.
 */
final class Diana {
    static final String TAG = "skyrpg_minos";
    private static final String OWNER_TAG = TAG + ".owner.";
    private static final String TIER_TAG = TAG + ".tier.";
    private static final String KIND_TAG = TAG + ".kind.";
    private static final List<String> SPADES = List.of("ANCESTRAL_SPADE", "ARCHAIC_SPADE", "DEIFIC_SPADE");
    private static final int[] CHAIN = {4, 6, 8};
    private static final String[] PREFIX = {"", "Blessed ", "Stalwart ", "Venerable ", "Exalted ", "Empyrean "};
    private static final String[] RARITY = {"Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic"};
    private static final int STARTS = 3;
    private static final String[] SHOUTS = {"Danger!", "Oi!", "Uh oh!", "Woah!", "Yikes!", "Good Grief!"};

    /** One of Hypixel's Mythological mobs: from which Griffin rarity, spawn weight, and its numbers at each rarity. */
    private record Minos(String name, EntityType<? extends Mob> type, int min, int weight, boolean elusive, int[] level, double[] health,
                         double[] damage, int[] coins, int[] xp, int[] claws, String rare, double[] rareChance, String rare2,
                         double rare2Chance) {
    }

    private static int[] i(int... values) {
        return values;
    }

    private static double[] d(double... values) {
        return values;
    }

    private static final List<Minos> MOBS = List.of(
            new Minos("Minos Hunter", EntityTypes.ZOMBIE, 0, 1000, false, i(20, 43, 92, 137, 184, 211),
                    d(4e3, 15e3, 100e3, 350e3, 1e6, 1.75e6), d(40, 125, 500, 1250, 2750, 4000), i(6, 14, 30, 45, 61, 70),
                    i(30, 64, 138, 205, 276, 316), i(1, 2, 3, 4, 6, 8), "HILT_OF_REVELATIONS", d(0.0025, 0.005, 0.0075, 0.01, 0.015, 0.02), "", 0),
            new Minos("Siamese Lynxes", EntityTypes.OCELOT, 0, 1000, false, i(15, 36, 80, 124, 171, 193),
                    d(2.5e3, 12.5e3, 75e3, 250e3, 750e3, 1.25e6), d(30, 75, 350, 1000, 2250, 3000), i(5, 12, 26, 41, 57, 64),
                    i(22, 54, 120, 186, 256, 289), i(1, 2, 3, 4, 6, 8), "CROCHET_TIGER_PLUSHIE", d(0.0005, 0.001, 0.0015, 0.002, 0.003, 0.004), "", 0),
            new Minos("Stranded Nymph", EntityTypes.DROWNED, 1, 800, false, i(0, 44, 83, 128, 178, 208),
                    d(0, 20e3, 125e3, 400e3, 1.25e6, 2.5e6), d(0, 100, 250, 750, 1750, 2500), i(0, 14, 27, 42, 59, 69),
                    i(0, 66, 124, 192, 267, 312), i(0, 4, 5, 6, 8, 10), "WASHED_UP_SOUVENIR", d(0, 0.002, 0.0025, 0.003, 0.004, 0.005), "", 0),
            new Minos("Cretan Bull", EntityTypes.RAVAGER, 1, 800, false, i(0, 47, 95, 135, 186, 216),
                    d(0, 25e3, 150e3, 500e3, 1.5e6, 3e6), d(0, 100, 400, 800, 2000, 2750), i(0, 15, 31, 45, 62, 72),
                    i(0, 70, 142, 202, 279, 324), i(0, 4, 5, 6, 8, 10), "CRETAN_URN", d(0, 0.002, 0.0025, 0.003, 0.004, 0.005), "", 0),
            new Minos("Harpy", EntityTypes.PHANTOM, 2, 600, false, i(0, 0, 113, 160, 213, 251),
                    d(0, 0, 200e3, 750e3, 2e6, 3.75e6), d(0, 0, 750, 1500, 3750, 6500), i(0, 0, 37, 53, 71, 83),
                    i(0, 0, 169, 240, 319, 376), i(0, 0, 7, 8, 10, 12), "ANTIQUE_REMEDIES", d(0, 0, 0.0035, 0.004, 0.005, 0.006), "", 0),
            new Minos("Gaia Construct", EntityTypes.IRON_GOLEM, 2, 600, false, i(0, 0, 106, 164, 203, 240),
                    d(0, 0, 175e3, 650e3, 1.5e6, 3.5e6), d(0, 0, 600, 2000, 3500, 5000), i(0, 0, 35, 54, 67, 80),
                    i(0, 0, 159, 246, 304, 360), i(0, 0, 7, 8, 10, 12), "DWARF_TURTLE_SHELMET", d(0, 0, 0.0035, 0.004, 0.005, 0.006), "", 0),
            new Minos("Minotaur", EntityTypes.VINDICATOR, 3, 400, false, i(0, 0, 0, 141, 195, 236),
                    d(0, 0, 0, 1.5e6, 8e6, 15.5e6), d(0, 0, 0, 350, 500, 1000), i(0, 0, 0, 47, 65, 78),
                    i(0, 0, 0, 211, 292, 354), i(0, 0, 0, 10, 12, 14), "DAEDALUS_STICK", d(0, 0, 0, 0.0008, 0.0012, 0.0016), "", 0),
            new Minos("Minos Champion", EntityTypes.SKELETON, 3, 400, false, i(0, 0, 0, 175, 248, 285),
                    d(0, 0, 0, 2e6, 12.5e6, 25e6), d(0, 0, 0, 1000, 1750, 2500), i(0, 0, 0, 58, 82, 95),
                    i(0, 0, 0, 262, 372, 427), i(0, 0, 0, 10, 12, 14), "MINOS_RELIC", d(0, 0, 0, 0.0002, 0.0003, 0.0004), "", 0),
            new Minos("Minos Inquisitor", EntityTypes.WITHER_SKELETON, 4, 75, true, i(0, 0, 0, 0, 264, 290),
                    d(0, 0, 0, 0, 50e6, 80e6), d(0, 0, 0, 0, 700, 900), i(0, 0, 0, 0, 88, 96),
                    i(0, 0, 0, 0, 396, 435), i(0, 0, 0, 0, 16, 32), "book:ultimate_chimera", d(0, 0, 0, 0, 0.01, 0.0125), "", 0),
            new Minos("Sphinx", EntityTypes.HUSK, 4, 75, true, i(0, 0, 0, 0, 315, 349),
                    d(0, 0, 0, 0, 40e6, 65e6), d(0, 0, 0, 0, 3500, 5000), i(0, 0, 0, 0, 105, 116),
                    i(0, 0, 0, 0, 472, 523), i(0, 0, 0, 0, 16, 32), "BRAIN_FOOD", d(0, 0, 0, 0, 0.0025, 0.005), "", 0),
            new Minos("Manticore", EntityTypes.RAVAGER, 5, 15, true, i(0, 0, 0, 0, 0, 377),
                    d(0, 0, 0, 0, 0, 125e6), d(0, 0, 0, 0, 0, 5000), i(0, 0, 0, 0, 0, 125),
                    i(0, 0, 0, 0, 0, 565), i(0, 0, 0, 0, 0, 48), "FATEFUL_STINGER", d(0, 0, 0, 0, 0, 0.005), "MANTI_CORE", 0.002),
            new Minos("King Minos", EntityTypes.PIGLIN_BRUTE, 5, 15, true, i(0, 0, 0, 0, 0, 338),
                    d(0, 0, 0, 0, 0, 100e6), d(0, 0, 0, 0, 0, 2500), i(0, 0, 0, 0, 0, 112),
                    i(0, 0, 0, 0, 0, 507), i(0, 0, 0, 0, 0, 48), "CROWN_OF_GREED", d(0, 0, 0, 0, 0, 0.02), "SHIMMERING_WOOL", 0.002));

    /** A spade's treasure table: item (or "coins:N") and weight (Hypixel's). */
    private record Treasure(String item, int weight) {
    }

    private static final List<List<Treasure>> TREASURE = List.of(
            List.of(new Treasure("GRIFFIN_FEATHER", 64), new Treasure("coins:2500", 15), new Treasure("coins:5000", 10),
                    new Treasure("coins:10000", 5), new Treasure("coins:25000", 2)),
            List.of(new Treasure("GRIFFIN_FEATHER", 375), new Treasure("coins:5000", 100), new Treasure("coins:10000", 40),
                    new Treasure("MYTHOS_FRAGMENT", 40), new Treasure("coins:25000", 20), new Treasure("coins:50000", 10),
                    new Treasure("coins:100000", 5), new Treasure("coins:250000", 2)),
            List.of(new Treasure("GRIFFIN_FEATHER", 2020), new Treasure("coins:10000", 500), new Treasure("MYTHOS_FRAGMENT", 300),
                    new Treasure("coins:25000", 200), new Treasure("coins:50000", 100), new Treasure("coins:100000", 50),
                    new Treasure("coins:250000", 25), new Treasure("coins:500000", 15), new Treasure("coins:1000000", 10),
                    new Treasure("BRAIDED_GRIFFIN_FEATHER", 1)));

    private enum Kind { START, MOB, TREASURE }

    private record Burrow(BlockPos pos, Kind kind) {
    }

    /** A chain being dug: its length, burrows dug, the next burrow, and the mob defending it (if any). */
    private static final class Chain {
        int length;
        int dug;
        Burrow next;
        UUID defender;
    }

    private static final class Hunt {
        final List<Burrow> starts = new ArrayList<>();
        Chain chain;
    }

    private static final Map<UUID, Hunt> HUNTS = new HashMap<>();

    private Diana() {
    }

    static void init() {
        Abilities.register("echo", (player, ability) -> echo(player));
        Actions.register("diana", (player, params) -> shop(player));
        Identifier first = SkyRpgSkyblock.id("diana_dig");
        AttackBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        AttackBlockCallback.EVENT.register(first, (player, level, hand, pos, direction) -> {
            if (!Hub.isHub(level) || spade(player.getMainHandItem()) < 0) {
                return InteractionResult.PASS;
            }
            // the grass block, or the grass or flower growing on it
            BlockPos ground = level.getBlockState(pos).is(Blocks.GRASS_BLOCK) ? pos
                    : level.getBlockState(pos).canBeReplaced() && level.getBlockState(pos.below()).is(Blocks.GRASS_BLOCK) ? pos.below() : null;
            if (ground == null) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return player instanceof ServerPlayer server && dig(server, ground.immutable()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!entity.entityTags().contains(TAG) || !(source.getEntity() instanceof ServerPlayer attacker)) {
                return true;
            }
            int need = tagInt(entity.entityTags(), TIER_TAG);
            if (griffin(attacker) < need) {
                attacker.sendOverlayMessage(Component.literal("You need a " + RARITY[need] + " Griffin pet or better to damage this!")
                        .withStyle(ChatFormatting.RED));
                return false;
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.entityTags().contains(TAG) && entity.level() instanceof ServerLevel level) {
                if (source.getEntity() instanceof ServerPlayer killer) {
                    // SkyBlock Leveling: +1 per 100 Mythological Creatures killed
                    com.skyrpg.skyblock.leveling.LevelingTasks.count(killer, "mythological", 1, 100);
                }
                defeated(level, (Mob) entity);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Diana::tick);
        // For testing: /sbdiana burrows <player> lists their burrows.
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                dispatcher.register(net.minecraft.commands.Commands.literal("sbdiana")
                        .requires(net.minecraft.commands.Commands.hasPermission(net.minecraft.commands.Commands.LEVEL_GAMEMASTERS))
                        .then(net.minecraft.commands.Commands.literal("burrows")
                                .then(net.minecraft.commands.Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer target = net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx, "player");
                                            Hunt hunt = hunt(target);
                                            StringBuilder out = new StringBuilder("starts:");
                                            hunt.starts.forEach(b -> out.append(" ").append(b.pos().toShortString()));
                                            if (hunt.chain != null) {
                                                out.append(" | chain ").append(hunt.chain.dug).append("/").append(hunt.chain.length).append(" next ")
                                                        .append(hunt.chain.next == null ? "-" : hunt.chain.next.kind() + " " + hunt.chain.next.pos().toShortString());
                                            }
                                            ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
                                            return 1;
                                        })))));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HUNTS.remove(handler.player.getUUID()));
    }

    private static boolean ritual() {
        return Mayors.active("mythological_ritual");
    }

    /** Which spade this is (0 Ancestral, 1 Archaic, 2 Deific), or -1. */
    private static int spade(ItemStack stack) {
        String id = SkyItems.skyblockId(stack);
        return id == null ? -1 : SPADES.indexOf(id);
    }

    /** The rarity (0 Common to 5 Mythic) of the Griffin pet out, or -1. */
    static int griffin(ServerPlayer player) {
        ItemStack pet = Pets.active(player);
        if (!SkyBlockPets.isPet(pet) || !"GRIFFIN".equals(SkyBlockPets.type(pet))) {
            return -1;
        }
        String tier = SkyBlockPets.tier(pet);
        return tier == null ? 0 : Math.max(0, List.of(SkyBlockPets.TIERS).indexOf(tier));
    }

    private static int tagInt(java.util.Set<String> tags, String prefix) {
        return tags.stream().filter(t -> t.startsWith(prefix)).findFirst().map(t -> Integer.parseInt(t.substring(prefix.length()))).orElse(0);
    }

    // ---------------------------------------------------------------- burrows

    /** A grass spot 15 to 50 blocks from {@code around}, open to the sky, in a loaded chunk; null when none was found. */
    private static BlockPos spot(ServerLevel level, Vec3 around, RandomSource random) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = 15 + random.nextDouble() * 35;
            BlockPos column = BlockPos.containing(around.x + Math.cos(a) * r, 0, around.z + Math.sin(a) * r);
            if (!level.isLoaded(column)) {
                continue;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
            if (level.getBlockState(top).is(Blocks.GRASS_BLOCK) && level.getBlockState(top.above()).canBeReplaced()) {
                return top.immutable();
            }
        }
        return null;
    }

    private static Hunt hunt(ServerPlayer player) {
        return HUNTS.computeIfAbsent(player.getUUID(), id -> new Hunt());
    }

    private static Kind roll(RandomSource random) {
        return random.nextDouble() < 0.75 ? Kind.MOB : Kind.TREASURE;
    }

    /** Left-click with a spade: dig a burrow of yours here, if there's one. */
    private static boolean dig(ServerPlayer player, BlockPos pos) {
        if (!ritual()) {
            return false;
        }
        Hunt hunt = hunt(player);
        ServerLevel level = (ServerLevel) player.level();
        int spade = spade(player.getMainHandItem());
        Burrow start = hunt.starts.stream().filter(b -> b.pos().equals(pos)).findFirst().orElse(null);
        if (start != null) {
            if (hunt.chain != null) {
                player.sendOverlayMessage(Component.literal("Finish your burrow chain first!").withStyle(ChatFormatting.YELLOW));
                return true;
            }
            hunt.starts.remove(start);
            Chain chain = new Chain();
            chain.length = CHAIN[spade];
            chain.dug = 1;
            hunt.chain = chain;
            effects(level, pos);
            player.sendSystemMessage(Component.literal("You dug out a Griffin Burrow! ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("(1/" + chain.length + ")").withStyle(ChatFormatting.GRAY)));
            next(player, level, chain, pos);
            return true;
        }
        Chain chain = hunt.chain;
        if (chain == null || chain.next == null || !chain.next.pos().equals(pos)) {
            return false;
        }
        if (chain.defender != null) {
            var mob = level.getEntity(chain.defender);
            if (mob != null && mob.isAlive()) {
                player.sendOverlayMessage(Component.literal("Defeat the burrow defenders in order to dig it!").withStyle(ChatFormatting.RED));
                return true;
            }
            chain.defender = null;
        }
        effects(level, pos);
        if (chain.next.kind() == Kind.MOB) {
            int tier = griffin(player);
            if (tier < 0) {
                player.sendOverlayMessage(Component.literal("Something stirs below... you need a Griffin pet out to face it!").withStyle(ChatFormatting.RED));
                return true;
            }
            Minos kind = pick(player, tier);
            chain.defender = summon(level, player, pos, kind, tier).getUUID();
            player.sendSystemMessage(Component.literal(SHOUTS[player.getRandom().nextInt(SHOUTS.length)] + " ").withStyle(ChatFormatting.RED)
                    .append(Component.literal("You dug out a ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(PREFIX[tier] + kind.name()).withStyle(ChatFormatting.GREEN))
                    .append(Component.literal("!").withStyle(ChatFormatting.YELLOW)));
            Msg.playSound(player, SoundEvents.RAVAGER_ROAR, 0.6f, 1.4f);
            return true;
        }
        treasure(player, spade);
        advance(player, level, chain, pos);
        return true;
    }

    /** The burrow counts: on to the next one, or the chain is done. */
    private static void advance(ServerPlayer player, ServerLevel level, Chain chain, BlockPos at) {
        chain.dug++;
        chain.defender = null;
        Hunt hunt = hunt(player);
        if (chain.dug >= chain.length) {
            hunt.chain = null;
            player.sendSystemMessage(Component.literal("You finished the Griffin burrow chain! ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("(" + chain.length + "/" + chain.length + ")").withStyle(ChatFormatting.GRAY)));
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.8f, 1.2f);
            return;
        }
        player.sendSystemMessage(Component.literal("You dug out a Griffin Burrow! ").withStyle(ChatFormatting.YELLOW)
                .append(Component.literal("(" + chain.dug + "/" + chain.length + ")").withStyle(ChatFormatting.GRAY)));
        next(player, level, chain, at);
    }

    private static void next(ServerPlayer player, ServerLevel level, Chain chain, BlockPos from) {
        BlockPos pos = spot(level, Vec3.atCenterOf(from), player.getRandom());
        if (pos == null) {
            pos = spot(level, player.position(), player.getRandom());
        }
        chain.next = pos == null ? null : new Burrow(pos, roll(player.getRandom()));
        if (chain.next == null) {
            HUNTS.get(player.getUUID()).chain = null;
        }
    }

    private static void effects(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.01);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 10, 0.4, 0.2, 0.4, 0);
        level.playSound(null, pos, SoundEvents.ROOTED_DIRT_BREAK, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.8f);
    }

    // ---------------------------------------------------------------- treasure

    private static void treasure(ServerPlayer player, int spade) {
        RandomSource random = player.getRandom();
        if (random.nextDouble() < 0.0001) {
            give(player, "MYTH_THE_FISH", 1);
            player.sendSystemMessage(Component.literal("RARE DROP! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("You dug out Myth the Fish!").withStyle(ChatFormatting.AQUA)));
            return;
        }
        List<Treasure> table = TREASURE.get(spade);
        int total = table.stream().mapToInt(Treasure::weight).sum();
        int roll = random.nextInt(total);
        Treasure pick = table.getFirst();
        for (Treasure t : table) {
            roll -= t.weight();
            if (roll < 0) {
                pick = t;
                break;
            }
        }
        if (pick.item().startsWith("coins:")) {
            long coins = Long.parseLong(pick.item().substring(6));
            // the Crown of Greed's Indulgence: twice the coins
            if ("CROWN_OF_GREED".equals(SkyItems.skyblockId(player.getItemBySlot(EquipmentSlot.HEAD)))) {
                coins *= 2;
            }
            Purse.add(player, coins);
            player.sendSystemMessage(Component.literal("Wow! ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("You dug out " + Msg.number(coins) + " coins!").withStyle(ChatFormatting.YELLOW)));
            Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 1f);
            return;
        }
        Component name = give(player, pick.item(), 1);
        player.sendSystemMessage(Component.literal("RARE DROP! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("You dug out a ").withStyle(ChatFormatting.YELLOW)).append(name)
                .append(Component.literal("!").withStyle(ChatFormatting.YELLOW)));
        Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.5f);
    }

    private static Component give(ServerPlayer player, String id, int count) {
        ItemStack stack;
        if (id.startsWith("book:")) {
            stack = com.skyrpg.items.enchant.Books.create(id.substring(5), 1, 1);
        } else {
            ItemDefinition def = ItemRegistry.skyblock(id);
            stack = def == null ? null : SkyItems.create(def, count);
        }
        if (stack == null || stack.isEmpty()) {
            return Component.literal(id);
        }
        Component name = stack.getHoverName().copy();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        return name;
    }

    // ---------------------------------------------------------------- the Mythological mobs

    /** A mob for this Griffin rarity: every mob it can call (cumulative), by weight; elusive ones x (1 + Tracking / 100). */
    private static Minos pick(ServerPlayer player, int tier) {
        double tracking = Stats.get(player, StatTypes.TRACKING);
        List<Minos> pool = MOBS.stream().filter(m -> m.min() <= tier).toList();
        double total = pool.stream().mapToDouble(m -> m.weight() * (m.elusive() ? 1 + tracking / 100 : 1)).sum();
        double roll = player.getRandom().nextDouble() * total;
        for (Minos m : pool) {
            roll -= m.weight() * (m.elusive() ? 1 + tracking / 100 : 1);
            if (roll < 0) {
                return m;
            }
        }
        return pool.getFirst();
    }

    private static Mob summon(ServerLevel level, ServerPlayer owner, BlockPos pos, Minos kind, int tier) {
        Mob mob = kind.type().create(level, EntitySpawnReason.EVENT);
        mob.snapTo(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, owner.getYRot() + 180, 0);
        Zones.dressAs(mob, PREFIX[tier] + kind.name(), kind.level()[tier], kind.health()[tier], kind.damage()[tier], kind.xp()[tier]);
        if (kind.type() == EntityTypes.ZOMBIE || kind.type() == EntityTypes.PIGLIN_BRUTE) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(kind.type() == EntityTypes.ZOMBIE ? Items.IRON_SWORD : Items.GOLDEN_AXE));
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        } else if (kind.type() == EntityTypes.SKELETON || kind.type() == EntityTypes.WITHER_SKELETON) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(kind.type() == EntityTypes.SKELETON ? Items.BOW : Items.GOLDEN_SWORD));
            mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.GOLDEN_CHESTPLATE));
        } else if (kind.type() == EntityTypes.VINDICATOR) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
        }
        mob.addTag(TAG);
        mob.addTag(Zones.FIGHTABLE_TAG);
        mob.addTag(OWNER_TAG + owner.getUUID());
        mob.addTag(TIER_TAG + tier);
        mob.addTag(KIND_TAG + MOBS.indexOf(kind));
        mob.setPersistenceRequired();
        mob.setTarget(owner);
        level.addFreshEntity(mob);
        level.sendParticles(ParticleTypes.EXPLOSION, mob.getX(), mob.getY() + 0.5, mob.getZ(), 2, 0.3, 0.3, 0.3, 0);
        return mob;
    }

    /** A Mythological mob died: its drops to its digger, and their burrow counts. */
    private static void defeated(ServerLevel level, Mob mob) {
        UUID owner = mob.entityTags().stream().filter(t -> t.startsWith(OWNER_TAG)).findFirst()
                .map(t -> UUID.fromString(t.substring(OWNER_TAG.length()))).orElse(null);
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (player == null) {
            return;
        }
        int tier = tagInt(mob.entityTags(), TIER_TAG);
        Minos kind = MOBS.get(tagInt(mob.entityTags(), KIND_TAG));
        RandomSource random = player.getRandom();
        double find = 1 + Stats.get(player, StatTypes.MAGIC_FIND) / 100;
        Purse.add(player, kind.coins()[tier]);
        give(player, "ANCIENT_CLAW", kind.claws()[tier]);
        if (random.nextBoolean()) {
            give(player, "ENCHANTED_GOLD", kind.claws()[tier]);
        }
        if (kind.elusive() && random.nextBoolean()) {
            give(player, "ENCHANTED_ANCIENT_CLAW", kind.min() == 5 ? 4 : tier == 5 ? 2 : 1);
        }
        for (String[] rare : new String[][] {{kind.rare(), Double.toString(kind.rareChance()[tier])}, {kind.rare2(), Double.toString(kind.rare2Chance())}}) {
            if (!rare[0].isEmpty() && random.nextDouble() < Double.parseDouble(rare[1]) * find) {
                Component name = give(player, rare[0], 1);
                player.sendSystemMessage(Component.literal("RARE DROP! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).append(name)
                        .append(Component.literal(" (+" + Msg.number(Stats.get(player, StatTypes.MAGIC_FIND)) + "% ✯ Magic Find)").withStyle(ChatFormatting.AQUA)));
                Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 2f);
            }
        }
        Hunt hunt = HUNTS.get(player.getUUID());
        if (hunt != null && hunt.chain != null && mob.getUUID().equals(hunt.chain.defender)) {
            advance(player, level, hunt.chain, hunt.chain.next.pos());
        }
    }

    // ---------------------------------------------------------------- the Echo, showing burrows, the tick

    /** The spade's Echo: a trail of particles toward the next burrow of your chain (or the nearest start burrow). */
    private static boolean echo(ServerPlayer player) {
        if (spade(player.getMainHandItem()) < 0) {
            return false;
        }
        if (!ritual() || !Hub.isHub(player.level())) {
            player.sendOverlayMessage(Component.literal("Griffin burrows are only out in the Hub during Diana's Mythological Ritual.")
                    .withStyle(ChatFormatting.GRAY));
            return true;
        }
        Hunt hunt = hunt(player);
        BlockPos target = hunt.chain != null && hunt.chain.next != null ? hunt.chain.next.pos()
                : hunt.starts.stream().map(Burrow::pos).min(java.util.Comparator.comparingDouble(p -> p.distSqr(player.blockPosition()))).orElse(null);
        if (target == null) {
            player.sendOverlayMessage(Component.literal("No burrows nearby yet...").withStyle(ChatFormatting.GRAY));
            return true;
        }
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 to = Vec3.atCenterOf(target).add(0, 1, 0).subtract(eye);
        double distance = to.length();
        int color = distance < 25 ? 0xFFFF55 : distance < 60 ? 0xFF5555 : 0x555555;
        DustParticleOptions dust = new DustParticleOptions(color, 2f);
        Vec3 step = to.normalize().scale(0.5);
        Vec3 at = eye.add(to.normalize().scale(1.5));
        for (int k = 0; k < Math.min(30, distance * 2); k++) {
            level.sendParticles(player, dust, true, true, at.x, at.y, at.z, 2, 0.03, 0.03, 0.03, 0);
            at = at.add(step);
        }
        Msg.playSound(player, SoundEvents.NOTE_BLOCK_HARP.value(), 0.6f, distance < 25 ? 2f : distance < 60 ? 1.4f : 0.8f);
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        ServerLevel hub = Hub.level(server);
        boolean ritual = ritual();
        if (hub == null) {
            return;
        }
        for (ServerPlayer player : hub.players()) {
            int spade = spade(player.getMainHandItem());
            if (!ritual || spade < 0) {
                continue;
            }
            Hunt hunt = hunt(player);
            // start burrows near you (re-placed when you've wandered far from them)
            hunt.starts.removeIf(b -> b.pos().distSqr(player.blockPosition()) > 150 * 150);
            if (hunt.chain == null && hunt.starts.size() < STARTS && server.getTickCount() % 40 == 0) {
                BlockPos pos = spot(hub, player.position(), player.getRandom());
                if (pos != null) {
                    hunt.starts.add(new Burrow(pos, Kind.START));
                }
            }
            for (Burrow b : hunt.starts) {
                show(hub, player, b);
            }
            if (hunt.chain != null && hunt.chain.next != null) {
                show(hub, player, hunt.chain.next);
            }
        }
        // defenders whose digger has gone go too
        if (server.getTickCount() % 100 == 0) {
            for (var entity : hub.getAllEntities()) {
                if (entity instanceof Mob mob && entity.entityTags().contains(TAG)) {
                    UUID owner = mob.entityTags().stream().filter(t -> t.startsWith(OWNER_TAG)).findFirst()
                            .map(t -> UUID.fromString(t.substring(OWNER_TAG.length()))).orElse(null);
                    ServerPlayer player = owner == null ? null : server.getPlayerList().getPlayer(owner);
                    if (player == null || player.level() != hub || player.distanceTo(mob) > 64) {
                        mob.discard();
                    } else if (mob.getTarget() == null) {
                        mob.setTarget(player);
                    }
                }
            }
        }
    }

    /** A burrow's particles, for its digger only: start green, mob red, treasure gold. */
    private static void show(ServerLevel level, ServerPlayer player, Burrow burrow) {
        if (burrow.pos().distSqr(player.blockPosition()) > 80 * 80) {
            return;
        }
        int color = switch (burrow.kind()) {
            case START -> 0x55FF55;
            case MOB -> 0xFF5555;
            case TREASURE -> 0xFFAA00;
        };
        BlockPos p = burrow.pos();
        level.sendParticles(player, new DustParticleOptions(color, 2.2f), true, true, p.getX() + 0.5, p.getY() + 1.2, p.getZ() + 0.5, 8, 0.35, 0.15, 0.35, 0);
        var sparkle = switch (burrow.kind()) {
            case START -> ParticleTypes.HAPPY_VILLAGER;
            case MOB -> ParticleTypes.CRIT;
            case TREASURE -> ParticleTypes.WAX_ON;
        };
        level.sendParticles(player, sparkle, true, true, p.getX() + 0.5, p.getY() + 1.5, p.getZ() + 0.5, 3, 0.25, 0.3, 0.25, 0.02);
    }

    // ---------------------------------------------------------------- Diana's shop

    /** Diana: the Griffin pet (25,000 coins, during her ritual) and the Ancestral Spade (5,000 coins and 32 Gold Ingots). */
    static void shop(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Diana");
        CardMenu.open(player, p -> {
            long coins = Purse.get(p);
            long gold = count(p, "GOLD_INGOT");
            boolean on = ritual();
            ItemDefinition spadeDef = ItemRegistry.skyblock("ANCESTRAL_SPADE");
            List<CardView.Card> cards = new ArrayList<>();
            ItemStack griffin = SkyBlockPets.create("GRIFFIN", "COMMON", 0);
            cards.add(new CardView.Card("Griffin", on ? "A pet that calls up Mythological creatures from the burrows." : "Only while my ritual is on.",
                    0xEEEEEE, on && coins >= 25_000, on ? "Buy" : "Closed", griffin, "", List.of(Chip.price("coins", 25_000, coins)), -1, ""));
            cards.add(CardView.Card.offer(spadeDef == null ? ItemStack.EMPTY : SkyItems.create(spadeDef, 1),
                    List.of(Chip.price("coins", 5_000, coins), Chip.note(new ItemStack(Items.GOLD_INGOT), "32", gold >= 32))));
            cards.add(new CardView.Card("The ritual", on ? "Hold a spade in the Hub: dig the green burrows, follow the Echo (right-click)."
                    : "Elect me and I'll open the burrows.", on ? 0x55FF55 : 0xAAAAAA, false, "", new ItemStack(Items.FEATHER), "", List.of(), -1, ""));
            return new CardView("SKYBLOCK", "Diana", "Mythological Ritual", npc, List.of(Chip.balance("coins", coins)), cards,
                    on ? "The Mythological Ritual is on." : "The Mythological Ritual comes with my perk.", 1, 40);
        }, (p, card) -> {
            if (card == 0) {
                if (!ritual()) {
                    return "Only while my ritual is on.";
                }
                if (!Purse.trySpend(p, 25_000)) {
                    return "You need 25,000 coins.";
                }
                ItemStack pet = SkyBlockPets.create("GRIFFIN", "COMMON", 0);
                if (!p.getInventory().add(pet)) {
                    p.drop(pet, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
                Msg.playSound(p, SoundEvents.VILLAGER_YES, 0.8f, 1.1f);
            } else if (card == 1) {
                if (count(p, "GOLD_INGOT") < 32) {
                    return "You need 32 Gold Ingots.";
                }
                if (!Purse.trySpend(p, 5_000)) {
                    return "You need 5,000 coins.";
                }
                take(p, "GOLD_INGOT", 32);
                give(p, "ANCESTRAL_SPADE", 1);
                Msg.playSound(p, SoundEvents.VILLAGER_YES, 0.8f, 1.1f);
            }
            return "";
        });
    }

    private static long count(ServerPlayer player, String id) {
        long count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (matches(stack, id)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static boolean matches(ItemStack stack, String id) {
        String sky = SkyItems.skyblockId(stack);
        return id.equals(sky) || sky == null && id.equals("GOLD_INGOT") && stack.is(Items.GOLD_INGOT);
    }

    private static void take(ServerPlayer player, String id, int count) {
        int left = count;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (left > 0 && matches(stack, id)) {
                int n = Math.min(left, stack.getCount());
                stack.shrink(n);
                left -= n;
            }
        }
    }
}
