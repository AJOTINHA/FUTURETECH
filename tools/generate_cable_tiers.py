"""Draw the core sprites of every tier of the energy cable, and of the item cable, and write
the block files of the tiers past MK1.

The patches are the ones art/cable_mk1/Build-Cable.ps1 authors for the energy cable, so every
cable in the mod shares one pattern and differs only in its four-step ramp: the core wears the
MK's colour — white, yellow, red, cyan, the colours of the battery's lines and the machines'
corners — and the item cable wears grey. Every patch is edge-padded to the sprite's full size:
the padding repeats the nearest real pixel, so mipmaps never blend filler into the cable.

MK2 to MK4 are the MK1 models with their own core textures, the same multipart renamed, a loot
table, and a recipe of eight cables of the tier below around that MK's upgrade kit. Requires
Pillow.
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/futuretech'
DATA = ROOT / 'src/main/resources/data/futuretech'

# Base, tone, core and light, darkest to brightest. MK1 is white like the battery's lines;
# MK2 and MK4 ramp around the machines' corner colours (#F2C202, #01FDFE); MK3 is charcoal
# insulation with a thin lit red line and its dark red halo, a wire rather than a flat red,
# which keeps it apart from the redstone cable; the item cable is a neutral grey.
RAMPS = {
    'cable_mk1': ((142, 147, 152), (181, 186, 191), (228, 231, 234), (255, 255, 255)),
    'cable_mk2': ((122, 90, 0), (185, 143, 2), (242, 194, 2), (255, 229, 122)),
    'cable_mk3': ((28, 30, 34), (92, 14, 14), (200, 20, 20), (255, 60, 60)),
    'cable_mk4': ((37, 95, 146), (56, 148, 175), (88, 196, 196), (121, 229, 217)),
    'item_cable': ((60, 64, 68), (91, 97, 102), (138, 144, 150), (180, 186, 192)),
}

# Six texels across the six-unit core, four-texel bands along its length. Thirty-two rows are
# exactly four periods, so consecutive blocks continue the pattern. MK3 keeps its lit line
# thin — one or two texels — over the dark core.
STRIP = ['BTCLTB' if y // 4 % 2 == 0 else 'BBLCBB' for y in range(32)]
THIN_STRIP = ['BBTLTB' if y // 4 % 2 == 0 else 'BBLTBB' for y in range(32)]
STRIPS = {'cable_mk3': THIN_STRIP}
# The node's open faces show a small central contact.
NODE = ['TTTBBB', 'TTTBBB', 'TTCLBB', 'BBLCTT', 'BBBTTT', 'BBBTTT']
# The same contact at eight texels, for the plate that closes the cable's mouth in a collar.
CONTACT = ['TTTTBBBB', 'TTTTBBBB', 'TTTTBBBB', 'TTTCLBBB',
           'BBBLCTTT', 'BBBBTTTT', 'BBBBTTTT', 'BBBBTTTT']


def save_sprite(path, size, rows, ramp):
    palette = dict(zip('BTCL', ramp))
    image = Image.new('RGBA', (size, size))
    for y in range(size):
        row = rows[min(y, len(rows) - 1)]
        for x in range(size):
            image.putpixel((x, y), palette[row[min(x, len(row) - 1)]] + (255,))
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)
    print(f'  {path.relative_to(ROOT)}')


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8', newline='\n')
    print(f'  {path.relative_to(ROOT)}')


def sprites():
    for tier in ('cable_mk1', 'cable_mk2', 'cable_mk3', 'cable_mk4'):
        folder = ASSETS / 'textures/block' / tier
        save_sprite(folder / f'{tier}.png', 32, STRIPS.get(tier, STRIP), RAMPS[tier])
        save_sprite(folder / f'{tier}_node.png', 16, NODE, RAMPS[tier])
        # The connector draws it per block, so it carries no tier in its name.
        save_sprite(folder / 'cable_contact.png', 16, CONTACT, RAMPS[tier])
    folder = ASSETS / 'textures/block/item_cable'
    save_sprite(folder / 'item_cable_opaque.png', 32, STRIP, RAMPS['item_cable'])
    save_sprite(folder / 'item_cable_opaque_node.png', 16, NODE, RAMPS['item_cable'])
    save_sprite(folder / 'item_cable_contact.png', 16, CONTACT, RAMPS['item_cable'])


def tier_files(mk):
    tier = f'cable_mk{mk}'
    textures = {'cable': f'futuretech:block/{tier}/{tier}', 'node': f'futuretech:block/{tier}/{tier}_node'}
    for part in ('arm', 'cap', 'line', 'node'):
        write_json(ASSETS / f'models/block/{tier}_{part}.json',
                   {'parent': f'futuretech:block/cable_mk1_{part}', 'textures': textures})
    write_json(ASSETS / f'models/item/{tier}.json', {'parent': 'futuretech:item/cable_mk1', 'textures': textures})
    write_json(ASSETS / f'items/{tier}.json', {'model': {'type': 'minecraft:model', 'model': f'futuretech:item/{tier}'}})
    # The same multipart renamed, byte for byte, which is what the resources test checks.
    blockstate = (ASSETS / 'blockstates/cable_mk1.json').read_text(encoding='utf-8')
    target = ASSETS / f'blockstates/{tier}.json'
    target.write_text(blockstate.replace('futuretech:block/cable_mk1_', f'futuretech:block/{tier}_'), encoding='utf-8', newline='')
    print(f'  {target.relative_to(ROOT)}')
    write_json(DATA / f'loot_table/blocks/{tier}.json', {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'futuretech:{tier}'}],
                   'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    below = f'futuretech:cable_mk{mk - 1}'
    kit = f'futuretech:upgrade_kit_mk{mk}'
    write_json(DATA / f'recipe/{tier}.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc',
        'pattern': ['CCC', 'CKC', 'CCC'], 'key': {'C': below, 'K': kit},
        'result': {'id': f'futuretech:{tier}', 'count': 8}})
    write_json(DATA / f'advancement/recipes/{tier}.json', {
        'parent': 'minecraft:recipes/root',
        'criteria': {
            'has_kit': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': kit}]}},
            'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': f'futuretech:{tier}'}}},
        'requirements': [['has_kit', 'has_the_recipe']],
        'rewards': {'recipes': [f'futuretech:{tier}']}})


def main():
    sprites()
    for mk in (2, 3, 4):
        tier_files(mk)


if __name__ == '__main__':
    main()
