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
 * 鸟瞰运镜期间的玩家显示（仅客户端）。
 *
 * <h3>分工</h3>
 * <ul>
 *   <li><b>替身</b>（{@link ViewpointPlayerActor}）是放进客户端世界的普通实体，
 *       正常由<b>原版实体通道</b>绘制 —— 着色/混合/深度/光照/矩阵都由
 *       {@code RenderLivingBase} 自己摆好，不存在任何状态继承问题。</li>
 *   <li>本类只在<b>原版这一帧没画替身</b>时兜底补画一次（见 {@link #onRenderWorldLast}），
 *       保证任何 mod（实体剔除之类）或异常路径都不会让玩家整个消失。</li>
 *   <li>同时取消<b>真实玩家</b>的渲染，否则真身与替身在同一位置会重影。</li>
 * </ul>
 *
 * <h3>两个已经踩过的坑</h3>
 * <ol>
 *   <li><b>替身被视锥剔除</b>：只写 {@code posX/posY/posZ} 时 {@code boundingBox} 不会更新，
 *       而原版 {@code RenderGlobal} 的剔除用的正是 {@code getEntityBoundingBox()} ——
 *       结果原版一次都画不到替身。已在 {@code ViewpointPlayerActor.syncToPlayer}
 *       改用 {@code Entity.setPosition}（它会重建包围盒）修掉。</li>
 *   <li><b>兜底绘制被染色</b>：{@code RenderWorldLastEvent} 是整帧 GL 状态最脏的时刻，
 *       早期实现只 {@code pushMatrix()} 就画，身体继承了残留的带 alpha 着色，
 *       变成半透明偏蓝，而披风正常 —— 因为 {@code LayerCape} 自己会
 *       {@code GlStateManager.color(1,1,1,1)} 重置颜色。下面的兜底绘制因此把
 *       颜色/混合/深度/雾全部显式摆平。</li>
 * </ol>
 */
@SideOnly(Side.CLIENT)
public final class ViewpointPlayerRenderer {

    /** 本帧原版实体通道是否画过替身。 */
    private static boolean actorRenderedThisFrame = false;

    /** 替身是否至少被原版画过一次（决定能不能取消真身，避免把玩家弄成透明人）。 */
    private static boolean actorRenderedOnce = false;

    /**
     * 鸟瞰期间取消<b>真实本地玩家</b>的渲染（替身会代替它出现在同一位置）。
     * <p>
     * 只有替身<b>确实被画过</b>之后才取消：万一某个路径把替身漏掉，宁可多看到一个真身，
     * 也不要让玩家整个消失。
     */
    @SubscribeEvent
    public void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!ViewpointCameraManager.isActive()) {
            // 动画没在播放：清标记，下次开演重新从"不取消"开始
            actorRenderedThisFrame = false;
            actorRenderedOnce = false;
            return;
        }
        if (ViewpointCameraManager.playerActor() == null || !actorRenderedOnce) {
            return;
        }
        if (event.getEntityPlayer() == Minecraft.getMinecraft().player) {
            event.setCanceled(true);
        }
    }

    /** 记录原版这一帧画过替身。 */
    @SubscribeEvent
    public void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        ViewpointPlayerActor actor = ViewpointCameraManager.playerActor();
        if (actor != null && event.getEntityPlayer() == actor) {
            actorRenderedThisFrame = true;
            actorRenderedOnce = true;
        }
    }

    /**
     * 兜底：原版这一帧没画替身时补画一次。
     * <p>
     * 正常帧不会走到这里（替身由原版实体通道画），所以下面这些 GL 状态处理只在异常路径生效。
     */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        boolean rendered = actorRenderedThisFrame;
        actorRenderedThisFrame = false;

        if (rendered || !ViewpointCameraManager.isActive()) {
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

        double x = ViewpointCameraManager.startPlayerX;
        double y = ViewpointCameraManager.startPlayerY;
        double z = ViewpointCameraManager.startPlayerZ;
        actor.syncToPlayer(x, y, z);

        GlStateManager.pushMatrix();
        try {
            // ---- 摆到确定干净的基准：不继承前一个渲染器留下的任何东西 ----
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F); // 残留着色（带 alpha）会让身体半透明偏色
            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);               // 不写深度会显得"透出来"
            GlStateManager.disableBlend();                // 身体不该半透明
            GlStateManager.disableFog();                  // 雾会把模型染成天空色
            GlStateManager.enableCull();

            GlStateManager.translate(-renderManager.viewerPosX, -renderManager.viewerPosY, -renderManager.viewerPosZ);
            RenderHelper.enableStandardItemLighting();
            try {
                renderPlayer.doRender((AbstractClientPlayer) actor, x, y, z, actor.rotationYaw, event.getPartialTicks());
            } finally {
                RenderHelper.disableStandardItemLighting();
            }
        } finally {
            // 还原成安全默认值，别把脏状态留给后面的 GUI 渲染
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.depthMask(true);
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }
}
