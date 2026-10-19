package com.skyrpg.skyblock.ability;

import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SetBonuses;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.skyblock.island.IslandRules;
import com.skyrpg.skyblock.island.Islands;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The small item abilities: Parley (the Aspect of the Jerry's quote), Phwomp (the Carnival Dart Tube's dart), Weather
 * (the Weather Stick, cycling clear, rain and thunder for everyone on your island), Farmer's Speed (the Rancher's Boots'
 * Speed cap, raised with left clicks) and Spooker (punching a player with the Great Spook Staff in the full Great Spook
 * Armor). Nasty Bite is in {@link Shortbows}.
 */
final class MiscAbilities {
    private static final String[] JERRY = {"Hello!", "Hi there!", "Jerry!", "What's up?", "Have you seen my cheese?",
            "I'm a Jerry. Are you a Jerry?", "Don't be a Jerry.", "Jerry? Jerry!", "Nice weather for a Jerry.", "Hmm?"};
    private static final String[] WEATHER_NAMES = {"Clear", "Rain", "Thunderstorm"};
    /** The weather each island's stick has set (island cell to index). */
    private static final Map<Integer, Integer> WEATHER_SET = new HashMap<>();
    private static final String SPEED_CAP = "ranchers_speed";
    private static final Map<UUID, Double> CUT = new HashMap<>();
    private static final Map<UUID, Long> SPOOKED = new HashMap<>();

    private MiscAbilities() {
    }

    static void init() {
        Abilities.register("parley", (player, ability) -> {
            player.sendSystemMessage(Component.literal("Jerry: ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(JERRY[player.getRandom().nextInt(JERRY.length)]).withStyle(ChatFormatting.WHITE)));
            Msg.playSound(player, SoundEvents.VILLAGER_YES, 1.0f, 1.3f);
            return true;
        });
        Abilities.register("phwomp", MiscAbilities::phwomp);
        Abilities.register("weather", MiscAbilities::weather);
        Abilities.register("farmer_s_speed", MiscAbilities::farmersSpeed);
        // The staff's own punch is the ability (below); a swing at the air does nothing.
        Abilities.register("spooker", (player, ability) -> false);
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (player instanceof ServerPlayer spooker && target instanceof ServerPlayer victim && victim != spooker) {
                spook(spooker, victim);
            }
            return InteractionResult.PASS;
        });
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Rancher's Boots";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
                ItemDefinition def = SkyItems.definition(boots);
                double cut = 0;
                if (def != null && "RANCHERS_BOOTS".equals(def.skyblockId())) {
                    CompoundTag tag = ItemMods.tag(boots);
                    int cap = tag == null ? 0 : tag.getIntOr(SPEED_CAP, 0);
                    double speed = Stats.get(player, StatTypes.SPEED) - CUT.getOrDefault(player.getUUID(), 0.0);
                    if (cap > 0 && speed > cap) {
                        cut = cap - speed;
                        out.add(StatTypes.SPEED, cut);
                    }
                }
                CUT.put(player.getUUID(), cut);
            }
        });
    }

    /** A dart: a short line of sparks, and nothing else (it only hurts the Carnival's Zombie Shootout). */
    private static boolean phwomp(ServerPlayer player, ItemDefinition.Ability ability) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        for (double d = 1; d < 16; d += 0.5) {
            Vec3 at = eye.add(look.scale(d));
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.8f, 1.6f);
        return true;
    }

    /** The Weather Stick: on your own island, the next weather, for the players there (their client's sky; the world's is untouched). */
    private static boolean weather(ServerPlayer player, ItemDefinition.Ability ability) {
        if (!Islands.isIslands(player.level()) || !IslandRules.isMember(player, player.blockPosition())) {
            player.sendOverlayMessage(Msg.text("You can only change the weather on your own island.", ChatFormatting.RED));
            return false;
        }
        int cell = Islands.cellAt(player.blockPosition());
        int next = (WEATHER_SET.getOrDefault(cell, 0) + 1) % WEATHER_NAMES.length;
        WEATHER_SET.put(cell, next);
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other.level() == player.level() && Islands.cellAt(other.blockPosition()) == cell) {
                show(other, next);
            }
        }
        return true;
    }

    private static void show(ServerPlayer player, int weather) {
        boolean rain = weather >= 1;
        player.connection.send(new ClientboundGameEventPacket(rain ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0));
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain ? 1 : 0));
        player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, weather >= 2 ? 1 : 0));
        player.sendOverlayMessage(Msg.text("Weather: " + WEATHER_NAMES[weather], ChatFormatting.AQUA));
    }

    /** Farmer's Speed: each left click raises the Speed the boots allow by 25 (from 100 up to 400, then no cap). */
    private static boolean farmersSpeed(ServerPlayer player, ItemDefinition.Ability ability) {
        ItemStack stack = player.getMainHandItem();
        CompoundTag tag = ItemMods.tag(stack);
        int cap = tag == null ? 0 : tag.getIntOr(SPEED_CAP, 0);
        int next = cap == 0 ? 100 : cap >= 400 ? 0 : cap + 25;
        ItemMods.edit(stack, data -> data.putInt(SPEED_CAP, next));
        player.sendOverlayMessage(Msg.text(next == 0 ? "Maximum Speed: none" : "Maximum Speed: " + next + " " + StatTypes.SPEED.symbol(), ChatFormatting.GREEN));
        Stats.refresh(player);
        return false;
    }

    /** Punching a player with the Great Spook Staff in the full Great Spook Armor scares them. */
    private static void spook(ServerPlayer spooker, ServerPlayer victim) {
        ItemDefinition def = SkyItems.definition(spooker.getMainHandItem());
        long now = spooker.level().getServer().getTickCount();
        if (def == null || !"GREAT_SPOOK_STAFF".equals(def.skyblockId()) || SetBonuses.count(spooker, "spooky") < 4
                || now - SPOOKED.getOrDefault(spooker.getUUID(), -100L) < 20) {
            return;
        }
        SPOOKED.put(spooker.getUUID(), now);
        victim.sendSystemMessage(Component.literal("BOO! ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal(spooker.getName().getString() + " spooked you!").withStyle(ChatFormatting.RED)));
        Msg.playSound(victim, SoundEvents.GHAST_SCREAM, 1.0f, 0.6f);
        spooker.sendOverlayMessage(Msg.text("You spooked " + victim.getName().getString() + "!", ChatFormatting.GOLD));
    }
}
