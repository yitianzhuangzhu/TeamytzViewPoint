package com.ytzzhu.teamytzviewpoint.blocks;

import com.ytzzhu.teamytzviewpoint.ModCreativeTabs;
import com.ytzzhu.teamytzviewpoint.TeamytzViewpoint;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = TeamytzViewpoint.MODID)
public class ModBlocks {
    public static final Block VIEWPOINT_BLOCK = new ViewpointBlock();
    
    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(VIEWPOINT_BLOCK);
    }
    
    @SubscribeEvent
    public static void registerItemBlocks(RegistryEvent.Register<net.minecraft.item.Item> event) {
        // 显式把 ItemBlock 也放进本模组的标签页（不依赖 Item(Block) 构造函数是否继承方块的标签页）
        event.getRegistry().register(new ItemBlock(VIEWPOINT_BLOCK)
                .setRegistryName(VIEWPOINT_BLOCK.getRegistryName())
                .setCreativeTab(ModCreativeTabs.VIEWPOINT));
    }
}
