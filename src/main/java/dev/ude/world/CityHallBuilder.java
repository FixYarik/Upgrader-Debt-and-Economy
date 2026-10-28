package dev.ude.world;

import dev.ude.UpgraderDebtEconomy;
import dev.ude.npc.CivicNpc;
import dev.ude.npc.CivicRole;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import java.util.*;

/** Vanilla StructureTemplate data, applied in a bounded and restart-safe sequence. */
public final class CityHallBuilder {
    public static final int HALF_X=27, HALF_Z=32, COLUMNS=55*65;
    public record PlanBlock(BlockPos pos,BlockState state) {}
    private CityHallBuilder() {}
    public static List<PlanBlock> plan(ServerLevel level) {
        var template=level.getStructureManager().get(new ResourceLocation(UpgraderDebtEconomy.MOD_ID,"cityhall"))
            .orElseThrow(()->new IllegalStateException("Missing city hall StructureTemplate"));
        CompoundTag n=template.save(new CompoundTag());
        ListTag palette=n.getList("palette",Tag.TAG_COMPOUND);
        List<BlockState> states=new ArrayList<>();
        for(Tag t:palette) states.add(NbtUtils.readBlockState(level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),(CompoundTag)t));
        List<PlanBlock> result=new ArrayList<>();
        for(Tag t:n.getList("blocks",Tag.TAG_COMPOUND)) {
            CompoundTag b=(CompoundTag)t; ListTag p=b.getList("pos",Tag.TAG_INT);
            result.add(new PlanBlock(new BlockPos(p.getInt(0)-27,p.getInt(1)-5,p.getInt(2)-32),states.get(b.getInt("state"))));
        }
        if(result.size()!=55*24*65) throw new IllegalStateException("Unexpected city hall template size");
        return List.copyOf(result);
    }
    public static boolean loaded(ServerLevel level,BlockPos c) {
        if(c.getY()-5<level.getMinBuildHeight() || c.getY()+18>=level.getMaxBuildHeight()) return false;
        if(!level.getWorldBorder().isWithinBounds(c.offset(-27,0,-32)) || !level.getWorldBorder().isWithinBounds(c.offset(27,0,32))) return false;
        for(int x=(c.getX()-27)>>4;x<=(c.getX()+27)>>4;x++)
            for(int z=(c.getZ()-32)>>4;z<=(c.getZ()+32)>>4;z++) if(!level.hasChunk(x,z)) return false;
        return true;
    }
    public static boolean natural(BlockState s) {
        return s.is(Blocks.DIRT)||s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.COARSE_DIRT)||s.is(Blocks.ROOTED_DIRT)
            ||s.is(Blocks.PODZOL)||s.is(Blocks.STONE)||s.is(Blocks.ANDESITE)||s.is(Blocks.DIORITE)||s.is(Blocks.GRANITE)
            ||s.is(Blocks.DEEPSLATE)||s.is(Blocks.SAND)||s.is(Blocks.RED_SAND)||s.is(Blocks.SANDSTONE)
            ||s.is(Blocks.RED_SANDSTONE)||s.is(Blocks.GRAVEL)||s.is(Blocks.CLAY)||s.is(Blocks.TUFF);
    }
    public static boolean replaceable(ServerLevel level,BlockPos p) {
        BlockState s=level.getBlockState(p);
        return s.getFluidState().isEmpty() && level.getBlockEntity(p)==null && (s.isAir()||s.canBeReplaced()||natural(s));
    }
    /** Every column is checked, including underground support and unsampled player blocks. */
    public static boolean safeColumn(ServerLevel level,BlockPos center,int column) {
        int x=column%55-27,z=column/55-32;
        BlockPos surface=center.offset(x,0,z);
        if(CityHallData.get(level).playerModified(new ChunkPos(surface).toLong())) return false;
        int height=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,surface.getX(),surface.getZ());
        if(Math.abs(height-1-center.getY())>2) return false;
        BlockState ground=level.getBlockState(new BlockPos(surface.getX(),height-1,surface.getZ()));
        if(!(ground.is(Blocks.GRASS_BLOCK)||ground.is(Blocks.DIRT)||ground.is(Blocks.SAND)||ground.is(Blocks.RED_SAND)||ground.is(Blocks.PODZOL))) return false;
        for(int y=-6;y<=18;y++) {
            BlockPos at=center.offset(x,y,z); BlockState s=level.getBlockState(at);
            if(!replaceable(level,at)) return false;
            if(at.getY()<height-1 && (!natural(s)||!s.isSolidRender(level,at))) return false;
        }
        return true;
    }
    public static boolean canBuild(ServerLevel level,BlockPos center) {
        if(!loaded(level,center)) return false;
        for(int i=0;i<COLUMNS;i++)if(!safeColumn(level,center,i))return false;
        return true;
    }
    /** Applies only air/natural terrain or identical already-written blocks; stops on a new obstruction. */
    public static boolean step(ServerLevel level,CityHallData.Hall hall,List<PlanBlock> plan,int budget) {
        if(!loaded(level,hall.center))return false;
        for(int n=0;n<budget && hall.cursor<plan.size();n++,hall.cursor++) {
            PlanBlock b=plan.get(hall.cursor); BlockPos at=hall.center.offset(b.pos());
            if(level.getBlockState(at).is(b.state().getBlock()))continue;
            if(!replaceable(level,at) || ForgeEventFactory.onBlockPlace(null,BlockSnapshot.create(level.dimension(),level,at),Direction.UP)) {
                hall.phase=CityHallData.Phase.BLOCKED; CityHallData.get(level).setDirty();return false;
            }
            level.setBlock(at,b.state(),2);
        }
        CityHallData.get(level).setDirty();
        if(hall.cursor==plan.size()) {
            hall.phase=CityHallData.Phase.COMPLETE;
            // Complete blocks are saved before staff reconciliation; staff UUIDs are deterministic.
            ensureStaff(level,hall); return true;
        }
        return false;
    }
    public static UUID staffId(UUID hall,CivicRole role) {
        return UUID.nameUUIDFromBytes((hall+":"+role.name()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static void ensureStaff(ServerLevel level,CityHallData.Hall hall) {
        if(hall.phase!=CityHallData.Phase.COMPLETE || !loaded(level,hall.center))return;
        for(CivicRole role:CivicRole.values()) {
            if(hall.staffRetry.getOrDefault(role.name(),0L)>level.getGameTime())continue;
            UUID id=staffId(hall.id,role);
            if(level.getEntity(id)!=null)continue;
            CivicNpc npc=UpgraderDebtEconomy.CIVIC_NPC.get().create(level);
            if(npc==null)continue;
            npc.setUUID(id);npc.assign(hall.id,role,hall.center.offset(role.x,role.y,role.z));
            if(level.addFreshEntity(npc)&&hall.staffRetry.remove(role.name())!=null)CityHallData.get(level).setDirty();
        }
    }
    /** Admin command also creates a persisted reservation; no synchronous unbounded build. */
    public static void build(ServerLevel level,BlockPos c) {
        if(level.dimension()!=net.minecraft.world.level.Level.OVERWORLD)throw new IllegalStateException("City halls are supported in the Overworld");
        CityHallData data=CityHallData.get(level);
        long key=new ChunkPos(c).toLong();
        if(data.halls().stream().anyMatch(h->h.village==key)) throw new IllegalStateException("City hall already registered here");
        var hall=data.discover(level,key,new net.minecraft.world.level.levelgen.structure.BoundingBox(c.offset(-1,0,-1)));
        if(hall==null)throw new IllegalStateException("City hall disabled by configured frequency or limit");
        hall.center=c.immutable();hall.phase=CityHallData.Phase.BUILDING;data.setDirty();level.getDataStorage().save();
    }
}
