package com.adventcalendar.client;

import com.adventcalendar.AdventCalendar;
import com.adventcalendar.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registro del renderer del NPC. Solo existe en el cliente (distmarker). */
@Mod.EventBusSubscriber(modid = AdventCalendar.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ADVENT_NPC.get(), AdventNpcRenderer::new);
    }
}
