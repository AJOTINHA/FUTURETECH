"""Front textures, pipe, models, blockstate and item model of the lava pump.

The fronts are the water pump's with the water recoloured to lava, tone for tone, so the two
pumps read as one family: same casing, same window, same nozzle. The pipe is a 16x16 tile the
renderer wraps round each block-long section, so its rings repeat down the length. The block
models copy the water pump's, which already reuse the shared machine sides and MK corners.

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

PIPE = "#3a3d43"
PIPE_LIGHT = "#6d737b"
PIPE_DARK = "#1f2226"
RING = "#8b9199"
RING_SHADE = "#4c5158"


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
    image = Image.new("RGBA", (16, 16), PIPE)
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 1, 15), fill=PIPE_LIGHT)
    draw.rectangle((14, 0, 15, 15), fill=PIPE_DARK)
    draw.line((7, 0, 7, 15), fill=PIPE_DARK)
    # A coupling ring at the top of every section, a thinner one two thirds down.
    draw.rectangle((0, 0, 15, 1), fill=RING)
    draw.line((0, 2, 15, 2), fill=RING_SHADE)
    draw.line((0, 10, 15, 10), fill=RING)
    draw.line((0, 11, 15, 11), fill=RING_SHADE)
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
