"""Builds Hypixel SkyBlock's slayers: Revenant Horror, Tarantula Broodfather, Sven Packmaster, Voidgloom Seraph and
Inferno Demonlord.

    python tools/skyblock/import_slayers.py

Sources: the fandom wiki (cached in tools/skyblock/cache/wiki/): each boss page's infobox (health, damage per second,
level, Combat XP), its spawning table (cost, Combat XP the quest needs, Combat level needed) and its drops table
(amount and chance per tier, the slayer level each drop needs); each slayer page's rewards table (stats and SkyBlock XP
per slayer level). NEU's leveling constants give the slayer XP per level and per boss. Writes
combat/src/main/resources/data/skyrpg_combat/skyrpg/slayers/<line>.json (the survival lines are replaced).
"""
import json
import re
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / "tools/skyblock/cache"
ITEMS = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/items"
OUT = ROOT / "combat/src/main/resources/data/skyrpg_combat/skyrpg/slayers"

# line id, boss page, slayer page, NEU key, boss entity, look, colour, icon, the mobs that count (and their Combat XP).
LINES = [
    ("revenant_horror", "Revenant_Horror", "Zombie_Slayer", "zombie", "skyrpg_combat:grave_tyrant", "revenant", "#55FF55",
     "minecraft:rotten_flesh", {"minecraft:zombie": 6, "minecraft:husk": 6, "minecraft:drowned": 6, "minecraft:zombie_villager": 6,
                               "minecraft:zombified_piglin": 8}, "zombies"),
    ("tarantula_broodfather", "Tarantula_Broodfather", "Spider_Slayer", "spider", "skyrpg_combat:broodmother", "tarantula", "#AA0000",
     "minecraft:string", {"minecraft:spider": 5, "minecraft:cave_spider": 5}, "spiders"),
    ("sven_packmaster", "Sven_Packmaster", "Wolf_Slayer", "wolf", "skyrpg_combat:sven_packmaster", "sven", "#FFFFFF",
     "minecraft:mutton", {"minecraft:wolf": 10}, "wolves"),
    ("voidgloom_seraph", "Voidgloom_Seraph", "Enderman_Slayer", "enderman", "skyrpg_combat:void_stalker", "voidgloom", "#AA00AA",
     "minecraft:ender_pearl", {"minecraft:enderman": 12, "minecraft:endermite": 4}, "endermen"),
    ("inferno_demonlord", "Inferno_Demonlord", "Blaze_Slayer", "blaze", "minecraft:blaze", "demonlord", "#FFAA00",
     "minecraft:blaze_powder", {"minecraft:blaze": 60, "minecraft:magma_cube": 50}, "blazes and magma cubes"),
]
STATS = {"hp": "health", "health": "health", "cd": "crit_damage", "cc": "crit_chance", "spd": "speed", "speed": "speed", "str": "strength",
         "strength": "strength", "int": "intelligence", "def": "defense", "defense": "defense", "td": "true_defense", "ferocity": "ferocity",
         "fer": "ferocity", "mf": "magic_find", "as": "attack_speed", "cw": "combat_wisdom", "true def": "true_defense",
         "true defense": "true_defense"}
ODDS = {"guaranteed": "Guaranteed", "occasional": "Occasional", "rare": "Rare", "extraordinary": "Extraordinary",
        "rngesus": "Pray RNGesus", "rng": "Pray RNGesus", "rng i": "RNGesus Incarnate", "rngesus incarnate": "RNGesus Incarnate"}
COMBAT_XP = [50, 100, 200, 500, 2000]
# What a boss page's infobox leaves out: the Inferno Demonlord's levels (Hypixel's wiki, Blaze Slayer research) and its
# own hits per second (decided: its Immolate and its demons do the real damage).
OVERRIDES = {"inferno_demonlord": {"level": [436, 551, 695, 825], "dps": [1200, 4000, 7500, 15000]}}
ROMAN = {"I": 1, "II": 2, "III": 3, "IV": 4, "V": 5, "VI": 6, "VII": 7, "VIII": 8, "IX": 9}


def wikitext(page):
    path = CACHE / "wiki" / f"slayer_{page}.json"
    if not path.exists():
        url = f"https://hypixel-skyblock.fandom.com/api.php?action=parse&page={page}&prop=wikitext&format=json"
        request = urllib.request.Request(url, headers={"User-Agent": "skyrpg-skyblock-importer"})
        path.write_bytes(urllib.request.urlopen(request, timeout=60).read())
    return json.loads(path.read_text(encoding="utf-8"))["parse"]["wikitext"]["*"]


def number(text):
    """"20,000", "400k", "1.5M" -> a number."""
    m = re.search(r"([\d.,]+)\s*([kKmMbB]?)", text)
    if not m:
        return 0
    value = float(m.group(1).replace(",", ""))
    return value * {"": 1, "k": 1e3, "m": 1e6, "b": 1e9}[m.group(2).lower()]


def infobox(text, key, tiers):
    out = []
    for tier in range(1, tiers + 1):
        m = re.search(r"\|\s*" + key + (str(tier) if tier > 1 else "") + r"\s*=\s*([^\n|]*)", text)
        out.append(m.group(1).strip() if m else "")
    return out


def cells(row):
    """A wikitable row's cells, colspan expanded: ["a", "b", "b"] for "|a\n| colspan="2" |b"."""
    out = []
    for raw in re.split(r"\n\|(?!-)", "\n" + row)[1:]:
        raw = raw.strip()
        span = 1
        m = re.match(r'(?:class="[^"]*"\s*)?(?:style="[^"]*"\s*)?colspan="(\d+)"\s*\|(.*)', raw, re.S)
        if m:
            span, raw = int(m.group(1)), m.group(2)
        else:
            m = re.match(r'(?:class|style|rowspan)="[^"]*"\s*\|(.*)', raw, re.S)
            if m:
                raw = m.group(1)
        out.extend([raw.strip()] * span)
    return out


def tables(text, header):
    start = text.find(header)
    if start < 0:
        return ""
    end = text.find("|}", start)
    return text[start:end]


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    leveling = json.loads(neu.read("NotEnoughUpdates-REPO-master/constants/leveling.json"))
    boss_xp = leveling["slayer_boss_xp"]
    names = {}
    for f in ITEMS.glob("*.json"):
        d = json.loads(f.read_text(encoding="utf-8"))
        names.setdefault(re.sub(r"[^a-z0-9 ]", "", d["name"].lower()).strip(), d["skyblock_id"])
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob("*.json"):
        old.unlink()
    for line_id, boss_page, slayer_page, key, boss, kind, color, icon, counts, counts_text in LINES:
        boss_text = wikitext(boss_page)
        healths = [h for h in infobox(boss_text, "health", 5) if h]
        tiers = len(healths)
        levels = infobox(boss_text, "mob_level", tiers)
        damage = infobox(boss_text, "damage", tiers)
        combat_xp = [number(x) for x in infobox(boss_text, "xp", tiers)]

        # Spawning: cost, Combat XP the quest needs, Combat level needed.
        spawn = tables(boss_text, "== Spawning")
        rows = [r for r in spawn.split("|-")[1:] if "Stier" in r]
        tier_rows = []
        for r in rows[:tiers]:
            cost = re.search(r"\{\{C\|([^}]+)\}\}", r)
            xp = re.search(r"Skill XP\|\+?([\d,]+)", r)
            level = re.search(r"\{\{skill\|combat\|[IVXL]+ \((\d+)\)\}\}", r, re.I)
            # "{{MobSprite|Revenant Horror}} {{Stier|2}}": that slayer's tier must be beaten first.
            boss_req = re.search(r"MobSprite\|([^}]+)\}\}\s*\{\{Stier\|(\d)", r)
            req = None
            if boss_req:
                req_line = next((l[0] for l in LINES if l[1].replace("_", " ") == boss_req.group(1).strip()), None)
                if req_line:
                    req = {"line": req_line, "tier": int(boss_req.group(2))}
            tier_rows.append((number(cost.group(1)) if cost else 0, int(xp.group(1).replace(",", "")) if xp else 100,
                              int(level.group(1)) if level else 0, req))

        # Drops: amounts and chances per tier, and the slayer level each needs.
        drops = []
        table = tables(boss_text, "==Drops==") or tables(boss_text, "== Drops ==")
        for row in table.split("|-")[2:]:
            c = cells(row.strip())
            if len(c) < 3 + tiers + 1 + tiers or "Slot|" not in c[0]:
                continue
            raw = re.search(r"Slot\|([^}|;,]+)", c[0]).group(1)
            name = re.sub(r"[^a-z0-9 ]", "", raw.split("!")[0].lower()).strip()
            item = names.get(name)
            if not item:
                print(f"  {line_id}: no item for {raw}")
                continue
            req = int(number(c[2])) if re.search(r"\d", c[2]) else 0
            per_tier = {}
            for t in range(tiers):
                amount = c[3 + t]
                chance = c[3 + tiers + 1 + t]
                if "bc" in amount or "bc" in chance:
                    continue
                lo_hi = re.findall(r"\d+", amount)
                lo, hi = (int(lo_hi[0]), int(lo_hi[-1])) if lo_hi else (1, 1)
                m = re.search(r"([\d.]+)\s*%", chance)
                p = float(m.group(1)) / 100 if m else 0
                if p > 0:
                    per_tier[str(t + 1)] = [lo, hi, round(p, 6)]
            odds = re.search(r"\{\{[Oo]dds\|([^|}]+)", c[3 + tiers])
            odds = ODDS.get(odds.group(1).strip().lower(), "") if odds else ""
            if per_tier:
                drops.append({"item": item, "level": req, "odds": odds, "tiers": per_tier})

        # Slayer levels: stats and SkyBlock XP for each.
        slayer_text = wikitext(slayer_page)
        rewards = []
        table = tables(slayer_text, "Rewards ==") or tables(slayer_text, "Rewards==")
        for row in table.split("{{Text anchor|")[1:]:
            stats = {}
            body = row.split("|-")[0] if "|-" in row else row
            for abbr, value in re.findall(r"\{\{stat\|([\w ]+)\|\+?([\d.]+)%?\}\}", body, re.I):
                stat = STATS.get(abbr.lower())
                if stat:
                    stats[stat] = stats.get(stat, 0) + float(value)
            sbxp = re.search(r"\{\{SBXP\|(\d+)\}\}", body)
            unlocks = [s.strip() for s in re.findall(r"\{\{[Ss]lot\|([^}|;,]+)\}\}", body.split("Boss Drops")[0])][:6]
            rewards.append({"stats": stats, "sbxp": int(sbxp.group(1)) if sbxp else 0, "unlocks": unlocks})
            if len(rewards) >= 9:
                break

        tier_json = []
        for t in range(tiers):
            fee, quest_xp, combat_level, beaten = tier_rows[t] if t < len(tier_rows) else (0, 100, 0, None)
            dps = number(damage[t]) if t < len(damage) else 0
            fix = OVERRIDES.get(line_id, {})
            if not dps and "dps" in fix:
                dps = fix["dps"][t]
            tier_json.append({
                "fee": int(fee), "xp": quest_xp, "health": number(healths[t]), "damage": round(max(1.0, dps / 15), 2),
                "dps": dps, "level": int(number(levels[t])) if t < len(levels) and number(levels[t]) else fix.get("level", [0] * 5)[t], "minutes": 3 if t < 2 else 4,
                "slayer_xp": boss_xp[t] if t < len(boss_xp) else boss_xp[-1], "requires": 0, "combat_level": combat_level,
                # Every boss gives 50 / 100 / 200 / 500 / 2,000 Combat XP; a tier the infobox leaves out gets that.
                "combat_xp": combat_xp[t] if t < len(combat_xp) and combat_xp[t] else COMBAT_XP[t],
            })
            if beaten:
                tier_json[-1]["requires_boss"] = beaten
        out = {"order": LINES.index(next(l for l in LINES if l[0] == line_id)) + 1, "name": boss_page.replace("_", " "), "key": key, "color": color,
               "icon": icon, "boss": boss, "kind": kind, "counts": counts, "counts_text": counts_text,
               "levels": leveling["slayer_xp"][key][:9], "level_rewards": rewards, "drops": drops, "tiers": tier_json}
        (OUT / (line_id + ".json")).write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
        print(f"{line_id}: {tiers} tiers, {len(drops)} drops, {len(rewards)} levels")


if __name__ == "__main__":
    main()
