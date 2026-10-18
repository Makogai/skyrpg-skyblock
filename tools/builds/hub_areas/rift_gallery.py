#!/usr/bin/env python3
"""The Rift, the Rift Gallery (Hypixel's): the Rift's museum, a pocket hall (x -380..-352, y 96..112, z -126..-106) reached
by the teleport pad in the Rift Wizard Tower once the Supreme Timecharm is secured. Eight pedestals, one per Timecharm,
each with a glass case on top: the mod shows the charm (its block) in the case of each one you've secured, on your
screen only. A pad at the back takes you home. Rift Time doesn't drain here.

    cd tools/builds/hub_areas && PYTHONIOENCODING=utf-8 python rift_gallery.py
"""
from kit import hash01
from rift_lib import new_area

area = new_area("gallery", version=1)
area.shell = 0
S = area.set

X0, X1 = -378, -354             # interior x
Z0, Z1 = -124, -108             # interior z
F = 100                          # floor
H = 9                            # interior height
ZC = (Z0 + Z1) // 2              # -116
CHARMS = ["RIFT_TROPHY_WYLDLY_SUPREME", "RIFT_TROPHY_MIRRORED", "RIFT_TROPHY_CHICKEN_N_EGG", "RIFT_TROPHY_CITIZEN",
          "RIFT_TROPHY_LAZY_LIVING", "RIFT_TROPHY_SLIME", "RIFT_TROPHY_VAMPIRIC", "RIFT_TROPHY_MOUNTAIN"]
NAMES = [["Wyldly", "Supreme"], ["mrahcemiT", "esrevrorriM"], ["Chicken N Egg", ""], ["SkyBlock", "Citizen"],
         ["Living", ""], ["Globulate", ""], ["Vampiric", ""], ["Celestial", ""]]

# the hall: purpur and calcite walls, a dark floor with a violet runner, a coffered ceiling with lamps
for x in range(X0 - 1, X1 + 2):
    for z in range(Z0 - 1, Z1 + 2):
        wall = x in (X0 - 1, X1 + 1) or z in (Z0 - 1, Z1 + 1)
        S(x, F - 1, z, "minecraft:deepslate_tiles")
        runner = abs(z - ZC) <= 1 and X0 + 1 <= x <= X1 - 1
        S(x, F, z, "minecraft:purple_carpet" if runner and not wall else "minecraft:polished_deepslate" if (x + z) % 2 else "minecraft:deepslate_tiles")
        if runner and not wall:
            S(x, F - 1, z, "minecraft:polished_deepslate")
            S(x, F, z, "minecraft:polished_blackstone_bricks")
            S(x, F + 1, z, "minecraft:purple_carpet")
        ceiling = "minecraft:purpur_block"
        if not wall and (x - X0) % 4 == 2 and (z - Z0) % 4 == 2:
            ceiling = "minecraft:pearlescent_froglight"
        S(x, F + H, z, ceiling)
        for y in range(F + 1, F + H):
            if wall:
                if y == F + 1:
                    S(x, y, z, "minecraft:polished_blackstone_bricks")
                elif y == F + H - 1:
                    S(x, y, z, "minecraft:purpur_pillar[axis=y]")
                elif (x + y + z) % 7 == 0 and z in (Z0 - 1, Z1 + 1) and F + 3 <= y <= F + 6:
                    S(x, y, z, "minecraft:amethyst_block")
                else:
                    S(x, y, z, "minecraft:calcite" if hash01(x, y, z, 5) < 0.7 else "minecraft:smooth_quartz")
            elif not (runner and y == F + 1):
                area.clear(x, y, z)

# eight pedestals, four along each long wall, facing the runner
for k, charm in enumerate(CHARMS):
    side = -1 if k < 4 else 1
    x = X0 + 4 + (k % 4) * 6
    z = ZC + side * 5
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            S(x + dx, F + 1, z + dz, "minecraft:polished_blackstone_brick_slab[type=bottom]")
    S(x, F + 1, z, "minecraft:chiseled_polished_blackstone")
    S(x, F + 2, z, "minecraft:polished_blackstone_wall")
    S(x, F + 3, z, "minecraft:chiseled_quartz_block")
    # the case: glass round the display cell (F + 4); the mod puts the charm's block in it for each player
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        S(x + dx, F + 4, z + dz, "minecraft:glass_pane")
    S(x, F + 5, z, "minecraft:purpur_slab[type=bottom]")
    area.clear(x, F + 4, z)
    facing = "north" if side > 0 else "south"
    sz = z - side * 2
    S(x, F + 2, sz, "minecraft:polished_blackstone_wall")
    S(x, F + 3, sz, f"minecraft:dark_oak_wall_sign[facing={facing}]" if False else f"minecraft:dark_oak_sign[rotation={8 if side > 0 else 0}]",
      {"front_text": {"messages": [NAMES[k][0], NAMES[k][1], "Timecharm", ""]}, "back_text": {"messages": [NAMES[k][0], NAMES[k][1], "Timecharm", ""]}})
    area.npc(f"spawn:gallery_slot_{k + 1}", x, F + 4, z, 0.0, f"the {charm} case (display cell)")
    for dx, dz in ((-1, -1), (1, 1)):
        S(x + dx, F + 2, z + dz, "minecraft:amethyst_cluster[facing=up]")

# the way home: a pad at the east end under an arch
for z in range(ZC - 2, ZC + 3):
    for y in range(F + 1, F + 6):
        if abs(z - ZC) == 2 or y == F + 5:
            S(X1, y, z, "minecraft:crying_obsidian")
        else:
            S(X1, y, z, "minecraft:purple_stained_glass")
S(X1 - 1, F, ZC, "minecraft:respawn_anchor[charges=4]")
area.npc("spawn:gallery_arrive", X0 + 1, F + 2, ZC, -90.0, "the Gallery's west end, on the runner, facing in")
area.npc("spawn:gallery_exit", X1 - 1, F + 1, ZC, -90.0, "the pad home, east end")
area.npc("Elise (Gallery)", X1 - 3, F + 2, ZC + 2, 90.0, "by the pad home, on the runner's side")

area.zone("Rift Gallery", box=((X0 - 2, F - 3, Z0 - 2), (X1 + 2, F + H + 2, Z1 + 2)))
area.save()
