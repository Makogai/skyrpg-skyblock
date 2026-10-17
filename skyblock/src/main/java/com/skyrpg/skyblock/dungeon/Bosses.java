package com.skyrpg.skyblock.dungeon;

import com.skyrpg.core.text.Msg;
import com.skyrpg.hub.mixin.DisplayAccess;
import com.skyrpg.skyblock.mixin.InteractionAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Catacombs' bosses, Floor I to VII, with Hypixel's health ({@code docs/design/research/dungeons.md}) and each one's
 * signature mechanic, simplified (see {@code docs/design/catacombs.md}).
 */
final class Bosses {
    private Bosses() {
    }

    static ItemStack dyed(net.minecraft.world.item.Item item, int color) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
        return stack;
    }

    // ---------------------------------------------------------------- Floor I: Bonzo

    /** Bonzo: throws balloons and calls undead; falls once and gets back up, faster. */
    static final class Bonzo extends BossFight {
        private int phase = 1;
        private long nextBalloon;
        private long nextMinions;

        Bonzo(Run run) {
            super(run);
        }

        @Override
        String name() {
            return "Bonzo";
        }

        @Override
        void intro() {
            say(name(), "Gratz for making it this far, but I'm basically unbeatable.");
        }

        @Override
        void begin() {
            Mob bonzo = spawnBoss(EntityTypes.ZOMBIE, spot(0, -10), "Bonzo", hp(250_000, 200_000_000), bossDamage());
            if (bonzo != null) {
                stack(bonzo, EquipmentSlot.MAINHAND, new ItemStack(Items.BLAZE_ROD));
                stack(bonzo, EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, 0xB02E26));
                stack(bonzo, EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            }
            long now = run.level.getGameTime();
            nextBalloon = now + 80;
            nextMinions = now + 60;
        }

        @Override
        void fight(long now) {
            if (boss == null || !boss.isAlive()) {
                return;
            }
            if (boss.getTarget() == null && randomTarget() instanceof ServerPlayer target) {
                boss.setTarget(target);
            }
            if (now >= nextBalloon && randomTarget() instanceof ServerPlayer target) {
                nextBalloon = now + (phase == 1 ? 120 : 70);
                Vec3 at = target.position();
                run.level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1, at.z, 3, 0.8, 0.4, 0.8, 0);
                hitAround(at, 3, 1.5);
                Msg.playSound(target, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
            }
            if (now >= nextMinions && minions.size() < 4) {
                nextMinions = now + 200;
                for (int i = 0; i < 2; i++) {
                    minion(EntityTypes.ZOMBIE, spot(run.random.nextInt(11) - 5, run.random.nextInt(11) - 5), "Undead", 22_000, 150);
                }
            }
        }

        @Override
        boolean allowDeath(LivingEntity entity) {
            if (entity != boss) {
                return true;
            }
            if (phase == 1) {
                phase = 2;
                entity.setHealth(entity.getMaxHealth());
                entity.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 600, 1, false, false));
                entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0, false, false));
                say(name(), "Oh, I'm not done yet!");
                return false;
            }
            win("Alright, maybe I'm just weak after all..");
            return true;
        }
    }

    // ---------------------------------------------------------------- Floor II: Scarf

    /** Scarf: shielded while his four undead live; then he fights, blinking around and calling skeletons. */
    static final class Scarf extends BossFight {
        private boolean shielded = true;
        private long nextBlink;
        private long nextCall;

        Scarf(Run run) {
            super(run);
        }

        @Override
        String name() {
            return "Scarf";
        }

        @Override
        void intro() {
            say(name(), "This is where the journey ends for you, Adventurers.");
        }

        @Override
        void begin() {
            Mob scarf = spawnBoss(EntityTypes.ZOMBIE, spot(0, -12), "Scarf", hp(1_000_000, 375_000_000), bossDamage());
            if (scarf != null) {
                stack(scarf, EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, 0x6A1B9A));
                stack(scarf, EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, 0xB02E26));
                scarf.setNoAi(true);
                scarf.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0, false, false));
            }
            say(name(), "Go, my undead!");
            minion(EntityTypes.ZOMBIE, spot(-8, -4), "Undead Warrior", 200_000, bossDamage());
            minion(EntityTypes.STRAY, spot(8, -4), "Undead Archer", 160_000, bossDamage() * 0.8);
            minion(EntityTypes.HUSK, spot(-8, 4), "Undead Priest", 180_000, bossDamage() * 0.8);
            minion(EntityTypes.ZOMBIE_VILLAGER, spot(8, 4), "Undead Mage", 180_000, bossDamage() * 0.9);
            nextBlink = run.level.getGameTime() + 100;
        }

        @Override
        void fight(long now) {
            if (boss == null || !boss.isAlive()) {
                return;
            }
            if (shielded && minions.isEmpty()) {
                shielded = false;
                boss.setNoAi(false);
                boss.removeEffect(MobEffects.GLOWING);
                say(name(), "Those toys are not strong enough I see.");
                nextCall = now + 300;
            }
            if (!shielded && now >= nextBlink && randomTarget() instanceof ServerPlayer target) {
                nextBlink = now + 160;
                Vec3 to = spot(run.random.nextInt(21) - 10, run.random.nextInt(21) - 10);
                boss.teleportTo(to.x, to.y, to.z);
                boss.setTarget(target);
                run.level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + 1, target.getZ(), 30, 0.6, 0.6, 0.6, 0.05);
                hitAround(target.position(), 3, 1.2);
            }
            if (!shielded && now >= nextCall && minions.size() < 3) {
                nextCall = now + 300;
                for (int i = 0; i < 2; i++) {
                    minion(EntityTypes.SKELETON, spot(run.random.nextInt(15) - 7, run.random.nextInt(15) - 7), "Undead Archer", 160_000,
                            bossDamage() * 0.8);
                }
            }
        }

        @Override
        boolean allowDamage(LivingEntity entity, DamageSource source) {
            return entity != boss || !shielded;
        }

        @Override
        boolean allowDeath(LivingEntity entity) {
            if (entity == boss) {
                win("Whatever...");
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- Floor III: The Professor

    /** The Professor: shielded behind four Guardians; at half health he calls two more and speeds up. */
    static final class Professor extends BossFight {
        private boolean shielded = true;
        private boolean halfway;
        private long nextBlast;

        Professor(Run run) {
            super(run);
        }

        @Override
        String name() {
            return "The Professor";
        }

        @Override
        void intro() {
            say(name(), "I was burdened with terrible news recently...");
        }

        @Override
        void begin() {
            Mob professor = spawnBoss(EntityTypes.ZOMBIE_VILLAGER, spot(0, -14), "The Professor", hp(3_000_000, 600_000_000), bossDamage());
            if (professor != null) {
                professor.setNoAi(true);
                professor.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0, false, false));
            }
            say(name(), "Behold my Guardians!");
            guardians(4);
            nextBlast = run.level.getGameTime() + 120;
        }

        private void guardians(int count) {
            String[] names = {"Healthy Guardian", "Reinforced Guardian", "Laser Guardian", "Chaos Guardian"};
            for (int i = 0; i < count; i++) {
                double a = Math.PI * 2 * i / count;
                Mob g = minion(i % 2 == 0 ? EntityTypes.ZOMBIE : EntityTypes.SKELETON, spot(Math.cos(a) * 9, Math.sin(a) * 9 - 4), names[i % 4],
                        600_000, bossDamage());
                if (g != null) {
                    stack(g, EquipmentSlot.HEAD, new ItemStack(Items.PRISMARINE));
                    stack(g, EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                }
            }
        }

        @Override
        void fight(long now) {
            if (boss == null || !boss.isAlive()) {
                return;
            }
            if (shielded && minions.isEmpty()) {
                shielded = false;
                boss.setNoAi(false);
                boss.removeEffect(MobEffects.GLOWING);
                say(name(), "Oh? You found my Guardians' one weakness?");
            }
            if (!shielded && !halfway && boss.getHealth() < boss.getMaxHealth() / 2) {
                halfway = true;
                say(name(), "I see. You have forced me to use my ultimate technique.");
                guardians(2);
                boss.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 600, 1, false, false));
            }
            if (!shielded && now >= nextBlast && randomTarget() instanceof ServerPlayer target) {
                nextBlast = now + 100;
                run.level.sendParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);
                hitAround(target.position(), 2.5, 1.5);
                if (boss.getTarget() == null) {
                    boss.setTarget(target);
                }
            }
        }

        @Override
        boolean allowDamage(LivingEntity entity, DamageSource source) {
            return entity != boss || !shielded;
        }

        @Override
        boolean allowDeath(LivingEntity entity) {
            if (entity == boss) {
                win("What?! My Guardian power is unbeatable!");
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- Floor IV: Thorn

    /**
     * Thorn: a floating ghast only a Spirit Bow can hurt. Spirit animals come; a Spirit Bear gives its killer a Spirit Bow
     * with one arrow. Four Spirit Bow hits (six in Master Mode) and he's done.
     */
    static final class Thorn extends BossFight {
        static final String SPIRIT_BOW = "skyrpg_spirit_bow";
        private int hits;
        private long nextAnimals;
        private long nextThrow;
        private final Set<UUID> bears = new HashSet<>();

        Thorn(Run run) {
            super(run);
        }

        @Override
        String name() {
            return "Thorn";
        }

        /** Spirit Bow hits to beat him: 4, or 6 in Master Mode. */
        private int hits() {
            return run.floor.master() ? 6 : 4;
        }

        @Override
        void intro() {
            say(name(), "Welcome Adventurers! I am Thorn, the Spirit! And host of the Vegan Trials!");
        }

        @Override
        void begin() {
            Mob thorn = spawnBoss(EntityTypes.GHAST, center().add(0, 10, -8), "Thorn", hits(), bossDamage());
            if (thorn != null) {
                thorn.setNoAi(true);
                thorn.setNoGravity(true);
            }
            say(name(), "Only the Spirit Bow can hurt me. Kill a Spirit Bear for it!");
            long now = run.level.getGameTime();
            nextAnimals = now + 40;
            nextThrow = now + 120;
        }

        @Override
        void fight(long now) {
            if (now >= nextAnimals && minions.size() < 6) {
                nextAnimals = now + 300;
                for (int i = 0; i < 2; i++) {
                    minion(EntityTypes.WOLF, spot(run.random.nextInt(21) - 10, run.random.nextInt(21) - 10), "Spirit Wolf", 300_000, bossDamage() * 0.6);
                }
                Mob bear = minion(EntityTypes.POLAR_BEAR, spot(run.random.nextInt(21) - 10, run.random.nextInt(21) - 10), "Spirit Bear", 600_000,
                        bossDamage());
                if (bear != null) {
                    bears.add(bear.getUUID());
                }
            }
            if (now >= nextThrow && randomTarget() instanceof ServerPlayer target) {
                nextThrow = now + 120;
                run.level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1, target.getZ(), 30, 0.5, 0.6, 0.5, 0.02);
                hitAround(target.position(), 3, 1);
            }
        }

        @Override
        void died(LivingEntity entity) {
            if (bears.remove(entity.getUUID()) && entity.getLastHurtByMob() instanceof ServerPlayer killer) {
                ItemStack bow = new ItemStack(Items.BOW);
                bow.set(DataComponents.CUSTOM_NAME, Component.literal("Spirit Bow").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.LIGHT_PURPLE)));
                CompoundTag tag = new CompoundTag();
                tag.putBoolean(SPIRIT_BOW, true);
                bow.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                killer.getInventory().add(bow);
                killer.getInventory().add(new ItemStack(Items.ARROW));
                run.broadcast(Component.literal(killer.getScoreboardName() + " picked up the Spirit Bow! Shoot Thorn!").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
        }

        @Override
        boolean allowDamage(LivingEntity entity, DamageSource source) {
            if (entity != boss) {
                return true;
            }
            if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getWeaponItem() != null
                    && arrow.getWeaponItem().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr(SPIRIT_BOW, false)) {
                arrow.discard();
                hits++;
                entity.setHealth(entity.getMaxHealth() * (hits() - hits) / hits());
                say(name(), hits < hits() ? "Ouch! That actually hurt!" : "This is it... I am defeated...");
                if (source.getEntity() instanceof ServerPlayer shooter) {
                    for (int i = 0; i < shooter.getInventory().getContainerSize(); i++) {
                        ItemStack stack = shooter.getInventory().getItem(i);
                        if (stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBooleanOr(SPIRIT_BOW, false)) {
                            shooter.getInventory().setItem(i, ItemStack.EMPTY);
                        }
                    }
                }
                if (hits >= hits()) {
                    entity.discard();
                    win(null);
                }
            }
            return false;
        }
    }

    // ---------------------------------------------------------------- Floor V: Livid

    /** Livid: nine of him, each a different colour; the real one's colour is on the block in the middle. */
    static final class Livid extends BossFight {
        private static final DyeColor[] COLORS = {DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.CYAN, DyeColor.BLUE,
                DyeColor.PURPLE, DyeColor.PINK, DyeColor.WHITE};
        private static final ChatFormatting[] FORMATS = {ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW, ChatFormatting.GREEN,
                ChatFormatting.DARK_AQUA, ChatFormatting.BLUE, ChatFormatting.DARK_PURPLE, ChatFormatting.LIGHT_PURPLE, ChatFormatting.WHITE};
        private final Set<UUID> fakes = new HashSet<>();

        Livid(Run run) {
            super(run);
        }

        @Override
        String name() {
            return "Livid";
        }

        @Override
        void intro() {
            say(name(), "Welcome, you've arrived right on time. I am Livid, the Master of Shadows.");
        }

        @Override
        void begin() {
            int real = run.random.nextInt(COLORS.length);
            for (int i = 0; i < COLORS.length; i++) {
                double a = Math.PI * 2 * i / COLORS.length;
                Vec3 at = spot(Math.cos(a) * 10, Math.sin(a) * 10);
                Mob livid = i == real ? spawnBoss(EntityTypes.ZOMBIE, at, "Livid", hp(7_000_000, 600_000_000), bossDamage())
                        : minion(EntityTypes.ZOMBIE, at, "Livid", hp(7_000_000, 600_000_000) / minionScale(), bossDamage() * 0.5);
                if (livid == null) {
                    continue;
                }
                livid.setCustomName(Component.literal("Livid").withStyle(FORMATS[i]));
                stack(livid, EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, COLORS[i].getTextureDiffuseColor()));
                stack(livid, EquipmentSlot.HEAD, new ItemStack(Items.WITHER_SKELETON_SKULL));
                stack(livid, EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                if (i != real) {
                    fakes.add(livid.getUUID());
                }
                if (randomTarget() instanceof ServerPlayer target) {
                    livid.setTarget(target);
                }
            }
            run.level.setBlock(net.minecraft.core.BlockPos.containing(center()).below(), Blocks.WOOL.pick(COLORS[real]).defaultBlockState(), 3);
            say(name(), "Can you find the real me? Look at the floor.");
        }

        @Override
        void fight(long now) {
        }

        @Override
        boolean allowDamage(LivingEntity entity, DamageSource source) {
            if (fakes.contains(entity.getUUID()) && source.getEntity() instanceof ServerPlayer attacker && run.level.getGameTime() % 10 == 0) {
                attacker.sendOverlayMessage(Component.literal("That's not the real Livid!").withStyle(ChatFormatting.RED));
                attacker.hurtServer(run.level, run.level.damageSources().magic(), attacker.getMaxHealth() * 0.1f);
            }
            return true;
        }

        @Override
        boolean allowDeath(LivingEntity entity) {
            if (entity == boss) {
                for (UUID id : fakes) {
                    Entity fake = run.level.getEntity(id);
                    if (fake != null) {
                        fake.discard();
                    }
                }
                win("Impossible! How did you figure out which one I was?!");
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- Floor VI: Sadan

    /** Sadan: survive his terracotta army for a minute, beat his four Giants, then Sadan himself. */
    static final class Sadan extends BossFight {
        private static final String[] GIANTS = {"Diamond Giant", "Bigfoot", "L.A.S.R.", "Jolly Pink Giant"};
        private int phase;
        private long phaseEnds;
        private long nextTerracotta;
        private long nextStomp;
        private final Set<UUID> giants = new HashSet<>();

        Sadan(Run run) {
            super(run);
        }

        @Override
        String name() {
            return "Sadan";
        }

        @Override
        void intro() {
            say(name(), "So you made it all the way here... Now you wish to defy me? Sadan?!");
        }

        @Override
        void begin() {
            phase = 1;
            phaseEnds = run.level.getGameTime() + 60 * 20;
            say(name(), "My terracotta army will make short work of you!");
        }

        @Override
        void fight(long now) {
            if (phase == 1) {
                if (now >= nextTerracotta && minions.size() < 8) {
                    nextTerracotta = now + 100;
                    for (int i = 0; i < 2; i++) {
                        Mob t = minion(EntityTypes.ZOMBIE, spot(run.random.nextInt(31) - 15, run.random.nextInt(31) - 15), "Terracotta", 500_000,
                                bossDamage() * 0.5);
                        if (t != null) {
                            stack(t, EquipmentSlot.HEAD, new ItemStack(Blocks.TERRACOTTA.asItem()));
                        }
                    }
                }
                if (now >= phaseEnds) {
                    phase = 2;
                    say(name(), "ENOUGH! My Giants will unify and crush you!");
                    for (int i = 0; i < GIANTS.length; i++) {
                        double a = Math.PI * 2 * i / GIANTS.length + Math.PI / 4;
                        Mob giant = spawn(EntityTypes.GIANT, spot(Math.cos(a) * 16, Math.sin(a) * 16), GIANTS[i], hp(25_000_000, 250_000_000), bossDamage() * 2, 500);
                        if (giant != null) {
                            giants.add(giant.getUUID());
                            boss = giant;
                        }
                    }
                    nextStomp = now + 100;
                }
                return;
            }
            giants.removeIf(id -> {
                Entity e = run.level.getEntity(id);
                return e == null || !e.isAlive();
            });
            if (phase == 2 && giants.isEmpty()) {
                phase = 3;
                say(name(), "You did it. I understand now, you have earned my respect.");
                say(name(), "But I will not lose!");
                spawnBoss(EntityTypes.GIANT, spot(0, -12), "Sadan", hp(40_000_000, 800_000_000), bossDamage() * 2.5);
                nextStomp = now + 100;
            }
            if (now >= nextStomp) {
                nextStomp = now + 100;
                for (UUID id : phase == 2 ? giants : (boss == null ? Set.<UUID>of() : Set.of(boss.getUUID()))) {
                    Entity giant = run.level.getEntity(id);
                    if (giant instanceof Mob mob && randomTarget() instanceof ServerPlayer target) {
                        Vec3 toward = target.position().subtract(giant.position()).normalize().scale(4);
                        mob.teleportTo(giant.getX() + toward.x, giant.getY(), giant.getZ() + toward.z);
                        run.level.sendParticles(ParticleTypes.EXPLOSION, giant.getX(), giant.getY() + 0.5, giant.getZ(), 6, 3, 0.2, 3, 0);
                        double damage = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 2;
                        for (ServerPlayer p : targets()) {
                            if (p.distanceTo(giant) < 7) {
                                p.hurtServer(run.level, run.level.damageSources().mobAttack(mob), (float) damage);
                            }
                        }
                    }
                }
            }
        }

        @Override
        boolean allowDeath(LivingEntity entity) {
            if (phase == 3 && entity == boss) {
                win("NOOOOOOOOO!!! THIS IS IMPOSSIBLE!!");
            }
            return true;
        }
    }

    // ---------------------------------------------------------------- Floor VII: the Wither Lords

    /**
     * Floor VII: Maxor, Storm, Goldor, then Necron. Maxor is shielded until both Energy Crystals are carried to their pads;
     * Storm only takes damage for 10 seconds after a pillar is pulled down; Goldor until all four terminals are done;
     * Necron always, with a frenzy (a blast around him) at 80% and 5%.
     */
    static final class Necron extends BossFight {
        private static final String[] LORDS = {"Maxor", "Storm", "Goldor", "Necron"};
        private static final double[] HEALTH = {100_000_000, 400_000_000, 750_000_000, 1_000_000_000};
        private static final double[] MASTER_HEALTH = {800_000_000, 1_000_000_000, 1_200_000_000, 1_400_000_000};
        private int lord;
        private long vulnerableUntil;
        private long nextAttack;
        private final Map<UUID, String> parts = new HashMap<>();
        private final Set<Integer> placed = new HashSet<>();
        private final Set<Integer> terminals = new HashSet<>();
        private final Map<UUID, Integer> carrying = new HashMap<>();
        private boolean frenzy80;
        private boolean frenzy5;

        Necron(Run run) {
            super(run);
        }

        @Override
        String name() {
            return LORDS[lord];
        }

        @Override
        void intro() {
            say("Maxor", "WELL WELL WELL LOOK WHO'S HERE!");
        }

        @Override
        void begin() {
            next();
        }

        private void next() {
            clearParts();
            Mob wither = spawnBoss(EntityTypes.WITHER, center().add(0, 4, -10), LORDS[lord], hp(HEALTH[lord], MASTER_HEALTH[lord]), bossDamage());
            if (wither != null) {
                wither.setNoAi(true);
                wither.setNoGravity(true);
            }
            vulnerableUntil = lord == 3 ? Long.MAX_VALUE : 0;
            nextAttack = run.level.getGameTime() + 100;
            switch (lord) {
                case 0 -> {
                    say("Maxor", "YOU TRICKED ME! Carry my Energy Crystals to the pads, I DARE YOU.");
                    placed.clear();
                    crystal(0);
                    crystal(1);
                    pad(0);
                    pad(1);
                }
                case 1 -> {
                    say("Storm", "Pathetic Maxor, just like expected. I'll crush you myself!");
                    part("pillar0", spot(-12, 0), "Pillar", ChatFormatting.AQUA);
                    part("pillar1", spot(12, 0), "Pillar", ChatFormatting.AQUA);
                }
                case 2 -> {
                    say("Goldor", "Who dares trespass into my domain? Finish my terminals if you can.");
                    terminals.clear();
                    for (int i = 0; i < 4; i++) {
                        double a = Math.PI * 2 * i / 4 + Math.PI / 4;
                        part("terminal" + i, spot(Math.cos(a) * 14, Math.sin(a) * 14), "Terminal", ChatFormatting.GOLD);
                    }
                }
                default -> say("Necron", "Finally, I heard so much about you. The Eye likes you very much.");
            }
        }

        private void crystal(int i) {
            Vec3 at = spot(i == 0 ? -22 : 22, 10);
            Display.ItemDisplay crystal = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, run.level);
            crystal.snapTo(at.x, at.y + 1, at.z, 0, 0);
            ((com.skyrpg.hub.mixin.ItemDisplayAccess) crystal).skyrpg$setItemStack(new ItemStack(Items.END_CRYSTAL));
            ((DisplayAccess) crystal).skyrpg$setBillboard(Display.BillboardConstraints.VERTICAL);
            crystal.addTag(run.tag);
            crystal.addTag(Catacombs.TAG);
            run.level.addFreshEntity(crystal);
            parts.put(crystal.getUUID(), "crystal" + i);
            parts.put(run.label(at.add(0, 2.2, 0), Component.literal("Energy Crystal").withStyle(ChatFormatting.LIGHT_PURPLE)).getUUID(), "label");
        }

        private void pad(int i) {
            part("pad" + i, spot(i == 0 ? -8 : 8, -6), "Energy Pad", ChatFormatting.LIGHT_PURPLE);
        }

        /** A clickable part: a box with a label. */
        private void part(String id, Vec3 at, String label, ChatFormatting color) {
            Interaction box = EntityTypes.INTERACTION.create(run.level, EntitySpawnReason.EVENT);
            if (box == null) {
                return;
            }
            ((InteractionAccess) box).skyrpg$setWidth(1.2f);
            ((InteractionAccess) box).skyrpg$setHeight(1.6f);
            box.snapTo(at.x, at.y, at.z, 0, 0);
            box.addTag(run.tag);
            box.addTag(Catacombs.TAG);
            run.level.addFreshEntity(box);
            parts.put(box.getUUID(), id);
            run.level.setBlock(net.minecraft.core.BlockPos.containing(at).below(),
                    (label.equals("Terminal") ? Blocks.GOLD_BLOCK : label.equals("Pillar") ? Blocks.PRISMARINE_BRICKS : Blocks.PURPUR_BLOCK).defaultBlockState(), 3);
            parts.put(run.label(at.add(0, 2, 0), Component.literal(label).withStyle(color).append(Component.literal("\nright-click")
                    .withStyle(ChatFormatting.GRAY))).getUUID(), "label");
        }

        private void clearParts() {
            for (UUID id : parts.keySet()) {
                Entity e = run.level.getEntity(id);
                if (e != null) {
                    e.discard();
                }
            }
            parts.clear();
            carrying.clear();
        }

        /** Right-clicking a part (pads, pillars, terminals). */
        @Override
        boolean useEntity(ServerPlayer player, Entity entity) {
            String id = parts.get(entity.getUUID());
            if (id == null || boss == null) {
                return false;
            }
            long now = run.level.getGameTime();
            if (id.startsWith("pad") && carrying.containsKey(player.getUUID())) {
                int crystal = carrying.remove(player.getUUID());
                placed.add(crystal);
                run.broadcast(Component.literal(player.getScoreboardName() + " placed an Energy Crystal! (" + placed.size() + "/2)")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                if (placed.size() >= 2) {
                    vulnerableUntil = now + 15 * 20;
                    say("Maxor", "THE LASER! IT BURNS!");
                    placed.clear();
                    run.level.getServer().execute(() -> {
                        crystal(0);
                        crystal(1);
                    });
                }
                return true;
            }
            if (id.startsWith("pillar") && lord == 1) {
                if (now < vulnerableUntil + 200) {
                    player.sendOverlayMessage(Component.literal("The pillar isn't ready yet.").withStyle(ChatFormatting.RED));
                    return true;
                }
                vulnerableUntil = now + 10 * 20;
                say("Storm", "Ouch, that hurt!");
                run.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY(), boss.getZ(), 1, 0, 0, 0, 0);
                return true;
            }
            if (id.startsWith("terminal") && lord == 2) {
                int i = Integer.parseInt(id.substring(8));
                if (terminals.add(i)) {
                    run.broadcast(Component.literal(player.getScoreboardName() + " activated a terminal! (" + terminals.size() + "/4)")
                            .withStyle(ChatFormatting.GOLD));
                    if (terminals.size() == 4) {
                        vulnerableUntil = Long.MAX_VALUE;
                        say("Goldor", "You have done it, you destroyed the factory...");
                    }
                }
                return true;
            }
            return true;
        }

        @Override
        void fight(long now) {
            // Carrying crystals: walk into one to pick it up.
            if (lord == 0) {
                for (ServerPlayer p : targets()) {
                    if (carrying.containsKey(p.getUUID())) {
                        continue;
                    }
                    for (Entity e : run.level.getEntities(p, p.getBoundingBox().inflate(1.0), e -> parts.getOrDefault(e.getUUID(), "").startsWith("crystal"))) {
                        int crystal = Integer.parseInt(parts.remove(e.getUUID()).substring(7));
                        e.discard();
                        carrying.put(p.getUUID(), crystal);
                        p.sendOverlayMessage(Component.literal("You picked up an Energy Crystal! Right-click a pad.").withStyle(ChatFormatting.LIGHT_PURPLE));
                        break;
                    }
                }
            }
            if (boss == null || !boss.isAlive()) {
                return;
            }
            boolean open = now < vulnerableUntil;
            if (open) {
                boss.removeEffect(MobEffects.GLOWING);
            } else if (!boss.hasEffect(MobEffects.GLOWING)) {
                boss.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0, false, false));
            }
            if (now >= nextAttack && randomTarget() instanceof ServerPlayer target) {
                nextAttack = now + (lord == 2 ? 100 : 80);
                if (lord == 2) {
                    // Goldor's tick hurts everyone.
                    for (ServerPlayer p : targets()) {
                        hitAround(p.position(), 0.5, 0.6);
                    }
                } else {
                    run.level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1, target.getZ(), 4, 1, 0.5, 1, 0);
                    hitAround(target.position(), 3, 1.5);
                }
            }
            if (lord == 3) {
                double share = boss.getHealth() / boss.getMaxHealth();
                if (!frenzy80 && share < 0.8 || !frenzy5 && share < 0.05) {
                    if (share < 0.05) {
                        frenzy5 = true;
                    } else {
                        frenzy80 = true;
                    }
                    say("Necron", "Let's make some space!");
                    run.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, boss.getX(), boss.getY(), boss.getZ(), 3, 4, 1, 4, 0);
                    hitAround(boss.position(), 20, 3);
                }
            }
        }

        @Override
        boolean allowDamage(LivingEntity entity, DamageSource source) {
            return entity != boss || run.level.getGameTime() < vulnerableUntil;
        }

        @Override
        boolean allowDeath(LivingEntity entity) {
            if (entity != boss) {
                return true;
            }
            String[] lines = {"I'M TOO YOUNG TO DIE AGAIN!", "I should have known that I stood no chance.", "Necron, forgive me.",
                    "All this, for nothing..."};
            say(LORDS[lord], lines[lord]);
            if (lord < 3) {
                lord++;
                run.level.getServer().execute(this::next);
            } else {
                clearParts();
                win(null);
            }
            return true;
        }
    }

    static List<UUID> list(Set<UUID> set) {
        return new ArrayList<>(set);
    }
}
