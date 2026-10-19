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
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Hypixel's Museum ({@code skyrpg/museum}, from {@code tools/skyblock/import_museum.py}): 973 donations in eight
 * categories (Combat, Farming, Mining, Fishing, Foraging, Dungeoneering, Hunting, Special). Donating an item (or a whole
 * armor set) puts it in the Museum and pays its SkyBlock XP ("Museum Progression"); a higher item also counts the lower
 * ones it replaces (a Refined Mithril Pickaxe counts the Mithril Pickaxe), with their XP. The Museum keeps the very item
 * (reforge, enchantments and all): you can borrow it back and return it, and a borrowed item still counts for the
 * milestones ({@link MuseumMilestones}). The Special category pays no XP and holds 48 items. Per profile.
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
    /** The items the Museum keeps, by donation id (the real stacks, so borrowing gives yours back); gone while borrowed. */
    public static final AttachmentType<Map<String, List<ItemStack>>> STORED = AttachmentRegistry.create(SkyRpgSkyblock.id("museum_items"),
            builder -> builder.initializer(Map::of).persistent(Codec.unboundedMap(Codec.STRING, ItemStack.CODEC.listOf())).copyOnDeath());
    /** Donations that are out on loan. */
    public static final AttachmentType<List<String>> BORROWED = AttachmentRegistry.create(SkyRpgSkyblock.id("museum_borrowed"),
            builder -> builder.initializer(List::of).persistent(Codec.STRING.listOf()).copyOnDeath());
    /** Whether the Museum Appraisal Service has been paid for. */
    public static final AttachmentType<Boolean> APPRAISED = AttachmentRegistry.create(SkyRpgSkyblock.id("museum_appraised"),
            builder -> builder.initializer(() -> false).persistent(Codec.BOOL).copyOnDeath());

    /** How many Special items fit in the Museum. */
    public static final int SPECIAL_LIMIT = 48;
    /** What the Appraisal Service costs (once; from Milestone 3). */
    public static final long APPRAISAL_COST = 10_000_000;

    private static List<Category> categories = List.of();
    private static Map<String, Entry> byId = Map.of();

    private Museum() {
    }

    public static void init() {
        JsonDataLoader.register(SkyRpgSkyblock.id("museum"), "skyrpg/museum", Museum::load);
        MuseumMenu.init();
        Curator.init();
        com.skyrpg.economy.bank.Bank.interestBonus = MuseumMilestones::bankBonus;
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

    public static Entry entry(String id) {
        return byId.get(id);
    }

    public static Set<String> donated(ServerPlayer player) {
        return new LinkedHashSet<>(player.getAttachedOrElse(DONATED, List.of()));
    }

    public static Set<String> borrowed(ServerPlayer player) {
        return new LinkedHashSet<>(player.getAttachedOrElse(BORROWED, List.of()));
    }

    /** What the Museum keeps (donation id -> its items); a borrowed donation isn't in it. */
    public static Map<String, List<ItemStack>> stored(ServerPlayer player) {
        return player.getAttachedOrElse(STORED, Map.<String, List<ItemStack>>of());
    }

    /** The SkyBlock XP the Museum has paid (what the milestones count): every donation's, a borrowed one's too. */
    public static int xp(ServerPlayer player) {
        int xp = 0;
        for (String id : donated(player)) {
            Entry entry = byId.get(id);
            xp += entry == null ? 0 : entry.xp();
        }
        return xp;
    }

    /** How many Special items are in the Museum. */
    public static int specials(Set<String> donated) {
        int count = 0;
        for (String id : donated) {
            Entry entry = byId.get(id);
            if (entry != null && entry.category().equals("special")) {
                count++;
            }
        }
        return count;
    }

    /**
     * What the Museum is worth, for the Appraisal Service: what an NPC pays for each item in it (borrowed ones don't
     * count while they're out). Hypixel values them at their market price; most of these have none here.
     */
    public static long value(ServerPlayer player) {
        double value = 0;
        for (List<ItemStack> kept : stored(player).values()) {
            for (ItemStack stack : kept) {
                value += SkyStacks.npcPrice(SkyStacks.id(stack)) * stack.getCount();
            }
        }
        return Math.round(value);
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
        if (entry.category().equals("special") && specials(donated) >= SPECIAL_LIMIT) {
            return "Your Museum has room for " + SPECIAL_LIMIT + " Special items!";
        }
        int before = MuseumMilestones.reached(player);
        keep(player, entry);
        int xp = 0;
        List<String> counted = new ArrayList<>();
        for (Entry at = entry; at != null && !donated.contains(at.id()); at = at.child() == null ? null : byId.get(at.child())) {
            donated.add(at.id());
            xp += at.xp();
            counted.add(name(at));
        }
        player.setAttached(DONATED, List.copyOf(donated));
        if (xp > 0) {
            SkyBlockXp.give(player, xp, "Museum Progression");
        }
        Notice.Panel panel = Notice.of(Notice.Kind.COLLECTION).tag("MUSEUM").panel();
        if (counted.size() > 1) {
            panel.line(Component.literal("Also counts: " + String.join(", ", counted.subList(1, counted.size()))).withStyle(ChatFormatting.GRAY));
        }
        if (xp > 0) {
            panel.line(Component.literal("+" + xp + " SkyBlock XP").withStyle(ChatFormatting.AQUA));
        }
        int after = MuseumMilestones.reached(player);
        if (after > before) {
            panel.line(Component.literal("Museum Milestone " + after + " reached! ").withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal("[Visit the Curator]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent.RunCommand("/curator")))));
        }
        panel.send(player, Component.literal("Donated ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(name(entry)).withStyle(ChatFormatting.GOLD)).append(Component.literal("!").withStyle(ChatFormatting.GRAY)));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 0.6f, 1.6f);
        return "";
    }

    /** Takes one of each of the donation's items out of the inventory and keeps those very stacks in the Museum. */
    private static void keep(ServerPlayer player, Entry entry) {
        List<ItemStack> kept = new ArrayList<>();
        for (String item : entry.items()) {
            ItemStack one = takeOne(player.getInventory(), item);
            if (!one.isEmpty()) {
                kept.add(one);
            }
        }
        Map<String, List<ItemStack>> stored = new HashMap<>(stored(player));
        stored.put(entry.id(), List.copyOf(kept));
        player.setAttached(STORED, Map.copyOf(stored));
    }

    /** The first stack of the item in the inventory, one of it, taken out (with everything it has on it). */
    private static ItemStack takeOne(Inventory inventory, String item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (SkyStacks.is(stack, item)) {
                ItemStack one = stack.copyWithCount(1);
                stack.shrink(1);
                inventory.setChanged();
                return one;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Whether the Museum has the item(s) of a donation to lend (a donation that only counted as a lower one has none). */
    public static boolean borrowable(Set<String> borrowed, Map<String, List<ItemStack>> stored, String id) {
        return !borrowed.contains(id) && stored.containsKey(id);
    }

    /** Borrows a donation back: its item(s) go to your inventory (it still counts for the milestones). "" when done, else why not. */
    static String borrow(ServerPlayer player, String id) {
        Entry entry = byId.get(id);
        Map<String, List<ItemStack>> stored = new HashMap<>(stored(player));
        Set<String> borrowed = borrowed(player);
        if (entry == null || !donated(player).contains(id)) {
            return "You haven't donated this!";
        }
        if (borrowed.contains(id)) {
            return "You already borrowed this!";
        }
        List<ItemStack> kept = stored.get(id);
        if (kept == null) {
            return "The Museum can't lend this one: it only counts as donated.";
        }
        int free = 0;
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                free++;
            }
        }
        if (free < kept.size()) {
            return "You need " + kept.size() + " free inventory slot" + (kept.size() == 1 ? "" : "s") + "!";
        }
        kept.forEach(stack -> player.getInventory().add(stack.copy()));
        stored.remove(id);
        player.setAttached(STORED, Map.copyOf(stored));
        borrowed.add(id);
        player.setAttached(BORROWED, List.copyOf(borrowed));
        player.sendSystemMessage(Component.literal("You retrieved your ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(name(entry)).withStyle(ChatFormatting.WHITE)).append(Component.literal(" from the ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("Museum").withStyle(ChatFormatting.DARK_AQUA)).append(Component.literal("!").withStyle(ChatFormatting.GREEN)));
        player.sendSystemMessage(Component.literal("It still counts towards your Museum progress, but not towards its value.").withStyle(ChatFormatting.GRAY));
        Msg.playSound(player, SoundEvents.BUNDLE_REMOVE_ONE, 1.0f, 1.0f);
        return "";
    }

    /** Gives a borrowed donation back (the very items you hold now go into the Museum). "" when done, else why not. */
    static String giveBack(ServerPlayer player, String id) {
        Entry entry = byId.get(id);
        Set<String> borrowed = borrowed(player);
        if (entry == null || !borrowed.contains(id)) {
            return "";
        }
        if (!canDonate(player, entry)) {
            return entry.set() ? "You need the whole set in your inventory!" : "You don't have this item!";
        }
        keep(player, entry);
        borrowed.remove(id);
        player.setAttached(BORROWED, List.copyOf(borrowed));
        player.sendSystemMessage(Component.literal("You returned your ").withStyle(ChatFormatting.GREEN)
                .append(Component.literal(name(entry)).withStyle(ChatFormatting.WHITE)).append(Component.literal(" to the ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("Museum").withStyle(ChatFormatting.DARK_AQUA)).append(Component.literal("!").withStyle(ChatFormatting.GREEN)));
        Msg.playSound(player, SoundEvents.BUNDLE_INSERT, 1.0f, 1.0f);
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
