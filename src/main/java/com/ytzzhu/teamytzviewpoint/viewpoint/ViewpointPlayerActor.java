package com.ytzzhu.teamytzviewpoint.viewpoint;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 鸟瞰运镜里的"玩家替身"（仅客户端）。
 *
 * <h3>为什么需要它，而不是直接画真实玩家</h3>
 * 相机换成独立实体之后，原版"要不要画本地玩家"取决于 render view entity 是不是玩家自己、
 * 第三人称档位、骑乘状态、区块加载等一串条件 —— 相机独立之后这些条件全变了，
 * 实测就是画面里没有自己。与其去猜那些条件，不如像演出系统那样
 * <b>造一个只存在于客户端的"假玩家"实体放在鸟瞰点上</b>：
 * 它就是一个普通实体，走的是原版实体的正常渲染通道，不依赖任何特殊条件。
 *
 * <h3>外观怎么和本人一致</h3>
 * 用<b>同一个 {@link GameProfile}</b>（同 UUID）构造 —— 客户端 {@code PlayerInfoMap} 里本来就有
 * 本地玩家的条目，于是 {@code getLocationSkin()} / {@code getSkinType()} 直接命中同一条记录，
 * <b>皮肤与 Alex/Steve 模型自动和本人一致，不需要任何额外处理</b>。
 * <p>
 * 装备与皮肤分层则是<b>渲染时实时从当前玩家身上取</b>（下面三个 override），
 * 所以录进去的不是"当时的装备"，玩家换装后画面跟着变。
 *
 * <h3>为什么要把姿态一起搬过来</h3>
 * {@code EntityOtherPlayerMP.onLivingUpdate()} <b>不是空体</b>（它会按 {@code otherPlayerMPPosRotationIncrements}
 * 做位置插值），所以位置必须由我们每帧显式压住。另外手臂/腿的摆动量、潜行、使用的物品
 * 这些都是渲染时直接读实体字段的，因此渲染前把玩家身上那一组"姿态字段"整体复制过来，
 * 身体才会和本人一样（举盾、拉弓、潜行等），而不是站成一个僵直的木头人。
 */
@SideOnly(Side.CLIENT)
public class ViewpointPlayerActor extends EntityOtherPlayerMP {

    public ViewpointPlayerActor(World world, GameProfile profile) {
        super(world, profile);
        this.noClip = true;                 // 只是外观，不该被方块碰撞推走
        this.preventEntitySpawning = true;
    }

    /** 当前活着的玩家：外观与姿态的唯一来源。 */
    private static EntityPlayer live() {
        return Minecraft.getMinecraft().player;
    }

    // ---------------- 外观：装备与皮肤分层实时解析 ----------------

    @Override
    public ItemStack getHeldItem(EnumHand hand) {
        EntityPlayer p = live();
        return p == null ? ItemStack.EMPTY : p.getHeldItem(hand);
    }

    @Override
    public ItemStack getItemStackFromSlot(EntityEquipmentSlot slot) {
        EntityPlayer p = live();
        return p == null ? ItemStack.EMPTY : p.getItemStackFromSlot(slot);
    }

    /**
     * 皮肤的<b>第二层</b>（帽子/夹克/袖子/裤腿）开关。
     * <p>
     * {@code RenderPlayer} 会把每个外层部件的 {@code showModel} 设成
     * {@code isWearing(EnumPlayerModelParts.X)}，而它读的是实体 DataManager 里的
     * {@code PLAYER_MODEL_FLAG} 字节。替身是新实体、这个字节是 0，于是外层皮肤会被全部关掉
     * （表现就是"皮肤只渲染了一层"）。这里转发给活体玩家，让替身跟着玩家自己的显示设置走。
     */
    @Override
    public boolean isWearing(EnumPlayerModelParts part) {
        EntityPlayer p = live();
        return p != null && p.isWearing(part);
    }

    // ---------------- 姿态：把玩家身上那一组渲染字段整体搬过来 ----------------

    /**
     * 把位置/朝向/姿态对齐到"开演时冻结的玩家"，朝向与动作取玩家<b>此刻</b>的真实状态。
     *
     * @param x 冻结的脚底 X
     * @param y 冻结的脚底 Y
     * @param z 冻结的脚底 Z
     */
    public void syncToPlayer(double x, double y, double z) {
        EntityPlayer p = live();

        // 位置：prev 与 cur 写同一个值，渲染插值恒等于它，不会抖
        this.prevPosX = this.posX = this.lastTickPosX = x;
        this.prevPosY = this.posY = this.lastTickPosY = y;
        this.prevPosZ = this.posZ = this.lastTickPosZ = z;

        if (p == null) {
            return;
        }

        // 朝向：主体朝向用冻结值（鸟瞰期间玩家的 yaw/pitch 也被我们压住了，两者一致），
        // 头部/四肢的朝向与动作则实时取玩家的，姿势才自然
        this.prevRotationYaw = this.rotationYaw = p.rotationYaw;
        this.prevRotationPitch = this.rotationPitch = p.rotationPitch;
        this.prevRotationYawHead = this.rotationYawHead = p.rotationYawHead;
        this.prevRenderYawOffset = this.renderYawOffset = p.renderYawOffset;

        // 走动动画与附属渲染字段（披风摆动、受击变色、使用物品的姿势都读这些）
        this.limbSwing = p.limbSwing;
        this.limbSwingAmount = p.limbSwingAmount;
        this.prevLimbSwingAmount = p.prevLimbSwingAmount;
        this.distanceWalkedModified = p.distanceWalkedModified;
        this.prevDistanceWalkedModified = p.prevDistanceWalkedModified;
        this.chasingPosX = p.chasingPosX;
        this.chasingPosY = p.chasingPosY;
        this.chasingPosZ = p.chasingPosZ;
        this.prevChasingPosX = p.prevChasingPosX;
        this.prevChasingPosY = p.prevChasingPosY;
        this.prevChasingPosZ = p.prevChasingPosZ;
        this.cameraYaw = p.cameraYaw;
        this.prevCameraYaw = p.prevCameraYaw;
        this.hurtTime = p.hurtTime;
        this.maxHurtTime = p.maxHurtTime;
        this.deathTime = p.deathTime;
        this.swingProgress = p.swingProgress;
        this.prevSwingProgress = p.prevSwingProgress;
        this.isSwingInProgress = p.isSwingInProgress;
        this.swingingHand = p.swingingHand;
        this.setSneaking(p.isSneaking());
        this.setSprinting(p.isSprinting());
        this.setEntityInvulnerable(p.isEntityInvulnerable(net.minecraft.util.DamageSource.GENERIC));
    }
}
