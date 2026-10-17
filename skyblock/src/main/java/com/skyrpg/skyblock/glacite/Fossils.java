package com.skyrpg.skyblock.glacite;

import com.mojang.serialization.Codec;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.event.ItemGains;
import com.skyrpg.core.progress.SkyBlockXp;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyStacks;
import com.skyrpg.items.enchant.Books;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.essence.Essence;
import com.skyrpg.skyblock.mining.Hotm;
import com.skyrpg.skyblock.zone.Zones;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Fossil Research Center at the Dwarven Base Camp. The Fossil Excavator digs with a chisel and a Suspicious Scrap
 * ({@link ExcavatorMenu}); Dr. Stone takes each kind of fossil once (25,000 Mining XP, 25,000 Glacite Powder, 5,000 HotM XP,
 * 10 SkyBlock XP; every kind donated: +50,000 Glacite Powder and +10,000 HotM XP); Researcher Beryl sells Suspicious Scrap
 * for 100 Fossil Essence and salvages a scrap (5 Fossil Essence) or the fossils Dr. Stone already has (50, Claw 25).
 */
public final class Fossils {
    public static final String SCRAP = "SUSPICIOUS_SCRAP";
    private static final int SCRAP_PRICE = 100;
    private static final int SCRAP_SALVAGE = 5;

    /** A chisel: the item in the inventory, its charges and its chance (in %) to show something on uncovering dirt. */
    record Chisel(ItemStack stack, int charges, int highlight) {
    }

    /** A fossil: its name, item and shape in the dig ("X" = part of it). */
    record Fossil(String name, String item, String[] shape) {
    }

    /** The six fossils the excavator can find (the Claw and Helix come from the Crystal Nucleus). */
    static final List<Fossil> FOSSILS = List.of(
            new Fossil("Clubbed Fossil", "CLUBBED_FOSSIL", new String[] {"..XX", "XXXX", "..XX"}),
            new Fossil("Footprint Fossil", "FOOTPRINT_FOSSIL", new String[] {"X.X.X", ".XXX.", ".XXX."}),
            new Fossil("Spine Fossil", "SPINE_FOSSIL", new String[] {"X.X.X", "XXXXX"}),
            new Fossil("Tusk Fossil", "TUSK_FOSSIL", new String[] {"XX..", "X...", ".X..", "..XX"}),
            new Fossil("Ugly Fossil", "UGLY_FOSSIL", new String[] {".XX.", "XXXX", "X.XX"}),
            new Fossil("Webbed Fossil", "WEBBED_FOSSIL", new String[] {"X.X.X", "XXXXX", ".XXX."}));
    /** What Dr. Stone takes. */
    private static final List<String> DONATABLE = List.of("CLUBBED_FOSSIL", "FOOTPRINT_FOSSIL", "SPINE_FOSSIL", "TUSK_FOSSIL", "UGLY_FOSSIL",
            "WEBBED_FOSSIL", "CLAW_FOSSIL");

    /** The fossils this player has given Dr. Stone. */
    static final AttachmentType<List<String>> DONATED = AttachmentRegistry.create(SkyRpgSkyblock.id("fossils_donated"),
            builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

    private Fossils() {
    }

    static void init() {
        Actions.register("fossil_excavator", (player, params) -> {
            if (Hotm.level(player) < GlaciteTunnels.REQUIRED_HOTM) {
                player.sendSystemMessage(Component.literal("You need Heart of the Mountain " + GlaciteTunnels.REQUIRED_HOTM + " to use the Fossil Excavator!")
                        .withStyle(ChatFormatting.RED));
                return;
            }
            ExcavatorMenu.openSetup(player);
        });
        Actions.register("fossil_donate", (player, params) -> donate(player));
        Actions.register("fossil_research", (player, params) -> research(player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("fossilresearch")
                .then(Commands.literal("buy").executes(ctx -> {
                    buyScrap(ctx.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("salvage").executes(ctx -> {
                    salvage(ctx.getSource().getPlayerOrException(), false);
                    return 1;
                }).then(Commands.literal("scrap").executes(ctx -> {
                    salvage(ctx.getSource().getPlayerOrException(), true);
                    return 1;
                })))));
    }

    // ---------------------------------------------------------------- chisels

    /** The best chisel in the inventory, or null. */
    static Chisel bestChisel(ServerPlayer player) {
        String[] ids = {"PERFECT_CHISEL", "GLACITE_CHISEL", "REINFORCED_CHISEL", "CHISEL"};
        int[] charges = {22, 20, 18, 16};
        int[] highlight = {60, 60, 60, 25};
        for (int i = 0; i < ids.length; i++) {
            for (ItemStack stack : player.getInventory()) {
                if (ids[i].equals(SkyItems.skyblockId(stack))) {
                    return new Chisel(stack, charges[i], highlight[i]);
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- treasures

    /** A treasure in the dig: an item, Fossil Essence or Glacite Powder. */
    record Treasure(String kind, String id, int count) {
        ItemStack icon() {
            return switch (kind) {
                case "essence" -> named(Items.BONE_MEAL, "Fossil Essence", count, Essence.color(Essence.FOSSIL));
                case "powder" -> named(Items.PRISMARINE_CRYSTALS, "Glacite Powder", count, Hotm.powderColor(Hotm.GLACITE));
                default -> stack(id, count);
            };
        }

        MutableComponent text() {
            return switch (kind) {
                case "essence" -> Component.literal("+" + count + " Fossil Essence").withColor(Essence.color(Essence.FOSSIL));
                case "powder" -> Component.literal("+" + Msg.number(count) + " Glacite Powder").withColor(Hotm.powderColor(Hotm.GLACITE));
                default -> {
                    ItemStack stack = stack(id, count);
                    yield Component.literal(count > 1 ? count + "x " : "").withStyle(ChatFormatting.GRAY).append(stack.getHoverName());
                }
            };
        }

        void give(ServerPlayer player) {
            switch (kind) {
                case "essence" -> Essence.give(player, Essence.FOSSIL, count);
                case "powder" -> Hotm.givePowder(player, Hotm.GLACITE, count);
                default -> {
                    ItemStack stack = stack(id, count);
                    ItemGains.give(player, stack, ItemGains.Source.MINING);
                    if (!stack.isEmpty()) {
                        player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
                    }
                }
            }
        }
    }

    private static ItemStack named(net.minecraft.world.item.Item item, String name, int count, int color) {
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(Msg.number(count) + " " + name)
                .withStyle(style -> style.withItalic(false).withColor(color)));
        return stack;
    }

    /**
     * One treasure: Fossil Essence (10-40) 30, Glacite Powder (1,000-10,000) 20, an Enchanted Glacite, Umber or Tungsten 12
     * each, a Flawed Tunnels gem 8, a Fine one 2, Paleontologist I 1.5, Fossil the Fish 0.3.
     */
    static Treasure rollTreasure(RandomSource random) {
        double roll = random.nextDouble() * 97.8;
        String[] gems = {"AQUAMARINE", "ONYX", "CITRINE", "PERIDOT"};
        if ((roll -= 30) < 0) {
            return new Treasure("essence", "", 10 + random.nextInt(31));
        }
        if ((roll -= 20) < 0) {
            int[] amounts = {1000, 1000, 1000, 2000, 2000, 5000, 10000};
            return new Treasure("powder", "", amounts[random.nextInt(amounts.length)]);
        }
        String[] enchanted = {"ENCHANTED_GLACITE", "ENCHANTED_UMBER", "ENCHANTED_TUNGSTEN"};
        for (String id : enchanted) {
            if ((roll -= 12) < 0) {
                return new Treasure("item", id, 1 + random.nextInt(2));
            }
        }
        if ((roll -= 8) < 0) {
            return new Treasure("item", "FLAWED_" + gems[random.nextInt(gems.length)] + "_GEM", 1);
        }
        if ((roll -= 2) < 0) {
            return new Treasure("item", "FINE_" + gems[random.nextInt(gems.length)] + "_GEM", 1);
        }
        if ((roll -= 1.5) < 0) {
            return new Treasure("item", "book:paleontologist:1", 1);
        }
        return new Treasure("item", "FOSSIL_THE_FISH", 1);
    }

    /** A SkyBlock item ("book:enchant:level" for a book), or empty if we don't have it. */
    static ItemStack stack(String id, int count) {
        if (id.startsWith("book:")) {
            String[] parts = id.split(":");
            return Books.create(parts[1], Integer.parseInt(parts[2]), count);
        }
        ItemDefinition def = ItemRegistry.skyblock(id);
        return def == null ? ItemStack.EMPTY : SkyItems.create(def, count);
    }

    // ---------------------------------------------------------------- Dr. Stone

    /** Dr. Stone: every kind of fossil not given yet, from the inventory, for its rewards. */
    private static void donate(ServerPlayer player) {
        List<String> donated = new ArrayList<>(player.getAttachedOrElse(DONATED, List.of()));
        Notice.Panel panel = Notice.of(Notice.Kind.MINING).tag("DR. STONE").color(0xFFAA00).panel();
        int given = 0;
        for (String fossil : DONATABLE) {
            if (donated.contains(fossil) || SkyStacks.count(player.getInventory(), fossil) < 1) {
                continue;
            }
            SkyStacks.take(player.getInventory(), fossil, 1);
            donated.add(fossil);
            given++;
            SkillLevels.giveXp(player, SkillLevels.MINING, 25_000);
            Hotm.givePowder(player, Hotm.GLACITE, 25_000);
            Hotm.giveXp(player, 5_000);
            SkyBlockXp.give(player, 10, "Fossil donation");
            panel.line(Component.literal(" ").append(stack(fossil, 1).getHoverName()).append(Component.literal(": +25,000 Mining XP, +25,000 Glacite Powder, "
                    + "+5,000 HotM XP").withStyle(ChatFormatting.GRAY)));
            if (donated.size() == DONATABLE.size()) {
                Hotm.givePowder(player, Hotm.GLACITE, 50_000);
                Hotm.giveXp(player, 10_000);
                panel.line(Component.literal(" Every fossil donated! +50,000 Glacite Powder, +10,000 HotM XP").withStyle(ChatFormatting.GOLD));
            }
        }
        if (given == 0) {
            player.sendSystemMessage(Notice.build(Notice.Kind.MINING, Component.literal("Dr. Stone: Bring me a fossil I don't have yet! ")
                    .withStyle(ChatFormatting.YELLOW).append(Component.literal("(" + donated.size() + "/" + DONATABLE.size() + " donated)")
                            .withStyle(ChatFormatting.GRAY))));
            return;
        }
        player.setAttached(DONATED, List.copyOf(donated));
        panel.send(player, Component.literal("Thank you for the fossil" + (given > 1 ? "s" : "") + "! " + donated.size() + "/" + DONATABLE.size()
                + " donated").withStyle(ChatFormatting.YELLOW));
        Msg.playSound(player, SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    // ---------------------------------------------------------------- Researcher Beryl

    private static void research(ServerPlayer player) {
        long essence = Essence.get(player, Essence.FOSSIL);
        Notice.of(Notice.Kind.SHOP).tag("RESEARCHER BERYL").color(0x55FF55).panel()
                .line(Component.literal(" You have " + Msg.number(essence) + " Fossil Essence.").withColor(Essence.color(Essence.FOSSIL)))
                .line(Component.literal(" [Buy a Suspicious Scrap: " + SCRAP_PRICE + " Fossil Essence]").withStyle(style -> style
                        .withColor(ChatFormatting.GREEN).withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/fossilresearch buy"))))
                .line(Component.literal(" [Salvage fossils Dr. Stone already has: 50 each, Claw 25]")
                        .withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/fossilresearch salvage"))))
                .line(Component.literal(" [Salvage one Suspicious Scrap: " + SCRAP_SALVAGE + " Fossil Essence]")
                        .withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/fossilresearch salvage scrap"))))
                .send(player, Component.literal("Fossil Essence buys Suspicious Scrap.").withStyle(ChatFormatting.GREEN));
    }

    /** Beryl's trades only work in the Fossil Research Center's camp. */
    private static boolean atCamp(ServerPlayer player) {
        Zones.Zone zone = Zones.at(player.level(), player.blockPosition());
        if (zone != null && zone.name().equals(GlaciteTunnels.BASE_CAMP)) {
            return true;
        }
        player.sendSystemMessage(Component.literal("Talk to Researcher Beryl at the Dwarven Base Camp!").withStyle(ChatFormatting.RED));
        return false;
    }

    private static void buyScrap(ServerPlayer player) {
        if (!atCamp(player)) {
            return;
        }
        ItemStack scrap = stack(SCRAP, 1);
        if (scrap.isEmpty() || !Essence.take(player, Essence.FOSSIL, SCRAP_PRICE)) {
            player.sendSystemMessage(Component.literal("You need " + SCRAP_PRICE + " Fossil Essence!").withStyle(ChatFormatting.RED));
            Msg.playSound(player, SoundEvents.VILLAGER_NO, 0.8f, 1.0f);
            return;
        }
        ItemGains.give(player, scrap, ItemGains.Source.OTHER);
        player.sendSystemMessage(Notice.build(Notice.Kind.SHOP, Component.literal("You bought a Suspicious Scrap for " + SCRAP_PRICE + " Fossil Essence.")
                .withStyle(ChatFormatting.GREEN)));
        Msg.playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    /** Salvages one Suspicious Scrap, or every fossil Dr. Stone already has, for Fossil Essence. */
    private static void salvage(ServerPlayer player, boolean scrap) {
        if (!atCamp(player)) {
            return;
        }
        List<String> donated = scrap ? List.of() : player.getAttachedOrElse(DONATED, List.of());
        int essence = 0;
        if (scrap && SkyStacks.count(player.getInventory(), SCRAP) > 0) {
            SkyStacks.take(player.getInventory(), SCRAP, 1);
            essence += SCRAP_SALVAGE;
        }
        for (String fossil : donated) {
            int have = SkyStacks.count(player.getInventory(), fossil);
            if (have > 0) {
                SkyStacks.take(player.getInventory(), fossil, have);
                essence += have * (fossil.equals("CLAW_FOSSIL") ? 25 : 50);
            }
        }
        if (essence == 0) {
            player.sendSystemMessage(Component.literal("You have nothing to salvage.").withStyle(ChatFormatting.RED));
            return;
        }
        Essence.give(player, Essence.FOSSIL, essence);
        player.sendSystemMessage(Notice.build(Notice.Kind.SHOP, Component.literal("Salvaged for " + essence + " Fossil Essence.")
                .withColor(Essence.color(Essence.FOSSIL))));
        Msg.playSound(player, SoundEvents.GRINDSTONE_USE, 0.8f, 1.0f);
    }
}
