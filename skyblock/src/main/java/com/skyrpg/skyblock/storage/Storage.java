package com.skyrpg.skyblock.storage;

import com.mojang.serialization.Codec;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Hypixel's Storage: Ender Chest pages of 45 slots each, up to {@link #MAX_PAGES}. Every player starts with
 * {@link #STARTING_PAGES}; more unlock with SkyBlock Leveling (until that exists, admins use
 * {@code /storage unlock}). Pages are part of the profile's player data. A vanilla ender chest's contents move into
 * the first page the first time Storage is used. And Hypixel's {@link #BACKPACK_SLOTS} backpack slots: right-click
 * a backpack (Small 9 slots to Jumbo 45) to put it in the first free one; it opens from Storage like a page (page
 * {@link #BACKPACK_PAGE} + slot), and comes out again once it's empty.
 */
public final class Storage {
    public static final int MAX_PAGES = 9;
    public static final int STARTING_PAGES = 2;
    public static final int PAGE_SIZE = 45;
    public static final int BACKPACK_SLOTS = 18;
    /** Page numbers from here on are backpack slots. */
    public static final int BACKPACK_PAGE = 100;

    static final AttachmentType<List<ItemContainerContents>> PAGES = AttachmentRegistry.create(SkyRpgSkyblock.id("ender_pages"),
            builder -> builder.initializer(List::of).persistent(ItemContainerContents.CODEC.listOf()).copyOnDeath());
    static final AttachmentType<Integer> UNLOCKED = AttachmentRegistry.create(SkyRpgSkyblock.id("ender_pages_unlocked"),
            builder -> builder.initializer(() -> STARTING_PAGES).persistent(Codec.INT).copyOnDeath());
    static final AttachmentType<Boolean> MIGRATED = AttachmentRegistry.create(SkyRpgSkyblock.id("ender_chest_migrated"),
            builder -> builder.initializer(() -> false).persistent(Codec.BOOL).copyOnDeath());
    /** The backpack in each slot (EMPTY where there's none), and what's inside each. */
    static final AttachmentType<List<ItemStack>> BACKPACKS = AttachmentRegistry.create(SkyRpgSkyblock.id("storage_backpacks"),
            builder -> builder.initializer(List::of).persistent(ItemStack.OPTIONAL_CODEC.listOf()).copyOnDeath());
    static final AttachmentType<List<ItemContainerContents>> BACKPACK_CONTENTS = AttachmentRegistry.create(SkyRpgSkyblock.id("storage_backpack_contents"),
            builder -> builder.initializer(List::of).persistent(ItemContainerContents.CODEC.listOf()).copyOnDeath());

    private Storage() {
    }

    static void init() {
        SkyMenus.init();
        // Touch the attachment types so they register at start-up.
        PAGES.identifier();
        UNLOCKED.identifier();
        MIGRATED.identifier();
        BACKPACKS.identifier();
        BACKPACK_CONTENTS.identifier();
        // Right-click a backpack: into the first free backpack slot (early: the island's protection refuses heads).
        Identifier early = SkyRpgSkyblock.id("storage_backpacks");
        UseItemCallback.EVENT.addPhaseOrdering(early, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(early, (player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            if (capacity(held) <= 0) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer server) {
                store(server, held);
            }
            return InteractionResult.SUCCESS;
        });
    }

    /** A SkyBlock backpack's size by its id (Small 9, Medium 18, Large 27, Greater 36, Jumbo 45; any colour), else 0. */
    public static int capacity(ItemStack stack) {
        String id = SkyItems.skyblockId(stack);
        if (id == null) {
            return 0;
        }
        return id.endsWith("JUMBO_BACKPACK") ? 45 : id.endsWith("GREATER_BACKPACK") ? 36 : id.endsWith("LARGE_BACKPACK") ? 27
                : id.endsWith("MEDIUM_BACKPACK") ? 18 : id.endsWith("SMALL_BACKPACK") ? 9 : 0;
    }

    public static ItemStack backpack(ServerPlayer player, int slot) {
        List<ItemStack> backpacks = player.getAttachedOrElse(BACKPACKS, List.of());
        return slot >= 0 && slot < backpacks.size() ? backpacks.get(slot) : ItemStack.EMPTY;
    }

    private static void setBackpack(ServerPlayer player, int slot, ItemStack stack, ItemContainerContents contents) {
        List<ItemStack> backpacks = new ArrayList<>(player.getAttachedOrElse(BACKPACKS, List.of()));
        List<ItemContainerContents> all = new ArrayList<>(player.getAttachedOrElse(BACKPACK_CONTENTS, List.of()));
        while (backpacks.size() < BACKPACK_SLOTS) {
            backpacks.add(ItemStack.EMPTY);
        }
        while (all.size() < BACKPACK_SLOTS) {
            all.add(ItemContainerContents.EMPTY);
        }
        backpacks.set(slot, stack);
        all.set(slot, contents);
        player.setAttached(BACKPACKS, List.copyOf(backpacks));
        player.setAttached(BACKPACK_CONTENTS, List.copyOf(all));
    }

    /** What's in the backpack in that slot (as many slots as it holds). */
    public static NonNullList<ItemStack> backpackItems(ServerPlayer player, int slot) {
        NonNullList<ItemStack> items = NonNullList.withSize(Math.max(1, capacity(backpack(player, slot))), ItemStack.EMPTY);
        List<ItemContainerContents> all = player.getAttachedOrElse(BACKPACK_CONTENTS, List.of());
        if (slot < all.size()) {
            all.get(slot).copyInto(items);
        }
        return items;
    }

    static void saveBackpack(ServerPlayer player, int slot, List<ItemStack> items) {
        setBackpack(player, slot, backpack(player, slot), ItemContainerContents.fromItems(items));
    }

    /** Puts the backpack in hand into the first free slot. */
    static void store(ServerPlayer player, ItemStack held) {
        for (int slot = 0; slot < BACKPACK_SLOTS; slot++) {
            if (backpack(player, slot).isEmpty()) {
                setBackpack(player, slot, held.copyWithCount(1), ItemContainerContents.EMPTY);
                Component name = held.getHoverName().copy();
                held.shrink(1);
                player.sendSystemMessage(Component.literal("Put ").withStyle(ChatFormatting.GREEN).append(name)
                        .append(Component.literal(" in Storage, backpack slot " + (slot + 1) + ".").withStyle(ChatFormatting.GREEN)));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.BUNDLE_INSERT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1f);
                StoragePayloads.sendSummary(player);
                return;
            }
        }
        player.sendOverlayMessage(Component.literal("All " + BACKPACK_SLOTS + " backpack slots are full.").withStyle(ChatFormatting.RED));
    }

    /** Takes the backpack in that slot back out, if it's empty. */
    static void takeOut(ServerPlayer player, int slot) {
        ItemStack backpack = backpack(player, slot);
        if (backpack.isEmpty()) {
            return;
        }
        if (backpackItems(player, slot).stream().anyMatch(s -> !s.isEmpty())) {
            player.sendOverlayMessage(Component.literal("Empty the backpack before you take it out.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!player.getInventory().add(backpack.copy())) {
            player.sendOverlayMessage(Component.literal("Make room in your inventory first.").withStyle(ChatFormatting.RED));
            return;
        }
        setBackpack(player, slot, ItemStack.EMPTY, ItemContainerContents.EMPTY);
        StoragePayloads.sendSummary(player);
    }

    public static int unlocked(ServerPlayer player) {
        return Math.min(MAX_PAGES, player.getAttachedOrElse(UNLOCKED, STARTING_PAGES));
    }

    public static void setUnlocked(ServerPlayer player, int pages) {
        player.setAttached(UNLOCKED, Math.max(1, Math.min(MAX_PAGES, pages)));
    }

    /** A page's items (always {@link #PAGE_SIZE} long). */
    public static NonNullList<ItemStack> page(ServerPlayer player, int page) {
        migrate(player);
        NonNullList<ItemStack> items = NonNullList.withSize(PAGE_SIZE, ItemStack.EMPTY);
        List<ItemContainerContents> pages = player.getAttachedOrElse(PAGES, List.of());
        if (page < pages.size()) {
            pages.get(page).copyInto(items);
        }
        return items;
    }

    static void save(ServerPlayer player, int page, List<ItemStack> items) {
        List<ItemContainerContents> pages = new ArrayList<>(player.getAttachedOrElse(PAGES, List.of()));
        while (pages.size() <= page) {
            pages.add(ItemContainerContents.EMPTY);
        }
        pages.set(page, ItemContainerContents.fromItems(items));
        player.setAttached(PAGES, List.copyOf(pages));
    }

    /** Opens Ender Chest page {@code page} (0-based), if it's unlocked, or backpack slot {@code page - BACKPACK_PAGE}. */
    public static void open(ServerPlayer player, int page) {
        if (page >= BACKPACK_PAGE && page < BACKPACK_PAGE + BACKPACK_SLOTS) {
            ItemStack backpack = backpack(player, page - BACKPACK_PAGE);
            if (!backpack.isEmpty()) {
                player.openMenu(new SimpleMenuProvider((id, inventory, p) -> EnderPageMenu.server(id, inventory, player, page), backpack.getHoverName()));
            }
            return;
        }
        if (page < 0 || page >= MAX_PAGES) {
            return;
        }
        if (page >= unlocked(player)) {
            player.sendSystemMessage(Component.literal("Ender Chest page " + (page + 1) + " is locked. More pages unlock with SkyBlock Leveling.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> EnderPageMenu.server(id, inventory, player, page),
                Component.literal("Ender Chest")));
    }

    /** The first time: what was in the vanilla ender chest goes into page 1 (as far as it fits; the rest stays). */
    private static void migrate(ServerPlayer player) {
        if (player.getAttachedOrElse(MIGRATED, false)) {
            return;
        }
        player.setAttached(MIGRATED, true);
        var ender = player.getEnderChestInventory();
        if (ender.isEmpty()) {
            return;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(PAGE_SIZE, ItemStack.EMPTY);
        List<ItemContainerContents> pages = player.getAttachedOrElse(PAGES, List.of());
        if (!pages.isEmpty()) {
            pages.getFirst().copyInto(items);
        }
        int next = 0;
        for (int i = 0; i < ender.getContainerSize(); i++) {
            ItemStack stack = ender.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            while (next < PAGE_SIZE && !items.get(next).isEmpty()) {
                next++;
            }
            if (next >= PAGE_SIZE) {
                break;
            }
            items.set(next, stack.copy());
            ender.setItem(i, ItemStack.EMPTY);
        }
        save(player, 0, items);
    }
}
