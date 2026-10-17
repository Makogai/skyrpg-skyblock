package com.skyrpg.skyblock.glacite;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.hub.mixin.DisplayAccess;
import com.skyrpg.hub.mixin.TextDisplayAccess;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.items.enchant.Books;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.mining.Hotm;
import com.skyrpg.skyblock.mining.SkyMining;
import com.skyrpg.skyblock.mixin.InteractionAccess;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Hypixel's Glacite Mineshafts: while you mine in the Glacite Tunnels, a 1 in 2,000 chance a block (a pity counter makes
 * it certain within 2,000 points: Mithril 2, Tungsten/Umber/Glacite 4, gemstones and Titanium 8) opens a Mineshaft portal
 * beside you for 30 seconds. Inside (a shaft of its own, in the {@code glacite_mineshafts} world): a tunnel with side
 * rooms whose walls are full of one kind of ore (16 kinds, the one you mined last likelier), 1 to 3 Frozen Corpses (Lapis
 * free; Umber and Tungsten need their keys; the Fairy shaft's Vanguard Corpses need a Skeleton Key) with Hypixel's loot,
 * Glacite Maniacs (only mining tools hurt them), and Suspicious Scrap now and then from the ore. Cold keeps building.
 * The exit takes you back to the Base Camp with a Glacite Powder bonus (1,000, +500 a scrap, +250 a corpse, +10 a point of
 * Cold). A shaft is cleared away once nobody's in it.
 */
public final class Mineshafts {
    public static final ResourceKey<Level> LEVEL = SkyMining.GLACITE_MINESHAFTS;
    static final String TAG = "skyrpg_mineshaft";
    static final String PORTAL = TAG + "_portal";
    static final String EXIT = TAG + "_exit";
    static final String CORPSE = TAG + "_corpse";
    public static final String MANIAC = TAG + "_maniac";
    private static final int SPACING = 512;
    private static final int SLOTS = 32;
    private static final int Y = 100;
    private static final int LENGTH = 60;
    private static final int PORTAL_TICKS = 30 * 20;
    private static final int EMPTY_TICKS = 60 * 20;
    private static final int PITY = 2000;

    /** One kind of mineshaft: its ore blocks. */
    enum Kind {
        TITANIUM(Blocks.POLISHED_DIORITE), TUNGSTEN(Blocks.COBBLESTONE, Blocks.CLAY), UMBER(Blocks.TERRACOTTA,
                Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN), Blocks.RED_SANDSTONE),
        JADE(glass(DyeColor.LIME)), AMBER(glass(DyeColor.ORANGE)), TOPAZ(glass(DyeColor.YELLOW)), SAPPHIRE(glass(DyeColor.LIGHT_BLUE)),
        AMETHYST(glass(DyeColor.PURPLE)), JASPER(glass(DyeColor.MAGENTA)), RUBY(glass(DyeColor.RED)), OPAL(glass(DyeColor.WHITE)),
        ONYX(glass(DyeColor.BLACK)), PERIDOT(glass(DyeColor.GREEN)), CITRINE(glass(DyeColor.BROWN)), AQUAMARINE(glass(DyeColor.BLUE)),
        FAIRY(glass(DyeColor.LIME), glass(DyeColor.ORANGE), glass(DyeColor.YELLOW), glass(DyeColor.LIGHT_BLUE), glass(DyeColor.PURPLE),
                glass(DyeColor.MAGENTA), glass(DyeColor.RED), glass(DyeColor.WHITE), glass(DyeColor.BLACK), glass(DyeColor.GREEN),
                glass(DyeColor.BROWN), glass(DyeColor.BLUE));

        final Block[] ores;

        Kind(Block... ores) {
            this.ores = ores;
        }

        String title() {
            return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
        }

        private static Block glass(DyeColor color) {
            return Blocks.STAINED_GLASS.pick(color);
        }
    }

    record Loot(String item, int count, double weight) {
    }

    record Corpse(String kind, String name, String key, int min, int max, List<String> armor, List<Loot> loot) {
    }

    /** A mineshaft: where, of which kind, who's been in, and what each found (for the leaving bonus). */
    static final class Shaft {
        final int slot;
        final Kind kind;
        final BlockPos origin;
        long emptySince = -1;
        final Map<UUID, Integer> scraps = new HashMap<>();
        final Map<UUID, Integer> corpses = new HashMap<>();
        final Map<UUID, Long> entered = new HashMap<>();
        final List<Mob> maniacs = new ArrayList<>();
        int corpsesPlaced;

        Shaft(int slot, Kind kind) {
            this.slot = slot;
            this.kind = kind;
            this.origin = new BlockPos(slot * SPACING, Y, 0);
        }
    }

    /** A portal out in the Tunnels: where it leads and when it closes. */
    record Portal(ServerLevel level, Vec3 pos, Kind kind, long closes, List<Entity> parts) {
    }

    /** Blocks mined toward the guaranteed mineshaft (counts down from 2,000). */
    static final AttachmentType<Integer> PITY_LEFT = AttachmentRegistry.create(SkyRpgSkyblock.id("mineshaft_pity"),
            builder -> builder.persistent(Codec.INT).copyOnDeath());

    private static Map<String, Corpse> corpses = Map.of();
    private static final Map<Integer, Shaft> SHAFTS = new HashMap<>();
    private static final Map<UUID, Portal> PORTALS = new HashMap<>();
    /** The kind each player mined last in the Tunnels (that kind is likelier). */
    private static final Map<UUID, Kind> LAST_KIND = new HashMap<>();
    private static final Set<UUID> HITTING = new HashSet<>();

    private Mineshafts() {
    }

    public static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("glacite_corpses"), "skyrpg/glacite", Mineshafts::load);
        SkyMining.MINED.register((player, state, zone) -> mined(player, state));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer server) || level.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (entity.entityTags().contains(PORTAL)) {
                enterPortal(server, entity);
                return InteractionResult.SUCCESS;
            }
            if (entity.entityTags().contains(EXIT)) {
                leave(server, true);
                return InteractionResult.SUCCESS;
            }
            if (entity.entityTags().contains(CORPSE)) {
                loot(server, entity);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(Mineshafts::tick);
        com.skyrpg.core.stats.Stats.registerSource(new com.skyrpg.core.stats.Stats.Source() {
            @Override
            public String name() {
                return "Mineshaft Mayhem";
            }

            @Override
            public void contribute(ServerPlayer player, com.skyrpg.core.stats.Stats.Contributor out) {
                Mayhem buff = MAYHEM.get(player.getUUID());
                if (buff == null || !player.level().dimension().equals(LEVEL)) {
                    return;
                }
                switch (buff) {
                    case FORTUNE -> out.add(com.skyrpg.core.stats.StatTypes.MINING_FORTUNE, 100);
                    case SPEED -> out.add(com.skyrpg.core.stats.StatTypes.MINING_SPEED, 200);
                    case COLD -> out.add(com.skyrpg.core.stats.StatTypes.COLD_RESISTANCE, 10);
                    default -> {
                    }
                }
            }
        });
        // Shafts don't outlast the server: everyone inside goes back to the Base Camp, and the shafts are cleared away.
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerLevel level = server.getLevel(LEVEL);
            if (level != null) {
                List.copyOf(level.players()).forEach(GlaciteTunnels::toBaseCamp);
                SHAFTS.values().forEach(shaft -> clear(level, shaft));
            }
            SHAFTS.clear();
            PORTALS.values().forEach(portal -> portal.parts().forEach(Entity::discard));
            PORTALS.clear();
        });
        // Glacite Maniacs: only a mining tool hurts them, 8% of their health a hit.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!entity.entityTags().contains(MANIAC) || HITTING.contains(entity.getUUID())) {
                return true;
            }
            if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player
                    && Hotm.isMiningTool(player.getMainHandItem()) && entity.level() instanceof ServerLevel level) {
                float left = entity.getHealth() - entity.getMaxHealth() * 0.08f;
                if (left > 0) {
                    entity.setHealth(left);
                    level.broadcastDamageEvent(entity, source);
                    Msg.playSound(player, SoundEvents.GLASS_BREAK, 0.6f, 1.4f);
                } else {
                    HITTING.add(entity.getUUID());
                    entity.hurtServer(level, source, Float.MAX_VALUE);
                    HITTING.remove(entity.getUUID());
                }
            } else if (source.getEntity() instanceof ServerPlayer player) {
                player.sendOverlayMessage(Component.literal("Glacite Maniacs only take damage from mining tools!").withStyle(ChatFormatting.RED));
            }
            return false;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity.entityTags().contains(MANIAC) && source.getEntity() instanceof ServerPlayer killer) {
                maniacDrops(killer, entity);
                com.skyrpg.skyblock.mining.Commissions.maniacSlain(killer);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("sbmineshaft")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("portal").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    openPortal(target, pickKind(target));
                    return 1;
                })))));
    }

    private static void load(Map<Identifier, JsonElement> files) {
        Map<String, Corpse> loaded = new HashMap<>();
        for (JsonElement file : files.values()) {
            if (!file.getAsJsonObject().has("corpses")) {
                continue;
            }
            file.getAsJsonObject().getAsJsonObject("corpses").entrySet().forEach(e -> {
                JsonObject c = e.getValue().getAsJsonObject();
                List<String> armor = new ArrayList<>();
                c.getAsJsonArray("armor").forEach(a -> armor.add(a.getAsString()));
                List<Loot> loot = new ArrayList<>();
                for (JsonElement l : c.getAsJsonArray("loot")) {
                    JsonObject o = l.getAsJsonObject();
                    loot.add(new Loot(o.get("item").getAsString(), o.get("count").getAsInt(), o.get("weight").getAsDouble()));
                }
                var rolls = c.getAsJsonArray("rolls");
                loaded.put(e.getKey(), new Corpse(e.getKey(), c.get("name").getAsString(), c.get("key").getAsString(), rolls.get(0).getAsInt(),
                        rolls.get(1).getAsInt(), List.copyOf(armor), List.copyOf(loot)));
            });
        }
        corpses = Map.copyOf(loaded);
        SkyRpgSkyblock.LOGGER.info("Loaded {} Frozen Corpse kinds", corpses.size());
    }

    // ---------------------------------------------------------------- finding a mineshaft

    /** A block mined in the Tunnels: maybe a portal opens. In a shaft: maybe Suspicious Scrap. */
    private static void mined(ServerPlayer player, BlockState state) {
        ServerLevel level = player.level();
        Kind kind = kindOf(state);
        if (level.dimension().equals(LEVEL)) {
            scrap(player, level);
            return;
        }
        if (!level.dimension().equals(SkyMining.GLACITE_TUNNELS) || PORTALS.containsKey(player.getUUID())) {
            return;
        }
        if (kind != null) {
            LAST_KIND.put(player.getUUID(), kind);
        }
        int points = pityPoints(state, kind);
        if (points == 0) {
            return;
        }
        int left = player.getAttachedOrElse(PITY_LEFT, PITY) - points;
        // Surveyor (a Glacite Powder perk) makes it likelier.
        double chance = (1 + Hotm.perkNumber(player, "surveyor", "stat") / 100) / 2000;
        boolean found = left <= 0 || player.getRandom().nextDouble() < chance;
        player.setAttached(PITY_LEFT, found ? PITY : left);
        if (found) {
            openPortal(player, pickKind(player));
        }
    }

    /** Toward the guaranteed mineshaft: Mithril 2, Glacite, Tungsten and Umber 4, gemstones and Titanium 8 (Hard Stone nothing). */
    private static int pityPoints(BlockState state, Kind kind) {
        if (state.is(Blocks.PACKED_ICE) || kind == Kind.TUNGSTEN || kind == Kind.UMBER) {
            return 4;
        }
        if (kind != null) {
            return 8;
        }
        return state.is(Blocks.STONE) ? 0 : 2;
    }

    private static Kind kindOf(BlockState state) {
        for (Kind kind : Kind.values()) {
            for (Block ore : kind.ores) {
                if (state.is(ore)) {
                    return kind;
                }
            }
        }
        return null;
    }

    /** Each kind is as likely as the others, except the one you mined last: a third of the time it's that. */
    private static Kind pickKind(ServerPlayer player) {
        Kind last = LAST_KIND.get(player.getUUID());
        RandomSource random = player.getRandom();
        // Vanguard Seeker (a Glacite Powder perk): the Fairy mineshaft, with its Vanguard Corpses, comes up more.
        double vanguard = Hotm.perkNumber(player, "vanguard_seeker", "stat") / 100;
        if (random.nextDouble() < vanguard / Kind.values().length) {
            return Kind.FAIRY;
        }
        if (last != null && random.nextInt(3) == 0) {
            return last;
        }
        return Kind.values()[random.nextInt(Kind.values().length)];
    }

    private static void openPortal(ServerPlayer player, Kind kind) {
        ServerLevel level = player.level();
        Vec3 at = player.position().add(player.getLookAngle().multiply(2, 0, 2));
        List<Entity> parts = new ArrayList<>();
        Interaction box = EntityTypes.INTERACTION.create(level, EntitySpawnReason.EVENT);
        if (box != null) {
            ((InteractionAccess) box).skyrpg$setWidth(1.4f);
            ((InteractionAccess) box).skyrpg$setHeight(2.4f);
            box.snapTo(at.x, at.y, at.z, 0, 0);
            box.addTag(PORTAL);
            level.addFreshEntity(box);
            parts.add(box);
        }
        // The portal itself: a nether-portal pane turned to face whoever found it.
        Display.BlockDisplay pane = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        pane.snapTo(at.x, at.y, at.z, player.getYRot(), 0);
        ((com.skyrpg.skyblock.mixin.BlockDisplayAccess) pane).skyrpg$setBlockState(Blocks.NETHER_PORTAL.defaultBlockState());
        ((DisplayAccess) pane).skyrpg$setTransformation(new Transformation(new Vector3f(-0.7f, 0, -0.5f), new Quaternionf(),
                new Vector3f(1.4f, 2.4f, 1), new Quaternionf()));
        pane.addTag(TAG);
        level.addFreshEntity(pane);
        parts.add(pane);
        parts.add(label(level, at.add(0, 2.7, 0), Component.literal("Mineshaft Portal").withColor(0xB98CFF)
                .append(Component.literal("\n" + kind.title() + " - right-click to enter").withStyle(ChatFormatting.GRAY))));
        Portal old = PORTALS.put(player.getUUID(), new Portal(level, at, kind, level.getGameTime() + PORTAL_TICKS, parts));
        if (old != null) {
            old.parts().forEach(Entity::discard);
        }
        Notice.of(Notice.Kind.MINING).tag("MINESHAFT!").color(0xB98CFF).send(player, Component.literal("A mineshaft portal spawned nearby!")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("MINESHAFT!").withColor(0xB98CFF)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("A portal spawned nearby").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.END_PORTAL_SPAWN, 0.5f, 1.4f);
    }

    private static Display.TextDisplay label(ServerLevel level, Vec3 at, Component text) {
        Display.TextDisplay display = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
        display.snapTo(at.x, at.y, at.z, 0, 0);
        ((TextDisplayAccess) display).skyrpg$setText(text);
        ((TextDisplayAccess) display).skyrpg$setBackgroundColor(0xA00B1018);
        ((DisplayAccess) display).skyrpg$setBillboard(Display.BillboardConstraints.CENTER);
        ((DisplayAccess) display).skyrpg$setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.8f, 0.8f, 0.8f),
                new Quaternionf()));
        display.addTag(TAG);
        level.addFreshEntity(display);
        return display;
    }

    private static void enterPortal(ServerPlayer player, Entity box) {
        for (Map.Entry<UUID, Portal> entry : PORTALS.entrySet()) {
            Portal portal = entry.getValue();
            if (portal.parts().contains(box)) {
                Shaft shaft = open(player.level().getServer(), portal.kind());
                if (shaft == null) {
                    player.sendSystemMessage(Component.literal("Every mineshaft is busy right now. Try again soon!").withStyle(ChatFormatting.RED));
                    return;
                }
                portal.parts().forEach(Entity::discard);
                PORTALS.remove(entry.getKey());
                enter(player, shaft);
                return;
            }
        }
    }

    // ---------------------------------------------------------------- the shafts

    private static Shaft open(MinecraftServer server, Kind kind) {
        ServerLevel level = server.getLevel(LEVEL);
        if (level == null) {
            return null;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!SHAFTS.containsKey(slot)) {
                Shaft shaft = new Shaft(slot, kind);
                SHAFTS.put(slot, shaft);
                build(level, shaft);
                return shaft;
            }
        }
        return null;
    }

    private static void enter(ServerPlayer player, Shaft shaft) {
        ServerLevel level = player.level().getServer().getLevel(LEVEL);
        if (level == null) {
            return;
        }
        if (shaft.entered.putIfAbsent(player.getUUID(), level.getGameTime()) == null) {
            com.skyrpg.skyblock.mining.Commissions.mineshaftEntered(player);
            firstEntry(player, level, shaft);
        }
        player.teleport(new net.minecraft.world.level.portal.TeleportTransition(level, Vec3.atBottomCenterOf(shaft.origin.offset(3, 0, 0)), Vec3.ZERO,
                -90, 0, Set.of(), net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        com.skyrpg.core.stats.Stats.refresh(player);
        Notice.of(Notice.Kind.MINING).tag("MINESHAFT").color(0xB98CFF).send(player, Component.literal("You entered a ")
                .append(Component.literal(shaft.kind.title() + " Mineshaft").withStyle(ChatFormatting.AQUA))
                .append(Component.literal(". Loot the corpses, mine the ore, mind the Cold!").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.PORTAL_TRAVEL, 0.3f, 1.6f);
    }

    /** Mineshaft Mayhem's buffs: one at random each time you enter. */
    enum Mayhem {
        SCRAP("+5% chance to find Suspicious Scrap"), FORTUNE("+100 Mining Fortune"), SPEED("+200 Mining Speed"),
        COLD("+10 Cold Resistance"), COOLDOWN("-25% Pickaxe Ability cooldown");

        final String text;

        Mayhem(String text) {
            this.text = text;
        }
    }

    private static final Map<UUID, Mayhem> MAYHEM = new HashMap<>();

    /** The Glacite Powder perks that act on entering: Dead Man's Chest (maybe one more corpse), Mineshaft Mayhem (a buff). */
    private static void firstEntry(ServerPlayer player, ServerLevel level, Shaft shaft) {
        if (player.getRandom().nextDouble() < Hotm.perkNumber(player, "dead_mans_chest", "stat") / 100) {
            addCorpse(level, shaft, player.getRandom());
            Notice.of(Notice.Kind.MINING).tag("DEAD MAN'S CHEST").color(0x55FFFF).send(player,
                    Component.literal("An extra Frozen Corpse is in this mineshaft!").withStyle(ChatFormatting.AQUA));
        }
        if (Hotm.state(player).active("mineshaft_mayhem") > 0) {
            Mayhem buff = Mayhem.values()[player.getRandom().nextInt(Mayhem.values().length)];
            MAYHEM.put(player.getUUID(), buff);
            Notice.of(Notice.Kind.MINING).tag("MINESHAFT MAYHEM").color(0xB98CFF).send(player,
                    Component.literal("You got " + buff.text + " in this mineshaft!").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** What the pickaxe ability's cooldown is multiplied by (Mineshaft Mayhem's cooldown buff: 0.75). */
    public static double cooldownFactor(ServerPlayer player) {
        return MAYHEM.get(player.getUUID()) == Mayhem.COOLDOWN && player.level().dimension().equals(LEVEL) ? 0.75 : 1;
    }

    /** Extra Cold vulnerability in a shaft: +0.25 for every 150 seconds since you went in. */
    static double vulnerability(ServerPlayer player) {
        Shaft shaft = shaftOf(player);
        Long since = shaft == null ? null : shaft.entered.get(player.getUUID());
        return since == null ? 0 : 0.25 * ((player.level().getGameTime() - since) / (150 * 20));
    }

    /** The shaft this player is in, or null. */
    static Shaft shaftOf(ServerPlayer player) {
        if (!player.level().dimension().equals(LEVEL)) {
            return null;
        }
        int slot = Math.floorDiv(player.getBlockX() + SPACING / 2, SPACING);
        return SHAFTS.get(slot);
    }

    /** Leaving (the exit, or freezing): back to the Base Camp, with the Glacite Powder bonus if it was the exit. */
    static void leave(ServerPlayer player, boolean bonus) {
        Shaft shaft = shaftOf(player);
        if (shaft != null && bonus) {
            int scraps = shaft.scraps.getOrDefault(player.getUUID(), 0);
            int looted = shaft.corpses.getOrDefault(player.getUUID(), 0);
            int cold = GlaciteTunnels.cold(player);
            int powder = 1000 + 500 * scraps + 250 * looted + 10 * cold;
            Hotm.givePowder(player, Hotm.GLACITE, powder);
            Notice.of(Notice.Kind.MINING).tag("MINESHAFT").color(0xB98CFF).panel()
                    .line(Component.literal(" +" + Msg.number(powder) + " Glacite Powder").withColor(Hotm.powderColor(Hotm.GLACITE)))
                    .line(Component.literal(" " + scraps + " Suspicious Scrap, " + looted + (looted == 1 ? " corpse, " : " corpses, ") + cold + " Cold")
                            .withStyle(ChatFormatting.GRAY))
                    .send(player, Component.literal("You left the mineshaft.").withStyle(ChatFormatting.AQUA));
        }
        GlaciteTunnels.toBaseCamp(player);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        // Portals sparkle, and close after 30 seconds.
        PORTALS.entrySet().removeIf(entry -> {
            Portal portal = entry.getValue();
            if (portal.level().getGameTime() > portal.closes()) {
                portal.parts().forEach(Entity::discard);
                return true;
            }
            portal.level().sendParticles(ParticleTypes.PORTAL, portal.pos().x, portal.pos().y + 1.2, portal.pos().z, 30, 0.5, 1.0, 0.5, 0.3);
            portal.level().sendParticles(ParticleTypes.END_ROD, portal.pos().x, portal.pos().y + 1.2, portal.pos().z, 4, 0.4, 0.8, 0.4, 0.01);
            return false;
        });
        ServerLevel level = server.getLevel(LEVEL);
        if (level == null) {
            return;
        }
        // Mayhem buffs end with the visit.
        MAYHEM.keySet().removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && player.level().dimension().equals(LEVEL)) {
                return false;
            }
            if (player != null) {
                com.skyrpg.core.stats.Stats.refresh(player);
            }
            return true;
        });
        // Someone in a shaft that isn't there any more (it was cleared, or the server restarted): back to the Base Camp.
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (shaftOf(player) == null) {
                GlaciteTunnels.toBaseCamp(player);
            }
        }
        // A shaft nobody's been in for a minute is cleared away.
        SHAFTS.entrySet().removeIf(entry -> {
            Shaft shaft = entry.getValue();
            boolean occupied = level.players().stream().anyMatch(p -> shaftOf(p) == shaft);
            if (occupied) {
                shaft.emptySince = -1;
                shaft.maniacs.removeIf(Entity::isRemoved);
                for (Mob mob : shaft.maniacs) {
                    if (mob.getTarget() == null || !mob.getTarget().isAlive()) {
                        ServerPlayer near = level.getNearestPlayer(mob, 16) instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator() ? p : null;
                        if (near != null) {
                            mob.setTarget(near);
                        }
                    }
                }
                return false;
            }
            if (shaft.emptySince < 0) {
                shaft.emptySince = level.getGameTime();
            }
            if (level.getGameTime() - shaft.emptySince > EMPTY_TICKS) {
                clear(level, shaft);
                return true;
            }
            return false;
        });
    }

    // ---------------------------------------------------------------- building one

    private static final Block[] WALLS = {Blocks.STONE, Blocks.PACKED_ICE, Blocks.STONE, Blocks.ANDESITE, Blocks.BLUE_ICE};

    /** A main tunnel with three side rooms, walls of rock thick with the shaft's ore, timber frames and lanterns. */
    private static void build(ServerLevel level, Shaft shaft) {
        RandomSource random = level.getRandom();
        BlockPos o = shaft.origin;
        int x0 = o.getX();
        // Load the chunks it's in.
        for (int x = x0 - 16; x <= x0 + LENGTH + 32; x += 16) {
            for (int z = -32; z <= 32; z += 16) {
                level.getChunk(x >> 4, z >> 4);
            }
        }
        List<int[]> rooms = new ArrayList<>();
        rooms.add(new int[] {x0 + 15, 1});
        rooms.add(new int[] {x0 + 30, -1});
        rooms.add(new int[] {x0 + 45, 1});
        // Solid rock all around first.
        for (int x = x0 - 2; x <= x0 + LENGTH + 2; x++) {
            for (int y = Y - 2; y <= Y + 7; y++) {
                for (int z = -14; z <= 14; z++) {
                    boolean edge = x == x0 - 2 || x == x0 + LENGTH + 2 || y == Y - 2 || y == Y + 7 || Math.abs(z) == 14;
                    set(level, x, y, z, edge ? Blocks.BEDROCK.defaultBlockState() : rock(shaft, random));
                }
            }
        }
        // The tunnel and the rooms.
        carve(level, x0, Y, -3, x0 + LENGTH, Y + 5, 3);
        for (int[] room : rooms) {
            int zFrom = room[1] > 0 ? 4 : -12;
            carve(level, room[0] - 3, Y, zFrom, room[0] + 3, Y + 4, zFrom + 8);
        }
        // Timber frames and lanterns every six blocks.
        for (int x = x0 + 4; x < x0 + LENGTH; x += 6) {
            for (int y = Y; y <= Y + 4; y++) {
                set(level, x, y, -3, Blocks.SPRUCE_LOG.defaultBlockState());
                set(level, x, y, 3, Blocks.SPRUCE_LOG.defaultBlockState());
            }
            for (int z = -3; z <= 3; z++) {
                set(level, x, Y + 5, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
            }
            set(level, x, Y + 4, 0, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }
        for (int[] room : rooms) {
            set(level, room[0], Y + 4, room[1] > 0 ? 8 : -8,
                    Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
        }
        // The exit at the start.
        for (int y = Y; y <= Y + 5; y++) {
            set(level, x0, y, 0, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING,
                    net.minecraft.core.Direction.EAST));
        }
        Interaction exit = EntityTypes.INTERACTION.create(level, EntitySpawnReason.EVENT);
        if (exit != null) {
            ((InteractionAccess) exit).skyrpg$setWidth(1.2f);
            ((InteractionAccess) exit).skyrpg$setHeight(2.2f);
            exit.snapTo(x0 + 0.5, Y, 0.5, 0, 0);
            exit.addTag(EXIT);
            exit.addTag(TAG);
            level.addFreshEntity(exit);
        }
        label(level, new Vec3(x0 + 0.9, Y + 2.6, 0.5), Component.literal("Exit the Mineshaft").withStyle(ChatFormatting.GREEN)
                .append(Component.literal("\nright-click").withStyle(ChatFormatting.GRAY)));
        // Corpses: one to three, in the rooms (a Fairy shaft's are Vanguards).
        int count = 1 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            addCorpse(level, shaft, random);
        }
        // Glacite Maniacs: three, at the far end and in the rooms.
        maniac(level, shaft, new Vec3(x0 + LENGTH - 3 + 0.5, Y, 0.5), random);
        maniac(level, shaft, new Vec3(rooms.get(0)[0] + 0.5, Y, 7.5), random);
        maniac(level, shaft, new Vec3(rooms.get(1)[0] + 0.5, Y, -7.5), random);
    }

    /** One more corpse, in the next free spot along the side rooms (Lapis half the time, else Umber or Tungsten). */
    private static void addCorpse(ServerLevel level, Shaft shaft, RandomSource random) {
        String kind = shaft.kind == Kind.FAIRY ? "VANGUARD" : random.nextInt(2) == 0 ? "LAPIS" : random.nextInt(2) == 0 ? "UMBER" : "TUNGSTEN";
        int i = shaft.corpsesPlaced++;
        int x = shaft.origin.getX() + 15 * (1 + i % 3);
        int side = i % 3 == 1 ? -1 : 1;
        corpse(level, new Vec3(x + 0.5 + (i / 3) * 2, Y, side * 10 + 0.5), kind);
    }

    private static BlockState rock(Shaft shaft, RandomSource random) {
        if (random.nextFloat() < 0.32f) {
            return shaft.kind.ores[random.nextInt(shaft.kind.ores.length)].defaultBlockState();
        }
        return WALLS[random.nextInt(WALLS.length)].defaultBlockState();
    }

    private static void carve(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                set(level, x, y1 - 1, z, Blocks.SNOW_BLOCK.defaultBlockState());
                for (int y = y1; y <= y2; y++) {
                    set(level, x, y, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void clear(ServerLevel level, Shaft shaft) {
        int x0 = shaft.origin.getX();
        for (Entity entity : level.getAllEntities()) {
            if (entity.entityTags().contains(TAG) && Math.abs(entity.getX() - x0 - LENGTH / 2.0) < SPACING / 2.0) {
                entity.discard();
            }
        }
        for (int x = x0 - 2; x <= x0 + LENGTH + 2; x++) {
            for (int y = Y - 2; y <= Y + 7; y++) {
                for (int z = -14; z <= 14; z++) {
                    set(level, x, y, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    // ---------------------------------------------------------------- corpses

    private static void corpse(ServerLevel level, Vec3 at, String kind) {
        Corpse corpse = corpses.get(kind);
        ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.EVENT);
        if (corpse == null || stand == null) {
            return;
        }
        stand.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setPermanentlyInvulnerable(true);
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < Math.min(slots.length, corpse.armor().size()); i++) {
            stand.setItemSlot(slots[i], armor(corpse.armor().get(i)));
        }
        stand.addTag(TAG);
        level.addFreshEntity(stand);
        // The click goes to a box around it (the Hub's rules leave armour stands alone).
        Interaction box = EntityTypes.INTERACTION.create(level, EntitySpawnReason.EVENT);
        if (box != null) {
            ((InteractionAccess) box).skyrpg$setWidth(0.9f);
            ((InteractionAccess) box).skyrpg$setHeight(2.0f);
            box.snapTo(at.x, at.y, at.z, 0, 0);
            box.addTag(TAG);
            box.addTag(CORPSE);
            box.addTag(CORPSE + "." + kind);
            level.addFreshEntity(box);
        }
        label(level, at.add(0, 2.3, 0), Component.literal(corpse.name()).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(corpse.key().isEmpty() ? "\nright-click to loot" : "\nneeds a " + keyName(corpse.key()))
                        .withStyle(ChatFormatting.GRAY)));
    }

    private static ItemStack armor(String spec) {
        String[] parts = spec.split("#");
        ItemDefinition def = ItemRegistry.skyblock(parts[0]);
        ItemStack stack;
        if (def != null) {
            stack = SkyItems.create(def, 1);
        } else {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(parts[0].toLowerCase(Locale.ROOT)));
            stack = new ItemStack(item == null ? Items.AIR : item);
        }
        if (parts.length > 1) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(Integer.parseInt(parts[1], 16)));
        }
        return stack;
    }

    private static String keyName(String key) {
        ItemDefinition def = ItemRegistry.skyblock(key);
        return def == null ? key : def.name();
    }

    private static void loot(ServerPlayer player, Entity box) {
        String kind = box.entityTags().stream().filter(t -> t.startsWith(CORPSE + ".")).map(t -> t.substring(CORPSE.length() + 1))
                .findFirst().orElse("");
        Corpse corpse = corpses.get(kind);
        if (corpse == null) {
            return;
        }
        if (!corpse.key().isEmpty()) {
            if (SkyStacks.count(player.getInventory(), corpse.key()) < 1) {
                player.sendOverlayMessage(Component.literal("You need a " + keyName(corpse.key()) + " to loot this corpse!").withStyle(ChatFormatting.RED));
                return;
            }
            SkyStacks.take(player.getInventory(), corpse.key(), 1);
        }
        RandomSource random = player.getRandom();
        int rolls = corpse.min() + random.nextInt(corpse.max() - corpse.min() + 1);
        // Gifts from the Departed (a Glacite Powder perk): a chance at one more.
        if (random.nextDouble() < Hotm.perkNumber(player, "gifts_from_the_departed", "stat") / 100) {
            rolls++;
        }
        double total = corpse.loot().stream().mapToDouble(Loot::weight).sum();
        // Each roll is one pick from the whole table; the same item twice is one line.
        Map<String, Integer> won = new java.util.LinkedHashMap<>();
        for (int i = 0; i < rolls; i++) {
            double pick = random.nextDouble() * total;
            for (Loot loot : corpse.loot()) {
                pick -= loot.weight();
                if (pick < 0) {
                    won.merge(loot.item(), loot.count(), Integer::sum);
                    break;
                }
            }
        }
        Notice.Panel panel = Notice.of(Notice.Kind.MINING).tag("CORPSE").color(0x55FFFF).panel();
        won.forEach((item, count) -> {
            ItemStack stack = stack(item, count);
            if (stack.isEmpty()) {
                return;
            }
            panel.line(Component.literal(" " + (count > 1 ? Msg.number(count) + "x " : "")).withStyle(ChatFormatting.GRAY).append(stack.getHoverName()));
            com.skyrpg.core.event.ItemGains.give(player, stack, com.skyrpg.core.event.ItemGains.Source.MINING);
            if (!stack.isEmpty()) {
                player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        });
        panel.send(player, Component.literal("You looted the " + corpse.name() + "!").withStyle(ChatFormatting.AQUA));
        Msg.playSound(player, SoundEvents.CHEST_OPEN, 0.8f, 1.2f);
        Shaft shaft = shaftOf(player);
        if (shaft != null) {
            shaft.corpses.merge(player.getUUID(), 1, Integer::sum);
        }
        com.skyrpg.skyblock.mining.Commissions.corpseLooted(player);
        if (won.containsKey("SUSPICIOUS_SCRAP")) {
            com.skyrpg.skyblock.mining.Commissions.scrapFound(player, won.get("SUSPICIOUS_SCRAP"));
        }
        // The body and its label go too.
        for (Entity near : player.level().getEntities(box, box.getBoundingBox().inflate(0.5, 3, 0.5))) {
            if ((near instanceof Display.TextDisplay || near instanceof ArmorStand) && near.entityTags().contains(TAG)) {
                near.discard();
            }
        }
        box.discard();
    }

    private static ItemStack stack(String item, int count) {
        if (item.startsWith("book:")) {
            String[] parts = item.split(":");
            return Books.create(parts[1], Integer.parseInt(parts[2]), count);
        }
        ItemDefinition def = ItemRegistry.skyblock(item);
        return def == null ? ItemStack.EMPTY : SkyItems.create(def, count);
    }

    // ---------------------------------------------------------------- scrap and Maniacs

    /** Ore mined in a shaft: Suspicious Scrap 1 in 200, half as likely after each one found. */
    private static void scrap(ServerPlayer player, ServerLevel level) {
        Shaft shaft = shaftOf(player);
        if (shaft == null) {
            return;
        }
        int found = shaft.scraps.getOrDefault(player.getUUID(), 0);
        // No Stone Unturned (a Glacite Powder perk) and Mineshaft Mayhem's scrap buff make it likelier.
        double bonus = Hotm.perkNumber(player, "no_stone_unturned", "stat") + (MAYHEM.get(player.getUUID()) == Mayhem.SCRAP ? 5 : 0);
        if (player.getRandom().nextDouble() >= (1 + bonus / 100) / (200 << Math.min(found, 10))) {
            return;
        }
        shaft.scraps.put(player.getUUID(), found + 1);
        com.skyrpg.skyblock.mining.Commissions.scrapFound(player, 1);
        ItemStack scrap = stack("SUSPICIOUS_SCRAP", 1);
        if (!scrap.isEmpty()) {
            com.skyrpg.core.event.ItemGains.give(player, scrap, com.skyrpg.core.event.ItemGains.Source.MINING);
        }
        Notice.rareDrop(player, Component.literal("Suspicious Scrap").withColor(0xFFAA00), 0xFFAA00);
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.5f);
    }

    private static final String[][] MANIACS = {
            {"minecraft:wolf", "Glacite Mutt", "173", "750000", "2500", "259", "57"},
            {"minecraft:stray", "Glacite Bowman", "174", "750000", "2550", "261", "58"},
            {"minecraft:husk", "Glacite Caver", "175", "800000", "2500", "262", "58"},
            {"minecraft:zombie", "Glacite Mage", "177", "825000", "2550", "265", "59"}};

    private static void maniac(ServerLevel level, Shaft shaft, Vec3 at, RandomSource random) {
        String[] m = MANIACS[random.nextInt(MANIACS.length)];
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(m[0]));
        if (!(type.create(level, EntitySpawnReason.EVENT) instanceof Mob mob)) {
            return;
        }
        mob.snapTo(at.x, at.y, at.z, random.nextFloat() * 360, 0);
        Zones.dressAs(mob, m[1], Integer.parseInt(m[2]), Double.parseDouble(m[3]), Double.parseDouble(m[4]), Double.parseDouble(m[5]));
        if (m[0].equals("minecraft:stray")) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        }
        mob.setPersistenceRequired();
        mob.addTag(TAG);
        mob.addTag(MANIAC);
        mob.addTag(Zones.FIGHTABLE_TAG);
        mob.addTag(MANIAC + ".coins." + m[6]);
        level.addFreshEntity(mob);
        shaft.maniacs.add(mob);
    }

    /** A Maniac's drops: 50 Glacite Powder, an Enchanted Glacite, and the rare ones. */
    private static void maniacDrops(ServerPlayer killer, Entity maniac) {
        RandomSource random = killer.getRandom();
        String coins = maniac.entityTags().stream().filter(t -> t.startsWith(MANIAC + ".coins.")).findFirst().orElse("");
        if (!coins.isEmpty()) {
            com.skyrpg.core.economy.Wallet.give(killer, Long.parseLong(coins.substring((MANIAC + ".coins.").length())));
        }
        Hotm.givePowder(killer, Hotm.GLACITE, 50);
        List<String> drops = new ArrayList<>(List.of("ENCHANTED_GLACITE"));
        for (String gem : List.of("CITRINE", "AQUAMARINE", "ONYX", "PERIDOT")) {
            if (random.nextInt(20) == 0) {
                drops.add("FINE_" + gem + "_GEM");
            }
        }
        if (random.nextInt(25) == 0) {
            drops.add("GLACITE_JEWEL");
        }
        if (random.nextInt(1000) == 0) {
            drops.add("GLACITE_SHARD");
        }
        for (String id : drops) {
            ItemStack stack = stack(id, 1);
            if (!stack.isEmpty()) {
                if (!id.equals("ENCHANTED_GLACITE")) {
                    Notice.rareDrop(killer, stack.getHoverName().copy(), 0x5555FF);
                }
                com.skyrpg.core.event.ItemGains.give(killer, stack, com.skyrpg.core.event.ItemGains.Source.COMBAT);
            }
        }
        killer.sendOverlayMessage(Component.literal("+50 Glacite Powder").withColor(Hotm.powderColor(Hotm.GLACITE)));
    }
}
