#!/usr/bin/env python3
"""The SkyBlock clone's progress card: every Hypixel system's percent and the overall one, as one image for Discord.

    python tools/progress_card.py            # renders dist/progress/progress-<date>.png from docs/design/skyblock-status.json
    python tools/progress_card.py --record   # also notes today's overall percent in the file's history (for the "+N%")

The numbers live in docs/design/skyblock-status.json (weight = the system's share of the whole game, done = percent).
Item icons come from the Minecraft jar in the Gradle cache; fonts from Windows (Segoe UI, Bahnschrift). Needs Pillow.
"""
import argparse
import json
import zipfile
from datetime import date
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
STATUS = ROOT / "docs/design/skyblock-status.json"
OUT = ROOT / "dist/progress"
GAME_JAR = Path.home() / ".gradle/caches/fabric-loom/26.3/minecraft-merged.jar"
FONTS = Path("C:/Windows/Fonts")

S = 2                       # drawn at twice the size, then scaled down (smooth edges)
W = 1600
MARGIN = 56
GAP = 22
ROW = 40
CARD_HEAD = 58

BG_TOP = (11, 16, 34)
BG_BOTTOM = (16, 22, 44)
CARD = (21, 28, 52)
CARD_EDGE = (38, 48, 84)
TRACK = (40, 50, 86)
TEXT = (236, 240, 252)
MUTED = (150, 160, 192)
DIM = (92, 102, 136)
ACCENT = (185, 140, 255)


def font(name, size, variation=None):
    try:
        f = ImageFont.truetype(str(FONTS / name), size * S)
        if variation:
            try:
                f.set_variation_by_name(variation)
            except (OSError, ValueError):
                pass
        return f
    except OSError:
        return ImageFont.load_default(size * S)


def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Icons:
    """Item textures straight from the game jar, scaled up with hard pixels."""

    def __init__(self):
        self.jar = zipfile.ZipFile(GAME_JAR) if GAME_JAR.exists() else None

    def get(self, name, size):
        if self.jar is None:
            return None
        try:
            with self.jar.open(f"assets/minecraft/textures/{name}.png") as f:
                img = Image.open(f).convert("RGBA")
        except KeyError:
            return None
        if img.height > img.width:          # animated strips: the first frame
            img = img.crop((0, 0, img.width, img.width))
        return img.resize((size * S, size * S), Image.NEAREST)


def overall(categories):
    systems = [s for c in categories for s in c["systems"]]
    return sum(s["weight"] * s["done"] for s in systems) / sum(s["weight"] for s in systems)


def category_percent(category):
    return sum(s["weight"] * s["done"] for s in category["systems"]) / sum(s["weight"] for s in category["systems"])


def render(status):
    categories = status["categories"]
    total = overall(categories)
    systems = [s for c in categories for s in c["systems"]]
    done = sum(1 for s in systems if s["done"] >= 90)
    started = sum(1 for s in systems if 0 < s["done"] < 90)
    todo = sum(1 for s in systems if s["done"] == 0)

    # Cards: two rows of three, each row as tall as its longest card.
    rows = [categories[:3], categories[3:6]]
    row_heights = [CARD_HEAD + max(len(c["systems"]) for c in row) * ROW + 18 for row in rows]
    header_h = 236
    footer_h = 92
    H = header_h + sum(row_heights) + GAP * (len(rows) - 1) + footer_h + 28

    img = Image.new("RGB", (W * S, H * S), BG_TOP)
    d = ImageDraw.Draw(img)
    for y in range(H * S):
        d.line([(0, y), (W * S, y)], fill=mix(BG_TOP, BG_BOTTOM, y / (H * S)))
    # A soft glow behind the ring.
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gx, gy = (W - 190) * S, 128 * S
    gd.ellipse([gx - 260 * S, gy - 200 * S, gx + 260 * S, gy + 200 * S], fill=ACCENT + (60,))
    glow = glow.filter(ImageFilter.GaussianBlur(90 * S))
    img.paste(glow, (0, 0), glow)
    d = ImageDraw.Draw(img)

    f_label = font("seguisb.ttf", 17)
    f_title = font("segoeuib.ttf", 50)
    f_sub = font("segoeui.ttf", 21)
    f_chip = font("seguisb.ttf", 17)
    f_cat = font("segoeuib.ttf", 22)
    f_name = font("seguisb.ttf", 17)
    f_num = font("bahnschrift.ttf", 19, "Bold")
    f_cat_num = font("bahnschrift.ttf", 24, "Bold")
    f_big = font("bahnschrift.ttf", 64, "Bold")
    f_ring_label = font("seguisb.ttf", 16)
    f_foot = font("segoeui.ttf", 18)
    f_foot_b = font("seguisb.ttf", 18)
    icons = Icons()

    def text(x, y, value, f, fill, anchor="la"):
        d.text((x * S, y * S), value, font=f, fill=fill, anchor=anchor)

    def width(value, f):
        return d.textlength(value, font=f) / S

    def rounded(x1, y1, x2, y2, r, fill, outline=None, w=1):
        d.rounded_rectangle([x1 * S, y1 * S, x2 * S, y2 * S], radius=r * S, fill=fill, outline=outline, width=w * S)

    # ---- header
    label = "SKYRPG  ·  SKYBLOCK"
    text(MARGIN, 52, label, f_label, ACCENT)
    text(MARGIN, 74, "1:1 Hypixel SkyBlock clone", f_title, TEXT)
    when = date.fromisoformat(status["updated"]).strftime("%d %B %Y").lstrip("0")
    text(MARGIN, 140, f"Progress across every system  ·  {when}", f_sub, MUTED)
    x = MARGIN
    for value, color in ((f"{len(systems)} systems", MUTED), (f"{done} done", (92, 214, 138)),
                         (f"{started} in progress", (255, 200, 87)), (f"{todo} not started", DIM)):
        w = width(value, f_chip) + 26
        rounded(x, 182, x + w, 212, 15, (26, 34, 62), CARD_EDGE)
        text(x + 13, 197, value, f_chip, color, "lm")
        x += w + 10

    # ---- the ring
    cx, cy, r, ring = W - 190, 126, 92, 18
    box = [(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S]
    d.arc(box, 0, 360, fill=TRACK, width=ring * S)
    end = -90 + 360 * total / 100
    steps = 60
    for i in range(steps):                     # a gradient sweep, purple into cyan
        a1 = -90 + (end + 90) * i / steps
        a2 = -90 + (end + 90) * (i + 1) / steps + 0.6
        d.arc(box, a1, a2, fill=mix(ACCENT, (79, 209, 224), i / steps), width=ring * S)
    text(cx, cy - 6, f"{total:.0f}%", f_big, TEXT, "mm")
    text(cx, cy + 38, "OVERALL", f_ring_label, MUTED, "mm")
    history = status.get("history") or []
    previous = next((h for h in reversed(history) if h["date"] != status["updated"]), None)
    if previous:
        delta = total - previous["overall"]
        when = date.fromisoformat(previous["date"]).strftime("%d %b").lstrip("0")
        text(cx, cy + r + 26, f"{delta:+.1f}% since {when}", f_chip, (92, 214, 138) if delta >= 0 else (255, 107, 107), "mm")

    # ---- the cards
    card_w = (W - 2 * MARGIN - 2 * GAP) / 3
    y = header_h
    for row, row_h in zip(rows, row_heights):
        for i, category in enumerate(row):
            x1 = MARGIN + i * (card_w + GAP)
            x2 = x1 + card_w
            color = hex_rgb(category["color"])
            rounded(x1, y, x2, y + row_h, 16, CARD, CARD_EDGE)
            rounded(x1, y + 14, x1 + 5, y + 42, 2, color)
            text(x1 + 20, y + 29, category["name"], f_cat, TEXT, "lm")
            text(x2 - 20, y + 29, f"{category_percent(category):.0f}%", f_cat_num, color, "rm")
            d.line([(x1 + 16) * S, (y + CARD_HEAD - 6) * S, (x2 - 16) * S, (y + CARD_HEAD - 6) * S], fill=CARD_EDGE, width=S)
            ry = y + CARD_HEAD
            for system in category["systems"]:
                pct = system["done"]
                icon = icons.get(system["icon"], 20)
                if icon is not None:
                    if pct == 0:
                        icon = Image.blend(Image.new("RGBA", icon.size, (0, 0, 0, 0)), icon, 0.45)
                    img.paste(icon, (int((x1 + 18) * S), int((ry + 4) * S)), icon)
                name_color = TEXT if pct > 0 else DIM
                text(x1 + 48, ry + 14, system["name"], f_name, name_color, "lm")
                num_color = (92, 214, 138) if pct >= 90 else TEXT if pct > 0 else DIM
                text(x2 - 20, ry + 14, f"{pct}%", f_num, num_color, "rm")
                bx1, bx2, by = x1 + 48, x2 - 20, ry + 28
                rounded(bx1, by, bx2, by + 6, 3, TRACK)
                if pct > 0:
                    fill_to = bx1 + (bx2 - bx1) * pct / 100
                    rounded(bx1, by, max(fill_to, bx1 + 6), by + 6, 3, color if pct < 90 else mix(color, (255, 255, 255), 0.25))
                ry += ROW
        y += row_h + GAP

    # ---- footer
    fy = y - GAP + 22
    rounded(MARGIN, fy, W - MARGIN, fy + 60, 16, (24, 31, 58), CARD_EDGE)
    rounded(MARGIN + 16, fy + 17, MARGIN + 70, fy + 43, 13, ACCENT)
    text(MARGIN + 43, fy + 30, "NEW", f_chip, BG_TOP, "mm")
    latest = status.get("latest", "")
    phase = status.get("phase")
    right = ""
    if phase:
        right = f"{phase['name']}  {phase['done']}%"
    max_w = W - 2 * MARGIN - 110 - (width(right, f_foot_b) + 190 if right else 0)
    while latest and width(latest, f_foot) > max_w:
        latest = latest[:-2].rstrip(" ,") + "…"
    text(MARGIN + 84, fy + 30, latest, f_foot, TEXT, "lm")
    if phase:
        px2 = W - MARGIN - 18
        px1 = px2 - 150
        rounded(px1, fy + 27, px2, fy + 33, 3, TRACK)
        rounded(px1, fy + 27, px1 + 150 * phase["done"] / 100, fy + 33, 3, ACCENT)
        text(px1 - 14, fy + 30, right, f_foot_b, MUTED, "rm")

    return img.resize((W, H), Image.LANCZOS), total


def main():
    parser = argparse.ArgumentParser(description="Render the SkyBlock clone's progress card.")
    parser.add_argument("--record", action="store_true", help="add today's overall percent to the history")
    parser.add_argument("-o", "--out", help="where to write the PNG")
    args = parser.parse_args()
    status = json.loads(STATUS.read_text(encoding="utf-8"))
    card, total = render(status)
    out = Path(args.out) if args.out else OUT / f"progress-{status['updated']}.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    card.save(out, optimize=True)
    print(f"{out}  ({card.width}x{card.height}, overall {total:.1f}%)")
    if args.record:
        history = [h for h in status.get("history", []) if h["date"] != status["updated"]]
        history.append({"date": status["updated"], "overall": round(total, 1)})
        status["history"] = history
        STATUS.write_text(json.dumps(status, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
