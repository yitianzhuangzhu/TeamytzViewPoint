package com.ytzzhu.teamytzviewpoint.blocks;

import com.ytzzhu.teamytzviewpoint.ModCreativeTabs;
import com.ytzzhu.teamytzviewpoint.TeamytzViewpoint;
import com.ytzzhu.teamytzviewpoint.items.WrenchItem;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ViewpointBlock extends Block {
    public ViewpointBlock() {
        super(Material.ROCK);
        this.setUnlocalizedName("viewpoint_block");
        this.setRegistryName("teamytzviewpoint", "viewpoint_block");
        this.setCreativeTab(ModCreativeTabs.VIEWPOINT);
        this.setHardness(2.0F);
        this.setResistance(10.0F);
        // 必须**不挡光**（lightOpacity = 0）。
        // 这条是实机反馈改回来的：2.1.0 时为了"让行为变化只限于面剔除"，这里曾写成 setLightOpacity(255)，
        // 结果造出一个不自洽的组合 ——
        //   * 面剔除认为本方块是**透**的（isOpaqueCube=false），所以下方地面那一块的顶面会被渲染出来；
        //   * 光照却认为它是**实心不透光**的（lightOpacity=255），本方块所在那一格的天空光被压成 0；
        //   * 而地面顶面的平滑光照 / 环境光遮蔽正是采样"它上方那几格"的光照值 →
        //     那个面被算成"整个在阴影里"，透过玻璃看下去就是一块暗斑。
        // 原版实心方块不会暴露这个问题，因为它会把自己的邻居面整面剔除掉，你根本看不到那个暗面；
        // 本方块为了支持透明外观而保留了那些面，于是暗面就露出来了。
        // 设为 0 之后本方块所在格有正常天空光，地面顶面按正常亮度渲染。
        // 代价是它不再投下阴影 —— 对本方块是正确取舍：它的外观可能是玻璃/树叶，
        // 一块"会挡光的玻璃"本身就不合理。
        // 注意这个值本来就会在父类构造期由 isOpaqueCube() 派生出来（fullBlock=false → lightOpacity=0，
        // 见字节码 183/188/193/206），显式写出来是为了防止以后有人调整覆写顺序又把它带回 255。
        this.setLightOpacity(0);
    }
    
    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }
    
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new ViewpointTileEntity();
    }
    
    @SuppressWarnings("deprecation")   // 覆写点本身是 @Deprecated 的，但它是 1.12.2 唯一的覆写入口
    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        // 世界中的外观完全交给 ViewpointBlockRenderer (TESR) 绘制
        return EnumBlockRenderType.INVISIBLE;
    }
    
    /**
     * 必须返回 false —— 这是"外观选玻璃/树叶时，方块底部透视出矿洞和蓝色"的修复点。
     * <p>
     * 1.12.2 的面剔除链路（已用 javap 核对字节码，非推测）：
     * <ul>
     *   <li>{@code Block.doesSideBlockRendering(...)} 的实现就是 {@code return state.isOpaqueCube();}</li>
     *   <li>{@code Block.shouldSideBeRendered(...)} 在自身是满方块时返回 {@code !邻居.doesSideBlockRendering(...)}</li>
     * </ul>
     * 本方块默认 {@code isOpaqueCube() == true}（Block 的默认实现就是 {@code return true;}），
     * 于是"下方地面那一块"的顶面会被判定成"被本方块挡住"而**整面剔除**。
     * 外观是不透明方块时看不出来；但外观一旦是玻璃/树叶这类透明方块，视线就会
     * 穿过本方块的透明部分 → 再穿过被剔除的地面顶面（背面不渲染）→ 直接看到地面内部、
     * 矿洞乃至天空背景（也就是那层蓝色）。
     * <p>
     * 原版透明方块的做法一致：玻璃（{@code BlockBreakable}）与树叶（{@code BlockLeavesBase}）
     * 都覆写成 false。本方块的外观可以是任意方块，所以它必须声明"我可能是透的"。
     * <p>
     * <b>连带副作用与处置</b>：{@code Block} 的构造函数会从默认状态的 {@code isOpaqueCube()} 派生两个字段
     * （字节码 183/188/193/206：{@code fullBlock = isOpaqueCube()}、{@code lightOpacity = fullBlock ? 255 : 0}），
     * 所以本覆写会让 {@code fullBlock} 变成 false、{@code lightOpacity} 变成 0。两者的处置不同：
     * <ul>
     *   <li>{@code lightOpacity = 0}（不挡光）是<b>要保留</b>的。见构造函数里的说明：
     *       一旦挡光，被渲染出来的下方地面顶面就会变成暗斑 —— 这正是实机反馈过的问题。</li>
     *   <li>{@code fullBlock = false} 影响的是玩法侧判定（生物寻路能否站上来、爆炸后能否生火、
     *       蘑菇能否附着、相邻流体流向），与渲染无关，已在下面覆写 {@code isFullBlock} 恢复为 true。</li>
     * </ul>
     * <p>
     * {@code isFullCube} 特意保持不动：它不参与这条面剔除链路（已核对字节码），
     * 改了反而会波及 {@code isTopSolid} / {@code isNormalCube} / 红石判定 / 刷怪等一大片。
     */
    @SuppressWarnings("deprecation")   // 同上：覆写点本身是 @Deprecated 的
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }
    
    /**
     * isOpaqueCube() 改成 false 会让父类构造期派生的 {@code fullBlock} 字段变成 false
     * （见构造函数里的说明）。{@code fullBlock} 参与的全是玩法侧判定，与渲染无关：
     * 生物寻路能否站在上面、爆炸后能否生成火焰、蘑菇能否附着、相邻流体流向。
     * <p>
     * 本方块在结构上**仍然是一个完整的实心立方体**，外观透不透只影响渲染，
     * 所以这里显式恢复成 true，避免这次渲染修复波及玩法。
     */
    @SuppressWarnings("deprecation")   // 覆写点本身是 @Deprecated 的
    @Override
    public boolean isFullBlock(IBlockState state) {
        return true;
    }
    
    /**
     * 右键只保留"拿扳手 → 打开设置界面"。
     * <p>
     * 鸟瞰的<b>触发与解锁都已改成"站在方块上按触发键"</b>（见 {@code ViewpointTrigger} /
     * {@code ViewpointTriggerPacket}），所以空手右键不再触发任何东西。
     * 这里仍然返回 true：让右键在本方块上被"吃掉"，避免变成往鸟瞰点上贴方块之类的误操作。
     */
    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!worldIn.isRemote) {
            boolean holdingWrench = playerIn.getHeldItemMainhand().getItem() instanceof WrenchItem
                    || playerIn.getHeldItemOffhand().getItem() instanceof WrenchItem;
            if (holdingWrench && worldIn.getTileEntity(pos) instanceof ViewpointTileEntity) {
                playerIn.openGui(TeamytzViewpoint.instance, 0, worldIn, pos.getX(), pos.getY(), pos.getZ());
            }
        }
        return true;
    }
}
