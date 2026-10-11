package com.adventcalendar;

import com.adventcalendar.item.AdventCalendarItem;
import com.adventcalendar.item.ControlWandItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Items of the mod. */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AdventCalendar.MOD_ID);

    /** OP tool: select, move and configure the Advent NPC. */
    public static final RegistryObject<Item> CONTROL_WAND =
            ITEMS.register("control_wand", ControlWandItem::new);

    /** Portable calendar: right click to open the GUI. */
    public static final RegistryObject<Item> ADVENT_CALENDAR =
            ITEMS.register("advent_calendar", AdventCalendarItem::new);

    private ModItems() {
    }
}
