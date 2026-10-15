package com.ytzzhu.teamytzviewpoint.viewpoint;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 鸟瞰运镜用的相机实体（仅客户端）。
 * <p>
 * 把 {@code Minecraft.setRenderViewEntity(这个实体)} 一设，原版相机的位置/朝向就完全由我们决定 ——
 * {@code EntityRenderer.orientCamera} 的位置、眼高、yaw/pitch 全部只从 render view entity 取。
 * 这正是旁观模式与 ReplayMod 的 {@code CameraEntity} 走的同一条路，
 * <b>不需要反射任何第三方 mod 的私有字段，也不需要 freecam 之类的前置 mod</b>。
 * <p>
 * 它被放进客户端世界（{@code WorldClient.addEntityToWorld}），让区块/视锥/雾的计算跟着它走；
 * 服务端完全不知道它存在，所以它不受伤、不存档、不参与任何同步。
 * <p>
 * <b>为什么"把玩家实体挪来挪去"的老做法必须换掉</b>：freecam 那套是把玩家实体传送到相机位置、
 * 再每帧改写它的朝向。副作用是玩家的 {@code renderYawOffset / chasingPos / limbSwing /
 * cameraYaw} 全被搅动，身体和披风会乱转，于是不得不额外做一套"冻结玩家姿态再强制重画"的补丁。
 * 用独立相机实体之后，玩家实体的位置与朝向在整个动画期间<b>完全没被动过</b>，
 * 原版实体的渲染通道会自然地把站在鸟瞰点上的玩家照常画出来（第三人称看自己），
 * 那套补丁（姿态快照 + 强制重画）也就整体不需要了。
 */
@SideOnly(Side.CLIENT)
public class CinematicCamera extends Entity {

    /**
     * 镜头视野角。
     * <p>
     * 只作为 {@link ViewpointCameraState} 的载体保留：原版相机没有"自定义 FOV"这个自由度，
     * 要改必须走 {@code EntityViewRenderEvent.FOVModifier}；本项目不需要改 FOV，所以留 70 不用。
     */
    public float fov = 70.0F;

    public CinematicCamera(World world) {
        super(world);
        setSize(0.1F, 0.1F);
        this.noClip = true;                 // 相机穿墙：运镜不该被方块挡住
        this.preventEntitySpawning = true;  // 不参与任何自然生成逻辑
    }

    /**
     * 相机不该有"眼高"。
     * <p>
     * 相机最终 Y = {@code posY + getEyeHeight()}，所以这里必须返回 0，
     * 否则整个镜头会平白高出 1.6 格（{@code Entity} 基类的默认值不是 0）。
     * 这也是为什么 {@link ViewpointCameraState} 里的 y 是"相机本身"的高度，不做额外偏移。
     */
    @Override
    public float getEyeHeight() {
        return 0.0F;
    }

    /**
     * 精确写入"本帧"的位姿。
     * <p>
     * {@code EntityRenderer.orientCamera} 的插值是
     * {@code prevPos + (pos - prevPos) * partialTicks}。这里把 prev 与 cur 写成同一个值，
     * 插值结果就恒等于该值 —— 于是每帧都能取到轨道的<b>精确</b>采样，
     * 不受"一帧跑了几个 tick"影响，也不需要自己去猜 tick 边界。
     */
    public void setFrame(ViewpointCameraState state) {
        this.prevPosX = this.posX = this.lastTickPosX = state.x;
        this.prevPosY = this.posY = this.lastTickPosY = state.y;
        this.prevPosZ = this.posZ = this.lastTickPosZ = state.z;
        this.prevRotationYaw = this.rotationYaw = state.yaw;
        this.prevRotationPitch = this.rotationPitch = state.pitch;
        this.fov = state.fov;
    }

    // ---- Entity 的三个抽象方法：相机是纯客户端临时实体，不需要任何持久化 ----

    @Override
    protected void entityInit() {
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound nbt) {
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound nbt) {
    }
}
