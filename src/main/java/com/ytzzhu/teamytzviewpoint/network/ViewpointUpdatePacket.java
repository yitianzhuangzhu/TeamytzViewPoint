package com.ytzzhu.teamytzviewpoint.network;

import com.ytzzhu.teamytzviewpoint.TeamytzViewpoint;
import com.ytzzhu.teamytzviewpoint.ViewpointWaypointColors;
import com.ytzzhu.teamytzviewpoint.blocks.ViewpointMaterial;
import com.ytzzhu.teamytzviewpoint.blocks.ViewpointTileEntity;
import com.ytzzhu.teamytzviewpoint.items.WrenchItem;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端 -> 服务端：提交鸟瞰点的名称与外观材质。
 * 线格式（PLAN §7.1）：byte 协议版本 + long 坐标 + int/byte[] 名称(UTF-8) + int/byte[] 材质(UTF-8)，
 * 字段顺序与 ViewpointNameSyncPacket 完全一致。
 */
public class ViewpointUpdatePacket implements IMessage {
    
    /** 线格式版本，放在最前面；混版时旧解析方会立刻错位失败，好过静默解析出错位数据。2 = 追加了路径点颜色。 */
    private static final byte PROTOCOL_VERSION = 2;
    
    private byte protocolVersion = PROTOCOL_VERSION;
    private BlockPos pos;
    private String name;
    private String material;
    private int color = ViewpointWaypointColors.defaultColor();
    
    public ViewpointUpdatePacket() {
    }
    
    public ViewpointUpdatePacket(BlockPos pos, String name, String material, int color) {
        this.pos = pos;
        this.name = name;
        this.material = material;
        this.color = color;
    }
    
    @Override
    public void fromBytes(ByteBuf buf) {
        this.protocolVersion = buf.readByte();
        this.pos = BlockPos.fromLong(buf.readLong());
        this.name = readString(buf);
        this.material = readString(buf);
        this.color = buf.readInt();
    }
    
    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(PROTOCOL_VERSION);
        buf.writeLong(pos.toLong());
        writeString(buf, name);
        writeString(buf, material);
        buf.writeInt(color);
    }
    
    /** 写一个带长度前缀的 UTF-8 字符串；null 按空串写。 */
    private static void writeString(ByteBuf buf, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }
    
    /** 读一个带长度前缀的 UTF-8 字符串；长度非法（负数或超限）时丢弃剩余字节并当空串，避免畸形包导致 OOM。 */
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
    
    public static class Handler implements IMessageHandler<ViewpointUpdatePacket, IMessage> {
        @Override
        public IMessage onMessage(ViewpointUpdatePacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            World world = player.getServerWorld();
            BlockPos pos = message.pos;
            
            player.getServer().addScheduledTask(() -> {
                // 服务端权威校验 1：目标必须真的是鸟瞰点方块
                TileEntity tileEntity = world.getTileEntity(pos);
                if (!(tileEntity instanceof ViewpointTileEntity)) {
                    return;
                }
                // 服务端权威校验 2：玩家必须在交互距离内（与 ViewpointContainer.canInteractWith 同一门槛）
                if (player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) {
                    return;
                }
                // 服务端权威校验 3：主手或副手必须持有扳手（与 ViewpointBlock.onBlockActivated 的开门条件一致）
                boolean holdingWrench = player.getHeldItemMainhand().getItem() instanceof WrenchItem
                        || player.getHeldItemOffhand().getItem() instanceof WrenchItem;
                if (!holdingWrench) {
                    return;
                }
                // 服务端权威校验 4：名字只做 trim + 截断，为空则回落默认名。
                // **绝不能**用 ViewpointMaterial.normalize() 处理名字：那是给方块注册名用的，
                // 它会 toLowerCase() 并给不含冒号的值补上 "minecraft:" 前缀 ——
                // 套到展示名上会把"鸟瞰点"变成"minecraft:鸟瞰点"。
                String name = (message.name == null) ? "" : message.name.trim();
                if (name.length() > ViewpointTileEntity.MAX_NAME_LENGTH) {
                    name = name.substring(0, ViewpointTileEntity.MAX_NAME_LENGTH);
                }
                if (name.isEmpty()) {
                    name = ViewpointTileEntity.DEFAULT_NAME;
                }
                // 服务端权威校验 5：材质非法一律回落空串（默认石砖），保证进 NBT 的永远是合法值
                String normalizedMaterial = ViewpointMaterial.normalize(message.material);
                String material = (ViewpointMaterial.resolve(normalizedMaterial) != null) ? normalizedMaterial : "";
                // 路径点颜色：只接受调色板里的预设色，其余一律回落默认色，保证进 NBT 的值可控
                int color = ViewpointWaypointColors.isPreset(message.color)
                        ? message.color : ViewpointWaypointColors.defaultColor();
                
                ViewpointTileEntity viewpoint = (ViewpointTileEntity) tileEntity;
                viewpoint.setName(name);
                viewpoint.setMaterial(material);
                viewpoint.setWaypointColor(color);
                viewpoint.markDirty();
                
                // 回发权威结果给编辑者，并广播给附近所有玩家（修复"别人看不到外观变化"）
                ViewpointNameSyncPacket sync = new ViewpointNameSyncPacket(pos, name, material, color);
                TeamytzViewpoint.instance.network.sendTo(sync, player);
                TeamytzViewpoint.instance.network.sendToAllAround(sync, new NetworkRegistry.TargetPoint(
                        world.provider.getDimension(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 64.0));
            });
            
            return null;
        }
    }
}
