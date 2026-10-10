package com.ytzzhu.teamytzviewpoint.client;

import com.ytzzhu.teamytzviewpoint.TeamytzViewpoint;
import com.ytzzhu.teamytzviewpoint.blocks.ViewpointBlock;
import com.ytzzhu.teamytzviewpoint.blocks.ViewpointTileEntity;
import com.ytzzhu.teamytzviewpoint.integration.XaeroMinimapIntegration;
import com.ytzzhu.teamytzviewpoint.network.ViewpointTriggerPacket;
import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointCameraManager;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 鸟瞰点的触发逻辑（纯客户端）：判断玩家是否正站在鸟瞰点方块上，以及按键触发时要做什么。
 * <p>
 * HUD 提示（{@link ViewpointHudRenderer}）与按键处理（{@code ViewpointClientEvents.onKeyInput}）
 * 共用这里的判定，保证"提示出现 = 按键一定有效"。
 */
@SideOnly(Side.CLIENT)
public final class ViewpointTrigger {

    private ViewpointTrigger() {
    }

    /**
     * 玩家正踩着的鸟瞰点方块坐标；没踩着返回 null。
     * <p>
     * 用包围盒底面往下取 0.1 格来判断脚下那一格，而不是用 posY：
     * 玩家站在方块顶面时环绕盒底面正好等于该方块顶面的高度，取整后会落到方块自己那一格。
     */
    public static BlockPos findViewpointUnderPlayer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            return null;
        }
        EntityPlayer player = mc.player;
        BlockPos pos = new BlockPos(player.posX, player.getEntityBoundingBox().minY - 0.1D, player.posZ);
        return (mc.world.getBlockState(pos).getBlock() instanceof ViewpointBlock) ? pos : null;
    }

    /** 现在是否应该显示提示 / 允许触发：站在鸟瞰点上、没有开界面、鸟瞰也没在播放。 */
    public static boolean canTrigger() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.player != null
                && mc.world != null
                && mc.currentScreen == null
                && !ViewpointCameraManager.isActive()
                && findViewpointUnderPlayer() != null;
    }

    /**
     * 满足条件才触发；返回是否真的触发了。
     * 按键处理与 HUD 提示共用 {@link #canTrigger()}，所以"看到提示"就一定"按了有效"。
     */
    public static boolean triggerIfPossible() {
        if (!canTrigger()) {
            return false;
        }
        trigger(findViewpointUnderPlayer());
        return true;
    }

    /**
     * 触发鸟瞰：通知服务端做解锁记录（"按键完全接管"了原来空手右键的解锁职责），
     * 然后在本地开始鸟瞰动画。动画本身是纯客户端的（freecam）。
     */
    public static void trigger(BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || pos == null) {
            return;
        }
        TeamytzViewpoint.instance.network.sendToServer(new ViewpointTriggerPacket(pos));
        addXaeroWaypoint(pos);
        if (!ViewpointCameraManager.isActive()) {
            ViewpointCameraManager.startViewpoint(mc.player);
        }
    }

    /** Xaero 是否在场；只查一次。null = 还没查过。 */
    private static Boolean xaeroPresent;

    /**
     * 用字符串查类判断 Xaero 小地图在不在。
     * <p>
     * 必须在**不引用 Xaero 类型**的类里做这件事：{@link XaeroMinimapIntegration} 是硬引用 Xaero 的，
     * 没装 Xaero 时一旦被加载就会 NoClassDefFoundError。这里先鉴定，通过了才去碰它。
     */
    private static boolean isXaeroPresent() {
        if (xaeroPresent == null) {
            try {
                Class.forName("xaero.common.XaeroMinimapSession");
                xaeroPresent = Boolean.TRUE;
            } catch (Throwable t) {
                xaeroPresent = Boolean.FALSE;
                XAERO_LOG.info("Xaero's Minimap not found, waypoint integration disabled");
            }
        }
        return xaeroPresent.booleanValue();
    }

    private static final org.apache.logging.log4j.Logger XAERO_LOG =
            org.apache.logging.log4j.LogManager.getLogger("teamytzviewpoint-xaero");

    /**
     * 装了 Xaero 小地图时，给这个鸟瞰点加一个同名路径点（颜色取方块 UI 里设置的那个）。
     * 小地图是纯客户端的东西，所以整件事都在客户端做，不走网络。
     */
    private static void addXaeroWaypoint(BlockPos pos) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || !isXaeroPresent()) {
            return;
        }
        if (!(mc.world.getTileEntity(pos) instanceof ViewpointTileEntity)) {
            return;
        }
        ViewpointTileEntity viewpoint = (ViewpointTileEntity) mc.world.getTileEntity(pos);
        XaeroMinimapIntegration.addWaypoint(pos, viewpoint.getName(), viewpoint.getWaypointColor());
    }
}
