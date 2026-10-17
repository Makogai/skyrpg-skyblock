"""The page model shared by the Markdown parser, the reference pages and both outputs (see docs/design/wiki.md)."""
import re

CATEGORIES = [
    ("getting-started", "Getting Started", "minecraft:compass"),
    ("skyblock", "SkyBlock", "minecraft:player_head"),
    ("skyhaven", "Skyhaven", "minecraft:bell"),
    ("economy", "Economy", "minecraft:gold_ingot"),
    ("skills", "Skills", "minecraft:iron_pickaxe"),
    ("gear", "Gear", "minecraft:iron_chestplate"),
    ("combat", "Combat", "minecraft:iron_sword"),
    ("world", "Extras", "minecraft:chest"),
    ("companions", "Pets", "minecraft:bone"),
    ("interface", "Interface", "minecraft:writable_book"),
    ("reference", "Reference", "minecraft:book"),
]
CATEGORY_IDS = [c[0] for c in CATEGORIES]

PAGE_LIST = {
    "getting-started": ["welcome", "skyrpg-mods", "first-launch", "first-steps", "controls-and-keys", "mods-and-shaders", "progression-guide"],
    "skyblock": ["skyblock-menu", "skyblock-items", "profiles-and-islands", "the-hub", "the-islands", "dwarven-mines", "calendar-and-events", "storage-and-wardrobe", "minions", "collections", "recipe-book", "skyblock-leveling", "npc-shops", "bazaar", "auction-house", "reforging", "gemstones", "crystal-hollows", "slayers"],
    "skyhaven": ["skyhaven", "npcs"],
    "economy": ["coins-and-bank"],
    "skills": ["skills-overview", "mining", "heart-of-the-mountain", "farming", "foraging", "fishing", "enchanting", "crafting"],
    "gear": ["weapons", "armour-sets", "tools", "accessories", "backpacks"],
    "combat": ["stats", "combat-basics"],
    "world": ["builder-station", "brainrots", "crates"],
    "companions": ["pets"],
    "interface": ["k-menu", "chat", "hud-and-minimap", "waypoints", "party", "skyhelper", "settings"],
    "reference": ["items-weapons", "items-armour", "items-tools", "items-accessories", "items-materials", "items-consumables", "items-other", "enchant-list", "boss-list", "collection-list", "sell-prices"],
}
LISTED_IDS = {pid: cat for cat, ids in PAGE_LIST.items() for pid in ids}
GENERATED_IDS = set(PAGE_LIST["reference"])

PAGE_ID_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
ITEM_ID_RE = re.compile(r"^[a-z0-9_.\-]+:[a-z0-9_.\-/]+$")


def slug(text):
    """Heading anchor: lowercase, runs of non-alphanumerics become one '-', no leading or trailing '-'."""
    s = re.sub(r"[^a-z0-9]+", "-", text.lower())
    return s.strip("-") or "section"


def item_anchor(item_id):
    return "item-" + item_id.replace(":", "-").replace("/", "-")


def span(text, **kw):
    d = {"x": text}
    for k, v in kw.items():
        if v:
            d[k] = v
    return d


def plain(spans):
    return "".join(s.get("x", "") for s in spans)


def merge_spans(spans):
    out = []
    for s in spans:
        if not s.get("x") and "item" not in s:
            continue
        if out and "item" not in s and "item" not in out[-1] and \
                {k: v for k, v in s.items() if k != "x"} == {k: v for k, v in out[-1].items() if k != "x"}:
            out[-1] = dict(out[-1], x=out[-1]["x"] + s["x"])
        else:
            out.append(dict(s))
    return out


def blocks_text(blocks):
    """All plain text of a block list (for the search words)."""
    parts = []
    for b in blocks:
        t = b.get("t")
        if t in ("h2", "h3"):
            parts.append(b["text"])
        elif t == "p":
            parts.append(plain(b["s"]))
        elif t in ("ul", "ol"):
            for it in b["items"]:
                parts.append(plain(it["s"]))
                for sub in it.get("sub", []):
                    parts.append(plain(sub["s"]))
        elif t == "table":
            for cell in b["head"]:
                parts.append(plain(cell))
            for row in b["rows"]:
                for cell in row:
                    parts.append(plain(cell))
        elif t == "callout":
            parts.append(blocks_text(b["b"]))
        elif t == "item":
            parts.append(b["name"])
            for line in b["lines"]:
                parts.append(plain(line))
        elif t == "hero":
            parts.append(b["name"])
            parts.append(b.get("kind", ""))
            parts.append(b.get("rarity", ""))
        elif t == "img":
            parts.append(b.get("caption", ""))
    return "\n".join(parts)


STOP = set("the and for you your with that this are from can has have not but all any its into out one will "
           "when what which their them then than also each more most only over some such they was were while".split())


def search_words(title, blocks):
    text = title + "\n" + blocks_text(blocks)
    words = set()
    for w in re.findall(r"[a-z0-9][a-z0-9']*", text.lower()):
        w = w.strip("'")
        if len(w) >= 3 and w not in STOP:
            words.add(w)
    return sorted(words)
