#!/usr/bin/env python3
"""The village ends of the areas' sky bridges: the stretches inside the village's own build box, which no area's box
covers without overlapping another area. Same calls as the area scripts, so the halves line up; cells outside the
village box are dropped. Its box is all village, so placing it never clears anything (see HubAreas).

    python tools/builds/hub_areas/village_bridges.py
"""
from kit import Area

area = Area("village_bridges", (-96, 20, -96), (192, 100, 192), version=2)
area.bridge((80, -27), (108, -124), y=64)        # the Farm, from the Bazaar islet's north end
area.bridge((-50, 46), (-117, 84), y=64)         # the Forest, from the south-west edge
area.bridge((38, -89), (22, -122), y=77)         # the Coal Mine, from the Starwatch islet
area.bridge((95, 0), (124, 4), y=64, width=3)    # the Wilderness, from the Bazaar islet's east end
area.save()
