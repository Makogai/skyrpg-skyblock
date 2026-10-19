package com.skyrpg.items.client;

import com.skyrpg.core.items.ToolTiers;
import com.skyrpg.core.items.ToolWear;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.ItemSet;
import com.skyrpg.items.Rarity;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.backpack.Backpacks;
import com.skyrpg.items.enchant.Enchants;
import com.skyrpg.items.reforge.Reforges;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Builds the tooltip for SkyRPG items from their definition: name, stat lines with their icons,
 * description, ability, set bonus (with your worn pieces counted) and the rarity line.
 */
final class ItemTooltips {
    /** Paragraphs wrap at the name's width, but no narrower than this... */
    private static final int MIN_WRAP = 170;
    /** ...and no wider than this, so a long custom name doesn't make one very wide line. */
    private static final int MAX_WRAP = 220;

    /** Set per tooltip from its name line; tooltips are built one at a time on the render thread. */
    private static int wrapWidth = MIN_WRAP;

    private ItemTooltips() {
    }

    static void replace(ItemStack stack, TooltipFlag flag, List<Component> lines) {
        ItemDefinition def = SkyItems.definition(stack);
        if (def == null && !lines.isEmpty() && hasTier(stack)) {
            lines.add(1, tierLine(stack)); // vanilla pickaxes and axes say their tier too
        }
        // Pets (Hypixel's PET item) and brewed potions carry their own name and lore, written for what they are.
        if (def == null || lines.isEmpty() || "PET".equals(def.skyblockId())
                || "POTION".equals(def.skyblockId()) && stack.has(DataComponents.LORE)) {
            return;
        }
        List<Component> out = new ArrayList<>();
        Rarity rarity = ItemMods.rarity(stack, def);
        out.add(stack.has(DataComponents.CUSTOM_NAME) ? lines.getFirst() : Component.literal(ItemMods.name(stack, def)).withColor(rarity.color()));
        wrapWidth = Mth.clamp(Minecraft.getInstance().font.width(out.getFirst()), MIN_WRAP, MAX_WRAP);

        // Weapon damage first, then the rest in the usual stat order; SkyBlock items in Hypixel's order, as listed, then
        // whatever only their reforge or potato books add.
        List<StatType> order = new ArrayList<>(StatTypes.all());
        order.remove(StatTypes.DAMAGE);
        order.addFirst(StatTypes.DAMAGE);
        Map<StatType, Double> total = ItemMods.stats(stack, def);
        if (def.skyblock()) {
            List<StatType> listed = new ArrayList<>(def.stats().keySet());
            order.stream().filter(stat -> total.containsKey(stat) && !listed.contains(stat)).forEach(listed::add);
            order = listed;
        }
        Map<StatType, Double> potato = ItemMods.potatoStats(stack, def);
        Map<StatType, Double> reforged = ItemMods.reforgeStats(stack, def);
        Map<StatType, Double> gemmed = ItemMods.gemStats(stack, def);
        for (StatType stat : order) {
            double value = total.getOrDefault(stat, 0.0);
            if (value != 0) {
                MutableComponent line = Component.literal(stat.symbol() + " ").withColor(stat.color())
                        .append(Component.literal(stat.name() + " ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(stat.formatBonus(value)).withColor(stat.color()));
                // Hypixel's markers: (+N) in yellow for potato books, in blue for the reforge.
                if (potato.containsKey(stat)) {
                    line.append(Component.literal(" (" + stat.formatBonus(potato.get(stat)) + ")").withStyle(ChatFormatting.YELLOW));
                }
                if (reforged.containsKey(stat)) {
                    line.append(Component.literal(" (" + stat.formatBonus(reforged.get(stat)) + ")").withStyle(ChatFormatting.BLUE));
                }
                // ...and in pink for its gemstones.
                if (gemmed.containsKey(stat)) {
                    line.append(Component.literal(" (" + stat.formatBonus(gemmed.get(stat)) + ")").withStyle(ChatFormatting.LIGHT_PURPLE));
                }
                out.add(line);
            }
        }
        Component slots = gemSlotsLine(stack, def);
        if (slots != null) {
            out.add(slots);
        }

        enchantLines(stack, out);

        if (hasTier(stack)) {
            out.add(tierLine(stack));
        }
        if (SkyItems.isTool(stack) && stack.isDamageableItem()) {
            out.add(durabilityLine(stack));
        }

        // Perks: what the item's gear effects do, in plain words.
        if (!def.perks().isEmpty()) {
            out.add(Component.empty());
            for (String perk : def.perks()) {
                List<String> wrapped = wrap(perk);
                for (int i = 0; i < wrapped.size(); i++) {
                    out.add(Component.literal((i == 0 ? "✔ " : "  ") + wrapped.get(i)).withStyle(ChatFormatting.GREEN));
                }
            }
        }

        // Keep what vanilla adds after the name (enchantments, lore).
        if (lines.size() > 1 && !flag.isAdvanced()) {
            out.add(Component.empty());
            out.addAll(lines.subList(1, lines.size()));
        }

        if (!def.description().isEmpty()) {
            out.add(Component.empty());
            if (def.skyblock()) {
                // Hypixel's lore, with its own formatting codes.
                wrapFormatted(def.description()).forEach(line -> out.add(Component.literal(line)));
            } else {
                wrap(def.description()).forEach(line -> out.add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
            }
        }

        if (Backpacks.isBackpack(stack)) {
            long used = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().count();
            out.add(Component.literal("Holds ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(used + "/" + Backpacks.capacity(stack)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" stacks").withStyle(ChatFormatting.GRAY)));
            out.add(Component.literal("Carry it in a backpack slot (E), press B to open").withStyle(ChatFormatting.DARK_GRAY));
        }

        ItemDefinition.Ability ability = com.skyrpg.items.ability.ItemAbilities.of(stack, def);
        if (ability == null && def.ability() != null) {
            // a Wither scroll: its ability once it's on a Necron's Blade
            out.add(Component.empty());
            out.add(Component.literal("Scroll: " + def.ability().name()).withStyle(ChatFormatting.GOLD));
            wrap(def.ability().description()).forEach(line -> out.add(Component.literal(line).withStyle(ChatFormatting.GRAY)));
            out.add(Component.literal("Apply it to a Necron's Blade at an anvil.").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (ability != null) {
            out.add(Component.empty());
            // Hypixel's trigger ("RIGHT CLICK", "SNEAK"...) as NEU's lore names it; our own abilities are right-clicks.
            String trigger = ability.trigger();
            java.util.List<String> scrolls = com.skyrpg.items.ItemMods.scrolls(stack);
            if (!scrolls.isEmpty()) {
                out.add(Component.literal("Scrolls: " + scrolls.stream().map(s -> {
                    ItemDefinition scroll = com.skyrpg.items.ItemRegistry.skyblock(s);
                    return scroll == null ? s : scroll.name();
                }).collect(java.util.stream.Collectors.joining(", "))).withStyle(ChatFormatting.DARK_GRAY));
            }
            out.add(Component.literal("Ability: " + ability.name() + " ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(trigger).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD)));
            wrap(ability.description()).forEach(line -> out.add(Component.literal(line).withStyle(ChatFormatting.GRAY)));
            String attuned = com.skyrpg.items.ItemMods.attunement(stack, def);
            if (!attuned.isEmpty()) {
                int color = switch (attuned) {
                    case "Ashen" -> 0x555555;
                    case "Auric" -> 0xFFD24A;
                    case "Crystal" -> 0x55FFFF;
                    default -> 0xFFFFFF;
                };
                out.add(Component.literal("Attuned: ").withStyle(ChatFormatting.GRAY).append(Component.literal(attuned).withColor(color)));
            }
            MutableComponent cost = Component.empty();
            if (ability.mana() > 0) {
                cost.append(Component.literal(StatTypes.MANA_ICON + " " + Msg.number(ability.mana()) + " Mana").withStyle(ChatFormatting.AQUA));
            }
            if (ability.cooldownSeconds() > 0) {
                if (ability.mana() > 0) {
                    cost.append(Component.literal("   ").withStyle(ChatFormatting.DARK_GRAY));
                }
                cost.append(Component.literal("Recharge " + Msg.number(ability.cooldownSeconds()) + "s").withStyle(ChatFormatting.DARK_GREEN));
            }
            if (!cost.getSiblings().isEmpty()) {
                out.add(cost);
            }
        }

        ItemSet set = ItemRegistry.set(def.set());
        if (set != null) {
            Minecraft minecraft = Minecraft.getInstance();
            int worn = minecraft.player == null ? 0 : SkyItems.wornPieces(minecraft.player).getOrDefault(set.id(), 0);
            boolean complete = worn >= set.pieces();
            out.add(Component.empty());
            out.add(Component.literal(set.name() + " ").withStyle(complete ? ChatFormatting.GREEN : ChatFormatting.GOLD)
                    .append(Component.literal("(" + worn + "/" + set.pieces() + ")").withStyle(complete ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_GRAY)));
            out.add(Component.literal(set.bonusName()).withStyle(complete ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            wrap(set.bonusDescription()).forEach(line -> out.add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY)));
            for (Map.Entry<StatType, Double> bonus : set.bonusStats().entrySet()) {
                StatType stat = bonus.getKey();
                out.add(Component.literal(stat.symbol() + " " + stat.formatBonus(bonus.getValue()) + " " + stat.name())
                        .withColor(complete ? stat.color() : 0xFF707070));
            }
        }

        Reforges.Reforge reforge = ItemMods.reforge(stack);
        if (reforge != null && !reforge.ability().isEmpty()) {
            out.add(Component.empty());
            out.add(Component.literal(reforge.name() + " Bonus").withStyle(ChatFormatting.BLUE));
            wrapFormatted(reforge.ability()).forEach(line -> out.add(Component.literal(line)));
        }

        out.add(Component.empty());
        if (def.skyblock()) {
            if (reforge == null && !Reforges.blacksmith(def, rarity).isEmpty()) {
                out.add(Component.literal("This item can be reforged!").withStyle(ChatFormatting.DARK_GRAY));
            }
            // Hypixel's rarity line: "LEGENDARY HELMET", bold; a Recombobulator 3000 wraps it in flickering marks.
            MutableComponent rarityLine = Component.literal((rarity.label() + " " + SkyItems.typeLabel(def)).trim().toUpperCase(java.util.Locale.ROOT))
                    .withStyle(style -> style.withColor(rarity.color()).withBold(true));
            if (ItemMods.recombobulated(stack)) {
                // Siblings inherit their parent's style, so the marks and the line sit side by side under a plain parent.
                MutableComponent mark = Component.literal("a").withStyle(style -> style.withColor(rarity.color()).withBold(true).withObfuscated(true));
                rarityLine = Component.empty().append(mark.copy()).append(Component.literal(" ")).append(rarityLine)
                        .append(Component.literal(" ")).append(mark);
            }
            out.add(rarityLine);
        } else {
            out.add(Component.literal("\u25C6 " + def.rarity().label() + " " + SkyItems.typeLabel(def)).withColor(def.rarity().color()));
        }
        if (flag.isAdvanced()) {
            out.add(Component.literal(def.id().toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.clear();
        lines.addAll(out);
    }

    /**
     * Hypixel's enchantment lines: ultimates first in bold pink, then the rest in blue; with five or fewer each comes
     * with what it does, with more they're listed three to a line.
     */
    private static void enchantLines(ItemStack stack, List<Component> out) {
        Map<String, Integer> enchants = ItemMods.enchantments(stack);
        if (enchants.isEmpty()) {
            return;
        }
        List<Map.Entry<String, Integer>> order = new ArrayList<>(enchants.entrySet());
        order.sort((a, b) -> Boolean.compare(ultimate(b.getKey()), ultimate(a.getKey())));
        out.add(Component.empty());
        if (order.size() <= 5) {
            for (Map.Entry<String, Integer> entry : order) {
                Enchants.Enchant enchant = Enchants.get(entry.getKey());
                out.add(enchantName(entry.getKey(), entry.getValue()));
                Enchants.Level level = enchant == null ? null : enchant.level(Math.min(entry.getValue(), enchant.max()));
                if (level != null) {
                    level.lore().forEach(line -> out.add(Component.literal(line)));
                }
            }
            return;
        }
        MutableComponent line = null;
        int onLine = 0;
        for (Map.Entry<String, Integer> entry : order) {
            if (line == null || onLine == 3) {
                if (line != null) {
                    out.add(line);
                }
                line = Component.empty();
                onLine = 0;
            } else {
                line.append(Component.literal(", ").withStyle(ChatFormatting.BLUE));
            }
            line.append(enchantName(entry.getKey(), entry.getValue()));
            onLine++;
        }
        out.add(line);
    }

    private static boolean ultimate(String id) {
        Enchants.Enchant enchant = Enchants.get(id);
        return enchant != null && enchant.ultimate();
    }

    private static MutableComponent enchantName(String id, int level) {
        Enchants.Enchant enchant = Enchants.get(id);
        String label = enchant == null ? id + " " + Enchants.roman(level) : enchant.label(level);
        return enchant != null && enchant.ultimate()
                ? Component.literal(label).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                : Component.literal(label).withStyle(ChatFormatting.BLUE);
    }

    private static boolean hasTier(ItemStack stack) {
        return (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)) && ToolTiers.tier(stack) > 0;
    }

    /** "⛏ Tier IV pickaxe": which of our ores or woods it can break (see ToolTiers). */
    private static Component tierLine(ItemStack stack) {
        boolean axe = stack.is(ItemTags.AXES);
        return Component.literal(axe ? "⚒ " : "⛏ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Tier " + ToolTiers.roman(ToolTiers.tier(stack)) + (axe ? " axe" : " pickaxe")).withStyle(ChatFormatting.GRAY));
    }

    /** "Durability 450 / 600" (green, then yellow, then red as it wears), or "Broken: repair it" at 1. */
    private static Component durabilityLine(ItemStack stack) {
        int max = stack.getMaxDamage();
        int left = max - stack.getDamageValue();
        if (ToolWear.isBroken(stack)) {
            return Component.literal("⚒ ").withStyle(ChatFormatting.RED)
                    .append(Component.literal("Broken: repair it").withStyle(ChatFormatting.RED));
        }
        float ratio = left / (float) max;
        ChatFormatting color = ratio > 0.5f ? ChatFormatting.GREEN : ratio > 0.2f ? ChatFormatting.YELLOW : ChatFormatting.RED;
        return Component.literal("⚒ ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("Durability ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(Msg.number(left) + " / " + Msg.number(max)).withStyle(color));
    }

    /** "UNCOMMON" -> "Uncommon". */
    /**
     * Like {@link #wrap}, for text with formatting codes and line breaks: each wrapped line starts with the colour and
     * styles still active where the previous one ended.
     */
    private static List<String> wrapFormatted(String text) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            String carried = "";
            for (String line : wrap(paragraph)) {
                String full = carried + line;
                lines.add(full);
                carried = activeCodes(full);
            }
        }
        return lines;
    }

    /** Hypixel's gem slot row: " [❤] [⸕] [✦]", each slot in its gem's colour, bracketed by the gem's tier; locked ones dark. */
    private static Component gemSlotsLine(ItemStack stack, ItemDefinition def) {
        List<com.skyrpg.items.gem.Gemstones.Slot> slots = com.skyrpg.items.gem.Gemstones.slots(def);
        if (slots.isEmpty()) {
            return null;
        }
        Map<String, com.skyrpg.items.gem.Gemstones.Gem> gems = new java.util.HashMap<>();
        com.skyrpg.items.gem.Gemstones.gems(stack).forEach(gem -> gems.put(gem.slot(), gem));
        MutableComponent line = Component.empty();
        for (com.skyrpg.items.gem.Gemstones.Slot slot : slots) {
            com.skyrpg.items.gem.Gemstones.Gem gem = gems.get(slot.id());
            boolean open = com.skyrpg.items.gem.Gemstones.unlocked(stack, slot);
            String type = gem != null ? gem.type() : slot.type();
            ChatFormatting bracket = !open ? ChatFormatting.DARK_GRAY : gem == null ? ChatFormatting.GRAY : switch (gem.tier()) {
                case "FLAWED" -> ChatFormatting.GREEN;
                case "FINE" -> ChatFormatting.BLUE;
                case "FLAWLESS" -> ChatFormatting.DARK_PURPLE;
                case "PERFECT" -> ChatFormatting.GOLD;
                default -> ChatFormatting.WHITE;
            };
            line.append(Component.literal(" [").withStyle(bracket))
                    .append(Component.literal(gemSymbol(type)).withStyle(open ? gemColor(type) : ChatFormatting.DARK_GRAY))
                    .append(Component.literal("]").withStyle(bracket));
        }
        return line;
    }

    private static String gemSymbol(String type) {
        return switch (type) {
            case "RUBY" -> "❤";
            case "AMBER" -> "⸕";
            case "TOPAZ" -> "✧";
            case "JADE", "CITRINE", "PERIDOT" -> "☘";
            case "SAPPHIRE" -> "✎";
            case "AMETHYST" -> "❈";
            case "JASPER" -> "❁";
            case "OPAL", "UNIVERSAL" -> "❂";
            case "ONYX" -> "☠";
            case "AQUAMARINE" -> "α";
            case "COMBAT", "OFFENSIVE" -> "⚔";
            case "DEFENSIVE" -> "☤";
            case "MINING" -> "✦";
            default -> "◆";
        };
    }

    private static ChatFormatting gemColor(String type) {
        return switch (type) {
            case "RUBY" -> ChatFormatting.RED;
            case "AMBER" -> ChatFormatting.GOLD;
            case "TOPAZ" -> ChatFormatting.YELLOW;
            case "JADE", "PERIDOT" -> ChatFormatting.GREEN;
            case "SAPPHIRE", "AQUAMARINE" -> ChatFormatting.AQUA;
            case "AMETHYST", "JASPER" -> ChatFormatting.LIGHT_PURPLE;
            case "ONYX" -> ChatFormatting.DARK_GRAY;
            case "CITRINE" -> ChatFormatting.DARK_RED;
            case "COMBAT", "OFFENSIVE" -> ChatFormatting.DARK_RED;
            case "DEFENSIVE" -> ChatFormatting.GREEN;
            case "MINING" -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.WHITE;
        };
    }

    /** The colour and style codes in effect at the end of this text ("\u00a77\u00a7o"). */
    private static String activeCodes(String text) {
        String color = "";
        StringBuilder styles = new StringBuilder();
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) != '\u00a7') {
                continue;
            }
            char code = Character.toLowerCase(text.charAt(i + 1));
            if ("0123456789abcdef".indexOf(code) >= 0) {
                color = "\u00a7" + code;
                styles.setLength(0);
            } else if (code == 'r') {
                color = "";
                styles.setLength(0);
            } else if ("klmno".indexOf(code) >= 0) {
                styles.append('\u00a7').append(code);
            }
            i++;
        }
        return color + styles;
    }

    /** Splits text into lines at word boundaries, as wide as the tooltip's name line (within limits). */
    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        for (FormattedText line : Minecraft.getInstance().font.getSplitter().splitLines(text, wrapWidth, Style.EMPTY)) {
            lines.add(line.getString());
        }
        return lines;
    }
}
