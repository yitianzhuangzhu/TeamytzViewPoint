package com.ytzzhu.teamytzviewpoint;

import com.ytzzhu.teamytzviewpoint.blocks.ViewpointTileEntity;
import com.ytzzhu.teamytzviewpoint.client.ViewpointClientEvents;
import com.ytzzhu.teamytzviewpoint.client.ViewpointRenderHandler;
import com.ytzzhu.teamytzviewpoint.gui.ViewpointContainer;
import com.ytzzhu.teamytzviewpoint.gui.ViewpointGui;
import com.ytzzhu.teamytzviewpoint.network.ViewpointNameSyncPacket;
import com.ytzzhu.teamytzviewpoint.network.ViewpointUpdatePacket;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
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

@Mod(modid = TeamytzViewpoint.MODID, name = TeamytzViewpoint.NAME, version = TeamytzViewpoint.VERSION)
public class TeamytzViewpoint implements IGuiHandler
{
    public static final String MODID = "teamytzviewpoint";
    public static final String NAME = "Teamytz Viewpoint";
    public static final String VERSION = "1.0.15";
    
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
        registerFTBQuestsTaskIntegration();
    }
    
    @EventHandler
    public void init(FMLInitializationEvent event)
    {
        if (event.getSide() == Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new ViewpointClientEvents());
            MinecraftForge.EVENT_BUS.register(new ViewpointRenderHandler());
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
