"""Front textures, models, blockstate, item model, data and recipes of the sawmill.

The front is drawn over the crusher's front: same casing, status light and window frame, with a
circular saw blade over a log behind the glass instead of the rollers. The lit front is an
eight-frame animation of the blade turning and sawdust flying off the cut. The MK1..MK4 block
models copy the smeltery's, which reuse the shared machine sides and the coloured MK corners, and
only swap the front.

Run from any directory: python tools/generate_sawmill.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/futuretech"
TEXTURES = ASSETS / "textures/block"
FRAMES = 8
FRAMETIME = 2

# Palette sampled from the crusher front so the two machines sit next to each other.
WINDOW = (26, 29, 34, 255)
WINDOW_EDGE = (17, 18, 19, 255)
STEEL_DARK = (69, 74, 83, 255)
STEEL = (123, 129, 140, 255)
STEEL_LIGHT = (143, 148, 157, 255)
TOOTH_OFF = (130, 135, 146, 255)
TOOTH_ON = (119, 249, 252, 255)
HUB = (19, 20, 22, 255)
BARK = (74, 52, 30, 255)
BARK_DARK = (52, 36, 20, 255)
WOOD = (150, 110, 62, 255)
WOOD_LIGHT = (178, 136, 82, 255)
WOOD_DARK = (118, 84, 46, 255)
KERF = (38, 26, 14, 255)
SAWDUST = (214, 178, 116, 255)

# Interior of the window frame on the crusher front.
INNER_X0, INNER_X1 = 6, 25
INNER_Y0, INNER_Y1 = 9, 21
# Where the blade turns and how big it is, in pixels of the 32 x 32 front.
CENTRE_X, CENTRE_Y = 15.5, 13.0
BODY_RADIUS, TOOTH_RADIUS = 4.3, 5.4
TEETH = 6
# The log lies along the bottom of the window; the blade bites into its top.
LOG_Y0 = 16


def fill(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image.putpixel((x, y), colour)


def status_light(image, on_frame):
    """The crusher's lit front only changes the four status pixels; copy them when lit."""
    for x in range(14, 18):
        image.putpixel((x, 4), on_frame.getpixel((x, 4)))


def log(image, on, frame, rng):
    fill(image, INNER_X0, LOG_Y0, INNER_X1, INNER_Y1, WOOD)
    fill(image, INNER_X0, LOG_Y0, INNER_X1, LOG_Y0, BARK)
    fill(image, INNER_X0, INNER_Y1, INNER_X1, INNER_Y1, BARK_DARK)
    # Grain: a light and a dark streak that drift a pixel as the log feeds through while working.
    shift = frame % 4 if on else 0
    for x in range(INNER_X0, INNER_X1 + 1):
        if (x + shift) % 5 == 0: image.putpixel((x, LOG_Y0 + 2), WOOD_LIGHT)
        if (x + shift) % 7 == 3: image.putpixel((x, LOG_Y0 + 4), WOOD_DARK)
    # The kerf: the cut the blade leaves behind it, half the window wide when working.
    kerf_x0 = INNER_X0 if on else 15
    fill(image, kerf_x0, LOG_Y0, 16, LOG_Y0 + 2, KERF)


def blade(image, on, frame):
    phase = frame * (360 / TEETH) / FRAMES
    for y in range(INNER_Y0, INNER_Y1 + 1):
        for x in range(INNER_X0, INNER_X1 + 1):
            dx, dy = x + 0.5 - CENTRE_X, y + 0.5 - CENTRE_Y
            r = math.hypot(dx, dy)
            if r > TOOTH_RADIUS: continue
            if r <= 1.2:
                image.putpixel((x, y), HUB)
            elif r <= BODY_RADIUS:
                # A lighter upper-left half reads as a lit metal disc.
                image.putpixel((x, y), STEEL_LIGHT if dx + dy < -1.5 else STEEL if dx + dy < 2.5 else STEEL_DARK)
            else:
                angle = (math.degrees(math.atan2(dy, dx)) - phase) % (360 / TEETH)
                if angle < 360 / TEETH / 2:
                    image.putpixel((x, y), TOOTH_ON if on else TOOTH_OFF)


def sawdust(image, frame, rng):
    # A few chips thrown up and to the right of the cut, different every frame.
    for _ in range(4):
        x = rng.randint(17, INNER_X1)
        y = rng.randint(INNER_Y0, LOG_Y0 - 1)
        dx, dy = x + 0.5 - CENTRE_X, y + 0.5 - CENTRE_Y
        if math.hypot(dx, dy) > TOOTH_RADIUS + 0.5:
            image.putpixel((x, y), SAWDUST)


def front(on, frame, rng):
    image = Image.open(TEXTURES / "crusher/crusher_front.png").convert("RGBA")
    if on:
        status_light(image, Image.open(TEXTURES / "crusher/crusher_front_on.png").convert("RGBA"))
    fill(image, INNER_X0, INNER_Y0, INNER_X1, INNER_Y1, WINDOW)
    fill(image, INNER_X0, INNER_Y0, INNER_X0, INNER_Y1, WINDOW_EDGE)
    fill(image, INNER_X1, INNER_Y0, INNER_X1, INNER_Y1, WINDOW_EDGE)
    log(image, on, frame, rng)
    blade(image, on, frame)
    if on: sawdust(image, frame, rng)
    return image


def textures():
    out = TEXTURES / "sawmill"
    out.mkdir(parents=True, exist_ok=True)
    front(False, 0, random.Random(0)).save(out / "sawmill_front.png")
    sheet = Image.new("RGBA", (32, 32 * FRAMES))
    rng = random.Random(7)
    for frame in range(FRAMES):
        sheet.paste(front(True, frame, rng), (0, 32 * frame))
    sheet.save(out / "sawmill_front_on.png")
    (out / "sawmill_front_on.png.mcmeta").write_text(json.dumps(
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
                      f"assets/futuretech/models/block/sawmill/mk{mk}{suffix}.json", "smeltery", "sawmill")
    copy_json("assets/futuretech/blockstates/smeltery.json", "assets/futuretech/blockstates/sawmill.json", "smeltery", "sawmill")
    copy_json("assets/futuretech/items/smeltery.json", "assets/futuretech/items/sawmill.json", "smeltery", "sawmill")
    copy_json("data/futuretech/loot_table/blocks/crusher.json", "data/futuretech/loot_table/blocks/sawmill.json", "crusher", "sawmill")
    copy_json("data/futuretech/advancement/recipes/crusher.json", "data/futuretech/advancement/recipes/sawmill.json", "crusher", "sawmill")
    # The crafting recipe is balanced by hand in data/futuretech/recipe/ and is not written here.


def recipes():
    """Every vanilla wood: a log tag gives twice the planks a crafting table would, and a plank twice the sticks."""
    woods = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak"]
    entries = {}
    for wood in woods:
        entries[f"{wood}_planks"] = {"ingredient": f"#minecraft:{wood}_logs", "result": {"id": f"minecraft:{wood}_planks", "count": 8}}
    for stem in ("crimson", "warped"):
        entries[f"{stem}_planks"] = {"ingredient": f"#minecraft:{stem}_stems", "result": {"id": f"minecraft:{stem}_planks", "count": 8}}
    entries["bamboo_planks"] = {"ingredient": "#minecraft:bamboo_blocks", "result": {"id": "minecraft:bamboo_planks", "count": 4}}
    entries["stick"] = {"ingredient": "#minecraft:planks", "result": {"id": "minecraft:stick", "count": 4}}
    write(ROOT / "src/main/recipes/sawmill.json", {"type": "futuretech:sawing", "recipes": dict(sorted(entries.items()))})


def lang():
    for language, name in (("pt_br", "Serraria"), ("en_us", "Sawmill")):
        path = ASSETS / f"lang/{language}.json"
        translations = json.loads(path.read_text(encoding="utf-8-sig"))
        translations["block.futuretech.sawmill"] = name
        write(path, translations)


if __name__ == "__main__":
    textures()
    models_and_data()
    recipes()
    lang()
    print("Generated the sawmill: textures, models, data, recipes and names.")
