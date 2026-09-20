"""Icon of the portable teleporter: a wide graphite handset with an antenna off one corner, a round
ender-green eye in the middle and two keys under it; nothing of the portable battery's pack shape.

Run from any directory: python tools/generate_portable_teleporter.py
"""
from pathlib import Path

from PIL import Image

TEXTURES = Path(__file__).resolve().parents[1] / "src/main/resources/assets/futuretech/textures/item"

OUTLINE = (17, 18, 19, 255)
BODY = (75, 80, 90, 255)
BODY_LIGHT = (123, 129, 140, 255)
BODY_DARK = (52, 58, 70, 255)
CYAN = (85, 231, 237, 255)
CYAN_DARK = (22, 118, 196, 255)
SCREEN = (16, 40, 34, 255)
ENDER = (58, 206, 139, 255)
ENDER_DARK = (24, 120, 82, 255)


def fill(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image.putpixel((x, y), colour)


def icon():
    """A handset held upright: an antenna off the top-left corner, a wide dark body with cut
    corners, a round ender-green eye in the middle of it and two keys under the eye."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # The antenna: a stalk leaning out to the top-left, with a cyan tip.
    for x, y in ((4, 3), (3, 2), (2, 1)):
        image.putpixel((x, y), OUTLINE)
    image.putpixel((2, 0), CYAN)
    image.putpixel((1, 1), CYAN_DARK)
    # The body, wide and short, corners cut.
    fill(image, 2, 4, 13, 15, OUTLINE)
    fill(image, 3, 5, 12, 14, BODY)
    for x, y in ((2, 4), (13, 4), (2, 15), (13, 15)):
        image.putpixel((x, y), (0, 0, 0, 0))
    for x, y in ((3, 5), (12, 5), (3, 14), (12, 14)):
        image.putpixel((x, y), OUTLINE)
    fill(image, 4, 6, 4, 13, BODY_LIGHT)
    fill(image, 11, 6, 11, 13, BODY_DARK)
    fill(image, 4, 6, 11, 6, BODY_LIGHT)
    # The eye: a dark round glass, a ring of ender green, a bright centre with a glint.
    fill(image, 6, 7, 9, 10, SCREEN)
    for x, y in ((5, 8), (5, 9), (10, 8), (10, 9), (7, 6), (8, 6), (7, 11), (8, 11)):
        image.putpixel((x, y), SCREEN)
    for x, y in ((6, 7), (9, 7), (6, 10), (9, 10)):
        image.putpixel((x, y), ENDER_DARK)
    fill(image, 7, 7, 8, 7, ENDER_DARK)
    fill(image, 7, 10, 8, 10, ENDER_DARK)
    fill(image, 6, 8, 6, 9, ENDER_DARK)
    fill(image, 9, 8, 9, 9, ENDER_DARK)
    fill(image, 7, 8, 8, 9, ENDER)
    image.putpixel((7, 8), (140, 240, 200, 255))
    # Two keys under the eye: cyan to go, grey to save.
    fill(image, 5, 13, 7, 13, CYAN_DARK)
    fill(image, 8, 13, 10, 13, BODY_DARK)
    return image


if __name__ == "__main__":
    TEXTURES.mkdir(parents=True, exist_ok=True)
    icon().save(TEXTURES / "portable_teleporter.png")
    print("  textures/item/portable_teleporter.png")
