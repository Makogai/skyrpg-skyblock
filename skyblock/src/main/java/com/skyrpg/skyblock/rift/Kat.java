package com.skyrpg.skyblock.rift;

import com.mojang.serialization.Codec;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.pet.SkyBlockPets;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Kat's Infested House in West Village (Hypixel's): Kat lends you the Turbomax Vacuum Cleaner; hold right-click to suck
 * up the flies, spiders and silverfish crawling round the front room (the bag holds 8), then empty it into the Vermin Bin.
 * What you've deposited unlocks her trades: the Disinfestor Gloves (4 of each, 12 Shadow Crux), the Vermin Belt (15
 * flies, a Leech Belt), the Rift Ferret (20 flies, 10 spiders, 20 silverfish; 12 Riftwart Roots and 3,000 Motes), the
 * Key to the Infested House Soul (40 silverfish, 500 Motes) and the Fake Neuroscience Degree (30 / 40 / 60; 10 Volt
 * Crux, 10 Shadow Crux and 5,000 Motes). The key opens the locked back room and its Enigma Soul.
 */
public final class Kat {
    static final String VERMIN_TAG = "skyrpg_rift_vermin";
    static final String KEY = "KEY_TO_KAT_SOUL";
    static final String VACUUM = "TURBOMAX_VACUUM";
    private static final int BAG = 8;
    private static final int PER_KIND = 2;
    private static final List<String> KINDS = List.of("fly", "spider", "silverfish");

    /** What you've deposited ("fly", "spider", "silverfish") and what's in the vacuum's bag ("bag_fly"...). */
    static final AttachmentType<Map<String, Integer>> VERMIN = AttachmentRegistry.create(SkyRpgSkyblock.id("rift_vermin"),
            builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

    private record Trade(String item, String needs, int fly, int spider, int silverfish, long motes, List<String[]> costs) {
    }

    private static final List<Trade> TRADES = List.of(
            new Trade("DISINFESTOR_GLOVES", "", 4, 4, 4, 0, List.<String[]>of(new String[] {"SHADOW_CRUX", "12"})),
            new Trade("VERMIN_BELT", "DISINFESTOR_GLOVES", 15, 0, 0, 0, List.<String[]>of(new String[] {"LEECH_BELT", "1"})),
            new Trade("RIFT_FERRET", "", 20, 10, 20, 3_000, List.<String[]>of(new String[] {"RIFTWART_ROOTS", "12"})),
            new Trade(KEY, "", 0, 0, 40, 500, List.of()),
            new Trade("FAKE_NEUROSCIENCE_DEGREE", "", 30, 40, 60, 5_000,
                    List.<String[]>of(new String[] {"VOLT_CRUX", "10"}, new String[] {"SHADOW_CRUX", "10"})));

    private static final List<Mob> LIVE = new ArrayList<>();

    private Kat() {
    }

    static void init() {
        Identifier first = SkyRpgSkyblock.id("rift_first");
        UseBlockCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(first, (player, level, hand, hit) -> {
            if (!Rift.isRift(level)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                // The Hub's protection turns this click away on the client: send it on, the server decides.
                return level.getBlockState(hit.getBlockPos()).is(Blocks.COMPOSTER) ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            return player instanceof ServerPlayer server && use(server, hit.getBlockPos()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            // Vermin bite nobody, and only the vacuum catches them.
            if (source.getEntity() != null && source.getEntity().entityTags().contains(VERMIN_TAG)) {
                return false;
            }
            return !entity.entityTags().contains(VERMIN_TAG) || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(VERMIN_TAG) && !LIVE.contains(entity)) {
                entity.discard();
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(Kat::tick);
    }

    private static Map<String, Integer> vermin(ServerPlayer player) {
        return player.getAttachedOrElse(VERMIN, Map.of());
    }

    private static void add(ServerPlayer player, String key, int amount) {
        Map<String, Integer> map = new HashMap<>(vermin(player));
        map.merge(key, amount, Integer::sum);
        player.setAttached(VERMIN, Map.copyOf(map));
    }

    private static int bag(ServerPlayer player) {
        return KINDS.stream().mapToInt(k -> vermin(player).getOrDefault("bag_" + k, 0)).sum();
    }

    // ---------------------------------------------------------------- the vermin

    private static String kind(Entity entity) {
        return entity.getType() == EntityTypes.BAT ? "fly" : entity.getType() == EntityTypes.CAVE_SPIDER ? "spider" : "silverfish";
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel level = Rift.level(server);
        Rift.Spot house = Rift.spots().get("infested_house");
        LIVE.removeIf(mob -> !mob.isAlive());
        if (level == null || house == null) {
            return;
        }
        AABB box = new AABB(-107, 69, -66, -98, 72, -62);
        boolean near = !level.getPlayers(p -> p.position().distanceToSqr(house.pos()) < 32 * 32).isEmpty();
        if (!near) {
            LIVE.forEach(Entity::discard);
            LIVE.clear();
            return;
        }
        for (Mob mob : LIVE) {
            if (!box.contains(mob.position())) {
                mob.snapTo(house.pos().x, house.pos().y + 0.5, house.pos().z, mob.getYRot(), 0);
            }
        }
        for (String kind : KINDS) {
            long have = LIVE.stream().filter(m -> kind(m).equals(kind)).count();
            if (have >= PER_KIND) {
                continue;
            }
            EntityType<? extends Mob> type = kind.equals("fly") ? EntityTypes.BAT : kind.equals("spider") ? EntityTypes.CAVE_SPIDER : EntityTypes.SILVERFISH;
            Mob mob = type.create(level, EntitySpawnReason.EVENT);
            if (mob == null) {
                continue;
            }
            double x = box.minX + 0.5 + level.getRandom().nextDouble() * (box.getXsize() - 1);
            double z = box.minZ + 0.5 + level.getRandom().nextDouble() * (box.getZsize() - 1);
            mob.snapTo(x, kind.equals("fly") ? 70.5 : 69, z, level.getRandom().nextFloat() * 360, 0);
            mob.setCustomName(Component.literal(kind.equals("fly") ? "Fly" : kind.equals("spider") ? "Spider" : "Silverfish").withStyle(ChatFormatting.GRAY));
            mob.setPersistenceRequired();
            mob.addTag(VERMIN_TAG);
            if (mob instanceof net.minecraft.world.entity.ambient.Bat bat) {
                bat.setResting(false);
            }
            LIVE.add(mob);
            level.addFreshEntity(mob);
        }
    }

    /** The Turbomax Vacuum Cleaner's Vacuum (held right-click repeats it): pulls vermin in, and bags the close ones. */
    public static boolean vacuum(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        int bagged = bag(player);
        boolean any = false;
        for (Mob mob : List.copyOf(LIVE)) {
            Vec3 to = mob.position().add(0, mob.getBbHeight() / 2, 0).subtract(eye);
            if (to.length() > 8 || look.dot(to.normalize()) < 0.7) {
                continue;
            }
            any = true;
            if (to.length() < 3.2) {
                if (bagged >= BAG) {
                    player.sendOverlayMessage(Component.literal("Your vacuum bag is full! Empty it into the Vermin Bin.").withStyle(ChatFormatting.RED));
                    return true;
                }
                add(player, "bag_" + kind(mob), 1);
                bagged++;
                LIVE.remove(mob);
                level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.3, mob.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
                mob.discard();
                Msg.playSound(player, SoundEvents.ITEM_PICKUP, 0.8f, 0.6f);
                player.sendOverlayMessage(Component.literal("Vacuum Bag: " + bagged + "/" + BAG).withStyle(ChatFormatting.YELLOW));
            } else {
                mob.setDeltaMovement(to.normalize().scale(-0.6));
                mob.needsSync = true;
            }
            for (int i = 1; i <= 5; i++) {
                Vec3 p = eye.add(to.scale(i / 6.0));
                level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
            }
        }
        if (!any) {
            level.sendParticles(ParticleTypes.CLOUD, eye.x + look.x * 2, eye.y + look.y * 2, eye.z + look.z * 2, 3, 0.2, 0.2, 0.2, 0.01);
        }
        return true;
    }

    // ---------------------------------------------------------------- the bin, the locked door

    private static boolean use(ServerPlayer player, BlockPos pos) {
        Rift.Spot bin = Rift.spots().get("vermin_bin");
        Rift.Spot door = Rift.spots().get("kat_door");
        Rift.Spot room = Rift.spots().get("kat_room");
        if (bin != null && pos.equals(BlockPos.containing(bin.pos()))) {
            int bagged = bag(player);
            if (bagged == 0) {
                player.sendOverlayMessage(Component.literal("Your vacuum bag is empty.").withStyle(ChatFormatting.GRAY));
                return true;
            }
            for (String kind : KINDS) {
                int n = vermin(player).getOrDefault("bag_" + kind, 0);
                if (n > 0) {
                    add(player, kind, n);
                    add(player, "bag_" + kind, -n);
                }
            }
            Map<String, Integer> v = vermin(player);
            player.sendSystemMessage(Component.literal("You emptied " + bagged + " vermin into the bin. ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("Flies " + v.getOrDefault("fly", 0) + " · Spiders " + v.getOrDefault("spider", 0) + " · Silverfish "
                            + v.getOrDefault("silverfish", 0)).withStyle(ChatFormatting.GRAY)));
            Msg.playSound(player, SoundEvents.COMPOSTER_FILL_SUCCESS, 1f, 0.8f);
            return true;
        }
        if (door == null || room == null || pos.distToCenterSqr(door.pos().add(0, 0.5, 1)) > 2.5 * 2.5) {
            return false;
        }
        BlockState state = player.level().getBlockState(pos);
        if (!state.is(BlockTags.BUTTONS) && !state.is(Blocks.IRON_DOOR)) {
            return false;
        }
        if (player.getZ() > room.pos().z - 1.5) {
            player.teleportTo(door.pos().x, door.pos().y, door.pos().z);
        } else if (Sacks.countAnywhere(player, KEY) > 0) {
            player.teleportTo(room.pos().x, room.pos().y, room.pos().z);
            Msg.playSound(player, SoundEvents.IRON_DOOR_OPEN, 1f, 1f);
        } else {
            player.sendOverlayMessage(Component.literal("Locked. Kat has the key.").withStyle(ChatFormatting.RED));
        }
        return true;
    }

    // ---------------------------------------------------------------- Kat

    static void open(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            Map<String, Integer> v = vermin(p);
            int fly = v.getOrDefault("fly", 0);
            int spider = v.getOrDefault("spider", 0);
            int silverfish = v.getOrDefault("silverfish", 0);
            boolean vacuum = Sacks.countAnywhere(p, VACUUM) > 0;
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("Turbomax Vacuum Cleaner", "Deposited: " + fly + " flies, " + spider + " spiders, " + silverfish + " silverfish",
                    0xFFAA00, !vacuum, vacuum ? "Held" : "Borrow", RiftNpcs.icon(VACUUM), "", List.of(), bag(p) / (float) BAG, "Bag " + bag(p) + "/" + BAG));
            for (Trade trade : TRADES) {
                boolean have = Sacks.countAnywhere(p, trade.item()) > 0;
                boolean ready = fly >= trade.fly() && spider >= trade.spider() && silverfish >= trade.silverfish()
                        && (trade.needs().isEmpty() || Sacks.countAnywhere(p, trade.needs()) > 0 || RiftFlags.has(p, "kat:" + trade.needs()));
                List<Chip> price = new ArrayList<>();
                if (trade.motes() > 0) {
                    price.add(Chip.price("motes", trade.motes(), Motes.get(p)));
                }
                for (String[] cost : trade.costs()) {
                    price.add(Chip.item(RiftNpcs.icon(cost[0]), Integer.parseInt(cost[1]), Sacks.countAnywhere(p, cost[0])));
                }
                String needs = (trade.fly() > 0 ? trade.fly() + " flies " : "") + (trade.spider() > 0 ? trade.spider() + " spiders " : "")
                        + (trade.silverfish() > 0 ? trade.silverfish() + " silverfish " : "") + "deposited"
                        + (trade.needs().isEmpty() ? "" : ", the Disinfestor Gloves first");
                ItemStack icon = trade.item().equals("RIFT_FERRET") ? SkyBlockPets.create("RIFT_FERRET", "EPIC", 0) : RiftNpcs.icon(trade.item());
                String title = icon.isEmpty() ? trade.item() : icon.getHoverName().getString();
                cards.add(new CardView.Card(title, ready ? (RiftFlags.has(p, "kat:" + trade.item()) ? "Bought" : "Unlocked") : needs, 0xFFFFFF,
                        ready && !RiftFlags.has(p, "kat:" + trade.item()), have ? "Owned" : "Buy", icon, "", price, -1, ""));
            }
            return new CardView("RIFT", "Kat", "Infested House", npc, List.of(Chip.balance("motes", Motes.get(p))), cards,
                    "I'm a neuroscientist, not an exterminator. Please hurry.", 1, 36);
        }, (p, card) -> {
            if (card == 0) {
                if (Sacks.countAnywhere(p, VACUUM) == 0) {
                    give(p, VACUUM);
                    say(p, "Here, the Turbomax. Hold it down at them. Then the bin, please. I'm not touching that bin.");
                }
                return "";
            }
            Trade trade = TRADES.get(card - 1);
            Map<String, Integer> v = vermin(p);
            if (RiftFlags.has(p, "kat:" + trade.item())) {
                return "You already bought that.";
            }
            if (v.getOrDefault("fly", 0) < trade.fly() || v.getOrDefault("spider", 0) < trade.spider() || v.getOrDefault("silverfish", 0) < trade.silverfish()) {
                return "Deposit more vermin first.";
            }
            if (!trade.needs().isEmpty() && !RiftFlags.has(p, "kat:" + trade.needs())) {
                return "Get the Disinfestor Gloves first.";
            }
            if (Motes.get(p) < trade.motes()) {
                return "You need " + Msg.number(trade.motes()) + " Motes.";
            }
            for (String[] cost : trade.costs()) {
                if (Sacks.countAnywhere(p, cost[0]) < Integer.parseInt(cost[1])) {
                    return "You need " + cost[1] + " " + RiftNpcs.icon(cost[0]).getHoverName().getString() + ".";
                }
            }
            Motes.take(p, trade.motes());
            for (String[] cost : trade.costs()) {
                Sacks.takeAnywhere(p, cost[0], Integer.parseInt(cost[1]));
            }
            RiftFlags.set(p, "kat:" + trade.item());
            if (trade.item().equals("RIFT_FERRET")) {
                ItemStack pet = SkyBlockPets.create("RIFT_FERRET", "EPIC", 0);
                if (!p.getInventory().add(pet)) {
                    p.drop(pet, false, net.minecraft.util.Prediction.SERVER_ONLY);
                }
            } else {
                give(p, trade.item());
            }
            Notice.of(Notice.Kind.SUCCESS).tag("KAT").send(p, Component.literal("Kat hands you " + RiftNpcs.icon(trade.item()).getHoverName().getString()
                    + ".").withStyle(ChatFormatting.LIGHT_PURPLE));
            return "";
        });
    }

    private static void say(ServerPlayer player, String line) {
        player.sendSystemMessage(Component.literal("[NPC] Kat: ").withStyle(ChatFormatting.YELLOW).append(Component.literal(line).withStyle(ChatFormatting.WHITE)));
    }

    private static void give(ServerPlayer player, String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null) {
            return;
        }
        ItemStack stack = SkyItems.create(def, 1);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }
}
