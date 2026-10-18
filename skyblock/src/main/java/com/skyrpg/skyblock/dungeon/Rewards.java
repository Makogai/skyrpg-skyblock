package com.skyrpg.skyblock.dungeon;

import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.enchant.Books;
import com.skyrpg.skyblock.essence.Essence;
import com.skyrpg.skyblock.mixin.InteractionAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The end of a run: the score, Catacombs and class XP, and each player's reward chests. Chests: Wood (free), Gold,
 * Diamond, then Emerald (rank A), Obsidian (S), Bedrock (S+), where the floor has them (prices in {@link Floors}). One
 * chest a run. Their loot is rolled at the end from the floor's table; a better rank makes the good rolls likelier.
 */
final class Rewards {
    static final String[] CHESTS = {"Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock"};
    static final int[] COLORS = {0x9C6B30, 0xFFAA00, 0x55FFFF, 0x55FF55, 0x8A5CD6, 0x555555};

    /** One chest: its tier, price, what's in it, and whether it's been opened. */
    static final class Chest {
        final int tier;
        final long cost;
        final List<ItemStack> items = new ArrayList<>();
        final Map<String, Integer> essence = new HashMap<>();
        boolean opened;

        Chest(int tier, long cost) {
            this.tier = tier;
            this.cost = cost;
        }
    }

    /** Each player's chests for this run, and whether they've opened one. */
    static final Map<Long, Map<UUID, List<Chest>>> CHESTS_BY_RUN = new HashMap<>();

    private Rewards() {
    }

    static void complete(Run run) {
        Score.Result score = run.result;
        double share = (0.3 + 0.7 * Math.min(1, score.total() / 300.0)) * (run.failed ? 0.25 : 1);
        Map<UUID, List<Chest>> chests = new HashMap<>();
        for (ServerPlayer player : run.online()) {
            int xp = (int) Math.round(run.floor.xp() * share);
            String picked = run.classes.getOrDefault(player.getUUID(), DungeonXp.selected(player));
            Notice.Panel panel = Notice.of(Notice.Kind.DUNGEON).tag(run.floor.number() == 0 ? "ENTRANCE" : "FLOOR " + Floors.roman(run.floor.number()))
                    .panel();
            panel.line(Component.literal(" Team Score: ").withStyle(ChatFormatting.GRAY).append(Component.literal(score.total() + " (" + score.rank() + ")")
                    .withStyle(rankColor(score.rank()), ChatFormatting.BOLD)));
            panel.line(Component.literal(" Skill " + score.skill() + "  Explore " + score.explore() + "  Speed " + score.speed() + "  Bonus "
                    + score.bonus()).withStyle(ChatFormatting.GRAY));
            panel.line(Component.literal(" Rooms " + Math.round(score.cleared() * 100) + "%  Secrets " + score.secretsFound() + "/" + score.secretsNeeded()
                    + "  Deaths " + score.deaths() + "  Time " + time(score.seconds())).withStyle(ChatFormatting.DARK_GRAY));
            panel.line(Component.literal(" +" + Msg.number(xp) + " Catacombs Experience").withStyle(ChatFormatting.DARK_AQUA));
            panel.line(Component.literal(" +" + Msg.number(xp) + " " + DungeonXp.title(picked) + " Experience").withStyle(ChatFormatting.GREEN));
            panel.send(player, Component.literal(run.floor.title() + (run.failed ? " failed!" : " complete!")).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            Msg.playSound(player, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
            if (!run.failed) {
                DungeonXp.complete(player, run.floor.code());
            }
            // XP after the score, so level-ups show under it.
            DungeonXp.give(player, DungeonXp.CATACOMBS, xp);
            for (String c : DungeonXp.CLASSES) {
                DungeonXp.give(player, c, c.equals(picked) ? xp : xp * 0.25, !c.equals(picked));
            }
            chests.put(player.getUUID(), roll(run, score.rank(), player.getRandom()));
        }
        CHESTS_BY_RUN.put(run.id, chests);
        if (run.failed) {
            run.broadcast(Component.literal("Back to the Dungeon Hub in 10 seconds...").withStyle(ChatFormatting.GRAY));
            return;
        }
        // The chests, in the middle of the arena.
        Vec3 at = run.boss != null ? run.boss.center() : run.spawnPoint();
        Interaction box = EntityTypes.INTERACTION.create(run.level, EntitySpawnReason.EVENT);
        if (box != null) {
            ((InteractionAccess) box).skyrpg$setWidth(1.6f);
            ((InteractionAccess) box).skyrpg$setHeight(1.6f);
            box.snapTo(at.x, at.y, at.z, 0, 0);
            box.addTag(run.tag);
            box.addTag(Catacombs.TAG);
            box.addTag(Catacombs.REWARDS);
            run.level.addFreshEntity(box);
        }
        run.level.setBlock(net.minecraft.core.BlockPos.containing(at), net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        run.label(at.add(0, 1.8, 0), Component.literal("Reward Chests").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                .append(Component.literal("\nright-click").withStyle(ChatFormatting.GRAY)));
        run.broadcast(Component.literal("Leave with /warp dungeon_hub when you're done.").withStyle(ChatFormatting.GRAY));
    }

    private static ChatFormatting rankColor(String rank) {
        return switch (rank) {
            case "S+", "S" -> ChatFormatting.GOLD;
            case "A" -> ChatFormatting.DARK_PURPLE;
            case "B" -> ChatFormatting.GREEN;
            case "C" -> ChatFormatting.BLUE;
            default -> ChatFormatting.GRAY;
        };
    }

    static String time(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    /** One loot entry: an item ("book:id:level" for a book) with a chance per chest tier (Wood..Bedrock), and how many. */
    private record Entry(String item, double[] chances, int min, int max) {
    }

    /** Each floor's loot ({@code skyrpg/dungeon_loot/loot.json}, written by {@code tools/skyblock/dungeon_loot.py}). */
    private static Map<Integer, List<Entry>> loot = Map.of();

    static void init() {
        com.skyrpg.core.data.JsonDataLoader.register(com.skyrpg.skyblock.SkyRpgSkyblock.id("dungeon_loot"), "skyrpg/dungeon_loot", files -> {
            Map<Integer, List<Entry>> loaded = new HashMap<>();
            for (com.google.gson.JsonElement file : files.values()) {
                if (!file.getAsJsonObject().has("floors")) {
                    continue;
                }
                file.getAsJsonObject().getAsJsonObject("floors").entrySet().forEach(e -> {
                    List<Entry> entries = new ArrayList<>();
                    for (com.google.gson.JsonElement el : e.getValue().getAsJsonArray()) {
                        com.google.gson.JsonObject o = el.getAsJsonObject();
                        var arr = o.getAsJsonArray("chances");
                        double[] chances = new double[6];
                        for (int i = 0; i < Math.min(6, arr.size()); i++) {
                            chances[i] = arr.get(i).getAsDouble();
                        }
                        entries.add(new Entry(o.get("item").getAsString(), chances, o.get("min").getAsInt(), o.get("max").getAsInt()));
                    }
                    loaded.put(Integer.parseInt(e.getKey()), List.copyOf(entries));
                });
            }
            loot = Map.copyOf(loaded);
        });
    }

    private static List<Chest> roll(Run run, String rank, RandomSource random) {
        double luck = switch (rank) {
            case "S+" -> 1.5;
            case "S" -> 1.25;
            case "A" -> 1.0;
            case "B" -> 0.8;
            case "C" -> 0.6;
            default -> 0.5;
        };
        int unlocked = switch (rank) {
            case "S+" -> 6;
            case "S" -> 5;
            case "A" -> 4;
            default -> 3;
        };
        List<Chest> out = new ArrayList<>();
        for (int tier = 0; tier < unlocked; tier++) {
            long cost = tier == 0 ? 0 : run.floor.chests()[tier - 1];
            if (cost < 0) {
                continue;
            }
            // Paul's Marauder: 20% off
            if (com.skyrpg.core.world.Mayor.active("marauder")) {
                cost = Math.round(cost * 0.8);
            }
            Chest chest = new Chest(tier, cost);
            int base = (1 + tier) * (2 + run.floor.number() * 3);
            chest.essence.put("UNDEAD", base + random.nextInt(base + 1));
            if (run.floor.number() >= 3 && tier >= 1) {
                chest.essence.put("WITHER", base / 2 + random.nextInt(base / 2 + 1));
            }
            List<Entry> table = new ArrayList<>(loot.getOrDefault(run.floor.number(), loot.getOrDefault(1, List.of())));
            if (run.floor.master()) {
                table.addAll(loot.getOrDefault(100 + run.floor.number(), List.of()));
            }
            for (Entry entry : table) {
                double chance = entry.chances()[tier] * (tier == 0 ? 1 : luck);
                if (random.nextDouble() < chance) {
                    ItemStack stack = stack(entry.item(), entry.min() + random.nextInt(entry.max() - entry.min() + 1));
                    if (!stack.isEmpty()) {
                        chest.items.add(stack);
                    }
                }
            }
            out.add(chest);
        }
        return out;
    }

    static ItemStack stack(String id, int count) {
        if (id.startsWith("book:")) {
            String[] parts = id.split(":");
            return Books.create(parts[1], Integer.parseInt(parts[2]), count);
        }
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? ItemStack.EMPTY : SkyItems.create(def, count);
    }

    static List<Chest> chestsOf(Run run, ServerPlayer player) {
        return CHESTS_BY_RUN.getOrDefault(run.id, Map.of()).getOrDefault(player.getUUID(), List.of());
    }

    /** Opens one of the player's chests: "" when it worked, else why not. */
    static String open(Run run, ServerPlayer player, int index) {
        List<Chest> chests = chestsOf(run, player);
        if (index < 0 || index >= chests.size()) {
            return "";
        }
        if (chests.stream().anyMatch(c -> c.opened)) {
            return "You already opened a chest this run!";
        }
        Chest chest = chests.get(index);
        if (chest.cost > 0 && !com.skyrpg.core.economy.Wallet.take(player, chest.cost)) {
            return "You need " + Msg.number(chest.cost) + " coins to open this chest!";
        }
        chest.opened = true;
        Notice.Panel panel = Notice.of(Notice.Kind.DUNGEON).tag("REWARDS").color(COLORS[chest.tier]).panel();
        chest.essence.forEach((kind, amount) -> {
            Essence.give(player, kind, amount);
            panel.line(Component.literal(" " + amount + " " + Essence.name(kind)).withColor(Essence.color(kind)));
        });
        for (ItemStack stack : chest.items) {
            ItemStack copy = stack.copy();
            panel.line(Component.literal(" " + (copy.getCount() > 1 ? copy.getCount() + "x " : "")).withStyle(ChatFormatting.GRAY).append(copy.getHoverName()));
            ItemGains.give(player, copy, ItemGains.Source.COMBAT);
            if (!copy.isEmpty()) {
                player.drop(copy, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
        }
        panel.send(player, Component.literal("You opened the " + CHESTS[chest.tier] + " Chest!").withColor(COLORS[chest.tier]));
        Msg.playSound(player, SoundEvents.CHEST_OPEN, 1.0f, 1.0f);
        return "";
    }
}
