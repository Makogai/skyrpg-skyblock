package com.skyrpg.skyblock.leveling;

import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.action.Actions;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The SkyBlock Leveling menu's tasks (Hypixel's "SkyBlock Leveling" categories): a card per category with its XP against
 * the most it gives, then the category's tasks, each with its progress bar. {@code /sblevel tasks}, the Leveling page's
 * Tasks panel, or the {@code leveling_tasks} action; the Emblems card opens {@link Emblems}.
 */
public final class LevelingMenu {
    /** The category each player is looking at (none: the categories). */
    private static final Map<UUID, LevelingTasks.Category> PAGE = new ConcurrentHashMap<>();

    private LevelingMenu() {
    }

    static void init() {
        Actions.register("leveling_tasks", (player, params) -> open(player));
    }

    public static void open(ServerPlayer player) {
        PAGE.remove(player.getUUID());
        CardMenu.open(player, LevelingMenu::view, LevelingMenu::click);
    }

    private static CardView view(ServerPlayer player) {
        LevelingTasks.Category page = PAGE.get(player.getUUID());
        int xp = SkyBlockXp.xp(player);
        List<Chip> wallet = List.of(Chip.note(new ItemStack(Items.EXPERIENCE_BOTTLE), "Level " + xp / SkyBlockXp.PER_LEVEL, true),
                Chip.note(new ItemStack(Items.LAPIS_LAZULI), Msg.number(xp) + " XP", true));
        List<CardView.Card> cards = new ArrayList<>();
        if (page == null) {
            for (LevelingTasks.Category category : LevelingTasks.Category.values()) {
                int earned = LevelingTasks.earned(player, category);
                int max = LevelingTasks.max(category);
                cards.add(new CardView.Card(category.title, category.about, category.color, true, "", new ItemStack(category.icon), "",
                        List.of(Chip.note(new ItemStack(Items.LAPIS_LAZULI), Msg.number(earned) + " / " + Msg.number(max) + " XP", earned >= max)),
                        max == 0 ? -1 : earned / (float) max, percent(earned, max)));
            }
            cards.add(new CardView.Card("Emblems", "Show an emblem after your name in chat", 0xF2F2F2, true, "", new ItemStack(Items.NAME_TAG), "",
                    List.of(Chip.note(new ItemStack(Items.NAME_TAG), Emblems.unlocked(player).size() + " unlocked", true)), -1, "Emblems"));
            int other = LevelingTasks.earned(player, "other");
            String footer = "Every 100 SkyBlock XP is a level." + (other > 0 ? " " + Msg.number(other) + " XP came from elsewhere (quests, older rewards)." : "");
            return new CardView("SKYBLOCK", "SkyBlock Leveling", "Tasks", -1, wallet, cards, footer, 2, 40);
        }
        cards.add(new CardView.Card("Back", "To the categories", 0xAAAAAA, true, "", new ItemStack(Items.ARROW), "", List.of(), -1, ""));
        for (LevelingTasks.Task task : LevelingTasks.tasks(page)) {
            int earned = Math.min(task.max(), LevelingTasks.earned(player, task.id()));
            boolean done = earned >= task.max();
            cards.add(new CardView.Card(task.name(), task.how(), done ? 0x55FF55 : 0xF2F2F2, false, "", new ItemStack(task.icon()), "",
                    List.of(Chip.note(new ItemStack(Items.LAPIS_LAZULI), Msg.number(earned) + " / " + Msg.number(task.max()) + " XP", done)),
                    earned / (float) task.max(), percent(earned, task.max())));
        }
        int earned = LevelingTasks.earned(player, page);
        return new CardView("SKYBLOCK", page.title, Msg.number(earned) + " / " + Msg.number(LevelingTasks.max(page)) + " XP", -1, wallet, cards,
                page.about, 2, 40);
    }

    private static String percent(int earned, int max) {
        return max == 0 ? "" : Math.round(100f * earned / max) + "%";
    }

    private static String click(ServerPlayer player, int card) {
        LevelingTasks.Category page = PAGE.get(player.getUUID());
        if (page == null) {
            LevelingTasks.Category[] all = LevelingTasks.Category.values();
            if (card >= 0 && card < all.length) {
                PAGE.put(player.getUUID(), all[card]);
                return "";
            }
            if (card == all.length) {
                Emblems.open(player);
                return "";
            }
            return "";
        }
        if (card == 0) {
            PAGE.remove(player.getUUID());
        }
        return "";
    }
}
