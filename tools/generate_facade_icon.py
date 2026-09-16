"""Draw the facade item icon: a steel panel with a cut corner showing the cable it hides.

Colours come from the machines' own side texture, so the item sits in the same palette as the
blocks it covers. Requires Pillow.
"""
from collections import Counter
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / 'src/main/resources/assets/futuretech/textures'
MACHINE = TEXTURES / 'block/machine/machine_side.png'
CABLE = TEXTURES / 'block/cable_mk1/cable_mk1.png'


def fill(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image.putpixel((x, y), colour)


def main():
    machine = Image.open(MACHINE).convert('RGB')
    palette = Counter(machine.get_flattened_data())
    body = (*palette.most_common(1)[0][0], 255)
    outline = (*min(palette, key=lambda c: sum(c)), 255)
    highlight = (*max(palette, key=lambda c: sum(c)), 255)
    cable = Image.open(CABLE).convert('RGB') if CABLE.exists() else machine
    wire = (*Counter(cable.get_flattened_data()).most_common(1)[0][0], 255)

    icon = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    # The cable runs behind the panel, showing at the corner the panel leaves open.
    fill(icon, 9, 1, 14, 3, wire)
    fill(icon, 12, 1, 14, 6, wire)
    # The panel itself, a plate with a lit top-left edge and a dark rim.
    fill(icon, 1, 4, 12, 14, outline)
    fill(icon, 2, 5, 11, 13, body)
    fill(icon, 2, 5, 11, 5, highlight)
    fill(icon, 2, 5, 2, 13, highlight)
    # Four rivets, the same ones the frames wear.
    for x, y in ((4, 7), (9, 7), (4, 11), (9, 11)):
        icon.putpixel((x, y), outline)

    path = TEXTURES / 'item/facade.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    icon.save(path)
    print(f'gravado {path.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
