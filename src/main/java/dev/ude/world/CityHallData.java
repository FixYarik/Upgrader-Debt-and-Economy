package dev.ude.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Separate, versioned settlement records; generation never depends on player names. */
public final class CityHallData extends SavedData {
    public enum Phase { SEARCHING, BUILDING, COMPLETE, BLOCKED, NO_SITE }
    public static final class Hall {
        public final UUID id;
        public final long village;
        public final BoundingBox villageBounds;
        public BlockPos center;
        public Phase phase = Phase.SEARCHING;
        public int candidate, cursor;
        public final Set<Integer> rejected = new HashSet<>();
        public final Map<String,Long> staffRetry = new HashMap<>();
        public Hall(UUID id, long village, BoundingBox bounds) { this.id=id; this.village=village; this.villageBounds=bounds; }
        public BoundingBox bounds() {
            return new BoundingBox(center.getX()-27, center.getY()-5, center.getZ()-32,
                center.getX()+27, center.getY()+18, center.getZ()+32);
        }
        public boolean vault(BlockPos p) {
            if (center==null) return false;
            int x=p.getX()-center.getX(), y=p.getY()-center.getY(), z=p.getZ()-center.getZ();
            return Math.abs(x)<=9 && y>=-5 && y<=-1 && z>=11 && z<=29;
        }
    }
    private final Map<Long,Hall> halls = new LinkedHashMap<>();
    private final Set<Long> playerChunks = new HashSet<>();
    public void protectChunk(long chunk) { if(playerChunks.add(chunk)) setDirty(); }
    public boolean playerModified(long chunk) { return playerChunks.contains(chunk); }
    public static CityHallData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CityHallData::load, CityHallData::new, "ude_city_halls");
    }
    public Collection<Hall> halls() { return Collections.unmodifiableCollection(halls.values()); }
    public Hall byId(UUID id) { return halls.values().stream().filter(h->h.id.equals(id)).findFirst().orElse(null); }
    public Hall discover(ServerLevel level, long village, BoundingBox bounds) {
        Hall old=halls.get(village); if (old!=null) return old;
        if (halls.size()>=CityHallConfig.MAX_HALLS.get()) return null;
        UUID id=UUID.nameUUIDFromBytes((level.dimension().location()+":"+village).getBytes(StandardCharsets.UTF_8));
        if (Math.floorMod(id.hashCode(),100)>=CityHallConfig.VILLAGE_CHANCE.get()) return null;
        Hall hall=new Hall(id,village,bounds); halls.put(village,hall); setDirty(); return hall;
    }
    public static CityHallData load(CompoundTag tag) {
        CityHallData data=new CityHallData();
        for(long chunk:tag.getLongArray("player_chunks")) data.playerChunks.add(chunk);
        for (Tag t:tag.getList("halls",Tag.TAG_COMPOUND)) {
            CompoundTag n=(CompoundTag)t; int[] b=n.getIntArray("village_bounds");
            if (b.length!=6 || !n.hasUUID("id")) continue;
            Hall h=new Hall(n.getUUID("id"),n.getLong("village"),new BoundingBox(b[0],b[1],b[2],b[3],b[4],b[5]));
            if(n.contains("center",Tag.TAG_COMPOUND)) h.center=NbtUtils.readBlockPos(n.getCompound("center"));
            try { h.phase=Phase.valueOf(n.getString("phase")); } catch (IllegalArgumentException e) { h.phase=Phase.BLOCKED; }
            h.candidate=Math.max(0,n.getInt("candidate"));
            for(int index:n.getIntArray("rejected")) h.rejected.add(index);
            CompoundTag retry=n.getCompound("staff_retry");
            for(String role:retry.getAllKeys())h.staffRetry.put(role,retry.getLong(role));
            // Reconcile from the beginning: data and chunk files can have different save times.
            h.cursor=0;
            if(h.center==null && h.phase!=Phase.SEARCHING && h.phase!=Phase.NO_SITE) h.phase=Phase.BLOCKED;
            data.halls.put(h.village,h);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        tag.putInt("data_version",1); ListTag list=new ListTag();
        tag.putLongArray("player_chunks",playerChunks.stream().mapToLong(Long::longValue).toArray());
        for(Hall h:halls.values()) {
            CompoundTag n=new CompoundTag(); n.putUUID("id",h.id); n.putLong("village",h.village);
            BoundingBox b=h.villageBounds;
            n.putIntArray("village_bounds",new int[]{b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()});
            if(h.center!=null)n.put("center",NbtUtils.writeBlockPos(h.center));
            n.putString("phase",h.phase.name()); n.putInt("candidate",h.candidate); list.add(n);
            n.putIntArray("rejected",h.rejected.stream().mapToInt(Integer::intValue).toArray());
            CompoundTag retry=new CompoundTag();h.staffRetry.forEach(retry::putLong);n.put("staff_retry",retry);
        }
        tag.put("halls",list); return tag;
    }
}
