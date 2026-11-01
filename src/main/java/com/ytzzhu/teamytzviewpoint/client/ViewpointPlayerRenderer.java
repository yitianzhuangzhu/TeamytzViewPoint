package com.ytzzhu.teamytzviewpoint.client;

import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointCameraManager;
import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointPlayerActor;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 鸟瞰运镜期间的玩家显示（仅客户端）。
 *
 * <h3>现在只做一件事：取消"真实玩家"的渲染</h3>
 * 真实玩家在动画期间就站在鸟瞰点上，而替身实体（{@link ViewpointPlayerActor}）也在同一个位置。
 * 不取消前者就会看到两个重叠的身体，所以这里把它取消掉。
 *
 * <h3>为什么不自己画替身</h3>
 * 早期版本在 {@code RenderWorldLastEvent} 里手动 {@code doRender} 替身，结果是
 * <b>身体变成半透明、偏蓝／偏色，而披风正常</b>。原因很明确：
 * <ul>
 *   <li>{@code RenderWorldLastEvent} 是整帧 GL 状态最"脏"的时刻 —— 前一个渲染器可能留下
 *       带 alpha 的 {@code GlStateManager.color}、开启的混合、关掉的深度写入、以及遗留的矩阵变换；
 *       我们当时只 {@code pushMatrix()}，没有重置着色/混合/深度，于是模型被整体染色。</li>
 *   <li>之所以<b>披风不受影响</b>：{@code LayerCape.doRenderLayer} 每次都会先
 *       {@code GlStateManager.color(1,1,1,1)} 把颜色重置回纯白；而身体和装备层不会，
 *       所以只有它们继承了残留的染色。这一点反过来正好印证了上面的判断。</li>
 * </ul>
 * 正确做法是<b>不要在那种时刻自己画实体</b>：替身是放进客户端世界的普通实体，
 * 原版实体通道本来就会画它，而且用的是完全正确的 GL 状态。
 * 我们只需在渲染之前把它的位置/姿态摆好（见 {@code ViewpointCameraManager.updateActorPose()}，
 * 由 {@code ViewpointClientEvents.onRenderTick} 在 {@code RenderTickEvent.START} 调用）。
 */
@SideOnly(Side.CLIENT)
public final class ViewpointPlayerRenderer {

    /**
     * 替身是否已经被原版实体通道真正画过至少一次。
     * <p>
     * 自愈保护：正常情况下替身是放进世界的普通实体，原版每帧都会画它，这个标记第一帧就会置位。
     * 但万一某个 mod（实体剔除之类）或异常路径把它漏掉了，我们就<b>不</b>取消真身的渲染 ——
     * 宁可多看到一个真实玩家，也不要让玩家整个消失。
     */
    private static boolean actorRenderedOnce = false;

    /**
     * 鸟瞰期间取消<b>真实本地玩家</b>的渲染（替身会代替它出现在同一位置）。
     * <p>
     * 三个保护条件：
     * <ul>
     *   <li>替身不存在时不取消 —— 否则玩家会整个消失；</li>
     *   <li>替身还没被成功画过时不取消（见 {@link #actorRenderedOnce}），同样是避免变成透明人；</li>
     *   <li>只针对本地玩家，别的玩家与别的 mod 的演出演员都不受影响。</li>
     * </ul>
     */
    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!ViewpointCameraManager.isActive()) {
            // 动画没在播放：清掉上一轮的标记，下次开演重新从"不取消"开始
            actorRenderedOnce = false;
            return;
        }
        if (ViewpointCameraManager.playerActor() == null || !actorRenderedOnce) {
            return;
        }
        EntityPlayer rendered = event.getEntityPlayer();
        if (rendered == Minecraft.getMinecraft().player) {
            event.setCanceled(true);
        }
    }

    /** 记录替身确实被原版画过（用于上面的自愈保护）。 */
    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        ViewpointPlayerActor actor = ViewpointCameraManager.playerActor();
        if (actor != null && event.getEntityPlayer() == actor) {
            actorRenderedOnce = true;
        }
    }
}
