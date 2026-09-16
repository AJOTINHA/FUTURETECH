"""Icons of the portable batteries: a graphite power pack with a copper terminal and cyan charge bars.

MK2, MK3 and MK4 carry the machines' MK colours (yellow, red, cyan) on the terminal and the top band,
the same accents the upgraded machines and battery blocks wear.

Run from any directory: python tools/generate_portable_battery.py
"""
from pathlib import Path

from PIL import Image

TEXTURES = Path(__file__).resolve().parents[1] / "src/main/resources/assets/futuretech/textures/item"

OUTLINE = (17, 18, 19, 255)
BODY = (75, 80, 90, 255)
BODY_LIGHT = (123, 129, 140, 255)
BODY_DARK = (52, 58, 70, 255)
COPPER = (196, 116, 70, 255)
COPPER_LIGHT = (236, 156, 100, 255)
CYAN = (85, 231, 237, 255)
CYAN_DARK = (22, 118, 196, 255)
WINDOW = (26, 29, 34, 255)


def fill(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image.putpixel((x, y), colour)


# The MK accents of the machines' corners: MK1 keeps copper.
ACCENTS = {1: (COPPER_LIGHT, COPPER), 2: ((253, 204, 2, 255), (209, 170, 2, 255)),
           3: ((238, 60, 64, 255), (176, 30, 34, 255)), 4: ((85, 231, 237, 255), (22, 118, 196, 255))}


def icon(mk=1):
    light, dark = ACCENTS[mk]
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # Terminal on top, then the pack with cut corners.
    fill(image, 6, 0, 9, 1, OUTLINE)
    fill(image, 7, 0, 8, 0, light)
    fill(image, 7, 1, 8, 1, dark)
    fill(image, 3, 2, 12, 15, OUTLINE)
    fill(image, 4, 3, 11, 14, BODY)
    for x, y in ((3, 2), (12, 2), (3, 15), (12, 15)):
        image.putpixel((x, y), (0, 0, 0, 0))
    for x, y in ((4, 3), (11, 3), (4, 14), (11, 14)):
        image.putpixel((x, y), OUTLINE)
    # Light catches the left edge; the right edge falls into shadow.
    fill(image, 5, 4, 5, 13, BODY_LIGHT)
    fill(image, 10, 4, 10, 13, BODY_DARK)
    fill(image, 5, 4, 10, 4, BODY_LIGHT if mk == 1 else dark)
    # Level pips under the window: one per MK above the first.
    for pip in range(mk - 1):
        image.putpixel((6 + pip * 2, 14), light)
    # A dark window with three charge bars, the top one brightest.
    fill(image, 6, 6, 9, 13, WINDOW)
    fill(image, 7, 7, 8, 8, CYAN)
    fill(image, 7, 9, 8, 10, CYAN)
    fill(image, 7, 11, 8, 12, CYAN_DARK)
    return image


if __name__ == "__main__":
    TEXTURES.mkdir(parents=True, exist_ok=True)
    for mk in range(1, 5):
        name = "portable_battery" if mk == 1 else f"portable_battery_mk{mk}"
        icon(mk).save(TEXTURES / f"{name}.png")
    print("Saved the four portable battery icons in", TEXTURES)
