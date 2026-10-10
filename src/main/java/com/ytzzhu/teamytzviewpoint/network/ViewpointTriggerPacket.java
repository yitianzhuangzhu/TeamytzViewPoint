package com.ytzzhu.teamytzviewpoint.network;

import com.ytzzhu.teamytzviewpoint.blocks.ViewpointBlock;
import com.ytzzhu.teamytzviewpoint.blocks.ViewpointTileEntity;
import com.ytzzhu.teamytzviewpoint.data.ViewpointUnlockData;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 客户端 → 服务端：玩家站在鸟瞰点上按下了触发键。
 * <p>
 * 鸟瞰动画本身是纯客户端行为（freecam），不需要服务端参与；这个包只负责把
 * "解锁"这件事搬到服务端来做 —— 也就是原来"空手右键"时 {@code ViewpointBlock.onBlockActivated}
 * 里做的那两件事：写 {@link ViewpointUnlockData}、给玩家发"已解锁"消息。
 * FTBQuests 的解锁任务靠轮询 {@code unlockData.hasUnlocked(uuid, 名字)} 判定，所以这条链必须保留。
 * <p>
 * 服务端是唯一的可信入口，因此这里重新做一遍"玩家确实站在这一格上"的校验。
 */
public class ViewpointTriggerPacket implements IMessage {

    private BlockPos pos;

    public ViewpointTriggerPacket() {
    }

    public ViewpointTriggerPacket(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = BlockPos.fromLong(buf.readLong());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos.toLong());
    }

    public static class Handler implements IMessageHandler<ViewpointTriggerPacket, IMessage> {
        @Override
        public IMessage onMessage(ViewpointTriggerPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            World world = player.getServerWorld();
            BlockPos pos = message.pos;

            player.getServer().addScheduledTask(() -> {
                if (pos == null) {
                    return;
                }
                // 服务端权威校验：玩家必须站在这一格上（客户端只说"我按了键"，不可信）。
                // 但**不能严格比对**：客户端按下按键到这个包被处理之间至少隔一个 tick（高延迟下更多），
                // 玩家边走/边跳边按时脚下已经挪走，严格比对会把这次触发静默吞掉 ——
                // 表现是本地动画照放、却没有"已解锁"消息，FTBQuests 也不计数，玩家完全不知道发生了什么。
                // 所以允许"站在这一格附近"：水平 1.4 格内（相邻格/斜角格都在内）、竖直方向在该格上下合理范围内。
                // 这仍然禁止从远处解锁。
                BlockPos feet = new BlockPos(player.posX, player.getEntityBoundingBox().minY - 0.1D, player.posZ);
                if (!feet.equals(pos)) {
                    double dx = player.posX - (pos.getX() + 0.5D);
                    double dz = player.posZ - (pos.getZ() + 0.5D);
                    double dy = player.getEntityBoundingBox().minY - pos.getY();
                    if (dx * dx + dz * dz > 2.0D || dy < -0.5D || dy > 2.0D) {
                        return;
                    }
                }
                IBlockState state = world.getBlockState(pos);
                if (!(state.getBlock() instanceof ViewpointBlock)) {
                    return;
                }
                TileEntity tileEntity = world.getTileEntity(pos);
                if (!(tileEntity instanceof ViewpointTileEntity)) {
                    return;
                }
                ViewpointTileEntity viewpoint = (ViewpointTileEntity) tileEntity;
                if (viewpoint.isUnlocked()) {
                    // 已经解锁过：不重复发消息，也不重复记账（动画仍由客户端照常播放）
                    return;
                }
                String name = viewpoint.getName();
                viewpoint.setUnlocked(true);
                ViewpointUnlockData.get(world).unlockViewpoint(player.getUniqueID(), name);
                player.sendMessage(new TextComponentTranslation("viewpoint.message.unlocked", name));
            });

            return null;
        }
    }
}
