package com.skyrpg.minions;

import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.island.IslandRules;
import com.skyrpg.skyblock.island.Islands;
import com.skyrpg.skyblock.profile.Profile;
import com.skyrpg.skyblock.profile.Profiles;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SkyRPG Minions: Hypixel's minions. Right-click a minion item on the top of a block on your island to place it; it
 * works the 5 by 5 around it, even while you're offline. A profile can place 5 to start with, more as it crafts unique
 * minions ({@link MinionSlots}).
 */
public final class SkyRpgMinions implements ModInitializer {
    public static final String MOD_ID = "skyrpg_minions";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final EntityType<MinionEntity> MINION = registerEntity();
    public static final MenuType<MinionMenu> MENU = Registry.register(BuiltInRegistries.MENU, id("minion"),
            new ExtendedMenuType<>(MinionMenu::new, MinionMenu.View.CODEC));

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(MINION, ArmorStand.createAttributes());
        JsonDataLoader.register(id("minions"), Minions.DIRECTORY, Minions::load);
        JsonDataLoader.register(id("minion_fuels"), MinionFuels.DIRECTORY, MinionFuels::load);
        MinionSlots.init();
        // The Enchanted Clock and the Enchanted Time Clock: every minion on your island works an hour ahead.
        for (String type : new String[] {"time_skip", "time_warp"}) {
            com.skyrpg.items.ability.Abilities.register(type, SkyRpgMinions::skipHour);
        }
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack held = player.getItemInHand(hand);
            Minions.Placed placed = Minions.fromItem(SkyItems.skyblockId(held));
            if (placed == null) {
                return InteractionResult.PASS;
            }
            if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
                place(serverPlayer, serverLevel, hit.getBlockPos(), hit.getDirection(), held, placed);
            }
            return InteractionResult.SUCCESS;
        });
    }

    private static boolean skipHour(ServerPlayer player, com.skyrpg.items.ItemDefinition.Ability ability) {
        if (!Islands.isIslands(player.level()) || !IslandRules.isMember(player, player.blockPosition())) {
            player.sendOverlayMessage(Component.literal("You can only skip time on your own island.").withStyle(ChatFormatting.RED));
            return false;
        }
        Profile profile = Profiles.current(player);
        ServerLevel level = (ServerLevel) player.level();
        int skipped = 0;
        for (MinionEntity minion : level.getEntities(MINION, new AABB(Islands.center(profile.island())).inflate(Islands.BUILD_RADIUS + 8, 400, Islands.BUILD_RADIUS + 8),
                minion -> true)) {
            minion.skipTime(3_600_000L);
            skipped++;
        }
        player.sendOverlayMessage(Component.literal(skipped == 0 ? "You have no minions here." : "Skipped an hour for " + skipped + " minion" + (skipped == 1 ? "" : "s") + ".")
                .withStyle(skipped == 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.5f);
        return skipped > 0;
    }

    private static void place(ServerPlayer player, ServerLevel level, BlockPos against, Direction face, ItemStack held, Minions.Placed placed) {
        if (!Islands.isIslands(level) || !IslandRules.isMember(player, against)) {
            player.sendOverlayMessage(Component.literal("You can only place minions on your own island.").withStyle(ChatFormatting.RED));
            return;
        }
        if (face != Direction.UP || !level.getBlockState(against.above()).isAir()) {
            player.sendOverlayMessage(Component.literal("Place a minion on the top of a block.").withStyle(ChatFormatting.RED));
            return;
        }
        Profile profile = Profiles.current(player);
        int placedHere = level.getEntities(MINION, new AABB(Islands.center(profile.island())).inflate(Islands.BUILD_RADIUS + 8, 400, Islands.BUILD_RADIUS + 8),
                minion -> true).size();
        int slots = MinionSlots.slots(player);
        if (placedHere >= slots) {
            int toNext = MinionSlots.toNext(player);
            player.sendOverlayMessage(Component.literal("You can place " + slots + " minions"
                    + (toNext > 0 ? ": craft " + toNext + " more unique minions for another" : "") + ".").withStyle(ChatFormatting.RED));
            return;
        }
        MinionEntity minion = MINION.create(level, net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE);
        if (minion == null) {
            return;
        }
        BlockPos at = against.above();
        minion.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Math.round(player.getYRot() / 90f) * 90f + 180f, 0);
        minion.init(placed.type(), placed.tier(), profile.id());
        level.addFreshEntity(minion);
        if (!player.isCreative()) {
            held.shrink(1);
        }
        level.playSound(null, at, SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 1f, 1.2f);
        player.sendOverlayMessage(Component.literal("You placed a " + placed.type().title(placed.tier()) + "! (" + (placedHere + 1) + "/" + slots + ")")
                .withStyle(ChatFormatting.GREEN));
    }

    private static EntityType<MinionEntity> registerEntity() {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id("minion"));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<MinionEntity>of(MinionEntity::new, MobCategory.MISC)
                // An armour stand's own size: the small flag halves it (and its armour), as for vanilla's small stands.
                .sized(0.5f, 1.975f).clientTrackingRange(10).build(key));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
