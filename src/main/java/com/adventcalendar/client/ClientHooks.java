package com.adventcalendar.client;

import com.adventcalendar.entity.AdventNpcEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Puentes hacia código de cliente, llamados desde los paquetes de red. */
public class ClientHooks {
    public static void openCalendar(int claimedMask, int rewardsMask) {
        Minecraft.getInstance().setScreen(new AdventCalendarScreen(claimedMask, rewardsMask));
    }

    public static void openNpcConfig(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity e = mc.level.getEntity(entityId);
        if (e instanceof AdventNpcEntity npc) {
            mc.setScreen(new NpcConfigScreen(npc));
        }
    }
}
