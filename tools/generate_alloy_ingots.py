"""Red alloy and ender alloy: ingots alloyed in the smeltery from a vanilla ingot and two of something.
Only the ingot exists - no block, powder, plate or gear. Each texture is vanilla's iron ingot
recoloured, the way the tin, lead and silver ingots are; the item model, the `c:ingots/<name>` tag,
the smeltery recipe and the names come with it. The crafting recipes that take the ingots (redstone
cable, tesseract, teleporter) live in data/futuretech/recipe/ and are balanced by hand.

Run from any directory: python tools/generate_alloy_ingots.py
"""
import json

from generate_tin_lead import ASSETS, DATA, ROOT, add_to_tag, tag, tinted, write_json

ALLOYS = {
    "red_alloy": {
        "en": "Red Alloy Ingot", "pt": "Lingote de Liga Vermelha",
        # Redstone's own red, lifted a little so the ingot's shadows stay red rather than going black.
        "tint": 0xE8382A, "lift": 0.12,
        "first": "#c:ingots/iron", "second": "minecraft:redstone",
    },
    "ender_alloy": {
        "en": "Ender Alloy Ingot", "pt": "Lingote de Liga do End",
        # The ender pearl's teal green, lifted the same way.
        "tint": 0x2FBF8A, "lift": 0.12,
        "first": "#c:ingots/gold", "second": "minecraft:ender_pearl",
    },
}


def main():
    smeltery = ROOT / "src/main/recipes/smeltery.json"
    book = json.loads(smeltery.read_text(encoding="utf-8"))
    for name, alloy in ALLOYS.items():
        item = f"{name}_ingot"
        tinted(ROOT / "art/steel/iron-ingot-reference.png", alloy["tint"], alloy["lift"]).save(ASSETS / "textures/item" / f"{item}.png")
        write_json(ASSETS / "models/item" / f"{item}.json",
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"futuretech:item/{item}"}})
        write_json(ASSETS / "items" / f"{item}.json", {"model": {"type": "minecraft:model", "model": f"futuretech:item/{item}"}})
        tag(DATA / "c/tags/item/ingots" / f"{name}.json", [f"futuretech:{item}"])
        add_to_tag(DATA / "c/tags/item/ingots.json", [f"#c:ingots/{name}"])
        # Smeltery: one ingot and two of the other make one, in the steel ingot's time.
        book["recipes"][item] = {
            "first": {"ingredient": alloy["first"], "count": 1},
            "second": {"ingredient": alloy["second"], "count": 2},
            "result": {"count": 1, "id": f"futuretech:{item}"},
            "duration": 200,
        }
        for lang, key in (("en_us", "en"), ("pt_br", "pt")):
            path = ASSETS / "lang" / f"{lang}.json"
            text = path.read_text(encoding="utf-8")
            entry = f"item.futuretech.{item}"
            if f'"{entry}"' not in text:
                anchor = '  "item.futuretech.electrum_ingot":'
                end = text.index("\n", text.index(anchor)) + 1
                text = text[:end] + f'  "{entry}": {json.dumps(alloy[key], ensure_ascii=False)},\n' + text[end:]
                path.write_text(text, encoding="utf-8")
        print(f"  {name}: texture, model, tag, smeltery recipe, lang")
    smeltery.write_text(json.dumps(book, indent=2) + "\n", encoding="utf-8")

    # The crafting recipes that take the alloys (redstone cable, tesseract, teleporter) are balanced
    # by hand in data/futuretech/recipe/ and are not written here.


if __name__ == "__main__":
    main()
