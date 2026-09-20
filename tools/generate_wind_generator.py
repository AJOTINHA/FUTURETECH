"""Reproducible wind turbine assets. Dedicated materials, with the machines' MK trim."""
import json
from copy import deepcopy
from PIL import Image, ImageDraw
from generate_solar_generator import ASSETS, DATA, write, cube, machine_corners, machine_side

FACES=('north','south','east','west','up','down')

def solid(name, lower, upper, material):
    return cube(name,lower,upper,{s:material for s in FACES})

def main():
    folder=ASSETS/'textures/block/wind_generator'; folder.mkdir(parents=True,exist_ok=True)
    steel=Image.new('RGBA',(16,16),'#718793'); draw=ImageDraw.Draw(steel)
    for x0,x1,color in ((0,1,'#394d59'),(2,4,'#aabec7'),(5,8,'#8da5b2'),(12,15,'#4b6473')):
        draw.rectangle((x0,0,x1,15),fill=color)
    steel.save(folder/'steel.png')
    tower=Image.new('RGBA',(16,32),'#d9dfe0'); d=ImageDraw.Draw(tower)
    d.rectangle((0,0,1,31),fill='#f3f5ef'); d.rectangle((12,0,15,31),fill='#8c9da4')
    d.rectangle((6,0,8,31),fill='#657982'); d.line((5,0,5,31),fill='#b4c1c5')
    tower.save(folder/'tower.png')
    # Underside of the motor: a sealed plate around the tower mount, with no vents.
    bottom=Image.new('RGBA',(32,32),'#aebdc4'); d=ImageDraw.Draw(bottom)
    d.rectangle((0,0,31,31),outline='#536a76')
    d.line((1,1,30,1),fill='#e2e9e8'); d.line((1,1,1,30),fill='#d0dcde')
    d.line((2,30,30,30),fill='#7a909c'); d.line((30,2,30,30),fill='#7a909c')
    # The mast meets Z8; on the downward-facing UVs this is near row 20.
    d.rectangle((8,12,23,28),fill='#637c89',outline='#405866')
    d.rectangle((9,13,22,27),outline='#d0dbde')
    d.rectangle((11,15,20,25),fill='#8ca1ac')
    for x,y in ((3,3),(26,3),(3,26),(26,26)):
        d.rectangle((x,y,x+2,y+2),fill='#4c6370')
        d.line((x,y,x+1,y),fill='#edf1e9')
        d.point((x+1,y+1),fill='#b8c8ce')
    bottom.save(folder/'housing_bottom.png')
    for mk in range(1,5):
        accent=(204,147,79,255) if mk==1 else Image.open(ASSETS/('textures/'+machine_side(mk).split(':')[1]+'.png')).getpixel((2,0))
        blade=Image.new('RGBA',(8,32),'#e5e9e5'); d=ImageDraw.Draw(blade)
        d.rectangle((0,0,0,31),fill='#f1f4ef'); d.rectangle((6,0,7,31),fill='#617d8c')
        d.rectangle((1,0,5,2),fill=accent); d.line((1,3,5,3),fill='#81939a')
        d.line((2,8,2,29),fill='#dce6e6'); d.rectangle((1,29,5,31),fill='#7c97a4')
        # Drawn at 8x32 and doubled: a sprite narrower than 16 px would cap the whole block atlas at mip level 3.
        blade.resize((16,64),Image.NEAREST).save(folder/f'blade_mk{mk}.png')
        lid=Image.new('RGBA',(32,32),'#394b59'); d=ImageDraw.Draw(lid)
        d.rectangle((1,1,30,30),outline='#8b9daa'); d.rectangle((4,4,27,27),outline='#263340')
        for x,y in ((3,3),(27,3),(3,27),(27,27)): d.rectangle((x,y,x+1,y+1),fill='#becbd0')
        machine_corners(lid,mk).save(folder/f'lid_mk{mk}.png')
        housing=Image.new('RGBA',(32,32),'#cbd4d7'); d=ImageDraw.Draw(housing)
        d.rectangle((0,0,31,31),outline='#f0f3ed'); d.rectangle((2,2,29,4),fill=accent)
        for y in (7,11,15,19,23): d.line((5,y,26,y),fill='#1d313e')
        housing.save(folder/f'housing_mk{mk}.png')
        for on in (False,True):
            name=f'mk{mk}'+('_on' if on else '')
            body=Image.new('RGBA',(32,16),'#344b59'); d=ImageDraw.Draw(body)
            d.line((0,0,31,0),fill='#8398a6'); d.line((0,15,31,15),fill='#172730')
            for x in (0,31):
                d.line((x,0,x,2),fill=accent); d.line((x,13,x,15),fill=accent)
            for y in (0,15):
                d.line((0,y,4,y),fill=accent); d.line((27,y,31,y),fill=accent)
            for x in (4,7,22,25): d.rectangle((x,5,x+1,10),fill='#10202b')
            d.rectangle((11,4,20,11),fill='#101e28'); d.rectangle((12,5,19,10),fill='#62d6b0' if on else '#54786f'); d.rectangle((14,7,17,8),fill='#a8ede0' if on else '#7baba0')
            body.save(folder/f'{name}.png')
            textures={key:f'futuretech:block/wind_generator/{value}' for key,value in {
                'side':name,'particle':name,'lid':f'lid_mk{mk}','steel':'steel',
                'housing':f'housing_mk{mk}','housing_bottom':'housing_bottom','blade':f'blade_mk{mk}','tower':'tower'}.items()}
            elements=[cube('base',[1,0,1],[15,8,15],{'up':'lid','down':'lid'})]
            model={'parent':'minecraft:block/block','textures':textures,'elements':elements}
            write(ASSETS/f'models/block/wind_generator/{name}.json',model)
            if not on:
                write(ASSETS/f'models/block/wind_generator/mk{mk}_item.json',{
                    'parent':'minecraft:block/block','textures':textures,'elements':[],
                    'display':{'gui':{'rotation':[15,145,0],'translation':[0,0,0],'scale':[1.1,1.1,1.1]},
                               'ground':{'translation':[0,3,0],'scale':[.7,.7,.7]},
                               'fixed':{'rotation':[0,180,0],'scale':[.8,.8,.8]}}})
    # Explicit atlas entries include the materials drawn only by the dynamic renderer.
    atlas=ASSETS/'atlases/blocks.json'; obj=json.loads(atlas.read_text())
    for texture in folder.glob('*.png'):
        source={'type':'single','resource':'futuretech:block/wind_generator/'+texture.stem}
        if source not in obj['sources']: obj['sources'].append(source)
    write(atlas,obj)
    for path in ('blockstates/solar_generator.json','items/solar_generator.json'):
        write(ASSETS/path.replace('solar_generator','wind_generator'),json.loads((ASSETS/path).read_text().replace('solar_generator','wind_generator')))
    item=json.loads((ASSETS/'items/wind_generator.json').read_text())
    for mk,entry in [(1,item['model']['fallback'])]+[(int(case['when']),case['model']) for case in item['model']['cases']]:
        entry.clear(); entry.update({'type':'minecraft:special','base':f'futuretech:block/wind_generator/mk{mk}_item',
                                    'model':{'type':'futuretech:wind_turbine','mk':mk}})
    write(ASSETS/'items/wind_generator.json',item)
    write(ASSETS/'models/block/wind_generator/part.json',{'textures':{'particle':'futuretech:block/wind_generator/steel'},'elements':[]})
    write(ASSETS/'blockstates/wind_turbine_part.json',{'variants':{'':{'model':'futuretech:block/wind_generator/part'}}})
    for path in ('loot_table/blocks/solar_generator.json','advancement/recipes/solar_generator.json'):
        write(DATA/'futuretech'/path.replace('solar_generator','wind_generator'),json.loads((DATA/'futuretech'/path).read_text().replace('solar_generator','wind_generator')))
    write(DATA/'futuretech/recipe/wind_generator.json',{
        'type':'minecraft:crafting_shaped','category':'misc','pattern':[' I ','IRI','CMC'],
        'key':{'I':'#c:ingots/iron','R':'futuretech:reception_coil','C':'#c:ingots/copper','M':'futuretech:machine_casing'},
        'result':{'id':'futuretech:wind_generator','count':1}})
    for tag in ('mineable/pickaxe','needs_stone_tool'):
        path=DATA/f'minecraft/tags/block/{tag}.json'; obj=json.loads(path.read_text())
        for block in ('wind_generator','wind_turbine_part'):
            if 'futuretech:'+block not in obj['values']: obj['values'].append('futuretech:'+block)
        write(path,obj)
    translations={
        'pt_br':('Gerador Eólico','Gerando energia','Vento insuficiente','Turbina obstruída','Pausado por redstone','Reserva cheia','Dimensão sem vento','Vento: %s%% (altura e espaço livre)'),
        'en_us':('Wind Generator','Generating energy','Insufficient wind','Turbine obstructed','Paused by redstone','Buffer full','Dimension without wind','Wind: %s%% (height and clearance)')}
    for lang,values in translations.items():
        path=ASSETS/f'lang/{lang}.json'; text=path.read_text(encoding='utf-8'); obj=json.loads(text)
        keys=['block.futuretech.wind_generator']+['gui.futuretech.wind.'+key for key in ('active','calm','obstructed','disabled','full','no_sky','windStrength')]
        additions={k:v for k,v in zip(keys,values) if k not in obj}
        if additions:
            text=text[:text.rfind('}')].rstrip()+',\n'+',\n'.join('  '+json.dumps(k)+': '+json.dumps(v,ensure_ascii=False) for k,v in additions.items())+'\n}\n'
            path.write_text(text,encoding='utf-8')
    print('Wind generator assets generated.')

if __name__=='__main__': main()
