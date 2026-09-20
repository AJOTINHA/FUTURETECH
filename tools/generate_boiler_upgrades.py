"""Lava Upgrade, Energy Upgrade and Sand Upgrade: the boiler's heat sources and the extruder's sand
products, plus their models, recipes, tags and names.

All are the Efficiency Upgrade's module (frame, panel, pins copied from speed_upgrade.png) with a
different symbol on the panel: a drop of lava in the lava generator's oranges, a battery cell in
the energy bar's blues, and a heap of sand in sand's own yellows. The item models copy the
Efficiency Upgrade's, only the texture changes.

Run from any directory: python tools/generate_boiler_upgrades.py
"""
import json
from pathlib import Path

from PIL import Image

from generate_efficiency_upgrade import PANEL, PANEL_LEFT, PANEL_TOP, PANEL_RIGHT, PANEL_BOTTOM, TEXTURES

ASSETS = TEXTURES.parents[1]
DATA = ASSETS.parents[1] / "data"

LAVA = (248, 98, 6, 255)
LAVA_BRIGHT = (253, 159, 7, 255)
LAVA_DARK = (192, 74, 6, 255)
ENERGY = (85, 231, 237, 255)
ENERGY_DARK = (22, 118, 196, 255)
ENERGY_SHELL = (172, 188, 200, 255)
SAND = (219, 207, 163, 255)
SAND_LIGHT = (237, 228, 190, 255)
SAND_DARK = (188, 172, 122, 255)

# A drop over the 22 x 17 panel: narrow at the top, round at the bottom, a bright highlight on its left.
DROP = [
    "......................",
    "..........D...........",
    "..........LD..........",
    ".........LLLD.........",
    ".........LLLD.........",
    "........BLLLLD........",
    "........BLLLLD........",
    ".......BBLLLLLD.......",
    ".......BLLLLLLD.......",
    "......BBLLLLLLLD......",
    "......BLLLLLLLLD......",
    "......LLLLLLLLLD......",
    ".......LLLLLLLD.......",
    "........DDDDDD........",
]

# A battery cell lying across the panel: shell, cap on the right, three charged bars inside.
CELL = [
    "......................",
    "......................",
    "......................",
    "..SSSSSSSSSSSSSSSS....",
    "..S..............SSS..",
    "..S.LL.LL.LL.....S.S..",
    "..S.LL.LL.LL.....S.S..",
    "..S.LL.LL.LL.....S.S..",
    "..S.LL.LL.LL.....S.S..",
    "..S.DD.DD.DD.....SSS..",
    "..S..............S....",
    "..SSSSSSSSSSSSSSSS....",
    "......................",
    "......................",
]

# A heap of sand over the panel: a rounded mound, lit from the upper left, with a few loose grains around it.
HEAP = [
    "......................",
    "......................",
    "......................",
    "..........LL..........",
    "........LLLLLS........",
    ".......LLLLLLSS.......",
    "......LLLLLLSSSD......",
    ".....LLLLLSSSSSD......",
    "....LLLLSSSSSSSDD.....",
    "...LLLSSSSSSSSSDDD....",
    "..LLSSSSSSSSSSDDDDD...",
    ".SSSSSSSSSSSSDDDDDDD..",
    "......................",
    "...D......D.....D.....",
]


def texture(rows, colours):
    image = Image.open(TEXTURES / "speed_upgrade.png").convert("RGBA")
    for y in range(PANEL_TOP, PANEL_BOTTOM + 1):
        for x in range(PANEL_LEFT, PANEL_RIGHT + 1):
            image.putpixel((x, y), PANEL)
    for row, line in enumerate(rows):
        for column, cell in enumerate(line):
            if cell != ".":
                image.putpixel((PANEL_LEFT + column, PANEL_TOP + 1 + row), colours[cell])
    return image


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def copy_json(source, target, old, new):
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(source.read_text(encoding="utf-8").replace(old, new), encoding="utf-8")


def resources(name, side_ingredient):
    copy_json(ASSETS / "models/item/efficiency_upgrade.json", ASSETS / f"models/item/{name}.json", "efficiency_upgrade", name)
    copy_json(ASSETS / "items/efficiency_upgrade.json", ASSETS / f"items/{name}.json", "efficiency_upgrade", name)
    copy_json(DATA / "futuretech/advancement/recipes/efficiency_upgrade.json",
              DATA / f"futuretech/advancement/recipes/{name}.json", "efficiency_upgrade", name)
    # The Efficiency Upgrade's recipe with the heat source in place of the redstone.
    write(DATA / f"futuretech/recipe/{name}.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["IGI", "SCS", "IGI"],
        "key": {"I": "minecraft:iron_ingot", "G": "minecraft:gold_ingot", "S": side_ingredient, "C": "futuretech:chip"},
        "result": {"id": f"futuretech:{name}", "count": 1}})
    write(DATA / f"futuretech/tags/item/upgrades/{name.removesuffix('_upgrade')}.json",
          {"replace": False, "values": [f"futuretech:{name}"]})
    tags = DATA / "futuretech/tags/item/upgrades.json"
    obj = json.loads(tags.read_text(encoding="utf-8"))
    if f"futuretech:{name}" not in obj["values"]:
        obj["values"].append(f"futuretech:{name}")
        write(tags, obj)


def names(entries):
    for index, lang in enumerate(("pt_br", "en_us")):
        path = ASSETS / f"lang/{lang}.json"
        text = path.read_text(encoding="utf-8")
        obj = json.loads(text)
        additions = {key: value[index] for key, value in entries.items() if key not in obj}
        if additions:
            text = text[:text.rfind("}")].rstrip() + ",\n" + ",\n".join(
                "  " + json.dumps(key) + ": " + json.dumps(value, ensure_ascii=False) for key, value in additions.items()) + "\n}\n"
            path.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    texture(DROP, {"L": LAVA, "B": LAVA_BRIGHT, "D": LAVA_DARK}).save(TEXTURES / "lava_upgrade.png")
    texture(CELL, {"S": ENERGY_SHELL, "L": ENERGY, "D": ENERGY_DARK}).save(TEXTURES / "energy_upgrade.png")
    texture(HEAP, {"L": SAND_LIGHT, "S": SAND, "D": SAND_DARK}).save(TEXTURES / "sand_upgrade.png")
    resources("lava_upgrade", "minecraft:magma_block")
    resources("energy_upgrade", "futuretech:reception_coil")
    resources("sand_upgrade", "minecraft:sand")
    names({
        "item.futuretech.lava_upgrade": ("Upgrade de Lava", "Lava Upgrade"),
        "item.futuretech.energy_upgrade": ("Upgrade de Energia", "Energy Upgrade"),
        "item.futuretech.sand_upgrade": ("Upgrade de Areia", "Sand Upgrade"),
        "gui.futuretech.boiler.no_lava": ("Sem lava", "No lava"),
        "gui.futuretech.boiler.no_energy": ("Sem energia", "No energy"),
        "gui.futuretech.boiler.lava": ("Lava", "Lava"),
    })
    print("Lava, energy and sand upgrades generated.")
