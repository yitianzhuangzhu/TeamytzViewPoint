package com.ytzzhu.teamytzviewpoint.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 鸟瞰动画期间的 HUD 开关（等价于玩家自己按 F1）。
 * <p>
 * 实现就是改 {@code Minecraft.gameSettings.hideGUI}，vanilla 的 F1 按下的也正是这个字段。
 * <p>
 * 为什么是"每 tick 同步"而不是在动画开始/结束处各调一次：
 * 动画可能被外力打断（退出世界、维度切换、掉线），
 * 那些路径不会走到 {@code updateReturning()} 的正常收尾，状态可能停在 PULLBACK/ORBITING。
 * 每 tick 拿 {@code isActive()} 对一次账，任何异常路径都会自动还原，不会把玩家的 HUD 永久关掉。
 * <p>
 * 玩家自己原本的 F1 状态会被记住并在动画结束后还原：进来时已经按着 F1 的人，出去还是按着。
 */
@SideOnly(Side.CLIENT)
public final class ViewpointHud {

    /** 是否处于"我们接管了 hideGUI"的状态。 */
    private static boolean overridden = false;

    /** 接管之前玩家自己的 hideGUI 值。 */
    private static boolean savedHideGUI = false;

    private ViewpointHud() {
    }

    /**
     * 客户端每 tick 调一次，把 hideGUI 对齐到"鸟瞰是否进行中"。
     *
     * @param overlooking 当前是否处于鸟瞰动画中（{@code ViewpointCameraManager.isActive()}）
     */
    public static void sync(boolean overlooking) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return;
        }

        if (overlooking) {
            if (!overridden) {
                // 只在这里强制一次，之后不再每 tick 覆盖：
                // 玩家中途按 F1 想自己看 HUD 时不会和我们打架。
                savedHideGUI = mc.gameSettings.hideGUI;
                mc.gameSettings.hideGUI = true;
                overridden = true;
            }
        } else if (overridden) {
            mc.gameSettings.hideGUI = savedHideGUI;
            overridden = false;
        }
    }

    /** 无条件还原（留给需要强制收尾的路径用）。 */
    public static void restore() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.gameSettings != null && overridden) {
            mc.gameSettings.hideGUI = savedHideGUI;
            overridden = false;
        }
    }
}
