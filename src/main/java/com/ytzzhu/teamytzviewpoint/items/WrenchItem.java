package com.ytzzhu.teamytzviewpoint.items;

import com.ytzzhu.teamytzviewpoint.ModCreativeTabs;
import net.minecraft.item.Item;

public class WrenchItem extends Item {
    public WrenchItem() {
        this.setUnlocalizedName("viewpoint_wrench");
        this.setRegistryName("teamytzviewpoint", "viewpoint_wrench");
        this.setCreativeTab(ModCreativeTabs.VIEWPOINT);
        this.setMaxStackSize(1);
    }
}
