package com.adventcalendar;

import com.adventcalendar.entity.AdventNpcEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Entity types of the mod. */
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, AdventCalendar.MOD_ID);

    /** The Advent NPC: a stationary humanoid that wears any player skin. */
    public static final RegistryObject<EntityType<AdventNpcEntity>> ADVENT_NPC =
            ENTITIES.register("advent_npc", () -> EntityType.Builder
                    .of(AdventNpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(10)
                    .build("advent_npc"));
}
