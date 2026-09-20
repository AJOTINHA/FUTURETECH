"""Front textures, models, blockstates, item models and data of the controllers.

Both fronts are drawn over the crusher's front: same casing, status light and window frame, with
the sky behind the glass. The time controller's is a night sky over a strip of ground, the sun
low on the left and the moon high on the right, the two ends of the day the machine moves
between; lit, the same sky at dusk with the sun and moon bright. The weather controller's is a
cloud over the same ground with rain under it; lit, the cloud has a bolt coming out of it. A
block glows for a moment after it fires, and that is the moment the lit fronts show.

Run from any directory: python tools/generate_controllers.py
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
CLOUD = (196, 204, 214, 255)
CLOUD_SHADE = (140, 150, 164, 255)
RAIN = (98, 150, 220, 255)
BOLT = (255, 226, 110, 255)

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


def cloud(image, on):
    # A cloud across the top of the window: a wide body with a bump on it, shaded underneath.
    fill(image, 9, 12, 22, 14, CLOUD)
    fill(image, 12, 10, 18, 11, CLOUD)
    fill(image, 9, 14, 22, 14, CLOUD_SHADE)
    if on:
        # The bolt out of the cloud, zigzagging down to the ground.
        for x, y in ((16, 15), (15, 16), (16, 16), (15, 17), (14, 18)):
            image.putpixel((x, y), BOLT)
    else:
        for x in (10, 13, 19, 22):
            image.putpixel((x, 16), RAIN)
        for x in (11, 15, 17, 21):
            image.putpixel((x, 17), RAIN)
        for x in (10, 13, 19, 22):
            image.putpixel((x, 18), RAIN)


def front(kind, on):
    image = Image.open(TEXTURES / "crusher/crusher_front.png").convert("RGBA")
    if on:
        status_light(image, Image.open(TEXTURES / "crusher/crusher_front_on.png").convert("RGBA"))
    sky(image, on)
    if kind == "time_controller":
        sun(image, on)
        moon(image, on)
    else:
        cloud(image, on)
    return image


def textures(kind):
    out = TEXTURES / kind
    out.mkdir(parents=True, exist_ok=True)
    front(kind, False).save(out / f"{kind}_front.png")
    front(kind, True).save(out / f"{kind}_front_on.png")
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


# What each controller is crafted around: a clock for the time, a lightning rod for the weather.
CORES = {"time_controller": "minecraft:clock", "weather_controller": "minecraft:lightning_rod"}


def models_and_data(kind):
    # One block, one tier: the machine casing on five faces and the window on the front, lit or not.
    for suffix in ("", "_on"):
        write(ASSETS / f"models/block/{kind}{suffix}.json", {
            "parent": "futuretech:block/machine_base",
            "textures": {"front": f"futuretech:block/{kind}/{kind}_front{suffix}"},
        })
    variants = {}
    for facing, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for lit in (False, True):
            variant = {"model": f"futuretech:block/{kind}" + ("_on" if lit else "")}
            if angle:
                variant["y"] = angle
            variants[f"facing={facing},lit={str(lit).lower()}"] = variant
    write(ASSETS / f"blockstates/{kind}.json", {"variants": variants})
    write(ASSETS / f"items/{kind}.json", {
        "model": {"type": "minecraft:model", "model": f"futuretech:block/{kind}"},
    })
    write(RES / f"data/futuretech/loot_table/blocks/{kind}.json", {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "entries": [{"type": "minecraft:item", "name": f"futuretech:{kind}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })
    copy_json("data/futuretech/advancement/recipes/crusher.json",
              f"data/futuretech/advancement/recipes/{kind}.json", "crusher", kind)
    # The crafting recipe is balanced by hand in data/futuretech/recipe/ and is not written here.


if __name__ == "__main__":
    for kind in CORES:
        textures(kind)
        models_and_data(kind)
    print("Generated the controllers: textures, models, data and recipes.")
