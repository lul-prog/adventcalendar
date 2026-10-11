package com.adventcalendar;

import com.adventcalendar.command.AdventCommand;
import com.adventcalendar.network.ModNetwork;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Clase principal del mod Advent Calendar. */
@Mod(AdventCalendar.MOD_ID)
public class AdventCalendar {
    public static final String MOD_ID = "adventcalendar";
    public static final Logger LOGGER = LogManager.getLogger();

    public AdventCalendar() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
        bus.addListener(this::onAttributes);
        bus.addListener(this::onCreativeTab);
        ModNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(AdventCommand::register);
    }

    /** Atributos mínimos del NPC (sin esto el registro de la entidad falla). */
    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.ADVENT_NPC.get(), Mob.createMobAttributes().build());
    }

    /** El mando de control aparece en la pestaña de herramientas del creativo. */
    private void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.CONTROL_WAND);
        }
    }
}
