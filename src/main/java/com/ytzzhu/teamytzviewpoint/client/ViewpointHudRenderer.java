package com.ytzzhu.teamytzviewpoint.client;

import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointCameraManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

/**
 * 站在鸟瞰点方块上时，在物品栏上方显示一行提示："按 [按键] 进行同步"。
 * <p>
 * 按键名取自 {@link ViewpointKeyBindings#TRIGGER} 的 {@code getDisplayName()}，
 * 所以玩家在"控制"里改键之后，这里显示的会跟着变。
 * <p>
 * 画在 {@code RenderGameOverlayEvent.Post} 的 {@code ALL} 阶段（所有原版 HUD 之后），
 * 与原版"物品名提示"一样居中。竖直位置见 {@link #BOTTOM_OFFSET}。
 */
@SideOnly(Side.CLIENT)
public class ViewpointHudRenderer {

    /**
     * 提示行相对屏幕底部的偏移（缩放后像素）。
     * <p>
     * 取 70 是刻意避让底部三条原版 HUD（数值来自原版字节码）：
     * 物品栏顶边 height-22、心/护甲两行占 height-49..height-30（且从 width/2-91 起，
     * 居中的文字左端会压到最右边那几颗心）、切换物品时的物品名提示 height-59..height-50。
     * 放在 height-70 之后与三者都不重叠，同时又仍在物品栏上方。
     */
    private static final int BOTTOM_OFFSET = 70;

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.fontRenderer == null) {
            return;
        }
        // F1 隐藏 HUD 时不画；鸟瞰播放中、开着界面、或没站在鸟瞰点上都不画
        if (mc.gameSettings.hideGUI || mc.currentScreen != null) {
            return;
        }
        if (ViewpointCameraManager.isActive() || !ViewpointTrigger.canTrigger()) {
            return;
        }

        ScaledResolution res = event.getResolution();
        if (res == null) {
            return;
        }

        String text;
        if (ViewpointKeyBindings.TRIGGER.getKeyCode() == Keyboard.KEY_NONE) {
            // 玩家把这个键解绑了。不能只判断 getDisplayName() 是否为空：
            // 未绑定时它返回的是本地化过的 "NONE" 之类文本，非空，会显示成"按NONE进行同步"。
            text = I18n.format("viewpoint.hud.trigger.unbound");
        } else {
            text = I18n.format("viewpoint.hud.trigger", ViewpointKeyBindings.TRIGGER.getDisplayName());
        }

        FontRenderer fr = mc.fontRenderer;
        int x = (res.getScaledWidth() - fr.getStringWidth(text)) / 2;
        int y = res.getScaledHeight() - BOTTOM_OFFSET;
        fr.drawStringWithShadow(text, (float) x, (float) y, 0xFFFFFF);
    }
}
