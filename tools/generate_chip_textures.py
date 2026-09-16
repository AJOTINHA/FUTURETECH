"""Draw the chip's dedicated 32x32 pixel-art materials with Pillow."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'src/main/resources/assets/futuretech/textures/item/chip'


def save(name, image):
    OUT.mkdir(parents=True, exist_ok=True)
    image.save(OUT / f'{name}.png')


def main():
    pcb = Image.new('RGBA', (32, 32), '#143a49')
    d = ImageDraw.Draw(pcb)
    d.rectangle((0, 0, 31, 31), outline='#0b2633')
    d.line((1, 30, 1, 1, 30, 1), fill='#285264')
    d.line((2, 30, 30, 30, 30, 2), fill='#102e3b')
    # Actual printed copper runs: six contacts per side, with short 45-degree bends.
    traces = Image.new('RGBA', (32, 32))
    t = ImageDraw.Draw(traces)
    for x, finish in ((4, 7), (8, 9), (13, 13), (18, 18), (23, 22), (27, 24)):
        t.line([(x, 1), (x, 3), (finish, 6), (finish, 10)], fill='#a77748')
        t.line((x, 1, x, 2), fill='#dec18b')
    for rotation in range(4):
        pcb.alpha_composite(traces.rotate(90 * rotation))
    d = ImageDraw.Draw(pcb)
    # Small silver solder vias in the board corners, separate from the CPU package.
    for x, y in ((3, 3), (27, 3), (3, 27), (27, 27)):
        d.rectangle((x, y, x+1, y+1), fill='#61858b')
        d.point((x, y), fill='#b1c2bc')
    save('board', pcb)
    save('board_back', pcb.transpose(Image.Transpose.FLIP_LEFT_RIGHT))

    ceramic = Image.new('RGBA', (32, 32), '#262d37')
    d = ImageDraw.Draw(ceramic)
    d.rectangle((0, 0, 31, 31), outline='#151e29')
    d.line((1, 29, 1, 1, 29, 1), fill='#56616e')
    d.line((2, 30, 30, 30, 30, 2), fill='#1b2330')
    d.rectangle((3, 3, 28, 28), outline='#353f4b')
    save('ceramic', ceramic)

    cap = Image.new('RGBA', (32, 32), '#535e6c')
    d = ImageDraw.Draw(cap)
    d.polygon([(4,1),(27,1),(30,4),(30,27),(27,30),(4,30),(1,27),(1,4)], fill='#8b95a0')
    d.polygon([(5,3),(26,3),(28,5),(28,26),(26,28),(5,28),(3,26),(3,5)], fill='#78818e')
    d.line([(3,25),(3,5),(5,3),(26,3)], fill='#b4bdc5')
    d.line([(5,28),(26,28),(28,26),(28,5)], fill='#4b5869')
    # Restrained horizontal machining marks; no high-frequency random noise.
    for y in (6, 10, 14, 18, 22, 26):
        d.line((5,y,26,y), fill='#7d8792')
    d.rectangle((10,9,21,20), fill='#384958')
    d.line((10,20,10,9,21,9), fill='#263845')
    d.rectangle((12,11,19,18), fill='#2b6979', outline='#4ba0ac')
    d.rectangle((14,13,17,16), fill='#80c5cd')
    for p in (12,15,18):
        d.line((p,7,p,8),fill='#b5bec5')
        d.line((p,21,p,22),fill='#b5bec5')
        d.line((8,p,9,p),fill='#b5bec5')
        d.line((22,p,23,p),fill='#b5bec5')
    d.line((11,25,17,25),fill='#4c5969')
    d.line((19,25,21,25),fill='#4c5969')
    save('die', cap)

    gold = Image.new('RGBA',(32,32),'#b18a45')
    d = ImageDraw.Draw(gold)
    for rect, color in (((0,0,31,3),'#f0dca1'), ((0,4,31,8),'#d7b86f'),
                        ((0,9,31,21),'#c29b53'), ((0,22,31,28),'#a57a3c'),
                        ((0,29,31,31),'#74532e')):
        d.rectangle(rect,fill=color)
    d.line((2,4,2,28),fill='#e3c784')
    save('gold',gold)


if __name__ == '__main__':
    main()
