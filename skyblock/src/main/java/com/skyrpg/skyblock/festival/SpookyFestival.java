package com.skyrpg.skyblock.festival;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.combat.MobLevels;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.world.Mayor;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.hub.Hub;
import com.skyrpg.hub.mixin.DisplayAccess;
import com.skyrpg.hub.mixin.ItemDisplayAccess;
import com.skyrpg.hub.mixin.TextDisplayAccess;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.enchant.Books;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.accessory.AccessoryBag;
import com.skyrpg.skyblock.bestiary.Bestiary;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.pet.SkyBlockPets;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.time.CalendarEvents;
import com.skyrpg.skyblock.time.SkyBlockTime;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import com.skyrpg.skyblock.zone.Zones;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Hypixel's Spooky Festival (Autumn 29th to 31st, a real hour). Every mob killed anywhere in SkyBlock has a 10% chance to
 * drop Green Candy, a tenth of that Purple (more with the Candy accessories and Spooky armour); Spooky mobs come out at
 * night in the Graveyard and the Wilderness, Shielded (every hit does 1 damage); Trick or Treat Chests turn up where mobs
 * die. Candy from mobs is your Candy Score (Green 1, Purple 5), and when the festival ends everyone's bracket reward
 * (Wood 100 ... Emerald 5,000, and the festival's best) waits at the Fear Mongerer.
 *
 * <p>Decided where Hypixel's numbers don't carry over: its "Top 50 of all of SkyBlock" bracket is the server's best
 * score of the festival (at least the Emerald 5,000); Spooky mobs keep 10 to an area; a Trick or Treat Chest waits a
 * minute for its opener.
 */
public final class SpookyFestival {
    static final String NAME = "Spooky Festival";
    /** Shielded mobs: every hit does 1 damage (2 with the Ghoul Buster). */
    public static final String SHIELDED = "skyrpg_shielded";
    private static final String SPOOKY = "skyrpg_spooky";
    private static final String KIND = "skyrpg_spooky.";
    private static final String CHEST = "skyrpg_trick_or_treat";
    private static final String CHEST_OWNER = "skyrpg_tot_owner.";
    private static final int CHEST_LIFE = 60 * 20;
    private static final int MOBS_PER_AREA = 10;

    /** A Spooky mob: Hypixel's name, level, health, Combat XP and coins, its candy and Ectoplasm drops, its armour colour. */
    private record Spooky(String id, String name, EntityType<? extends Mob> type, int level, double health, long coins, double xp,
                          int greenMin, int greenMax, double ectoplasm, int color, double trickWeight) {
    }

    private static final List<Spooky> MOBS = List.of(
            new Spooky("crazy_witch", "Crazy Witch", EntityTypes.ZOMBIE, 102, 17, 34, 153, 1, 2, 0, 0x4A2466, 6.12),
            new Spooky("wraith", "Wraith", EntityTypes.ZOMBIE, 99, 15, 33, 148, 1, 2, 0.10, 0x2B3238, 5.10),
            new Spooky("scary_jerry", "Scary Jerry", EntityTypes.ZOMBIE, 95, 12, 31, 142, 1, 2, 0, 0xE0661A, 3.06),
            new Spooky("wither_gourd", "Wither Gourd", EntityTypes.WITHER_SKELETON, 94, 11, 31, 141, 0, 0, 0, 0x1C1C1C, 4.08),
            new Spooky("phantom_spirit", "Phantom Spirit", EntityTypes.ZOMBIE, 92, 10, 30, 138, 1, 2, 0.10, 0xD8DEE6, 3.57),
            new Spooky("trick_or_treater", "Trick or Treater", EntityTypes.ZOMBIE, 92, 10, 30, 138, 1, 3, 0, 0xFF8C1A, 3.06));

    /** Where Spooky mobs come out at night: the Graveyard's lawns and the Wilderness. */
    private static final List<AABB> AREAS = List.of(new AABB(-199, 62, -61, -142, 75, -12), new AABB(117, 29, -50, 250, 107, 83));

    /** The Candy Contest's brackets: the score each needs, its coins and Carnival Tickets (the last is the festival's best). */
    static final String[] BRACKETS = {"Wood", "Stone", "Iron", "Gold", "Diamond", "Emerald", "Top"};
    private static final int[] BRACKET_SCORES = {100, 1000, 1500, 1750, 2000, 5000, 5000};
    private static final ChatFormatting[] BRACKET_COLORS = {ChatFormatting.GOLD, ChatFormatting.GRAY, ChatFormatting.WHITE, ChatFormatting.YELLOW,
            ChatFormatting.AQUA, ChatFormatting.GREEN, ChatFormatting.LIGHT_PURPLE};
    private static final long[] BRACKET_COINS = {25_000, 50_000, 100_000, 200_000, 500_000, 1_000_000, 1_000_000};
    private static final int[] BRACKET_TICKETS = {5, 10, 15, 20, 25, 35, 50};

    /** One trick-or-treat roll: an item, how many, its chance (Hypixel's TREAT table). */
    private record Treat(String item, int min, int max, double chance) {
    }

    private static final List<Treat> TREATS = List.of(new Treat("GREEN_CANDY", 4, 8, 0.2604), new Treat("PURPLE_CANDY", 1, 4, 0.1771),
            new Treat("ECTOPLASM", 1, 4, 0.11), new Treat("PUMPKIN_BOMB", 1, 2, 0.0729), new Treat("PUMPKIN_GUTS", 1, 1, 0.0417),
            new Treat("CANDY_CORN", 1, 1, 0.0313), new Treat("ROCK_CANDY", 1, 1, 0.0208), new Treat("PET_ITEM_SPOOKY_CUPCAKE", 1, 1, 0.0167),
            new Treat("BAT_PERSON_TALISMAN", 1, 1, 0.0104), new Treat("book:vampirism:6", 1, 1, 0.0021), new Treat("CANDY_THE_FISH", 1, 1, 0.0021));

    // ---------------------------------------------------------------- the Candy Contest's book

    /** A player's candy from mobs this festival. */
    record Entry(String name, int green, int purple) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Entry::name),
                Codec.INT.fieldOf("green").forGetter(Entry::green),
                Codec.INT.fieldOf("purple").forGetter(Entry::purple)).apply(i, Entry::new));

        int score() {
            return green + 5 * purple;
        }
    }

    /** A bracket reward waiting to be claimed. */
    record Reward(int year, int score, int rank, int bracket) {
        static final Codec<Reward> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("year").forGetter(Reward::year),
                Codec.INT.fieldOf("score").forGetter(Reward::score),
                Codec.INT.fieldOf("rank").forGetter(Reward::rank),
                Codec.INT.fieldOf("bracket").forGetter(Reward::bracket)).apply(i, Reward::new));
    }

    static final class Book extends SavedData {
        static final Codec<Book> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("active", false).forGetter(b -> b.active),
                Codec.INT.optionalFieldOf("year", 0).forGetter(b -> b.year),
                Codec.unboundedMap(Codec.STRING, Entry.CODEC).optionalFieldOf("scores", Map.of()).forGetter(b -> b.scores),
                Codec.unboundedMap(Codec.STRING, Reward.CODEC.listOf()).optionalFieldOf("unclaimed", Map.of()).forGetter(b -> b.unclaimed))
                .apply(i, Book::new));
        static final SavedDataType<Book> TYPE = new SavedDataType<>(SkyRpgSkyblock.id("spooky_festival"), Book::new, CODEC, null);

        boolean active;
        int year;
        final Map<String, Entry> scores;
        final Map<String, List<Reward>> unclaimed;

        Book() {
            this(false, 0, Map.of(), Map.of());
        }

        Book(boolean active, int year, Map<String, Entry> scores, Map<String, List<Reward>> unclaimed) {
            this.active = active;
            this.year = year;
            this.scores = new HashMap<>(scores);
            this.unclaimed = new HashMap<>();
            unclaimed.forEach((k, v) -> this.unclaimed.put(k, new ArrayList<>(v)));
        }

        static Book of(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(TYPE);
        }

        /** Everyone's entry, best score first. */
        List<Map.Entry<String, Entry>> ranked() {
            return scores.entrySet().stream().sorted(Comparator.comparingInt((Map.Entry<String, Entry> e) -> e.getValue().score()).reversed())
                    .toList();
        }
    }

    /** A Trick or Treat Chest waiting for its opener: its parts, and when it goes. */
    private record Chest(UUID owner, List<Entity> parts, long until) {
    }

    private static final Map<UUID, Chest> CHESTS = new HashMap<>();

    private SpookyFestival() {
    }

    static boolean running() {
        return CalendarEvents.running(System.currentTimeMillis(), NAME);
    }

    static void init() {
        Actions.register("fear_mongerer", (player, params) -> open(player));
        IncomingDamage.registerOverride((target, source) -> {
            if (!target.entityTags().contains(SHIELDED)) {
                return Double.NaN;
            }
            boolean buster = source.getEntity() instanceof ServerPlayer p && "GHOUL_BUSTER".equals(SkyItems.skyblockId(p.getMainHandItem()));
            return (buster ? 2 : 1) / StatTypes.HEALTH_SCALE;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer player) || !(entity instanceof Mob mob) || entity instanceof Player
                    || player.isCreative() || !SkyBlockWorlds.isSkyBlock(player.level()) || !running()) {
                return;
            }
            killed(player, mob);
        });
        Identifier first = SkyRpgSkyblock.id("trick_or_treat_first");
        UseEntityCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseEntityCallback.EVENT.register(first, (player, level, hand, entity, hit) -> {
            if (!entity.entityTags().contains(CHEST)) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer server) {
                openChest(server, entity);
            }
            return InteractionResult.SUCCESS;
        });
        AttackEntityCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        AttackEntityCallback.EVENT.register(first, (player, level, hand, entity, hit) -> {
            if (!entity.entityTags().contains(CHEST)) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer server) {
                openChest(server, entity);
            }
            return InteractionResult.SUCCESS;
        });
        // After a restart: a chest nobody's waiting on is gone, and Spooky mobs outside the festival too.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            boolean orphan = entity.entityTags().contains(CHEST) && CHESTS.values().stream().noneMatch(c -> c.parts().contains(entity));
            if (orphan || entity.entityTags().contains(SPOOKY) && !running()) {
                level.getServer().execute(entity::discard);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(SpookyFestival::tick);
    }

    // ---------------------------------------------------------------- candy

    private static void killed(ServerPlayer player, Mob mob) {
        // Every kill: Green Candy at 10% (+ accessories, armour, Foxy), a tenth of it (and more for high-level mobs) Purple.
        double chance = 0.10 + candyBonus(player) + (Mayor.active("sweet_benevolence") ? 0.30 : 0);
        if (player.getRandom().nextDouble() < chance) {
            MobLevels.MobInfo info = mob.getAttached(MobLevels.INFO);
            int level = info == null ? 1 : info.level();
            double purple = 0.10 + 0.002 * level + (fullSet(player, "SPOOKY_") ? 0.05 : 0) + (fullSet(player, "BAT_PERSON_") ? 0.05 : 0);
            boolean isPurple = player.getRandom().nextDouble() < purple;
            candy(player, isPurple, 1);
        }
        Spooky kind = kindOf(mob);
        if (kind != null) {
            Wallet.give(player, kind.coins());
            int green = kind.greenMax() == 0 ? 0 : kind.greenMin() + player.getRandom().nextInt(kind.greenMax() - kind.greenMin() + 1);
            if (green > 0) {
                candy(player, false, green);
            }
            if (kind.greenMax() > 0 && player.getRandom().nextDouble() < 0.05) {
                candy(player, true, 1);
            }
            if (kind.ectoplasm() > 0 && player.getRandom().nextDouble() < kind.ectoplasm()) {
                give(player, "ECTOPLASM", 1);
            }
            if (kind.id().equals("wither_gourd")) {
                give(player, "PUMPKIN_BOMB", 2 + player.getRandom().nextInt(3));
            }
        }
        // Trick or Treat Chests: 1% where a mob dies, 3% for a Spooky mob.
        if (player.getRandom().nextDouble() < (kind != null ? 0.03 : 0.01)) {
            spawnChest(player, mob.position());
        }
    }

    /** The Candy accessories (the best one), +5% a piece of Spooky or Bat Person armour. */
    private static double candyBonus(ServerPlayer player) {
        double bonus = 0;
        String[] line = {"CANDY_RELIC", "CANDY_ARTIFACT", "CANDY_RING", "CANDY_TALISMAN"};
        double[] amount = {0.20, 0.15, 0.10, 0.05};
        for (int i = 0; i < line.length; i++) {
            if (AccessoryBag.has(player, line[i])) {
                bonus += amount[i];
                break;
            }
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            String id = SkyItems.skyblockId(player.getItemBySlot(slot));
            if (id != null && (id.startsWith("SPOOKY_") || id.startsWith("BAT_PERSON_"))) {
                bonus += 0.05;
            }
        }
        return bonus;
    }

    private static boolean fullSet(ServerPlayer player, String prefix) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            String id = SkyItems.skyblockId(player.getItemBySlot(slot));
            if (id == null || !id.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    /** Candy from a mob: into the inventory (and on into the Spooky Sack or Trick or Treat Bag), and onto the score. */
    private static void candy(ServerPlayer player, boolean purple, int count) {
        give(player, purple ? "PURPLE_CANDY" : "GREEN_CANDY", count);
        Book book = Book.of(player.level().getServer());
        String key = player.getUUID().toString();
        Entry was = book.scores.getOrDefault(key, new Entry(player.getName().getString(), 0, 0));
        book.scores.put(key, new Entry(player.getName().getString(), was.green() + (purple ? 0 : count), was.purple() + (purple ? count : 0)));
        book.setDirty();
        Msg.playSound(player, SoundEvents.ITEM_PICKUP, 0.5f, purple ? 0.7f : 1.4f);
    }

    private static Component give(ServerPlayer player, String id, int count) {
        ItemStack stack;
        if (id.startsWith("book:")) {
            String[] parts = id.split(":");
            stack = Books.create(parts[1], Integer.parseInt(parts[2]), 1);
        } else if (id.startsWith("pet:")) {
            String[] parts = id.split(":");
            stack = SkyBlockPets.create(parts[1], parts[2], 0);
        } else {
            ItemDefinition def = ItemRegistry.skyblock(id);
            stack = def == null ? ItemStack.EMPTY : SkyItems.create(def, count);
        }
        if (stack == null || stack.isEmpty()) {
            return Component.literal(id);
        }
        Component name = stack.getHoverName().copy();
        ItemGains.give(player, stack, ItemGains.Source.COMBAT);
        if (!stack.isEmpty()) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        return name;
    }

    // ---------------------------------------------------------------- Spooky mobs

    private static Spooky kindOf(Entity entity) {
        if (!entity.entityTags().contains(SPOOKY)) {
            return null;
        }
        for (Spooky kind : MOBS) {
            if (entity.entityTags().contains(KIND + kind.id())) {
                return kind;
            }
        }
        return null;
    }

    private static Mob summon(ServerLevel level, Spooky kind, Vec3 at, boolean persistent) {
        Mob mob = kind.type().create(level, EntitySpawnReason.EVENT);
        if (mob == null) {
            return null;
        }
        mob.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
        Zones.dressAs(mob, kind.name(), kind.level(), kind.health(), 50, kind.xp());
        mob.setItemSlot(EquipmentSlot.HEAD, head(kind));
        mob.setItemSlot(EquipmentSlot.CHEST, leather(Items.LEATHER_CHESTPLATE, kind.color()));
        mob.setItemSlot(EquipmentSlot.LEGS, leather(Items.LEATHER_LEGGINGS, kind.color()));
        mob.setItemSlot(EquipmentSlot.FEET, leather(Items.LEATHER_BOOTS, kind.color()));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            mob.setDropChance(slot, 0);
        }
        // Shielded means 1 damage a hit, exactly: no armour points to take it lower.
        net.minecraft.world.entity.ai.attributes.AttributeInstance armor = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        if (armor != null) {
            armor.setBaseValue(0);
        }
        mob.addTag(SPOOKY);
        mob.addTag(KIND + kind.id());
        mob.addTag(SHIELDED);
        mob.addTag(Zones.FIGHTABLE_TAG);
        if (persistent) {
            mob.setPersistenceRequired();
        }
        level.addFreshEntity(mob);
        return mob;
    }

    private static ItemStack head(Spooky kind) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        String texture = Bestiary.islands().stream().flatMap(i -> i.families().stream()).filter(f -> f.id().equals(kind.id()))
                .map(Bestiary.Family::texture).filter(t -> t != null && !t.isEmpty()).findFirst().orElse(null);
        if (texture == null) {
            return new ItemStack(Items.CARVED_PUMPKIN);
        }
        UUID id = UUID.nameUUIDFromBytes(("skyrpg_spooky:" + kind.id()).getBytes(StandardCharsets.UTF_8));
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(id, "",
                new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", texture))))));
        return head;
    }

    private static ItemStack leather(net.minecraft.world.item.Item item, int color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0xFF000000 | color));
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY);
        return stack;
    }

    private static Spooky pickTrick(ServerPlayer player) {
        double total = MOBS.stream().mapToDouble(Spooky::trickWeight).sum();
        double roll = player.getRandom().nextDouble() * total;
        for (Spooky kind : MOBS) {
            roll -= kind.trickWeight();
            if (roll < 0) {
                return kind;
            }
        }
        return MOBS.getFirst();
    }

    /** At night during the festival, Spooky mobs fill the Graveyard and the Wilderness while someone's near; at dawn they go. */
    private static void spawnSpooky(MinecraftServer server, boolean on) {
        ServerLevel hub = server.getLevel(Hub.LEVEL);
        if (hub == null) {
            return;
        }
        boolean night = on && !hub.isBrightOutside();
        for (AABB box : AREAS) {
            List<Mob> alive = hub.getEntitiesOfClass(Mob.class, box.inflate(16), m -> m.isAlive() && m.entityTags().contains(SPOOKY)
                    && !m.entityTags().contains("skyrpg_trick"));
            if (!night) {
                alive.stream().limit(3).forEach(Entity::discard);
                continue;
            }
            if (alive.size() >= MOBS_PER_AREA || hub.players().stream().noneMatch(p -> p.getBoundingBox().intersects(box.inflate(48)))) {
                continue;
            }
            for (int attempt = 0; attempt < 8; attempt++) {
                BlockPos spot = Zones.standingSpot(hub, box, hub.getRandom(), false);
                if (spot != null) {
                    summon(hub, MOBS.get(hub.getRandom().nextInt(MOBS.size())), Vec3.atBottomCenterOf(spot), true);
                    break;
                }
            }
        }
    }

    // ---------------------------------------------------------------- Trick or Treat Chests

    static void spawnChest(ServerPlayer owner, Vec3 at) {
        ServerLevel level = (ServerLevel) owner.level();
        Vec3 base = new Vec3(at.x, Math.floor(at.y), at.z);
        Interaction box = EntityTypes.INTERACTION.create(level, EntitySpawnReason.EVENT);
        Display.ItemDisplay chest = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        Display.TextDisplay label = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
        if (box == null) {
            return;
        }
        ((com.skyrpg.skyblock.mixin.InteractionAccess) box).skyrpg$setWidth(1f);
        ((com.skyrpg.skyblock.mixin.InteractionAccess) box).skyrpg$setHeight(1f);
        box.snapTo(base.x, base.y, base.z, 0, 0);
        float yaw = owner.getYRot() + 180;
        chest.snapTo(base.x, base.y + 0.5, base.z, yaw, 0);
        ((ItemDisplayAccess) chest).skyrpg$setItemStack(new ItemStack(Items.CHEST));
        label.snapTo(base.x, base.y + 1.35, base.z, 0, 0);
        ((TextDisplayAccess) label).skyrpg$setText(Component.literal("Trick or Treat Chest").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\n" + owner.getName().getString() + "'s · click to open").withStyle(ChatFormatting.GRAY)));
        ((TextDisplayAccess) label).skyrpg$setBackgroundColor(0xA0120C0F);
        ((DisplayAccess) label).skyrpg$setBillboard(Display.BillboardConstraints.CENTER);
        ((DisplayAccess) label).skyrpg$setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.7f, 0.7f, 0.7f),
                new Quaternionf()));
        List<Entity> parts = List.of(box, chest, label);
        CHESTS.put(box.getUUID(), new Chest(owner.getUUID(), parts, level.getServer().getTickCount() + CHEST_LIFE));
        for (Entity part : parts) {
            part.addTag(CHEST);
            part.addTag(CHEST_OWNER + owner.getUUID());
            level.addFreshEntity(part);
        }
        level.sendParticles(ParticleTypes.WITCH, base.x, base.y + 0.5, base.z, 20, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, base.x, base.y, base.z, SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 0.8f, 1.2f);
        owner.sendSystemMessage(Component.literal("TRICK OR TREAT! ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("A Trick or Treat Chest appeared where that mob died!").withStyle(ChatFormatting.YELLOW)));
    }

    private static void openChest(ServerPlayer player, Entity clicked) {
        Chest chest = CHESTS.get(clicked.getUUID());
        if (chest == null) {
            chest = CHESTS.values().stream().filter(c -> c.parts().contains(clicked)).findFirst().orElse(null);
        }
        if (chest == null) {
            clicked.discard();
            return;
        }
        if (!chest.owner().equals(player.getUUID())) {
            player.sendOverlayMessage(Component.literal("This isn't your Trick or Treat Chest!").withStyle(ChatFormatting.RED));
            return;
        }
        Chest opened = chest;
        CHESTS.values().removeIf(c -> c == opened);
        Vec3 at = chest.parts().getFirst().position();
        chest.parts().forEach(Entity::discard);
        ServerLevel level = (ServerLevel) player.level();
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CHEST_OPEN, SoundSource.PLAYERS, 1f, 1f);
        if (player.getRandom().nextDouble() < 0.25) {
            // TRICK: a Spooky mob jumps out.
            Spooky kind = pickTrick(player);
            Mob mob = summon(level, kind, at, false);
            if (mob != null) {
                mob.addTag("skyrpg_trick");
                mob.setTarget(player);
            }
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 25, 0.3, 0.4, 0.3, 0.02);
            player.sendSystemMessage(Component.literal("TRICK! ").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)
                    .append(Component.literal("A " + kind.name() + " jumped out of the chest!").withStyle(ChatFormatting.YELLOW)));
            Msg.playSound(player, SoundEvents.WITCH_CELEBRATE, 1f, 1f);
            return;
        }
        // TREAT: one to three rolls of the table.
        int rolls = 1 + player.getRandom().nextInt(3);
        MutableComponent line = Component.literal("TREAT! ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(Component.literal("You found ").withStyle(ChatFormatting.YELLOW));
        List<Treat> left = new ArrayList<>(TREATS);
        for (int r = 0; r < rolls; r++) {
            // each roll a different kind of loot
            double roll = player.getRandom().nextDouble() * left.stream().mapToDouble(Treat::chance).sum();
            Treat pick = left.getFirst();
            for (Treat treat : left) {
                roll -= treat.chance();
                if (roll < 0) {
                    pick = treat;
                    break;
                }
            }
            left.remove(pick);
            int count = pick.min() + player.getRandom().nextInt(pick.max() - pick.min() + 1);
            Component name = give(player, pick.item(), count);
            line.append(r == 0 ? Component.empty() : Component.literal(", ").withStyle(ChatFormatting.YELLOW)).append(name)
                    .append(Component.literal(count > 1 ? " x" + count : "").withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(line.append(Component.literal("!").withStyle(ChatFormatting.YELLOW)));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.5, at.z, 15, 0.4, 0.4, 0.4, 0);
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
    }

    // ---------------------------------------------------------------- the festival's start and end

    private static void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (!CHESTS.isEmpty()) {
            CHESTS.values().removeIf(chest -> {
                if (tick > chest.until() || chest.parts().stream().anyMatch(Entity::isRemoved)) {
                    chest.parts().forEach(Entity::discard);
                    return true;
                }
                return false;
            });
        }
        if (tick % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        CalendarEvents.Occurrence occurrence = CalendarEvents.occurrence(now, NAME);
        Book book = Book.of(server);
        if (occurrence != null && !book.active) {
            book.active = true;
            book.year = SkyBlockTime.at(occurrence.start()).year();
            book.scores.clear();
            book.setDirty();
            broadcast(server, Component.literal("SPOOKY FESTIVAL ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("has started! Mobs everywhere has a chance to drop Candy!").withStyle(ChatFormatting.YELLOW)));
        } else if (occurrence == null && book.active) {
            book.active = false;
            book.setDirty();
            broadcast(server, Component.literal("SPOOKY FESTIVAL ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    .append(Component.literal("The event has ended! The Fear Mongerer is selling cool items in the Hub for a limited time! "
                            + "Candy Contest results will arrive shortly.").withStyle(ChatFormatting.YELLOW)));
            finish(server, book);
        }
        if (tick % 40 == 0) {
            spawnSpooky(server, occurrence != null);
        }
        sidebar(server, book, occurrence, now);
    }

    private static void broadcast(MinecraftServer server, Component message) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (SkyBlockWorlds.isSkyBlock(player.level())) {
                player.sendSystemMessage(message);
            }
        }
    }

    /** The Candy Contest's results: everyone ranked, each one's best bracket waiting at the Fear Mongerer. */
    static void finish(MinecraftServer server, Book book) {
        List<Map.Entry<String, Entry>> ranked = book.ranked();
        for (int i = 0; i < ranked.size(); i++) {
            Entry entry = ranked.get(i).getValue();
            int rank = i + 1;
            int bracket = bracket(entry.score(), rank);
            ServerPlayer online = server.getPlayerList().getPlayer(UUID.fromString(ranked.get(i).getKey()));
            if (bracket >= 0) {
                book.unclaimed.computeIfAbsent(ranked.get(i).getKey(), k -> new ArrayList<>()).add(new Reward(book.year, entry.score(), rank, bracket));
            }
            if (online != null) {
                MutableComponent line = Component.literal("CANDY CONTEST ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("You placed ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("#" + rank).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" with a Candy Score of ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(Msg.number(entry.score())).withStyle(ChatFormatting.GOLD));
                if (bracket >= 0) {
                    line.append(Component.literal(": " + BRACKETS[bracket] + " bracket! ").withStyle(BRACKET_COLORS[bracket]))
                            .append(Component.literal("[Claim your rewards]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                                    .withClickEvent(new ClickEvent.RunCommand("/spookyfestival"))));
                } else {
                    line.append(Component.literal(". 100 gets you a reward next time!").withStyle(ChatFormatting.YELLOW));
                }
                online.sendSystemMessage(line);
            }
        }
        book.scores.clear();
        book.setDirty();
    }

    /** The best bracket this score reaches (-1 for none); the festival's #1 with 5,000+ gets the Top one. */
    static int bracket(int score, int rank) {
        if (rank == 1 && score >= BRACKET_SCORES[6]) {
            return 6;
        }
        int best = -1;
        for (int i = 0; i < 6; i++) {
            if (score >= BRACKET_SCORES[i]) {
                best = i;
            }
        }
        return best;
    }

    private static void sidebar(MinecraftServer server, Book book, CalendarEvents.Occurrence occurrence, long now) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String line = null;
            if (occurrence != null && SkyBlockWorlds.isSkyBlock(player.level())) {
                long left = Math.max(0, (occurrence.end() - now) / 1000);
                Entry entry = book.scores.get(player.getUUID().toString());
                int green = entry == null ? 0 : entry.green();
                int purple = entry == null ? 0 : entry.purple();
                line = "§6Spooky Festival §f" + left / 60 + ":" + String.format("%02d", left % 60) + "\n§fYour Candy:\n §a" + Msg.number(green)
                        + " Green§7, §5" + Msg.number(purple) + " Purple §7(" + Msg.number(green + 5L * purple) + " pts.)";
            }
            Festivals.sidebar(player, line);
        }
    }

    // ---------------------------------------------------------------- the Fear Mongerer

    /** Admin: a player's score this festival (testing the contest). */
    static void setScore(ServerPlayer player, int green, int purple) {
        Book book = Book.of(player.level().getServer());
        book.scores.put(player.getUUID().toString(), new Entry(player.getName().getString(), green, purple));
        book.setDirty();
    }

    static void open(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Fear Mongerer");
        Entity near = npc < 0 ? null : player.level().getEntity(npc);
        boolean atNpc = near != null && near.getName().getString().contains("Fear Mongerer");
        CardMenu.open(player, p -> view(p, atNpc ? npc : -1), (p, card) -> click(p, card, atNpc));
    }

    private static CardView view(ServerPlayer player, int npc) {
        Book book = Book.of(player.level().getServer());
        String key = player.getUUID().toString();
        List<CardView.Card> cards = new ArrayList<>();
        List<Map.Entry<String, Entry>> ranked = book.ranked();
        Entry mine = book.scores.get(key);
        int score = mine == null ? 0 : mine.score();
        int rank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            if (ranked.get(i).getKey().equals(key)) {
                rank = i + 1;
            }
        }
        // Your Candy Score and the next bracket.
        int next = -1;
        for (int i = 0; i < 6; i++) {
            if (score < BRACKET_SCORES[i]) {
                next = i;
                break;
            }
        }
        boolean on = running();
        // Rewards waiting come first.
        List<Reward> waiting = book.unclaimed.getOrDefault(key, List.of());
        for (Reward reward : waiting) {
            List<Chip> chips = new ArrayList<>();
            chips.add(Chip.balance("coins", BRACKET_COINS[reward.bracket()]));
            for (String id : rewardItems(reward.bracket())) {
                ItemStack icon = id.startsWith("book:") ? new ItemStack(Items.ENCHANTED_BOOK) : id.startsWith("pet:") ? new ItemStack(Items.BONE)
                        : item(id);
                chips.add(Chip.note(icon, id.startsWith("book:") ? "Vampirism VI" : id.startsWith("pet:") ? "Skeleton Horse"
                        : icon.getHoverName().getString(), true));
            }
            chips.add(Chip.note(item("CARNIVAL_TICKET"), BRACKET_TICKETS[reward.bracket()] + " Carnival Tickets", true));
            cards.add(new CardView.Card(BRACKETS[reward.bracket()] + " Bracket (Year " + reward.year() + ")",
                    "#" + reward.rank() + " with " + Msg.number(reward.score()) + " Candy Score.", 0x55FF55, true, "Claim",
                    new ItemStack(Items.CHEST), "", chips, -1, ""));
        }
        String detail = !on ? "Kill mobs during the Spooky Festival (Autumn 29th) to collect Candy."
                : mine == null ? "Kill mobs to collect Candy! Green is 1 point, Purple 5."
                : "#" + rank + " with " + Msg.number(mine.green()) + " Green and " + Msg.number(mine.purple()) + " Purple."
                + (next >= 0 ? " " + Msg.number(BRACKET_SCORES[next] - score) + " more for " + BRACKETS[next] + "." : " Emerald bracket!");
        cards.add(new CardView.Card("Candy Score: " + Msg.number(score), detail, 0xFF8C1A, false, "", item("GREEN_CANDY"), "",
                List.of(), next >= 0 && on ? Math.min(1f, score / (float) BRACKET_SCORES[next]) : -1, on ? "Year " + book.year : ""));
        // The shop.
        cards.add(new CardView.Card("Shop", "Candy for exclusively spooky items: talismans, Spooky armour, the Bat Firework...", 0xEEEEEE,
                true, "Browse", item("PURPLE_CANDY"), "", List.of(), -1, ""));
        // The leaderboard.
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(5, ranked.size()); i++) {
            Entry e = ranked.get(i).getValue();
            top.append(i == 0 ? "" : "  ").append("#").append(i + 1).append(" ").append(e.name()).append(" ").append(Msg.number(e.score()));
        }
        cards.add(CardView.Card.info("Candy Contest", ranked.isEmpty() ? "No candy yet this festival." : top.toString(), 0xB46CFF,
                new ItemStack(Items.JACK_O_LANTERN)));
        List<Chip> wallet = List.of(Chip.note(item("GREEN_CANDY"), Msg.number(Sacks.countAnywhere(player, "GREEN_CANDY")) + " Green", true),
                Chip.note(item("PURPLE_CANDY"), Msg.number(Sacks.countAnywhere(player, "PURPLE_CANDY")) + " Purple", true));
        return new CardView("SPOOKY", "Fear Mongerer", "Spooky Festival", npc, wallet, cards,
                "I accept Candy in exchange for exclusively spooky items!", 1, 44);
    }

    private static String click(ServerPlayer player, int card, boolean atNpc) {
        Book book = Book.of(player.level().getServer());
        List<Reward> waiting = book.unclaimed.get(player.getUUID().toString());
        int rewards = waiting == null ? 0 : waiting.size();
        if (card == rewards + 1) {
            if (!atNpc) {
                return "Visit the Fear Mongerer in the Hub to shop!";
            }
            com.google.gson.JsonObject params = new com.google.gson.JsonObject();
            params.addProperty("shop", "fear_mongerer");
            Actions.run("skyblock_shop", player, params);
            return null;
        }
        if (card >= rewards) {
            return "";
        }
        Reward reward = waiting.remove(card);
        if (waiting.isEmpty()) {
            book.unclaimed.remove(player.getUUID().toString());
        }
        book.setDirty();
        Wallet.give(player, BRACKET_COINS[reward.bracket()]);
        give(player, "CARNIVAL_TICKET", BRACKET_TICKETS[reward.bracket()]);
        for (String id : rewardItems(reward.bracket())) {
            if (id.equals("SPOOKY_PIE")) {
                pie(player, reward);
            } else {
                give(player, id, 1);
            }
        }
        player.sendSystemMessage(Component.literal("You claimed your ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(BRACKETS[reward.bracket()] + " Bracket").withStyle(BRACKET_COLORS[reward.bracket()]))
                .append(Component.literal(" rewards for the Year " + reward.year() + " Spooky Festival!").withStyle(ChatFormatting.GREEN)));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1f, 1f);
        return "";
    }

    /** The bracket's items besides coins and tickets: the pie always, then the disc, Vampirism VI, the mask, the horse. */
    private static List<String> rewardItems(int bracket) {
        List<String> items = new ArrayList<>();
        if (bracket >= 5) {
            items.add("pet:SKELETON_HORSE:LEGENDARY");
        }
        if (bracket >= 4) {
            items.add("WITCH_MASK");
        }
        if (bracket >= 2) {
            items.add("book:vampirism:6");
        }
        if (bracket >= 1) {
            items.add("SPOOKY_DISC");
        }
        items.add("SPOOKY_PIE");
        return items;
    }

    /** The Spooky Pie, stamped with the festival, your place and your score. */
    private static void pie(ServerPlayer player, Reward reward) {
        ItemDefinition def = ItemRegistry.skyblock("SPOOKY_PIE");
        if (def == null) {
            return;
        }
        ItemStack pie = SkyItems.create(def, 1);
        pie.set(DataComponents.CUSTOM_NAME, Component.literal("Spooky Pie (Year " + reward.year() + ")").withStyle(ChatFormatting.RED)
                .withStyle(style -> style.withItalic(false)));
        pie.set(DataComponents.LORE, new ItemLore(List.of(
                gray("Obtained during the " + SkyBlockTime.ordinal(reward.year()) + " Spooky Festival"),
                gray("during SkyBlock Year " + reward.year() + "."),
                Component.empty(),
                gray("Player: ").append(Component.literal(player.getName().getString()).withStyle(ChatFormatting.WHITE)),
                gray("Position: ").append(Component.literal("#" + reward.rank()).withStyle(ChatFormatting.YELLOW)),
                gray("Candy Score: ").append(Component.literal(Msg.number(reward.score())).withStyle(ChatFormatting.GOLD)))));
        ItemGains.give(player, pie, ItemGains.Source.OTHER);
        if (!pie.isEmpty()) {
            player.drop(pie, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    private static MutableComponent gray(String text) {
        return Component.literal(text).withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false));
    }

    private static ItemStack item(String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? new ItemStack(Items.SUGAR) : SkyItems.create(def, 1);
    }
}
