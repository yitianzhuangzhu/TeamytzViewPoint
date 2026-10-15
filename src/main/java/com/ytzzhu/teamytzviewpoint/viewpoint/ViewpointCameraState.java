package com.ytzzhu.teamytzviewpoint.viewpoint;

/**
 * 相机在某一时刻的完整状态（纯数据，双端安全，不依赖任何客户端类）。
 * <p>
 * 它是"运镜数学 → 相机实体"之间唯一的数据载体：{@link ViewpointCameraManager} 里的
 * 轨道采样负责写入，渲染层负责读出来写进 {@link CinematicCamera}。
 * <p>
 * 角度约定与原版一致：{@code yaw=0} 面向 +Z，朝向向量为 {@code (-sin(yaw), 0, cos(yaw))}；
 * pitch 正数朝下。
 */
public final class ViewpointCameraState {

    public double x;
    public double y;
    public double z;

    /** 水平朝向（度）。 */
    public float yaw;
    /** 俯仰（度，正数朝下，与原版一致）。 */
    public float pitch;

    /** 视野角。 */
    public float fov = 70.0F;
}
