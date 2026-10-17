package com.skyrpg.items.accessory;

import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import net.minecraft.server.level.ServerPlayer;

/** Stats from worn accessories: each one's item stats, like armour in its slot. */
final class AccessoryStatSource implements Stats.Source {
    @Override
    public String name() {
        return "Accessories";
    }

    @Override
    public void contribute(ServerPlayer player, Stats.Contributor out) {
        for (AccessorySlot slot : AccessorySlot.values()) {
            net.minecraft.world.item.ItemStack worn = Accessories.worn(player, slot);
            ItemDefinition def = SkyItems.definition(worn);
            if (def != null) {
                // With its reforge (equipment reforges) and potato books.
                com.skyrpg.items.ItemMods.stats(worn, def).forEach((stat, value) -> {
                    if (StatTypes.isPlayerStat(stat)) {
                        out.add(stat, value);
                    }
                });
            }
        }
    }
}
