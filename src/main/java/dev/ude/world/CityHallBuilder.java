package dev.ude.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Alpha city-hall generator. Command-driven placement, not yet village worldgen.
 * 55x65 vanilla-block building with two floors, vault, patrol hall, clerks and offices.
 */
public final class CityHallBuilder {
    public static final int HALF_X=27,HALF_Z=32;
    private CityHallBuilder(){}
    public static boolean canBuild(ServerLevel level,BlockPos center){
        int y=center.getY();
        // Conservative: avoid existing construction and steep terrain.
        for(int x=-HALF_X;x<=HALF_X;x+=3)for(int z=-HALF_Z;z<=HALF_Z;z+=3){
            int surface=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,center.getX()+x,center.getZ()+z);
            if(Math.abs(surface-y)>2)return false;
            for(int dy=0;dy<=18;dy+=3) {
                BlockPos at=center.offset(x,dy,z);
                if(!level.isEmptyBlock(at) && !level.getBlockState(at).canBeReplaced())return false;
            }
        }
        return true;
    }
    private static void set(ServerLevel level,BlockPos center,int x,int y,int z,BlockState state){
        level.setBlock(center.offset(x,y,z),state,3);
    }
    private static void spawnClerk(ServerLevel level,BlockPos c,int x,int y,int z,String name){
        Villager v=EntityType.VILLAGER.create(level);
        if(v==null)return;
        v.moveTo(c.getX()+x+0.5,c.getY()+y,c.getZ()+z+0.5,180f,0f);
        v.setCustomName(Component.literal(name));v.setCustomNameVisible(true);
        v.setPersistenceRequired();v.setNoAi(true);
        level.addFreshEntity(v);
    }
    public static void build(ServerLevel level,BlockPos c){
        // Foundation, terracotta floors and hollow stone-brick walls.
        for(int x=-HALF_X;x<=HALF_X;x++)for(int z=-HALF_Z;z<=HALF_Z;z++){
            set(level,c,x,-1,z,Blocks.STONE_BRICKS.defaultBlockState());
            set(level,c,x,0,z,Blocks.POLISHED_ANDESITE.defaultBlockState());
            set(level,c,x,7,z,Blocks.SMOOTH_STONE.defaultBlockState());
            set(level,c,x,15,z,Blocks.DEEPSLATE_TILES.defaultBlockState());
        }
        for(int y=1;y<=14;y++){
            for(int x=-HALF_X;x<=HALF_X;x++)for(int z=-HALF_Z;z<=HALF_Z;z++){
                if(Math.abs(x)==HALF_X || Math.abs(z)==HALF_Z){
                    boolean window=y%7>=3 && y%7<=5 && ((Math.abs(x)==HALF_X && z%5==0)||(Math.abs(z)==HALF_Z && x%5==0));
                    set(level,c,x,y,z,(window?Blocks.GLASS_PANE:Blocks.STONE_BRICKS).defaultBlockState());
                }
            }
        }
        // Entrance: 7-block-wide corridor.
        for(int x=-3;x<=3;x++)for(int y=1;y<=6;y++)set(level,c,x,y,-HALF_Z,Blocks.AIR.defaultBlockState());
        // Open the central staircase corridor between floors.
        for(int x=-3;x<=3;x++)for(int z=-6;z<=6;z++)set(level,c,x,7,z,Blocks.AIR.defaultBlockState());
        // Ornamental pillars on the facade.
        for(int x=-22;x<=22;x+=11)for(int y=1;y<=14;y++){
            set(level,c,x,y,-HALF_Z-1,Blocks.QUARTZ_PILLAR.defaultBlockState());
        }
        // Three transverse departments: bank, mayor, business.
        for(int x=-HALF_X+1;x<=HALF_X-1;x++)for(int y=1;y<=6;y++){
            for(int z:new int[]{-11,11}) {
                if(Math.abs(x)>3)set(level,c,x,y,z,Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
        }
        // Bank's impressive counters and vault door.
        for(int z=-28;z<=-14;z++) {
            set(level,c,-10,1,z,Blocks.IRON_BLOCK.defaultBlockState());
            set(level,c,10,1,z,Blocks.IRON_BLOCK.defaultBlockState());
        }
        for(int x=-6;x<=6;x++)for(int y=1;y<=4;y++)set(level,c,x,y,17,Blocks.IRON_BLOCK.defaultBlockState());
        for(int y=1;y<=3;y++)set(level,c,0,y,17,Blocks.IRON_DOOR.defaultBlockState());
        for(int x=-22;x<=22;x+=11)for(int z=-26;z<=26;z+=13){
            set(level,c,x,14,z,Blocks.SEA_LANTERN.defaultBlockState());
            set(level,c,x,6,z,Blocks.SEA_LANTERN.defaultBlockState());
        }
        // Decorative fake reserve, not exploitable economy money.
        for(int x=-3;x<=3;x++)for(int z=22;z<=27;z++)set(level,c,x,1,z,Blocks.GOLD_BLOCK.defaultBlockState());
        spawnClerk(level,c,-15,1,-18,"Банкир");
        spawnClerk(level,c,15,1,-18,"Старший банкир");
        spawnClerk(level,c,0,8,15,"Мэр");
        spawnClerk(level,c,12,8,15,"БизнесЧлен");
        spawnClerk(level,c,-12,8,15,"Инспектор Геннадий");
        spawnClerk(level,c,-16,1,4,"Спортик Валера");
        spawnClerk(level,c,-13,1,4,"Спортик Димон");
        spawnClerk(level,c,-10,1,4,"Спортик Саня");
        spawnClerk(level,c,8,1,20,"Начальник охраны");
    }
}
