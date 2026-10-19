package com.skyrpg.skyblock.accessory;

import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * The Accessory Enrichment Swapper: Shift + right-click picks which enrichment it swaps to (it remembers), right-click
 * changes every enriched accessory in your Accessory Bag to that one and uses it up. (Enrichments themselves go on at the
 * Anvil, like a Recombobulator.)
 */
public final class EnrichmentSwapper {
    public static final String ID = "TALISMAN_ENRICHMENT_SWAPPER";
    private static final String TARGET = "enrichment_swapper_target";

    private EnrichmentSwapper() {
    }

    static void init() {
        // Before the Hub's protection, which won't let you use a base item such as a comparator.
        Identifier early = SkyRpgSkyblock.id("swapper_early");
        UseItemCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(early, (player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            if (!ID.equals(SkyItems.skyblockId(held))) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer server) {
                use(server, held);
            }
            return InteractionResult.SUCCESS;
        });
    }

    /** The enrichment the swapper is set to (the first one until it's changed). */
    public static ItemMods.Enrichment target(ItemStack swapper) {
        CompoundTag tag = ItemMods.tag(swapper);
        ItemMods.Enrichment set = tag == null ? null : ItemMods.enrichmentById(tag.getString(TARGET).orElse(null));
        return set != null ? set : ItemMods.ENRICHMENTS.getFirst();
    }

    private static void use(ServerPlayer player, ItemStack swapper) {
        ItemMods.Enrichment target = target(swapper);
        if (player.isShiftKeyDown()) {
            List<ItemMods.Enrichment> all = ItemMods.ENRICHMENTS;
            ItemMods.Enrichment next = all.get((all.indexOf(target) + 1) % all.size());
            ItemMods.edit(swapper, tag -> tag.putString(TARGET, next.id()));
            player.sendOverlayMessage(Component.literal("Swapping to: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(next.name() + " Enrichment").withStyle(ChatFormatting.DARK_AQUA)));
            return;
        }
        int pages = player.getAttachedOrElse(AccessoryBag.PAGES, List.<net.minecraft.world.item.component.ItemContainerContents>of()).size();
        int changed = 0;
        for (int page = 0; page < pages; page++) {
            NonNullList<ItemStack> items = AccessoryBag.page(player, page);
            boolean touched = false;
            for (int i = 0; i < items.size(); i++) {
                ItemStack stack = items.get(i);
                ItemDefinition def = SkyItems.definition(stack);
                if (def != null && ItemMods.enrichment(stack) != null && ItemMods.enrichment(stack) != target && ItemMods.enrichable(stack, def)) {
                    ItemStack copy = stack.copy();
                    ItemMods.setEnrichment(copy, target);
                    items.set(i, copy);
                    touched = true;
                    changed++;
                }
            }
            if (touched) {
                AccessoryBag.save(player, page, new ArrayList<>(items));
            }
        }
        if (changed == 0) {
            player.sendOverlayMessage(Component.literal("No accessory in your bag has a different enrichment to swap.").withStyle(ChatFormatting.RED));
            return;
        }
        swapper.shrink(1);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f);
        player.sendSystemMessage(Component.literal("Swapped " + changed + " accessor" + (changed == 1 ? "y" : "ies") + " to the "
                + target.name() + " Enrichment.").withStyle(ChatFormatting.GREEN));
    }
}
