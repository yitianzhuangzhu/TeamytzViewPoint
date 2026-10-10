package com.ytzzhu.teamytzviewpoint.client.render;

import com.ytzzhu.teamytzviewpoint.blocks.ViewpointMaterial;
import com.ytzzhu.teamytzviewpoint.blocks.ViewpointTileEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

/**
 * 鸟瞰点方块在世界里的外观渲染器（TESR）：用目标方块的模型/贴图绘制。
 * 纯客户端类：只允许被 TeamytzViewpoint.init() 的 CLIENT 分支 / @SideOnly(Side.CLIENT) 代码引用。
 * <p>
 * 实现要点（PLAN §4.1/§4.2）：{@code BlockRendererDispatcher.renderBlock} 会把 {@code pos} 的<b>绝对世界坐标</b>
 * 加进每个顶点，所以 GL 矩阵平移到相机相对坐标（x/y/z）的同时，必须用
 * {@code buffer.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ())} 把顶点拉回 0 附近，
 * 画完再把平移复位，否则会污染后续共用同一个 Tessellator 的渲染。
 */
@SideOnly(Side.CLIENT)
public class ViewpointBlockRenderer extends TileEntitySpecialRenderer<ViewpointTileEntity> {

    /** 本类专用的日志器（主类的 logger 是 private，不去动它）。 */
    private static final Logger LOGGER = LogManager.getLogger(ViewpointBlockRenderer.class);

    /**
     * material 字符串 → 解析结果缓存，避免每帧调 {@code Block.getBlockFromName}。
     * 解析失败时缓存 null 语义，所以读取时必须用 {@code containsKey} 区分"没缓存"和"缓存了 null"。
     * 只在客户端渲染线程访问，无需同步。
     */
    private static final Map<String, IBlockState> STATE_CACHE = new HashMap<String, IBlockState>();

    @Override
    public void render(ViewpointTileEntity te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        // 1) 解析外观：material 为空/非法 → 回落到本方块的默认状态（石砖）
        IBlockState state = getRenderState(te.getMaterial());

        World world = te.getWorld();
        if (world == null) {
            // 方块还不在世界里（或 TE 已被摘除）：没有邻居信息可算 AO/光照，直接不画
            return;
        }
        BlockPos pos = te.getPos();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        GlStateManager.pushMatrix();                       // 保存 GL 状态
        GlStateManager.disableLighting();                  // BLOCK 顶点格式自带光照，关掉 GL 固定光照防干扰
        GlStateManager.translate(x, y, z);                 // x/y/z 已经是相机相对坐标

        try {
            // bindTexture 也在 try 内：它内部会走 TextureManager.loadTexture，理论上可能抛异常；
            // 放在 try 外会让 pushMatrix/disableLighting 得不到复原（矩阵栈与光照状态泄漏）。
            this.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE); // TESR 父类方法（= blocks 图集）

            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK); // GL11 = org.lwjgl.opengl.GL11
            buffer.setTranslation(-pos.getX(), -pos.getY(), -pos.getZ()); // 抵消 renderBlock 写入的绝对坐标

            // 用真实世界做 IBlockAccess → AO/光照/连接状态按世界邻居计算
            Minecraft.getMinecraft().getBlockRendererDispatcher().renderBlock(state, pos, world, buffer);

            buffer.setTranslation(0.0D, 0.0D, 0.0D);       // 必须复位
            // begin() 已经调用过，所以即使没有产生任何顶点，draw() 里的 finishDrawing() 也不会抛
            // "Not building!"（它只要求 isDrawing == true）；上传器对 0 顶点会自行跳过。
            tessellator.draw();
        } catch (Throwable t) {
            // 任何 mod 的怪模型都不允许崩客户端。这里把该材质锁存为"已知渲染失败"：
            // 缓存改写成默认石砖状态，之后这个材质固定用石砖渲染 —— 既不会每帧刷日志，
            // 也不会让方块一直隐形（即 PLAN §4.2 说的"记录一次，之后按默认外观渲染"）。
            STATE_CACHE.put(ViewpointMaterial.normalize(te.getMaterial()),
                    ViewpointMaterial.getRenderStateOrDefault(ViewpointMaterial.DEFAULT_MATERIAL));
            LOGGER.error("Failed to render viewpoint material '" + te.getMaterial() + "' at " + pos
                    + "; falling back to the default stone brick look for this material", t);
            // 把共享 buffer 的状态拉回来：若异常发生在 begin() 之后，这里会复位 isDrawing；
            // 若发生在 begin() 之前（例如 bindTexture 先失败），draw() 会抛 "Not building!"，由下面吞掉。
            try {
                tessellator.draw();
            } catch (Throwable ignored) {
                // 复位失败也不能再抛，否则会盖掉上面记录的真实异常
            }
        } finally {
            buffer.setTranslation(0.0D, 0.0D, 0.0D);        // 平移绝不能泄漏给后续渲染
            GlStateManager.enableLighting();               // 恢复 GL 状态
            GlStateManager.popMatrix();
        }
    }

    /**
     * 取材质对应的渲染状态：空串或非法一律回落到默认外观（石砖）。
     * 语义等价于 {@code ViewpointMaterial.getRenderStateOrDefault(te.getMaterial())}，
     * 只是多了一层按规范化名字的解析缓存（TESR 每帧都会走这里）。
     * <p>
     * 返回值一定满足 {@code getRenderType() == MODEL}：{@code resolve(...)} 只接受 MODEL 状态，
     * 回落值（石砖）也是 MODEL —— 否则 {@code renderBlock} 会直接 {@code return false} 什么都不画。
     */
    private static IBlockState getRenderState(String material) {
        String key = ViewpointMaterial.normalize(material);
        IBlockState cached;
        if (STATE_CACHE.containsKey(key)) {
            cached = STATE_CACHE.get(key);
        } else {
            cached = ViewpointMaterial.resolve(key);
            STATE_CACHE.put(key, cached);   // 解析失败也缓存（null 语义），避免每帧重查注册表
        }
        return (cached != null) ? cached : ViewpointMaterial.getRenderStateOrDefault(ViewpointMaterial.DEFAULT_MATERIAL);
    }
}
