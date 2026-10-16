"""Builds the Bazaar's catalogue: every product, its tab and group, and Hypixel's prices at the time of the snapshot.

    python tools/skyblock/import_bazaar.py            # uses the cached snapshot
    python tools/skyblock/import_bazaar.py --fetch    # takes a new snapshot of Hypixel's Bazaar first

Sources: the Hypixel Bazaar API (public, no key; cached as tools/skyblock/cache/bazaar.json): every product and its
best sell offer (what instant buying costs) and best buy order (what instant selling pays). Tabs follow Hypixel's
(Farming, Mining, Combat, Woods & Fishes, Oddities): a product goes where the collection it comes from is, found
through the imported recipes (Enchanted Cobblestone is made of Cobblestone, so it's in Mining, in the Cobblestone
group). Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/bazaar/catalog.json:

    {"snapshot": 1792175302830,
     "categories": [{"name": "Mining", "groups": [{"name": "Cobblestone", "icon": "COBBLESTONE",
                     "products": [{"id": "ENCHANTED_COBBLESTONE", "buy": 498.8, "sell": 400.4}, ...]}]}]}
"""
import argparse
import json
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / "tools/skyblock/cache"
DATA = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg"
OUT = DATA / "bazaar/catalog.json"
TABS = {"farming": "Farming", "mining": "Mining", "combat": "Combat", "foraging": "Woods & Fishes", "fishing": "Woods & Fishes"}
ORDER = ["Farming", "Mining", "Combat", "Woods & Fishes", "Oddities"]


def snapshot(fetch):
    path = CACHE / "bazaar.json"
    if fetch or not path.exists():
        request = urllib.request.Request("https://api.hypixel.net/v2/skyblock/bazaar", headers={"User-Agent": "skyrpg-skyblock-importer"})
        path.write_bytes(urllib.request.urlopen(request, timeout=60).read())
    return json.loads(path.read_text(encoding="utf-8"))


def oddity_group(item):
    """An Oddities group: the item's kind (Reforge Stones, Bait...), or a letter range for the rest."""
    data = json.loads((DATA / "items" / (item.lower().replace(":", "-") + ".json")).read_text(encoding="utf-8"))
    kind = data.get("type", "")
    if kind:
        return kind + ("s" if not kind.endswith("s") else "")
    first = item[0]
    for letters in ("ABC", "DEFG", "HIJKL", "MNOP", "QRS", "TUVWXYZ"):
        if first in letters:
            return f"Misc {letters[0]}-{letters[-1]}"
    return "Misc"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fetch", action="store_true", help="take a new snapshot of Hypixel's Bazaar")
    args = parser.parse_args()
    data = snapshot(args.fetch)
    known = {json.loads(f.read_text(encoding="utf-8"))["skyblock_id"] for f in (DATA / "items").glob("*.json")}

    # Which collection each item is: the collection items themselves...
    collection_of = {}
    collections = {}
    for f in (DATA / "collections").glob("*.json"):
        c = json.loads(f.read_text(encoding="utf-8"))
        collections[c["name"]] = c
        for item in c["skyblock"]:
            collection_of.setdefault(item, c["name"])
    # ... and what they're made of, through the recipes (the ingredient there's most of leads).
    made_of = {}
    for f in (DATA / "skyblock_recipes").glob("*.json"):
        r = json.loads(f.read_text(encoding="utf-8"))
        counts = {}
        for cell in r["grid"]:
            if cell:
                item, _, n = cell.rpartition(":")
                counts[item] = counts.get(item, 0) + int(n)
        if counts and r["output"] not in made_of:
            made_of[r["output"]] = max(counts, key=counts.get)

    def collection(item, depth=0):
        if item in collection_of:
            return collection_of[item]
        if depth < 6 and item in made_of and made_of[item] != item:
            return collection(made_of[item], depth + 1)
        return None

    groups = {}
    for product_id, product in data["products"].items():
        item = product_id
        if item not in known:
            continue
        sells = product.get("sell_summary") or []
        buys = product.get("buy_summary") or []
        if not sells and not buys:
            continue
        # buy_summary holds sell offers (what you instant-buy from), sell_summary buy orders (what you instant-sell to).
        buy = round(buys[0]["pricePerUnit"], 1) if buys else round(product["quick_status"]["buyPrice"], 1)
        sell = round(sells[0]["pricePerUnit"], 1) if sells else round(product["quick_status"]["sellPrice"], 1)
        if buy <= 0 and sell <= 0:
            continue
        name = collection(item)
        tab = TABS.get(collections[name]["category"], "Oddities") if name else "Oddities"
        group = name or oddity_group(item)
        groups.setdefault((tab, group), []).append({"id": item, "buy": buy, "sell": sell})

    categories = []
    for tab in ORDER:
        tab_groups = []
        for (t, group), products in sorted(groups.items(), key=lambda g: g[0][1]):
            if t != tab:
                continue
            products.sort(key=lambda p: (p["buy"] or p["sell"]))
            icon = collections[group]["skyblock"][0] if group in collections else products[0]["id"]
            tab_groups.append({"name": group, "icon": icon, "products": products})
        categories.append({"name": tab, "groups": tab_groups})
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"snapshot": data.get("lastUpdated", 0), "categories": categories}, indent=1) + "\n", encoding="utf-8")
    total = sum(len(g["products"]) for c in categories for g in c["groups"])
    print(f"Wrote {total} products in {sum(len(c['groups']) for c in categories)} groups to {OUT.relative_to(ROOT)}")
    for c in categories:
        print(f"  {c['name']}: {len(c['groups'])} groups, {sum(len(g['products']) for g in c['groups'])} products")


if __name__ == "__main__":
    main()
