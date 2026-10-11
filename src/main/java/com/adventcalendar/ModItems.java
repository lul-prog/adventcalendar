package com.adventcalendar;

import com.adventcalendar.item.ControlWandItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registro de ítems del mod. */
public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AdventCalendar.MOD_ID);

    public static final RegistryObject<Item> CONTROL_WAND =
            ITEMS.register("control_wand", ControlWandItem::new);
}
