package com.ytzzhu.teamytzviewpoint.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;

/**
 * TeamytzViewpoint 的配置文件：{@code config/teamytzviewpoint.cfg}。
 * <p>
 * 写法沿用 TGCEAddon 的 {@code ConfigHandler}：一个静态类集中读取，
 * 读到的值放在 {@code public static} 字段里，别处直接读字段，不各自去碰 Configuration。
 * <p>
 * 目前只有一组开关：
 * <ul>
 *   <li>{@code renderViewpointIcon} —— 是否在鸟瞰点方块上方渲染那个悬浮图标
 *       （白色 = 未解锁，绿色 = 已解锁）。</li>
 * </ul>
 * 注意这个开关<b>只影响客户端渲染</b>：服务端不需要它，关掉也不会影响解锁/同步这些逻辑。
 */
public class ConfigHandler {

    /** 分类名（会显示在 config 文件的 [鸟瞰点图标] 段下）。 */
    private static final String CATEGORY_ICON = "鸟瞰点图标";

    /** 配置文件名的固定部分；Forge 会在 config 目录下生成 teamytzviewpoint.cfg。 */
    private static final String FILE_NAME = "teamytzviewpoint.cfg";

    private static Configuration config;

    /**
     * 是否在鸟瞰点方块上方渲染图标。
     * <p>
     * 默认 {@code true}（保持与引入该功能时一致的观感）。关掉之后世界里的鸟瞰点
     * 只显示方块本体，不再有悬浮图标；方块上方的交互、解锁、路径点等一切照旧。
     */
    public static boolean renderViewpointIcon = true;

    /** 在 {@code FMLPreInitializationEvent} 里调用一次。 */
    public static void init(FMLPreInitializationEvent event) {
        File file = new File(event.getModConfigurationDirectory(), FILE_NAME);
        config = new Configuration(file);
        config.load();

        renderViewpointIcon = config.getBoolean("renderViewpointIcon", CATEGORY_ICON,
                true,
                "是否在鸟瞰点方块上方渲染图标（白色=未解锁，绿色=已解锁）。\n"
                        + "只影响客户端渲染，关掉不影响解锁、同步与路径点等逻辑。");

        config.save();
    }

    /** 配置文件对象（供以后需要热重载/写回时使用；目前只在 init 里用）。 */
    public static Configuration getConfig() {
        return config;
    }
}
