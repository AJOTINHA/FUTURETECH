"""Front textures, models, blockstate and item model of the lava generator.

The front is drawn over the smeltery's front: same casing, status light and window frame, with
a lava tank behind the glass instead of a crucible. The lit front is an eight-frame animation of
the lava bubbling under the inlet nozzle. The MK1..MK4 block models copy the smeltery's, which
already reuse the shared machine sides and the coloured MK corners, and only swap the front.

Run from any directory: python tools/generate_lava_generator.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/futuretech"
TEXTURES = ASSETS / "textures/block"
FRAMES = 8
FRAMETIME = 3

# Palette shared with the smeltery front, plus the lava colours it uses when lit.
DARK = (26, 27, 30, 255)
FRAME = (36, 40, 46, 255)
STEEL_DARK = (59, 64, 75, 255)
STEEL = (75, 80, 90, 255)
STEEL_LIGHT = (132, 138, 149, 255)
LIGHT_OFF_EDGE = (29, 42, 75, 255)
LIGHT_OFF = (44, 59, 94, 255)
LIGHT_ON_EDGE = (192, 74, 6, 255)
LIGHT_ON = (253, 115, 8, 255)
LAVA_OFF = (46, 31, 28, 255)
LAVA_OFF_SPECK = (62, 37, 34, 255)
LAVA_OFF_CRUST = (69, 39, 33, 255)
LAVA = (248, 98, 6, 255)
LAVA_WARM = (253, 115, 8, 255)
LAVA_BRIGHT = (253, 159, 7, 255)
GLOW = (96, 52, 36, 255)

# Interior of the window frame, and where the lava stands inside it.
INNER_X0, INNER_X1 = 6, 25
INNER_Y0, INNER_Y1 = 10, 25
LAVA_TOP = 18
NOZZLE_X0, NOZZLE_X1 = 14, 17


def fill(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image.putpixel((x, y), colour)


def status_light(image, on):
    edge, centre = (LIGHT_ON_EDGE, LIGHT_ON) if on else (LIGHT_OFF_EDGE, LIGHT_OFF)
    image.putpixel((12, 4), edge)
    image.putpixel((19, 4), edge)
    fill(image, 13, 4, 18, 4, centre)


def tank(image, on, frame, rng):
    # Inlet manifold across the top of the window, with the nozzle hanging from it.
    fill(image, INNER_X0, INNER_Y0, INNER_X1, INNER_Y0, STEEL)
    fill(image, INNER_X0, INNER_Y0 + 1, INNER_X1, INNER_Y0 + 1, STEEL_DARK)
    fill(image, INNER_X0, INNER_Y0 + 2, INNER_X1, INNER_Y1, DARK)
    fill(image, NOZZLE_X0, INNER_Y0 + 2, NOZZLE_X1, INNER_Y0 + 3, STEEL_DARK)
    fill(image, NOZZLE_X0, INNER_Y0 + 2, NOZZLE_X0, INNER_Y0 + 3, STEEL)
    fill(image, NOZZLE_X0 + 1, INNER_Y0 + 3, NOZZLE_X1 - 1, INNER_Y0 + 3, FRAME)
    # Level marks down the right edge of the glass.
    for y in (14, 18, 22):
        image.putpixel((INNER_X1, y), STEEL_LIGHT)
    if not on:
        fill(image, INNER_X0, LAVA_TOP, INNER_X1 - 1, INNER_Y1, LAVA_OFF)
        fill(image, INNER_X0, LAVA_TOP, INNER_X1 - 1, LAVA_TOP, LAVA_OFF_CRUST)
        for x, y in ((8, 20), (13, 23), (18, 21), (22, 24), (11, 25)):
            image.putpixel((x, y), LAVA_OFF_SPECK)
        return
    fill(image, INNER_X0, LAVA_TOP, INNER_X1 - 1, INNER_Y1, LAVA)
    fill(image, INNER_X0, LAVA_TOP, INNER_X1 - 1, LAVA_TOP, LAVA_WARM)
    fill(image, INNER_X0, LAVA_TOP - 1, INNER_X1 - 1, LAVA_TOP - 1, GLOW)
    # Slow warm patches drift across the body of the lava.
    for _ in range(6):
        x = rng.randint(INNER_X0, INNER_X1 - 2)
        y = rng.randint(LAVA_TOP + 1, INNER_Y1)
        fill(image, x, y, x + 1, y, LAVA_WARM)
    # Bubbles rise one row a frame and burst at the surface.
    for start_x, phase in ((9, 0), (20, 3), (14, 5), (23, 6)):
        step = (frame + phase) % FRAMES
        y = INNER_Y1 - step
        if y > LAVA_TOP:
            image.putpixel((start_x, y), LAVA_BRIGHT)
        elif y == LAVA_TOP:
            fill(image, start_x - 1, LAVA_TOP, start_x + 1, LAVA_TOP, LAVA_BRIGHT)
    # A drip from the nozzle grows over the first frames, then splashes.
    drip = frame % FRAMES
    if drip < 5:
        fill(image, 15, INNER_Y0 + 4, 16, INNER_Y0 + 4 + drip, LAVA)
        image.putpixel((15, INNER_Y0 + 4 + drip), LAVA_BRIGHT)
    elif drip == 5:
        fill(image, 15, INNER_Y0 + 4, 16, LAVA_TOP - 1, LAVA)
        fill(image, 13, LAVA_TOP, 18, LAVA_TOP, LAVA_BRIGHT)
    else:
        fill(image, 14, LAVA_TOP, 17, LAVA_TOP, LAVA_BRIGHT)


def front(on, frame, rng):
    image = Image.open(TEXTURES / "smeltery/smeltery_front.png").convert("RGBA")
    status_light(image, on)
    tank(image, on, frame, rng)
    return image


def textures():
    out = TEXTURES / "lava_generator"
    out.mkdir(parents=True, exist_ok=True)
    front(False, 0, random.Random(0)).save(out / "lava_generator_front.png")
    sheet = Image.new("RGBA", (32, 32 * FRAMES))
    rng = random.Random(7)
    for frame in range(FRAMES):
        sheet.paste(front(True, frame, rng), (0, 32 * frame))
    sheet.save(out / "lava_generator_front_on.png")
    (out / "lava_generator_front_on.png.mcmeta").write_text(json.dumps(
        {"animation": {"width": 32, "height": 32, "frametime": FRAMETIME, "interpolate": False}}, indent=2) + "\n",
        encoding="utf-8")


def copy_json(source, target):
    text = (ASSETS / source).read_text(encoding="utf-8").replace("smeltery", "lava_generator")
    path = ASSETS / target
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def models():
    for mk in range(1, 5):
        for suffix in ("", "_on"):
            copy_json(f"models/block/smeltery/mk{mk}{suffix}.json", f"models/block/lava_generator/mk{mk}{suffix}.json")
    copy_json("blockstates/smeltery.json", "blockstates/lava_generator.json")
    copy_json("items/smeltery.json", "items/lava_generator.json")


if __name__ == "__main__":
    textures()
    models()
