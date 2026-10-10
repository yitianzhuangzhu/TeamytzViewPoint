package com.ytzzhu.teamytzviewpoint.blocks;

import com.ytzzhu.teamytzviewpoint.ViewpointWaypointColors;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

public class ViewpointTileEntity extends TileEntity {
    /** 名字最大长度（与 GUI 输入框的限制一致）。 */
    public static final int MAX_NAME_LENGTH = 32;
    /** 名字留空时使用的默认名。 */
    public static final String DEFAULT_NAME = "鸟瞰点";

    private String name = DEFAULT_NAME;
    private boolean unlocked = false;
    /** 目标方块注册名（如 "minecraft:stonebrick"）；空串 = 使用鸟瞰点方块自己的外观（石砖）。 */
    private String material = "";
    /** 触发鸟瞰点时在小地图上创建的同名路径点用什么颜色（0xRRGGBB）。 */
    private int waypointColor = ViewpointWaypointColors.defaultColor();
    
    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("ViewpointName")) {
            this.name = stripBuggyNamespace(compound.getString("ViewpointName"));
        }
        if (compound.hasKey("ViewpointUnlocked")) {
            this.unlocked = compound.getBoolean("ViewpointUnlocked");
        }
        // 老存档没有该 key 时显式回落空串，避免复用同一实例时残留旧值
        this.material = compound.hasKey("ViewpointMaterial") ? compound.getString("ViewpointMaterial") : "";
        this.waypointColor = compound.hasKey("ViewpointWaypointColor")
                ? compound.getInteger("ViewpointWaypointColor") : ViewpointWaypointColors.defaultColor();
    }
    
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setString("ViewpointName", this.name);
        compound.setBoolean("ViewpointUnlocked", this.unlocked);
        compound.setString("ViewpointMaterial", this.material == null ? "" : this.material);
        compound.setInteger("ViewpointWaypointColor", this.waypointColor);
        return compound;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
        this.markDirty();
    }
    
    /**
     * 兼容还原：2.3.1 之前服务端处理名字时误用了 {@code ViewpointMaterial.normalize()}，
     * 那会把不含冒号的名字补成 {@code "minecraft:xxx"}（顺带 toLowerCase）。
     * 这里在读盘时把这个前缀去掉，让老存档里已经被污染的鸟瞰点名字恢复正常。
     * <p>
     * 本 mod 是 {@code ViewpointName} 这个 NBT key 的唯一写入方，所以"以 minecraft: 开头"
     * 几乎只可能来自那个 bug。注意被 toLowerCase 掉的原始大小写无法还原。
     */
    private static String stripBuggyNamespace(String name) {
        if (name != null && name.startsWith("minecraft:")) {
            return name.substring("minecraft:".length());
        }
        return name;
    }
    
    public boolean isUnlocked() {
        return unlocked;
    }
    
    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
        this.markDirty();
    }
    
    public String getMaterial() {
        return material;
    }
    
    public void setMaterial(String material) {
        String next = (material == null) ? "" : material;
        if (!next.equals(this.material)) {   // 避免无意义的 markDirty/网络风暴
            this.material = next;
            this.markDirty();
        }
    }
    
    public int getWaypointColor() {
        return waypointColor;
    }
    
    public void setWaypointColor(int waypointColor) {
        if (waypointColor != this.waypointColor) {
            this.waypointColor = waypointColor;
            this.markDirty();
        }
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
