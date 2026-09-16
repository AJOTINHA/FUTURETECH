"""Generate native cuboid models, recipes, tags and names for the 3x3 tools: hammers and excavators.

No texture copies: each material samples the same palette pixels as its vanilla pickaxe (hammer)
or shovel (excavator), so resource packs also recolor the corresponding tool.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/futuretech'
MATERIALS = {
    'wooden': ('wooden_tool_materials', 'Madeira', 'Wooden'),
    'stone': ('stone_tool_materials', 'Pedra', 'Stone'),
    'copper': ('copper_tool_materials', 'Cobre', 'Copper'),
    'iron': ('iron_tool_materials', 'Ferro', 'Iron'),
    'golden': ('gold_tool_materials', 'Ouro', 'Golden'),
    'diamond': ('diamond_tool_materials', 'Diamante', 'Diamond'),
    'netherite': ('netherite_tool_materials', 'Netherita', 'Netherite'),
}

DISPLAY = {
    'gui': {'rotation': [20, -25, -35], 'translation': [0, 0, 0], 'scale': [.9] * 3},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [.5] * 3},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [.8] * 3},
    'thirdperson_righthand': {'rotation': [0, -90, 0], 'translation': [0, 3, 1], 'scale': [.85] * 3},
    'thirdperson_lefthand': {'rotation': [0, 90, 0], 'translation': [0, 3, 1], 'scale': [.85] * 3},
    'firstperson_righthand': {'rotation': [0, -65, 12], 'translation': [1, 2.5, 0], 'scale': [.85] * 3},
    'firstperson_lefthand': {'rotation': [0, 65, -12], 'translation': [1, 2.5, 0], 'scale': [.85] * 3},
}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


class Model:
    """Cuboids and flat patches whose faces each sample one palette pixel of the vanilla texture."""

    def __init__(self, pixels):
        self.pixels = pixels
        self.parts = []

    def uv(self, shade):
        # Pixel centers avoid transparent borders.
        x, y = self.pixels[shade]
        return [x + .25, y + .25, x + .75, y + .75]

    def add(self, part, rotation):
        if rotation: part['rotation'] = rotation
        self.parts.append(part)

    def box(self, name, start, end, front, top=None, side=None, rotation=None):
        faces = {}
        for face in ('north', 'south', 'east', 'west', 'up', 'down'):
            shade = (top or front) if face == 'up' else ((side or front) if face in ('east', 'west', 'down') else front)
            faces[face] = {'texture': '#palette', 'uv': self.uv(shade)}
        self.add({'name': name, 'from': start, 'to': end, 'faces': faces}, rotation)

    def face_patch(self, name, x0, y0, x1, y1, z, shade, back=False, rotation=None):
        """A native model face samples one palette color, like a pixel-art patch on the surface."""
        self.add({'name': name, 'from': [x0, y0, z], 'to': [x1, y1, z],
                  'faces': {('south' if back else 'north'): {'texture': '#palette', 'uv': self.uv(shade)}}}, rotation)

    def grip(self, top, bands, edge='wood_dark'):
        """Wooden grain and grip bands break up an otherwise solid-colored handle."""
        for back in (False, True):
            label = 'back' if back else 'front'
            z = 9.015 if back else 6.885
            self.face_patch(f'{label}_handle_shaded_edge', 8.5, 1.5, 9, top, z, edge, back)
            for index, y in enumerate(bands):
                self.face_patch(f'{label}_grip_shadow_{index}', 7, y, 8.5, y + .25, z, 'wood_dark', back)
                self.face_patch(f'{label}_grain_{index}', 7.65, y + .4, 7.9, y + 1, z, 'wood_light', back)


def hammer():
    # Coordinates are shared by all seven pickaxes.
    m = Model({'metal': (8, 3), 'facet': (10, 3), 'light': (6, 3), 'shade': (11, 3), 'dark': (7, 4),
               'wood': (10, 6), 'wood_light': (9, 7), 'wood_dark': (9, 6)})
    m.box('handle', [7, 1, 7], [9, 11.5, 9], 'wood', 'wood_light', 'wood_dark')
    m.box('handle_highlight', [7, 1.5, 6.9], [7.5, 10.5, 7], 'wood_light')
    m.box('heel', [6.75, .5, 6.75], [9.25, 1.5, 9.25], 'wood_dark', 'wood')
    m.box('collar', [6.5, 9.5, 6.5], [9.5, 10.5, 9.5], 'shade', 'light', 'dark')
    m.box('head', [2, 10.5, 5.5], [14, 14.5, 10.5], 'metal', 'light', 'shade')
    m.box('left_striking_face', [1, 10, 5], [3.5, 15, 11], 'metal', 'light', 'shade')
    m.box('right_striking_face', [12.5, 10, 5], [15, 15, 11], 'metal', 'light', 'shade')
    m.box('left_rim', [3.5, 10.5, 5.4], [4.25, 14.5, 10.6], 'shade', 'metal', 'dark')
    m.box('right_rim', [11.75, 10.5, 5.4], [12.5, 14.5, 10.6], 'shade', 'metal', 'dark')
    m.box('front_socket', [6.75, 11.5, 5.25], [9.25, 13.5, 5.5], 'shade', 'metal')
    m.box('front_pin', [7.5, 12, 5.15], [8.5, 13, 5.25], 'light')
    m.box('back_socket', [6.75, 11.5, 10.5], [9.25, 13.5, 10.75], 'shade', 'metal')
    m.box('back_pin', [7.5, 12, 10.75], [8.5, 13, 10.85], 'light')

    # Shade the faces themselves as well as the silhouette: bevel highlights, stepped lower
    # shadows and a few broad forged facets. Keep every tone in the matching vanilla palette.
    for back in (False, True):
        label = 'back' if back else 'front'
        z = 10.515 if back else 5.485
        m.face_patch(f'{label}_head_upper_facet', 4.25, 13.25, 11.75, 14.3, z, 'facet', back)
        m.face_patch(f'{label}_head_top_bevel', 4.25, 14.15, 11.75, 14.5, z + (.005 if back else -.005), 'light', back)
        m.face_patch(f'{label}_head_shadow', 4.25, 10.5, 11.75, 11.25, z, 'shade', back)
        m.face_patch(f'{label}_head_bottom_bevel', 4.25, 10.5, 11.75, 10.75, z + (.005 if back else -.005), 'dark', back)
        for x, y, w in [(4.5, 12.6, 1.5), (5.5, 12.25, .75), (9.75, 12.9, 1.5), (10.5, 12.55, .75)]:
            m.face_patch(f'{label}_forged_facet_{x}', x, y, x + w, y + .3, z, 'facet', back)
        # End caps have a bright upper rim and a dark stepped lower edge.
        for side, x0, x1 in [('left', 1, 3.5), ('right', 12.5, 15)]:
            zcap = 11.015 if back else 4.985
            m.face_patch(f'{label}_{side}_cap_light', x0, 14.5, x1, 15, zcap, 'light', back)
            m.face_patch(f'{label}_{side}_cap_facet', x0, 13.5, x1, 14.5, zcap, 'facet', back)
            m.face_patch(f'{label}_{side}_cap_shadow', x0, 10, x1, 10.75, zcap, 'shade', back)
            m.face_patch(f'{label}_{side}_cap_bottom', x0, 10, x1, 10.25, zcap + (.005 if back else -.005), 'dark', back)
            m.face_patch(f'{label}_{side}_edge', x0, 10.75, x0 + .25, 14.5, zcap, 'light', back)
    m.grip(9.5, (2.1, 3.8, 5.5, 7.2, 8.9))

    # The visible ends have a raised striking pad, an inset face and a narrow bright bevel.
    m.box('left_strike_inset', [.9, 10.6, 5.6], [1, 14.4, 10.4], 'shade', 'light', 'metal')
    m.box('right_strike_inset', [15, 10.6, 5.6], [15.1, 14.4, 10.4], 'shade', 'light', 'metal')
    m.box('left_strike_bevel', [.85, 14.1, 5.6], [1, 14.4, 10.4], 'light')
    m.box('right_strike_bevel', [15, 14.1, 5.6], [15.15, 14.4, 10.4], 'light')
    return m


def excavator():
    # Coordinates are shared by all seven shovels and carry the same tones as the pickaxe palette,
    # plus the dark outline pixel of the shovel handle.
    m = Model({'metal': (12, 3), 'facet': (11, 4), 'light': (10, 3), 'shade': (9, 4), 'dark': (14, 4),
               'wood': (7, 9), 'wood_light': (8, 8), 'wood_dark': (7, 8), 'wood_edge': (8, 9)})
    # A D-grip at the bottom, a short shaft, then a metal ferrule where the blade's socket takes the shaft.
    m.box('grip_bar', [6, .5, 7.25], [10, 1.5, 8.75], 'wood_dark', 'wood', 'wood_edge')
    m.box('grip_left', [6, 1.5, 7.25], [7, 2.75, 8.75], 'wood', 'wood_light', 'wood_dark')
    m.box('grip_right', [9, 1.5, 7.25], [10, 2.75, 8.75], 'wood', 'wood_light', 'wood_dark')
    m.box('handle', [7, 1.5, 7], [9, 7, 9], 'wood', 'wood_light', 'wood_dark')
    m.box('handle_highlight', [7, 2.75, 6.9], [7.5, 6.5, 7], 'wood_light')
    m.box('ferrule', [6.5, 6.5, 6.5], [9.5, 9, 9.5], 'shade', 'light', 'dark')
    m.box('ferrule_ring', [6.4, 8.25, 6.4], [9.6, 8.75, 9.6], 'light', 'light', 'facet')
    # The blade is taller than wide and scooped: a flat centre, two wings angled back and a tip that
    # rounds off in steps, the way a shovel's silhouette does in pixel art.
    left = {'angle': 22.5, 'axis': 'y', 'origin': [6, 12, 8]}
    right = {'angle': -22.5, 'axis': 'y', 'origin': [10, 12, 8]}
    m.box('blade_centre', [6, 8, 7.25], [10, 15.25, 8.75], 'metal', 'light', 'shade')
    m.box('blade_point', [7, 15.25, 7.25], [9, 16, 8.75], 'metal', 'light', 'shade')
    m.box('left_wing', [3.5, 8.5, 7.5], [6, 14, 8.5], 'metal', 'light', 'shade', rotation=left)
    m.box('right_wing', [10, 8.5, 7.5], [12.5, 14, 8.5], 'metal', 'light', 'shade', rotation=right)
    m.box('left_shoulder', [2.75, 9, 7.6], [3.5, 12.5, 8.4], 'metal', 'light', 'shade', rotation=left)
    m.box('right_shoulder', [12.5, 9, 7.6], [13.25, 12.5, 8.4], 'metal', 'light', 'shade', rotation=right)
    m.box('back_rib', [7.5, 9, 8.75], [8.5, 14.5, 9.1], 'facet', 'light', 'shade')

    # Restrained shading: a bright bevel along the cutting edge, one broad facet per panel and a
    # shadow where the blade meets the socket.
    for back in (False, True):
        label = 'back' if back else 'front'
        z = 8.765 if back else 7.235
        m.face_patch(f'{label}_centre_bevel', 6, 14.85, 10, 15.25, z, 'light', back)
        m.face_patch(f'{label}_point_bevel', 7, 15.6, 9, 16, z, 'light', back)
        m.face_patch(f'{label}_centre_facet', 6.25, 12.25, 9.75, 14.35, z, 'facet', back)
        m.face_patch(f'{label}_centre_shadow', 6, 8.5, 10, 9.75, z, 'shade', back)
        zw = 8.515 if back else 7.485
        for side, x0, x1, rotation in [('left', 3.5, 6, left), ('right', 10, 12.5, right)]:
            m.face_patch(f'{label}_{side}_wing_bevel', x0, 13.6, x1, 14, zw, 'light', back, rotation)
            m.face_patch(f'{label}_{side}_wing_facet', x0 + .25, 11.5, x1 - .25, 13.1, zw, 'facet', back, rotation)
            m.face_patch(f'{label}_{side}_wing_shadow', x0, 8.5, x1, 9.5, zw, 'shade', back, rotation)
    m.grip(6.5, (3.4, 5), edge='wood_edge')
    return m


def lumber_axe():
    # Coordinates are shared by all seven axes and carry the same tones as the pickaxe palette,
    # plus the dark outline pixel of the axe handle.
    m = Model({'metal': (9, 4), 'facet': (10, 3), 'light': (9, 2), 'shade': (9, 1), 'dark': (7, 6),
               'wood': (7, 9), 'wood_light': (8, 8), 'wood_dark': (7, 8), 'wood_edge': (8, 9)})
    m.box('handle', [7, 1, 7], [9, 15.25, 9], 'wood', 'wood_light', 'wood_dark')
    m.box('handle_highlight', [7, 1.5, 6.9], [7.5, 9.5, 7], 'wood_light')
    m.box('heel', [6.75, .5, 6.75], [9.25, 1.5, 9.25], 'wood_dark', 'wood')
    m.box('handle_wedge', [7.25, 15.25, 7.25], [8.75, 15.6, 8.75], 'dark', 'facet')
    # A single broad bit to the left, a squared poll to the right and a thick eye around the handle.
    m.box('eye', [6, 10, 6.4], [10, 14.75, 9.6], 'metal', 'light', 'shade')
    m.box('poll', [10, 10.5, 6.75], [12.25, 14.25, 9.25], 'metal', 'light', 'shade')
    m.box('poll_face', [12.25, 10.75, 7], [12.5, 14, 9], 'shade', 'light', 'facet')
    m.box('blade_body', [3.5, 9.5, 7], [6, 15.25, 9], 'metal', 'light', 'shade')
    m.box('blade_flare', [1.75, 8.5, 7.4], [3.5, 16, 8.6], 'metal', 'light', 'shade')
    m.box('cutting_edge', [1.25, 8.75, 7.75], [1.75, 15.75, 8.25], 'light', 'light', 'light')
    m.box('front_pin', [7.6, 11.75, 6.3], [8.4, 12.75, 6.4], 'light')
    m.box('back_pin', [7.6, 11.75, 9.6], [8.4, 12.75, 9.7], 'light')

    # Shading on both faces: bevel toward the edge, a broad forged facet on the bit and shadows where the
    # bit leaves the eye and under the poll.
    for back in (False, True):
        label = 'back' if back else 'front'
        zb = 9.015 if back else 6.985
        m.face_patch(f'{label}_blade_facet', 3.75, 12, 5.75, 14.5, zb, 'facet', back)
        m.face_patch(f'{label}_blade_shadow', 3.5, 9.5, 6, 10.5, zb, 'shade', back)
        m.face_patch(f'{label}_blade_top_bevel', 3.5, 14.9, 6, 15.25, zb, 'light', back)
        zf = 8.615 if back else 7.385
        m.face_patch(f'{label}_flare_bevel', 1.75, 9, 2.5, 15.5, zf, 'light', back)
        m.face_patch(f'{label}_flare_facet', 2.5, 11.5, 3.5, 14.5, zf, 'facet', back)
        m.face_patch(f'{label}_flare_shadow', 1.75, 8.5, 3.5, 9.25, zf, 'shade', back)
        ze = 9.615 if back else 6.385
        m.face_patch(f'{label}_eye_top_bevel', 6, 14.35, 10, 14.75, ze, 'light', back)
        m.face_patch(f'{label}_eye_shadow', 6, 10, 10, 10.75, ze, 'shade', back)
        zp = 9.265 if back else 6.735
        m.face_patch(f'{label}_poll_bevel', 10, 13.85, 12.25, 14.25, zp, 'light', back)
        m.face_patch(f'{label}_poll_shadow', 10, 10.5, 12.25, 11.1, zp, 'shade', back)
    m.grip(9.5, (2.1, 3.8, 5.5, 7.2, 8.9), edge='wood_edge')
    return m


TOOLS = {
    # name: (model, vanilla texture, vanilla item tag, crafting pattern, pt name, en name)
    'hammer': (hammer, 'pickaxe', 'pickaxes', ['MMM', 'MSM', ' S '], 'Martelo de {}', '{} Hammer'),
    'excavator': (excavator, 'shovel', 'shovels', [' M ', 'MSM', ' S '], 'Escavadora de {}', '{} Excavator'),
    'lumber_axe': (lumber_axe, 'axe', 'axes', ['MMM', 'MS ', ' S '], 'Machado Lenhador de {}', '{} Lumber Axe'),
}

for tool, (build, vanilla, vanilla_tag, pattern, pt_name, en_name) in TOOLS.items():
    model = build()
    # Flat lighting in the GUI, like vanilla tools; side lighting darkens cuboid models in the inventory.
    write(ASSETS / f'models/item/{tool}.json', {'ambientocclusion': False, 'gui_light': 'front',
                                                'textures': {'particle': '#palette'},
                                                'elements': model.parts, 'display': DISPLAY})
    for material, (tag, pt, en) in MATERIALS.items():
        name = f'{material}_{tool}'
        item = 'futuretech:' + name
        write(ASSETS / f'models/item/{name}.json', {'parent': f'futuretech:item/{tool}',
              'textures': {'palette': f'minecraft:item/{material}_{vanilla}'}})
        write(ASSETS / f'items/{name}.json', {'model': {'type': 'minecraft:model', 'model': 'futuretech:item/' + name}})
        if material == 'netherite':
            recipe = {'type': 'minecraft:smithing_transform', 'template': 'minecraft:netherite_upgrade_smithing_template',
                      'base': f'futuretech:diamond_{tool}', 'addition': '#minecraft:' + tag, 'result': {'id': item}}
        else:
            recipe = {'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': pattern,
                      'key': {'M': '#minecraft:' + tag, 'S': 'minecraft:stick'}, 'result': {'id': item, 'count': 1}}
        write(RES / f'data/futuretech/recipe/{name}.json', recipe)
        write(RES / f'data/futuretech/advancement/recipes/tools/{name}.json', {
            'parent': 'minecraft:recipes/root',
            'criteria': {
                'has_material': {'trigger': 'minecraft:inventory_changed',
                                 'conditions': {'items': [{'items': '#minecraft:' + tag}]}},
                'has_the_recipe': {'trigger': 'minecraft:recipe_unlocked', 'conditions': {'recipe': item}},
            },
            'requirements': [['has_material', 'has_the_recipe']], 'rewards': {'recipes': [item]},
        })
    items = [f'futuretech:{material}_{tool}' for material in MATERIALS]
    write(RES / f'data/futuretech/tags/item/{tool}s.json', {'replace': False, 'values': items})
    write(RES / f'data/minecraft/tags/item/{vanilla_tag}.json', {'replace': False, 'values': [f'#futuretech:{tool}s']})
    print(f'Generated 7 {tool}s sharing a {len(model.parts)}-element model and vanilla {vanilla} palettes.')

for language in ('pt_br', 'en_us'):
    path = ASSETS / f'lang/{language}.json'
    translations = json.loads(path.read_text(encoding='utf-8-sig'))
    translations.pop('tooltip.futuretech.hammer.area', None)
    translations.pop('tooltip.futuretech.hammer.sneak', None)
    for tool, (_, _, _, _, pt_name, en_name) in TOOLS.items():
        for material, (_, pt, en) in MATERIALS.items():
            translations[f'item.futuretech.{material}_{tool}'] = (pt_name.format(pt) if language == 'pt_br'
                                                                  else en_name.format(en))
    translations.pop('tooltip.futuretech.area_tool.area', None)
    translations['tooltip.futuretech.area_tool.plane'] = ('Minera uma área 3×3 na face atingida.' if language == 'pt_br'
                                                          else 'Mines a 3×3 area on the struck face.')
    translations['tooltip.futuretech.area_tool.tree'] = ('Derruba a árvore inteira a partir de qualquer tronco.' if language == 'pt_br'
                                                         else 'Fells the whole tree from any of its logs.')
    translations['tooltip.futuretech.area_tool.sneak'] = ('Segure Shift para minerar apenas um bloco.' if language == 'pt_br'
                                                          else 'Hold Shift to mine a single block.')
    write(path, translations)
