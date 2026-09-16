"""Native cuboid models using dedicated FUTURETECH chip and casing textures.

Run from any directory: python tools/generate_chip_and_casing.py
"""
import json
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/futuretech"
SIDES = ("north", "south", "east", "west", "up", "down")


def box(name, start, end, texture, uv=(0, 0, 16, 16)):
    return {"name": name, "from": start, "to": end,
            "faces": {side: {"texture": "#" + texture, "uv": list(uv)} for side in SIDES}}


def write(path, model):
    target = ASSETS / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")


def chip():
    elements = [
        box("substrate", [2.75, 2.75, 7.6], [13.25, 13.25, 8.4], "board"),
        box("ceramic_package", [4.75, 4.75, 7.05], [11.25, 11.25, 8.95], "dark"),
        box("processor_lid", [5.2, 5.2, 6.85], [10.8, 10.8, 7.05], "cyan"),
        box("back_lid", [5.2, 5.2, 8.95], [10.8, 10.8, 9.15], "cyan"),
    ]
    # Thin PCB edges show laminate rather than a compressed copy of the circuits.
    for side in ("up", "down", "east", "west"):
        elements[0]["faces"][side]["uv"] = [7, 7, 9, 9]
    # Six thin contacts on each edge; a wider exposed board makes the circuit readable.
    for index in range(6):
        p = 3.925 + index * 1.5
        for name, start, end in (
            ("left", [1.4, p, 7.8], [2.75, p + .65, 8.2]),
            ("right", [13.25, p, 7.8], [14.6, p + .65, 8.2]),
            ("bottom", [p, 1.4, 7.8], [p + .65, 2.75, 8.2]),
            ("top", [p, 13.25, 7.8], [p + .65, 14.6, 8.2]),
        ):
            elements.append(box(f"pin_{name}_{index}", start, end, "gold", [2, 2, 6, 12]))
    write("models/item/chip.json", {
        "parent": "minecraft:block/block", "ambientocclusion": False, "gui_light": "front",
        "textures": {"particle": "futuretech:item/chip/ceramic",
                     "board": "futuretech:item/chip/board",
                     "dark": "futuretech:item/chip/ceramic",
                     "gold": "futuretech:item/chip/gold", "cyan": "futuretech:item/chip/die"},
        "elements": elements,
        "display": {
            "gui": {"rotation": [12, -20, -12], "scale": [.95, .95, .95]},
            "ground": {"translation": [0, 3, 0], "scale": [.5, .5, .5]},
            "fixed": {"rotation": [0, 180, 0], "scale": [.85, .85, .85]},
            "firstperson_righthand": {"rotation": [0, -30, 5], "scale": [.75, .75, .75]},
            "firstperson_lefthand": {"rotation": [0, 30, -5], "scale": [.75, .75, .75]},
            "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [0, 2, 0], "scale": [.65, .65, .65]},
            "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [0, 2, 0], "scale": [.65, .65, .65]},
        },
    })


def casing():
    from itertools import product

    edges = (0, 5, 11, 16)
    cells = {cell for cell in product(range(3), repeat=3) if cell.count(1) <= 1}
    directions = {"west": (0, -1), "east": (0, 1), "down": (1, -1),
                  "up": (1, 1), "north": (2, -1), "south": (2, 1)}
    elements = []
    for cell in sorted(cells):
        x, y, z = (edges[i] for i in cell)
        X, Y, Z = (edges[i + 1] for i in cell)
        element = box("frame_" + "_".join(map(str, cell)), [x, y, z], [X, Y, Z], "frame")
        # Project one continuous face texture over all beams of each cube face.
        uvs = {"north": [16-X, 16-Y, 16-x, 16-y], "south": [x, 16-Y, X, 16-y],
               "east": [16-Z, 16-Y, 16-z, 16-y], "west": [z, 16-Y, Z, 16-y],
               "up": [x, z, X, Z], "down": [x, 16-Z, X, 16-z]}
        for side, (axis, step) in directions.items():
            neighbor = list(cell)
            neighbor[axis] += step
            if tuple(neighbor) in cells:
                del element["faces"][side]
                continue
            outer = neighbor[axis] < 0 or neighbor[axis] > 2
            element["faces"][side]["uv"] = uvs[side] if outer else [7, 7, 9, 9]
        elements.append(element)
    write("models/block/machine_casing.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": "futuretech:block/machine_casing/hollow_frame",
                     "frame": "futuretech:block/machine_casing/hollow_frame"},
        "elements": elements,
    })


if __name__ == "__main__":
    chip()
    casing()
