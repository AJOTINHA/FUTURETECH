"""Models, blockstates and item models of the wireless redstone plates.

The shape is WR-CBE's, converted from its `logic.obj`: a plate the size of the face with the lamp
sunk into it, an obsidian mast off the middle of it, and for the receiver a second length of mast
leaning out of the first at 22.5 degrees to hold the dish. The dish itself and the hedron that
floats over both plates are not boxes, so they are not here — `WirelessRedstoneRenderer` draws them.

The models are drawn lying on the floor, the way WR-CBE's own numbers are given, and the blockstate
turns that to whichever of the six faces the plate was mounted on.

Run from any directory: python tools/generate_wireless_redstone.py
"""
import json
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parents[1] / "src/main/resources/assets/futuretech"

# The digits the plate shows its frequency in, three pixels by five, stacked nought to nine. The
# renderer reads one cell per digit, so the cell is taller than the digit to leave a line between
# them; the strip is a power of two so it mipmaps like any other texture.
DIGIT_CELL = 6
DIGIT_ROWS = 5
DIGIT_COLUMNS = 3
STRIP_WIDTH = 4
STRIP_HEIGHT = 64
DIGIT_SHAPES = {
    0: ("###", "#.#", "#.#", "#.#", "###"),
    1: ("..#", "..#", "..#", "..#", "..#"),
    2: ("###", "..#", "###", "#..", "###"),
    3: ("###", "..#", "###", "..#", "###"),
    4: ("#.#", "#.#", "###", "..#", "..#"),
    5: ("###", "#..", "###", "..#", "###"),
    6: ("###", "#..", "###", "#.#", "###"),
    7: ("###", "..#", "..#", "..#", "..#"),
    8: ("###", "#.#", "###", "#.#", "###"),
    9: ("###", "#.#", "###", "..#", "###"),
}


def digit_strip():
    """The ten digits in white on nothing; the renderer tints them to the ink it wants."""
    strip = Image.new("RGBA", (STRIP_WIDTH, STRIP_HEIGHT), (0, 0, 0, 0))
    pixels = strip.load()
    for digit, shape in DIGIT_SHAPES.items():
        for row, line in enumerate(shape):
            for column, mark in enumerate(line):
                if mark == "#":
                    pixels[column, digit * DIGIT_CELL + row] = (255, 255, 255, 255)
    return strip

# The plate: two pixels of stone over the face, the lamp drawn on the side that looks out.
PLATE_DEEP = 2
# The mast, in WR-CBE's place: two pixels across, off centre towards the back of the plate.
MAST_X0, MAST_X1 = 7, 9
MAST_Z0, MAST_Z1 = 4, 6
# How far each mast goes: the transmitter's carries the hedron, the receiver's stops for the arm.
# WR-CBE cuts the top of the receiver's post at the arm's own angle, from 9.0 at the back down to
# 8.16 at the front, so the arm lies flush on it. A box cannot be cut at an angle, so the post
# stops at the middle of that cut and the arm runs down inside it instead.
TRANSMITTER_MAST_TOP = 10
RECEIVER_MAST_TOP = 8.5
# The receiver's arm, drawn upright about its middle and then leaned out over the plate. The top
# end is where WR-CBE's dish stand has it; the bottom carries on past where WR-CBE stops, down
# inside the post, so the joint between the two is hidden rather than stepped.
ARM = {"from": [7.2, 5.7, 5.71], "to": [8.8, 12.15, 7.31]}
ARM_ORIGIN = [8, 10, 6.51]
# Positive leans it out over the plate: a model's rotation goes anticlockwise about
# its axis, the other way round from the turns a blockstate gives the whole model.
ARM_LEAN = 22.5

FACES = ("north", "south", "east", "west", "up", "down")

# How far a plate can be turned on the face it hangs on; the same range as the block's SPIN.
SPINS = (0, 1, 2, 3)


def angles(facing, spin):
    """The x and then the y that turn the model as drawn — the plate lying on the floor — to a face
    and a quarter turn on it. The same table as `WirelessRedstoneBlock.angles`, which the block's
    boxes and the renderer's pose both read; a test keeps the two from drifting apart.

    A plate on a wall does not turn: an x and a y reach sixteen of the twenty-four ways round a
    cube, and rolling a wall plate is one of the eight they cannot say."""
    if facing == "up":
        return {"x": 0, "y": 90 * spin}
    if facing == "down":
        return {"x": 180, "y": 90 * spin}
    return {"x": 90, "y": {"north": 0, "east": 90, "south": 180, "west": 270}[facing]}


def write_json(path, data):
    target = ASSETS / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def mast_faces(skip=()):
    """The obsidian all round; the end buried in the plate is left out."""
    return {face: {"texture": "#mast"} for face in FACES if face not in skip}


def model(kind, lit):
    plate = {
        # The lamp looks out of the plate; the rest of it is the same stone as the sides.
        "up": {"uv": [0, 0, 16, 16], "texture": "#front"},
        "down": {"uv": [0, 0, 16, 16], "texture": "#base", "cullface": "down"},
    }
    for side in ("north", "south", "east", "west"):
        # A two pixel strip of the stone, so the edge is the cut through the plate.
        plate[side] = {"uv": [0, 0, 16, PLATE_DEEP], "texture": "#base"}
    elements = [{"from": [0, 0, 0], "to": [16, PLATE_DEEP, 16], "faces": plate}]
    top = TRANSMITTER_MAST_TOP if kind == "transmitter" else RECEIVER_MAST_TOP
    elements.append({
        "from": [MAST_X0, PLATE_DEEP, MAST_Z0], "to": [MAST_X1, top, MAST_Z1],
        "faces": mast_faces(skip=("down",)),
    })
    if kind == "receiver":
        elements.append({
            "from": ARM["from"], "to": ARM["to"],
            "rotation": {"origin": ARM_ORIGIN, "axis": "x", "angle": ARM_LEAN},
            "faces": mast_faces(),
        })
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "front": f"futuretech:block/wireless/{'plate_on' if lit else 'plate'}",
            "base": "futuretech:block/wireless/base",
            "mast": "minecraft:block/obsidian",
            "particle": "futuretech:block/wireless/base",
        },
        "elements": elements,
    }


def main():
    entity = ASSETS / "textures/entity"
    entity.mkdir(parents=True, exist_ok=True)
    digit_strip().save(entity / "wireless_digits.png")
    print("  digitos: textures/entity/wireless_digits.png")
    for kind in ("transmitter", "receiver"):
        name = f"wireless_{kind}"
        write_json(f"models/block/wireless/{kind}.json", model(kind, False))
        write_json(f"models/block/wireless/{kind}_on.json", model(kind, True))
        variants = {}
        for facing in ("up", "down", "north", "east", "south", "west"):
            for spin in SPINS:
                for lit in (False, True):
                    variant = {"model": f"futuretech:block/wireless/{kind}{'_on' if lit else ''}"}
                    variant.update({key: value for key, value in angles(facing, spin).items() if value})
                    variants[f"facing={facing},lit={str(lit).lower()},spin={spin}"] = variant
        write_json(f"blockstates/{name}.json", {"variants": variants})
        # In the hand and in the inventory the plate is the unlit model, drawn the way block/block
        # draws a slab; the dish and the hedron come from the special renderer over it.
        write_json(f"models/item/{name}.json", {"parent": f"futuretech:block/wireless/{kind}"})
        write_json(f"items/{name}.json", {"model": {"type": "minecraft:composite", "models": [
            {"type": "minecraft:model", "model": f"futuretech:item/{name}"},
            {"type": "minecraft:special", "base": f"futuretech:item/{name}",
             "model": {"type": "futuretech:wireless_head", "kind": kind}},
        ]}})
        print(f"  {name}: model, blockstate, item")


if __name__ == "__main__":
    print("redstone sem fio:")
    main()
    print("pronto.")
