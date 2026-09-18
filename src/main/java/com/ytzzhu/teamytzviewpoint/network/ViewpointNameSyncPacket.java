package com.ytzzhu.teamytzviewpoint.network;

import com.ytzzhu.teamytzviewpoint.blocks.ViewpointTileEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

public class ViewpointNameSyncPacket implements IMessage {
    
    private BlockPos pos;
    private String name;
    
    public ViewpointNameSyncPacket() {
    }
    
    public ViewpointNameSyncPacket(BlockPos pos, String name) {
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
    
    public static class Handler implements IMessageHandler<ViewpointNameSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(ViewpointNameSyncPacket message, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                    World world = net.minecraft.client.Minecraft.getMinecraft().world;
                    if (world != null) {
                        TileEntity tileEntity = world.getTileEntity(message.pos);
                        if (tileEntity instanceof ViewpointTileEntity) {
                            ((ViewpointTileEntity) tileEntity).setName(message.name);
                        }
                    }
                });
            }
            return null;
        }
    }
}