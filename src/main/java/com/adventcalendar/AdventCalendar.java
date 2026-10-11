package com.adventcalendar;

import com.adventcalendar.command.AdventCommand;
import com.adventcalendar.data.AdventDataManager;
import com.adventcalendar.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Advent Calendar - a real-world date driven advent calendar for Forge 1.20.1.
 *
 * <p>Twenty-four configurable doors unlock one per day in December, every player tracks their
 * own progress (UUID based, JSON persisted), rewards can be items or commands and a fully
 * customizable NPC hands the calendar out.</p>
 */
@Mod(AdventCalendar.MOD_ID)
public class AdventCalendar {
    public static final String MOD_ID = "adventcalendar";
    public static final Logger LOGGER = LogManager.getLogger("AdventCalendar");

    public AdventCalendar() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        AdventConfig.register();
        ModSounds.SOUNDS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        modBus.addListener(this::onEntityAttributes);
        modBus.addListener(this::onCreativeTabContents);

        ModNetwork.register();

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    private void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.ADVENT_NPC.get(), Mob.createMobAttributes().build());
    }

    private void onCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES
                || event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.CONTROL_WAND);
            event.accept(ModItems.ADVENT_CALENDAR);
        }
    }

    // ------------------------------------------------------------------ server events

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        AdventCommand.register(event);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        AdventDataManager.get().onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        AdventDataManager.get().onServerStopping();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            AdventDataManager.get().tick(event.getServer());
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AdventDataManager data = AdventDataManager.get();
            data.claims().touch(player.getUUID(), player.getGameProfile().getName());
            data.syncTo(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        AdventDataManager.get().saveAsync();
    }

    @SubscribeEvent
    public void onEntityJoinLevel(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        // An Advent NPC always keeps its own configuration, even when spawned by a structure or
        // another mod: make sure the synced values match the saved ones.
        if (event.getEntity() instanceof AdventNpcEntity npc && !event.getLevel().isClientSide) {
            npc.setSkinModel(npc.getSkinModel());
        }
    }
}
