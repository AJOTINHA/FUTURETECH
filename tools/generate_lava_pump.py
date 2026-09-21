"""Front textures, pipe, models, blockstate and item model of the lava pump.

The fronts are the water pump's with the water recoloured to lava, tone for tone, so the two
pumps read as one family: same casing, same window, same nozzle. The pipe follows BuildCraft's
pump tube: a side strip four pixels wide the renderer runs down each block-long section, and a
small end cap. The block models copy the water pump's, which already reuse the shared machine
sides and MK corners.

Run from any directory: python tools/generate_lava_pump.py
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/futuretech"
TEXTURES = ASSETS / "textures/block"

# Water tone -> lava tone. The dark shade is the lava generator's crust, the bright the glow.
RECOLOUR = {
    (0x24, 0x59, 0xc7): (0xf8, 0x62, 0x06),
    (0x1c, 0x46, 0xa5): (0xc0, 0x4a, 0x06),
    (0x48, 0x8c, 0xe6): (0xfd, 0x73, 0x08),
    (0x22, 0x2c, 0x3c): (0x45, 0x27, 0x21),
    (0x55, 0xe7, 0xed): (0xfd, 0x9f, 0x07),
    (0xaa, 0xd2, 0xfa): (0xff, 0xd8, 0x7a),
}

# The pipe, after BuildCraft's pump tube: a dark rim, a speckled grey body and a shadowed edge.
TUBE_RIM = (0x5a, 0x5a, 0x5a, 255)
TUBE_EDGE = (0x60, 0x60, 0x60, 255)
TUBE_BODY = ((0x72, 0x72, 0x72, 255), (0x7c, 0x7c, 0x7c, 255), (0x83, 0x83, 0x83, 255))


def recolour(source):
    image = Image.open(source).convert("RGBA")
    pixels = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            swap = RECOLOUR.get((r, g, b))
            if swap is not None:
                pixels[x, y] = (*swap, a)
    return image


def pipe():
    """A 16x16 sheet: the side strip in the first four columns, two half-block segments tall, and
    the end cap in the 4x4 patch beside its foot. The renderer maps each 1:1 onto the pipe's faces."""
    image = Image.new("RGBA", (16, 16), TUBE_RIM)
    pixels = image.load()
    for segment in (0, 8):
        for y in range(1, 7):
            for x in range(3):
                pixels[x, segment + y] = TUBE_BODY[(x + y) % 3]
            pixels[3, segment + y] = TUBE_EDGE
    for y in range(13, 15):
        for x in range(5, 7):
            pixels[x, y] = TUBE_BODY[(x + y) % 2 * 2]
    return image


def textures():
    out = TEXTURES / "lava_pump"
    out.mkdir(parents=True, exist_ok=True)
    source = TEXTURES / "water_pump"
    recolour(source / "water_pump_front.png").save(out / "lava_pump_front.png")
    recolour(source / "water_pump_front_on.png").save(out / "lava_pump_front_on.png")
    (out / "lava_pump_front_on.png.mcmeta").write_text(
        (source / "water_pump_front_on.png.mcmeta").read_text(encoding="utf-8"), encoding="utf-8")
    pipe().save(out / "pipe.png")


def copy_json(source, target):
    text = (ASSETS / source).read_text(encoding="utf-8").replace("water_pump", "lava_pump")
    path = ASSETS / target
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def models():
    for mk in range(1, 5):
        for suffix in ("", "_on"):
            copy_json(f"models/block/water_pump/mk{mk}{suffix}.json", f"models/block/lava_pump/mk{mk}{suffix}.json")
    copy_json("blockstates/water_pump.json", "blockstates/lava_pump.json")
    copy_json("items/water_pump.json", "items/lava_pump.json")


if __name__ == "__main__":
    textures()
    models()
