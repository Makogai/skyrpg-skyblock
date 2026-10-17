"""Generates the reference pages (items, enchants, slayer bosses, collections, prices)."""
import glob
import os
import re

from gamedata import fmt_num, humanize, read_text, jload
import itempages
from model import (LISTED_IDS, PAGE_LIST, item_anchor, plain, span, merge_spans)

GREY = "#AAAAAA"
GOLD = "#FFAA00"
GREEN = "#55FF55"
CYAN = "#55FFFF"
RARITY_ORDER = ["common", "uncommon", "rare", "epic", "legendary", "mythic", "special"]
ORDINAL = {c: i for i, c in enumerate(RARITY_ORDER)}


def roman(n):
    vals = [(10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]
    out = ""
    for v, s in vals:
        while n >= v:
            out += s
            n -= v
    return out


def coins(n):
    return "{:,}".format(int(n)) + " coins" if n != 1 else "1 coin"


class Ref:
    def __init__(self, game):
        self.g = game
        self.pages = {}
        self.item_page = {}   # item id -> reference page id
        self.used_items = set()
        self.boss_names = {}
        self.build_all()

    # ------------------------------------------------------------------ helpers
    def ispan(self, item_id, text=None, **extra):
        """An item span with the item's name in its rarity colour."""
        g = self.g
        it = g.items.get(item_id)
        name = text or g.name_of(item_id) or item_id
        s = {"x": name, "item": item_id, "icon": item_id}
        if it:
            s["color"] = g.rarity_color(it["rarity"])
        self.used_items.add(item_id)
        s.update(extra)
        return s

    def item_list_spans(self, entries, sep=", "):
        """entries: list of (item_id, count) -> spans like '4x Prism Fragment, Heartstone'."""
        out = []
        for i, (iid, n) in enumerate(entries):
            if i:
                out.append(span(sep))
            if n and n != 1:
                out.append(span("%sx " % fmt_num(n)))
            out.append(self.ispan(iid))
        return out

    def add_page(self, pid, title, summary, icon, blocks, order=None):
        cat = "reference"
        if order is None:
            order = PAGE_LIST["reference"].index(pid) + 1
        self.pages[pid] = {"id": pid, "title": title, "category": cat, "order": order, "icon": icon,
                           "summary": summary, "blocks": blocks, "generated": True}

    @staticmethod
    def h2(text, anchor=None):
        from model import slug
        return {"t": "h2", "text": text, "anchor": anchor or slug(text)}

    @staticmethod
    def h3(text, anchor=None):
        from model import slug
        return {"t": "h3", "text": text, "anchor": anchor or slug(text)}

    @staticmethod
    def p(*spans):
        return {"t": "p", "s": merge_spans(list(spans))}

    @staticmethod
    def ul(rows):
        return {"t": "ul", "items": [{"s": merge_spans(r)} for r in rows]}

    @staticmethod
    def table(head, rows, align=None):
        return {"t": "table", "head": [[span(h)] if isinstance(h, str) else h for h in head],
                "rows": rows, "align": align or ["l"] * len(head)}

    def uniq_anchors(self, blocks):
        seen = {}
        for b in blocks:
            if b.get("t") in ("h2", "h3"):
                a = b["anchor"]
                n = seen.get(a, 0)
                seen[a] = n + 1
                if n:
                    b["anchor"] = "%s-%d" % (a, n + 1)

    # ------------------------------------------------------------------ item cards
    def stat_rank(self, sid):
        """Weapon damage first, then the stats in the game's display order."""
        if sid == "damage":
            return -1
        order = list(self.g.stats.keys())
        return order.index(sid) if sid in order else 99

    def stat_line(self, sid, value):
        st = self.g.stats.get(sid)
        if not st:
            return [span(humanize(sid) + ": ", color=GREY), span("+" + fmt_num(value), color=GREY)]
        sign = "+" if value >= 0 else "-"
        txt = sign + fmt_num(abs(value)) + ("%" if st["percent"] else "")
        return [span(st["name"] + ": ", color=GREY), span(txt, color=st["color"])]

    def ability_lines(self, ab):
        lines = []
        head = [span("Ability: ", color=GOLD), span(ab.get("name", ""), color=GOLD, b=True)]
        lines.append(head)
        if ab.get("description"):
            lines.append([span(ab["description"], color=GREY)])
        meta = []
        if ab.get("mana"):
            meta.append(span("Mana cost: ", color=GREY))
            meta.append(span(fmt_num(ab["mana"]), color=CYAN))
        if ab.get("cooldown"):
            if meta:
                meta.append(span("   "))
            meta.append(span("Cooldown: ", color=GREY))
            meta.append(span(fmt_num(ab["cooldown"]) + "s", color=GREEN))
        if meta:
            lines.append(meta)
        return lines

    def sources(self, it):
        g = self.g
        iid = it["id"]
        out = []
        for title, offer, count in g.shop_offers.get(iid, []):
            out.append("Sold by %s for %s%s%s" % (title, self.offer_price(offer), "" if count == 1 else " (%d)" % count,
                                                  self.offer_req(offer)))
        for group, ns, key in g.loot_sources.get(iid, []):
            ref = "%s:%s" % (ns, key)
            if group == "bosses":
                nm = self.boss_names.get(ref)
                out.append("Dropped by %s" % nm if nm else "Boss drop")
            elif group == "rewards":
                nm = self.boss_names.get(ref)
                out.append("Reward for clearing %s" % nm if nm else "Dungeon reward")
            elif group == "entities":
                out.append("Dropped by %s" % g.entity_name(ref))
            elif group == "blocks":
                if key == it["path"]:
                    continue
                nm = g.lang.get("block.%s.%s" % (ns, key))
                if nm:
                    out.append("Mined from %s" % nm)
            elif group == "chests":
                out.append("Found in %s chests" % humanize(key.replace("/", " ")).lower())
        for key, wb in self.world_bosses.items():
            if wb.get("rare") == iid:
                out.append("Rare drop from %s (%s%%)" % (wb["name"], fmt_num(wb.get("rare_chance", 0) * 100)))
            if wb.get("bonus") == iid:
                out.append("Bonus drop from %s (%s%%)" % (wb["name"], fmt_num(wb.get("bonus_chance", 0) * 100)))
        for key, sl in self.slayers.items():
            nm = sl.get("name")
            if sl.get("token") == iid:
                out.append("Slayer token from %s" % nm)
            if sl.get("rare") == iid:
                out.append("Rare drop from %s" % nm)
        for key, rg in self.regions.items():
            for dr in rg.get("drops", []):
                if dr.get("item") == iid:
                    out.append("Dropped by monsters in %s" % rg["name"])
        for title, q in self.quest_rewards.get(iid, []):
            out.append("Quest reward: %s" % title)
        seen, uniq = set(), []
        for s in out:
            if s not in seen:
                seen.add(s)
                uniq.append(s)
        return uniq

    def offer_price(self, offer):
        if offer.get("price") is not None:
            return coins(offer["price"])
        parts = ["%sx %s" % (c.get("count", 1), self.g.name_of(c["item"]) or c["item"]) for c in offer.get("costs", [])]
        return " + ".join(parts) or "free"

    def offer_req(self, offer):
        req = offer.get("requires")
        if req and req.get("skill"):
            return " (needs %s level %s)" % (self.g.skill_name(req["skill"]), req.get("level", 1))
        return ""

    def recipe_lines(self, it):
        g = self.g
        lines = []
        for r in g.recipes.get(it["id"], [])[:2]:
            ing = list(r["ingredients"].items())
            head = [span("Recipe: ", color=GREY)]
            if r.get("count", 1) != 1:
                head.insert(0, span("Makes %d. " % r["count"], color=GREY))
            lines.append(head + self.item_list_spans(ing))
            req = r.get("requires")
            if req and req.get("skill"):
                lines.append([span("Requires %s level %s" % (g.skill_name(req["skill"]), req.get("level", 1)), color=GREY)])
        return lines

    def item_card(self, it):
        g = self.g
        tip = []
        stat_ids = sorted(it["stats"].keys(), key=self.stat_rank)
        for sid in stat_ids:
            tip.append(self.stat_line(sid, it["stats"][sid]))
        if it["perks"]:
            if tip:
                tip.append([span(" ")])
            for pk in it["perks"]:
                tip.append([span(pk, color=GREEN)])
        if it["ability"]:
            if tip:
                tip.append([span(" ")])
            tip.extend(self.ability_lines(it["ability"]))
        if it["set"]:
            sd = g.item_sets.get(it["set"])
            if sd:
                if tip:
                    tip.append([span(" ")])
                tip.append([span("Set: ", color=GREY), span(sd.get("name", ""), color=GOLD)])
        if it.get("durability"):
            if tip:
                tip.append([span(" ")])
            tip.append([span("Durability ", color=GREY), span("{:,}".format(it["durability"]), color=GREEN)])
        if it["description"]:
            if tip:
                tip.append([span(" ")])
            tip.append([span(it["description"], color=GREY, i=True)])
        if tip:
            tip.append([span(" ")])
        label = "%s %s" % (it["rarity"].upper(), g.type_label(it).upper()) if it.get("kind") or it.get("type") else it["rarity"].upper()
        color = g.rarity_color(it["rarity"])
        tip.append([span(label, color=color, b=True)])
        lines = list(tip)
        info = []
        price = g.prices.get(it["id"])
        if price is not None:
            info.append([span("Sells for ", color=GREY), span(coins(price), color=GOLD)])
        info.extend(self.recipe_lines(it))
        src = self.sources(it)
        if src:
            info.append([span("How to get: ", color=GREY), span("; ".join(src[:5]) + ("; and more" if len(src) > 5 else ""))])
        card = {"t": "item", "id": it["id"], "icon": it["id"], "name": it["name"], "rarity": it["rarity"], "color": color,
                "anchor": item_anchor(it["id"]), "link": item_anchor(it["id"]), "lines": lines + info, "tip": len(lines)}
        self.used_items.add(it["id"])
        return card


    # ------------------------------------------------------------------ {{items:...}} tables for hand-written pages
    def items_table(self, ids):
        g = self.g
        rows = []
        for iid in ids:
            it = g.items.get(iid)
            row = [[self.ispan(iid)]]
            if it:
                row.append([span(it["rarity"].capitalize(), color=g.rarity_color(it["rarity"]))])
                sids = sorted(it["stats"].keys(), key=self.stat_rank)[:4]
                txt = ", ".join(" ".join(plain(self.stat_line(sk, it["stats"][sk])).split()) for sk in sids)
                row.append([span(txt)])
            else:
                row.append([span("")])
                row.append([span("")])
            price = g.prices.get(iid)
            row.append([span(coins(price) if price is not None else "")])
            src = self.sources(it) if it else ["Sold by %s for %s" % (t, self.offer_price(o)) for t, o, c in g.shop_offers.get(iid, [])]
            row.append([span("; ".join(src[:2]))])
            rows.append(row)
        return self.table(["Item", "Rarity", "Key stats", "Sells for", "How to get"], rows, ["l"] * 5)

    # ------------------------------------------------------------------ build everything
    def build_all(self):
        g = self.g
        self.world_bosses = g.read_dir("world_bosses")
        self.world_bosses = {k.split(":", 1)[1]: v for k, v in self.world_bosses.items()}
        self.dungeon_bosses = g.read_dir("dungeon_bosses")
        self.dungeons = g.read_dir("dungeons")
        self.slayers = {k.split(":", 1)[1]: v for k, v in g.read_dir("slayers").items()}
        self.regions = {k: v for k, v in g.read_dir("danger_regions").items()}
        self.regions = {k.split(":", 1)[1]: v for k, v in self.regions.items()}
        for k, v in self.dungeon_bosses.items():
            self.boss_names[k] = v.get("name", humanize(k.split(":")[1]))
        for k, v in self.world_bosses.items():
            self.boss_names["skyrpg_combat:" + k] = v.get("name", humanize(k))
        self.quests = g.read_dir("quests", recursive=True)
        self.quest_rewards = {}
        for k, q in self.quests.items():
            for r in (q.get("rewards") or {}).get("items", []) or []:
                iid = r.get("item") if isinstance(r, dict) else r
                if isinstance(iid, str):
                    self.quest_rewards.setdefault(iid, []).append((q.get("title", k), q))
        self.apply_item_overrides()
        self.build_item_pages()
        self.item_detail = itempages.build(self)   # item id -> its own page id
        self.build_enchants()
        self.build_bosses()
        self.build_collections()
        self.build_prices()
        for p in self.pages.values():
            self.uniq_anchors(p["blocks"])

    def apply_item_overrides(self):
        for iid, it in self.g.items.items():
            if not it["json"]:
                if it["path"].endswith("_pet") or it["path"].endswith("_egg"):
                    it["rarity"] = "epic" if it["path"] == "golden_pet_egg" else "uncommon"
                    if it["path"].endswith("_pet"):
                        it["type"] = "Pet"
                    else:
                        it["type"] = "Pet egg"
                elif it["path"] == "griffin_whistle":
                    it["type"] = "Mount"
                elif it["path"] == "arcane_tome":
                    it["type"] = "Consumable"
                elif it["ns"] == "skyrpg_mining":
                    it["type"] = "Material"

    # ------------------------------------------------------------------ item pages
    def sort_key(self, it):
        dmg = it["stats"].get("damage", 0)
        return (ORDINAL.get(it["rarity"], 9), dmg, it["tier"] or 0, it["name"])

    SB_GROUPS = {
        "items-weapons": ("Weapons", "Swords, longswords, bows and wands, from SkyBlock's own item data.", "minecraft:iron_sword",
                          {"SWORD": "Swords", "LONGSWORD": "Longswords", "BOW": "Bows", "WAND": "Wands"}),
        "items-armour": ("Armour", "Every helmet, chestplate, pair of leggings and pair of boots.", "minecraft:iron_chestplate",
                         {"HELMET": "Helmets", "CHESTPLATE": "Chestplates", "LEGGINGS": "Leggings", "BOOTS": "Boots"}),
        "items-accessories": ("Accessories", "Accessories and the equipment slots: necklaces, cloaks, belts, gloves and bracelets.",
                              "minecraft:amethyst_shard",
                              {"ACCESSORY": "Accessories", "NECKLACE": "Necklaces", "CLOAK": "Cloaks", "BELT": "Belts",
                               "GLOVES": "Gloves", "BRACELET": "Bracelets"}),
        "items-tools": ("Tools", "Pickaxes, drills, axes, farming tools, fishing rods and the other tools.", "minecraft:iron_pickaxe",
                        {"PICKAXE": "Pickaxes", "DRILL": "Drills", "AXE": "Axes", "FARMING_TOOL": "Farming tools",
                         "FISHING_ROD": "Fishing rods", "SPADE": "Spades", "SHEARS": "Shears", "CHISEL": "Chisels",
                         "WATERING_CAN": "Watering cans", "VACUUM": "Vacuums", "LASSO": "Lassos", "FISHING_NET": "Fishing nets",
                         "GAUNTLET": "Gauntlets"}),
        "items-consumables": ("Consumables", "Items that are used up: potions of every kind, bait, boosters, arrows and scrolls.",
                              "minecraft:bread",
                              {"CONSUMABLE": "Consumables", "BAIT": "Bait", "BOOSTER": "Boosters", "ARROW": "Arrows",
                               "ARROW_POISON": "Arrow poisons", "ABILITY_SCROLL": "Ability scrolls", "CAPSULE": "Capsules"}),
    }

    def build_item_pages(self):
        g = self.g
        by_page = {}
        for it in g.items.values():
            pid = g.item_page(it)
            self.item_page[it["id"]] = pid   # every item keeps its own page (and its group)
            if it.get("skyblock"):           # the survival items are no longer listed on the reference pages
                by_page.setdefault(pid, []).append(it)
        for pid, (title, summary, icon, groups) in self.SB_GROUPS.items():
            self.page_listing(pid, title, summary, icon, by_page.get(pid, []), groups, True,
                              "Sorted by rarity. Select an item for its own page.")
        mats = by_page.get("items-materials", [])
        self.page_listing("items-materials", "Materials", "Crafting materials, drops, ores and everything without a category.",
                          "minecraft:iron_ingot", mats, None, False,
                          "Everything you gather, mine, catch and take from mobs, and what you craft from it. Grouped by rarity.")
        other = by_page.get("items-other", [])
        names = {}
        for it in other:
            names[it["category"]] = humanize(it["category"]).replace("Pet Item", "Pet items") if it.get("category") else "Other"
        self.page_listing("items-other", "Other items", "Reforge stones, pet items, sacks, cosmetics, portals, travel scrolls and the rest.",
                          "minecraft:barrel", other, {k: v if v.endswith("s") else v + "s" for k, v in names.items()}, False,
                          "Items that are not gear, materials or consumables, grouped by their SkyBlock category.")

    def listing_table(self, items, stats):
        g = self.g
        rows = []
        for it in sorted(items, key=lambda i: (ORDINAL.get(i["rarity"], 9), i["name"].lower())):
            row = [[self.ispan(it["id"])], [span(it["rarity"].replace("_", " ").capitalize(), color=g.rarity_color(it["rarity"]))]]
            if stats:
                sids = sorted(it["stats"].keys(), key=self.stat_rank)[:4]
                row.append([span(", ".join(" ".join(plain(self.stat_line(sk, it["stats"][sk])).split()) for sk in sids))])
            price = g.prices.get(it["id"])
            row.append([span("{:,}".format(int(price)) if price is not None else "")])
            rows.append(row)
        head = ["Item", "Rarity"] + (["Key stats"] if stats else []) + ["Sells for (coins)"]
        return self.table(head, rows, ["l"] * (len(head) - 1) + ["r"])

    def page_listing(self, pid, title, summary, icon, items, groups, stats, intro):
        """A compact list page: one table per group (by Hypixel category, or by rarity when groups is None)."""
        blocks = [self.p(span("%s %s" % (intro, "%d items." % len(items))))]
        buckets = {}
        for it in items:
            if groups is None:
                key = it["rarity"]
            else:
                key = it.get("category") or "OTHER"
            buckets.setdefault(key, []).append(it)
        if groups is None:
            order = [r for r in RARITY_ORDER if r in buckets] + sorted(k for k in buckets if k not in RARITY_ORDER)
            label = lambda k: "%s materials" % k.replace("_", " ").capitalize()
        else:
            order = [k for k in groups if k in buckets] + sorted(k for k in buckets if k not in groups)
            label = lambda k: groups.get(k, humanize(k))
        for k in order:
            blocks.append(self.h2("%s (%d)" % (label(k), len(buckets[k]))))
            blocks.append(self.listing_table(buckets[k], stats))
        self.add_page(pid, title, summary, icon, blocks)

    # ------------------------------------------------------------------ enchants
    def build_enchants(self):
        g = self.g
        ench = g.read_dir("enchants")
        rules = g.read_dir("enchanting").get("skyrpg_enchants:rules", {})
        groups = {}
        for k, e in ench.items():
            groups.setdefault(e.get("rarity", "common"), []).append(e)
        blocks = [self.p(span("Enchantments come from the enchanting table (up to the table level shown) or as tomes. "
                              "Ultimate enchants are tome-only and an item can hold just one. The effect column shows the maximum level."))]

        def fill(e, level):
            values = e.get("values", {})

            def rep(m):
                name, pct = m.group(1), m.group(2)
                arr = values.get(name)
                if not arr:
                    return m.group(0)
                v = arr[min(level, len(arr)) - 1]
                return fmt_num(v * 100) + "%" if pct else fmt_num(v)
            return re.sub(r"\{(\w+)(%?)\}", rep, e.get("description", ""))

        for r in ["ultimate", "legendary", "epic", "rare", "uncommon", "common"]:
            if r not in groups:
                continue
            blocks.append(self.h2("%s enchants" % r.capitalize()))
            rows = []
            color = g.enchant_rarity_colors.get(r, "#FFFFFF")
            for e in sorted(groups[r], key=lambda x: x["name"]):
                ml = e.get("max_level", 1)
                applies = ", ".join(a.capitalize() for a in e.get("applies_to", []))
                eff = [span(fill(e, ml))]
                if e.get("source"):
                    eff.append(span(" " + e["source"] + ".", i=True))
                elif not e.get("table_max") and r != "ultimate":
                    pass
                tm = e.get("table_max")
                rows.append([[span(e["name"], color=color, b=True)], [span(applies)],
                             [span(roman(ml) if ml > 1 else "I")], eff])
            blocks.append(self.table(["Enchant", "Applies to", "Max level", "Effect at max level"], rows, ["l", "l", "c", "l"]))
        self.add_page("enchant-list", "Enchantments", "Every enchantment, what it applies to and what it does at its top level.",
                      "minecraft:enchanted_book", blocks)

    # ------------------------------------------------------------------ bosses (slayer bosses only)
    def build_bosses(self):
        blocks = [self.p(span("The four slayer bosses. Slayer quests are bought from Maddox the Slayer in the Hub. Kill enough of the listed "
                              "mobs, then fight the boss. Higher tiers need a higher slayer level or a lower tier cleared first. "
                              "Full details are on the "), span("Slayers", link="slayers"), span(" page."))]
        for key, sl in sorted(self.slayers.items(), key=lambda kv: kv[1].get("order", 0)):
            blocks.append(self.h2(sl.get("name", humanize(key))))
            if sl.get("counts_text"):
                blocks.append(self.p(span("Counts kills of: " + sl["counts_text"] + ".")))
            rows = []
            for i, t in enumerate(sl.get("tiers", [])):
                rows.append([[span(roman(i + 1))], [span("{:,}".format(t.get("fee", 0)))], [span("{:,}".format(t.get("xp", 0)))],
                             [span("{:,}".format(int(t.get("health", 0))))], [span(fmt_num(t.get("damage", 0)))],
                             [span(str(t.get("slayer_xp", "")))], [span(fmt_num(t.get("combat_xp", 0)))]])
            blocks.append(self.table(["Tier", "Fee (coins)", "Kills needed (XP)", "Boss health", "Damage", "Slayer XP", "Combat XP"],
                                     rows, ["c", "r", "r", "r", "r", "r", "r"]))
        self.add_page("boss-list", "Bosses", "The slayer bosses with their tiers, fees, health and rewards.",
                      "minecraft:netherite_sword", blocks)

    # ------------------------------------------------------------------ collections
    def build_collections(self):
        g = self.g
        cols = g.read_dir("collections")
        cats = {}
        for k, c in cols.items():
            cats.setdefault(c.get("category", "other"), []).append((k, c))
        blocks = [self.p(span("Hypixel SkyBlock's collections. A collection counts everything of one kind you gather. "
                              "Each tier lists the amount needed and what it unlocks."))]
        for cat in sorted(cats):
            blocks.append(self.h2(humanize(cat)))
            for k, c in sorted(cats[cat], key=lambda kv: kv[1].get("name", "")):
                items = c.get("items", [])
                icon = c.get("icon") or (items[0] if items else None)
                name = c.get("name", humanize(k.split(":")[1]))
                blocks.append(self.h3(name))
                tiers = c.get("tiers", [])
                th = c.get("thresholds", [])
                rows = []
                for i, amount in enumerate(th):
                    t = tiers[i] if i < len(tiers) else {}
                    parts = list(t.get("unlocks") or [])
                    if t.get("coins"):
                        parts.append(coins(t["coins"]))
                    for sk, sv in (t.get("stats") or {}).items():
                        parts.append("%s +%s" % (g.stats.get(sk, {}).get("name", humanize(sk)), fmt_num(sv)))
                    rows.append([[span(roman(i + 1))], [span("{:,}".format(amount))], [span("; ".join(parts))]])
                has_unlocks = any(r[2][0]["x"] for r in rows)
                if has_unlocks:
                    blocks.append(self.table(["Tier", "Amount", "Unlocks"], rows, ["c", "r", "l"]))
                else:
                    blocks.append(self.table(["Tier", "Amount"], [r[:2] for r in rows], ["c", "r"]))
        self.add_page("collection-list", "Collections", "Every collection with its tier amounts and what each tier unlocks.",
                      "minecraft:writable_book", blocks)

    # ------------------------------------------------------------------ sell prices
    def build_prices(self):
        g = self.g
        blocks = [self.p(span("What NPC shops pay for SkyBlock items, from Hypixel's data. Prices are per item; selling a stack pays for "
                              "every item in it. Items without a price cannot be sold to an NPC."))]
        groups = {}
        for it in g.items.values():
            price = g.prices.get(it["id"])
            if it.get("skyblock") and price is not None:
                groups.setdefault(self.item_page[it["id"]], []).append((it, price))
        for pid in ("items-weapons", "items-armour", "items-accessories", "items-tools", "items-consumables", "items-materials", "items-other"):
            lst = groups.get(pid)
            if not lst:
                continue
            blocks.append(self.h2(self.pages[pid]["title"]))
            rows = [[[self.ispan(it["id"])], [span("{:,}".format(int(price)))]]
                    for it, price in sorted(lst, key=lambda x: x[0]["name"].lower())]
            blocks.append(self.table(["Item", "Sells for (coins)"], rows, ["l", "r"]))
        self.add_page("sell-prices", "Sell prices", "What NPC shops pay for every SkyBlock item.", "minecraft:gold_nugget", blocks)
