"""Draw the redstone cable's three core sprites: the strip, the node face and the contact.

The patches are the ones art/cable_mk1/Build-Cable.ps1 authors for the energy cable, so every
cable in the mod shares one pattern and differs only in its four-step ramp. This one is red.
Every patch is edge-padded to the sprite's full size: the padding repeats the nearest real pixel,
so mipmaps never blend filler into the cable. Requires Pillow.
"""
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
FOLDER = ROOT / 'src/main/resources/assets/futuretech/textures/block/redstone_cable'

# Base, tone, core and light, darkest to brightest, like the purple of the network cable: the
# machine red the corners of the MK3 machines wear, in the same bands as every other cable.
PALETTE = {
    'B': (94, 11, 13, 255),
    'T': (150, 18, 22, 255),
    'C': (210, 26, 30, 255),
    'L': (255, 122, 110, 255),
}

# Six texels across the six-unit core, four-texel bands along its length. Thirty-two rows are
# exactly four periods, so consecutive blocks continue the pattern.
STRIP = ['BTCLTB' if y // 4 % 2 == 0 else 'BBLCBB' for y in range(32)]
# The node's open faces show a small central contact.
NODE = ['TTTBBB', 'TTTBBB', 'TTCLBB', 'BBLCTT', 'BBBTTT', 'BBBTTT']
# The same contact at eight texels, for the plate that closes the cable's mouth in a collar.
CONTACT = ['TTTTBBBB', 'TTTTBBBB', 'TTTTBBBB', 'TTTCLBBB',
           'BBBLCTTT', 'BBBBTTTT', 'BBBBTTTT', 'BBBBTTTT']


def save(name, size, rows):
    image = Image.new('RGBA', (size, size))
    for y in range(size):
        row = rows[min(y, len(rows) - 1)]
        for x in range(size):
            image.putpixel((x, y), PALETTE[row[min(x, len(row) - 1)]])
    path = FOLDER / name
    image.save(path)
    print(f'  {path.relative_to(ROOT)}')


def main():
    FOLDER.mkdir(parents=True, exist_ok=True)
    save('redstone_cable.png', 32, STRIP)
    save('redstone_cable_node.png', 16, NODE)
    save('redstone_cable_contact.png', 16, CONTACT)


if __name__ == '__main__':
    main()
