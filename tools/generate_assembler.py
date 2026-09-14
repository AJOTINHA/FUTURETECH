"""Export the Assembler's native box models and supporting Minecraft data."""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/futuretech'
DATA = ROOT / 'data/futuretech'
NAMES = ['assembly_table', 'transport_arm', 'assembly_arm', 'assembler_terminal']

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

UV = {'dark': [1, 1, 7, 7], 'gray': [9, 1, 15, 7], 'silver': [1, 9, 7, 15], 'cyan': [9, 9, 15, 15]}
# Minecraft 26.2 requires each cuboid model to use a single atlas, and block models
# must use the blocks atlas. Keep the shared palette in that atlas for this cell.
shutil.copyfile(ASSETS / 'textures/item/filter_materials.png', ASSETS / 'textures/block/assembler_materials.png')
TEXTURES = {'palette': 'futuretech:block/assembler_materials', 'particle': 'futuretech:block/machine_side',
            'blue': 'futuretech:block/battery_port_input', 'orange': 'futuretech:block/battery_port_output'}

def box(a, b, material='dark', rotation=None):
    texture = material if material in ['blue', 'orange'] else 'palette'
    uv = [.1, .1, .2, .2] if material in ['blue', 'orange'] else UV[material]
    result = {'from': a, 'to': b, 'faces': {f: {'uv': uv, 'texture': '#' + texture} for f in ['north','south','east','west','up','down']}}
    if rotation: result['rotation'] = rotation
    return result

def model(elements):
    return {'parent': 'minecraft:block/block', 'textures': TEXTURES, 'elements': elements,
            'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, -1, 0], 'scale': [.60, .60, .60]},
                        'ground': {'translation': [0, 3, 0], 'scale': [.3,.3,.3]},
                        'fixed': {'rotation': [0, 180, 0], 'scale': [.6,.6,.6]}}}

table = [box([0,10,0],[16,12,16],'dark'),box([.5,12,.5],[15.5,13,15.5],'gray'),box([2,12.98,2],[14,13.1,14],'dark')]
for x in [1,12]:
    for z in [1,12]:
        table += [box([x-.5,0,z-.5],[x+3.5,1,z+3.5],'dark'),box([x,1,z],[x+3,10,z+3],'silver'),box([x+.5,2,z-.05],[x+2.5,8,z+.3],'gray')]
# Join the grid without overlapping horizontal strips at their intersections.
for x in [5.9,9.9]: table.append(box([x,13.1,2],[x+.16,13.15,14],'gray'))
for z in [5.9,9.9]:
    for x0,x1 in [(2,5.9),(6.06,9.9),(10.06,14)]:
        table.append(box([x0,13.1,z],[x1,13.15,z+.16],'gray'))
# The trim protrudes 1/8 model unit beyond the apron; its visible face must not
# share z=0 or z=16 with the dark shell, which caused depth-buffer flickering.
for z0,z1 in [(-.125,.25),(15.75,16.125)]:
    table.append(box([4,10.7,z0],[12,11.3,z1],'cyan'))

base = [box([1,0,1],[15,1.5,15],'dark'),box([2,1.5,2],[14,2.5,14],'silver'),box([4,2.5,4],[12,4,12],'gray'),box([5,4,5],[11,7,11],'dark')]
for x in [2,13]:
    for z in [2,13]: base.append(box([x,2.5,z],[x+1,2.8,z+1],'dark'))

terminal = [box([2,0,3],[14,1.5,13],'dark'),box([3,1.5,4],[13,2,12],'silver'),box([6.5,2,7],[9.5,10,10],'gray'),
            box([0,9,5],[16,19,8],'dark'),box([.5,9.5,4.7],[15.5,18.5,5],'silver'),box([1.2,10.2,4.5],[14.8,17.8,4.7],'dark')]
for i in range(3): terminal += [box([2.2,11+i*2,4.3],[3.5,12+i*2,4.5],'cyan'),box([4.5,11.2+i*2,4.3],[12.5-i*1.7,11.6+i*2,4.5],'gray')]
terminal += [box([3,3,3],[13,3.8,6],'dark'),box([4,3.8,3.7],[10,3.9,5],'gray'),box([11,3.8,3.7],[12,3.9,5],'cyan')]

for name, elements in [('assembly_table',table),('transport_arm',base),('assembly_arm',base),('assembler_terminal',terminal)]:
    write(ASSETS / f'models/block/{name}.json', model(elements))
    variants = {f'facing={face}': {'model': f'futuretech:block/{name}', 'y': angle} for face,angle in [('north',0),('east',90),('south',180),('west',270)]}
    write(ASSETS / f'blockstates/{name}.json', {'variants':variants})
    if name in ['transport_arm','assembly_arm']:
        color = 'blue' if name == 'transport_arm' else 'orange'
        tilt = {'origin':[8,8,8], 'axis':'x', 'angle':22.5}
        idle = base + [box([5,6,6],[11,9,10],'dark'),box([6,8,6],[10,18,10],color,tilt),
                       box([6.7,9,5.8],[9.3,16,6],'dark',tilt),box([5.3,8,7.4],[5.8,18,8.6],'silver',tilt),
                       box([5,16,9],[11,19,14],'dark'),box([4.8,16.7,10],[5,18.3,13],'silver'),box([11,16.7,10],[11.2,18.3,13],color),
                       box([6.5,16,0],[9.5,18,12],color),box([6.3,16.4,2],[6.5,17.6,10],'dark'),
                       box([9.7,16.7,1],[10.2,17.3,11],'silver'),box([6,14,-1],[10,17,2],'dark')]
        if name == 'transport_arm': idle += [box([5.5,11,-.5],[6.5,15,1.5],'silver'),box([9.5,11,-.5],[10.5,15,1.5],'silver')]
        else: idle += [box([6.5,12,-.5],[9.5,14,1.5],'silver'),box([7.5,9,0],[8.5,12,1],'cyan')]
        write(ASSETS / f'models/item/{name}.json', model(idle))
    else: write(ASSETS / f'models/item/{name}.json', {'parent':f'futuretech:block/{name}'})
    write(ASSETS / f'items/{name}.json', {'model':{'type':'minecraft:model','model':f'futuretech:item/{name}'}})
    write(DATA / f'loot_table/blocks/{name}.json', {'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'futuretech:{name}'}],'conditions':[{'condition':'minecraft:survives_explosion'}]}]})

recipes = {
 'assembly_table': (['III',' C ','I I'], {'I':'minecraft:iron_ingot','C':'minecraft:copper_ingot'}),
 'transport_arm': ([' II',' PI','RC '], {'I':'minecraft:iron_ingot','P':'minecraft:piston','R':'minecraft:redstone','C':'minecraft:copper_ingot'}),
 'assembly_arm': ([' II',' DP','RC '], {'I':'minecraft:iron_ingot','P':'minecraft:piston','D':'minecraft:diamond','R':'minecraft:redstone','C':'minecraft:copper_ingot'}),
 'assembler_terminal': (['GGG','IRI',' C '], {'G':'minecraft:glass_pane','I':'minecraft:iron_ingot','R':'minecraft:redstone','C':'minecraft:copper_ingot'})
}
for name,(pattern,key) in recipes.items():
    write(DATA / f'recipe/{name}.json', {'type':'minecraft:crafting_shaped','category':'redstone','pattern':pattern,'key':key,'result':{'id':f'futuretech:{name}','count':1}})
    write(DATA / f'advancement/recipes/{name}.json', {'parent':'minecraft:recipes/root','criteria':{'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'minecraft:iron_ingot'}]}}},'requirements':[['has_iron']],'rewards':{'recipes':[f'futuretech:{name}']}})
write(DATA / 'recipe/assembling/machine_casing.json', {'type':'futuretech:assembling','ingredients':['minecraft:stone','minecraft:iron_ingot'],'result':{'id':'futuretech:machine_casing','count':1},'duration':80})
for tag in ['mineable/pickaxe','needs_stone_tool']:
    path = ROOT / f'data/minecraft/tags/block/{tag}.json'
    data = json.loads(path.read_text(encoding='utf-8-sig'))
    for name in NAMES:
        if f'futuretech:{name}' not in data['values']: data['values'].append(f'futuretech:{name}')
    write(path, data)

translations = {
 'gui.futuretech.assembler.energy': ('Energia do controller: %s / %s FE','Controller energy: %s / %s FE'),
 'gui.futuretech.assembler.status.9': ('Sem energia no controller','Controller needs energy'),
 'block.futuretech.assembly_table': ('Mesa de montagem','Assembly Table'),
 'block.futuretech.transport_arm': ('Braço de transporte','Transport Arm'),
 'block.futuretech.assembly_arm': ('Braço de montagem','Assembly Arm'),
 'block.futuretech.assembler_terminal': ('Terminal do Assembler','Assembler Terminal'),
 'gui.futuretech.assembler.title': ('Assembler','Assembler'),
 'gui.futuretech.assembler.select': ('Selecione uma receita','Select a recipe'),
 'gui.futuretech.assembler.busy': ('Braço em movimento. Aguarde ou use Shift + Wrench para recuperar os itens.','Arm in motion. Wait or use Shift + Wrench to recover items.'),
 'gui.futuretech.assembler.input': ('Entrada · azul','Input · blue'),
 'gui.futuretech.assembler.output': ('Saída · laranja','Output · orange'),
 'gui.futuretech.assembler.arm_hint': ('Alcance: 3 blocos. Use Wrench para alternar entrada/saída.','Reach: 3 blocks. Use Wrench to switch input/output.'),
 'gui.futuretech.assembler.tool_hint': ('Alcance: 3 blocos. Monta automaticamente os ingredientes da mesa.','Reach: 3 blocks. Automatically assembles the ingredients on the table.'),
 'gui.futuretech.assembler.no_table': ('Nenhuma mesa de montagem ao alcance de 3 blocos.','No assembly table within 3 blocks.'),
 'gui.futuretech.assembler.clear_table': ('Esvazie a mesa e aguarde os braços antes de trocar a receita.','Empty the table and wait for the arms before changing recipes.'),
 'gui.futuretech.assembler.in_short': ('Ent.','In'),
 'gui.futuretech.assembler.work_short': ('Mont.','Work'),
 'gui.futuretech.assembler.out_short': ('Saída','Out'),
 'gui.futuretech.assembler.terminal_short': ('Term.','Term.'),
 'gui.futuretech.assembler.status.0': ('Aguardando ingredientes','Waiting for ingredients'),
 'gui.futuretech.assembler.status.1': ('Terminal fora de alcance','Terminal out of reach'),
 'gui.futuretech.assembler.status.2': ('Escolha a receita nas setas','Choose a recipe using the arrows'),
 'gui.futuretech.assembler.status.3': ('Recebendo ingredientes','Receiving ingredients'),
 'gui.futuretech.assembler.status.4': ('Montando...','Assembling...'),
 'gui.futuretech.assembler.status.5': ('Recuando ferramenta...','Retracting tool...'),
 'gui.futuretech.assembler.status.6': ('Enviando resultado','Sending result'),
 'gui.futuretech.assembler.status.7': ('Aguardando saída livre','Waiting for output space'),
 'gui.futuretech.assembler.status.8': ('Aguardando braço de montagem','Waiting for assembly arm'),
}
for locale,index in [('pt_br',0),('en_us',1)]:
    path = ASSETS / f'lang/{locale}.json'
    data = json.loads(path.read_text(encoding='utf-8-sig'))
    data.update({key:value[index] for key,value in translations.items()})
    write(path,data)
