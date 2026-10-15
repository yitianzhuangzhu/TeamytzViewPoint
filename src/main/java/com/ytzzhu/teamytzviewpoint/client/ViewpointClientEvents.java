package com.ytzzhu.teamytzviewpoint.client;

import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointCameraManager;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 鸟瞰运镜的客户端事件：输入、tick 推进、每帧相机采样。
 * <p>
 * 玩家本体的渲染不在这里 —— 它单独放在 {@link ViewpointPlayerRenderer}
 * （那件事需要"取消原版渲染 + 精确重画一次"两个配套事件，独立一类更好读）。
 */
@SideOnly(Side.CLIENT)
public class ViewpointClientEvents {

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
}
