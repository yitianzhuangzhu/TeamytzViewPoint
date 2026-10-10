package com.ytzzhu.teamytzviewpoint.integration;

import com.ytzzhu.teamytzviewpoint.ViewpointWaypointColors;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.WaypointColor;

/**
 * 与 Xaero 小地图的联动：触发鸟瞰点时，在玩家当前所在的世界里加一个与鸟瞰点同名的路径点。
 * <p>
 * <b>本类是"硬引用"Xaero 类的</b>（编译期靠 libs 里的 jar），所以它绝对不能被提前加载：
 * 调用方 {@code ViewpointTrigger} 先用 {@code Class.forName("xaero.common.XaeroMinimapSession")}
 * 判断 Xaero 在不在，只有在了才会碰这个类。没装 Xaero 的整合包里这个类永远不会被加载，
 * 也就不会出现 NoClassDefFoundError。
 * <p>
 * 取法（全部用 javap 核对过 1.12.2 的 xaerominimap 26.5.1）：
 * {@code XaeroMinimapSession.getCurrentSession()} → {@code getWaypointsManager()}
 * → {@code getCurrentWorld()} → {@code getCurrentSet()} → {@code add(Waypoint)}
 * → {@code getWorldManagerIO().saveWorld(world)} 落盘。
 * <p>
 * <b>为什么这几个 xaero.common 类用全限定名而不 import、为什么整类加
 * {@code @SuppressWarnings("deprecation")}</b>：26.5.1 里 {@code xaero.common.*} 是 Xaero 为 1.12.2
 * 保留的旧版兼容层（新架构在 {@code xaero.hud.*}）。这里刻意继续用兼容层 —— 它的行为被钉死在我们
 * 随整合包发布的那个 Xaero 版本上。坑在于：这几个类是**类本身**被标了过时，而 javac 会把警告挂在
 * import 语句上，@SuppressWarnings 管不到 import（方法级/类级都试过，无效），所以改成内联全限定名，
 * 让用法落在方法体里再由类级抑制盖住。
 * <p>
 * 整段逻辑都在 try/catch 里：万一将来 Xaero 移除兼容层，表现是"不再生成路径点 + 一条日志"，不会崩游戏。
 */
@SideOnly(Side.CLIENT)
@SuppressWarnings("deprecation")
public final class XaeroMinimapIntegration {

    private static final Logger LOGGER = LogManager.getLogger(XaeroMinimapIntegration.class);

    /** 路径点在小地图上显示的缩写符号。 */
    private static final String SYMBOL = "VP";

    private XaeroMinimapIntegration() {
    }

    /**
     * 在当前世界里添加（或刷新）这个鸟瞰点的路径点。
     *
     * @param pos   鸟瞰点方块的坐标
     * @param name  路径点名（与鸟瞰点同名）
     * @param color 路径点颜色（0xRRGGBB，来自方块 UI 里选的预设色）
     */
    public static void addWaypoint(BlockPos pos, String name, int color) {
        xaero.common.minimap.waypoints.WaypointsManager manager;
        xaero.common.minimap.waypoints.WaypointWorld world;
        try {
            xaero.common.XaeroMinimapSession session = xaero.common.XaeroMinimapSession.getCurrentSession();
            if (session == null) {
                return;                     // 还没进入世界（主菜单/退到标题界面）
            }
            manager = session.getWaypointsManager();
            if (manager == null) {
                return;
            }
            world = manager.getCurrentWorld();
            if (world == null) {
                return;
            }
            xaero.common.minimap.waypoints.WaypointSet set = world.getCurrentSet();
            if (set == null) {
                return;
            }

            // 玩家是站在方块顶面上的，所以路径点的 y 取方块上方一格（作者指定）
            int x = pos.getX();
            int y = pos.getY() + 1;
            int z = pos.getZ();
            int colorIndex = toXaeroColorIndex(color);

            // 同名同坐标的路径点已经存在就只更新颜色，不重复添加（鸟瞰点会被反复触发）
            for (Waypoint existing : set.getWaypoints()) {
                if (existing.getX() == x && existing.getY() == y && existing.getZ() == z
                        && name != null && name.equals(existing.getName())) {
                    existing.setColor(colorIndex);
                    saveWorld(manager, world);
                    return;
                }
            }

            set.add(new Waypoint(x, y, z, name, SYMBOL, colorIndex));
        } catch (Throwable t) {
            LOGGER.warn("Failed to add an Xaero waypoint for the viewpoint at " + pos, t);
            return;
        }
        saveWorld(manager, world);
    }

    /**
     * 把我们的 0xRRGGBB 颜色换算成 Xaero 的**颜色序号**。
     * <p>
     * 这是必须转的一步，也是个坑：{@code new Waypoint(..., int color)} 的第 6 个 int
     * <b>不是颜色值</b>，而是 {@code WaypointColor.fromIndex(int)} 要的枚举序号（内部 clamp 到 0..20）。
     * 直接把 0xRRGGBB 传进去（比如 0xFF5555 = 16734549）会被 clamp 成 20 → 所有路径点恒为棕色。
     * <p>
     * <b>为什么按颜色值查、而不是用调色板下标当序号</b>：本 mod 的颜色表
     * （{@link ViewpointWaypointColors}）顺序是自由的，加色/换序不该影响 Xaero 侧的映射，
     * 所以这里以"颜色值"为准，用 Xaero 自己的 {@code WaypointColor.getHex()} 逐个比对。
     * 这样即使将来 Xaero 调整枚举顺序也不会错位。
     * <p>
     * 查不到时（只可能来自手改 NBT 的存档，正常路径不会发生）退而求其次取<b>最接近</b>的颜色，
     * 而不是一律回落金色 —— 否则玩家会看到"设置的颜色莫名其妙变成金色"。
     */
    private static int toXaeroColorIndex(int rgb) {
        int target = rgb & 0xFFFFFF;
        WaypointColor nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (WaypointColor color : WaypointColor.values()) {
            int candidate = color.getHex() & 0xFFFFFF;
            if (candidate == target) {
                return color.ordinal();          // 精确命中
            }
            int distance = rgbDistance(candidate, target);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = color;
            }
        }
        return (nearest != null) ? nearest.ordinal() : WaypointColor.GOLD.ordinal();
    }

    /**
     * 两个 0xRRGGBB 颜色的感知差异（加权欧氏距离）。
     * <p>
     * 人眼对绿色最敏感、蓝色最不敏感，所以用 2/4/3 这组常见权重，
     * 直接按 RGB 等权算的话深蓝和深绿可能被判定成"更接近对方"。
     */
    private static int rgbDistance(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return 2 * dr * dr + 4 * dg * dg + 3 * db * db;
    }

    /**
     * 让 Xaero 把这个世界（连同刚加的路径点）写到磁盘。
     * <p>
     * <b>不要</b>改用 {@code getWaypointSession().setSetChangedTime(...)}：那个字段的唯一消费者是
     * {@code WaypointMapRenderer} 的 1.5 秒淡入动画计时器，跟落盘毫无关系。
     * 而 {@code WaypointSet.add()} 本身也只是一句 {@code list.add()}，不标脏、不触发保存 ——
     * 少了下面这一步，路径点只活在内存里，退出游戏就没了。
     * Xaero 自己的界面（GuiAddWaypoint / GuiWaypoints / SwitchWaypointSetFunction）改完路径点后
     * 调的就是 {@code getWorldManagerIO().saveWorld(world)}。
     */
    private static void saveWorld(xaero.common.minimap.waypoints.WaypointsManager manager,
                                  xaero.common.minimap.waypoints.WaypointWorld world) {
        try {
            manager.getWorldManagerIO().saveWorld(world);
        } catch (Throwable t) {
            // 路径点已经在内存里、本次游戏内可见，只是没写盘，单独记一条免得被误读成"整条链失败"
            LOGGER.warn("Added the Xaero waypoint but failed to write the waypoint file", t);
        }
    }
}
