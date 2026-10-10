package com.ytzzhu.teamytzviewpoint.client;

import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointCameraManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ViewpointClientEvents {

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

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ViewpointCameraManager.update();
            // 鸟瞰期间隐藏 HUD（等价于游戏自己的 F1）；动画结束或被中途打断时自动还原
            ViewpointHud.sync(ViewpointCameraManager.isActive());
        }
    }
}
