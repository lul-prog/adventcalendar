package com.adventcalendar.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.server.level.ServerLevel;

import java.util.Arrays;

/**
 * Recompensas del calendario: 31 ItemStacks guardados en el SavedData del overworld.
 * Persisten entre reinicios del servidor.
 */
public class AdventRewardsData extends SavedData {
    private final ItemStack[] rewards = new ItemStack[31];

    public AdventRewardsData() {
        Arrays.fill(rewards, ItemStack.EMPTY);
    }

    public static AdventRewardsData get(ServerLevel overworld) {
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(AdventRewardsData::load, AdventRewardsData::new,
                "adventcalendar_rewards");
    }

    public static AdventRewardsData load(CompoundTag tag) {
        AdventRewardsData data = new AdventRewardsData();
        for (int i = 0; i < 31; i++) {
            if (tag.contains("r" + i)) {
                data.rewards[i] = ItemStack.of(tag.getCompound("r" + i));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        for (int i = 0; i < 31; i++) {
            if (!rewards[i].isEmpty()) {
                tag.put("r" + i, rewards[i].save(new CompoundTag()));
            }
        }
        return tag;
    }

    /** Guarda una copia del stack como recompensa del día (1-31). */
    public void setReward(int day, ItemStack stack) {
        rewards[day - 1] = stack;
        setDirty();
    }

    public ItemStack getReward(int day) {
        return rewards[day - 1];
    }

    /** Bitmask de los días que tienen recompensa configurada (para pintar la GUI). */
    public int getRewardsMask() {
        int mask = 0;
        for (int i = 0; i < 31; i++) {
            if (!rewards[i].isEmpty()) {
                mask |= (1 << i);
            }
        }
        return mask;
    }
}
