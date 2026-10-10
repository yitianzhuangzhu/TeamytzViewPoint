package com.ytzzhu.teamytzviewpoint;

/**
 * 鸟瞰点路径点可选的颜色。
 * <p>
 * 取值全部来自 <b>Xaero 小地图 {@code WaypointColor} 枚举</b>（原版聊天 16 色 + 5 个扩展色，
 * 用 javap 反汇编 {@code getHex()} 核对过），所以界面上列出的每个颜色 Xaero 都能原样显示。
 * <p>
 * 本类只负责"颜色值 ↔ 调色板索引"；<b>"索引 ↔ Xaero ordinal"的换算在
 * {@code XaeroMinimapIntegration}</b>（那边硬引用 Xaero 类，双端类不能碰）。
 * 因此本数组的顺序是自由的，加色/换序不会影响 Xaero 侧的映射。
 * <p>
 * 双端安全：只有 int / String，GUI（客户端）与 TE（双端）都用它。
 * 存储时存的是<b>颜色整数</b>而不是索引 —— 以后想调整/扩充调色板也不会把老存档的颜色弄错。
 */
public final class ViewpointWaypointColors {

    // ---- Xaero WaypointColor 用到的全部颜色值，逐个与 getHex() 核对过（原版聊天色） ----
    // 注意 LIME 与 GREEN 同值、MAGENTA 省略（与 PURPLE 同值），见 PRESETS 的说明。
    public static final int BLACK = 0x000000;
    public static final int DARK_BLUE = 0x0000AA;
    public static final int DARK_GREEN = 0x00AA00;
    public static final int DARK_AQUA = 0x00AAAA;
    public static final int DARK_RED = 0xAA0000;
    public static final int DARK_PURPLE = 0xAA00AA;
    public static final int GOLD = 0xFFAA00;
    public static final int GRAY = 0xAAAAAA;
    public static final int DARK_GRAY = 0x555555;
    public static final int BLUE = 0x5555FF;
    public static final int GREEN = 0x55FF55;
    public static final int AQUA = 0x55FFFF;
    public static final int RED = 0xFF5555;
    public static final int PURPLE = 0xFF55FF;
    public static final int YELLOW = 0xFFFF55;
    public static final int WHITE = 0xFFFFFF;
    public static final int LIGHT_BLUE = 0x55AAFF;
    public static final int LIME = 0x55FF55;
    public static final int PINK = 0xFF55AA;
    public static final int BROWN = 0xAA5500;

    /**
     * 界面里可选的颜色，ARGB 里的 RGB 部分（Xaero 的 waypoint color 就是 0xRRGGBB）。
     * <p>
     * 每行末尾的注释是<b>该颜色在 Xaero {@code WaypointColor} 里的 ordinal</b>，只作参考 ——
     * 真正的映射由 {@code XaeroMinimapIntegration} 在运行期按颜色值自己查，本数组换序不会查错。
     * <p>
     * Xaero 的枚举有 21 项，但 MAGENTA(16) 与 PURPLE(13) 同值 0xFF55FF、LIME(18) 与 GREEN(10)
     * 同值 0x55FF55 —— 真正互不相同的只有 19 色，同值的那两个在界面/小地图上都分辨不出来，
     * 所以本表只收录 19 色。{@code LIME} 常量保留备用，但同值不会被 {@link #indexOf(int)} 命中。
     * <p>
     * <b>金色必须留在下标 0</b>：{@link #DEFAULT_INDEX} = 0，而服务端对非法颜色的回落值也是它。
     */
    public static final int[] PRESETS = {
            GOLD,           // 6  ← 默认色，见上方说明
            BLACK,          // 0
            DARK_BLUE,      // 1
            DARK_GREEN,     // 2
            DARK_AQUA,      // 3
            DARK_RED,       // 4
            DARK_PURPLE,    // 5
            GRAY,           // 7
            DARK_GRAY,      // 8
            BLUE,           // 9
            GREEN,          // 10
            AQUA,           // 11
            RED,            // 12
            PURPLE,         // 13
            YELLOW,         // 14
            WHITE,          // 15
            LIGHT_BLUE,     // 17
            PINK,           // 19
            BROWN           // 20
    };

    /** 与 {@link #PRESETS} 一一对应的 lang key 后缀（viewpoint.color.&lt;后缀&gt;）。 */
    public static final String[] NAME_KEYS = {
            "gold",
            "black",
            "dark_blue",
            "dark_green",
            "dark_aqua",
            "dark_red",
            "dark_purple",
            "gray",
            "dark_gray",
            "blue",
            "green",
            "aqua",
            "red",
            "purple",
            "yellow",
            "white",
            "light_blue",
            "pink",
            "brown"
    };

    /** 默认颜色索引：{@link #PRESETS} 里的金色，醒目且不刺眼。 */
    public static final int DEFAULT_INDEX = 0;

    private ViewpointWaypointColors() {
    }

    public static int colorAt(int index) {
        return PRESETS[clampIndex(index)];
    }

    public static int clampIndex(int index) {
        if (index < 0) {
            return 0;
        }
        return index >= PRESETS.length ? PRESETS.length - 1 : index;
    }

    /**
     * 从颜色整数反查调色板索引（{@link #PRESETS} 的下标，不是 Xaero 的 ordinal）；
     * 不是调色板里的颜色时返回 -1。
     * 存档里存的是颜色本身，所以老数据/手改过的值都可能不在调色板里。
     */
    public static int indexOf(int color) {
        int rgb = color & 0xFFFFFF;
        for (int i = 0; i < PRESETS.length; i++) {
            if ((PRESETS[i] & 0xFFFFFF) == rgb) {
                return i;
            }
        }
        return -1;
    }

    /** 是否是我们认得的颜色。 */
    public static boolean isPreset(int color) {
        return indexOf(color) >= 0;
    }

    /** 默认颜色（新建的鸟瞰点用）。 */
    public static int defaultColor() {
        return colorAt(DEFAULT_INDEX);
    }
}
