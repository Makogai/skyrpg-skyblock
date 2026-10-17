#!/usr/bin/env python3
"""Hypixel's Heart of the Mountain tree, from the NotEnoughUpdates repo (MIT, tools/skyblock/cache/neu-repo.zip):

    python tools/skyblock/import_hotm.py

Writes skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/hotm/tree.json (generated: overwritten each run):

    {"levels": [0, 3000, ...],                         XP for each HotM level (to reach level 2, 3...)
     "perks": [{"id": "mining_speed", "name": "Mining Speed", "x": 3, "y": 9, "max": 50,
                "powder": ["MITHRIL", ...],            the powder each upgrade costs (index 0: level 1 -> 2)
                "cost": [27, 64, ...],                 the powder for each upgrade (index 0: level 1 -> 2)
                "values": {"stat": [20, 40, ...]},     each {name} in the lore at levels 1..max
                "values_potm": {...},                  the same once Core of the Mountain is level 2+ (only when they differ)
                "lore": ["§7Grants §a+§a{stat} ...", {"text": "...", "from": 2}]}]}  (a line shown from level 2 on)

NEU's constants/hotmlayout.json writes costs and numbers as small Lisp expressions over level (the perk's level),
maxLevel, potm (Core of the Mountain's level) and hotm (the HotM level); they're evaluated here, once per level.
Row y = 9 is HotM tier 1, y = 0 tier 10.
"""
import json
import math
import re
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / "cache"
OUT = ROOT / "skyblock/src/main/resources/data/skyrpg_skyblock/skyrpg/hotm"
NEU = "NotEnoughUpdates-REPO-master/"
# Hypixel's own font icons (private-use characters) draw as boxes here: dropped.
ICONS = re.compile("[-] ?")


# ---------------------------------------------------------------- a tiny Lisp
def tokenize(text):
    return re.findall(r'\(|\)|"[^"]*"|[^\s()]+', text)


def parse(tokens):
    token = tokens.pop(0)
    if token == "(":
        out = []
        while tokens[0] != ")":
            out.append(parse(tokens))
        tokens.pop(0)
        return out
    if token.startswith('"'):
        return ("str", token[1:-1])
    try:
        return float(token)
    except ValueError:
        return token


class Lisp:
    def __init__(self, prelude):
        self.functions = {}
        for line in prelude:
            self.eval(parse(tokenize(line)), {})

    def eval(self, node, env):
        if isinstance(node, float):
            return node
        if isinstance(node, tuple):
            return node[1]
        if isinstance(node, str):
            if node.startswith(":"):
                return node[1:]
            if node == "true":
                return True
            if node in env:
                return env[node]
            if node.isupper():
                return node
            raise KeyError(node)
        head, *args = node
        if head == "defun":
            name, params, body = args
            self.functions[name] = (params, body)
            return None
        if head == "if":
            return self.eval(args[1] if self.eval(args[0], env) else args[2], env)
        values = [self.eval(a, env) for a in args]
        builtin = {
            "+": lambda *v: sum(v),
            "-": lambda a, b=None: -a if b is None else a - b,
            "*": lambda *v: math.prod(v),
            "/": lambda a, b: a / b,
            "pow": lambda a, b: a ** b,
            "lt": lambda a, b: a < b,
            "gt": lambda a, b: a > b,
            "=": lambda a, b: a == b,
            "round": lambda a: math.floor(a + 0.5),
            "floor": lambda a: math.floor(a),
            "list.new": lambda *v: list(v),
            "list.at": lambda lst, i: lst[int(i)],
        }
        if head in builtin:
            return builtin[head](*values)
        if head in self.functions:
            params, body = self.functions[head]
            return self.eval(body, dict(env, **dict(zip(params, values))))
        raise KeyError(head)

    def run(self, text, env):
        if text is None or text == "":
            return None
        if not text.startswith("("):
            if text.isupper():
                return text
            return float(text)
        return self.eval(parse(tokenize(text)), env)


def number(value):
    """Whole numbers as ints, others to 2 places (as Hypixel shows them)."""
    if isinstance(value, str):
        try:
            value = float(value)
        except ValueError:
            return value
    if isinstance(value, bool):
        return value
    if abs(value - round(value)) < 1e-9:
        return int(round(value))
    return round(value, 2)


def main():
    neu = zipfile.ZipFile(CACHE / "neu-repo.zip")
    layout = json.loads(neu.read(NEU + "constants/hotmlayout.json"))
    leveling = json.loads(neu.read(NEU + "constants/leveling.json"))
    lisp = Lisp(layout["prelude"])
    perks = []
    for pid, perk in layout["hotm"]["perks"].items():
        top = perk["maxLevel"]
        keys = [k for k in perk if k == "stat" or (k.startswith("stat") and k[4:5].isupper())]
        out = {"id": pid, "name": perk["name"], "x": perk["x"], "y": perk["y"], "max": top}
        powders, costs = [], []
        for level in range(1, top):
            env = {"level": level, "level0": level, "maxLevel": top, "potm": 0, "hotm": 10}
            powder = lisp.run(perk["powder"], env) if perk["powder"].startswith("(") else perk["powder"]
            powders.append(powder)
            cost = lisp.run(perk.get("cost"), env)
            costs.append(int(math.floor(cost)) if cost is not None else 0)
        if top > 1:
            out["powder"] = powders if len(set(powders)) > 1 else powders[:1]
            out["cost"] = costs
        else:
            out["powder"] = [perk["powder"] if not perk["powder"].startswith("(") else "MITHRIL"]
            out["cost"] = []
        values, potm = {}, {}
        for key in keys:
            name = "stat" if key == "stat" else key[4].lower() + key[5:]
            plain = [number(lisp.run(perk[key], {"level": lv, "level0": lv, "maxLevel": top, "potm": 0, "hotm": 1}))
                     for lv in range(1, top + 1)]
            boosted = [number(lisp.run(perk[key], {"level": lv, "level0": lv, "maxLevel": top, "potm": 2, "hotm": 1}))
                       for lv in range(1, top + 1)]
            values[name] = plain
            if boosted != plain:
                potm[name] = boosted
        out["values"] = values
        if potm:
            out["values_potm"] = potm
        lore = []
        for entry in perk.get("lore", []):
            # A line only shown from some level on (Core of the Mountain's rewards): {"text": ..., "from": level}.
            text = entry["text"] if isinstance(entry, dict) else entry
            line = ICONS.sub("", text)
            # NEU names the placeholders after the expression keys: {stat}, {statBoost} -> {boost}...
            line = re.sub(r"\{stat([A-Z]\w*)\}", lambda m: "{" + m.group(1)[0].lower() + m.group(1)[1:] + "}", line)
            if isinstance(entry, dict) and "onlyIf" in entry:
                first = next((lv for lv in range(0, top + 1) if lisp.run(entry["onlyIf"], {"level": lv, "level0": lv, "maxLevel": top,
                                                                                         "potm": lv, "hotm": 10})), top)
                lore.append({"text": line, "from": first})
            else:
                lore.append(line)
        out["lore"] = lore
        perks.append(out)
    perks.sort(key=lambda p: (-p["y"], p["x"]))
    OUT.mkdir(parents=True, exist_ok=True)
    data = {"levels": leveling["HOTM"], "perks": perks}
    (OUT / "tree.json").write_text(json.dumps(data, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(perks)} perks -> {OUT / 'tree.json'}")


if __name__ == "__main__":
    main()
