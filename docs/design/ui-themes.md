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
| FARMING | oak-plank frame with iron corner plates, dark soil, a straw-rope divider with wheat sprigs, grass-block buttons (cobblestone when disabled), wooden cards with a straw-gold hover edge | the Garden (Desk, SkyMart, Phillip, Jacob, Anita, visitors) |

Planned: MINING (HOTM, the Forge, commissions, the gemstone grinder), CRIMSON (factions, Kuudra, Malik, Odger, the
Dojo), DUNGEON (the Catacombs screens, Maxwell), MAGIC (enchanting, experiments, brewing), FISHING, and the rest on a
polished DEFAULT.

## The Garden screens (the first on it)

`GardenScreen` lays its rows out as cards: an item icon or currency sprite, a title (the item's own name and rarity
colour on shop cards), a note, a progress bar, a detail line, chips, and a button (or the whole card is the button).
NPC views put the NPC's live model and your wallet on the left. Shop cards show the item's real tooltip with its
price; what you can't afford has its icon dimmed and red chips.
