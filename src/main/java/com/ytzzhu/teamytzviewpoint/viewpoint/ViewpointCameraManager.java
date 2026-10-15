package com.ytzzhu.teamytzviewpoint.viewpoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 鸟瞰运镜的相机接管（仅客户端）。
 *
 * <h3>用的是什么机制</h3>
 * 开演时造一个 {@link CinematicCamera} 实体丢进客户端世界，然后
 * {@code Minecraft.setRenderViewEntity(相机)} —— 从那以后原版相机的位置/朝向完全由我们决定。
 * 这是旁观模式和 ReplayMod 的 {@code CameraEntity} 走的同一条路，
 * <b>不需要反射任何第三方 mod 的私有字段，也不再需要 freecam 作为前置</b>。
 *
 * <h3>为什么每帧重算而不是每 tick 累加</h3>
 * 运镜是"时间的纯函数"：{@code sample(time, out)} 在任意时刻都能求值。
 * 所以渲染线程用 {@code elapsedTicks - 1 + partialTicks} 每帧采样一次并整帧写入相机位姿，
 * 镜头运动与客户端 tick 节奏解耦，帧率再高也不抖、也不会出现"一帧跑了两个 tick"的跳变。
 *
 * <h3>生命周期：end 必须无条件幂等</h3>
 * 退出世界、换维度、掉线、玩家自己触发收尾、时长走完 —— 任何一条路径都要能收场，
 * 否则玩家会卡在"视角不在自己手里"的状态。{@link #update()} 每 tick 对账一次。
 *
 * <h3>玩家实体的朝向会被压住</h3>
 * 鼠标增量是打在<b>玩家实体</b>上的（相机实体不接收输入），所以每 tick 把玩家的 yaw/pitch
 * 复位成开演时的值并清空鼠标增量。收尾时也把朝向复位，交接才不跳。
 */
@SideOnly(Side.CLIENT)
public final class ViewpointCameraManager {

    /** 相机实体用负数 id：远离服务端实体的 id 段，不会撞上任何同步下来的实体。 */
    private static final int CAMERA_ENTITY_ID = -2100000;

    /** 拉远（从眼睛升到环绕圈起点）。 */
    private static final int PULLBACK_DURATION = 60;
    /** 环绕一整圈。 */
    private static final int ORBIT_DURATION = 360;
    /** 归位（收回眼睛）。 */
    private static final int RETURN_DURATION = 60;

    /** 环绕半径（格）。 */
    private static final double ORBIT_RADIUS = 25.0D;
    /** 环绕高度，相对玩家眼睛（格）。 */
    private static final double ORBIT_HEIGHT = 20.0D;

    /** 归位段：相机与眼睛的距离小于它就认为已经到位，可以提前收尾。 */
    private static final double RETURN_EPSILON = 0.1D;

    private static final ViewpointCameraManager INSTANCE = new ViewpointCameraManager();

    // ---- 对外暴露的状态 ----

    /** 当前动画阶段。 */
    public static ViewpointState currentState = ViewpointState.INACTIVE;
    /** 当前阶段内已经过的 tick 数。 */
    public static int animationTick = 0;

    /** 开演时冻结的玩家脚底坐标（收尾交接的目标点）。 */
    public static double startPlayerX, startPlayerY, startPlayerZ;
    /** 开演时冻结的玩家水平朝向。 */
    public static float startPlayerYaw;

    // ---- 实例字段 ----

    private boolean active;
    private int elapsedTicks;
    private CinematicCamera camera;
    /** 相机实体所在的那个客户端世界（收尾时要往它里面删实体，所以必须是 WorldClient 类型）。 */
    private WorldClient cameraWorld;
    private Entity previousViewEntity;
    /** 开演时冻结的玩家俯仰（收尾交接的目标朝向之一）。 */
    private float frozenPitch;
    /** 开演时冻结的玩家眼高（站着 1.62，潜行/游泳会更低）。 */
    private float frozenEyeHeight;

    /** 复用的采样结果，避免每帧分配。仅渲染线程访问。 */
    private final ViewpointCameraState state = new ViewpointCameraState();

    private ViewpointCameraManager() {
    }

    // ================= 对外入口 =================

    public static boolean isActive() {
        return INSTANCE.active;
    }

    /** 别名：语义上"正在鸟瞰"，与 {@link #isActive()} 等价。 */
    public static boolean isOverlooking() {
        return INSTANCE.active;
    }

    /** 开演前冻结的玩家眼睛 Y —— 收尾交接与"看向焦点"都用它。 */
    public static double startPlayerEyeY() {
        return startPlayerY + INSTANCE.frozenEyeHeight;
    }

    /** 开演前冻结的玩家俯仰 —— 强制重画玩家本体时要用它，否则身体会跟着鼠标转。 */
    public static float startPlayerPitch() {
        return INSTANCE.frozenPitch;
    }

    /**
     * 开始鸟瞰：造相机实体、接管 render view entity、冻结玩家朝向。
     * <p>
     * 已经在播放时不重复开演（调用方 {@code ViewpointTrigger} 也会先查 {@code isActive()}）。
     */
    public static void startViewpoint(EntityPlayer player) {
        if (player == null) {
            return;
        }
        if (INSTANCE.active) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || !(mc.world instanceof net.minecraft.client.multiplayer.WorldClient)) {
            return;
        }

        INSTANCE.active = true;
        INSTANCE.elapsedTicks = 0;
        INSTANCE.frozenPitch = player.rotationPitch;
        INSTANCE.frozenEyeHeight = player.getEyeHeight();
        INSTANCE.previousViewEntity = mc.getRenderViewEntity();

        currentState = ViewpointState.PULLBACK;
        animationTick = 0;

        startPlayerX = player.posX;
        startPlayerY = player.posY;
        startPlayerZ = player.posZ;
        startPlayerYaw = player.rotationYaw;

        WorldClient world = (WorldClient) mc.world;
        INSTANCE.cameraWorld = world;
        INSTANCE.camera = new CinematicCamera(world);
        INSTANCE.camera.setPosition(startPlayerX, startPlayerY, startPlayerZ);
        world.addEntityToWorld(CAMERA_ENTITY_ID, INSTANCE.camera);
        mc.setRenderViewEntity(INSTANCE.camera);
    }

    /**
     * 每 tick 对账一次（在 {@code TickEvent.ClientTickEvent} 里调）。
     * <p>
     * 负责：外部状态变化时收尾、压住玩家朝向、推进阶段计时。
     */
    public static void update() {
        if (!INSTANCE.active) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();

        // 世界换了（退出存档 / 换维度 / 掉线）→ 相机实体在那个世界已经不存在了，立刻收尾
        if (mc.world == null || mc.player == null || mc.world != INSTANCE.cameraWorld) {
            end();
            return;
        }

        // 压住玩家朝向并清空鼠标增量：鼠标增量打在玩家身上，不清就会一边环绕一边转视角
        INSTANCE.freezePlayerAim(mc.player);

        INSTANCE.elapsedTicks++;
        INSTANCE.advanceState();

        if (INSTANCE.elapsedTicks > totalDuration()) {
            end();
        }
    }

    /**
     * 每帧采样并写入相机位姿（在 {@code TickEvent.RenderTickEvent.START} 里调）。
     * <p>
     * 必须用 START：它在 {@code entityRenderer.updateCameraAndRender} 之前发出。
     *
     * @param partialTicks 距上一次 tick 过去的时间比例（{@code RenderTickEvent.renderTickTime}）
     */
    public static void updateCamera(float partialTicks) {
        if (!INSTANCE.active || INSTANCE.camera == null) {
            return;
        }
        float time = INSTANCE.elapsedTicks - 1 + partialTicks;
        if (time < 0.0F) {
            time = 0.0F;
        }
        INSTANCE.sample(time, INSTANCE.state);
        INSTANCE.camera.setFrame(INSTANCE.state);
    }

    /** 无条件收尾（幂等）。 */
    public static void reset() {
        end();
    }

    // ================= 内部实现 =================

    private static int totalDuration() {
        return PULLBACK_DURATION + ORBIT_DURATION + RETURN_DURATION;
    }

    private void freezePlayerAim(EntityPlayer player) {
        player.rotationYaw = startPlayerYaw;
        player.rotationPitch = this.frozenPitch;
        player.prevRotationYaw = startPlayerYaw;
        player.prevRotationPitch = this.frozenPitch;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.mouseHelper != null) {
            mc.mouseHelper.deltaX = 0;
            mc.mouseHelper.deltaY = 0;
        }
    }

    /** 推进阶段计时，并在跨段时复位 {@link #animationTick}。 */
    private void advanceState() {
        animationTick++;
        if (currentState == ViewpointState.PULLBACK) {
            if (animationTick >= PULLBACK_DURATION) {
                currentState = ViewpointState.ORBITING;
                animationTick = 0;
            }
        } else if (currentState == ViewpointState.ORBITING) {
            if (animationTick >= ORBIT_DURATION) {
                currentState = ViewpointState.RETURNING;
                animationTick = 0;
            }
        } else if (currentState == ViewpointState.RETURNING) {
            if (animationTick >= RETURN_DURATION || eyeDistance(animationTick) < RETURN_EPSILON) {
                end();
            }
        }
    }

    /**
     * 轨道采样：拉远 → 环绕 → 归位，全程视线锁定玩家眼睛，最后精确回到玩家眼睛与朝向。
     * <p>
     * 角度约定与原版一致：{@code yaw = -atan2(dx, dz)}，{@code pitch = -atan2(dy, 水平距离)}。
     */
    private void sample(float time, ViewpointCameraState out) {
        out.fov = 70.0F;

        double focusX = startPlayerX;
        double focusY = startPlayerEyeY();
        double focusZ = startPlayerZ;
        // 环绕圈起点 = 玩家正后方（相对玩家冻结朝向），所以第一镜是"从背后拉起"
        double base = Math.toRadians(startPlayerYaw);
        double orbitY = focusY + ORBIT_HEIGHT;

        if (time < PULLBACK_DURATION) {
            // ---- 拉远：从眼睛平滑升到环绕圈起点 ----
            float p = ease(time / PULLBACK_DURATION);
            out.x = lerp(focusX, orbitX(base, 0.0D, focusX), p);
            out.y = lerp(focusY, orbitY, p);
            out.z = lerp(focusZ, orbitZ(base, 0.0D, focusZ), p);
            lookAt(out, focusX, focusY, focusZ, startPlayerYaw, this.frozenPitch);

        } else if (time < PULLBACK_DURATION + ORBIT_DURATION) {
            // ---- 环绕：绕焦点转一整圈，高度不变 ----
            float p = (time - PULLBACK_DURATION) / (float) ORBIT_DURATION;
            double a = p * Math.PI * 2.0D;
            out.x = orbitX(base, a, focusX);
            out.y = orbitY;
            out.z = orbitZ(base, a, focusZ);
            lookAt(out, focusX, focusY, focusZ, startPlayerYaw, this.frozenPitch);

        } else {
            // ---- 归位：位置收回眼睛，朝向转回玩家原朝向 ----
            float p = ease((time - PULLBACK_DURATION - ORBIT_DURATION) / (float) RETURN_DURATION);
            double sx = orbitX(base, Math.PI * 2.0D, focusX);
            double sz = orbitZ(base, Math.PI * 2.0D, focusZ);
            out.x = lerp(sx, focusX, p);
            out.y = lerp(orbitY, focusY, p);
            out.z = lerp(sz, focusZ, p);

            // 起始朝向 = 环绕圈终点处"看向焦点"的朝向；再绕最短弧转回玩家的原朝向。
            // 这一段是"无缝交接"的关键：p=1 时相机位置/朝向与玩家眼睛、玩家朝向完全重合，
            // 所以此刻把 render view entity 换回玩家，画面不会跳。
            lookAt(out, focusX, focusY, focusZ, startPlayerYaw, this.frozenPitch);
            out.yaw = out.yaw + wrapDegrees(startPlayerYaw - out.yaw) * p;
            out.pitch = out.pitch + (this.frozenPitch - out.pitch) * p;
        }
    }

    /** 环绕圈上角度 a 处的 X（a=0 为玩家正后方）。 */
    private static double orbitX(double base, double a, double focusX) {
        return focusX + Math.sin(base + a) * ORBIT_RADIUS;
    }

    private static double orbitZ(double base, double a, double focusZ) {
        return focusZ - Math.cos(base + a) * ORBIT_RADIUS;
    }

    /**
     * 让相机看向焦点。
     * <p>
     * 相机几乎贴在焦点上时（开演第 0 帧相机就在眼睛里）退化为玩家的原朝向，
     * 这样第一帧画面与触发前的玩家视角完全一致，不会先甩一下。
     */
    private static void lookAt(ViewpointCameraState out, double focusX, double focusY, double focusZ,
                               float fallbackYaw, float fallbackPitch) {
        double dx = focusX - out.x;
        double dy = focusY - out.y;
        double dz = focusZ - out.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 0.5D) {
            out.yaw = fallbackYaw;
            out.pitch = fallbackPitch;
            return;
        }
        out.yaw = (float) -Math.toDegrees(Math.atan2(dx, dz));
        out.pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }

    /** 归位段里相机到玩家眼睛的距离，用来判断"已经到位"。 */
    private double eyeDistance(int tick) {
        sample(PULLBACK_DURATION + ORBIT_DURATION + tick, this.state);
        double dx = this.state.x - startPlayerX;
        double dy = this.state.y - startPlayerEyeY();
        double dz = this.state.z - startPlayerZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double lerp(double a, double b, float p) {
        return a + (b - a) * p;
    }

    /** 缓入缓出（与项目里其它动画同一套手感）。 */
    private static float ease(float t) {
        float c = t < 0.0F ? 0.0F : (t > 1.0F ? 1.0F : t);
        return (float) ((1.0D - Math.cos(c * Math.PI)) / 2.0D);
    }

    /** 把角度规整到 (-180, 180]，用于走最短弧。 */
    private static float wrapDegrees(float v) {
        return MathHelper.wrapDegrees(v);
    }

    /**
     * 收尾（幂等）：撤掉相机实体、把视角还给玩家、复位玩家朝向。
     *
     * <p>返回视角前必须先把玩家朝向复位成开演时的值 —— 归位段的终点就是"玩家眼睛 + 开演朝向"，
     * 两者对上，交接瞬间画面才不跳。</p>
     */
    private static void end() {
        if (!INSTANCE.active) {
            return;
        }
        INSTANCE.active = false;
        Minecraft mc = Minecraft.getMinecraft();

        // 1) 把相机实体从"它当时所在的那个世界"移除。
        //    换维度后 mc.world 已经是新世界，往新世界删是删不掉的，也没必要 —— 旧世界整个被丢掉了。
        if (INSTANCE.camera != null && INSTANCE.cameraWorld != null && mc.world == INSTANCE.cameraWorld) {
            INSTANCE.cameraWorld.removeEntityFromWorld(CAMERA_ENTITY_ID);
        }
        INSTANCE.camera = null;
        INSTANCE.cameraWorld = null;

        // 2) 视角还给玩家，并复位演出期间被我们压住的朝向
        if (mc.player != null) {
            mc.player.rotationYaw = startPlayerYaw;
            mc.player.rotationPitch = INSTANCE.frozenPitch;
            mc.player.prevRotationYaw = startPlayerYaw;
            mc.player.prevRotationPitch = INSTANCE.frozenPitch;
        }
        mc.setRenderViewEntity(mc.player != null ? mc.player : INSTANCE.previousViewEntity);
        INSTANCE.previousViewEntity = null;

        currentState = ViewpointState.INACTIVE;
        animationTick = 0;
    }
}
