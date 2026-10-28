package dev.ude.gametest;

import dev.ude.UpgraderDebtEconomy;
import dev.ude.menu.CivicMenu;
import dev.ude.npc.*;
import dev.ude.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@GameTestHolder(UpgraderDebtEconomy.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CityHallGameTests {
    private static net.minecraft.server.level.ServerPlayer player(ServerLevel level){
        return new net.minecraft.server.level.ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ude-test")){
            @Override public void sendSystemMessage(net.minecraft.network.chat.Component message){}
        };
    }
    private static BlockPos flat(GameTestHelper h){
        BlockPos origin=h.absolutePos(new BlockPos(40,8,40));
        // Above sea level: an excavated underwater test box would immediately flood from its neighbours.
        BlockPos c=new BlockPos(origin.getX(),90,origin.getZ());ServerLevel level=h.getLevel();
        for(int x=-27;x<=27;x++)for(int z=-32;z<=32;z++){
            for(int y=c.getY()+1;y<level.getMaxBuildHeight();y++){
                BlockPos p=new BlockPos(c.getX()+x,y,c.getZ()+z);
                if(!level.isEmptyBlock(p))level.setBlock(p,Blocks.AIR.defaultBlockState(),2);
            }
            for(int y=-6;y<=0;y++)level.setBlock(c.offset(x,y,z),(y==0?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),2);
        }
        return c;
    }
    @GameTest(template="test/empty",timeoutTicks=800)
    public static void villagePlacementAndRoster(GameTestHelper h){
        ServerLevel level=h.getLevel();BlockPos c=flat(h);
        h.assertTrue(CityHallBuilder.canBuild(level,c),"Initial north plot is safe");
        // North candidate zero is exactly this flat plot, outside the village bounds.
        BlockPos village=c.offset(32,0,41);
        var hall=CityHallData.get(level).discover(level,new ChunkPos(village).toLong(),new BoundingBox(village));
        h.assertTrue(hall!=null,"Village record created");
        AtomicReference<net.minecraft.world.phys.Vec3> start=new AtomicReference<>();
        h.succeedWhen(()->{
            h.assertTrue(hall.phase==CityHallData.Phase.COMPLETE,"Automatic scheduler must complete the template: "+hall.phase);
            h.assertTrue(hall.center.equals(c),"Only the validated north candidate is used");
            h.assertFalse(CityHallWorldgen.overlaps(hall.bounds(),hall.villageBounds),"Village is not replaced");
            h.assertTrue(level.getBlockState(c.offset(0,1,-4)).is(Blocks.STONE_BRICK_STAIRS),"Stairs connect floors");
            h.assertTrue(level.getBlockState(c.offset(-4,-4,21)).is(Blocks.GOLD_BLOCK),"Vault contents exist");
            h.assertTrue(CityHallProtection.vault(level,c.offset(-4,-4,21)),"Vault contents protected");
            var staff=level.getEntitiesOfClass(CivicNpc.class,AABB.of(hall.bounds()));
            h.assertTrue(staff.size()==CivicRole.values().length,"Exactly nine role NPCs");
            for(CivicRole role:CivicRole.values())h.assertTrue(staff.stream().filter(n->n.role()==role&&hall.id.equals(n.hallId())).count()==1,"Unique staff role: "+role);
            for(CivicNpc npc:staff)h.assertTrue(level.noCollision(npc,npc.getBoundingBox()),"Staff must not spawn in a counter or wall: "+npc.role());
            for(int x=-25;x<=25;x+=3)for(int z=-30;z<=30;z+=3)for(int y:new int[]{-4,1,8}){
                BlockPos p=c.offset(x,y,z);
                if(level.isEmptyBlock(p))h.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,p)>0,"No hostile-mob dark spot at "+p);
            }
            CityHallBuilder.ensureStaff(level,hall);
            h.assertTrue(level.getEntitiesOfClass(CivicNpc.class,AABB.of(hall.bounds())).size()==9,"Reconciliation cannot duplicate staff");
            var reloaded=CityHallData.load(CityHallData.get(level).save(new CompoundTag())).byId(hall.id);
            h.assertTrue(reloaded!=null&&reloaded.center.equals(c)&&reloaded.phase==CityHallData.Phase.COMPLETE,"Hall survives NBT reload");
            CivicNpc collector=(CivicNpc)level.getEntity(CityHallBuilder.staffId(hall.id,CivicRole.VALERA));
            h.assertFalse(collector.isNoAi(),"NPC AI enabled");
            if(start.get()==null)start.set(collector.position());
            h.assertTrue(collector.position().distanceToSqr(start.get())>0.5,"Collector actually walks its patrol");
            var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            zombie.setPos(collector.getX()+1,collector.getY(),collector.getZ());
            float before=zombie.getHealth();
            h.assertTrue(collector.doHurtTarget(zombie)&&zombie.getHealth()<before,"Patrol can defend staff using real mob damage");
            var visitor=player(level);visitor.setPos(collector.getX()+1,collector.getY(),collector.getZ());
            h.assertFalse(collector.doHurtTarget(visitor),"Patrol cannot attack an ordinary visitor");
            zombie.setPos(c.getX()+100,c.getY(),c.getZ());
            h.assertFalse(collector.doHurtTarget(zombie),"Guards do not pursue enemies outside city hall bounds");
        });
    }
    @GameTest(template="test/empty",timeoutTicks=120)
    public static void safePlacementRejectsObstructions(GameTestHelper h){
        var level=h.getLevel();BlockPos c=flat(h);
        h.assertTrue(CityHallBuilder.loaded(level,c),"Footprint chunks loaded: "+c);
        for(int i=0;i<CityHallBuilder.COLUMNS;i++){
            BlockPos at=c.offset(i%55-27,0,i/55-32);
            int height=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at.getX(),at.getZ());
            h.assertTrue(CityHallBuilder.safeColumn(level,c,i),"Natural column "+i+" at "+at+" height="+height+" ground="+level.getBlockState(at));
        }
        BlockPos obstruction=c.offset(1,1,1);level.setBlock(obstruction,Blocks.OAK_PLANKS.defaultBlockState(),2);
        h.assertFalse(CityHallBuilder.canBuild(level,c),"Unsampled player block rejected");
        level.setBlock(obstruction,Blocks.AIR.defaultBlockState(),2);
        BlockPos underground=c.offset(2,-3,2);level.setBlock(underground,Blocks.CHEST.defaultBlockState(),2);
        h.assertFalse(CityHallBuilder.canBuild(level,c),"Buried container rejected");
        level.setBlock(underground,Blocks.WATER.defaultBlockState(),2);
        h.assertFalse(CityHallBuilder.canBuild(level,c),"Water and caves rejected");
        level.setBlock(underground,Blocks.DIRT.defaultBlockState(),2);
        CityHallData.get(level).protectChunk(new ChunkPos(c).toLong());
        h.assertFalse(CityHallBuilder.canBuild(level,c),"Player-modified natural terrain rejected");
        var copy=CityHallData.load(CityHallData.get(level).save(new CompoundTag()));
        h.assertTrue(copy.playerModified(new ChunkPos(c).toLong()),"Player chunk protection persists");
        h.succeed();
    }
    @GameTest(template="test/empty",timeoutTicks=120)
    public static void bankerMenuValidatesPlayerAndRole(GameTestHelper h){
        var level=h.getLevel();var p=player(level);
        CivicNpc banker=UpgraderDebtEconomy.CIVIC_NPC.get().create(level);
        banker.assign(UUID.randomUUID(),CivicRole.BANKER,h.absolutePos(new BlockPos(10,5,10)));level.addFreshEntity(banker);
        p.setPos(banker.getX()+1,banker.getY(),banker.getZ());
        var menu=new CivicMenu(1,p.getInventory(),banker);
        h.assertTrue(menu.clickMenuButton(p,0),"Open account through NPC menu");
        h.assertFalse(menu.clickMenuButton(p,3),"Same-tick replay rate limited");
        var other=player(level);other.setPos(p.getX(),p.getY(),p.getZ());
        h.assertFalse(menu.clickMenuButton(other,2),"Different player cannot use menu");
        p.setPos(banker.getX()+20,banker.getY(),banker.getZ());
        h.assertFalse(menu.stillValid(p),"Remote menu invalid");
        p.setPos(banker.getX()+1,banker.getY(),banker.getZ());
        h.runAtTickTime(12,()->{
            h.assertTrue(menu.clickMenuButton(p,3),"Issue actual microloan");
            var engine=UdeSavedData.get(level.getServer()).engine();
            h.assertTrue(engine.balance(p.getUUID())==500,"Loan principal credited");
            h.assertTrue(engine.loans().stream().anyMatch(l->l.owner.equals(p.getUUID())&&l.outstanding==575),"Fixed fee applied");
            banker.assign(UUID.randomUUID(),CivicRole.MAYOR,banker.blockPosition());
            var mayorMenu=new CivicMenu(2,p.getInventory(),banker);
            h.assertFalse(mayorMenu.clickMenuButton(p,3),"Forged bank action rejected for mayor");
            CompoundTag n=new CompoundTag();banker.saveWithoutId(n);
            var restored=UpgraderDebtEconomy.CIVIC_NPC.get().create(level);restored.load(n);
            h.assertTrue(restored.role()==CivicRole.MAYOR&&restored.hallId().equals(banker.hallId()),"NPC role and hall persist");
            BlockPos door=h.absolutePos(new BlockPos(15,5,15));
            level.setBlock(door.below(),Blocks.STONE.defaultBlockState(),2);
            var state=UpgraderDebtEconomy.VAULT_DOOR.get().defaultBlockState();
            level.setBlock(door,state,2);
            level.setBlock(door.above(),state.setValue(net.minecraft.world.level.block.DoorBlock.HALF,net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER),2);
            UpgraderDebtEconomy.VAULT_DOOR.get().use(state,level,door,p,net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(door),net.minecraft.core.Direction.NORTH,door,false));
            h.assertFalse(level.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),"Non-admin cannot open vault door");
            level.setBlock(door.east(),Blocks.REDSTONE_BLOCK.defaultBlockState(),3);
            h.assertFalse(level.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),"Redstone cannot bypass vault access");
            h.succeed();
        });
    }

    @GameTest(template="test/empty",timeoutTicks=160)
    public static void interruptedPlacementProtectsNewBlocks(GameTestHelper h){
        var level=h.getLevel();BlockPos c=flat(h);
        var hall=new CityHallData.Hall(UUID.randomUUID(),new ChunkPos(c).toLong(),new BoundingBox(c.offset(200,0,200)));
        hall.center=c;hall.phase=CityHallData.Phase.BUILDING;
        var plan=CityHallBuilder.plan(level);
        CityHallBuilder.step(level,hall,plan,256);
        h.assertTrue(hall.cursor==256,"Placement work is bounded");
        BlockPos obstruction=c.offset(plan.get(hall.cursor+1).pos());
        level.setBlock(obstruction,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
        CityHallBuilder.step(level,hall,plan,512);
        h.assertTrue(hall.phase==CityHallData.Phase.BLOCKED,"A newly placed player block stops generation");
        h.assertTrue(level.getBlockState(obstruction).is(Blocks.DIAMOND_BLOCK),"New obstruction remains intact");
        h.succeed();
    }
}
