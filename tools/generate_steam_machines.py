"""Boiler and steam turbine pixel-art assets, recipes and names."""
import json
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw
from generate_solar_generator import ASSETS, DATA, write
from generate_lava_generator import TEXTURES, DARK, STEEL, STEEL_DARK, INNER_X0, INNER_X1, INNER_Y0, INNER_Y1, fill, status_light

# Palette of the shared front, plus the copper and fire of the boiler and the steel of the blades.
COPPER=(184,115,51,255); COPPER_LIGHT=(226,160,92,255); COPPER_DARK=(120,72,34,255)
GLASS_RIM=(150,160,170,255); WATER=(58,120,196,255); WATER_LIGHT=(110,170,230,255)
COAL=(38,34,34,255); COAL_OFF=(52,44,42,255); EMBER=(140,50,18,255)
FLAME=(248,98,6,255); FLAME_WARM=(253,159,7,255); FLAME_BRIGHT=(255,214,110,255)
BLADE=(192,212,218,255); BLADE_EDGE=(107,137,152,255); BLADE_DIM=(120,134,140,255)
HUB=(214,228,228,255); RIM=(165,185,195,255); RIM_DARK=(59,91,110,255)
BOILER_FRAMES=8; TURBINE_FRAMES=4

def window(image):
    """The dark interior behind the window frame, the same the lava generator shows its tank in."""
    fill(image,INNER_X0,INNER_Y0,INNER_X1,INNER_Y1,DARK)

def boiler_front(on,frame,rng):
    image=Image.open(TEXTURES/'smeltery/smeltery_front.png').convert('RGBA'); status_light(image,on); window(image)
    d=ImageDraw.Draw(image)
    # Copper drum across the top of the window with its round sight glass, water standing inside.
    d.rounded_rectangle((INNER_X0+1,INNER_Y0,INNER_X1-1,INNER_Y0+7),radius=2,fill=COPPER,outline=COPPER_DARK)
    d.line((INNER_X0+2,INNER_Y0+1,INNER_X1-2,INNER_Y0+1),fill=COPPER_LIGHT)
    for x in (INNER_X0+2,INNER_X1-2): d.line((x,INNER_Y0+2,x,INNER_Y0+5),fill=COPPER_DARK)
    d.ellipse((13,INNER_Y0+1,18,INNER_Y0+6),fill=DARK,outline=GLASS_RIM)
    fill(image,14,INNER_Y0+3,17,INNER_Y0+5,WATER); fill(image,14,INNER_Y0+3,17,INNER_Y0+3,WATER_LIGHT)
    if on: image.putpixel((15+(frame//2)%2,INNER_Y0+2),WATER_LIGHT)
    # Steel shelf, then the firebox with its grate; the coal glows and the flames flicker while lit.
    fill(image,INNER_X0,INNER_Y0+8,INNER_X1,INNER_Y0+8,STEEL); fill(image,INNER_X0,INNER_Y0+9,INNER_X1,INNER_Y0+9,STEEL_DARK)
    fill(image,INNER_X0+1,INNER_Y1-2,INNER_X1-1,INNER_Y1,COAL if on else COAL_OFF)
    for x in range(INNER_X0+2,INNER_X1,3): fill(image,x,INNER_Y1-1,x,INNER_Y1,STEEL_DARK)
    if not on:
        for x in (8,12,17,21): image.putpixel((x,INNER_Y1-2),EMBER)
        return image
    for x in range(INNER_X0+1,INNER_X1,2): image.putpixel((x,INNER_Y1-2),EMBER)
    for base_x,phase in ((8,0),(11,3),(14,5),(17,1),(20,6),(23,4)):
        height=2+((frame+phase)%4); top=INNER_Y1-2-height
        fill(image,base_x,top+1,base_x+1,INNER_Y1-3,FLAME)
        fill(image,base_x,INNER_Y1-4,base_x+1,INNER_Y1-3,FLAME_WARM)
        image.putpixel((base_x+rng.randint(0,1),top),FLAME_BRIGHT if height>3 else FLAME_WARM)
    return image

def turbine_front(on,angle):
    image=Image.open(TEXTURES/'smeltery/smeltery_front.png').convert('RGBA'); status_light(image,on); window(image)
    d=ImageDraw.Draw(image)
    # Round rim filling the window height, four straight blades turning behind the hub.
    cx,cy=15.5,INNER_Y0+7.5
    d.ellipse((8,INNER_Y0,23,INNER_Y1),fill=DARK,outline=RIM)
    blade,edge=(BLADE,BLADE_EDGE) if on else (BLADE_DIM,RIM_DARK)
    for arm in range(4):
        a=math.radians(angle+arm*90)
        tip=(cx+6*math.cos(a),cy+6*math.sin(a))
        d.line((cx,cy,tip[0],tip[1]),fill=edge,width=3)
        d.line((cx,cy,tip[0],tip[1]),fill=blade,width=1)
    d.ellipse((14,INNER_Y0+6,17,INNER_Y0+9),fill=HUB,outline=BLADE_EDGE)
    return image

def sheet(frames,frametime,path):
    image=Image.new('RGBA',(32,32*len(frames)))
    for index,frame in enumerate(frames): image.paste(frame,(0,32*index))
    image.save(path)
    write(Path(str(path)+'.mcmeta'),{'animation':{'width':32,'height':32,'frametime':frametime,'interpolate':False}})

def textures():
    for kind in ('boiler','steam_turbine'):
        folder=TEXTURES/kind; folder.mkdir(parents=True,exist_ok=True)
        for stale in list(folder.glob('mk*.png'))+list(folder.glob('mk*.mcmeta'))+list(folder.glob('side_mk*.png'))+list(folder.glob('top_mk*.png')): stale.unlink()
    rng=random.Random(11)
    boiler_front(False,0,rng).save(TEXTURES/'boiler/boiler_front.png')
    sheet([boiler_front(True,frame,rng) for frame in range(BOILER_FRAMES)],3,TEXTURES/'boiler/boiler_front_on.png')
    turbine_front(False,0).save(TEXTURES/'steam_turbine/steam_turbine_front.png')
    sheet([turbine_front(True,frame*22.5) for frame in range(TURBINE_FRAMES)],2,TEXTURES/'steam_turbine/steam_turbine_front_on.png')

def copy_json(source,target,kind):
    """The MK1..MK4 block models copy the smeltery's: shared machine sides, coloured MK corners, only the front swapped."""
    text=(ASSETS/source).read_text(encoding='utf-8').replace('smeltery',kind)
    path=ASSETS/target; path.parent.mkdir(parents=True,exist_ok=True); path.write_text(text,encoding='utf-8')

def models():
    for kind in ('boiler','steam_turbine'):
        for mk in range(1,5):
            for suffix in ('','_on'):
                copy_json(f'models/block/smeltery/mk{mk}{suffix}.json',f'models/block/{kind}/mk{mk}{suffix}.json',kind)

def main():
    steam=Image.new('RGBA',(32,32),'#c4d9df');d=ImageDraw.Draw(steam)
    for y in range(32):
        for x in range(32):
            v=round(209+12*math.sin(x*.35+y*.23)+7*math.cos(y*.61-x*.2))
            d.point((x,y),fill=(v,min(255,v+10),min(255,v+13),255))
    steam.save(ASSETS/'textures/block/steam.png')
    atlas=ASSETS/'atlases/blocks.json';obj=json.loads(atlas.read_text())
    source={'type':'single','resource':'futuretech:block/steam'}
    if source not in obj['sources']: obj['sources'].append(source)
    write(atlas,obj)
    textures(); models()
    for kind in ('boiler','steam_turbine'):
        for path in ('blockstates/lava_generator.json','items/lava_generator.json'):
            write(ASSETS/path.replace('lava_generator',kind),json.loads((ASSETS/path).read_text().replace('lava_generator',kind)))
        for path in ('loot_table/blocks/lava_generator.json',):
            write(DATA/'futuretech'/path.replace('lava_generator',kind),json.loads((DATA/'futuretech'/path).read_text().replace('lava_generator',kind)))
        write(DATA/f'futuretech/advancement/recipes/{kind}.json',{
            'parent':'minecraft:recipes/root','criteria':{
                'has_item':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'futuretech:machine_casing'}]}},
                'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':f'futuretech:{kind}'}}},
            'requirements':[['has_item','has_the_recipe']],'rewards':{'recipes':[f'futuretech:{kind}']}})
        for tag in ('mineable/pickaxe','needs_stone_tool'):
            path=DATA/f'minecraft/tags/block/{tag}.json';obj=json.loads(path.read_text())
            if 'futuretech:'+kind not in obj['values']: obj['values'].append('futuretech:'+kind); write(path,obj)
    write(DATA/'futuretech/recipe/boiler.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':['CGC','CFC','CMC'],
        'key':{'C':'#c:ingots/copper','G':'minecraft:glass','F':'minecraft:furnace','M':'futuretech:machine_casing'},'result':{'id':'futuretech:boiler','count':1}})
    write(DATA/'futuretech/recipe/steam_turbine.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':['IRI','CMC','IRI'],
        'key':{'I':'#c:ingots/iron','C':'#c:ingots/copper','R':'futuretech:transmission_coil','M':'futuretech:machine_casing'},'result':{'id':'futuretech:steam_turbine','count':1}})
    names={
        'block.futuretech.boiler':('Boiler','Boiler'),
        'block.futuretech.steam_turbine':('Turbina a Vapor','Steam Turbine'),
        'fluid.futuretech.steam':('Vapor','Steam'),
        'gui.futuretech.boiler.water':('Água','Water'),
        'gui.futuretech.boiler.fuel':('Combustível','Fuel'),
        'gui.futuretech.boiler.active':('Produzindo vapor','Producing steam'),
        'gui.futuretech.boiler.no_water':('Sem água','No water'),
        'gui.futuretech.boiler.no_fuel':('Sem combustível','No fuel'),
        'gui.futuretech.boiler.full':('Tanque de vapor cheio','Steam tank full'),
        'gui.futuretech.boiler.disabled':('Pausado por redstone','Paused by redstone'),
        'gui.futuretech.steam_turbine.active':('Gerando energia','Generating energy'),
        'gui.futuretech.steam_turbine.no_steam':('Sem vapor','No steam'),
        'gui.futuretech.steam_turbine.disabled':('Pausada por redstone','Paused by redstone'),
        'gui.futuretech.steam_turbine.full':('Reserva de energia cheia','Energy buffer full')}
    for index,lang in enumerate(('pt_br','en_us')):
        path=ASSETS/f'lang/{lang}.json';text=path.read_text(encoding='utf-8');obj=json.loads(text)
        additions={k:v[index] for k,v in names.items() if k not in obj}
        if additions:
            text=text[:text.rfind('}')].rstrip()+',\n'+',\n'.join('  '+json.dumps(k)+': '+json.dumps(v,ensure_ascii=False) for k,v in additions.items())+'\n}\n'
            path.write_text(text,encoding='utf-8')
    print('Boiler, steam turbine and steam resources generated.')

if __name__=='__main__': main()
