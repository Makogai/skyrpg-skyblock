package com.skyrpg.skyblock.festival;

import com.mojang.serialization.Codec;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.crimson.FactionMenu;
import com.skyrpg.skyblock.time.CalendarEvents;
import com.skyrpg.skyblock.time.SkyBlockTime;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Hypixel's New Year Celebration (Late Winter 29th to 31st): the Baker on the Hub plaza gives everyone one New Year
 * Cake for the year, the year on it (collectors keep every year's), and sells the New Year Cake Bag that holds them.
 */
final class NewYear {
    static final AttachmentType<List<Integer>> CLAIMED = AttachmentRegistry.create(SkyRpgSkyblock.id("new_year_cakes"),
            builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath());

    private NewYear() {
    }

    static void init() {
        Actions.register("baker", (player, params) -> open(player));
    }

    private static boolean on() {
        return CalendarEvents.running(System.currentTimeMillis(), "New Year Celebration");
    }

    static void open(ServerPlayer player) {
        int npc = FactionMenu.npcNear(player, "Baker");
        CardMenu.open(player, p -> {
            int year = SkyBlockTime.now().year();
            boolean claimed = p.getAttachedOrElse(CLAIMED, List.of()).contains(year);
            List<CardView.Card> cards = new ArrayList<>();
            cards.add(new CardView.Card("New Year Cake (Year " + year + ")", claimed ? "You've had your cake this year. Happy New Year!"
                    : on() ? "Free for everyone, once a year." : "Come back for the New Year Celebration.", 0xFF55FF,
                    on() && !claimed, claimed ? "Claimed" : "Claim", new ItemStack(Items.CAKE), "", List.of(), -1, ""));
            cards.add(new CardView.Card("Shop", "The New Year Cake Bag: keep every year's cake.", 0xEEEEEE, true, "Browse",
                    new ItemStack(Items.BUNDLE), "", List.of(), -1, ""));
            return new CardView("SKYBLOCK", "Baker", "New Year Celebration", npc, List.of(), cards, "Happy SkyBlock New Year!", 1, 40);
        }, (p, card) -> {
            if (card == 1) {
                com.google.gson.JsonObject params = new com.google.gson.JsonObject();
                params.addProperty("shop", "baker");
                Actions.run("skyblock_shop", p, params);
                return null;
            }
            int year = SkyBlockTime.now().year();
            List<Integer> claimed = new ArrayList<>(p.getAttachedOrElse(CLAIMED, List.of()));
            if (!on() || claimed.contains(year)) {
                return "";
            }
            ItemDefinition def = ItemRegistry.skyblock("NEW_YEAR_CAKE");
            if (def == null) {
                return "";
            }
            ItemStack cake = SkyItems.create(def, 1);
            cake.set(DataComponents.CUSTOM_NAME, Component.literal("New Year Cake (Year " + year + ")").withStyle(ChatFormatting.RED)
                    .withStyle(style -> style.withItalic(false)));
            cake.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Given to every player as a celebration of the "
                    + SkyBlockTime.ordinal(year) + " SkyBlock year!").withStyle(ChatFormatting.GRAY).withStyle(style -> style.withItalic(false)))));
            CustomData.update(DataComponents.CUSTOM_DATA, cake, tag -> tag.putInt("new_years_cake", year));
            if (!p.getInventory().add(cake)) {
                p.drop(cake, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            claimed.add(year);
            p.setAttached(CLAIMED, List.copyOf(claimed));
            p.sendSystemMessage(Component.literal("You claimed a New Year Cake for year " + year + "!").withStyle(ChatFormatting.LIGHT_PURPLE));
            Msg.playSound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1f, 1f);
            return "";
        });
    }
}
