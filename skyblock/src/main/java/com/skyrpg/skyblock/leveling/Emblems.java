package com.skyrpg.skyblock.leveling;

import com.mojang.serialization.Codec;
import com.skyrpg.core.progress.SkyBlockEmblems;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.dungeon.DungeonXp;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import com.skyrpg.skyblock.ui.Chip;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hypixel's emblems: a symbol after your name in chat, unlocked by SkyBlock Levels (Diamond ♦ at 10 ... Golden Fleur De
 * Lis ⚜ at 500), skill levels (Mining Pickaxe at Mining 50, Mining Master at 60...) and the Catacombs (Catacombs
 * Swords at XL, the class emblems, Dungeon Runner for a Floor VII clear). Pick one in the SkyBlock Leveling menu's
 * Emblems page; hover it in chat for its name and how it's earned.
 */
public final class Emblems {
    /** An emblem: its id, symbol, name, colour, whether it's bold, how it's earned, and the check. */
    public record Emblem(String id, String symbol, String name, ChatFormatting color, boolean bold, String requirement,
                         Predicate<ServerPlayer> earned) {
        MutableComponent component() {
            MutableComponent text = Component.literal(symbol).withStyle(color);
            if (bold) {
                text = text.withStyle(ChatFormatting.BOLD);
            }
            return text.withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(Component.literal(name).withStyle(color)
                    .append(Component.literal("\n" + requirement).withStyle(ChatFormatting.GRAY)))));
        }
    }

    private static final List<Emblem> ALL = new ArrayList<>();

    /** The emblem each player shows (its id; "" for none). */
    private static final AttachmentType<String> PICKED = AttachmentRegistry.create(SkyRpgSkyblock.id("emblem"),
            builder -> builder.persistent(Codec.STRING).initializer(() -> "").copyOnDeath());

    private static void level(String id, String symbol, String name, int level, boolean bold) {
        ALL.add(new Emblem(id, symbol, name + " Emblem", level >= 500 ? ChatFormatting.GOLD : ChatFormatting.GRAY, bold,
                "SkyBlock Level " + level, p -> SkyBlockXp.level(p) >= level));
    }

    private static void skill(String id, String symbol, String name, String skill, int level, boolean bold) {
        Identifier skillId = Identifier.fromNamespaceAndPath("skyrpg_skills", skill);
        boolean master = name.endsWith("Master");
        ALL.add(new Emblem(id, symbol, name, master ? ChatFormatting.GOLD : ChatFormatting.GRAY, bold,
                SkillLevels.displayName(skillId) + " " + level, p -> SkillLevels.level(p, skillId) >= level));
    }

    private static void dungeon(String id, String symbol, String name, String key, int level, boolean gold, boolean bold) {
        String what = key.equals(DungeonXp.CATACOMBS) ? "The Catacombs" : DungeonXp.title(key);
        ALL.add(new Emblem(id, symbol, name, gold ? ChatFormatting.GOLD : ChatFormatting.GRAY, bold, what + " " + level,
                p -> DungeonXp.level(p, key) >= level));
    }

    static {
        level("diamond", "♦", "Diamond", 10, false);
        level("spade", "♠", "Spade", 20, false);
        level("heart", "❤", "Heart", 30, false);
        level("pristine", "✧", "Pristine", 40, false);
        level("arc_reactor", "⎊", "Arc Reactor", 50, false);
        level("marker", "፠", "Marker", 60, false);
        level("badge", "☬", "Badge", 70, false);
        level("star", "⚝", "Star", 80, true);
        level("boxes", "⧉", "Boxes", 90, false);
        level("jerry", "ꈔ", "Jerry", 100, true);
        level("globe", "㋖", "Globe", 120, true);
        level("soulflow", "⸎", "Soulflow", 140, false);
        level("warning", "⚠", "Warning", 160, false);
        level("mustache", "ꕁ", "Mustache", 180, false);
        level("helmet", "〠", "Helmet", 200, false);
        level("smiley", "ツ", "Sideways Smiley", 250, false);
        level("spaceship", "⥈", "Spaceship", 300, false);
        level("toxic", "☢", "Toxic", 350, false);
        level("biohazard", "☣", "Biohazard", 400, false);
        level("florette", "✾", "Florette", 450, false);
        level("fleur_de_lis", "⚜", "Golden Fleur De Lis", 500, false);

        skill("mining_pickaxe", "⸕", "Mining Pickaxe", "mining", 50, true);
        skill("mining_master", "⸕", "Mining Master", "mining", 60, true);
        skill("farming_flower", "✿", "Farming Flower", "farming", 50, false);
        skill("farming_master", "✿", "Farming Master", "farming", 60, false);
        skill("combat_explosion", "❁", "Combat Explosion", "combat", 50, false);
        skill("combat_master", "❁", "Combat Master", "combat", 60, false);
        skill("foraging_leaf", "⸙", "Foraging Leaf", "foraging", 50, false);
        skill("fishing_fish", "α", "Fishing Fish", "fishing", 50, false);
        skill("enchanting_pencil", "✎", "Enchanting Pencil", "enchanting", 50, false);
        skill("enchanting_master", "✎", "Enchanting Master", "enchanting", 60, false);
        skill("alchemy_brew", "☕", "Alchemy Brew", "alchemy", 50, false);
        skill("carpentry_house", "☖", "Carpentry House", "carpentry", 50, false);
        skill("taming_clover", "♣", "Taming Clover", "taming", 50, false);
        skill("taming_master", "♣", "Taming Master", "taming", 60, false);
        skill("social_statement", "℻", "Social Statement", "social", 15, true);
        skill("social_master", "℻", "Social Master", "social", 25, true);

        dungeon("catacombs_swords", "⚔", "Catacombs Swords", DungeonXp.CATACOMBS, 40, false, true);
        dungeon("golden_catacombs_swords", "⚔", "Golden Catacombs Swords", DungeonXp.CATACOMBS, 50, true, true);
        dungeon("archer_spade", "➶", "Archer Spade", "archer", 40, false, false);
        dungeon("archer_master", "➶", "Archer Master", "archer", 50, true, false);
        dungeon("mage_lightning", "⚡", "Mage Lightning", "mage", 40, false, true);
        dungeon("mage_master", "⚡", "Mage Master", "mage", 50, true, true);
        dungeon("berserk_explosion", "☄", "Berserk Explosion", "berserk", 40, false, false);
        dungeon("berserk_master", "☄", "Berserk Master", "berserk", 50, true, false);
        dungeon("healer_staff", "⚚", "Healer Staff", "healer", 40, false, false);
        dungeon("healer_master", "⚚", "Healer Master", "healer", 50, true, false);
        dungeon("tank_anchor", "⚓", "Tank Anchor", "tank", 40, false, false);
        dungeon("tank_master", "⚓", "Tank Master", "tank", 50, true, false);
        ALL.add(new Emblem("dungeon_runner", "☠", "Dungeon Runner", ChatFormatting.GRAY, false, "Clear The Catacombs - Floor VII",
                p -> DungeonXp.completions(p, "F7") > 0));
        ALL.add(new Emblem("dungeon_master", "☠", "Dungeon Master", ChatFormatting.GOLD, false, "Clear Master Mode Floor VII",
                p -> DungeonXp.completions(p, "M7") > 0));
    }

    private Emblems() {
    }

    static void init() {
        SkyBlockEmblems.install(player -> {
            Emblem emblem = picked(player);
            return emblem == null ? null : emblem.component();
        });
    }

    public static List<Emblem> all() {
        return List.copyOf(ALL);
    }

    public static List<Emblem> unlocked(ServerPlayer player) {
        return ALL.stream().filter(e -> e.earned().test(player)).toList();
    }

    /** The emblem the player shows, if they still have it. */
    public static Emblem picked(ServerPlayer player) {
        String id = player.getAttachedOrElse(PICKED, "");
        for (Emblem emblem : ALL) {
            if (emblem.id().equals(id)) {
                return emblem.earned().test(player) ? emblem : null;
            }
        }
        return null;
    }

    static void open(ServerPlayer player) {
        CardMenu.open(player, Emblems::view, Emblems::click);
    }

    private static CardView view(ServerPlayer player) {
        Emblem picked = picked(player);
        List<CardView.Card> cards = new ArrayList<>();
        cards.add(new CardView.Card("Back", "To SkyBlock Leveling", 0xAAAAAA, true, "", new ItemStack(Items.ARROW), "", List.of(), -1, ""));
        cards.add(new CardView.Card("No emblem", "Show nothing after your name", 0xAAAAAA, true, "", new ItemStack(Items.BARRIER), "", List.of(), -1,
                picked == null ? "Shown" : ""));
        for (Emblem emblem : ALL) {
            boolean has = emblem.earned().test(player);
            boolean shown = emblem == picked;
            int color = has ? (emblem.color() == ChatFormatting.GOLD ? 0xFFAA00 : 0xF2F2F2) : 0x777777;
            cards.add(new CardView.Card(emblem.symbol() + "  " + emblem.name(), (has ? "Unlocked · " : "Locked · ") + emblem.requirement(), color,
                    has && !shown, "", new ItemStack(has ? Items.NAME_TAG : Items.DYE.gray()), "", List.of(), -1,
                    shown ? "Shown" : has ? "Show it" : ""));
        }
        return new CardView("SKYBLOCK", "Emblems", unlocked(player).size() + " of " + ALL.size() + " unlocked", -1, List.of(), cards,
                "Your emblem shows after your name in chat. Hover it there to see what it is.", 3, 36);
    }

    private static String click(ServerPlayer player, int card) {
        if (card == 0) {
            LevelingMenu.open(player);
            return "";
        }
        if (card == 1) {
            player.setAttached(PICKED, "");
            return "";
        }
        int index = card - 2;
        if (index < 0 || index >= ALL.size()) {
            return "";
        }
        Emblem emblem = ALL.get(index);
        if (!emblem.earned().test(player)) {
            return "You haven't unlocked the " + emblem.name() + " yet: " + emblem.requirement() + ".";
        }
        player.setAttached(PICKED, emblem.id());
        player.sendSystemMessage(Component.literal("You're showing the ").withStyle(ChatFormatting.GREEN).append(emblem.component())
                .append(Component.literal(" " + emblem.name() + ".").withStyle(ChatFormatting.GREEN)));
        return "";
    }
}
