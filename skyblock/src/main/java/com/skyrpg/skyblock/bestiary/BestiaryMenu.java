package com.skyrpg.skyblock.bestiary;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.skyrpg.core.action.Actions;
import com.skyrpg.skyblock.storage.SkyMenus;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;

/** The Bestiary: the islands down the side, the chosen island's families with their kills and tiers. Button {@code i} picks island {@code i}. */
public final class BestiaryMenu extends AbstractContainerMenu {
    public record IslandView(String name, ItemStack icon, int found, int total) {
        static final StreamCodec<RegistryFriendlyByteBuf, IslandView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, IslandView::name,
                ItemStack.OPTIONAL_STREAM_CODEC, IslandView::icon,
                ByteBufCodecs.VAR_INT, IslandView::found,
                ByteBufCodecs.VAR_INT, IslandView::total,
                IslandView::new);
    }

    /** A family: name, head, kills, tier and top tier, and the kills the next tier needs (0 when maxed). */
    public record FamilyView(String name, ItemStack head, long kills, int tier, int max, long next) {
        static final StreamCodec<RegistryFriendlyByteBuf, FamilyView> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, FamilyView::name,
                ItemStack.OPTIONAL_STREAM_CODEC, FamilyView::head,
                ByteBufCodecs.VAR_LONG, FamilyView::kills,
                ByteBufCodecs.VAR_INT, FamilyView::tier,
                ByteBufCodecs.VAR_INT, FamilyView::max,
                ByteBufCodecs.VAR_LONG, FamilyView::next,
                FamilyView::new);
    }

    public record View(int selected, int totalTiers, int milestone, List<IslandView> islands, List<FamilyView> families) {
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, View::selected,
                ByteBufCodecs.VAR_INT, View::totalTiers,
                ByteBufCodecs.VAR_INT, View::milestone,
                IslandView.CODEC.apply(ByteBufCodecs.list()), View::islands,
                FamilyView.CODEC.apply(ByteBufCodecs.list()), View::families,
                View::new);
    }

    private final View view;
    private final ServerPlayer owner;

    public BestiaryMenu(int id, Inventory inventory, View view) {
        this(id, view, null);
    }

    private BestiaryMenu(int id, View view, ServerPlayer owner) {
        super(SkyMenus.BESTIARY, id);
        this.view = view;
        this.owner = owner;
    }

    public View view() {
        return view;
    }

    static void init() {
        Actions.register("bestiary", (player, params) -> open(player, 0));
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> {
            dispatcher.register(Commands.literal("bestiary").executes(ctx -> {
                open(ctx.getSource().getPlayerOrException(), 0);
                return 1;
            }));
            dispatcher.register(Commands.literal("be").executes(ctx -> {
                open(ctx.getSource().getPlayerOrException(), 0);
                return 1;
            }));
        });
    }

    public static void open(ServerPlayer player, int selected) {
        List<Bestiary.Island> islands = Bestiary.islands();
        int pick = Math.max(0, Math.min(selected, islands.size() - 1));
        List<IslandView> islandViews = new ArrayList<>();
        for (Bestiary.Island island : islands) {
            int found = (int) island.families().stream().filter(f -> Bestiary.kills(player, f) > 0).count();
            islandViews.add(new IslandView(island.name(), head(island.icon(), "island:" + island.id()), found, island.families().size()));
        }
        List<FamilyView> families = new ArrayList<>();
        if (!islands.isEmpty()) {
            for (Bestiary.Family family : islands.get(pick).families()) {
                long kills = Bestiary.kills(player, family);
                int tier = family.tier(kills);
                long next = tier >= family.maxTier() ? 0 : family.thresholds()[tier];
                families.add(new FamilyView(family.name(), head(family.texture(), family.id()), kills, tier, family.maxTier(), next));
            }
        }
        View view = new View(pick, Bestiary.totalTiers(player), Bestiary.milestone(player), islandViews, families);
        player.openMenu(new ExtendedMenuProvider<View>() {
            @Override
            public View getScreenOpeningData(ServerPlayer p) {
                return view;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Bestiary");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
                return new BestiaryMenu(id, view, player);
            }
        });
    }

    /** A player head with this texture. */
    private static ItemStack head(String texture, String key) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        if (texture != null && !texture.isEmpty()) {
            UUID id = UUID.nameUUIDFromBytes(("skyrpg_bestiary:" + key).getBytes(StandardCharsets.UTF_8));
            head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(id, "",
                    new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", texture))))));
        }
        return head;
    }

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (owner == null || clicker != owner || id < 0 || id >= view.islands().size()) {
            return false;
        }
        owner.level().getServer().execute(() -> open(owner, id));
        return true;
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
