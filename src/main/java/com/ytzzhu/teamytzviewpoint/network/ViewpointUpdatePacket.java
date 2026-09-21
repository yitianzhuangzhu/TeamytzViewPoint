package com.ytzzhu.teamytzviewpoint.network;

import com.ytzzhu.teamytzviewpoint.TeamytzViewpoint;
import com.ytzzhu.teamytzviewpoint.blocks.ViewpointTileEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class ViewpointUpdatePacket implements IMessage {
    
    private BlockPos pos;
    private String name;
    
    public ViewpointUpdatePacket() {
    }
    
    public ViewpointUpdatePacket(BlockPos pos, String name) {
        this.pos = pos;
        this.name = name;
    }
    
    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
        int nameLength = buf.readInt();
        byte[] nameBytes = new byte[nameLength];
        buf.readBytes(nameBytes);
        this.name = new String(nameBytes);
    }
    
    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
        byte[] nameBytes = name.getBytes();
        buf.writeInt(nameBytes.length);
        buf.writeBytes(nameBytes);
    }
    
    public static class Handler implements IMessageHandler<ViewpointUpdatePacket, IMessage> {
        @Override
        public IMessage onMessage(ViewpointUpdatePacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            World world = player.getServerWorld();
            BlockPos pos = message.pos;
            
            player.getServer().addScheduledTask(() -> {
                ViewpointTileEntity tileEntity = (ViewpointTileEntity) world.getTileEntity(pos);
                if (tileEntity != null) {
                    tileEntity.setName(message.name);
                    TeamytzViewpoint.instance.network.sendTo(new ViewpointNameSyncPacket(pos, message.name), player);
                }
            });
            
            return null;
        }
    }
}
