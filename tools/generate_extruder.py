"""Front textures, models, blockstate and item model of the extruder.

The front is drawn over the smeltery's front: same casing, status light and window frame, with the
extrusion die behind the glass. Lava runs down a ramp on the left and water down one on the
right, they meet over the die in a line of steam, and the stone that sets there is pushed out of it
a layer at a time, an eight-frame animation. Unlit, both feeds go cold and a half-finished slab
sits in the die. The MK1..MK4 block models copy the smeltery's, which already reuse the shared machine sides
and the coloured MK corners, and only swap the front.

Run from any directory: python tools/generate_extruder.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/futuretech"
TEXTURES = ASSETS / "textures/block"
FRAMES = 8
FRAMETIME = 3

# Palette shared with the smeltery front, plus what the die is fed and what leaves it.
DARK = (26, 27, 30, 255)
STEEL_DARK = (59, 64, 75, 255)
STEEL = (75, 80, 90, 255)
STEEL_LIGHT = (132, 138, 149, 255)
LIGHT_OFF_EDGE = (29, 42, 75, 255)
LIGHT_OFF = (44, 59, 94, 255)
LIGHT_ON_EDGE = (192, 74, 6, 255)
LIGHT_ON = (253, 115, 8, 255)
LAVA = (248, 98, 6, 255)
LAVA_BRIGHT = (253, 159, 7, 255)
LAVA_COLD = (92, 45, 28, 255)
WATER = (54, 118, 214, 255)
WATER_BRIGHT = (108, 172, 236, 255)
WATER_COLD = (34, 56, 92, 255)
STEAM = (196, 208, 214, 255)
STONE = (125, 125, 125, 255)
STONE_DARK = (98, 98, 98, 255)
STONE_LIGHT = (152, 152, 152, 255)
# The layer that has just set still carries the heat that made it.
STONE_HOT = (176, 108, 72, 255)

# Interior of the window frame, and what stands where inside it.
INNER_X0, INNER_X1 = 6, 25
INNER_Y0, INNER_Y1 = 10, 25
# The lava ramp: one span per row, from the window's top left down towards the middle. The water
# ramp is this mirrored, so the two feeds meet over the die in a V.
LAVA_RAMP = ((6, 12), (7, 12), (8, 13), (9, 13))
RAMP_Y0 = INNER_Y0
# Where the two run together, right above the die.
MEETING_Y = RAMP_Y0 + len(LAVA_RAMP)
# The die: a steel bar across the window with a dark mouth in it.
DIE_Y = MEETING_Y + 1
DIE_X0, DIE_X1 = 8, 23
MOUTH_X0, MOUTH_X1 = 12, 19
# The stone stands below the die, growing towards the bottom of the window.
STACK_Y0 = DIE_Y + 2


def fill(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image.putpixel((x, y), colour)


def mirrored(span):
    """The same span on the other side of the window."""
    x0, x1 = span
    return INNER_X0 + INNER_X1 - x1, INNER_X0 + INNER_X1 - x0


def status_light(image, on):
    edge, centre = (LIGHT_ON_EDGE, LIGHT_ON) if on else (LIGHT_OFF_EDGE, LIGHT_OFF)
    image.putpixel((12, 4), edge)
    image.putpixel((19, 4), edge)
    fill(image, 13, 4, 18, 4, centre)


def feeds(image, on, frame):
    """The two ramps: lava down the left, water down the right, both aimed at the die."""
    lava, water = (LAVA, WATER) if on else (LAVA_COLD, WATER_COLD)
    for row, span in enumerate(LAVA_RAMP):
        y = RAMP_Y0 + row
        fill(image, span[0], y, span[1], y, lava)
        right = mirrored(span)
        fill(image, right[0], y, right[1], y, water)
        # A bright row runs down each ramp, so a feed reads as flowing rather than painted on.
        if on and row == frame % len(LAVA_RAMP):
            fill(image, span[0], y, span[1], y, LAVA_BRIGHT)
            fill(image, right[0], y, right[1], y, WATER_BRIGHT)


def meeting(image, on, frame, rng):
    """Where lava and water run together over the die: a line of steam that boils along it."""
    if not on:
        return
    fill(image, MOUTH_X0, MEETING_Y, MOUTH_X1, MEETING_Y, STEAM)
    for _ in range(3):
        image.putpixel((rng.randrange(MOUTH_X0, MOUTH_X1 + 1), MEETING_Y - 1), STEAM)
    # The line itself is uneven, which keeps it from reading as a drawn bar.
    for _ in range(2):
        image.putpixel((rng.randrange(MOUTH_X0, MOUTH_X1 + 1), MEETING_Y), DARK)


def die(image, on):
    """The frame the stone sets in: a steel bar across the window with a dark mouth in it."""
    fill(image, DIE_X0, DIE_Y, DIE_X1, DIE_Y + 1, STEEL if on else STEEL_DARK)
    fill(image, DIE_X0, DIE_Y, DIE_X1, DIE_Y, STEEL_LIGHT if on else STEEL)
    fill(image, MOUTH_X0, DIE_Y + 1, MOUTH_X1, DIE_Y + 1, DARK)


def stack(image, on, frame, rng):
    """
    The stone leaving the die, a layer at a time. It grows for seven frames and the eighth drops
    away, which reads as the finished piece being pushed clear rather than as a stutter.
    """
    height = (1, 2, 3, 4, 5, 6, 7, 2)[frame] if on else 4
    bottom = min(INNER_Y1, STACK_Y0 + height - 1)
    for y in range(STACK_Y0, bottom + 1):
        for x in range(MOUTH_X0, MOUTH_X1 + 1):
            image.putpixel((x, y), rng.choice((STONE, STONE, STONE_DARK, STONE_LIGHT)))
    # The newest layer, right under the die, has not finished cooling.
    if on:
        fill(image, MOUTH_X0, STACK_Y0, MOUTH_X1, STACK_Y0, STONE_HOT)
    # A shadow down each side of the mouth keeps the slab off the window's back wall.
    for y in range(STACK_Y0, bottom + 1):
        image.putpixel((MOUTH_X0 - 1, y), STEEL_DARK)
        image.putpixel((MOUTH_X1 + 1, y), STEEL_DARK)


def front(on, frame, rng):
    image = Image.open(TEXTURES / "smeltery/smeltery_front.png").convert("RGBA")
    status_light(image, on)
    fill(image, INNER_X0, INNER_Y0, INNER_X1, INNER_Y1, DARK)
    feeds(image, on, frame)
    meeting(image, on, frame, rng)
    die(image, on)
    stack(image, on, frame, rng)
    return image


def textures():
    out = TEXTURES / "extruder"
    out.mkdir(parents=True, exist_ok=True)
    front(False, 0, random.Random(0)).save(out / "extruder_front.png")
    sheet = Image.new("RGBA", (32, 32 * FRAMES))
    for frame in range(FRAMES):
        # A seed per frame keeps the stone's speckle steady while the animation runs.
        sheet.paste(front(True, frame, random.Random(20 + frame)), (0, 32 * frame))
    sheet.save(out / "extruder_front_on.png")
    (out / "extruder_front_on.png.mcmeta").write_text(json.dumps(
        {"animation": {"width": 32, "height": 32, "frametime": FRAMETIME, "interpolate": False}}, indent=2) + "\n",
        encoding="utf-8")


def copy_json(source, target):
    text = (ASSETS / source).read_text(encoding="utf-8").replace("smeltery", "extruder")
    path = ASSETS / target
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def models():
    for mk in range(1, 5):
        for suffix in ("", "_on"):
            copy_json(f"models/block/smeltery/mk{mk}{suffix}.json", f"models/block/extruder/mk{mk}{suffix}.json")
    copy_json("blockstates/smeltery.json", "blockstates/extruder.json")
    copy_json("items/smeltery.json", "items/extruder.json")


if __name__ == "__main__":
    textures()
    models()
