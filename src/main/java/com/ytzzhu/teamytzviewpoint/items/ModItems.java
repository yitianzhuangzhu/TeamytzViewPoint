package com.ytzzhu.teamytzviewpoint.items;

import com.ytzzhu.teamytzviewpoint.TeamytzViewpoint;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = TeamytzViewpoint.MODID)
public class ModItems {
    public static final Item VIEWPOINT_WRENCH = new WrenchItem();
    
    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(VIEWPOINT_WRENCH);
    }
}
