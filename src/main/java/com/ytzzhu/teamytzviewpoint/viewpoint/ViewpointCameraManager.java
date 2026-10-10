package com.ytzzhu.teamytzviewpoint.viewpoint;

import com.zergatul.freecam.FreeCam;
import net.minecraft.entity.player.EntityPlayer;

import java.lang.reflect.Field;

public class ViewpointCameraManager {
    public static ViewpointState currentState = ViewpointState.INACTIVE;
    public static int animationTick = 0;
    
    public static double startPlayerX, startPlayerY, startPlayerZ;
    public static float startPlayerYaw;
    
    /**
     * Pose captured when the animation starts. The freecam mod teleports the player
     * entity to the orbiting camera position and rewrites its rotation every frame,
     * which makes the live entity state (renderYawOffset, chasingPos, cameraYaw,
     * limbSwing, ...) spin during the animation. The forced player render freezes
     * this snapshot so the player model stands still at the viewpoint with the cape
     * hanging normally instead of rotating.
     */
    public static PlayerPoseSnapshot frozenPlayerPose;
    
    public static final int PULLBACK_DURATION = 60;
    public static final int ORBIT_DURATION = 360;
    public static final int RETURN_DURATION = 60;
    
    public static final double ORBIT_RADIUS = 25.0;
    public static final double ORBIT_HEIGHT = 20.0;
    
    private static Field freecamXField;
    private static Field freecamYField;
    private static Field freecamZField;
    private static Field freecamXRotField;
    private static Field freecamYRotField;
    
    static {
        try {
            freecamXField = FreeCam.class.getDeclaredField("x");
            freecamXField.setAccessible(true);
            freecamYField = FreeCam.class.getDeclaredField("y");
            freecamYField.setAccessible(true);
            freecamZField = FreeCam.class.getDeclaredField("z");
            freecamZField.setAccessible(true);
            freecamXRotField = FreeCam.class.getDeclaredField("xRot");
            freecamXRotField.setAccessible(true);
            freecamYRotField = FreeCam.class.getDeclaredField("yRot");
            freecamYRotField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }
    
    public static void startViewpoint(EntityPlayer player) {
        if (!FreeCam.instance.isActive()) {
            FreeCam.instance.toggle();
        }
        
        currentState = ViewpointState.PULLBACK;
        animationTick = 0;
        
        startPlayerX = player.posX;
        startPlayerY = player.posY;
        startPlayerZ = player.posZ;
        startPlayerYaw = player.rotationYaw;
        
        frozenPlayerPose = PlayerPoseSnapshot.capture(player);
        
        setFreeCamPosition(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        setFreeCamRotation(player.rotationPitch, player.rotationYaw);
    }
    
    public static void update() {
        if (!FreeCam.instance.isActive()) {
            // freecam 被外部关掉了（玩家自己按了切换键、退出世界、切维度等）：动画实际上已经结束。
            // 必须把状态收回 INACTIVE，否则 isActive() 会一直为 true —— 那会让鸟瞰期间隐藏的 HUD
            // 永远不恢复，也会让 ViewpointRenderHandler 一直强制重画玩家。
            if (currentState != ViewpointState.INACTIVE) {
                reset();
            }
            return;
        }
        
        if (currentState == ViewpointState.PULLBACK) {
            animationTick++;
            updatePullback();
        } else if (currentState == ViewpointState.ORBITING) {
            animationTick++;
            updateOrbiting();
        } else if (currentState == ViewpointState.RETURNING) {
            animationTick++;
            updateReturning();
        } else if (currentState == ViewpointState.INACTIVE) {
        }
    }
    
    private static void updatePullback() {
        float progress = Math.min(1.0f, (float) animationTick / PULLBACK_DURATION);
        progress = easeInOutSine(progress);
        
        double baseX = startPlayerX;
        double baseY = startPlayerY + 1.62;
        double baseZ = startPlayerZ;
        
        double offsetX = -Math.sin(Math.toRadians(0)) * ORBIT_RADIUS * progress;
        double offsetY = ORBIT_HEIGHT * progress;
        double offsetZ = Math.cos(Math.toRadians(0)) * ORBIT_RADIUS * progress;
        
        double camX = baseX + offsetX;
        double camY = baseY + offsetY;
        double camZ = baseZ + offsetZ;
        
        setFreeCamPosition(camX, camY, camZ);
        updateFreeCamLookAt(camX, camY, camZ);
        
        if (animationTick >= PULLBACK_DURATION) {
            currentState = ViewpointState.ORBITING;
            animationTick = 0;
        }
    }
    
    private static void updateOrbiting() {
        float progress = (float) animationTick / ORBIT_DURATION;
        double angle = progress * Math.PI * 2;
        
        double camX = startPlayerX + Math.sin(angle) * ORBIT_RADIUS;
        double camY = startPlayerY + 1.62 + ORBIT_HEIGHT;
        double camZ = startPlayerZ + Math.cos(angle) * ORBIT_RADIUS;
        
        setFreeCamPosition(camX, camY, camZ);
        updateFreeCamLookAt(camX, camY, camZ);
        
        if (animationTick >= ORBIT_DURATION) {
            currentState = ViewpointState.RETURNING;
            animationTick = 0;
        }
    }
    
    private static void updateReturning() {
        float progress = Math.min(1.0f, (float) animationTick / RETURN_DURATION);
        progress = easeInOutSine(progress);
        
        double startCamX = FreeCam.instance.getX();
        double startCamY = FreeCam.instance.getY();
        double startCamZ = FreeCam.instance.getZ();
        
        double endCamX = startPlayerX;
        double endCamY = startPlayerY + 1.62;
        double endCamZ = startPlayerZ;
        
        double camX = startCamX + (endCamX - startCamX) * progress;
        double camY = startCamY + (endCamY - startCamY) * progress;
        double camZ = startCamZ + (endCamZ - startCamZ) * progress;
        
        setFreeCamPosition(camX, camY, camZ);
        updateFreeCamLookAt(camX, camY, camZ);
        
        float distToTarget = (float) Math.sqrt(
            Math.pow(camX - endCamX, 2) +
            Math.pow(camY - endCamY, 2) +
            Math.pow(camZ - endCamZ, 2)
        );
        
        if (animationTick >= RETURN_DURATION || distToTarget < 0.1) {
            FreeCam.instance.toggle();
            currentState = ViewpointState.INACTIVE;
            animationTick = 0;
        }
    }
    
    private static void updateFreeCamLookAt(double camX, double camY, double camZ) {
        double dx = startPlayerX - camX;
        double dy = startPlayerY + 1.62 - camY;
        double dz = startPlayerZ - camZ;
        
        double distance = Math.sqrt(dx * dx + dz * dz);
        float yaw = -(float) Math.toDegrees(Math.atan2(dx, dz));
        
        float pitch;
        if (distance < 0.001) {
            pitch = dy < 0 ? -90.0f : 90.0f;
        } else {
            pitch = -(float) Math.toDegrees(Math.atan2(dy, distance));
        }
        
        setFreeCamRotation(pitch, yaw);
    }
    
    private static void setFreeCamPosition(double x, double y, double z) {
        try {
            freecamXField.set(FreeCam.instance, x);
            freecamYField.set(FreeCam.instance, y);
            freecamZField.set(FreeCam.instance, z);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
    
    private static void setFreeCamRotation(float xRot, float yRot) {
        try {
            freecamXRotField.set(FreeCam.instance, xRot);
            freecamYRotField.set(FreeCam.instance, yRot);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }
    
    public static boolean isActive() {
        if (currentState == ViewpointState.PULLBACK) {
            return true;
        } else if (currentState == ViewpointState.ORBITING) {
            return true;
        } else if (currentState == ViewpointState.RETURNING) {
            return true;
        } else if (currentState == ViewpointState.INACTIVE) {
            return false;
        }
        return false;
    }
    
    public static boolean isOverlooking() {
        return isActive();
    }
    
    public static void reset() {
        currentState = ViewpointState.INACTIVE;
        animationTick = 0;
        frozenPlayerPose = null;
    }
    
    /**
     * Snapshot of every render-relevant entity field that freecam perturbs while
     * active. apply() freezes the pose for one render call, restore is done by
     * applying a freshly captured live snapshot afterwards.
     */
    public static class PlayerPoseSnapshot {
        public double posX, posY, posZ;
        public double prevPosX, prevPosY, prevPosZ;
        public double lastTickPosX, lastTickPosY, lastTickPosZ;
        public float rotationYaw, prevRotationYaw;
        public float rotationPitch, prevRotationPitch;
        public float rotationYawHead, prevRotationYawHead;
        public float renderYawOffset, prevRenderYawOffset;
        public float cameraYaw, prevCameraYaw;
        public double chasingPosX, chasingPosY, chasingPosZ;
        public double prevChasingPosX, prevChasingPosY, prevChasingPosZ;
        public float distanceWalkedModified, prevDistanceWalkedModified;
        public float limbSwing, limbSwingAmount, prevLimbSwingAmount;
        
        public static PlayerPoseSnapshot capture(EntityPlayer player) {
            PlayerPoseSnapshot snap = new PlayerPoseSnapshot();
            snap.posX = player.posX;
            snap.posY = player.posY;
            snap.posZ = player.posZ;
            snap.prevPosX = player.prevPosX;
            snap.prevPosY = player.prevPosY;
            snap.prevPosZ = player.prevPosZ;
            snap.lastTickPosX = player.lastTickPosX;
            snap.lastTickPosY = player.lastTickPosY;
            snap.lastTickPosZ = player.lastTickPosZ;
            snap.rotationYaw = player.rotationYaw;
            snap.prevRotationYaw = player.prevRotationYaw;
            snap.rotationPitch = player.rotationPitch;
            snap.prevRotationPitch = player.prevRotationPitch;
            snap.rotationYawHead = player.rotationYawHead;
            snap.prevRotationYawHead = player.prevRotationYawHead;
            snap.renderYawOffset = player.renderYawOffset;
            snap.prevRenderYawOffset = player.prevRenderYawOffset;
            snap.cameraYaw = player.cameraYaw;
            snap.prevCameraYaw = player.prevCameraYaw;
            snap.chasingPosX = player.chasingPosX;
            snap.chasingPosY = player.chasingPosY;
            snap.chasingPosZ = player.chasingPosZ;
            snap.prevChasingPosX = player.prevChasingPosX;
            snap.prevChasingPosY = player.prevChasingPosY;
            snap.prevChasingPosZ = player.prevChasingPosZ;
            snap.distanceWalkedModified = player.distanceWalkedModified;
            snap.prevDistanceWalkedModified = player.prevDistanceWalkedModified;
            snap.limbSwing = player.limbSwing;
            snap.limbSwingAmount = player.limbSwingAmount;
            snap.prevLimbSwingAmount = player.prevLimbSwingAmount;
            return snap;
        }
        
        public void apply(EntityPlayer player) {
            player.posX = this.posX;
            player.posY = this.posY;
            player.posZ = this.posZ;
            player.prevPosX = this.prevPosX;
            player.prevPosY = this.prevPosY;
            player.prevPosZ = this.prevPosZ;
            player.lastTickPosX = this.lastTickPosX;
            player.lastTickPosY = this.lastTickPosY;
            player.lastTickPosZ = this.lastTickPosZ;
            player.rotationYaw = this.rotationYaw;
            player.prevRotationYaw = this.prevRotationYaw;
            player.rotationPitch = this.rotationPitch;
            player.prevRotationPitch = this.prevRotationPitch;
            player.rotationYawHead = this.rotationYawHead;
            player.prevRotationYawHead = this.prevRotationYawHead;
            player.renderYawOffset = this.renderYawOffset;
            player.prevRenderYawOffset = this.prevRenderYawOffset;
            player.cameraYaw = this.cameraYaw;
            player.prevCameraYaw = this.prevCameraYaw;
            player.chasingPosX = this.chasingPosX;
            player.chasingPosY = this.chasingPosY;
            player.chasingPosZ = this.chasingPosZ;
            player.prevChasingPosX = this.prevChasingPosX;
            player.prevChasingPosY = this.prevChasingPosY;
            player.prevChasingPosZ = this.prevChasingPosZ;
            player.distanceWalkedModified = this.distanceWalkedModified;
            player.prevDistanceWalkedModified = this.prevDistanceWalkedModified;
            player.limbSwing = this.limbSwing;
            player.limbSwingAmount = this.limbSwingAmount;
            player.prevLimbSwingAmount = this.prevLimbSwingAmount;
        }
    }
    
    private static float easeInOutSine(float t) {
        return (float) ((1 - Math.cos(t * Math.PI)) / 2);
    }
}
