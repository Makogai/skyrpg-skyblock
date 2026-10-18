package com.skyrpg.skyblock.garden;

import com.skyrpg.core.economy.Wallet;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.skyblock.profile.Profile;
import com.skyrpg.skyblock.profile.Profiles;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Garden pests (Hypixel's, from Garden V): breaking a Garden crop while the pest cooldown (5 minutes) is over has a
 * 0.2% chance to bring that crop's pest (Fly for Wheat, Cricket for Carrots ... Beetle for Nether Wart, each unlocking at
 * its Garden level). Pests hover over your plots with 600 health, glowing. Only a vacuum hurts them (hold right-click:
 * its damage a second to the pest you look at, within 10 blocks). Each one is worth 1,000 coins, 1,500 Farming XP and an
 * enchanted crop of its kind (one more per 50 Farming Fortune). With four or more alive (more with Bonus Pest Chance:
 * one more per 100), your Farming Fortune in the Garden drops: -5%, -15%, -30%, -50%, then -75%. Pest Repellent (MAX)
 * halves (quarters) the chance for an hour. Pests killed are Pesthunter Phillip's currency.
 */
final class Pests {
    static final String TAG = "skyrpg_garden_pest";
    private static final String OWNER = TAG + "_owner:";
    private static final long COOLDOWN_MS = 5 * 60 * 1000L;
    private static final double CHANCE = 0.002;
    private static final double HEALTH = 600;
    private static final int[] PENALTY = {5, 15, 30, 50, 75};

    /** A pest: name, its crop, the Garden level it needs, its look. */
    record Kind(String name, String crop, int level, EntityType<? extends Mob> type) {
    }

    static final List<Kind> KINDS = List.of(
            new Kind("Fly", "wheat", 5, EntityTypes.ENDERMITE), new Kind("Cricket", "carrot", 5, EntityTypes.SILVERFISH),
            new Kind("Locust", "potato", 5, EntityTypes.ENDERMITE), new Kind("Rat", "pumpkin", 5, EntityTypes.SILVERFISH),
            new Kind("Mosquito", "sugar_cane", 5, EntityTypes.ENDERMITE), new Kind("Earthworm", "melon", 6, EntityTypes.SILVERFISH),
            new Kind("Mite", "cactus", 7, EntityTypes.ENDERMITE), new Kind("Moth", "cocoa", 8, EntityTypes.ENDERMITE),
            new Kind("Slug", "mushroom", 9, EntityTypes.SILVERFISH), new Kind("Beetle", "nether_wart", 10, EntityTypes.ENDERMITE));

    private static final Map<String, String> ENCHANTED = Map.of("wheat", "ENCHANTED_WHEAT", "carrot", "ENCHANTED_CARROT", "potato",
            "ENCHANTED_POTATO", "pumpkin", "ENCHANTED_PUMPKIN", "sugar_cane", "ENCHANTED_SUGAR", "melon", "ENCHANTED_MELON", "cactus",
            "ENCHANTED_CACTUS_GREEN", "cocoa", "ENCHANTED_COCOA", "mushroom", "ENCHANTED_RED_MUSHROOM", "nether_wart", "ENCHANTED_NETHER_STALK");

    /** When each profile's pest cooldown ends, and its repellent (until, factor). */
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final Map<UUID, long[]> REPELLENT = new HashMap<>();
    private static final ThreadLocal<Boolean> COUNTING = ThreadLocal.withInitial(() -> false);

    private Pests() {
    }

    static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !entity.entityTags().contains(TAG));
        ServerTickEvents.END_SERVER_TICK.register(Pests::tick);
        Abilities.register("vacuum", (player, ability) -> vacuum(player));
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(player instanceof ServerPlayer server) || level.isClientSide() || !Garden.isGarden(level)) {
                return InteractionResult.PASS;
            }
            ItemStack held = player.getItemInHand(hand);
            ItemDefinition def = SkyItems.definition(held);
            if (def == null || def.skyblockId() == null || !def.skyblockId().startsWith("PEST_REPELLENT")) {
                return InteractionResult.PASS;
            }
            Profile profile = Profiles.current(server);
            if (profile == null) {
                return InteractionResult.PASS;
            }
            boolean max = def.skyblockId().equals("PEST_REPELLENT_MAX");
            REPELLENT.put(profile.id(), new long[] {System.currentTimeMillis() + 60 * 60 * 1000L, max ? 4 : 2});
            held.shrink(1);
            server.sendSystemMessage(Component.literal("Pests are " + (max ? "4" : "2") + "x less likely to show up for an hour.")
                    .withStyle(ChatFormatting.GREEN));
            Msg.playSound(server, SoundEvents.BREWING_STAND_BREW, 1f, 1.2f);
            return InteractionResult.SUCCESS;
        });
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Pests";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                if (COUNTING.get() || !Garden.isGarden(player.level())) {
                    return;
                }
                Profile owner = Garden.ownerAt(player.blockPosition());
                if (owner == null) {
                    return;
                }
                int alive = count((ServerLevel) player.level(), owner);
                COUNTING.set(true);
                try {
                    int free = 4 + (int) (Stats.get(player, StatTypes.BONUS_PEST_CHANCE) / 100);
                    if (alive >= free) {
                        double fortune = Stats.get(player, StatTypes.FARMING_FORTUNE);
                        out.add(StatTypes.FARMING_FORTUNE, -fortune * PENALTY[Math.min(PENALTY.length - 1, alive - free)] / 100.0);
                    }
                } finally {
                    COUNTING.set(false);
                }
            }
        });
    }

    /** The pests alive in a Garden. */
    static int count(ServerLevel level, Profile owner) {
        BlockPos c = Garden.center(owner);
        AABB box = new AABB(c.getX() - Garden.HALF, c.getY() - 20, c.getZ() - Garden.HALF, c.getX() + Garden.HALF, c.getY() + 60,
                c.getZ() + Garden.HALF);
        return level.getEntitiesOfClass(Mob.class, box, m -> m.entityTags().contains(OWNER + owner.id())).size();
    }

    /** A Garden crop was harvested: maybe a pest. */
    static void harvested(ServerLevel level, ServerPlayer player, BlockPos pos, Garden.Crop crop) {
        Profile owner = Garden.ownerAt(pos);
        if (owner == null) {
            return;
        }
        GardenData.State state = Garden.state(level.getServer(), owner);
        int gardenLevel = Garden.level(state);
        Sprays.Spray spray = Sprays.at(pos);
        Kind kind = spray != null ? sprayed(spray, gardenLevel, player.getRandom()) : KINDS.stream().filter(k -> k.crop().equals(crop.key())).findFirst().orElse(null);
        long now = System.currentTimeMillis();
        if (kind == null || gardenLevel < kind.level() || now < COOLDOWN.getOrDefault(owner.id(), 0L)) {
            return;
        }
        long[] repellent = REPELLENT.get(owner.id());
        double bonus = Stats.get(player, StatTypes.BONUS_PEST_CHANCE) + (spray == null ? 0 : spray.juicy() ? 50 : 25);
        double sprayed = spray == null ? 1 : spray.juicy() ? 2 : 1.5;
        double chance = CHANCE * sprayed * (1 + bonus / 100) / (repellent != null && now < repellent[0] ? repellent[1] : 1);
        if (player.getRandom().nextDouble() >= chance || count(level, owner) >= 8) {
            return;
        }
        COOLDOWN.put(owner.id(), now + COOLDOWN_MS);
        spawn(level, owner, kind, pos);
        player.sendSystemMessage(Component.literal("A " + kind.name() + " has appeared in " + Garden.areaName(pos) + "!")
                .withStyle(ChatFormatting.GOLD));
        Msg.playSound(player, SoundEvents.SILVERFISH_AMBIENT, 1f, 0.8f);
    }

    /** In a sprayed plot any pest the Garden is ready for can come, its material's far likelier (+1,000 weight against 100). */
    private static Kind sprayed(Sprays.Spray spray, int gardenLevel, net.minecraft.util.RandomSource random) {
        Sprays.Material material = Sprays.MATERIALS.stream().filter(m -> m.item().equals(spray.material())).findFirst().orElse(null);
        List<Kind> ready = KINDS.stream().filter(k -> gardenLevel >= k.level()).toList();
        int total = 0;
        for (Kind k : ready) {
            total += material != null && material.pests().contains(k.name()) ? 1_100 : 100;
        }
        int roll = random.nextInt(Math.max(1, total));
        for (Kind k : ready) {
            roll -= material != null && material.pests().contains(k.name()) ? 1_100 : 100;
            if (roll < 0) {
                return k;
            }
        }
        return ready.isEmpty() ? null : ready.get(0);
    }

    static Mob spawn(ServerLevel level, Profile owner, Kind kind, BlockPos near) {
        Mob pest = kind.type().create(level, EntitySpawnReason.EVENT);
        if (pest == null) {
            return null;
        }
        var random = level.getRandom();
        pest.snapTo(near.getX() + random.nextInt(9) - 4 + 0.5, near.getY() + 1.5, near.getZ() + random.nextInt(9) - 4 + 0.5, 0, 0);
        pest.setNoAi(true);
        pest.setNoGravity(true);
        pest.setPersistenceRequired();
        pest.setGlowingTag(true);
        var health = pest.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(HEALTH / com.skyrpg.core.stats.StatTypes.HEALTH_SCALE);
        }
        pest.setHealth(pest.getMaxHealth());
        pest.setCustomName(Component.literal(kind.name()).withStyle(ChatFormatting.GOLD));
        pest.setCustomNameVisible(true);
        pest.addTag(TAG);
        pest.addTag(OWNER + owner.id());
        pest.addTag(TAG + "_kind:" + kind.name());
        level.addFreshEntity(pest);
        return pest;
    }

    /** Pests drift over the plots. */
    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        ServerLevel level = Garden.level(server);
        if (level == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            for (Mob pest : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64), m -> m.entityTags().contains(TAG))) {
                var random = pest.getRandom();
                Vec3 to = pest.position().add(random.nextDouble() - 0.5, (random.nextDouble() - 0.5) * 0.4, random.nextDouble() - 0.5);
                if (to.y < Garden.GROUND + 1.5) {
                    to = new Vec3(to.x, Garden.GROUND + 1.5, to.z);
                }
                if (to.y > Garden.GROUND + 4) {
                    to = new Vec3(to.x, Garden.GROUND + 4, to.z);
                }
                pest.teleportTo(to.x, to.y, to.z);
            }
        }
    }

    /** A vacuum's pull (about every 4 ticks while right-click is held): its damage a second to the pest in front of you. */
    private static boolean vacuum(ServerPlayer player) {
        // The Rift's Turbomax Vacuum Cleaner catches Kat's vermin instead.
        if (com.skyrpg.skyblock.rift.Rift.isRift(player.level())) {
            return com.skyrpg.skyblock.rift.Kat.vacuum(player);
        }
        if (!Garden.isGarden(player.level())) {
            player.sendOverlayMessage(Component.literal("Vacuums work on Garden pests.").withStyle(ChatFormatting.GRAY));
            return false;
        }
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Mob target = null;
        double best = 0.7;
        for (Mob pest : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(10), m -> m.entityTags().contains(TAG))) {
            Vec3 to = pest.position().add(0, 0.2, 0).subtract(eye);
            double dot = look.dot(to.normalize());
            if (to.length() <= 10 && dot > best) {
                best = dot;
                target = pest;
            }
        }
        if (target == null) {
            return true;
        }
        ItemDefinition def = SkyItems.definition(player.getMainHandItem());
        double dps = def == null ? 100 : def.stats().entrySet().stream().filter(e -> e.getKey().id().equals("damage"))
                .mapToDouble(Map.Entry::getValue).findFirst().orElse(100);
        double damage = dps * 0.2 / com.skyrpg.core.stats.StatTypes.HEALTH_SCALE;
        for (int i = 0; i <= 8; i++) {
            Vec3 p = eye.lerp(target.position(), i / 8.0);
            level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        float left = (float) (target.getHealth() - damage);
        if (left > 0) {
            target.setHealth(left);
            player.sendOverlayMessage(Component.literal(target.getCustomName().getString() + ": " + Math.round(left * com.skyrpg.core.stats.StatTypes.HEALTH_SCALE)
                    + "❤").withStyle(ChatFormatting.GOLD));
            return true;
        }
        killed(player, target);
        return true;
    }

    private static void killed(ServerPlayer player, Mob pest) {
        String kindName = pest.entityTags().stream().filter(t -> t.startsWith(TAG + "_kind:")).map(t -> t.substring((TAG + "_kind:").length()))
                .findFirst().orElse("Fly");
        Kind kind = KINDS.stream().filter(k -> k.name().equals(kindName)).findFirst().orElse(KINDS.get(0));
        ServerLevel level = (ServerLevel) pest.level();
        level.sendParticles(ParticleTypes.POOF, pest.getX(), pest.getY(), pest.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        pest.discard();
        Wallet.give(player, 1_000);
        SkillLevels.giveXp(player, SkillLevels.FARMING, 1_500);
        int crops = 1 + (int) (Stats.get(player, StatTypes.FARMING_FORTUNE) / 50);
        ItemDefinition def = ItemRegistry.skyblock(ENCHANTED.get(kind.crop()));
        if (def != null) {
            ItemStack stack = SkyItems.create(def, Math.min(64, crops));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        // A spray material now and then (decided: 10%, any of them), and the rare Vermin Vaporizer Chip.
        var random = player.getRandom();
        if (random.nextDouble() < 0.10) {
            Sprays.Material material = Sprays.MATERIALS.get(random.nextInt(Sprays.MATERIALS.size()));
            GardenChips.give(player, material.item());
            player.sendSystemMessage(Component.literal("The " + kind.name() + " dropped " + material.name() + "!").withStyle(ChatFormatting.GREEN));
        }
        if (random.nextDouble() < 0.01) {
            GardenChips.give(player, "VERMIN_VAPORIZER_GARDEN_CHIP");
            player.sendSystemMessage(Component.literal("RARE DROP! Vermin Vaporizer Chip").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        Profile owner = Garden.ownerAt(pest.blockPosition());
        if (owner != null) {
            Garden.state(level.getServer(), owner).pests++;
            GardenData.get(level.getServer()).setDirty();
        }
        Notice.of(Notice.Kind.FARMING).tag("PEST").panel()
                .line(Component.literal(" +1,000 coins  ·  +1,500 Farming XP  ·  " + crops + " " + (def == null ? "" : def.name()))
                        .withStyle(ChatFormatting.GRAY))
                .send(player, Component.literal("You vacuumed up a " + kind.name() + "!").withStyle(ChatFormatting.GREEN));
        Msg.playSound(player, SoundEvents.ITEM_PICKUP, 1f, 0.6f);
    }

    /** Pesthunter Phillip's shop, for pests killed. */
    record Sale(String item, int pests) {
    }

    static final List<Sale> PHILLIP = List.of(new Sale("PESTHUNTER_BADGE", 25), new Sale("PESTHUNTERS_NECKLACE", 40),
            new Sale("PESTHUNTERS_CLOAK", 40), new Sale("PESTHUNTERS_BELT", 40), new Sale("PESTHUNTERS_GLOVES", 40));

    static String buy(ServerPlayer player, GardenData.State state, int row) {
        if (row < 0 || row >= PHILLIP.size()) {
            return "";
        }
        Sale sale = PHILLIP.get(row);
        if (state.pests < sale.pests()) {
            return "You need " + sale.pests() + " pests.";
        }
        ItemDefinition def = ItemRegistry.skyblock(sale.item());
        if (def == null) {
            return "";
        }
        state.pests -= sale.pests();
        ItemStack stack = SkyItems.create(def, 1);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        player.sendSystemMessage(Component.literal("Bought " + def.name() + ".").withStyle(ChatFormatting.GREEN));
        return "";
    }
}
