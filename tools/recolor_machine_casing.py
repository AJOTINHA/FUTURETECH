"""Recolor the hollow frame with colors sampled from the machines' MK1 texture.

Requires Pillow. Keeps the ImageGen source untouched and changes only RGB values.
"""
from collections import Counter
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / 'src/main/resources/assets/futuretech/textures'


def recolor():
    source = Image.open(ROOT / 'art/chip_and_casing/casing_reference_source.png').convert('RGBA')
    source = source.resize((32, 32), Image.Resampling.NEAREST)
    reference = Image.open(TEXTURES / 'block/machine/machine_side.png').convert('RGB')
    palette = Counter(reference.get_flattened_data())
    # Dominant panel and border colors, plus an existing restrained metal highlight.
    body = palette.most_common(1)[0][0]
    border = reference.getpixel((0, 0))
    shadow = min(palette, key=lambda c: sum(c))
    highlight = min(palette, key=lambda c: sum((a-b)**2 for a,b in zip(c, (143,148,157))))
    anchors = ((0, shadow), (18, border), (28, border), (69, body), (96, highlight), (255, highlight))

    def color(pixel):
        r, g, b, alpha = pixel
        luminance = .2126*r + .7152*g + .0722*b
        for (lo, start), (hi, end) in zip(anchors, anchors[1:]):
            if luminance <= hi:
                weight = (luminance-lo)/(hi-lo)
                target = tuple(a + weight*(b-a) for a,b in zip(start,end))
                # Every exported RGB triplet must exist in the actual machine texture.
                nearest = min(palette, key=lambda c: (sum((a-b)**2 for a,b in zip(c,target)), -palette[c], c))
                return (*nearest, alpha)
        raise AssertionError(luminance)

    output = Image.new('RGBA', source.size)
    output.putdata([color(p) for p in source.get_flattened_data()])
    path = TEXTURES / 'block/machine_casing/hollow_frame.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    output.save(path)
    print(f'Applied machine palette: body {body}, border {border}; saved {path}')


if __name__ == '__main__':
    recolor()
