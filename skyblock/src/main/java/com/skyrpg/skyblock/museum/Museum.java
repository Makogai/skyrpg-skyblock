package com.skyrpg.skyblock.museum;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Hypixel's Museum ({@code skyrpg/museum}, from {@code tools/skyblock/import_museum.py}): 973 donations in eight
 * categories (Combat, Farming, Mining, Fishing, Foraging, Dungeoneering, Hunting, Special). Donating an item (or a whole
 * armor set) gives it to the Museum for good and pays its SkyBlock XP; a higher item also counts the lower ones it
 * replaces (a Refined Mithril Pickaxe counts the Mithril Pickaxe), with their XP. Per profile.
 */
public final class Museum {
    public record Entry(String id, String category, int xp, String stage, List<String> items, String child) {
        public boolean set() {
            return items.size() > 1;
        }
    }

    public record Category(String id, List<Entry> entries) {
    }

    public static final AttachmentType<List<String>> DONATED = AttachmentRegistry.create(SkyRpgSkyblock.id("museum"),
            builder -> builder.initializer(List::of).persistent(Codec.STRING.listOf()).copyOnDeath());

    private static List<Category> categories = List.of();
    private static Map<String, Entry> byId = Map.of();

    private Museum() {
    }

    public static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("museum"), "skyrpg/museum", Museum::load);
        MuseumMenu.init();
    }

    private static void load(Map<Identifier, JsonElement> files) {
        List<Category> loaded = new ArrayList<>();
        Map<String, Entry> ids = new LinkedHashMap<>();
        for (JsonElement file : files.values()) {
            for (JsonElement c : file.getAsJsonObject().getAsJsonArray("categories")) {
                JsonObject category = c.getAsJsonObject();
                String cat = category.get("id").getAsString();
                List<Entry> entries = new ArrayList<>();
                for (JsonElement e : category.getAsJsonArray("entries")) {
                    JsonObject entry = e.getAsJsonObject();
                    List<String> items = new ArrayList<>();
                    entry.getAsJsonArray("items").forEach(i -> items.add(i.getAsString()));
                    Entry parsed = new Entry(entry.get("id").getAsString(), cat, entry.get("xp").getAsInt(), entry.get("stage").getAsString(),
                            List.copyOf(items), entry.has("child") ? entry.get("child").getAsString() : null);
                    entries.add(parsed);
                    ids.put(parsed.id(), parsed);
                }
                loaded.add(new Category(cat, List.copyOf(entries)));
            }
        }
        categories = List.copyOf(loaded);
        byId = ids;
        SkyRpgSkyblock.LOGGER.info("Loaded {} Museum donations", ids.size());
    }

    public static List<Category> categories() {
        return categories;
    }

    public static Set<String> donated(ServerPlayer player) {
        return new LinkedHashSet<>(player.getAttachedOrElse(DONATED, List.of()));
    }

    /** The donation's name: the item's, or a set's ("Abyssal Armor"). */
    public static String name(Entry entry) {
        ItemDefinition first = ItemRegistry.skyblock(entry.items().getFirst());
        if (!entry.set()) {
            return first == null ? entry.id() : first.name();
        }
        String name = first == null ? entry.id() : first.name();
        for (String piece : new String[] {" Helmet", " Chestplate", " Leggings", " Boots", " Hat", " Belt", " Cloak", " Necklace"}) {
            if (name.endsWith(piece)) {
                return name.substring(0, name.length() - piece.length()) + " Armor";
            }
        }
        return name;
    }

    /** Whether everything this donation takes is in the player's inventory. */
    public static boolean canDonate(ServerPlayer player, Entry entry) {
        for (String item : entry.items()) {
            if (SkyStacks.count(player.getInventory(), item) < 1) {
                return false;
            }
        }
        return true;
    }

    /** Donates it (and counts the lower ones it replaces): "" when done, else why not. */
    static String donate(ServerPlayer player, String id) {
        Entry entry = byId.get(id);
        if (entry == null) {
            return "";
        }
        Set<String> donated = donated(player);
        if (donated.contains(id)) {
            return "You already donated this!";
        }
        if (!canDonate(player, entry)) {
            return entry.set() ? "You need the whole set in your inventory!" : "You don't have this item!";
        }
        entry.items().forEach(item -> SkyStacks.take(player.getInventory(), item, 1));
        int xp = 0;
        List<String> counted = new ArrayList<>();
        for (Entry at = entry; at != null && !donated.contains(at.id()); at = at.child() == null ? null : byId.get(at.child())) {
            donated.add(at.id());
            xp += at.xp();
            counted.add(name(at));
        }
        player.setAttached(DONATED, List.copyOf(donated));
        if (xp > 0) {
            SkyBlockXp.give(player, xp, "Museum donation");
        }
        Notice.Panel panel = Notice.of(Notice.Kind.COLLECTION).tag("MUSEUM").panel();
        if (counted.size() > 1) {
            panel.line(Component.literal("Also counts: " + String.join(", ", counted.subList(1, counted.size()))).withStyle(ChatFormatting.GRAY));
        }
        if (xp > 0) {
            panel.line(Component.literal("+" + xp + " SkyBlock XP").withStyle(ChatFormatting.AQUA));
        }
        panel.send(player, Component.literal("Donated ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(name(entry)).withStyle(ChatFormatting.GOLD)).append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
        return "";
    }

    /** Donations and XP per category, for the menu. */
    static Map<String, int[]> progress(ServerPlayer player) {
        Set<String> donated = donated(player);
        Map<String, int[]> out = new HashMap<>();
        for (Category category : categories) {
            int done = (int) category.entries().stream().filter(e -> donated.contains(e.id())).count();
            out.put(category.id(), new int[] {done, category.entries().size()});
        }
        return out;
    }
}
