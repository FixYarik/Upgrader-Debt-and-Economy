#!/usr/bin/env python3
"""Author UDE's deterministic vanilla StructureTemplate (no external assets)."""
from pathlib import Path
import gzip,struct
W,H,D=55,24,65
blocks={}
def put(x,y,z,name,**props):
    assert 0<=x<W and 0<=y<H and 0<=z<D
    blocks[x,y,z]=(name if ':' in name else 'minecraft:'+name,tuple(sorted(props.items())))
def fill(x1,y1,z1,x2,y2,z2,name,**props):
    for y in range(y1,y2+1):
        for z in range(z1,z2+1):
            for x in range(x1,x2+1):put(x,y,z,name,**props)
fill(0,0,0,54,23,64,'air')
for y,name in [(0,'stone_bricks'),(5,'polished_andesite'),(12,'smooth_stone'),(21,'deepslate_tiles')]:fill(0,y,0,54,y,64,name)
for y in range(1,21):
    for x in range(W):
        for z in [0,64]:put(x,y,z,'glass' if y in [8,9,10,15,16,17] and x%5 in [1,2] else 'stone_bricks')
    for z in range(D):
        for x in [0,54]:put(x,y,z,'glass' if y in [8,9,10,15,16,17] and z%5 in [1,2] else 'stone_bricks')
# Wide public entrance and paved forecourt within the validated footprint.
fill(23,6,0,31,10,0,'air')
for x in [5,16,38,49]:fill(x,6,1,x,20,1,'quartz_pillar',axis='y')
fill(18,22,0,36,22,5,'quartz_block');fill(22,23,0,32,23,4,'quartz_block')
# Two bank counters, waiting benches and ATM.
for x in [15,39]:
    fill(x,6,8,x,6,22,'dark_oak_planks')
    for z in [9,13,17,21]:put(x,7,z,'oak_fence')
for x in [5,8,46,49]:
    for z in [7,11,15]:put(x,6,z,'oak_stairs',facing='north',half='bottom',shape='straight',waterlogged='false')
# Main stair, seven blocks wide, genuinely connects both storeys.
fill(24,12,28,30,12,34,'air')
for i in range(7):
    for x in range(24,31):
        put(x,6+i,28+i,'stone_brick_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')
        for y in [7+i,8+i]:put(x,y,28+i,'air')
# Basement stair and guarded vault; never use invalid single-block door states.
fill(8,5,30,12,5,34,'air')
for i in range(5):
    for x in range(8,13):
        put(x,1+i,30+i,'stone_brick_stairs',facing='south',half='bottom',shape='straight',waterlogged='false')
        put(x,2+i,30+i,'air');put(x,3+i,30+i,'air')
for x in range(18,37):
    for z in range(43,62):
        if x in [18,36] or z in [43,61]:fill(x,1,z,x,4,z,'iron_block')
for y,half in [(1,'lower'),(2,'upper')]:put(27,y,43,'upgrader_debt_economy:vault_door',facing='north',half=half,hinge='left',open='false',powered='false')
fill(23,1,53,31,1,57,'gold_block')
# Upper offices open onto a public gallery.
for x in [19,35]:
    fill(x,13,42,x,20,62,'polished_andesite');fill(x,13,48,x,15,50,'air')
for x in [12,27,42]:
    fill(x-3,13,53,x+3,13,53,'dark_oak_planks')
    put(x,14,53,'lantern',hanging='false',waterlogged='false')
# Railings around stairwell, lamps, carpet runners and original interior arrangement.
for x in [23,31]:fill(x,13,27,x,13,35,'oak_fence')
for y in [4,11,20]:
    for x in [7,20,34,47]:
        for z in [6,20,39,59]:put(x,y,z,'sea_lantern')
for y in [6,13]:
    fill(25,y,3,29,y,24,'red_carpet')
# Floor lighting keeps every occupied interior away from hostile-mob light level zero.
for y in [0,5,12]:
    for x in range(3,52,8):
        for z in range(3,62,8):
            # Preserve stair openings and stair blocks.
            if y==5 and 8<=x<=12 and 30<=z<=34:continue
            if y==12 and 24<=x<=30 and 28<=z<=34:continue
            put(x,y,z,'sea_lantern')
# The narrow rear service corridor is separated from the vault lamps by an opaque wall.
for y in [0,5,12]:
    for x in range(3,52,8):put(x,y,63,'sea_lantern')
# Vanilla NBT writer. Preserve all palette states and explicit air for resumable placement.
def string(s):
    b=s.encode();return struct.pack('>H',len(b))+b
def payload(t,v):
    if t==3:return struct.pack('>i',v)
    if t==8:return string(v)
    if t==9:
        et,values=v;return bytes([et])+struct.pack('>i',len(values))+b''.join(payload(et,e) for e in values)
    if t==10:return b''.join(bytes([et])+string(k)+payload(et,value) for k,(et,value) in v.items())+b'\0'
    raise ValueError(t)
def write(path,size,palette,entries):
    tag={'DataVersion':(3,3465),'size':(9,(3,list(size))),'palette':(9,(10,palette)),'blocks':(9,(10,entries)),'entities':(9,(10,[]))}
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(gzip.compress(bytes([10])+string('')+payload(10,tag),mtime=0))
palette=[];indices={};entries=[]
for (x,y,z),state in sorted(blocks.items(),key=lambda e:(e[0][1],e[0][2],e[0][0])):
    if state not in indices:
        indices[state]=len(palette);name,props=state;p={'Name':(8,name)}
        if props:p['Properties']=(10,{k:(8,v) for k,v in props})
        palette.append(p)
    entries.append({'pos':(9,(3,[x,y,z])),'state':(3,indices[state])})
root=Path(__file__).resolve().parents[1]/'src/main/resources/data/upgrader_debt_economy/structures'
write(root/'cityhall.nbt',(W,H,D),palette,entries)
write(root/'test/empty.nbt',(80,32,80),[{'Name':(8,'minecraft:air')}],[])
print(f'City hall: {len(entries)} blocks, {len(palette)} states, {W}x{H}x{D}')
