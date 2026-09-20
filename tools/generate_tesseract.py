"""Escreve o modelo e a definição do item do Tesseract.

O cubo do meio é o portal do End de verdade, desenhado por renderer: no mundo pelo do bloco e no
item por um "special model" registrado no cliente, então o ícone, a mão e o chão mostram o mesmo
portal animado. O modelo do item é só a moldura — a cópia da bateria que mora na pasta do
tesseract — e a definição do item compõe a moldura com o renderer do miolo.

    python tools/generate_tesseract.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/futuretech'
TEXTURES = ASSETS / 'textures/block'
NAME = 'tesseract'
# Onde o cubo encosta nas barras da moldura: elas vão de 0,25 a 2,75.
INNER = 2.75
OUTER = 16 - INNER

def write_json(relative, value):
    path = ASSETS / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    print(f'  {relative}')


def main():
    frame = json.loads((ASSETS / f'models/block/{NAME}/frame.json').read_text(encoding='utf-8'))
    # O ícone: a moldura inteira; o miolo é do renderer, pela definição do item.
    write_json(f'models/item/{NAME}.json', {
        'parent': 'minecraft:block/block',
        'textures': frame['textures'],
        'elements': frame['elements'],
    })
    write_json(f'items/{NAME}.json', {
        'model': {
            'type': 'minecraft:composite',
            'models': [
                {'type': 'minecraft:model', 'model': f'futuretech:item/{NAME}'},
                {'type': 'minecraft:special', 'base': f'futuretech:item/{NAME}',
                 'model': {'type': f'futuretech:{NAME}_core'}},
            ],
        }
    })


if __name__ == '__main__':
    print('tesseract:')
    main()
    print('pronto.')
