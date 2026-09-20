"""Front textures, models, blockstate and item model of the melter.

The front is drawn over the smeltery's front: same casing, status light and window frame, with a
crucible of stone melting behind the glass. Unlit, a grey stone sits on the grate over dark
slag; lit, the stone glows from the bottom up and lava drips off the grate into the pool below,
an eight-frame animation. The MK1..MK4 block models copy the smeltery's, which already reuse the
shared machine sides and the coloured MK corners, and only swap the front.

Run from any directory: python tools/generate_melter.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/futuretech"
TEXTURES = ASSETS / "textures/block"
FRAMES = 8
FRAMETIME = 3

# Palette shared with the smeltery front, plus the stone and lava the melter shows.
DARK = (26, 27, 30, 255)
FRAME = (36, 40, 46, 255)
STEEL_DARK = (59, 64, 75, 255)
STEEL = (75, 80, 90, 255)
STEEL_LIGHT = (132, 138, 149, 255)
LIGHT_OFF_EDGE = (29, 42, 75, 255)
LIGHT_OFF = (44, 59, 94, 255)
LIGHT_ON_EDGE = (192, 74, 6, 255)
LIGHT_ON = (253, 115, 8, 255)
STONE = (125, 125, 125, 255)
STONE_DARK = (98, 98, 98, 255)
STONE_HOT = (200, 90, 40, 255)
STONE_HOTTER = (240, 120, 30, 255)
LAVA_OFF = (46, 31, 28, 255)
LAVA_OFF_CRUST = (69, 39, 33, 255)
LAVA = (248, 98, 6, 255)
LAVA_WARM = (253, 115, 8, 255)
LAVA_BRIGHT = (253, 159, 7, 255)
GLOW = (96, 52, 36, 255)

# Interior of the window frame: the grate the stone sits on, and the pool under it.
INNER_X0, INNER_X1 = 6, 25
INNER_Y0, INNER_Y1 = 10, 25
GRATE_Y = 18
STONE_X0, STONE_X1 = 12, 19
STONE_Y0 = 12


def fill(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image.putpixel((x, y), colour)


def status_light(image, on):
    edge, centre = (LIGHT_ON_EDGE, LIGHT_ON) if on else (LIGHT_OFF_EDGE, LIGHT_OFF)
    image.putpixel((12, 4), edge)
    image.putpixel((19, 4), edge)
    fill(image, 13, 4, 18, 4, centre)


def crucible(image, on, frame, rng):
    fill(image, INNER_X0, INNER_Y0, INNER_X1, INNER_Y1, DARK)
    # The grate: a bar of steel across the window with gaps the melt drips through.
    fill(image, INNER_X0, GRATE_Y, INNER_X1, GRATE_Y, STEEL_DARK)
    for x in range(INNER_X0 + 1, INNER_X1, 3):
        image.putpixel((x, GRATE_Y), STEEL)
    # Level marks down the right edge of the glass.
    for y in (14, 21, 24):
        image.putpixel((INNER_X1, y), STEEL_LIGHT)
    # The stone on the grate: a rough block, darker at its base.
    fill(image, STONE_X0, STONE_Y0, STONE_X1, GRATE_Y - 1, STONE)
    fill(image, STONE_X0, GRATE_Y - 2, STONE_X1, GRATE_Y - 1, STONE_DARK)
    image.putpixel((STONE_X0, STONE_Y0), DARK)
    image.putpixel((STONE_X1, STONE_Y0), DARK)
    if not on:
        fill(image, INNER_X0, GRATE_Y + 3, INNER_X1 - 1, INNER_Y1, LAVA_OFF)
        fill(image, INNER_X0, GRATE_Y + 3, INNER_X1 - 1, GRATE_Y + 3, LAVA_OFF_CRUST)
        return
    # The stone heats from the bottom: the two lowest rows glow, brighter at the edges of the grate.
    fill(image, STONE_X0, GRATE_Y - 2, STONE_X1, GRATE_Y - 1, STONE_HOT)
    fill(image, STONE_X0 + 1, GRATE_Y - 1, STONE_X1 - 1, GRATE_Y - 1, STONE_HOTTER)
    for _ in range(3):
        x = rng.randint(STONE_X0, STONE_X1)
        image.putpixel((x, GRATE_Y - 3), STONE_HOT)
    # The pool below, lit, with warm patches drifting through it.
    fill(image, INNER_X0, GRATE_Y + 3, INNER_X1 - 1, INNER_Y1, LAVA)
    fill(image, INNER_X0, GRATE_Y + 3, INNER_X1 - 1, GRATE_Y + 3, LAVA_WARM)
    fill(image, INNER_X0, GRATE_Y + 2, INNER_X1 - 1, GRATE_Y + 2, GLOW)
    for _ in range(4):
        x = rng.randint(INNER_X0, INNER_X1 - 2)
        y = rng.randint(GRATE_Y + 4, INNER_Y1)
        fill(image, x, y, x + 1, y, LAVA_WARM)
    # Drips fall from the grate's gaps into the pool, each on its own beat.
    for x, phase in ((13, 0), (17, 3), (15, 5)):
        step = (frame + phase) % FRAMES
        if step < 3:
            image.putpixel((x, GRATE_Y + 1 + step), LAVA_BRIGHT)
        elif step == 3:
            fill(image, x - 1, GRATE_Y + 3, x + 1, GRATE_Y + 3, LAVA_BRIGHT)


def front(on, frame, rng):
    image = Image.open(TEXTURES / "smeltery/smeltery_front.png").convert("RGBA")
    status_light(image, on)
    crucible(image, on, frame, rng)
    return image


def textures():
    out = TEXTURES / "melter"
    out.mkdir(parents=True, exist_ok=True)
    front(False, 0, random.Random(0)).save(out / "melter_front.png")
    sheet = Image.new("RGBA", (32, 32 * FRAMES))
    rng = random.Random(11)
    for frame in range(FRAMES):
        sheet.paste(front(True, frame, rng), (0, 32 * frame))
    sheet.save(out / "melter_front_on.png")
    (out / "melter_front_on.png.mcmeta").write_text(json.dumps(
        {"animation": {"width": 32, "height": 32, "frametime": FRAMETIME, "interpolate": False}}, indent=2) + "\n",
        encoding="utf-8")


def copy_json(source, target):
    text = (ASSETS / source).read_text(encoding="utf-8").replace("smeltery", "melter")
    path = ASSETS / target
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def models():
    for mk in range(1, 5):
        for suffix in ("", "_on"):
            copy_json(f"models/block/smeltery/mk{mk}{suffix}.json", f"models/block/melter/mk{mk}{suffix}.json")
    copy_json("blockstates/smeltery.json", "blockstates/melter.json")
    copy_json("items/smeltery.json", "items/melter.json")


if __name__ == "__main__":
    textures()
    models()
