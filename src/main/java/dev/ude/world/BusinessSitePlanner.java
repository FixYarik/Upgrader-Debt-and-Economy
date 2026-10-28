package dev.ude.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Lightweight builder: foundation + verifiable build requirements, not yet automated factories. */
public final class BusinessSitePlanner {
    public static final int RADIUS=7;
    private BusinessSitePlanner(){}
    public static boolean isClear(ServerLevel level,BlockPos center){
        var bounds=new net.minecraft.world.level.levelgen.structure.BoundingBox(center.getX()-RADIUS,center.getY()-1,center.getZ()-RADIUS,center.getX()+RADIUS,center.getY()+6,center.getZ()+RADIUS);
        if(!level.getWorldBorder().isWithinBounds(center.offset(-RADIUS,0,-RADIUS))||!level.getWorldBorder().isWithinBounds(center.offset(RADIUS,0,RADIUS)))return false;
        if(UdeSavedData.get(level.getServer()).intersectsPlot(level,bounds))return false;
        for(var hall:CityHallData.get(level).halls())if(hall.center!=null&&CityHallWorldgen.overlaps(bounds,hall.bounds()))return false;
        int y=center.getY();
        for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++){
            BlockPos floor=center.offset(x,-1,z);
            if(!level.hasChunk(floor.getX()>>4,floor.getZ()>>4))return false;
            if(CityHallData.get(level).playerModified(new net.minecraft.world.level.ChunkPos(floor).toLong()))return false;
            if(!CityHallBuilder.natural(level.getBlockState(floor))||level.getBlockEntity(floor)!=null||!level.getFluidState(floor).isEmpty())return false;
            int height=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,center.getX()+x,center.getZ()+z);
            if(Math.abs(height-y)>1)return false;
            for(int dy=0;dy<=6;dy++){
                BlockPos pos=center.offset(x,dy,z);
                if(!level.getBlockState(pos).canBeReplaced())return false;
            }
        }
        return true;
    }
    public static BlockPos find(ServerLevel level,BlockPos start){
        for(int distance:new int[]{24,32,40,48,64,80}){
            for(int dx:new int[]{-distance,0,distance})for(int dz:new int[]{-distance,0,distance}){
                if(dx==0&&dz==0)continue;
                int x=start.getX()+dx,z=start.getZ()+dz;
                if(!level.hasChunk(x>>4,z>>4))continue;
                int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                BlockPos candidate=new BlockPos(x,y,z);
                if(isClear(level,candidate))return candidate;
            }
        }
        return null;
    }
    public static void placeFoundation(ServerLevel level,BlockPos center){
        for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++)
            level.setBlock(center.offset(x,-1,z),Blocks.STONE_BRICKS.defaultBlockState(),3);
        level.setBlock(center.offset(0,0,0),Blocks.GOLD_BLOCK.defaultBlockState(),3);
    }
    public static int missing(ServerLevel level,BlockPos c){
        int count=0;
        for(int x:new int[]{-6,6})for(int z:new int[]{-6,6})for(int y=0;y<4;y++)
            if(!level.getBlockState(c.offset(x,y,z)).is(Blocks.OAK_LOG))count++;
        for(int i=-6;i<=6;i++){
            if(!level.getBlockState(c.offset(i,4,-6)).is(Blocks.OAK_PLANKS))count++;
            if(!level.getBlockState(c.offset(i,4,6)).is(Blocks.OAK_PLANKS))count++;
            if(i!=-6&&i!=6){
                if(!level.getBlockState(c.offset(-6,4,i)).is(Blocks.OAK_PLANKS))count++;
                if(!level.getBlockState(c.offset(6,4,i)).is(Blocks.OAK_PLANKS))count++;
            }
        }
        if(!level.getBlockState(c.offset(0,0,2)).is(Blocks.CRAFTING_TABLE))count++;
        if(!level.getBlockState(c.offset(2,0,2)).is(Blocks.BARREL))count++;
        return count;
    }
}
