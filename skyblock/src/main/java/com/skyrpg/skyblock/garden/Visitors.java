package com.skyrpg.skyblock.garden;

import com.mojang.serialization.JsonOps;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.skyblock.profile.Profile;
import com.skyrpg.skyblock.profile.Profiles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Garden visitors: one arrives every 15 minutes while someone in the profile is online (decided: Hypixel's interval
 * wasn't found), up to five waiting in the Barn's yard. Each asks for one or two of your Garden crops (raw, or enchanted
 * for bigger orders) and pays Copper, Garden XP and Farming XP, more for rarer visitors (Uncommon, Rare, Legendary,
 * Mythic from Garden XIV). A new visitor served gives 1 SkyBlock XP. The SkyMart NPC and Pesthunter Phillip stand in
 * the Barn plot too (all player-shaped Mannequins, kept while someone is in that Garden).
 */
final class Visitors {
    static final String TAG = "skyrpg_garden_npc";
    private static final long INTERVAL_MS = 15 * 60 * 1000L;
    private static final int MAX = 5;
    private static final String[] RARITIES = {"Uncommon", "Rare", "Legendary", "Mythic"};
    private static final int[] RARITY_COLORS = {0x55FF55, 0x5555FF, 0xFFAA00, 0xFF55FF};
    private static final double[] RARITY_SIZE = {1, 1.5, 3, 6};
    private static final double[] RARITY_COPPER = {1, 1, 5, 5};
    private static final String[][] ROSTER = {
            {"Adventurer", "Alchemist", "Andrew", "Anita", "Arthur", "Chunk", "Duke", "Farmer Jon", "Farmhand", "Felix", "Fisherman",
                    "Friendly Hiker", "Gimley", "Guy", "Hornum", "Jack", "Jacob", "Jamie", "Leo", "Liam", "Librarian", "Lumberjack", "Lynn",
                    "Mason", "Plumber Joe", "Rhys", "Ryu", "Shaggy", "Stella", "Terry", "Tom", "Trevor", "Weaponsmith", "Wizard"},
            {"Archaeologist", "Artist", "Bartender", "Bear Pete", "Carpenter", "Cold Enjoyer", "Duncan", "Farm Merchant", "Gemma",
                    "Gold Forger", "Grandma Wolf", "Gwendolyn", "Iron Forger", "Lazy Miner", "Marigold", "Old Man Garry", "Ophelia",
                    "Pet Trainer", "Puzzler", "Queen Mismyla", "Rusty", "Seymour", "Sherry", "Shifty", "Spider Tamer", "Tammy", "Tia"},
            {"Baker", "Beth", "Carrot King", "Chief Scorn", "Elle", "Jerry", "Madame Eleanor", "Queen Nyx", "Seraphine", "Sirius",
                    "Vincent"},
            {"Ludleth", "Maeve", "Ravenous Rhino", "Taylor"}};
    private static final String[] SKINS = {"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};
    /** The crops Garden levels open up, in order (level 1 Wheat ... level 10 Nether Wart). */
    static final String[] CROP_ORDER = {"wheat", "carrot", "potato", "pumpkin", "sugar_cane", "melon", "cactus", "cocoa", "mushroom", "nether_wart"};
    private static final Map<String, String> ENCHANTED = Map.of("WHEAT", "ENCHANTED_WHEAT", "CARROT_ITEM", "ENCHANTED_CARROT",
            "POTATO_ITEM", "ENCHANTED_POTATO", "PUMPKIN", "ENCHANTED_PUMPKIN", "MELON", "ENCHANTED_MELON", "SUGAR_CANE", "ENCHANTED_SUGAR",
            "CACTUS", "ENCHANTED_CACTUS_GREEN", "INK_SACK:3", "ENCHANTED_COCOA", "RED_MUSHROOM", "ENCHANTED_RED_MUSHROOM",
            "NETHER_STALK", "ENCHANTED_NETHER_STALK");

    /** An offer: who, rarity, the items wanted (id -> amount), and the rewards. */
    record Offer(String name, int rarity, Map<String, Integer> wants, int copper, int gardenXp, int farmingXp, String skin) {
        String encode() {
            StringBuilder items = new StringBuilder();
            wants.forEach((id, n) -> items.append(items.isEmpty() ? "" : ";").append(id).append("=").append(n));
            return String.join("|", name, String.valueOf(rarity), items, String.valueOf(copper), String.valueOf(gardenXp),
                    String.valueOf(farmingXp), skin);
        }

        static Offer decode(String s) {
            String[] p = s.split("\\|");
            Map<String, Integer> wants = new java.util.LinkedHashMap<>();
            for (String part : p[2].split(";")) {
                String[] kv = part.split("=");
                wants.put(kv[0], Integer.parseInt(kv[1]));
            }
            return new Offer(p[0], Integer.parseInt(p[1]), wants, Integer.parseInt(p[3]), Integer.parseInt(p[4]), Integer.parseInt(p[5]),
                    p.length > 6 ? p[6] : "steve");
        }
    }

    /** The Mannequins standing in each loaded Garden (by profile). */
    private static final Map<UUID, List<Mannequin>> LIVE = new HashMap<>();

    private Visitors() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(Visitors::tick);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayer server) || level.isClientSide() || !entity.entityTags().contains(TAG)) {
                return InteractionResult.PASS;
            }
            if (!GardenRules.member(server, entity.blockPosition())) {
                return InteractionResult.SUCCESS;
            }
            for (String tag : entity.entityTags()) {
                if (tag.equals(TAG + ":skymart")) {
                    GardenMenu.open(server, GardenMenu.Tab.SKYMART);
                } else if (tag.equals(TAG + ":phillip")) {
                    server.sendSystemMessage(Component.literal("Pesthunter Phillip: Pests? Not in this Garden yet. Keep your crops clean!")
                            .withStyle(ChatFormatting.GRAY));
                } else if (tag.startsWith(TAG + ":visitor:")) {
                    GardenMenu.openVisitor(server, Integer.parseInt(tag.substring((TAG + ":visitor:").length())));
                }
            }
            return InteractionResult.SUCCESS;
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !entity.entityTags().contains(TAG));
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity.entityTags().contains(TAG) && LIVE.values().stream().noneMatch(list -> list.contains(entity))) {
                entity.discard();
            }
        });
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        // Arrivals, for every profile someone is playing.
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Profile profile = Profiles.current(player);
            if (profile == null) {
                continue;
            }
            GardenData.State state = Garden.state(server, profile);
            if (!state.built) {
                continue;
            }
            if (state.nextVisitor == 0) {
                state.nextVisitor = now + 60_000;
            }
            if (now >= state.nextVisitor && state.visitors.size() < MAX) {
                state.visitors.add(roll(state, new Random(now ^ profile.id().getMostSignificantBits())).encode());
                state.nextVisitor = now + INTERVAL_MS;
                GardenData.get(server).setDirty();
                Offer offer = Offer.decode(state.visitors.get(state.visitors.size() - 1));
                player.sendSystemMessage(Component.literal(offer.name() + " has arrived on your Garden!")
                        .withColor(RARITY_COLORS[offer.rarity()]));
                refresh(server, profile);
            }
        }
        // The Mannequins: in Gardens with someone in them; gone from the rest.
        ServerLevel level = Garden.level(server);
        if (level == null) {
            return;
        }
        java.util.Set<UUID> occupied = new java.util.HashSet<>();
        for (ServerPlayer player : level.players()) {
            Profile owner = Garden.ownerAt(player.blockPosition());
            if (owner != null) {
                occupied.add(owner.id());
                if (!LIVE.containsKey(owner.id())) {
                    refresh(server, owner);
                }
            }
        }
        LIVE.entrySet().removeIf(e -> {
            if (occupied.contains(e.getKey())) {
                return false;
            }
            e.getValue().forEach(Entity::discard);
            return true;
        });
    }

    /** Puts the Garden's NPCs where they belong: SkyMart, Phillip, and one per waiting visitor. */
    static void refresh(MinecraftServer server, Profile profile) {
        ServerLevel level = Garden.level(server);
        if (level == null) {
            return;
        }
        List<Mannequin> old = LIVE.remove(profile.id());
        if (old != null) {
            old.forEach(Entity::discard);
        }
        GardenData.State state = Garden.state(server, profile);
        if (!state.built) {
            return;
        }
        BlockPos c = Garden.center(profile);
        List<Mannequin> live = new ArrayList<>();
        LIVE.put(profile.id(), live);
        live.add(mannequin(level, c.offset(-18, 1, -2), 90, "SkyMart", 0xFFAA00, "Copper shop", "noor", TAG + ":skymart"));
        live.add(mannequin(level, c.offset(-14, 1, 10), 90, "Pesthunter Phillip", 0x55FF55, "Pests", "efe", TAG + ":phillip"));
        int[][] spots = {{-8, 18}, {-4, 20}, {0, 21}, {4, 20}, {8, 18}};
        for (int i = 0; i < state.visitors.size() && i < spots.length; i++) {
            Offer offer = Offer.decode(state.visitors.get(i));
            live.add(mannequin(level, c.offset(spots[i][0], 1, spots[i][1]), 180, offer.name(), RARITY_COLORS[offer.rarity()],
                    RARITIES[offer.rarity()] + " visitor", offer.skin(), TAG + ":visitor:" + i));
        }
    }

    private static Mannequin mannequin(ServerLevel level, BlockPos at, float yaw, String name, int color, String role, String skin, String tag) {
        Mannequin npc = EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.COMMAND);
        if (npc == null) {
            return null;
        }
        npc.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        npc.setYHeadRot(yaw);
        npc.setYBodyRot(yaw);
        npc.setCustomName(Component.literal(name).withColor(color));
        npc.setCustomNameVisible(true);
        npc.setNoGravity(true);
        npc.setSilent(true);
        com.skyrpg.skyblock.mixin.MannequinRoleAccess access = (com.skyrpg.skyblock.mixin.MannequinRoleAccess) npc;
        access.skyrpg$setStill(true);
        access.skyrpg$setHideRole(false);
        access.skyrpg$setRole(Component.literal(role).withStyle(ChatFormatting.GRAY));
        npc.addTag(TAG);
        npc.addTag(tag);
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        json.addProperty("texture", "minecraft:entity/player/wide/" + skin);
        ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, json).result().ifPresent(p -> npc.setComponent(DataComponents.PROFILE, p));
        level.addFreshEntity(npc);
        return npc;
    }

    /** A new visitor for this Garden. */
    private static Offer roll(GardenData.State state, Random random) {
        int level = Garden.level(state);
        double roll = random.nextDouble() * 100;
        int rarity = level >= 14 && roll < 2 ? 3 : roll < 10 ? 2 : roll < 40 ? 1 : 0;
        String[] names = ROSTER[rarity];
        String name = names[random.nextInt(names.length)];
        int crops = Math.min(CROP_ORDER.length, Math.max(1, level));
        int kinds = level >= 5 && random.nextBoolean() ? 2 : 1;
        Map<String, Integer> wants = new java.util.LinkedHashMap<>();
        double raw = 0;
        for (int k = 0; k < kinds; k++) {
            String key = CROP_ORDER[random.nextInt(crops)];
            Garden.Crop crop = Garden.CROPS.stream().filter(c -> c.key().equals(key)).findFirst().orElseThrow();
            double amount = 200 * (1 + level) * RARITY_SIZE[rarity] * (0.75 + random.nextDouble() * 0.5) / kinds * crop.perBlock();
            raw += amount / crop.perBlock();
            String item = crop.item();
            int count = (int) Math.ceil(amount);
            if (amount >= 320 && ENCHANTED.containsKey(item)) {
                item = ENCHANTED.get(item);
                count = (int) Math.ceil(amount / 160);
            }
            wants.merge(item, count, Integer::sum);
        }
        int copper = (int) Math.max(2, Math.min(100, Math.round(raw / 1000 * 2 * RARITY_COPPER[rarity])));
        int gardenXp = (int) Math.max(30, Math.round(raw / 40));
        int farmingXp = (int) Math.round(raw * 1.5);
        return new Offer(name, rarity, wants, copper, gardenXp, farmingXp, SKINS[random.nextInt(SKINS.length)]);
    }

    /** Accepting visitor {@code index}'s offer: their items for the rewards. "" when done, else why not. */
    static String accept(ServerPlayer player, Profile profile, int index) {
        GardenData.State state = Garden.state(player.level().getServer(), profile);
        if (index < 0 || index >= state.visitors.size()) {
            return "That visitor has left.";
        }
        Offer offer = Offer.decode(state.visitors.get(index));
        for (Map.Entry<String, Integer> want : offer.wants().entrySet()) {
            if (com.skyrpg.skyblock.sack.Sacks.countAnywhere(player, want.getKey()) < want.getValue()) {
                return "You need " + want.getValue() + " " + name(want.getKey()) + ".";
            }
        }
        offer.wants().forEach((id, n) -> com.skyrpg.skyblock.sack.Sacks.takeAnywhere(player, id, n));
        state.visitors.remove(index);
        state.copper += offer.copper();
        boolean first = !state.served.containsKey(offer.name());
        state.served.merge(offer.name(), 1, Integer::sum);
        SkillLevels.giveXp(player, SkillLevels.FARMING, offer.farmingXp());
        Notice.Panel panel = Notice.of(Notice.Kind.FARMING).tag("OFFER ACCEPTED").panel()
                .line(Component.literal(" +" + offer.copper() + " Copper").withColor(0xE07B39))
                .line(Component.literal(" +" + Msg.number(offer.gardenXp()) + " Garden XP  ·  +" + Msg.number(offer.farmingXp()) + " Farming XP")
                        .withStyle(ChatFormatting.GRAY));
        if (first) {
            SkyBlockXp.give(player, 1, "new Garden visitor");
            panel.line(Component.literal(" +1 SkyBlock XP (a new visitor)").withStyle(ChatFormatting.AQUA));
        }
        panel.send(player, Component.literal(offer.name()).withColor(RARITY_COLORS[offer.rarity()])
                .append(Component.literal(" is happy!").withStyle(ChatFormatting.GREEN)));
        Msg.playSound(player, SoundEvents.VILLAGER_YES, 1f, 1f);
        Garden.giveXp(player, state, offer.gardenXp());
        GardenData.get(player.level().getServer()).setDirty();
        refresh(player.level().getServer(), profile);
        return "";
    }

    static void decline(ServerPlayer player, Profile profile, int index) {
        GardenData.State state = Garden.state(player.level().getServer(), profile);
        if (index >= 0 && index < state.visitors.size()) {
            Offer offer = Offer.decode(state.visitors.remove(index));
            player.sendSystemMessage(Component.literal(offer.name() + " left.").withStyle(ChatFormatting.GRAY));
            GardenData.get(player.level().getServer()).setDirty();
            refresh(player.level().getServer(), profile);
        }
    }

    static String name(String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? id : def.name();
    }

    static int color(int rarity) {
        return RARITY_COLORS[rarity];
    }

    static String rarity(int rarity) {
        return RARITIES[rarity];
    }
}
