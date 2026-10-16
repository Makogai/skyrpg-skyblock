package com.skyrpg.skyblock.storage;

import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.recipe.CraftMenu;
import com.skyrpg.skyblock.shop.ShopMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

/** SkyBlock's item screens: our own menus, drawn by our own screens (never a chest GUI). */
public final class SkyMenus {
    public static final MenuType<WardrobeMenu> WARDROBE = Registry.register(BuiltInRegistries.MENU, SkyRpgSkyblock.id("wardrobe"),
            new MenuType<>(WardrobeMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<EnderPageMenu> ENDER_PAGE = Registry.register(BuiltInRegistries.MENU, SkyRpgSkyblock.id("ender_page"),
            new MenuType<>(EnderPageMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<ShopMenu> SHOP = Registry.register(BuiltInRegistries.MENU, SkyRpgSkyblock.id("shop"),
            new ExtendedMenuType<>(ShopMenu::new, ShopMenu.View.CODEC));
    public static final MenuType<CraftMenu> CRAFT = Registry.register(BuiltInRegistries.MENU, SkyRpgSkyblock.id("craft"),
            new MenuType<>(CraftMenu::new, FeatureFlags.VANILLA_SET));

    private SkyMenus() {
    }

    static void init() {
        // Loading the class registers the menu types.
    }
}
