"""Front textures of the mixer, a machine the mod does not have yet.

Drawn over the smeltery's front: same casing, status light and window frame, with a vat behind the
glass. Unlit, a still pool sits under a stopped paddle; lit, the paddle turns and the surface
churns, an eight-frame animation. This was the extruder's first front; the extruder now has one of
its own, and this one is kept for the mixer.

It writes to art/mixer/exported, outside the resources, so nothing of it reaches the JAR. When the
mixer exists, point OUT at its textures folder and add the models the way generate_extruder.py does.

Run from any directory: python tools/generate_mixer.py
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/futuretech"
TEXTURES = ASSETS / "textures/block"
# Kept out of the resources until there is a mixer to wear it.
OUT = ROOT / "art/mixer/exported"
FRAMES = 8
FRAMETIME = 3

# Palette shared with the smeltery front, plus the vat and what turns in it.
STEEL_DARK = (59, 64, 75, 255)
STEEL = (75, 80, 90, 255)
STEEL_LIGHT = (132, 138, 149, 255)
LIGHT_OFF_EDGE = (29, 42, 75, 255)
LIGHT_OFF = (44, 59, 94, 255)
LIGHT_ON_EDGE = (192, 74, 6, 255)
LIGHT_ON = (253, 115, 8, 255)
# The mix itself: a cold teal that is nobody's fluid in particular, so any pair of them reads as one.
VAT_DARK = (18, 34, 42, 255)
MIX_DEEP = (23, 76, 96, 255)
MIX = (36, 116, 142, 255)
MIX_LIGHT = (62, 158, 178, 255)
MIX_FOAM = (139, 203, 214, 255)
MIX_STILL = (30, 88, 104, 255)
MIX_STILL_TOP = (46, 118, 134, 255)

# Interior of the window frame: the vat, and the level the mix stands at.
INNER_X0, INNER_X1 = 6, 25
INNER_Y0, INNER_Y1 = 10, 25
SURFACE_Y = 16
SHAFT_X0, SHAFT_X1 = 15, 16
PADDLE_Y = 20


def fill(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image.putpixel((x, y), colour)


def status_light(image, on):
    edge, centre = (LIGHT_ON_EDGE, LIGHT_ON) if on else (LIGHT_OFF_EDGE, LIGHT_OFF)
    image.putpixel((12, 4), edge)
    image.putpixel((19, 4), edge)
    fill(image, 13, 4, 18, 4, centre)


def vat(image, on, frame, rng):
    """The pool behind the glass, its surface, and the paddle turning in it."""
    fill(image, INNER_X0, INNER_Y0, INNER_X1, INNER_Y1, VAT_DARK)
    body = MIX if on else MIX_STILL
    top = MIX_LIGHT if on else MIX_STILL_TOP
    fill(image, INNER_X0, SURFACE_Y, INNER_X1, INNER_Y1, body)
    fill(image, INNER_X0, INNER_Y1 - 2, INNER_X1, INNER_Y1, MIX_DEEP)
    surface(image, on, frame, top, rng)
    shaft(image, on)
    paddle(image, on, frame)


def surface(image, on, frame, top, rng):
    """A flat line at rest; while it works, a wave crosses it and foam breaks off the paddle."""
    for x in range(INNER_X0, INNER_X1 + 1):
        # A wave rather than a repeating step: on a 20 pixel surface a period of four reads as teeth.
        lift = 1 if on and math.sin((x + frame * 1.5) * 0.9) > 0.35 else 0
        fill(image, x, SURFACE_Y - lift, x, SURFACE_Y, top)
    if not on:
        return
    for _ in range(3):
        x = rng.randrange(INNER_X0 + 1, INNER_X1)
        image.putpixel((x, SURFACE_Y - 1), MIX_FOAM)


def shaft(image, on):
    """The rod the paddle hangs from, down from the top of the window into the mix."""
    fill(image, SHAFT_X0, INNER_Y0, SHAFT_X1, PADDLE_Y, STEEL if on else STEEL_DARK)
    fill(image, SHAFT_X0, INNER_Y0, SHAFT_X0, SURFACE_Y - 1, STEEL_LIGHT)


def paddle(image, on, frame):
    """
    The blade seen edge on: its half-width follows the turn, so it is widest across the window at
    the quarter turns and a single column when it points at the glass. Stopped, it rests half open.
    """
    # Never edge on to the glass: a blade that vanishes for a frame reads as a flicker, not a turn.
    reach = (1, 3, 5, 7, 7, 5, 3, 1)[frame] if on else 5
    colour = STEEL_LIGHT if on else STEEL
    left = max(INNER_X0, SHAFT_X0 - reach)
    right = min(INNER_X1, SHAFT_X1 + reach)
    fill(image, left, PADDLE_Y, right, PADDLE_Y + 1, colour)
    # The far edge of the blade catches the light one row up, which reads as tilt rather than a bar.
    fill(image, left, PADDLE_Y, left + 1, PADDLE_Y, STEEL_DARK)
    fill(image, right - 1, PADDLE_Y, right, PADDLE_Y, STEEL_DARK)


def front(on, frame, rng):
    image = Image.open(TEXTURES / "smeltery/smeltery_front.png").convert("RGBA")
    status_light(image, on)
    vat(image, on, frame, rng)
    return image


def textures():
    out = OUT
    out.mkdir(parents=True, exist_ok=True)
    front(False, 0, random.Random(0)).save(out / "mixer_front.png")
    sheet = Image.new("RGBA", (32, 32 * FRAMES))
    rng = random.Random(7)
    for frame in range(FRAMES):
        sheet.paste(front(True, frame, rng), (0, 32 * frame))
    sheet.save(out / "mixer_front_on.png")
    (out / "mixer_front_on.png.mcmeta").write_text(json.dumps(
        {"animation": {"width": 32, "height": 32, "frametime": FRAMETIME, "interpolate": False}}, indent=2) + "\n",
        encoding="utf-8")


if __name__ == "__main__":
    textures()
