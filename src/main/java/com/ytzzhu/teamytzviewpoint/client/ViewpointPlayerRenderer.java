package com.ytzzhu.teamytzviewpoint.client;

import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointCameraManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 鸟瞰运镜期间把<b>玩家本体</b>画出来（仅客户端）。
 *
 * <h3>为什么需要这个类</h3>
 * 相机换成独立实体（{@link com.ytzzhu.teamytzviewpoint.viewpoint.CinematicCamera}）之后，
 * 视角不再挂在玩家身上，玩家本体就"没人管"了 —— 实测表现为环绕镜头里看不到自己。
 * 这里把这件事明确接管下来：<b>取消原版那一次玩家渲染，改为在鸟瞰期间精确重画一次</b>。
 *
 * <h3>为什么要"取消 + 重画"，而不是"只在没画的时候补画"</h3>
 * 补画式写法（判断原版这一帧画没画过）有两个绕不开的坑：
 * <ol>
 *   <li><b>可能一次都不画</b>：原版会不会画本地玩家取决于 render view entity 是不是它自己、
 *       第三人称档位、是否骑乘、所在区块有没有加载等一串条件。相机独立之后这些条件全变了，
 *       "以为原版会画"是猜不准的 —— 实测就是一次都没画出来。</li>
 *   <li><b>可能画两次（重影）</b>：玩家实体本身一直在世界里，原版实体遍历本来就可能画它。</li>
 * </ol>
 * 所以这里改成确定性做法：原版那次一律取消 → 我们自己画且只画一次。
 * 位置/朝向一律取开演时冻结的快照，于是无论玩家实体在动画期间被谁动过，
 * 画面里的身体都稳定地站在鸟瞰点上，不会抖、不会重影。
 *
 * <h3>取消点为什么可靠</h3>
 * {@code RenderPlayerEvent.Pre} 由 {@code RenderPlayer.doRender} 的<b>第一句</b>发出，
 * 且带 {@code @Cancelable}（已用 javap 核对：字节码 0..25 是 post 事件、
 * {@code ifeq 26 / return} 就是取消后的提前返回），所以取消它等价于"这次玩家渲染不画"。
 * <p>
 * 重画走的是 {@code RenderPlayer.doRender}，它同样会发一次 {@code RenderPlayerEvent.Pre}，
 * 靠 {@link #drawingPlayer} 这个重入标记放行，不会自己把自己取消掉。
 */
@SideOnly(Side.CLIENT)
public final class ViewpointPlayerRenderer {

    /** 为 true 时表示"我们自己的重画正在执行"，此时不要取消渲染。 */
    private static boolean drawingPlayer = false;

    /**
     * 鸟瞰期间取消原版对本地玩家的渲染。
     * <p>
     * 只针对本地玩家：别的玩家、以及别的 mod 生成的 actor（例如 RPG 那套演出演员）都不受影响。
     */
    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (drawingPlayer || !ViewpointCameraManager.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && event.getEntityPlayer() == mc.player) {
            event.setCanceled(true);
        }
    }

    /**
     * 在鸟瞰期间把玩家本体画在"开演时冻结的位置与朝向"上。
     * <p>
     * 放在 {@code RenderWorldLastEvent}：此时世界（含实体）已经画完，我们这一笔是最后加上去的，
     * 不会被后续的原版实体的深度/透明排序盖掉，也不需要和别的 mod 抢顺序。
     */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!ViewpointCameraManager.isActive()) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (!(player instanceof AbstractClientPlayer)) {
            return;
        }

        RenderManager renderManager = mc.getRenderManager();
        Render<?> render = renderManager.getEntityRenderObject(player);
        if (!(render instanceof RenderPlayer)) {
            return;
        }
        RenderPlayer renderPlayer = (RenderPlayer) render;

        double x = ViewpointCameraManager.startPlayerX;
        double y = ViewpointCameraManager.startPlayerY;
        double z = ViewpointCameraManager.startPlayerZ;
        float yaw = ViewpointCameraManager.startPlayerYaw;
        float partialTicks = event.getPartialTicks();

        // 只临时改"画在哪、朝哪边"这几个量。玩家实体本身在鸟瞰期间没被动过，
        // 而且移动输入与朝向都被锁住，所以不需要旧版 freecam 时代那套
        // "快照整个实体的渲染字段再回填"的重型补丁。
        double savedPosX = player.posX;
        double savedPosY = player.posY;
        double savedPosZ = player.posZ;
        double savedPrevPosX = player.prevPosX;
        double savedPrevPosY = player.prevPosY;
        double savedPrevPosZ = player.prevPosZ;
        float savedYaw = player.rotationYaw;
        float savedPrevYaw = player.prevRotationYaw;

        GlStateManager.pushMatrix();
        // 渲染原点在相机身上，而我们在世界坐标画，所以先抵消相机位移
        GlStateManager.translate(-renderManager.viewerPosX, -renderManager.viewerPosY, -renderManager.viewerPosZ);
        // 玩家模型自带光照，这里按原版实体通道的做法开标准光照，保证身体不会忽明忽暗
        RenderHelper.enableStandardItemLighting();
        drawingPlayer = true;
        try {
            player.posX = x;
            player.posY = y;
            player.posZ = z;
            player.prevPosX = x;
            player.prevPosY = y;
            player.prevPosZ = z;
            player.rotationYaw = yaw;
            player.prevRotationYaw = yaw;

            renderPlayer.doRender((AbstractClientPlayer) player, x, y, z, yaw, partialTicks);
        } finally {
            drawingPlayer = false;
            RenderHelper.disableStandardItemLighting();
            player.posX = savedPosX;
            player.posY = savedPosY;
            player.posZ = savedPosZ;
            player.prevPosX = savedPrevPosX;
            player.prevPosY = savedPrevPosY;
            player.prevPosZ = savedPrevPosZ;
            player.rotationYaw = savedYaw;
            player.prevRotationYaw = savedPrevYaw;
            GlStateManager.popMatrix();
        }
    }
}
