#!/usr/bin/env python3
"""Hypixel's Chocolate Rabbits, for the Chocolate Factory and Hoppity's Hunt:

    python tools/skyblock/import_rabbits.py

Source: the official wiki's "Chocolate Rabbits/List" (fetched once into tools/skyblock/cache/wiki/official_*.txt).
Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/chocolate/rabbits.json (generated: overwritten each run):

    {"rabbits": [{"name": "Aaron", "rarity": "COMMON", "resident": "", "source": "egg", "text": "..."}]}

"source" says how a rabbit is found: "egg" (Chocolate Eggs and strays, any island or only its "resident" one),
"milestone" (all-time chocolate), "shop" (chocolate spent in the shop), "faction" (Factory 5's factions),
"chocobit" (Factory 6) or "requirement" (something else, in "text").
"""
import json
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache" / "wiki"
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/chocolate"
API = "https://hypixelskyblock.minecraft.wiki/api.php"
RARITIES = ["COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL"]


def wikitext(page):
    f = CACHE / ("official_" + page.replace("/", "_").replace(" ", "_") + ".txt")
    if not f.exists():
        out = subprocess.run(["curl", "-s", "-G", API, "--data-urlencode", "action=query", "-d", "prop=revisions", "-d", "rvprop=content",
                              "-d", "rvslots=main", "-d", "format=json", "-d", "formatversion=2", "--data-urlencode", "titles=" + page],
                             capture_output=True, check=True).stdout
        data = json.loads(out.decode("utf-8"))["query"]["pages"][0]
        CACHE.mkdir(parents=True, exist_ok=True)
        f.write_text(data["revisions"][0]["slots"]["main"]["content"], encoding="utf-8")
    return f.read_text(encoding="utf-8")


def plain(text):
    """Wiki markup to plain text: links, templates' last argument, formatting codes."""
    text = re.sub(r"\{\{Footnote[^}]*\}\}", "", text)
    text = re.sub(r"\[\[[^]|]*\|([^]]*)\]\]", r"\1", text)
    text = re.sub(r"\[\[([^]]*)\]\]", r"\1", text)
    text = re.sub(r"\{\{(?:[^{}|]*\|)*([^{}|]*)\}\}", r"\1", text)
    text = re.sub(r"&[0-9a-fk-or]", "", text)
    return re.sub(r"\s+", " ", text).strip()


def lore(slot):
    """The rabbit's tooltip text (what it grants), from its {{Slot|...|text=...}}."""
    m = re.search(r"\|text=(.*)\}\}\s*$", slot)
    if not m:
        return ""
    lines = []
    for line in m.group(1).replace("//", "/\n/").split("/"):
        line = re.sub(r"&[0-9a-fk-or]", "", line).strip()
        if line.startswith(("You have not found", "✖", "Right click")) or line.endswith("RABBIT") or "Resident" in line:
            break
        lines.append(line)
    return re.sub(r"\s+", " ", " ".join(lines)).strip()


def main():
    text = wikitext("Chocolate Rabbits/List")
    rabbits = []
    for row in re.split(r"\n\|-[^\n]*\n", text):
        cells = [line.lstrip("|").strip() for line in row.split("\n") if line.startswith("|") and not line.startswith("|}")]
        if len(cells) < 5 or not cells[0].startswith("{{Slot"):
            continue
        slot, name, rarity, resident, requirement = cells[0], plain(cells[1]), cells[2], cells[3], cells[4]
        rarity = next((r for r in RARITIES if "{{" + r.capitalize() + "}}" in rarity or "{{" + r.lower() + "}}" in rarity.lower()), None)
        if rarity is None or not name:
            continue
        resident = "" if "{{bc}}" in resident else plain(resident)
        need = "" if "{{bc}}" in requirement else plain(requirement)
        if not need:
            source = "egg"
        elif "Faction" in need:
            source = "faction"
        elif "Factory Milestones" in need:
            source = "milestone"
        elif "Shop Milestones" in need:
            source = "shop"
        elif "Chocobit" in need or rarity == "SPECIAL":
            source = "chocobit"
        else:
            source = "requirement"
        rabbit = {"name": name, "rarity": rarity, "resident": resident, "source": source, "text": lore(slot)}
        if need and source == "requirement":
            rabbit["requirement"] = need
        rabbits.append(rabbit)
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / "rabbits.json").write_text(json.dumps({"rabbits": rabbits}, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    counts = {}
    for r in rabbits:
        counts[(r["source"], r["rarity"])] = counts.get((r["source"], r["rarity"]), 0) + 1
    print(f"{len(rabbits)} rabbits -> {OUT / 'rabbits.json'}")
    for key in sorted(counts):
        print(" ", key, counts[key])


if __name__ == "__main__":
    main()
