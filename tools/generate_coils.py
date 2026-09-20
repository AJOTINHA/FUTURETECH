"""The redstone coils, Thermal Expansion 5's three: reception (gold), transmission (silver) and
conductance (electrum). Shared 3D geometry and 32px material atlases, plus
item definitions, crafting recipes, advancements and names. Use --assets-only
to regenerate the visual assets without changing gameplay data.

Run from any directory: python tools/generate_coils.py
"""
import argparse
import json
from pathlib import Path

from coil_assets import coil_model, material_atlas

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/futuretech"
DATA = ROOT / "src/main/resources/data"

# The wire's colours, light to dark, and what the recipe winds it from.
COILS = {
    "reception_coil": {
        "en": "Redstone Reception Coil", "pt": "Bobina de Recepção de Redstone",
        # Saturated amber gold stays distinct from the pale electrum below.
        "wire": ((255, 224, 112, 255), (246, 174, 32, 255), (190, 112, 16, 255), (117, 62, 12, 255)),
        "ingot": "#c:ingots/gold", "pattern": ["  R", " G ", "R  "],
    },
    "transmission_coil": {
        "en": "Redstone Transmission Coil", "pt": "Bobina de Transmissão de Redstone",
        "wire": ((250, 252, 255, 255), (222, 232, 240, 255), (168, 184, 200, 255), (110, 124, 140, 255)),
        "ingot": "#c:ingots/silver", "pattern": ["  R", " G ", "R  "],
    },
    "conductance_coil": {
        "en": "Redstone Conductance Coil", "pt": "Bobina de Condutância de Redstone",
        # Cream highlights and champagne shadows distinguish electrum from gold and silver.
        "wire": ((255, 255, 231, 255), (239, 233, 181, 255), (193, 187, 132, 255), (132, 130, 88, 255)),
        "ingot": "#c:ingots/electrum", "pattern": ["R  ", " G ", "  R"],
    },
}


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


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


def main(assets_only=False):
    (ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)
    write_json(ASSETS / "models/item/coil.json", coil_model())
    for name, coil in COILS.items():
        material_atlas(coil).save(ASSETS / "textures/item" / f"{name}.png")
        write_json(ASSETS / "models/item" / f"{name}.json", {"parent": "futuretech:item/coil", "textures": {"material": f"futuretech:item/{name}"}})
        write_json(ASSETS / "items" / f"{name}.json", {"model": {"type": "minecraft:model", "model": f"futuretech:item/{name}"}})
        if assets_only:
            print(f"  {name}: 3D model, material atlas")
            continue
        # The coils are made in the Assembler (src/main/recipes/assembler.json), not on the crafting table.
        print(f"  {name}: texture, model")
    if assets_only:
        return
    for lang, key in (("en_us", "en"), ("pt_br", "pt")):
        path = ASSETS / "lang" / f"{lang}.json"
        text = path.read_text(encoding="utf-8")
        lines = [f'  "item.futuretech.{name}": {json.dumps(coil[key], ensure_ascii=False)},\n'
                 for name, coil in COILS.items() if f'"item.futuretech.{name}"' not in text]
        anchor = '  "item.futuretech.electrum_powder":'
        if lines and anchor in text:
            end = text.index("\n", text.index(anchor)) + 1
            path.write_text(text[:end] + "".join(lines) + text[end:], encoding="utf-8")
    print("  lang: en_us, pt_br")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets-only", action="store_true", help="Only regenerate models and textures")
    args = parser.parse_args()
    print("bobinas:")
    main(assets_only=args.assets_only)
    print("pronto.")
