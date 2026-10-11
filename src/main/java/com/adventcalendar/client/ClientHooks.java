package com.adventcalendar.client;

import com.adventcalendar.entity.AdventNpcEntity;
import com.adventcalendar.network.CalendarSyncPacket;
import com.adventcalendar.network.ClaimFeedbackPacket;
import com.adventcalendar.network.OpenGuiPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Bridges from the network layer into client only code. Referenced through
 * {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)} so the classes are only loaded on
 * a client.
 */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void applySync(CalendarSyncPacket packet) {
        ClientCalendarState.get().apply(packet);
    }

    public static void onClaimFeedback(ClaimFeedbackPacket packet) {
        ClientCalendarState state = ClientCalendarState.get();
        state.updateCounters(packet.claimedMask, packet.pendingCount);
        if (packet.result.isSuccess()) {
            state.startClaimAnimation(packet.day);
        }
    }

    public static void openGui(int gui, int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientCalendarState state = ClientCalendarState.get();
        switch (gui) {
            case OpenGuiPacket.GUI_ADMIN -> minecraft.setScreen(new AdventAdminScreen());
            case OpenGuiPacket.GUI_DEBUG -> minecraft.setScreen(new AdventDebugScreen());
            case OpenGuiPacket.GUI_NPC_CONFIG -> {
                if (minecraft.level != null) {
                    Entity entity = minecraft.level.getEntity(entityId);
                    if (entity instanceof AdventNpcEntity npc) {
                        minecraft.setScreen(new NpcConfigScreen(npc));
                    }
                }
            }
            default -> minecraft.setScreen(new AdventCalendarScreen());
        }
    }
}
