package com.skyrpg.skyblock.alchemy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.accessory.AccessoryBag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/**
 * Drinking Hypixel's potions: right-click one for its effect (a splash potion gives it to every player within 8 blocks,
 * you too). An effect lasts its potion's time, plus 1% per Alchemy level and the Potion Affinity accessories' 10/25/50%;
 * it counts down while you're online and stays through death. A new potion of the same effect replaces it, unless it's
 * a lower level. Effects give stats (Speed, Strength, Health Regen, Wisdom...), Absorption, healing and Mana every second,
 * vanilla's Night Vision, Invisibility, Blindness and Jump Boost, mob attacks that miss (Agility, Dodge), bow damage
 * (Archery), less fire damage and drowning. {@code /effects} lists them.
 */
public final class PotionEffects {
    /** One active effect: its potion, level and ticks left. */
    public record Active(String potion, int level, int ticks) {
        static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("potion").forGetter(Active::potion),
                Codec.INT.fieldOf("level").forGetter(Active::level),
                Codec.INT.fieldOf("ticks").forGetter(Active::ticks)).apply(i, Active::new));
    }

    public static final AttachmentType<List<Active>> ACTIVE = AttachmentRegistry.create(SkyRpgSkyblock.id("potion_effects"),
            builder -> builder.persistent(Active.CODEC.listOf()).copyOnDeath());
    private static final double SPLASH_RADIUS = 8;

    private PotionEffects() {
    }

    static void init() {
        Identifier early = SkyRpgSkyblock.id("potions_early");
        UseItemCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(early, (player, level, hand) -> use(player, hand));
        UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(early, (player, level, hand, hit) ->
                level.getBlockEntity(hit.getBlockPos()) instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity
                        ? InteractionResult.PASS : use(player, hand));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                server.getPlayerList().getPlayers().forEach(PotionEffects::second);
            }
        });
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Potions";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                for (Active active : active(player)) {
                    Potions.Kind kind = Potions.kind(active.potion());
                    if (kind == null) {
                        continue;
                    }
                    kind.level(active.level()).stats().forEach((id, value) -> {
                        StatType stat = StatTypes.get(id);
                        if (stat != null) {
                            out.add(stat, value);
                        }
                    });
                }
            }
        });
        IncomingDamage.registerHit(PotionEffects::damage);
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("effects")
                .executes(ctx -> {
                    show(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }

    public static List<Active> active(ServerPlayer player) {
        return player.getAttachedOrElse(ACTIVE, List.of());
    }

    private static InteractionResult use(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Potions.Brew brew = Potions.read(stack);
        if (brew == null || !Potions.ID.equals(com.skyrpg.items.SkyItems.skyblockId(stack))) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResult.SUCCESS;
        }
        Potions.Kind kind = Potions.kind(brew.potion());
        if (kind == null) {
            server.sendOverlayMessage(Component.literal("Brew it into a potion first!").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        List<ServerPlayer> targets = new ArrayList<>();
        if (brew.splash()) {
            server.level().players().stream().filter(p -> p.distanceToSqr(server) <= SPLASH_RADIUS * SPLASH_RADIUS).forEach(targets::add);
        } else {
            targets.add(server);
        }
        int ticks = Potions.ticks(brew);
        for (ServerPlayer target : targets) {
            give(target, kind, brew.level(), ticks);
        }
        stack.shrink(1);
        Msg.playSound(server, brew.splash() ? SoundEvents.SPLASH_POTION_BREAK : SoundEvents.GENERIC_DRINK.value(), 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** An effect for a player: its time stretched by their Alchemy level and Potion Affinity accessories. */
    private static void give(ServerPlayer player, Potions.Kind kind, int level, int ticks) {
        Potions.Level at = kind.level(level);
        Double heal = at.special().get("instant_heal");
        if (heal != null) {
            player.heal((float) (heal / StatTypes.HEALTH_SCALE));
        }
        Double absorption = at.special().get("absorption");
        if (absorption != null) {
            player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), (float) (absorption / StatTypes.HEALTH_SCALE)));
        }
        if (ticks > 0) {
            double longer = 1 + SkillLevels.level(player, BrewMenu.ALCHEMY) / 100.0 + affinity(player);
            int total = (int) Math.round(ticks * longer);
            List<Active> list = new ArrayList<>(active(player));
            Active current = list.stream().filter(a -> a.potion().equals(kind.id())).findFirst().orElse(null);
            if (current != null && current.level() > level) {
                player.sendOverlayMessage(Component.literal("You already have a stronger " + kind.name() + " effect!").withStyle(ChatFormatting.RED));
                return;
            }
            list.removeIf(a -> a.potion().equals(kind.id()));
            list.add(new Active(kind.id(), level, total));
            player.setAttached(ACTIVE, List.copyOf(list));
            vanilla(player, kind.id(), level, at);
            Stats.refresh(player);
        }
        Notice.of(Notice.Kind.SKILL).tag("BUFF!").color(0x55FF55).send(player, Component.literal("You have gained ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(kind.name() + " " + Potions.roman(level)).withColor(kind.color()))
                .append(Component.literal("! Type /effects to view your active effects.").withStyle(ChatFormatting.GRAY)));
    }

    /** Potion Affinity accessories: 10%, 25% or 50% longer (only the highest counts). */
    private static double affinity(ServerPlayer player) {
        if (AccessoryBag.has(player, "ARTIFACT_POTION_AFFINITY")) {
            return 0.5;
        }
        if (AccessoryBag.has(player, "RING_POTION_AFFINITY")) {
            return 0.25;
        }
        return AccessoryBag.has(player, "POTION_AFFINITY_TALISMAN") ? 0.1 : 0;
    }

    /** Every second: time runs down, healing and Mana, vanilla's effects kept up. */
    private static void second(ServerPlayer player) {
        List<Active> list = active(player);
        if (list.isEmpty()) {
            return;
        }
        List<Active> left = new ArrayList<>();
        boolean ended = false;
        for (Active active : list) {
            Potions.Kind kind = Potions.kind(active.potion());
            int ticks = active.ticks() - 20;
            if (kind == null || ticks <= 0) {
                ended = true;
                if (kind != null) {
                    player.sendOverlayMessage(Component.literal("Your " + kind.name() + " " + Potions.roman(active.level()) + " effect has ended.")
                            .withStyle(ChatFormatting.GRAY));
                }
                continue;
            }
            left.add(new Active(active.potion(), active.level(), ticks));
            Potions.Level at = kind.level(active.level());
            Double heal = at.special().get("heal_per_second");
            if (heal != null) {
                player.heal((float) (heal / StatTypes.HEALTH_SCALE));
            }
            Double mana = at.special().get("mana_per_second");
            if (mana != null) {
                // Spending a negative amount gives it (up to the maximum).
                Stats.spendMana(player, -mana);
            }
            vanilla(player, kind.id(), active.level(), at);
        }
        player.setAttached(ACTIVE, List.copyOf(left));
        if (ended) {
            Stats.refresh(player);
        }
    }

    /** Vanilla's own effects for the ones that are vanilla's: refreshed every second, so they end with the potion's. */
    private static void vanilla(ServerPlayer player, String potion, int level, Potions.Level at) {
        if (at.special().containsKey("night_vision")) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, true));
        }
        if (at.special().containsKey("invisibility")) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, true, false, true));
        }
        if (at.special().containsKey("blindness")) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, true, false, true));
        }
        Double jump = at.special().get("jump");
        if (jump != null) {
            int amplifier = jump > 0 ? jump.intValue() - 1 : level - 1;
            player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 40, amplifier, true, false, true));
        }
    }

    private static double special(ServerPlayer player, String key) {
        double best = 0;
        for (Active active : active(player)) {
            Potions.Kind kind = Potions.kind(active.potion());
            if (kind != null) {
                best = Math.max(best, kind.level(active.level()).special().getOrDefault(key, 0.0));
            }
        }
        return best;
    }

    private static double damage(LivingEntity target, DamageSource source) {
        double multiplier = 1;
        if (source.getEntity() instanceof ServerPlayer attacker && source.getDirectEntity() instanceof AbstractArrow) {
            multiplier *= 1 + special(attacker, "bow_damage") / 100;
        }
        if (target instanceof ServerPlayer player && SkyBlockWorlds.isSkyBlock(player.level()) && !active(player).isEmpty()) {
            if (source.getEntity() instanceof Mob && player.getRandom().nextDouble() * 100 < special(player, "miss")) {
                return 0;
            }
            if (source.is(DamageTypeTags.IS_FIRE)) {
                multiplier *= 1 - special(player, "fire_reduction") / 100;
            }
            if (source.is(DamageTypes.DROWN) && player.getRandom().nextDouble() * 100 < special(player, "drown_chance")) {
                return 0;
            }
        }
        return multiplier;
    }

    private static void show(ServerPlayer player) {
        List<Active> list = new ArrayList<>(active(player));
        if (list.isEmpty()) {
            Notice.of(Notice.Kind.INFO).tag("EFFECTS").send(player, Component.literal("You have no active effects. Drink a potion!")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        list.sort(Comparator.comparingInt(Active::ticks).reversed());
        Notice.Panel panel = Notice.of(Notice.Kind.INFO).tag("EFFECTS").panel();
        for (Active active : list) {
            Potions.Kind kind = Potions.kind(active.potion());
            if (kind == null) {
                continue;
            }
            panel.line(Component.literal(kind.name() + " " + Potions.roman(active.level())).withColor(kind.color())
                    .append(Component.literal("  " + Potions.duration(active.ticks())).withStyle(ChatFormatting.WHITE)));
        }
        panel.send(player, Component.literal(list.size() + " active").withStyle(ChatFormatting.GRAY));
    }
}
