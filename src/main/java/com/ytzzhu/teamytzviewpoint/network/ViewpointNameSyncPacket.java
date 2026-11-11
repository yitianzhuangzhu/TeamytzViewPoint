package com.ytzzhu.teamytzviewpoint.network;

import com.ytzzhu.teamytzviewpoint.ViewpointWaypointColors;
import com.ytzzhu.teamytzviewpoint.blocks.ViewpointMaterial;
import com.ytzzhu.teamytzviewpoint.blocks.ViewpointTileEntity;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 服务端 -> 客户端：把权威的名称、外观材质、路径点颜色与<b>解锁状态</b>同步给客户端。
 * 线格式（PLAN §7.1）与 ViewpointUpdatePacket 逐字段一致。
 * <p>
 * 解锁状态必须走这个包，不能指望原版同步：{@code SPacketBlockChange} 只带方块状态、
 * 不带方块实体数据，所以服务端解锁后客户端不会自动知道（图标也就不会变绿）。
 * 换维度/重进世界时则走 {@code getUpdateTag()} / {@code handleUpdateTag()}，无需本包。
 */
public class ViewpointNameSyncPacket implements IMessage {
    
    /** 线格式版本，放在最前面；混版时旧解析方会立刻错位失败。3 = 追加了解锁状态。 */
    private static final byte PROTOCOL_VERSION = 3;
    
    private byte protocolVersion = PROTOCOL_VERSION;
    private BlockPos pos;
    private String name;
    private String material;
    private int color = ViewpointWaypointColors.defaultColor();
    private boolean unlocked;
    
    public ViewpointNameSyncPacket() {
    }
    
    public ViewpointNameSyncPacket(BlockPos pos, String name, String material, int color, boolean unlocked) {
        this.pos = pos;
        this.name = name;
        this.material = material;
        this.color = color;
        this.unlocked = unlocked;
    }
    
    @Override
    public void fromBytes(ByteBuf buf) {
        this.protocolVersion = buf.readByte();
        this.pos = BlockPos.fromLong(buf.readLong());
        this.name = readString(buf);
        this.material = readString(buf);
        this.color = buf.readInt();
        this.unlocked = buf.readBoolean();
    }
    
    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(PROTOCOL_VERSION);
        buf.writeLong(pos.toLong());
        writeString(buf, name);
        writeString(buf, material);
        buf.writeInt(color);
        buf.writeBoolean(unlocked);
    }
    
    /** 写一个带长度前缀的 UTF-8 字符串；null 按空串写。 */
    private static void writeString(ByteBuf buf, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }
    
    /** 读一个带长度前缀的 UTF-8 字符串；长度非法（负数或超限）时丢弃剩余字节并当空串。 */
    private static String readString(ByteBuf buf) {
        int length = buf.readInt();
        if (length < 0 || length > ViewpointMaterial.MAX_WIRE_BYTES) {
            buf.skipBytes(buf.readableBytes());
            return "";
        }
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
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
                            ViewpointTileEntity viewpoint = (ViewpointTileEntity) tileEntity;
                            viewpoint.setName(message.name);
                            // 非法材质不在这里重复校验，由 ViewpointMaterial.getRenderStateOrDefault 在渲染时兜底
                            viewpoint.setMaterial(message.material);
                            viewpoint.setWaypointColor(message.color);
                            // 解锁状态：方块上方的图标据此在白色/绿色之间切换
                            viewpoint.setUnlocked(message.unlocked);
                        }
                    }
                });
            }
            return null;
        }
    }
}
