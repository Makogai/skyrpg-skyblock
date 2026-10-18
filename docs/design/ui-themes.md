# Themed screens

The user's direction (2026-10-18): every screen should be rich (item icons, cards, price chips, real tooltips, progress
bars; never plain text rows) and themed per area so each feels unique: its own frame, background and header divider.

## The pieces

- `tools/ui/themes.py` draws each theme's pixel art with PIL and writes GUI sprites with their `.mcmeta` scaling under
  `skyblock/src/main/resources/assets/skyrpg_skyblock/textures/gui/sprites/theme/<name>/`: `frame` (nine-slice),
  `background` (tile), `divider` + `divider_left`/`divider_right`, `card`, `card_hover`, `card_locked`, `button`,
  `button_hover`, `button_disabled`, `tab`, `tab_active`. Also the currency icons under `currency/` (coins, copper,
  pests, tickets, medal_gold/silver/bronze). `--preview` writes a 4x preview of each theme to `$TEMP/theme-<name>.png`.
- `ScreenTheme` (client): the theme's sprites and colours (accent, text, muted, dim, good, bar track, content inset).
  `DEFAULT` is the plain dark window, drawn without sprites, so every screen can move over one at a time.
- `SkyScreens`: `window(theme, ...)`, `divider`, `card`, `button`, `tab`, `bar`, `chip`/`chips`, `npcPortrait` (an
  NPC's live model without its name tag or role line) and `currencyName`.
- `ui/Chip` (common): a price or balance (currency sprite or item icon, the amount, whether you have enough), sent to
  the client in menu views.

## Themes

| Theme | Look | Used by |
| --- | --- | --- |
| FARMING | oak planks and iron corner plates, dark soil, a straw-rope divider with wheat sprigs, grass-block buttons | the Garden; shops on the farming islands |
| SKYBLOCK | dark slate with gold trim and ruby corner ornaments, a gold divider | Sacks, Pets, Wardrobe, Quiver, Accessory Bag, Craft, Ender Chest, Museum, Bestiary, Kat; Hub shops |
| MINING | stone bricks with ore flecks, gem corners, a minecart-rail divider with pickaxes, stone buttons | HotM, the Forge, Commissions, the Gemstone Grinder, the Fossil Excavator; mining shops |
| CRIMSON | nether bricks with lava seams, gilded blackstone corners, a blaze-rod divider with flames, magma buttons | Faction leaders, Kuudra, the Emissaries, the Dojo, Vulcan; Crimson Isle shops |
| DUNGEON | mossy stone bricks, skull corners, a chain divider with torches, stone-brick buttons | Mort's Catacombs screen, the reward chests, the Party Finder, Malik; Dungeon Hub shops |
| MAGIC | indigo with gold filigree, a rune divider with crystals, lapis buttons | Enchanting, Experiments, Brewing, Reforge/Anvil, Maxwell |
| FISHING | driftwood and rope, a wave divider with fish, prismarine buttons | Odger; fishing shops |

The SkyBlock menu, Storage and the Artisan Bench are core screens with their own modern look (not on these themes).

## Card menus for any NPC (`ui/CardMenu`, `CardView`, client `CardScreen`)

`CardMenu.open(player, viewBuilder, clicker)`: the server builds a `CardView` (theme name, title, subtitle, the NPC's
entity id, your balances, cards, footer, columns, card height); clicking card n calls the clicker ("" ok, a message in
red, or null to close) and the view is rebuilt. `CardScreen` draws it like the Garden's NPC views: the NPC's live model
and wallet on the left, item cards with real tooltips and price chips on the right. New NPC menus use this instead of a
screen of their own.

## Verifying

`/devtools action <player> <type> [json]` runs any NPC action (opens its screen), for screenshots without walking to
the NPC (the NPC portrait is empty then).

## The Garden screens (the first on it)

`GardenScreen` lays its rows out as cards: an item icon or currency sprite, a title (the item's own name and rarity
colour on shop cards), a note, a progress bar, a detail line, chips, and a button (or the whole card is the button).
NPC views put the NPC's live model and your wallet on the left. Shop cards show the item's real tooltip with its
price; what you can't afford has its icon dimmed and red chips.
