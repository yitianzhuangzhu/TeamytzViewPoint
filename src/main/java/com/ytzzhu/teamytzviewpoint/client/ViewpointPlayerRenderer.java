package com.ytzzhu.teamytzviewpoint.client;

import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointCameraManager;
import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointPlayerActor;
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
 * 鸟瞰运镜期间把"站在鸟瞰点上的自己"画出来（仅客户端）。
 *
 * <h3>为什么画的是替身实体，而不是真实玩家</h3>
 * 相机换成独立实体（{@link com.ytzzhu.teamytzviewpoint.viewpoint.CinematicCamera}）之后，
 * 视角不再挂在玩家身上，原版那一次玩家渲染就没了 —— 实机表现是环绕镜头里看不到自己。
 * <p>
 * 第一版的做法是"取消原版渲染 + 把真实玩家强制重画一次"，仍然是徒劳的：
 * 真实玩家能不能被画出来，取决于原版一串条件（render view entity 是不是它自己、第三人称档位、
 * 骑乘、区块加载……），在相机独立的前提下这些条件很难保证，实测就是一次都画不出来。
 * <p>
 * 现在的做法改成<b>确定性</b>的：开演时在鸟瞰点位置放一个客户端专属的"假玩家"实体
 * （{@link ViewpointPlayerActor}，同一个 GameProfile → 皮肤与本人一致），
 * 它就是一个普通实体，走原版实体的正常渲染通道。我们只做两件事：
 * <ol>
 *   <li>取消原版对<b>真实玩家</b>与<b>替身</b>的渲染 —— 前者会重影（真人就站在替身那里），
 *       后者会和下面的强制重画叠成两个身体；</li>
 *   <li>在世界渲染完之后，把替身实体画一次。</li>
 * </ol>
 *
 * <h3>取消点为什么可靠</h3>
 * {@code RenderPlayerEvent.Pre} 由 {@code RenderPlayer.doRender} 的<b>第一句</b>发出，且带
 * {@code @Cancelable}（已用 javap 核对字节码：0..25 发事件、{@code ifeq 26 / return} 就是取消后的提前返回）。
 */
@SideOnly(Side.CLIENT)
public final class ViewpointPlayerRenderer {

    /** 为 true 时表示"我们自己的重画正在执行"，此时不要取消渲染。 */
    private static boolean drawingActor = false;

    /**
     * 鸟瞰期间取消原版的两处玩家渲染，改由本类统一画替身：
     * <ul>
     *   <li><b>真实本地玩家</b>：不取消的话真人（就站在替身所在的鸟瞰点上）会和替身重影；</li>
     *   <li><b>替身实体自己</b>：它被 {@code addEntityToWorld} 放进了客户端世界，
     *       所以原版实体通道也会画它一次 —— 不取消就会和下面的强制重画叠成两个身体。</li>
     * </ul>
     * 其余玩家、以及别的 mod 生成的演出演员都不受影响。
     */
    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (drawingActor || !ViewpointCameraManager.isActive()) {
            return;
        }
        EntityPlayer rendered = event.getEntityPlayer();
        if (rendered == Minecraft.getMinecraft().player
                || rendered == ViewpointCameraManager.playerActor()) {
            event.setCanceled(true);
        }
    }

    /**
     * 在世界渲染之后把替身实体画一次。
     * <p>
     * 放在 {@code RenderWorldLastEvent}：此时世界（含实体）已经画完，我们这一笔是最后加上去的，
     * 不会被后续的实体/透明排序盖掉，也不必和别的 mod 抢顺序。
     */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!ViewpointCameraManager.isActive()) {
            return;
        }
        ViewpointPlayerActor actor = ViewpointCameraManager.playerActor();
        if (actor == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        if (player == null) {
            return;
        }

        RenderManager renderManager = mc.getRenderManager();
        Render<?> render = renderManager.getEntityRenderObject(actor);
        if (!(render instanceof RenderPlayer)) {
            return;
        }
        RenderPlayer renderPlayer = (RenderPlayer) render;

        // 位置用冻结值；朝向与姿态每帧从玩家身上同步过来，身体才自然（举盾/拉弓/潜行都跟手）
        double x = ViewpointCameraManager.startPlayerX;
        double y = ViewpointCameraManager.startPlayerY;
        double z = ViewpointCameraManager.startPlayerZ;
        actor.syncToPlayer(x, y, z);

        float partialTicks = event.getPartialTicks();

        GlStateManager.pushMatrix();
        // 渲染原点在相机身上，而我们在世界坐标画，所以先抵消相机位移
        GlStateManager.translate(-renderManager.viewerPosX, -renderManager.viewerPosY, -renderManager.viewerPosZ);
        // 实体通道的标准光照，保证身体不会忽明忽暗
        RenderHelper.enableStandardItemLighting();
        drawingActor = true;
        try {
            renderPlayer.doRender((AbstractClientPlayer) actor, x, y, z, actor.rotationYaw, partialTicks);
        } finally {
            drawingActor = false;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
    }
}
