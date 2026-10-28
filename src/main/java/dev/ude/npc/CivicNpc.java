package dev.ude.npc;

import dev.ude.menu.CivicMenu;
import dev.ude.world.CityHallData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import java.util.UUID;

/** A persistent role entity, with bounded routes instead of a disabled villager brain. */
public final class CivicNpc extends Villager {
    private static final EntityDataAccessor<Integer> ROLE=SynchedEntityData.defineId(CivicNpc.class,EntityDataSerializers.INT);
    private UUID hallId;
    private BlockPos workplace=BlockPos.ZERO;
    private int route, stalled;
    private double lastX,lastZ;
    public CivicNpc(EntityType<? extends Villager> type,Level level){super(type,level);setPersistenceRequired();}
    public static AttributeSupplier.Builder civicAttributes(){return Villager.createAttributes().add(Attributes.MAX_HEALTH,30).add(Attributes.MOVEMENT_SPEED,0.35).add(Attributes.ATTACK_DAMAGE,4);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(ROLE,0);}
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(1,new MeleeAttackGoal(this,0.8,true));
        goalSelector.addGoal(2,new OpenDoorGoal(this,true));
        goalSelector.addGoal(5,new LookAtPlayerGoal(this,Player.class,6));
        goalSelector.addGoal(6,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this,Mob.class,10,true,false,this::mayDefend));
    }
    private boolean mayDefend(LivingEntity entity){
        if(!role().patrol()||hallId==null||!(entity instanceof net.minecraft.world.entity.monster.Enemy)||!(level() instanceof ServerLevel level))return false;
        var h=CityHallData.get(level).byId(hallId);
        return h!=null&&h.center!=null&&new net.minecraft.world.phys.AABB(h.center.offset(-35,-6,-40),h.center.offset(35,20,40)).contains(entity.position());
    }
    @Override public boolean doHurtTarget(Entity entity){
        return entity instanceof LivingEntity living&&mayDefend(living)&&entity.hurt(damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE));
    }
    public CivicRole role(){return CivicRole.values()[Math.floorMod(entityData.get(ROLE),CivicRole.values().length)];}
    public UUID hallId(){return hallId;}
    public BlockPos workplace(){return workplace;}
    public void assign(UUID hall,CivicRole role,BlockPos home){
        hallId=hall;entityData.set(ROLE,role.ordinal());workplace=home.immutable();
        setVillagerData(getVillagerData().setProfession(role.profession));
        setCustomName(Component.translatable(role.key()));setCustomNameVisible(true);
        moveTo(home.getX()+0.5,home.getY(),home.getZ()+0.5,180,0);
        setPersistenceRequired();
        if(getNavigation() instanceof GroundPathNavigation nav){nav.setCanOpenDoors(true);nav.setCanPassDoors(true);}
    }
    @Override protected void customServerAiStep(){
        // Deliberately replace the vanilla trading/breeding brain with an explicit short route.
        if(hallId==null || tickCount%40!=0 || !(level() instanceof ServerLevel level))return;
        CityHallData.Hall hall=CityHallData.get(level).byId(hallId);
        if(hall==null||hall.center==null||hall.phase!=CityHallData.Phase.COMPLETE)return;
        if(getTarget()!=null){if(getTarget().isAlive()&&mayDefend(getTarget()))return;setTarget(null);}
        if(getNavigation() instanceof GroundPathNavigation nav){nav.setCanOpenDoors(true);nav.setCanPassDoors(true);}
        boolean working=level.getDayTime()%24000<12000;
        BlockPos target=workplace;
        if(role().patrol()) {
            int[][] points=role()==CivicRole.GUARD?new int[][]{{0,-4,8},{-12,-4,4},{12,-4,4},{0,-4,8}}:
                new int[][]{{-16,1,4},{-4,1,4},{12,1,4},{0,1,-5}};
            int[] p=points[route%points.length];target=hall.center.offset(p[0],p[1],p[2]);
            if(blockPosition().closerThan(target,2))route++;
        }else if(!working){
            // A small office walk; banker stays at the counter during working hours.
            target=workplace.offset(0,0,(route%2==0)?2:-2);
            if(blockPosition().closerThan(target,1.5))route++;
        }
        if(!level.hasChunkAt(target))return;
        if(distanceToSqr(target.getX()+.5,target.getY(),target.getZ()+.5)>1.5)
            getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,0.55);
        if(Math.abs(getX()-lastX)+Math.abs(getZ()-lastZ)<0.1 && !blockPosition().closerThan(target,2))stalled++;else stalled=0;
        lastX=getX();lastZ=getZ();
        // Bounded recovery: change waypoint, never force-load or teleport through players' homes.
        if(stalled>=5){getNavigation().stop();route++;stalled=0;}
    }
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand){
        if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        if(player instanceof ServerPlayer server && hallId!=null && isAlive()) {
            var persistent=server.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            if(role()==CivicRole.MAYOR && !persistent.getBoolean("UdeMayorGuide")){
                var book=dev.ude.world.MayorGuide.make();
                if(!server.getInventory().add(book))server.drop(book,false);
                persistent.putBoolean("UdeMayorGuide",true);
                server.getPersistentData().put(Player.PERSISTED_NBT_TAG,persistent);
            }
            NetworkHooks.openScreen(server,new SimpleMenuProvider((id,inv,p)->new CivicMenu(id,inv,this),getDisplayName()),buf->buf.writeInt(getId()));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);
        if(hallId!=null)tag.putUUID("UdeHall",hallId);
        tag.putString("UdeRole",role().name());tag.put("UdeWorkplace",NbtUtils.writeBlockPos(workplace));tag.putInt("UdeRoute",route);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);
        if(tag.hasUUID("UdeHall"))hallId=tag.getUUID("UdeHall");
        try{entityData.set(ROLE,CivicRole.valueOf(tag.getString("UdeRole")).ordinal());}catch(IllegalArgumentException ignored){entityData.set(ROLE,0);}
        workplace=NbtUtils.readBlockPos(tag.getCompound("UdeWorkplace"));route=Math.max(0,tag.getInt("UdeRoute"));setPersistenceRequired();
    }
    @Override public boolean removeWhenFarAway(double distance){return false;}
}
