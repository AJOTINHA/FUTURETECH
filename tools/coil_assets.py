"""Native Minecraft geometry and deterministic pixel materials for the coil generator."""
import math

from PIL import Image


def material_atlas(coil):
    """Eight 8px material swatches in a 32px atlas, with crisp machined edges."""
    light, mid, dark, shade = coil["wire"]
    image = Image.new("RGBA", (32, 32), (17, 18, 19, 255))
    palettes = [
        (light, mid, mid, mid, dark, dark, shade, shade),
        (light, light, mid, mid, mid, mid, dark, dark),
        (dark, dark, dark, shade, shade, shade, shade, shade),
        ((72, 79, 88, 255),) * 2 + ((42, 47, 56, 255),) * 4 + ((26, 29, 36, 255),) * 2,
        ((255, 90, 80, 255),) + ((200, 30, 30, 255),) * 3 + ((120, 16, 16, 255),) * 2 + ((77, 15, 25, 255),) * 2,
        ((235, 64, 64, 255),) * 2 + ((178, 27, 40, 255),) * 4 + ((120, 16, 16, 255),) * 2,
        ((111, 124, 137, 255),) * 2 + ((64, 74, 86, 255),) * 4 + ((33, 40, 50, 255),) * 2,
        ((255, 161, 139, 255),) * 2 + ((255, 90, 80, 255),) * 3 + ((200, 30, 30, 255),) * 3,
    ]
    for index, rows in enumerate(palettes):
        x0, y0 = (index % 4) * 8, (index // 4) * 8
        for y, colour in enumerate(rows):
            for x in range(8):
                image.putpixel((x0+x, y0+y), colour)
    for x, y in ((2, 3), (5, 4), (11, 3), (26, 3), (29, 4), (10, 11), (13, 12)):
        c = image.getpixel((x, y))
        image.putpixel((x, y), tuple(max(0, v - 12) for v in c[:3]) + (255,))
    return image


def coil_model():
    elements = []

    def uv(tile):
        x, y = (tile % 4) * 4, (tile // 4) * 4
        return [x, y, x+4, y+4]

    def box(name, lower, upper, side, top=None, bottom=None, rotation=None):
        faces = {face: {"texture": "#material", "uv": uv(tile)} for face, tile in (
            ("north", side), ("south", side), ("east", side), ("west", side),
            ("up", side if top is None else top), ("down", side if bottom is None else bottom))}
        element = {"name": name, "from": lower, "to": upper, "faces": faces}
        if rotation:
            element["rotation"] = rotation
        elements.append(element)

    def ring(name, y, radius, thickness, height, side, top, bottom):
        # Eight tangent bars form an octagon. The diagonal joiners are recessed
        # on both ends of the Y axis: intersecting bars must never contribute
        # two top/bottom faces in the same plane (visible z-fighting).
        join_recess = 1 / 16
        half_length = round(radius * math.tan(math.pi / 8) + thickness * .22, 4)
        for i in range(8):
            angle = i * math.pi / 4
            cx, cz = 8 + radius * math.sin(angle), 8 + radius * math.cos(angle)
            dx, dz = (thickness/2, half_length) if i % 4 == 2 else (half_length, thickness/2)
            rotation = None
            if i % 2:
                rotation = {"origin": [round(cx, 4), y, round(cz, 4)], "axis": "y",
                            "angle": 45 if i in (1, 5) else -45, "rescale": False}
            inset = join_recess if i % 2 else 0
            box(f"{name}_{i}", [round(cx-dx, 4), round(y+inset, 4), round(cz-dz, 4)],
                [round(cx+dx, 4), round(y+height-inset, 4), round(cz+dz, 4)], side, top, bottom, rotation)

    box("graphite_core", [5.4, 2.7, 5.4], [10.6, 13.3, 10.6], 3)
    for label, y in (("lower", 1.8), ("upper", 12.7)):
        ring(f"{label}_redstone_collar", y, 4.65, 1.1, 1.5, 4, 5, 4)
        box(f"{label}_plate", [4.8, y+.15, 4.8], [11.2, y+1.3, 11.2], 4, 5, 4)
        box(f"{label}_socket", [6.5, y-.1, 6.5], [9.5, y+1.6, 9.5], 6)
        box(f"{label}_contact", [7.35, y-.2, 7.35], [8.65, y+1.7, 8.65], 0, 1, 2)
    for turn in range(6):
        ring(f"winding_{turn+1}", round(3.5 + turn*1.48, 4), 3.65, .85, 1.05, 0, 1, 2)
    # Leads enter the winding below its outer surfaces. The narrower elbows
    # likewise enter the leads without coplanar side walls at the connection.
    box("lower_lead", [1.4, 3.65, 7.6], [4.8, 4.35, 8.4], 0, 1, 2)
    box("lower_lead_elbow", [1.48, 2.3, 7.68], [2.12, 3.9, 8.32], 0, 1, 2)
    box("upper_lead", [11.2, 11.05, 7.6], [14.6, 11.75, 8.4], 0, 1, 2)
    box("upper_lead_elbow", [13.88, 11.5, 7.68], [14.52, 13, 8.32], 0, 1, 2)
    box("redstone_indicator", [7.1, 13.05, 2.75], [8.9, 13.65, 3.05], 7)
    return {
        "ambientocclusion": False, "gui_light": "side", "textures": {"particle": "#material"},
        "display": {
            "gui": {"rotation": [25, 35, -18], "translation": [0, 0, 0], "scale": [.85, .85, .85]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [.5, .5, .5]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [.8, .8, .8]},
            "thirdperson_righthand": {"rotation": [0, 0, 10], "translation": [0, 2, 1], "scale": [.55, .55, .55]},
            "thirdperson_lefthand": {"rotation": [0, 0, -10], "translation": [0, 2, 1], "scale": [.55, .55, .55]},
            "firstperson_righthand": {"rotation": [0, -35, 12], "translation": [1, 2, 0], "scale": [.65, .65, .65]},
            "firstperson_lefthand": {"rotation": [0, 35, -12], "translation": [1, 2, 0], "scale": [.65, .65, .65]},
        }, "elements": elements,
    }
