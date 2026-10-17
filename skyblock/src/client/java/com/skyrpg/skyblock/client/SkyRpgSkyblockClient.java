package com.skyrpg.skyblock.client;

import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.theme.ToggledPacks;
import com.skyrpg.skyblock.area.Areas;
import com.skyrpg.skyblock.auction.AuctionHouse;
import com.skyrpg.skyblock.bazaar.Bazaar;
import com.skyrpg.skyblock.bazaar.BazaarCatalog;
import com.skyrpg.skyblock.client.screen.CraftScreen;
import com.skyrpg.skyblock.client.screen.EnderPageScreen;
import com.skyrpg.skyblock.client.screen.ShopScreen;
import com.skyrpg.skyblock.client.screen.WardrobeScreen;
import com.skyrpg.skyblock.profile.ProfileListPayload;
import com.skyrpg.skyblock.recipe.RecipesPayload;
import com.skyrpg.skyblock.storage.SkyMenus;
import com.skyrpg.skyblock.storage.StoragePayloads;
import com.skyrpg.skyblock.travel.FastTravel;
import com.skyrpg.skyblock.zone.Zones;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.resources.Identifier;

/** Client side of SkyRPG SkyBlock: the SkyBlock pages of the K menu. */
public final class SkyRpgSkyblockClient implements ClientModInitializer {
    /** FurfSky Reborn as the modpack downloads it from Modrinth (see tools/pack/modrinth.json). */
    private static final String FURFSKY = "file/FurfSkyReborn.cats.zip";

    @Override
    public void onInitializeClient() {
        // FurfSky Reborn is how SkyBlock items look (read by Catharsis): always on, and only its item looks load (FurfSkyFilter).
        ToggledPacks.alwaysOn(FURFSKY);
        retireSurvival();
        MenuScreens.register(SkyMenus.WARDROBE, WardrobeScreen::new);
        MenuScreens.register(SkyMenus.ENDER_PAGE, EnderPageScreen::new);
        MenuScreens.register(SkyMenus.CRAFT, CraftScreen::new);
        MenuScreens.register(SkyMenus.REFORGE, com.skyrpg.skyblock.client.screen.ItemWorkScreen::new);
        MenuScreens.register(SkyMenus.ANVIL, com.skyrpg.skyblock.client.screen.ItemWorkScreen::new);
        MenuScreens.register(SkyMenus.ENCHANT, com.skyrpg.skyblock.client.screen.EnchantScreen::new);
        MenuScreens.register(SkyMenus.ACCESSORY_BAG, com.skyrpg.skyblock.client.screen.AccessoryBagScreen::new);
        MenuScreens.register(SkyMenus.MAXWELL, com.skyrpg.skyblock.client.screen.MaxwellScreen::new);
        MenuScreens.register(SkyMenus.HOTM, com.skyrpg.skyblock.client.screen.HotmScreen::new);
        MenuScreens.register(SkyMenus.FORGE, com.skyrpg.skyblock.client.screen.ForgeScreen::new);
        MenuScreens.register(SkyMenus.COMMISSIONS, com.skyrpg.skyblock.client.screen.CommissionsScreen::new);
        MenuScreens.register(SkyMenus.SACKS, com.skyrpg.skyblock.client.screen.SacksScreen::new);
        MenuScreens.register(SkyMenus.GEM_GRINDER, com.skyrpg.skyblock.client.screen.GemGrinderScreen::new);
        MenuScreens.register(SkyMenus.PETS, com.skyrpg.skyblock.client.screen.PetsScreen::new);
        MenuScreens.register(SkyMenus.SHOP, ShopScreen::new);
        SkyRpgMenu.registerPage(new SkyBlockMenuPage());
        SkyRpgMenu.registerPage(new ProfilesPage());
        SkyRpgMenu.registerPage(new FastTravelPage());
        ClientPlayNetworking.registerGlobalReceiver(FastTravel.WarpList.TYPE, (payload, context) -> FastTravelPage.set(payload));
        ClientPlayNetworking.registerGlobalReceiver(com.skyrpg.skyblock.pet.PetCatalog.CatalogPayload.TYPE, (payload, context) -> payload.apply());
        ClientPlayNetworking.registerGlobalReceiver(com.skyrpg.skyblock.travel.Lifts.Menu.TYPE,
                (payload, context) -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new LiftScreen(payload)));
        SkyRpgMenu.registerPage(new CalendarPage());
        SkyRpgMenu.registerPage(new ItemBrowserPage());
        SkyRpgMenu.registerPage(new RecipeBookPage());
        SkyRpgMenu.registerPage(new SkyBlockLevelingPage());
        SkyRpgMenu.registerPage(new BazaarPage());
        SkyRpgMenu.registerPage(new AuctionHousePage());
        ClientPlayNetworking.registerGlobalReceiver(AuctionHouse.Listing.TYPE, (payload, context) -> AuctionHousePage.setListing(payload));
        ClientPlayNetworking.registerGlobalReceiver(AuctionHouse.Mine.TYPE, (payload, context) -> AuctionHousePage.setMine(payload));
        ClientPlayNetworking.registerGlobalReceiver(Bazaar.Catalog.TYPE, (payload, context) -> BazaarCatalog.replace(payload.categories()));
        ClientPlayNetworking.registerGlobalReceiver(Bazaar.Book.TYPE, (payload, context) -> BazaarPage.setBook(payload));
        ClientPlayNetworking.registerGlobalReceiver(Bazaar.Orders.TYPE, (payload, context) -> BazaarPage.setOrders(payload));
        ClientPlayNetworking.registerGlobalReceiver(RecipesPayload.TYPE, (payload, context) -> payload.apply());
        StoragePage storage = new StoragePage();
        SkyRpgMenu.registerPage(storage);
        ClientPlayNetworking.registerGlobalReceiver(StoragePayloads.Summary.TYPE, (payload, context) -> StoragePage.set(payload));
        ClientPlayNetworking.registerGlobalReceiver(StoragePayloads.Show.TYPE, (payload, context) -> SkyRpgMenu.open(storage.id()));
        HudManager.register(new SidebarWidget());
        ClientPlayNetworking.registerGlobalReceiver(Zones.ZoneList.TYPE, (payload, context) -> Zones.replace(payload.zones()));
        ClientPlayNetworking.registerGlobalReceiver(Areas.AreaPayload.TYPE, (payload, context) -> SidebarWidget.setArea(payload.area()));
        ClientPlayNetworking.registerGlobalReceiver(ProfileListPayload.TYPE, (payload, context) -> ProfilesPage.set(payload));
    }

    /**
     * The survival pack's pages and HUD boxes that SkyBlock has no use for (for now): the Journal and its quest
     * tracker, Contracts, griffin mounts, the spell wheel, talents, titles, /pay and the Blood Moon. Their mods stay
     * loaded; they just don't show.
     */
    private static void retireSurvival() {
        for (String page : List.of("skyrpg_progress:journal", "skyrpg_events:contracts", "skyrpg_mounts:mounts",
                "skyrpg_skills:talents", "skyrpg_progress:titles", "skyrpg_economy:pay", "skyrpg_pets:pets")) {
            SkyRpgMenu.retirePage(Identifier.parse(page));
        }
        for (String widget : List.of("skyrpg_progress:quest_tracker", "skyrpg_events:contracts_hud", "skyrpg_mounts:stamina",
                "skyrpg_magic:spell", "skyrpg_world:blood_moon")) {
            HudManager.retire(Identifier.parse(widget));
        }
    }
}
