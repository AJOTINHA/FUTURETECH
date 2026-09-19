"""Texture of the Efficiency Upgrade: the Speed Upgrade's module with a green bolt on its panel.

The frame, the panel and the pins are copied from speed_upgrade.png so the two modules match on
every side; only the panel's symbol changes, the cyan chevrons giving way to a lightning bolt in
two greens, the colour the upgrade tab could reuse for anything that saves energy.

Run from any directory: python tools/generate_efficiency_upgrade.py
"""
from pathlib import Path

from PIL import Image

TEXTURES = Path(__file__).resolve().parents[1] / "src/main/resources/assets/futuretech/textures/item"

PANEL = (6, 30, 62, 255)
GREEN = (98, 245, 110, 255)
GREEN_DARK = (28, 160, 70, 255)

# The panel's inner face, where the speed upgrade draws its chevrons.
PANEL_LEFT, PANEL_TOP, PANEL_RIGHT, PANEL_BOTTOM = 5, 7, 26, 23

# A bolt over the 22 x 17 panel: the upper stroke comes down to the arm, the lower one tapers to a point.
BOLT = [
    "......................",
    ".........LLLLLL.......",
    "........LLLLLD........",
    ".......LLLLLD.........",
    "......LLLLLD..........",
    ".....LLLLLLLLLLLD.....",
    "......DDDDLLLLLD......",
    ".........LLLLLD.......",
    "........LLLLD.........",
    ".......LLLD...........",
    "......LLD.............",
    ".....LD...............",
    "....D.................",
    "......................",
]


def texture():
    image = Image.open(TEXTURES / "speed_upgrade.png").convert("RGBA")
    for y in range(PANEL_TOP, PANEL_BOTTOM + 1):
        for x in range(PANEL_LEFT, PANEL_RIGHT + 1):
            image.putpixel((x, y), PANEL)
    for row, line in enumerate(BOLT):
        for column, cell in enumerate(line):
            if cell == ".":
                continue
            image.putpixel((PANEL_LEFT + column, PANEL_TOP + 1 + row), GREEN if cell == "L" else GREEN_DARK)
    return image


if __name__ == "__main__":
    texture().save(TEXTURES / "efficiency_upgrade.png")
