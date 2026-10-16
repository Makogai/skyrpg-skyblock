package com.skyrpg.skyblock.storage;

import com.mojang.serialization.Codec;
import com.skyrpg.skyblock.SkyRpgSkyblock;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Hypixel's Storage: Ender Chest pages of 45 slots each, up to {@link #MAX_PAGES}. Every player starts with
 * {@link #STARTING_PAGES}; more unlock with SkyBlock Leveling (until that exists, admins use
 * {@code /storage unlock}). Pages are part of the profile's player data. A vanilla ender chest's contents move into
 * the first page the first time Storage is used.
 */
public final class Storage {
    public static final int MAX_PAGES = 9;
    public static final int STARTING_PAGES = 2;
    public static final int PAGE_SIZE = 45;

    static final AttachmentType<List<ItemContainerContents>> PAGES = AttachmentRegistry.create(SkyRpgSkyblock.id("ender_pages"),
            builder -> builder.initializer(List::of).persistent(ItemContainerContents.CODEC.listOf()).copyOnDeath());
    static final AttachmentType<Integer> UNLOCKED = AttachmentRegistry.create(SkyRpgSkyblock.id("ender_pages_unlocked"),
            builder -> builder.initializer(() -> STARTING_PAGES).persistent(Codec.INT).copyOnDeath());
    static final AttachmentType<Boolean> MIGRATED = AttachmentRegistry.create(SkyRpgSkyblock.id("ender_chest_migrated"),
            builder -> builder.initializer(() -> false).persistent(Codec.BOOL).copyOnDeath());

    private Storage() {
    }

    static void init() {
        // Touch the attachment types so they register at start-up.
        PAGES.identifier();
        UNLOCKED.identifier();
        MIGRATED.identifier();
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

    /** Opens Ender Chest page {@code page} (0-based), if it's unlocked. */
    public static void open(ServerPlayer player, int page) {
        if (page < 0 || page >= MAX_PAGES) {
            return;
        }
        if (page >= unlocked(player)) {
            player.sendSystemMessage(Component.literal("Ender Chest page " + (page + 1) + " is locked. More pages unlock with SkyBlock Leveling.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new ChestMenu(MenuType.GENERIC_9x5, id, inventory, new Page(player, page), 5),
                Component.literal("Ender Chest (" + (page + 1) + "/" + unlocked(player) + ")")));
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

    /** One page, written back to the player's data after every change. */
    private static final class Page extends SimpleContainer {
        private final ServerPlayer owner;
        private final int page;
        private boolean loading;

        Page(ServerPlayer owner, int page) {
            super(PAGE_SIZE);
            this.owner = owner;
            this.page = page;
            loading = true;
            NonNullList<ItemStack> items = Storage.page(owner, page);
            for (int i = 0; i < PAGE_SIZE; i++) {
                setItem(i, items.get(i));
            }
            loading = false;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (!loading) {
                save(owner, page, getItems());
            }
        }

        @Override
        public void stopOpen(net.minecraft.world.entity.ContainerUser user) {
            super.stopOpen(user);
            StoragePayloads.sendSummary(owner);
        }
    }
}
