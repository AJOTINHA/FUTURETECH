"""Models, blockstates and item models of the wireless redstone plates.

The shape is WR-CBE's, converted from its `logic.obj`: a plate the size of the face with the lamp
sunk into it, an obsidian mast off the middle of it, and for the receiver a second length of mast
leaning out of the first at 22.5 degrees to hold the dish. The dish itself and the hedron that
floats over both plates are not boxes, so they are not here — `WirelessRedstoneRenderer` draws them.

The models are drawn lying on the floor, the way WR-CBE's own numbers are given, and the blockstate
turns that to whichever of the six faces the plate was mounted on. A blockstate's x and y cannot
roll a plate sideways on a wall, so each plate also has a `_turned` model, the same one turned a
quarter on the floor, and the two between them hang every way; `WirelessRedstoneBlock.angles` holds
the same table as `angles` here.

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


CLOCKWISE = {"north": "east", "east": "south", "south": "west", "west": "north"}
STEP = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}


def front(facing, spin):
    """Where the plate's front looks: the same rule as `WirelessRedstoneBlock.front`."""
    if facing in ("up", "down"):
        looking = "north" if facing == "down" else "south"
        for _ in range(spin):
            looking = CLOCKWISE[looking]
        return looking
    return ("up", CLOCKWISE[facing], "down", {value: key for key, value in CLOCKWISE.items()}[facing])[spin]


def turn(base, x, y, point):
    """A point of the model as drawn, turned the way the blockstate turns it: the quarter baked
    into the model, the x, then the y, a quarter at a time about the middle of the block. The same
    arithmetic as `WirelessRedstoneBlock.turn`."""
    a, b, c = point[0] - 8, point[1] - 8, point[2] - 8
    for _ in range(base // 90):
        a, c = -c, a
    for _ in range(x // 90):
        b, c = c, -b
    for _ in range(y // 90):
        a, c = -c, a
    # Squared up to the model's own precision, so a turn adds no float dust to the numbers.
    return round(a + 8, 4), round(b + 8, 4), round(c + 8, 4)


def direction(point):
    """The direction a point off the middle of the block lies in."""
    a, b, c = point[0] - 8, point[1] - 8, point[2] - 8
    return max(STEP, key=lambda name: a * STEP[name][0] + b * STEP[name][1] + c * STEP[name][2])


def angles(facing, spin):
    """The quarter baked into the model, the x and the y that turn the model as drawn — the plate
    lying on the floor, front to the south — to a face and a quarter turn on it. The same table as
    `WirelessRedstoneBlock.angles`, which the block's boxes and the renderer's pose both read; a
    test keeps the two from drifting apart.

    On the floor and the ceiling the y is the turn. On a wall the table is searched for the way
    round that stands the mast off the wall and points the front where `front` says: an x of 90 or
    270 with the plain model reaches up and down, and the turned model the two sides."""
    if facing == "up":
        return {"base": 0, "x": 0, "y": 90 * spin}
    if facing == "down":
        return {"base": 0, "x": 180, "y": 90 * spin}
    wanted = front(facing, spin)
    for base in (0, 90):
        for x in (90, 270):
            for y in (0, 90, 180, 270):
                if direction(turn(base, x, y, (8, 9, 8))) == facing and direction(turn(base, x, y, (8, 8, 9))) == wanted:
                    return {"base": base, "x": x, "y": y}
    raise ValueError(f"no turn hangs a plate on {facing} looking {wanted}")


def quarter(point):
    """A point of the model turned a quarter on the floor, the way the blockstate's y turns it."""
    return turn(90, 0, 0, point)


def turned(element):
    """An element of the model turned a quarter on the floor: its corners, its faces and, for the
    arm, the axis it leans about, which goes round with it."""
    one = quarter(element["from"])
    two = quarter(element["to"])
    result = {
        "from": [min(one[i], two[i]) for i in range(3)],
        "to": [max(one[i], two[i]) for i in range(3)],
        "faces": {},
    }
    for face, detail in element["faces"].items():
        moved = CLOCKWISE.get(face, face)
        detail = dict(detail)
        if "cullface" in detail:
            detail["cullface"] = CLOCKWISE.get(detail["cullface"], detail["cullface"])
        result["faces"][moved] = detail
    if "rotation" in element:
        rotation = dict(element["rotation"])
        rotation["origin"] = list(quarter(rotation["origin"]))
        rotation["axis"] = {"x": "z", "z": "x"}.get(rotation["axis"], rotation["axis"])
        result["rotation"] = rotation
    return result


def write_json(path, data):
    target = ASSETS / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def mast_faces(skip=()):
    """The obsidian all round; the end buried in the plate is left out."""
    return {face: {"texture": "#mast"} for face in FACES if face not in skip}


def model(kind, lit, base=0):
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
    if base:
        # The turned model: every box a quarter round, and the lamp's texture turned with it.
        elements = [turned(element) for element in elements]
    return {
        "parent": "minecraft:block/block",
        "textures": {
            "front": f"futuretech:block/wireless/{'plate_on' if lit else 'plate'}{'_turned' if base else ''}",
            "base": "futuretech:block/wireless/base",
            "mast": "minecraft:block/obsidian",
            "particle": "futuretech:block/wireless/base",
        },
        "elements": elements,
    }


def turned_textures():
    """The lamp texture turned the quarter the model is: the lamp, at the bottom (south) of the
    plain one, moves to the left (west), which is where a quarter clockwise on the floor takes the
    south edge."""
    for name in ("plate", "plate_on"):
        source = ASSETS / f"textures/block/wireless/{name}.png"
        Image.open(source).transpose(Image.ROTATE_270).save(ASSETS / f"textures/block/wireless/{name}_turned.png")


def main():
    entity = ASSETS / "textures/entity"
    entity.mkdir(parents=True, exist_ok=True)
    digit_strip().save(entity / "wireless_digits.png")
    print("  digitos: textures/entity/wireless_digits.png")
    turned_textures()
    print("  placas viradas: textures/block/wireless/plate_turned.png, plate_on_turned.png")
    for kind in ("transmitter", "receiver"):
        name = f"wireless_{kind}"
        write_json(f"models/block/wireless/{kind}.json", model(kind, False))
        write_json(f"models/block/wireless/{kind}_on.json", model(kind, True))
        write_json(f"models/block/wireless/{kind}_turned.json", model(kind, False, 90))
        write_json(f"models/block/wireless/{kind}_turned_on.json", model(kind, True, 90))
        variants = {}
        for facing in ("up", "down", "north", "east", "south", "west"):
            for spin in SPINS:
                for lit in (False, True):
                    turn_by = angles(facing, spin)
                    model_name = f"{kind}{'_turned' if turn_by['base'] else ''}{'_on' if lit else ''}"
                    variant = {"model": f"futuretech:block/wireless/{model_name}"}
                    variant.update({key: turn_by[key] for key in ("x", "y") if turn_by[key]})
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
