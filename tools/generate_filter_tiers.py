"""Draw the MK2 to MK4 filter cards and write their models, items, recipes and advancements.

A card of every MK is the same card: the same steel frame, the same clip. What changes is the
mesh, and the marker under it, which climb the mod's own MK scale — the battery's yellow, red and
cyan — so a card reads at a glance the way a battery or a cable does. The mesh is what shows of a
card in a slot, which is why it carries the colour and not just the marker. Each part is
recoloured by brightness rather than replaced flat, so the shading drawn into the MK1 is still
there underneath the new colour.

A card is crafted by putting the matching upgrade kit on the card below it, which is how every
other tier in the mod is raised. It is a transmute, so the list, the mode and the settings on the
card come through the upgrade untouched. Requires Pillow.
"""
import json
import shutil
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/futuretech'
DATA = RES / 'data/futuretech'

# The scale the batteries set and the cables followed; the MK1 keeps the silver and cyan it was drawn with.
ACCENTS = {2: (0xF2, 0xC2, 0x02), 3: (0xD2, 0x1A, 0x1E), 4: (0x01, 0xFD, 0xFE)}
# The quadrants of the materials texture that take the colour: the mesh the model calls "silver",
# bottom left of the four, and the marker it calls "cyan", bottom right.
COLOURED_BOXES = ((0, 16, 16, 32), (16, 16, 32, 32))


def luma(colour):
    return 0.299 * colour[0] + 0.587 * colour[1] + 0.114 * colour[2]


def recoloured(pixel, accent, brightest):
    """The pixel's own brightness, in the new colour: keeps every bevel the MK1 had."""
    ratio = luma(pixel) / brightest
    return tuple(min(255, round(channel * ratio)) for channel in accent) + (pixel[3],)


def opaque(image, box):
    for x in range(box[0], box[2]):
        for y in range(box[1], box[3]):
            pixel = image.getpixel((x, y))
            if pixel[3] != 0:
                yield x, y, pixel


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8', newline='\n')
    print(f'  {path.relative_to(ROOT)}')


def texture(mk):
    source = ASSETS / 'textures/item/filter_materials.png'
    image = Image.open(source).convert('RGBA')
    accent = ACCENTS[mk]
    for box in COLOURED_BOXES:
        # The brightest pixel of the part wears the colour as it is; the rest shade down from it.
        brightest = max(luma(pixel) for _, _, pixel in opaque(image, box))
        for x, y, pixel in list(opaque(image, box)):
            image.putpixel((x, y), recoloured(pixel, accent, brightest))
    path = ASSETS / f'textures/item/filter_mk{mk}_materials.png'
    image.save(path)
    print(f'  {path.relative_to(ROOT)}')


def model(mk):
    card = json.loads((ASSETS / 'models/item/filter.json').read_text(encoding='utf-8'))
    card['textures'] = {name: f'futuretech:item/filter_mk{mk}_materials' for name in card['textures']}
    write(ASSETS / f'models/item/filter_mk{mk}.json', card)
    write(ASSETS / f'items/filter_mk{mk}.json', {
        'model': {'type': 'minecraft:model', 'model': f'futuretech:item/filter_mk{mk}'},
    })


def data(mk):
    below = 'futuretech:filter' if mk == 2 else f'futuretech:filter_mk{mk - 1}'
    kit = f'futuretech:upgrade_kit_mk{mk}'
    name = f'filter_mk{mk}'
    write(DATA / f'recipe/{name}.json', {
        'type': 'minecraft:crafting_transmute',
        'category': 'equipment',
        'input': below,
        'material': kit,
        'result': {'id': f'futuretech:{name}'},
    })
    write(DATA / f'advancement/recipes/{name}.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {
            'has_kit': {'trigger': 'minecraft:inventory_changed',
                        'conditions': {'items': [{'items': kit}]}},
            'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked',
                               'conditions': {'recipe': f'futuretech:{name}'}}},
        'requirements': [['has_kit', 'has_the_recipe']],
        'rewards': {'recipes': [f'futuretech:{name}']},
    })


if __name__ == '__main__':
    for mk in (2, 3, 4):
        texture(mk)
        model(mk)
        data(mk)
