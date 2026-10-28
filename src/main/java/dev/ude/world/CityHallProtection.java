package dev.ude.world;

import dev.ude.UpgraderDebtEconomy;
import dev.ude.npc.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=UpgraderDebtEconomy.MOD_ID)
public final class CityHallProtection {
    private CityHallProtection(){}
    public static boolean vault(ServerLevel level,BlockPos p){return CityHallData.get(level).halls().stream().anyMatch(h->h.center!=null&&h.vault(p));}
    private static boolean reserved(ServerLevel level,BlockPos p){return CityHallData.get(level).halls().stream().anyMatch(h->h.center!=null&&h.phase==CityHallData.Phase.BUILDING&&h.bounds().isInside(p));}
    private static boolean structure(ServerLevel level,BlockPos p){return CityHallData.get(level).halls().stream().anyMatch(h->h.center!=null&&h.bounds().isInside(p));}
    private static boolean admin(Player p){return p instanceof ServerPlayer s&&s.createCommandSourceStack().hasPermission(2);}
    @SubscribeEvent public static void staffDied(net.minecraftforge.event.entity.living.LivingDeathEvent e){
        if(e.getEntity() instanceof CivicNpc npc&&npc.level() instanceof ServerLevel level&&npc.hallId()!=null){
            var data=CityHallData.get(level);var hall=data.byId(npc.hallId());
            if(hall!=null){hall.staffRetry.put(npc.role().name(),level.getGameTime()+1200);data.setDirty();}
        }
    }
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent e){
        if(!(e.getLevel() instanceof ServerLevel level))return;
        if(!admin(e.getPlayer())&&(vault(level,e.getPos())||reserved(level,e.getPos()))){e.setCanceled(true);return;}
        if(level.dimension()==net.minecraft.world.level.Level.OVERWORLD)CityHallData.get(level).protectChunk(new net.minecraft.world.level.ChunkPos(e.getPos()).toLong());
    }
    @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent e){
        if(!(e.getLevel() instanceof ServerLevel level)||!(e.getEntity() instanceof Player p))return;
        if(!admin(p)&&(vault(level,e.getPos())||reserved(level,e.getPos()))){e.setCanceled(true);return;}
        if(level.dimension()==net.minecraft.world.level.Level.OVERWORLD)CityHallData.get(level).protectChunk(new net.minecraft.world.level.ChunkPos(e.getPos()).toLong());
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.RightClickBlock e){
        if(e.getLevel() instanceof ServerLevel level&&!admin(e.getEntity())&&vault(level,e.getPos())){
            e.setCanceled(true);e.getEntity().sendSystemMessage(Component.translatable("npc.upgrader_debt_economy.vault_locked"));
        }
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate e){
        if(e.getLevel() instanceof ServerLevel level)e.getAffectedBlocks().removeIf(p->structure(level,p));
    }
    @SubscribeEvent public static void piston(PistonEvent.Pre e){
        if(!(e.getLevel() instanceof ServerLevel level))return;
        for(var hall:CityHallData.get(level).halls())if(hall.center!=null){
            BlockPos c=hall.center,p=e.getPos();
            if(Math.abs(p.getX()-c.getX())<=22&&p.getZ()-c.getZ()>=-2&&p.getZ()-c.getZ()<=42&&p.getY()-c.getY()>=-18&&p.getY()-c.getY()<=12){e.setCanceled(true);return;}
        }
    }
    /** Guards warn first and use bounded vanilla damage only while the intruder remains in the vault. */
    public static void tick(ServerLevel level){
        if(level.getGameTime()%20!=0)return;
        for(ServerPlayer p:level.players()){
            if(admin(p)||p.isCreative()||p.isSpectator())continue;
            var hall=CityHallData.get(level).halls().stream().filter(h->h.phase==CityHallData.Phase.COMPLETE&&h.vault(p.blockPosition())).findFirst().orElse(null);
            var tag=p.getPersistentData();
            if(hall==null){tag.remove("UdeVaultWarning");continue;}
            if(!tag.contains("UdeVaultWarning")){
                tag.putLong("UdeVaultWarning",level.getGameTime());p.sendSystemMessage(Component.translatable("npc.upgrader_debt_economy.vault_warning"));
            }
            if(level.getEntity(CityHallBuilder.staffId(hall.id,CivicRole.GUARD)) instanceof CivicNpc guard){
                guard.getNavigation().moveTo(p,0.7);
                if(level.getGameTime()-tag.getLong("UdeVaultWarning")>=100&&level.getGameTime()%40==0&&guard.distanceToSqr(p)<6.25&&guard.hasLineOfSight(p)){
                    guard.swing(InteractionHand.MAIN_HAND);p.hurt(p.damageSources().mobAttack(guard),2);
                }
            }
        }
    }
}
