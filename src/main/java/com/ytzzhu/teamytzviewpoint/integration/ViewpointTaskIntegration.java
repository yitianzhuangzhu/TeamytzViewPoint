package com.ytzzhu.teamytzviewpoint.integration;

import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.task.Task;
import com.feed_the_beast.ftbquests.quest.task.TaskType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ViewpointTaskIntegration {
    public static TaskType VIEWPOINT_TASK;
    
    public static void preInit() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(ViewpointTaskIntegration.class);
    }
    
    @SubscribeEvent
    public static void registerTasks(RegistryEvent.Register<TaskType> event) {
        VIEWPOINT_TASK = new TaskType(new TaskType.Provider() {
            @Override
            public Task create(Quest quest) {
                return new ViewpointTask(quest);
            }
        });
        VIEWPOINT_TASK.setRegistryName("teamytzviewpoint", "viewpoint");
        event.getRegistry().register(VIEWPOINT_TASK);
    }
}
