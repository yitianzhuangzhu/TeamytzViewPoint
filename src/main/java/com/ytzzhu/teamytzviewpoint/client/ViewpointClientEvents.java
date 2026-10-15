package com.ytzzhu.teamytzviewpoint.client;

import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointCameraManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ViewpointClientEvents {

    /** 为 true 时表示我们自己的强制重画正在执行（防止 RenderLivingEvent.Post 自己数自己）。 */
    private static boolean forceRenderingPlayer = false;

    /** 本帧原版实体通道是否已经画过本地玩家。 */
    private boolean playerRenderedThisFrame = false;

    // ================= 输入 =================

    /**
     * 鸟瞰点的触发方式：站在鸟瞰点方块上，按玩家在"控制"里绑定的触发键（见
     * {@link ViewpointKeyBindings}）。<b>不再是右键触发</b>——右键只在拿扳手时打开设置界面。
     * <p>
     * 用 FML 的 {@code InputEvent.KeyInputEvent}（1.12.2 里没有 Forge 自己的 InputEvent），
     * 配 {@code KeyBinding.isPressed()} 拿到"按下那一下"的边沿触发，避免长按连发。
     */
    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (ViewpointKeyBindings.TRIGGER.isPressed()) {
            ViewpointTrigger.triggerIfPossible();
        }
    }

    /**
     * 鸟瞰期间的移动锁。
     * <p>
     * {@code InputUpdateEvent} <b>不可取消</b>（类上没有 {@code @Cancelable}），所以只能改
     * {@link MovementInput} 的字段。它是在 {@code EntityPlayerSP.onLivingUpdate()} 里
     * {@code updatePlayerMoveState()} 之后发出的，改在这里正好来得及。
     * <p>
     * 相机已经不挂在玩家身上了，但玩家实体本身还在世界里 —— 不锁的话玩家会一边环绕
     * 一边走出去，鸟瞰点的环绕中心就废了。
     */
    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        if (!ViewpointCameraManager.isActive()) {
            return;
        }
        MovementInput input = event.getMovementInput();
        if (input == null) {
            return;
        }
        input.moveStrafe = 0.0F;
        input.moveForward = 0.0F;
        input.forwardKeyDown = false;
        input.backKeyDown = false;
        input.leftKeyDown = false;
        input.rightKeyDown = false;
        input.jump = false;
        input.sneak = false;
    }

    /**
     * 鸟瞰期间不画第一人称手臂。
     * <p>
     * 相机是独立实体，原版会把它当成"非玩家视角"从而跳过手臂渲染；这里再显式取消一次，
     * 保证任何情况下（比如别的 mod 改了 renderViewEntity 的判断）都不会在环绕镜头里看到一只手。
     */
    @SubscribeEvent
    public void onRenderHand(RenderHandEvent event) {
        if (ViewpointCameraManager.isActive()) {
            event.setCanceled(true);
        }
    }

    // ================= tick / 渲染 =================

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ViewpointCameraManager.update();
            // 鸟瞰期间隐藏 HUD（等价于游戏自己的 F1）；动画结束或被中途打断时自动还原
            ViewpointHud.sync(ViewpointCameraManager.isActive());
        }
    }

    /**
     * 每帧把轨道采样写进相机实体。
     * <p>
     * 必须用 {@code RenderTickEvent.START}：它在 {@code entityRenderer.updateCameraAndRender}
     * 之前发出，{@code renderTickTime} 就是这一帧的 partialTicks。
     */
    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        ViewpointCameraManager.updateCamera(event.renderTickTime);
    }

    /**
     * 记录"本帧原版实体通道画过本地玩家没有"。
     * <p>
     * 相机是独立实体、玩家也没被隐藏，所以正常情况下原版就会把站在鸟瞰点上的玩家画出来，
     * 这里只是数一下；只有原版没画（例如玩家处于隐身状态）才需要下面兜底重画一次。
     */
    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Post<EntityLivingBase> event) {
        if (forceRenderingPlayer) {
            return;
        }
        EntityPlayer localPlayer = Minecraft.getMinecraft().player;
        if (localPlayer != null && event.getEntity() == localPlayer) {
            playerRenderedThisFrame = true;
        }
    }

    /**
     * 兜底：原版这一帧没画本地玩家时（典型是隐身），在鸟瞰期间补画一次。
     * <p>
     * 位置/朝向用开演时冻结的快照 —— 鸟瞰期间玩家实体的朝向被我们压住、位置也没动过，
     * 所以这里只需要临时把"画在哪"覆盖掉，画完立刻还原，<b>不需要</b>旧版那套
     * "快照整个实体的渲染相关字段再回填"的重型补丁（那是 freecam 会搅动玩家实体才需要的）。
     */
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        boolean overlooking = ViewpointCameraManager.isActive();
        boolean needFallback = overlooking && !playerRenderedThisFrame;
        this.playerRenderedThisFrame = false;
        if (!needFallback) {
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

        double x = ViewpointCameraManager.startPlayerX;
        double y = ViewpointCameraManager.startPlayerY;
        double z = ViewpointCameraManager.startPlayerZ;
        float yaw = ViewpointCameraManager.startPlayerYaw;

        // 只临时改"画在哪"这四个量；玩家实体本身在鸟瞰期间没被动过，画完原样填回即可
        double savedPosX = player.posX;
        double savedPosY = player.posY;
        double savedPosZ = player.posZ;
        float savedYaw = player.rotationYaw;
        double savedPrevX = player.prevPosX;
        double savedPrevY = player.prevPosY;
        double savedPrevZ = player.prevPosZ;
        float savedPrevYaw = player.prevRotationYaw;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-renderManager.viewerPosX, -renderManager.viewerPosY, -renderManager.viewerPosZ);
        forceRenderingPlayer = true;
        try {
            player.posX = x;
            player.posY = y;
            player.posZ = z;
            player.prevPosX = x;
            player.prevPosY = y;
            player.prevPosZ = z;
            player.rotationYaw = yaw;
            player.prevRotationYaw = yaw;
            ((RenderPlayer) render).doRender((AbstractClientPlayer) player, x, y, z, yaw, event.getPartialTicks());
        } finally {
            forceRenderingPlayer = false;
            player.posX = savedPosX;
            player.posY = savedPosY;
            player.posZ = savedPosZ;
            player.prevPosX = savedPrevX;
            player.prevPosY = savedPrevY;
            player.prevPosZ = savedPrevZ;
            player.rotationYaw = savedYaw;
            player.prevRotationYaw = savedPrevYaw;
            GlStateManager.popMatrix();
        }
    }
}
