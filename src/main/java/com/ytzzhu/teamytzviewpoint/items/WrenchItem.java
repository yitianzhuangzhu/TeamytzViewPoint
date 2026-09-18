package com.ytzzhu.teamytzviewpoint.items;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

public class WrenchItem extends Item {
    public WrenchItem() {
        this.setUnlocalizedName("viewpoint_wrench");
        this.setRegistryName("teamytzviewpoint", "viewpoint_wrench");
        this.setCreativeTab(CreativeTabs.TOOLS);
        this.setMaxStackSize(1);
    }
}
