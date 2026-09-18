package com.ytzzhu.teamytzviewpoint.blocks;

import com.ytzzhu.teamytzviewpoint.TeamytzViewpoint;
import com.ytzzhu.teamytzviewpoint.data.ViewpointUnlockData;
import com.ytzzhu.teamytzviewpoint.items.WrenchItem;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

public class ViewpointBlock extends Block {
    public ViewpointBlock() {
        super(Material.ROCK);
        this.setUnlocalizedName("viewpoint_block");
        this.setRegistryName("teamytzviewpoint", "viewpoint_block");
        this.setCreativeTab(CreativeTabs.MISC);
        this.setHardness(2.0F);
        this.setResistance(10.0F);
    }
    
    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }
    
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new ViewpointTileEntity();
    }
    
    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!worldIn.isRemote) {
            boolean holdingWrench = playerIn.getHeldItemMainhand().getItem() instanceof WrenchItem
                    || playerIn.getHeldItemOffhand().getItem() instanceof WrenchItem;
            ViewpointTileEntity tileEntity = (ViewpointTileEntity) worldIn.getTileEntity(pos);
            if (tileEntity != null) {
                if (holdingWrench) {
                    playerIn.openGui(TeamytzViewpoint.instance, 0, worldIn, pos.getX(), pos.getY(), pos.getZ());
                    return true;
                }
                if (!tileEntity.isUnlocked()) {
                    String name = tileEntity.getName();
                    tileEntity.setUnlocked(true);
                    ViewpointUnlockData.get(worldIn).unlockViewpoint(playerIn.getUniqueID(), name);
                    playerIn.sendMessage(new TextComponentTranslation("viewpoint.message.unlocked", name));
                }
            }
        }
        return true;
    }
}
