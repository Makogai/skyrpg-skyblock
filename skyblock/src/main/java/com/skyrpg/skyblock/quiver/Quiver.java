package com.skyrpg.skyblock.quiver;

import com.skyrpg.core.stats.SkyArrows;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemMods;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.bow.SkyBowItem;
import com.skyrpg.items.enchant.Enchants;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Hypixel's Quiver: your arrows live in it, not in your inventory (arrows you pick up or buy go in by themselves).
 * {@code /quiver} shows it. A bow shoots the selected arrow type (the Arrow Swapper picks it; else the first in the
 * quiver), one a shot (Infinite Quiver saves 3% a level). A SkyBlock bow's arrow hits for 5 + the bow's Damage + the
 * arrow's Damage, by how far it was drawn, then the shooter's Strength and crits. Icy Arrows slow, Glue Arrows root.
 * Per profile; the arrow that will be shot is synced to the client, so it lets you draw the bow.
 */
public final class Quiver {
    public static final int SIZE = 45;
    public static final String ICY = "ICY_ARROW";
    public static final String GLUE = "GLUE_ARROW";
    private static final String ARROW_TAG = "skyrpg_arrow.";

    static final AttachmentType<ItemContainerContents> CONTENTS = AttachmentRegistry.create(SkyRpgSkyblock.id("quiver"),
            builder -> builder.persistent(ItemContainerContents.CODEC).copyOnDeath());
    static final AttachmentType<String> SELECTED = AttachmentRegistry.create(SkyRpgSkyblock.id("quiver_selected"),
            builder -> builder.persistent(com.mojang.serialization.Codec.STRING).copyOnDeath());
    /** The arrows the next shot comes from (a stack of the selected type), on both sides. */
    public static final AttachmentType<ItemStack> AMMO = AttachmentRegistry.create(SkyRpgSkyblock.id("quiver_ammo"),
            builder -> builder.syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    private Quiver() {
    }

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("quiver")
                .executes(ctx -> {
                    QuiverMenu.open(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                server.getPlayerList().getPlayers().forEach(Quiver::stash);
            }
        });
        // A SkyBlock bow's arrows: their damage and their kind (for the Icy and Glue Arrows' effects).
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof ServerPlayer shooter && SkyBlockWorlds.isSkyBlock(level)) {
                aim(arrow, shooter);
            }
        });
        com.skyrpg.core.stats.IncomingDamage.registerHit((target, source) -> {
            if (source.getDirectEntity() instanceof AbstractArrow arrow) {
                hit(target, arrow);
            }
            return 1;
        });
        Identifier early = SkyRpgSkyblock.id("arrow_swapper_early");
        UseItemCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(early, (player, level, hand) -> swapper(player, hand));
        UseBlockCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseBlockCallback.EVENT.register(early, (player, level, hand, hit) -> swapper(player, hand));
    }

    public static NonNullList<ItemStack> contents(ServerPlayer player) {
        NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        player.getAttachedOrElse(CONTENTS, ItemContainerContents.EMPTY).copyInto(items);
        return items;
    }

    static void save(ServerPlayer player, List<ItemStack> items) {
        player.setAttached(CONTENTS, ItemContainerContents.fromItems(items));
        refreshAmmo(player);
    }

    /** Whether this is one of Hypixel's arrows. */
    static boolean isArrow(ItemStack stack) {
        ItemDefinition def = SkyItems.definition(stack);
        return def != null && def.skyblock() && stack.getItem() instanceof ArrowItem;
    }

    /** Arrows in the inventory go into the quiver (what doesn't fit stays). */
    private static void stash(ServerPlayer player) {
        if (!SkyBlockWorlds.isSkyBlock(player.level())) {
            return;
        }
        NonNullList<ItemStack> quiver = null;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!isArrow(stack)) {
                continue;
            }
            if (quiver == null) {
                quiver = contents(player);
            }
            insert(quiver, stack);
        }
        if (quiver != null) {
            save(player, quiver);
        } else if (player.getAttached(AMMO) == null && player.hasAttached(CONTENTS)) {
            refreshAmmo(player);
        }
    }

    /** Merges as much of {@code stack} into the quiver's slots as fits (the stack shrinks by that much). */
    static void insert(List<ItemStack> quiver, ItemStack stack) {
        for (int i = 0; i < quiver.size() && !stack.isEmpty(); i++) {
            ItemStack slot = quiver.get(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, stack)) {
                int moved = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(moved);
                stack.shrink(moved);
            }
        }
        for (int i = 0; i < quiver.size() && !stack.isEmpty(); i++) {
            if (quiver.get(i).isEmpty()) {
                quiver.set(i, stack.copy());
                stack.setCount(0);
            }
        }
    }

    /** The arrow type shots use: the selected one if the quiver has it, else the first. */
    private static String current(ServerPlayer player, List<ItemStack> quiver) {
        String selected = player.getAttachedOrElse(SELECTED, "");
        String first = "";
        for (ItemStack stack : quiver) {
            String id = SkyItems.skyblockId(stack);
            if (id == null) {
                continue;
            }
            if (id.equals(selected)) {
                return id;
            }
            if (first.isEmpty()) {
                first = id;
            }
        }
        return first;
    }

    static void refreshAmmo(ServerPlayer player) {
        NonNullList<ItemStack> quiver = contents(player);
        String id = current(player, quiver);
        ItemStack ammo = ItemStack.EMPTY;
        int total = 0;
        for (ItemStack stack : quiver) {
            if (id.equals(SkyItems.skyblockId(stack))) {
                if (ammo.isEmpty()) {
                    ammo = stack.copy();
                }
                total += stack.getCount();
            }
        }
        if (!ammo.isEmpty()) {
            ammo.setCount(Math.min(ammo.getMaxStackSize(), total));
        }
        ItemStack old = player.getAttached(AMMO);
        if (old == null || !ItemStack.matches(old, ammo)) {
            player.setAttached(AMMO, ammo);
        }
    }

    /** The arrows a bow may shoot from the quiver (empty if none), on either side. */
    public static ItemStack ammo(Player player) {
        return player.getAttachedOrElse(AMMO, ItemStack.EMPTY);
    }

    /** A shot from the quiver: one arrow of its type used up (Infinite Quiver may save it). */
    public static void used(ServerPlayer player, ItemStack weapon, ItemStack projectile) {
        if (hasInventoryArrows(player)) {
            return; // it came from the inventory (vanilla handles that)
        }
        int infinite = ItemMods.enchantment(weapon, "infinite_quiver");
        Enchants.Enchant enchant = Enchants.get("infinite_quiver");
        if (infinite > 0 && enchant != null && player.getRandom().nextDouble() * 100 < enchant.value(infinite)) {
            return;
        }
        String id = SkyItems.skyblockId(projectile);
        NonNullList<ItemStack> quiver = contents(player);
        for (ItemStack stack : quiver) {
            if (id != null && id.equals(SkyItems.skyblockId(stack))) {
                stack.shrink(1);
                save(player, quiver);
                return;
            }
        }
    }

    private static boolean hasInventoryArrows(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof ArrowItem) {
                return true;
            }
        }
        return false;
    }

    /** A new arrow from a SkyBlock bow: its damage (5 + bow + arrow, by the draw) and its kind. */
    private static void aim(AbstractArrow arrow, ServerPlayer shooter) {
        ItemStack bow = arrow.getWeaponItem();
        ItemDefinition bowDef = bow == null ? null : SkyItems.definition(bow);
        if (bowDef == null || bow.getItem() instanceof SkyBowItem || SkyArrows.of(arrow) != null) {
            return;
        }
        ItemStack ammo = arrow.getPickupItemStackOrigin();
        ItemDefinition ammoDef = SkyItems.definition(ammo);
        double arrowDamage = ammoDef == null ? 0 : ItemMods.stats(ammo, ammoDef).getOrDefault(StatTypes.DAMAGE, 0.0);
        double bowDamage = ItemMods.stats(bow, bowDef).getOrDefault(StatTypes.DAMAGE, 0.0);
        double power = Math.min(1, arrow.getDeltaMovement().length() / 3.0);
        SkyArrows.tag(arrow, (5 + bowDamage + arrowDamage) * Math.max(0.1, power));
        if (ammoDef != null) {
            arrow.addTag(ARROW_TAG + ammoDef.skyblockId());
        }
    }

    private static void hit(LivingEntity target, AbstractArrow arrow) {
        if (arrow.entityTags().contains(ARROW_TAG + ICY)) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 0));
        }
        if (arrow.entityTags().contains(ARROW_TAG + GLUE)) {
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 9));
        }
    }

    /** The Arrow Swapper: right-click for the next arrow type in the quiver, sneak and right-click for the one before. */
    private static InteractionResult swapper(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!"ARROW_SWAPPER".equals(SkyItems.skyblockId(held))) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            List<String> types = new ArrayList<>();
            for (ItemStack stack : contents(server)) {
                String id = SkyItems.skyblockId(stack);
                if (id != null && !types.contains(id)) {
                    types.add(id);
                }
            }
            if (types.isEmpty()) {
                server.sendOverlayMessage(Component.literal("Your quiver is empty!").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            int at = types.indexOf(current(server, contents(server)));
            int next = Math.floorMod(at + (server.isShiftKeyDown() ? -1 : 1), types.size());
            select(server, types.get(next));
        }
        return InteractionResult.SUCCESS;
    }

    static void select(ServerPlayer player, String id) {
        player.setAttached(SELECTED, id);
        refreshAmmo(player);
        ItemStack ammo = ammo(player);
        player.sendOverlayMessage(Component.literal("Selected: ").withStyle(ChatFormatting.GRAY)
                .append(ammo.isEmpty() ? Component.literal(id) : ammo.getHoverName()));
        com.skyrpg.core.text.Msg.playSound(player, SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.6f);
    }

}
