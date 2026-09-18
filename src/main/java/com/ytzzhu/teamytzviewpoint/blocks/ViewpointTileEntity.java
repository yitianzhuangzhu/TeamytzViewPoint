package com.ytzzhu.teamytzviewpoint.blocks;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class ViewpointTileEntity extends TileEntity {
    private String name = "鸟瞰点";
    private boolean unlocked = false;
    
    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("ViewpointName")) {
            this.name = compound.getString("ViewpointName");
        }
        if (compound.hasKey("ViewpointUnlocked")) {
            this.unlocked = compound.getBoolean("ViewpointUnlocked");
        }
    }
    
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setString("ViewpointName", this.name);
        compound.setBoolean("ViewpointUnlocked", this.unlocked);
        return compound;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
        this.markDirty();
    }
    
    public boolean isUnlocked() {
        return unlocked;
    }
    
    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
        this.markDirty();
    }
    
    @Override
    public NBTTagCompound getUpdateTag() {
        return this.writeToNBT(new NBTTagCompound());
    }
    
    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        this.readFromNBT(tag);
    }
}
