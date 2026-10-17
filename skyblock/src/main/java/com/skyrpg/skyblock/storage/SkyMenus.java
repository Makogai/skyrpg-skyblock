package com.skyrpg.skyblock.storage;

import com.skyrpg.skyblock.SkyRpgSkyblock;
import com.skyrpg.skyblock.recipe.CraftMenu;
import com.skyrpg.skyblock.reforge.ItemWork;
import com.skyrpg.skyblock.reforge.ItemWorkMenu;
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
    public static final MenuType<com.skyrpg.skyblock.alchemy.BrewMenu> BREW = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("brew"), new MenuType<>(com.skyrpg.skyblock.alchemy.BrewMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<com.skyrpg.skyblock.quiver.QuiverMenu> QUIVER = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("quiver"), new MenuType<>(com.skyrpg.skyblock.quiver.QuiverMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<EnderPageMenu> ENDER_PAGE = Registry.register(BuiltInRegistries.MENU, SkyRpgSkyblock.id("ender_page"),
            new MenuType<>(EnderPageMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<ShopMenu> SHOP = Registry.register(BuiltInRegistries.MENU, SkyRpgSkyblock.id("shop"),
            new ExtendedMenuType<>(ShopMenu::new, ShopMenu.View.CODEC));
    public static final MenuType<CraftMenu> CRAFT = Registry.register(BuiltInRegistries.MENU, SkyRpgSkyblock.id("craft"),
            new MenuType<>(CraftMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<ItemWorkMenu> REFORGE = Registry.register(BuiltInRegistries.MENU, SkyRpgSkyblock.id("reforge"),
            new MenuType<>((id, inventory) -> new ItemWorkMenu(SkyMenus.REFORGE, id, inventory, ItemWork.Mode.REFORGE), FeatureFlags.VANILLA_SET));
    public static final MenuType<com.skyrpg.skyblock.enchant.EnchantMenu> ENCHANT = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("enchant"), new MenuType<>(com.skyrpg.skyblock.enchant.EnchantMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<com.skyrpg.skyblock.accessory.AccessoryBagMenu> ACCESSORY_BAG = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("accessory_bag"), new MenuType<>(com.skyrpg.skyblock.accessory.AccessoryBagMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<com.skyrpg.skyblock.accessory.MaxwellMenu> MAXWELL = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("maxwell"), new ExtendedMenuType<>(com.skyrpg.skyblock.accessory.MaxwellMenu::new,
                    com.skyrpg.skyblock.accessory.MaxwellMenu.View.CODEC));
    public static final MenuType<com.skyrpg.skyblock.mining.HotmMenu> HOTM = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("hotm"), new ExtendedMenuType<>(com.skyrpg.skyblock.mining.HotmMenu::new, com.skyrpg.skyblock.mining.HotmMenu.View.CODEC));
    public static final MenuType<com.skyrpg.skyblock.mining.ForgeMenu> FORGE = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("forge"), new ExtendedMenuType<>(com.skyrpg.skyblock.mining.ForgeMenu::new, com.skyrpg.skyblock.mining.ForgeMenu.View.CODEC));
    public static final MenuType<com.skyrpg.skyblock.mining.CommissionsMenu> COMMISSIONS = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("commissions"), new ExtendedMenuType<>(com.skyrpg.skyblock.mining.CommissionsMenu::new,
                    com.skyrpg.skyblock.mining.CommissionsMenu.View.CODEC));
    public static final MenuType<com.skyrpg.skyblock.sack.SacksMenu> SACKS = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("sacks"), new ExtendedMenuType<>(com.skyrpg.skyblock.sack.SacksMenu::new, com.skyrpg.skyblock.sack.SacksMenu.View.CODEC));
    public static final MenuType<com.skyrpg.skyblock.gem.GemGrinderMenu> GEM_GRINDER = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("gem_grinder"), new MenuType<>(com.skyrpg.skyblock.gem.GemGrinderMenu::new, FeatureFlags.VANILLA_SET));
    public static final MenuType<com.skyrpg.skyblock.bestiary.BestiaryMenu> BESTIARY = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("bestiary"), new ExtendedMenuType<>(com.skyrpg.skyblock.bestiary.BestiaryMenu::new,
                    com.skyrpg.skyblock.bestiary.BestiaryMenu.View.CODEC));
    public static final MenuType<com.skyrpg.skyblock.museum.MuseumMenu> MUSEUM = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("museum"), new ExtendedMenuType<>(com.skyrpg.skyblock.museum.MuseumMenu::new,
                    com.skyrpg.skyblock.museum.MuseumMenu.View.CODEC));
    public static final MenuType<com.skyrpg.skyblock.pet.KatMenu> KAT = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("kat"), new ExtendedMenuType<>(com.skyrpg.skyblock.pet.KatMenu::new, com.skyrpg.skyblock.pet.KatMenu.View.CODEC));
    public static final MenuType<com.skyrpg.skyblock.experiment.ExperimentMenu> EXPERIMENT = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("experiment"), new ExtendedMenuType<>(com.skyrpg.skyblock.experiment.ExperimentMenu::new,
                    com.skyrpg.skyblock.experiment.ExperimentMenu.View.CODEC));
    public static final MenuType<com.skyrpg.skyblock.pet.PetsMenu> PETS = Registry.register(BuiltInRegistries.MENU,
            SkyRpgSkyblock.id("pets"), new ExtendedMenuType<>(com.skyrpg.skyblock.pet.PetsMenu::new, com.skyrpg.skyblock.pet.PetsMenu.View.CODEC));
    public static final MenuType<ItemWorkMenu> ANVIL = Registry.register(BuiltInRegistries.MENU, SkyRpgSkyblock.id("anvil"),
            new MenuType<>((id, inventory) -> new ItemWorkMenu(SkyMenus.ANVIL, id, inventory, ItemWork.Mode.ANVIL), FeatureFlags.VANILLA_SET));

    private SkyMenus() {
    }

    static void init() {
        // Loading the class registers the menu types.
    }
}
