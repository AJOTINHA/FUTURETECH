"""Desenha o miolo do Tesseract e escreve o modelo do item.

No mundo, o cubo do meio é o portal do End de verdade, desenhado pelo renderer. No inventário o
item não passa por renderer, então o ícone leva um cubo com uma textura parada que lembra o
portal: o céu do End, azul quase preto com pontos de luz, gerada aqui com uma semente fixa para o
ícone ser sempre o mesmo. A moldura é a cópia da bateria que mora na pasta do tesseract.

    python tools/generate_tesseract.py
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/futuretech'
TEXTURES = ASSETS / 'textures/block'
NAME = 'tesseract'
# Onde o cubo encosta nas barras da moldura: elas vão de 0,25 a 2,75.
INNER = 2.75
OUTER = 16 - INNER

SKY = (8, 10, 20, 255)
STARS = [(40, 70, 110, 255), (60, 110, 140, 255), (120, 160, 190, 255), (90, 60, 130, 255)]


def draw_core():
    im = Image.new('RGBA', (32, 32), SKY)
    rng = random.Random(0x7E55E)
    for _ in range(90):
        x, y = rng.randrange(32), rng.randrange(32)
        im.putpixel((x, y), rng.choice(STARS))
    return im


def write_json(relative, value):
    path = ASSETS / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    print(f'  {relative}')


def main():
    folder = TEXTURES / NAME
    folder.mkdir(parents=True, exist_ok=True)
    draw_core().save(folder / f'{NAME}_core.png')
    print(f'  textures/block/{NAME}/{NAME}_core.png')

    # O ícone: a moldura inteira, mais o cubo do meio com o céu do End.
    frame = json.loads((ASSETS / f'models/block/{NAME}/frame.json').read_text(encoding='utf-8'))
    core = {
        'name': 'core',
        'from': [INNER, INNER, INNER],
        'to': [OUTER, OUTER, OUTER],
        'faces': {face: {'texture': '#core', 'uv': [0, 0, 16, 16]} for face in
                  ('north', 'south', 'east', 'west', 'up', 'down')},
    }
    write_json(f'models/item/{NAME}.json', {
        'parent': 'minecraft:block/block',
        'textures': dict(frame['textures'], core=f'futuretech:block/{NAME}/{NAME}_core'),
        'elements': frame['elements'] + [core],
    })


if __name__ == '__main__':
    print('tesseract:')
    main()
    print('pronto.')
