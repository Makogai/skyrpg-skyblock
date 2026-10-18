package com.skyrpg.skyblock.rift;

import com.mojang.serialization.Codec;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.sack.Sacks;
import com.skyrpg.skyblock.ui.CardMenu;
import com.skyrpg.skyblock.ui.CardView;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * Timecharms (Hypixel's): eight trophies from all over the Rift, secured with Elise for the Rift Gallery. Each one
 * secured is +100 SkyBlock XP and opens more of the Rift (the gates in {@link RiftWorld}); the first earns the Rift
 * Necklace.
 */
final class Timecharms {
    static final String SUPREME = "RIFT_TROPHY_WYLDLY_SUPREME";
    /** Every Timecharm, in the order the Rift opens up, and what it's for. */
    static final List<String[]> ALL = List.of(
            new String[] {SUPREME, "Opens West Village, the Dreadfarm and the Mirrorverse"},
            new String[] {"RIFT_TROPHY_MIRRORED", "With Chicken N Egg, opens the Village Plaza"},
            new String[] {"RIFT_TROPHY_CHICKEN_N_EGG", "With the mirrored one, opens the Village Plaza"},
            new String[] {"RIFT_TROPHY_CITIZEN", "Opens the Living Cave"},
            new String[] {"RIFT_TROPHY_LAZY_LIVING", "Opens the Colosseum"},
            new String[] {"RIFT_TROPHY_SLIME", "Opens Barrier Street"},
            new String[] {"RIFT_TROPHY_VAMPIRIC", "Opens the Mountaintop"},
            new String[] {"RIFT_TROPHY_MOUNTAIN", "Opens the Wizardman Bureau"});

    static final AttachmentType<List<String>> SECURED = AttachmentRegistry.create(SkyRpgSkyblock.id("rift_timecharms"),
            builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

    private Timecharms() {
    }

    /** The Rift Necklace's stats by Timecharms secured (Hypixel's tiers, inside the Rift): Rift Time, Speed, Intelligence,
     *  Mana Regen, Rift Damage, Hearts. */
    private static final double[][] NECKLACE = {
            {60, 0, 0, 0, 0, 0}, {110, 0, 0, 0, 0, 0}, {155, 1, 5, 0, 0, 0}, {200, 2, 5, 10, 0, 0}, {245, 3, 10, 10, 0, 0},
            {290, 4, 10, 15, 1, 0}, {335, 5, 10, 20, 1, 1}, {380, 6, 10, 25, 1, 1}};

    /** Loads the class at startup (an attachment type made after a player's data is read drops what they secured), and
     *  the Rift Necklace's stats, which grow with every Timecharm secured. */
    static void init() {
        com.skyrpg.core.stats.Stats.registerSource(new com.skyrpg.core.stats.Stats.Source() {
            @Override
            public String name() {
                return "Rift Necklace";
            }

            @Override
            public void contribute(ServerPlayer player, com.skyrpg.core.stats.Stats.Contributor out) {
                int secured = player.getAttachedOrElse(SECURED, List.of()).size();
                if (secured == 0 || !Rift.isRift(player.level())) {
                    return;
                }
                double[] tier = NECKLACE[Math.min(NECKLACE.length, secured) - 1];
                out.add(com.skyrpg.core.stats.StatTypes.RIFT_TIME, tier[0]);
                out.add(com.skyrpg.core.stats.StatTypes.SPEED, tier[1]);
                out.add(com.skyrpg.core.stats.StatTypes.INTELLIGENCE, tier[2]);
                out.add(com.skyrpg.core.stats.StatTypes.RIFT_MANA_REGEN, tier[3]);
                out.add(com.skyrpg.core.stats.StatTypes.RIFT_DAMAGE, tier[4]);
                out.add(com.skyrpg.core.stats.StatTypes.RIFT_HEALTH, tier[5]);
            }
        });
    }

    static boolean secured(ServerPlayer player, String charm) {
        return player.getAttachedOrElse(SECURED, List.of()).contains(charm);
    }

    static String name(String charm) {
        ItemDefinition def = ItemRegistry.skyblock(charm);
        return def == null ? charm : def.name();
    }

    /** Elise's Gallery: every Timecharm, secured or not; bring one to secure it. */
    static void elise(ServerPlayer player, int npc) {
        CardMenu.open(player, p -> {
            List<CardView.Card> cards = new ArrayList<>();
            for (String[] charm : ALL) {
                boolean done = secured(p, charm[0]);
                boolean carried = Sacks.countAnywhere(p, charm[0]) > 0;
                ItemStack icon = RiftNpcs.icon(charm[0]);
                // The whole card is the button (two columns leave no room for one).
                cards.add(new CardView.Card(name(charm[0]), done ? "Secured in the Gallery" : charm[1], done ? 0x55FF55 : carried ? 0xFFFF55 : 0xFF5555,
                        !done && carried, "", icon, "", List.of(), -1, done ? "Secured" : carried ? "Secure" : ""));
            }
            int count = (int) ALL.stream().filter(c -> secured(p, c[0])).count();
            return new CardView("RIFT", "Elise", count + "/8 Timecharms secured", npc, List.of(), cards,
                    "Each Timecharm secured: +100 SkyBlock XP. Your first earns the Rift Necklace.", 2, 34);
        }, (p, card) -> {
            String charm = ALL.get(card)[0];
            if (secured(p, charm)) {
                return "";
            }
            if (!Sacks.takeAnywhere(p, charm, 1)) {
                return "Bring the " + name(charm) + " first.";
            }
            List<String> list = new ArrayList<>(p.getAttachedOrElse(SECURED, List.of()));
            boolean first = list.isEmpty();
            list.add(charm);
            p.setAttached(SECURED, List.copyOf(list));
            com.skyrpg.core.stats.Stats.refresh(p);
            SkyBlockXp.give(p, 100, name(charm));
            if (first) {
                ItemDefinition necklace = ItemRegistry.skyblock("RIFT_NECKLACE_INSIDE");
                if (necklace != null) {
                    ItemStack stack = SkyItems.create(necklace, 1);
                    if (!p.getInventory().add(stack)) {
                        p.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                    }
                }
            }
            Notice.of(Notice.Kind.SUCCESS).tag("TIMECHARM").panel()
                    .line(Component.literal(" " + ALL.get(card)[1] + ".").withStyle(ChatFormatting.GRAY))
                    .line(Component.literal(first ? " +100 SkyBlock XP  ·  Elise gives you the Rift Necklace" : " +100 SkyBlock XP")
                            .withStyle(ChatFormatting.GRAY))
                    .send(p, Component.literal(name(charm) + " secured!").withStyle(ChatFormatting.LIGHT_PURPLE));
            Msg.playSound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            return "";
        });
    }
}
