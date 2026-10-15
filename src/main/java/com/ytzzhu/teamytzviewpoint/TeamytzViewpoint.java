package com.ytzzhu.teamytzviewpoint;

import com.ytzzhu.teamytzviewpoint.blocks.ViewpointTileEntity;
import com.ytzzhu.teamytzviewpoint.client.ViewpointClientEvents;
import com.ytzzhu.teamytzviewpoint.client.ViewpointHudRenderer;
import com.ytzzhu.teamytzviewpoint.client.ViewpointKeyBindings;
import com.ytzzhu.teamytzviewpoint.client.ViewpointPlayerRenderer;
import com.ytzzhu.teamytzviewpoint.client.render.ViewpointBlockRenderer;
import com.ytzzhu.teamytzviewpoint.gui.ViewpointContainer;
import com.ytzzhu.teamytzviewpoint.gui.ViewpointGui;
import com.ytzzhu.teamytzviewpoint.network.ViewpointNameSyncPacket;
import com.ytzzhu.teamytzviewpoint.network.ViewpointTriggerPacket;
import com.ytzzhu.teamytzviewpoint.network.ViewpointUpdatePacket;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.Logger;

@Mod(modid = TeamytzViewpoint.MODID, name = TeamytzViewpoint.NAME, version = TeamytzViewpoint.VERSION,
     dependencies = "after:xaerominimap")
public class TeamytzViewpoint implements IGuiHandler
{
    public static final String MODID = "teamytzviewpoint";
    public static final String NAME = "Teamytz Viewpoint";
    public static final String VERSION = "4.0.0";
    
    @Mod.Instance(MODID)
    public static TeamytzViewpoint instance;

    private static Logger logger;
    
    public SimpleNetworkWrapper network;

    @SuppressWarnings("deprecation")
    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();
        logger.info("TeamytzViewpoint initialized!");
        GameRegistry.registerTileEntity(ViewpointTileEntity.class, "teamytzviewpoint:viewpoint_tile_entity");
        network = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        network.registerMessage(ViewpointUpdatePacket.Handler.class, ViewpointUpdatePacket.class, 0, Side.SERVER);
        network.registerMessage(ViewpointNameSyncPacket.Handler.class, ViewpointNameSyncPacket.class, 1, Side.CLIENT);
        network.registerMessage(ViewpointTriggerPacket.Handler.class, ViewpointTriggerPacket.class, 2, Side.SERVER);
        registerFTBQuestsTaskIntegration();
    }
    
    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        if (event.getSide() == Side.CLIENT) {
            // 输入锁 / tick 推进 / 每帧相机采样
            MinecraftForge.EVENT_BUS.register(new ViewpointClientEvents());
            // 鸟瞰期间玩家本体的显示：取消原版那一次渲染 + 在冻结位置精确重画一次
            MinecraftForge.EVENT_BUS.register(new ViewpointPlayerRenderer());
            MinecraftForge.EVENT_BUS.register(new ViewpointHudRenderer());
            // 注册"触发鸟瞰点"键位（默认 V，玩家可在"控制"里改）
            ViewpointKeyBindings.register();
            try {
                ClientRegistry.bindTileEntitySpecialRenderer(ViewpointTileEntity.class, new ViewpointBlockRenderer());
                logger.info("Viewpoint TESR registered.");
            } catch (Throwable t) {
                logger.error("Failed to register Viewpoint TESR; viewpoint blocks will render invisible!", t);
            }
            registerAmbienceIntegration();
        }
        net.minecraftforge.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(this, this);
    }
    
    private void registerAmbienceIntegration() {
        try {
            Class<?> ambienceClass = Class.forName("vazkii.ambience.Ambience");
            MinecraftForge.EVENT_BUS.register(new com.ytzzhu.teamytzviewpoint.integration.AmbienceIntegration());
            logger.info("Ambience integration registered!");
        } catch (ClassNotFoundException e) {
            logger.info("Ambience mod not found, skipping integration");
        }
    }
    
    private void registerFTBQuestsTaskIntegration() {
        try {
            Class<?> ftbqClass = Class.forName("com.feed_the_beast.ftbquests.FTBQuests");
            com.ytzzhu.teamytzviewpoint.integration.ViewpointTaskIntegration.preInit();
            logger.info("FTBQuests ViewpointTask integration registered!");
        } catch (ClassNotFoundException e) {
            logger.info("FTBQuests mod not found, skipping ViewpointTask integration");
        }
    }
    
    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tileEntity = world.getTileEntity(new BlockPos(x, y, z));
        if (tileEntity instanceof ViewpointTileEntity) {
            return new ViewpointContainer(player.inventory, (ViewpointTileEntity) tileEntity);
        }
        return null;
    }
    
    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        TileEntity tileEntity = world.getTileEntity(new BlockPos(x, y, z));
        if (tileEntity instanceof ViewpointTileEntity) {
            return new ViewpointGui((ViewpointTileEntity) tileEntity);
        }
        return null;
    }
}
