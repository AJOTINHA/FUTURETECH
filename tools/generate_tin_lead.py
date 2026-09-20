"""Tin, lead and silver, and the electrum alloyed from silver and gold: every texture is iron's, recoloured — vanilla's iron ore, deepslate iron ore,
iron block, iron ingot and raw iron (copies kept under art/), and the mod's own iron powder — plus the JSON around them — blockstates, block and
item models, loot tables, the tags the recipes match on, the worldgen that scatters the ore, and
the crafting and smelting recipes. Plates and gears come from the steel models: the same iron
block texture, tinted to the metal's colour.

Recolouring takes a pixel to its brightness and multiplies it by the metal's tint; on the ores only
the nuggets are touched, told from the stone by their saturation. Tin is a pale silver with a blue
cast, lead a dull blue-grey, silver bright and faintly warm.

Run from any directory: python tools/generate_tin_lead.py
"""
import colorsys
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/futuretech"
DATA = ROOT / "src/main/resources/data"

METALS = {
    "tin": {
        "en": "Tin", "pt": "Estanho",
        # What every iron texture is multiplied by: a pale blue-silver, so it reads apart from iron.
        "tint": 0xC9DCE6,
        # As common as copper: vanilla's ore_copper, one band peaking at Y 48.
        "like": ("cobre", "copper"),
        "bands": [{"name": "", "vein": 10, "count": 16, "shape": "trapezoid", "min": -16, "max": 112}],
    },
    "lead": {
        "en": "Lead", "pt": "Chumbo",
        # A dull blue-grey.
        "tint": 0x7B8698,
        # As common as iron: vanilla's ore_iron_upper, ore_iron_middle and ore_iron_small.
        "like": ("ferro", "iron"),
        "bands": [{"name": "_middle", "vein": 9, "count": 10, "shape": "trapezoid", "min": -24, "max": 56},
                  {"name": "_upper", "vein": 9, "count": 90, "shape": "trapezoid", "min": 80, "max": 384},
                  {"name": "_small", "vein": 4, "count": 10, "shape": "uniform", "min": -64, "max": 72}],
    },
    "silver": {
        "en": "Silver", "pt": "Prata",
        # Thermal's silver: nearly white with a blue cast, its shadows far paler than iron's, which is
        # what the lift does; the tint alone could only darken.
        "tint": 0xE4F0FA, "lift": 0.45,
        # As rare as gold: vanilla's ore_gold and the buried ore_gold_lower (a vein in every other chunk, half discarded at air).
        "like": ("ouro", "gold"),
        "bands": [{"name": "", "vein": 9, "count": 4, "shape": "trapezoid", "min": -64, "max": 32},
                  {"name": "_lower", "vein": 9, "count": (0, 1), "shape": "uniform", "min": -64, "max": -48, "discard": 0.5}],
    },
}


def spawn_lines(metal, lang):
    """What the ore's tooltip says about its bands: the first band as the main line, the rest on a second."""
    bands = metal["bands"]
    like = metal["like"][0 if lang == "pt" else 1]
    first = bands[0]
    if lang == "pt":
        main = f"Gera de Y {first['min']} a Y {first['max']} (como o {like})"
        rest = ", ".join(f"Y {b['min']} a Y {b['max']}" for b in bands[1:])
        second = f"Também em {rest}" if rest else ""
    else:
        main = f"Generates from Y {first['min']} to Y {first['max']} (like {like})"
        rest = ", ".join(f"Y {b['min']} to Y {b['max']}" for b in bands[1:])
        second = f"Also at {rest}" if rest else ""
    return main, second


# Alloys: an ingot and a powder only, made in the smeltery rather than dug up.
ALLOYS = {
    "electrum": {
        "en": "Electrum", "pt": "Electrum",
        # Thermal's electrum: a bright lemon gold, paler than vanilla's gold.
        "tint": 0xF8E060, "lift": 0.35,
    },
}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def ore_texture(metal, source):
    """Vanilla's own iron ore, its nuggets recoloured: the stone (or deepslate) stays as it is, and
    every pixel with iron's warm colour in it — the nuggets, told apart by their saturation — is
    taken to its brightness and multiplied by the metal's tint."""
    image = Image.open(source).convert("RGBA")
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = image.getpixel((x, y))
            _, saturation, _ = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if a and saturation > 0.2:
                image.putpixel((x, y), recolour((r, g, b, a), metal))
    return image


def block(metal):
    """Vanilla's own iron block, tinted."""
    return tinted(ROOT / "art/tin_lead/iron_block.png", metal["tint"], metal.get("lift", 0.0))


def raw_block(metal):
    """Vanilla's own block of raw iron, recoloured like the lump."""
    return tinted(ROOT / "art/tin_lead/raw_iron_block.png", metal["tint"], metal.get("lift", 0.0))


def tinted(source, tint, lift=0.0):
    """A texture recoloured: each pixel taken to its brightness, lifted towards white by {@code lift}
    (0 leaves iron's own greys; silver needs its shadows paler than iron's), and multiplied by {@code tint}."""
    image = Image.open(source).convert("RGBA")
    red, green, blue = tint >> 16 & 255, tint >> 8 & 255, tint & 255
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = image.getpixel((x, y))
            if a:
                grey = 0.30 * r + 0.59 * g + 0.11 * b
                grey = round(grey + (255 - grey) * lift)
                image.putpixel((x, y), (grey * red // 255, grey * green // 255, grey * blue // 255, a))
    return image


def recolour(pixel, metal):
    """One pixel of an ore's nugget, the way {@link tinted} does a whole texture."""
    r, g, b, a = pixel
    tint = metal["tint"]
    red, green, blue = tint >> 16 & 255, tint >> 8 & 255, tint & 255
    grey = 0.30 * r + 0.59 * g + 0.11 * b
    grey = round(grey + (255 - grey) * metal.get("lift", 0.0))
    return grey * red // 255, grey * green // 255, grey * blue // 255, a


def ingot(metal):
    """Vanilla's own iron ingot, tinted: the copy kept in art/steel, the same as the steel ingot's."""
    return tinted(ROOT / "art/steel/iron-ingot-reference.png", metal["tint"], metal.get("lift", 0.0))


def raw(metal):
    """Vanilla's own raw iron, recoloured: the copy kept in art/steel, iron's rust taken to brightness."""
    return tinted(ROOT / "art/steel/raw-iron-reference.png", metal["tint"], metal.get("lift", 0.0))


def powder(metal):
    """The mod's own iron powder, recoloured to the metal, so the three heaps sit together."""
    return tinted(ASSETS / "textures/item/iron_powder.png", metal["tint"], metal.get("lift", 0.0))


def ore_loot(ore, raw_item):
    return {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{
                "type": "minecraft:alternatives",
                "children": [
                    {"type": "minecraft:item", "name": ore, "conditions": [{
                        "condition": "minecraft:match_tool",
                        "predicate": {"predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
                    {"type": "minecraft:item", "name": raw_item, "functions": [
                        {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                        {"function": "minecraft:explosion_decay"}]},
                ],
            }],
        }],
    }


def simple_loot(item):
    return {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": item}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
    }


def recipe_advancement(recipe, item):
    return {
        "parent": "minecraft:recipes/root",
        "criteria": {
            "has_item": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}},
            "has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipe": recipe}},
        },
        "requirements": [["has_item", "has_the_recipe"]],
        "rewards": {"recipes": [recipe]},
    }


def cooking(kind, ingredient, result, time, xp):
    return {"type": f"minecraft:{kind}", "category": "misc", "ingredient": ingredient, "result": {"id": result, "count": 1},
            "experience": xp, "cookingtime": time}


def tag(path, values):
    write_json(path, {"replace": False, "values": values})


def add_to_tag(path, values):
    current = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {"replace": False, "values": []}
    for value in values:
        if value not in current["values"]:
            current["values"].append(value)
    write_json(path, current)


def main():
    textures = ASSETS / "textures"
    for name, metal in METALS.items():
        ore = f"{name}_ore"
        deep = f"deepslate_{name}_ore"
        storage = f"{name}_block"
        raw_storage = f"raw_{name}_block"
        ore_texture(metal, ROOT / "art/tin_lead/iron_ore.png").save(textures / "block" / f"{ore}.png")
        ore_texture(metal, ROOT / "art/tin_lead/deepslate_iron_ore.png").save(textures / "block" / f"{deep}.png")
        block(metal).save(textures / "block" / f"{storage}.png")
        raw_block(metal).save(textures / "block" / f"{raw_storage}.png")
        ingot(metal).save(textures / "item" / f"{name}_ingot.png")
        raw(metal).save(textures / "item" / f"raw_{name}.png")
        powder(metal).save(textures / "item" / f"{name}_powder.png")

        # Blocks: models, blockstates, items, loot.
        for block_name in (ore, deep, storage, raw_storage):
            write_json(ASSETS / "models/block" / f"{block_name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"futuretech:block/{block_name}"}})
        for block_name in (ore, deep, storage, raw_storage):
            write_json(ASSETS / "blockstates" / f"{block_name}.json", {"variants": {"": {"model": f"futuretech:block/{block_name}"}}})
            write_json(ASSETS / "models/item" / f"{block_name}.json", {"parent": f"futuretech:block/{block_name}"})
            write_json(ASSETS / "items" / f"{block_name}.json", {"model": {"type": "minecraft:model", "model": f"futuretech:item/{block_name}"}})
        write_json(DATA / "futuretech/loot_table/blocks" / f"{ore}.json", ore_loot(f"futuretech:{ore}", f"futuretech:raw_{name}"))
        write_json(DATA / "futuretech/loot_table/blocks" / f"{deep}.json", ore_loot(f"futuretech:{deep}", f"futuretech:raw_{name}"))
        write_json(DATA / "futuretech/loot_table/blocks" / f"{storage}.json", simple_loot(f"futuretech:{storage}"))
        write_json(DATA / "futuretech/loot_table/blocks" / f"{raw_storage}.json", simple_loot(f"futuretech:{raw_storage}"))

        # Items: the flat icons, and the plate and gear as the steel models tinted.
        for item in (f"{name}_ingot", f"raw_{name}", f"{name}_powder"):
            model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"futuretech:item/{item}"}}
            # The powders are drawn a third bigger in the inventory, the way the iron powder's model has it.
            if item.endswith("_powder"):
                model["display"] = {"gui": {"scale": [1.3, 1.3, 1.3]}}
            write_json(ASSETS / "models/item" / f"{item}.json", model)
            write_json(ASSETS / "items" / f"{item}.json", {"model": {"type": "minecraft:model", "model": f"futuretech:item/{item}"}})
        for kind in ("plate", "gear"):
            steel = json.loads((ASSETS / "models/item/metal_parts" / f"steel_{kind}.json").read_text(encoding="utf-8"))
            write_json(ASSETS / "models/item/metal_parts" / f"{name}_{kind}.json", steel)
            write_json(ASSETS / "items" / f"{name}_{kind}.json", {"model": {
                "type": "minecraft:model", "model": f"futuretech:item/metal_parts/{name}_{kind}",
                "tints": [{"type": "minecraft:constant", "value": metal["tint"] - 0x1000000}]}})

        # Tags: the common ones the recipes match on, and the tool ones.
        tag(DATA / "c/tags/item/ingots" / f"{name}.json", [f"futuretech:{name}_ingot"])
        tag(DATA / "c/tags/item/dusts" / f"{name}.json", [f"futuretech:{name}_powder"])
        tag(DATA / "c/tags/item/plates" / f"{name}.json", [f"futuretech:{name}_plate"])
        tag(DATA / "c/tags/item/gears" / f"{name}.json", [f"futuretech:{name}_gear"])
        tag(DATA / "c/tags/item/raw_materials" / f"{name}.json", [f"futuretech:raw_{name}"])
        tag(DATA / "c/tags/item/ores" / f"{name}.json", [f"futuretech:{ore}", f"futuretech:{deep}"])
        tag(DATA / "c/tags/block/ores" / f"{name}.json", [f"futuretech:{ore}", f"futuretech:{deep}"])
        tag(DATA / "c/tags/item/storage_blocks" / f"{name}.json", [f"futuretech:{storage}"])
        tag(DATA / "c/tags/block/storage_blocks" / f"{name}.json", [f"futuretech:{storage}"])
        tag(DATA / "c/tags/item/storage_blocks" / f"raw_{name}.json", [f"futuretech:{raw_storage}"])
        tag(DATA / "c/tags/block/storage_blocks" / f"raw_{name}.json", [f"futuretech:{raw_storage}"])
        for group in ("ingots", "dusts", "plates", "gears", "raw_materials", "ores", "storage_blocks"):
            add_to_tag(DATA / "c/tags/item" / f"{group}.json", [f"#c:{group}/{name}"])
        add_to_tag(DATA / "c/tags/item/storage_blocks.json", [f"#c:storage_blocks/raw_{name}"])
        for group in ("ores", "storage_blocks"):
            add_to_tag(DATA / "c/tags/block" / f"{group}.json", [f"#c:{group}/{name}"])
        add_to_tag(DATA / "c/tags/block/storage_blocks.json", [f"#c:storage_blocks/raw_{name}"])
        blocks = [f"futuretech:{ore}", f"futuretech:{deep}", f"futuretech:{storage}", f"futuretech:{raw_storage}"]
        add_to_tag(DATA / "minecraft/tags/block/mineable/pickaxe.json", blocks)
        add_to_tag(DATA / "minecraft/tags/block/needs_stone_tool.json", blocks)

        # Recipes: the furnace ones, and the block both ways.
        recipes = DATA / "futuretech/recipe"
        advancements = DATA / "futuretech/advancement/recipes"
        write_json(recipes / f"{name}_ingot_from_smelting_raw.json", cooking("smelting", f"#c:raw_materials/{name}", f"futuretech:{name}_ingot", 200, 0.7))
        write_json(recipes / f"{name}_ingot_from_blasting_raw.json", cooking("blasting", f"#c:raw_materials/{name}", f"futuretech:{name}_ingot", 100, 0.7))
        write_json(recipes / f"{name}_ingot_from_smelting_ore.json", cooking("smelting", f"#c:ores/{name}", f"futuretech:{name}_ingot", 200, 0.7))
        write_json(recipes / f"{name}_ingot_from_blasting_ore.json", cooking("blasting", f"#c:ores/{name}", f"futuretech:{name}_ingot", 100, 0.7))
        write_json(recipes / f"{name}_ingot_from_powder.json", cooking("smelting", f"#c:dusts/{name}", f"futuretech:{name}_ingot", 200, 0.0))
        write_json(recipes / f"{storage}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["III", "III", "III"],
                                                  "key": {"I": f"#c:ingots/{name}"}, "result": {"id": f"futuretech:{storage}", "count": 1}})
        write_json(recipes / f"{name}_ingot_from_block.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                                "ingredients": [f"futuretech:{storage}"], "result": {"id": f"futuretech:{name}_ingot", "count": 9}})
        write_json(recipes / f"{raw_storage}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["RRR", "RRR", "RRR"],
                                                      "key": {"R": f"#c:raw_materials/{name}"}, "result": {"id": f"futuretech:{raw_storage}", "count": 1}})
        write_json(recipes / f"raw_{name}_from_block.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                                              "ingredients": [f"futuretech:{raw_storage}"], "result": {"id": f"futuretech:raw_{name}", "count": 9}})
        for recipe, item in ((f"{name}_ingot_from_smelting_raw", f"futuretech:raw_{name}"), (f"{name}_ingot_from_blasting_raw", f"futuretech:raw_{name}"),
                             (f"{name}_ingot_from_smelting_ore", f"futuretech:{ore}"), (f"{name}_ingot_from_blasting_ore", f"futuretech:{ore}"),
                             (f"{name}_ingot_from_powder", f"futuretech:{name}_powder"), (storage, f"futuretech:{name}_ingot"),
                             (f"{name}_ingot_from_block", f"futuretech:{storage}"), (raw_storage, f"futuretech:raw_{name}"),
                             (f"raw_{name}_from_block", f"futuretech:{raw_storage}")):
            write_json(advancements / f"{recipe}.json", recipe_advancement(f"futuretech:{recipe}", item))

        # Worldgen: veins in stone and deepslate, everywhere in the overworld, one placed feature per
        # band and one configured feature per vein size and discard chance, the way vanilla lays out its own ores.
        placed = []
        for band in metal["bands"]:
            feature = f"{ore}{band['name']}"
            discard = band.get("discard", 0.0)
            write_json(DATA / "futuretech/worldgen/configured_feature" / f"{feature}.json", {
                "type": "minecraft:ore",
                "config": {"size": band["vein"], "discard_chance_on_air_exposure": discard, "targets": [
                    {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}, "state": {"Name": f"futuretech:{ore}"}},
                    {"target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}, "state": {"Name": f"futuretech:{deep}"}},
                ]}})
            count = band["count"]
            if isinstance(count, tuple):
                count = {"type": "minecraft:uniform", "min_inclusive": count[0], "max_inclusive": count[1]}
            write_json(DATA / "futuretech/worldgen/placed_feature" / f"{feature}.json", {
                "feature": f"futuretech:{feature}",
                "placement": [
                    {"type": "minecraft:count", "count": count},
                    {"type": "minecraft:in_square"},
                    {"type": "minecraft:height_range", "height": {"type": f"minecraft:{band['shape']}",
                                                                  "min_inclusive": {"absolute": band["min"]}, "max_inclusive": {"absolute": band["max"]}}},
                    {"type": "minecraft:biome"},
                ]})
            placed.append(f"futuretech:{feature}")
        write_json(DATA / "futuretech/neoforge/biome_modifier" / f"{ore}.json", {
            "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld", "features": placed, "step": "underground_ores"})
        print(f"  {name}: textures, models, loot, tags, recipes, worldgen")

    for name, alloy in ALLOYS.items():
        ingot(alloy).save(textures / "item" / f"{name}_ingot.png")
        powder(alloy).save(textures / "item" / f"{name}_powder.png")
        for item in (f"{name}_ingot", f"{name}_powder"):
            model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"futuretech:item/{item}"}}
            if item.endswith("_powder"):
                model["display"] = {"gui": {"scale": [1.3, 1.3, 1.3]}}
            write_json(ASSETS / "models/item" / f"{item}.json", model)
            write_json(ASSETS / "items" / f"{item}.json", {"model": {"type": "minecraft:model", "model": f"futuretech:item/{item}"}})
        tag(DATA / "c/tags/item/ingots" / f"{name}.json", [f"futuretech:{name}_ingot"])
        tag(DATA / "c/tags/item/dusts" / f"{name}.json", [f"futuretech:{name}_powder"])
        add_to_tag(DATA / "c/tags/item/ingots.json", [f"#c:ingots/{name}"])
        add_to_tag(DATA / "c/tags/item/dusts.json", [f"#c:dusts/{name}"])
        recipes = DATA / "futuretech/recipe"
        write_json(recipes / f"{name}_ingot_from_powder.json", cooking("smelting", f"#c:dusts/{name}", f"futuretech:{name}_ingot", 200, 0.0))
        write_json(DATA / "futuretech/advancement/recipes" / f"{name}_ingot_from_powder.json",
                   recipe_advancement(f"futuretech:{name}_ingot_from_powder", f"futuretech:{name}_powder"))
        print(f"  {name}: textures, models, tags, recipes")

    # Lang, both languages, after the copper gear.
    for lang, key in (("en_us", "en"), ("pt_br", "pt")):
        path = ASSETS / "lang" / f"{lang}.json"
        text = path.read_text(encoding="utf-8")
        lines = []
        for name, metal in METALS.items():
            word = metal[key]
            if key == "en":
                entries = {f"block.futuretech.{name}_ore": f"{word} Ore", f"block.futuretech.deepslate_{name}_ore": f"Deepslate {word} Ore",
                           f"block.futuretech.{name}_block": f"Block of {word}", f"block.futuretech.raw_{name}_block": f"Block of Raw {word}",
                           f"item.futuretech.raw_{name}": f"Raw {word}",
                           f"item.futuretech.{name}_ingot": f"{word} Ingot", f"item.futuretech.{name}_powder": f"{word} Powder",
                           f"item.futuretech.{name}_plate": f"{word} Plate", f"item.futuretech.{name}_gear": f"{word} Gear"}
            else:
                entries = {f"block.futuretech.{name}_ore": f"Minério de {word}", f"block.futuretech.deepslate_{name}_ore": f"Minério de {word} de Ardósia",
                           f"block.futuretech.{name}_block": f"Bloco de {word}", f"block.futuretech.raw_{name}_block": f"Bloco de {word} Bruto",
                           f"item.futuretech.raw_{name}": f"{word} Bruto",
                           f"item.futuretech.{name}_ingot": f"Lingote de {word}", f"item.futuretech.{name}_powder": f"Pó de {word}",
                           f"item.futuretech.{name}_plate": f"Placa de {word}", f"item.futuretech.{name}_gear": f"Engrenagem de {word}"}
            main, second = spawn_lines(metal, key)
            entries[f"block.futuretech.{name}_ore.spawn"] = main
            entries[f"block.futuretech.{name}_ore.spawn2"] = second
            for entry, value in entries.items():
                if f'"{entry}"' not in text:
                    lines.append(f'  "{entry}": {json.dumps(value, ensure_ascii=False)},\n')
        for name, alloy in ALLOYS.items():
            word = alloy[key]
            entries = {f"item.futuretech.{name}_ingot": f"{word} Ingot" if key == "en" else f"Lingote de {word}",
                       f"item.futuretech.{name}_powder": f"{word} Powder" if key == "en" else f"Pó de {word}"}
            for entry, value in entries.items():
                if f'"{entry}"' not in text:
                    lines.append(f'  "{entry}": {json.dumps(value, ensure_ascii=False)},\n')
        anchor = '  "item.futuretech.copper_gear":'
        if lines and anchor in text:
            end = text.index("\n", text.index(anchor)) + 1
            text = text[:end] + "".join(lines) + text[end:]
            path.write_text(text, encoding="utf-8")
    print("  lang: en_us, pt_br")


if __name__ == "__main__":
    print("estanho, chumbo, prata e electrum:")
    main()
    print("pronto.")
