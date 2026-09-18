"""Textures, models, blockstate, item model and data of the rain sensor.

A slab in the daylight detector's proportions, drawn in the mod's steel: a dark rim, a lighter
frame inside it and, in the middle, the glass the sensor reads through — blue with a drop on it,
or, inverted, amber with the drop crossed out, the way the detector's inverted top reads at a
glance. The side is the same steel with a lighter band along the top edge. The model is
vanilla's own detector template, so the slab is the detector's slab, turned by the blockstate so
the drop's foot is on the side the block faces.

Run from any directory: python tools/generate_rain_sensor.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/futuretech"
TEXTURES = ASSETS / "textures/block/rain_sensor"
SIZE = 32

RIM = (69, 74, 83, 255)
FRAME = (123, 129, 140, 255)
FRAME_LIGHT = (156, 162, 172, 255)
STEEL = (105, 111, 122, 255)
GLASS = (30, 60, 96, 255)
GLASS_EDGE = (22, 44, 72, 255)
GLASS_INVERTED = (110, 78, 34, 255)
GLASS_INVERTED_EDGE = (82, 56, 24, 255)
DROP = (150, 205, 255, 255)
DROP_CORE = (220, 240, 255, 255)
CROSS = (232, 150, 90, 255)


def fill(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image.putpixel((x, y), colour)


def drop(image, colour, core):
    # A drop with its point up: a widening body over a round base, a highlight on the left.
    cx, cy = 15, 15
    for row, half in enumerate((0, 0, 1, 1, 2, 2, 3, 3, 3, 3, 3, 2, 1)):
        fill(image, cx - half, cy - 6 + row, cx + half + 1, cy - 6 + row, colour)
    fill(image, cx - 1, cy + 1, cx - 1, cy + 3, core)


def top(inverted):
    image = Image.new("RGBA", (SIZE, SIZE), RIM)
    fill(image, 2, 2, SIZE - 3, SIZE - 3, FRAME)
    fill(image, 2, 2, SIZE - 3, 2, FRAME_LIGHT)
    fill(image, 2, 2, 2, SIZE - 3, FRAME_LIGHT)
    # Four screws at the corners of the frame.
    for x, y in ((3, 3), (SIZE - 5, 3), (3, SIZE - 5), (SIZE - 5, SIZE - 5)):
        fill(image, x, y, x + 1, y + 1, RIM)
    edge = GLASS_INVERTED_EDGE if inverted else GLASS_EDGE
    glass = GLASS_INVERTED if inverted else GLASS
    fill(image, 6, 6, SIZE - 7, SIZE - 7, edge)
    fill(image, 7, 7, SIZE - 8, SIZE - 8, glass)
    drop(image, DROP, DROP_CORE)
    if inverted:
        # A stroke across the drop, corner to corner: no rain.
        for step in range(12):
            fill(image, 9 + step, 9 + step, 10 + step, 9 + step, CROSS)
    return image


def side():
    image = Image.new("RGBA", (SIZE, SIZE), STEEL)
    # Only rows 20 to 31 show on the slab; a lighter band along the top edge and a dark foot.
    fill(image, 0, 20, SIZE - 1, 21, FRAME_LIGHT)
    fill(image, 0, 22, SIZE - 1, 22, FRAME)
    fill(image, 0, SIZE - 2, SIZE - 1, SIZE - 1, RIM)
    return image


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"  {path.relative_to(ROOT)}")


def textures():
    TEXTURES.mkdir(parents=True, exist_ok=True)
    top(False).save(TEXTURES / "rain_sensor_top.png")
    top(True).save(TEXTURES / "rain_sensor_inverted_top.png")
    side().save(TEXTURES / "rain_sensor_side.png")
    print(f"  {TEXTURES.relative_to(ROOT)}")


def models_and_data():
    for name, top_texture in (("rain_sensor", "rain_sensor_top"), ("rain_sensor_inverted", "rain_sensor_inverted_top")):
        write(ASSETS / f"models/block/{name}.json", {
            "parent": "minecraft:block/template_daylight_detector",
            "textures": {"top": f"futuretech:block/rain_sensor/{top_texture}",
                         "side": "futuretech:block/rain_sensor/rain_sensor_side"},
        })
    # The drop's foot is at the south edge of the texture, so a sensor facing south is unturned.
    variants = {}
    for facing, angle in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
        for inverted in (False, True):
            variant = {"model": "futuretech:block/rain_sensor" + ("_inverted" if inverted else "")}
            if angle:
                variant["y"] = angle
            variants[f"facing={facing},inverted={str(inverted).lower()}"] = variant
    write(ASSETS / "blockstates/rain_sensor.json", {"variants": variants})
    write(ASSETS / "items/rain_sensor.json", {
        "model": {"type": "minecraft:model", "model": "futuretech:block/rain_sensor"},
    })
    write(RES / "data/futuretech/loot_table/blocks/rain_sensor.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "futuretech:rain_sensor"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    # The detector's own recipe, with lapis where its quartz goes: glass to see the sky, blue for the rain.
    write(RES / "data/futuretech/recipe/rain_sensor.json", {
        "type": "minecraft:crafting_shaped", "category": "redstone",
        "pattern": ["GGG", "LLL", "SSS"],
        "key": {"G": "minecraft:glass", "L": "minecraft:lapis_lazuli", "S": "minecraft:stone_slab"},
        "result": {"id": "futuretech:rain_sensor", "count": 1},
    })
    write(RES / "data/futuretech/advancement/recipes/rain_sensor.json", {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_lapis": {"trigger": "minecraft:inventory_changed",
                          "conditions": {"items": [{"items": "minecraft:lapis_lazuli"}]}},
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked",
                               "conditions": {"recipe": "futuretech:rain_sensor"}}},
        "requirements": [["has_lapis", "has_the_recipe"]],
        "rewards": {"recipes": ["futuretech:rain_sensor"]},
    })


if __name__ == "__main__":
    textures()
    models_and_data()
    print("Generated the rain sensor: textures, models, data and recipe.")
