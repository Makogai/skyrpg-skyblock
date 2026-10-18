package com.skyrpg.skyblock.fishing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.google.common.collect.ImmutableMultimap;
import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.island.Islands;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.phys.Vec3;

/**
 * Hypixel's sea creatures ({@code skyrpg/skyblock_fishing/sea_creatures.json}, {@code tools/skyblock/import_sea_creatures.py}):
 * what comes up when a bite is a sea creature. Each has a Fishing level to unlock it, a weight among the others that can
 * come up there (its fluid, island and zone, and a condition like Dark Bait at night), Hypixel's level, health, damage,
 * look and drops. It surfaces at the bobber and is flung at its fisher; whoever kills it gets its coins, drops and
 * Fishing XP (none on a private island). It leaves if its fisher goes away, and after six minutes.
 */
public final class SkySeaCreatures {
    public static final String TAG = "skyrpg_sb_sea_creature";
    private static final String ID_TAG = TAG + ".id.";
    private static final String OWNER_TAG = TAG + ".owner.";
    private static final String BORN_TAG = TAG + ".born.";
    private static final String MOUNT_TAG = "skyrpg_sb_sea_creature_mount";
    /** Sea creatures one player can have out at once (more bites are fish). */
    private static final int MAX_PER_PLAYER = 20;
    private static final long LIFETIME_TICKS = 6 * 60 * 20;
    private static final double LEASH = 64;
    /** During the Fishing Festival: the share of water sea creatures that are sharks, and Shark Bait's extra (Hypixel's). */
    private static final double SHARK_CHANCE = 0.15;
    private static final double SHARK_BAIT = 0.20;
    /** The Spooky Festival: the share of water sea creatures that are spooky ones, and Spooky Bait's extra (Hypixel's). */
    private static final double SPOOKY_CHANCE = 0.15;
    private static final double SPOOKY_BAIT = 0.15;

    public record Drop(String item, double chance, int min, int max) {
    }

    public record Creature(String id, String name, String rarity, boolean lava, int fishingLevel, double weight, String dimension,
                           List<String> zones, String condition, int level, double health, double damage, double fishingXp, double combatXp,
                           long coins, String message, JsonObject look, List<Drop> drops) {
        boolean livesAt(ServerLevel level, String zone) {
            return (dimension.isEmpty() || level.dimension().identifier().toString().equals(dimension))
                    && (zones.isEmpty() || zones.contains(zone));
        }
    }

    private static List<Creature> creatures = List.of();

    private SkySeaCreatures() {
    }

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!entity.entityTags().contains(TAG) || !(entity.level() instanceof ServerLevel level)) {
                return;
            }
            if (entity.getVehicle() != null && entity.getVehicle().entityTags().contains(MOUNT_TAG)) {
                entity.getVehicle().discard();
            }
            Creature creature = of(entity);
            if (creature != null && source.getEntity() instanceof ServerPlayer killer && !killer.isCreative()) {
                rewards(killer, level, creature);
            }
        });
        // Fishing rods hit sea creatures twice as hard.
        IncomingDamage.registerHit((target, damage) -> target.entityTags().contains(TAG) && damage.getDirectEntity() instanceof ServerPlayer player
                && player.getMainHandItem().getItem() instanceof FishingRodItem ? 2 : 1);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 100 == 0) {
                sweep(server);
            }
        });
    }

    static void load(JsonObject json) {
        List<Creature> loaded = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("creatures")) {
            JsonObject c = element.getAsJsonObject();
            JsonObject where = c.has("where") ? c.getAsJsonObject("where") : new JsonObject();
            List<String> zones = new ArrayList<>();
            if (where.has("zones")) {
                where.getAsJsonArray("zones").forEach(z -> zones.add(z.getAsString()));
            }
            List<Drop> drops = new ArrayList<>();
            for (JsonElement d : c.getAsJsonArray("drops")) {
                JsonObject drop = d.getAsJsonObject();
                drops.add(new Drop(drop.get("item").getAsString(), drop.get("chance").getAsDouble(), drop.get("min").getAsInt(),
                        drop.get("max").getAsInt()));
            }
            loaded.add(new Creature(c.get("id").getAsString(), c.get("name").getAsString(), c.get("rarity").getAsString(),
                    c.get("fluid").getAsString().equals("lava"), c.get("fishing_level").getAsInt(), c.get("weight").getAsDouble(),
                    where.has("dimension") ? where.get("dimension").getAsString() : "", List.copyOf(zones),
                    c.has("condition") ? c.get("condition").getAsString() : "", c.get("level").getAsInt(), c.get("health").getAsDouble(),
                    c.get("damage").getAsDouble(), c.get("fishing_xp").getAsDouble(), c.get("combat_xp").getAsDouble(),
                    c.get("coins").getAsLong(), c.get("message").getAsString(), c.getAsJsonObject("look"), List.copyOf(drops)));
        }
        creatures = List.copyOf(loaded);
        SkyRpgSkyblock.LOGGER.info("Loaded {} sea creatures", creatures.size());
    }

    public static List<Creature> all() {
        return creatures;
    }

    /**
     * The sea creature this bite brings up, or null if none can come up here for this player: one the player's Fishing
     * level has unlocked, that lives in this fluid and place, by weight. Light, Dark and Whale Bait make the rare ones
     * (weight under 300) 1.25 times as likely; Carrot Bait makes 5% of them the Carrot King.
     */
    static Creature roll(ServerPlayer player, ServerLevel level, boolean lava, String zone, SkyFishing.Bait bait) {
        int fishing = SkillLevels.level(player, SkillLevels.FISHING);
        RandomSource random = player.getRandom();
        boolean night = !level.isBrightOutside();
        if (bait != null && bait.carrotKing() > 0 && random.nextDouble() < bait.carrotKing()) {
            for (Creature c : creatures) {
                if (c.condition().equals("carrot_bait") && fishing >= c.fishingLevel() && c.lava() == lava) {
                    return c;
                }
            }
        }
        // Marina's Fishing Festival: a share of the water's sea creatures are sharks (Shark Bait: more)
        if (!lava && com.skyrpg.skyblock.time.CalendarEvents.running("fishing_festival", System.currentTimeMillis()) != null
                && random.nextDouble() < SHARK_CHANCE + (bait != null && bait.id().equals("SHARK_BAIT") ? SHARK_BAIT : 0)) {
            List<Creature> sharks = creatures.stream().filter(c -> c.condition().equals("fishing_festival") && fishing >= c.fishingLevel()).toList();
            double sum = sharks.stream().mapToDouble(Creature::weight).sum();
            double at = random.nextDouble() * sum;
            for (Creature shark : sharks) {
                at -= shark.weight();
                if (at < 0) {
                    return shark;
                }
            }
        }
        boolean rareBoost = bait != null && (bait.rare().equals("always") || bait.rare().equals("day") && !night
                || bait.rare().equals("night") && night);
        // The Spooky Festival (the hour before and after too): a share of the water's sea creatures are spooky ones.
        if (!lava && com.skyrpg.skyblock.time.CalendarEvents.near(System.currentTimeMillis(), "Spooky Festival", 3_600_000L)
                && random.nextDouble() < Math.min(0.75, SPOOKY_CHANCE + (bait != null && bait.id().equals("SPOOKY_BAIT") ? SPOOKY_BAIT : 0))) {
            List<Creature> spooky = creatures.stream().filter(c -> c.condition().equals("spooky_festival") && fishing >= c.fishingLevel()).toList();
            double sum = spooky.stream().mapToDouble(c -> c.weight() * (rareBoost && c.weight() < 300 ? 1.25 : 1)).sum();
            double at = random.nextDouble() * sum;
            for (Creature c : spooky) {
                at -= c.weight() * (rareBoost && c.weight() < 300 ? 1.25 : 1);
                if (at < 0) {
                    return c;
                }
            }
        }
        List<Creature> pool = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        for (Creature c : creatures) {
            if (c.lava() != lava || fishing < c.fishingLevel() || c.weight() <= 0 || !c.livesAt(level, zone)) {
                continue;
            }
            if (c.condition().equals("dark_bait_night") && !(night && bait != null && bait.id().equals("DARK_BAIT"))) {
                continue;
            }
            if (c.condition().equals("carrot_bait") || c.condition().equals("fishing_festival") || c.condition().equals("spooky_festival")) {
                continue;
            }
            double weight = c.weight() * (rareBoost && c.weight() < 300 ? 1.25 : 1);
            pool.add(c);
            weights.add(weight);
            total += weight;
        }
        double pick = random.nextDouble() * total;
        for (int i = 0; i < pool.size(); i++) {
            pick -= weights.get(i);
            if (pick < 0) {
                return pool.get(i);
            }
        }
        return null;
    }

    /** True if this player can have another sea creature out. */
    static boolean room(ServerPlayer player) {
        String owner = OWNER_TAG + player.getUUID();
        int count = 0;
        for (Entity entity : player.level().getAllEntities()) {
            if (entity.entityTags().contains(owner) && entity.isAlive() && ++count >= MAX_PER_PLAYER) {
                return false;
            }
        }
        return true;
    }

    /** It comes up at the bobber, flung at its fisher, with Hypixel's catch message. */
    static void surface(Creature creature, FishingHook hook, ServerPlayer owner, boolean doubleHook) {
        ServerLevel level = owner.level();
        JsonObject look = creature.look();
        Mob mob = create(level, look.get("entity").getAsString());
        if (mob == null) {
            return;
        }
        RandomSource random = owner.getRandom();
        mob.snapTo(hook.getX(), hook.getY(), hook.getZ(), random.nextFloat() * 360, 0);
        Zones.dressAs(mob, creature.name(), creature.level(), creature.health(), creature.damage(), creature.combatXp());
        dress(mob, look);
        mob.addTag(TAG);
        mob.addTag(ID_TAG + creature.id());
        mob.addTag(OWNER_TAG + owner.getUUID());
        mob.addTag(BORN_TAG + level.getGameTime());
        mob.addTag(Zones.FIGHTABLE_TAG);
        mob.setPersistenceRequired();
        if (creature.lava()) {
            mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        }
        Vec3 pull = owner.position().subtract(hook.position());
        mob.setDeltaMovement(pull.x * 0.1, pull.y * 0.1 + Math.sqrt(pull.length()) * 0.08, pull.z * 0.1);
        level.addFreshEntity(mob);
        if (look.has("mount")) {
            Mob mount = create(level, look.get("mount").getAsString());
            if (mount != null) {
                mount.snapTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), 0);
                mount.setPermanentlyInvulnerable(true);
                mount.addTag(MOUNT_TAG);
                mount.addTag(com.skyrpg.core.world.MobTags.NO_SUNBURN);
                mount.setPersistenceRequired();
                mount.setDeltaMovement(mob.getDeltaMovement());
                level.addFreshEntity(mount);
                mob.startRiding(mount, true, true);
            }
        }
        if (mob instanceof net.minecraft.world.entity.monster.Enemy || mob instanceof net.minecraft.world.entity.animal.golem.IronGolem
                || mob instanceof net.minecraft.world.entity.NeutralMob) {
            mob.setTarget(owner);
        }
        int color = SkyFishing.rarityColor(creature.rarity());
        Notice.of(Notice.Kind.FISHING).tag(doubleHook ? "DOUBLE HOOK!" : "SEA CREATURE").send(owner,
                Component.literal(creature.message()).withColor(color));
        boolean big = creature.rarity().equals("LEGENDARY") || creature.rarity().equals("MYTHIC");
        Msg.playSound(owner, big ? SoundEvents.WITHER_SPAWN : SoundEvents.PLAYER_SPLASH_HIGH_SPEED, big ? 0.5f : 0.8f, big ? 1.4f : 1.0f);
    }

    private static Mob create(ServerLevel level, String type) {
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(type));
        return entityType.create(level, EntitySpawnReason.EVENT) instanceof Mob mob ? mob : null;
    }

    /** Hypixel's look: armour, a weapon, a head with its skin, a baby, the Killer Bunny. Nothing of it drops. */
    private static void dress(Mob mob, JsonObject look) {
        for (Map.Entry<String, EquipmentSlot> slot : Map.of("hand", EquipmentSlot.MAINHAND, "head", EquipmentSlot.HEAD,
                "chest", EquipmentSlot.CHEST, "legs", EquipmentSlot.LEGS, "feet", EquipmentSlot.FEET).entrySet()) {
            if (look.has(slot.getKey())) {
                mob.setItemSlot(slot.getValue(), equipment(look.getAsJsonObject(slot.getKey())));
                mob.setDropChance(slot.getValue(), 0);
            }
        }
        if (look.has("baby") && mob instanceof Zombie zombie) {
            zombie.setBaby(true);
        }
        if (look.has("variant") && mob instanceof Rabbit rabbit) {
            rabbit.setComponent(DataComponents.RABBIT_VARIANT, Rabbit.Variant.EVIL);
        }
    }

    private static ItemStack equipment(JsonObject spec) {
        if (spec.has("skin")) {
            ItemStack head = new ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
            Property textures = new Property("textures", spec.get("skin").getAsString(), null);
            UUID uuid = UUID.nameUUIDFromBytes(spec.get("skin").getAsString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(
                    new GameProfile(uuid, "", new PropertyMap(ImmutableMultimap.of("textures", textures)))));
            return head;
        }
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(spec.get("item").getAsString()));
        ItemStack stack = new ItemStack(item);
        if (spec.has("color")) {
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(Integer.parseInt(spec.get("color").getAsString(), 16)));
        }
        return stack;
    }

    /** The sea creature this entity is, or null. */
    public static Creature of(Entity entity) {
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(ID_TAG)) {
                String id = tag.substring(ID_TAG.length());
                for (Creature c : creatures) {
                    if (c.id().equals(id)) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    /** Its coins, drops (rare ones announced) and Fishing XP, for its killer. */
    private static void rewards(ServerPlayer killer, ServerLevel level, Creature creature) {
        if (creature.coins() > 0) {
            Wallet.give(killer, creature.coins());
        }
        RandomSource random = killer.getRandom();
        for (Drop drop : creature.drops()) {
            if (random.nextDouble() >= drop.chance()) {
                continue;
            }
            int count = drop.min() + (drop.max() > drop.min() ? random.nextInt(drop.max() - drop.min() + 1) : 0);
            ItemStack stack = SkyFishing.stack(drop.item(), count);
            if (stack.isEmpty()) {
                continue;
            }
            if (drop.chance() <= 0.05) {
                Notice.rareDrop(killer, stack.getHoverName().copy().append(Component.literal(String.format(Locale.ROOT, " (%s%%)",
                        trim(drop.chance() * 100))).withStyle(ChatFormatting.AQUA)), SkyFishing.rarityColor(stack));
                Msg.playSound(killer, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.6f);
            }
            com.skyrpg.core.event.ItemGains.give(killer, stack, com.skyrpg.core.event.ItemGains.Source.COMBAT);
            if (!stack.isEmpty()) {
                killer.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        // Hypixel: sea creatures killed on a private island give no Fishing XP.
        if (!Islands.isIslands(level)) {
            SkillLevels.giveXp(killer, SkillLevels.FISHING, creature.fishingXp());
        }
    }

    private static String trim(double percent) {
        return percent >= 1 ? String.format(Locale.ROOT, "%.0f", percent) : String.format(Locale.ROOT, "%.2g", percent);
    }

    /** Sea creatures leave when their fisher logs off, goes to another place or far away, or after six minutes. */
    private static void sweep(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (!SkyBlockWorlds.isSkyBlock(level)) {
                continue;
            }
            List<Entity> leaving = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity.entityTags().contains(MOUNT_TAG) && entity.getPassengers().isEmpty()) {
                    leaving.add(entity);
                } else if (entity.entityTags().contains(TAG) && !stays(server, level, entity)) {
                    leaving.add(entity);
                }
            }
            for (Entity entity : leaving) {
                if (entity.getVehicle() != null && entity.getVehicle().entityTags().contains(MOUNT_TAG)) {
                    entity.getVehicle().discard();
                }
                entity.discard();
            }
        }
    }

    private static boolean stays(MinecraftServer server, ServerLevel level, Entity entity) {
        UUID owner = null;
        long born = 0;
        for (String tag : entity.entityTags()) {
            try {
                if (tag.startsWith(OWNER_TAG)) {
                    owner = UUID.fromString(tag.substring(OWNER_TAG.length()));
                } else if (tag.startsWith(BORN_TAG)) {
                    born = Long.parseLong(tag.substring(BORN_TAG.length()));
                }
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        ServerPlayer fisher = owner == null ? null : server.getPlayerList().getPlayer(owner);
        return fisher != null && fisher.level() == level && fisher.distanceToSqr(entity) < LEASH * LEASH
                && level.getGameTime() - born < LIFETIME_TICKS;
    }
}
