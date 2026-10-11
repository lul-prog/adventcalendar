package com.adventcalendar.client;

import com.adventcalendar.AdventCalendar;
import com.adventcalendar.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client side glue: clears the cached calendar state when leaving a world and plays the
 * "a new door unlocked" sound when the calendar rolls over while the player has the GUI open.
 */
@Mod.EventBusSubscriber(modid = AdventCalendar.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private static int lastUnlockedThrough = -1;

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientCalendarState.get().reset();
        lastUnlockedThrough = -1;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            lastUnlockedThrough = -1;
            return;
        }
        int unlocked = ClientCalendarState.get().unlockedThrough();
        if (lastUnlockedThrough >= 0 && unlocked > lastUnlockedThrough && minecraft.screen instanceof AbstractCalendarScreen) {
            minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(ModSounds.UNLOCK.get(), 1.4F));
        }
        lastUnlockedThrough = unlocked;
    }
}
