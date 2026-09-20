"""Desenha a frente do Storage Cards e escreve o modelo, o blockstate e o item.

O banco é um cubo com a carcaça das máquinas (`machine/machine_side.png`, com as quinas coloridas
de cada MK acima do primeiro) e uma frente própria:
uma porta escura com uma estante de cartões atrás do vidro, três prateleiras de três cartões,
cada cartão com a rampa roxa do cabo de rede, para os dois lerem como uma coisa só.

    python tools/generate_storage_cards.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/futuretech'
TEXTURES = ASSETS / 'textures/block'
NAME = 'storage_cards'

# A mesma rampa roxa do cabo de rede e do painel.
DARK = (69, 33, 104, 255)
MID = (106, 52, 160, 255)
CORE = (168, 85, 214, 255)
LIGHT = (210, 166, 240, 255)
GLASS = (26, 14, 38, 255)
BEZEL = (36, 40, 46, 255)
BEZEL_EDGE = (59, 64, 75, 255)
SHELF = (48, 52, 60, 255)


def casing(mk=1):
    """The machines' casing of that level: MK1 plain, the others with their coloured corners."""
    folder = 'machine' if mk == 1 else f'machine/mk{mk}'
    return Image.open(TEXTURES / folder / 'machine_side.png').convert('RGBA').copy()


def front_path(mk):
    """Where a level's front lives, the way the teleporter's tops are laid out."""
    return f'{NAME}/{NAME}_front' if mk == 1 else f'{NAME}/mk{mk}/{NAME}_front'


def box(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image.putpixel((x, y), colour)


def draw_front(mk):
    """A porta: moldura escura, vidro quase preto e três prateleiras de três cartões, na carcaça do MK."""
    im = casing(mk)
    box(im, 4, 4, 27, 27, BEZEL_EDGE)
    box(im, 5, 5, 26, 26, BEZEL)
    box(im, 6, 6, 25, 25, GLASS)
    for shelf in range(3):
        top = 8 + shelf * 6
        # A prateleira em si, um fio de metal sob os cartões.
        box(im, 7, top + 4, 24, top + 4, SHELF)
        for column in range(3):
            left = 8 + column * 6
            # O cartão: corpo roxo com a faixa clara em cima, como o item.
            box(im, left, top, left + 3, top + 3, MID)
            box(im, left, top, left + 3, top, LIGHT)
            box(im, left + 1, top + 2, left + 2, top + 2, CORE)
            box(im, left + 3, top + 1, left + 3, top + 3, DARK)
    return im


def write_json(relative, value):
    path = ASSETS / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    print(f'  {relative}')


def main():
    for mk in range(1, 5):
        path = TEXTURES / f'{front_path(mk)}.png'
        path.parent.mkdir(parents=True, exist_ok=True)
        draw_front(mk).save(path)
    print(f'  textures/block/{NAME}/ (uma frente por MK)')

    # Um modelo por MK: a carcaça e a frente trazem as quinas coloridas do nível, como as outras
    # máquinas. O item é o MK1.
    faces = {}
    for face in ('up', 'down', 'east', 'west', 'south'):
        faces[face] = {'uv': [0, 0, 16, 16], 'texture': '#side', 'cullface': face}
    faces['north'] = {'uv': [0, 0, 16, 16], 'texture': '#front', 'cullface': 'north'}
    for mk in range(1, 5):
        side = 'futuretech:block/machine/machine_side' if mk == 1 else f'futuretech:block/machine/mk{mk}/machine_side'
        write_json(f'models/block/{NAME}/mk{mk}.json', {
            'parent': 'minecraft:block/block',
            'textures': {'front': f'futuretech:block/{front_path(mk)}', 'side': side, 'particle': side},
            'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces}],
        })
    turn = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    variants = {}
    for facing, y in turn.items():
        for mk in range(1, 5):
            variants[f'facing={facing},mk={mk}'] = {'model': f'futuretech:block/{NAME}/mk{mk}', 'y': y}
    write_json(f'blockstates/{NAME}.json', {'variants': variants})
    write_json(f'models/item/{NAME}.json', {'parent': f'futuretech:block/{NAME}/mk1'})
    write_json(f'items/{NAME}.json', {
        'model': {'type': 'minecraft:model', 'model': f'futuretech:item/{NAME}'}
    })


if __name__ == '__main__':
    print('storage cards:')
    main()
    print('pronto.')
