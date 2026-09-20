"""Solar panel model, pixel textures, recipes and translations. Run with python -B."""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/futuretech'
DATA = ROOT / 'src/main/resources/data'


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')


def machine_side(mk):
    return 'futuretech:block/machine/' + (f'mk{mk}/' if mk > 1 else '') + 'machine_side'


def machine_corners(image, mk):
    """Use the exact five-pixel L trim of the machines; leave rivets/interior intact."""
    if mk == 1:
        return image
    path = ASSETS / ('textures/' + machine_side(mk).split(':')[1] + '.png')
    with Image.open(path) as source:
        for y in range(32):
            for x in range(32):
                dx, dy = min(x, 31-x), min(y, 31-y)
                if dx < 5 and dy < 5 and (dx == 0 or dy == 0):
                    image.putpixel((x, y), source.getpixel((x, y)))
    return image


def panel(mk=1):
    im = Image.new('RGBA', (32,32), '#505e70')
    d = ImageDraw.Draw(im)
    d.rectangle((0,0,31,0), fill='#c5d3df')
    d.rectangle((0,0,0,31), fill='#9dabbc')
    d.rectangle((1,1,30,30), fill='#161f35')
    for cy in range(4):
        for cx in range(4):
            x,y = 2+cx*7,2+cy*7
            d.rectangle((x,y,x+5,y+5), fill='#183d88')
            d.line((x,y,x+5,y), fill='#457bcb')
            d.line((x,y+3,x+5,y+3), fill='#2b60aa')
            d.line((x+2,y,x+2,y+5), fill='#6196cb')
            d.point((x+4,y+1), fill='#3065b7')
            d.line((x,y+5,x+5,y+5), fill='#112951')
    for x,y in ((0,0),(30,0),(0,30),(30,30)):
        d.point((x,y), fill='#e0e7eb')
    return machine_corners(im, mk)


def body(mk, active):
    im=Image.new('RGBA',(32,32),'#424b57'); d=ImageDraw.Draw(im)
    d.rectangle((1,1,30,30), outline='#7b8794')
    d.rectangle((3,4,28,27), fill='#252c37', outline='#141a22')
    for y in (19,22,25):
        d.line((7,y,24,y), fill='#121922')
        d.line((7,y+1,24,y+1), fill='#596574')
    # Upgraded machines keep their rivets silver; tier colour belongs to the outer corners.
    accent='#dc9455' if mk == 1 else '#b0b4bc'
    for x in (2,28): d.rectangle((x,2,x+1,4), fill=accent)
    sun='#ffdc64' if active else '#6c705f'
    d.rectangle((13,8,18,13),fill=sun)
    for box in ((15,5,16,6),(15,15,16,16),(10,10,11,11),(20,10,21,11)):
        d.rectangle(box,fill=sun)
    return machine_corners(im, mk)


def materials(mk):
    """Purpose-built materials: no complete machine face on thin metal parts."""
    lid=Image.new('RGBA',(32,32),'#39434e'); d=ImageDraw.Draw(lid)
    d.rectangle((0,0,31,31),outline='#788896')
    d.rectangle((3,3,28,28),outline='#252e38')
    for x,y in ((2,2),(28,2),(2,28),(28,28)):
        d.rectangle((x,y,x+1,y+1),fill='#b4c2cc')
    mast=Image.new('RGBA',(32,32),'#6a7b88'); d=ImageDraw.Draw(mast)
    # Brushed steel shaft: broad highlights remain visible on the slim support.
    for x0,x1,color in ((0,3,'#354651'),(4,7,'#91a4b1'),(8,12,'#b8c6ce'),
                        (13,19,'#8095a3'),(20,27,'#536a79'),(28,31,'#293d4b')):
        d.rectangle((x0,0,x1,31),fill=color)
    for y in (4,27): d.line((0,y,31,y),fill='#415866')
    back=Image.new('RGBA',(32,32),'#222d38'); d=ImageDraw.Draw(back)
    d.rectangle((0,0,31,31),outline='#4d606f')
    for x in (5,10,21,26): d.line((x,2,x,29),fill='#344655')
    frame=Image.new('RGBA',(32,32),'#536573'); d=ImageDraw.Draw(frame)
    # Only the top two rows are mapped to the one-pixel-thick edge.
    d.line((0,0,31,0),fill='#adbdc7'); d.line((0,1,31,1),fill='#3e505e')
    if mk>1:
        with Image.open(ASSETS/('textures/'+machine_side(mk).split(':')[1]+'.png')) as source:
            for x in range(32):
                if min(x,31-x)<5:
                    for y in (0,1): frame.putpixel((x,y),source.getpixel((x,0)))
    return {'lid':machine_corners(lid,mk),'mast':mast,'back':back,'frame':frame}


def cube(name, lower, upper, textures):
    return {'name':name,'from':lower,'to':upper,
            'faces':{side:{'texture':'#'+textures.get(side,'side'),'uv':[0,0,16,16]}
                     for side in ('north','south','east','west','up','down')}}


def main():
    tex=ASSETS/'textures/block/solar_generator'; tex.mkdir(parents=True,exist_ok=True)
    panel().save(tex/'panel.png')
    for mk in range(1,5):
        for material,image in materials(mk).items(): image.save(tex/f'{material}_mk{mk}.png')
        panel_name='panel' if mk == 1 else f'panel_mk{mk}'
        if mk > 1:
            panel(mk).save(tex/f'{panel_name}.png')
        for active in (False,True):
            suffix='_on' if active else ''
            name=f'mk{mk}{suffix}'
            body(mk,active).save(tex/f'{name}.png')
            # The panel is not in the block model: SolarPanelRenderer draws it turning with the sun.
            # The item keeps a model of its own with the panel on, so the icon shows the whole thing.
            elements=[
                cube('base',[2,0,2],[14,6,14],{'north':'front','up':'lid','down':'back'}),
                # Support ends are embedded; no surfaces coincide with the base or panel.
                cube('support_foot',[6.75,5.9,6.75],[9.25,6.35,9.25],{s:'mast' for s in ('north','south','east','west','up','down')}),
                cube('support',[7.25,6.2,7.25],[8.75,10.1,8.75],{s:'mast' for s in ('north','south','east','west','up','down')}),
            ]
            # Keep these resting dimensions aligned with SolarPanelGeometry.
            panel_element=cube('solar_panel',[.25,10.75,0],[15.75,11.75,16],
                               {'up':'panel','down':'back',**{s:'frame' for s in ('north','south','east','west')}})
            for side in ('north','south','east','west'): panel_element['faces'][side]['uv']=[0,0,16,1]
            # Fixed rounded bearing: a stepped circular profile across the shaft.
            # Only the panel rotates, about its underside; there is no leaning stem.
            bearing=[]
            for index,(x0,y0,x1,y1) in enumerate(((7.5,9.55,8.5,9.8),
                    (7.125,9.8,8.875,10.8),(7.5,10.8,8.5,11.05))):
                part=cube(f'bearing_{index}',[x0,y0,7.5],[x1,y1,8.5],
                          {face:'mast' for face in ('north','south','east','west','up','down')})
                if index==0: del part['faces']['up']
                if index==2: del part['faces']['down']
                bearing.append(part)
            elements += bearing
            textures={
                'particle':machine_side(mk),
                'side':machine_side(mk),
                'front':f'futuretech:block/solar_generator/{name}',
                'panel':f'futuretech:block/solar_generator/{panel_name}'}
            textures.update({key:f'futuretech:block/solar_generator/{key}_mk{mk}' for key in ('lid','mast','back','frame')})
            write(ASSETS/f'models/block/solar_generator/{name}.json',
                  {'parent':'minecraft:block/block','textures':textures,'elements':elements})
            if not active:
                write(ASSETS/f'models/block/solar_generator/{name}_item.json',
                      {'parent':'minecraft:block/block','textures':textures,'elements':elements+[panel_element]})
    variants={}
    for facing,rotation in (('north',0),('east',90),('south',180),('west',270)):
        for mk in range(1,5):
            for lit in (False,True):
                variants[f'facing={facing},lit={str(lit).lower()},mk={mk}']={
                    'model':f'futuretech:block/solar_generator/mk{mk}'+('_on' if lit else ''),'y':rotation}
    write(ASSETS/'blockstates/solar_generator.json',{'variants':variants})
    item=json.loads((ASSETS/'items/lava_generator.json').read_text().replace('lava_generator','solar_generator'))
    # The item's models are the ones with the panel on.
    for case in item['model']['cases']:
        case['model']['model']+='_item'
    item['model']['fallback']['model']+='_item'
    write(ASSETS/'items/solar_generator.json',item)
    loot=json.loads((DATA/'futuretech/loot_table/blocks/lava_generator.json').read_text().replace('lava_generator','solar_generator'))
    write(DATA/'futuretech/loot_table/blocks/solar_generator.json',loot)
    # The crafting recipe is balanced by hand in data/futuretech/recipe/ and is not written here.
    write(DATA/'futuretech/advancement/recipes/solar_generator.json',{
        'parent':'minecraft:recipes/root','criteria':{
            'has_item':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'futuretech:reception_coil'}]}},
            'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'futuretech:solar_generator'}}},
        'requirements':[['has_item','has_the_recipe']],'rewards':{'recipes':['futuretech:solar_generator']}})
    for tag in ('mineable/pickaxe','needs_stone_tool'):
        path=DATA/f'minecraft/tags/block/{tag}.json'; value=json.loads(path.read_text())
        if 'futuretech:solar_generator' not in value['values']:
            value['values'].append('futuretech:solar_generator'); write(path,value)
    translations={
        'pt_br':('Gerador Solar','Gerando energia','Sem luz solar','Painel coberto','Pausado por redstone','Reserva cheia','Dimensão sem sol','Luz solar: %s%%'),
        'en_us':('Solar Generator','Generating energy','No sunlight','Panel covered','Paused by redstone','Buffer full','Dimension without sunlight','Sunlight: %s%%')}
    for lang,values in translations.items():
        path=ASSETS/f'lang/{lang}.json'; text=path.read_text(encoding='utf-8'); obj=json.loads(text)
        keys=['block.futuretech.solar_generator']+['gui.futuretech.solar.'+key for key in ('active','night','covered','disabled','full','no_sky','sunlight')]
        additions={key:value for key,value in zip(keys,values) if key not in obj}
        if additions:
            # Preserve the existing order and formatting of translations.
            end=text.rfind('}')
            prefix=text[:end].rstrip()
            text=prefix+',\n'+',\n'.join('  '+json.dumps(k)+': '+json.dumps(v,ensure_ascii=False) for k,v in additions.items())+'\n}\n'
            path.write_text(text,encoding='utf-8')
    print('Solar generator: 8 block models, 4 item models, 28 textures, blockstates, recipe, loot, tags and names.')


if __name__ == '__main__': main()
