"""Front textures, models, blockstate, item model and data of the charger.

The front is drawn over the crusher's front: same casing, status light and window frame, with a
charging bay behind the glass: a battery outline whose bars fill up, and a bolt on the contact.
The lit front is an eight-frame animation of the bars filling and the bolt flashing.

Run from any directory: python tools/generate_charger.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/futuretech"
TEXTURES = ASSETS / "textures/block"
FRAMES = 8
FRAMETIME = 3

WINDOW = (26, 29, 34, 255)
WINDOW_EDGE = (17, 18, 19, 255)
STEEL = (123, 129, 140, 255)
STEEL_DARK = (69, 74, 83, 255)
COPPER = (196, 116, 70, 255)
COPPER_LIGHT = (236, 156, 100, 255)
CYAN = (119, 249, 252, 255)
CYAN_DARK = (22, 118, 196, 255)
BAR_OFF = (52, 58, 70, 255)

INNER_X0, INNER_X1 = 6, 25
INNER_Y0, INNER_Y1 = 9, 21
# The battery lies on its side in the bay: body, terminal and four charge bars.
BODY_X0, BODY_X1, BODY_Y0, BODY_Y1 = 8, 20, 12, 18
BARS = [(10, 12), (13, 15), (16, 18)]


def fill(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image.putpixel((x, y), colour)


def status_light(image, on_frame):
    for x in range(14, 18):
        image.putpixel((x, 4), on_frame.getpixel((x, 4)))


def bay(image, on, frame):
    # Contacts either side of the battery, the battery outline and its terminal.
    fill(image, INNER_X0 + 1, BODY_Y0 + 2, INNER_X0 + 1, BODY_Y1 - 2, STEEL)
    fill(image, INNER_X1 - 1, BODY_Y0 + 2, INNER_X1 - 1, BODY_Y1 - 2, STEEL)
    fill(image, BODY_X0, BODY_Y0, BODY_X1, BODY_Y1, STEEL_DARK)
    fill(image, BODY_X0 + 1, BODY_Y0 + 1, BODY_X1 - 1, BODY_Y1 - 1, WINDOW)
    fill(image, BODY_X1 + 1, BODY_Y0 + 2, BODY_X1 + 1, BODY_Y1 - 2, COPPER)
    # The bars fill left to right over the animation; off, they are all dark.
    lit = (frame * (len(BARS) + 1)) // FRAMES if on else 0
    for index, (x0, x1) in enumerate(BARS):
        colour = CYAN if index < lit else BAR_OFF
        fill(image, x0, BODY_Y0 + 2, x1, BODY_Y1 - 2, colour)
    # A bolt over the left contact flashes on alternate frames while charging.
    if on and frame % 2 == 0:
        for x, y in ((7, 10), (6, 11), (7, 11), (6, 12)):
            image.putpixel((x, y), CYAN)
        image.putpixel((7, 12), CYAN_DARK)


def front(on, frame):
    image = Image.open(TEXTURES / "crusher/crusher_front.png").convert("RGBA")
    if on:
        status_light(image, Image.open(TEXTURES / "crusher/crusher_front_on.png").convert("RGBA"))
    fill(image, INNER_X0, INNER_Y0, INNER_X1, INNER_Y1, WINDOW)
    fill(image, INNER_X0, INNER_Y0, INNER_X0, INNER_Y1, WINDOW_EDGE)
    fill(image, INNER_X1, INNER_Y0, INNER_X1, INNER_Y1, WINDOW_EDGE)
    bay(image, on, frame)
    return image


def textures():
    out = TEXTURES / "charger"
    out.mkdir(parents=True, exist_ok=True)
    front(False, 0).save(out / "charger_front.png")
    sheet = Image.new("RGBA", (32, 32 * FRAMES))
    for frame in range(FRAMES):
        sheet.paste(front(True, frame), (0, 32 * frame))
    sheet.save(out / "charger_front_on.png")
    (out / "charger_front_on.png.mcmeta").write_text(json.dumps(
        {"animation": {"width": 32, "height": 32, "frametime": FRAMETIME, "interpolate": False}}, indent=2) + "\n",
        encoding="utf-8")


def copy_json(source, target, old, new):
    text = (RES / source).read_text(encoding="utf-8").replace(old, new)
    path = RES / target
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def models_and_data():
    for mk in range(1, 5):
        for suffix in ("", "_on"):
            copy_json(f"assets/futuretech/models/block/smeltery/mk{mk}{suffix}.json",
                      f"assets/futuretech/models/block/charger/mk{mk}{suffix}.json", "smeltery", "charger")
    copy_json("assets/futuretech/blockstates/smeltery.json", "assets/futuretech/blockstates/charger.json", "smeltery", "charger")
    copy_json("assets/futuretech/items/smeltery.json", "assets/futuretech/items/charger.json", "smeltery", "charger")
    copy_json("data/futuretech/loot_table/blocks/crusher.json", "data/futuretech/loot_table/blocks/charger.json", "crusher", "charger")
    copy_json("data/futuretech/advancement/recipes/crusher.json", "data/futuretech/advancement/recipes/charger.json", "crusher", "charger")
    write(RES / "data/futuretech/recipe/charger.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["IBI", "CMC", "IRI"],
        "key": {"I": "minecraft:iron_ingot", "B": "futuretech:battery_mk1", "C": "minecraft:copper_ingot",
                "M": "futuretech:machine_casing", "R": "minecraft:redstone"},
        "result": {"id": "futuretech:charger", "count": 1},
    })


if __name__ == "__main__":
    textures()
    models_and_data()
    print("Generated the charger: textures, models, data and recipe.")
