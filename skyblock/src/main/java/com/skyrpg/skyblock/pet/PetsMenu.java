package com.skyrpg.skyblock.pet;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyrpg.core.action.Actions;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.pets.Pets;
import com.skyrpg.skyblock.storage.SkyMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The Pets menu: your summoned pet and the rest. Button {@code i} summons stored pet {@code i} (the one out takes its
 * place), {@link #DESPAWN} puts the summoned one away, {@code TAKE + i} takes stored pet {@code i} out as an item.
 * {@code /pets} opens it; {@code /sbpet give} for admins.
 */
public final class PetsMenu extends AbstractContainerMenu {
    public static final int DESPAWN = 500;
    public static final int TAKE = 1000;

    public record View(ItemStack active, List<ItemStack> stored) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, View::active,
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, View::stored,
                View::new);
    }

    private final View view;
    private final ServerPlayer owner;

    public PetsMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private PetsMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.PETS, id);
        this.view = view;
        this.owner = owner;
    }

    public View view() {
        return view;
    }

    static void init() {
        Actions.register("skyblock_pets", (player, params) -> open(player));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal("pets").executes(ctx -> {
                open(ctx.getSource().getPlayerOrException());
                return 1;
            }));
            dispatcher.register(Commands.literal("sbpet").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player())
                            .then(Commands.argument("type", StringArgumentType.word())
                                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SkyBlockPets.types(), builder))
                                    .then(Commands.argument("tier", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of(SkyBlockPets.TIERS), builder))
                                            .executes(ctx -> give(ctx.getSource().getServer(), EntityArgument.getPlayer(ctx, "player"),
                                                    StringArgumentType.getString(ctx, "type"), StringArgumentType.getString(ctx, "tier"), 0))
                                            .then(Commands.argument("exp", IntegerArgumentType.integer(0))
                                                    .executes(ctx -> give(ctx.getSource().getServer(), EntityArgument.getPlayer(ctx, "player"),
                                                            StringArgumentType.getString(ctx, "type"), StringArgumentType.getString(ctx, "tier"),
                                                            IntegerArgumentType.getInteger(ctx, "exp")))))))));
        });
    }

    private static int give(net.minecraft.server.MinecraftServer server, ServerPlayer player, String type, String tier, int exp) {
        ItemStack pet = SkyBlockPets.create(type.toUpperCase(Locale.ROOT), tier.toUpperCase(Locale.ROOT), exp);
        if (pet.isEmpty()) {
            return 0;
        }
        player.getInventory().add(pet);
        return 1;
    }

    public static void open(ServerPlayer player) {
        ItemStack active = Pets.active(player);
        if (SkyBlockPets.isPet(active)) {
            SkyBlockPets.refreshLook(active);
        } else {
            active = ItemStack.EMPTY;
        }
        List<ItemStack> stored = new ArrayList<>();
        for (ItemStack pet : SkyBlockPets.stored(player)) {
            SkyBlockPets.refreshLook(pet);
            stored.add(pet);
        }
        View view = new View(active, stored);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Pets");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new PetsMenu(id, view, player);
            }
        });
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner) {
            return false;
        }
        List<ItemStack> stored = SkyBlockPets.stored(owner);
        ItemStack active = Pets.active(owner);
        boolean hasActive = SkyBlockPets.isPet(active);
        if (id == DESPAWN) {
            if (!hasActive) {
                return false;
            }
            stored.add(0, active);
            owner.removeAttached(Pets.ACTIVE);
            message("You despawned your ", active, ".");
        } else if (id >= TAKE) {
            int index = id - TAKE;
            if (index < 0 || index >= stored.size()) {
                return false;
            }
            if (owner.getInventory().getFreeSlot() < 0) {
                owner.sendOverlayMessage(Component.literal("Your inventory is full.").withStyle(ChatFormatting.RED));
                return true;
            }
            ItemStack pet = stored.remove(index);
            SkyBlockPets.refreshLook(pet);
            owner.getInventory().add(pet);
            message("You took ", pet, " out of your pet menu.");
        } else {
            if (id < 0 || id >= stored.size()) {
                return false;
            }
            ItemStack pet = stored.remove(id);
            if (hasActive) {
                stored.add(id, active);
            }
            owner.setAttached(Pets.ACTIVE, pet);
            message("You summoned your ", pet, "!");
        }
        SkyBlockPets.setStored(owner, stored);
        Stats.refresh(owner);
        owner.level().playSound(null, owner.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 0.7f, 1.3f);
        owner.level().getServer().execute(() -> open(owner));
        return true;
    }

    private void message(String before, ItemStack pet, String after) {
        owner.sendSystemMessage(Component.literal(before).withStyle(ChatFormatting.GRAY).append(SkyBlockPets.name(pet))
                .append(Component.literal(after).withStyle(ChatFormatting.GRAY)));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == null || player == owner && owner.isAlive();
    }
}
