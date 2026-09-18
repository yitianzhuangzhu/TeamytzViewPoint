package com.ytzzhu.teamytzviewpoint.gui;

import com.ytzzhu.teamytzviewpoint.blocks.ViewpointTileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

public class ViewpointContainer extends Container {
    private ViewpointTileEntity tileEntity;
    
    public ViewpointContainer(InventoryPlayer playerInventory, ViewpointTileEntity tileEntity) {
        this.tileEntity = tileEntity;
    }
    
    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return playerIn.getDistanceSq(tileEntity.getPos().getX() + 0.5, tileEntity.getPos().getY() + 0.5, tileEntity.getPos().getZ() + 0.5) <= 64.0;
    }
    
    @Override
    public ItemStack transferStackInSlot(EntityPlayer playerIn, int index) {
        return ItemStack.EMPTY;
    }
}
