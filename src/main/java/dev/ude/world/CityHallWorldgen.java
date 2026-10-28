package dev.ude.world;

import dev.ude.UpgraderDebtEconomy;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opportunistic adjacent-village placement. No locate calls, chunk tickets or generation requests. */
@Mod.EventBusSubscriber(modid=UpgraderDebtEconomy.MOD_ID)
public final class CityHallWorldgen {
    public static final int CANDIDATES=48;
    private static final Map<ServerLevel,Worker> WORKERS=new WeakHashMap<>();
    private record Discovery(ServerLevel level,net.minecraft.world.level.ChunkPos chunk,long after,int attempts) {}
    private static final java.util.concurrent.ConcurrentLinkedQueue<Discovery> DISCOVERIES=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final java.util.concurrent.atomic.AtomicInteger PENDING=new java.util.concurrent.atomic.AtomicInteger();
    private CityHallWorldgen(){}
    @SubscribeEvent public static void chunkLoaded(ChunkEvent.Load event){
        if(!(event.getLevel() instanceof ServerLevel level)||level.dimension()!=Level.OVERWORLD)return;
        // execute() may run immediately inside ChunkEvent.Load, before the chunk is FULL.
        // Queue coordinates only, then inspect the completed chunk from a later server tick.
        if(PENDING.incrementAndGet()>4096){PENDING.decrementAndGet();return;}
        DISCOVERIES.add(new Discovery(level,event.getChunk().getPos(),0,0));
    }
    private static void discoverLoadedChunks(ServerLevel level){
        List<Discovery> deferred=new ArrayList<>();
        for(int i=0;i<8;i++){
            Discovery d=DISCOVERIES.poll();if(d==null)break;PENDING.decrementAndGet();
            if(d.level()!=level){deferred.add(d);continue;}
            if(d.after()>level.getGameTime()){deferred.add(d);continue;}
            if(!level.hasChunk(d.chunk().x,d.chunk().z)){
                if(d.attempts()<2)deferred.add(new Discovery(level,d.chunk(),level.getGameTime()+20,d.attempts()+1));
                continue;
            }
            if(!CityHallConfig.ENABLED.get())continue;
            inspect(level,d.chunk().x,d.chunk().z);
        }
        for(Discovery d:deferred){PENDING.incrementAndGet();DISCOVERIES.add(d);}
    }
    private static void inspect(ServerLevel level,int x,int z){
        if(!level.hasChunk(x,z))return;
        var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        level.getChunk(x,z).getAllStarts().forEach((structure,start)->{
            var key=registry.getKey(structure);
            if(key!=null&&key.getNamespace().equals("minecraft")&&key.getPath().startsWith("village_")&&start.isValid())
                CityHallData.get(level).discover(level,start.getChunkPos().toLong(),start.getBoundingBox());
        });
    }
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e){
        DISCOVERIES.removeIf(d->d.level().getServer()==e.getServer());PENDING.set(DISCOVERIES.size());
        WORKERS.keySet().removeIf(l->l.getServer()==e.getServer());
    }
    public static boolean overlaps(BoundingBox a,BoundingBox b){return a.minX()<=b.maxX()&&a.maxX()>=b.minX()&&a.minZ()<=b.maxZ()&&a.maxZ()>=b.minZ();}
    public static BlockPos candidate(ServerLevel level,BoundingBox village,int index){
        int side=index%4,gap=new int[]{8,24,48,80}[(index/4)%4],offset=new int[]{-32,0,32}[index/16];
        int x=(village.minX()+village.maxX())/2,z=(village.minZ()+village.maxZ())/2;
        switch(side){
            case 0 -> {x+=offset;z=village.minZ()-33-gap;}
            case 1 -> {x+=offset;z=village.maxZ()+33+gap;}
            case 2 -> {z+=offset;x=village.minX()-28-gap;}
            case 3 -> {z+=offset;x=village.maxX()+28+gap;}
        }
        if(!level.hasChunk(x>>4,z>>4))return null;
        return new BlockPos(x,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1,z);
    }
    public static boolean reserved(ServerLevel level,CityHallData.Hall own,BlockPos c){
        BoundingBox box=new BoundingBox(c.getX()-27,c.getY()-5,c.getZ()-32,c.getX()+27,c.getY()+18,c.getZ()+32);
        if(overlaps(box,own.villageBounds))return true;
        if(UdeSavedData.get(level.getServer()).intersectsPlot(level,box))return true;
        for(var h:CityHallData.get(level).halls())if(h!=own&&h.center!=null&&overlaps(box,h.bounds()))return true;
        // Also avoid other loaded structure starts, not merely houses sampled at a few points.
        for(int x=(c.getX()-27)>>4;x<=(c.getX()+27)>>4;x++)for(int z=(c.getZ()-32)>>4;z<=(c.getZ()+32)>>4;z++) {
            if(!level.hasChunk(x,z))return true;
            for(var s:level.getChunk(x,z).getAllStarts().values())if(s.isValid()&&s.getBoundingBox().intersects(box))return true;
        }
        return false;
    }
    public static void tick(ServerLevel level){
        if(level.dimension()!=Level.OVERWORLD)return;
        discoverLoadedChunks(level);
        var data=CityHallData.get(level);
        if(level.getGameTime()%100==0)for(var h:data.halls())CityHallBuilder.ensureStaff(level,h);
        WORKERS.computeIfAbsent(level,l->new Worker()).tick(level,data);
    }
    private static final class Probe {
        final CityHallData.Hall hall;final BlockPos center;final int index;int column;
        Probe(CityHallData.Hall h,BlockPos p,int i){hall=h;center=p;index=i;}
    }
    private static final class Worker {
        Probe probe;List<CityHallBuilder.PlanBlock> plan;int roundRobin,discoveryCursor;
        void tick(ServerLevel level,CityHallData data){
            // Also discover already-loaded villages after config changes, one loaded chunk per tick.
            var players=level.players();
            if(CityHallConfig.ENABLED.get()&&!players.isEmpty()){
                int i=discoveryCursor++;if(discoveryCursor>=81*players.size())discoveryCursor=0;
                var p=players.get(Math.floorMod(i/81,players.size())).chunkPosition();
                inspect(level,p.x+i%9-4,p.z+(i%81)/9-4);
            }
            for(var hall:data.halls())if(hall.phase==CityHallData.Phase.BUILDING&&CityHallBuilder.loaded(level,hall.center)){
                if(plan==null)plan=CityHallBuilder.plan(level);
                CityHallBuilder.step(level,hall,plan,CityHallConfig.BLOCKS_PER_TICK.get());return;
            }
            if(!CityHallConfig.ENABLED.get())return;
            if(probe!=null){
                if(!CityHallBuilder.loaded(level,probe.center)){probe=null;return;}
                for(int n=0;n<CityHallConfig.COLUMNS_PER_TICK.get()&&probe.column<CityHallBuilder.COLUMNS;n++){
                    if(!CityHallBuilder.safeColumn(level,probe.center,probe.column++)){
                        probe.hall.rejected.add(probe.index);data.setDirty();probe=null;return;
                    }
                }
                if(probe.column==CityHallBuilder.COLUMNS){
                    if(reserved(level,probe.hall,probe.center)){probe.hall.rejected.add(probe.index);data.setDirty();probe=null;return;}
                    probe.hall.center=probe.center;probe.hall.phase=CityHallData.Phase.BUILDING;probe.hall.cursor=0;
                    data.setDirty();level.getDataStorage().save();probe=null;
                }
                return;
            }
            if(level.getGameTime()%20!=0)return;
            List<CityHallData.Hall> halls=new ArrayList<>(data.halls());
            for(int n=0;n<halls.size();n++){
                var h=halls.get(Math.floorMod(roundRobin++,halls.size()));
                if(h.phase!=CityHallData.Phase.SEARCHING)continue;
                if(h.rejected.size()>=CANDIDATES){h.phase=CityHallData.Phase.NO_SITE;data.setDirty();continue;}
                for(int i=0;i<CANDIDATES;i++){
                    int index=Math.floorMod(h.candidate++,CANDIDATES);
                    if(h.rejected.contains(index))continue;
                    BlockPos c=candidate(level,h.villageBounds,index);
                    // Unloaded sites are retried later, never marked failed.
                    if(c!=null&&CityHallBuilder.loaded(level,c)&&!reserved(level,h,c)){probe=new Probe(h,c,index);return;}
                }
            }
        }
    }
}
