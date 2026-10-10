package com.ytzzhu.teamytzviewpoint.client;

import com.ytzzhu.teamytzviewpoint.TeamytzViewpoint;
import com.ytzzhu.teamytzviewpoint.items.ModItems;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 物品模型注册（纯客户端）。
 * <p>
 * <b>1.12.2 不会按注册名自动解析物品模型。</b>普通 {@code Item} 必须在这里显式把
 * {@code ModelResourceLocation} 绑到物品上，否则游戏直接拿原版 missingno 的紫黑占位图；
 * 而且因为模型没被解析、那张贴图根本没被请求，日志里**既不会报缺贴图也不会报缺模型**，
 * 排查时很容易误判成"贴图路径写错了"。
 * <p>
 * {@code ItemBlock} 不需要这一步：它走方块 {@code blockstates} 里的 {@code "inventory"} variant
 * （本模组的 {@code blockstates/viewpoint_block.json} 里已经写了这一项），所以鸟瞰点方块那个物品
 * 一直是正常的，只有扳手是普通 Item 才需要注册。
 * <p>
 * {@code value = Side.CLIENT} 保证专用服务端不会注册本类（服务端没有 {@code ModelLoader}）。
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = TeamytzViewpoint.MODID)
public class ClientModelRegistration {

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(ModItems.VIEWPOINT_WRENCH, 0,
                new ModelResourceLocation("teamytzviewpoint:viewpoint_wrench", "inventory"));
    }
}
