"""Front textures, models, blockstate, item model and data of the time controller.

The front is drawn over the crusher's front: same casing, status light and window frame, with the
sky behind the glass — a night sky over a strip of ground, the sun low on the left and the moon
high on the right, the two ends of the day the machine moves between. The lit front is the same
sky at dusk, with the status light on and the sun and moon bright: the block glows for a moment
after it moves the clock, and that is the moment this shows.

Run from any directory: python tools/generate_time_controller.py
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/futuretech"
TEXTURES = ASSETS / "textures/block"

WINDOW_EDGE = (17, 18, 19, 255)
NIGHT = (26, 29, 34, 255)
DUSK = (44, 58, 92, 255)
DUSK_GLOW = (92, 70, 84, 255)
GROUND = (69, 74, 83, 255)
HORIZON = (123, 129, 140, 255)
STAR = (170, 178, 190, 255)
SUN = (242, 194, 2, 255)
SUN_RAY = (255, 226, 110, 255)
MOON = (214, 220, 232, 255)
MOON_SHADE = (150, 158, 172, 255)

INNER_X0, INNER_X1 = 6, 25
INNER_Y0, INNER_Y1 = 9, 21
GROUND_Y = 19
SUN_X, SUN_Y = 11, 16
MOON_X, MOON_Y = 20, 12
STARS = ((8, 10), (15, 11), (23, 10), (17, 14))


def fill(image, x0, y0, x1, y1, colour):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            image.putpixel((x, y), colour)


def status_light(image, on_frame):
    for x in range(14, 18):
        image.putpixel((x, 4), on_frame.getpixel((x, 4)))


def sky(image, on):
    # The glass, edged, with the sky in it: night, or dusk with a warm band at the horizon.
    fill(image, INNER_X0, INNER_Y0, INNER_X1, INNER_Y1, DUSK if on else NIGHT)
    if on:
        fill(image, INNER_X0 + 1, GROUND_Y - 2, INNER_X1 - 1, GROUND_Y - 1, DUSK_GLOW)
    fill(image, INNER_X0, INNER_Y0, INNER_X0, INNER_Y1, WINDOW_EDGE)
    fill(image, INNER_X1, INNER_Y0, INNER_X1, INNER_Y1, WINDOW_EDGE)
    # The ground, a line of lighter steel where it meets the sky.
    fill(image, INNER_X0 + 1, GROUND_Y, INNER_X1 - 1, INNER_Y1, GROUND)
    fill(image, INNER_X0 + 1, GROUND_Y, INNER_X1 - 1, GROUND_Y, HORIZON)
    if not on:
        for x, y in STARS:
            image.putpixel((x, y), STAR)


def sun(image, on):
    # Half a disc on the horizon, with rays only while it shines.
    fill(image, SUN_X - 1, SUN_Y, SUN_X + 1, SUN_Y + 2, SUN)
    image.putpixel((SUN_X - 2, SUN_Y + 1), SUN)
    image.putpixel((SUN_X + 2, SUN_Y + 1), SUN)
    if on:
        for x, y in ((SUN_X, SUN_Y - 2), (SUN_X - 3, SUN_Y - 1), (SUN_X + 3, SUN_Y - 1), (SUN_X - 4, SUN_Y + 1), (SUN_X + 4, SUN_Y + 1)):
            image.putpixel((x, y), SUN_RAY)


def moon(image, on):
    # A crescent open to the right, its inner edge in shade.
    bright = MOON if on else MOON_SHADE
    for x, y in ((MOON_X, MOON_Y - 1), (MOON_X + 1, MOON_Y - 1), (MOON_X - 1, MOON_Y), (MOON_X - 1, MOON_Y + 1),
                 (MOON_X, MOON_Y + 2), (MOON_X + 1, MOON_Y + 2)):
        image.putpixel((x, y), bright)
    image.putpixel((MOON_X, MOON_Y), MOON_SHADE if on else GROUND)
    image.putpixel((MOON_X, MOON_Y + 1), MOON_SHADE if on else GROUND)


def front(on):
    image = Image.open(TEXTURES / "crusher/crusher_front.png").convert("RGBA")
    if on:
        status_light(image, Image.open(TEXTURES / "crusher/crusher_front_on.png").convert("RGBA"))
    sky(image, on)
    sun(image, on)
    moon(image, on)
    return image


def textures():
    out = TEXTURES / "time_controller"
    out.mkdir(parents=True, exist_ok=True)
    front(False).save(out / "time_controller_front.png")
    front(True).save(out / "time_controller_front_on.png")
    print(f"  {out.relative_to(ROOT)}")


def copy_json(source, target, old, new):
    text = (RES / source).read_text(encoding="utf-8").replace(old, new)
    path = RES / target
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    print(f"  {path.relative_to(ROOT)}")


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"  {path.relative_to(ROOT)}")


def models_and_data():
    # One block, one tier: the machine casing on five faces and the window on the front, lit or not.
    for suffix in ("", "_on"):
        write(ASSETS / f"models/block/time_controller{suffix}.json", {
            "parent": "futuretech:block/machine_base",
            "textures": {"front": f"futuretech:block/time_controller/time_controller_front{suffix}"},
        })
    variants = {}
    for facing, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in (False, True):
            variant = {"model": "futuretech:block/time_controller" + ("_on" if lit else "")}
            if angle:
                variant["y"] = angle
            variants[f"facing={facing},lit={str(lit).lower()}"] = variant
    write(ASSETS / "blockstates/time_controller.json", {"variants": variants})
    write(ASSETS / "items/time_controller.json", {
        "model": {"type": "minecraft:model", "model": "futuretech:block/time_controller"},
    })
    write(RES / "data/futuretech/loot_table/blocks/time_controller.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": "futuretech:time_controller"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    copy_json("data/futuretech/advancement/recipes/crusher.json",
              "data/futuretech/advancement/recipes/time_controller.json", "crusher", "time_controller")
    # A clock over the casing, a battery either side to hold the fare, redstone under it to hear the signal.
    write(RES / "data/futuretech/recipe/time_controller.json", {
        "type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["IKI", "BMB", "IRI"],
        "key": {"I": "minecraft:iron_ingot", "K": "minecraft:clock", "B": "futuretech:battery_mk1",
                "M": "futuretech:machine_casing", "R": "minecraft:redstone"},
        "result": {"id": "futuretech:time_controller", "count": 1},
    })


if __name__ == "__main__":
    textures()
    models_and_data()
    print("Generated the time controller: textures, models, data and recipe.")
