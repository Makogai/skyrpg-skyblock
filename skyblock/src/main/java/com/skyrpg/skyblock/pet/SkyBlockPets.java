package com.skyrpg.skyblock.pet;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.pets.Pets;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * Hypixel's pets (87, from tools/skyblock/import_pets.py): a pet is Hypixel's {@code PET} item, its {@code petInfo}
 * (type, tier, exp) in its custom data, a head with the pet's skin. Right-click one to add it to your pets; the Pets
 * menu summons one (it floats at your shoulder: the pets mod's synced active pet) and takes pets back out as items.
 * <ul>
 *   <li>Levels 1 to 100 (the Golden Dragon to 200) on Hypixel's XP table, a rarer pet starting further up it.</li>
 *   <li>Stats and ability numbers go in a straight line from level 1 to 100, per rarity (NEU's petnums).</li>
 *   <li>The active pet gets your skill XP: all of its own skill's (one and a half times for Mining and Fishing pets),
 *       a quarter of the others', a twelfth of Alchemy's and Enchanting's; +1% a Taming level (Zoologist). What it gets
 *       is also your Taming XP.</li>
 * </ul>
 * Abilities show on the pet with their numbers; {@link PetAbilities} makes the ones it knows work.
 */
public final class SkyBlockPets {
    public static final String PET_ID = "PET";
    /** The Phoenix is the only SPECIAL and VERY_SPECIAL pet. */
    public static final String[] TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "SPECIAL", "VERY_SPECIAL"};
    private static final ChatFormatting[] TIER_COLORS = {ChatFormatting.WHITE, ChatFormatting.GREEN, ChatFormatting.BLUE,
            ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.LIGHT_PURPLE, ChatFormatting.RED, ChatFormatting.RED};
    private static final int EXP_SHARE_SLOTS = 3;
    private static final double EXP_SHARE_BASE = 20;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Z_0-9]+)}");

    /** One pet: its type, skill, head skin, top level, and per rarity the lore and the numbers at level 1 and 100. */
    record PetDef(String id, String name, String skill, String skin, int maxLevel, Map<String, Tier> tiers) {
    }

    record Tier(List<String> lore, Map<String, Double> statsAt1, Map<String, Double> statsAt100, double[] numbersAt1, double[] numbersAt100) {
    }

    /** The stored pets (items), per profile; the active one is the pets mod's {@link Pets#ACTIVE}. */
    static final AttachmentType<List<ItemStack>> STORED = AttachmentRegistry.create(SkyRpgSkyblock.id("skyblock_pets"),
            builder -> builder.initializer(List::of).persistent(ItemStack.OPTIONAL_CODEC.listOf()).copyOnDeath());

    private static Map<String, PetDef> pets = Map.of();
    private static double[] levelXp = new double[0];
    private static Map<String, Integer> offsets = Map.of();
    private static Map<String, double[]> customLevels = Map.of();

    private SkyBlockPets() {
    }

    public static void init() {
        STORED.identifier();
        JsonDataLoader.register(SkyRpgSkyblock.id("skyblock_pets"), "skyrpg/skyblock_pets", SkyBlockPets::loadPets);
        JsonDataLoader.register(SkyRpgSkyblock.id("skyblock_pet_levels"), "skyrpg/skyblock_pet_levels", SkyBlockPets::loadLevels);
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Pet";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                ItemStack pet = Pets.active(player);
                if (isPet(pet)) {
                    stats(pet).forEach(out::add);
                }
            }
        });
        SkillLevels.XP_EARNED.register(SkyBlockPets::xpEarned);
        // Before the Hub's protection (a pet is a head, a block item, which the Hub won't let you use): adding a pet works
        // anywhere.
        Identifier early = SkyRpgSkyblock.id("pets_early");
        UseItemCallback.EVENT.addPhaseOrdering(early, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(early, (player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            if (!isPet(held)) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer server) {
                add(server, held.copyWithCount(1));
                held.shrink(1);
            }
            return InteractionResult.SUCCESS;
        });
        // Looking at a block when you right-click is a block use, which the Hub refuses: a pet is added then too.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.addPhaseOrdering(early, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(early, (player, level, hand, hit) -> {
            ItemStack held = player.getItemInHand(hand);
            if (!isPet(held)) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer server) {
                add(server, held.copyWithCount(1));
                held.shrink(1);
            }
            return InteractionResult.SUCCESS;
        });
        PetsMenu.init();
        PetCatalog.init();
        PetItems.init();
        PetAbilities.init();
        PetPerks.init();
        Kat.init();
    }

    // ---------------------------------------------------------------- data

    public static boolean known(String type) {
        return pets.containsKey(type);
    }

    public static List<String> types() {
        return List.copyOf(pets.keySet());
    }

    private static void loadPets(Map<Identifier, JsonElement> files) {
        Map<String, PetDef> loaded = new TreeMap<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                Map<String, Tier> tiers = new LinkedHashMap<>();
                json.getAsJsonObject("rarities").entrySet().forEach(entry -> {
                    JsonObject tier = entry.getValue().getAsJsonObject();
                    List<String> lore = new ArrayList<>();
                    tier.getAsJsonArray("lore").forEach(line -> lore.add(line.getAsString()));
                    JsonArray stats = tier.getAsJsonArray("stats");
                    JsonArray numbers = tier.getAsJsonArray("numbers");
                    tiers.put(entry.getKey(), new Tier(List.copyOf(lore), numberMap(stats.get(0).getAsJsonObject()),
                            numberMap(stats.get(1).getAsJsonObject()), numbers(numbers.get(0).getAsJsonArray()), numbers(numbers.get(1).getAsJsonArray())));
                });
                String type = id.getPath().toUpperCase(Locale.ROOT);
                loaded.put(type, new PetDef(type, json.get("name").getAsString(), json.get("type").getAsString(),
                        json.has("skin") ? json.get("skin").getAsString() : null, json.get("max_level").getAsInt(), tiers));
            } catch (RuntimeException e) {
                SkyRpgSkyblock.LOGGER.warn("Skipping pet {}: {}", id, e.getMessage());
            }
        });
        pets = loaded;
        List<PetCatalog.Entry> catalog = new ArrayList<>();
        loaded.values().forEach(def -> catalog.add(new PetCatalog.Entry(def.id(), def.name(), def.skin() == null ? "" : def.skin(),
                List.copyOf(def.tiers().keySet()))));
        PetCatalog.replace(catalog);
        SkyRpgSkyblock.LOGGER.info("Loaded {} SkyBlock pets", loaded.size());
    }

    private static void loadLevels(Map<Identifier, JsonElement> files) {
        files.values().forEach(element -> {
            JsonObject json = element.getAsJsonObject();
            levelXp = numbers(json.getAsJsonArray("levels"));
            Map<String, Integer> off = new LinkedHashMap<>();
            json.getAsJsonObject("offsets").entrySet().forEach(e -> off.put(e.getKey(), e.getValue().getAsInt()));
            offsets = off;
            Map<String, double[]> custom = new LinkedHashMap<>();
            json.getAsJsonObject("custom").entrySet().forEach(e -> custom.put(e.getKey(), numbers(e.getValue().getAsJsonObject().getAsJsonArray("levels"))));
            customLevels = custom;
        });
    }

    private static Map<String, Double> numberMap(JsonObject json) {
        Map<String, Double> out = new LinkedHashMap<>();
        json.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsDouble()));
        return out;
    }

    private static double[] numbers(JsonArray array) {
        double[] out = new double[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsDouble();
        }
        return out;
    }

    // ---------------------------------------------------------------- the item

    public static boolean isPet(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && PET_ID.equals(data.copyTag().getStringOr("id", ""));
    }

    private static JsonObject info(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        String raw = data == null ? "" : data.copyTag().getStringOr("petInfo", "");
        try {
            return raw.isEmpty() ? new JsonObject() : JsonParser.parseString(raw).getAsJsonObject();
        } catch (RuntimeException e) {
            return new JsonObject();
        }
    }

    private static void setInfo(ItemStack stack, JsonObject info) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString("petInfo", info.toString()));
    }

    public static String type(ItemStack stack) {
        JsonObject info = info(stack);
        return info.has("type") ? info.get("type").getAsString() : "";
    }

    public static String tier(ItemStack stack) {
        JsonObject info = info(stack);
        return info.has("tier") ? info.get("tier").getAsString() : "COMMON";
    }

    public static double exp(ItemStack stack) {
        JsonObject info = info(stack);
        return info.has("exp") ? info.get("exp").getAsDouble() : 0;
    }

    /** The pet item it holds (Hypixel's petInfo "heldItem"), or null. */
    public static String heldItem(ItemStack stack) {
        JsonObject info = info(stack);
        return info.has("heldItem") && !info.get("heldItem").isJsonNull() ? info.get("heldItem").getAsString() : null;
    }

    /** Changes the pet's rarity (Kat's upgrades); its XP stays, so its level follows the new rarity's table. */
    static void setTier(ItemStack stack, String tier) {
        JsonObject info = info(stack);
        info.addProperty("tier", tier);
        setInfo(stack, info);
        refreshLook(stack);
    }

    static void setHeldItem(ItemStack stack, String item) {
        JsonObject info = info(stack);
        info.addProperty("heldItem", item);
        setInfo(stack, info);
    }

    /** The rarity its stats count as: one higher with a Tier Boost (if the pet has that rarity). */
    static String statTier(ItemStack stack) {
        String tier = tier(stack);
        PetItems.Effect held = PetItems.effect(heldItem(stack));
        PetDef def = pets.get(type(stack));
        if (held != null && held.tierBoost() && def != null) {
            int next = tierIndex(tier) + 1;
            if (next < TIERS.length && def.tiers().containsKey(TIERS[next])) {
                return TIERS[next];
            }
        }
        return tier;
    }

    /** A new pet at level 1 of this tier (Hypixel's PET item: id and petInfo in its custom data, the pet's head). */
    public static ItemStack create(String type, String tier, double exp) {
        PetDef def = pets.get(type);
        if (def == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", PET_ID);
        JsonObject info = new JsonObject();
        info.addProperty("type", type);
        info.addProperty("active", false);
        info.addProperty("exp", exp);
        info.addProperty("tier", tier);
        info.addProperty("hideInfo", false);
        info.addProperty("candyUsed", 0);
        info.addProperty("uuid", UUID.randomUUID().toString());
        tag.putString("petInfo", info.toString());
        tag.putString("uuid", info.get("uuid").getAsString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        if (def.skin() != null) {
            UUID skinId = UUID.nameUUIDFromBytes(("skyrpg_pet:" + type).getBytes(StandardCharsets.UTF_8));
            stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(skinId, "",
                    new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", def.skin()))))));
        }
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        refreshLook(stack);
        return stack;
    }

    private static int tierIndex(String tier) {
        for (int i = 0; i < TIERS.length; i++) {
            if (TIERS[i].equals(tier)) {
                return i;
            }
        }
        return 0;
    }

    public static ChatFormatting color(String tier) {
        return TIER_COLORS[tierIndex(tier)];
    }

    private static int maxLevel(String type) {
        PetDef def = pets.get(type);
        return def == null ? 100 : def.maxLevel();
    }

    /** XP to go from {@code level} to the next, for this pet and tier. */
    private static double stepXp(String type, String tier, int level) {
        if (level >= 100) {
            double[] custom = customLevels.get(type);
            return custom != null && custom.length > 0 ? custom[Math.min(custom.length - 1, level - 100)] : Double.MAX_VALUE;
        }
        int index = offsets.getOrDefault(tier, 0) + level - 1;
        return index >= 0 && index < levelXp.length ? levelXp[index] : Double.MAX_VALUE;
    }

    public static int level(ItemStack stack) {
        String type = type(stack);
        String tier = tier(stack);
        double left = exp(stack);
        int level = 1;
        int max = maxLevel(type);
        while (level < max) {
            double step = stepXp(type, tier, level);
            if (left < step) {
                break;
            }
            left -= step;
            level++;
        }
        return level;
    }

    /** XP into the current level and what the next one takes (0 at the top). */
    static double[] progress(ItemStack stack) {
        String type = type(stack);
        String tier = tier(stack);
        double left = exp(stack);
        int level = 1;
        int max = maxLevel(type);
        while (level < max) {
            double step = stepXp(type, tier, level);
            if (left < step) {
                return new double[] {left, step};
            }
            left -= step;
            level++;
        }
        return new double[] {0, 0};
    }

    private static double along(double first, double last, int level) {
        double t = (Math.min(level, 100) - 1) / 99.0;
        return first + (last - first) * t;
    }

    /** The pet's ability number {@code index} ({@code {0}} in its lore) at its level and its stats' rarity, 0 if none. */
    public static double number(ItemStack stack, int index) {
        PetDef def = pets.get(type(stack));
        Tier tier = def == null ? null : def.tiers().get(statTier(stack));
        if (tier == null || index < 0 || index >= tier.numbersAt100().length) {
            return 0;
        }
        return along(index < tier.numbersAt1().length ? tier.numbersAt1()[index] : 0, tier.numbersAt100()[index], level(stack));
    }

    /** The pet's stats at its level. */
    public static Map<StatType, Double> stats(ItemStack stack) {
        PetDef def = pets.get(type(stack));
        Tier tier = def == null ? null : def.tiers().get(statTier(stack));
        if (tier == null) {
            return Map.of();
        }
        int level = level(stack);
        Map<StatType, Double> out = new LinkedHashMap<>();
        tier.statsAt100().forEach((key, last) -> {
            StatType stat = StatTypes.get(key);
            if (stat != null) {
                out.put(stat, along(tier.statsAt1().getOrDefault(key, 0.0), last, level));
            }
        });
        // Its pet item: a percent more of its own stats (Textbook), then flat stats (Big Teeth).
        PetItems.Effect held = PetItems.effect(heldItem(stack));
        if (held != null) {
            held.multiply().forEach((stat, percent) -> out.computeIfPresent(stat, (k, v) -> v * (1 + percent / 100)));
            if (held.multiplyAll() > 0) {
                out.replaceAll((stat, v) -> v * (1 + held.multiplyAll() / 100));
            }
            held.stats().forEach((stat, value) -> out.merge(stat, value, Double::sum));
            if (held.every() > 0) {
                held.perLevelStats().forEach((stat, value) -> out.merge(stat, value * (level / held.every()), Double::sum));
            }
        }
        return out;
    }

    /** "[Lvl 54] Bee" in the tier's colour, and the lore with its numbers filled in, as Hypixel shows a pet. */
    public static void refreshLook(ItemStack stack) {
        PetDef def = pets.get(type(stack));
        if (def == null) {
            return;
        }
        String tierName = statTier(stack);
        Tier tier = def.tiers().get(tierName);
        int level = level(stack);
        ChatFormatting color = color(tierName);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("[Lvl " + level + "] ").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY))
                .append(Component.literal(def.name()).withStyle(s -> s.withItalic(false).withColor(color))));
        List<Component> lines = new ArrayList<>();
        lines.add(plain(capital(def.skill()) + " Pet", ChatFormatting.DARK_GRAY));
        lines.add(Component.empty());
        Map<StatType, Double> shown = stats(stack);
        if (tier != null) {
            for (String line : tier.lore()) {
                lines.add(Component.literal(fill(line, tier, level, shown)).withStyle(s -> s.withItalic(false)));
            }
            lines.add(Component.empty());
        }
        String heldId = heldItem(stack);
        if (heldId != null) {
            com.skyrpg.items.ItemDefinition heldDef = com.skyrpg.items.ItemRegistry.skyblock(heldId);
            PetItems.Effect held = PetItems.effect(heldId);
            lines.add(plain("Held Item: ", ChatFormatting.GOLD).append(Component.literal(heldDef == null ? heldId : heldDef.name())
                    .withStyle(s -> s.withItalic(false).withColor(heldDef == null ? 0xFFFFFF : heldDef.rarity().color()))));
            if (held != null && !held.text().isEmpty()) {
                lines.add(plain(held.text(), ChatFormatting.GRAY));
            }
            lines.add(Component.empty());
        }
        double[] progress = progress(stack);
        if (progress[1] <= 0) {
            lines.add(plain("MAX LEVEL", ChatFormatting.AQUA));
        } else {
            double percent = progress[0] / progress[1] * 100;
            lines.add(plain("Progress to Level " + (level + 1) + ": ", ChatFormatting.GRAY)
                    .append(plain(String.format(Locale.ROOT, "%.1f%%", percent), ChatFormatting.YELLOW)));
            lines.add(plain(Msg.number(Math.floor(progress[0])) + " / " + Msg.compact(progress[1]) + " XP", ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.empty());
        lines.add(plain("Right-click to add this pet to your pet menu!", ChatFormatting.YELLOW));
        lines.add(Component.empty());
        lines.add(Component.literal(tierName + " PET").withStyle(s -> s.withItalic(false).withBold(true).withColor(color)));
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static String fill(String line, Tier tier, int level, Map<StatType, Double> shown) {
        Matcher m = PLACEHOLDER.matcher(line);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value;
            if (key.equals("LVL")) {
                value = Integer.toString(level);
            } else if (Character.isDigit(key.charAt(0))) {
                int i = Integer.parseInt(key);
                value = i < tier.numbersAt100().length
                        ? format(along(i < tier.numbersAt1().length ? tier.numbersAt1()[i] : 0, tier.numbersAt100()[i], level)) : "?";
            } else {
                String stat = key.toLowerCase(Locale.ROOT);
                StatType type = StatTypes.get(stat);
                // Its stats as they are, pet item included (a Textbook doubles the Intelligence shown).
                value = type != null && shown != null && shown.containsKey(type) ? format(shown.get(type))
                        : tier.statsAt100().containsKey(stat) ? format(along(tier.statsAt1().getOrDefault(stat, 0.0), tier.statsAt100().get(stat), level)) : "?";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        // NEU's lore lost Hypixel's stat symbols, leaving "+2Health Regen" and "0.1Swing Range": put the space back
        // (never after a colour code's digit, "§7Crit").
        return out.toString().replaceAll("(?<=\\d)(?<!§\\d)(?=[A-Z][a-z])", " ");
    }

    private static String format(double value) {
        // a whole number shows as one, but a small fraction (0.0015% a level) isn't rounded away to 0
        if (Math.abs(value - Math.round(value)) < 0.005 && (Math.abs(value) >= 1 || value == 0)) {
            return Long.toString(Math.round(value));
        }
        String text = String.format(Locale.ROOT, Math.abs(value) < 1 ? "%.3f" : "%.1f", value);
        return text.contains(".") ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text;
    }

    private static MutableComponent plain(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(s -> s.withItalic(false).withColor(color));
    }

    private static String capital(String skill) {
        return skill.isEmpty() ? "" : skill.charAt(0) + skill.substring(1).toLowerCase(Locale.ROOT);
    }

    public static MutableComponent name(ItemStack pet) {
        PetDef def = pets.get(type(pet));
        return Component.literal(def == null ? "Pet" : def.name()).withStyle(color(tier(pet)));
    }

    // ---------------------------------------------------------------- the player's pets

    public static List<ItemStack> stored(ServerPlayer player) {
        return new ArrayList<>(player.getAttachedOrElse(STORED, List.of()));
    }

    static void setStored(ServerPlayer player, List<ItemStack> list) {
        player.setAttached(STORED, List.copyOf(list));
    }

    /** Adds a pet to the player's pets (summoning it when none is out). */
    public static void add(ServerPlayer player, ItemStack pet) {
        refreshLook(pet);
        if (!isPet(Pets.active(player))) {
            player.setAttached(Pets.ACTIVE, pet);
            Stats.refresh(player);
            Notice.of(Notice.Kind.PET).tag("PET").send(player, Component.literal("You summoned your ").withStyle(ChatFormatting.GRAY)
                    .append(name(pet)).append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
        } else {
            List<ItemStack> list = stored(player);
            list.add(pet);
            setStored(player, list);
            Notice.of(Notice.Kind.PET).tag("PET").send(player, Component.literal("Added ").withStyle(ChatFormatting.GRAY)
                    .append(name(pet)).append(Component.literal(" to your pet menu.").withStyle(ChatFormatting.GRAY)));
        }
        Msg.playSound(player, SoundEvents.ALLAY_ITEM_GIVEN, 0.8f, 1.3f);
        com.skyrpg.skyblock.leveling.LevelingTasks.sync(player, "pet_score", 3 * petScore(player));
    }

    /**
     * Hypixel's Pet Score: each kind of pet you have counts once, at its best rarity (Common 1 ... Mythic 6), +1 for each
     * kind you have at its top level.
     */
    public static int petScore(ServerPlayer player) {
        java.util.Map<String, Integer> best = new java.util.HashMap<>();
        java.util.Set<String> maxed = new java.util.HashSet<>();
        List<ItemStack> all = stored(player);
        all.add(Pets.active(player));
        for (ItemStack pet : all) {
            if (!isPet(pet)) {
                continue;
            }
            String type = type(pet);
            int rarity = java.util.Arrays.asList(TIERS).indexOf(tier(pet)) + 1;
            best.merge(type, Math.max(1, rarity), Math::max);
            if (level(pet) >= maxLevel(type)) {
                maxed.add(type);
            }
        }
        return best.values().stream().mapToInt(Integer::intValue).sum() + maxed.size();
    }

    /** What share of skill XP the active pet gets (Hypixel's), before Zoologist. */
    static double share(String petSkill, Identifier skill) {
        String gained = skill.getPath().toUpperCase(Locale.ROOT);
        if (gained.equals(petSkill)) {
            return petSkill.equals("MINING") || petSkill.equals("FISHING") ? 1.5 : 1.0;
        }
        return gained.equals("ALCHEMY") || gained.equals("ENCHANTING") ? 1.0 / 12 : 0.25;
    }

    /** Pets in your menu that hold an Exp Share (up to three) earn a fifth of what the pet out earned, and what the item adds. */
    private static void shareExp(ServerPlayer player, double petXp) {
        List<ItemStack> all = stored(player);
        boolean changed = false;
        int slots = 0;
        for (int i = 0; i < all.size() && slots < EXP_SHARE_SLOTS; i++) {
            ItemStack pet = all.get(i);
            PetItems.Effect held = PetItems.effect(heldItem(pet));
            if (!isPet(pet) || held == null || held.expShare() <= 0) {
                continue;
            }
            slots++;
            ItemStack updated = pet.copy();
            JsonObject info = info(updated);
            int before = level(updated);
            info.addProperty("exp", exp(updated) + petXp * (EXP_SHARE_BASE + held.expShare()) / 100);
            setInfo(updated, info);
            if (level(updated) > before) {
                refreshLook(updated);
            }
            all.set(i, updated);
            changed = true;
        }
        if (changed) {
            setStored(player, all);
        }
    }

    private static void xpEarned(ServerPlayer player, Identifier skill, double xp) {
        ItemStack pet = Pets.active(player);
        if (!isPet(pet) || xp <= 0 || skill.equals(SkillLevels.TAMING)) {
            return;   // Taming XP comes from the pet itself
        }
        PetDef def = pets.get(type(pet));
        if (def == null) {
            return;
        }
        int before = level(pet);
        double petXp = xp * share(def.skill(), skill);
        PetItems.Effect held = PetItems.effect(heldItem(pet));
        if (held != null) {
            double boost = held.exp().getOrDefault(skill.getPath().toUpperCase(Locale.ROOT), held.exp().getOrDefault("ALL", 0.0));
            petXp *= 1 + boost / 100;
        }
        double zoologist = 1 + SkillLevels.level(player, SkillLevels.TAMING) / 100.0;
        ItemStack updated = pet.copy();
        JsonObject info = info(updated);
        info.addProperty("exp", exp(updated) + petXp * zoologist);
        setInfo(updated, info);
        SkillLevels.giveXpQuietly(player, SkillLevels.TAMING, petXp);
        shareExp(player, petXp);
        int after = level(updated);
        if (after > before) {
            refreshLook(updated);
            Notice.of(Notice.Kind.PET).tag("PET LEVEL UP").send(player, Component.literal("Your ").withStyle(ChatFormatting.GRAY)
                    .append(name(updated)).append(Component.literal(" leveled up to level ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(Integer.toString(after)).withStyle(ChatFormatting.AQUA)).append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
            Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.5f, 1.8f);
            Stats.refresh(player);
        }
        player.setAttached(Pets.ACTIVE, updated);
    }
}
