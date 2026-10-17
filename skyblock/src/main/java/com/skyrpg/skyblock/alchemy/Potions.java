package com.skyrpg.skyblock.alchemy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.Rarity;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Hypixel's potions ({@code skyrpg/skyblock_potions}, {@code tools/skyblock/import_potions.py}): every brewable potion's
 * levels (base duration, text, stats and special effects), which ingredient starts which potion, and the modifiers.
 *
 * <p>A potion is Hypixel's POTION item with its data in custom data: {@code potion} (speed, or awkward), {@code potion_level},
 * {@code splash}, {@code extra_ticks} (from Redstone), {@code level_mod} / {@code duration_mod} (a modifier of each kind is
 * used), and {@code alchemy_xp} (paid when it's taken out of the brewing stand). No {@code potion} is a Water Bottle, as is
 * vanilla's water bottle.
 */
public final class Potions {
    public static final String ID = "POTION";
    public static final String AWKWARD = "awkward";

    public record Level(int ticks, String rarity, List<String> text, Map<String, Double> stats, Map<String, Double> special) {
    }

    public record Kind(String id, String name, int max, int color, Map<Integer, Level> levels) {
        public Level level(int level) {
            return levels.getOrDefault(Math.min(level, max), levels.get(max));
        }
    }

    /** What a modifier does: levels added, minutes added, or splash (its duration factor; 0 when not a splash). */
    public record Modifier(int levels, int minutes, double splash) {
    }

    /** Ingredient: the potion it starts and at which level. */
    public record Start(String potion, int level) {
    }

    /** A potion stack's data: {@code potion} "" for water. */
    public record Brew(String potion, int level, boolean splash, double splashFactor, int extraTicks, boolean levelMod,
                       boolean durationMod, double alchemyXp) {
        public boolean water() {
            return potion.isEmpty();
        }

        public boolean awkward() {
            return potion.equals(AWKWARD);
        }
    }

    private static Map<String, Kind> kinds = Map.of();
    private static Map<String, Start> ingredients = Map.of();
    private static Map<String, Modifier> modifiers = Map.of();
    private static List<String> awkward = List.of();

    private Potions() {
    }

    static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("skyblock_potions"), "skyrpg/skyblock_potions", Potions::load);
    }

    private static void load(Map<Identifier, JsonElement> files) {
        for (JsonElement element : files.values()) {
            JsonObject json = element.getAsJsonObject();
            Map<String, Kind> loaded = new HashMap<>();
            json.getAsJsonObject("potions").entrySet().forEach(e -> {
                JsonObject p = e.getValue().getAsJsonObject();
                Map<Integer, Level> levels = new HashMap<>();
                p.getAsJsonObject("levels").entrySet().forEach(l -> {
                    JsonObject lv = l.getValue().getAsJsonObject();
                    List<String> text = new ArrayList<>();
                    lv.getAsJsonArray("text").forEach(t -> text.add(t.getAsString()));
                    levels.put(Integer.parseInt(l.getKey()), new Level(lv.get("ticks").getAsInt(), lv.get("rarity").getAsString(), List.copyOf(text),
                            numbers(lv.getAsJsonObject("stats")), numbers(lv.getAsJsonObject("special"))));
                });
                loaded.put(e.getKey(), new Kind(e.getKey(), p.get("name").getAsString(), p.get("max").getAsInt(),
                        Integer.parseInt(p.get("color").getAsString().substring(1), 16), Map.copyOf(levels)));
            });
            Map<String, Start> starts = new HashMap<>();
            json.getAsJsonObject("ingredients").entrySet().forEach(e -> starts.put(e.getKey(),
                    new Start(e.getValue().getAsJsonObject().get("potion").getAsString(), e.getValue().getAsJsonObject().get("level").getAsInt())));
            Map<String, Modifier> mods = new HashMap<>();
            json.getAsJsonObject("modifiers").entrySet().forEach(e -> {
                JsonObject m = e.getValue().getAsJsonObject();
                mods.put(e.getKey(), new Modifier(m.has("levels") ? m.get("levels").getAsInt() : 0, m.has("minutes") ? m.get("minutes").getAsInt() : 0,
                        m.has("splash") ? m.get("splash").getAsDouble() : 0));
            });
            List<String> wart = new ArrayList<>();
            json.getAsJsonArray("awkward").forEach(a -> wart.add(a.getAsString()));
            kinds = Map.copyOf(loaded);
            ingredients = Map.copyOf(starts);
            modifiers = Map.copyOf(mods);
            awkward = List.copyOf(wart);
        }
        SkyRpgSkyblock.LOGGER.info("Loaded {} potions and {} brewing ingredients", kinds.size(), ingredients.size());
    }

    private static Map<String, Double> numbers(JsonObject json) {
        Map<String, Double> out = new HashMap<>();
        json.entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsDouble()));
        return Map.copyOf(out);
    }

    public static Kind kind(String id) {
        return kinds.get(id);
    }

    static Start start(String ingredient) {
        return ingredients.get(ingredient);
    }

    static Modifier modifier(String ingredient) {
        return modifiers.get(ingredient);
    }

    static boolean awkwardIngredient(String ingredient) {
        return awkward.contains(ingredient);
    }

    /** Whether this can go in a brewing stand's ingredient slot. */
    static boolean ingredient(String id) {
        return ingredients.containsKey(id) || modifiers.containsKey(id) || awkward.contains(id);
    }

    /** A potion's data, or null when it isn't a bottle the stand takes (a water bottle reads as water). */
    public static Brew read(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(Items.POTION) && SkyItems.definition(stack) == null) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            return contents != null && contents.is(net.minecraft.world.item.alchemy.Potions.WATER) ? new Brew("", 0, false, 1, 0, false, false, 0) : null;
        }
        if (!ID.equals(SkyItems.skyblockId(stack))) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
        return new Brew(tag.getStringOr("potion", ""), tag.getIntOr("potion_level", 0), tag.getBooleanOr("splash", false),
                tag.getDoubleOr("splash_factor", 1), tag.getIntOr("extra_ticks", 0), tag.getBooleanOr("level_mod", false),
                tag.getBooleanOr("duration_mod", false), tag.getDoubleOr("alchemy_xp", 0));
    }

    /** The potion's duration as brewed (base for its level, Redstone added, splash factor), in ticks. */
    public static int ticks(Brew brew) {
        Kind kind = kind(brew.potion());
        if (kind == null) {
            return 0;
        }
        return (int) Math.round((kind.level(brew.level()).ticks() + brew.extraTicks()) * (brew.splash() ? brew.splashFactor() : 1));
    }

    /** A potion stack for this data: Hypixel's name, its text with the duration, colour and rarity. */
    public static ItemStack create(Brew brew) {
        ItemDefinition def = ItemRegistry.skyblock(ID);
        ItemStack stack = def == null ? new ItemStack(Items.POTION) : SkyItems.create(def, 1);
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = existing == null ? new CompoundTag() : existing.copyTag();
        tag.putString("id", ID);
        tag.putString("potion", brew.potion());
        tag.putInt("potion_level", brew.level());
        tag.putBoolean("splash", brew.splash());
        tag.putDouble("splash_factor", brew.splashFactor());
        tag.putInt("extra_ticks", brew.extraTicks());
        tag.putBoolean("level_mod", brew.levelMod());
        tag.putBoolean("duration_mod", brew.durationMod());
        if (brew.alchemyXp() > 0) {
            tag.putDouble("alchemy_xp", brew.alchemyXp());
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        look(stack, brew);
        return stack;
    }

    /** The same potion without the Alchemy XP it carried out of the stand. */
    static ItemStack withoutXp(ItemStack stack) {
        Brew brew = read(stack);
        if (brew == null || brew.alchemyXp() <= 0) {
            return stack;
        }
        ItemStack out = create(new Brew(brew.potion(), brew.level(), brew.splash(), brew.splashFactor(), brew.extraTicks(), brew.levelMod(),
                brew.durationMod(), 0));
        out.setCount(stack.getCount());
        return out;
    }

    private static void look(ItemStack stack, Brew brew) {
        // Its text says what it does: not vanilla's "No Effects".
        stack.set(DataComponents.TOOLTIP_DISPLAY, stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY,
                net.minecraft.world.item.component.TooltipDisplay.DEFAULT).withHidden(DataComponents.POTION_CONTENTS, true));
        Kind kind = kind(brew.potion());
        Style plain = Style.EMPTY.withItalic(false);
        if (brew.awkward() || kind == null) {
            String name = brew.awkward() ? "Awkward Potion" : "Water Bottle";
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(plain.withColor(0xFFFFFF)));
            List<Component> lore = new ArrayList<>();
            if (brew.awkward()) {
                lore.add(Component.empty());
                lore.add(Component.literal("Use Awkward Potions in Brewing Stands").withStyle(plain.withColor(0xAAAAAA)));
                lore.add(Component.literal("with Brewing Ingredients to brew Potions.").withStyle(plain.withColor(0xAAAAAA)));
            }
            lore.add(Component.empty());
            lore.add(Component.literal("COMMON").withStyle(plain.withColor(Rarity.COMMON.color() & 0xFFFFFF).withBold(true)));
            stack.set(DataComponents.LORE, new ItemLore(lore));
            stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(brew.awkward() ? 0x385DC6 : 0x385DC6),
                    List.of(), Optional.empty()));
            return;
        }
        if (brew.splash()) {
            stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("splash_potion"));
        }
        Level level = kind.level(brew.level());
        Rarity rarity = rarity(level.rarity());
        String title = (brew.splash() ? "Splash " : "") + kind.name() + " " + roman(brew.level()) + " Potion";
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(title).withStyle(plain.withColor(rarity.color() & 0xFFFFFF)));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        int ticks = ticks(brew);
        MutableComponent head = Component.literal(kind.name() + " " + roman(brew.level())).withStyle(plain.withColor(0x5555FF));
        if (ticks > 0) {
            head.append(Component.literal(" (" + duration(ticks) + ")").withStyle(plain.withColor(0xFFFFFF)));
        }
        lore.add(head);
        for (String line : level.text()) {
            lore.add(Component.literal(line).withStyle(plain.withColor(0xAAAAAA)));
        }
        if (brew.splash()) {
            lore.add(Component.empty());
            lore.add(Component.literal("Throw it to share it with the players near you.").withStyle(plain.withColor(0x55FFFF)));
        }
        lore.add(Component.empty());
        lore.add(Component.literal(rarity.name() + (brew.splash() ? " SPLASH POTION" : " POTION")).withStyle(plain.withColor(rarity.color() & 0xFFFFFF)
                .withBold(true)));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(kind.color()), List.of(), Optional.empty()));
    }

    private static Rarity rarity(String name) {
        try {
            return Rarity.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Rarity.COMMON;
        }
    }

    public static String roman(int level) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 0 && level < numerals.length ? numerals[level] : Integer.toString(level);
    }

    /** "3:00", "1:03:00". */
    public static String duration(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int hours = seconds / 3600;
        return hours > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
