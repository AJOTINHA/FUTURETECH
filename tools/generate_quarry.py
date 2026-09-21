"""Reproducible quarry scaffold and arm materials.

The 32px scaffold tile has orange enamel flanges, a recessed web and riveted joints.
Its central 12px row/column map to the models' UVs 5..11, with a rotation-symmetric
12x12 joint so every connection can share the same texture. Beam ends tile without
seams. The arm wears charcoal steel with lengthwise grooves; the renderer runs the
middle rows along the rails and shows the whole tile, bolts included, on the carriage.
"""
from PIL import Image, ImageDraw
from generate_solar_generator import ASSETS

FRAME_EDGE='#343a40'
FRAME_ORANGE='#cf8b2d'
FRAME_LIGHT='#edb650'
FRAME_SHADE='#925c25'

def girder(draw, across, vertical):
    """A 12px-wide rolled beam; symmetric bevels also work on rotated model faces."""
    def px(a,c,color):
        draw.point((across+c,a) if vertical else (a,across+c), fill=color)
    profile = (FRAME_EDGE, FRAME_SHADE, FRAME_LIGHT, FRAME_ORANGE,
               '#a56c29', '#b77b2d', '#b77b2d', '#a56c29',
               FRAME_ORANGE, FRAME_LIGHT, FRAME_SHADE, FRAME_EDGE)
    for a in range(32):
        for c, color in enumerate(profile):
            px(a,c,color)
    # Sparse enamel variation, away from the endpoints so adjacent blocks meet cleanly.
    for a in (3,4,27,28):
        for c in (5,6): px(a,c,'#be8230')
    for a in (6,25):
        for c in (2,9): px(a,c,'#dca448')

def frame():
    image=Image.new('RGBA',(32,32),FRAME_EDGE); draw=ImageDraw.Draw(image)
    girder(draw,10,vertical=False)
    girder(draw,10,vertical=True)
    # Square splice plate: all faces and all six connections use this same UV island.
    draw.rectangle((10,10,21,21),fill=FRAME_SHADE)
    draw.rectangle((11,11,20,20),fill=FRAME_LIGHT)
    draw.rectangle((12,12,19,19),fill=FRAME_ORANGE)
    draw.rectangle((14,14,17,17),fill='#d99532')
    # Exposed steel rivets with a dark socket and small symmetric metallic highlights.
    for x,y in ((12,12),(18,12),(12,18),(18,18)):
        draw.rectangle((x-1,y-1,x+2,y+2),fill=FRAME_SHADE)
        draw.rectangle((x,y,x+1,y+1),fill='#929b9f')
        draw.point((x if x<16 else x+1,y if y<16 else y+1),fill='#d2d7d1')
    return image

STEEL='#34373c'; STEEL_LIGHT='#6b727b'; STEEL_DARK='#1e2125'; GROOVE='#24272b'; GROOVE_EDGE='#4f555c'
BOLT='#8d959d'; BOLT_SHADE='#50565d'

def arm():
    image=Image.new('RGBA',(32,32),STEEL); draw=ImageDraw.Draw(image)
    draw.rectangle((0,0,1,31),fill=STEEL_LIGHT); draw.rectangle((30,0,31,31),fill=STEEL_DARK)
    for x in (8,22):
        draw.rectangle((x,0,x+1,31),fill=GROOVE); draw.line((x-1,0,x-1,31),fill=GROOVE_EDGE)
    # Plate ends and bolts sit outside rows 10..21, the band the renderer repeats along a rail.
    draw.line((2,1,29,1),fill=STEEL_LIGHT); draw.line((2,30,29,30),fill=STEEL_DARK)
    for x,y in ((4,4),(26,4),(4,26),(26,26)):
        draw.rectangle((x,y,x+1,y+1),fill=BOLT); draw.point((x+1,y+1),fill=BOLT_SHADE)
    return image

FLUTE='#8a9199'; FLUTE_EDGE='#b9c1c7'

def drill():
    image=Image.new('RGBA',(32,32),'#2b2e32'); draw=ImageDraw.Draw(image)
    for offset in range(-32,64,8):
        draw.line((offset,0,offset+32,32),fill=FLUTE,width=3)
        draw.line((offset-2,0,offset+30,32),fill=FLUTE_EDGE)
    draw.rectangle((0,0,31,31),outline=STEEL_DARK)
    return image

def main():
    folder=ASSETS/'textures/block/quarry'; folder.mkdir(parents=True,exist_ok=True)
    frame().save(folder/'frame.png')
    arm().save(folder/'arm.png')
    drill().save(folder/'drill.png')

if __name__=='__main__':
    main()
