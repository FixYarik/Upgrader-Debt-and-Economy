package dev.ude.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.BlockHitResult;

/** An actual access-controlled door; levers cannot bypass NPC vault security. */
public final class VaultDoorBlock extends DoorBlock {
    public VaultDoorBlock(){super(BlockBehaviour.Properties.copy(Blocks.IRON_DOOR),net.minecraft.world.level.block.state.properties.BlockSetType.IRON);}
    @Override public void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,BlockPos from,boolean moving){
        // Redstone is intentionally not an access credential.
    }
    @Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player instanceof ServerPlayer p && p.createCommandSourceStack().hasPermission(2))setOpen(player,level,state,pos,!state.getValue(OPEN));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
