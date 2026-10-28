package dev.ude.world;

import dev.ude.core.EconomyEngine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Holds all banking and loan data in the Overworld data directory. */
public final class UdeSavedData extends SavedData {
    private EconomyEngine engine=new EconomyEngine();
    public record BusinessPlot(String dimension,int x,int y,int z){
        public BlockPos pos(){return new BlockPos(x,y,z);}
        public ServerLevel level(MinecraftServer server){
            return server.getLevel(ResourceKey.create(Registries.DIMENSION,new ResourceLocation(dimension)));
        }
    }
    private final Map<UUID,BusinessPlot> plots=new HashMap<>();
    public BusinessPlot plot(UUID businessId){return plots.get(businessId);}
    public void assignPlot(UUID businessId,ServerLevel level,BlockPos pos){
        if(plots.containsKey(businessId))throw new IllegalStateException("Plot already assigned");
        plots.put(businessId,new BusinessPlot(level.dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ()));
        changed();
    }
    public static UdeSavedData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(
            UdeSavedData::new,UdeSavedData::load,null),"upgrader_debt_economy");
    }
    private static UdeSavedData load(CompoundTag tag){
        UdeSavedData data=new UdeSavedData();
        if(tag.contains("engine_v1"))data.engine=EconomyEngine.importText(tag.getString("engine_v1"));
        ListTag list=tag.getList("business_plots",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            CompoundTag p=list.getCompound(i);
            data.plots.put(p.getUUID("id"),new BusinessPlot(p.getString("dim"),p.getInt("x"),p.getInt("y"),p.getInt("z")));
        }
        return data;
    }
    public EconomyEngine engine(){return engine;}
    @Override public CompoundTag save(CompoundTag tag){
        tag.putString("engine_v1",engine.exportText());
        ListTag list=new ListTag();
        plots.forEach((id,p)->{
            CompoundTag n=new CompoundTag();n.putUUID("id",id);n.putString("dim",p.dimension());
            n.putInt("x",p.x());n.putInt("y",p.y());n.putInt("z",p.z());list.add(n);
        });
        tag.put("business_plots",list);
        return tag;
    }
    public void changed(){setDirty();}
}
