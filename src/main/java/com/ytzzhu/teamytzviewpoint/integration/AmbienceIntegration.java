package com.ytzzhu.teamytzviewpoint.integration;

import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointCameraManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class AmbienceIntegration {
    
    @SubscribeEvent
    public void onAmbienceEvent(vazkii.ambience.AmbienceEventEvent.Pre event) {
        if (ViewpointCameraManager.isOverlooking()) {
            event.event = "viewpoint";
        }
    }
    
    @SubscribeEvent
    public void onAmbienceEventPost(vazkii.ambience.AmbienceEventEvent.Post event) {
    }
}
