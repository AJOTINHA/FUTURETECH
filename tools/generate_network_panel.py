"""Desenha as texturas do Painel de Rede e escreve o modelo, o blockstate e o item.

O painel é uma placa fina montada na face de um bloco, não um cubo: ela ocupa só os dois pixels
colados nele, que é onde o braço do cabo termina, então os dois se encostam sem vão. A carcaça é a
das máquinas (`machine/machine_side.png`) e a frente é uma tela roxa, na rampa do cabo de rede,
com quatro linhas de lista; a linha acesa desce e volta ao topo, como um painel relendo a rede.

    python tools/generate_network_panel.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/futuretech'
TEXTURES = ASSETS / 'textures/block'
NAME = 'network_panel'
# Espessura da placa, em pixels: o quanto ela se destaca do bloco em que está montada.
THICKNESS = 2

# A mesma rampa roxa do cabo de rede, para os dois lerem como uma coisa só.
DARK = (69, 33, 104, 255)
MID = (106, 52, 160, 255)
CORE = (168, 85, 214, 255)
LIGHT = (210, 166, 240, 255)
GLASS = (26, 14, 38, 255)
BEZEL = (36, 40, 46, 255)
BEZEL_EDGE = (59, 64, 75, 255)


def casing():
    return Image.open(TEXTURES / 'machine/machine_side.png').convert('RGBA').copy()


def box(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image.putpixel((x, y), colour)


ROWS = 4
FRAMES = ROWS * 2


def draw_front(frame):
    """A tela: moldura escura, vidro quase preto e quatro linhas de lista.

    A linha acesa desce a lista e volta ao topo, como um painel relendo a rede. Cada linha fica
    acesa por dois quadros, então uma volta inteira leva os oito.
    """
    im = casing()
    box(im, 4, 4, 27, 27, BEZEL_EDGE)
    box(im, 5, 5, 26, 26, BEZEL)
    box(im, 6, 6, 25, 25, GLASS)
    lit = frame // 2
    for index, top in enumerate(range(8, 24, 4)):
        on = index == lit
        box(im, 8, top, 21, top + 1, LIGHT if on else MID)
        # O marcador à direita de cada linha, aceso só na que está sendo lida.
        box(im, 22, top, 23, top + 1, CORE if on else DARK)
    return im


def write_json(relative, value):
    path = ASSETS / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    print(f'  {relative}')


def main():
    folder = TEXTURES / NAME
    folder.mkdir(parents=True, exist_ok=True)
    sheet = Image.new('RGBA', (32, 32 * FRAMES))
    for frame in range(FRAMES):
        sheet.paste(draw_front(frame), (0, frame * 32))
    sheet.save(folder / f'{NAME}_front.png')
    meta = {'animation': {'width': 32, 'height': 32, 'frametime': 3, 'interpolate': False}}
    (folder / f'{NAME}_front.png.mcmeta').write_text(json.dumps(meta, indent=2) + '\n', encoding='utf-8')
    print(f'  textures/block/{NAME}/ ({FRAMES} quadros na frente)')

    side = 'futuretech:block/machine/machine_side'
    # Uma placa fina, não um cubo: ela ocupa os dois pixels colados no bloco em que foi montada,
    # que é onde o braço do cabo termina. O modelo é desenhado virado para o norte e o blockstate
    # o gira para as outras cinco faces.
    faces = {}
    # The four edges are two pixels deep. Left to itself the game derives their UV from the box, so
    # those two pixels come from two pixels of the casing — its own dark border, at its own size.
    # Naming a UV here meant squashing fourteen rows of casing into two, which is what smeared them.
    for face in ('up', 'down', 'east', 'west'):
        faces[face] = {'texture': '#side'}
    faces['north'] = {'uv': [0, 0, 16, 16], 'texture': '#front'}
    faces['south'] = {'uv': [0, 0, 16, 16], 'texture': '#side', 'cullface': 'south'}
    write_json(f'models/block/{NAME}.json', {
        # block/block carries no geometry, only the standard display transforms. Without a parent
        # the item has none at all, and an item with no transform is drawn at full block size:
        # that is why the panel filled the hand.
        'parent': 'minecraft:block/block',
        'textures': {
            'front': f'futuretech:block/{NAME}/{NAME}_front',
            'side': side,
            'particle': side,
        },
        'elements': [{'from': [0, 0, 16 - THICKNESS], 'to': [16, 16, 16], 'faces': faces}],
    })
    turn = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270},
            'up': {'x': 270}, 'down': {'x': 90}}
    variants = {}
    for facing, rotation in turn.items():
        variant = {'model': f'futuretech:block/{NAME}'}
        variant.update(rotation)
        variants[f'facing={facing}'] = variant
    write_json(f'blockstates/{NAME}.json', {'variants': variants})
    # Toda posição é declarada aqui, não só as duas que mudam: nesta versão o display do filho
    # substitui o do pai inteiro, então herdar block/block e escrever só 'gui' apagava as medidas
    # de mão e o painel era empunhado no tamanho de um bloco. As de mão, chão e prateleira são as
    # do próprio block/block, copiadas; as outras duas são as que o painel precisa.
    #
    # No inventário o painel é a vista isométrica do block/block, a mesma das máquinas: a tela
    # está na face norte, que é a face da esquerda nessa vista. Como a placa mora no fundo do
    # cubo, o centro dela cai fora do centro do slot: sete pixels de z, girados de 225° e 30° e
    # reduzidos a 0,625, viram uns três pixels para a esquerda e um e meio para cima, que a
    # translação devolve. A meia-volta no quadro e na prateleira é porque ali a câmera olha a
    # face sul: sem ela seriam as costas da placa.
    write_json(f'models/item/{NAME}.json', {
        'parent': f'futuretech:block/{NAME}',
        'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [3, -1.5, 0], 'scale': [0.625, 0.625, 0.625]},
            'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.5, 0.5, 0.5]},
            'on_shelf': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [1.0, 1.0, 1.0]},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.25, 0.25, 0.25]},
            'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0],
                                      'scale': [0.375, 0.375, 0.375]},
            'thirdperson_lefthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0],
                                     'scale': [0.375, 0.375, 0.375]},
            'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 0, 0],
                                      'scale': [0.4, 0.4, 0.4]},
            'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 0, 0],
                                     'scale': [0.4, 0.4, 0.4]},
        },
    })
    write_json(f'items/{NAME}.json', {
        'model': {'type': 'minecraft:model', 'model': f'futuretech:item/{NAME}'}
    })


if __name__ == '__main__':
    print('painel de rede:')
    main()
    print('pronto.')
