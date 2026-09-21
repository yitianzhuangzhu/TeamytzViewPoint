package com.ytzzhu.teamytzviewpoint.integration;

import com.ytzzhu.teamytzviewpoint.data.ViewpointUnlockData;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestData;
import com.feed_the_beast.ftbquests.quest.task.BooleanTaskData;
import com.feed_the_beast.ftbquests.quest.task.Task;
import com.feed_the_beast.ftbquests.quest.task.TaskData;
import com.feed_the_beast.ftbquests.quest.task.TaskType;
import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextComponentTranslation;

public class ViewpointTask extends Task {
    public String viewpointName = "";
    
    public ViewpointTask(Quest quest) {
        super(quest);
    }
    
    @Override
    public TaskType getType() {
        return ViewpointTaskIntegration.VIEWPOINT_TASK;
    }
    
    @Override
    public void writeData(NBTTagCompound compound) {
        super.writeData(compound);
        compound.setString("viewpointName", viewpointName);
    }
    
    @Override
    public void readData(NBTTagCompound compound) {
        super.readData(compound);
        viewpointName = compound.getString("viewpointName");
    }
    
    @Override
    public void writeNetData(DataOut data) {
        super.writeNetData(data);
        data.writeString(viewpointName);
    }
    
    @Override
    public void readNetData(DataIn data) {
        super.readNetData(data);
        viewpointName = data.readString();
    }
    
    @Override
    public void getConfig(ConfigGroup config) {
        super.getConfig(config);
        config.addString("viewpointName", () -> viewpointName, v -> viewpointName = v, "")
            .setDisplayName(new TextComponentTranslation("viewpoint.task.name"));
    }
    
    @Override
    public long getMaxProgress() {
        return 1L;
    }
    
    @Override
    public int autoSubmitOnPlayerTick() {
        return 10;
    }
    
    @Override
    public TaskData createData(QuestData data) {
        return new ViewpointTaskData(this, data);
    }
    
    public static class ViewpointTaskData extends BooleanTaskData {
        public ViewpointTaskData(ViewpointTask task, QuestData data) {
            super(task, data);
        }
        
        @Override
        public boolean canSubmit(net.minecraft.entity.player.EntityPlayerMP player) {
            ViewpointTask task = (ViewpointTask) this.task;
            ViewpointUnlockData unlockData = ViewpointUnlockData.get(player.getServerWorld());
            return unlockData.hasUnlocked(player.getUniqueID(), task.viewpointName);
        }
    }
}
