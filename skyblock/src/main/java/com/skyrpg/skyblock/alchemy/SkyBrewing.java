package com.skyrpg.skyblock.alchemy;

import com.google.gson.JsonObject;
import com.skyrpg.core.world.SkyBlockWorlds;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.SkyItems;
import com.skyrpg.skyblock.mixin.BrewingStandAccess;
import java.util.Locale;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hypixel's brewing, at every brewing stand in a SkyBlock world (they open {@link BrewMenu}):
 * <ul>
 *   <li>Every brew takes 20 seconds and needs no fuel.</li>
 *   <li>Nether Wart turns Water Bottles into Awkward Potions; a Brewing Ingredient turns those into its potion at level
 *       I, III or V (Sugar is Speed I, Enchanted Sugar Speed III...).</li>
 *   <li>Then one level modifier (Glowstone +1, Enchanted Glowstone Dust +2, Enchanted Glowstone +3, up to the potion's
 *       top level), one duration modifier (Redstone +8 minutes, Enchanted Redstone +16, Enchanted Redstone Block +40),
 *       an Enchanted Redstone Lamp for both (+3 levels and +16 minutes), and Gunpowder for a splash potion (half its
 *       time; Enchanted Gunpowder keeps it).</li>
 *   <li>Each brew adds the ingredient's Alchemy XP (Hypixel's per-ingredient numbers) to the potion, paid out when it's
 *       taken from the stand.</li>
 * </ul>
 */
public final class SkyBrewing {
    public static final int BREW_TICKS = 400;
    public static final int INGREDIENT = 3;

    private SkyBrewing() {
    }

    public static void init() {
        Potions.init();
        PotionEffects.init();
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() || !SkyBlockWorlds.isSkyBlock(level) || player.isSpectator()
                    || !(level.getBlockEntity(hit.getBlockPos()) instanceof BrewingStandBlockEntity stand)) {
                return InteractionResult.PASS;
            }
            if (player.isSecondaryUseActive() && !player.getItemInHand(hand).isEmpty()) {
                return InteractionResult.PASS;
            }
            BrewMenu.open((ServerPlayer) player, stand);
            return InteractionResult.SUCCESS;
        });
    }

    /** One tick of a stand in a SkyBlock world (instead of vanilla's). */
    public static void tick(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand) {
        BrewingStandAccess access = (BrewingStandAccess) stand;
        access.skyrpg$setFuel(20);
        ItemStack ingredient = stand.getItem(INGREDIENT);
        boolean brewable = brewable(stand, ingredient);
        int time = access.skyrpg$brewTime();
        if (time > 0) {
            if (!brewable) {
                access.skyrpg$setBrewTime(0);
            } else if (--time == 0) {
                brew(level, pos, stand, ingredient);
                access.skyrpg$setBrewTime(0);
            } else {
                access.skyrpg$setBrewTime(time);
            }
            stand.setChanged();
        } else if (brewable) {
            access.skyrpg$setBrewTime(BREW_TICKS);
            access.skyrpg$setTotalBrewTime(BREW_TICKS);
            stand.setChanged();
        }
        bottles(level, pos, stand);
    }

    private static boolean brewable(BrewingStandBlockEntity stand, ItemStack ingredient) {
        if (ingredient.isEmpty()) {
            return false;
        }
        for (int i = 0; i < INGREDIENT; i++) {
            if (result(stand.getItem(i), ingredient) != null) {
                return true;
            }
        }
        return false;
    }

    private static void brew(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand, ItemStack ingredient) {
        for (int i = 0; i < INGREDIENT; i++) {
            ItemStack out = result(stand.getItem(i), ingredient);
            if (out != null) {
                stand.setItem(i, out);
            }
        }
        ingredient.shrink(1);
        level.levelEvent(1035, pos, 0);
    }

    /** The stand shows a bottle in each slot that holds one. */
    private static void bottles(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BrewingStandBlock)) {
            return;
        }
        BlockState shown = state;
        for (int i = 0; i < INGREDIENT; i++) {
            shown = shown.setValue(BrewingStandBlock.HAS_BOTTLE[i], !stand.getItem(i).isEmpty());
        }
        if (shown != state) {
            level.setBlock(pos, shown, 2);
        }
    }

    /** What a bottle becomes with this ingredient, or null when it doesn't brew. */
    static ItemStack result(ItemStack bottle, ItemStack ingredientStack) {
        Potions.Brew brew = Potions.read(bottle);
        String id = ingredientId(ingredientStack);
        if (brew == null || id == null) {
            return null;
        }
        double xp = brew.alchemyXp() + alchemyXp(id);
        if (brew.water()) {
            return Potions.awkwardIngredient(id) ? Potions.create(new Potions.Brew(Potions.AWKWARD, 0, false, 1, 0, false, false, xp)) : null;
        }
        if (brew.awkward()) {
            Potions.Start start = Potions.start(id);
            return start == null ? null : Potions.create(new Potions.Brew(start.potion(), start.level(), false, 1, 0, false, false, xp));
        }
        Potions.Modifier modifier = Potions.modifier(id);
        Potions.Kind kind = Potions.kind(brew.potion());
        if (modifier == null || kind == null) {
            return null;
        }
        int level = brew.level();
        int extra = brew.extraTicks();
        boolean levelMod = brew.levelMod();
        boolean durationMod = brew.durationMod();
        boolean splash = brew.splash();
        double factor = brew.splashFactor();
        if (modifier.splash() > 0) {
            if (splash) {
                return null;
            }
            splash = true;
            factor = modifier.splash();
        }
        if (modifier.levels() > 0) {
            if (levelMod || level >= kind.max()) {
                return null;
            }
            level = Math.min(kind.max(), level + modifier.levels());
            levelMod = true;
        }
        if (modifier.minutes() > 0) {
            // Instant and few-second potions (Healing, Damage, Stamina, Blindness) don't take Redstone.
            if (durationMod || kind.level(level).ticks() <= 200) {
                return null;
            }
            extra += modifier.minutes() * 1200;
            durationMod = true;
        }
        return Potions.create(new Potions.Brew(brew.potion(), level, splash, factor, extra, levelMod, durationMod, xp));
    }

    /** Hypixel's id for an ingredient (a vanilla item by its own name, Nether Wart as NETHER_STALK). */
    static String ingredientId(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String id = SkyItems.skyblockId(stack);
        if (id != null) {
            return id;
        }
        if (stack.is(Items.NETHER_WART)) {
            return "NETHER_STALK";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toUpperCase(Locale.ROOT);
    }

    /** The Alchemy XP Hypixel gives for brewing with this ({@code experience.alchemy.INGREDIENT_BREW}). */
    static double alchemyXp(String id) {
        ItemDefinition def = ItemRegistry.skyblock(id);
        if (def == null || !def.json().has("hypixel")) {
            return 0;
        }
        JsonObject hypixel = def.json().getAsJsonObject("hypixel");
        if (!hypixel.has("experience") || !hypixel.getAsJsonObject("experience").has("alchemy")) {
            return 0;
        }
        JsonObject alchemy = hypixel.getAsJsonObject("experience").getAsJsonObject("alchemy");
        return alchemy.has("INGREDIENT_BREW") ? alchemy.get("INGREDIENT_BREW").getAsDouble() : 0;
    }
}
