package com.ytzzhu.teamytzviewpoint.client;

import com.ytzzhu.teamytzviewpoint.blocks.ViewpointBlock;
import com.ytzzhu.teamytzviewpoint.items.WrenchItem;
import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointCameraManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ViewpointClientEvents {
    
    @SubscribeEvent
    public void onPlayerInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getWorld().isRemote && event.getWorld().getBlockState(event.getPos()).getBlock() instanceof ViewpointBlock) {
            EntityPlayer player = event.getEntityPlayer();
            
            // While holding the wrench (either hand) the only interaction is the
            // naming GUI; never start the bird's-eye animation in that case.
            if (player.getHeldItemMainhand().getItem() instanceof WrenchItem
                    || player.getHeldItemOffhand().getItem() instanceof WrenchItem) {
                return;
            }
            
            // Do NOT cancel the event: the server-side onBlockActivated performs the
            // unlock, and cancelling here would prevent the interaction from ever
            // reaching the server (the viewpoint could then only be unlocked by a
            // second click during the animation).
            if (!ViewpointCameraManager.isActive()) {
                ViewpointCameraManager.startViewpoint(player);
            }
        }
    }
    
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ViewpointCameraManager.update();
        }
    }
}
