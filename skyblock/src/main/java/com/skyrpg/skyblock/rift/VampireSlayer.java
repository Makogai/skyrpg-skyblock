package com.skyrpg.skyblock.rift;

import com.mojang.serialization.Codec;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.progress.SlayerLevels;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The Vampire Slayer (Hypixel's Riftstalker Bloodfiend), started with Maddox in Stillgore Chateau for Motes. Kill the
 * Chateau's vampires to fill the quest (Thrall 30, Fledgling 20, Splatter 30; decided: 300 / 600 / 1,000 / 1,500 /
 * 2,400 for the five tiers), then the Bloodfiend rises beside you. Its Coagulation takes Hearts every second (0.5 / 1 /
 * 1 / 1 / 1.5), Twinclaws (tier II+) slash the Hearts of whoever stands in front of it every 7 seconds (3 / 5 / 6 / 8),
 * Mania (tier II+, at 60% health; tier III+ also at 75% and 40%) leaves it untouchable for 8 seconds. Below a fifth of its
 * health only a Steak Stake finishes it. Rewards: slayer XP (10 / 25 / 60 / 120 / 150; levels at 20, 75, 240, 840,
 * 2,400 give Rift Time and SkyBlock XP), Coven Seals and Hypixel's rare drops, and Motes (decided: a quarter more than
 * the fee).
 */
final class VampireSlayer {
    static final String TAG = "skyrpg_bloodfiend";
    private static final int STAKE_PERCENT = 20;
    private static final DustParticleOptions BLOOD = new DustParticleOptions(0xB00000, 1.4f);

    /** A tier: its Motes fee, health, Hearts a second, slayer XP, the quest's size, Combat level, Twinclaws' Hearts. */
    record Tier(long motes, double health, double heartsPerSecond, int xp, int quest, int combat, int claws) {
    }

    static final List<Tier> TIERS = List.of(new Tier(2_000, 625, 0.5, 10, 300, 0, 0), new Tier(4_000, 1_100, 1, 25, 600, 0, 3),
            new Tier(5_000, 1_800, 1, 60, 1_000, 0, 5), new Tier(7_000, 2_400, 1, 120, 1_500, 15, 6),
            new Tier(10_000, 3_000, 1.5, 150, 2_400, 25, 8));
    static final int[] LEVELS = {20, 75, 240, 840, 2_400};
    static final int[] LEVEL_TIME = {20, 30, 45, 60, 80};
    static final int[] LEVEL_SBXP = {15, 25, 35, 50, 75};
    private static final Map<String, Integer> QUEST_XP = Map.of("Thrall", 30, "Fledgling", 20, "Splatter", 30);
    /** Seals per tier (min, max) and rare drops (item, chance from tier). */
    private static final int[][] SEALS = {{8, 10}, {10, 16}, {28, 48}, {52, 64}, {64, 128}};
    private static final Object[][] RARES = {{"BUBBA_BLISTER", 0.10, 2}, {"GUARDIAN_LUCKY_BLOCK", 0.0625, 3}, {"MCGRUBBER_BURGER", 0.0122, 4},
            {"UNFANGED_VAMPIRE_PART", 0.0122, 4}};

    /** XP and the highest tier killed ("xp", "best"); and the quest running ("tier" 1-5, "progress"). */
    static final AttachmentType<Map<String, Integer>> STATE = AttachmentRegistry.create(SkyRpgSkyblock.id("vampire_slayer"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());
    /** The slayer level, synced (recipes lock behind it on the client too). */
    static final AttachmentType<Integer> LEVEL = AttachmentRegistry.create(SkyRpgSkyblock.id("vampire_level"),
            builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()).copyOnDeath());

    /** A boss fight in progress: the boss, its owner, its bar, when it started, its manias left. */
    private static final class Fight {
        Zombie boss;
        UUID owner;
        int tier;
        ServerBossEvent bar;
        long started;
        long maniaUntil;
        final List<Double> manias = new ArrayList<>();
    }

    private static final Map<UUID, Fight> FIGHTS = new HashMap<>();

    private VampireSlayer() {
    }

    static void init() {
        SlayerLevels.installKey("vampire", player -> player.getAttachedOrElse(LEVEL, 0));
        ServerTickEvents.END_SERVER_TICK.register(VampireSlayer::tick);
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Vampire Slayer";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                int level = player.getAttachedOrElse(LEVEL, 0);
                int time = 0;
                for (int i = 0; i < level; i++) {
                    time += LEVEL_TIME[i];
                }
                if (time > 0) {
                    out.add(StatTypes.RIFT_TIME, time);
                }
            }
        });
        // Quest progress from the Chateau's vampires.
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer player) || entity.getCustomName() == null || !Rift.isRift(entity.level())) {
                return;
            }
            Fight fight = FIGHTS.get(player.getUUID());
            if (fight != null && fight.boss == entity) {
                return;
            }
            String name = entity.getCustomName().getString().replaceAll("^\\[Lv\\d+\\] ", "");
            Integer xp = QUEST_XP.entrySet().stream().filter(e -> name.contains(e.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
            Map<String, Integer> state = state(player);
            int tier = state.getOrDefault("tier", 0);
            if (xp == null || tier == 0 || FIGHTS.containsKey(player.getUUID())) {
                return;
            }
            int progress = Math.min(TIERS.get(tier - 1).quest(), state.getOrDefault("progress", 0) + xp);
            put(player, "progress", progress);
            player.sendOverlayMessage(Component.literal("Vampire Slayer: " + progress + "/" + TIERS.get(tier - 1).quest()).withStyle(ChatFormatting.DARK_RED));
            if (progress >= TIERS.get(tier - 1).quest()) {
                spawn(player, tier);
            }
        });
        // Below a fifth of its health only a Steak Stake finishes the Bloodfiend; nothing hurts it during Mania.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            Fight fight = fightOf(entity);
            if (fight == null) {
                return true;
            }
            long now = entity.level().getServer().getTickCount();
            if (now < fight.maniaUntil) {
                if (source.getEntity() instanceof ServerPlayer p) {
                    p.sendOverlayMessage(Component.literal("Mania! The Bloodfiend can't be hurt right now.").withStyle(ChatFormatting.RED));
                }
                return false;
            }
            boolean stake = source.getEntity() instanceof ServerPlayer p && isStake(p.getMainHandItem());
            LivingEntity boss = (LivingEntity) entity;
            double floor = boss.getMaxHealth() * STAKE_PERCENT / 100;
            if (boss.getHealth() <= floor + 0.01) {
                if (stake) {
                    boss.setHealth(0.01f);
                    return true;
                }
                if (source.getEntity() instanceof ServerPlayer p) {
                    p.sendOverlayMessage(Component.literal("Finish it with a Steak Stake!").withStyle(ChatFormatting.RED));
                }
                return false;
            }
            return true;
        });
        // Never below the stake threshold except to a stake.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            Fight fight = fightOf(entity);
            if (fight != null && !(source.getEntity() instanceof ServerPlayer p && isStake(p.getMainHandItem()))) {
                LivingEntity boss = (LivingEntity) entity;
                double floor = boss.getMaxHealth() * STAKE_PERCENT / 100;
                if (boss.getHealth() < floor && boss.isAlive()) {
                    boss.setHealth((float) floor);
                }
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Fight fight = fightOf(entity);
            if (fight != null) {
                won(entity.level().getServer(), fight);
            }
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && fightOf(entity) == null) {
                entity.discard();
            }
        });
    }

    private static Fight fightOf(net.minecraft.world.entity.Entity entity) {
        if (!entity.entityTags().contains(TAG)) {
            return null;
        }
        return FIGHTS.values().stream().filter(f -> f.boss == entity).findFirst().orElse(null);
    }

    static boolean isStake(ItemStack stack) {
        ItemDefinition def = SkyItems.definition(stack);
        return def != null && def.skyblockId() != null && def.skyblockId().contains("STEAK_STAKE");
    }

    static Map<String, Integer> state(ServerPlayer player) {
        return player.getAttachedOrElse(STATE, Map.of());
    }

    private static void put(ServerPlayer player, String key, int value) {
        Map<String, Integer> state = new HashMap<>(state(player));
        state.put(key, value);
        player.setAttached(STATE, Map.copyOf(state));
    }

    static int level(ServerPlayer player) {
        int xp = state(player).getOrDefault("xp", 0);
        int level = 0;
        while (level < LEVELS.length && xp >= LEVELS[level]) {
            level++;
        }
        return level;
    }

    // ---------------------------------------------------------------- Maddox

    static void maddox(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            Map<String, Integer> state = state(p);
            int level = level(p);
            int xp = state.getOrDefault("xp", 0);
            int best = state.getOrDefault("best", 0);
            int active = state.getOrDefault("tier", 0);
            long motes = Motes.get(p);
            int combat = SkillLevels.level(p, SkillLevels.COMBAT);
            List<CardView.Card> cards = new ArrayList<>();
            int next = level < LEVELS.length ? LEVELS[level] : LEVELS[LEVELS.length - 1];
            int prev = level == 0 ? 0 : LEVELS[level - 1];
            cards.add(new CardView.Card(active > 0 ? "Quest: Bloodfiend " + Msg.roman(active) : "Vampire Slayer " + (level == 0 ? "0" : Msg.roman(level)),
                    active > 0 ? (FIGHTS.containsKey(p.getUUID()) ? "The Bloodfiend is up! Finish it with a Steak Stake." : state.getOrDefault("progress", 0) + "/"
                            + TIERS.get(active - 1).quest() + " from the Chateau's vampires") : "Level " + level + "  ·  " + xp + " XP", 0xFF5555, active > 0,
                    active > 0 ? "Cancel" : "", new ItemStack(Items.REDSTONE), "", List.of(), active > 0 ? state.getOrDefault("progress", 0)
                    / (float) TIERS.get(active - 1).quest() : level >= LEVELS.length ? 1f : (xp - prev) / (float) Math.max(1, next - prev), ""));
            for (int t = 1; t <= TIERS.size(); t++) {
                Tier tier = TIERS.get(t - 1);
                String lock = t > 1 && best < t - 1 ? "Kill a tier " + Msg.roman(t - 1) + " first" : combat < tier.combat() ? "Needs Combat " + tier.combat() : "";
                cards.add(new CardView.Card("Riftstalker Bloodfiend " + Msg.roman(t), lock.isEmpty() ? Msg.number((long) tier.health()) + " health  ·  "
                        + tier.heartsPerSecond() + " ❤/s  ·  " + tier.xp() + " XP" : lock, lock.isEmpty() ? 0xFF5555 : 0x777777,
                        lock.isEmpty() && active == 0 && motes >= tier.motes(), "Start", new ItemStack(t >= 4 ? Items.WITHER_SKELETON_SKULL : Items.REDSTONE_BLOCK),
                        "", List.of(Chip.price("motes", tier.motes(), motes)), -1, ""));
            }
            cards.add(CardView.Card.offer(RiftNpcs.icon("STEAK_STAKE"), List.of(Chip.price("motes", 800, motes))));
            return new CardView("RIFT", "Maddox", "Vampire Slayer", npc, List.of(Chip.balance("motes", motes)), cards,
                    "Below a fifth of its health, only a Steak Stake finishes the Bloodfiend.", 1, 28);
        }, (p, card) -> {
            Map<String, Integer> state = state(p);
            if (card == 0) {
                if (state.getOrDefault("tier", 0) > 0) {
                    Fight fight = FIGHTS.remove(p.getUUID());
                    if (fight != null) {
                        end(fight);
                    }
                    put(p, "tier", 0);
                    put(p, "progress", 0);
                    p.sendSystemMessage(Component.literal("Vampire Slayer quest cancelled.").withStyle(ChatFormatting.RED));
                }
                return "";
            }
            if (card == TIERS.size() + 1) {
                if (!Motes.take(p, 800)) {
                    return "You need 800 Motes.";
                }
                ItemDefinition stake = ItemRegistry.skyblock("STEAK_STAKE");
                if (stake != null) {
                    ItemStack stack = SkyItems.create(stake, 1);
                    if (!p.getInventory().add(stack)) {
                        p.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                    }
                }
                return "";
            }
            int t = card;
            Tier tier = TIERS.get(t - 1);
            if (state.getOrDefault("tier", 0) > 0) {
                return "You already have a Vampire Slayer quest.";
            }
            if (t > 1 && state.getOrDefault("best", 0) < t - 1 || SkillLevels.level(p, SkillLevels.COMBAT) < tier.combat()) {
                return "You can't start this tier yet.";
            }
            if (!Motes.take(p, tier.motes())) {
                return "You need " + Msg.number(tier.motes()) + " Motes.";
            }
            put(p, "tier", t);
            put(p, "progress", 0);
            Notice.of(Notice.Kind.SLAYER).tag("VAMPIRE SLAYER").send(p, Component.literal("Riftstalker Bloodfiend " + Msg.roman(t)
                    + " started! Kill the Chateau's vampires.").withStyle(ChatFormatting.RED));
            return null;
        });
    }

    // ---------------------------------------------------------------- the fight

    private static void spawn(ServerPlayer player, int tier) {
        ServerLevel level = (ServerLevel) player.level();
        Zombie boss = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
        if (boss == null) {
            return;
        }
        Vec3 at = player.position().add(player.getLookAngle().multiply(3, 0, 3));
        boss.snapTo(at.x, player.getY(), at.z, player.getYRot() + 180, 0);
        Tier t = TIERS.get(tier - 1);
        boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(t.health() / StatTypes.HEALTH_SCALE);
        boss.setHealth(boss.getMaxHealth());
        boss.getAttribute(Attributes.SCALE).setBaseValue(1.25);
        boss.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(8 / StatTypes.HEALTH_SCALE);
        boss.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
        boss.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.WITHER_SKELETON_SKULL));
        boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        boss.setCustomName(Component.literal("Riftstalker Bloodfiend " + Msg.roman(tier)).withStyle(ChatFormatting.DARK_RED));
        boss.setCustomNameVisible(true);
        boss.setPersistenceRequired();
        boss.addTag(TAG);
        boss.setTarget(player);
        Fight fight = new Fight();
        fight.boss = boss;
        fight.owner = player.getUUID();
        fight.tier = tier;
        fight.started = level.getServer().getTickCount();
        fight.bar = new ServerBossEvent(UUID.randomUUID(), boss.getCustomName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        fight.bar.addPlayer(player);
        if (tier >= 2) {
            fight.manias.add(0.6);
        }
        if (tier >= 3) {
            fight.manias.add(0.75);
            fight.manias.add(0.4);
        }
        FIGHTS.put(player.getUUID(), fight);
        level.addFreshEntity(boss);
        level.sendParticles(BLOOD, at.x, player.getY() + 1, at.z, 60, 0.6, 1, 0.6, 0);
        Notice.of(Notice.Kind.SLAYER).tag("VAMPIRE SLAYER").send(player, Component.literal("The Riftstalker Bloodfiend has risen!").withStyle(ChatFormatting.DARK_RED));
        Msg.playSound(player, SoundEvents.WITHER_SPAWN, 0.6f, 1.4f);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0 || FIGHTS.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (Fight fight : new ArrayList<>(FIGHTS.values())) {
            ServerPlayer owner = server.getPlayerList().getPlayer(fight.owner);
            if (owner == null || fight.boss.isRemoved() || !Rift.isRift(owner.level()) || now - fight.started > 3 * 60 * 20) {
                if (owner != null && !fight.boss.isRemoved()) {
                    owner.sendSystemMessage(Component.literal("The Riftstalker Bloodfiend got away. Quest failed.").withStyle(ChatFormatting.RED));
                }
                if (owner != null) {
                    put(owner, "tier", 0);
                    put(owner, "progress", 0);
                }
                FIGHTS.remove(fight.owner);
                end(fight);
                continue;
            }
            Zombie boss = fight.boss;
            Tier tier = TIERS.get(fight.tier - 1);
            fight.bar.setProgress(Math.max(0, boss.getHealth() / boss.getMaxHealth()));
            boss.setTarget(owner);
            // Mania at its health marks.
            double left = boss.getHealth() / boss.getMaxHealth();
            for (Double mark : new ArrayList<>(fight.manias)) {
                if (left <= mark) {
                    fight.manias.remove(mark);
                    fight.maniaUntil = now + 8 * 20;
                    owner.sendOverlayMessage(Component.literal("MANIA! The Bloodfiend can't be hurt for 8 seconds!").withStyle(ChatFormatting.RED));
                    Msg.playSound(owner, SoundEvents.RAVAGER_ROAR, 1f, 1.2f);
                }
            }
            if (now < fight.maniaUntil) {
                ((ServerLevel) boss.level()).sendParticles(BLOOD, boss.getX(), boss.getY() + 0.1, boss.getZ(), 20, 3, 0, 3, 0);
            }
            if (now % 20 == 0) {
                // Coagulation: Hearts every second.
                Rift.takeHearts(owner, tier.heartsPerSecond(), "Coagulation");
            }
            // Twinclaws (tier II+), every 7 seconds, in front of it.
            if (tier.claws() > 0 && (now - fight.started) % 140 == 0 && owner.distanceToSqr(boss) < 5 * 5) {
                Vec3 facing = boss.getLookAngle();
                Vec3 to = owner.position().subtract(boss.position()).normalize();
                ((ServerLevel) boss.level()).sendParticles(ParticleTypes.SWEEP_ATTACK, boss.getX() + facing.x * 2, boss.getY() + 1, boss.getZ() + facing.z * 2,
                        4, 0.8, 0.3, 0.8, 0);
                if (facing.dot(to) > 0.3) {
                    Rift.takeHearts(owner, tier.claws(), "Twinclaws");
                }
            }
        }
    }

    private static void end(Fight fight) {
        fight.bar.removeAllPlayers();
        if (!fight.boss.isRemoved()) {
            fight.boss.discard();
        }
    }

    private static void won(MinecraftServer server, Fight fight) {
        FIGHTS.remove(fight.owner);
        end(fight);
        ServerPlayer player = server.getPlayerList().getPlayer(fight.owner);
        if (player == null) {
            return;
        }
        Tier tier = TIERS.get(fight.tier - 1);
        int before = level(player);
        Map<String, Integer> state = state(player);
        put(player, "xp", state.getOrDefault("xp", 0) + tier.xp());
        put(player, "best", Math.max(state.getOrDefault("best", 0), fight.tier));
        put(player, "tier", 0);
        put(player, "progress", 0);
        int after = level(player);
        player.setAttached(LEVEL, after);
        java.util.Random random = new java.util.Random();
        int seals = SEALS[fight.tier - 1][0] + random.nextInt(SEALS[fight.tier - 1][1] - SEALS[fight.tier - 1][0] + 1);
        give(player, "COVEN_SEAL", seals);
        List<String> rares = new ArrayList<>();
        for (Object[] rare : RARES) {
            if (fight.tier >= (int) rare[2] && random.nextDouble() < (double) rare[1] && give(player, (String) rare[0], 1)) {
                rares.add(RiftNpcs.icon((String) rare[0]).getHoverName().getString());
            }
        }
        long motes = Math.round(tier.motes() * 1.25);
        Motes.give(player, motes);
        var notice = Notice.of(Notice.Kind.SLAYER).tag("SLAYER QUEST COMPLETE").panel()
                .line(Component.literal(" +" + tier.xp() + " Vampire Slayer XP  ·  " + seals + " Coven Seals  ·  +" + Msg.number(motes) + " Motes")
                        .withStyle(ChatFormatting.GRAY));
        for (String rare : rares) {
            notice.line(Component.literal(" RARE DROP! " + rare).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        notice.send(player, Component.literal("Riftstalker Bloodfiend " + Msg.roman(fight.tier) + " slain!").withStyle(ChatFormatting.DARK_RED));
        Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.9f);
        for (int level = before + 1; level <= after; level++) {
            SkyBlockXp.give(player, LEVEL_SBXP[level - 1], "Vampire Slayer " + level);
            Notice.of(Notice.Kind.SLAYER).tag("SLAYER LEVEL UP").panel()
                    .line(Component.literal(" +" + LEVEL_TIME[level - 1] + "s ф Rift Time  ·  +" + LEVEL_SBXP[level - 1] + " SkyBlock XP").withStyle(ChatFormatting.GRAY))
                    .send(player, Component.literal("Vampire Slayer " + Msg.roman(level) + "!").withStyle(ChatFormatting.RED));
        }
        Stats.refresh(player);
    }

    private static boolean give(ServerPlayer player, String id, int count) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return false;
        }
        ItemStack stack = SkyItems.create(def, count);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        return true;
    }
}
