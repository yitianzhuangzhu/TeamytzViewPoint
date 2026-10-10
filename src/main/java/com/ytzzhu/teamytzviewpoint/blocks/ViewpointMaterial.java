package com.ytzzhu.teamytzviewpoint.blocks;

import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumBlockRenderType;

/**
 * 外观材质名的校验/解析工具（双端安全）。
 * 客户端 GUI 的实时校验、服务端 handler 的权威校验、TESR 的渲染解析共用这里的同一套判定，
 * 保证"客户端说合法、服务端也说合法"。空串 = 使用鸟瞰点方块自己的外观（石砖）。
 * <p>
 * 本类绝对不能引用任何 net.minecraft.client.* —— 服务端也会加载并调用它。
 */
public final class ViewpointMaterial {

    /** GUI 输入框允许的最大长度（与服务端截断保持一致）。 */
    public static final int MAX_LENGTH = 64;

    /** 默认外观（空串）使用的方块：空串代表"用鸟瞰点方块自己的默认状态"。 */
    public static final String DEFAULT_MATERIAL = "";

    /** 网络线格式里单个字符串字段的 UTF-8 字节数上限；超过即视为畸形包。 */
    public static final int MAX_WIRE_BYTES = 1024;

    /** 合法性分级，仅供 GUI 选择提示文案；是否接受只看 {@link #resolve(String)} 是否为 null。 */
    public enum Status {
        DEFAULT,
        VALID,
        UNKNOWN_BLOCK,
        NOT_MODEL,
        SELF
    }

    private ViewpointMaterial() {
    }

    /**
     * 规范化：trim → toLowerCase(Locale.ROOT) → 按 {@link #MAX_LENGTH} 截断 → 补默认命名空间。
     * 任何输入都不抛异常；null 视为空串。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return DEFAULT_MATERIAL;
        }
        String value = raw.trim();
        value = value.toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            value = value.substring(0, MAX_LENGTH);
        }
        if (value.isEmpty()) {
            return DEFAULT_MATERIAL;
        }
        if (value.indexOf(':') < 0) {
            // "stonebrick" → "minecraft:stonebrick"，让双端判定必然一致
            value = "minecraft:" + value;
        }
        return value;
    }

    /** 按注册名查方块：任何非法名字都不抛异常，找不到返回 null。 */
    private static Block lookup(String normalized) {
        try {
            return Block.getBlockFromName(normalized);
        } catch (RuntimeException e) {
            // 极端畸形名字（ResourceLocation 解析失败）也一律当作"找不到"
            return null;
        }
    }

    /**
     * 校验并解析。返回 null 表示"非法"（未知方块 / 非 MODEL 渲染类型 / 空气 / 自我引用）。
     * 入参必须是已 normalize 的字符串；空串返回 null（调用方按"默认外观"处理）。
     */
    public static IBlockState resolve(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        Block block = lookup(normalized);
        if (block == null) {
            return null;
        }
        if (block == Blocks.AIR || block instanceof BlockAir) {
            return null;
        }
        if (block == ModBlocks.VIEWPOINT_BLOCK) {
            // 防自我递归：TESR 渲染自己会无限递归
            return null;
        }
        IBlockState state = block.getDefaultState();
        if (state == null) {
            return null;
        }
        // 用 IBlockState.getRenderType()（IBlockProperties 的无参访问器）。
        // 注意：Block.getRenderType(IBlockState) 在 Forge 1.12.2 是 @Deprecated 的，调用它会引入过时 API 提示。
        if (state.getRenderType() != EnumBlockRenderType.MODEL) {
            // 排除 LIQUID（水/岩浆）、ENTITYBLOCK_ANIMATED（箱子/潜影盒）、INVISIBLE（空气/屏障）
            return null;
        }
        return state;
    }

    /**
     * 渲染用：空串或非法一律回落到默认外观（石砖）。
     * <p>
     * <b>不要</b>回落到 {@code ModBlocks.VIEWPOINT_BLOCK.getDefaultState()}：本方块自己的
     * {@code getRenderType()} 已经是 {@code INVISIBLE}，而 {@code BlockRendererDispatcher.renderBlock}
     * 开头就是"state 渲染类型 == INVISIBLE ⇒ 直接 return false"，一个顶点都不会写
     * （已用 javap 核实 1.12.2-14.23.5.2860 的字节码），那样"未设置材质"的方块在世界里会完全不可见。
     * <p>
     * 这里用 {@code minecraft:stonebrick}：它与 {@code models/block/viewpoint_block.json}
     * （{@code parent: block/cube_all} + {@code textures.all = minecraft:blocks/stonebrick}）
     * 是同一个 cube_all 模型 + 同一张贴图，外观完全一致（PLAN §4.3 / S1）。
     */
    public static IBlockState getRenderStateOrDefault(String material) {
        IBlockState state = resolve(normalize(material));
        return (state != null) ? state : Blocks.STONEBRICK.getDefaultState();
    }

    /** 合法性分级，供 GUI 选提示文案；判定是否接受只看 {@link #resolve(String)}。 */
    public static Status status(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return Status.DEFAULT;
        }
        Block block = lookup(normalized);
        if (block == null) {
            return Status.UNKNOWN_BLOCK;
        }
        if (block == ModBlocks.VIEWPOINT_BLOCK) {
            return Status.SELF;
        }
        if (block == Blocks.AIR || block instanceof BlockAir) {
            return Status.NOT_MODEL;
        }
        IBlockState state = block.getDefaultState();
        if (state == null || state.getRenderType() != EnumBlockRenderType.MODEL) {
            return Status.NOT_MODEL;
        }
        return Status.VALID;
    }
}
