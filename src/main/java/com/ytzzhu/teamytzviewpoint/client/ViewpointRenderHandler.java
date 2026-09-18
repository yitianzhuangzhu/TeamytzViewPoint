package com.ytzzhu.teamytzviewpoint.client;

import com.ytzzhu.teamytzviewpoint.viewpoint.ViewpointCameraManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ViewpointRenderHandler {

    /** True while our own forced render call is executing (re-entry guard). */
    private static boolean forceRenderingPlayer = false;

    /** Set when the normal entity pass rendered the local player this frame. */
    private boolean playerRenderedThisFrame = false;

    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Post<EntityLivingBase> event) {
        if (forceRenderingPlayer) {
            return;
        }
        EntityPlayer localPlayer = Minecraft.getMinecraft().player;
        if (localPlayer != null && event.getEntity() == localPlayer) {
            playerRenderedThisFrame = true;
        }
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (!ViewpointCameraManager.isOverlooking()) {
            playerRenderedThisFrame = false;
            return;
        }
        
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        
        if (player == null || !(player instanceof AbstractClientPlayer)) {
            playerRenderedThisFrame = false;
            return;
        }
        
        RenderManager renderManager = mc.getRenderManager();
        float partialTicks = event.getPartialTicks();
        
        double x = ViewpointCameraManager.startPlayerX;
        double y = ViewpointCameraManager.startPlayerY;
        double z = ViewpointCameraManager.startPlayerZ;
        float yaw = ViewpointCameraManager.startPlayerYaw;
        
        // While freecam is active it switches to third-person view and the normal
        // entity pass already renders the player. Forcing a second render on top
        // of that produces a ghost image, so only force-render when the normal
        // pass skipped the player this frame (the mod-conflict case where the
        // player would otherwise be invisible) or when the player is invisible.
        boolean forceRender = !playerRenderedThisFrame || player.isInvisible();
        playerRenderedThisFrame = false;
        
        if (!forceRender) {
            return;
        }
        
        GlStateManager.pushMatrix();
        GlStateManager.translate(-renderManager.viewerPosX, -renderManager.viewerPosY, -renderManager.viewerPosZ);
        
        net.minecraft.client.renderer.entity.RenderPlayer renderPlayer = (net.minecraft.client.renderer.entity.RenderPlayer) ((net.minecraft.client.renderer.entity.Render) renderManager.getEntityRenderObject(player));
        if (renderPlayer != null) {
            // Freeze the pose captured at animation start around the render call:
            // freecam rewrites the live player position/rotation every frame while
            // orbiting, which would otherwise spin the body and cape around.
            ViewpointCameraManager.PlayerPoseSnapshot livePose = ViewpointCameraManager.PlayerPoseSnapshot.capture(player);
            if (ViewpointCameraManager.frozenPlayerPose != null) {
                ViewpointCameraManager.frozenPlayerPose.apply(player);
            }
            forceRenderingPlayer = true;
            try {
                renderPlayer.doRender((AbstractClientPlayer) player, x, y, z, yaw, partialTicks);
            } finally {
                forceRenderingPlayer = false;
                livePose.apply(player);
            }
        }
        
        GlStateManager.popMatrix();
    }
}