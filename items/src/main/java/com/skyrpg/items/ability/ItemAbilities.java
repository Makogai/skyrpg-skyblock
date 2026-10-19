package com.skyrpg.items.ability;

import com.google.gson.JsonObject;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.reforge.Reforges;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

/**
 * The ability an item has: its own, or on Necron's Blade and its four upgrades what its Wither scrolls give (Hypixel's
 * {@code ability_scroll}, put on at the anvil): the last scroll's ability, or Wither Impact with all three. A scroll on
 * its own does nothing.
 */
public final class ItemAbilities {
    public static final List<String> WITHER_SCROLLS = List.of("IMPLOSION_SCROLL", "SHADOW_WARP_SCROLL", "WITHER_SHIELD_SCROLL");
    public static final String ULTIMATE_WITHER_SCROLL = "ULTIMATE_WITHER_SCROLL";
    public static final Set<String> WITHER_BLADES = Set.of("NECRON_BLADE", "HYPERION", "ASTRAEA", "SCYLLA", "VALKYRIE");
    public static final ItemDefinition.Ability WITHER_IMPACT = new ItemDefinition.Ability("wither_impact", "Wither Impact", 300, 0,
            "Teleport 10 blocks ahead of you. Then implode dealing 10,000 damage to nearby enemies. Also applies the wither shield scroll "
                    + "ability reducing damage taken and granting an absorption shield for 5 seconds.", new JsonObject(), "RIGHT CLICK");

    private ItemAbilities() {
    }

    public static ItemDefinition.Ability of(ItemStack stack, ItemDefinition def) {
        if (def == null) {
            return null;
        }
        if ("ABILITY_SCROLL".equals(Reforges.category(def))) {
            return null;
        }
        List<String> scrolls = ItemMods.scrolls(stack);
        if (!scrolls.isEmpty()) {
            if (scrolls.containsAll(WITHER_SCROLLS)) {
                return WITHER_IMPACT;
            }
            ItemDefinition last = ItemRegistry.skyblock(scrolls.getLast());
            if (last != null && last.ability() != null) {
                return last.ability();
            }
        }
        return def.ability();
    }
}
