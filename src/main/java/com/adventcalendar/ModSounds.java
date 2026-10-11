package com.adventcalendar;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's custom sound events. The matching ogg files live in
 * {@code assets/adventcalendar/sounds/} and are described by {@code sounds.json}.
 *
 * <ul>
 *     <li>{@link #LOCK} - error sound when a locked door is clicked</li>
 *     <li>{@link #UNLOCK} - played when a door becomes available</li>
 *     <li>{@link #CLAIM} - played when a gift is claimed successfully</li>
 *     <li>{@link #NOTIFY} - soft ping for chat notifications</li>
 * </ul>
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, AdventCalendar.MOD_ID);

    public static final RegistryObject<SoundEvent> LOCK = register("lock");
    public static final RegistryObject<SoundEvent> UNLOCK = register("unlock");
    public static final RegistryObject<SoundEvent> CLAIM = register("claim");
    public static final RegistryObject<SoundEvent> NOTIFY = register("notify");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(AdventCalendar.MOD_ID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
