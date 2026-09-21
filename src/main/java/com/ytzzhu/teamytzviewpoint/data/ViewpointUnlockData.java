package com.ytzzhu.teamytzviewpoint.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import java.util.*;

public class ViewpointUnlockData extends WorldSavedData {
    private static final String DATA_NAME = "viewpoint_unlocks";
    
    private Map<UUID, Set<String>> playerUnlocks = new HashMap<>();
    
    public ViewpointUnlockData(String name) {
        super(name);
    }
    
    public static ViewpointUnlockData get(World world) {
        ViewpointUnlockData data = (ViewpointUnlockData) world.getMapStorage().getOrLoadData(ViewpointUnlockData.class, DATA_NAME);
        if (data == null) {
            data = new ViewpointUnlockData(DATA_NAME);
            world.getMapStorage().setData(DATA_NAME, data);
        }
        return data;
    }
    
    public void unlockViewpoint(UUID playerUuid, String viewpointName) {
        playerUnlocks.computeIfAbsent(playerUuid, k -> new HashSet<>()).add(viewpointName);
        markDirty();
    }
    
    public boolean hasUnlocked(UUID playerUuid, String viewpointName) {
        Set<String> unlocked = playerUnlocks.get(playerUuid);
        return unlocked != null && unlocked.contains(viewpointName);
    }
    
    public Set<String> getUnlockedViewpoints(UUID playerUuid) {
        return playerUnlocks.getOrDefault(playerUuid, Collections.emptySet());
    }
    
    @Override
    public void readFromNBT(NBTTagCompound compound) {
        playerUnlocks.clear();
        NBTTagList playersList = compound.getTagList("Players", 10);
        for (int i = 0; i < playersList.tagCount(); i++) {
            NBTTagCompound playerCompound = playersList.getCompoundTagAt(i);
            UUID uuid = UUID.fromString(playerCompound.getString("UUID"));
            Set<String> unlocks = new HashSet<>();
            NBTTagList unlocksList = playerCompound.getTagList("Unlocks", 8);
            for (int j = 0; j < unlocksList.tagCount(); j++) {
                unlocks.add(unlocksList.getStringTagAt(j));
            }
            playerUnlocks.put(uuid, unlocks);
        }
    }
    
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList playersList = new NBTTagList();
        for (Map.Entry<UUID, Set<String>> entry : playerUnlocks.entrySet()) {
            NBTTagCompound playerCompound = new NBTTagCompound();
            playerCompound.setString("UUID", entry.getKey().toString());
            NBTTagList unlocksList = new NBTTagList();
            for (String name : entry.getValue()) {
                unlocksList.appendTag(new NBTTagString(name));
            }
            playerCompound.setTag("Unlocks", unlocksList);
            playersList.appendTag(playerCompound);
        }
        compound.setTag("Players", playersList);
        return compound;
    }
}
